import assert from 'node:assert/strict'
import test from 'node:test'
import { buildPluginRouteTree, partitionPluginRouteTree } from './plugin-route-tree'

test('places a plugin module below its configured plugin parent instead of creating a second root menu', () => {
  const tree = buildPluginRouteTree([
    {
      pluginCode: 'yudream-student-info',
      moduleName: 'yudreamStudentInfo',
      menuCode: 'plugin:yudream-student-info:module:yudreamStudentInfo',
      menuTitle: '学生信息',
      routes: [],
    },
    {
      pluginCode: 'minecraft-activity-proof',
      moduleName: 'minecraftActivityProof',
      menuCode: 'plugin:minecraft-activity-proof:module:minecraftActivityProof',
      parentCode: 'plugin:yudream-student-info:module:yudreamStudentInfo',
      menuTitle: '活动证明',
      routes: [{
        menuCode: 'plugin:minecraft-activity-proof:route:minecraftActivityProof:proof',
        parentCode: 'plugin:minecraft-activity-proof:module:minecraftActivityProof',
        path: '/student/activity-proofs',
        name: 'activity-proofs',
        title: '活动证明记录',
      }],
    },
  ])

  assert.equal(tree.length, 1)
  assert.equal(tree[0].code, 'plugin:yudream-student-info:module:yudreamStudentInfo')
  assert.equal(tree[0].children[0].code, 'plugin:minecraft-activity-proof:module:minecraftActivityProof')
  assert.equal(tree[0].children[0].children[0].code, 'plugin:minecraft-activity-proof:route:minecraftActivityProof:proof')
})

test('assigns a plugin route to its configured static menu parent', () => {
  const tree = buildPluginRouteTree([{
    pluginCode: 'minecraft-server',
    moduleName: 'minecraftServer',
    menuCode: 'plugin:minecraft-server:module:minecraftServer',
    menuTitle: 'MC Server',
    routes: [{
      menuCode: 'plugin:minecraft-server:route:minecraftServer:admin',
      parentCode: 'system:logs',
      path: '/platform/plugins/minecraft-server/admin',
      name: 'minecraft-server-admin',
      title: 'Server Management',
    }],
  }])

  const result = partitionPluginRouteTree(tree, new Set(['system:logs']))

  assert.equal(result.roots.length, 0)
  assert.equal(result.staticChildren.get('system:logs')?.[0].code, 'plugin:minecraft-server:route:minecraftServer:admin')
})
