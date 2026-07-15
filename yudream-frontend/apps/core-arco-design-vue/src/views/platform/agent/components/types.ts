export type AgentNodeKind
  = | 'input'
    | 'start'
    | 'end'
    | 'understand'
    | 'condition'
    | 'code'
    | 'template'
    | 'search'
    | 'vector'
    | 'rerank'
    | 'document'
    | 'citation'
    | 'llm'
    | 'extract'
    | 'classify'
    | 'vision'
    | 'embedding'
    | 'tool'

export type AgentConnectionStyle = 'arrow' | 'line'
export type AgentToolMode = 'NONE' | 'AUTO' | 'REQUIRED'

export interface AgentNodeTemplate {
  kind: AgentNodeKind
  label: string
  icon: string
  color: string
  description: string
  inputName: string
  outputName: string
}

export interface AgentNodeData extends AgentNodeTemplate {
  title: string
  prompt: string
  toolCodes: string[]
  toolMode: AgentToolMode
  toolConfigDeclared: boolean
  toolCode: string
  outputSchema: string
  classes: string[]
  imageVariable: string
  condition: string
  code: string
  template: string
  providerCode: string
  modelCode: string
  vision: boolean
  acceptFiles: boolean
  inputVariable: string
  outputVariable: string
  knowledgeSpaceSlug: string
  topK: number
  pathPrefix: string
  graphExpansion: boolean
  documentInput: string
  documentMode: 'text'
  citationSource: string
  citationFormat: 'json' | 'markdown'
}

export type AgentDebugStatus = 'RUNNING' | 'COMPLETED' | 'SKIPPED' | 'FAILED'

export interface AgentDebugStep {
  nodeId: string
  nodeTitle: string
  nodeKind: string
  status: AgentDebugStatus
  message: string
}

export interface AgentDebugToolResult {
  toolName: string
  action?: string
  message?: string
}

export interface AgentDebugMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  status?: 'streaming' | 'completed' | 'failed' | 'cancelled'
  steps?: AgentDebugStep[]
  tools?: AgentDebugToolResult[]
  attachments?: AgentDebugAttachment[]
}

export interface AgentDebugAttachment {
  id: string
  name: string
  type: string
  size: number
  dataUrl?: string
  text?: string
}
