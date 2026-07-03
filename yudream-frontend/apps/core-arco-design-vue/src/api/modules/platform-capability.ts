import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type CapabilityType = 'REALTIME' | 'MESSAGING' | 'DOCUMENTATION'
export type CapabilityStatus = 'ENABLED' | 'DISABLED' | 'ERROR'

export interface CapabilityItem {
  code: string
  name: string
  type: CapabilityType
  description?: string
  icon?: string
  sort?: number
  enabled: boolean
  config: Record<string, string>
  status: CapabilityStatus
  healthMessage?: string
  checkedAt?: string
  metrics: Record<string, any>
}

export interface CapabilityTestResult {
  success: boolean
  message: string
  testedAt?: string
}

export default {
  list: () => {
    return systemClient.get<unknown, ApiResponse<CapabilityItem[]>>('api/platform/capabilities')
  },
  updateConfig: (code: string, config: Record<string, string>) => {
    return systemClient.put<unknown, ApiResponse<CapabilityItem>>(`api/platform/capabilities/${code}/config`, { config })
  },
  enable: (code: string) => {
    return systemClient.post<unknown, ApiResponse<CapabilityItem>>(`api/platform/capabilities/${code}/enable`)
  },
  disable: (code: string) => {
    return systemClient.post<unknown, ApiResponse<CapabilityItem>>(`api/platform/capabilities/${code}/disable`)
  },
  test: (code: string, message: string) => {
    return systemClient.post<unknown, ApiResponse<CapabilityTestResult>>(`api/platform/capabilities/${code}/test`, { message })
  },
}
