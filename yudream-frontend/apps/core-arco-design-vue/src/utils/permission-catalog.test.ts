import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { describe, it } from 'node:test'
import { buildPermissionTree, mergeVisiblePermissions, permissionCategoryOf, permissionResourceLabels, permissionResourceOf, permissionTreeKeys } from './permission-catalog'

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

  it('groups original navigation gates with their action permissions without rewriting either gate', () => {
    const permissions = [
      { code: 'system:user', name: '用户页入口', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:user:view', name: '查看用户', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:monitor:resource', name: '资源监控入口', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:monitor:resource:view', name: '查看资源监控', module: '系统管理', status: 'ACTIVE' as const },
    ]
    const tree = buildPermissionTree(permissions)
    assert.deepEqual(tree[0]?.children?.map(node => node.key), ['resource:system:monitor:resource', 'resource:system:user'])
    assert.deepEqual(new Set(permissionTreeKeys(tree, true)), new Set(permissions.map(permission => permission.code)))
    assert.equal(permissionResourceOf(permissions[0]!).key, 'system:user')
    assert.equal(permissionResourceOf(permissions[0]!).valid, false)
    assert.equal(permissionResourceOf(permissions[2]!).key, 'system:monitor:resource')
    assert.equal(permissionResourceOf({ code: 'platform:agent:tool', module: '平台能力' }).key, 'platform:agent:tool')
  })

  it('uses a stable plugin owner and one catalog label regardless of nested forms or ordering', () => {
    const permissions = [
      { code: 'plugin:forum:category:view', name: '查看分类', module: '论坛分类', status: 'ACTIVE' as const },
      { code: 'plugin:forum:view', name: '查看论坛', module: '论坛', status: 'ACTIVE' as const },
      { code: 'plugin:another:view', name: '查看另一个论坛', module: '论坛', status: 'ACTIVE' as const },
      { code: 'plugin:forum:topic:create', name: '创建帖子', module: '论坛帖子', status: 'ACTIVE' as const },
      { code: 'plugin:forum:old', name: '旧入口', module: '旧', status: 'DEPRECATED' as const },
    ]
    const labels = permissionResourceLabels(permissions)
    assert.deepEqual([...labels], [...permissionResourceLabels([...permissions].reverse())])
    assert.equal(labels.get('plugin:forum'), '论坛（plugin:forum）')
    assert.equal(labels.get('plugin:another'), '论坛（plugin:another）')
    assert.equal(permissionResourceOf(permissions[0]!, labels).key, 'plugin:forum')
    assert.equal(permissionResourceOf(permissions[0]!, labels).label, '论坛（plugin:forum）')
    assert.equal(permissionResourceOf(permissions[0]!, labels).valid, false)
    assert.equal(buildPermissionTree(permissions, '分类')[0]?.children?.[0]?.title, '论坛（plugin:forum）（1）')
    assert.deepEqual(buildPermissionTree(permissions), buildPermissionTree([...permissions].reverse()))
    assert.deepEqual(buildPermissionTree(permissions)[0]?.children?.map(node => node.key), ['resource:plugin:another', 'resource:plugin:forum'])
  })

  it('maps seeded monitor and theme navigation identities to their actual action resource without merging gates', () => {
    const pairs = [
      ['system:agent-trace', 'system:monitor:agent-trace'],
      ['system:api-log', 'system:monitor:api-log'],
      ['system:login-log', 'system:monitor:login-log'],
      ['system:online-user', 'system:monitor:online'],
      ['system:redis-monitor', 'system:monitor:redis'],
      ['system:resource-monitor', 'system:monitor:resource'],
      ['system:theme', 'system:setting:theme'],
    ]
    for (const [navigation, resource] of pairs) {
      const permissions = [
        { code: navigation, name: '页面入口', module: '系统管理', status: 'ACTIVE' as const },
        { code: `${resource}:view`, name: '查看资源', module: '系统管理', status: 'ACTIVE' as const },
      ]
      assert.equal(permissionResourceOf(permissions[0]).key, resource)
      assert.equal(permissionResourceOf({ code: `${navigation}:view`, module: '系统管理' }).key, resource)
      const tree = buildPermissionTree(permissions)
      assert.equal(tree[0]?.children?.length, 1)
      assert.deepEqual(new Set(permissionTreeKeys(tree, true)), new Set(permissions.map(item => item.code)))
    }
    assert.equal(permissionResourceOf({ code: 'platform:ai:tool:web-fetch', module: '平台能力' }).label, 'AI 工具')
    assert.equal(permissionResourceOf({ code: 'platform:mobile-config:view', module: '平台能力' }).label, '移动端配置')
  })

  it('keeps unknown historical resource identities and resolves module-name ties deterministically', () => {
    const permissions = [
      { code: 'plugin:example:view', name: '查看', module: '乙组', status: 'ACTIVE' as const },
      { code: 'plugin:example:edit', name: '编辑', module: '甲组', status: 'ACTIVE' as const },
      { code: 'old-code', name: '旧接口', module: '历史模块', status: 'ACTIVE' as const },
    ]
    assert.deepEqual([...permissionResourceLabels(permissions)], [...permissionResourceLabels([...permissions].reverse())])
    assert.equal(permissionResourceOf(permissions[2]!).key, 'legacy:old-code')
    assert.equal(permissionResourceOf({ code: 'custom:navigation', module: '未知来源' }).key, 'legacy:custom:navigation')
  })

  it('disambiguates equal names within a domain while leaving one resource and cross-domain names intact', () => {
    const permissions = [
      { code: 'plugin:skin-one:view', name: '查看皮肤一', module: '皮肤管理', status: 'ACTIVE' as const },
      { code: 'plugin:skin-two:view', name: '查看皮肤二', module: '皮肤管理', status: 'ACTIVE' as const },
      { code: 'plugin:skin-one:category:edit', name: '编辑分类', module: '皮肤分类', status: 'ACTIVE' as const },
      { code: 'system:user', name: '用户页', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'system:user:view', name: '查看用户', module: '系统管理', status: 'ACTIVE' as const },
      { code: 'plugin:users:view', name: '查看插件用户', module: '用户管理', status: 'ACTIVE' as const },
    ]
    const labels = permissionResourceLabels(permissions)
    assert.equal(labels.get('plugin:skin-one'), '皮肤管理（plugin:skin-one）')
    assert.equal(labels.get('plugin:skin-two'), '皮肤管理（plugin:skin-two）')
    assert.equal(labels.get('system:user'), '用户管理')
    assert.equal(labels.get('plugin:users'), '用户管理')
    assert.equal(permissionResourceOf(permissions[2]!, labels).label, labels.get('plugin:skin-one'))
    assert.deepEqual([...labels], [...permissionResourceLabels([...permissions].reverse())])
    const tree = buildPermissionTree(permissions)
    assert.ok(tree[1]?.children?.some(node => node.key === 'resource:plugin:skin-one' && node.title.startsWith('皮肤管理（plugin:skin-one）')))
    assert.equal(tree[0]?.children?.length, 1)
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
