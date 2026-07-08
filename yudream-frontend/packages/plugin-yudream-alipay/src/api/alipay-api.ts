import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { AlipayConfig, AlipayOrder } from '../types'

const PAGE_SIZE = 200

export function createAlipayApi(sdk: YuDreamPluginSdk) {
  return {
    config: () => sdk.http.get<AlipayConfig>('/config'),
    saveConfig: (data: AlipayConfig) => sdk.http.request<AlipayConfig>('/config', { method: 'PUT', data }),
    orders: () => getAllPages<AlipayOrder>(sdk, '/orders'),
  }
}

async function getAllPages<T>(sdk: YuDreamPluginSdk, path: string) {
  const records: T[] = []
  let page = 1
  while (true) {
    const batch = await sdk.http.get<T[]>(`${path}?page=${page}&size=${PAGE_SIZE}`)
    records.push(...batch)
    if (batch.length < PAGE_SIZE) {
      return records
    }
    page += 1
  }
}
