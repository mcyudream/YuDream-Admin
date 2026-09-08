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

export interface HostDisk {
  name?: string
  mount?: string
  type?: string
  totalBytes?: number
  usedBytes?: number
  usableBytes?: number
  usagePercent?: number
}

export interface HostNetwork {
  name?: string
  displayName?: string
  ipv4?: string
  mac?: string
  recvBytes?: number
  sentBytes?: number
  recvBytesPerSec?: number
  sentBytesPerSec?: number
}

export interface JvmMemoryPool {
  name?: string
  type?: string
  usedBytes?: number
  committedBytes?: number
  maxBytes?: number
  usagePercent?: number
}

export interface JvmGc {
  name?: string
  collectionCount?: number
  collectionTimeMs?: number
}

export interface HostProcess {
  pid?: number
  name?: string
  user?: string
  rssBytes?: number
  virtualBytes?: number
  cpuPercent?: number
  currentJvm?: boolean
  commandLine?: string
}

export interface HostThreadHotspot {
  threadId?: string
  name?: string
  state?: string
  cpuTimeMs?: number
  userTimeMs?: number
  daemon?: boolean
  stackTop?: string[]
}

export interface HostResourceSnapshot {
  hostname?: string
  osName?: string
  osVersion?: string
  architecture?: string
  cpuName?: string
  cpuPhysicalCount?: number
  cpuLogicalCount?: number
  cpuUsagePercent?: number
  load1?: number
  load5?: number
  load15?: number
  memoryTotalBytes?: number
  memoryUsedBytes?: number
  memoryAvailableBytes?: number
  memoryUsagePercent?: number
  swapTotalBytes?: number
  swapUsedBytes?: number
  disks: HostDisk[]
  diskTotalBytes?: number
  diskUsedBytes?: number
  diskUsagePercent?: number
  networks: HostNetwork[]
  networkRecvBytesPerSec?: number
  networkSentBytesPerSec?: number
  jvmPid?: string
  javaVersion?: string
  jvmUptimeMs?: number
  jvmHeapUsedBytes?: number
  jvmHeapMaxBytes?: number
  jvmNonHeapUsedBytes?: number
  jvmNonHeapMaxBytes?: number
  jvmDirectUsedBytes?: number
  jvmDirectMaxBytes?: number
  jvmThreadCount?: number
  jvmDaemonThreadCount?: number
  jvmLoadedClassCount?: number
  memoryPools: JvmMemoryPool[]
  garbageCollectors: JvmGc[]
  topProcesses: HostProcess[]
  topThreads: HostThreadHotspot[]
  notice?: string
  sampledAt?: string
}

export interface ResourceMetricPoint {
  sampledAt?: string
  cpuUsagePercent?: number
  memoryUsedBytes?: number
  memoryTotalBytes?: number
  memoryUsagePercent?: number
  swapUsedBytes?: number
  swapTotalBytes?: number
  diskUsedBytes?: number
  diskTotalBytes?: number
  diskUsagePercent?: number
  networkRecvBytesPerSec?: number
  networkSentBytesPerSec?: number
  jvmHeapUsedBytes?: number
  jvmHeapMaxBytes?: number
  jvmNonHeapUsedBytes?: number
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
  resource: () => {
    return systemClient.get<unknown, ApiResponse<HostResourceSnapshot>>('api/system/monitor/resource')
  },
  resourceHistory: (params?: { hours?: number }) => {
    return systemClient.get<unknown, ApiResponse<ResourceMetricPoint[]>>('api/system/monitor/resource/history', { params })
  },
}
