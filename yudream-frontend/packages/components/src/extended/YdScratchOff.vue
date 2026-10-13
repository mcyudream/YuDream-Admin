<script setup lang="ts">
import { computed, nextTick, onMounted, onScopeDispose, ref, watch } from 'vue'
import Button from '../basic/button/index.vue'
import { useYdMotion } from './motion'
import './extended.css'

const props = withDefaults(defineProps<{
  width?: number | string
  height?: number | string
  brushSize?: number
  threshold?: number
  coverText?: string
  coverColor?: string
  disabled?: boolean
  revealLabel?: string
}>(), { width: '100%', height: 180, brushSize: 28, threshold: 50, coverText: '刮开，发现小惊喜', disabled: false, revealLabel: '直接揭晓' })
const emit = defineEmits<{
  progress: [percent: number]
  complete: []
  reset: []
  error: [error: Error]
}>()
const { reduced } = useYdMotion()
const frame = ref<HTMLDivElement>()
const canvas = ref<HTMLCanvasElement>()
const completed = ref(false)
const progress = ref(0)
const painted = ref(false)
const dimension = computed(() => ({ width: typeof props.width === 'number' ? `${props.width}px` : props.width, height: typeof props.height === 'number' ? `${props.height}px` : props.height }))
let resizeObserver: ResizeObserver | undefined
let themeObserver: MutationObserver | undefined
let context: CanvasRenderingContext2D | null = null
let pointerId: number | undefined
let previous: {
  x: number
  y: number
} | undefined
let progressFrame = 0
let logicalWidth = 0
let logicalHeight = 0
let scale = 1
function reportError(error: unknown) {
  emit('error', error instanceof Error ? error : new Error(String(error)))
}
function drawCover(preserve = false) {
  const element = canvas.value
  if (!element || !frame.value) {
    return
  }
  const rect = frame.value.getBoundingClientRect()
  if (!rect.width || !rect.height) {
    return
  }
  let previousCanvas: HTMLCanvasElement | undefined
  if (preserve && painted.value && element.width && element.height) {
    previousCanvas = document.createElement('canvas')
    previousCanvas.width = element.width
    previousCanvas.height = element.height
    previousCanvas.getContext('2d')?.drawImage(element, 0, 0)
  }
  logicalWidth = rect.width
  logicalHeight = rect.height
  scale = Math.min(3, window.devicePixelRatio || 1)
  element.width = Math.round(logicalWidth * scale)
  element.height = Math.round(logicalHeight * scale)
  context = element.getContext('2d', { willReadFrequently: true })
  if (!context) {
    reportError(new Error('浏览器不支持 Canvas'))
    return
  }
  context.setTransform(scale, 0, 0, scale, 0, 0)
  if (completed.value) {
    painted.value = true
    return
  }
  if (previousCanvas) {
    context.drawImage(previousCanvas, 0, 0, logicalWidth, logicalHeight)
  }
  else {
    context.fillStyle = props.coverColor || getComputedStyle(element).color
    context.fillRect(0, 0, logicalWidth, logicalHeight)
    context.fillStyle = getComputedStyle(element).getPropertyValue('--scratch-label-color').trim() || '#ffffff'
    context.font = `600 16px ${getComputedStyle(element).fontFamily}`
    context.textAlign = 'center'
    context.textBaseline = 'middle'
    context.fillText(props.coverText, logicalWidth / 2, logicalHeight / 2, logicalWidth - 24)
    context.globalAlpha = 0.13
    for (let x = -logicalHeight; x < logicalWidth; x += 18) {
      context.beginPath()
      context.moveTo(x, 0)
      context.lineTo(x + logicalHeight, logicalHeight)
      context.strokeStyle = '#ffffff'
      context.lineWidth = 2
      context.stroke()
    }
    context.globalAlpha = 1
  }
  context.globalCompositeOperation = 'destination-out'
  context.lineWidth = Math.max(1, Number.isFinite(props.brushSize) ? props.brushSize : 28)
  context.lineCap = 'round'
  context.lineJoin = 'round'
  painted.value = true
}
function reveal() {
  if (completed.value || props.disabled) {
    return
  }
  completed.value = true
  progress.value = 100
  emit('progress', 100)
  emit('complete')
}
function measure() {
  progressFrame = 0
  if (!context || !canvas.value || completed.value) {
    return
  }
  try {
    const { data, width, height } = context.getImageData(0, 0, canvas.value.width, canvas.value.height)
    // 网格采样控制读取成本；覆盖层只由本地 Canvas 绘制，不引入跨域图片。
    const stride = Math.max(1, Math.ceil(Math.max(width, height) / 100))
    let cleared = 0
    let total = 0
    for (let y = 0; y < height; y += stride) {
      for (let x = 0; x < width; x += stride) {
        if ((data[(y * width + x) * 4 + 3] ?? 0) < 128) {
          cleared++
        }
        total++
      }
    }
    const percentage = Math.min(100, Math.round(cleared / Math.max(1, total) * 100))
    if (percentage !== progress.value) {
      progress.value = percentage
      emit('progress', percentage)
    }
    const threshold = Number.isFinite(props.threshold) ? Math.max(1, Math.min(100, props.threshold)) : 50
    if (percentage >= threshold) {
      reveal()
    }
  }
  catch (error) {
    reportError(error)
  }
}
function point(event: PointerEvent) {
  const rect = canvas.value!.getBoundingClientRect()
  return { x: event.clientX - rect.left, y: event.clientY - rect.top }
}
function scratch(event: PointerEvent) {
  if (event.pointerId !== pointerId || !context || completed.value || props.disabled) {
    return
  }
  event.preventDefault()
  const position = point(event)
  context.beginPath()
  context.moveTo(previous?.x ?? position.x, previous?.y ?? position.y)
  context.lineTo(position.x + 0.01, position.y + 0.01)
  context.stroke()
  previous = position
  if (!progressFrame) {
    progressFrame = requestAnimationFrame(measure)
  }
}
function start(event: PointerEvent) {
  if (props.disabled || completed.value || pointerId !== undefined || event.button !== 0) {
    return
  }
  pointerId = event.pointerId
  previous = point(event)
  canvas.value?.setPointerCapture(event.pointerId)
  scratch(event)
}
function stop(event: PointerEvent) {
  if (event.pointerId !== pointerId) {
    return
  }
  pointerId = undefined
  previous = undefined
  if (canvas.value?.hasPointerCapture(event.pointerId)) {
    canvas.value.releasePointerCapture(event.pointerId)
  }
}
async function reset() {
  pointerId = undefined
  previous = undefined
  completed.value = false
  progress.value = 0
  await nextTick()
  drawCover()
  emit('progress', 0)
  emit('reset')
}
watch(() => [props.coverText, props.coverColor], () => {
  if (!completed.value && progress.value === 0) {
    drawCover()
  }
})
watch(() => props.brushSize, () => {
  if (context) {
    context.lineWidth = Math.max(1, Number.isFinite(props.brushSize) ? props.brushSize : 28)
  }
})
onMounted(() => {
  drawCover()
  resizeObserver = new ResizeObserver(() => drawCover(true))
  if (frame.value) {
    resizeObserver.observe(frame.value)
  }
  themeObserver = new MutationObserver(() => {
    if (!completed.value && progress.value === 0) {
      drawCover()
    }
  })
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class', 'style', 'data-admin-style'] })
})
onScopeDispose(() => {
  resizeObserver?.disconnect()
  themeObserver?.disconnect()
  if (typeof cancelAnimationFrame !== 'undefined') {
    cancelAnimationFrame(progressFrame)
  }
})
defineExpose({ reveal, reset, progress, completed })
</script>

<template>
  <div class="yd-scratch">
    <div ref="frame" class="yd-scratch-frame yd-surface" :style="dimension" :class="{ 'is-complete': completed, 'is-reduced': reduced }">
      <div class="yd-scratch-content" :inert="!completed">
        <slot :revealed="completed" :progress="progress" />
      </div>
      <canvas ref="canvas" class="yd-scratch-cover yd-focus yd-animated" :class="{ 'is-painted': painted }" role="button" :tabindex="completed || disabled ? -1 : 0" :aria-label="`${coverText}，按回车或空格直接揭晓`" :aria-disabled="disabled" :aria-hidden="completed" @pointerdown="start" @pointermove="scratch" @pointerup="stop" @pointercancel="stop" @lostpointercapture="stop" @keydown.enter.prevent="reveal" @keydown.space.prevent="reveal" />
    </div>
    <div class="yd-scratch-footer">
      <span class="yd-muted" role="status">{{ completed ? '已揭晓' : `已刮开 ${progress}%` }}</span><Button v-if="!completed" variant="ghost" size="sm" :disabled="disabled" @click="reveal">
        {{ revealLabel }}
      </Button><slot v-else name="completed" :reset="reset" />
    </div>
  </div>
</template>

<style scoped>
.yd-scratch-frame {
  position: relative;
  max-width: 100%;
  overflow: hidden;
  isolation: isolate;
}

.yd-scratch-content {
  display: grid;
  place-items: center;
  height: 100%;
  padding: var(--yd-surface-padding);
}

.yd-scratch-cover {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  color: oklch(var(--primary));
  touch-action: none;
  cursor: crosshair;
  opacity: 0;
  transition: opacity var(--yd-motion-duration-normal), transform var(--yd-motion-duration-normal);

  --scratch-label-color: oklch(var(--primary-foreground));
}

.yd-scratch-cover.is-painted {
  opacity: 1;
}

.is-complete .yd-scratch-cover {
  pointer-events: none;
  opacity: 0;
  transform: scale(1.04);
}

.is-reduced .yd-scratch-cover {
  transition: opacity 180ms ease-out, transform 180ms ease-out;
}

.is-reduced.is-complete .yd-scratch-cover {
  transform: scale(1.01);
}

.yd-scratch-footer {
  display: flex;
  gap: 0.5rem;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  margin-top: 0.5rem;
  font-size: 0.8rem;
}
</style>
