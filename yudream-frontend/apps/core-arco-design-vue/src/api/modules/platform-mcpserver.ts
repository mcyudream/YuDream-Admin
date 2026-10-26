import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface McpServerTool {
  name: string
  title: string
  description: string
  /** 调用该工具所需的权限码（空 = 登录即可），引导管理员在 API Key 中勾选 */
  permissionCode: string
  /** true = 写操作（启停/写文件/改配置/备份等），凭 API Key 授权的权限码放行 */
  write: boolean
}

export interface McpServerModule {
  code: string
  displayName: string
  /** 模块端点路径（站内相对路径，展示时拼站点 origin） */
  endpoint: string
  toolCount: number
  tools: McpServerTool[]
}

export interface McpServerOverview {
  enabled: boolean
  endpointFull: string
  toolCount: number
  modules: McpServerModule[]
}

export default {
  overview: () => systemClient.get<unknown, ApiResponse<McpServerOverview>>('api/platform/mcpserver/overview'),
}
