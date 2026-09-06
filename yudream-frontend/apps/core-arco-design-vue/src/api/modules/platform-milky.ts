import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type MilkyProtocol = 'milky' | 'official'

export interface MilkyConnection {
  id: string
  name: string
  protocol?: MilkyProtocol
  baseUrl: string
  appId?: string
  sandbox?: boolean
  intents?: number
  enabled: boolean
  credentialConfigured: boolean
  commandMenuImageMode?: 'base64' | 'url'
  commandMenuPublicBaseUrl?: string
  createTime?: string
  updateTime?: string
}

export interface MilkyConnectionPayload {
  name: string
  protocol?: MilkyProtocol
  baseUrl?: string
  token?: string
  appId?: string
  appSecret?: string
  sandbox?: boolean
  intents?: number
  commandMenuImageMode?: 'base64' | 'url'
  commandMenuPublicBaseUrl?: string
}

export default {
  page: (params: { page: number; size: number; keyword?: string }) => systemClient.get<unknown, ApiResponse<PageResult<MilkyConnection>>>('api/platform/milky/connections', { params }),
  create: (data: MilkyConnectionPayload) => systemClient.post<unknown, ApiResponse<MilkyConnection>>('api/platform/milky/connections', data),
  update: (id: string, data: MilkyConnectionPayload) => systemClient.put<unknown, ApiResponse<MilkyConnection>>(`api/platform/milky/connections/${id}`, data),
  enable: (id: string) => systemClient.post<unknown, ApiResponse<MilkyConnection>>(`api/platform/milky/connections/${id}/enable`),
  disable: (id: string) => systemClient.post<unknown, ApiResponse<MilkyConnection>>(`api/platform/milky/connections/${id}/disable`),
  test: (id: string) => systemClient.post<unknown, ApiResponse<Record<string, unknown>>>(`api/platform/milky/connections/${id}/test`),
}
