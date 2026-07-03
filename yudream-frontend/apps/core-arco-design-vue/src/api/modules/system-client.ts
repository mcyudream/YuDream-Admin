import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'

declare module 'axios' {
  export interface AxiosRequestConfig {
    apiEncryptionKey?: CryptoKey
    apiPlainData?: unknown
    skipTokenRefresh?: boolean
    tokenRetried?: boolean
  }
}

interface BackendResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

const systemClient = axios.create({
  baseURL: (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
})

let refreshingToken: Promise<string> | null = null

systemClient.interceptors.request.use(async (request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  if (request.apiPlainData === undefined) {
    request.apiPlainData = request.data
  }
  if (request.responseType !== 'blob') {
    request.data = request.apiPlainData
    const encrypted = await prepareApiEncryption(request.url, request.data)
    if (encrypted) {
      Object.assign(request.headers, encrypted.headers)
      request.data = encrypted.body
      request.apiEncryptionKey = encrypted.key
    }
  }
  return request
})

systemClient.interceptors.response.use(
  async (response) => {
    if (response.config.responseType === 'blob') {
      return Promise.resolve(response as any)
    }
    response.data = await decryptApiResponse(response.data, response.config.apiEncryptionKey)
    const result = response.data as BackendResult<any>
    if (result && result.code === 200) {
      return Promise.resolve({
        status: 1,
        error: '',
        data: result.data,
      } as any)
    }
    if (result?.code === 401) {
      return retryAfterRefresh(response.config)
    }
    const message = result?.message || '请求失败'
    useFaToast().error('错误', { description: message })
    return Promise.reject(new Error(message))
  },
  async (error: AxiosError) => {
    if (error.response?.data) {
      error.response.data = await decryptApiResponse(error.response.data, error.config?.apiEncryptionKey)
    }
    if (error.response?.status === 401) {
      return retryAfterRefresh(error.config)
    }
    const data = error.response?.data as BackendResult<unknown> | undefined
    const message = data?.message || error.message || '网络错误'
    useFaToast().error('错误', { description: message })
    return Promise.reject(error)
  },
)

async function retryAfterRefresh(config?: InternalAxiosRequestConfig) {
  if (!config || config.skipTokenRefresh || config.tokenRetried) {
    useAppAccountStore().requestLogout()
    return Promise.reject(new Error('登录已过期'))
  }
  config.tokenRetried = true
  try {
    await refreshTokenOnce()
    config.data = config.apiPlainData
    config.apiEncryptionKey = undefined
    return systemClient(config)
  }
  catch (error) {
    useAppAccountStore().requestLogout()
    return Promise.reject(error)
  }
}

async function refreshTokenOnce() {
  if (!refreshingToken) {
    refreshingToken = useAppAccountStore().refreshAccessToken().finally(() => {
      refreshingToken = null
    })
  }
  return refreshingToken
}

export interface ApiResponse<T> {
  status: 1
  error: ''
  data: T
}

export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

export default systemClient
