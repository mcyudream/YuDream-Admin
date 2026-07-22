export type RouteAuthValue = string | string[]

export interface AuthRouteNode {
  path?: string
  component?: unknown
  meta?: Record<string, unknown> & { auth?: RouteAuthValue }
  children?: AuthRouteNode[]
}

export function stripBackendStructuralAuth<T extends AuthRouteNode>(routes: T[]): T[] {
  return routes.map((route) => {
    const children = route.children?.length
      ? stripBackendStructuralAuth(route.children)
      : route.children
    const meta = route.meta ? { ...route.meta } : undefined
    if (children?.length && (route.component === undefined || route.component === 'Layout')) {
      delete meta?.auth
    }
    return {
      ...route,
      ...(meta && { meta }),
      ...(children && { children }),
    } as T
  })
}

export function flattenBackendRouteGroups<T extends AuthRouteNode>(routes: T[]): AuthRouteNode[] {
  return routes.flatMap((route) => {
    if (!route.path && !route.component) {
      return flattenBackendRouteGroups(route.children ?? [])
    }
    const children = route.children?.length
      ? flattenBackendRouteGroups(route.children)
      : route.children
    return [{
      ...route,
      ...(children?.length ? { children } : { children: undefined }),
    } as T]
  })
}

export function mergeBackendStructuralRoutes<T extends AuthRouteNode>(routes: T[]): T[] {
  const merged = new Map<string, T>()
  const result: T[] = []

  routes.forEach((route) => {
    const children = route.children?.length
      ? mergeBackendStructuralRoutes(route.children)
      : route.children
    const normalized = {
      ...route,
      ...(children && { children }),
    } as T
    const isLayoutBranch = normalized.component === 'Layout'
      && typeof normalized.path === 'string'
      && normalized.path.length > 0
      && children?.length

    if (!isLayoutBranch) {
      result.push(normalized)
      return
    }

    const existing = merged.get(normalized.path!)
    if (!existing) {
      merged.set(normalized.path!, normalized)
      result.push(normalized)
      return
    }
    existing.children = [...(existing.children ?? []), ...children]
  })

  return result
}

export function filterBackendMenuTreeByAuth<T extends AuthRouteNode>(
  menus: T[],
  auth: (value: RouteAuthValue) => boolean,
): T[] {
  const result: T[] = []
  menus.forEach((menu) => {
    const wasBranch = Boolean(menu.children?.length)
    const children = wasBranch
      ? filterBackendMenuTreeByAuth(menu.children!, auth)
      : []
    if (children.length) {
      result.push({ ...menu, children } as T)
      return
    }
    if (wasBranch) {
      return
    }
    if (auth(menu.meta?.auth ?? '')) {
      const leaf = { ...menu } as T
      delete leaf.children
      result.push(leaf)
    }
  })
  return result
}
