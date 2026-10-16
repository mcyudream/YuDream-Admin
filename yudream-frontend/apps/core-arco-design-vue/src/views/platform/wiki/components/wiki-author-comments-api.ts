import type { WikiAnchorBlockType, WikiQuoteAnchor, WikiQuoteAnchorScope } from '../../../wiki/wiki-quote-anchor'
import type { ApiResponse, PageResult } from '@/api/modules/system-client'
import { normalizeWikiVisibleText } from '../../../wiki/wiki-quote-anchor'

/**
 * 作者工作区局部评论接口适配层（与公开端 `views/wiki/wiki-public-api.ts` 同构）。
 *
 * 为什么单独放这里而不是 `@/api/modules/platform-wiki.ts`：共享接口模块由宿主维护，
 * 这里只声明作者工作区「读取已发布评论 + 发表待审核评论」真正用到的类型与调用，
 * 路径与后端 WikiPublicCommentController 一一对应：
 *
 * - `GET  api/public/wiki/{spaceSlug}/nodes/{nodeId}/comments?page=&size=`（PageResult，只读已发布评论）
 * - `POST api/public/wiki/{spaceSlug}/nodes/{nodeId}/comments`（body: content + parentId? + anchor?）
 *
 * 契约要点（与后端 WikiCommentRes / WikiCommentCreateRequest 对齐）：
 * - 作者身份由服务端从认证上下文推导，请求不携带作者/空间字段；未登录一律拒绝，匿名评论未开放；
 * - 锚点请求只提交引用文本（exact）、可选前后文（prefix/suffix）、扁平块提示（blockOrdinal/blockType/tableRow/tableCell）与起止偏移（startOffset/endOffset）；版本身份只在评论响应的 anchor 兄弟字段中出现，服务端盖章；
 * - 服务端校验空间公开且开启评论、节点为本空间已发布页面，并要求引用能在已发布正文中唯一命中；
 *   无法唯一命中的锚点直接拒绝（不会静默退化为整页评论）；
 * - 新评论与回复一律先落 PENDING，审核通过后才出现在只读列表；
 * - 所有 ID 一律保持字符串（雪花 ID 禁止 Number()）。
 *
 * 纯映射函数与 HTTP 调用放在同一模块：前者可被 node:test 直接覆盖，后者只做路径拼装。
 */

/** 锚点请求载荷：与 WikiCommentAnchorRequest 的 flat field 完全一致。 */
export interface WikiAuthorCommentAnchor {
  exact: string
  prefix?: string
  suffix?: string
  blockOrdinal?: number
  blockType?: string
  tableRow?: number
  tableCell?: number
  startOffset?: number
  endOffset?: number
}

/** 评论响应锚点身份与状态是其兄弟字段；公开端不包含空间/作者内部 ID。 */
export interface WikiAuthorComment {
  id: string
  nodeId?: string
  parentId?: string
  authorType?: string
  authorName?: string
  content: string
  anchor?: WikiAuthorCommentAnchor | null
  versionId?: string
  revision?: string
  contentHash?: string
  anchorState?: 'active' | 'reanchorable' | 'orphaned'
  status?: string
  pinned?: boolean
  createTime?: string
  updateTime?: string
  replies?: WikiAuthorComment[]
}

export interface WikiAuthorCommentCreatePayload {
  content: string
  parentId?: string
  anchor?: WikiAuthorCommentAnchor
}
/** 宿主纯定位模型的块类型 → 后端 lowerCamel 块类型词表（WikiTextBlockType） */
const WIKI_COMMENT_BLOCK_TYPE_TO_SERVER: Record<string, string> = {
  paragraph: 'paragraph',
  heading: 'heading',
  listItem: 'listItem',
  blockquote: 'blockquote',
  codeBlock: 'codeBlock',
  tableCell: 'tableCell',
}

/** 后端与宿主当前共享 lowerCamel 字面值；未知类型不作为定位提示 */
const WIKI_COMMENT_BLOCK_TYPE_FROM_SERVER: Record<string, WikiAnchorBlockType> = {
  paragraph: 'paragraph',
  heading: 'heading',
  listItem: 'listItem',
  blockquote: 'blockquote',
  codeBlock: 'codeBlock',
  tableCell: 'tableCell',
}

/** 映射块类型到服务端词表；未知类型返回 undefined（不提交含糊的定位提示） */
export function wikiCommentBlockTypeToServer(type?: string | null): string | undefined {
  return type ? WIKI_COMMENT_BLOCK_TYPE_TO_SERVER[type] : undefined
}

/** 服务端块类型反解为宿主模型块类型；未知类型返回 undefined（只影响定位提示，不影响引用原文匹配） */
export function wikiCommentBlockTypeFromServer(type?: string | null): WikiAnchorBlockType | undefined {
  return type ? WIKI_COMMENT_BLOCK_TYPE_FROM_SERVER[type.trim()] : undefined
}

function isWikiCommentSpace(char: string | undefined): boolean {
  return char == null || /\s/.test(char)
}

/**
 * 选区是否与相邻文字紧邻（交界处没有空白）。
 *
 * 服务端对前后文只做「紧邻」校验：prefix 必须正好是引用原文之前的文本、suffix 正好是之后的文本，
 * 且落库前会折叠空白并去首尾。只要交界处是空白，提交的上下文在服务端就永远无法命中，
 * 因此这里只在紧邻时提交上下文，其余情况退回「只按引用原文唯一命中」，由服务端给出可操作错误。
 */
export function wikiCommentContextAdjacent(anchor: WikiQuoteAnchor, blockText: string, side: 'before' | 'after'): boolean {
  const text = normalizeWikiVisibleText(blockText)
  if (side === 'before') {
    const offset = anchor.startOffset
    return offset != null && offset > 0 && !isWikiCommentSpace(text[offset - 1])
  }
  const offset = anchor.endOffset
  return offset != null && offset < text.length && !isWikiCommentSpace(text[offset])
}

/**
 * 预览选区锚点 → 服务端创建请求锚点。
 *
 * - 只提交引用文本与定位提示，绝不提交版本/哈希（服务端按当前发布版本盖章，客户端无法伪造）；
 * - 块提示仅在类型可映射且序号有效时提交；TABLE_CELL 缺行列时整体丢弃（服务端要求行列成对）；
 * - 前后文只在紧邻时提交（见 wikiCommentContextAdjacent）。
 */
export function toWikiAuthorCommentAnchor(anchor: WikiQuoteAnchor | null | undefined, blockText: string): WikiAuthorCommentAnchor | null {
  if (!anchor) {
    return null
  }
  const exact = normalizeWikiVisibleText(anchor.exact)
  if (!exact) {
    return null
  }
  const payload: WikiAuthorCommentAnchor = { exact }
  const prefix = normalizeWikiVisibleText(anchor.prefix)
  if (prefix && wikiCommentContextAdjacent(anchor, blockText, 'before')) {
    payload.prefix = prefix
  }
  const suffix = normalizeWikiVisibleText(anchor.suffix)
  if (suffix && wikiCommentContextAdjacent(anchor, blockText, 'after')) {
    payload.suffix = suffix
  }
  const ordinal = anchor.blockOrdinal
  const blockType = anchor.blockType ? wikiCommentBlockTypeToServer(anchor.blockType) : undefined
  const validTableCell = blockType !== 'tableCell' || (anchor.tableRow != null && anchor.tableCell != null
    && anchor.tableRow >= 0 && anchor.tableCell >= 0)
  if (blockType && ordinal != null && ordinal >= 0 && validTableCell) {
    payload.blockType = blockType
    payload.blockOrdinal = ordinal
    if (blockType === 'tableCell') {
      payload.tableRow = anchor.tableRow
      payload.tableCell = anchor.tableCell
    }
  }
  if (ordinal != null && ordinal >= 0) {
    const start = anchor.startOffset
    const end = anchor.endOffset
    if (start != null && end != null && start >= 0 && end > start) {
      payload.startOffset = start
      payload.endOffset = end
    }
  }
  return payload
}

/** 服务端锚点 → 宿主纯定位模型；引用原文缺失时返回 null（该评论只按整页评论展示） */
export function wikiAnchorFromAuthorComment(anchor?: WikiAuthorCommentAnchor | null): WikiQuoteAnchor | null {
  if (!anchor) {
    return null
  }
  const exact = normalizeWikiVisibleText(anchor.exact)
  if (!exact) {
    return null
  }
  return {
    exact,
    prefix: normalizeWikiVisibleText(anchor.prefix),
    suffix: normalizeWikiVisibleText(anchor.suffix),
    blockOrdinal: anchor.blockOrdinal,
    blockType: wikiCommentBlockTypeFromServer(anchor.blockType),
    tableRow: anchor.tableRow,
    tableCell: anchor.tableCell,
    startOffset: anchor.startOffset,
    endOffset: anchor.endOffset,
  }
}

/** 评论锚点版本身份与锚点平级，revision 是字符串以保持 API 大数字精度。 */
export function wikiAnchorScopeFromAuthorComment(comment?: WikiAuthorComment | null): WikiQuoteAnchorScope | null {
  if (!comment?.anchor || !comment.versionId || !comment.revision || !comment.contentHash) {
    return null
  }
  return { versionId: comment.versionId, revision: comment.revision, contentHash: comment.contentHash }
}

/** 服务端锚点状态小写字段；未知状态保守返回 undefined。 */
export function wikiAnchorStateFromAuthorComment(comment?: WikiAuthorComment | null): 'active' | 'reanchorable' | 'orphaned' | undefined {
  const state = comment?.anchorState
  return state === 'active' || state === 'reanchorable' || state === 'orphaned' ? state : undefined
}

/**
 * 原始文本坐标 → 归一化文本坐标（与 normalizeWikiVisibleText 同一套空白折叠规则）。
 *
 * 预览 DOM 的选区偏移按 textContent 原始坐标给出，而锚点模型的 exact/prefix/suffix
 * 与偏移都是归一化坐标；折叠空白会改变长度，这里按「空白串折叠成单空格、首尾空白丢弃」逐步累加：
 * 只有完整落在边界之前的空白串才计入那个折叠空格（边界之后还有非空白字符），
 * 落在空白串内部的边界对应到折叠空格之前的位置，交由 buildWikiQuoteAnchor 的边缘裁剪处理。
 */
export function wikiNormalizedOffsetAt(raw: string, rawOffset: number): number {
  if (!raw) {
    return 0
  }
  const fixed = Number.isFinite(rawOffset) ? Math.trunc(rawOffset) : 0
  const clamped = Math.min(Math.max(fixed, 0), raw.length)
  let normalized = 0
  let pendingSpace = false
  for (let index = 0; index < clamped; index++) {
    if (isWikiCommentSpace(raw[index])) {
      if (normalized > 0) {
        pendingSpace = true
      }
      continue
    }
    if (pendingSpace) {
      normalized++
      pendingSpace = false
    }
    normalized++
  }
  if (pendingSpace && clamped < raw.length && !isWikiCommentSpace(raw[clamped])) {
    normalized++
  }
  return normalized
}

function authorCommentBase(spaceSlug: string, nodeId: string): string {
  return `api/public/wiki/${encodeURIComponent(spaceSlug)}/nodes/${encodeURIComponent(nodeId)}/comments`
}

/**
 * 共享 axios 客户端延迟导入：本模块的纯映射函数要被 node:test 直接导入，
 * 而共享 client 在模块顶层读取 `import.meta.env`、并在拦截器里访问 localStorage，
 * 不适合在测试进程里求值。client 已在主包内，动态导入不会产生额外请求。
 */
async function authorCommentClient() {
  return (await import('@/api/modules/system-client')).default
}

/** 已发布评论分页（只读）：服务端只返回 PUBLISHED 根评论与各自已发布回复，并按当前发布版本计算锚点状态 */
export async function fetchWikiAuthorComments(spaceSlug: string, nodeId: string, params?: { page?: number, size?: number }) {
  const client = await authorCommentClient()
  return client.get<unknown, ApiResponse<PageResult<WikiAuthorComment>>>(authorCommentBase(spaceSlug, nodeId), { params })
}

/**
 * 发表评论（要求登录，服务端落 PENDING，不会立即出现在只读列表里）。
 * 只提交正文、父评论与选区锚点草稿；版本与作者身份由服务端推导。
 */
export async function createWikiAuthorComment(spaceSlug: string, nodeId: string, data: WikiAuthorCommentCreatePayload) {
  const client = await authorCommentClient()
  return client.post<unknown, ApiResponse<WikiAuthorComment>>(authorCommentBase(spaceSlug, nodeId), data)
}
