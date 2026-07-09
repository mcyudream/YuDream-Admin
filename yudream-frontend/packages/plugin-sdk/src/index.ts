import type { Component } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'

export interface YuDreamPluginHttpClient {
  request: <T = unknown>(path: string, options?: { method?: string, data?: unknown }) => Promise<T>
  get: <T = unknown>(path: string) => Promise<T>
  post: <T = unknown>(path: string, data?: unknown) => Promise<T>
  url: (path: string) => string
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
}

export interface YuDreamPluginPageProps {
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}

export interface YuDreamPluginFrontendModule {
  routes?: Record<string, Component>
  default?: Component | YuDreamPluginFrontendModule
  install?: () => void | Promise<void>
}

export function defineYuDreamPlugin(module: YuDreamPluginFrontendModule) {
  return module
}
