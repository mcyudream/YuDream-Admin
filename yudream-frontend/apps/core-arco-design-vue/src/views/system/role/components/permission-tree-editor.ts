import type { YdTreeNode } from '@yudream/components'
import type { PermissionItem } from '@/api/modules/system-role'
import type { PermissionCategoryVariant, PermissionTreeNode } from '@/utils/permission-catalog'
import { buildPermissionTree, permissionCategoryOf, permissionResourceLabels, permissionResourceOf } from '@/utils/permission-catalog'

export interface PermissionEditorNode extends YdTreeNode {
  code?: string
  variant?: PermissionCategoryVariant
  children?: PermissionEditorNode[]
}

export interface PermissionTreeCatalog {
  data: PermissionEditorNode[]
  searchText: Map<string, string>
  leafCodes: string[]
  nodeIndex: Map<string, PermissionEditorNode>
  ancestors: Map<string, string[]>
}

export interface PermissionEditorView {
  data: PermissionEditorNode[]
  leafCodes: string[]
  branchKeys: string[]
  virtualList: true
  defaultExpandAll: false
}

export interface PermissionEditorPage extends PermissionEditorView {
  page: number
  size: number
  total: number
  pageCount: number
  from: number
  to: number
}

export const permissionEditorPageSizes = [50, 100, 200]
export const permissionEditorDefaultPageSize = 100

function leafCount(nodes: PermissionEditorNode[]): number {
  return nodes.reduce((total, node) => total + (node.code ? 1 : leafCount(node.children || [])), 0)
}

function viewKeys(nodes: PermissionEditorNode[], leaves: boolean): string[] {
  return nodes.flatMap(node => [
    ...((leaves ? !!node.code : !node.code) ? [node.key] : []),
    ...viewKeys(node.children || [], leaves),
  ])
}

/** 目录树和检索索引仅依赖权限登记，授权勾选不重建整棵数据树。 */
export function createPermissionTreeCatalog(permissions: readonly PermissionItem[]): PermissionTreeCatalog {
  function convert(nodes: PermissionTreeNode[]): PermissionEditorNode[] {
    return nodes.map(node => ({
      key: node.key,
      label: node.title,
      code: node.code,
      variant: node.variant,
      children: node.children ? convert(node.children) : undefined,
    }))
  }
  const data = convert(buildPermissionTree([...permissions]))
  const labels = permissionResourceLabels(permissions)
  const searchText = new Map(permissions.filter(item => item.status === 'ACTIVE').map((item) => {
    const resource = permissionResourceOf(item, labels)
    return [item.code, [item.name, item.code, item.module, item.desc, resource.label, resource.domainLabel, permissionCategoryOf(item.code).label].filter(Boolean).join(' ').toLocaleLowerCase()]
  }))
  const nodeIndex = new Map<string, PermissionEditorNode>()
  const ancestors = new Map<string, string[]>()
  function index(nodes: PermissionEditorNode[], path: string[]) {
    for (const node of nodes) {
      nodeIndex.set(node.key, node)
      if (node.code) {
        ancestors.set(node.code, path)
      }
      else {
        index(node.children || [], [...path, node.key])
      }
    }
  }
  index(data, [])
  return { data, searchText, leafCodes: viewKeys(data, true), nodeIndex, ancestors }
}

/** 搜索完整目录，结果仍按登记树的稳定顺序排列，不受当前页影响。 */
export function permissionEditorMatches(catalog: PermissionTreeCatalog, keyword = ''): string[] {
  const query = keyword.trim().toLocaleLowerCase()
  return query ? catalog.leafCodes.filter(code => catalog.searchText.get(code)?.includes(query)) : catalog.leafCodes
}

/** 只构造当前页命中的祖先路径，叶对象复用目录缓存。 */
function pageTree(catalog: PermissionTreeCatalog, codes: string[]): PermissionEditorNode[] {
  const roots: PermissionEditorNode[] = []
  const branches = new Map<string, PermissionEditorNode>()
  for (const code of codes) {
    const leaf = catalog.nodeIndex.get(code)
    if (!leaf?.code) {
      continue
    }
    let children = roots
    for (const key of catalog.ancestors.get(code) || []) {
      let branch = branches.get(key)
      if (!branch) {
        branch = { ...catalog.nodeIndex.get(key)!, children: [] }
        branches.set(key, branch)
        children.push(branch)
      }
      children = branch.children!
    }
    children.push(leaf)
  }
  for (const branch of branches.values()) {
    branch.label = branch.label.replace(/（\d+）$/, `（${leafCount(branch.children || [])}）`)
  }
  return roots
}

/** 大目录也只向 YdTree 传本页权限，分页与授权勾选互不改变目录缓存。 */
export function permissionEditorPage(catalog: PermissionTreeCatalog, matching: readonly string[], requestedPage = 1, requestedSize = permissionEditorDefaultPageSize): PermissionEditorPage {
  const size = Number.isFinite(requestedSize) && requestedSize > 0 ? Math.min(200, Math.max(1, Math.floor(requestedSize))) : permissionEditorDefaultPageSize
  const total = matching.length
  const pageCount = Math.max(1, Math.ceil(total / size))
  const page = Number.isFinite(requestedPage) ? Math.min(pageCount, Math.max(1, Math.floor(requestedPage))) : 1
  const offset = (page - 1) * size
  const leafCodes = matching.slice(offset, offset + size)
  const data = pageTree(catalog, leafCodes)
  return { data, leafCodes, branchKeys: viewKeys(data, false), virtualList: true, defaultExpandAll: false, page, size, total, pageCount, from: total ? offset + 1 : 0, to: offset + leafCodes.length }
}

/** 分组勾选状态按本页叶子推导；这些展示键不能写回角色权限配置。 */
export function permissionEditorCheckedKeys(view: PermissionEditorView, selected: readonly string[]): string[] {
  const granted = new Set(selected)
  const checked: string[] = []
  function visit(node: PermissionEditorNode): boolean {
    if (node.code) {
      if (granted.has(node.code)) {
        checked.push(node.key)
        return true
      }
      return false
    }
    const states = (node.children || []).map(visit)
    if (states.length && states.every(Boolean)) {
      checked.push(node.key)
      return true
    }
    return false
  }
  view.data.forEach(visit)
  return checked
}

/** 搜索仅剪枝命中路径，保留叶对象；展开多少节点都使用同一个虚拟窗口。 */
export function permissionEditorView(catalog: PermissionTreeCatalog, keyword = ''): PermissionEditorView {
  const query = keyword.trim().toLocaleLowerCase()
  function filter(nodes: PermissionEditorNode[]): PermissionEditorNode[] {
    const result: PermissionEditorNode[] = []
    for (const node of nodes) {
      if (node.code) {
        if (catalog.searchText.get(node.code)?.includes(query)) {
          result.push(node)
        }
      }
      else {
        const children = filter(node.children || [])
        if (children.length) {
          result.push({ ...node, label: node.label.replace(/（\d+）$/, `（${leafCount(children)}）`), children })
        }
      }
    }
    return result
  }
  const data = query ? filter(catalog.data) : catalog.data
  return { data, leafCodes: query ? viewKeys(data, true) : catalog.leafCodes, branchKeys: viewKeys(data, false), virtualList: true, defaultExpandAll: false }
}

export function permissionEditorDefaultExpansion(view: PermissionEditorView): string[] {
  return view.data.map(node => node.key)
}
