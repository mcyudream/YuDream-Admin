import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type DashboardOwnerType = 'DEFAULT' | 'USER'
export type DashboardSource = 'SYSTEM' | 'PLUGIN'
export type DashboardBreakpoint = 'lg' | 'md' | 'sm' | 'xs'

export interface DashboardGridPlacement {
  x: number
  y: number
  w: number
  h: number
}

export interface DashboardLayoutItem {
  cardCode: string
  visible: boolean
  placements: Partial<Record<DashboardBreakpoint, DashboardGridPlacement>>
}

export interface DashboardLayout {
  id?: number | string
  ownerType: DashboardOwnerType
  ownerId?: number | string
  items: DashboardLayoutItem[]
  createTime?: string
  updateTime?: string
}

export interface DashboardCard {
  code: string
  title: string
  description?: string
  icon?: string
  category?: string
  source: DashboardSource
  pluginCode?: string
  permission?: string
  component: string
  actionPath?: string
  dragPayloadTemplate?: string
  tone?: string
  defaultW: number
  defaultH: number
  minW: number
  minH: number
  sort: number
}

export interface DashboardWorkspace {
  cards: DashboardCard[]
  defaultLayout: DashboardLayout
  userLayout?: DashboardLayout
  effectiveLayout: DashboardLayout
}

export interface DashboardLayoutSavePayload {
  items: DashboardLayoutItem[]
}

export default {
  workspace: () => systemClient.get<unknown, ApiResponse<DashboardWorkspace>>('api/system/dashboard/me'),
  cards: () => systemClient.get<unknown, ApiResponse<DashboardCard[]>>('api/system/dashboard/cards'),
  saveMyLayout: (data: DashboardLayoutSavePayload) => systemClient.put<unknown, ApiResponse<DashboardLayout>>('api/system/dashboard/me/layout', data),
  resetMyLayout: () => systemClient.delete<unknown, ApiResponse<null>>('api/system/dashboard/me/layout'),
  defaultLayout: () => systemClient.get<unknown, ApiResponse<DashboardLayout>>('api/system/dashboard/default-layout'),
  saveDefaultLayout: (data: DashboardLayoutSavePayload) => systemClient.put<unknown, ApiResponse<DashboardLayout>>('api/system/dashboard/default-layout', data),
}
