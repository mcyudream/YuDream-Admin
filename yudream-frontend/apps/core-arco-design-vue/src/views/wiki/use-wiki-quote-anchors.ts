import type { Ref } from 'vue'
import type { WikiCommentAnchorState } from './wiki-comment-anchor'
import type { WikiCommentAnchorPayload, WikiPublicComment } from './wiki-public-api'
import type { WikiQuoteAnchor, WikiQuoteAnchorScope } from './wiki-quote-anchor'
import type { WikiQuoteSelectionRect, WikiQuoteSelectionSnapshot } from './wiki-quote-dom'
import { onBeforeUnmount, ref, watch } from 'vue'
import {
  resolveWikiCommentAnchorState,
  wikiQuoteAnchorFromResponse,
  wikiQuoteAnchorPayload,
  wikiQuoteAnchorScopeFromResponse,
} from './wiki-comment-anchor'
import {
  applyWikiQuoteHighlights,
  captureWikiQuoteSelection,
  clearWikiQuoteHighlights,
  resolveWikiPreviewRoot,
  scrollWikiQuoteHighlight,
  setWikiQuoteHighlightActive,
  wikiQuoteSelectionErrorMessage,
} from './wiki-quote-dom'

/**
 * 公开文章的选区引用交互：捕获 `.md-editor-preview` 内的真实选区、维护浮出的「评论」入口、
 * 并把已发布评论的锚点重新定位成阅读层高亮。
 *
 * 约束：
 * - 只捕获当前已发布正文预览内的单个文本块/表格单元格选区，跨块与预览外一律拒绝；
 * - 选区快照在 pointerup/键盘选区时冻结（含锚点与实时 Range），按钮点击不再依赖浏览器选区；
 * - 高亮只包唯一命中的引用，歧义/失锚绝不高亮；每次重渲染/换页/换节点后清除并按当前 DOM 重建；
 * - 不修改任何持久化 Markdown/HTML，装饰 span 只存在于阅读层；
 * - 不为正文子树另挂 MutationObserver：预览重渲染统一由 TOC 观察者的 `onRendered` 回调驱动
 *   `reapply`（applyWikiQuoteHighlights 自带签名跳过，没有装饰变形就不会回写自激）。
 */

/** 浮出入口的定位常量：位于选区上方，上方空间不足时翻到下方 */
const ENTRY_HEIGHT = 32
const ENTRY_GAP = 8
const ENTRY_MARGIN = 16
/** 浮出入口容器的稳定标记（teleport 到 body，pointerdown 时用它与正文区分） */
export const WIKI_QUOTE_ENTRY_SELECTOR = '[data-wiki-quote-entry]'
const NAVIGATION_KEYS = new Set(['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown', 'Home', 'End'])
const NOTICE_TIMEOUT = 4200

export interface WikiQuoteFloatingPosition {
  top: number
  left: number
}

/** 待发表的选区引用：冻结的锚点、展示文本与可直接提交的请求载荷 */
export interface WikiPendingQuote {
  anchor: WikiQuoteAnchor
  text: string
  payload: WikiCommentAnchorPayload
  position: WikiQuoteFloatingPosition | null
}

/**
 * 高亮目标：整条已发布评论。锚点是扁平线协议字段，版本身份与 anchorState 是评论的兄弟字段，
 * 因此这里保留评论本体，不把兄弟字段塞进 anchor。
 */
export interface WikiQuoteAnchorTarget {
  commentId: string
  comment: WikiPublicComment
}

export interface UseWikiQuoteAnchorsOptions {
  /** 文章正文容器（`.wiki-doc__content`），内部再解析 `.md-editor-preview` */
  containerRef: Ref<HTMLElement | null>
  /** 当前页面标识（已发布节点 ID / 原文档 ID）：变化即清空待引用与高亮 */
  activeKey: Ref<string>
  /** 只有已发布页面阅读态才启用选区委引与高亮 */
  enabled: Ref<boolean>
  /**
   * 当前已发布版本身份（可选）：仅在能拿到同一个「已定版」身份时比较，缺省则不比较——
   * 服务端的 anchorState 已按 contentHash 判定改版，客户端拿不到当前版本哈希时不臆断。
   */
  currentScope?: Ref<WikiQuoteAnchorScope | null>
  /** 已发布评论的锚点目标（仅根评论；回复继承根锚点，不重复高亮） */
  targets: Ref<WikiQuoteAnchorTarget[]>
  /** 点击正文高亮时打开对应线程 */
  onOpenComment: (commentId: string) => void
}

export interface WikiQuoteAnchorsController {
  /** 评论 ID → 客户端锚点状态（本地歧义 + 服务端投影；无锚点评论不出现在这里） */
  statuses: Ref<Record<string, WikiCommentAnchorState>>
  pendingQuote: Ref<WikiPendingQuote | null>
  /** 跨块/过短选区的明确提示；空串表示无提示 */
  selectionNotice: Ref<string>
  dismissQuote: () => void
  /** 滚动到唯一命中的引用；未高亮（歧义/失锚/未渲染）返回 false，不滚动到错误文字 */
  locate: (commentId: string) => boolean
  /** 标记聚焦引用 */
  activate: (commentId: string) => void
  /** 立即按当前 DOM 重建高亮（重渲染后由 TOC 观察者回调） */
  reapply: () => void
}

/** 浮出入口位置（纯函数，便于测试）：横向以选区中心对齐，纵向优先在上方。 */
export function wikiQuoteEntryPosition(rect: WikiQuoteSelectionRect, viewport: { width: number, height: number }): WikiQuoteFloatingPosition {
  const above = rect.top - ENTRY_GAP - ENTRY_HEIGHT
  const top = above >= ENTRY_GAP ? above : rect.top + rect.height + ENTRY_GAP
  const maxLeft = Math.max(viewport.width - ENTRY_MARGIN, ENTRY_MARGIN)
  return {
    top: Math.max(top, ENTRY_GAP),
    left: Math.min(Math.max(rect.left + rect.width / 2, ENTRY_MARGIN), maxLeft),
  }
}

export function useWikiQuoteAnchors(options: UseWikiQuoteAnchorsOptions): WikiQuoteAnchorsController {
  const statuses = ref<Record<string, WikiCommentAnchorState>>({})
  const pendingQuote = ref<WikiPendingQuote | null>(null)
  const selectionNotice = ref('')

  let liveRange: Range | null = null
  let noticeTimer: ReturnType<typeof setTimeout> | undefined
  let repositionFrame = 0
  let applyGeneration = 0

  function previewRoot(): HTMLElement | null {
    return resolveWikiPreviewRoot(options.containerRef.value)
  }

  function showNotice(message: string) {
    if (noticeTimer) {
      clearTimeout(noticeTimer)
      noticeTimer = undefined
    }
    selectionNotice.value = message
    if (message) {
      noticeTimer = setTimeout(() => {
        noticeTimer = undefined
        selectionNotice.value = ''
      }, NOTICE_TIMEOUT)
    }
  }

  function dismissQuote() {
    pendingQuote.value = null
    liveRange = null
  }

  // —— 高亮重建：解析 → 签名比对 → 需要时才写 DOM ——

  function applyHighlights() {
    const root = previewRoot()
    const generation = ++applyGeneration
    if (!root || !options.enabled.value) {
      statuses.value = {}
      return
    }
    const targets = options.targets.value
    // 阅读层定位只认纯算法模型：把扁平线协议锚点折算成块提示与起止偏移，
    // 否则表格行列/块序号会静默丢失，同一段文字在多个单元格时退化为歧义。
    // 服务端状态与版本身份是评论的兄弟字段，投影时再用评论本体读取，两侧不混用。
    const flatTargets = targets.map(target => ({
      commentId: target.commentId,
      anchor: wikiQuoteAnchorFromResponse(target.comment.anchor),
    }))
    const outcome = applyWikiQuoteHighlights(root, flatTargets, {
      onClick: (commentId, event) => {
        event.preventDefault()
        options.onOpenComment(commentId)
      },
      scopeOf: (commentId) => {
        const target = targets.find(candidate => candidate.commentId === commentId)
        return {
          captured: wikiQuoteAnchorScopeFromResponse(target?.comment),
          current: options.currentScope?.value ?? undefined,
        }
      },
    })
    if (generation !== applyGeneration) {
      return
    }
    const next: Record<string, WikiCommentAnchorState> = {}
    for (const target of targets) {
      const state = resolveWikiCommentAnchorState(target.comment, outcome.classifications[target.commentId] ?? null)
      // 无锚点评论没有引用态，不进 statuses（不造 PAGE 之类投影）
      if (state) {
        next[target.commentId] = state
      }
    }
    statuses.value = next
  }

  // —— 选区捕获与浮出入口 ——

  function refreshPendingPosition() {
    if (!pendingQuote.value || !liveRange) {
      return
    }
    const container = liveRange.startContainer
    if (!container.isConnected) {
      dismissQuote()
      return
    }
    const rect = liveRange.getBoundingClientRect()
    if (!rect.width && !rect.height) {
      pendingQuote.value = { ...pendingQuote.value, position: null }
      return
    }
    pendingQuote.value = {
      ...pendingQuote.value,
      position: wikiQuoteEntryPosition(
        { top: rect.top, left: rect.left, width: rect.width, height: rect.height },
        { width: window.innerWidth, height: window.innerHeight },
      ),
    }
  }

  function scheduleReposition() {
    if (repositionFrame || !pendingQuote.value) {
      return
    }
    repositionFrame = window.requestAnimationFrame(() => {
      repositionFrame = 0
      refreshPendingPosition()
    })
  }

  function freezeSelection(snapshot: WikiQuoteSelectionSnapshot) {
    const payload = wikiQuoteAnchorPayload(snapshot.anchor) ?? {
      exact: snapshot.anchor.exact,
      prefix: snapshot.anchor.prefix,
      suffix: snapshot.anchor.suffix,
    }
    liveRange = snapshot.range
    pendingQuote.value = { anchor: snapshot.anchor, text: snapshot.anchor.exact, payload, position: null }
    showNotice('')
    refreshPendingPosition()
  }

  function captureSelection() {
    if (!options.enabled.value) {
      return
    }
    const result = captureWikiQuoteSelection(previewRoot())
    if (result.snapshot) {
      freezeSelection(result.snapshot)
      return
    }
    const message = wikiQuoteSelectionErrorMessage(result.error)
    if (message) {
      // 明确的操作失误（跨块/过短）：给出提示并丢弃上一次待引用，不静默截断
      dismissQuote()
      showNotice(message)
    }
    // 折叠选区或在正文外点击：保留已冻结的引用，等用户继续操作
  }

  function onPointerDown(event: PointerEvent) {
    const target = event.target as Element | null
    if (target?.closest?.(WIKI_QUOTE_ENTRY_SELECTOR)) {
      // 按下浮出入口不收起浏览器选区（锚点在 pointerup 时已冻结，这里只是视觉一致性）
      event.preventDefault()
    }
  }

  function onPointerUp(event: PointerEvent) {
    const target = event.target as Element | null
    if (target?.closest?.(WIKI_QUOTE_ENTRY_SELECTOR)) {
      return
    }
    captureSelection()
  }

  function onKeyUp(event: KeyboardEvent) {
    if (event.shiftKey || NAVIGATION_KEYS.has(event.key)) {
      captureSelection()
    }
  }

  // —— 对外动作 ——

  function locate(commentId: string): boolean {
    const root = previewRoot()
    const found = root ? scrollWikiQuoteHighlight(root, commentId) : false
    const state = statuses.value[commentId]
    if (!found && (state === 'ORPHANED' || state === 'AMBIGUOUS')) {
      showNotice('该评论引用的原文在当前发布版本中无法唯一定位，只保留评论线程。')
    }
    return found
  }

  function activate(commentId: string) {
    setWikiQuoteHighlightActive(previewRoot(), commentId)
  }

  // —— 生命周期 ——

  // 容器（含 SiteChrome 重建后的新节点）变化即按当前 DOM 重建高亮；
  // 预览子树的重渲染由 TOC 观察者 debounce 后回调 reapply，这里不再单挂第二个观察者
  watch(() => options.containerRef.value, () => applyHighlights(), { immediate: true })

  watch([() => options.activeKey.value, () => options.enabled.value], () => {
    dismissQuote()
    showNotice('')
    statuses.value = {}
    applyHighlights()
  })

  watch(() => options.targets.value, () => applyHighlights())

  onBeforeUnmount(() => {
    applyGeneration += 1
    if (noticeTimer) {
      clearTimeout(noticeTimer)
    }
    if (repositionFrame) {
      window.cancelAnimationFrame(repositionFrame)
    }
    clearWikiQuoteHighlights(previewRoot())
    window.removeEventListener('pointerdown', onPointerDown)
    window.removeEventListener('pointerup', onPointerUp)
    window.removeEventListener('keyup', onKeyUp)
    window.removeEventListener('scroll', scheduleReposition)
    window.removeEventListener('resize', scheduleReposition)
  })

  window.addEventListener('pointerdown', onPointerDown, true)
  window.addEventListener('pointerup', onPointerUp, true)
  window.addEventListener('keyup', onKeyUp, true)
  window.addEventListener('scroll', scheduleReposition, { passive: true })
  window.addEventListener('resize', scheduleReposition, { passive: true })

  return {
    statuses,
    pendingQuote,
    selectionNotice,
    dismissQuote,
    locate,
    activate,
    reapply: applyHighlights,
  }
}
