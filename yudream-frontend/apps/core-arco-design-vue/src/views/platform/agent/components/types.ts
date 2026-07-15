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
    | 'embedding'
    | 'tool'

export type AgentConnectionStyle = 'arrow' | 'line'

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
  toolCode: string
  condition: string
  providerCode: string
  modelCode: string
  vision: boolean
  acceptFiles: boolean
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
