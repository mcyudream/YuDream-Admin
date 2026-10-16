/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { FEISHU_PERMISSIONS, FEISHU_WRITE_CONTRACT } from './feishu-contract'

// 结构性回归：读源码断言「不许伪装编辑成功」「端点只出现在 api 模块」。
// 这些约束无法在纯逻辑测试里覆盖，但一旦被改回乐观 UI 就是产品级回退。

function feishuFile(relativePath: string) {
  return readFileSync(join(import.meta.dirname, relativePath), 'utf8')
}

test('document content has no whole-document save entry point', () => {
  const detail = feishuFile('components/FeishuDocumentDetail.vue')
  assert.equal(FEISHU_WRITE_CONTRACT.documentContent, 'unavailable')
  // 正文编辑器只读，且没有整篇保存按钮或正文写入调用
  assert.match(detail, /:model-value="state\.content\.content"[\s\S]{0,80}readonly/)
  assert.doesNotMatch(detail, /保存正文/)
  assert.doesNotMatch(detail, /updateDocumentContent|patchDocumentContent/)
  // 只有被判定为「单个纯文本元素」的块才有编辑入口
  assert.match(detail, /只支持单个无样式文本元素块的 elements 更新/)
  // 向用户说明没有整篇保存能力，修改走块结构
  assert.match(detail, /没有整篇保存入口，内容修改走块结构/)
})

test('block editing stays within the confirmed element and children contract', () => {
  const detail = feishuFile('components/FeishuDocumentDetail.vue')
  assert.match(detail, /v-auth="FEISHU_PERMISSIONS\.edit"/)
  // 只提交文本 elements 与版本号，不做全块替换
  assert.match(detail, /elements: ticket\.elements/)
  assert.match(detail, /documentRevisionId: revisionParamOf\(state\.meta\?\.revisionId\)/)
  // 删除必须经权威下标判定，不能把页内位置当索引
  assert.match(detail, /deleteTargetOf\(node, state\.blocks\)/)
  assert.match(detail, /deleteBlockChildren\(props\.documentId, node\.parentId, target\.range\)/)
  assert.doesNotMatch(detail, /deleteRangeOf\(/)
  // 写入后以返回块原文 + 重读子块数校验，禁止乐观成功
  assert.match(detail, /session\.settleElementWrite\(data, ticket\.blockId, ticket\.text\)/)
  assert.match(detail, /verifyChildCount\(before, after, 'create'\)/)
  assert.match(detail, /verifyChildCount\(before, after, 'delete'\)/)
  assert.match(detail, /结果未确认/)
})

test('page states are explicit and never replaced by fabricated data', () => {
  const detail = feishuFile('components/FeishuDocumentDetail.vue')
  assert.match(detail, /该块未返回可解析的文本内容/)
  assert.match(detail, /未返回/)
  const browser = feishuFile('components/FeishuFileBrowser.vue')
  assert.match(browser, /no-result/)
  assert.match(browser, /目录为空/)
  assert.match(browser, /加载更多/)
  const nav = feishuFile('components/FeishuSourceNav.vue')
  assert.match(nav, /选择空间后浏览知识库节点/)
  assert.match(nav, /@click="emit\('selectSpace', space\.spaceId\)"/)
  const page = feishuFile('index.vue')
  assert.match(page, /import apiFeishu from '@\/api\/modules\/platform-feishu\.ts'/)
  assert.match(page, /:nodes="filteredWikiNodes"/)
  assert.match(page, /node\.objType === 'docx' && node\.objToken/)
  assert.doesNotMatch(page, /<YdPageMain/)
  assert.match(page, /\.feishu-page\s*\{[\s\S]*height: 100%/)
  assert.match(page, /\.feishu-workbench__main\s*\{[\s\S]*overflow-y: auto/)
  assert.match(nav, /FeishuAccessGuide compact/)
  assert.match(nav, /没有可见知识库/)
  const wikiBrowser = feishuFile('components/FeishuWikiNodeBrowser.vue')
  assert.match(wikiBrowser, /浏览子节点/)
  assert.match(wikiBrowser, /row\.hasChild && row\.objType === 'docx'/)
  assert.match(wikiBrowser, /打开文档/)
  assert.match(wikiBrowser, /当前目录没有节点/)
  const accessGuide = feishuFile('components/FeishuAccessGuide.vue')
  assert.match(accessGuide, /drive:drive:readonly/)
  assert.match(accessGuide, /wiki:wiki:readonly/)
  assert.match(accessGuide, /docx:document:readonly/)
  assert.match(accessGuide, /添加文档应用/)
  assert.match(accessGuide, /知识库管理员/)
})

test('paged lists and blocks do not pretend to be complete', () => {
  const browser = feishuFile('components/FeishuFileBrowser.vue')
  assert.match(browser, /搜索与类型筛选只作用于已加载条目/)
  const detail = feishuFile('components/FeishuDocumentDetail.vue')
  assert.match(detail, /块未全部加载/)
})

test('leaving a document goes through a dirty guard, never a silent reset', () => {
  const detail = feishuFile('components/FeishuDocumentDetail.vue')
  const page = feishuFile('index.vue')
  // 详情组件只暴露守卫状态与显式放弃入口，不自己清空草稿
  assert.match(detail, /defineExpose\(\{ hasUnsavedEdits, isWriting, discardEdits, confirmLeave \}\)/)
  assert.match(detail, /只在用户明确确认放弃后调用/)
  // 放弃只发生在确认回调里
  assert.match(detail, /onConfirm: \(\) => \{\s*discardEdits\(\)\s*resolve\(true\)/)
  assert.match(detail, /onCancel: \(\) => resolve\(false\)/)
  // 写入进行中直接拒绝离开，不等价于可丢弃
  assert.match(detail, /本次写入结果尚未确认，请等待完成后再离开/)
  // 视图切换、返回、目录跳转、打开其他文档都先过 leaveGuard
  assert.match(page, /function leaveGuard\(action: \(\) => void\)/)
  for (const action of ['openFolder', 'openRoot', 'goToFolder', 'selectSpace', 'openWikiNode', 'openWikiDocument', 'goToWikiNodeParent', 'openDocument', 'closeDocument']) {
    const block = page.slice(page.indexOf(`function ${action}(`), page.indexOf(`function ${action}(`) + 400)
    assert.match(block, /leaveGuard\(/, `${action} must be guarded`)
  }
  assert.match(page, /onViewChange/)
  // 点击路径只经守卫：确认前不改任何状态
  assert.match(page, /if \(detail && !await detail\.confirmLeave\(\)\) \{\s*return false/)
  assert.doesNotMatch(page, /@back="activeDocumentId = ''"/)
  // 侧栏等整页离开路径同样被守卫，并在硬刷新时提示
  assert.match(page, /onBeforeRouteLeave\(\(\) => detailRef\.value\?\.confirmLeave\(\) \?\? true\)/)
  assert.match(page, /onBeforeRouteUpdate\(\(\) => detailRef\.value\?\.confirmLeave\(\) \?\? true\)/)
  assert.match(page, /window\.addEventListener\('beforeunload', warnUnsaved\)/)
  assert.match(page, /window\.removeEventListener\('beforeunload', warnUnsaved\)/)
})

test('endpoints live in the api module only', () => {
  const api = readFileSync(join(import.meta.dirname, '../../../api/modules/platform-feishu.ts'), 'utf8')
  for (const endpoint of [
    'api/platform/feishu/drive/files',
    'api/platform/feishu/wiki/spaces',
    '/nodes',
    'api/platform/feishu/documents/',
    '/blocks',
    '/elements',
    '/children',
    'api/platform/feishu/quota/me',
    'api/platform/feishu/quota/config',
  ]) {
    assert.ok(api.includes(endpoint), `api module should own ${endpoint}`)
  }
  // 端点不得在页面组件里拼装
  for (const view of [
    'index.vue',
    'components/FeishuWikiNodeBrowser.vue',
    'components/FeishuFileBrowser.vue',
    'components/FeishuDocumentDetail.vue',
    'components/FeishuQuotaPanel.vue',
    'components/FeishuSourceNav.vue',
  ]) {
    assert.doesNotMatch(feishuFile(view), /api\/platform\/feishu/)
  }
})

test('permission codes used by the page are the backend-registered ones', () => {
  assert.deepEqual(FEISHU_PERMISSIONS, {
    view: 'platform:feishu:view',
    edit: 'platform:feishu:edit',
    config: 'platform:feishu:config',
  })
  assert.match(feishuFile('components/FeishuDocumentDetail.vue'), /v-auth="FEISHU_PERMISSIONS\.edit"/)
  assert.match(feishuFile('components/FeishuQuotaPanel.vue'), /v-auth="FEISHU_PERMISSIONS\.config"/)
})
