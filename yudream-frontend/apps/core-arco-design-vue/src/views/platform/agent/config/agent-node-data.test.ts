import type { AgentNodeTemplate } from '../components/types'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { agentModelKind, createAgentNodeData, normalizeAgentNodeData } from './agent-node-data'

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
