/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { FEISHU_WRITE_CONTRACT, normalizeFeishuFailure, readOnlyReasonText, resolveBlockPage, resolveQuotaConfig, textBlockJson } from './feishu-contract'
import { createFeishuDocumentSession, createFeishuQuotaDraft, validateQuotaForm } from './feishu-state'

// 工作台状态回归：编辑草稿/脏状态、分页、并发冲突、限流与写后校验、额度草稿的保存门槛。
// 目标是证明「不会伪装成功」：写结果未确认、冲突、结构缺失时都不得显示已保存。

function blockPage() {
  return resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['b1', 'b2'] },
      { blockId: 'b1', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '原文' } }] } }) },
      { blockId: 'b2', parentId: 'page', blockType: 27, raw: JSON.stringify({ block_type: 27 }) },
    ],
    hasMore: false,
  })
}

function sessionWithContent() {
  const session = createFeishuDocumentSession()
  session.select('dox1')
  session.applyMeta({ documentId: 'dox1', title: '设计说明', revisionId: '7' })
  session.applyContent({ documentId: 'dox1', content: '正文' }, undefined)
  const page = blockPage()
  session.applyBlocks(page.items, { nextPageToken: page.nextPageToken, hasMore: page.hasMore })
  return session
}

test('selecting another document clears panes, paging and pending tickets', () => {
  const session = sessionWithContent()
  const text = session.state.blocks.find(node => node.blockId === 'b1')!
  session.beginEditBlock(text)
  const stale = session.blockTicket()

  session.select('dox2')
  assert.equal(session.state.meta, null)
  assert.equal(session.state.blocks.length, 0)
  assert.equal(session.state.blocksPageToken, undefined)
  assert.equal(session.state.contentStatus, 'loading')
  assert.equal(session.state.editingId, '')
  assert.equal(session.acknowledgeBlock(stale), false)
})

test('stale save acknowledgement cannot be applied after a reload', () => {
  const session = sessionWithContent()
  const text = session.state.blocks.find(node => node.blockId === 'b1')!
  session.beginEditBlock(text)
  const ticket = session.blockTicket()
  session.reset()
  session.select('dox1')
  const page = blockPage()
  session.applyBlocks(page.items, { nextPageToken: page.nextPageToken, hasMore: page.hasMore })
  assert.equal(session.acknowledgeBlock(ticket), false)
})

test('non text blocks stay read only and are explained', () => {
  const session = sessionWithContent()
  const image = session.state.blocks.find(node => node.blockId === 'b2')!
  assert.equal(session.beginEditBlock(image), false)
  assert.equal(session.state.editingId, '')
  assert.match(readOnlyReasonText(image.readOnlyReason), /只读/)
})

test('styled or multi element text blocks cannot enter edit mode', () => {
  const page = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'page', blockType: 1, children: ['styled', 'multi'] },
      { blockId: 'styled', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '高亮', text_element_style: { bold: true } } }] } }) },
      { blockId: 'multi', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: 'A' } }, { text_run: { content: 'B' } }] } }) },
    ],
    hasMore: false,
  })
  const session = createFeishuDocumentSession()
  session.select('dox1')
  session.applyMeta({ documentId: 'dox1', title: '样式文档' })
  session.applyBlocks(page.items, { hasMore: false })

  for (const blockId of ['styled', 'multi']) {
    const node = session.state.blocks.find(item => item.blockId === blockId)!
    assert.equal(session.beginEditBlock(node), false)
    assert.equal(session.state.editingId, '')
    assert.equal(node.editable, false)
  }
})

test('editing tracks dirty state and only submits with the confirmed write contract', () => {
  const session = sessionWithContent()
  const text = session.state.blocks.find(node => node.blockId === 'b1')!
  assert.equal(session.beginEditBlock(text), true)
  assert.equal(session.dirty.value, false)
  assert.equal(session.canSubmitBlock.value, false)

  session.state.draft = '改后的内容'
  assert.equal(session.dirty.value, true)
  assert.equal(session.canSubmitBlock.value, true)
  assert.equal(FEISHU_WRITE_CONTRACT.blockElements, 'confirmed')

  const ticket = session.blockTicket()
  assert.deepEqual(ticket.elements, [JSON.stringify({ text_run: { content: '改后的内容' } })])
  session.acknowledgeBlock(ticket)
  assert.equal(session.state.savedText, '改后的内容')
  assert.equal(session.dirty.value, false)
})

test('conflict keeps the local draft and blocks further submission', () => {
  const session = sessionWithContent()
  session.beginEditBlock(blockPage().items[1])
  session.state.draft = '本地新内容'
  session.setSaving(true)
  session.failWrite(normalizeFeishuFailure({ response: { status: 409 } }, 'document'))

  assert.equal(session.state.saving, false)
  assert.equal(session.state.conflict, true)
  assert.equal(session.state.draft, '本地新内容')
  assert.equal(session.canSubmitBlock.value, false)
})

test('quota exhausted failure keeps the draft and reports the throttle', () => {
  const session = sessionWithContent()
  session.beginEditBlock(blockPage().items[1])
  session.state.draft = '被限流的改动'
  session.setSaving(true)
  session.failWrite(normalizeFeishuFailure(new Error('飞书云文档调用额度已用完（每分钟 20 次、每日 200 次），请稍后再试'), 'document'))

  assert.equal(session.state.writeFailure?.kind, 'rate-limited')
  assert.equal(session.state.draft, '被限流的改动')
  assert.equal(session.state.conflict, false)
})

test('cancel is refused while saving so a pending write cannot be dropped', () => {
  const session = sessionWithContent()
  session.beginEditBlock(blockPage().items[1])
  session.setSaving(true)
  assert.equal(session.cancelEditBlock(), false)
  assert.equal(session.state.editingId, 'b1')
})

test('element write result is verified before the page may show success', () => {
  const session = sessionWithContent()
  session.beginEditBlock(blockPage().items[1])

  const unverified = session.settleElementWrite({ documentId: 'dox1', blockIds: ['b1'], blocksRaw: [JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '别的文本' } }] } })] }, 'b1', '改后的内容')
  assert.equal(unverified, 'unverified')
  assert.match(session.state.writeNotice, /与提交文本不一致/)

  const missing = session.settleElementWrite({ documentId: 'dox1', blockIds: [] }, 'b1', '改后的内容')
  assert.equal(missing, 'missing')
  assert.match(session.state.writeNotice, /未返回该块内容/)

  const verified = session.settleElementWrite({ documentId: 'dox1', blockIds: ['b1'], blocksRaw: [JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '改后的内容' } }] } })] }, 'b1', '改后的内容')
  assert.equal(verified, 'verified')
  assert.equal(session.state.writeNotice, '')
  // 本地块文本推进到真实值，页面不再显示旧文本
  assert.equal(session.state.blocks.find(node => node.blockId === 'b1')?.text, '改后的内容')
})

test('block pagination appends without duplicates and reports the truncation limit', () => {
  const session = sessionWithContent()
  const next = resolveBlockPage({
    documentId: 'dox1',
    items: [
      { blockId: 'b1', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '原文' } }] } }) },
      { blockId: 'b3', parentId: 'page', blockType: 2, raw: JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content: '新块' } }] } }) },
    ],
    hasMore: true,
  })
  session.applyBlocks(next.items, { nextPageToken: 'p2', hasMore: true })
  session.appendBlocks(resolveBlockPage({ documentId: 'dox1', items: [{ blockId: 'b4', blockType: 2 }], hasMore: true }).items, { nextPageToken: 'p3', hasMore: true })
  assert.deepEqual(session.state.blocks.map(node => node.blockId), ['b1', 'b3', 'b4'])
  assert.equal(session.state.blocksPageCount, 2)
  assert.equal(session.state.blocksHasMore, true)
  assert.equal(session.state.blocksTruncated, false)
  assert.equal(session.canLoadMoreBlocks.value, true)
})

test('child block append body carries a raw text block JSON', () => {
  const session = sessionWithContent()
  assert.equal(session.beginAddChild('page'), true)
  assert.equal(session.childDirty.value, false)
  assert.equal(session.canSubmitChild.value, false)

  session.state.childDraft = '   '
  assert.equal(session.canSubmitChild.value, false)

  session.state.childDraft = ' 新增段落 '
  assert.equal(session.childDirty.value, true)
  assert.equal(session.canSubmitChild.value, true)
  assert.deepEqual(session.childAppendBody(), { children: [textBlockJson('新增段落')] })
})

test('child count is read from the loaded parent block for write verification', () => {
  const session = sessionWithContent()
  assert.equal(session.childCountOf('page'), 2)
  assert.equal(session.childCountOf('missing'), undefined)
})

test('unsaved edits are reported so navigation can guard instead of silently discarding', () => {
  const session = sessionWithContent()
  assert.equal(session.hasUnsavedEdits.value, false)
  assert.equal(session.isWriting.value, false)

  session.beginEditBlock(blockPage().items[1])
  assert.equal(session.hasUnsavedEdits.value, false)
  session.state.draft = '未保存的文本'
  assert.equal(session.hasUnsavedEdits.value, true)

  // 确认放弃后才回到干净状态；未确认前草稿必须保留
  session.cancelEditBlock()
  assert.equal(session.hasUnsavedEdits.value, false)

  session.beginEditBlock(blockPage().items[1])
  session.state.draft = '仍要保留'
  assert.equal(session.hasUnsavedEdits.value, true)
  assert.equal(session.state.draft, '仍要保留')

  // 未提交的子块文本也属于未保存输入
  session.cancelEditBlock()
  session.beginAddChild('page')
  session.state.childDraft = '待提交子块'
  assert.equal(session.hasUnsavedEdits.value, true)
  session.cancelAddChild()
  assert.equal(session.hasUnsavedEdits.value, false)
})

test('a write in progress is reported separately so navigation blocks instead of discarding', () => {
  const session = sessionWithContent()
  session.beginEditBlock(blockPage().items[1])
  session.setSaving(true)
  assert.equal(session.isWriting.value, true)
  // 保存中即使用户没有新的草稿改动，也不能被当作可丢弃状态
  session.state.draft = ''
  session.setSaving(true)
  assert.equal(session.isWriting.value, true)
  session.setSaving(false)
  assert.equal(session.isWriting.value, false)

  session.beginAddChild('page')
  session.state.childDraft = 'x'
  session.setChildSaving(true)
  assert.equal(session.isWriting.value, true)
  session.failChild(normalizeFeishuFailure({ response: { status: 429 } }, 'document'))
  assert.equal(session.isWriting.value, false)
})

test('quota form validation rejects non positive and inverted limits', () => {
  assert.deepEqual(validateQuotaForm({ defaultPerMinuteLimit: 20, defaultDailyLimit: 200 }), {})
  assert.ok(validateQuotaForm({ defaultPerMinuteLimit: 0, defaultDailyLimit: 200 }).defaultPerMinuteLimit)
  assert.ok(validateQuotaForm({ defaultPerMinuteLimit: 10, defaultDailyLimit: 5 }).defaultDailyLimit)
  assert.ok(validateQuotaForm({ defaultPerMinuteLimit: 1.5, defaultDailyLimit: 10 }).defaultPerMinuteLimit)
})

test('quota draft keeps unsaved edits when the remote config changes', () => {
  const draft = createFeishuQuotaDraft()
  draft.load(resolveQuotaConfig({ defaultPerMinuteLimit: 20, defaultDailyLimit: 200 }))
  assert.equal(draft.dirty.value, false)
  assert.equal(draft.submittable.value, false)
  assert.deepEqual(draft.body(), { defaultPerMinuteLimit: 20, defaultDailyLimit: 200 })

  draft.state.form.defaultPerMinuteLimit = 30
  assert.equal(draft.dirty.value, true)
  assert.equal(draft.submittable.value, true)

  draft.load(resolveQuotaConfig({ defaultPerMinuteLimit: 25, defaultDailyLimit: 800 }))
  assert.equal(draft.state.form.defaultPerMinuteLimit, 30)
  assert.equal(draft.state.conflict, true)

  const ticket = draft.ticket()
  draft.setSaving(true)
  draft.acknowledge(ticket)
  assert.equal(draft.state.conflict, false)
  assert.equal(draft.dirty.value, false)
})

test('quota draft refuses submission when the response structure is unresolved', () => {
  const draft = createFeishuQuotaDraft()
  draft.load(resolveQuotaConfig({ defaultPerMinuteLimit: 20 }))
  assert.equal(draft.state.config?.resolved, false)
  draft.state.form.defaultDailyLimit = 100
  assert.equal(draft.dirty.value, true)
  assert.equal(draft.submittable.value, false)
  assert.equal(draft.discard(), false)
})

test('quota draft failure keeps the edited values for retry', () => {
  const draft = createFeishuQuotaDraft()
  draft.load(resolveQuotaConfig({ defaultPerMinuteLimit: 20, defaultDailyLimit: 200 }))
  draft.state.form.defaultDailyLimit = 900
  draft.setSaving(true)
  draft.fail(normalizeFeishuFailure({ response: { status: 403 } }, 'quota'))

  assert.equal(draft.state.saving, false)
  assert.equal(draft.state.failure?.kind, 'forbidden')
  assert.equal(draft.state.form.defaultDailyLimit, 900)
  assert.equal(draft.dirty.value, true)
})
