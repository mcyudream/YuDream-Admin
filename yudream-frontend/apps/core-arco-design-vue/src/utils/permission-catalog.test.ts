import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { buildPermissionTree, mergeVisiblePermissions, permissionCategoryOf, permissionResourceOf, permissionTreeKeys } from './permission-catalog'

describe('permission catalog', () => {
  it('classifies the full assignment action family and keeps legacy verbs explicit', () => {
    assert.equal(permissionCategoryOf('system:user:assign-team').key, 'operate')
    assert.equal(permissionCategoryOf('platform:milky:internal').key, 'other')
    assert.equal(permissionCategoryOf('platform:graph:query').key, 'other')
    assert.equal(permissionCategoryOf('system:user:delete').key, 'danger')
    assert.equal(permissionCategoryOf('system:user:assign-').key, 'other')
  })

  it('groups by stable resource codes, separating subresources and preserving plugin identity', () => {
    assert.equal(permissionResourceOf({ code: 'platform:agent:tool:edit', module: '平台能力' }).label, 'Agent 工具')
    assert.equal(permissionResourceOf({ code: 'platform:agent:edit', module: '平台能力' }).key, 'platform:agent')
    assert.equal(permissionResourceOf({ code: 'plugin:alpha:use', module: '同名插件' }).key, 'plugin:alpha')
    assert.equal(permissionResourceOf({ code: 'plugin:beta:use', module: '同名插件' }).key, 'plugin:beta')
    assert.equal(permissionResourceOf({ code: 'old-code', module: '' }).valid, false)
  })

  it('uses the same searchable hierarchy and excludes inactive permissions', () => {
    const permissions = [
      { code: 'system:user:edit', name: '编辑用户', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:role:view', name: '查看角色', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:user:delete', name: '删除用户', module: '系统管理', status: 'DEPRECATED' as const },
    ]
    const tree = buildPermissionTree(permissions, '用户管理')
    assert.deepEqual(permissionTreeKeys(tree, true), ['system:user:edit'])
    assert.deepEqual(permissionTreeKeys(tree), ['domain:system', 'resource:system:user', 'category:system:user:operate'])
  })

  it('preserves hidden selections and missing plugin codes during partial checks', () => {
    assert.deepEqual(mergeVisiblePermissions(['plugin:offline:use', 'system:user:edit', 'system:role:view'], ['system:user:view', 'domain:system'], new Set(['system:user:edit', 'system:user:view'])), [
      'plugin:offline:use',
      'system:role:view',
      'system:user:view',
    ])
  })

  it('never merges different resources with the same displayed module name', () => {
    const permissions = [
      { code: 'plugin:beta:use', name: '使用乙插件', module: '同名插件', status: 'ACTIVE' as const },
      { code: 'plugin:alpha:use', name: '使用甲插件', module: '同名插件', status: 'ACTIVE' as const },
      { code: 'system:role:edit', name: '编辑角色', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:user:assign-host-device', name: '分配设备', module: '系统管理', status: 'ACTIVE' as const },
    ]
    const before = [...permissions]
    const tree = buildPermissionTree(permissions)
    assert.deepEqual(tree.map(node => node.key), ['domain:system', 'domain:plugin'])
    assert.deepEqual(tree[1]?.children?.map(node => node.key), ['resource:plugin:alpha', 'resource:plugin:beta'])
    assert.ok(permissionTreeKeys(tree).includes('category:system:user:operate'))
    assert.deepEqual(permissions, before)
    assert.equal(new Set(permissionTreeKeys(tree, true)).size, permissions.length)
  })

  it('keeps malformed historical permissions searchable and removes only unchecked visible grants', () => {
    const tree = buildPermissionTree([
      { code: 'legacy-edit', name: '历史操作', module: '历史模块', status: 'ACTIVE' },
      { code: 'system:user:edit', name: '编辑用户', module: '系统管理', status: 'ACTIVE' },
    ], '历史')
    assert.deepEqual(permissionTreeKeys(tree, true), ['legacy-edit'])
    assert.deepEqual(mergeVisiblePermissions(['legacy-edit', 'system:user:edit', 'plugin:offline:use'], [123, 'resource:legacy:legacy-edit'], new Set(permissionTreeKeys(tree, true))), [
      'system:user:edit',
      'plugin:offline:use',
    ])
  })
})
