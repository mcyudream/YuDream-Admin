import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface ApiDocSettings {
  id?: number | string
  enabled: boolean
  apiKeyAccessEnabled: boolean
  title: string
  description?: string
  version: string
  openApiPath: string
  swaggerUiPath: string
  updateTime?: string
}

export default {
  settings: () => systemClient.get<unknown, ApiResponse<ApiDocSettings>>('api/platform/docs/settings'),
  update: (data: ApiDocSettings) => systemClient.put<unknown, ApiResponse<ApiDocSettings>>('api/platform/docs/settings', data),
}
