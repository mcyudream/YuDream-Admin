import apiPlugin from '@/api/modules/platform-plugin'
import { toBackendAssetUrl } from '@/utils/backend-url'

export interface YuDreamPluginSdk {
  version: string
  pluginCode: string
  http: {
    request: <T = unknown>(path: string, options?: { method?: string, data?: unknown }) => Promise<T>
    get: <T = unknown>(path: string) => Promise<T>
    post: <T = unknown>(path: string, data?: unknown) => Promise<T>
    url: (path: string) => string
  }
}

export const YUDREAM_PLUGIN_SDK_VERSION = '1.0.0'

export function createPluginSdk(pluginCode: string): YuDreamPluginSdk {
  return {
    version: YUDREAM_PLUGIN_SDK_VERSION,
    pluginCode,
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
      url(path: string) {
        const normalized = path.startsWith('/') ? path : `/${path}`
        return toBackendAssetUrl(`/api/plugins/${pluginCode}${normalized}`)
      },
    },
  }
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
