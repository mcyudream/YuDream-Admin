import type { FeishuBlockNode, FeishuDocumentContent, FeishuDocumentMeta, FeishuFailure, FeishuQuotaConfigView } from './feishu-contract'
import type { FeishuBlockWriteResultRes, FeishuQuotaConfigBody } from '@/api/modules/platform-feishu'
import { computed, reactive } from 'vue'
import { FEISHU_BLOCK_PAGE_LIMIT, FEISHU_WRITE_CONTRACT, textBlockJson, textElementJson, verifyElementWrite } from './feishu-contract'

/**
 * 飞书文档工作台的纯状态逻辑：选中、分栏加载状态、块分页、块编辑草稿、冲突与限流、写入后校验。
 * 组件只消费这里的状态与方法，避免把页面状态散落进模板条件分支。
 */

export type FeishuLoadStatus
  = | 'idle'
    | 'loading'
    | 'ready'
    | 'error'
    | 'unconfigured'
    | 'unavailable'
    | 'unauthorized'
    | 'forbidden'
    | 'not-found'
    | 'rate-limited'
    | 'conflict'
    | 'timeout'

export function statusFromFailure(failure: FeishuFailure): FeishuLoadStatus {
  return failure.kind
}

export function createFeishuDocumentSession() {
  const state = reactive({
    selectedId: '',
    generation: 0,
    status: 'idle' as FeishuLoadStatus,
    failure: null as FeishuFailure | null,
    meta: null as FeishuDocumentMeta | null,
    content: null as FeishuDocumentContent | null,
    contentStatus: 'idle' as FeishuLoadStatus,
    contentFailure: null as FeishuFailure | null,
    contentLang: undefined as number | undefined,
    blocks: [] as FeishuBlockNode[],
    blocksStatus: 'idle' as FeishuLoadStatus,
    blocksFailure: null as FeishuFailure | null,
    blocksPageToken: undefined as string | undefined,
    blocksHasMore: false,
    blocksTruncated: false,
    blocksPageCount: 0,
    editingId: '',
    draft: '',
    savedText: '',
    saving: false,
    writeFailure: null as FeishuFailure | null,
    writeNotice: '',
    conflict: false,
    childParentId: '',
    childDraft: '',
    childSaving: false,
    childFailure: null as FeishuFailure | null,
  })

  const dirty = computed(() => Boolean(state.editingId) && state.draft !== state.savedText)
  const childDirty = computed(() => Boolean(state.childParentId) && state.childDraft.trim() !== '')

  /** 只有确认写能力 + 有改动 + 不在保存中 + 未处于冲突时才允许提交。 */
  const canSubmitBlock = computed(() => (
    FEISHU_WRITE_CONTRACT.blockElements === 'confirmed'
    && dirty.value
    && !state.saving
    && !state.conflict
  ))
  const canSubmitChild = computed(() => (
    FEISHU_WRITE_CONTRACT.blockChildren === 'confirmed'
    && childDirty.value
    && !state.childSaving
  ))
  const canLoadMoreBlocks = computed(() => FEISHU_BLOCK_PAGE_LIMIT > state.blocksPageCount)
  /** 有未保存的用户输入（块编辑草稿或未提交的子块文本）。 */
  const hasUnsavedEdits = computed(() => dirty.value || childDirty.value)
  /** 有写入正在进行：此时离开会丢失结果确认，必须阻止而不是确认丢弃。 */
  const isWriting = computed(() => state.saving || state.childSaving)

  function reset() {
    state.selectedId = ''
    state.generation++
    state.status = 'idle'
    state.failure = null
    state.meta = null
    state.content = null
    state.contentStatus = 'idle'
    state.contentFailure = null
    state.contentLang = undefined
    state.blocks = []
    state.blocksStatus = 'idle'
    state.blocksFailure = null
    state.blocksPageToken = undefined
    state.blocksHasMore = false
    state.blocksTruncated = false
    state.blocksPageCount = 0
    resetEdit()
  }

  function select(id: string) {
    if (id === state.selectedId) {
      return false
    }
    reset()
    state.selectedId = id
    state.status = id ? 'loading' : 'idle'
    state.contentStatus = id ? 'loading' : 'idle'
    state.blocksStatus = id ? 'loading' : 'idle'
    return true
  }

  function resetEdit() {
    state.editingId = ''
    state.draft = ''
    state.savedText = ''
    state.saving = false
    state.writeFailure = null
    state.writeNotice = ''
    state.conflict = false
    state.childParentId = ''
    state.childDraft = ''
    state.childSaving = false
    state.childFailure = null
  }

  function applyMeta(meta: FeishuDocumentMeta) {
    state.meta = meta
    state.status = 'ready'
    state.failure = null
  }

  function failMeta(failure: FeishuFailure) {
    state.failure = failure
    state.status = statusFromFailure(failure)
  }

  function applyContent(content: FeishuDocumentContent, lang: number | undefined) {
    state.content = content
    state.contentLang = lang
    state.contentStatus = 'ready'
    state.contentFailure = null
  }

  function failContent(failure: FeishuFailure) {
    state.contentFailure = failure
    state.contentStatus = statusFromFailure(failure)
  }

  /** 首页块替换整个列表；后续页用 appendBlocks 追加。 */
  function applyBlocks(nodes: FeishuBlockNode[], page: { nextPageToken?: string, hasMore: boolean }) {
    state.blocks = nodes
    state.blocksPageToken = page.nextPageToken
    state.blocksHasMore = page.hasMore
    state.blocksPageCount = 1
    state.blocksTruncated = page.hasMore && !canLoadMoreBlocks.value
    state.blocksStatus = 'ready'
    state.blocksFailure = null
  }

  function appendBlocks(nodes: FeishuBlockNode[], page: { nextPageToken?: string, hasMore: boolean }) {
    const seen = new Set(state.blocks.map(node => node.blockId))
    state.blocks.push(...nodes.filter(node => !seen.has(node.blockId)))
    state.blocksPageToken = page.nextPageToken
    state.blocksHasMore = page.hasMore
    state.blocksPageCount++
    state.blocksTruncated = page.hasMore && !canLoadMoreBlocks.value
  }

  function failBlocks(failure: FeishuFailure) {
    state.blocksFailure = failure
    state.blocksStatus = statusFromFailure(failure)
  }

  function childCountOf(parentId: string) {
    return state.blocks.find(node => node.blockId === parentId)?.childCount
  }

  function beginEditBlock(node: FeishuBlockNode) {
    if (!node.editable) {
      return false
    }
    state.editingId = node.blockId
    state.draft = node.text ?? ''
    state.savedText = node.text ?? ''
    state.writeFailure = null
    state.writeNotice = ''
    return true
  }

  function cancelEditBlock() {
    if (state.saving) {
      return false
    }
    state.editingId = ''
    state.draft = ''
    state.savedText = ''
    state.writeFailure = null
    state.writeNotice = ''
    return true
  }

  function blockTicket() {
    return {
      documentId: state.selectedId,
      blockId: state.editingId,
      generation: state.generation,
      text: state.draft,
      elements: [textElementJson(state.draft)],
      documentRevisionId: undefined as number | undefined,
    }
  }

  function setSaving(saving: boolean) {
    state.saving = saving
    if (saving) {
      state.writeFailure = null
      state.writeNotice = ''
    }
  }

  /** 提交成功后只推进「已保存文本」；草稿可能已被继续编辑。 */
  function acknowledgeBlock(ticket: ReturnType<typeof blockTicket>) {
    if (ticket.generation !== state.generation || ticket.blockId !== state.editingId) {
      return false
    }
    state.savedText = ticket.text
    return true
  }

  function failWrite(failure: FeishuFailure) {
    state.saving = false
    state.writeFailure = failure
    if (failure.kind === 'conflict') {
      state.conflict = true
    }
  }

  /**
   * 写入后校验：用写接口返回的 blocksRaw 与列表重读结果双重判定，未确认时如实提示。
   * 同时也把本地块文本推进到真实值，避免页面继续显示旧文本。
   */
  function settleElementWrite(result: FeishuBlockWriteResultRes | undefined, blockId: string, expectedText: string) {
    const verified = verifyElementWrite(result, blockId, expectedText)
    const node = state.blocks.find(item => item.blockId === blockId)
    if (node && verified !== 'missing') {
      node.text = expectedText
      node.textMissing = false
    }
    if (verified === 'verified') {
      state.writeNotice = ''
      return verified
    }
    state.writeNotice = verified === 'unverified'
      ? '已提交更新，但写接口返回的块内容与提交文本不一致，请在飞书中核对后再保存。'
      : '已提交更新，但写接口未返回该块内容，无法确认结果，请在飞书中核对。'
    return verified
  }

  function beginAddChild(parentId: string) {
    if (FEISHU_WRITE_CONTRACT.blockChildren !== 'confirmed') {
      return false
    }
    state.childParentId = parentId
    state.childDraft = ''
    state.childFailure = null
    return true
  }

  function cancelAddChild() {
    if (state.childSaving) {
      return false
    }
    state.childParentId = ''
    state.childDraft = ''
    state.childFailure = null
    return true
  }

  function setChildSaving(saving: boolean) {
    state.childSaving = saving
    if (saving) {
      state.childFailure = null
    }
  }

  function failChild(failure: FeishuFailure) {
    state.childSaving = false
    state.childFailure = failure
  }

  /** 追加子块请求体：children 为块 JSON 原文（与后端 raw 保真契约一致）。 */
  function childAppendBody() {
    return { children: [textBlockJson(state.childDraft.trim())] }
  }

  return {
    state,
    dirty,
    childDirty,
    canSubmitBlock,
    canSubmitChild,
    canLoadMoreBlocks,
    hasUnsavedEdits,
    isWriting,
    reset,
    resetEdit,
    select,
    applyMeta,
    failMeta,
    applyContent,
    failContent,
    applyBlocks,
    appendBlocks,
    failBlocks,
    childCountOf,
    beginEditBlock,
    cancelEditBlock,
    blockTicket,
    setSaving,
    acknowledgeBlock,
    failWrite,
    settleElementWrite,
    beginAddChild,
    cancelAddChild,
    setChildSaving,
    failChild,
    childAppendBody,
  }
}

/* ------------------------------------------------------------------ *
 * 全局默认额度（唯一确认可写的配置路径）
 * ------------------------------------------------------------------ */

export interface FeishuQuotaForm {
  defaultPerMinuteLimit: number
  defaultDailyLimit: number
}

export interface FeishuQuotaValidation {
  defaultPerMinuteLimit?: string
  defaultDailyLimit?: string
}

/** 后端 request 约束：两个上限都必须大于 0；另外每日上限不应小于每分钟上限。 */
export function validateQuotaForm(form: FeishuQuotaForm): FeishuQuotaValidation {
  const issues: FeishuQuotaValidation = {}
  if (!Number.isInteger(form.defaultPerMinuteLimit) || form.defaultPerMinuteLimit < 1) {
    issues.defaultPerMinuteLimit = '每分钟上限需为不小于 1 的整数'
  }
  if (!Number.isInteger(form.defaultDailyLimit) || form.defaultDailyLimit < 1) {
    issues.defaultDailyLimit = '每日上限需为不小于 1 的整数'
  }
  if (!issues.defaultPerMinuteLimit && !issues.defaultDailyLimit && form.defaultDailyLimit < form.defaultPerMinuteLimit) {
    issues.defaultDailyLimit = '每日上限不能小于每分钟上限'
  }
  return issues
}

export function createFeishuQuotaDraft() {
  const state = reactive({
    loading: false,
    saving: false,
    failure: null as FeishuFailure | null,
    loaded: false,
    config: null as FeishuQuotaConfigView | null,
    form: { defaultPerMinuteLimit: 0, defaultDailyLimit: 0 } as FeishuQuotaForm,
    saved: '',
    conflict: false,
  })

  const issues = computed(() => validateQuotaForm(state.form))
  const dirty = computed(() => state.loaded && JSON.stringify(state.form) !== state.saved)
  const submittable = computed(() => (
    FEISHU_WRITE_CONTRACT.quotaConfig === 'confirmed'
    && state.loaded
    && state.config?.resolved !== false
    && dirty.value
    && !state.saving
    && Object.keys(issues.value).length === 0
  ))

  /** 远端配置到达时不覆盖用户未保存的编辑，改标记冲突，由用户选择重新载入。 */
  function load(config: FeishuQuotaConfigView) {
    const next: FeishuQuotaForm = {
      defaultPerMinuteLimit: config.defaultPerMinuteLimit ?? 0,
      defaultDailyLimit: config.defaultDailyLimit ?? 0,
    }
    const snapshot = JSON.stringify(next)
    // 必须在标记 loaded 之前判定，否则首次载入会把初始空值误判成未保存编辑
    const wasDirty = dirty.value
    state.config = config
    state.loaded = true
    if (wasDirty) {
      if (snapshot !== state.saved) {
        state.conflict = true
      }
      return false
    }
    applyForm(next)
    return true
  }

  function applyForm(form: FeishuQuotaForm) {
    state.form.defaultPerMinuteLimit = form.defaultPerMinuteLimit
    state.form.defaultDailyLimit = form.defaultDailyLimit
    state.saved = JSON.stringify(form)
    state.conflict = false
  }

  function ticket() {
    return { snapshot: JSON.stringify(state.form), saved: state.saved }
  }

  function acknowledge(token: ReturnType<typeof ticket>) {
    state.saved = token.snapshot
    state.conflict = false
    state.saving = false
    state.failure = null
    return true
  }

  function setSaving(saving: boolean) {
    state.saving = saving
    if (saving) {
      state.failure = null
    }
  }

  function fail(failure: FeishuFailure) {
    state.saving = false
    state.failure = failure
    if (failure.kind === 'conflict') {
      state.conflict = true
    }
  }

  function discard() {
    if (!state.loaded || state.config?.resolved === false) {
      return false
    }
    applyForm({
      defaultPerMinuteLimit: state.config?.defaultPerMinuteLimit ?? 0,
      defaultDailyLimit: state.config?.defaultDailyLimit ?? 0,
    })
    state.failure = null
    return true
  }

  function body(): FeishuQuotaConfigBody {
    return {
      defaultPerMinuteLimit: state.form.defaultPerMinuteLimit,
      defaultDailyLimit: state.form.defaultDailyLimit,
    }
  }

  return { state, issues, dirty, submittable, load, ticket, acknowledge, setSaving, fail, discard, body }
}
