import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type RoleStatus = 'ACTIVE' | 'DEPRECATED'
export type RoleLevel = 'SUPER_ADMIN' | 'ADMIN' | 'USER' | 'GUEST'

/**
 * 角色代表色推荐预设（首选中性色，便于「未显式选择」时保持克制）。
 * 仅作 YdColorPicker 的快速预设，取值不受限；保存一律为 `#RRGGBB` 小写 hex。
 */
export const ROLE_COLOR_PRESETS: string[] = [
  '#64748b',
  '#2563eb',
  '#16a34a',
  '#d97706',
  '#dc2626',
  '#7c3aed',
]

export type IdValue = string

export interface OptionItem {
  id: IdValue
  label: string
  value: string
  deptId?: IdValue
  deptName?: string
}

export interface PermissionItem {
  code: string
  name: string
  module: string
  desc?: string
  status: 'ACTIVE' | 'DEPRECATED'
}

export interface RoleManageItem {
  id: IdValue
  name: string
  deptId?: IdValue
  deptName?: string
  code: string
  level: RoleLevel
  systemRole: boolean
  systemType?: string
  /** Morphicons 名称（形如 morph:shield），可空。 */
  icon?: string
  /** 代表色，`#RRGGBB` 小写 hex，可空（未设置表示用默认）。 */
  color?: string
  permissions: string[]
  permissionCount: number
  status: RoleStatus
  createTime?: string
  updateTime?: string
}

export interface RolePageParams {
  page: number
  size: number
  keyword?: string
  deptId?: IdValue
  status?: RoleStatus
}

export interface RolePayload {
  name: string
  code: string
  deptId?: IdValue
  level?: RoleLevel
  status?: RoleStatus
  /** Morphicons 名称（形如 morph:shield），可空。 */
  icon?: string
  /** 代表色，`#RRGGBB` 小写 hex，可空（未设置表示用默认）。 */
  color?: string
  permissions: string[]
}

export default {
  page: (params: RolePageParams) => systemClient.get<unknown, ApiResponse<PageResult<RoleManageItem>>>('api/system/roles', { params }),
  options: () => systemClient.get<unknown, ApiResponse<OptionItem[]>>('api/system/roles/options'),
  permissions: () => systemClient.get<unknown, ApiResponse<PermissionItem[]>>('api/system/roles/permissions'),
  create: (data: RolePayload) => systemClient.post<unknown, ApiResponse<RoleManageItem>>('api/system/roles', data),
  update: (id: IdValue, data: RolePayload) => systemClient.put<unknown, ApiResponse<RoleManageItem>>(`api/system/roles/${id}`, data),
  enable: (id: IdValue) => systemClient.post<unknown, ApiResponse<null>>(`api/system/roles/${id}/enable`),
  disable: (id: IdValue) => systemClient.delete<unknown, ApiResponse<null>>(`api/system/roles/${id}`),
  assignPermissions: (id: IdValue, permissions: string[]) => systemClient.put<unknown, ApiResponse<RoleManageItem>>(`api/system/roles/${id}/permissions`, { permissions }),
}
