import type {
  ActivityProofExportRecord,
  ActivityProofMapping,
  ActivityProofParticipant,
  ActivityProofServer,
  ActivityProofSettings,
  ActivityProofStatus,
  ActivityProofTemplate,
  ExportForm,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

const PAGE_SIZE = 200

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

  async function getAllPages<T>(path: string, params: Record<string, string | number | boolean | undefined> = {}) {
    const records: T[] = []
    let page = 1
    while (true) {
      const batch = await sdk.http.get<T[]>(`${path}${query({ ...params, page, size: PAGE_SIZE })}`)
      records.push(...batch)
      if (batch.length < PAGE_SIZE) {
        return records
      }
      page += 1
    }
  }

  return {
    status: () => sdk.http.get<ActivityProofStatus>('/status'),
    servers: () => sdk.http.get<ActivityProofServer[]>('/servers'),
    templates: (keyword = '') => getAllPages<ActivityProofTemplate>('/templates', { keyword }),
    settings: () => sdk.http.get<ActivityProofSettings>('/settings'),
    saveSettings: (data: Record<string, unknown>) => sdk.http.request<ActivityProofSettings>('/settings', { method: 'PUT', data }),
    selectTemplate: (templateId: number) => sdk.http.request<ActivityProofSettings>('/template', { method: 'PUT', data: { templateId } }),
    mappings: (serverId: string) => getAllPages<ActivityProofMapping>('/mappings', { serverId }),
    saveMapping: (data: Record<string, unknown>) => sdk.http.request<ActivityProofMapping>('/mappings', { method: 'PUT', data }),
    deleteMapping: (id: string) => sdk.http.request(`/mappings/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    participants: (serverId: string, minOnlineMinutes: number, includeAfk: boolean) => sdk.http.get<ActivityProofParticipant[]>(`/participants${query({ serverId, minOnlineMinutes, includeAfk })}`),
    exportWord: (data: ExportForm & { serverId: string, selectedPlayerIds: string[] }) => sdk.http.post<ActivityProofExportRecord>('/exports', data),
    exports: (page = 1, size = 20) => sdk.http.get<ActivityProofExportRecord[]>(`/exports${query({ page, size })}`),
    downloadUrl: (path: string) => sdk.http.url(path),
  }
}
