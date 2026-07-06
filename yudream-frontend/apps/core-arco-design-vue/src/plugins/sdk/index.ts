import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import * as FantasticAdminComponents from '@fantastic-admin/components'
import * as Vue from 'vue'
import * as VueRouter from 'vue-router'
import apiPlugin from '@/api/modules/platform-plugin'
import { toBackendAssetUrl } from '@/utils/backend-url'

export type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

export const YUDREAM_PLUGIN_SDK_VERSION = '1.0.0'

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
    __YUDREAM_PLUGIN_SHARED__?: {
      vue: typeof Vue
      vueRouter: typeof VueRouter
      components: typeof FantasticAdminComponents
    }
  }
}

if (typeof window !== 'undefined') {
  window.__YUDREAM_PLUGIN_SHARED__ = {
    vue: Vue,
    vueRouter: VueRouter,
    components: FantasticAdminComponents,
  }
  window.__YUDREAM_PLUGIN_SDK__ = {
    version: YUDREAM_PLUGIN_SDK_VERSION,
    create: createPluginSdk,
  }
}
