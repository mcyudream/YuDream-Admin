import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface PluginBlobResponse {
  data: Blob
  headers: Record<string, string>
}

export type PluginStatus = 'INSTALLED' | 'LOADED' | 'ENABLED' | 'DISABLED' | 'ERROR'

export interface PluginModule {
  id?: string
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
  hideInMenu?: boolean
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

export interface PluginFrontendManifest {
  sdkVersion: string
  modules: PluginFrontendModule[]
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

async function rejectJsonBlob(response: PluginBlobResponse) {
  const contentType = String(response.headers?.['content-type'] || '')
  if (!contentType.includes('application/json')) {
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
