import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { EconomyRecord, MinecraftServer, SeasonOperation } from '../types'

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
    previewSeason: (id: string, data: Record<string, unknown>) => sdk.http.post<SeasonOperation>(`/servers/${encodeURIComponent(id)}/seasons/preview`, data),
    openSeason: (id: string, data: Record<string, unknown>) => sdk.http.post<SeasonOperation>(`/servers/${encodeURIComponent(id)}/seasons/open`, data),
    operations: (id: string) => sdk.http.get<SeasonOperation[]>(`/servers/${encodeURIComponent(id)}/operations`),
    rollbackOperation: (operationId: string) => sdk.http.post<SeasonOperation>(`/operations/${encodeURIComponent(operationId)}/rollback`),
    myRecords: (id: string) => sdk.http.get<EconomyRecord[]>(`/servers/${encodeURIComponent(id)}/my-records`),
  }
}
