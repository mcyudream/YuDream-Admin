import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type MarketSourceSyncStatus = 'OK' | 'ERROR'
export type MarketSourceType = 'LOCAL' | 'STATIC_INDEX' | 'V2_API'

export type PluginPublicationStatus = 'PENDING' | 'PUBLISHED' | 'REJECTED' | 'REVOKED'
export type PluginPublicationChannel = 'UI' | 'PIPELINE'

export interface PluginMarketSource {
  id: string
  code: string
  name: string
  type?: MarketSourceType
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
  type?: MarketSourceType
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

export interface PluginMarketPublication {
  id: string
  code: string
  pluginVersion: string
  displayName?: string
  description?: string
  releaseNotes?: string
  license?: string
  category?: string
  tags?: string[]
  compatibility?: Record<string, string>
  sha256: string
  sizeBytes?: number
  downloadCount?: number
  publisherUserId?: string
  channel: PluginPublicationChannel
  status: PluginPublicationStatus
  reviewNote?: string
  reviewedAt?: string
  createTime?: string
}

export interface PluginMarketPublicationEditPayload {
  displayName?: string
  description?: string
  releaseNotes?: string
  license?: string
  category?: string
  tags?: string[]
  compatibility?: Record<string, string>
}

export const PLUGIN_PUBLICATION_STATUS_OPTIONS: { label: string, value: PluginPublicationStatus }[] = [
  { label: '待审核', value: 'PENDING' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已拒绝', value: 'REJECTED' },
  { label: '已下架', value: 'REVOKED' },
]

export default {
  list: () => systemClient.get<unknown, ApiResponse<PluginMarketSource[]>>('api/platform/plugin-market-sources'),
  create: (data: PluginMarketSourcePayload) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>('api/platform/plugin-market-sources', data),
  update: (id: string, data: PluginMarketSourcePayload) => systemClient.put<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}`, data),
  remove: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/plugin-market-sources/${id}`),
  enable: (id: string) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}/enable`),
  disable: (id: string) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}/disable`),
  sync: (id: string) => systemClient.post<unknown, ApiResponse<PluginMarketSource>>(`api/platform/plugin-market-sources/${id}/sync`),
  syncAll: () => systemClient.post<unknown, ApiResponse<PluginMarketSource[]>>('api/platform/plugin-market-sources/sync-all'),
  test: (data: { rootUrl: string, token?: string, type?: MarketSourceType }) => systemClient.post<unknown, ApiResponse<PluginMarketSourceTestResult>>('api/platform/plugin-market-sources/test', data),
  publications: (params?: { status?: PluginPublicationStatus, mine?: boolean, page?: number, size?: number }) =>
    systemClient.get<unknown, ApiResponse<PageResult<PluginMarketPublication>>>('api/platform/plugin-market-source/publications', { params }),
  uploadPublication: (data: FormData) => systemClient.post<unknown, ApiResponse<PluginMarketPublication>>('api/platform/plugin-market-source/publications', data, { timeout: 0 }),
  acceptPublication: (id: string, note?: string) => systemClient.post<unknown, ApiResponse<PluginMarketPublication>>(`api/platform/plugin-market-source/publications/${id}/accept`, { note }),
  rejectPublication: (id: string, note?: string) => systemClient.post<unknown, ApiResponse<PluginMarketPublication>>(`api/platform/plugin-market-source/publications/${id}/reject`, { note }),
  unpublishPublication: (id: string, note?: string) => systemClient.post<unknown, ApiResponse<PluginMarketPublication>>(`api/platform/plugin-market-source/publications/${id}/unpublish`, { note }),
  updatePublication: (id: string, data: PluginMarketPublicationEditPayload) => systemClient.put<unknown, ApiResponse<PluginMarketPublication>>(`api/platform/plugin-market-source/publications/${id}`, data),
  deletePublication: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/plugin-market-source/publications/${id}`),
  reviewRequired: () => systemClient.get<unknown, ApiResponse<boolean>>('api/platform/plugin-market-source/publications/review-required'),
  updateReviewRequired: (reviewRequired: boolean) => systemClient.put<unknown, ApiResponse<boolean>>('api/platform/plugin-market-source/publications/review-required', { reviewRequired }),
  publicEnabled: () => systemClient.get<unknown, ApiResponse<boolean>>('api/platform/plugin-market-source/publications/public-enabled'),
  updatePublicEnabled: (publicEnabled: boolean) => systemClient.put<unknown, ApiResponse<boolean>>('api/platform/plugin-market-source/publications/public-enabled', { publicEnabled }),
  skipReview: () => systemClient.get<unknown, ApiResponse<boolean>>('api/platform/plugin-market-source/publications/skip-review'),
}
