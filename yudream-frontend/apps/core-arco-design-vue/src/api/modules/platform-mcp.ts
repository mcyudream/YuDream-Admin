import type { ApiResponse } from './system-client'
import systemClient from './system-client'

/** 请求头回显掩码：编辑时保持该值即保留服务端已保存的凭据。 */
export const MCP_HEADER_MASK = '••••••'

export type McpTransport = 'STREAMABLE_HTTP'
export type McpConnectionStatus = 'UNKNOWN' | 'CONNECTED' | 'ERROR'

export interface McpTool {
  name: string
  title: string
  description?: string
  risk?: 'READ' | 'WRITE'
  requiresConfirmation?: boolean
  /** 桥接后的平台工具编码：mcp.<serverCode>.<toolName> */
  platformToolCode?: string
}

export interface McpServer {
  id: string
  code: string
  name: string
  description?: string
  transport: McpTransport
  endpointUrl: string
  headers?: Record<string, string>
  enabled: boolean
  /** 常驻模块：开启后工具无需 load_tools 加载 */
  preload: boolean
  timeoutMillis: number
  allowPrivateNetwork: boolean
  lastStatus: McpConnectionStatus
  lastError?: string
  lastConnectedAt?: string
  protocolVersion?: string
  serverName?: string
  serverVersion?: string
  tools: McpTool[]
  /** 对应系统工具模块编码：mcp:<serverCode> */
  moduleCode?: string
  moduleEnabled?: boolean
  updateTime?: string
}

export interface McpServerPayload {
  name: string
  code: string
  description?: string
  transport: McpTransport
  endpointUrl: string
  headers?: Record<string, string>
  enabled: boolean
  preload: boolean
  timeoutMillis: number
  allowPrivateNetwork: boolean
}

export default {
  list: () => systemClient.get<unknown, ApiResponse<McpServer[]>>('api/platform/mcp/servers'),
  create: (data: McpServerPayload) => systemClient.post<unknown, ApiResponse<McpServer>>('api/platform/mcp/servers', data),
  update: (id: string, data: McpServerPayload) => systemClient.put<unknown, ApiResponse<McpServer>>(`api/platform/mcp/servers/${id}`, data),
  remove: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/mcp/servers/${id}`),
  test: (id: string) => systemClient.post<unknown, ApiResponse<McpServer>>(`api/platform/mcp/servers/${id}/test`),
}
