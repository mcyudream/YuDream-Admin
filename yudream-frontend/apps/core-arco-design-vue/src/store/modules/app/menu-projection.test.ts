import assert from 'node:assert/strict'
import path from 'node:path'
import test from 'node:test'
import { findBestMenuGroupIndex } from './menu-match'
import { projectRouteMenus } from './menu-projection'

function resolvePath(basePath?: string, routePath?: string) {
  return basePath ? path.posix.resolve(basePath, routePath ?? '') : routePath ?? ''
}

test('a shared category projects multiple plugins into one primary navigation item', () => {
  const menus = projectRouteMenus([{
    meta: { title: 'MC', icon: 'morph:blocks', menuCode: 'category:mc' },
    children: [{
      meta: { title: '服务器', menuCode: 'plugin:minecraft-server:module:admin' },
      children: [{ path: '/platform/plugins/minecraft-server/admin', meta: { title: '服务器列表' } }],
    }, {
      meta: { title: '皮肤', menuCode: 'plugin:blessing-skin:module:admin' },
      children: [{ path: '/platform/plugins/blessing-skin/admin', meta: { title: '皮肤管理' } }],
    }],
  }], 'side', resolvePath)

  assert.equal(menus.length, 1)
  assert.equal(menus[0].meta?.title, 'MC')
  assert.equal(menus[0].meta?.icon, 'morph:blocks')
  assert.deepEqual(menus[0].children.map(menu => menu.meta?.title), ['服务器', '皮肤'])
  assert.equal(findBestMenuGroupIndex(menus, '/platform/plugins/blessing-skin/admin'), 0)
})

test('a legacy independent page keeps its root path and visible navigation entry', () => {
  const menus = projectRouteMenus([{
    path: '/legacy/page',
    component: 'Layout',
    meta: { title: '独立页面' },
    children: [{ path: '', component: 'legacy/Page', meta: { menu: false } }],
  }], 'side', resolvePath)

  assert.equal(menus[0].children[0].path, '/legacy/page')
  assert.equal(menus[0].children[0].meta?.title, '独立页面')
  assert.equal(menus[0].children[0].children?.[0].path, '/legacy/page')
  assert.equal(findBestMenuGroupIndex(menus, '/legacy/page'), 0)
})

test('a legacy root link without children remains available', () => {
  const menus = projectRouteMenus([{
    path: '/legacy/docs',
    component: 'Layout',
    meta: { title: '文档', link: 'https://example.com/docs' },
  }], 'side', resolvePath)

  assert.equal(menus.length, 1)
  assert.equal(menus[0].children[0].path, '/legacy/docs')
  assert.equal(menus[0].children[0].meta?.link, 'https://example.com/docs')
})

test('a legacy layout keeps its original submenu level and resolves relative paths', () => {
  const menus = projectRouteMenus([{
    path: '/legacy',
    component: 'Layout',
    meta: { title: '旧模式' },
    children: [{ path: 'list', meta: { title: '列表' } }],
  }], 'side', resolvePath)

  assert.equal(menus[0].children[0].meta?.title, '列表')
  assert.equal(menus[0].children[0].path, '/legacy/list')
})

test('a root layout containing only contextual hidden routes has no primary navigation item', () => {
  const menus = projectRouteMenus([{
    path: '/legacy',
    component: 'Layout',
    meta: { title: '旧布局' },
    children: [{ path: 'detail/:id', component: 'legacy/Detail', meta: { menu: false } }],
  }], 'side', resolvePath)

  assert.deepEqual(menus, [])
})

test('nested empty categories are pruned without creating clickable empty directories', () => {
  const menus = projectRouteMenus([{
    meta: { title: 'MC' },
    children: [{
      meta: { title: '空分类' },
      children: [{ meta: { title: '更深的空分类' } }],
    }, {
      path: '/platform/plugins/minecraft-server/admin',
      component: 'server/Admin',
      meta: { title: '服务器' },
    }],
  }], 'side', resolvePath)

  assert.equal(menus.length, 1)
  assert.deepEqual(menus[0].children.map(menu => menu.meta?.title), ['服务器'])
})

test('categories containing only hidden routes do not create visible navigation items', () => {
  const menus = projectRouteMenus([{
    meta: { title: '待办' },
    children: [{
      meta: { title: '详情分类' },
      children: [{ path: '/todos/detail/:id', component: 'todos/Detail', meta: { menu: false } }],
    }],
  }], 'side', resolvePath)

  assert.deepEqual(menus, [])
})

test('a nested layout with only contextual hidden routes does not keep its category visible', () => {
  const menus = projectRouteMenus([{
    meta: { title: '待办' },
    children: [{
      path: '/todos',
      component: () => undefined,
      meta: { title: '事项目录' },
      children: [{ path: 'detail/:id', component: 'todos/Detail', meta: { menu: false } }],
    }],
  }], 'side', resolvePath)

  assert.deepEqual(menus, [])
})

test('a nested legacy layout with a default page keeps a visible navigable entry', () => {
  const menus = projectRouteMenus([{
    meta: { title: '待办' },
    children: [{
      path: '/todos',
      component: () => undefined,
      meta: { title: '事项列表' },
      children: [
        { path: '', component: 'todos/List', meta: { menu: false } },
        { path: 'detail/:id', component: 'todos/Detail', meta: { menu: false } },
      ],
    }],
  }], 'side', resolvePath)

  assert.equal(menus.length, 1)
  assert.equal(menus[0].children[0].path, '/todos')
  assert.equal(menus[0].children[0].meta?.title, '事项列表')
  assert.equal(findBestMenuGroupIndex(menus, '/todos/detail/123'), 0)
})

test('single navigation mode combines categories and explicit independent entries', () => {
  const menus = projectRouteMenus([{
    meta: { title: '待办' },
    children: [{ path: '/todos', meta: { title: '事项' } }],
  }, {
    path: '/legacy/page',
    component: 'legacy/Page',
    meta: { title: '独立页面' },
  }, {
    path: '/contextual/detail',
    component: 'detail/Page',
    meta: { menu: false },
  }], 'single', resolvePath)

  assert.equal(menus.length, 1)
  assert.deepEqual(menus[0].children.map(menu => menu.path), ['/todos', '/legacy/page'])
})
