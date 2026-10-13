<script setup lang="ts" generic="T">
import { computed, nextTick, onMounted, onScopeDispose, ref, watch } from 'vue'
import './extended.css'

const props = withDefaults(defineProps<{
  items?: T[]
  modelValue?: number
  direction?: 'horizontal' | 'vertical'
  itemSize?: string
  gap?: number
  snapAlign?: 'start' | 'center'
  draggable?: boolean
  label?: string
}>(), { direction: 'horizontal', itemSize: '100%', gap: 0, snapAlign: 'start', draggable: true, label: '可滑动内容' })
const emit = defineEmits<{
  'update:modelValue': [index: number]
  'change': [index: number]
}>()
const viewport = ref<HTMLDivElement>()
const activeIndex = ref(Number.isFinite(props.modelValue) ? Math.max(0, Math.trunc(props.modelValue!)) : 0)
const count = ref(0)
const dragging = ref(false)
const vertical = computed(() => props.direction === 'vertical')
let observer: MutationObserver | undefined
let resizeObserver: ResizeObserver | undefined
let scrollTimer: ReturnType<typeof setTimeout> | undefined
let clickTimer: ReturnType<typeof setTimeout> | undefined
let drag: {
  pointerId: number
  start: number
  scroll: number
  moved: boolean
} | undefined
let suppressClick = false
function children() {
  return Array.from(viewport.value?.children ?? []).filter((child): child is HTMLElement => child instanceof HTMLElement)
}
function setIndex(index: number) {
  if (index === activeIndex.value) {
    return
  }
  activeIndex.value = index
  emit('update:modelValue', index)
  emit('change', index)
}
function refresh() {
  count.value = children().length
  const index = Math.max(0, Math.min(activeIndex.value, count.value - 1))
  setIndex(index)
}
function scrollTo(index: number, behavior: ScrollBehavior = 'smooth') {
  const element = viewport.value
  const targets = children()
  if (!element || !targets.length) {
    return
  }
  const nextIndex = Math.max(0, Math.min(Math.trunc(index), targets.length - 1))
  const target = targets[nextIndex]
  if (!target) {
    return
  }
  const frame = element.getBoundingClientRect()
  const rect = target.getBoundingClientRect()
  const center = props.snapAlign === 'center'
  const distance = vertical.value
    ? rect.top - frame.top - element.clientTop - (center ? (element.clientHeight - rect.height) / 2 : 0)
    : rect.left - frame.left - element.clientLeft - (center ? (element.clientWidth - rect.width) / 2 : 0)
  element.scrollBy({ top: vertical.value ? distance : 0, left: vertical.value ? 0 : distance, behavior })
  setIndex(nextIndex)
}
function prev() {
  scrollTo(activeIndex.value - 1)
}
function next() {
  scrollTo(activeIndex.value + 1)
}
function onScroll() {
  clearTimeout(scrollTimer)
  scrollTimer = setTimeout(() => {
    const element = viewport.value
    if (!element || dragging.value) {
      return
    }
    const frame = element.getBoundingClientRect()
    const center = props.snapAlign === 'center'
    const target = vertical.value
      ? frame.top + element.clientTop + (center ? element.clientHeight / 2 : 0)
      : frame.left + element.clientLeft + (center ? element.clientWidth / 2 : 0)
    let closest = 0
    let minimum = Number.POSITIVE_INFINITY
    children().forEach((child, index) => {
      const rect = child.getBoundingClientRect()
      const position = vertical.value ? rect.top + (center ? rect.height / 2 : 0) : rect.left + (center ? rect.width / 2 : 0)
      const distance = Math.abs(position - target)
      if (distance < minimum) {
        minimum = distance
        closest = index
      }
    })
    setIndex(closest)
  }, 120)
}
function onKeydown(event: KeyboardEvent) {
  if (event.target !== viewport.value) {
    return
  }
  const previous = vertical.value ? 'ArrowUp' : 'ArrowLeft'
  const following = vertical.value ? 'ArrowDown' : 'ArrowRight'
  if (![previous, following, 'Home', 'End'].includes(event.key)) {
    return
  }
  event.preventDefault()
  if (event.key === previous) {
    prev()
  }
  else if (event.key === following) {
    next()
  }
  else {
    scrollTo(event.key === 'Home' ? 0 : count.value - 1)
  }
}
function onPointerdown(event: PointerEvent) {
  const element = viewport.value
  if (!element || !props.draggable || event.pointerType !== 'mouse' || event.button !== 0) {
    return
  }
  if ((event.target as Element).closest('button,a,input,textarea,select,[contenteditable="true"],[role="button"]')) {
    return
  }
  suppressClick = false
  drag = { pointerId: event.pointerId, start: vertical.value ? event.clientY : event.clientX, scroll: vertical.value ? element.scrollTop : element.scrollLeft, moved: false }
}
function onPointermove(event: PointerEvent) {
  const element = viewport.value
  if (!element || !drag || event.pointerId !== drag.pointerId) {
    return
  }
  if (!(event.buttons & 1)) {
    stopDrag(event)
    return
  }
  const distance = (vertical.value ? event.clientY : event.clientX) - drag.start
  if (!drag.moved && Math.abs(distance) < 4) {
    return
  }
  if (!drag.moved) {
    element.setPointerCapture(event.pointerId)
    drag.moved = true
  }
  dragging.value = true
  event.preventDefault()
  if (vertical.value) {
    element.scrollTop = drag.scroll - distance
  }
  else {
    element.scrollLeft = drag.scroll - distance
  }
}
function stopDrag(event: PointerEvent) {
  const element = viewport.value
  if (!drag || drag.pointerId !== event.pointerId) {
    return
  }
  suppressClick = drag.moved
  clearTimeout(clickTimer)
  clickTimer = setTimeout(() => {
    suppressClick = false
  }, 0)
  drag = undefined
  dragging.value = false
  if (element?.hasPointerCapture(event.pointerId)) {
    element.releasePointerCapture(event.pointerId)
  }
  onScroll()
}
function onClick(event: MouseEvent) {
  if (!suppressClick) {
    return
  }
  event.preventDefault()
  event.stopPropagation()
  suppressClick = false
}
watch(() => props.modelValue, (index) => {
  if (index !== undefined && Number.isFinite(index) && index !== activeIndex.value) {
    scrollTo(index)
  }
})
watch(() => [props.direction, props.itemSize, props.items?.length], async () => {
  await nextTick()
  refresh()
  scrollTo(activeIndex.value, 'auto')
})
onMounted(() => {
  refresh()
  scrollTo(activeIndex.value, 'auto')
  if (!viewport.value) {
    return
  }
  observer = new MutationObserver(refresh)
  observer.observe(viewport.value, { childList: true })
  resizeObserver = new ResizeObserver(() => scrollTo(activeIndex.value, 'auto'))
  resizeObserver.observe(viewport.value)
})
onScopeDispose(() => {
  observer?.disconnect()
  resizeObserver?.disconnect()
  clearTimeout(scrollTimer)
  clearTimeout(clickTimer)
})
defineExpose({ prev, next, scrollTo, activeIndex, count, viewport })
</script>

<template>
  <div ref="viewport" class="yd-swipe yd-focus" :class="{ 'is-vertical': vertical, 'is-dragging': dragging }" :style="{ '--swipe-gap': `${gap}px`, '--swipe-size': itemSize, '--swipe-align': snapAlign }" role="region" :aria-label="label" tabindex="0" @scroll.passive="onScroll" @keydown="onKeydown" @pointerdown="onPointerdown" @pointermove="onPointermove" @pointerup="stopDrag" @pointercancel="stopDrag" @lostpointercapture="stopDrag" @click.capture="onClick" @dragstart.prevent>
    <template v-if="items">
      <div v-for="(item, index) in items" :key="index" class="yd-swipe-item">
        <slot name="item" :item="item" :index="index" :active="index === activeIndex" />
      </div>
    </template>
    <slot v-else :active-index="activeIndex" :prev="prev" :next="next" :scroll-to="scrollTo" />
  </div>
</template>

<style scoped>
.yd-swipe {
  display: flex;
  gap: var(--swipe-gap);
  width: 100%;
  overflow: auto;
  overscroll-behavior-x: contain;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
  -webkit-overflow-scrolling: touch;
}

.yd-swipe::-webkit-scrollbar {
  display: none;
}

.yd-swipe :deep(> *) {
  flex: 0 0 var(--swipe-size);
  min-width: 0;
  scroll-snap-align: var(--swipe-align);
}

.yd-swipe.is-vertical {
  flex-direction: column;
  height: 100%;
  overscroll-behavior-y: contain;
  scroll-snap-type: y mandatory;
}

.yd-swipe.is-vertical :deep(> *) {
  min-height: 0;
}

.yd-swipe.is-dragging {
  cursor: grabbing;
  user-select: none;
  scroll-snap-type: none;
}
</style>
