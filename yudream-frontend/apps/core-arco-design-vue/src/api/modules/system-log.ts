import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

/** 日志级别词表：与后端 SystemLogLevel 枚举一一对应 */
export type SystemLogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

export interface SystemLogItem {
  sequence: number
  timestamp: number
  time: string
  level: SystemLogLevel
  module: string
  thread: string
  traceId?: string
  logger: string
  message: string
  throwable?: string
}

export interface SystemLogStats {
  size: number
  droppedCount: number
  maxEntries: number
  /** 缓存内各级别条数（键为级别名）；后端 Long 走全局序列化，读到的是字符串，算术前须 Number() */
  levelCounts: Record<string, number | string>
}

export interface DockerLogSettings {
  enabled: boolean
  containers: string[]
  transport: string
  socket: string
  tail: number
}

export interface SystemLogQuery {
  level?: string
  modules?: string
  keyword?: string
  page?: number
  size?: number
}

export interface SystemLogModuleGroup {
  label: string
  modules: string[]
}

export default {
  page: (params?: SystemLogQuery) => {
    return systemClient.get<unknown, ApiResponse<PageResult<SystemLogItem>>>('api/system/logs', { params })
  },
  modules: () => {
    return systemClient.get<unknown, ApiResponse<string[]>>('api/system/logs/modules')
  },
  moduleGroups: () => {
    return systemClient.get<unknown, ApiResponse<SystemLogModuleGroup[]>>('api/system/logs/module-groups')
  },
  stats: () => {
    return systemClient.get<unknown, ApiResponse<SystemLogStats>>('api/system/logs/stats')
  },
  clear: () => {
    return systemClient.delete<unknown, ApiResponse<number>>('api/system/logs')
  },
  download: (params?: { level?: string, modules?: string, keyword?: string }) => {
    return systemClient.get<unknown, { data: Blob, headers: Record<string, string> }>('api/system/logs/download', {
      params,
      responseType: 'blob',
    })
  },
  dockerSettings: () => {
    return systemClient.get<unknown, ApiResponse<DockerLogSettings>>('api/system/logs/docker-settings')
  },
  updateDockerSettings: (data: DockerLogSettings) => {
    return systemClient.put<unknown, ApiResponse<DockerLogSettings>>('api/system/logs/docker-settings', data)
  },
}
