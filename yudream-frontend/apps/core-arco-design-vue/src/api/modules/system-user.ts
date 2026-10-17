import type { MessagingIdentity } from './profile'
import type { ApiResponse, PageResult } from './system-client'
import type { LoginData } from './user'
import systemClient from './system-client'

export type UserStatus = 'ACTIVE' | 'DISABLED'
export type IdValue = string

export interface UserDeptAssign {
  deptId: IdValue
  defaultDept: boolean
}

export interface UserManageItem {
  id: IdValue
  username: string
  nickname?: string
  avatar?: string
  email?: string
  phone?: string
  qq?: string
  emailVerified: boolean
  status: UserStatus
  roleIds: IdValue[]
  roleNames: string[]
  deptIds: IdValue[]
  deptNames: string[]
  defaultDeptId?: IdValue
  /** 默认部门头像 URL，可空（未上传时前端按部门名回退图标）。 */
  defaultDeptAvatar?: string
  tags?: UserTagItem[]
  fields?: Record<string, Record<string, string>>
  messagingIdentities?: MessagingIdentity[]
  createTime?: string
  updateTime?: string
}

export interface UserTagItem {
  namespace: string
  code: string
  label: string
}

export interface UserPickerRow {
  id: IdValue
  username: string
  nickname?: string
  avatar?: string
}
export interface UserPageParams {
  page: number
  size: number
  keyword?: string
  deptId?: IdValue
  roleId?: IdValue
  emailVerified?: boolean
  status?: UserStatus
}

export interface UserCreatePayload {
  username: string
  nickname?: string
  email: string
  phone?: string
  qq?: string
  password: string
  emailVerified: boolean
  roleIds: IdValue[]
  depts: UserDeptAssign[]
}

export interface UserUpdatePayload {
  username?: string
  nickname?: string
  email?: string
  phone?: string
  qq?: string
  emailVerified?: boolean
}

/** 后端 Long 计数经全局 ToStringSerializer 序列化为字符串，消费方需 Number() 归一化 */
export interface UserStatsNamedCount {
  name: string
  count: string | number
}

export interface UserStatsDailyCount {
  date: string
  count: string | number
}

export interface UserStats {
  total: string | number
  verifiedCount: string | number
  activeCount: string | number
  disabledCount: string | number
  newToday: string | number
  newThisWeek: string | number
  trendDays: number
  registeredTrend: UserStatsDailyCount[]
  roleDistribution: UserStatsNamedCount[]
  completeness: UserStatsNamedCount[]
}

export default {
  page: (params: UserPageParams) => systemClient.get<unknown, ApiResponse<PageResult<UserManageItem>>>('api/system/users', { params }),
  /** 统计是「情报板」：不可用时由页面收起，不弹全局错误提示 */
  stats: () => systemClient.get<unknown, ApiResponse<UserStats>>('api/system/users/stats', { skipGlobalToast: true }),
  detail: (id: IdValue) => systemClient.get<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}`),
  create: (data: UserCreatePayload) => systemClient.post<unknown, ApiResponse<UserManageItem>>('api/system/users', data),
  update: (id: IdValue, data: UserUpdatePayload) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}`, data),
  disable: (id: IdValue) => systemClient.delete<unknown, ApiResponse<null>>(`api/system/users/${id}`),
  enable: (id: IdValue) => systemClient.post<unknown, ApiResponse<null>>(`api/system/users/${id}/enable`),
  uploadAvatar: (id: IdValue, data: FormData) => systemClient.post<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}/avatar`, data),
  changePassword: (id: IdValue, password: string) => systemClient.put<unknown, ApiResponse<null>>(`api/system/users/${id}/password`, { password }),
  assignRoles: (id: IdValue, roleIds: IdValue[]) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}/roles`, { roleIds }),
  assignDepts: (id: IdValue, depts: UserDeptAssign[]) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}/depts`, { depts }),
  impersonate: (id: IdValue) => systemClient.post<unknown, ApiResponse<LoginData>>(`api/system/users/${id}/impersonate`),
}
