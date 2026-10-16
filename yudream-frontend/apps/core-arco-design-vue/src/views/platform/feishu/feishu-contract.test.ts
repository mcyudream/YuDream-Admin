/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  appendPage,
  blockElementsOf,
  deleteRangeOf,
  deleteTargetOf,
  failureTitle,
  FEISHU_PERMISSIONS,
  fileTypeOptions,
  filterFileItems,
  normalizeFeishuFailure,
  parseBlockText,
  resolveBlockPage,
  resolveCloudDocPage,
  resolveDocumentContent,
  resolveDocumentMeta,
  resolveQuotaConfig,
  resolveQuotaUsage,
  resolveWikiNodePage,
  resolveWikiSpacePage,
  revisionParamOf,
  textBlockJson,
  textElementJson,
  verifyChildCount,
  verifyElementWrite,
} from './feishu-contract'

// 契约回归：字段名/端点以后端 FeishuCloudDocController、FeishuQuotaController 的实际 DTO 为准。

test('failure kind distinguishes collection 404 from document 404', () => {
  const notFound = { response: { status: 404 } }
  assert.equal(normalizeFeishuFailure(notFound, 'collection').kind, 'unavailable')
  assert.equal(normalizeFeishuFailure(notFound, 'document').kind, 'not-found')
})

test('failure kind maps throttle, conflict and permission statuses', () => {
  assert.equal(normalizeFeishuFailure({ response: { status: 429 } }, 'document').kind, 'rate-limited')
  assert.equal(normalizeFeishuFailure({ response: { status: 409 } }, 'document').kind, 'conflict')
  assert.equal(normalizeFeishuFailure({ response: { status: 403 } }, 'quota').kind, 'forbidden')
})

test('backend capability and quota BizExceptions map to the right page states', () => {
  // 后端原文：CapabilityAppService.ensureEnabled -> "飞书开放平台能力未启用，请先在平台能力中启用"
  assert.equal(normalizeFeishuFailure(new Error('飞书开放平台能力未启用，请先在平台能力中启用'), 'collection').kind, 'unconfigured')
  // 后端原文：FeishuAppService.consumed -> "飞书云文档调用额度已用完（每分钟 20 次、每日 200 次）..."
  assert.equal(normalizeFeishuFailure(new Error('飞书云文档调用额度已用完（每分钟 20 次、每日 200 次），请稍后再试或联系管理员调整额度'), 'document').kind, 'rate-limited')
  // 后端原文：FeishuQuotaAppService.me -> "当前用户未登录或账号不可用，无法查看飞书调用额度"
  assert.equal(normalizeFeishuFailure(new Error('当前用户未登录或账号不可用，无法查看飞书调用额度'), 'quota').kind, 'unauthorized')
  assert.equal(normalizeFeishuFailure(new Error('完全无法识别的错误'), 'document').kind, 'error')
  assert.equal(failureTitle('unconfigured'), '能力未启用')
})

test('cloud doc page resolves the real field names and folder type', () => {
  const page = resolveCloudDocPage({
    items: [
      { token: 'fld1', name: '产品文档', type: 'folder', modifiedTime: '2026-10-15 09:00' },
      { token: 'dox1', name: '需求说明', type: 'docx', url: 'https://example.com/d/1', ownerId: '7001', parentToken: 'fld1' },
    ],
    nextPageToken: 'pt-2',
    hasMore: true,
  })
  assert.equal(page.hasMore, true)
  assert.equal(page.nextPageToken, 'pt-2')
  assert.equal(page.items[0].isFolder, true)
  assert.equal(page.items[1].isFolder, false)
  assert.equal(page.items[1].url, 'https://example.com/d/1')
  assert.equal(page.items[1].ownerId, '7001')
})

test('wiki space page resolves spaceId and keeps missing fields undefined', () => {
  const page = resolveWikiSpacePage({ items: [{ spaceId: 'sp-1', name: '运维知识库', visibility: 'public' }], hasMore: false })
  assert.equal(page.items[0].spaceId, 'sp-1')
  assert.equal(page.items[0].description, undefined)
  assert.equal(page.hasMore, false)
})

test('wiki node page preserves object token/type, parent and empty continuation pages', () => {
  const page = resolveWikiNodePage({
    spaceId: 'sp-1',
    items: [{ nodeToken: 'wiki-n1', objToken: 'docx-1', objType: 'docx', title: '手册', hasChild: true, parentNodeToken: 'parent-1' }],
    nextPageToken: 'page-2',
    hasMore: true,
  })
  assert.deepEqual(page.items[0], {
    nodeToken: 'wiki-n1',
    objToken: 'docx-1',
    objType: 'docx',
    title: '手册',
    hasChild: true,
    parentNodeToken: 'parent-1',
    nodeType: undefined,
    originNodeToken: undefined,
    originSpaceId: undefined,
    spaceId: undefined,
  })
  assert.equal(page.hasMore, true)
  assert.deepEqual(resolveWikiNodePage({ spaceId: 'sp-1', items: [], nextPageToken: 'page-3', hasMore: true }), {
    items: [],
    nextPageToken: 'page-3',
    hasMore: true,
  })
  assert.deepEqual(resolveWikiNodePage({ spaceId: 'sp-1', items: [{ nodeToken: '', objToken: 'bad' }] }).items, [])
  assert.deepEqual(appendPage([], [{ nodeToken: 'n1' }, { nodeToken: 'n1' }], node => node.nodeToken), [{ nodeToken: 'n1' }])
})

test('page append deduplicates by key so repeated tokens do not duplicate rows', () => {
  const first = [{ token: 'a' }, { token: 'b' }]
  const merged = appendPage(first, [{ token: 'b' }, { token: 'c' }, { token: '' }], item => item.token)
  assert.deepEqual(merged.map(item => item.token), ['a', 'b', 'c'])
  assert.deepEqual(appendPage([], [{ token: 'x' }], item => item.token), [{ token: 'x' }])
})

test('keyword and type filters only touch loaded rows', () => {
  const page = resolveCloudDocPage({
    items: [
      { token: 'a', name: '发布流程', type: 'docx', ownerId: '7001' },
      { token: 'b', name: '图片素材', type: 'folder' },
    ],
  })
  assert.deepEqual(filterFileItems(page.items, '发布').map(item => item.token), ['a'])
  assert.deepEqual(filterFileItems(page.items, 'folder').map(item => item.token), ['b'])
  assert.equal(filterFileItems(page.items, '  ').length, 2)
  assert.deepEqual(fileTypeOptions(page.items), [{ label: 'docx', value: 'docx' }, { label: '文件夹', value: 'folder' }])
})

test('document meta and content map to the real response fields', () => {
  const meta = resolveDocumentMeta({ documentId: 'dox1', title: '需求说明', revisionId: '42' }, 'fallback')
  assert.deepEqual(meta, { documentId: 'dox1', title: '需求说明', revisionId: '42' })
  assert.equal(resolveDocumentMeta({ documentId: 'dox1' }, 'dox1').title, 'dox1')

  const content = resolveDocumentContent({ documentId: 'dox1', content: '第一段\n第二段' })
  assert.equal(content.content, '第一段\n第二段')
  assert.equal(resolveDocumentContent({ documentId: 'dox1' }).content, '')
})

test('block raw JSON is parsed for text instead of being displayed as raw or as placeholder text', () => {
  const raw = JSON.stringify({ block_id: 'b1', block_type: 2, text: { elements: [{ text_run: { content: '正文' } }] } })
  assert.deepEqual(parseBlockText(raw), { text: '正文', missing: false })
  assert.deepEqual(parseBlockText(JSON.stringify({ block_type: 27, image: { token: 'img' } })), { missing: true })
  assert.deepEqual(parseBlockText('not-json'), { missing: true })
  assert.deepEqual(parseBlockText(undefined), { missing: true })
})

test('block page marks text blocks editable, images read-only, and keeps sibling indices', () => {
  const page = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['b1', 'b2'] },
      { blockId: 'b1', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '第一段' } }] } }) },
      { blockId: 'b2', parentId: 'page', blockType: 27, raw: JSON.stringify({ block_type: 27 }) },
    ],
    nextPageToken: 'blocks-2',
    hasMore: true,
  })
  const text = page.items.find(node => node.blockId === 'b1')
  const image = page.items.find(node => node.blockId === 'b2')
  assert.equal(text?.editable, true)
  assert.equal(text?.text, '第一段')
  assert.equal(text?.index, 0)
  assert.equal(text?.depth, 1)
  assert.equal(image?.editable, false)
  assert.match(image?.readOnlyReason ?? '', /保持只读/)
  assert.equal(page.items.find(node => node.blockId === 'page')?.childCount, 2)
  assert.equal(page.nextPageToken, 'blocks-2')
  assert.equal(page.hasMore, true)
  assert.equal(page.items.find(node => node.blockId === 'b1')?.textMissing, false)
})

test('blocks without a returned id are dropped rather than rendered as fake rows', () => {
  const page = resolveBlockPage({ documentId: 'dox1', items: [{ blockId: '', blockType: 2 }], hasMore: false })
  assert.equal(page.items.length, 0)
})

test('text element and child block payloads follow the raw JSON contract', () => {
  assert.equal(textElementJson('新文本'), JSON.stringify({ text_run: { content: '新文本' } }))
  assert.equal(textBlockJson('子块'), JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '子块' } }] } }))
})

test('revision param is only sent when it is a safe integer version', () => {
  assert.equal(revisionParamOf('42'), 42)
  assert.equal(revisionParamOf('-1'), -1)
  assert.equal(revisionParamOf(undefined), undefined)
  assert.equal(revisionParamOf(''), undefined)
  assert.equal(revisionParamOf('abc'), undefined)
  // 超出安全整数范围的 Snowflake 式取值不做 Number 转换
  assert.equal(revisionParamOf('1234567890123456789'), undefined)
})

test('delete range follows the backend half-open contract', () => {
  assert.deepEqual(deleteRangeOf(3), { startIndex: 3, endIndex: 4 })
})

test('write verification reads the returned block raw and never claims success without a match', () => {
  const result = {
    documentId: 'dox1',
    revisionId: '43',
    blockIds: ['b1'],
    blocksRaw: [JSON.stringify({ block_id: 'b1', block_type: 2, text: { elements: [{ text_run: { content: '新文本' } }] } })],
  }
  assert.equal(verifyElementWrite(result, 'b1', '新文本'), 'verified')
  assert.equal(verifyElementWrite(result, 'b1', '期望文本'), 'unverified')
  assert.equal(verifyElementWrite({ documentId: 'dox1', blockIds: ['b1'] }, 'b1', '新文本'), 'missing')
  assert.equal(verifyElementWrite(undefined, 'b1', '新文本'), 'missing')
})

test('child count verification detects unconfirmed writes in both directions', () => {
  assert.equal(verifyChildCount(2, 3, 'create'), 'verified')
  assert.equal(verifyChildCount(2, 2, 'create'), 'unverified')
  assert.equal(verifyChildCount(3, 2, 'delete'), 'verified')
  assert.equal(verifyChildCount(undefined, 1, 'create'), 'unknown')
})

test('quota records map the real field names and report missing limits', () => {
  const usage = resolveQuotaUsage({ userId: '1', minuteUsed: 3, minuteLimit: 20, dailyUsed: 40, dailyLimit: 200 })
  assert.equal(usage.minuteRemaining, 17)
  assert.equal(usage.dailyRemaining, 160)
  assert.equal(resolveQuotaUsage({ userId: '1' }).minuteUsed, undefined)

  const config = resolveQuotaConfig({ defaultPerMinuteLimit: 20, defaultDailyLimit: 200 })
  assert.equal(config.resolved, true)
  assert.equal(config.defaultPerMinuteLimit, 20)

  const incomplete = resolveQuotaConfig({ defaultPerMinuteLimit: 20 })
  assert.equal(incomplete.resolved, false)
  assert.deepEqual(incomplete.missing, ['defaultDailyLimit'])
})

test('permission codes match the backend registrations', () => {
  assert.deepEqual(FEISHU_PERMISSIONS, {
    view: 'platform:feishu:view',
    edit: 'platform:feishu:edit',
    config: 'platform:feishu:config',
  })
})

test('only a single plain text run block is considered losslessly replaceable', () => {
  const plain = JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '整段文本' } }] } })
  assert.deepEqual(blockElementsOf(plain).losslessReplaceable, true)
  assert.equal(blockElementsOf(plain).count, 1)

  // 空文本块：没有可丢失的元素，单元素写入无损
  assert.equal(blockElementsOf(JSON.stringify({ block_type: 2, text: { elements: [] } })).losslessReplaceable, true)

  const multi = JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '加粗' } }, { text_run: { content: '普通' } }] } })
  const multiResult = blockElementsOf(multi)
  assert.equal(multiResult.losslessReplaceable, false)
  assert.equal(multiResult.count, 2)
  assert.match(multiResult.reason ?? '', /整块替换会丢失原有分段\/格式/)

  // 带样式（text_element_style）的单个元素同样会被覆盖掉格式
  const styled = JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '高亮', text_element_style: { bold: true } } }] } })
  assert.equal(blockElementsOf(styled).losslessReplaceable, false)
  assert.match(blockElementsOf(styled).reason ?? '', /带样式、链接或其他类型/)

  // 链接、@、公式等非 text_run 元素一律不可替换
  const link = JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '链接' } }, { mention_user: { user_id: '1' } }] } })
  assert.equal(blockElementsOf(link).losslessReplaceable, false)

  // 结构缺失或非法：不能证明无损，保持只读
  assert.equal(blockElementsOf(undefined).losslessReplaceable, false)
  assert.equal(blockElementsOf('not-json').losslessReplaceable, false)
  assert.equal(blockElementsOf(JSON.stringify({ block_type: 2 })).losslessReplaceable, false)
  assert.match(blockElementsOf(JSON.stringify({ block_type: 2 })).reason ?? '', /没有文本元素结构/)
})

test('styled or multi element text blocks are read only in the resolved tree', () => {
  const page = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['plain', 'styled', 'multi'] },
      { blockId: 'plain', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '可编辑' } }] } }) },
      { blockId: 'styled', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '带样式', text_element_style: { bold: true } } }] } }) },
      { blockId: 'multi', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: 'A' } }, { text_run: { content: 'B' } }] } }) },
    ],
    hasMore: false,
  })
  const byId = new Map(page.items.map(node => [node.blockId, node]))
  assert.equal(byId.get('plain')?.editable, true)
  assert.equal(byId.get('styled')?.editable, false)
  assert.match(byId.get('styled')?.readOnlyReason ?? '', /带样式、链接或其他类型/)
  assert.equal(byId.get('multi')?.editable, false)
  assert.match(byId.get('multi')?.readOnlyReason ?? '', /2 个文本元素/)
})

test('sibling index is only authoritative when the parent children list provides it', () => {
  const page = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['b1', 'b2'] },
      { blockId: 'b1', parentId: 'page', blockType: 2 },
      { blockId: 'b2', parentId: 'page', blockType: 2 },
      // 父块 children 未返回：不得用页内位置冒充同级下标
      { blockId: 'orphan', parentId: 'unknown-parent', blockType: 2 },
      { blockId: 'no-parent', blockType: 2 },
    ],
    hasMore: false,
  })
  const byId = new Map(page.items.map(node => [node.blockId, node]))
  assert.equal(byId.get('b1')?.index, 0)
  assert.equal(byId.get('b2')?.index, 1)
  assert.equal(byId.get('orphan')?.index, undefined)
  assert.equal(byId.get('no-parent')?.index, undefined)
  assert.equal(byId.get('page')?.childCount, 2)
  assert.equal(byId.get('orphan')?.childCount, undefined)
  assert.equal(byId.get('orphan')?.parentLoaded, false)
  assert.equal(byId.get('b1')?.parentLoaded, true)
})

test('delete is refused unless the parent children list confirms the index', () => {
  const page = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['b1', 'b2'] },
      { blockId: 'b1', parentId: 'page', blockType: 2 },
      { blockId: 'b2', parentId: 'page', blockType: 2 },
    ],
    hasMore: false,
  })
  const byId = new Map(page.items.map(node => [node.blockId, node]))
  const b2 = byId.get('b2')!

  const ok = deleteTargetOf(b2, page.items)
  assert.equal(ok.ok, true)
  assert.deepEqual(ok.range, { startIndex: 1, endIndex: 2 })

  // 只加载了子块、父块不在当前列表：拒绝删除而不是猜下标
  const withoutParent = deleteTargetOf(b2, page.items.filter(node => node.blockId !== 'page'))
  assert.equal(withoutParent.ok, false)
  assert.equal(withoutParent.range, undefined)
  assert.match(withoutParent.reason ?? '', /父块未加载/)

  // 父块 children 未列出该块：下标不可确认
  const orphan = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['other'] },
      { blockId: 'other', parentId: 'page', blockType: 2 },
      { blockId: 'ghost', parentId: 'page', blockType: 2 },
    ],
    hasMore: false,
  })
  const ghost = orphan.items.find(node => node.blockId === 'ghost')!
  const ghostTarget = deleteTargetOf(ghost, orphan.items)
  assert.equal(ghostTarget.ok, false)
  assert.match(ghostTarget.reason ?? '', /children 未列出该块/)

  // 没有 parentId 时同样拒绝
  const rootless = deleteTargetOf({ ...ghost, parentId: undefined }, orphan.items)
  assert.equal(rootless.ok, false)
  assert.match(rootless.reason ?? '', /未返回 parentId/)
})
