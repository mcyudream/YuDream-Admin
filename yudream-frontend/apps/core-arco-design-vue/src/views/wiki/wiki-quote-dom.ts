import type { WikiAnchorBlock, WikiAnchorBlockType, WikiQuoteAnchor, WikiQuoteAnchorClassification, WikiQuoteAnchorMatch, WikiQuoteAnchorScope } from './wiki-quote-anchor'
import { buildWikiQuoteAnchor, classifyWikiQuoteAnchor } from './wiki-quote-anchor'

/**
 * 公开 Wiki 正文评论文本锚点的 DOM 层：选区捕获 + 阅读层高亮。
 *
 * 只服务当前渲染的只读 `.md-editor-preview`：把浏览器 Selection 折算成 wiki-quote-anchor
 * 的纯数据锚点，并在渲染后给「唯一命中」的引用加装饰 span。任何持久化 Markdown/HTML 与
 * sanitizer 都不在这里改写；高亮只是阅读层装饰，可整体清除后按当前 DOM 重建。
 *
 * 归一化与纯算法模块保持同一套折叠规则（\s+ → 单空格并去首尾），本模块额外记录
 * 原始文本偏移与归一化偏移的双向映射，用于把 DOM Range 折算成锚点、再把锚点还原成 Range。
 */

/** 正文只读预览容器（md-editor-v3 的 MdPreview 预览层） */
const PREVIEW_ROOT_SELECTOR = '.md-editor-preview'
/** 可锚定的叶块：段落/标题/列表项/代码块/引用块/表格单元格 */
const BLOCK_SELECTOR = 'p, li, h1, h2, h3, h4, h5, h6, pre, blockquote, td, th, dt, dd'
/** 阅读层高亮标记的属性名与类名；点击处理由调用方注入，不在模板里模拟控件 */
export const WIKI_QUOTE_MARK_ATTRIBUTE = 'data-wiki-quote-comment'
/** 上一次高亮目标签名：正文未变且标记仍在时跳过重写，避免与外部 MutationObserver 互相回写 */
export const WIKI_QUOTE_MARK_SIGNATURE_ATTRIBUTE = 'data-wiki-quote-marks'
export const WIKI_QUOTE_MARK_CLASS = 'wiki-quote-hit'
const QUOTE_MARK_SELECTOR = `[${WIKI_QUOTE_MARK_ATTRIBUTE}]`

/** 原始文本 ↔ 归一化文本的偏移映射（UTF-16 下标，与 String.prototype.slice 一致）。 */
export interface WikiTextOffsetMap {
  /** 折叠归一化后的正文（与 normalizeWikiVisibleText 结果一致） */
  text: string
  /** 归一化下标 → 产生该字符的原始下标（长度等于 text.length） */
  rawIndexOf: number[]
  /** 原始下标（含）→ 归一化起点，长度 raw.length + 1 */
  normalizedStartOf: number[]
  /** 原始下标（不含）→ 归一化终点，长度 raw.length + 1 */
  normalizedEndOf: number[]
}

/** 归一化块：DOM 元素 + 纯算法用的文本模型 */
export interface WikiPreviewBlock {
  element: HTMLElement
  ordinal: number
  type: WikiAnchorBlockType
  tableRow?: number
  tableCell?: number
  /** 块的原始可见文本（未归一化） */
  rawText: string
  offsetMap: WikiTextOffsetMap
  textNodes: Text[]
}

/** 选区快照：冻结的锚点 + 定位信息，按钮点击不再依赖浏览器仍有选区 */
export interface WikiQuoteSelectionSnapshot {
  anchor: WikiQuoteAnchor
  blockOrdinal: number
  blockType: WikiAnchorBlockType
  tableRow?: number
  tableCell?: number
  rect: WikiQuoteSelectionRect | null
  range: Range
}

export interface WikiQuoteSelectionRect {
  top: number
  left: number
  width: number
  height: number
}

/** 拒绝原因：跨块与过短会给出明确提示，其余是普通点击/折叠选区，静默处理 */
export type WikiQuoteSelectionError = 'no-selection' | 'outside-preview' | 'collapsed' | 'cross-block' | 'too-short'

export type WikiQuoteSelectionResult
  = | { snapshot: WikiQuoteSelectionSnapshot, error?: undefined }
    | { snapshot?: undefined, error: WikiQuoteSelectionError }

export interface WikiQuoteHighlightTarget {
  commentId: string
  anchor: WikiQuoteAnchor | null | undefined
}

export interface WikiQuoteHighlightOptions {
  /** 点击高亮（阅读层装饰）时回调，由页面决定打开哪条评论线程 */
  onClick?: (commentId: string, event: MouseEvent) => void
  /** 每条目标两侧的版本身份（锚点盖章侧 / 当前正文侧）：用于判定是否仅改版后仍可定位 */
  scopeOf?: (commentId: string) => { captured?: WikiQuoteAnchorScope, current?: WikiQuoteAnchorScope } | undefined
}

export interface WikiQuoteHighlightOutcome {
  /** 成功高亮的评论 ID */
  applied: string[]
  /** 每条锚点的本地判别结果（含歧义候选），供 UI 投影五态 */
  classifications: Record<string, WikiQuoteAnchorClassification>
  matches: Record<string, WikiQuoteAnchorMatch>
}

/** 折叠空白为单空格并去首尾，同时记录原始/归一化偏移映射；与 normalizeWikiVisibleText 同规则。 */
export function buildWikiTextOffsetMap(raw: string): WikiTextOffsetMap {
  const rawIndexOf: number[] = []
  const normalizedStartOf: number[] = Array.from<number>({ length: raw.length + 1 }).fill(0)
  const normalizedEndOf: number[] = Array.from<number>({ length: raw.length + 1 }).fill(0)
  let text = ''
  let pendingSpaceAt = -1
  for (let index = 0; index < raw.length; index++) {
    normalizedStartOf[index] = text.length + (pendingSpaceAt >= 0 ? 1 : 0)
    normalizedEndOf[index] = text.length
    const char = raw[index]
    if (/\s/.test(char)) {
      // 首部空白直接丢弃（等价 trim），其余折叠进一个待写空格
      if (text.length > 0 && pendingSpaceAt < 0) {
        pendingSpaceAt = index
      }
      continue
    }
    if (pendingSpaceAt >= 0) {
      text += ' '
      rawIndexOf.push(pendingSpaceAt)
      pendingSpaceAt = -1
    }
    text += char
    rawIndexOf.push(index)
  }
  normalizedStartOf[raw.length] = text.length + (pendingSpaceAt >= 0 ? 1 : 0)
  normalizedEndOf[raw.length] = text.length
  return { text, rawIndexOf, normalizedStartOf, normalizedEndOf }
}

function clampIndex(value: number, max: number): number {
  if (!Number.isFinite(value)) {
    return 0
  }
  return Math.min(Math.max(Math.trunc(value), 0), max)
}

/** 原始区间 → 归一化区间（越界钳制，反序按区间取）；用于把 DOM 选区折算成锚点偏移。 */
export function normalizedRangeOfRaw(map: WikiTextOffsetMap, start: number, end: number): { start: number, end: number } {
  const max = map.normalizedStartOf.length - 1
  const from = clampIndex(Math.min(start, end), max)
  const to = clampIndex(Math.max(start, end), max)
  return { start: map.normalizedStartOf[from], end: map.normalizedEndOf[to] }
}

/** 归一化区间 → 原始区间；命中区间必须落在已存在字符上，否则返回 null（不造命中）。 */
export function rawRangeOfNormalized(map: WikiTextOffsetMap, start: number, end: number): { start: number, end: number } | null {
  const from = clampIndex(start, map.text.length)
  const to = clampIndex(end, map.text.length)
  if (to <= from) {
    return null
  }
  const first = map.rawIndexOf[from]
  const last = map.rawIndexOf[to - 1]
  return first == null || last == null ? null : { start: first, end: last + 1 }
}

/** 解析正文预览根：容器自身就是预览时直接使用；没有预览（编辑器/其它视图）返回 null。 */
export function resolveWikiPreviewRoot(container: HTMLElement | null | undefined): HTMLElement | null {
  if (!container) {
    return null
  }
  if (container.matches(PREVIEW_ROOT_SELECTOR)) {
    return container
  }
  return container.querySelector<HTMLElement>(PREVIEW_ROOT_SELECTOR)
}

function collectTextNodes(element: HTMLElement): Text[] {
  const walker = element.ownerDocument.createTreeWalker(element, NodeFilter.SHOW_TEXT)
  const nodes: Text[] = []
  for (let node = walker.nextNode(); node; node = walker.nextNode()) {
    nodes.push(node as Text)
  }
  return nodes
}

/** 块类型：表格单元格（含单元格内的段落）统一按 tableCell + 行列提示，其余按标签归类。 */
function blockTypeOf(element: HTMLElement, inTableCell: boolean): WikiAnchorBlockType {
  if (inTableCell) {
    return 'tableCell'
  }
  const tag = element.tagName.toLowerCase()
  if (tag === 'p') {
    return 'paragraph'
  }
  if (/^h[1-6]$/.test(tag)) {
    return 'heading'
  }
  if (tag === 'li' || tag === 'dt' || tag === 'dd') {
    return 'listItem'
  }
  if (tag === 'pre') {
    return 'codeBlock'
  }
  return 'blockquote'
}

/** 元素所在表格单元格的行列（0 起）：非表格内容返回 null。 */
function tablePositionOf(element: HTMLElement): { tableRow: number, tableCell: number } | null {
  const cell = (element.tagName === 'TD' || element.tagName === 'TH'
    ? element
    : element.closest<HTMLTableCellElement>('td, th')) as HTMLTableCellElement | null
  if (!cell) {
    return null
  }
  const row = cell.closest<HTMLTableRowElement>('tr')
  const tableRow = row && Number.isFinite(row.rowIndex)
    ? row.rowIndex
    : row?.parentElement
      ? [...row.parentElement.children].indexOf(row)
      : 0
  return { tableRow, tableCell: Number.isFinite(cell.cellIndex) ? cell.cellIndex : 0 }
}

/**
 * 采集正文块（文档顺序）：只保留叶块，避免父块把子块文本并入导致同一段文字出现两次。
 * ordinal 就是数组下标，锚点块提示与本地重定位都按它对齐。
 */
export function collectWikiPreviewBlocks(root: HTMLElement | null | undefined): WikiPreviewBlock[] {
  if (!root) {
    return []
  }
  const elements = [...root.querySelectorAll<HTMLElement>(BLOCK_SELECTOR)]
    .filter(element => !element.querySelector(BLOCK_SELECTOR))
  return elements.map((element, ordinal) => {
    const table = tablePositionOf(element)
    const rawText = element.textContent ?? ''
    return {
      element,
      ordinal,
      type: blockTypeOf(element, Boolean(table)),
      tableRow: table?.tableRow,
      tableCell: table?.tableCell,
      rawText,
      offsetMap: buildWikiTextOffsetMap(rawText),
      textNodes: collectTextNodes(element),
    }
  })
}

/** DOM 块 → 纯算法块模型（文本按同一套折叠规则归一化）。 */
export function wikiQuoteAnchorBlocks(blocks: readonly WikiPreviewBlock[]): WikiAnchorBlock[] {
  return blocks.map(block => ({
    ordinal: block.ordinal,
    type: block.type,
    tableRow: block.tableRow,
    tableCell: block.tableCell,
    text: block.rawText,
  }))
}

function closestPreviewBlock(node: Node, index: Map<Element, WikiPreviewBlock>): WikiPreviewBlock | null {
  let element = node.nodeType === Node.TEXT_NODE ? node.parentElement : node as Element | null
  while (element) {
    const block = index.get(element)
    if (block) {
      return block
    }
    element = element.parentElement
  }
  return null
}

/** 选区端点 → 块内原始文本偏移：用 Range 前缀文本长度折算，元素端点与文本端点统一处理。 */
function rawOffsetInBlock(block: WikiPreviewBlock, container: Node, offset: number): number | null {
  if (!block.element.contains(container)) {
    return null
  }
  const probe = block.element.ownerDocument.createRange()
  try {
    probe.setStart(block.element, 0)
    probe.setEnd(container, offset)
    return probe.toString().length
  }
  catch {
    return null
  }
}

/**
 * 捕获浏览器选区并冻结成锚点快照。
 *
 * 只接受当前预览层内的单个叶块（同一段落/标题/列表项/代码块/表格单元格）内的非折叠选区；
 * 跨块、预览外、空白或过短选区一律拒绝并给出原因，绝不截断或猜测。
 */
export function captureWikiQuoteSelection(root: HTMLElement | null | undefined, selection: Selection | null = typeof window === 'undefined' ? null : window.getSelection()): WikiQuoteSelectionResult {
  if (!selection || selection.rangeCount === 0) {
    return { error: 'no-selection' }
  }
  if (selection.isCollapsed) {
    return { error: 'collapsed' }
  }
  if (!root) {
    return { error: 'outside-preview' }
  }
  const range = selection.getRangeAt(0)
  if (range.collapsed) {
    return { error: 'collapsed' }
  }
  if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) {
    return { error: 'outside-preview' }
  }
  const blocks = collectWikiPreviewBlocks(root)
  const index = new Map<Element, WikiPreviewBlock>(blocks.map(block => [block.element, block]))
  const startBlock = closestPreviewBlock(range.startContainer, index)
  const endBlock = closestPreviewBlock(range.endContainer, index)
  if (!startBlock || !endBlock) {
    return { error: 'outside-preview' }
  }
  if (startBlock !== endBlock) {
    return { error: 'cross-block' }
  }
  const startRaw = rawOffsetInBlock(startBlock, range.startContainer, range.startOffset)
  const endRaw = rawOffsetInBlock(startBlock, range.endContainer, range.endOffset)
  if (startRaw == null || endRaw == null) {
    return { error: 'outside-preview' }
  }
  const normalized = normalizedRangeOfRaw(startBlock.offsetMap, startRaw, endRaw)
  const anchor = buildWikiQuoteAnchor({
    blockText: startBlock.rawText,
    start: normalized.start,
    end: normalized.end,
    blockOrdinal: startBlock.ordinal,
    blockType: startBlock.type,
    tableRow: startBlock.tableRow,
    tableCell: startBlock.tableCell,
  })
  if (!anchor) {
    return { error: 'too-short' }
  }
  const rect = range.getBoundingClientRect()
  return {
    snapshot: {
      anchor,
      blockOrdinal: startBlock.ordinal,
      blockType: startBlock.type,
      tableRow: startBlock.tableRow,
      tableCell: startBlock.tableCell,
      rect: rect.width || rect.height
        ? { top: rect.top, left: rect.left, width: rect.width, height: rect.height }
        : null,
      range: range.cloneRange(),
    },
  }
}

/** 拒绝原因对应的提示；空字符串表示静默（普通点击/折叠选区不算操作失误）。 */
export function wikiQuoteSelectionErrorMessage(error: WikiQuoteSelectionError): string {
  switch (error) {
    case 'cross-block':
      return '只能引用同一段落、列表项或表格单元格内的连续文字；跨块选区请分开引用。'
    case 'too-short':
      return '请选择至少 2 个字符的正文文字再发表评论。'
    default:
      return ''
  }
}

/** 清除阅读层高亮：只把装饰 span 摊平回文本节点，不合并文本节点（保持 Range 引用稳定）。 */
export function clearWikiQuoteHighlights(root: HTMLElement | null | undefined): void {
  if (!root) {
    return
  }
  for (const mark of [...root.querySelectorAll<HTMLElement>(QUOTE_MARK_SELECTOR)]) {
    const parent = mark.parentNode
    if (!parent) {
      continue
    }
    while (mark.firstChild) {
      parent.insertBefore(mark.firstChild, mark)
    }
    parent.removeChild(mark)
  }
  root.removeAttribute(WIKI_QUOTE_MARK_SIGNATURE_ATTRIBUTE)
}

/** 锚点在块内的归一化区间 → DOM Range；跨多个文本节点（含行内元素）也能还原。 */
function rangeOfBlockRange(block: WikiPreviewBlock, rawStart: number, rawEnd: number): Range | null {
  const range = block.element.ownerDocument.createRange()
  let offset = 0
  let started = false
  let ended = false
  for (const node of collectTextNodes(block.element)) {
    const length = node.data.length
    const nodeStart = offset
    const nodeEnd = offset + length
    offset = nodeEnd
    if (!started && rawStart < nodeEnd) {
      range.setStart(node, rawStart - nodeStart)
      started = true
    }
    if (started && rawEnd <= nodeEnd) {
      range.setEnd(node, rawEnd - nodeStart)
      ended = true
      break
    }
  }
  return started && ended && !range.collapsed ? range : null
}

/** 高亮目标签名：正文与目标未变时跳过 DOM 重写，避免观察者与写标记互相触发。 */
function highlightSignature(commentId: string, blockOrdinal: number, start: number, end: number): string {
  return `${commentId}:${blockOrdinal}:${start}:${end}`
}

/**
 * 按当前正文重建高亮：只包唯一命中的引用，歧义/失锚绝不加装饰。
 *
 * 实现要点：
 * - 先在未写标记的语义下解析（标记只用 span 包住原文本节点，文本与偏移不变）；
 * - 目标签名与现有标记一致时直接返回，不产生任何 DOM 变形（避免与外部 MutationObserver 回写）；
 * - 写入时先清除旧标记，再逐条按块内偏移还原 Range，用 extract/insert 兼容跨行内元素的引用。
 */
export function applyWikiQuoteHighlights(
  root: HTMLElement | null | undefined,
  targets: readonly WikiQuoteHighlightTarget[],
  options: WikiQuoteHighlightOptions = {},
): WikiQuoteHighlightOutcome {
  const outcome: WikiQuoteHighlightOutcome = { applied: [], classifications: {}, matches: {} }
  if (!root) {
    return outcome
  }
  const blocks = collectWikiPreviewBlocks(root)
  const blockIndex = wikiQuoteAnchorBlocks(blocks)
  const resolved: Array<{ commentId: string, block: WikiPreviewBlock, start: number, end: number }> = []
  const signatures: string[] = []
  for (const target of targets) {
    if (!target.anchor) {
      continue
    }
    // 版本身份两侧都由调用方给出：captured 来自评论盖章的兄弟字段，current 只在能拿到
    // 同一口径的版本身份时才传；缺任一侧纯函数不臆断为改版（scopeChanged 保持 false）
    const scope = options.scopeOf?.(target.commentId)
    const classification = classifyWikiQuoteAnchor(target.anchor, blockIndex, {
      capturedScope: scope?.captured,
      currentScope: scope?.current,
    })
    outcome.classifications[target.commentId] = classification
    if (!classification.match) {
      continue
    }
    const block = blocks.find(candidate => candidate.ordinal === classification.match?.blockOrdinal)
    const raw = block ? rawRangeOfNormalized(block.offsetMap, classification.match.start, classification.match.end) : null
    if (!block || !raw) {
      continue
    }
    resolved.push({ commentId: target.commentId, block, start: raw.start, end: raw.end })
    signatures.push(highlightSignature(target.commentId, block.ordinal, classification.match.start, classification.match.end))
    outcome.applied.push(target.commentId)
    outcome.matches[target.commentId] = classification.match
  }
  const signature = signatures.join('|')
  const marks = [...root.querySelectorAll<HTMLElement>(QUOTE_MARK_SELECTOR)]
  if (marks.length === resolved.length && root.getAttribute(WIKI_QUOTE_MARK_SIGNATURE_ATTRIBUTE) === signature) {
    return outcome
  }
  clearWikiQuoteHighlights(root)
  const applied: string[] = []
  for (const entry of resolved) {
    const range = rangeOfBlockRange(entry.block, entry.start, entry.end)
    if (!range) {
      continue
    }
    const mark = root.ownerDocument.createElement('span')
    mark.className = WIKI_QUOTE_MARK_CLASS
    mark.setAttribute(WIKI_QUOTE_MARK_ATTRIBUTE, entry.commentId)
    mark.setAttribute('title', '查看相关评论')
    if (options.onClick) {
      mark.addEventListener('click', event => options.onClick?.(entry.commentId, event))
    }
    mark.appendChild(range.extractContents())
    range.insertNode(mark)
    applied.push(entry.commentId)
  }
  outcome.applied = applied
  root.setAttribute(WIKI_QUOTE_MARK_SIGNATURE_ATTRIBUTE, signature)
  return outcome
}

/** 找出某条评论的高亮装饰（未高亮/未渲染时返回 null）。 */
export function findWikiQuoteHighlight(root: HTMLElement | null | undefined, commentId: string): HTMLElement | null {
  if (!root || !commentId) {
    return null
  }
  // 不拼选择器（评论 ID 不参与 CSS 转义），直接按属性比对，避免注入与转义差异
  const marks = [...root.querySelectorAll<HTMLElement>(QUOTE_MARK_SELECTOR)]
  return marks.find(mark => mark.getAttribute(WIKI_QUOTE_MARK_ATTRIBUTE) === commentId) ?? null
}

/** 标记当前聚焦的引用：其余高亮降级，供「点击评论定位原文」时的视觉反馈。 */
export function setWikiQuoteHighlightActive(root: HTMLElement | null | undefined, commentId: string): void {
  if (!root) {
    return
  }
  for (const mark of [...root.querySelectorAll<HTMLElement>(QUOTE_MARK_SELECTOR)]) {
    mark.classList.toggle('is-active', mark.getAttribute(WIKI_QUOTE_MARK_ATTRIBUTE) === commentId)
  }
}

/**
 * 滚动到唯一命中的引用：复用 `--wiki-sticky-top` 滚动契约（装饰类自带 scroll-margin-top）。
 * 没有高亮（歧义/失锚/未渲染）返回 false，由调用方给出「无法定位」提示，绝不滚动到错误文字。
 */
export function scrollWikiQuoteHighlight(root: HTMLElement | null | undefined, commentId: string): boolean {
  const mark = findWikiQuoteHighlight(root, commentId)
  if (!mark) {
    return false
  }
  mark.scrollIntoView({ behavior: 'smooth', block: 'start' })
  setWikiQuoteHighlightActive(root, commentId)
  return true
}
