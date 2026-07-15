import type { AgentNodeData } from '../components/types'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { deriveAgentApplicationToolCodes, migrateLegacyToolNodes } from './agent-workflow-tools'

function node(id: string, kind: AgentNodeData['kind'], data: Partial<AgentNodeData> = {}) {
  return { id, data: { kind, title: id, toolCodes: [], toolConfigDeclared: false, ...data } }
}

test('derives an ordered unique application tool authorization union', () => {
  const toolCodes = deriveAgentApplicationToolCodes([
    node('build', 'llm', { toolConfigDeclared: true, toolCodes: ['cms.canvas.patch', 'web.fetch', 'cms.canvas.patch'] }),
    node('classify', 'classify', { toolConfigDeclared: true, toolCodes: ['knowledge.lookup'] }),
    node('legacy', 'tool', { toolCode: 'web.fetch' }),
    node('embedding', 'embedding', { toolCodes: ['ignored.by.embedding'] }),
    node('undeclared', 'llm', { toolCodes: ['ignored.by.legacy.compatibility'] }),
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
  assert.equal(migrated.nodes.find(item => item.id === 'build')?.data?.toolMode, 'AUTO')
  assert.equal(migrated.nodes.find(item => item.id === 'build')?.data?.toolConfigDeclared, true)
  assert.deepEqual(migrated.edges, [
    { id: 'start-build', source: 'start', target: 'build' },
    { id: 'tool-end', source: 'build', target: 'end', sourceHandle: 'source', targetHandle: 'input', data: { connectionStyle: 'arrow' } },
  ])
  assert.deepEqual(input.nodes.find(item => item.id === 'build')?.data.toolCodes, ['web.fetch'])
  assert.equal(migrated.nodes.find(item => item.id === 'build')?.data?.toolCodes === input.nodes.find(item => item.id === 'build')?.data.toolCodes, false)
})

test('upgrades NONE tool mode and preserves configured tool modes during legacy migration', () => {
  const input = {
    nodes: [
      node('none', 'llm', { toolMode: 'NONE' }),
      node('none-tool', 'tool', { toolCode: 'tool.none' }),
      node('auto', 'llm', { toolMode: 'AUTO' }),
      node('auto-tool', 'tool', { toolCode: 'tool.auto' }),
      node('required', 'llm', { toolMode: 'REQUIRED' }),
      node('required-tool', 'tool', { toolCode: 'tool.required' }),
      node('end', 'end'),
    ],
    edges: [
      { source: 'none', target: 'none-tool' },
      { source: 'none-tool', target: 'end' },
      { source: 'auto', target: 'auto-tool' },
      { source: 'auto-tool', target: 'end' },
      { source: 'required', target: 'required-tool' },
      { source: 'required-tool', target: 'end' },
    ],
  }

  const migrated = migrateLegacyToolNodes(input)

  assert.equal(migrated.nodes.find(item => item.id === 'none')?.data?.toolMode, 'AUTO')
  assert.equal(migrated.nodes.find(item => item.id === 'auto')?.data?.toolMode, 'AUTO')
  assert.equal(migrated.nodes.find(item => item.id === 'required')?.data?.toolMode, 'REQUIRED')
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

test('keeps a legacy tool node when its predecessor model also has another outgoing branch', () => {
  const input = {
    nodes: [
      node('build', 'llm'),
      node('legacy-tool', 'tool', { toolCode: 'cms.canvas.patch' }),
      node('audit', 'template'),
      node('end', 'end'),
    ],
    edges: [
      { id: 'build-tool', source: 'build', target: 'legacy-tool' },
      { id: 'build-audit', source: 'build', target: 'audit' },
      { id: 'tool-end', source: 'legacy-tool', target: 'end' },
    ],
  }

  assert.deepEqual(migrateLegacyToolNodes(input), input)
})
