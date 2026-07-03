import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'

declare module 'axios' {
  export interface AxiosRequestConfig {
    apiEncryptionKey?: CryptoKey
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

systemClient.interceptors.request.use(async (request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  if (request.responseType !== 'blob') {
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
    const message = result?.message || '请求失败'
    useFaToast().error('错误', { description: message })
    return Promise.reject(new Error(message))
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络错误'
    useFaToast().error('错误', { description: message })
    return Promise.reject(error)
  },
)

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
