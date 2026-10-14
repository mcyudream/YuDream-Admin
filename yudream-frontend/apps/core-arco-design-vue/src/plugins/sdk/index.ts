import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { getCurrentScope, onScopeDispose, watch } from 'vue'
import { cancelAiRun, getAiRun, listAiRuns, respondAiInteraction, resumeAiRun, subscribeAiRun } from '@/api/modules/platform-ai-runs'
import apiFiles from '@/api/modules/files'
import apiPlugin from '@/api/modules/platform-plugin'
import apiPublicTheme from '@/api/modules/public-theme'
import { registerAiAssistantAction } from '@/composables/ai-assistant-actions'
import router from '@/router'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { pluginFrontendAssetUrl } from '@/plugins/frontend-assets'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { applyPublicSeo } from '@/utils/public-seo'

export type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

export const YUDREAM_PLUGIN_SDK_VERSION = '1.12.0'

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
      runs: {
        async list(sessionId) { return (await listAiRuns(sessionId)).data },
        async get(id) { return (await getAiRun(id)).data },
        subscribe(id, options) { return subscribeAiRun(id, options.after, options.signal, options.onEvent) },
        async watch(id, listener, options) {
          let disposed = false
          let cleanup = () => {}
          if (getCurrentScope()) onScopeDispose(() => { disposed = true; cleanup() })
          if (options?.signal?.aborted) return () => {}
          const owner = String(accountStore.userId || '')
          if (!owner) throw new Error('请先登录')
          const runtime = useAiAssistantStore()
          const run = (await getAiRun(id)).data
          if (disposed || options?.signal?.aborted || String(accountStore.userId || '') !== owner) return () => {}
          runtime.attachRun(run)
          const unwatch = watch(() => runtime.runs[id], (snapshot) => {
            if (snapshot && String(accountStore.userId || '') === owner) {
              try { listener(JSON.parse(JSON.stringify(snapshot))) }
              catch { console.warn('插件 AI 观察回调失败', pluginCode) }
            }
          }, { deep: true, immediate: true })
          const detach = () => { unwatch(); options?.signal?.removeEventListener('abort', detach) }
          cleanup = detach
          options?.signal?.addEventListener('abort', detach, { once: true })
          return detach
        },
        async cancel(id) { return (await cancelAiRun(id)).data },
        async resume(id) { return (await resumeAiRun(id)).data },
        async respond(id, requestId, answer) { return (await respondAiInteraction(id, requestId, answer)).data },
      },
      registerAction(action, element) {
        const name = router.currentRoute.value.name
        if (typeof name !== 'string') throw new Error('当前页面没有可注册的路由')
        if (!/^[a-z][a-z0-9:.-]{1,119}$/.test(action.code)) throw new Error('AI 操作标识无效')
        return registerAiAssistantAction({ ...action, code: `plugin:${pluginCode}:${action.code}`, routeName: name }, element)
      },
      async agents() {
        const res = await apiPlugin.aiAgentCatalog()
        return res.data || []
      },
      async providers() {
        const res = await apiPlugin.aiProviderCatalog()
        return res.data || []
      },
    },
    backup: {
      async targets() {
        const res = await apiPlugin.backupTargetCatalog()
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
