import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { useFaToast } from '@fantastic-admin/components'
import { computed, reactive, ref } from 'vue'
import { createAuthlibApi } from '../api/authlib-api'
import type { AuthlibEndpoint, AuthlibStatus } from '../types'

export function useAuthlibPlugin(sdk: YuDreamPluginSdk) {
  const api = createAuthlibApi(sdk)
  const toast = useFaToast()
  const loading = ref(false)
  const status = ref<AuthlibStatus | null>(null)

  const baseUrl = computed(() => absoluteUrl(api.apiUrl('/')).replace(/\/$/, ''))
  const launcherUrl = computed(() => `${baseUrl.value}/`)
  const statusText = computed(() => status.value ? '已连接' : '待刷新')
  const statusPayload = computed(() => status.value ? JSON.stringify(status.value, null, 2) : '暂无状态数据')

  const endpoints = computed<AuthlibEndpoint[]>(() => [
    { method: 'GET', path: '/', note: 'ALI 元数据与公钥' },
    { method: 'POST', path: '/authserver/authenticate', note: '账号登录并获取 accessToken' },
    { method: 'POST', path: '/authserver/refresh', note: '刷新访问令牌' },
    { method: 'POST', path: '/authserver/validate', note: '校验访问令牌' },
    { method: 'POST', path: '/authserver/invalidate', note: '吊销访问令牌' },
    { method: 'POST', path: '/authserver/signout', note: '账号登出全部会话' },
    { method: 'POST', path: '/sessionserver/session/minecraft/join', note: '客户端加入服务器' },
    { method: 'GET', path: '/sessionserver/session/minecraft/hasJoined', note: '服务端验证玩家' },
    { method: 'GET', path: '/sessionserver/session/minecraft/profile/{uuid}', note: '查询角色材质属性' },
    { method: 'POST', path: '/api/profiles/minecraft', note: '批量查询角色' },
    { method: 'PUT', path: '/api/user/profile/{uuid}/{textureType}', note: '绑定角色材质' },
    { method: 'DELETE', path: '/api/user/profile/{uuid}/{textureType}', note: '清除角色材质' },
  ])

  async function load() {
    loading.value = true
    try {
      status.value = await api.status()
    }
    finally {
      loading.value = false
    }
  }

  async function copy(value: string) {
    await navigator.clipboard.writeText(value)
    toast.success('已复制')
  }

  function absoluteUrl(url: string) {
    if (/^https?:\/\//i.test(url)) {
      return url
    }
    return `${window.location.origin}${url.startsWith('/') ? url : `/${url}`}`
  }

  return reactive({
    loading,
    status,
    baseUrl,
    launcherUrl,
    statusText,
    statusPayload,
    endpoints,
    load,
    copy,
  })
}

export type AuthlibPluginModel = ReturnType<typeof useAuthlibPlugin>
