import type { AgentNodeData } from '../components/types'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { deriveAgentApplicationToolCodes, migrateLegacyToolNodes } from './agent-workflow-tools'

function node(id: string, kind: AgentNodeData['kind'], data: Partial<AgentNodeData> = {}) {
  return { id, data: { kind, title: id, toolCodes: [], ...data } }
}

test('derives an ordered unique application tool authorization union', () => {
  const toolCodes = deriveAgentApplicationToolCodes([
    node('build', 'llm', { toolCodes: ['cms.canvas.patch', 'web.fetch', 'cms.canvas.patch'] }),
    node('classify', 'classify', { toolCodes: ['knowledge.lookup'] }),
    node('legacy', 'tool', { toolCode: 'web.fetch' }),
    node('embedding', 'embedding', { toolCodes: ['ignored.by.embedding'] }),
  ])

  assert.deepEqual(toolCodes, ['cms.canvas.patch', 'web.fetch', 'knowledge.lookup'])
})

test('migrates an unambiguous legacy tool node into its direct chat-model predecessor', () => {
  const input = {
    nodes: [
      node('start', 'start'),
      node('build', 'llm', { toolCodes: ['web.fetch'] }),
      node('legacy-tool', 'tool', { toolCode: 'cms.canvas.patch' }),
      node('end', 'end'),
    ],
    edges: [
      { id: 'start-build', source: 'start', target: 'build' },
      { id: 'build-tool', source: 'build', target: 'legacy-tool', sourceHandle: 'source' },
      { id: 'tool-end', source: 'legacy-tool', target: 'end', targetHandle: 'input', data: { connectionStyle: 'arrow' } },
    ],
  }

  const migrated = migrateLegacyToolNodes(input)

  assert.deepEqual(migrated.nodes.map(item => item.id), ['start', 'build', 'end'])
  assert.deepEqual(migrated.nodes.find(item => item.id === 'build')?.data?.toolCodes, ['web.fetch', 'cms.canvas.patch'])
  assert.deepEqual(migrated.edges, [
    { id: 'start-build', source: 'start', target: 'build' },
    { id: 'tool-end', source: 'build', target: 'end', sourceHandle: 'source', targetHandle: 'input', data: { connectionStyle: 'arrow' } },
  ])
  assert.deepEqual(input.nodes.find(item => item.id === 'build')?.data.toolCodes, ['web.fetch'])
  assert.equal(migrated.nodes.find(item => item.id === 'build')?.data?.toolCodes === input.nodes.find(item => item.id === 'build')?.data.toolCodes, false)
})

test('keeps an ambiguous legacy tool graph unchanged without mutating the input', () => {
  const input = {
    nodes: [
      node('first', 'llm'),
      node('second', 'llm'),
      node('legacy-tool', 'tool', { toolCode: 'cms.canvas.patch' }),
      node('end', 'end'),
    ],
    edges: [
      { id: 'first-tool', source: 'first', target: 'legacy-tool' },
      { id: 'second-tool', source: 'second', target: 'legacy-tool' },
      { id: 'tool-end', source: 'legacy-tool', target: 'end' },
    ],
  }
  const original = JSON.parse(JSON.stringify(input)) as typeof input

  const migrated = migrateLegacyToolNodes(input)

  assert.deepEqual(input, original)
  assert.deepEqual(migrated, original)
  assert.notEqual(migrated.nodes, input.nodes)
  assert.notEqual(migrated.edges, input.edges)
  assert.notEqual(migrated.nodes[0]?.data, input.nodes[0]?.data)
})
