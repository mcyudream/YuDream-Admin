import assert from 'node:assert/strict'
import test from 'node:test'
import { applyStudioCommand, createStudioDocument, createStudioNode } from '../utils/site-studio.ts'
import { executeStudioAgentTool, parseStudioAgentCommand } from './use-site-studio-agent.ts'

function fixture() {
  const document = createStudioDocument()
  document.pages[0]!.root.push(createStudioNode('text'))
  const context = { document, contextKey: 'studio-test', pageId: document.pages[0]!.id, revision: 'local-1', selectedNodeId: document.pages[0]!.root[0]!.id }
  const args = { contextKey: context.contextKey, documentId: document.id, pageId: context.pageId }
  let calls = 0
  const execute = (command: Parameters<typeof applyStudioCommand>[1]) => {
    context.document = applyStudioCommand(context.document, command)
    context.revision = 'local-2'
    calls++
  }
  return { context, args, execute, calls: () => calls }
}
test('read is paged and always returns binding and local revision; validate reads real document', async () => {
  const f = fixture()
  const read = await executeStudioAgentTool('cms.studio.read', { ...f.args, limit: 1 }, () => f.context, f.execute)
  assert.equal(read.revision, 'local-1')
  assert.equal(read.documentId, f.context.document.id)
  assert.equal((read.nodes as unknown[]).length, 1)
  assert.equal((await executeStudioAgentTool('cms.studio.validate', f.args, () => f.context, f.execute)).valid, true)
  f.context.document.pages[0]!.root[0]!.tag = 'script'
  const validation = await executeStudioAgentTool('cms.studio.validate', f.args, () => f.context, f.execute)
  assert.equal(validation.valid, false)
  assert.equal(f.calls(), 0)
})
test('apply is a single atomic batch and failed candidates never reach editor history', async () => {
  const f = fixture()
  const nodeId = f.context.selectedNodeId
  const result = await executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'local-1', command: { type: 'batch', commands: [{ type: 'set-text', nodeId, text: '真实修改' }, { type: 'set-style', nodeId, styles: { color: 'red' } }] } }, () => f.context, f.execute)
  assert.equal(result.commandCount, 2)
  assert.equal(f.calls(), 1)
  assert.equal(f.context.document.pages[0]!.root[0]!.text, '真实修改')
  await assert.rejects(executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'local-2', command: { type: 'batch', commands: [{ type: 'set-text', nodeId, text: '不能提交' }, { type: 'update-node', nodeId, patch: { tag: 'script' } }] } }, () => f.context, f.execute))
  assert.equal(f.calls(), 1)
  assert.equal(f.context.document.pages[0]!.root[0]!.text, '真实修改')
})
test('manual edits and page/document/context switches reject stale calls without mutation', async () => {
  for (const patch of [{ revision: 'new-edit' }, { contextKey: 'new-theme' }, { pageId: 'new-page' }, { documentId: 'other-doc' }]) {
    const f = fixture()
    const args = { ...f.args, expectedRevision: 'local-1', command: { type: 'set-text', nodeId: f.context.selectedNodeId, text: '旧结果' }, ...('documentId' in patch ? patch : {}) }
    if (typeof patch.revision === 'string') {
      f.context.revision = patch.revision
    }
    if (typeof patch.contextKey === 'string') {
      f.context.contextKey = patch.contextKey
    }
    if (typeof patch.pageId === 'string') {
      f.context.pageId = patch.pageId
    }
    await assert.rejects(executeStudioAgentTool('cms.studio.apply', args, () => f.context, f.execute))
    assert.equal(f.calls(), 0)
  }
})
test('executor rejects HTML/script/selectors, patch IDs, nested batch and unknown names', async () => {
  for (const command of [{
    type: 'html', html: '<script/>' }, { type: 'set-text', nodeId: 'id', text: 'x', script: 'x' }, { type: 'update-node', nodeId: 'id', patch: { id: 'changed' } }, { type: 'batch', commands: [{ type: 'batch', commands: [] }] }]) { assert.throws(() => parseStudioAgentCommand(command))
  }
  const f = fixture()
  await assert.rejects(executeStudioAgentTool('cms.studio.execute', f.args, () => f.context, f.execute))
  await assert.rejects(executeStudioAgentTool('cms.studio.read', { ...f.args, selector: '*' }, () => f.context, f.execute))
  await assert.rejects(executeStudioAgentTool('cms.studio.read', { ...f.args, limit: 1000 }, () => f.context, f.execute))
})
