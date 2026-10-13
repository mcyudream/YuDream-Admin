import type { ApiResponse } from './system-client'
import type { PermissionItem, RoleManageItem } from './system-role'
import type { UserManageItem } from './system-user'
import systemClient from './system-client'

export interface PermissionOverviewUser {
  id: string
  username: string
  nickname?: string
  roleIds: string[]
  deptIds: string[]
  status: 'ACTIVE' | 'DISABLED'
  emailVerified: boolean
  currentRoleId?: string
  currentDeptId?: string
  contextSource: 'SESSION' | 'DEFAULT'
}

export interface PermissionOverviewMenu {
  code: string
  name: string
  type: string
  parentCode?: string
  permission: string
  runtimeAvailable: boolean
  status: string
  path?: string
  roleIds: string[]
}

export interface PermissionOverview {
  roles: RoleManageItem[]
  permissions: PermissionItem[]
  users: PermissionOverviewUser[]
  menus: PermissionOverviewMenu[]
  stats: {
    userCount: number | null
    roleCount: number
    menuCount: number | null
    idleRoleCount: number | null
    unassignedUserCount: number | null
    ungrantedMenuCount: number | null
    assignmentCount: number | null
  }
  idleRoleIds: string[] | null
  userPage: number
  userSize: number
  userTotal: number | null
  canViewUsers: boolean
  canViewMenus: boolean
}

export interface PermissionOverviewQuery {
  userPage?: number
  userSize?: number
  userKeyword?: string
  userRoleId?: string
  userUnassignedOnly?: boolean
}

export interface PermissionRoleChange {
  roleId: string
  expectedPermissions: string[]
  grant: string[]
  revoke: string[]
}

export interface UserRoleChange {
  expectedRoleIds: string[]
  grant: string[]
  revoke: string[]
}

export default {
  overview: (params?: PermissionOverviewQuery) => systemClient.get<unknown, ApiResponse<PermissionOverview>>('api/system/roles/permission-overview', { params }),
  changeRolePermissions: ({ roleId, ...change }: PermissionRoleChange) => systemClient.put<unknown, ApiResponse<RoleManageItem>>(`api/system/roles/${roleId}/permission-changes`, change),
  changeUserRoles: (userId: string, change: UserRoleChange) => systemClient.put<unknown, ApiResponse<UserManageItem>>(`api/system/users/${userId}/role-changes`, change),
}
