/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { wikiManageAreaOf, wikiManageAreaPanels, wikiManageAreas, wikiManagePanels } from './wiki-utils'

// 评审回归：工作台外壳、工作区分栏与多标签、治理分区导航、llm-gen、设置版式与图片可见性。
// 这些断言读源码做结构性回归，配合 vue-tsc/eslint 覆盖无法在单测里渲染的布局与导航。

function wikiFile(relativePath: string) {
  return readFileSync(join(import.meta.dirname, relativePath), 'utf8')
}

test('directories can be renamed from the toolbar title input', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 目录标题不再禁用（saveNode 一直支持提交目录标题）
  assert.doesNotMatch(author, /:disabled="node\?\.nodeType === 'DIRECTORY'"/)
  assert.doesNotMatch(author, /:disabled="node\.nodeType === 'DIRECTORY'"/)
  // 标题只保留工具条一处输入（窄屏不再复制第二份，工作区靠标签与面板切换承载多页面）
  assert.equal(author.match(/v-model="editorFields\.title"/g)?.length, 1)
  assert.match(author, /:disabled="!node"[\s\S]{0,200}aria-label="页面标题"/)
  // 保存分支仍然提交目录标题
  assert.match(author, /title: fields\.title/)
})

test('workbench shell fills the area with its own background and never remounts on internal navigation', () => {
  const index = wikiFile('index.vue')
  // 全部工作面共享一个铺满的外壳：绝对定位 + 自带背景，不再依赖宿主 auto 高度的百分比链
  assert.match(index, /\.wiki-workbench \{[\s\S]*?position: absolute;[\s\S]*?inset: 0;/)
  assert.match(index, /\.wiki-workbench \{[\s\S]*?background: var\(--color-bg-1\);/)
  // 入口页自己滚动（外壳 overflow: hidden），不会把长内容裁掉
  assert.match(index, /\.wiki-workbench__entry \{[\s\S]*?overflow: auto;/)
  // 页面内导航用 replaceState 静默同步，绝不 router.push(...)（宿主按 route.fullPath 重挂载整页）
  assert.doesNotMatch(index, /router\.push\(/)
  assert.doesNotMatch(index, /useRouter/)
  assert.match(index, /window\.history\.replaceState/)
  // 工作面状态是本地 ref，query 只在真实导航时采用
  assert.match(index, /const view = ref<WikiWorkbenchView>/)
  assert.match(index, /const activePanel = ref<string>/)
})

test('editor is a workspace: activity rail, multi-document tabs, draggable panes and a status bar', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 外壳来自组件库，分栏宽度由 storage-key 记忆
  assert.match(author, /<YdWorkspaceLayout/)
  assert.match(author, /storage-key="wiki-author"/)
  assert.match(author, /v-model:rail="rail"/)
  assert.match(author, /v-model:sidebar-open="sidebarOpen"/)
  assert.match(author, /v-model:aside-open="asideOpen"/)
  assert.match(author, /:rail-items="railItems"/)
  // 多文件标签栏 + 打开/关闭都经文档管理器
  assert.match(author, /<YdWorkspaceTabs/)
  assert.match(author, /createWikiDocuments\(\)/)
  assert.match(author, /function closeDocument\(key: string\)/)
  assert.match(author, /@close="closeDocument"/)
  // 关闭未保存标签要先确认，不能静默丢草稿
  assert.match(author, /关闭未保存的标签？/)
  assert.match(author, /离开未保存的页面？/)
  // 状态栏给出路径、字数与保存态
  assert.match(author, /const statusItems = computed/)
  assert.match(author, /字`, tone: 'muted'/)
  assert.match(author, /已保存/)
  // 窄屏由外壳的面板切换器承载，不再维护第二套移动端模板
  assert.match(author, /v-model:pane="pane"/)
  assert.doesNotMatch(author, /v-else-if="isMobile"/)
  assert.doesNotMatch(author, /mobilePane/)
})

test('conflict resolution is reachable in every viewport', () => {
  const author = wikiFile('components/WikiAuthorEditor.vue')
  // 正文流里的冲突说明与重新载入（任何视口都可见，窄屏也在主面板内）
  assert.match(author, /v-if="conflict" class="author-conflict"[\s\S]{0,400}重新载入/)
  assert.match(author, /@click="reloadRemote"/)
  // 移动端底部固定栏同样提供重新载入
  const savebar = author.slice(author.indexOf('<YdFixedBar'))
  assert.match(savebar, /conflict[\s\S]{0,300}重新载入/)
  assert.match(savebar, /@click="reloadRemote"/)
  // 定位评论引用时窄屏先把主面板切回来
  assert.match(author, /pane\.value = 'main'/)
})

test('llm-gen is a real first-level directory, not only a grouped view', () => {
  const panel = wikiFile('components/WikiGeneratedPanel.vue')
  // 真实一级目录：根节点匹配 slug/title
  assert.match(panel, /store\.tree\.value\.find\(item =>/)
  assert.match(panel, /item\.slug === 'llm-gen' \|\| item\.title === 'llm-gen'/)
  // 创建的是 DIRECTORY 节点（无父节点 = 一级）
  assert.match(panel, /nodeType: 'DIRECTORY'/)
  assert.match(panel, /title: 'llm-gen'/)
  assert.match(panel, /slug: 'llm-gen'/)
  assert.match(panel, /parentId: undefined/)
  // 页面可以真正移入该目录
  assert.match(panel, /moveWikiNode\(page\.id, dir\.id\)/)
  assert.match(panel, /function moveAllInto\(\)/)
  // 文案明确分组视图 ≠ 目录归属
  assert.match(panel, /分组只是阅读视图，不代表页面已经在 llm-gen 目录下/)
  // 移动接口通过 Wiki 目录内的本地封装调用，且契约是核对过的而不是猜的
  const ops = wikiFile('wiki-node-ops.ts')
  assert.match(ops, /api\/platform\/wiki\/nodes\/\$\{id\}\/move/)
  assert.match(ops, /WikiNodeMoveRequest \{ String parentId \}/)
  assert.match(ops, /POST \/api\/platform\/wiki\/nodes\/\{id\}\/move/)
  assert.match(ops, /platform:wiki:edit/)
  // 服务端 Long.valueOf(parentId)：必须传数字字符串，禁止 Number(id)
  assert.match(ops, /必须传数字字符串/)
})

test('inline images stay private and the public rendering gap is stated, not claimed solved', () => {
  const images = wikiFile('wiki-images.ts')
  // 上传固定私有，不做空间可见性推导
  assert.match(images, /始终按私有文件保存/)
  assert.doesNotMatch(images, /publicReadEnabled/)
  // 未决契约在代码里显式记录（代理 / 提升两种方案）
  assert.match(images, /nodes\/\{nodeId\}\/images\/\{fileId\}/)
  assert.match(images, /已知未决项/)
  // 作者界面提示公开页仍未决，不误导
  const author = wikiFile('components/WikiAuthorEditor.vue')
  assert.match(author, /wikiImageUploadHint\(\)/)
})

test('settings uses YdSettingsLayout with header save, no fixed bottom bar', () => {
  const settings = wikiFile('components/WikiSettingsPanel.vue')
  assert.match(settings, /<YdSettingsLayout/)
  assert.match(settings, /<YdSettingsSection/)
  assert.match(settings, /surface/)
  assert.match(settings, /scroll/)
  assert.match(settings, /<template #header>/)
  assert.match(settings, /保存设置/)
  assert.doesNotMatch(settings, /YdFixedBar/)
  // 滚动模式需要的有界外壳
  assert.match(settings, /\.settings-page-shell \{[\s\S]*?height: 100%;/)
})

test('manage surface is one settings layout: left area nav, dynamic right content', () => {
  const manage = wikiFile('components/WikiManageView.vue')
  assert.equal(wikiManageAreas.length, 6)
  // 每个分区恰好属于一个区域，且区域首个分区必须是已注册分区
  const registered = new Set(wikiManagePanels.map(panel => panel.key))
  const seen = new Set<string>()
  for (const area of wikiManageAreas) {
    assert.ok(area.panels.length > 0, `${area.key} 区域必须有分区`)
    for (const key of area.panels) {
      assert.ok(registered.has(key), `${area.key} 引用了未注册分区 ${key}`)
      assert.ok(!seen.has(key), `分区 ${key} 被多个区域引用`)
      seen.add(key)
    }
    assert.equal(wikiManageAreaOf(area.panels[0]).key, area.key)
    assert.equal(wikiManageAreaPanels(area.key).length, area.panels.length)
  }
  assert.equal(seen.size, registered.size, '所有分区都要落在某个区域下')
  // 整体使用设置版式：左侧区域导航，右侧按选中区域动态加载面板；不再自绘 <nav>
  assert.match(manage, /<YdSettingsLayout/)
  assert.match(manage, /:list="areaList"/)
  assert.match(manage, /surface[\s\S]{0,40}scroll/)
  assert.doesNotMatch(manage, /<nav class="manage__nav"/)
  assert.doesNotMatch(manage, /manage__mobile-nav/)
  // 区域插槽显式写全，不用 v-for + 动态插槽名（该组合会让组件库模板编译器崩溃）
  assert.doesNotMatch(manage, /#\[area\.key\]/)
  assert.equal(manage.match(/<WikiManageArea/g)?.length, 6)
  for (const area of wikiManageAreas) {
    assert.match(manage, new RegExp(`area-key="${area.key}"`), `${area.key} 区域要有对应内容插槽`)
  }
  // 离开未保存的设置要确认，不能静默丢改动
  assert.match(manage, /areaInstances\.get\('settings'\)\?\.settingsDirty\(\)/)
  assert.match(manage, /离开未保存的设置？/)
  // 区域内容：多分区用横向子标签，且子标签行允许换行（不再被头部 overflow 硬裁）
  const area = wikiFile('components/WikiManageArea.vue')
  assert.match(area, /function areaTabs|const areaTabs = computed/)
  assert.match(area, /<YdSegmented[\s\S]{0,260}v-model="activePanelKey"/)
  assert.match(area, /\.manage__subhead \{[\s\S]*?flex-wrap: wrap;/)
  assert.match(area, /settingsDirty: \(\) => Boolean\(settingsRef\.value\?\.dirty\)/)
})
