import type { SiteDocument, StudioCommand, StudioContainer, StudioNode } from '../types/site-studio'
import { cloneStudioJson, STUDIO_LIMITS, studioAssert, validateStudioDocument, validateStudioNodes } from './site-studio-validation'

export function createStudioNode(type: string): StudioNode {
  const tag: Record<string, string> = { text: 'p', heading: 'h2', section: 'section', container: 'div', image: 'img', video: 'video', audio: 'audio', link: 'a', header: 'header', footer: 'footer', button: 'button' }
  return { id: crypto.randomUUID(), type, tag: tag[type] ?? 'div', attributes: {}, styles: {}, children: [], ...(['text', 'heading', 'link', 'button'].includes(type) ? { text: type === 'heading' ? '标题' : '文本' } : {}) }
}
export function createStudioDocument(name = '未命名站点'): SiteDocument {
  return { schemaVersion: 1, id: crypto.randomUUID(), name, theme: { tokens: {}, header: [], footer: [] }, pages: [{ id: crypto.randomUUID(), name: '首页', path: '/', root: [] }], assets: [] }
}
function collections(document: SiteDocument) {
  return [document.theme.header, ...document.pages.map(page => page.root), document.theme.footer]
}
interface Location {
  node: StudioNode
  siblings: StudioNode[]
  index: number
}
function locate(document: SiteDocument, nodeId: string): Location | undefined {
  const visit = (nodes: StudioNode[]): Location | undefined => {
    for (const [index, node] of nodes.entries()) {
      if (node.id === nodeId) {
        return { node, siblings: nodes, index }
      }
      const found = visit(node.children)
      if (found) {
        return found
      }
    }
  }
  for (const nodes of collections(document)) {
    const found = visit(nodes)
    if (found) {
      return found
    }
  }
}
export function findStudioNode(document: SiteDocument, nodeId: string): StudioNode | undefined {
  return locate(document, nodeId)?.node
}
function requireLocation(document: SiteDocument, nodeId: string) {
  const found = locate(document, nodeId)
  studioAssert(found, '节点不存在；页面与主题根集合不能作为节点删除或移动')
  return found
}
function container(document: SiteDocument, target: StudioContainer) {
  if ('pageId' in target) {
    const page = document.pages.find(page => page.id === target.pageId)
    studioAssert(page, '目标页面不存在')
    return page.root
  }
  if ('region' in target) {
    studioAssert(target.region === 'header' || target.region === 'footer', '无效主题区域')
    return document.theme[target.region]
  }
  const parent = requireLocation(document, target.parentId).node
  studioAssert(parent.text === undefined, '文本节点不能插入子节点；请显式转换内容')
  return parent.children
}
function insert(nodes: StudioNode[], node: StudioNode, index = nodes.length) {
  studioAssert(Number.isInteger(index) && index >= 0 && index <= nodes.length, '插入位置无效')
  nodes.splice(index, 0, node)
}
function freshNode(node: StudioNode): StudioNode {
  const copy = cloneStudioJson(node)
  copy.id = crypto.randomUUID()
  delete copy.attributes['data-studio-id']
  // DOM ids must not duplicate either; studio ids drive editor selection and CSS.
  delete copy.attributes.id
  copy.children = copy.children.map(freshNode)
  return copy
}
function mutate(document: SiteDocument, command: StudioCommand) {
  switch (command.type) {
    case 'batch': throw new Error('batch 不能嵌套')
    case 'set-text': {
      const node = requireLocation(document, command.nodeId).node
      studioAssert(node.children.length === 0, '含子节点的容器不能直接设置文本')
      node.text = command.text
      break
    }
    case 'update-node':
      Object.assign(requireLocation(document, command.nodeId).node, cloneStudioJson(command.patch))
      break
    case 'set-style': {
      const node = requireLocation(document, command.nodeId).node
      const breakpoint = command.breakpoint ?? 'desktop'
      studioAssert(['desktop', 'tablet', 'mobile'].includes(breakpoint), '无效断点')
      let target = node.styles
      if (breakpoint !== 'desktop') {
        node.responsive ??= {}
        node.responsive[breakpoint] ??= {}
        target = node.responsive[breakpoint]!
      }
      for (const [key, value] of Object.entries(command.styles)) {
        if (value === null) {
          delete target[key]
        }
        else {
          target[key] = value
        }
      }
      break
    }
    case 'insert':
      insert(container(document, command.target), cloneStudioJson(command.node), command.index)
      break
    case 'remove': {
      const found = requireLocation(document, command.nodeId)
      found.siblings.splice(found.index, 1)
      break
    }
    case 'move': {
      const found = requireLocation(document, command.nodeId)
      if ('parentId' in command.target) {
        const contains = (node: StudioNode): boolean => node.id === (command.target as {
          parentId: string
        }).parentId || node.children.some(contains)
        studioAssert(!contains(found.node), '不能把节点移动到自身或子节点内')
      }
      const target = container(document, command.target)
      found.siblings.splice(found.index, 1)
      insert(target, found.node, command.index)
      break
    }
    case 'duplicate': {
      const found = requireLocation(document, command.nodeId)
      insert(found.siblings, freshNode(found.node), found.index + 1)
      break
    }
    case 'set-theme': {
      for (const key of ['tokens', 'header', 'footer'] as const) {
        if (command[key] !== undefined) {
          Object.assign(document.theme, { [key]: cloneStudioJson(command[key]) })
        }
      }
      break
    }
    case 'add-page':
      document.pages.push(cloneStudioJson(command.page))
      break
    case 'update-page': {
      const page = document.pages.find(page => page.id === command.pageId)
      studioAssert(page, '页面不存在')
      Object.assign(page, cloneStudioJson(command.patch))
      break
    }
    case 'remove-page': {
      const index = document.pages.findIndex(page => page.id === command.pageId)
      studioAssert(index >= 0 && document.pages.length > 1, '页面不存在或是站点最后一个页面')
      document.pages.splice(index, 1)
      break
    }
    case 'set-assets':
      document.assets = cloneStudioJson(command.assets)
      break
    default: throw new Error('未知编辑命令')
  }
}
interface BatchGuard {
  ids: Set<string>
  count: number
}
function collectGuard(document: SiteDocument): BatchGuard {
  const ids = new Set([document.id, ...document.pages.map(page => page.id), ...document.assets.map(asset => asset.id)])
  let count = 0
  const visit = (nodes: StudioNode[]) => nodes.forEach((node) => {
    ids.add(node.id)
    count++
    visit(node.children)
  })
  collections(document).forEach(visit)
  return { ids, count }
}
function subtreeIds(node: StudioNode, into: string[] = []) {
  into.push(node.id)
  node.children.forEach(child => subtreeIds(child, into))
  return into
}
const LOCAL_PATCH_KEYS = new Set(['type', 'tag', 'text', 'attributes', 'styles', 'responsive'])
/**
 * One batch step. Field-only commands defer validation to the final full check; they
 * cannot change IDs or amplify size. ID uniqueness and the node budget are enforced at
 * every step because later commands address nodes by ID. Anything else (structural
 * patches, duplicate, theme/page/asset commands) still gets a full intermediate check.
 */
function applyBatchItem(document: SiteDocument, item: StudioCommand, guard: BatchGuard): BatchGuard {
  switch (item.type) {
    case 'set-text':
    case 'set-style':
    case 'move':
      mutate(document, item)
      return guard
    case 'update-node':
      if (Object.keys(item.patch ?? {}).every(key => LOCAL_PATCH_KEYS.has(key))) {
        mutate(document, item)
        return guard
      }
      break
    case 'remove': {
      const ids = subtreeIds(requireLocation(document, item.nodeId).node)
      mutate(document, item)
      ids.forEach(id => guard.ids.delete(id))
      guard.count -= ids.length
      return guard
    }
    case 'insert': {
      validateStudioNodes([item.node])
      const ids = subtreeIds(item.node)
      studioAssert(guard.count + ids.length <= STUDIO_LIMITS.nodes, '节点数超过 2000')
      ids.forEach(id => studioAssert(!guard.ids.has(id), `重复 ID：${id}`))
      mutate(document, item)
      ids.forEach(id => guard.ids.add(id))
      guard.count += ids.length
      return guard
    }
  }
  mutate(document, item)
  validateStudioDocument(document)
  return collectGuard(document)
}
/**
 * The caller stores document snapshots for undo/redo. No command mutates its input.
 * `trustedInput` skips re-validating a document the caller already holds validated.
 */
export function applyStudioCommand(document: SiteDocument, command: StudioCommand, options: { trustedInput?: boolean } = {}): SiteDocument {
  if (!options.trustedInput) {
    validateStudioDocument(document)
  }
  const copy = cloneStudioJson(document)
  if (command.type === 'batch') {
    studioAssert(Array.isArray(command.commands) && command.commands.length <= 2000, '批命令数量无效')
    let guard = collectGuard(copy)
    for (const item of command.commands) {
      guard = applyBatchItem(copy, item, guard)
    }
  }
  else {
    mutate(copy, command)
  }
  validateStudioDocument(copy)
  return copy
}
