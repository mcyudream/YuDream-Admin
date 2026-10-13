import type { PermissionRoleChange } from '@/api/modules/system-permission'
import type { RoleManageItem } from '@/api/modules/system-role'

export interface MatrixRoleDraft extends PermissionRoleChange {
  roleName: string
  roleCode: string
  conflict?: 'changed' | 'missing' | 'protected' | 'wildcard' | 'catalog'
}

export type MatrixDrafts = Record<string, MatrixRoleDraft>

export function sameMatrixPermissions(left: readonly string[], right: readonly string[]): boolean {
  const a = new Set(left)
  const b = new Set(right)
  return a.size === b.size && [...a].every(code => b.has(code))
}

function sameMatrixBaseline(left: readonly string[], right: readonly string[]): boolean {
  return left.length === right.length && left.every((code, index) => code === right[index])
}

export function matrixDraftTarget(draft: PermissionRoleChange): string[] {
  const revoked = new Set(draft.revoke)
  const kept = draft.expectedPermissions.filter(code => !revoked.has(code))
  const existing = new Set(kept)
  return [...kept, ...draft.grant.filter(code => !existing.has(code))]
}

/** 只记录相对原始基线的操作意图；未知和暂失效权限不参与矩阵改动。 */
export function setMatrixGrants(drafts: MatrixDrafts, role: RoleManageItem, codes: readonly string[], assigned: boolean, allowed: ReadonlySet<string>): MatrixDrafts {
  if (role.systemType === 'SUPER_ADMIN' || role.permissions.includes('*') || drafts[role.id]?.conflict) {
    return drafts
  }
  const draft = drafts[role.id] || {
    roleId: role.id,
    roleName: role.name,
    roleCode: role.code,
    expectedPermissions: [...role.permissions],
    grant: [],
    revoke: [],
  }
  const baseline = new Set(draft.expectedPermissions)
  const grant = new Set(draft.grant)
  const revoke = new Set(draft.revoke)
  for (const code of codes) {
    if (!allowed.has(code)) {
      continue
    }
    if (assigned === baseline.has(code)) {
      grant.delete(code)
      revoke.delete(code)
    }
    else if (assigned) {
      grant.add(code)
      revoke.delete(code)
    }
    else {
      revoke.add(code)
      grant.delete(code)
    }
  }
  const result = { ...drafts }
  if (!grant.size && !revoke.size) {
    delete result[role.id]
  }
  else {
    result[role.id] = { ...draft, grant: [...grant].sort(), revoke: [...revoke].sort() }
  }
  return result
}

export function toggleMatrixGrant(drafts: MatrixDrafts, role: RoleManageItem, code: string, allowed: ReadonlySet<string>): MatrixDrafts {
  const configured = drafts[role.id] ? matrixDraftTarget(drafts[role.id]) : role.permissions
  return setMatrixGrants(drafts, role, [code], !configured.includes(code), allowed)
}

/** 授权内容相同的刷新不影响草稿；成功的角色单独清理，冲突不自动重设基线。 */
export function reconcileMatrixDrafts(drafts: MatrixDrafts, roles: readonly RoleManageItem[], allowed?: ReadonlySet<string>): MatrixDrafts {
  const roleIndex = new Map(roles.map(role => [role.id, role]))
  const result: MatrixDrafts = {}
  for (const [id, draft] of Object.entries(drafts)) {
    const role = roleIndex.get(id)
    if (!role) {
      result[id] = { ...draft, conflict: 'missing' }
    }
    else if (role.systemType === 'SUPER_ADMIN') {
      result[id] = { ...draft, conflict: 'protected' }
    }
    else if (role.permissions.includes('*')) {
      result[id] = { ...draft, conflict: 'wildcard' }
    }
    else if (sameMatrixPermissions(role.permissions, matrixDraftTarget(draft))) {
      continue
    }
    else if (allowed && [...draft.grant, ...draft.revoke].some(code => !allowed.has(code))) {
      result[id] = { ...draft, conflict: draft.conflict || 'catalog' }
    }
    else {
      result[id] = {
        ...draft,
        roleName: role.name,
        roleCode: role.code,
        conflict: draft.conflict || (sameMatrixBaseline(role.permissions, draft.expectedPermissions) ? undefined : 'changed'),
      }
    }
  }
  return result
}

export function matrixSaveChanges(drafts: MatrixDrafts): PermissionRoleChange[] {
  return Object.values(drafts).filter(draft => !draft.conflict).map(draft => ({
    roleId: draft.roleId,
    expectedPermissions: [...draft.expectedPermissions],
    grant: [...draft.grant],
    revoke: [...draft.revoke],
  }))
}
