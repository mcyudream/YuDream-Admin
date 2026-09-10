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
  /** 列表封面用的稳定缩略图地址；非 `/api/files/.../content` 原样回退。 */
  thumbUrl: (url?: string, options?: YuDreamPluginThumbOptions) => string
}

export interface YuDreamPluginThumbOptions {
  /** 预留最长边，当前平台出口固定 400px JPEG。 */
  maxEdge?: number
}

/** Firefox 每域名约 6 条并发连接；列表封面必须排队，避免占满后把 API 请求挤掉。 */
export function acquireImageSlot(maxInflight = 2): Promise<() => void> {
  const limit = Math.max(1, maxInflight)
  return new Promise((resolve) => {
    const grant = () => {
      if (imageSlotState.inflight >= limit) {
        imageSlotState.waiters.push(grant)
        return
      }
      imageSlotState.inflight += 1
      let released = false
      resolve(() => {
        if (released) {
          return
        }
        released = true
        imageSlotState.inflight = Math.max(0, imageSlotState.inflight - 1)
        const next = imageSlotState.waiters.shift()
        next?.()
      })
    }
    grant()
  })
}

const imageSlotState = {
  inflight: 0,
  waiters: [] as Array<() => void>,
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

export interface YuDreamPluginSiteContextQuery {
  /** 请求的主题块编码列表；空表示不需要块数据 */
  blocks?: string[]
  /** 单块数据量上限 */
  limit?: number
  /** CMS 最新文章数量；空或 0 表示不需要 */
  cmsLatest?: number
}

export interface YuDreamPluginSiteContext {
  themeCode: string
  themeConfig: Record<string, any>
  /** 插件主题块提供者贡献的数据，键为块 code；插件未装载/不支持当前主题时缺省 */
  blocks: Record<string, any>
  cmsPagesLatest: Array<Record<string, any>>
}

export interface YuDreamPluginSiteSeoInput {
  title: string
  description?: string
  canonicalPath?: string
  image?: string
  type?: 'article' | 'website'
  siteName?: string
  publishedAt?: string
  updatedAt?: string
  breadcrumbs?: Array<{ name: string, path: string }>
}

/** 公开站主题页能力：仅对 SITE 场景的公开页面有意义，匿名可用。 */
export interface YuDreamPluginSiteClient {
  /** 聚合当前 SITE 主题配置、插件数据块与 CMS 最新文章（匿名公开端点） */
  context: (query?: YuDreamPluginSiteContextQuery) => Promise<YuDreamPluginSiteContext>
  /** 写入公开页 SEO 元信息（title/description/og/canonical/结构化数据） */
  applySeo: (input: YuDreamPluginSiteSeoInput) => void
  /** 后端相对资产路径（/api/...）转可访问 URL（dev 走代理前缀，生产同源不变） */
  assetUrl: (path: string) => string
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
  site: YuDreamPluginSiteClient
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
