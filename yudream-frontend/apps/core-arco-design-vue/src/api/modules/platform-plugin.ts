import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface PluginBlobResponse {
  data: Blob
  headers: Record<string, string>
  status: number
}

export type PluginStatus = 'INSTALLED' | 'LOADED' | 'ENABLED' | 'DISABLED' | 'ERROR'

export interface PluginModule {
  id?: string
  code: string
  name: string
  version?: string
  description?: string
  icon?: string
  mainClass?: string
  jarPath?: string
  dependencies?: string[]
  softDependencies?: string[]
  status: PluginStatus
  errorMessage?: string
  loadedAt?: string
  enabledAt?: string
  loaded: boolean
  enabled: boolean
  rollbackAvailable?: boolean
  rollbackVersion?: string
}

export interface PluginFrontendRoute {
  path: string
  name: string
  title: string
  icon?: string
  parentPath?: string
  parentTitle?: string
  parentIcon?: string
  parentSort?: number
  component?: string
  permission?: string
  sort?: number
  hideInMenu?: boolean
  publicAccess?: boolean
  siteNav?: boolean
  parentCode?: string
  visible?: boolean
  status?: 'ACTIVE' | 'DISABLED'
  menuCode?: string
  type?: 'CATEGORY' | 'LAYOUT' | 'MENU' | 'LINK' | 'BUTTON'
  module?: string
  link?: string
  parentMenuCode?: string
  parentParentCode?: string
  parentType?: 'CATEGORY' | 'LAYOUT' | 'MENU' | 'LINK' | 'BUTTON'
  parentModule?: string
  parentComponent?: string
  parentLink?: string
  parentPermission?: string
  parentVisible?: boolean
  parentStatus?: 'ACTIVE' | 'DISABLED'
}

export interface PluginFrontendModule {
  pluginCode: string
  entry?: string
  moduleName?: string
  sdkVersion?: string
  integrity?: string
  assetRevision?: string
  styles?: string[]
  scripts?: string[]
  menuTitle?: string
  menuIcon?: string
  menuSort?: number
  parentCode?: string
  visible?: boolean
  status?: 'ACTIVE' | 'DISABLED'
  menuCode?: string
  menuType?: 'CATEGORY' | 'LAYOUT' | 'MENU' | 'LINK' | 'BUTTON'
  menuModule?: string
  menuPath?: string
  menuComponent?: string
  menuLink?: string
  menuPermission?: string
  routes: PluginFrontendRoute[]
}

export interface PluginGlobalWidget {
  pluginCode: string
  code: string
  component: string
  permission?: string
  sort?: number
}

export interface PluginFrontendManifest {
  sdkVersion: string
  modules: PluginFrontendModule[]
  globalWidgets?: PluginGlobalWidget[]
}

export type PluginThemeScope = 'SITE' | 'ADMIN'

export interface PluginTheme {
  pluginCode: string
  code: string
  name: string
  description?: string
  scopes?: PluginThemeScope[]
  styles: string[]
  preview?: string
  /** 主题自带的首页内容定制方案资产路径，空表示不自带方案 */
  homePreset?: string
  /** 主题声明的公开站首页远程组件（如 theme/Home），空表示首页仍走 CMS 渲染 */
  homeComponent?: string
  /** 主题声明的站点 chrome 远程组件（如 theme/Chrome），空表示仍走宿主 SiteChrome */
  chromeComponent?: string
  /** homeComponent/chromeComponent 所在的远程模块名 */
  moduleName?: string
  assetRevision?: string
}

export interface PluginThemeOverview {
  themes: PluginTheme[]
  active: Partial<Record<PluginThemeScope, string>>
}

export interface PluginMessagingConnection {
  id: string
  name: string
  platform?: string
  userId?: string | null
  protocol?: string | null
}

export interface PluginMessagingGroup {
  id: string
  name: string
}

export interface PluginUserCatalog {
  id: string
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status?: string
  deptIds?: string[]
  deptNames?: string[]
}

export interface PluginDeptCatalog {
  id: string
  name: string
  label?: string
  parentId?: string | null
  status?: string
  children?: PluginDeptCatalog[]
}

export interface PluginRoleCatalog {
  id: string
  code?: string
  name: string
  deptId?: string | null
  deptName?: string | null
}

export interface PluginAiAgentCatalog {
  code: string
  name: string
  description?: string
}

export interface PluginAiModelCatalog {
  code: string
  name: string
}

export interface PluginAiProviderCatalog {
  code: string
  name: string
  models?: PluginAiModelCatalog[]
}

export default {
  list: () => systemClient.get<unknown, ApiResponse<PluginModule[]>>('api/platform/plugins'),
  refresh: () => systemClient.post<unknown, ApiResponse<PluginModule[]>>('api/platform/plugins/refresh'),
  upload: (data: FormData) => systemClient.post<unknown, ApiResponse<PluginModule[]>>('api/platform/plugins/upload', data),
  load: (code: string) => systemClient.post<unknown, ApiResponse<PluginModule>>(`api/platform/plugins/${code}/load`),
  enable: (code: string) => systemClient.post<unknown, ApiResponse<PluginModule>>(`api/platform/plugins/${code}/enable`),
  disable: (code: string) => systemClient.post<unknown, ApiResponse<PluginModule>>(`api/platform/plugins/${code}/disable`),
  unload: (code: string) => systemClient.post<unknown, ApiResponse<PluginModule>>(`api/platform/plugins/${code}/unload`),
  remove: (code: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/plugins/${code}`),
  frontendManifest: () => systemClient.get<unknown, ApiResponse<PluginFrontendManifest>>('api/platform/plugins/frontend-manifest'),
  activeThemes: () => systemClient.get<unknown, ApiResponse<Partial<Record<PluginThemeScope, PluginTheme>>>>('api/platform/plugins/themes/active'),
  themes: () => systemClient.get<unknown, ApiResponse<PluginThemeOverview>>('api/platform/plugins/themes'),
  messagingConnections: () => systemClient.get<unknown, ApiResponse<PluginMessagingConnection[]>>('api/platform/plugins/messaging/connections'),
  messagingGroups: (connectionId: string) => systemClient.get<unknown, ApiResponse<PluginMessagingGroup[]>>('api/platform/plugins/messaging/groups', {
    params: { connectionId },
  }),
  userCatalog: (params: { keyword?: string, deptId?: string, page?: number, size?: number } = {}) => systemClient.get<unknown, ApiResponse<PluginUserCatalog[]>>('api/platform/plugins/users', {
    params,
  }),
  resolveUsers: (ids: string[]) => systemClient.get<unknown, ApiResponse<PluginUserCatalog[]>>('api/platform/plugins/users/resolve', {
    params: { ids: ids.join(',') },
  }),
  departmentCatalog: (params: { keyword?: string, flatten?: boolean } = {}) => systemClient.get<unknown, ApiResponse<PluginDeptCatalog[]>>('api/platform/plugins/users/departments', {
    params,
  }),
  roleCatalog: () => systemClient.get<unknown, ApiResponse<PluginRoleCatalog[]>>('api/platform/plugins/users/roles'),
  aiAgentCatalog: () => systemClient.get<unknown, ApiResponse<PluginAiAgentCatalog[]>>('api/platform/plugins/ai/agents'),
  aiProviderCatalog: () => systemClient.get<unknown, ApiResponse<PluginAiProviderCatalog[]>>('api/platform/plugins/ai/providers'),
  request: <T = unknown>(pluginCode: string, path: string, options: { method?: string, data?: unknown } = {}) => {
    return systemClient.request<unknown, ApiResponse<T>>({
      url: `api/plugins/${pluginCode}${path.startsWith('/') ? path : `/${path}`}`,
      method: options.method || 'GET',
      data: options.data,
    })
  },
  blob: async (pluginCode: string, path: string, options: { method?: string, data?: unknown } = {}) => {
    const response = await systemClient.request<unknown, PluginBlobResponse>({
      url: `api/plugins/${pluginCode}${path.startsWith('/') ? path : `/${path}`}`,
      method: options.method || 'GET',
      data: options.data,
      responseType: 'blob',
    })
    await rejectJsonBlob(response)
    return response
  },
}

/**
 * 仅当插件以 JSON 返回错误（4xx/5xx）时，把 blob 错误体解析为 Error 抛出；
 * 2xx 的 application/json 响应是插件主动下发的 JSON 文件下载，必须原样放行。
 * （非 2xx 时 axios 走错误拦截器，插件 message 的解析兜底在 system-client。）
 */
async function rejectJsonBlob(response: PluginBlobResponse) {
  const contentType = String(response.headers?.['content-type'] || '')
  if (!contentType.includes('application/json') || response.status < 400) {
    return
  }
  const text = await response.data.text()
  try {
    const result = JSON.parse(text) as { message?: string }
    throw new Error(result.message || '文件下载失败')
  }
  catch (error) {
    if (error instanceof SyntaxError) {
      throw new Error(text || '文件下载失败')
    }
    throw error
  }
}
