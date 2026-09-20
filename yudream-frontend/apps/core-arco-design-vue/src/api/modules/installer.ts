import axios from 'axios'

interface BackendResult<T> {
  code: number
  message: string
  data: T
}

export interface InstallerStatusData {
  installerMode: boolean
  bootstrapFileLocation: string
  dockerDeployment: boolean
  javaVersion: string
  setupTokenRequired: boolean
}

export interface MiddlewareProbeData {
  target: string
  kind: string
  reachable: boolean
  authRequired: boolean
  authFailed: boolean
  version: string | null
  latencyMs: number
  message: string
}

export interface MiddlewareDiscoveryData {
  mongo: MiddlewareProbeData[]
  redis: MiddlewareProbeData[]
}

export interface InstallerApplyData {
  restarting: boolean
  bootstrapFileLocation: string
  restartDelayMs: number
}

export interface InstallerApplyPayload {
  mongoUri: string
  redisHost: string
  redisPort?: number
  redisPassword?: string
  redisDatabase?: number
  redisSsl?: boolean
  credentialKey?: string
  snowflakeDataCenterId?: number
  snowflakeMachineId?: number
  setupToken?: string
}

// 安装器切片没有接口加密过滤器与全局异常处理：使用裸 axios 并自行解包 Result，
// 404 视为「后端不是安装器模式」的正常信号，不得弹错误提示。
const installerApi = axios.create({
  baseURL: import.meta.env.VITE_APP_API_BASEURL,
  timeout: 1000 * 60,
})

export function installerErrorMessage(error: any): string {
  return error?.response?.data?.message || error?.message || '请求失败'
}

export default {
  /** 探测后端是否处于安装器模式；返回 null 表示非安装器模式（接口 404）。 */
  async detectStatus(): Promise<InstallerStatusData | null> {
    try {
      const res = await installerApi.get<BackendResult<InstallerStatusData>>('api/installer/status')
      return res.data?.data ?? null
    }
    catch (error: any) {
      if (error?.response?.status === 404) {
        return null
      }
      throw error
    }
  },
  async discover(): Promise<MiddlewareDiscoveryData> {
    const res = await installerApi.post<BackendResult<MiddlewareDiscoveryData>>('api/installer/discover')
    return res.data.data
  },
  async probeMongo(uri: string): Promise<MiddlewareProbeData> {
    const res = await installerApi.post<BackendResult<MiddlewareProbeData>>('api/installer/probe/mongo', { uri })
    return res.data.data
  },
  async probeRedis(payload: { host: string, port?: number, password?: string, database?: number }): Promise<MiddlewareProbeData> {
    const res = await installerApi.post<BackendResult<MiddlewareProbeData>>('api/installer/probe/redis', payload)
    return res.data.data
  },
  async apply(payload: InstallerApplyPayload): Promise<InstallerApplyData> {
    const res = await installerApi.post<BackendResult<InstallerApplyData>>('api/installer/apply', payload)
    return res.data.data
  },
}
