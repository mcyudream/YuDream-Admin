<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import FaIcon from '../basic/icon/index.vue'

const props = withDefaults(defineProps<{
  title?: string
  width?: number
  height?: number
  minWidth?: number
  minHeight?: number
  resizable?: boolean
  maximizable?: boolean
  fullscreenable?: boolean
  /** 最大化/全屏按钮是否改为对外抛出 expand（由父级跳转全屏页），而非就地放大 */
  expandOnly?: boolean
  /** 传入后按账号/场景持久化窗口位置与尺寸，例如 yd.chatWindow.ai.123 */
  storageKey?: string
  /** 首次出现时贴靠的横向边缘 */
  initialSide?: 'left' | 'right'
  /** 首次出现时距视口边缘的距离 */
  initialOffset?: number
}>(), {
  title: 'AI 助手',
  width: 480,
  height: 640,
  minWidth: 360,
  minHeight: 420,
  resizable: true,
  maximizable: true,
  fullscreenable: true,
  expandOnly: false,
  initialSide: 'right',
  initialOffset: 24,
})

const emits = defineEmits<{
  close: []
  /** 请求在全屏页打开（父级负责路由跳转） */
  expand: []
  /** 位置或尺寸变化后抛出当前几何信息，便于调用方联动 */
  geometry: [{ x: number, y: number, width: number, height: number }]
}>()

const rootEl = ref<HTMLElement | null>(null)
const state = reactive({
  x: 24,
  y: 24,
  width: props.width,
  height: props.height,
  maximized: false,
  fullscreen: false,
})

const dragState = reactive({ active: false, startX: 0, startY: 0, originX: 0, originY: 0 })
const resizeState = reactive({ active: false, startX: 0, startY: 0, originWidth: 0, originHeight: 0 })

/** 视口边界：标题栏必须留在视口内，尺寸不超过视口。 */
function limits() {
  const gutter = 8
  return {
    minX: gutter - Math.min(props.minWidth, window.innerWidth) + 140,
    maxX: Math.max(gutter, window.innerWidth - 140),
    minY: gutter,
    maxY: Math.max(gutter, window.innerHeight - 48),
    maxWidth: Math.max(props.minWidth, window.innerWidth - gutter * 2),
    maxHeight: Math.max(props.minHeight, window.innerHeight - gutter * 2),
  }
}

function clampX(value: number) {
  const limit = limits()
  return Math.min(Math.max(value, limit.minX), limit.maxX)
}

function clampY(value: number) {
  const limit = limits()
  return Math.min(Math.max(value, limit.minY), limit.maxY)
}

function defaultGeometry() {
  const limit = limits()
  const width = Math.min(props.width, limit.maxWidth)
  const height = Math.min(props.height, limit.maxHeight)
  const offset = Math.max(0, props.initialOffset)
  const x = props.initialSide === 'left' ? offset : Math.max(offset, window.innerWidth - width - offset)
  return { x: clampX(x), y: clampY(offset), width, height }
}

function persist() {
  if (!props.storageKey) {
    return
  }
  try {
    localStorage.setItem(props.storageKey, JSON.stringify({
      x: state.x, y: state.y, width: state.width, height: state.height, maximized: state.maximized,
    }))
  }
  catch {
    /* 存储不可用时仅保留本次会话几何 */
  }
}

function applyStored() {
  const fallback = defaultGeometry()
  let stored: Partial<{ x: number, y: number, width: number, height: number, maximized: boolean }> | undefined
  if (props.storageKey) {
    try {
      stored = JSON.parse(localStorage.getItem(props.storageKey) || 'null') || undefined
    }
    catch {
      stored = undefined
    }
  }
  const limit = limits()
  state.width = typeof stored?.width === 'number' ? Math.min(Math.max(stored.width, props.minWidth), limit.maxWidth) : fallback.width
  state.height = typeof stored?.height === 'number' ? Math.min(Math.max(stored.height, props.minHeight), limit.maxHeight) : fallback.height
  state.x = typeof stored?.x === 'number' ? clampX(stored.x) : fallback.x
  state.y = typeof stored?.y === 'number' ? clampY(stored.y) : fallback.y
  state.maximized = stored?.maximized === true
}

function reflow() {
  const limit = limits()
  state.width = Math.min(state.width, limit.maxWidth)
  state.height = Math.min(state.height, limit.maxHeight)
  state.x = clampX(state.x)
  state.y = clampY(state.y)
}

const style = computed(() => {
  if (state.maximized || state.fullscreen) {
    return {
      left: '0',
      top: '0',
      width: '100%',
      height: '100%',
    }
  }
  return {
    left: `${state.x}px`,
    top: `${state.y}px`,
    width: `${state.width}px`,
    height: `${state.height}px`,
  }
})

function startDrag(event: PointerEvent) {
  if (state.maximized || state.fullscreen || (event.target as HTMLElement).closest('button')) {
    return
  }
  dragState.active = true
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.originX = state.x
  dragState.originY = state.y
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', stopDrag)
}

function onDrag(event: PointerEvent) {
  if (!dragState.active) {
    return
  }
  state.x = clampX(dragState.originX + event.clientX - dragState.startX)
  state.y = clampY(dragState.originY + event.clientY - dragState.startY)
}

function stopDrag() {
  if (!dragState.active) {
    return
  }
  dragState.active = false
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', stopDrag)
  persist()
  emits('geometry', { x: state.x, y: state.y, width: state.width, height: state.height })
}

function startResize(event: PointerEvent) {
  if (!props.resizable || state.maximized || state.fullscreen) {
    return
  }
  resizeState.active = true
  resizeState.startX = event.clientX
  resizeState.startY = event.clientY
  resizeState.originWidth = state.width
  resizeState.originHeight = state.height
  window.addEventListener('pointermove', onResize)
  window.addEventListener('pointerup', stopResize)
}

function onResize(event: PointerEvent) {
  if (!resizeState.active) {
    return
  }
  const limit = limits()
  state.width = Math.min(Math.max(props.minWidth, resizeState.originWidth + event.clientX - resizeState.startX), limit.maxWidth)
  state.height = Math.min(Math.max(props.minHeight, resizeState.originHeight + event.clientY - resizeState.startY), limit.maxHeight)
}

function stopResize() {
  if (!resizeState.active) {
    return
  }
  resizeState.active = false
  window.removeEventListener('pointermove', onResize)
  window.removeEventListener('pointerup', stopResize)
  persist()
  emits('geometry', { x: state.x, y: state.y, width: state.width, height: state.height })
}

function toggleMaximize() {
  if (props.expandOnly) {
    emits('expand')
    return
  }
  if (state.fullscreen) {
    return
  }
  state.maximized = !state.maximized
  persist()
}

async function toggleFullscreen() {
  if (props.expandOnly) {
    emits('expand')
    return
  }
  if (!rootEl.value || !document.fullscreenEnabled) {
    return
  }
  if (!document.fullscreenElement) {
    state.fullscreen = true
    await rootEl.value.requestFullscreen?.()
  }
  else {
    await document.exitFullscreen()
  }
}

function onFullscreenChange() {
  state.fullscreen = Boolean(document.fullscreenElement)
}

watch(() => document.fullscreenElement, onFullscreenChange)
watch(() => [props.width, props.height], () => {
  if (props.storageKey) {
    return
  }
  state.width = props.width
  state.height = props.height
  reflow()
})
onMounted(() => {
  applyStored()
  window.addEventListener('resize', reflow)
})
onBeforeUnmount(() => {
  stopDrag()
  stopResize()
  window.removeEventListener('resize', reflow)
})
defineExpose({
  /** 恢复到首次出现的默认几何并清理持久化记录 */
  resetGeometry: () => {
    Object.assign(state, defaultGeometry())
    state.maximized = false
    persist()
  },
})
</script>

<template>
  <section ref="rootEl" class="yd-chat-window" :class="{ 'is-fullscreen': state.fullscreen }" :style="style" aria-label="AI 聊天窗口">
    <header class="yd-chat-window__head" @pointerdown="startDrag">
      <span class="yd-chat-window__title"><FaIcon name="morph:message-circle" />{{ title }}</span>
      <span class="yd-chat-window__tools">
        <slot name="head-actions" />
        <button v-if="maximizable" type="button" title="最大化" @click="toggleMaximize">
          <FaIcon :name="state.maximized ? 'morph:fold-horizontal' : 'morph:unfold-horizontal'" />
        </button>
        <button v-if="fullscreenable" type="button" title="全屏" @click="toggleFullscreen">
          <FaIcon :name="state.fullscreen ? 'morph:minimize' : 'morph:maximize'" />
        </button>
        <button type="button" title="关闭" @click="emits('close')">
          <FaIcon name="morph:x" />
        </button>
      </span>
    </header>
    <div class="yd-chat-window__body">
      <slot />
    </div>
    <button v-if="resizable && !state.maximized && !state.fullscreen" type="button" class="yd-chat-window__resize" @pointerdown="startResize">
      <FaIcon name="morph:corner-down-right" />
    </button>
  </section>
</template>

<style scoped>
/* 浮窗内的工作台与窗口融为一体：去掉卡片边框与圆角，避免「卡片套卡片」 */
.yd-chat-window :deep(.yd-agent-workbench) {
  border: 0;
  border-radius: 0;
  box-shadow: none;
}

/* 浮窗在页面框架（顶栏/侧栏约 1010）与悬浮球（1500）之上，低于其内部弹出的下拉、对话框与提示（约 2000+） */
.yd-chat-window {
  position: fixed;
  z-index: 1600;
  display: flex;
  min-width: 360px;
  min-height: 420px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 14px;
  background: var(--color-bg-1);
  box-shadow: 0 24px 70px rgb(15 23 42 / 24%);
  flex-direction: column;
}

.yd-chat-window.is-fullscreen {
  border: 0;
  border-radius: 0;
}

.yd-chat-window__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-fill-1);
  cursor: move;
  user-select: none;
}

.yd-chat-window__title {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: var(--color-text-1);
  font-size: 14px;
  font-weight: 600;
}

.yd-chat-window__title :deep(svg) {
  color: oklch(var(--primary));
}

.yd-chat-window__tools {
  display: flex;
  gap: 4px;
}

.yd-chat-window__tools button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  place-items: center;
}

.yd-chat-window__tools button:hover {
  background: var(--color-bg-1);
  color: oklch(var(--primary));
}

.yd-chat-window__body {
  display: flex;
  min-height: 0;
  flex: 1;
  flex-direction: column;
}

.yd-chat-window__resize {
  position: absolute;
  right: 0;
  bottom: 0;
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--color-text-3);
  cursor: nwse-resize;
  place-items: center;
}

.yd-chat-window__resize :deep(svg) {
  transform: rotate(90deg);
}
</style>
