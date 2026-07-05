import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { MigrationReport, SkinClosetItem, SkinMe, SkinPlayer, SkinSettings, SkinSummary, SkinTexture, SkinUser } from '../types'

export function createSkinApi(sdk: YuDreamPluginSdk) {
  return {
    status: () => sdk.http.get<SkinSummary>('/status'),
    me: () => sdk.http.get<SkinMe>('/me'),
    users: () => sdk.http.get<SkinUser[]>('/admin/users?page=1&size=100'),
    players: () => sdk.http.get<SkinPlayer[]>('/me/players?page=1&size=100'),
    adminPlayers: () => sdk.http.get<SkinPlayer[]>('/admin/players?page=1&size=100'),
    textures: () => sdk.http.get<SkinTexture[]>('/textures?page=1&size=100'),
    closet: () => sdk.http.get<SkinClosetItem[]>('/me/closet?page=1&size=200'),
    adminCloset: () => sdk.http.get<SkinClosetItem[]>('/admin/closet?page=1&size=200'),
    settings: () => sdk.http.get<SkinSettings>('/settings'),
    createUser: (data: Record<string, unknown>) => sdk.http.post<SkinUser>('/admin/users', data),
    createPlayer: (data: Record<string, unknown>) => sdk.http.post<SkinPlayer>('/me/players', data),
    createAdminPlayer: (data: Record<string, unknown>) => sdk.http.post<SkinPlayer>('/admin/players', data),
    renamePlayer: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/me/players/${encodeURIComponent(name)}/name`, { method: 'PUT', data }),
    renameAdminPlayer: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/admin/players/${encodeURIComponent(name)}/name`, { method: 'PUT', data }),
    deletePlayer: (name: string) => sdk.http.request(`/me/players/${encodeURIComponent(name)}`, { method: 'DELETE' }),
    deleteAdminPlayer: (name: string) => sdk.http.request(`/admin/players/${encodeURIComponent(name)}`, { method: 'DELETE' }),
    assignTextures: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/me/players/${encodeURIComponent(name)}/textures`, { method: 'PUT', data }),
    assignAdminTextures: (name: string, data: Record<string, unknown>) => sdk.http.request<SkinPlayer>(`/admin/players/${encodeURIComponent(name)}/textures`, { method: 'PUT', data }),
    uploadTexture: (data: Record<string, unknown>) => sdk.http.post<SkinTexture>('/me/textures', data),
    saveClosetItem: (data: Record<string, unknown>) => sdk.http.post<SkinClosetItem>('/me/closet', data),
    saveAdminClosetItem: (data: Record<string, unknown>) => sdk.http.post<SkinClosetItem>('/admin/closet', data),
    renameClosetItem: (id: string, data: Record<string, unknown>) => sdk.http.request<SkinClosetItem>(`/me/closet/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    renameAdminClosetItem: (id: string, data: Record<string, unknown>) => sdk.http.request<SkinClosetItem>(`/admin/closet/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    deleteClosetItem: (id: string) => sdk.http.request(`/me/closet/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    deleteAdminClosetItem: (id: string) => sdk.http.request(`/admin/closet/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    saveSettings: (data: Record<string, unknown>) => sdk.http.request<SkinSettings>('/settings', { method: 'PUT', data }),
    migrate: (data: Record<string, unknown>) => sdk.http.post<MigrationReport>('/migration/blessing-skin', data),
    textureUrl: (hash?: string) => (hash ? sdk.http.url(`/textures/${hash}`) : ''),
  }
}
