import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { AlipayConfig, AlipayOrder } from '../types'

export function createAlipayApi(sdk: YuDreamPluginSdk) {
  return {
    config: () => sdk.http.get<AlipayConfig>('/config'),
    saveConfig: (data: Record<string, unknown>) => sdk.http.request<AlipayConfig>('/config', { method: 'PUT', data }),
    orders: () => sdk.http.get<AlipayOrder[]>('/orders?page=1&size=50'),
  }
}
