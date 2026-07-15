import type { AgentNodeTemplate } from '../components/types'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { agentModelKind, agentSourceHandles, createAgentNodeData, normalizeAgentNodeData } from './agent-node-data'

const template: AgentNodeTemplate = {
  kind: 'search',
  label: '知识检索',
  icon: 'i-ri:book-open-line',
  color: '#7c3aed',
  description: '从知识库检索相关内容',
  inputName: 'str.query',
  outputName: 'Array<Document>',
}

test('新建节点会生成可直接序列化的完整默认配置', () => {
  const data = createAgentNodeData(template)

  assert.equal(data.title, '知识检索')
  assert.equal(data.inputVariable, 'query')
  assert.equal(data.outputVariable, 'documents')
  assert.equal(data.knowledgeSpaceSlug, '')
  assert.equal(data.topK, 5)
  assert.equal(data.pathPrefix, '')
  assert.equal(data.graphExpansion, false)
  assert.equal(data.documentInput, 'attachment')
  assert.equal(data.documentMode, 'text')
  assert.equal(data.citationSource, 'documents')
  assert.equal(data.citationFormat, 'markdown')
})

test('旧代码与模板节点的 prompt 会迁移到专用字段', () => {
  const codeTemplate = { ...template, kind: 'code' as const, label: '代码执行' }
  const templateTemplate = { ...template, kind: 'template' as const, label: '模板转换' }

  const code = normalizeAgentNodeData(codeTemplate, { prompt: 'print(input)' })
  const textTemplate = normalizeAgentNodeData(templateTemplate, { prompt: '你好，$name$' })

  assert.equal(code.code, 'print(input)')
  assert.equal(textTemplate.template, '你好，$name$')
})

test('已保存配置在归一化时保持原值', () => {
  const data = normalizeAgentNodeData(template, {
    title: '制度检索',
    inputVariable: 'question',
    outputVariable: 'hits',
    knowledgeSpaceSlug: 'company-policy',
    topK: 12,
    pathPrefix: '/hr',
    graphExpansion: true,
  })

  assert.equal(data.title, '制度检索')
  assert.equal(data.inputVariable, 'question')
  assert.equal(data.outputVariable, 'hits')
  assert.equal(data.knowledgeSpaceSlug, 'company-policy')
  assert.equal(data.topK, 12)
  assert.equal(data.pathPrefix, '/hr')
  assert.equal(data.graphExpansion, true)
})

test('旧模型节点的空模型字段会使用对应类型的默认模型', () => {
  const llmTemplate = { ...template, kind: 'llm' as const, label: '大模型' }
  const data = normalizeAgentNodeData(llmTemplate, {
    providerCode: '',
    modelCode: '',
  }, {
    providerCode: 'openai',
    modelCode: 'gpt-5-mini',
  })

  assert.equal(data.providerCode, 'openai')
  assert.equal(data.modelCode, 'gpt-5-mini')
})

test('需要模型的节点会映射到正确的模型类型', () => {
  assert.equal(agentModelKind('llm'), 'chat')
  assert.equal(agentModelKind('understand'), 'chat')
  assert.equal(agentModelKind('embedding'), 'embedding')
  assert.equal(agentModelKind('rerank'), 'rerank')
  assert.equal(agentModelKind('search'), '')
})

test('condition nodes expose true and false source handles', () => {
  assert.deepEqual(agentSourceHandles('condition'), ['true', 'false'])
  assert.deepEqual(agentSourceHandles('llm'), ['source'])
  assert.deepEqual(agentSourceHandles('end'), [])
})

test('聊天模型节点使用独立的工具、结构化输出和视觉输入默认配置', () => {
  const llmTemplate = { ...template, kind: 'llm' as const, label: '文本生成' }
  const extractTemplate = { ...template, kind: 'extract' as const, label: '结构化提取' }
  const classifyTemplate = { ...template, kind: 'classify' as const, label: '意图分类' }
  const visionTemplate = { ...template, kind: 'vision' as const, label: '视觉理解' }
  const first = createAgentNodeData(llmTemplate)
  const second = createAgentNodeData(llmTemplate)

  assert.equal(first.inputVariable, 'query')
  assert.equal(first.toolMode, 'NONE')
  assert.equal(first.toolConfigDeclared, true)
  assert.deepEqual(first.toolCodes, [])
  assert.notEqual(first.toolCodes, second.toolCodes)
  assert.equal(createAgentNodeData(extractTemplate).outputSchema, '')
  assert.deepEqual(createAgentNodeData(classifyTemplate).classes, [])
  assert.equal(createAgentNodeData(visionTemplate).inputVariable, 'query')
  assert.equal(createAgentNodeData(visionTemplate).imageVariable, '')
})

test('历史模型节点未声明工具配置时保持兼容状态，显式声明才覆盖应用级授权', () => {
  const llmTemplate = { ...template, kind: 'llm' as const, label: '文本生成' }
  const understandTemplate = { ...template, kind: 'understand' as const, label: '问题理解' }

  assert.equal(normalizeAgentNodeData(llmTemplate, { toolMode: 'NONE', toolCodes: [] }).toolConfigDeclared, false)
  assert.equal(normalizeAgentNodeData(llmTemplate, { toolConfigDeclared: true, toolMode: 'NONE', toolCodes: [] }).toolConfigDeclared, true)
  assert.equal(normalizeAgentNodeData(understandTemplate, { toolConfigDeclared: true }).toolConfigDeclared, false)
})

test('所有聊天模型语义映射到 chat 模型，Embedding 和 Rerank 保持各自类型', () => {
  assert.equal(agentModelKind('llm'), 'chat')
  assert.equal(agentModelKind('extract'), 'chat')
  assert.equal(agentModelKind('classify'), 'chat')
  assert.equal(agentModelKind('vision'), 'chat')
  assert.equal(agentModelKind('understand'), 'chat')
  assert.equal(agentModelKind('embedding'), 'embedding')
  assert.equal(agentModelKind('rerank'), 'rerank')
})

test('归一化保留历史 understand 和 tool 节点类型及其原始数据', () => {
  const understandTemplate = { ...template, kind: 'understand' as const, label: '问题理解' }
  const toolTemplate = { ...template, kind: 'tool' as const, label: '工具调用' }

  const understand = normalizeAgentNodeData(understandTemplate, { prompt: '提取用户意图', inputVariable: 'query' })
  const tool = normalizeAgentNodeData(toolTemplate, { toolCode: 'cms.canvas.patch', inputVariable: 'task' })

  assert.equal(understand.kind, 'understand')
  assert.equal(understand.prompt, '提取用户意图')
  assert.equal(tool.kind, 'tool')
  assert.equal(tool.toolCode, 'cms.canvas.patch')
  assert.equal(tool.inputVariable, 'task')
})
