import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MigrationStatus, SkinClosetItem, SkinMe, SkinPlayer, SkinSettings, SkinSummary, SkinTexture } from '../types'

const PAGE_SIZE = 200

export function createSkinApi(sdk: YuDreamPluginSdk) {
  return {
    status: () => sdk.http.get<SkinSummary>('/status'),
    me: () => sdk.http.get<SkinMe>('/me'),
    players: () => getAllPages<SkinPlayer>(sdk, '/me/players'),
    adminPlayers: () => getAllPages<SkinPlayer>(sdk, '/admin/players'),
    textures: () => getAllPages<SkinTexture>(sdk, '/textures'),
    adminTextures: async () => {
      try {
        return await getAllPages<SkinTexture>(sdk, '/admin/textures')
      }
      catch {
        return getAllPages<SkinTexture>(sdk, '/textures')
      }
    },
    closet: () => getAllPages<SkinClosetItem>(sdk, '/me/closet'),
    adminCloset: () => getAllPages<SkinClosetItem>(sdk, '/admin/closet'),
    settings: () => sdk.http.get<SkinSettings>('/settings'),
    createPlayer: (data: Record<string, unknown>) => sdk.http.post<SkinPlayer>('/me/players', data),
    createAdminPlayer: (data: Record<string, unknown>) => sdk.http.post<SkinPlayer>('/admin/players', data),
    renamePlayer: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/me/players/${encodeURIComponent(name)}/name`, { method: 'PUT', data }),
    renameAdminPlayer: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/admin/players/${encodeURIComponent(name)}/name`, { method: 'PUT', data }),
    deletePlayer: (name: string) => sdk.http.request(`/me/players/${encodeURIComponent(name)}`, { method: 'DELETE' }),
    deleteAdminPlayer: (name: string) => sdk.http.request(`/admin/players/${encodeURIComponent(name)}`, { method: 'DELETE' }),
    assignTextures: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/me/players/${encodeURIComponent(name)}/textures`, { method: 'PUT', data }),
    setDefaultPlayer: (data: Record<string, unknown>) => sdk.http.request<SkinPlayer>('/me/default-player', { method: 'PUT', data }),
    assignAdminTextures: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/admin/players/${encodeURIComponent(name)}/textures`, { method: 'PUT', data }),
    uploadTexture: (data: Record<string, unknown>) => sdk.http.post<SkinTexture>('/me/textures', data),
    uploadAdminTexture: (data: Record<string, unknown>) => sdk.http.post<SkinTexture>('/admin/textures', data),
    deleteAdminTexture: (hash: string) => sdk.http.request(`/admin/textures/${encodeURIComponent(hash)}`, { method: 'DELETE' }),
    saveClosetItem: (data: Record<string, unknown>) => sdk.http.post<SkinClosetItem>('/me/closet', data),
    saveAdminClosetItem: (data: Record<string, unknown>) => sdk.http.post<SkinClosetItem>('/admin/closet', data),
    renameClosetItem: (id: string, data: Record<string, unknown>) => sdk.http.request<SkinClosetItem>(`/me/closet/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    renameAdminClosetItem: (id: string, data: Record<string, unknown>) => sdk.http.request<SkinClosetItem>(`/admin/closet/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    deleteClosetItem: (id: string) => sdk.http.request(`/me/closet/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    deleteAdminClosetItem: (id: string) => sdk.http.request(`/admin/closet/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    saveSettings: (data: Record<string, unknown>) => sdk.http.request<SkinSettings>('/settings', { method: 'PUT', data }),
    migrate: (data: Record<string, unknown>) => sdk.http.post<MigrationStatus>('/migration/blessing-skin', data),
    migrationStatus: () => sdk.http.get<MigrationStatus>('/migration/blessing-skin/status'),
    migrationEventsUrl: () => sdk.http.url('/migration/blessing-skin/events'),
    pluginUrl: (path = '/') => sdk.http.url(path),
    textureUrl: (hash?: string) => (hash ? sdk.http.url(`/textures/${hash}`) : ''),
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
