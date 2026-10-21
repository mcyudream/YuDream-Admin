import assert from 'node:assert/strict'
import { it } from 'node:test'
import { createStudioDocument, createStudioNode } from '../utils/site-studio.ts'
import { executeStudioAgentTool } from './use-site-studio-agent.ts'

it('read prioritizes the selected page and never traverses unrelated pages', async () => {
  const document = createStudioDocument()
  const other = createStudioNode('text')
  document.pages[0]!.root = [other]
  const selected = createStudioNode('text')
  const page = { ...document.pages[0]!, id: 'page-two', name: '第二页', path: '/two', root: [selected] }
  document.pages.push(page)
  const header = createStudioNode('text')
  document.theme.header = [header]
  document.theme.footer = []
  const context = { document, contextKey: 'test', pageId: page.id, revision: 'v1', selectedNodeId: '' }
  const read = await executeStudioAgentTool('cms.studio.read', { contextKey: 'test', documentId: document.id, pageId: page.id, limit: 100 }, () => context, () => assert.fail('read cannot mutate'))
  assert.deepEqual((read.nodes as { id: string }[]).map(node => node.id), [selected.id, header.id])
  assert.equal(read.total, 2)
  assert.equal(read.hasMore, false)
  assert.equal((read.document as { pages: unknown[] }).pages.length, 2)
})
