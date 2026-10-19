<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import YdIcon from '../basic/icon/index.vue'
import './extended.css'

/**
 * 可拖拽的圆球入口：默认贴边悬浮，拖拽后按落点吸附左右边缘并记住位置。
 * 只作为「打开某个面板/视图」的入口；拖拽超过阈值不触发 click。
 */
const props = withDefaults(defineProps<{
  icon?: string
  /** 可访问名称，同时作为原生 title */
  label?: string
  /** 角标数字或短文案，空则不显示 */
  badge?: string | number
  size?: number
  draggable?: boolean
  /** 传入后按账号/场景持久化位置，例如 yd.floatBall.ai.123 */
  storageKey?: string
  /** 距离视口边缘的最小间距 */
  edgeOffset?: number
  /** 首次出现时距底部的距离 */
  bottom?: number
  /** 运行中：图标旋转变大并显示状态点 */
  busy?: boolean
  disabled?: boolean
  expanded?: boolean
}>(), {
  icon: 'morph:bot',
  label: 'AI 助手',
  size: 54,
  draggable: true,
  edgeOffset: 20,
  bottom: 96,
  busy: false,
  disabled: false,
  expanded: false,
})

const emit = defineEmits<{
  click: [event: MouseEvent]
}>()

type Side = 'left' | 'right'

const side = ref<Side>('right')
const centerY = ref(-1)
const centerX = ref(-1)
const dragging = ref(false)
const drag = { active: false, moved: false, startX: 0, startY: 0, originX: 0, originY: 0 }

function clamp(value: number, min: number, max: number) {
  return Math.min(Math.max(value, min), max)
}

/** 视口可用范围：圆球整体始终留在视口内。 */
function bounds() {
  const half = props.size / 2
  return {
    minY: half + props.edgeOffset,
    maxY: Math.max(half + props.edgeOffset, window.innerHeight - half - props.edgeOffset),
    minX: half + props.edgeOffset,
    maxX: Math.max(half + props.edgeOffset, window.innerWidth - half - props.edgeOffset),
  }
}

function xFor(current: Side) {
  const limit = bounds()
  return current === 'left' ? limit.minX : limit.maxX
}

function persist() {
  if (!props.storageKey) {
    return
  }
  try {
    localStorage.setItem(props.storageKey, JSON.stringify({ side: side.value, y: centerY.value }))
  }
  catch {
    /* 存储不可用时仅保留本次会话位置 */
  }
}

function restore() {
  let stored: { side?: Side, y?: number } | undefined
  if (props.storageKey) {
    try {
      stored = JSON.parse(localStorage.getItem(props.storageKey) || 'null') || undefined
    }
    catch {
      stored = undefined
    }
  }
  const limit = bounds()
  side.value = stored?.side === 'left' ? 'left' : 'right'
  centerY.value = typeof stored?.y === 'number' ? clamp(stored.y, limit.minY, limit.maxY) : limit.maxY
  centerX.value = xFor(side.value)
}

function reflow() {
  const limit = bounds()
  if (centerY.value < 0) {
    restore()
    return
  }
  centerY.value = clamp(centerY.value, limit.minY, limit.maxY)
  centerX.value = xFor(side.value)
}

function startDrag(event: PointerEvent) {
  if (!props.draggable || props.disabled || event.button !== 0) {
    return
  }
  const limit = bounds()
  drag.active = true
  drag.moved = false
  drag.startX = event.clientX
  drag.startY = event.clientY
  drag.originX = clamp(centerX.value < 0 ? xFor(side.value) : centerX.value, limit.minX, limit.maxX)
  drag.originY = centerY.value
  window.addEventListener('pointermove', onDrag)
  window.addEventListener('pointerup', stopDrag)
}

function onDrag(event: PointerEvent) {
  if (!drag.active) {
    return
  }
  const dx = event.clientX - drag.startX
  const dy = event.clientY - drag.startY
  if (!drag.moved && Math.hypot(dx, dy) < 4) {
    return
  }
  drag.moved = true
  dragging.value = true
  const limit = bounds()
  centerX.value = clamp(drag.originX + dx, limit.minX, limit.maxX)
  centerY.value = clamp(drag.originY + dy, limit.minY, limit.maxY)
}

function stopDrag() {
  if (!drag.active) {
    return
  }
  const moved = drag.moved
  drag.active = false
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', stopDrag)
  dragging.value = false
  if (!moved) {
    return
  }
  side.value = centerX.value <= window.innerWidth / 2 ? 'left' : 'right'
  centerX.value = xFor(side.value)
  persist()
}

function onClick(event: MouseEvent) {
  if (props.disabled) {
    event.preventDefault()
    event.stopPropagation()
    return
  }
  emit('click', event)
}

const style = computed(() => ({
  '--yd-float-ball-size': `${props.size}px`,
  left: `${centerX.value < 0 ? xFor(side.value) : centerX.value}px`,
  top: `${centerY.value < 0 ? 0 : centerY.value}px`,
}))

const badgeText = computed(() => (props.badge === undefined || props.badge === null || props.badge === '' ? '' : String(props.badge)))

onMounted(() => {
  restore()
  window.addEventListener('resize', reflow)
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', reflow)
  window.removeEventListener('pointermove', onDrag)
  window.removeEventListener('pointerup', stopDrag)
})
</script>

<template>
  <button
    type="button"
    class="yd-float-ball"
    :class="{ 'is-dragging': dragging, 'is-busy': busy, 'is-expanded': expanded }"
    :style="style"
    :title="label"
    :aria-label="label"
    :aria-expanded="expanded"
    aria-haspopup="dialog"
    :disabled="disabled"
    @pointerdown="startDrag"
    @click="onClick"
  >
    <YdIcon :name="icon" class="yd-float-ball__icon" />
    <span v-if="busy" class="yd-float-ball__pulse" aria-hidden="true" />
    <span v-if="badgeText" class="yd-float-ball__badge">{{ badgeText }}</span>
  </button>
</template>

<style scoped>
.yd-float-ball {
  position: fixed;
  z-index: 1500;
  display: grid;
  width: var(--yd-float-ball-size, 54px);
  height: var(--yd-float-ball-size, 54px);
  padding: 0;
  color: oklch(var(--primary-foreground));
  background: oklch(var(--primary));
  border: 0;
  border-radius: 50%;
  box-shadow: 0 10px 26px oklch(var(--primary) / 32%), 0 2px 6px oklch(var(--foreground) / 14%);
  cursor: grab;
  place-items: center;
  touch-action: none;
  transform: translate(-50%, -50%);
  transition: transform 180ms ease, box-shadow 180ms ease, opacity 180ms ease;
}

.yd-float-ball:hover:not(:disabled) {
  box-shadow: 0 14px 32px oklch(var(--primary) / 40%), 0 3px 8px oklch(var(--foreground) / 16%);
}

.yd-float-ball:focus-visible {
  outline: 2px solid oklch(var(--ring));
  outline-offset: 3px;
}

.yd-float-ball.is-dragging {
  cursor: grabbing;
  transform: translate(-50%, -50%) scale(1.06);
  transition: box-shadow 180ms ease;
}

.yd-float-ball.is-expanded {
  opacity: 0.92;
}

.yd-float-ball:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.yd-float-ball__icon {
  width: calc(var(--yd-float-ball-size, 54px) * 0.45);
  height: calc(var(--yd-float-ball-size, 54px) * 0.45);
}

.yd-float-ball.is-busy .yd-float-ball__icon {
  animation: yd-float-ball-spin 1.4s linear infinite;
}

.yd-float-ball__pulse {
  position: absolute;
  inset: 0;
  border: 2px solid oklch(var(--primary-foreground) / 55%);
  border-radius: 50%;
  animation: yd-float-ball-pulse 1.8s ease-out infinite;
}

.yd-float-ball__badge {
  position: absolute;
  top: -2px;
  right: -2px;
  min-width: 20px;
  height: 20px;
  padding: 0 6px;
  color: oklch(var(--primary-foreground));
  background: var(--yd-status-danger);
  border: 2px solid oklch(var(--background));
  border-radius: 10px;
  font-size: 0.6875rem;
  font-variant-numeric: tabular-nums;
  line-height: 16px;
  text-align: center;
}

@keyframes yd-float-ball-spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes yd-float-ball-pulse {
  0% {
    opacity: 0.9;
    transform: scale(1);
  }

  100% {
    opacity: 0;
    transform: scale(1.35);
  }
}

@media (prefers-reduced-motion: reduce) {
  .yd-float-ball,
  .yd-float-ball.is-dragging,
  .yd-float-ball__pulse {
    transition: none;
    animation: none;
  }
}

@media (width <= 768px) {
  .yd-float-ball {
    width: 48px;
    height: 48px;
  }
}
</style>
