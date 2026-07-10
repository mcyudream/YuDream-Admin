import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface SatoriConnection {
  id: string
  name: string
  baseUrl: string
  platform: string
  userId: string
  enabled: boolean
  credentialConfigured: boolean
  createTime?: string
  updateTime?: string
}

export interface SatoriConnectionPayload {
  name: string
  baseUrl: string
  platform: string
  userId: string
  token?: string
}

export interface SatoriConnectionTest {
  success: boolean
  platform: string
  userId: string
  status?: string
  adapter?: string
  features: string[]
  testedAt?: string
}

export interface SatoriOperationLog {
  id: string
  level: 'INFO' | 'WARN' | 'ERROR'
  category: string
  action: string
  detail?: string
  occurredAt?: string
}

export default {
  pageConnections: (params: { page: number, size: number, keyword?: string }) => systemClient.get<unknown, ApiResponse<PageResult<SatoriConnection>>>('api/platform/satori/connections', { params }),
  createConnection: (data: SatoriConnectionPayload) => systemClient.post<unknown, ApiResponse<SatoriConnection>>('api/platform/satori/connections', data),
  updateConnection: (id: string, data: SatoriConnectionPayload) => systemClient.put<unknown, ApiResponse<SatoriConnection>>(`api/platform/satori/connections/${id}`, data),
  enableConnection: (id: string) => systemClient.post<unknown, ApiResponse<SatoriConnection>>(`api/platform/satori/connections/${id}/enable`),
  disableConnection: (id: string) => systemClient.post<unknown, ApiResponse<SatoriConnection>>(`api/platform/satori/connections/${id}/disable`),
  testConnection: (id: string) => systemClient.post<unknown, ApiResponse<SatoriConnectionTest>>(`api/platform/satori/connections/${id}/test`),
  pageConnectionLogs: (id: string, params: { page: number, size: number }) => systemClient.get<unknown, ApiResponse<PageResult<SatoriOperationLog>>>(`api/platform/satori/connections/${id}/logs`, { params }),
  render: (data: { sourceType: 'HTML' | 'MARKDOWN' | 'URL', content: string, width?: number, transparent?: boolean }) => systemClient.post<Blob>('api/platform/render', data, { responseType: 'blob' }),
}
