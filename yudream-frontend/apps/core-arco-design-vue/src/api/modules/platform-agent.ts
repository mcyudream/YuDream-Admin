import type { ApiResponse, PageResult } from './system-client'
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
  toolResults: Array<{ toolName: string; action: string; message: string; payload?: Record<string, unknown> }>
}

export default {
  page: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AgentApplication>>>('api/platform/agents', { params }),
  detail: (id: string) => systemClient.get<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/${id}`),
  create: (data: AgentApplicationPayload) => systemClient.post<unknown, ApiResponse<AgentApplication>>('api/platform/agents', data),
  update: (id: string, data: AgentApplicationPayload) => systemClient.put<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/${id}`, data),
  publish: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/agents/${id}/publish`),
  delete: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agents/${id}`),
  run: (id: string, data: { input: string; providerCode?: string; modelCode?: string }) => systemClient.post<unknown, ApiResponse<AgentRunResult>>(`api/platform/agents/${id}/run`, data),
  pageTools: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AgentTool>>>('api/platform/agents/tools', { params }),
  systemTools: () => systemClient.get<unknown, ApiResponse<SystemAgentTool[]>>('api/platform/agents/tools/system'),
  createTool: (data: Omit<AgentTool, 'id' | 'type' | 'updateTime'>) => systemClient.post<unknown, ApiResponse<AgentTool>>('api/platform/agents/tools', data),
  updateTool: (id: string, data: Omit<AgentTool, 'id' | 'type' | 'updateTime'>) => systemClient.put<unknown, ApiResponse<AgentTool>>(`api/platform/agents/tools/${id}`, data),
  deleteTool: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agents/tools/${id}`),
}
