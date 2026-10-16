import type {
  FeishuBlockWriteResultRes,
  FeishuCloudDocPageRes,
  FeishuCloudDocRes,
  FeishuDocumentBlockPageRes,
  FeishuDocumentBlockRes,
  FeishuDocumentContentRes,
  FeishuDocumentMetaRes,
  FeishuQuotaConfigRes,
  FeishuQuotaRes,
  FeishuWikiNodePageRes,
  FeishuWikiNodeRes,
  FeishuWikiSpacePageRes,
  FeishuWikiSpaceRes,
} from '@/api/modules/platform-feishu'

/**
 * 飞书集成的契约边界：字段名与端点以后端 `FeishuCloudDocController` / `FeishuQuotaController`
 * 的实际 DTO 为准（非猜测）。这里只做三件事：
 * 1. 写能力与权限常量（与后端注册的权限码、已提供的写路径一致）；
 * 2. 失败归一（把后端业务异常文案 / HTTP 状态映射成页面状态）；
 * 3. 响应到视图模型的解析（分页合并、块 raw JSON 解析、文本元素与子块 JSON 生成）。
 *
 * 解析不到必需字段时返回 undefined 或 missing 标记，由页面如实提示，不使用占位数据填充。
 */

/** 写能力：后端已提供的写路径；未提供的路径不得出现前端保存入口。 */
export type FeishuWriteState = 'confirmed' | 'unavailable'

export const FEISHU_WRITE_CONTRACT: {
  /** 文本块 elements 更新：PATCH .../blocks/{blockId}/elements（飞书 update_text_elements）。 */
  blockElements: FeishuWriteState
  /** 子块追加/删除：POST/DELETE .../blocks/{parentBlockId}/children。 */
  blockChildren: FeishuWriteState
  /** 整篇正文覆盖写：后端未提供。 */
  documentContent: FeishuWriteState
  /** 全局默认额度配置：PUT api/platform/feishu/quota/config。 */
  quotaConfig: FeishuWriteState
} = {
  blockElements: 'confirmed',
  blockChildren: 'confirmed',
  documentContent: 'unavailable',
  quotaConfig: 'confirmed',
}

/** 后端 @PermissionRegister 实际注册的权限码。 */
export const FEISHU_PERMISSIONS = {
  view: 'platform:feishu:view',
  edit: 'platform:feishu:edit',
  config: 'platform:feishu:config',
} as const

/** 前端块加载页数上限：单页大小由后端决定（clamp ≤ 20），避免无界翻页。 */
export const FEISHU_BLOCK_PAGE_LIMIT = 10

/* ------------------------------------------------------------------ *
 * 失败归一
 * ------------------------------------------------------------------ */

export type FeishuFailureKind
  = | 'unconfigured'
    | 'unauthorized'
    | 'forbidden'
    | 'not-found'
    | 'unavailable'
    | 'rate-limited'
    | 'conflict'
    | 'timeout'
    | 'error'

export interface FeishuFailure {
  kind: FeishuFailureKind
  message: string
  status?: number
}

/** 请求范围决定 404 的含义：集合端点 404 视为能力未上线，文档端点 404 视为文档不存在。 */
export type FeishuRequestScope = 'collection' | 'document' | 'quota'

export function normalizeFeishuFailure(error: unknown, scope: FeishuRequestScope): FeishuFailure {
  const status = httpStatusOf(error)
  const message = failureMessage(error)
  const kind = failureKind(status, message, scope)
  return { kind, message: message || defaultFailureMessage(kind), status }
}

export function httpStatusOf(error: unknown): number | undefined {
  if (!isRecord(error)) {
    return undefined
  }
  const response = isRecord(error.response) ? error.response : undefined
  const candidate = response?.status ?? error.status ?? error.statusCode
  return typeof candidate === 'number' && Number.isFinite(candidate) ? candidate : undefined
}

function failureKind(status: number | undefined, message: string, scope: FeishuRequestScope): FeishuFailureKind {
  if (status === 429) {
    return 'rate-limited'
  }
  if (status === 401) {
    return 'unauthorized'
  }
  if (status === 403) {
    return 'forbidden'
  }
  if (status === 409) {
    return 'conflict'
  }
  if (status === 408 || status === 504) {
    return 'timeout'
  }
  if (status === 404) {
    return scope === 'document' ? 'not-found' : 'unavailable'
  }
  if (status === 501 || status === 502 || status === 503) {
    return 'unavailable'
  }
  // 后端能力闸门与额度闸门抛业务异常（HTTP 200），只能按文案判定
  if (/能力未启用|未配置|未接入|未启用|尚未配置|未绑定|not\s*configured/i.test(message)) {
    return 'unconfigured'
  }
  if (/额度已用完|限流|频率|过于频繁|请求过多|每分钟|每日|too\s*many|rate\s*limit|throttl/i.test(message)) {
    return 'rate-limited'
  }
  if (/未登录|登录状态|账号不可用|unauthorized/i.test(message)) {
    return 'unauthorized'
  }
  if (/无权|未授权|forbidden|denied|permission/i.test(message)) {
    return 'forbidden'
  }
  if (/冲突|已被.{0,6}修改|版本过期|revision|conflict|stale/i.test(message)) {
    return 'conflict'
  }
  if (/不存在|已删除|not\s*found/i.test(message)) {
    return scope === 'document' ? 'not-found' : 'unavailable'
  }
  if (/超时|timeout|timed\s*out/i.test(message)) {
    return 'timeout'
  }
  return 'error'
}

function failureMessage(error: unknown): string {
  if (typeof error === 'string') {
    return error
  }
  if (error instanceof Error) {
    return error.message
  }
  if (isRecord(error)) {
    const candidate = error.message ?? error.error ?? error.msg
    if (typeof candidate === 'string') {
      return candidate
    }
  }
  return ''
}

export function defaultFailureMessage(kind: FeishuFailureKind): string {
  return {
    'unconfigured': '飞书开放平台能力未启用',
    'unauthorized': '登录状态已失效',
    'forbidden': '当前账号无权访问该飞书资源',
    'not-found': '文档不存在或已被删除',
    'unavailable': '后端暂未提供该飞书能力',
    'rate-limited': '飞书调用额度不足',
    'conflict': '文档已被他人修改',
    'timeout': '请求超时',
    'error': '请求失败',
  }[kind]
}

export function failureTitle(kind: FeishuFailureKind): string {
  return {
    'unconfigured': '能力未启用',
    'unauthorized': '需要重新登录',
    'forbidden': '无权访问',
    'not-found': '文档不存在',
    'unavailable': '能力暂不可用',
    'rate-limited': '额度不足',
    'conflict': '存在冲突',
    'timeout': '请求超时',
    'error': '加载失败',
  }[kind]
}

/* ------------------------------------------------------------------ *
 * 通用取值
 * ------------------------------------------------------------------ */

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

export function textOf(value: unknown, fallback = ''): string {
  if (typeof value === 'string') {
    return value
  }
  if (typeof value === 'number' && Number.isFinite(value)) {
    // Long/Snowflake 一律字符串，禁止 Number(id) 往返
    return String(value)
  }
  return fallback
}

export function numberOf(value: unknown): number | undefined {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value)
    return Number.isFinite(parsed) ? parsed : undefined
  }
  return undefined
}

/* ------------------------------------------------------------------ *
 * 视图模型：云空间与知识库
 * ------------------------------------------------------------------ */

export interface FeishuFileItem {
  /** 云空间文件/文件夹 token；文件夹 token 即下钻用的 folderToken。 */
  token: string
  name: string
  type: string
  isFolder: boolean
  url?: string
  ownerId?: string
  modifiedTime?: string
  shortcutTargetToken?: string
}

export interface FeishuSpaceItem {
  spaceId: string
  name: string
  description?: string
  spaceType?: string
  visibility?: string
}

export interface FeishuWikiNodeItem {
  nodeToken: string
  objToken?: string
  objType?: string
  title?: string
  hasChild: boolean
  parentNodeToken?: string
  nodeType?: string
  originNodeToken?: string
  originSpaceId?: string
  spaceId?: string
}

export interface FeishuPage<T> {
  items: T[]
  nextPageToken?: string
  hasMore: boolean
}

export const FOLDER_TYPE = 'folder'

export function resolveCloudDocPage(raw: FeishuCloudDocPageRes | undefined): FeishuPage<FeishuFileItem> {
  const items = (raw?.items ?? []).filter(Boolean).map(resolveCloudDoc)
  return { items, nextPageToken: raw?.nextPageToken || undefined, hasMore: Boolean(raw?.hasMore) }
}

export function resolveCloudDoc(raw: FeishuCloudDocRes): FeishuFileItem {
  const type = textOf(raw?.type)
  return {
    token: textOf(raw?.token),
    name: textOf(raw?.name),
    type,
    isFolder: type.toLowerCase() === FOLDER_TYPE,
    url: raw?.url || undefined,
    ownerId: raw?.ownerId || undefined,
    modifiedTime: raw?.modifiedTime || undefined,
    shortcutTargetToken: raw?.shortcutTargetToken || undefined,
  }
}

export function resolveWikiSpacePage(raw: FeishuWikiSpacePageRes | undefined): FeishuPage<FeishuSpaceItem> {
  const items = (raw?.items ?? []).filter(Boolean).map(resolveWikiSpace)
  return { items, nextPageToken: raw?.nextPageToken || undefined, hasMore: Boolean(raw?.hasMore) }
}

export function resolveWikiSpace(raw: FeishuWikiSpaceRes): FeishuSpaceItem {
  return {
    spaceId: textOf(raw?.spaceId),
    name: textOf(raw?.name, textOf(raw?.spaceId)),
    description: raw?.description || undefined,
    spaceType: raw?.spaceType || undefined,
    visibility: raw?.visibility || undefined,
  }
}

export function resolveWikiNodePage(raw: FeishuWikiNodePageRes | undefined): FeishuPage<FeishuWikiNodeItem> {
  const items = (raw?.items ?? []).filter(Boolean).map(resolveWikiNode).filter(node => node.nodeToken)
  return { items, nextPageToken: raw?.nextPageToken || undefined, hasMore: Boolean(raw?.hasMore) }
}

export function resolveWikiNode(raw: FeishuWikiNodeRes): FeishuWikiNodeItem {
  return {
    nodeToken: textOf(raw?.nodeToken),
    objToken: raw?.objToken || undefined,
    objType: raw?.objType || undefined,
    title: raw?.title || undefined,
    hasChild: Boolean(raw?.hasChild),
    parentNodeToken: raw?.parentNodeToken || undefined,
    nodeType: raw?.nodeType || undefined,
    originNodeToken: raw?.originNodeToken || undefined,
    originSpaceId: raw?.originSpaceId || undefined,
    spaceId: raw?.spaceId || undefined,
  }
}

/** 分页追加：按 key 去重，避免翻页重复项污染列表。 */
export function appendPage<T>(current: T[], next: T[], key: (item: T) => string): T[] {
  const seen = new Set(current.map(item => key(item)))
  const merged = [...current]
  for (const item of next) {
    const itemKey = key(item)
    if (itemKey && !seen.has(itemKey)) {
      seen.add(itemKey)
      merged.push(item)
    }
  }
  return merged
}

/** 关键词过滤：端点未提供关键词参数，搜索只作用于已加载页，界面必须说明这一点。 */
export function filterFileItems(items: FeishuFileItem[], keyword: string): FeishuFileItem[] {
  const needle = keyword.trim().toLowerCase()
  if (!needle) {
    return items
  }
  return items.filter((item) => {
    const haystack = [item.name, item.token, item.type, item.ownerId, item.modifiedTime].filter(Boolean).join(' ').toLowerCase()
    return haystack.includes(needle)
  })
}

/** 类型筛选项来自真实返回数据，不预置未确认的类型枚举。 */
export function fileTypeOptions(items: FeishuFileItem[]): { label: string, value: string }[] {
  const types = [...new Set(items.map(item => item.type).filter(Boolean))].sort()
  return types.map(type => ({ label: type === FOLDER_TYPE ? '文件夹' : type, value: type }))
}

/* ------------------------------------------------------------------ *
 * 视图模型：文档元信息、正文与块
 * ------------------------------------------------------------------ */

export interface FeishuDocumentMeta {
  documentId: string
  title: string
  revisionId?: string
}

export interface FeishuDocumentContent {
  documentId: string
  content: string
}

export function resolveDocumentMeta(raw: FeishuDocumentMetaRes | undefined, fallbackId: string): FeishuDocumentMeta {
  return {
    documentId: textOf(raw?.documentId, fallbackId),
    title: raw?.title || fallbackId,
    revisionId: raw?.revisionId || undefined,
  }
}

export function resolveDocumentContent(raw: FeishuDocumentContentRes | undefined): FeishuDocumentContent {
  return {
    documentId: textOf(raw?.documentId),
    content: textOf(raw?.content),
  }
}

/** 可写文本块类型（飞书 docx block_type 子集）；其余块一律只读。 */
const BLOCK_TYPE_NAMES: Record<number, string> = {
  1: '页面',
  2: '文本',
  3: '标题 1',
  4: '标题 2',
  5: '标题 3',
  6: '标题 4',
  7: '标题 5',
  8: '标题 6',
  9: '标题 7',
  10: '标题 8',
  11: '标题 9',
  12: '无序列表',
  13: '有序列表',
  14: '代码块',
  15: '引用',
  16: '公式',
  17: '待办',
  18: '待办事项',
  19: '高亮块',
  20: '会话卡片',
  21: '流程图',
  22: '分隔线',
  23: '文件',
  24: '网格',
  25: '网格列',
  26: '引用 wiki',
  27: '图片',
  28: '表格',
  29: '表格单元格',
  30: '视图',
  31: '引用容器',
  32: '代码片段',
  33: '内嵌网页',
  34: '小组件',
  35: '画板',
  36: '画册',
}

const TEXT_BLOCK_TYPES = new Set([2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 17, 18, 19])

export interface FeishuBlockNode {
  blockId: string
  parentId?: string
  blockType?: number
  typeName: string
  /** 从 raw JSON 解析出的文本；解析不到时为 undefined，不用占位内容填充。 */
  text?: string
  /** raw 解析失败或没有文本元素。 */
  textMissing: boolean
  /**
   * 权威同级下标：只有父块 children 明确列出该块时才存在。
   * 分页或父块未载入时保持 undefined——用页内位置当索引会误删其他子块。
   */
  index?: number
  depth: number
  /** 父块 children 返回的已知子块数；父块未返回 children 或未载入时为 undefined。 */
  childCount?: number
  /** 父块是否出现在当前已加载块中（决定能否确认删除区间与子块数）。 */
  parentLoaded: boolean
  editable: boolean
  readOnlyReason?: string
}

export interface FeishuBlockTree {
  items: FeishuBlockNode[]
  nextPageToken?: string
  hasMore: boolean
}

export interface FeishuBlockElements {
  /** 原始文本元素数组（未返回可识别结构时为 undefined）。 */
  elements?: unknown[]
  /** 元素数量。 */
  count: number
  /**
   * 是否可以用「单个纯文本元素」整块替换而不丢数据：只有恰好一个 text_run、
   * 且该 text_run 只含 content（没有 text_element_style、链接、@ 等）时才为 true。
   * 多元素、带样式或结构未知的块一律只读，避免 PATCH elements 覆盖原始格式。
   */
  losslessReplaceable: boolean
  reason?: string
}

/**
 * 判断块能否安全地用单元素整块替换。
 * 这里刻意保守：只要无法从 raw 证明原结构是「单个无样式 text_run」，就判定为不可编辑。
 */
export function blockElementsOf(raw: string | undefined): FeishuBlockElements {
  if (!raw) {
    return { count: 0, losslessReplaceable: false, reason: '块原文未返回，无法证明整块替换无损，保持只读' }
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  }
  catch {
    return { count: 0, losslessReplaceable: false, reason: '块原文不是合法 JSON，无法证明整块替换无损，保持只读' }
  }
  if (!isRecord(parsed)) {
    return { count: 0, losslessReplaceable: false, reason: '块原文不是对象，无法证明整块替换无损，保持只读' }
  }
  const holder = parsed.text !== undefined ? parsed.text : parsed.elements
  const elements = elementsArrayOf(holder)
  if (!elements) {
    return { count: 0, losslessReplaceable: false, reason: '块原文没有文本元素结构，无法证明整块替换无损，保持只读' }
  }
  if (elements.length === 0) {
    // 空文本块没有可丢失的元素，写入单个纯文本元素是无损的
    return { elements, count: 0, losslessReplaceable: true }
  }
  if (elements.length > 1) {
    return { elements, count: elements.length, losslessReplaceable: false, reason: `该块由 ${elements.length} 个文本元素组成，整块替换会丢失原有分段/格式，保持只读` }
  }
  const only = elements[0]
  if (!isPlainTextRun(only)) {
    return { elements, count: 1, losslessReplaceable: false, reason: '该块文本元素带样式、链接或其他类型，整块替换会丢格式，保持只读' }
  }
  return { elements, count: 1, losslessReplaceable: true }
}

function elementsArrayOf(holder: unknown): unknown[] | undefined {
  if (Array.isArray(holder)) {
    return holder
  }
  if (isRecord(holder) && Array.isArray(holder.elements)) {
    return holder.elements
  }
  return undefined
}

/** 单个无样式纯文本元素：只允许 { text_run: { content } }，不出现任何其他字段。 */
function isPlainTextRun(element: unknown): boolean {
  if (!isRecord(element)) {
    return false
  }
  if (Object.keys(element).length !== 1 || !('text_run' in element)) {
    return false
  }
  const textRun = element.text_run
  if (!isRecord(textRun)) {
    return false
  }
  if (Object.keys(textRun).length !== 1 || !('content' in textRun)) {
    return false
  }
  return typeof textRun.content === 'string'
}

export function blockTypeName(blockType: number | undefined): string {
  if (blockType === undefined) {
    return '未知块'
  }
  return BLOCK_TYPE_NAMES[blockType] ?? `块类型 ${blockType}`
}

export function isTextBlock(blockType: number | undefined): boolean {
  return blockType !== undefined && TEXT_BLOCK_TYPES.has(blockType)
}

/** 块 raw 为飞书原始块 JSON 文本：解析文本元素，解析不出内容时标记 textMissing。 */
export function parseBlockText(raw: string | undefined): { text?: string, missing: boolean } {
  if (!raw) {
    return { missing: true }
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  }
  catch {
    return { missing: true }
  }
  const text = resolveBlockText(parsed)
  return text === undefined ? { missing: true } : { text, missing: false }
}

/** 文本元素按官方 text_run.content 提取；兼容 text.elements 与顶层 elements 两种承载位置。 */
export function resolveBlockText(node: unknown): string | undefined {
  if (!isRecord(node)) {
    return undefined
  }
  const holders: unknown[] = []
  if (node.text !== undefined) {
    holders.push(node.text)
  }
  if (node.elements !== undefined) {
    holders.push(node.elements)
  }
  for (const holder of holders) {
    const text = textFromHolder(holder)
    if (text !== undefined) {
      return text
    }
  }
  return undefined
}

function textFromHolder(holder: unknown): string | undefined {
  if (typeof holder === 'string') {
    return holder
  }
  if (Array.isArray(holder)) {
    return joinTextElements(holder)
  }
  if (isRecord(holder)) {
    if (Array.isArray(holder.elements)) {
      return joinTextElements(holder.elements)
    }
    const content = textOf(holder.content)
    return content === '' ? undefined : content
  }
  return undefined
}

function joinTextElements(elements: unknown[]): string | undefined {
  const parts: string[] = []
  for (const element of elements) {
    if (typeof element === 'string') {
      parts.push(element)
      continue
    }
    if (!isRecord(element)) {
      continue
    }
    const textRun = isRecord(element.text_run) ? element.text_run : (isRecord(element.textRun) ? element.textRun : undefined)
    if (!textRun) {
      continue
    }
    const content = textOf(textRun.content)
    if (content !== '') {
      parts.push(content)
    }
  }
  return parts.length ? parts.join('') : undefined
}

export function resolveBlockPage(raw: FeishuDocumentBlockPageRes | undefined): FeishuBlockTree {
  const records = ((raw?.items ?? []).filter(Boolean) as FeishuDocumentBlockRes[])
    .filter(record => textOf(record?.blockId) !== '')

  /** 只有后端返回的 children 列表可以当作权威同级顺序。 */
  const childIds = new Map<string, string[]>()
  const parentOf = new Map<string, string>()
  for (const record of records) {
    const blockId = textOf(record.blockId)
    if (Array.isArray(record.children)) {
      childIds.set(blockId, record.children.map(child => textOf(child)).filter(Boolean))
    }
    const parentId = textOf(record.parentId)
    if (parentId) {
      parentOf.set(blockId, parentId)
    }
  }
  const siblingIndex = new Map<string, number>()
  for (const ids of childIds.values()) {
    ids.forEach((id, position) => {
      if (!siblingIndex.has(id)) {
        siblingIndex.set(id, position)
      }
    })
  }

  const items = records.map((record): FeishuBlockNode => {
    const blockId = textOf(record.blockId)
    const blockType = numberOf(record.blockType)
    const { text, missing } = parseBlockText(record.raw)
    const parentId = textOf(record.parentId) || undefined
    const replaceable = blockElementsOf(record.raw).losslessReplaceable
    const editable = FEISHU_WRITE_CONTRACT.blockElements === 'confirmed' && isTextBlock(blockType) && replaceable
    return {
      blockId,
      parentId,
      blockType,
      typeName: blockTypeName(blockType),
      text,
      textMissing: missing,
      index: siblingIndex.get(blockId),
      depth: resolveDepth(blockId, parentOf, 0),
      childCount: childIds.get(blockId)?.length,
      parentLoaded: Boolean(parentId && records.some(item => textOf(item.blockId) === parentId)),
      editable,
      readOnlyReason: editable ? undefined : readOnlyReason(blockType, record.raw),
    }
  })

  return { items, nextPageToken: raw?.nextPageToken || undefined, hasMore: Boolean(raw?.hasMore) }
}

function resolveDepth(blockId: string, parentOf: Map<string, string>, guard: number): number {
  if (guard > 8) {
    return guard
  }
  const parentId = parentOf.get(blockId)
  if (!parentId) {
    return 0
  }
  if (!parentOf.has(parentId)) {
    // 父块在本页之外（分页边界）：按一级缩进呈现，不谎报为根级
    return 1
  }
  return resolveDepth(parentId, parentOf, guard + 1) + 1
}

export function readOnlyReasonText(reason: string | undefined) {
  return reason || '该块保持只读'
}

function readOnlyReason(blockType: number | undefined, raw: string | undefined): string {
  if (blockType === undefined) {
    return '后端未返回块类型，保持只读'
  }
  if (!isTextBlock(blockType)) {
    return '该类型块不支持文本 elements 更新，保持只读'
  }
  if (FEISHU_WRITE_CONTRACT.blockElements !== 'confirmed') {
    return '写能力未确认，保持只读'
  }
  return blockElementsOf(raw).reason ?? '该块无法证明整块替换无损，保持只读'
}

/* ------------------------------------------------------------------ *
 * 写入：文本元素与子块 JSON
 * ------------------------------------------------------------------ */

/** 单个文本元素 JSON 原文：飞书 update_text_elements.elements[].text_run.content。 */
export function textElementJson(content: string): string {
  return JSON.stringify({ text_run: { content } })
}

/** 追加子块用的文本块 JSON 原文（官方 block_type 2 = 文本块）。 */
export function textBlockJson(content: string): string {
  return JSON.stringify({ block_type: 2, text: { elements: [{ text_run: { content } }] } })
}

/**
 * documentRevisionId：后端为 Long，元信息里是字符串。
 * 只有能安全转成整数的版本号才回填（避免 Number(id) 式精度问题），否则省略让飞书按最新版本处理。
 */
export function revisionParamOf(revisionId: string | undefined): number | undefined {
  if (!revisionId) {
    return undefined
  }
  const trimmed = revisionId.trim()
  if (!/^-?\d+$/.test(trimmed)) {
    return undefined
  }
  const parsed = Number(trimmed)
  return Number.isSafeInteger(parsed) && parsed >= -1 ? parsed : undefined
}

/** 删除子块的区间：后端契约为 [startIndex, endIndex)。 */
export function deleteRangeOf(index: number) {
  return { startIndex: index, endIndex: index + 1 }
}

export interface FeishuDeleteTarget {
  ok: boolean
  reason?: string
  range?: { startIndex: number, endIndex: number }
}

/**
 * 删除子块的前置条件：必须有权威父块 children 列表给出的同级下标，且父块已在当前已加载块中。
 * 分页或父块未载入时页内位置不是真实下标，直接用它会删掉相邻子块，因此拒绝并提示重新加载。
 */
export function deleteTargetOf(node: FeishuBlockNode, blocks: FeishuBlockNode[]): FeishuDeleteTarget {
  if (!node.parentId) {
    return { ok: false, reason: '后端未返回 parentId，无法确定删除区间；请重新加载块结构。' }
  }
  if (!blocks.some(item => item.blockId === node.parentId)) {
    return { ok: false, reason: '父块未加载（可能在其他分页），无法确认同级下标；请加载父块后再删除，避免误删其他子块。' }
  }
  if (node.index === undefined) {
    return { ok: false, reason: '父块 children 未列出该块，同级下标不可确认；请重新加载块结构后再删除。' }
  }
  return { ok: true, range: deleteRangeOf(node.index) }
}

/** 写后校验：以写接口返回的 blocksRaw 为准，确认目标块文本确实等于提交内容。 */
export function verifyElementWrite(
  result: FeishuBlockWriteResultRes | undefined,
  blockId: string,
  expectedText: string,
): 'verified' | 'unverified' | 'missing' {
  const blockIds = (result?.blockIds ?? []).map(id => textOf(id))
  const rawBlocks = (result?.blocksRaw ?? []).map(raw => textOf(raw)).filter(Boolean)
  const position = blockIds.indexOf(blockId)
  const raw = position >= 0 ? rawBlocks[position] : rawBlocks[0]
  if (!raw) {
    return 'missing'
  }
  const { text } = parseBlockText(raw)
  if (text === undefined) {
    return 'unverified'
  }
  return text === expectedText ? 'verified' : 'unverified'
}

/** 写后校验：追加/删除子块以父块子块数变化核对（不可比时返回 unknown）。 */
export function verifyChildCount(before: number | undefined, after: number | undefined, direction: 'create' | 'delete') {
  if (before === undefined || after === undefined) {
    return 'unknown' as const
  }
  if (direction === 'create') {
    return after > before ? 'verified' as const : 'unverified' as const
  }
  return after < before ? 'verified' as const : 'unverified' as const
}

/* ------------------------------------------------------------------ *
 * 视图模型：额度
 * ------------------------------------------------------------------ */

export interface FeishuQuotaUsage {
  minuteUsed?: number
  minuteLimit?: number
  minuteRemaining?: number
  dailyUsed?: number
  dailyLimit?: number
  dailyRemaining?: number
}

export interface FeishuQuotaConfigView {
  defaultPerMinuteLimit?: number
  defaultDailyLimit?: number
  /** 两个上限是否都返回；未返回时页面禁用保存，避免把空值写回后端。 */
  resolved: boolean
  missing: string[]
}

export function resolveQuotaUsage(raw: FeishuQuotaRes | undefined): FeishuQuotaUsage {
  const minuteUsed = numberOf(raw?.minuteUsed)
  const minuteLimit = numberOf(raw?.minuteLimit)
  const dailyUsed = numberOf(raw?.dailyUsed)
  const dailyLimit = numberOf(raw?.dailyLimit)
  return {
    minuteUsed,
    minuteLimit,
    minuteRemaining: minuteUsed !== undefined && minuteLimit !== undefined ? Math.max(0, minuteLimit - minuteUsed) : undefined,
    dailyUsed,
    dailyLimit,
    dailyRemaining: dailyUsed !== undefined && dailyLimit !== undefined ? Math.max(0, dailyLimit - dailyUsed) : undefined,
  }
}

export function resolveQuotaConfig(raw: FeishuQuotaConfigRes | undefined): FeishuQuotaConfigView {
  const defaultPerMinuteLimit = numberOf(raw?.defaultPerMinuteLimit)
  const defaultDailyLimit = numberOf(raw?.defaultDailyLimit)
  const missing: string[] = []
  if (defaultPerMinuteLimit === undefined) {
    missing.push('defaultPerMinuteLimit')
  }
  if (defaultDailyLimit === undefined) {
    missing.push('defaultDailyLimit')
  }
  return {
    defaultPerMinuteLimit,
    defaultDailyLimit,
    resolved: missing.length === 0,
    missing,
  }
}

/** 授权说明：应用自有文档可直接编辑；他人文档需要 owner 授权并把应用添加为可访问应用。 */
export const FEISHU_AUTHZ_NOTICE = '应用自有的飞书文档可直接编辑；他人创建的文档需要文档 owner 授权，并把本应用添加为可访问应用后才能编辑。'
