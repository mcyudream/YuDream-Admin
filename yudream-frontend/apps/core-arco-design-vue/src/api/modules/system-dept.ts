import type { ApiResponse } from './system-client'
import type { IdValue, OptionItem } from './system-role'
import systemClient from './system-client'

export type DeptStatus = 'ACTIVE' | 'DEPRECATED'

/**
 * 部门主色推荐预设（首选中性色，未显式选择时保持克制）。
 * 仅作 YdColorPicker 的快速预设，取值不受限；保存一律为 `#RRGGBB` 小写 hex。
 */
export const DEPT_COLOR_PRESETS: string[] = [
  '#64748b',
  '#0ea5e9',
  '#16a34a',
  '#d97706',
  '#dc2626',
  '#7c3aed',
]

export interface DeptManageItem {
  id: IdValue
  name: string
  description?: string
  avatar?: string
  /** 部门主色，`#RRGGBB` 小写 hex，可空（未设置表示用默认）。 */
  color?: string
  leaderId?: IdValue
  leaderName?: string
  phone?: string
  parentId?: IdValue
  sortOrder?: number
  deptType?: string
  status: DeptStatus
  systemDept: boolean
  createTime?: string
  updateTime?: string
  children?: DeptManageItem[]
}

export interface DeptTreeParams {
  keyword?: string
  parentId?: IdValue
  status?: DeptStatus
}

export interface DeptPayload {
  name: string
  description?: string
  /** 部门主色，`#RRGGBB` 小写 hex，可空（未设置表示用默认）。 */
  color?: string
  leaderId?: IdValue
  phone?: string
  parentId?: IdValue
  sortOrder?: number
  status?: DeptStatus
}

export default {
  tree: (params?: DeptTreeParams) => systemClient.get<unknown, ApiResponse<DeptManageItem[]>>('api/system/depts', { params }),
  options: () => systemClient.get<unknown, ApiResponse<OptionItem[]>>('api/system/depts/options'),
  create: (data: DeptPayload) => systemClient.post<unknown, ApiResponse<DeptManageItem>>('api/system/depts', data),
  update: (id: IdValue, data: DeptPayload) => systemClient.put<unknown, ApiResponse<DeptManageItem>>(`api/system/depts/${id}`, data),
  uploadAvatar: (id: IdValue, data: FormData) => systemClient.post<unknown, ApiResponse<DeptManageItem>>(`api/system/depts/${id}/avatar`, data),
  enable: (id: IdValue) => systemClient.post<unknown, ApiResponse<null>>(`api/system/depts/${id}/enable`),
  disable: (id: IdValue) => systemClient.delete<unknown, ApiResponse<null>>(`api/system/depts/${id}`),
}
