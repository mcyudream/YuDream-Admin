import type { Component } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'

export interface YuDreamPluginHttpClient {
  request: <T = unknown>(path: string, options?: { method?: string, data?: unknown }) => Promise<T>
  get: <T = unknown>(path: string) => Promise<T>
  post: <T = unknown>(path: string, data?: unknown) => Promise<T>
  blob: (path: string, options?: { method?: string, data?: unknown }) => Promise<YuDreamPluginBlobResponse>
  url: (path: string) => string
}

export interface YuDreamPluginBlobResponse {
  data: Blob
  headers: Record<string, string>
}

export interface YuDreamPluginFileObject {
  id: string
  originalName?: string
  contentType?: string
  size?: number
  module?: string
  url?: string
  assetUrl?: string
  createTime?: string
}

export interface YuDreamPluginFileUploadOptions {
  module?: string
  publicAccess?: boolean
}

export interface YuDreamPluginFilesClient {
  uploadImage: (file: File, options?: YuDreamPluginFileUploadOptions) => Promise<YuDreamPluginFileObject>
  assetUrl: (url?: string) => string
}

export interface YuDreamPluginAssetsClient {
  url: (path: string) => string
}

export interface YuDreamPluginMessagingConnection {
  id: string
  name: string
  platform?: string
  userId?: string | null
  protocol?: string | null
}

export interface YuDreamPluginMessagingGroup {
  id: string
  name: string
}

export interface YuDreamPluginMessagingClient {
  connections: () => Promise<YuDreamPluginMessagingConnection[]>
  groups: (connectionId: string) => Promise<YuDreamPluginMessagingGroup[]>
}

export interface YuDreamPluginUserOption {
  id: string
  username: string
  nickname?: string
  email?: string
  avatar?: string
  status?: string
  deptIds?: string[]
  deptNames?: string[]
}

export interface YuDreamPluginDeptOption {
  id: string
  name: string
  label?: string
  parentId?: string | null
  status?: string
  children?: YuDreamPluginDeptOption[]
}

export interface YuDreamPluginRoleOption {
  id: string
  code?: string
  name: string
  deptId?: string | null
  deptName?: string | null
}

export interface YuDreamPluginUsersClient {
  search: (query?: { keyword?: string, deptId?: string, page?: number, size?: number }) => Promise<YuDreamPluginUserOption[]>
  resolve: (ids: string[]) => Promise<YuDreamPluginUserOption[]>
  departments: (query?: { keyword?: string, flatten?: boolean }) => Promise<YuDreamPluginDeptOption[]>
  roles: () => Promise<YuDreamPluginRoleOption[]>
}

export interface YuDreamPluginAiAgentOption {
  code: string
  name: string
  description?: string
}

export interface YuDreamPluginAiModelOption {
  code: string
  name: string
}

export interface YuDreamPluginAiProviderOption {
  code: string
  name: string
  models?: YuDreamPluginAiModelOption[]
}

export interface YuDreamPluginAiClient {
  agents: () => Promise<YuDreamPluginAiAgentOption[]>
  providers: () => Promise<YuDreamPluginAiProviderOption[]>
}

export interface YuDreamPluginAccount {
  userId: string
  username: string
  avatar?: string
  currentDept?: unknown
  currentRole?: unknown
  permissions: string[]
}

export interface YuDreamPluginSdk {
  version: string
  pluginCode: string
  account: YuDreamPluginAccount
  http: YuDreamPluginHttpClient
  files: YuDreamPluginFilesClient
  assets: YuDreamPluginAssetsClient
  messaging: YuDreamPluginMessagingClient
  users: YuDreamPluginUsersClient
  ai: YuDreamPluginAiClient
}

export interface YuDreamPluginPageProps {
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}

export interface YuDreamPluginFrontendModule {
  routes?: Record<string, Component>
  default?: Component | YuDreamPluginFrontendModule
  /** Called at most once for each loaded remote-module revision; implementations must be idempotent. */
  install?: () => void | Promise<void>
  /** Optional cleanup hook invoked when the host releases this remote-module revision. */
  dispose?: () => void | Promise<void>
}

export function defineYuDreamPlugin(module: YuDreamPluginFrontendModule) {
  return module
}
