import type { CSSProperties } from 'vue'

interface PanelPersisted {
  left: number
  top: number
  width: number
  height: number
}

const STORAGE_KEY = 'pluginDevtoolsPanel'
const EDGE = 8
const MIN_WIDTH = 680
const MIN_HEIGHT = 420
const DEFAULT_WIDTH = 960
const DEFAULT_HEIGHT = 600

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), Math.max(min, max))
}

/**
 * 开发者工具浮窗：非模态、可拖拽（标题栏）、右下角可缩放。
 * 位置与尺寸持久化到 localStorage，窗口缩放时自动收回视口内。
 */
export function useDevtoolsPanel() {
  const state = reactive({
    left: 0,
    top: 0,
    width: DEFAULT_WIDTH,
    height: DEFAULT_HEIGHT,
  })

  const drag = reactive({
    pointerId: -1,
    active: false,
    startX: 0,
    startY: 0,
    startLeft: 0,
    startTop: 0,
  })

  const resize = reactive({
    pointerId: -1,
    active: false,
    startX: 0,
    startY: 0,
    startWidth: 0,
    startHeight: 0,
  })

  const panelStyle = computed<CSSProperties>(() => ({
    left: `${state.left}px`,
    top: `${state.top}px`,
    width: `${state.width}px`,
    height: `${state.height}px`,
    transition: drag.active || resize.active ? 'none' : undefined,
  }))

  function clampAll() {
    state.width = clamp(state.width, MIN_WIDTH, window.innerWidth - EDGE * 2)
    state.height = clamp(state.height, MIN_HEIGHT, window.innerHeight - EDGE * 2)
    state.left = clamp(state.left, EDGE, window.innerWidth - state.width - EDGE)
    state.top = clamp(state.top, EDGE, window.innerHeight - state.height - EDGE)
  }

  function hydrate() {
    try {
      const raw = localStorage.getItem(STORAGE_KEY)
      if (raw) {
        const saved = JSON.parse(raw) as Partial<PanelPersisted>
        if (typeof saved.left === 'number' && Number.isFinite(saved.left)) {
          state.left = saved.left
        }
        if (typeof saved.top === 'number' && Number.isFinite(saved.top)) {
          state.top = saved.top
        }
        if (typeof saved.width === 'number' && Number.isFinite(saved.width)) {
          state.width = saved.width
        }
        if (typeof saved.height === 'number' && Number.isFinite(saved.height)) {
          state.height = saved.height
        }
        clampAll()
        return
      }
    }
    catch {
      // 损坏的持久化数据按默认位置处理
    }
    state.width = Math.min(DEFAULT_WIDTH, window.innerWidth - EDGE * 2)
    state.height = Math.min(DEFAULT_HEIGHT, window.innerHeight - EDGE * 2)
    state.left = (window.innerWidth - state.width) / 2
    state.top = Math.max(EDGE, (window.innerHeight - state.height) / 2 * 0.7)
    clampAll()
  }

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({
      left: state.left,
      top: state.top,
      width: state.width,
      height: state.height,
    } satisfies PanelPersisted))
  }

  /** 标题栏拖拽；点击栏内按钮不触发 */
  function handleHeaderPointerDown(event: PointerEvent) {
    if (!event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) {
      return
    }
    if (event.target instanceof HTMLElement && event.target.closest('button, a, input, textarea')) {
      return
    }
    const target = event.currentTarget
    if (!(target instanceof HTMLElement)) {
      return
    }
    drag.pointerId = event.pointerId
    drag.active = true
    drag.startX = event.clientX
    drag.startY = event.clientY
    drag.startLeft = state.left
    drag.startTop = state.top
    target.setPointerCapture?.(event.pointerId)
  }

  function handleHeaderPointerMove(event: PointerEvent) {
    if (!drag.active || drag.pointerId !== event.pointerId) {
      return
    }
    state.left = drag.startLeft + event.clientX - drag.startX
    state.top = drag.startTop + event.clientY - drag.startY
    state.left = clamp(state.left, EDGE, window.innerWidth - state.width - EDGE)
    state.top = clamp(state.top, EDGE, window.innerHeight - state.height - EDGE)
  }

  function finishDrag(target?: EventTarget | null) {
    const pointerId = drag.pointerId
    drag.pointerId = -1
    if (drag.active) {
      drag.active = false
      persist()
    }
    if (target instanceof HTMLElement && pointerId >= 0 && target.hasPointerCapture?.(pointerId)) {
      target.releasePointerCapture(pointerId)
    }
  }

  function handleHeaderPointerUp(event: PointerEvent) {
    if (drag.pointerId !== event.pointerId) {
      return
    }
    finishDrag(event.currentTarget)
  }

  function handleResizePointerDown(event: PointerEvent) {
    if (!event.isPrimary || (event.pointerType === 'mouse' && event.button !== 0)) {
      return
    }
    const target = event.currentTarget
    if (!(target instanceof HTMLElement)) {
      return
    }
    event.stopPropagation()
    resize.pointerId = event.pointerId
    resize.active = true
    resize.startX = event.clientX
    resize.startY = event.clientY
    resize.startWidth = state.width
    resize.startHeight = state.height
    target.setPointerCapture?.(event.pointerId)
  }

  function handleResizePointerMove(event: PointerEvent) {
    if (!resize.active || resize.pointerId !== event.pointerId) {
      return
    }
    state.width = clamp(resize.startWidth + event.clientX - resize.startX, MIN_WIDTH, window.innerWidth - EDGE * 2)
    state.height = clamp(resize.startHeight + event.clientY - resize.startY, MIN_HEIGHT, window.innerHeight - EDGE * 2)
  }

  function finishResize(target?: EventTarget | null) {
    const pointerId = resize.pointerId
    resize.pointerId = -1
    if (resize.active) {
      resize.active = false
      persist()
    }
    if (target instanceof HTMLElement && pointerId >= 0 && target.hasPointerCapture?.(pointerId)) {
      target.releasePointerCapture(pointerId)
    }
  }

  function handleResizePointerUp(event: PointerEvent) {
    if (resize.pointerId !== event.pointerId) {
      return
    }
    finishResize(event.currentTarget)
  }

  /** 设置页「重置浮窗位置」 */
  function reset() {
    localStorage.removeItem(STORAGE_KEY)
    hydrate()
  }

  function handleViewportResize() {
    clampAll()
  }

  onMounted(() => {
    hydrate()
    window.addEventListener('resize', handleViewportResize, { passive: true })
  })

  onUnmounted(() => {
    window.removeEventListener('resize', handleViewportResize)
  })

  return {
    panelState: state,
    panelStyle,
    panelDragging: computed(() => drag.active),
    panelResizing: computed(() => resize.active),
    handleHeaderPointerDown,
    handleHeaderPointerMove,
    handleHeaderPointerUp,
    handleResizePointerDown,
    handleResizePointerMove,
    handleResizePointerUp,
    resetPanel: reset,
  }
}
