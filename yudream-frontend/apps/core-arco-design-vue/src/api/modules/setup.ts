import axios from 'axios'

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

setupApi.interceptors.response.use(
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

export default {
  status: () => {
    return setupApi.get<unknown, { status: 1; error: ''; data: SetupStatusData }>('api/setup/status')
  },
  init: (data: SetupData) => {
    return setupApi.post<unknown, { status: 1; error: ''; data: null }>('api/setup/init', data)
  },
}
