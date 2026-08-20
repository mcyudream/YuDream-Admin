import type { CSSProperties, Ref } from 'vue'

type FabSide = 'left' | 'right'

interface FabPersisted {
  side: FabSide
  topRatio: number
  docked: boolean
}

const STORAGE_KEY = 'pluginDevtoolsFab'
const EDGE_OFFSET = 4
/** 松手时距边缘不超过该值则收成边缘条 */
const DOCK_ZONE = 24
const DRAG_THRESHOLD = 8
const DEFAULT_SIZE = 40
const DEFAULT_TOP_RATIO = 0.75

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

/**
 * 开发者工具悬浮按钮：可拖拽换位、松手吸附最近边缘、贴边收成半隐边缘条。
 * 位置持久化为 {side, topRatio, docked}（比例而非绝对像素，适配窗口缩放）。
 */
export function useDevtoolsFab(fabRef: Ref<HTMLElement | null>) {
  const state = reactive({
    initialized: false,
    side: 'right' as FabSide,
    topRatio: DEFAULT_TOP_RATIO,
    docked: false,
    left: 0,
    top: 0,
    width: DEFAULT_SIZE,
    height: DEFAULT_SIZE,
  })

  const drag = reactive({
    pointerId: -1,
    active: false,
    moved: false,
    suppressClick: false,
    startX: 0,
    startY: 0,
    startLeft: 0,
    startTop: 0,
  })

  const fabStyle = computed<CSSProperties>(() => ({
    left: `${state.left}px`,
    top: `${state.top}px`,
    opacity: state.initialized ? 1 : 0,
    transition: drag.active ? 'none' : 'left 200ms ease, top 200ms ease, opacity 120ms ease',
    willChange: 'left, top',
  }))

  function hydrate() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (!raw) {
        return
      }
      const saved = JSON.parse(raw) as Partial<FabPersisted>
      if (saved.side === 'left' || saved.side === 'right') {
        state.side = saved.side
      }
      if (typeof saved.topRatio === 'number' && Number.isFinite(saved.topRatio)) {
        state.topRatio = clamp(saved.topRatio, 0, 1)
      }
      state.docked = saved.docked === true
    }
    catch {
      // 损坏的持久化数据按默认位置处理
    }
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      side: state.side,
      topRatio: state.topRatio,
      docked: state.docked,
    } satisfies FabPersisted))
  }

  function syncMetrics() {
    const rect = fabRef.value?.getBoundingClientRect()
    if (!rect) {
      return
    }
    state.width = rect.width || DEFAULT_SIZE
    state.height = rect.height || DEFAULT_SIZE
  }

  function bounds() {
    return {
      minLeft: EDGE_OFFSET,
      maxLeft: Math.max(EDGE_OFFSET, window.innerWidth - state.width - EDGE_OFFSET),
      minTop: EDGE_OFFSET,
      maxTop: Math.max(EDGE_OFFSET, window.innerHeight - state.height - EDGE_OFFSET),
    }
  }

  /** 按 side + topRatio 锚定贴边位置（非拖拽态的渲染位置） */
  function applyAnchoredPosition() {
    const b = bounds()
    state.top = clamp(state.topRatio * (window.innerHeight - state.height), b.minTop, b.maxTop)
    state.left = state.side === 'left' ? b.minLeft : b.maxLeft
  }

  function setFreePosition(left: number, top: number) {
    const b = bounds()
    state.left = clamp(left, b.minLeft, b.maxLeft)
    state.top = clamp(top, b.minTop, b.maxTop)
  }

  function handlePointerDown(event: PointerEvent) {
    if (state.docked || !event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) {
      return
    }
    const target = event.currentTarget
    if (!(target instanceof HTMLElement)) {
      return
    }
    syncMetrics()
    drag.pointerId = event.pointerId
    drag.active = true
    drag.moved = false
    drag.startX = event.clientX
    drag.startY = event.clientY
    drag.startLeft = state.left
    drag.startTop = state.top
    target.setPointerCapture?.(event.pointerId)
  }

  function handlePointerMove(event: PointerEvent) {
    if (!drag.active || drag.pointerId !== event.pointerId) {
      return
    }
    const deltaX = event.clientX - drag.startX
    const deltaY = event.clientY - drag.startY
    if (!drag.moved && Math.hypot(deltaX, deltaY) < DRAG_THRESHOLD) {
      return
    }
    drag.moved = true
    setFreePosition(drag.startLeft + deltaX, drag.startTop + deltaY)
  }

  function finishDrag(target?: EventTarget | null) {
    const pointerId = drag.pointerId
    const shouldSuppressClick = drag.moved

    drag.pointerId = -1
    drag.active = false
    drag.moved = false

    if (target instanceof HTMLElement && pointerId >= 0 && target.hasPointerCapture?.(pointerId)) {
      target.releasePointerCapture(pointerId)
    }

    if (shouldSuppressClick) {
      // 吸附到最近一侧；松手点贴边（≤24px）则收成边缘条
      state.side = state.left + state.width / 2 <= window.innerWidth / 2 ? 'left' : 'right'
      const distanceToEdge = state.side === 'left' ? state.left : window.innerWidth - state.left - state.width
      state.docked = distanceToEdge <= DOCK_ZONE
      state.topRatio = clamp(state.top / Math.max(1, window.innerHeight - state.height), 0, 1)
      applyAnchoredPosition()
      persist()
    }

    drag.suppressClick = shouldSuppressClick
  }

  function handlePointerUp(event: PointerEvent) {
    if (drag.pointerId !== event.pointerId) {
      return
    }
    finishDrag(event.currentTarget)
  }

  function handlePointerCancel(event: PointerEvent) {
    if (drag.pointerId !== event.pointerId) {
      return
    }
    finishDrag(event.currentTarget)
  }

  function handleLostPointerCapture(event: PointerEvent) {
    if (drag.active && drag.pointerId === event.pointerId) {
      finishDrag(event.currentTarget)
    }
  }

  /** 完整按钮点击：打开抽屉；拖拽后的点击被抑制 */
  function handleActivate(open: () => void) {
    if (drag.suppressClick) {
      drag.suppressClick = false
      return
    }
    open()
  }

  /** 边缘条点击：展开回完整按钮 */
  async function undock() {
    if (drag.suppressClick) {
      drag.suppressClick = false
      return
    }
    state.docked = false
    await nextTick()
    syncMetrics()
    applyAnchoredPosition()
    persist()
  }

  /** 设置页「重置悬浮按钮位置」 */
  async function reset() {
    localStorage.removeItem(STORAGE_KEY)
    state.side = 'right'
    state.topRatio = DEFAULT_TOP_RATIO
    state.docked = false
    await nextTick()
    syncMetrics()
    applyAnchoredPosition()
  }

  function handleViewportResize() {
    if (!state.initialized) {
      return
    }
    syncMetrics()
    applyAnchoredPosition()
  }

  onMounted(async () => {
    hydrate()
    await nextTick()
    syncMetrics()
    applyAnchoredPosition()
    state.initialized = true
    window.addEventListener('resize', handleViewportResize, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleViewportResize)
  })

  return {
    fabState: state,
    fabDragging: computed(() => drag.active),
    fabStyle,
    handlePointerDown,
    handlePointerMove,
    handlePointerUp,
    handlePointerCancel,
    handleLostPointerCapture,
    handleActivate,
    undock,
    resetFab: reset,
  }
}
