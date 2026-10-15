<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, useId, watch } from 'vue'
import YdButton from '../basic/button/index.vue'
import { useYdMotion } from './motion'

defineOptions({ name: 'YdTour' })

export type YdTourPlacement = 'top' | 'bottom' | 'left' | 'right'

export interface YdTourStep {
  /** 目标元素：CSS 选择器（推荐 [data-tour="..."] 形式）或返回元素的函数；为空或无法解析时该步骤居中展示。 */
  target?: string | (() => HTMLElement | null | undefined)
  title?: string
  content: string
  /** 气泡相对目标元素的方位，默认 bottom；居中步骤忽略该值。 */
  placement?: YdTourPlacement
}

const props = withDefaults(defineProps<{
  steps: YdTourStep[]
  open: boolean
  initialStep?: number
  /** 遮罩是否拦截页面交互；关闭后仅做视觉提示，不阻止点击。 */
  mask?: boolean
  zIndex?: number
}>(), { initialStep: 0, mask: true, zIndex: 3000 })

const emit = defineEmits<{
  'update:open': [value: boolean]
  finish: []
  skip: []
  'step-change': [index: number]
}>()

const { reduced } = useYdMotion()
const maskId = `yd-tour-mask-${useId()}`
const GAP = 12
const SPOT_PAD = 8
const VIEWPORT_PAD = 8

interface SpotBox { x: number, y: number, width: number, height: number }

const index = ref(0)
const spot = ref<SpotBox>()
const popover = ref<HTMLElement>()
const popoverPos = ref({ top: -9999, left: -9999 })
let previousFocus: HTMLElement | null = null
let repositionFrame = 0

const currentStep = computed<YdTourStep | undefined>(() => props.steps[index.value])
const stepLabel = computed(() => `第 ${index.value + 1} 步，共 ${props.steps.length} 步`)
const isLast = computed(() => index.value >= props.steps.length - 1)

const overlayMaskRect = computed(() => spot.value
  ? { x: spot.value.x, y: spot.value.y, width: spot.value.width, height: spot.value.height }
  : undefined)

watch(() => props.open, async (open) => {
  if (open) {
    index.value = Math.min(Math.max(props.initialStep, 0), Math.max(props.steps.length - 1, 0))
    previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
    attachListeners()
    await nextTick()
    update(true)
  }
  else {
    detachListeners()
    restoreFocus()
  }
})

watch(() => props.steps, () => {
  if (!props.open) {
    return
  }
  if (index.value > props.steps.length - 1) {
    index.value = Math.max(props.steps.length - 1, 0)
  }
  update(false)
}, { deep: true })

function resolveStepElement(step: YdTourStep | undefined): HTMLElement | null {
  if (!step?.target) {
    return null
  }
  try {
    const candidate = typeof step.target === 'function' ? step.target() : document.querySelector(step.target)
    if (candidate instanceof HTMLElement && candidate.isConnected && candidate.getClientRects().length > 0) {
      return candidate
    }
  }
  catch {
    // 非法选择器按无目标处理，步骤降级为居中展示
  }
  return null
}

function update(withScroll: boolean) {
  const step = currentStep.value
  if (!step) {
    close('finish')
    return
  }
  const element = resolveStepElement(step)
  if (element && withScroll) {
    element.scrollIntoView({ block: 'center', inline: 'nearest', behavior: reduced.value ? 'auto' : 'smooth' })
  }
  if (element) {
    const rect = element.getBoundingClientRect()
    spot.value = {
      x: rect.left - SPOT_PAD,
      y: rect.top - SPOT_PAD,
      width: rect.width + SPOT_PAD * 2,
      height: rect.height + SPOT_PAD * 2,
    }
  }
  else {
    spot.value = undefined
  }
  void positionPopover(element?.getBoundingClientRect())
  // 兜底二次定位：步骤切换当帧模板引用可能未就绪导致首轮测量静默放弃
  window.requestAnimationFrame(() => {
    if (props.open) {
      void positionPopover(resolveStepElement(currentStep.value)?.getBoundingClientRect())
    }
  })
  emit('step-change', index.value)
  popover.value?.focus({ preventScroll: true })
}

async function positionPopover(targetRect: DOMRect | undefined, attempt = 0) {
  await nextTick()
  const node = popover.value
  if (!node) {
    if (attempt < 2) {
      await positionPopover(targetRect, attempt + 1)
    }
    return
  }
  const width = node.offsetWidth
  const height = node.offsetHeight
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  const maxWidth = Math.min(360, viewportWidth - VIEWPORT_PAD * 2)
  if (width > maxWidth + 1) {
    node.style.maxWidth = `${maxWidth}px`
  }
  const clampLeft = (left: number) => Math.min(Math.max(left, VIEWPORT_PAD), Math.max(viewportWidth - width - VIEWPORT_PAD, VIEWPORT_PAD))
  const clampTop = (top: number) => Math.min(Math.max(top, VIEWPORT_PAD), Math.max(viewportHeight - height - VIEWPORT_PAD, VIEWPORT_PAD))
  if (!targetRect || !spot.value) {
    popoverPos.value = { top: clampTop((viewportHeight - height) / 2), left: clampLeft((viewportWidth - width) / 2) }
    return
  }
  const placement = currentStep.value?.placement ?? 'bottom'
  let placementCandidates: YdTourPlacement[] = [placement]
  if (placement === 'top' || placement === 'bottom') {
    placementCandidates = [placement, placement === 'bottom' ? 'top' : 'bottom']
  }
  else {
    placementCandidates = [placement, placement === 'left' ? 'right' : 'left']
  }
  let top = 0
  let left = 0
  for (const candidate of placementCandidates) {
    if (candidate === 'bottom') {
      top = targetRect.bottom + GAP
      left = targetRect.left + targetRect.width / 2 - width / 2
      if (top + height <= viewportHeight - VIEWPORT_PAD) {
        break
      }
    }
    else if (candidate === 'top') {
      top = targetRect.top - GAP - height
      left = targetRect.left + targetRect.width / 2 - width / 2
      if (top >= VIEWPORT_PAD) {
        break
      }
    }
    else if (candidate === 'right') {
      top = targetRect.top + targetRect.height / 2 - height / 2
      left = targetRect.right + GAP
      if (left + width <= viewportWidth - VIEWPORT_PAD) {
        break
      }
    }
    else {
      top = targetRect.top + targetRect.height / 2 - height / 2
      left = targetRect.left - GAP - width
      if (left >= VIEWPORT_PAD) {
        break
      }
    }
  }
  popoverPos.value = { top: clampTop(top), left: clampLeft(left) }
}

function next() {
  if (!props.open) {
    return
  }
  if (isLast.value) {
    close('finish')
    return
  }
  index.value++
  update(true)
}

function prev() {
  if (!props.open || index.value <= 0) {
    return
  }
  index.value--
  update(true)
}

function goTo(step: number) {
  if (!props.open || step < 0 || step > props.steps.length - 1) {
    return
  }
  index.value = step
  update(true)
}

function skip() {
  close('skip')
}

function close(result: 'finish' | 'skip') {
  emit('update:open', false)
  if (result === 'finish') {
    emit('finish')
  }
  else {
    emit('skip')
  }
  restoreFocus()
}

function restoreFocus() {
  if (previousFocus && previousFocus.isConnected && !previousFocus.matches(':disabled, [aria-disabled="true"]')) {
    previousFocus.focus({ preventScroll: true })
  }
  previousFocus = null
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    event.preventDefault()
    skip()
    return
  }
  const target = event.target
  if (target instanceof HTMLElement && ['BUTTON', 'INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName)) {
    return
  }
  if (event.key === 'ArrowRight') {
    event.preventDefault()
    next()
  }
  else if (event.key === 'ArrowLeft') {
    event.preventDefault()
    prev()
  }
}

function scheduleReposition() {
  if (repositionFrame) {
    return
  }
  repositionFrame = window.requestAnimationFrame(() => {
    repositionFrame = 0
    if (props.open) {
      update(false)
    }
  })
}

function attachListeners() {
  window.addEventListener('resize', scheduleReposition, true)
  window.addEventListener('scroll', scheduleReposition, true)
}

function detachListeners() {
  window.removeEventListener('resize', scheduleReposition, true)
  window.removeEventListener('scroll', scheduleReposition, true)
  if (repositionFrame) {
    window.cancelAnimationFrame(repositionFrame)
    repositionFrame = 0
  }
}

onBeforeUnmount(() => {
  detachListeners()
  restoreFocus()
})

defineExpose({ next, prev, skip, goTo, current: index })
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open && currentStep"
      class="yd-tour"
      :class="{ 'yd-tour--reduced': reduced, 'yd-tour--passive': !mask }"
      :style="{ zIndex }"
      role="dialog"
      aria-modal="true"
      :aria-label="currentStep.title || '新手引导'"
    >
      <svg class="yd-tour__overlay" :aria-hidden="true">
        <defs>
          <mask :id="maskId">
            <rect x="0" y="0" width="100%" height="100%" fill="white" />
            <rect
              v-if="overlayMaskRect"
              class="yd-tour__hole"
              :x="overlayMaskRect.x"
              :y="overlayMaskRect.y"
              :width="overlayMaskRect.width"
              :height="overlayMaskRect.height"
              rx="10"
              fill="black"
            />
          </mask>
        </defs>
        <rect x="0" y="0" width="100%" height="100%" fill="var(--yd-tour-overlay, rgba(0, 0, 0, 0.55))" :mask="`url(#${maskId})`" />
        <rect
          v-if="overlayMaskRect"
          class="yd-tour__ring"
          :x="overlayMaskRect.x"
          :y="overlayMaskRect.y"
          :width="overlayMaskRect.width"
          :height="overlayMaskRect.height"
          rx="10"
        />
      </svg>
      <div
        ref="popover"
        class="yd-tour__popover"
        :style="{ top: `${popoverPos.top}px`, left: `${popoverPos.left}px` }"
        tabindex="-1"
        @keydown="onKeydown"
      >
        <header v-if="currentStep.title || steps.length > 1" class="yd-tour__header">
          <span v-if="currentStep.title" class="yd-tour__title">{{ currentStep.title }}</span>
          <span v-if="steps.length > 1" class="yd-tour__counter">{{ stepLabel }}</span>
        </header>
        <p class="yd-tour__content">
          {{ currentStep.content }}
        </p>
        <slot :step="currentStep" :index="index" />
        <footer class="yd-tour__footer">
          <YdButton variant="ghost" size="sm" @click="skip">
            跳过引导
          </YdButton>
          <div class="yd-tour__actions">
            <YdButton variant="outline" size="sm" :disabled="index <= 0" @click="prev">
              上一步
            </YdButton>
            <YdButton size="sm" @click="next">
              {{ isLast ? '完成' : '下一步' }}
            </YdButton>
          </div>
        </footer>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.yd-tour {
  position: fixed;
  inset: 0;
}

.yd-tour__overlay {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: auto;
}

.yd-tour--passive .yd-tour__overlay {
  pointer-events: none;
}

.yd-tour__hole,
.yd-tour__ring {
  transition: x 0.24s ease, y 0.24s ease, width 0.24s ease, height 0.24s ease;
}

.yd-tour--reduced .yd-tour__hole,
.yd-tour--reduced .yd-tour__ring {
  transition: none;
}

.yd-tour__ring {
  fill: none;
  stroke: var(--color-text-2);
  stroke-width: 2;
}

.yd-tour__popover {
  position: absolute;
  width: min(360px, calc(100vw - 16px));
  max-width: calc(100vw - 16px);
  padding: 14px 16px;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-1);
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
  outline: none;
  transition: top 0.24s ease, left 0.24s ease;
}

.yd-tour--reduced .yd-tour__popover {
  transition: none;
}

.yd-tour__header {
  display: flex;
  gap: 8px;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}

.yd-tour__title {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.yd-tour__counter {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.yd-tour__content {
  margin: 0;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
  white-space: pre-line;
}

.yd-tour__footer {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
}

.yd-tour__actions {
  display: flex;
  gap: 8px;
}
</style>
