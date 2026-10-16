/**
 * 公开 Wiki 正文评论文本锚点（纯函数，无 DOM 依赖）。
 *
 * 评论锚定正文中的一段引用文本：捕获时记录引用原文 exact、前后文 prefix/suffix、
 * 所在块序号/类型与可选表格行列提示、起止偏移提示；正文换版后用同一套纯函数重新定位，
 * 判定 active / reanchorable / orphaned / ambiguous。
 *
 * - 类型与服务端计划的嵌套 `anchor` 对象同构，但**不含版本身份**：versionId / revision /
 *   contentHash 由 API 与 anchor 平级单独下发，本模型只在判别时作为可选输入使用；
 * - 可见文本一律先做 whitespace 折叠归一化（含 NBSP、全角空格、换行），锚点中的
 *   exact/prefix/suffix 与块文本都按归一化文本存取；
 * - 偏移是归一化文本里的 UTF-16 索引（与 String.prototype.slice 一致），
 *   长度上限按码点计，避免截断代理对；
 * - 不做渲染标记、高亮或 DOM 注入；偏移提示只用于消歧，不会制造命中。
 */

/** 锚点长度边界：短于 minExact 的引用无法稳定重定位；原文与上下文按码点截断。 */
export const WIKI_QUOTE_ANCHOR_LIMITS = {
  /** 引用原文最小码点长度 */
  minExact: 2,
  /** 引用原文最大码点长度 */
  maxExact: 256,
  /** prefix / suffix 各自最大码点长度 */
  maxContext: 48,
} as const

/** 块类型：常见 Markdown 块；服务端后续新增类型按 string 透传。 */
export type WikiAnchorBlockType
  = | 'paragraph'
    | 'heading'
    | 'listItem'
    | 'tableCell'
    | 'codeBlock'
    | 'blockquote'
    | (string & {})

/**
 * 归一化可见文本块（正文顺序）。
 *
 * ordinal 缺省时按数组下标当作块序号；表格单元格是独立块，用行列提示消歧。
 */
export interface WikiAnchorBlock {
  /** 块在正文中的序号；缺省取数组下标 */
  ordinal?: number
  /** 块类型 */
  type?: WikiAnchorBlockType
  /** 块可见文本；内部会再次做 whitespace 归一化（幂等） */
  text: string
  /** 表格行序（仅表格单元格块有意义） */
  tableRow?: number
  /** 表格列序（仅表格单元格块有意义） */
  tableCell?: number
}

/** 捕获输入：光标所在块的选区（偏移按归一化文本坐标） */
export interface WikiQuoteAnchorCaptureInput {
  /** 选区所在块的可见文本；未归一化也可，内部会折叠空白 */
  blockText: string
  /** 选区起始偏移（归一化文本坐标，含） */
  start: number
  /** 选区结束偏移（归一化文本坐标，不含） */
  end: number
  /** 捕获时所在块序号 */
  blockOrdinal?: number
  /** 捕获时所在块类型 */
  blockType?: WikiAnchorBlockType
  /** 表格行序提示 */
  tableRow?: number
  /** 表格列序提示 */
  tableCell?: number
}

/** 服务端评论载荷里嵌套的 anchor 对象 */
export interface WikiQuoteAnchor {
  /** 引用原文（归一化可见文本） */
  exact: string
  /** 引用前文，最多 maxContext 码点，不含与引用交界的空白；无前文为空串 */
  prefix?: string
  /** 引用后文，最多 maxContext 码点，不含与引用交界的空白；无后文为空串 */
  suffix?: string
  /** 捕获时所在块序号 */
  blockOrdinal?: number
  /** 捕获时所在块类型 */
  blockType?: WikiAnchorBlockType
  /** 表格行序提示（可选） */
  tableRow?: number
  /** 表格列序提示（可选） */
  tableCell?: number
  /** 捕获时起始偏移（提示，不参与唯一性判定） */
  startOffset?: number
  /** 捕获时结束偏移（提示，不含） */
  endOffset?: number
}

/** 与 anchor 平级、由 API 单独下发的版本身份（不嵌进 anchor） */
export interface WikiQuoteAnchorScope {
  versionId?: string
  revision?: string
  contentHash?: string
}

/**
 * 命中依据：context 由前后文消歧，block-hint 由块/表格提示消歧，
 * position 由起止偏移提示消歧，exact 表示引用原文唯一出现。
 */
export type WikiQuoteAnchorMatchedBy = 'context' | 'block-hint' | 'position' | 'exact'

/** 归一化块文本里的命中区间（start 含，end 不含） */
export interface WikiQuoteAnchorMatch {
  blockOrdinal: number
  blockType?: WikiAnchorBlockType
  tableRow?: number
  tableCell?: number
  start: number
  end: number
  matchedBy: WikiQuoteAnchorMatchedBy
}

/** 锚点四态：原位有效 / 可重锚 / 已失效 / 歧义 */
export type WikiQuoteAnchorStatus = 'active' | 'reanchorable' | 'orphaned' | 'ambiguous'

/** 判别结果 */
export interface WikiQuoteAnchorClassification {
  status: WikiQuoteAnchorStatus
  /** 收窄后的候选；ambiguous 时为全部并列候选，供人工选择 */
  candidates: WikiQuoteAnchorMatch[]
  /** 唯一命中；orphaned / ambiguous 时缺省 */
  match?: WikiQuoteAnchorMatch
  /** 是否仍落在捕获时的块、上下文上 */
  inPlace: boolean
  /** 版本身份是否判定为已变化 */
  scopeChanged: boolean
}

/** 判别选项：版本身份由 API 单独提供，缺任一侧都无法比较、不臆断为变化 */
export interface WikiQuoteAnchorClassifyOptions {
  /** 捕获评论时的版本身份 */
  capturedScope?: WikiQuoteAnchorScope
  /** 当前正文的版本身份 */
  currentScope?: WikiQuoteAnchorScope
}

/** 归一化时折叠的空白：Unicode WhiteSpace（含 NBSP、全角空格）与换行 */
const WHITESPACE_RUN = /\s+/g

/** 归一化可见文本：折叠空白串为单空格并去首尾；幂等。 */
export function normalizeWikiVisibleText(value?: string | null): string {
  return (value ?? '').replace(WHITESPACE_RUN, ' ').trim()
}

/** 归一化文本坐标钳制：非有限数归零，越界收进 [0, max] */
function clampOffset(value: number | undefined, max: number): number {
  if (value == null || !Number.isFinite(value)) {
    return 0
  }
  return Math.min(Math.max(Math.trunc(value), 0), max)
}

/** 取前 max 个码点（不截断代理对） */
function clipHead(value: string, max: number): string {
  const points = Array.from(value)
  return points.length <= max ? value : points.slice(0, max).join('')
}

/** 取后 max 个码点（不截断代理对） */
function clipTail(value: string, max: number): string {
  const points = Array.from(value)
  return points.length <= max ? value : points.slice(-max).join('')
}

/**
 * 捕获选区生成锚点；选区内不足 minExact 码点（空选区、纯空白、单字）时返回 null。
 * 选区边缘空白不进入 exact，保证引用原文两端无非语义空格。
 */
export function buildWikiQuoteAnchor(input: WikiQuoteAnchorCaptureInput | null | undefined): WikiQuoteAnchor | null {
  if (!input) {
    return null
  }
  const text = normalizeWikiVisibleText(input.blockText)
  if (!text) {
    return null
  }
  const rawStart = clampOffset(input.start, text.length)
  const rawEnd = clampOffset(input.end, text.length)
  let start = Math.min(rawStart, rawEnd)
  let end = Math.max(rawStart, rawEnd)
  while (start < end && text.charCodeAt(start) === 0x20) {
    start++
  }
  while (end > start && text.charCodeAt(end - 1) === 0x20) {
    end--
  }
  const selected = text.slice(start, end)
  if (Array.from(selected).length < WIKI_QUOTE_ANCHOR_LIMITS.minExact) {
    return null
  }
  return {
    exact: clipHead(selected, WIKI_QUOTE_ANCHOR_LIMITS.maxExact),
    prefix: clipTail(text.slice(0, start).trimEnd(), WIKI_QUOTE_ANCHOR_LIMITS.maxContext),
    suffix: clipHead(text.slice(end).trimStart(), WIKI_QUOTE_ANCHOR_LIMITS.maxContext),
    blockOrdinal: input.blockOrdinal,
    blockType: input.blockType,
    tableRow: input.tableRow,
    tableCell: input.tableCell,
    startOffset: start,
    endOffset: end,
  }
}

/** 锚点判定用的归一化探针 */
interface AnchorProbe {
  exact: string
  prefix: string
  suffix: string
  blockOrdinal?: number
  blockType?: WikiAnchorBlockType
  tableRow?: number
  tableCell?: number
  startOffset?: number
  /** 锚点是否带前后文线索 */
  hasContext: boolean
  /** 锚点是否带块/表格线索 */
  hasHint: boolean
}

function createProbe(anchor: WikiQuoteAnchor | null | undefined): AnchorProbe | null {
  if (!anchor) {
    return null
  }
  const exact = normalizeWikiVisibleText(anchor.exact)
  if (!exact) {
    return null
  }
  const prefix = normalizeWikiVisibleText(anchor.prefix)
  const suffix = normalizeWikiVisibleText(anchor.suffix)
  return {
    exact,
    prefix,
    suffix,
    blockOrdinal: anchor.blockOrdinal,
    blockType: anchor.blockType,
    tableRow: anchor.tableRow,
    tableCell: anchor.tableCell,
    startOffset: anchor.startOffset,
    hasContext: prefix !== '' || suffix !== '',
    hasHint: anchor.blockOrdinal != null || anchor.blockType != null || anchor.tableRow != null || anchor.tableCell != null,
  }
}

/** 候选出现：命中区间 + 该候选满足哪类线索 */
interface AnchorCandidate {
  match: WikiQuoteAnchorMatch
  contextOk: boolean
  hintOk: boolean
}

function toCandidate(probe: AnchorProbe, block: WikiAnchorBlock, ordinal: number, start: number, text: string): AnchorCandidate {
  const end = start + probe.exact.length
  // 上下文比较不含与引用交界的空白，两侧都按归一化结果比对
  const contextOk = (!probe.prefix || text.slice(0, start).trimEnd().endsWith(probe.prefix))
    && (!probe.suffix || text.slice(end).trimStart().startsWith(probe.suffix))
  const hintOk = (probe.blockOrdinal == null || probe.blockOrdinal === ordinal)
    && (probe.blockType == null || probe.blockType === block.type)
    && (probe.tableRow == null || probe.tableRow === block.tableRow)
    && (probe.tableCell == null || probe.tableCell === block.tableCell)
  return {
    match: {
      blockOrdinal: ordinal,
      blockType: block.type,
      tableRow: block.tableRow,
      tableCell: block.tableCell,
      start,
      end,
      matchedBy: 'exact',
    },
    contextOk,
    hintOk,
  }
}

/** 在归一化块数据里收集 exact 的全部出现，按块序号与起点排序 */
function collectCandidates(probe: AnchorProbe, blocks: readonly WikiAnchorBlock[]): AnchorCandidate[] {
  const candidates: AnchorCandidate[] = []
  blocks.forEach((block, index) => {
    const text = normalizeWikiVisibleText(block?.text)
    if (!text) {
      return
    }
    const ordinal = block.ordinal ?? index
    for (let at = text.indexOf(probe.exact); at >= 0; at = text.indexOf(probe.exact, at + 1)) {
      candidates.push(toCandidate(probe, block, ordinal, at, text))
    }
  })
  return candidates.sort((a, b) => a.match.blockOrdinal - b.match.blockOrdinal || a.match.start - b.match.start)
}

/** 只收紧、不替换：过滤为空或没有减少候选时保持原池；真正消歧才标记命中依据 */
function applyClue(pool: AnchorCandidate[], predicate: (candidate: AnchorCandidate) => boolean, matchedBy: WikiQuoteAnchorMatchedBy): AnchorCandidate[] {
  const filtered = pool.filter(predicate)
  if (filtered.length === 0 || filtered.length >= pool.length) {
    return pool
  }
  for (const candidate of filtered) {
    candidate.match.matchedBy = matchedBy
  }
  return filtered
}

/** 按「上下文 → 块/表格提示 → 偏移提示」逐级消歧，顺序即证据强度 */
function narrowCandidates(probe: AnchorProbe, blocks: readonly WikiAnchorBlock[]): AnchorCandidate[] {
  let pool = collectCandidates(probe, blocks)
  if (pool.length <= 1) {
    return pool
  }
  if (probe.hasContext) {
    pool = applyClue(pool, candidate => candidate.contextOk, 'context')
  }
  if (pool.length > 1 && probe.hasHint) {
    pool = applyClue(pool, candidate => candidate.hintOk, 'block-hint')
  }
  if (pool.length > 1 && probe.startOffset != null) {
    pool = applyClue(pool, candidate => candidate.match.start === probe.startOffset, 'position')
  }
  return pool
}

/**
 * 列出 exact 在归一化块数据里的全部出现（未消歧），用于诊断与展示；
 * 引用缺失或挂在不存在的块上时返回空数组。
 */
export function findWikiQuoteAnchorRanges(anchor: WikiQuoteAnchor | null | undefined, blocks: readonly WikiAnchorBlock[] = []): WikiQuoteAnchorMatch[] {
  const probe = createProbe(anchor)
  return probe ? collectCandidates(probe, blocks).map(candidate => candidate.match) : []
}

/** 消歧后唯一命中才返回；缺失或歧义返回 null（歧义候选看 classify 的 candidates）。 */
export function resolveWikiQuoteAnchorRange(anchor: WikiQuoteAnchor | null | undefined, blocks: readonly WikiAnchorBlock[] = []): WikiQuoteAnchorMatch | null {
  const probe = createProbe(anchor)
  if (!probe) {
    return null
  }
  const candidates = narrowCandidates(probe, blocks)
  return candidates.length === 1 ? candidates[0].match : null
}

/**
 * 四态判别：
 * - active：唯一命中且仍在捕获时的块、上下文上，版本身份未变；
 * - reanchorable：唯一命中但位置或版本已变（块移动、内容换版）；
 * - orphaned：引用原文已不存在，偏移提示不制造命中；
 * - ambiguous：多个候选平分线索，交人工选择，不猜测。
 */
export function classifyWikiQuoteAnchor(anchor: WikiQuoteAnchor | null | undefined, blocks: readonly WikiAnchorBlock[] = [], options: WikiQuoteAnchorClassifyOptions = {}): WikiQuoteAnchorClassification {
  const scopeChanged = isWikiQuoteAnchorScopeChanged(options.capturedScope, options.currentScope)
  const probe = createProbe(anchor)
  if (probe) {
    const entries = narrowCandidates(probe, blocks)
    if (entries.length > 1) {
      return { status: 'ambiguous', candidates: entries.map(entry => entry.match), inPlace: false, scopeChanged }
    }
    if (entries.length === 1) {
      const entry = entries[0]
      const inPlace = (!probe.hasContext || entry.contextOk) && (!probe.hasHint || entry.hintOk)
      return {
        status: inPlace && !scopeChanged ? 'active' : 'reanchorable',
        candidates: [entry.match],
        match: entry.match,
        inPlace,
        scopeChanged,
      }
    }
  }
  return { status: 'orphaned', candidates: [], inPlace: false, scopeChanged }
}

/**
 * 版本身份变化判定：contentHash 优先（正文内容一致即视为同一段文本，即使 versionId/revision
 * 前进），其次 revision，最后 versionId；某一层两侧都缺时下探一层，三层都无法比较返回 false。
 */
export function isWikiQuoteAnchorScopeChanged(captured?: WikiQuoteAnchorScope | null, current?: WikiQuoteAnchorScope | null): boolean {
  if (!captured || !current) {
    return false
  }
  if (captured.contentHash != null && current.contentHash != null) {
    return captured.contentHash !== current.contentHash
  }
  if (captured.revision != null && current.revision != null) {
    return captured.revision !== current.revision
  }
  if (captured.versionId != null && current.versionId != null) {
    return captured.versionId !== current.versionId
  }
  return false
}
