<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
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
})

const emits = defineEmits<{
  close: []
  /** 请求在全屏页打开（父级负责路由跳转） */
  expand: []
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
  state.x = Math.max(0, dragState.originX + event.clientX - dragState.startX)
  state.y = Math.max(0, dragState.originY + event.clientY - dragState.startY)
}

function stopDrag() {
  dragState.active = false
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', stopDrag)
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
  state.width = Math.max(props.minWidth, resizeState.originWidth + event.clientX - resizeState.startX)
  state.height = Math.max(props.minHeight, resizeState.originHeight + event.clientY - resizeState.startY)
}

function stopResize() {
  resizeState.active = false
  window.removeEventListener('pointermove', onResize)
  window.removeEventListener('pointerup', stopResize)
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
onBeforeUnmount(() => {
  stopDrag()
  stopResize()
})
</script>

<template>
  <section ref="rootEl" class="yd-chat-window" :class="{ 'is-fullscreen': state.fullscreen }" :style="style" aria-label="AI 聊天窗口">
    <header class="yd-chat-window__head" @pointerdown="startDrag">
      <span class="yd-chat-window__title"><FaIcon name="i-ri:chat-3-line" />{{ title }}</span>
      <span class="yd-chat-window__tools">
        <button v-if="maximizable" type="button" title="最大化" @click="toggleMaximize">
          <FaIcon :name="state.maximized ? 'i-ri:contract-left-right-line' : 'i-ri:expand-left-right-line'" />
        </button>
        <button v-if="fullscreenable" type="button" title="全屏" @click="toggleFullscreen">
          <FaIcon :name="state.fullscreen ? 'i-ri:fullscreen-exit-line' : 'i-ri:fullscreen-line'" />
        </button>
        <button type="button" title="关闭" @click="emits('close')">
          <FaIcon name="i-ri:close-line" />
        </button>
      </span>
    </header>
    <div class="yd-chat-window__body">
      <slot />
    </div>
    <button v-if="resizable && !state.maximized && !state.fullscreen" type="button" class="yd-chat-window__resize" @pointerdown="startResize">
      <FaIcon name="i-ri:corner-down-right-line" />
    </button>
  </section>
</template>

<style scoped>
.yd-chat-window {
  position: fixed;
  z-index: 1000;
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
  color: rgb(var(--primary-6));
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
  color: rgb(var(--primary-6));
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
