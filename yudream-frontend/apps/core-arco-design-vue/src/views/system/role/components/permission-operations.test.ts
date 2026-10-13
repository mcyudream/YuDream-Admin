import type { PermissionItem, RoleManageItem } from '@/api/modules/system-role'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { permissionOperationError, permissionSources, rolePermissionChange, userRoleDelta } from './permission-operations'

const role: RoleManageItem = { id: '9007199254740993', name: '操作员', code: 'operator', level: 'USER', systemRole: false, permissions: ['plugin:missing:use', 'system:user:view'], permissionCount: 2, status: 'ACTIVE' }
const permission: PermissionItem = { code: 'system:user:edit', name: '编辑用户', module: '系统管理', status: 'ACTIVE' }

describe('permission operations', () => {
  it('builds explicit changes without rebuilding or losing the original snapshot', () => {
    assert.deepEqual(rolePermissionChange(role, permission, true), { roleId: role.id, expectedPermissions: ['plugin:missing:use', 'system:user:view'], grant: [permission.code], revoke: [] })
    assert.deepEqual(role.permissions, ['plugin:missing:use', 'system:user:view'])
    assert.deepEqual(rolePermissionChange(role, { ...permission, code: 'plugin:missing:use', status: 'DEPRECATED' }, false).revoke, ['plugin:missing:use'])
  })
  it('blocks adding unavailable permissions and changing implicit super-admin grants', () => {
    assert.throws(() => rolePermissionChange(role, { ...permission, status: 'DEPRECATED' }, true))
    assert.throws(() => rolePermissionChange({ ...role, systemType: 'SUPER_ADMIN' }, permission, false))
    assert.throws(() => rolePermissionChange({ ...role, permissions: ['*'] }, permission, false))
    assert.deepEqual(rolePermissionChange({ ...role, permissions: [permission.code] }, permission, true).grant, [])
  })
  it('shows explicit and wildcard sources with their actual role status', () => {
    const sources = permissionSources([role, { ...role, id: '2', systemType: 'SUPER_ADMIN', status: 'DEPRECATED' }], 'system:user:view')
    assert.deepEqual(sources.map(source => [source.wildcard, source.active]), [[false, true], [true, false]])
  })
  it('preserves historical string role ids and records only explicit selection changes', () => {
    assert.deepEqual(userRoleDelta(['9007199254740993', 'missing'], ['missing', '2']), { expectedRoleIds: ['9007199254740993', 'missing'], grant: ['2'], revoke: ['9007199254740993'] })
  })
  it('retains actionable conflict messages from both API response shapes', () => {
    assert.equal(permissionOperationError(new Error('授权已变化')), '授权已变化')
    assert.equal(permissionOperationError({ response: { data: { message: '角色已变化' } } }), '角色已变化')
  })
})
