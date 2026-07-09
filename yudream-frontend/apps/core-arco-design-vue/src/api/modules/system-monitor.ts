import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface RedisKeySample {
  key: string
  type: string
  ttl: number
}

export interface RedisMonitor {
  connected: boolean
  version?: string
  dbSize?: number
  uptime?: string
  usedMemory?: string
  maxMemory?: string
  connectedClients?: number
  totalCommands?: number
  opsPerSecond?: number
  keyspaceHits?: number
  keyspaceMisses?: number
  hitRate?: number
  keyspace?: Record<string, string>
  keys: RedisKeySample[]
  message?: string
}

export interface OnlineUser {
  token: string
  userId?: string
  username?: string
  nickname?: string
  email?: string
  timeout?: number
  activeTimeout?: number
  device?: string
}

export interface ApiLog {
  id: string
  method: string
  path: string
  query?: string
  requestBody?: string
  status?: number
  costMs?: number
  success?: boolean
  loginId?: string
  username?: string
  nickname?: string
  ip?: string
  userAgent?: string
  errorMessage?: string
  createTime?: string
}

export interface LoginLog {
  id: string
  username: string
  userId?: string
  success?: boolean
  message?: string
  ip?: string
  userAgent?: string
  token?: string
  createTime?: string
}

export default {
  redis: (params?: { pattern?: string; limit?: number }) => {
    return systemClient.get<unknown, ApiResponse<RedisMonitor>>('api/system/monitor/redis', { params })
  },
  onlineUsers: (params?: { keyword?: string; limit?: number }) => {
    return systemClient.get<unknown, ApiResponse<OnlineUser[]>>('api/system/monitor/online-users', { params })
  },
  kickoutOnlineUser: (token: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/system/monitor/online-users/${encodeURIComponent(token)}`)
  },
  apiLogs: (params?: { keyword?: string; success?: boolean; page?: number; size?: number }) => {
    return systemClient.get<unknown, ApiResponse<PageResult<ApiLog>>>('api/system/monitor/api-logs', { params })
  },
  clearApiLogs: () => {
    return systemClient.delete<unknown, ApiResponse<number>>('api/system/monitor/api-logs')
  },
  loginLogs: (params?: { keyword?: string; success?: boolean; page?: number; size?: number }) => {
    return systemClient.get<unknown, ApiResponse<PageResult<LoginLog>>>('api/system/monitor/login-logs', { params })
  },
  clearLoginLogs: () => {
    return systemClient.delete<unknown, ApiResponse<number>>('api/system/monitor/login-logs')
  },
}
