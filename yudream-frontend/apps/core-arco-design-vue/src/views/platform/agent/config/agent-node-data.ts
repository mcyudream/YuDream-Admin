import type { AgentNodeData, AgentNodeKind, AgentNodeTemplate } from '../components/types'

const variableDefaults: Record<AgentNodeKind, { input: string, output: string }> = {
  input: { input: 'request', output: 'query' },
  start: { input: 'input', output: 'query' },
  end: { input: 'answer', output: 'result' },
  understand: { input: 'query', output: 'intent' },
  condition: { input: 'input', output: 'matched' },
  code: { input: 'input', output: 'result' },
  template: { input: 'input', output: 'text' },
  search: { input: 'query', output: 'documents' },
  vector: { input: 'query', output: 'documents' },
  rerank: { input: 'documents', output: 'documents' },
  document: { input: 'attachment', output: 'document' },
  citation: { input: 'answer', output: 'citations' },
  llm: { input: 'context', output: 'answer' },
  embedding: { input: 'text', output: 'vector' },
  tool: { input: 'arguments', output: 'result' },
}

export function agentModelKind(kind: AgentNodeKind) {
  if (kind === 'llm' || kind === 'understand') {
    return 'chat'
  }
  return kind === 'embedding' || kind === 'rerank' ? kind : ''
}

export function agentSourceHandles(kind: AgentNodeKind) {
  if (kind === 'end') {
    return []
  }
  return kind === 'condition' ? ['true', 'false'] : ['source']
}

export function createAgentNodeData(template: AgentNodeTemplate, overrides: Partial<AgentNodeData> = {}): AgentNodeData {
  const variables = variableDefaults[template.kind]
  return {
    ...template,
    title: template.label,
    prompt: '',
    toolCode: '',
    condition: '',
    code: '',
    template: '',
    providerCode: '',
    modelCode: '',
    vision: false,
    acceptFiles: false,
    inputVariable: variables.input,
    outputVariable: variables.output,
    knowledgeSpaceSlug: '',
    topK: 5,
    pathPrefix: '',
    graphExpansion: false,
    documentInput: 'attachment',
    documentMode: 'text',
    citationSource: 'documents',
    citationFormat: 'markdown',
    ...overrides,
  }
}

export function normalizeAgentNodeData(template: AgentNodeTemplate, raw: Partial<AgentNodeData> = {}, defaults: Partial<AgentNodeData> = {}): AgentNodeData {
  const normalized = createAgentNodeData(template, { ...defaults, ...raw })
  normalized.title = raw.title || raw.label || template.label
  normalized.providerCode = raw.providerCode || defaults.providerCode || ''
  normalized.modelCode = raw.modelCode || defaults.modelCode || ''
  if (template.kind === 'code' && !raw.code && raw.prompt) {
    normalized.code = raw.prompt
  }
  if (template.kind === 'template' && !raw.template && raw.prompt) {
    normalized.template = raw.prompt
  }
  return normalized
}
