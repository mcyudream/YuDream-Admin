import assert from 'node:assert/strict'
import test from 'node:test'
import {
  filterBackendMenuTreeByAuth,
  flattenBackendRouteGroups,
  stripBackendStructuralAuth,
} from './route-auth'

test('backend route groups preserve child auth instead of inheriting parent auth', () => {
  const directChildren = flattenBackendRouteGroups([{
    meta: { auth: 'system' },
    children: [{
      path: '/system/user',
      meta: { auth: 'system:user' },
    }],
  }])
  const routes = stripBackendStructuralAuth([{
    meta: { auth: 'system' },
    children: [{
      path: '/system/personnel',
      component: 'Layout',
      meta: { auth: 'system:personnel' },
      children: [{
        path: '/system/user',
        component: 'system/user/index.vue',
        meta: { auth: 'system:user' },
      }],
    }],
  }])

  const flattened = flattenBackendRouteGroups(routes)

  assert.equal(directChildren[0].meta?.auth, 'system:user')
  assert.equal(routes[0].meta?.auth, undefined)
  assert.equal(flattened[0].meta?.auth, undefined)
  assert.equal(flattened[0].children?.[0].meta?.auth, 'system:user')
})

test('backend menu keeps an unauthorized structural parent with an authorized child only', () => {
  const menus = [{
    path: '/system/personnel',
    meta: { auth: 'system:personnel' },
    children: [
      { path: '/system/user', meta: { auth: 'system:user' } },
      { path: '/system/role', meta: { auth: 'system:role' } },
    ],
  }]

  const filtered = filterBackendMenuTreeByAuth(menus, auth => auth === 'system:user')

  assert.equal(filtered.length, 1)
  assert.equal(filtered[0].path, '/system/personnel')
  assert.deepEqual(filtered[0].children?.map(item => item.path), ['/system/user'])
})

test('backend menu drops a former branch when all of its children are unauthorized', () => {
  const filtered = filterBackendMenuTreeByAuth([{
    path: '/system/personnel',
    meta: { auth: 'system:personnel' },
    children: [{ path: '/system/role', meta: { auth: 'system:role' } }],
  }], auth => auth === 'system:personnel')

  assert.deepEqual(filtered, [])
})

test('stripped backend structural auth lets matched guards use the leaf auth', () => {
  const routes = stripBackendStructuralAuth([{
    path: '/system/personnel',
    component: 'Layout',
    meta: { auth: 'system:personnel' },
    children: [{
      path: '/system/user',
      component: 'system/user/index.vue',
      meta: { auth: 'system:user' },
    }],
  }])
  const matched = [routes[0], routes[0].children![0]]
  const permissions = ['system:user']
  const parentAuth = routes[0].meta?.auth
  const leafAuth = routes[0].children![0].meta?.auth

  const appAllowed = matched.every(route => !route.meta?.auth || permissions.includes(route.meta.auth))
  const guardAllowed = [parentAuth, leafAuth].filter(Boolean).at(-1) === 'system:user'

  assert.equal(appAllowed, true)
  assert.equal(guardAllowed, true)
})
