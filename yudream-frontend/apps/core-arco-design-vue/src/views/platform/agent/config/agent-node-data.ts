import type { AgentNodeData, AgentNodeKind, AgentNodeTemplate, AgentToolMode } from '../components/types'

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
  llm: { input: 'query', output: 'answer' },
  extract: { input: 'query', output: 'data' },
  classify: { input: 'query', output: 'classification' },
  vision: { input: 'query', output: 'answer' },
  embedding: { input: 'query', output: 'vector' },
  // Compatibility tool nodes should consume their direct predecessor by default.
  // A nonexistent `arguments` variable makes complex legacy graphs fail on load.
  tool: { input: '', output: 'result' },
}

export function agentModelKind(kind: AgentNodeKind) {
  if (isAgentChatModelNode(kind)) {
    return 'chat'
  }
  return kind === 'embedding' || kind === 'rerank' ? kind : ''
}

export function isAgentChatModelNode(kind: AgentNodeKind) {
  return kind === 'llm' || kind === 'extract' || kind === 'classify' || kind === 'vision' || kind === 'understand'
}

export function agentSourceHandles(kind: AgentNodeKind) {
  if (kind === 'end') {
    return []
  }
  return kind === 'condition' ? ['true', 'false'] : ['source']
}

export function createAgentNodeData(template: AgentNodeTemplate, overrides: Partial<AgentNodeData> = {}): AgentNodeData {
  const variables = variableDefaults[template.kind]
  const toolCodes = Array.isArray(overrides.toolCodes) ? [...overrides.toolCodes] : []
  const classes = Array.isArray(overrides.classes) ? [...overrides.classes] : []
  return {
    ...template,
    title: template.label,
    prompt: '',
    toolMode: 'NONE',
    toolConfigDeclared: isAgentToolConfigModelNode(template.kind),
    toolCode: '',
    outputSchema: '',
    imageVariable: '',
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
    toolCodes,
    classes,
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
  normalized.toolCodes = strings(raw.toolCodes ?? defaults.toolCodes)
  normalized.classes = strings(raw.classes ?? defaults.classes)
  normalized.toolMode = toolMode(raw.toolMode ?? defaults.toolMode)
  // Workflow data created before model-native tools has no declaration marker.
  // Keep it distinct from a user intentionally selecting NONE with no tools.
  normalized.toolConfigDeclared = declaresAgentNodeToolConfig({ ...raw, kind: raw.kind || template.kind })
  normalized.outputSchema = text(raw.outputSchema ?? defaults.outputSchema)
  normalized.imageVariable = text(raw.imageVariable ?? defaults.imageVariable)
  return normalized
}

export function isAgentToolConfigModelNode(kind: AgentNodeKind) {
  return kind !== 'understand' && isAgentChatModelNode(kind)
}

export function declaresAgentNodeToolConfig(data: Partial<AgentNodeData>) {
  if (!data.kind || !isAgentToolConfigModelNode(data.kind)) {
    return false
  }
  if (data.toolConfigDeclared === true) {
    return true
  }
  if (Array.isArray(data.toolCodes) && data.toolCodes.some(code => typeof code === 'string' && Boolean(code.trim()))) {
    return true
  }
  const mode = typeof data.toolMode === 'string' ? data.toolMode.trim().toUpperCase() : ''
  return mode === 'AUTO' || mode === 'ACTIVE' || mode === 'REQUIRED'
}

function strings(value: unknown) {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string').map(item => item.trim()).filter(Boolean)
    : []
}

function toolMode(value: unknown): AgentToolMode {
  return value === 'AUTO' || value === 'ACTIVE' || value === 'REQUIRED'
    ? value === 'ACTIVE' ? 'AUTO' : value
    : 'NONE'
}

function text(value: unknown) {
  return typeof value === 'string' ? value : ''
}
