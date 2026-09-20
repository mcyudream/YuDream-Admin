import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'
import { hintForMessage, toastApiError } from '@/utils/api-error'

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
  baseURL: import.meta.env.VITE_APP_API_BASEURL,
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
    useFaToast().error('错误', { description: hintForMessage(message) })
    return Promise.reject(new Error(message))
  },
  async (error) => {
    if (error.response?.data) {
      error.response.data = await decryptApiResponse(error.response.data, error.config?.apiEncryptionKey)
    }
    await toastApiError(error)
    return Promise.reject(error)
  },
)

export interface SetupIntegrationSaveData {
  mail?: {
    host: string
    port?: number
    username?: string
    password?: string
    from?: string
    ssl?: boolean
    starttls?: boolean
  } | null
  storage?: {
    endpoint: string
    accessKey?: string
    secretKey?: string
    bucket?: string
    region?: string
    pathStyle?: boolean
  } | null
  setupToken?: string
}

export interface SetupProbeData {
  ok: boolean
  message: string
}

export default {
  status: () => {
    return setupApi.get<unknown, { status: 1; error: ''; data: SetupStatusData }>('api/setup/status')
  },
  init: (data: SetupData) => {
    return setupApi.post<unknown, { status: 1; error: ''; data: null }>('api/setup/init', data)
  },
  saveIntegrations: (data: SetupIntegrationSaveData) => {
    return setupApi.put<unknown, { status: 1; error: ''; data: unknown }>('api/setup/integrations', data)
  },
  testMail: (data: SetupIntegrationSaveData['mail'] & { to: string, setupToken?: string }) => {
    return setupApi.post<unknown, { status: 1; error: ''; data: SetupProbeData }>('api/setup/integrations/test-mail', data)
  },
  testStorage: (data: { endpoint: string, accessKey?: string, secretKey?: string, bucket?: string, region?: string, pathStyle?: boolean, autoCreate?: boolean, setupToken?: string }) => {
    return setupApi.post<unknown, { status: 1; error: ''; data: SetupProbeData }>('api/setup/integrations/test-storage', data)
  },
}
