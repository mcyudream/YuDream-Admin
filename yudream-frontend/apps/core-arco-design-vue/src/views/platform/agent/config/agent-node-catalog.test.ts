import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { agentLegacyNodeTemplates, agentNodePaletteGroups, agentNodeTemplates, findAgentNodeTemplate } from './agent-node-catalog'

test('Agent 节点目录按输入、模型、知识、逻辑、转换和输出六类展示', () => {
  assert.deepEqual(agentNodePaletteGroups.map(group => group.title), ['输入', '模型', '知识', '逻辑', '转换', '输出'])
  assert.deepEqual(agentNodePaletteGroups[1]?.items.map(item => item.kind), [
    'llm',
    'extract',
    'classify',
    'vision',
    'embedding',
    'rerank',
  ])
})

test('新建面板只暴露实际语义节点，不暴露旧理解和独立工具节点', () => {
  const paletteKinds = agentNodeTemplates.map(item => item.kind)

  assert.equal(paletteKinds.includes('understand'), false)
  assert.equal(paletteKinds.includes('tool'), false)
  assert.equal(paletteKinds.includes('extract'), true)
  assert.equal(paletteKinds.includes('classify'), true)
  assert.equal(paletteKinds.includes('vision'), true)
})

test('旧 understand 和 tool 节点仍有加载模板，但不进入新建目录', () => {
  assert.deepEqual(agentLegacyNodeTemplates.map(item => item.kind), ['understand', 'tool'])
  assert.equal(findAgentNodeTemplate('understand')?.label, '问题理解')
  assert.equal(findAgentNodeTemplate('tool')?.label, '工具调用')
  assert.equal(findAgentNodeTemplate('not-a-node' as never), undefined)
})
