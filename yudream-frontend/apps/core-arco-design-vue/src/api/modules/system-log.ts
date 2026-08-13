import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface SystemLogItem {
  sequence: number
  timestamp: number
  time: string
  level: 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'
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

export default {
  page: (params?: SystemLogQuery) => {
    return systemClient.get<unknown, ApiResponse<PageResult<SystemLogItem>>>('api/system/logs', { params })
  },
  modules: () => {
    return systemClient.get<unknown, ApiResponse<string[]>>('api/system/logs/modules')
  },
  stats: () => {
    return systemClient.get<unknown, ApiResponse<SystemLogStats>>('api/system/logs/stats')
  },
  clear: () => {
    return systemClient.delete<unknown, ApiResponse<number>>('api/system/logs')
  },
  download: (params?: { level?: string; modules?: string; keyword?: string }) => {
    return systemClient.get<unknown, { data: Blob; headers: Record<string, string> }>('api/system/logs/download', {
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
