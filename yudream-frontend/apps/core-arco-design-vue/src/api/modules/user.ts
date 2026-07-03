import axios from 'axios'
import { decryptApiResponse, prepareApiEncryption } from '@/utils/api-encryption'

interface BackendResult<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface LoginData {
  token: string
  tokenName: string
  refreshToken?: string
  dualTokenEnabled?: boolean
  expiresIn?: number
  userId: IdValue
  username: string
  nickname?: string
  email?: string
  avatar?: string
  createTime?: string
}

export type IdValue = string | number

interface RegisterData {
  username: string
  email: string
  nickname?: string
  emailVerified: boolean
}

export interface DeptItem {
  id: IdValue
  name: string
  current: boolean
  defaultDept: boolean
}

export interface RoleItem {
  id: IdValue
  name: string
  code: string
  current: boolean
}

export interface ContextData {
  currentDept: DeptItem | null
  currentRole: RoleItem | null
}

const userApi = axios.create({
  baseURL: (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
})

userApi.interceptors.request.use(async (request) => {
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

userApi.interceptors.response.use(
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
  (error) => {
    const message = error.response?.data?.message || error.message || '网络错误'
    useFaToast().error('错误', { description: message })
    return Promise.reject(error)
  },
)

export default {
  login: (data: { account: string, password: string }) => {
    return userApi.post<unknown, { status: 1, error: '', data: LoginData }>('api/user/login', {
      username: data.account,
      password: data.password,
    })
  },

  refreshToken: (refreshToken: string) => {
    return userApi.post<unknown, { status: 1, error: '', data: LoginData }>('api/user/token/refresh', { refreshToken })
  },

  register: (data: { username: string, email: string, password: string, nickname?: string }) => {
    return userApi.post<unknown, { status: 1, error: '', data: RegisterData }>('api/user/register', data)
  },

  verifyEmail: (token: string) => {
    return userApi.get<unknown, { status: 1, error: '', data: null }>('api/user/verify-email', {
      params: { token },
    })
  },

  listDepts: () => {
    return userApi.get<unknown, { status: 1, error: '', data: DeptItem[] }>('api/user/me/depts')
  },

  listRoles: () => {
    return userApi.get<unknown, { status: 1, error: '', data: RoleItem[] }>('api/user/me/roles')
  },

  getContext: () => {
    return userApi.get<unknown, { status: 1, error: '', data: ContextData }>('api/user/me/context')
  },

  switchDept: (deptId: IdValue) => {
    return userApi.post<unknown, { status: 1, error: '', data: null }>('api/user/me/switch-dept', { deptId })
  },

  switchRole: (roleId: IdValue) => {
    return userApi.post<unknown, { status: 1, error: '', data: null }>('api/user/me/switch-role', { roleId })
  },
}
