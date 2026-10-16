import type { WikiCommentAnchorPayload, WikiPublicComment } from './wiki-public-api'
import type { WikiQuoteAnchor, WikiQuoteAnchorClassification, WikiQuoteAnchorScope } from './wiki-quote-anchor'

/**
 * 服务端评论文本锚点与本地纯算法模型之间的适配（无 DOM 依赖）。
 *
 * 线协议与后端 WikiCommentAnchorRequest / WikiCommentRes 逐字段一致：
 * - anchor 是**扁平**结构（exact/prefix/suffix/blockOrdinal/blockType/tableRow/tableCell/
 *   startOffset/endOffset），请求只提交引用文本与定位提示，响应原样带回同一组字段；
 * - 版本身份（versionId/revision/contentHash）与公开读取状态（anchorState，小写
 *   active/reanchorable/orphaned）是 anchor 的**兄弟字段**，与评论一起下发，不在 anchor 内；
 * - 无锚点评论（含历史评论）服务端不下发 anchor 与兄弟字段，也没有 PAGE 之类锚点种类：
 *   这里一律以「无锚点」返回 null，由调用方决定不展示引用态。
 * 本地算法（wiki-quote-anchor）同样是扁平模型，这里做双向折算与状态投影。
 */

/** UI 与高亮共用的锚点状态；无锚点评论没有状态（null），不造「整页评论」态。 */
export type WikiCommentAnchorState = 'ACTIVE' | 'REANCHORABLE' | 'ORPHANED' | 'AMBIGUOUS'

/** 服务端公开读取下发的小写状态（只读投影，客户端不自行推导这三个值）。 */
export type WikiCommentAnchorServerState = 'active' | 'reanchorable' | 'orphaned'

export const WIKI_COMMENT_ANCHOR_STATE_LABELS: Record<WikiCommentAnchorState, string> = {
  ACTIVE: '已定位原文',
  REANCHORABLE: '原文已改版，仍可定位',
  ORPHANED: '原文已变化，无法定位',
  AMBIGUOUS: '原文多处匹配，无法定位',
}

/** 状态对应的展示语气：可高亮为成功，可重锚为提醒，其余为中性。 */
export const WIKI_COMMENT_ANCHOR_STATE_TONES: Record<WikiCommentAnchorState, 'success' | 'warning' | 'neutral'> = {
  ACTIVE: 'success',
  REANCHORABLE: 'warning',
  ORPHANED: 'neutral',
  AMBIGUOUS: 'neutral',
}

const SERVER_STATE_PROJECTION: Record<WikiCommentAnchorServerState, WikiCommentAnchorState> = {
  active: 'ACTIVE',
  reanchorable: 'REANCHORABLE',
  orphaned: 'ORPHANED',
}

/** 只有唯一命中（原位或改版后仍唯一）才允许在正文里高亮。 */
export function wikiCommentAnchorHighlightable(state: WikiCommentAnchorState): boolean {
  return state === 'ACTIVE' || state === 'REANCHORABLE'
}

/** 状态的中文说明；文案集中在这里，避免多个入口各写一份。 */
export function wikiCommentAnchorStateLabel(state: WikiCommentAnchorState): string {
  return WIKI_COMMENT_ANCHOR_STATE_LABELS[state]
}

/** 服务端小写状态归一；缺失或未知返回 null（不臆断为可高亮）。 */
function serverAnchorState(state: string | null | undefined): WikiCommentAnchorServerState | null {
  const value = (state || '').trim().toLowerCase()
  return value === 'active' || value === 'reanchorable' || value === 'orphaned' ? value : null
}

/** 响应的扁平锚点 → 本地纯算法模型；无引用文本（无锚点/历史评论）返回 null。 */
export function wikiQuoteAnchorFromResponse(anchor: WikiCommentAnchorPayload | null | undefined): WikiQuoteAnchor | null {
  const exact = (anchor?.exact || '').trim()
  if (!exact) {
    return null
  }
  return {
    exact,
    prefix: anchor?.prefix,
    suffix: anchor?.suffix,
    blockOrdinal: anchor?.blockOrdinal,
    blockType: anchor?.blockType,
    tableRow: anchor?.tableRow,
    tableCell: anchor?.tableCell,
    startOffset: anchor?.startOffset,
    endOffset: anchor?.endOffset,
  }
}

/**
 * 评论锚点的版本身份（与 anchor 平级的服务端盖章字段）。
 * revision 服务端按字符串下发；缺任一侧时纯函数不臆断为改版。
 */
export function wikiQuoteAnchorScopeFromResponse(comment: WikiPublicComment | null | undefined): WikiQuoteAnchorScope {
  return {
    versionId: comment?.versionId,
    revision: comment?.revision,
    contentHash: comment?.contentHash,
  }
}

/**
 * 本地锚点 → 请求载荷（扁平字段，不含任何版本身份）。
 *
 * 块提示只在结构完整时提交：服务端要求 blockOrdinal/blockType 成对、表格行列只随 tableCell
 * 且必须成对，结构不完整的提示会拒绝整条评论——这里宁可丢弃提示也不提交半截字段。
 * 起止偏移要求在服务端校验（start <= end），反序提示直接丢弃。
 */
export function wikiQuoteAnchorPayload(anchor: WikiQuoteAnchor | null | undefined): WikiCommentAnchorPayload | undefined {
  const exact = (anchor?.exact || '').trim()
  if (!anchor || !exact) {
    return undefined
  }
  const payload: WikiCommentAnchorPayload = { exact }
  if (anchor.prefix) {
    payload.prefix = anchor.prefix
  }
  if (anchor.suffix) {
    payload.suffix = anchor.suffix
  }

  const blockType = anchor.blockType ? String(anchor.blockType) : ''
  const { blockOrdinal, tableRow, tableCell } = anchor
  if (blockType && blockOrdinal != null && (blockType !== 'tableCell' || (tableRow != null && tableCell != null))) {
    payload.blockType = blockType
    payload.blockOrdinal = blockOrdinal
    if (blockType === 'tableCell' && tableRow != null && tableCell != null) {
      payload.tableRow = tableRow
      payload.tableCell = tableCell
    }
  }

  const { startOffset, endOffset } = anchor
  if (startOffset != null && startOffset >= 0 && endOffset != null && endOffset >= startOffset) {
    payload.startOffset = startOffset
    payload.endOffset = endOffset
  }
  return payload
}

/**
 * 状态投影：无锚点返回 null（不造 PAGE 态）；本地判别与服务端状态都不能让失锚的评论被高亮。
 *
 * - 服务端判 orphaned（按当前已发布正文无法唯一命中）→ ORPHANED，本地命中也只是渲染差异；
 * - 本地多处命中 → AMBIGUOUS，交人工，不猜；
 * - 本地唯一命中：服务端说改版则 REANCHORABLE，否则 ACTIVE；
 * - 本地拿不到正文（正文未渲染/未采集）时保守取服务端状态，服务端也没给 → ORPHANED。
 */
export function resolveWikiCommentAnchorState(
  comment: WikiPublicComment | null | undefined,
  classification?: WikiQuoteAnchorClassification | null,
): WikiCommentAnchorState | null {
  if (!wikiQuoteAnchorFromResponse(comment?.anchor)) {
    return null
  }
  const serverState = serverAnchorState(comment?.anchorState)
  if (serverState === 'orphaned') {
    return 'ORPHANED'
  }
  if (classification) {
    if (classification.status === 'ambiguous') {
      return 'AMBIGUOUS'
    }
    if (classification.status === 'orphaned') {
      return 'ORPHANED'
    }
    if (classification.status === 'active') {
      return serverState === 'reanchorable' ? 'REANCHORABLE' : 'ACTIVE'
    }
    // reanchorable：唯一命中但块/版本已变
    return 'REANCHORABLE'
  }
  return serverState ? SERVER_STATE_PROJECTION[serverState] : 'ORPHANED'
}
