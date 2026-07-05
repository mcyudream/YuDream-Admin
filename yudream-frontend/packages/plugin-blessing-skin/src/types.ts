export type SkinPage = 'dashboard' | 'players' | 'textures' | 'closet' | 'system'

export interface SkinSettings {
  maxPlayersPerUser: number
  allowPublicUpload: boolean
  siteNotice: string
}

export interface SkinSummary {
  users: number
  players: number
  textures: number
  closetItems: number
  options: number
  settings?: SkinSettings
}

export interface SkinMe {
  userId: string
  hostUser?: {
    id: number | string
    username: string
    nickname?: string
    email?: string
    avatar?: string
  }
  skinUser?: SkinUser
  permissions: string[]
  manage: boolean
}

export interface SkinUser {
  id: string
  email: string
  nickname: string
  migratedUid?: number
  createdAt?: number
}

export interface SkinPlayer {
  uuid: string
  ownerId: string
  name: string
  skinHash?: string
  capeHash?: string
  lastModified?: number
}

export interface SkinTexture {
  hash: string
  name: string
  type: string
  model: string
  contentType?: string
  size?: number
  uploaderId?: string
  publicAccess?: boolean
  uploadedAt?: number
}

export interface SkinClosetItem {
  id: string
  userId: string
  textureHash: string
  itemName?: string
  createdAt?: number
}

export interface MigrationReport {
  users: number
  players: number
  textures: number
  closetItems: number
  options: number
  warnings: string[]
}

export interface SelectOption {
  label: string
  value: string
}
