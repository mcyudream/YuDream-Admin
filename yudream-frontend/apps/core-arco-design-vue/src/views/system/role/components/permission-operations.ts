import type { PermissionOverview, PermissionRoleChange } from '@/api/modules/system-permission'
import type { PermissionItem, RoleManageItem } from '@/api/modules/system-role'

export function permissionSources(roles: RoleManageItem[], code: string) {
  return roles.filter(role => role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*') || role.permissions.includes(code)).map(role => ({
    role,
    wildcard: role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*'),
    active: role.status === 'ACTIVE',
  }))
}

export function permissionDetails(overview: PermissionOverview, code: string) {
  const permission = overview.permissions.find(item => item.code === code)
  const sources = permissionSources(overview.roles, code)
  const roleIds = new Set(sources.map(source => source.role.id))
  return {
    permission,
    sources,
    menus: overview.canViewMenus ? overview.menus.filter(menu => menu.permission === code) : [],
    users: overview.canViewUsers ? overview.users.filter(user => user.roleIds.some(id => roleIds.has(id))) : [],
  }
}

export function rolePermissionChange(role: RoleManageItem, permission: PermissionItem, grant: boolean): PermissionRoleChange {
  if (role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*')) {
    throw new Error('此角色已有通配授权，不能单独授予或撤销，请使用原权限树处理。')
  }
  if (grant && permission.status !== 'ACTIVE') {
    throw new Error('此权限已停用，不能新增授权。')
  }
  const assigned = role.permissions.includes(permission.code)
  return {
    roleId: role.id,
    expectedPermissions: [...role.permissions],
    grant: grant && !assigned ? [permission.code] : [],
    revoke: !grant && assigned ? [permission.code] : [],
  }
}

export function userRoleDelta(expected: string[], selected: string[]) {
  const original = new Set(expected)
  const next = new Set(selected)
  return {
    expectedRoleIds: [...expected],
    grant: [...next].filter(id => !original.has(id)),
    revoke: [...original].filter(id => !next.has(id)),
  }
}

export function sameStringSet(left: string[], right: string[]) {
  const leftSet = new Set(left)
  const rightSet = new Set(right)
  return leftSet.size === rightSet.size && [...leftSet].every(value => rightSet.has(value))
}

export function permissionOperationError(error: unknown) {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = error.response as { data?: { message?: unknown } } | undefined
    if (typeof response?.data?.message === 'string') {
      return response.data.message
    }
  }
  return error instanceof Error ? error.message : '操作失败，请刷新复核后重试。'
}
