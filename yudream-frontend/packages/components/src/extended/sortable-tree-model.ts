export type YdSortableTreeDropPosition = -1 | 0 | 1

export interface YdSortableTreeNode {
  key: string
  label: string
  disabled?: boolean
  lazy?: boolean
  children?: YdSortableTreeNode[]
  [key: string]: unknown
}

export interface YdSortableTreeDropEvent {
  dragNode: YdSortableTreeNode
  dropNode: YdSortableTreeNode
  dragKey: string
  dropKey: string
  dropPosition: YdSortableTreeDropPosition
  sourceParentKey: string | null
  targetParentKey: string | null
  sourceDepth: number
  targetDepth: number
  nextDepth: number
  subtreeHeight: number
  trigger: 'pointer' | 'keyboard'
}

export type YdSortableTreeDropRejection = 'missing-node' | 'disabled-node' | 'self-drop' | 'cycle' | 'max-depth'

export type YdSortableTreeDropResult
  = | { accepted: true, event: YdSortableTreeDropEvent }
    | { accepted: false, reason: YdSortableTreeDropRejection }

interface NodeLocation {
  node: YdSortableTreeNode
  parentKey: string | null
  depth: number
  disabled: boolean
}

function indexTree(data: YdSortableTreeNode[]): Map<string, NodeLocation> {
  const index = new Map<string, NodeLocation>()
  const visit = (nodes: YdSortableTreeNode[], parentKey: string | null, depth: number, inheritedDisabled: boolean) => {
    for (const node of nodes) {
      const disabled = inheritedDisabled || Boolean(node.disabled)
      index.set(node.key, { node, parentKey, depth, disabled })
      visit(node.children ?? [], node.key, depth + 1, disabled)
    }
  }
  visit(data, null, 1, false)
  return index
}

function subtreeHeight(node: YdSortableTreeNode): number {
  const children = node.children ?? []
  return children.length ? 1 + Math.max(...children.map(subtreeHeight)) : 1
}

function subtreeContains(node: YdSortableTreeNode, key: string): boolean {
  return node.key === key || (node.children ?? []).some(child => subtreeContains(child, key))
}

/** Validate a reorder candidate and calculate the exact parent/depth metadata without mutating data. */
export function resolveYdSortableTreeDrop(
  data: YdSortableTreeNode[],
  dragKey: string,
  dropKey: string,
  dropPosition: YdSortableTreeDropPosition,
  options: { maxDepth?: number, trigger?: YdSortableTreeDropEvent['trigger'] } = {},
): YdSortableTreeDropResult {
  const nodes = indexTree(data)
  const drag = nodes.get(dragKey)
  const drop = nodes.get(dropKey)
  if (!drag || !drop) {
    return { accepted: false, reason: 'missing-node' }
  }
  if (drag.disabled || drop.disabled) {
    return { accepted: false, reason: 'disabled-node' }
  }
  if (dragKey === dropKey) {
    return { accepted: false, reason: 'self-drop' }
  }
  if (subtreeContains(drag.node, dropKey)) {
    return { accepted: false, reason: 'cycle' }
  }
  const targetParentKey = dropPosition === 0 ? dropKey : drop.parentKey
  const nextDepth = dropPosition === 0 ? drop.depth + 1 : drop.depth
  const height = subtreeHeight(drag.node)
  if (options.maxDepth !== undefined && nextDepth + height - 1 > options.maxDepth) {
    return { accepted: false, reason: 'max-depth' }
  }
  return {
    accepted: true,
    event: {
      dragNode: drag.node,
      dropNode: drop.node,
      dragKey,
      dropKey,
      dropPosition,
      sourceParentKey: drag.parentKey,
      targetParentKey,
      sourceDepth: drag.depth,
      targetDepth: drop.depth,
      nextDepth,
      subtreeHeight: height,
      trigger: options.trigger ?? 'pointer',
    },
  }
}

function cloneTree(nodes: YdSortableTreeNode[]): YdSortableTreeNode[] {
  return nodes.map(node => ({ ...node, children: node.children ? cloneTree(node.children) : undefined }))
}

function detachNode(nodes: YdSortableTreeNode[], key: string): YdSortableTreeNode | undefined {
  const index = nodes.findIndex(node => node.key === key)
  if (index >= 0) {
    return nodes.splice(index, 1)[0]
  }
  for (const node of nodes) {
    const detached = detachNode(node.children ?? [], key)
    if (detached) {
      return detached
    }
  }
}

function findNode(nodes: YdSortableTreeNode[], key: string): YdSortableTreeNode | undefined {
  for (const node of nodes) {
    if (node.key === key) {
      return node
    }
    const found = findNode(node.children ?? [], key)
    if (found) {
      return found
    }
  }
}

function findSiblings(nodes: YdSortableTreeNode[], key: string): YdSortableTreeNode[] | undefined {
  if (nodes.some(node => node.key === key)) {
    return nodes
  }
  for (const node of nodes) {
    const found = findSiblings(node.children ?? [], key)
    if (found) {
      return found
    }
  }
}

/** Apply an accepted event immutably; callers may instead persist the event through their own model. */
export function moveYdSortableTreeNode(data: YdSortableTreeNode[], event: YdSortableTreeDropEvent): YdSortableTreeNode[] {
  const result = cloneTree(data)
  const moved = detachNode(result, event.dragKey)
  if (!moved) {
    return data
  }
  if (event.dropPosition === 0) {
    const parent = findNode(result, event.dropKey)
    if (!parent) {
      return data
    }
    parent.children = [...(parent.children ?? []), moved]
    return result
  }
  const siblings = findSiblings(result, event.dropKey)
  const targetIndex = siblings?.findIndex(node => node.key === event.dropKey) ?? -1
  if (!siblings || targetIndex < 0) {
    return data
  }
  siblings.splice(targetIndex + (event.dropPosition === 1 ? 1 : 0), 0, moved)
  return result
}
