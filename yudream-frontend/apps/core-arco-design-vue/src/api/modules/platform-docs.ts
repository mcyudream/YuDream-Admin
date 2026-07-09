import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface ApiDocSettings {
  id?: string
  enabled: boolean
  apiKeyAccessEnabled: boolean
  title: string
  description?: string
  version: string
  openApiPath: string
  swaggerUiPath: string
  updateTime?: string
}

export interface ApiDocAccessTicket {
  ticket: string
  expiresIn: number
}

export default {
  settings: () => systemClient.get<unknown, ApiResponse<ApiDocSettings>>('api/platform/docs/settings'),
  accessTicket: () => systemClient.get<unknown, ApiResponse<ApiDocAccessTicket>>('api/platform/docs/access-ticket'),
  update: (data: ApiDocSettings) => systemClient.put<unknown, ApiResponse<ApiDocSettings>>('api/platform/docs/settings', data),
}
