import type { PermissionItem, RoleManageItem } from '@/api/modules/system-role'
import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { MATRIX_ROLE_PAGE_SIZE, MATRIX_UNASSIGNED_DEPT, matrixGrantLabel, matrixGrantState, matrixPage, matrixPermissions, matrixRoles } from './permission-matrix'

const permissions: PermissionItem[] = [
  { code: 'system:user:view', name: '查看用户', module: '用户管理', status: 'ACTIVE' },
  { code: 'system:user:edit', name: '编辑用户', module: '用户管理', status: 'ACTIVE' },
  { code: 'plugin:old:use', name: '旧插件', module: '插件', status: 'DEPRECATED' },
]
function role(overrides: Partial<RoleManageItem> = {}): RoleManageItem {
  return {
    id: '9223372036854775806',
    name: '内容编辑',
    code: 'EDITOR',
    level: 'USER',
    systemRole: false,
    permissions: ['system:user:view', 'plugin:missing:use'],
    permissionCount: 2,
    status: 'ACTIVE',
    ...overrides,
  }
}

describe('read-only permission matrix semantics', () => {
  it('treats only the SUPER_ADMIN system type as a wildcard, never the role level', () => {
    assert.equal(matrixGrantState(role({ level: 'SUPER_ADMIN' }), 'system:user:edit'), 'unassigned')
    assert.equal(matrixGrantState(role({ systemType: 'SUPER_ADMIN', permissions: [] }), 'system:user:edit'), 'super-admin')
    assert.equal(matrixGrantState(role(), 'system:user:view'), 'assigned')
  })

  it('keeps disabled assignments visible while explicitly explaining they do not take effect', () => {
    const disabled = role({ status: 'DEPRECATED' })
    assert.equal(matrixGrantState(disabled, 'system:user:view'), 'assigned')
    assert.match(matrixGrantLabel(disabled, permissions[0], 'assigned'), /已授权.*角色停用，配置未生效/)
    assert.match(matrixGrantLabel(role(), permissions[0], 'assigned'), /当前部门和角色/)
  })

  it('shows legacy configured wildcard roles as all permissions without relying on their level', () => {
    const configured = role({ permissions: ['*'], level: 'USER' })
    assert.equal(matrixGrantState(configured, 'system:user:edit'), 'configured-wildcard')
    assert.match(matrixGrantLabel(configured, permissions[0], 'configured-wildcard'), /配置含通配.*需原权限树处理/)
    assert.equal(matrixGrantState(role({ permissions: ['*'], systemType: 'SUPER_ADMIN' }), 'system:user:edit'), 'super-admin')
  })

  it('renders active catalog permissions only and preserves missing permission codes on the original role', () => {
    const configured = role()
    const before = [...configured.permissions]
    assert.deepEqual(matrixPermissions([...permissions, permissions[0]]).map(item => item.code), ['system:user:view', 'system:user:edit'])
    matrixPermissions(permissions).forEach(item => matrixGrantState(configured, item.code))
    assert.deepEqual(configured.permissions, before)
    assert.ok(configured.permissions.includes('plugin:missing:use'))
  })

  it('filters catalog and departments without coercing string identifiers', () => {
    const roles = [role(), role({ id: '9223372036854775807', deptId: '9223372036854775700', name: '审计员', code: 'AUDITOR', status: 'DEPRECATED' })]
    assert.equal(matrixRoles(roles, { deptId: '9223372036854775700', status: 'DEPRECATED' })[0]?.id, '9223372036854775807')
    assert.equal(matrixRoles(roles, { deptId: MATRIX_UNASSIGNED_DEPT }).length, 1)
    assert.equal(matrixRoles(roles, { keyword: 'editor' }).length, 1)
    assert.deepEqual(matrixPermissions(permissions, { domain: 'system', resource: 'system:user', keyword: '编辑' }).map(item => item.code), ['system:user:edit'])
    assert.deepEqual(matrixPermissions(permissions, { category: 'view' }).map(item => item.code), ['system:user:view'])
  })

  it('filters both navigation and action gates by one resource and preserves whole-catalog plugin labels', () => {
    const catalog: PermissionItem[] = [
      { code: 'system:user', name: '用户页入口', module: '系统管理', status: 'ACTIVE' },
      ...permissions,
      { code: 'plugin:forum:category:view', name: '查看分类', module: '论坛分类', status: 'ACTIVE' },
      { code: 'plugin:forum:view', name: '查看论坛', module: '论坛', status: 'ACTIVE' },
    ]
    assert.deepEqual(matrixPermissions(catalog, { resource: 'system:user' }).map(item => item.code), ['system:user:view', 'system:user:edit', 'system:user'])
    assert.equal(matrixPermissions(catalog, { resource: 'plugin:forum' }).length, 2)
    assert.deepEqual(matrixPermissions(catalog, { resource: 'plugin:forum', keyword: '分类' }).map(item => item.code), ['plugin:forum:category:view'])
    assert.deepEqual(matrixPermissions(catalog, { resource: 'plugin:forum' }), matrixPermissions([...catalog].reverse(), { resource: 'plugin:forum' }))
  })

  it('uses the shared action classification for every assign-* permission', () => {
    const assignment: PermissionItem = { code: 'system:user:assign-credential', name: '分配凭据', module: '用户管理', status: 'ACTIVE' }
    assert.deepEqual(matrixPermissions([assignment], { category: 'operate' }), [assignment])
    assert.deepEqual(matrixPermissions([assignment], { category: 'other' }), [])
  })

  it('caps rendered rows and columns and clamps an old page after filtering', () => {
    const many = Array.from({ length: 250 }, (_, index) => index)
    assert.equal(matrixPage(many, 1, MATRIX_ROLE_PAGE_SIZE).length, 12)
    assert.equal(matrixPage(many, 1, 100000).length, 30)
    assert.deepEqual(matrixPage([1, 2], 20, MATRIX_ROLE_PAGE_SIZE), [1, 2])
    assert.deepEqual(matrixPage([1, 2], Number.NaN, 12), [1, 2])
    assert.equal(matrixPage(many, -1, 0).length, 1)
    assert.equal(matrixPage(many, 1, 0.5).length, 1)
  })
})
