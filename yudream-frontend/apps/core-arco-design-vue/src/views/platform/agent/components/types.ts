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
}
