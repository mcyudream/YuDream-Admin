import type { AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { useAppAccountStore } from '@/store/modules/app/account'

declare module 'axios' {
  export interface AxiosRequestConfig {
    apiEncryptionKey?: CryptoKey
    apiPlainData?: unknown
    skipTokenRefresh?: boolean
    tokenRetried?: boolean
  }
}

let refreshingToken: Promise<string> | null = null

// 并发 401 共享同一次刷新：刷新令牌会轮转，旧令牌二次使用会被判失效
export function refreshTokenOnce(): Promise<string> {
  if (!refreshingToken) {
    refreshingToken = useAppAccountStore().refreshAccessToken().finally(() => {
      refreshingToken = null
    })
  }
  return refreshingToken
}

// 401 统一处理：先刷新令牌并重放原请求，无刷新条件时才登出
export async function retryAfterRefresh(client: AxiosInstance, config?: InternalAxiosRequestConfig) {
  if (!config || config.skipTokenRefresh || config.tokenRetried) {
    useAppAccountStore().requestLogout()
    return Promise.reject(new Error('登录已过期'))
  }
  config.tokenRetried = true
  try {
    await refreshTokenOnce()
    // 重放时还原明文 body，由请求拦截器重新加密
    config.data = config.apiPlainData
    config.apiEncryptionKey = undefined
    return client(config)
  }
  catch (error) {
    useAppAccountStore().requestLogout()
    return Promise.reject(error)
  }
}
