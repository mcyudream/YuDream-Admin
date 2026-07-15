import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'

interface BackendResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

interface SetupStatusData {
  setupCompleted: boolean
}

interface SetupData {
  siteName: string
  adminUsername: string
  adminNickname?: string
  adminEmail: string
  adminPassword: string
  adminConfirmPassword: string
}

const setupApi = axios.create({
  baseURL: (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
})

setupApi.interceptors.request.use(async (request) => {
  const encrypted = await prepareApiEncryption(request.url, request.data)
  if (encrypted) {
    Object.assign(request.headers, encrypted.headers)
    request.data = encrypted.body
    request.apiEncryptionKey = encrypted.key
  }
  return request
})

setupApi.interceptors.response.use(
  async (response) => {
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
  async (error) => {
    if (error.response?.data) {
      error.response.data = await decryptApiResponse(error.response.data, error.config?.apiEncryptionKey)
    }
    const message = error.response?.data?.message || error.message || '网络错误'
    useFaToast().error('错误', { description: message })
    return Promise.reject(error)
  },
)

export default {
  status: () => {
    return setupApi.get<unknown, { status: 1; error: ''; data: SetupStatusData }>('api/setup/status')
  },
  init: (data: SetupData) => {
    return setupApi.post<unknown, { status: 1; error: ''; data: null }>('api/setup/init', data)
  },
}
