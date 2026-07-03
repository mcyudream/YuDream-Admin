import axios from 'axios'

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

systemClient.interceptors.request.use((request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  return request
})

systemClient.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob') {
      return Promise.resolve(response as any)
    }
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
