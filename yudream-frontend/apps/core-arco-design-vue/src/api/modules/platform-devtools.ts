import type { ApiResponse } from './system-client'
import { toBackendAssetUrl } from '@/utils/backend-url'
import systemClient from './system-client'

/** 开发项目来源：yml 配置（面板只读）或面板登记的本地清单文件 */
export type PluginDevProjectSource = 'CONFIG' | 'FILE'

/** 开发模式项目配置（domain PluginDevProjectInfo 的 JSON 镜像，含有效性状态位） */
export interface PluginDevProject {
  code: string
  /** 插件模块根目录 */
  path: string
  frontendDist?: string
  autoCompile: boolean
  source: PluginDevProjectSource
  pathExists: boolean
  classesBuilt: boolean
  descriptorReady: boolean
}

/** 面板登记开发项目的保存载荷，code 留空时后端从 plugin.yml 推断 */
export interface PluginDevProjectSavePayload {
  path: string
  code?: string
  frontendDist?: string
  compileCommand?: string
  autoCompile: boolean
}

/** 新建插件骨架的载荷：在宿主机生成 Maven 模块，可选同时登记为开发模式项目 */
export interface PluginScaffoldPayload {
  /** 生成目录的父目录，模块落在 {parentDir}/yudream-plugin-{code} */
  parentDir: string
  /** 插件编码，kebab-case */
  code: string
  displayName?: string
  description?: string
  /** 版本，默认 1.0.0 */
  version?: string
  /** SPI 依赖版本，留空用宿主内置默认值 */
  spiVersion?: string
  /** 硬依赖插件编码列表 */
  depend?: string[]
  /** 软依赖插件编码列表 */
  softdepend?: string[]
  /** 生成后是否登记为开发模式项目，默认 true */
  register?: boolean
}

/** 新建插件骨架的结果 */
export interface PluginScaffoldResult {
  code: string
  /** 生成的模块根目录绝对路径 */
  projectPath: string
  /** 入口类全限定名 */
  mainClass: string
  spiVersion: string
  /** 已写入文件的相对路径 */
  files: string[]
  /** 是否已登记为开发模式项目 */
  registered: boolean
}

/** 宿主机目录条目（domain PluginDevDirectoryEntryInfo 的 JSON 镜像），仅目录与插件模块标记 */
export interface PluginDevDirectoryEntry {
  name: string
  path: string
  hasPom: boolean
  hasPluginYml: boolean
  inferredCode?: string
}

/** 宿主机目录浏览结果（domain PluginDevDirectoryBrowseInfo 的 JSON 镜像）；rootList 时 path 为空、entries 为盘符根 */
export interface PluginDevDirectoryBrowse {
  path: string
  parent?: string
  rootList: boolean
  /** 当前目录自身的模块标记，供「选择当前目录」时提示 */
  hasPom: boolean
  hasPluginYml: boolean
  inferredCode?: string
  entries: PluginDevDirectoryEntry[]
}

export interface PluginDevtoolsStatus {
  devModeEnabled: boolean
  traceEnabled: boolean
  devProjects: PluginDevProject[]
  installedCount: number
  loadedCount: number
  enabledCount: number
  /** 宿主运行方式：SOURCE（源码/IDE）或 JAR */
  hostRunMode?: string
  /** 开发模式开关是否来自自动检测（未显式配置 enabled） */
  devModeAuto?: boolean
  /** 面板登记的开发项目清单文件绝对路径，可供 coding agent 读取定位插件源码 */
  devProjectStoreFile?: string
}

export type PluginDevStatus = 'INSTALLED' | 'LOADED' | 'ENABLED' | 'DISABLED' | 'ERROR'

export interface PluginDevPlugin {
  code: string
  name: string
  version?: string
  description?: string
  status: PluginDevStatus
  loaded: boolean
  enabled: boolean
  devMode: boolean
  devProject?: PluginDevProject
  /** 硬依赖插件编码（plugin.yml depend），用于依赖图构图 */
  dependencies?: string[]
  /** 软依赖插件编码（plugin.yml softdepend），缺失不阻塞启用但相关集成降级 */
  softDependencies?: string[]
}

/** 插件禁用级联预览（后端按运行时状态计算） */
export interface PluginDisablePreview {
  code: string
  /** 已启用的传递硬依赖方，按建议禁用顺序排列 */
  blockers: string[]
  /** 已启用的直接软依赖方，禁用后其可选集成降级 */
  softDependents: string[]
  /** 已加载的直接依赖方，存在时卸载/重载将被拒绝 */
  unloadBlockers: string[]
}

export interface PluginMenuAsset {
  title: string
  path: string
  icon?: string
  permission?: string
  parentPath?: string
  sort?: number
}

export interface PluginPermissionAsset {
  code: string
  name: string
  module?: string
  description?: string
}

export interface PluginCapabilityAsset {
  code: string
  name: string
  type?: string
  description?: string
  icon?: string
  dependencies?: string[]
}

export interface PluginDashboardCardAsset {
  pluginCode: string
  code: string
  title: string
  description?: string
  icon?: string
  category?: string
  permission?: string
  component?: string
  actionPath?: string
}

export interface PluginFrontendRouteAsset {
  path: string
  name?: string
  title?: string
  icon?: string
  component?: string
  permission?: string
  sort?: number
  hideInMenu?: boolean
}

export interface PluginFrontendModuleAsset {
  pluginCode: string
  entry?: string
  moduleName?: string
  sdkVersion?: string
  menuTitle?: string
  menuIcon?: string
  menuSort?: number
  routes: PluginFrontendRouteAsset[]
  styles?: string[]
  scripts?: string[]
}

export interface PluginHttpEndpointAsset {
  pluginCode: string
  method: string
  path: string
  fullPath: string
  permission?: string
  wrapResult: boolean
}

export interface PluginCommandAsset {
  pluginCode: string
  code: string
  command: string
  name: string
  permission?: string
  description?: string
  allowAnonymous: boolean
}

export interface PluginMessageInteractionAsset {
  pluginCode: string
  kind: string
  eventTypes: string[]
  platform?: string
  channelId?: string
  command?: string
}

export interface PluginAiToolAsset {
  pluginCode: string
  name: string
  title?: string
  description?: string
  permissionCode?: string
  risk?: string
  requiresConfirmation: boolean
  allowedTriggers: string[]
}

export interface PluginRuntimeAgentAsset {
  pluginCode: string
  id: string
  code: string
  name: string
  description?: string
  icon?: string
  status?: string
}

export interface PluginRuntimeAssets {
  pluginCode: string
  loaded: boolean
  enabled: boolean
  menus: PluginMenuAsset[]
  permissions: PluginPermissionAsset[]
  capabilities: PluginCapabilityAsset[]
  dashboardCards: PluginDashboardCardAsset[]
  frontendModules: PluginFrontendModuleAsset[]
  httpEndpoints: PluginHttpEndpointAsset[]
  commands: PluginCommandAsset[]
  messageInteractions: PluginMessageInteractionAsset[]
  aiTools: PluginAiToolAsset[]
  agents: PluginRuntimeAgentAsset[]
  exposedServices: string[]
}

export interface PluginCommandTestPayload {
  command: string
  arguments?: string[]
  content?: string
}

export interface PluginCommandTestResult {
  pluginCode: string
  command: string
  matched: boolean
  success: boolean
  errorMessage?: string
  durationMs?: number
}

export type AgentTraceSource = 'CHAT' | 'WIKI' | 'CMS' | 'DEBUG' | 'PLUGIN' | 'SYSTEM'
export type AgentTraceStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface AiUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface AgentTraceSummary {
  traceId: string
  source: AgentTraceSource
  ownerPluginCode?: string
  agentId?: string
  agentCode?: string
  agentName?: string
  status: AgentTraceStatus
  input?: string
  error?: string
  stepCount: number
  durationMs?: number
  startTime?: string
}

export interface AgentTraceStep {
  seq: number
  nodeId?: string
  nodeKind?: string
  nodeTitle?: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | string
  inputSummary?: string
  outputSummary?: string
  reasoning?: string
  toolName?: string
  toolDetail?: string
  message?: string
  startTime?: string
  endTime?: string
  durationMs?: number
}

export interface AgentTraceDetail extends AgentTraceSummary {
  finalOutput?: string
  reasoning?: string
  usage?: AiUsage
  steps: AgentTraceStep[]
  endTime?: string
}

export interface AgentTracePage {
  total: number
  page: number
  size: number
  list: AgentTraceSummary[]
}

export type PluginLifecycleAction = 'LOAD' | 'ENABLE' | 'DISABLE' | 'UNLOAD' | 'RELOAD' | 'FRONTEND_RELOAD' | 'COMPILE'

/** 单类别资产差异（domain PluginRuntimeAssetsDiff.Entry 的 JSON 镜像），category 与 PluginRuntimeAssets 字段同名 */
export interface PluginRuntimeAssetsDiffEntry {
  category: string
  added: string[]
  removed: string[]
}

/** 生命周期 SSE 事件载荷（domain PluginLifecycleEvent 的 JSON 镜像） */
export interface PluginLifecycleEventPayload {
  pluginCode: string
  action: PluginLifecycleAction
  success: boolean
  version?: string
  durationMs?: number
  errorMessage?: string
  occurredAt?: string
  /** 仅开发模式 RELOAD 成功时携带：重载前后运行时资产差异 */
  assetsDiff?: { entries?: PluginRuntimeAssetsDiffEntry[] }
}

/** Agent 追踪 SSE 事件载荷（domain AgentTraceEvent 的 JSON 镜像） */
export interface AgentTraceEventPayload {
  traceId: string
  action: 'STARTED' | 'STEP' | 'COMPLETED' | 'FAILED'
  source: AgentTraceSource
  ownerPluginCode?: string
  agentCode?: string
  agentName?: string
  status: AgentTraceStatus
  step?: AgentTraceStep
  error?: string
  durationMs?: number
  usage?: AiUsage
  occurredAt?: string
}

export interface AgentTraceQueryParams {
  source?: string
  pluginCode?: string
  status?: string
  page?: number
  size?: number
}

/** 端点测试器结果，保留原始 HTTP 信息便于调试 */
export interface EndpointTestResult {
  ok: boolean
  status: number
  statusText: string
  durationMs: number
  body: string
}

/** 插件运行日志条目（interfaces PluginLogEntryRes 的 JSON 镜像） */
export interface PluginLogEntry {
  sequence: number
  timestamp: number
  time: string
  level: string
  logger: string
  thread: string
  traceId?: string
  message: string
  throwable?: string
}

export interface PluginLogQueryParams {
  level?: string
  keyword?: string
  limit?: number
}

export default {
  status: () =>
    systemClient.get<unknown, ApiResponse<PluginDevtoolsStatus>>('api/platform/plugin-devtools/status'),

  plugins: () =>
    systemClient.get<unknown, ApiResponse<PluginDevPlugin[]>>('api/platform/plugin-devtools/plugins'),

  assets: (code: string) =>
    systemClient.get<unknown, ApiResponse<PluginRuntimeAssets>>(`api/platform/plugin-devtools/plugins/${code}/assets`),

  /** 禁用级联预览：列出启用中的传递硬依赖方、软依赖方与卸载阻塞 */
  disablePreview: (code: string) =>
    systemClient.get<unknown, ApiResponse<PluginDisablePreview>>(`api/platform/plugin-devtools/plugins/${code}/disable-preview`),

  reload: (code: string) =>
    systemClient.post<unknown, ApiResponse<unknown>>(`api/platform/plugin-devtools/plugins/${code}/reload`),

  devProjects: () =>
    systemClient.get<unknown, ApiResponse<PluginDevProject[]>>('api/platform/plugin-devtools/dev-projects'),

  addDevProject: (data: PluginDevProjectSavePayload) =>
    systemClient.post<unknown, ApiResponse<PluginDevProject>>('api/platform/plugin-devtools/dev-projects', data),

  removeDevProject: (code: string) =>
    systemClient.delete<unknown, ApiResponse<unknown>>(`api/platform/plugin-devtools/dev-projects/${code}`),

  /** 浏览宿主机目录：path 为空时返回盘符根列表；仅列目录，不读文件内容 */
  browseDevDirectories: (path?: string) =>
    systemClient.get<unknown, ApiResponse<PluginDevDirectoryBrowse>>('api/platform/plugin-devtools/dev-projects/browse', { params: { path } }),

  /** 新建插件骨架：生成 Maven 模块，默认同时登记为开发模式项目 */
  scaffold: (data: PluginScaffoldPayload) =>
    systemClient.post<unknown, ApiResponse<PluginScaffoldResult>>('api/platform/plugin-devtools/scaffold', data),

  commandTest: (code: string, data: PluginCommandTestPayload) =>
    systemClient.post<unknown, ApiResponse<PluginCommandTestResult>>(`api/platform/plugin-devtools/plugins/${code}/command-test`, data),

  traces: (params: AgentTraceQueryParams) =>
    systemClient.get<unknown, ApiResponse<AgentTracePage>>('api/platform/plugin-devtools/agent-traces', { params }),

  traceDetail: (traceId: string) =>
    systemClient.get<unknown, ApiResponse<AgentTraceDetail>>(`api/platform/plugin-devtools/agent-traces/${traceId}`),

  lifecycleStreamUrl: () => toBackendAssetUrl('/api/platform/plugin-devtools/events/stream'),

  traceStreamUrl: () => toBackendAssetUrl('/api/platform/plugin-devtools/agent-traces/stream'),

  pluginLogs: (code: string, params: PluginLogQueryParams) =>
    systemClient.get<unknown, ApiResponse<PluginLogEntry[]>>(`api/platform/plugin-devtools/plugins/${code}/logs`, { params }),

  pluginLogsStreamUrl: (code: string, level?: string) => {
    const query = level ? `?level=${encodeURIComponent(level)}` : ''
    return toBackendAssetUrl(`/api/platform/plugin-devtools/plugins/${code}/logs/stream${query}`)
  },

  /**
   * 端点测试器：绕过 axios 封装直连目标端点，保留真实状态码与响应体。
   * 若目标端点启用接口加密，返回体可能是密文，面板按原文展示。
   */
  async testEndpoint(method: string, path: string, body?: string): Promise<EndpointTestResult> {
    const url = toBackendAssetUrl(path)
    const headers: Record<string, string> = { 'Accept-Language': 'zh-CN' }
    const token = localStorage.getItem('token')
    if (token) {
      headers.Authorization = token
    }
    const hasBody = body !== undefined && body.trim() !== '' && !['GET', 'HEAD'].includes(method.toUpperCase())
    if (hasBody) {
      headers['Content-Type'] = 'application/json'
    }
    const started = performance.now()
    try {
      const response = await fetch(url, {
        method: method.toUpperCase(),
        headers,
        body: hasBody ? body : undefined,
      })
      const text = await response.text()
      return {
        ok: response.ok,
        status: response.status,
        statusText: response.statusText,
        durationMs: Math.round(performance.now() - started),
        body: text,
      }
    }
    catch (error: any) {
      return {
        ok: false,
        status: 0,
        statusText: '网络错误',
        durationMs: Math.round(performance.now() - started),
        body: error?.message || '请求未能发出',
      }
    }
  },
}
