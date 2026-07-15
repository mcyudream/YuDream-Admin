import type { AgentNodeData } from '../components/types'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { migrateConditionSourceHandle, validateAgentWorkflow } from './agent-workflow-validation'

function node(id: string, kind: AgentNodeData['kind'], data: Partial<AgentNodeData> = {}) {
  return { id, data: { kind, title: id, ...data } as AgentNodeData }
}

test('发布校验会指出缺失的条件分支和节点配置', () => {
  const result = validateAgentWorkflow(
    [
      node('start', 'start'),
      node('condition', 'condition', { condition: '' }),
      node('end', 'end'),
    ],
    [
      { source: 'start', target: 'condition' },
      { source: 'condition', target: 'end', sourceHandle: 'true' },
    ],
    { models: [], knowledgeSpaceSlugs: new Set(), toolCodes: new Set() },
  )

  assert.equal(result.valid, false)
  assert.equal(result.issues[0]?.nodeId, 'condition')
  assert.match(result.issues.map(item => item.message).join('\n'), /条件表达式/)
  assert.match(result.issues.map(item => item.message).join('\n'), /true 和 false/)
})

test('发布校验要求节点引用已配置的模型知识空间和工具', () => {
  const result = validateAgentWorkflow(
    [
      node('start', 'start'),
      node('llm', 'llm', { providerCode: 'openai', modelCode: 'gpt-5' }),
      node('tool', 'tool', { toolCode: 'wiki.search' }),
      node('end', 'end'),
    ],
    [
      { source: 'start', target: 'llm' },
      { source: 'llm', target: 'tool' },
      { source: 'tool', target: 'end' },
    ],
    { models: [], knowledgeSpaceSlugs: new Set(), toolCodes: new Set() },
  )

  assert.match(result.issues.map(item => item.message).join('\n'), /模型未配置/)
  assert.match(result.issues.map(item => item.message).join('\n'), /工具不可用/)
})

test('旧条件边会从标签或 data.branch 迁移为标准句柄', () => {
  assert.equal(migrateConditionSourceHandle('condition', 'source', 'true', undefined), 'true')
  assert.equal(migrateConditionSourceHandle('condition', 'source', undefined, { branch: 'false' }), 'false')
  assert.equal(migrateConditionSourceHandle('llm', 'source', 'true', undefined), 'source')
  assert.equal(migrateConditionSourceHandle('condition', 'source', 'maybe', undefined), 'source')
})

test('完整工作流可以发布', () => {
  const result = validateAgentWorkflow(
    [
      node('start', 'start'),
      node('llm', 'llm', { providerCode: 'openai', modelCode: 'gpt-5' }),
      node('end', 'end'),
    ],
    [
      { source: 'start', target: 'llm' },
      { source: 'llm', target: 'end' },
    ],
    {
      models: [{ providerCode: 'openai', modelCode: 'gpt-5', kind: 'chat', configured: true }],
      knowledgeSpaceSlugs: new Set(),
      toolCodes: new Set(),
    },
  )

  assert.equal(result.valid, true)
  assert.deepEqual(result.issues, [])
})

test('chat model nodes validate tool modes and available tools', () => {
  const result = validateAgentWorkflow(
    [
      node('start', 'start'),
      node('none', 'llm', { providerCode: 'openai', modelCode: 'gpt-5', toolMode: 'NONE', toolCodes: ['wiki.search'] }),
      node('auto', 'llm', { providerCode: 'openai', modelCode: 'gpt-5', toolMode: 'AUTO', toolCodes: [] }),
      node('required', 'llm', { providerCode: 'openai', modelCode: 'gpt-5', toolMode: 'REQUIRED', toolCodes: ['missing.tool'] }),
      node('end', 'end'),
    ],
    [
      { source: 'start', target: 'none' },
      { source: 'none', target: 'auto' },
      { source: 'auto', target: 'required' },
      { source: 'required', target: 'end' },
    ],
    {
      models: [{ providerCode: 'openai', modelCode: 'gpt-5', kind: 'chat', configured: true, vision: false }],
      knowledgeSpaceSlugs: new Set(),
      toolCodes: new Set(['wiki.search']),
    },
  )

  assert.equal(result.valid, false)
  assert.deepEqual(new Set(result.issues.map(issue => issue.nodeId)), new Set(['none', 'auto', 'required']))
})

test('semantic model nodes validate vision capability, extract schema, and classify labels', () => {
  const result = validateAgentWorkflow(
    [
      node('start', 'start'),
      node('vision', 'vision', { providerCode: 'openai', modelCode: 'gpt-5', imageVariable: 'upload.image' }),
      node('extract', 'extract', { providerCode: 'openai', modelCode: 'gpt-5', outputSchema: '[]' }),
      node('classify', 'classify', { providerCode: 'openai', modelCode: 'gpt-5', classes: ['support', ' support '] }),
      node('end', 'end'),
    ],
    [
      { source: 'start', target: 'vision' },
      { source: 'vision', target: 'extract' },
      { source: 'extract', target: 'classify' },
      { source: 'classify', target: 'end' },
    ],
    {
      models: [{ providerCode: 'openai', modelCode: 'gpt-5', kind: 'chat', configured: true, vision: false }],
      knowledgeSpaceSlugs: new Set(),
      toolCodes: new Set(),
    },
  )

  assert.equal(result.valid, false)
  assert.deepEqual(new Set(result.issues.map(issue => issue.nodeId)), new Set(['vision', 'extract', 'classify']))
})

test('extract accepts a JSON object schema and classify accepts distinct labels', () => {
  const result = validateAgentWorkflow(
    [
      node('start', 'start'),
      node('extract', 'extract', { providerCode: 'openai', modelCode: 'gpt-5', outputSchema: '{"type":"object","properties":{"name":{"type":"string"}}}' }),
      node('classify', 'classify', { providerCode: 'openai', modelCode: 'gpt-5', classes: ['support', 'sales'] }),
      node('end', 'end'),
    ],
    [
      { source: 'start', target: 'extract' },
      { source: 'extract', target: 'classify' },
      { source: 'classify', target: 'end' },
    ],
    {
      models: [{ providerCode: 'openai', modelCode: 'gpt-5', kind: 'chat', configured: true, vision: true }],
      knowledgeSpaceSlugs: new Set(),
      toolCodes: new Set(),
    },
  )

  assert.equal(result.valid, true)
})
