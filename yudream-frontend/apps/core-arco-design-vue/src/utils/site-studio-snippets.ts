import type { StudioContainer, StudioNode } from '../types/site-studio'
import { cloneStudioJson, studioAssert, validateStudioNodes } from './site-studio-validation'

/**
 * 本地片段库：站点工作台「保存选中为可复用区块」的 localStorage 存储。
 *
 * 片段是独立副本：插入时生成全新节点 ID、剥离 DOM id 与画布选中标识，
 * 与来源页面不存在联动。localStorage 属不可信输入，读取与插入前都重新校验，
 * 非法条目整体丢弃、不影响其余片段。
 */
export interface StudioSnippet {
  version: 1
  id: string
  name: string
  createdAt: number
  /** 保存的节点子树（恰好一个根节点；引用的 data URL 随节点一并保存） */
  nodes: StudioNode[]
  /** 片段样式引用的主题变量名（含 -- 前缀；仅提示用，插入时以节点重新收集为准） */
  tokens: string[]
}

export const STUDIO_SNIPPET_LIMITS = {
  /** 每用户最多保存的区块数量 */
  count: 100,
  /** 区块名称最大字符数 */
  name: 50,
  /** 单个区块节点 JSON 的字节上限 */
  nodeBytes: 256 * 1024,
  /** 单用户片段库存储总字节上限 */
  totalBytes: 1024 * 1024,
  /** 单个区块的节点数量上限（插入目标文档还有自身节点总数与 2 MiB 限制兜底） */
  nodes: 200,
} as const

const STORAGE_KEY = 'site-studio:snippets'
const ID_PATTERN = /^[a-z0-9][\w-]{0,127}$/i
const USER_KEY_PATTERN = /^[a-z0-9][\w.@:,-]{0,127}$/i
const SNIPPET_FIELDS = ['version', 'id', 'name', 'createdAt', 'nodes', 'tokens']

/** 片段库存储键：按登录用户隔离，不同用户互不可见。 */
export function studioSnippetStorageKey(userKey: string): string {
  studioAssert(USER_KEY_PATTERN.test(userKey), '片段库用户标识无效')
  return `${STORAGE_KEY}:${userKey}`
}

/** 收集节点子树样式里引用的主题变量（var(--name)），统一带 -- 前缀并去重。 */
export function collectStudioTokenReferences(nodes: StudioNode[]): string[] {
  const referenced = new Set<string>()
  const visit = (node: StudioNode) => {
    const values = [...Object.values(node.styles), ...Object.values(node.responsive?.tablet || {}), ...Object.values(node.responsive?.mobile || {})]
    for (const value of values) {
      for (const match of value.matchAll(/var\(\s*(--[\w-]+)/g)) {
        referenced.add(match[1]!)
      }
    }
    node.children.forEach(visit)
  }
  nodes.forEach(visit)
  return [...referenced].sort()
}

/** 对比引用变量与目标文档主题变量，返回缺失清单；引用本身在插入时原样保留。 */
export function findMissingStudioTokens(referenced: string[], tokens: Record<string, string>): string[] {
  const defined = new Set(Object.keys(tokens).map(key => key.startsWith('--') ? key : `--${key}`))
  return referenced.filter(token => !defined.has(token))
}

/** 深拷贝并重建节点 ID；同时剥离画布选中标识与 DOM id，避免插入后的 ID 冲突。 */
export function freshStudioNodeTree(node: StudioNode): StudioNode {
  const copy = cloneStudioJson(node)
  copy.id = crypto.randomUUID()
  delete copy.attributes['data-studio-id']
  delete copy.attributes.id
  copy.children = copy.children.map(freshStudioNodeTree)
  return copy
}

function assertSnippetFields(value: Record<string, unknown>): asserts value is Record<string, unknown> {
  const keys = Object.keys(value)
  studioAssert(keys.length <= SNIPPET_FIELDS.length && keys.every(key => SNIPPET_FIELDS.includes(key)), '区块存在未知字段')
}

/** 校验并归一化单条区块数据；任何不合法输入返回 null（存储数据不可信）。 */
function parseSnippet(value: unknown): StudioSnippet | null {
  try {
    studioAssert(value !== null && typeof value === 'object' && !Array.isArray(value), '区块必须是对象')
    const item = value as Record<string, unknown>
    assertSnippetFields(item)
    studioAssert(item.version === 1, '不受支持的区块版本')
    studioAssert(typeof item.id === 'string' && ID_PATTERN.test(item.id), '区块 ID 无效')
    studioAssert(typeof item.name === 'string' && item.name.trim().length > 0 && item.name.length <= STUDIO_SNIPPET_LIMITS.name, '区块名称无效')
    studioAssert(typeof item.createdAt === 'number' && Number.isFinite(item.createdAt), '区块时间无效')
    studioAssert(Array.isArray(item.tokens) && item.tokens.every(token => typeof token === 'string' && /^--[\w-]+$/.test(token)), '区块主题变量引用无效')
    studioAssert(Array.isArray(item.nodes) && item.nodes.length === 1, '区块必须保存单个根节点')
    const nodes = item.nodes as StudioNode[]
    studioAssert(new TextEncoder().encode(JSON.stringify(nodes)).length <= STUDIO_SNIPPET_LIMITS.nodeBytes, '区块内容过大')
    validateStudioNodes(nodes)
    countStudioNodes(nodes)
    return { version: 1, id: item.id, name: item.name, createdAt: item.createdAt, nodes, tokens: item.tokens as string[] }
  }
  catch {
    return null
  }
}

function countStudioNodes(nodes: StudioNode[]): void {
  let count = 0
  const visit = (items: StudioNode[]) => {
    for (const item of items) {
      count += 1
      studioAssert(count <= STUDIO_SNIPPET_LIMITS.nodes, '区块节点数超过上限')
      visit(item.children)
    }
  }
  visit(nodes)
}

function readStored(storage: Storage, userKey: string): { raw: string | null, snippets: StudioSnippet[] } {
  let raw: string | null = null
  try {
    raw = storage.getItem(studioSnippetStorageKey(userKey))
  }
  catch {
    raw = null
  }
  if (!raw || new TextEncoder().encode(raw).length > STUDIO_SNIPPET_LIMITS.totalBytes) {
    return { raw: null, snippets: [] }
  }
  let parsed: unknown = null
  try {
    parsed = JSON.parse(raw)
  }
  catch {
    return { raw, snippets: [] }
  }
  const snippets = Array.isArray(parsed)
    ? parsed.slice(0, STUDIO_SNIPPET_LIMITS.count).map(parseSnippet).filter((item): item is StudioSnippet => item !== null)
    : []
  return { raw, snippets }
}

function writeStored(storage: Storage, userKey: string, snippets: StudioSnippet[]): void {
  studioAssert(snippets.length <= STUDIO_SNIPPET_LIMITS.count, `区块数量已达上限（${STUDIO_SNIPPET_LIMITS.count} 个），请先删除不需要的区块`)
  try {
    storage.setItem(studioSnippetStorageKey(userKey), JSON.stringify(snippets))
  }
  catch {
    throw new Error('区块保存失败：本地存储不可用或已满')
  }
}

function storageBytes(storage: Storage, userKey: string): number {
  const raw = storage.getItem(studioSnippetStorageKey(userKey))
  return raw ? new TextEncoder().encode(raw).length : 0
}

/** 读取当前用户的全部区块；损坏条目被丢弃而不是让整个片段库失效。 */
export function readStudioSnippets(storage: Storage, userKey: string): StudioSnippet[] {
  return readStored(storage, userKey).snippets
}

/** 保存选中节点为可复用区块；返回已持久化的片段。 */
export function saveStudioSnippet(input: { name: string, node: StudioNode }, storage: Storage, userKey: string): StudioSnippet {
  const name = input.name.trim()
  studioAssert(name.length > 0, '请为区块命名')
  studioAssert(name.length <= STUDIO_SNIPPET_LIMITS.name, `区块名称不能超过 ${STUDIO_SNIPPET_LIMITS.name} 个字符`)
  const nodes = [cloneStudioJson(input.node)]
  // 体积闸门先于逐条校验执行，避免超大内容进入完整校验路径
  const nodeJson = JSON.stringify(nodes)
  studioAssert(new TextEncoder().encode(nodeJson).length <= STUDIO_SNIPPET_LIMITS.nodeBytes, '区块内容过大，请拆分后分批保存')
  validateStudioNodes(nodes)
  countStudioNodes(nodes)
  const existing = readStudioSnippets(storage, userKey)
  studioAssert(existing.length < STUDIO_SNIPPET_LIMITS.count, `区块数量已达上限（${STUDIO_SNIPPET_LIMITS.count} 个），请先删除不需要的区块`)
  const snippet: StudioSnippet = {
    version: 1,
    id: crypto.randomUUID(),
    name,
    createdAt: Date.now(),
    nodes,
    tokens: collectStudioTokenReferences(nodes),
  }
  const nextBytes = storageBytes(storage, userKey) + new TextEncoder().encode(JSON.stringify(snippet)).length
  studioAssert(nextBytes <= STUDIO_SNIPPET_LIMITS.totalBytes, '本地区块库存储已满，请先删除不需要的区块')
  writeStored(storage, userKey, [...existing, snippet])
  return snippet
}

/** 删除指定区块；不存在时保持静默（多端同步场景幂等）。 */
export function deleteStudioSnippet(id: string, storage: Storage, userKey: string): void {
  const { snippets } = readStored(storage, userKey)
  writeStored(storage, userKey, snippets.filter(item => item.id !== id))
}

export interface StudioSnippetInsertion {
  /** 复用既有 insert 命令的单次插入；一次插入即一次撤销。 */
  command: { type: 'insert', target: StudioContainer, node: StudioNode }
  /** 插入后新树的根节点 ID（全新生成），用于选中新元素。 */
  rootId: string
  /** 以插入内容为准重新收集的主题变量引用。 */
  tokens: string[]
}

/** 把区块转换为插入命令：再校验一次后重建全部节点 ID，避免与文档既有 ID 冲突。 */
export function createStudioSnippetInsertion(snippet: unknown, target: StudioContainer): StudioSnippetInsertion {
  const parsed = parseSnippet(snippet)
  studioAssert(parsed, '区块数据已损坏或版本不受支持，无法插入')
  const fresh = freshStudioNodeTree(parsed!.nodes[0]!)
  studioAssert(fresh.id !== parsed!.nodes[0]!.id, '区块插入必须生成全新节点 ID')
  validateStudioNodes([fresh])
  return {
    command: { type: 'insert', target, node: fresh },
    rootId: fresh.id,
    tokens: collectStudioTokenReferences([fresh]),
  }
}
