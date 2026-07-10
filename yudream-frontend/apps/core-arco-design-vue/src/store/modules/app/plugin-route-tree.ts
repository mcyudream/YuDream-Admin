import type { PluginFrontendModule, PluginFrontendRoute } from '@/api/modules/platform-plugin'

export type PluginRouteTreeNodeKind = 'module' | 'parent' | 'route'

export interface PluginRouteTreeNode {
  code: string
  parentCode?: string
  kind: PluginRouteTreeNodeKind
  title: string
  icon?: string
  sort?: number
  visible: boolean
  module: PluginFrontendModule
  route?: PluginFrontendRoute
  children: PluginRouteTreeNode[]
}

export interface PartitionedPluginRouteTree {
  roots: PluginRouteTreeNode[]
  staticChildren: Map<string, PluginRouteTreeNode[]>
}

export function buildPluginRouteTree(modules: PluginFrontendModule[]): PluginRouteTreeNode[] {
  const nodes = new Map<string, PluginRouteTreeNode>()
  const insertionOrder: string[] = []

  const addNode = (node: PluginRouteTreeNode) => {
    const existing = nodes.get(node.code)
    if (existing) {
      return existing
    }
    nodes.set(node.code, node)
    insertionOrder.push(node.code)
    return node
  }

  modules.forEach((module) => {
    if (!isActive(module.status)) {
      return
    }
    const moduleCode = textValue(module.menuCode) || legacyModuleCode(module)
    addNode({
      code: moduleCode,
      parentCode: textValue(module.parentCode) || undefined,
      kind: 'module',
      title: textValue(module.menuTitle) || textValue(module.moduleName) || textValue(module.pluginCode),
      icon: module.menuIcon,
      sort: module.menuSort,
      visible: module.visible !== false,
      module,
      children: [],
    })

    module.routes?.forEach((route) => {
      if (!isActive(route.status) || !isActive(route.parentStatus)) {
        return
      }
      const parentCode = textValue(route.parentMenuCode)
      if (parentCode) {
        addNode({
          code: parentCode,
          parentCode: textValue(route.parentParentCode) || undefined,
          kind: 'parent',
          title: textValue(route.parentTitle) || parentCode,
          icon: route.parentIcon,
          sort: route.parentSort,
          visible: route.parentVisible !== false,
          module,
          route,
          children: [],
        })
      }

      const routeCode = textValue(route.menuCode) || legacyRouteCode(module, route)
      addNode({
        code: routeCode,
        parentCode: textValue(route.parentCode) || parentCode || moduleCode,
        kind: 'route',
        title: textValue(route.title) || textValue(route.name) || routeCode,
        icon: route.icon,
        sort: route.sort,
        visible: route.visible !== false,
        module,
        route,
        children: [],
      })
    })
  })

  nodes.forEach(node => node.children.splice(0))
  const roots: PluginRouteTreeNode[] = []
  insertionOrder.forEach((code) => {
    const node = nodes.get(code)!
    const parent = node.parentCode ? nodes.get(node.parentCode) : undefined
    if (!parent || parent === node || hasAncestor(parent, node, nodes)) {
      roots.push(node)
      return
    }
    parent.children.push(node)
  })
  return roots
}

export function partitionPluginRouteTree(
  roots: PluginRouteTreeNode[],
  staticMenuCodes: Set<string>,
): PartitionedPluginRouteTree {
  const standaloneRoots: PluginRouteTreeNode[] = []
  const staticChildren = new Map<string, PluginRouteTreeNode[]>()

  roots.forEach((root) => {
    if (root.parentCode && staticMenuCodes.has(root.parentCode)) {
      const children = staticChildren.get(root.parentCode) || []
      children.push(root)
      staticChildren.set(root.parentCode, children)
      return
    }
    if (root.kind === 'route' || root.children.length > 0) {
      standaloneRoots.push(root)
    }
  })

  return {
    roots: standaloneRoots,
    staticChildren,
  }
}

function isActive(status?: 'ACTIVE' | 'DISABLED') {
  return status !== 'DISABLED'
}

function hasAncestor(node: PluginRouteTreeNode, candidate: PluginRouteTreeNode, nodes: Map<string, PluginRouteTreeNode>) {
  const visited = new Set<string>()
  let cursor: PluginRouteTreeNode | undefined = node
  while (cursor && !visited.has(cursor.code)) {
    if (cursor === candidate) {
      return true
    }
    visited.add(cursor.code)
    cursor = cursor.parentCode ? nodes.get(cursor.parentCode) : undefined
  }
  return false
}

function legacyModuleCode(module: PluginFrontendModule) {
  return `legacy:module:${textValue(module.pluginCode)}:${textValue(module.moduleName)}`
}

function legacyRouteCode(module: PluginFrontendModule, route: PluginFrontendRoute) {
  return `legacy:route:${textValue(module.pluginCode)}:${textValue(module.moduleName)}:${textValue(route.name) || textValue(route.path)}`
}

function textValue(value?: string) {
  return typeof value === 'string' ? value.trim() : ''
}
