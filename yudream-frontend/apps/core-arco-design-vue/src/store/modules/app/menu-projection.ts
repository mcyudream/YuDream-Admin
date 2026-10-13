import type { MenuRecordMainRaw, MenuRecordRaw, RouteMetaRaw } from '@fantastic-admin/types'

interface MenuRouteNode {
  path?: string
  component?: unknown
  meta?: RouteMetaRaw & { menuCode?: unknown }
  children?: MenuRouteNode[]
}

type ResolveMenuPath = (basePath?: string, routePath?: string) => string

interface ProjectedMenu {
  menu: MenuRecordRaw
  visibleNavigation: boolean
}

export function projectRouteMenus(
  routes: MenuRouteNode[],
  mode: string,
  resolvePath: ResolveMenuPath,
): MenuRecordMainRaw[] {
  const groups: MenuRecordMainRaw[] = []
  for (const route of routes) {
    // 分类与旧版布局继续展示子菜单，独立页面或外链保留自身入口。
    const projectedChildren = projectChildren(route.children ?? [], resolvePath, route.path)
    const hasVisibleChildren = projectedChildren.some(child => child.visibleNavigation)
    const isIndependentEntry = Boolean(route.path || route.component) && !hasVisibleChildren
    const children = isIndependentEntry
      ? projectChildren([route], resolvePath)
      : projectedChildren
    if (!children.some(child => child.visibleNavigation) || route.meta?.menu === false) {
      continue
    }
    const menus = children.map(child => child.menu)
    if (mode === 'single') {
      groups[0] ??= { meta: {}, children: [] }
      groups[0].children.push(...menus)
    }
    else {
      groups.push({
        meta: {
          title: route.meta?.title,
          icon: route.meta?.icon,
          auth: route.meta?.auth,
        },
        children: menus,
      })
    }
  }
  return groups
}

function projectChildren(routes: MenuRouteNode[], resolvePath: ResolveMenuPath, basePath = ''): ProjectedMenu[] {
  return routes.flatMap((route) => {
    const meta = {
      auth: route.meta?.auth,
      title: route.meta?.title,
      icon: route.meta?.icon,
      menu: route.meta?.menu,
      expand: route.meta?.expand,
      link: route.meta?.link,
      menuCode: route.meta?.menuCode,
    }
    const menu: MenuRecordRaw = {
      path: resolvePath(basePath, route.path),
      meta,
    }
    const children = projectChildren(route.children ?? [], resolvePath, menu.path)
    const hasVisibleChildren = children.some(child => child.visibleNavigation)
    const hasDefaultPage = route.children?.some(child => child.path === '' && child.component)
    const ownsPage = Boolean(route.path || route.component) && (!route.children?.length || hasDefaultPage)
    // 分类与布局都需要实际页面；路径本身不能让仅含隐藏路由的目录成为入口。
    if (!ownsPage && !hasVisibleChildren) {
      return []
    }
    if (children.length) {
      menu.children = children.map(child => child.menu)
    }
    return [{
      menu,
      visibleNavigation: route.meta?.menu !== false && Boolean(ownsPage || hasVisibleChildren),
    }]
  })
}
