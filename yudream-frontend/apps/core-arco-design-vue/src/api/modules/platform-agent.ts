import type { AiRunSnapshot } from './platform-ai-runs'
import type { ApiResponse, PageResult } from './system-client'
import { prepareApiEncryption } from '@/utils/api-encryption'
import systemClient from './system-client'

export type AgentApplicationStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'
export type AgentWorkspaceScope = 'SHARED' | 'SCOPED'

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
  sourcePluginCode?: string
  /** 用户工作区可见范围：SHARED 整个工作区；SCOPED 只见 apps/<code>/ */
  workspaceScope?: AgentWorkspaceScope
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
  workspaceScope?: AgentWorkspaceScope
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

export interface AgentToolCandidate {
  code: string
  name: string
  description?: string
  permissionCode?: string
  source?: string
}

export interface AgentToolCandidateParams {
  page: number
  size: number
  keyword?: string
}

export interface SystemAgentTool extends AgentToolCandidate {
  inputSchema?: Record<string, unknown>
}

export interface AiToolModuleTool {
  code: string
  name: string
  description?: string
  permissionCode?: string
  risk?: 'READ' | 'WRITE'
  source?: string
  requiresConfirmation?: boolean
  enabled?: boolean
}

export interface AiToolModule {
  code: string
  name: string
  description?: string
  icon?: string
  source?: string
  /** 常驻模块：工具无需 load_tools 加载即进入模型视野 */
  alwaysOn?: boolean
  enabled?: boolean
  canDisable?: boolean
  /** 按需加载：需要模型先调用 load_tools 加载该模块 */
  lazy?: boolean
  toolCount?: number
  tools?: AiToolModuleTool[]
  /** 运行环境不可用的原因（如部署环境没有 Python），可用时为空 */
  unavailableReason?: string
}

export interface AiToolModuleCatalog {
  /** 系统工具总开关（配置项与持久开关同时开启才为 true） */
  systemEnabled: boolean
  /** 配置项闸门：部署侧 yudream.platform.agent.system-tools-enabled */
  configEnabled?: boolean
  /** 是否按需加载模式（eager 模式下所有模块常驻） */
  onDemand?: boolean
  modules: AiToolModule[]
}

export interface AiToolCatalogRow {
  /** Python 自定义工具主键（编辑/删除用）；其他来源为空 */
  toolId?: string
  code: string
  name: string
  description?: string
  /** 来源：system（宿主系统工具）/ plugin（插件）/ mcp（MCP 服务）/ python（自定义） */
  source: string
  moduleCode: string
  moduleName: string
  /** 模块开关（受系统工具总开关影响） */
  moduleEnabled: boolean
  /** 常驻模块：无需 load_tools 加载 */
  moduleAlwaysOn: boolean
  risk?: string
  requiresConfirmation?: boolean
  /** 工具自身启用状态：Python 工具可停用；系统/插件/MCP 跟随模块与服务开关 */
  enabled: boolean
  editable: boolean
  deletable: boolean
  testable: boolean
  permissionCode?: string
  /** 工具输入 schema，用于测试调用时预填参数 */
  inputSchema?: Record<string, unknown>
}

export interface AiToolTestResult {
  success: boolean
  message: string
  action?: string
  toolCode: string
  source?: string
  durationMillis: number
  payload?: Record<string, unknown>
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
  startDebugRun: (id: string, data: { input: string, providerCode?: string, modelCode?: string, imageDataUrl?: string, attachments?: AgentRunAttachment[] }) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/agents/${id}/debug/runs`, data),
  available: (config: { skipGlobalToast?: boolean } = {}) => systemClient.get<unknown, ApiResponse<AgentApplication[]>>('api/platform/agents/available', config),
  runtimeDetail: (code: string) => systemClient.get<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/runtime/${encodeURIComponent(code)}`),
  importRuntime: (code: string) => systemClient.post<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/runtime/${encodeURIComponent(code)}/import`),
  page: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AgentApplication>>>('api/platform/agents', { params }),
  detail: (id: string) => systemClient.get<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/${id}`),
  create: (data: AgentApplicationPayload) => systemClient.post<unknown, ApiResponse<AgentApplication>>('api/platform/agents', data),
  update: (id: string, data: AgentApplicationPayload) => systemClient.put<unknown, ApiResponse<AgentApplication>>(`api/platform/agents/${id}`, data),
  publish: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/agents/${id}/publish`),
  delete: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agents/${id}`),
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
  pageToolCandidates: (params: AgentToolCandidateParams) => systemClient.get<unknown, ApiResponse<PageResult<AgentToolCandidate>>>('api/platform/agents/tools/available', { params }),
  catalog: (config: { skipGlobalToast?: boolean } = {}) => systemClient.get<unknown, ApiResponse<AgentCatalog>>('api/platform/agents/catalog', config),
  models: () => systemClient.get<unknown, ApiResponse<AgentModelOption[]>>('api/platform/agents/models'),
  systemTools: () => systemClient.get<unknown, ApiResponse<SystemAgentTool[]>>('api/platform/agents/tools/system'),
  toolModules: () => systemClient.get<unknown, ApiResponse<AiToolModuleCatalog>>('api/platform/agents/tools/modules'),
  toolCatalog: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AiToolCatalogRow>>>('api/platform/agents/tools/catalog', { params }),
  testTool: (data: { code: string, arguments?: Record<string, unknown> }) => systemClient.post<unknown, ApiResponse<AiToolTestResult>>('api/platform/agents/tools/test', data),
  updateSystemToolsEnabled: (enabled: boolean) => systemClient.put<unknown, ApiResponse<AiToolModuleCatalog>>(`api/platform/agents/tools/system/enabled/${enabled}`),
  updateToolModuleEnabled: (code: string, enabled: boolean) => systemClient.put<unknown, ApiResponse<AiToolModuleCatalog>>(`api/platform/agents/tools/modules/${encodeURIComponent(code)}/enabled/${enabled}`),
  createTool: (data: Omit<AgentTool, 'id' | 'type' | 'updateTime'>) => systemClient.post<unknown, ApiResponse<AgentTool>>('api/platform/agents/tools', data),
  updateTool: (id: string, data: Omit<AgentTool, 'id' | 'type' | 'updateTime'>) => systemClient.put<unknown, ApiResponse<AgentTool>>(`api/platform/agents/tools/${id}`, data),
  deleteTool: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agents/tools/${id}`),
  toggleTool: (id: string, enabled: boolean) => systemClient.put<unknown, ApiResponse<AgentTool>>(`api/platform/agents/tools/${id}/enabled/${enabled}`),
}

function streamEndpoint(path: string) {
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${path}`
}
