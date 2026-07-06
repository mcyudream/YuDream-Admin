import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type PluginStatus = 'INSTALLED' | 'LOADED' | 'ENABLED' | 'DISABLED' | 'ERROR'

export interface PluginModule {
  id?: number
  code: string
  name: string
  version?: string
  description?: string
  mainClass?: string
  jarPath?: string
  dependencies?: string[]
  status: PluginStatus
  errorMessage?: string
  loadedAt?: string
  enabledAt?: string
  loaded: boolean
  enabled: boolean
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
}

export interface PluginFrontendModule {
  pluginCode: string
  entry?: string
  moduleName?: string
  sdkVersion?: string
  integrity?: string
  menuTitle?: string
  menuIcon?: string
  menuSort?: number
  routes: PluginFrontendRoute[]
}

export interface PluginFrontendManifest {
  sdkVersion: string
  modules: PluginFrontendModule[]
}

export interface PluginFrontendRouteSortPayload {
  path: string
  name: string
  sort?: number
  parentSort?: number
}

export interface PluginFrontendSortPayload {
  moduleName?: string
  menuSort?: number
  routes: PluginFrontendRouteSortPayload[]
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
  saveFrontendSort: (code: string, data: PluginFrontendSortPayload) => systemClient.put<unknown, ApiResponse<PluginFrontendModule>>(`api/platform/plugins/${code}/frontend-sort`, data),
  request: <T = unknown>(pluginCode: string, path: string, options: { method?: string, data?: unknown } = {}) => {
    return systemClient.request<unknown, ApiResponse<T>>({
      url: `api/plugins/${pluginCode}${path.startsWith('/') ? path : `/${path}`}`,
      method: options.method || 'GET',
      data: options.data,
    })
  },
}
