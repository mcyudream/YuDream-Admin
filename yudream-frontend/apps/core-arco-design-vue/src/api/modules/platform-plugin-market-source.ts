import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type MarketSourceSyncStatus = 'OK' | 'ERROR'

export interface PluginMarketSource {
  id: string
  code: string
  name: string
  rootUrl: string
  tokenConfigured: boolean
  enabled: boolean
  builtIn: boolean
  sortOrder?: number
  syncStatus?: MarketSourceSyncStatus
  syncErrorMessage?: string
  syncedAt?: string
  pluginCount?: number
}

export interface PluginMarketSourcePayload {
  code?: string
  name: string
  rootUrl?: string
  /** 空白表示保留现有令牌。 */
  token?: string
  sortOrder?: number
}

export interface PluginMarketSourceTestResult {
  ok: boolean
  pluginCount?: number
  message?: string
}

export default {
  list: () => systemClient.get<unknown, ApiResponse<PluginMarketSource[]>>('api/platform/plugin-market-sources'),
  create: (data: PluginMarketSourcePayload) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>('api/platform/plugin-market-sources', data),
  update: (id: string, data: PluginMarketSourcePayload) => systemClient.put<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}`, data),
  remove: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/plugin-market-sources/${id}`),
  enable: (id: string) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}/enable`),
  disable: (id: string) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}/disable`),
  sync: (id: string) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}/sync`),
  syncAll: () => systemClient.post<unknown, ApiResponse<PluginMarketSource[]>>('api/platform/plugin-market-sources/sync-all'),
  test: (data: { rootUrl: string, token?: string }) => systemClient.post<unknown, ApiResponse<PluginMarketSourceTestResult>>('api/platform/plugin-market-sources/test', data),
}
