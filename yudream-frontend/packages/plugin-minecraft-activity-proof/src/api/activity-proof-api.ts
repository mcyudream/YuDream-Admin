import type {
  ActivityProofExportRecord,
  ActivityProofMapping,
  ActivityProofParticipant,
  ActivityProofServer,
  ActivityProofSettings,
  ActivityProofStatus,
  ExportForm,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

export function createActivityProofApi(sdk: YuDreamPluginSdk) {
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
    status: () => sdk.http.get<ActivityProofStatus>('/status'),
    servers: () => sdk.http.get<ActivityProofServer[]>('/servers'),
    settings: () => sdk.http.get<ActivityProofSettings>('/settings'),
    saveSettings: (data: Record<string, unknown>) => sdk.http.request<ActivityProofSettings>('/settings', { method: 'PUT', data }),
    uploadTemplate: (data: Record<string, unknown>) => sdk.http.request<ActivityProofSettings>('/template', { method: 'PUT', data }),
    mappings: (serverId: string, page = 1, size = 1000) => sdk.http.get<ActivityProofMapping[]>(`/mappings${query({ serverId, page, size })}`),
    saveMapping: (data: Record<string, unknown>) => sdk.http.request<ActivityProofMapping>('/mappings', { method: 'PUT', data }),
    deleteMapping: (id: string) => sdk.http.request(`/mappings/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    participants: (serverId: string, minOnlineMinutes: number, includeAfk: boolean) => sdk.http.get<ActivityProofParticipant[]>(`/participants${query({ serverId, minOnlineMinutes, includeAfk })}`),
    exportWord: (data: ExportForm & { serverId: string, selectedPlayerIds: string[] }) => sdk.http.post<ActivityProofExportRecord>('/exports', data),
    exports: (page = 1, size = 20) => sdk.http.get<ActivityProofExportRecord[]>(`/exports${query({ page, size })}`),
    downloadUrl: (path: string) => sdk.http.url(path),
  }
}
