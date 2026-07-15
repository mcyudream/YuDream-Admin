import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'

interface BackendResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

const settingsApi = axios.create({
  baseURL: (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
})

settingsApi.interceptors.response.use(
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

settingsApi.interceptors.request.use(async (request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  const encrypted = await prepareApiEncryption(request.url, request.data)
  if (encrypted) {
    Object.assign(request.headers, encrypted.headers)
    request.data = encrypted.body
    request.apiEncryptionKey = encrypted.key
  }
  return request
})

export interface SiteSetting {
  siteName: string
  siteDescription?: string
  logo?: string
  favicon?: string
  loginBanner?: string
  copyrightCompany?: string
  copyrightWebsite?: string
  copyrightDates?: string
}

export interface ThemeSetting {
  config: Record<string, any>
}

export interface FrontendFeature {
  apiKeyEnabled: boolean
  passkeyEnabled: boolean
  oauthServerEnabled: boolean
  oauthClientEnabled: boolean
  capabilities: Record<string, boolean>
}

export default {
  publicSettings: () => {
    return settingsApi.get<unknown, { status: 1; error: ''; data: SiteSetting }>('api/settings/public')
  },
  features: () => {
    return settingsApi.get<unknown, { status: 1; error: ''; data: FrontendFeature }>('api/settings/features')
  },
  site: () => {
    return settingsApi.get<unknown, { status: 1; error: ''; data: SiteSetting }>('api/system/settings/site')
  },
  updateSite: (data: SiteSetting) => {
    return settingsApi.put<unknown, { status: 1; error: ''; data: SiteSetting }>('api/system/settings/site', data)
  },
  uploadLogo: (data: FormData) => {
    return settingsApi.post<unknown, { status: 1; error: ''; data: SiteSetting }>('api/system/settings/site/logo', data)
  },
  uploadFavicon: (data: FormData) => {
    return settingsApi.post<unknown, { status: 1; error: ''; data: SiteSetting }>('api/system/settings/site/favicon', data)
  },
  uploadLoginBanner: (data: FormData) => {
    return settingsApi.post<unknown, { status: 1; error: ''; data: SiteSetting }>('api/system/settings/site/login-banner', data)
  },
  publicTheme: () => {
    return settingsApi.get<unknown, { status: 1; error: ''; data: ThemeSetting }>('api/settings/theme')
  },
  theme: () => {
    return settingsApi.get<unknown, { status: 1; error: ''; data: ThemeSetting }>('api/system/settings/theme')
  },
  updateTheme: (data: ThemeSetting) => {
    return settingsApi.put<unknown, { status: 1; error: ''; data: ThemeSetting }>('api/system/settings/theme', data)
  },
}
