import type { ApiResponse, PageResult } from './system-client'
import type { LoginData } from './user'
import systemClient from './system-client'

export type UserStatus = 'ACTIVE' | 'DISABLED'
export type IdValue = string | number

export interface UserDeptAssign {
  deptId: IdValue
  defaultDept: boolean
}

export interface UserManageItem {
  id: IdValue
  username: string
  nickname?: string
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
  createTime?: string
  updateTime?: string
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

export default {
  page: (params: UserPageParams) => systemClient.get<unknown, ApiResponse<PageResult<UserManageItem>>>('api/system/users', { params }),
  create: (data: UserCreatePayload) => systemClient.post<unknown, ApiResponse<UserManageItem>>('api/system/users', data),
  update: (id: IdValue, data: UserUpdatePayload) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}`, data),
  disable: (id: IdValue) => systemClient.delete<unknown, ApiResponse<null>>(`api/system/users/${id}`),
  enable: (id: IdValue) => systemClient.post<unknown, ApiResponse<null>>(`api/system/users/${id}/enable`),
  assignRoles: (id: IdValue, roleIds: IdValue[]) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}/roles`, { roleIds }),
  assignDepts: (id: IdValue, depts: UserDeptAssign[]) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${id}/depts`, { depts }),
  impersonate: (id: IdValue) => systemClient.post<unknown, ApiResponse<LoginData>>(`api/system/users/${id}/impersonate`),
}
