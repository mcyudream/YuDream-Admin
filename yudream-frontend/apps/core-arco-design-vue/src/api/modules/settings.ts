import axios from 'axios'

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
  (response) => {
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

settingsApi.interceptors.request.use((request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  return request
})

export interface SiteSetting {
  siteName: string
  siteDescription?: string
  logo?: string
  favicon?: string
  copyrightCompany?: string
  copyrightWebsite?: string
  copyrightDates?: string
}

export interface ThemeSetting {
  config: Record<string, any>
}

export default {
  publicSettings: () => {
    return settingsApi.get<unknown, { status: 1; error: ''; data: SiteSetting }>('api/settings/public')
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
