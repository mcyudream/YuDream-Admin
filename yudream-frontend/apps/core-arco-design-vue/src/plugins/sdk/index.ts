import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import apiFiles from '@/api/modules/files'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPublicTheme from '@/api/modules/public-theme'
import { pluginFrontendAssetUrl } from '@/plugins/frontend-assets'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { applyPublicSeo } from '@/utils/public-seo'

export type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

export const YUDREAM_PLUGIN_SDK_VERSION = '1.7.0'

export function createPluginSdk(pluginCode: string): YuDreamPluginSdk {
  const accountStore = useAppAccountStore()
  return {
    version: YUDREAM_PLUGIN_SDK_VERSION,
    pluginCode,
    account: {
      userId: accountStore.userId ? String(accountStore.userId) : '',
      username: accountStore.account,
      avatar: accountStore.avatar,
      currentDept: accountStore.currentDept,
      currentRole: accountStore.currentRole,
      permissions: accountStore.permissions,
    },
    http: {
      async request<T = unknown>(path: string, options = {}) {
        const res = await apiPlugin.request<T>(pluginCode, path, options)
        return res.data
      },
      async get<T = unknown>(path: string) {
        const res = await apiPlugin.request<T>(pluginCode, path, { method: 'GET' })
        return res.data
      },
      async post<T = unknown>(path: string, data?: unknown) {
        const res = await apiPlugin.request<T>(pluginCode, path, { method: 'POST', data })
        return res.data
      },
      async blob(path: string, options = {}) {
        return apiPlugin.blob(pluginCode, path, options)
      },
      url(path: string) {
        const normalized = path.startsWith('/') ? path : `/${path}`
        return toBackendAssetUrl(`/api/plugins/${pluginCode}${normalized}`)
      },
    },
    files: {
      async uploadImage(file, options = {}) {
        const data = new FormData()
        data.append('file', file)
        data.append('module', options.module || pluginCode || 'plugin')
        data.append('publicAccess', String(options.publicAccess ?? true))
        const res = await apiFiles.upload(data)
        return {
          ...res.data,
          assetUrl: toBackendAssetUrl(res.data.url),
        }
      },
      assetUrl: toBackendAssetUrl,
      thumbUrl(url, _options) {
        return toBackendAssetUrl(toFileThumbPath(url))
      },
    },
    assets: {
      url(path: string) {
        return pluginFrontendAssetUrl(pluginCode, path)
      },
    },
    messaging: {
      async connections() {
        const res = await apiPlugin.messagingConnections()
        return res.data || []
      },
      async groups(connectionId: string) {
        if (!connectionId) {
          return []
        }
        const res = await apiPlugin.messagingGroups(connectionId)
        return res.data || []
      },
    },
    users: {
      async search(query = {}) {
        const res = await apiPlugin.userCatalog({
          keyword: query.keyword,
          deptId: query.deptId,
          page: query.page ?? 1,
          size: query.size ?? 20,
        })
        return res.data || []
      },
      async resolve(ids: string[]) {
        if (!ids.length) {
          return []
        }
        const res = await apiPlugin.resolveUsers(ids)
        return res.data || []
      },
      async departments(query = {}) {
        const res = await apiPlugin.departmentCatalog({
          keyword: query.keyword,
          flatten: query.flatten,
        })
        return res.data || []
      },
      async roles() {
        const res = await apiPlugin.roleCatalog()
        return res.data || []
      },
    },
    ai: {
      async agents() {
        const res = await apiPlugin.aiAgentCatalog()
        return res.data || []
      },
      async providers() {
        const res = await apiPlugin.aiProviderCatalog()
        return res.data || []
      },
    },
    site: {
      async context(query = {}) {
        const res = await apiPublicTheme.context(query)
        return res.data
      },
      applySeo(input) {
        applyPublicSeo(input)
      },
      assetUrl: toBackendAssetUrl,
    },
  }
}

function toFileThumbPath(url?: string) {
  const raw = (url || '').trim()
  if (!raw) {
    return ''
  }
  const match = raw.match(/^(.*\/api\/files\/(?:public\/)?\d+\/content)(?:\?.*)?$/i)
  if (!match) {
    return raw
  }
  return `${match[1]}/thumb`
}

declare global {
  interface Window {
    __YUDREAM_PLUGIN_SDK__?: {
      version: string
      create: typeof createPluginSdk
    }
  }
}

if (typeof window !== 'undefined') {
  window.__YUDREAM_PLUGIN_SDK__ = {
    version: YUDREAM_PLUGIN_SDK_VERSION,
    create: createPluginSdk,
  }
}
