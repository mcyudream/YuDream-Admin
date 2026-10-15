import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type CapabilityType = 'REALTIME' | 'MESSAGING' | 'DOCUMENTATION' | 'INTEGRATION' | 'DOCUMENT' | 'CONTENT' | 'GRAPH' | 'AI' | 'DISTRIBUTION'
export type CapabilityStatus = 'ENABLED' | 'DISABLED' | 'ERROR'

export interface CapabilityItem {
  code: string
  name: string
  type: CapabilityType
  description?: string
  icon?: string
  sort?: number
  enabled: boolean
  dependencies?: string[]
  config: Record<string, string>
  secretConfigured?: Record<string, boolean>
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

export interface AiModelDiscoveryPayload {
  providerCode?: string
  usage: 'chat' | 'embedding' | 'rerank'
  protocol?: string
  baseUrl?: string
  apiKey?: string
  proxyUrl?: string
}

export interface AiModelDiscoveryResult {
  models: { code: string, name: string, kind: string }[]
  listed: boolean
  message?: string
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
  /** 端点模型发现：按用途/协议请求远端模型列表，供「获取模型」登记。 */
  discoverModels: (payload: AiModelDiscoveryPayload) => {
    return systemClient.post<unknown, ApiResponse<AiModelDiscoveryResult>>('api/platform/ai/endpoints/discover-models', payload)
  },
}
