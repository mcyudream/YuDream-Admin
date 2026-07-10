import type { ApiResponse } from './system-client'
import type { IdValue, OptionItem } from './system-role'
import systemClient from './system-client'

export type DeptStatus = 'ACTIVE' | 'DEPRECATED'

export interface DeptManageItem {
  id: IdValue
  name: string
  description?: string
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
  enable: (id: IdValue) => systemClient.post<unknown, ApiResponse<null>>(`api/system/depts/${id}/enable`),
  disable: (id: IdValue) => systemClient.delete<unknown, ApiResponse<null>>(`api/system/depts/${id}`),
}
