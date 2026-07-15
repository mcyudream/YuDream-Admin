import type { ApiResponse, PageResult } from './system-client'
import { prepareApiEncryption } from '@/utils/api-encryption'
import systemClient from './system-client'

export type AgentApplicationStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface AgentApplication {
  id: string
  name: string
  code: string
  description?: string
  icon?: string
  systemPrompt?: string
  workflowJson: string
  toolCodes: string[]
  status: AgentApplicationStatus
  createTime?: string
  updateTime?: string
}

export interface AgentApplicationPayload {
  name: string
  code: string
  description?: string
  icon?: string
  systemPrompt?: string
  workflowJson: string
  toolCodes: string[]
  status: AgentApplicationStatus
}

export interface AgentTool {
  id: string
  name: string
  code: string
  description?: string
  type: 'PYTHON'
  inputSchemaJson?: string
  outputExampleJson?: string
  pythonCode: string
  timeoutMillis: number
  permissionCode?: string
  enabled: boolean
  updateTime?: string
}

export interface SystemAgentTool {
  code: string
  name: string
  description?: string
  permissionCode?: string
  inputSchema?: Record<string, unknown>
}

export interface AgentRunResult {
  content: string
  toolResults: Array<{ toolName: string, action: string, message: string, payload?: Record<string, unknown> }>
}

export interface AgentRunAttachment {
  name: string
  contentType: string
  size: number
  dataUrl: string
}

export interface AgentModelOption {
  providerCode: string
  providerName: string
  modelCode: string
  modelName: string
  kind: string
  vision: boolean
  configured: boolean
  defaultModel: boolean
}

export interface AgentKnowledgeSpaceOption {
  slug: string
  name: string
  topK?: number
  graphEnabled?: boolean
  embeddingProviderCode?: string
  embeddingModelCode?: string
}

export interface AgentCatalog {
  knowledgeSpaces: AgentKnowledgeSpaceOption[]
  models: AgentModelOption[]
}

export type AgentDebugNodeStatus = 'RUNNING' | 'COMPLETED' | 'SKIPPED' | 'FAILED'

export interface AgentDebugStreamEvent {
  type: 'RUN_STARTED' | 'NODE_RUNNING' | 'NODE_COMPLETED' | 'NODE_SKIPPED' | 'NODE_FAILED' | 'TEXT_MESSAGE_CHUNK' | 'TOOL_CALL_RESULT' | 'RUN_FINISHED' | 'RUN_ERROR'
  threadId?: string
  runId?: string
  timestamp?: number
  nodeId?: string
  nodeKind?: string
  nodeTitle?: string
  status?: AgentDebugNodeStatus
  message?: string
  delta?: string
  tool?: AgentRunResult['toolResults'][number]
  result?: AgentRunResult
}

export default {
  available: () => systemClient.get<unknown, ApiResponse<AgentApplication[]>>('api/platform/agents/available'),
  runtimeDetail: (code: string) => systemClient.get<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/runtime/${encodeURIComponent(code)}`),
  page: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AgentApplication>>>('api/platform/agents', { params }),
  detail: (id: string) => systemClient.get<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/${id}`),
  create: (data: AgentApplicationPayload) => systemClient.post<unknown, ApiResponse<AgentApplication>>('api/platform/agents', data),
  update: (id: string, data: AgentApplicationPayload) => systemClient.put<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/${id}`, data),
  publish: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/agents/${id}/publish`),
  delete: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agents/${id}`),
  run: (id: string, data: { input: string, providerCode?: string, modelCode?: string, imageDataUrl?: string, attachments?: AgentRunAttachment[] }) => systemClient.post<unknown, ApiResponse<AgentRunResult>>(`api/platform/agents/${id}/run`, data),
  debugStreamEndpoint: (id: string) => streamEndpoint(`/api/platform/agents/${id}/debug/stream`),
  debugStreamRequest: async (id: string, data: { input: string, providerCode?: string, modelCode?: string, imageDataUrl?: string, attachments?: AgentRunAttachment[] }): Promise<RequestInit> => {
    const headers: Record<string, string> = {
      'Accept-Language': 'zh-CN',
      'Content-Type': 'application/json',
    }
    const token = localStorage.getItem('token')
    if (token) {
      headers.Authorization = token
    }
    let body: unknown = data
    const encrypted = await prepareApiEncryption(`api/platform/agents/${id}/debug/stream`, data)
    if (encrypted) {
      Object.assign(headers, encrypted.headers)
      body = encrypted.body
    }
    return { method: 'POST', headers, body: JSON.stringify(body) }
  },
  pageTools: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AgentTool>>>('api/platform/agents/tools', { params }),
  catalog: () => systemClient.get<unknown, ApiResponse<AgentCatalog>>('api/platform/agents/catalog'),
  models: () => systemClient.get<unknown, ApiResponse<AgentModelOption[]>>('api/platform/agents/models'),
  systemTools: () => systemClient.get<unknown, ApiResponse<SystemAgentTool[]>>('api/platform/agents/tools/system'),
  createTool: (data: Omit<AgentTool, 'id' | 'type' | 'updateTime'>) => systemClient.post<unknown, ApiResponse<AgentTool>>('api/platform/agents/tools', data),
  updateTool: (id: string, data: Omit<AgentTool, 'id' | 'type' | 'updateTime'>) => systemClient.put<unknown, ApiResponse<AgentTool>>(`api/platform/agents/tools/${id}`, data),
  deleteTool: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agents/tools/${id}`),
}

function streamEndpoint(path: string) {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) {
    return `/proxy${path}`
  }
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${path}`
}
