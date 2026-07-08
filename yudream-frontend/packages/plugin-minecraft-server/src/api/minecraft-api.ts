import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EconomyRecord, MinecraftServer, MinecraftStatusSnapshot, PlayerActivity, SeasonOperation } from '../types'

export function createMinecraftApi(sdk: YuDreamPluginSdk) {
  function query(params: Record<string, string | number | boolean | undefined>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        search.set(key, String(value))
      }
    })
    const value = search.toString()
    return value ? `?${value}` : ''
  }

  return {
    list: (includeDisabled = false, refresh = false) => sdk.http.get<MinecraftServer[]>(`/servers${query({ includeDisabled, refresh })}`),
    detail: (id: string, refresh = false) => sdk.http.get<MinecraftServer>(`/servers/${encodeURIComponent(id)}${query({ refresh })}`),
    save: (data: Record<string, unknown>) => sdk.http.post<MinecraftServer>('/servers', data),
    remove: (id: string) => sdk.http.request(`/servers/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    refreshStatus: (id: string) => sdk.http.post<MinecraftServer>(`/servers/${encodeURIComponent(id)}/status/refresh`),
    statusHistory: (id: string, since?: number, limit?: number) => sdk.http.get<MinecraftStatusSnapshot[]>(`/servers/${encodeURIComponent(id)}/status/history${query({ since, limit })}`),
    economyStatus: () => sdk.http.get<{ walletEnabled: boolean }>('/economy/status'),
    previewSeason: (id: string, data: Record<string, unknown>) => sdk.http.post<SeasonOperation>(`/servers/${encodeURIComponent(id)}/seasons/preview`, data),
    openSeason: (id: string, data: Record<string, unknown>) => sdk.http.post<SeasonOperation>(`/servers/${encodeURIComponent(id)}/seasons/open`, data),
    operations: (id: string, page = 1, size = 20) => sdk.http.get<SeasonOperation[]>(`/servers/${encodeURIComponent(id)}/operations${query({ page, size })}`),
    rollbackOperation: (operationId: string) => sdk.http.post<SeasonOperation>(`/operations/${encodeURIComponent(operationId)}/rollback`),
    myRecords: (id: string, page = 1, size = 20) => sdk.http.get<EconomyRecord[]>(`/servers/${encodeURIComponent(id)}/my-records${query({ page, size })}`),
    playerActivities: (id: string, page = 1, size = 20) => sdk.http.get<PlayerActivity[]>(`/servers/${encodeURIComponent(id)}/players${query({ page, size })}`),
  }
}
