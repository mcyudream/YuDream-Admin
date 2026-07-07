import type { AxiosError, InternalAxiosRequestConfig } from 'axios'
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
  emailVerified: boolean
  avatar?: string
  createTime?: string
}

export interface PasskeyAuthenticationOptions {
  requestJson: string
  publicKeyJson: string
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

let refreshingToken: Promise<string> | null = null

userApi.interceptors.request.use(async (request) => {
  request.headers['Accept-Language'] = 'zh-CN'
  const token = localStorage.getItem('token')
  if (token) {
    request.headers.Authorization = token
  }
  if (request.apiPlainData === undefined) {
    request.apiPlainData = request.data
  }
  request.data = request.apiPlainData
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
    return userApi(config)
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

export default {
  login: (data: { account: string, password: string }) => {
    return userApi.post<unknown, { status: 1, error: '', data: LoginData }>('api/user/login', {
      username: data.account,
      password: data.password,
    }, { skipTokenRefresh: true })
  },

  startPasskeyAuthentication: (username: string) => {
    return userApi.post<unknown, { status: 1, error: '', data: PasskeyAuthenticationOptions }>('api/user/passkeys/authentication/options', { username }, { skipTokenRefresh: true })
  },

  finishPasskeyAuthentication: (data: { username: string, requestJson: string, responseJson: string }) => {
    return userApi.post<unknown, { status: 1, error: '', data: LoginData }>('api/user/passkeys/authentication', data, { skipTokenRefresh: true })
  },

  refreshToken: (refreshToken: string) => {
    return userApi.post<unknown, { status: 1, error: '', data: LoginData }>('api/user/token/refresh', { refreshToken }, { skipTokenRefresh: true })
  },

  register: (data: { username: string, email: string, password: string, nickname?: string }) => {
    return userApi.post<unknown, { status: 1, error: '', data: RegisterData }>('api/user/register', data, { skipTokenRefresh: true })
  },

  verifyEmail: (token: string) => {
    return userApi.get<unknown, { status: 1, error: '', data: null }>('api/user/verify-email', {
      params: { token },
      skipTokenRefresh: true,
    })
  },

  resendVerificationEmail: () => {
    return userApi.post<unknown, { status: 1, error: '', data: null }>('api/user/me/resend-verification-email')
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
