import type { PermissionItem, RoleManageItem, RoleStatus } from '@/api/modules/system-role'
import { permissionCategoryMeta, permissionCategoryOf, permissionResourceOf } from '@/utils/permission-catalog'

export const MATRIX_ROLE_PAGE_SIZE = 12
export const MATRIX_PERMISSION_PAGE_SIZE = 30
export const MATRIX_UNASSIGNED_DEPT = '__unassigned__'

export type MatrixGrantState = 'super-admin' | 'assigned' | 'unassigned'

export interface MatrixRoleFilter {
  keyword?: string
  deptId?: string
  status?: RoleStatus | ''
}

export interface MatrixPermissionFilter {
  domain?: string
  resource?: string
  category?: string
  keyword?: string
}

export function matrixPermissions(permissions: PermissionItem[], filter: MatrixPermissionFilter = {}): PermissionItem[] {
  const search = filter.keyword?.trim().toLocaleLowerCase() || ''
  const seen = new Set<string>()
  return permissions.filter((item) => {
    if (item.status !== 'ACTIVE' || seen.has(item.code)) {
      return false
    }
    seen.add(item.code)
    const resource = permissionResourceOf(item)
    const category = permissionCategoryOf(item.code)
    return (!filter.domain || resource.domainKey === filter.domain)
      && (!filter.resource || resource.key === filter.resource)
      && (!filter.category || category.key === filter.category)
      && (!search || `${item.name} ${item.code} ${item.module} ${item.desc || ''} ${resource.domainLabel} ${resource.label} ${category.label}`.toLocaleLowerCase().includes(search))
  }).sort((a, b) => {
    const resourceA = permissionResourceOf(a)
    const resourceB = permissionResourceOf(b)
    return resourceA.domainKey.localeCompare(resourceB.domainKey)
      || resourceA.key.localeCompare(resourceB.key)
      || permissionCategoryMeta.findIndex(item => item.key === permissionCategoryOf(a.code).key) - permissionCategoryMeta.findIndex(item => item.key === permissionCategoryOf(b.code).key)
      || a.code.localeCompare(b.code)
  })
}

export function matrixRoles(roles: RoleManageItem[], filter: MatrixRoleFilter = {}): RoleManageItem[] {
  const search = filter.keyword?.trim().toLocaleLowerCase() || ''
  return roles.filter(item => (!search || `${item.name} ${item.code}`.toLocaleLowerCase().includes(search))
    && (!filter.status || item.status === filter.status)
    && (!filter.deptId || (filter.deptId === MATRIX_UNASSIGNED_DEPT ? !item.deptId : item.deptId === filter.deptId)))
}

/** 等级只是管理层级；只有宿主 SUPER_ADMIN 系统角色具备通配权限。 */
export function matrixGrantState(role: RoleManageItem, permissionCode: string, configured = new Set(role.permissions)): MatrixGrantState {
  if (role.systemType === 'SUPER_ADMIN') {
    return 'super-admin'
  }
  return configured.has(permissionCode) ? 'assigned' : 'unassigned'
}

/** 停用角色仍展示其保存的配置，但不能解释为已生效的授权。 */
export function matrixGrantLabel(role: RoleManageItem, permission: PermissionItem, state: MatrixGrantState): string {
  const grant = state === 'super-admin' ? '超级管理员，全部权限' : state === 'assigned' ? '已授权' : '未授权'
  return `${role.name} · ${permission.name}：${grant}${role.status === 'DEPRECATED' ? '；角色停用，配置未生效' : '；是否对用户生效取决于当前部门和角色'}`
}

export function matrixPage<T>(items: T[], page: number, hardLimit: number): T[] {
  const limit = Number.isFinite(hardLimit) && hardLimit > 0 ? Math.max(1, Math.min(30, Math.floor(hardLimit))) : 1
  const maxPage = Math.max(1, Math.ceil(items.length / limit))
  const currentPage = Number.isFinite(page) ? Math.min(maxPage, Math.max(1, Math.floor(page))) : 1
  return items.slice((currentPage - 1) * limit, currentPage * limit)
}
