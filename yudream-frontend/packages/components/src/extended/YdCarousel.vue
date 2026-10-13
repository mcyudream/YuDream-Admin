<script setup lang="ts" generic="T">
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import Button from '../basic/button/index.vue'
import Icon from '../basic/icon/index.vue'
import YdSmoothSwipe from './YdSmoothSwipe.vue'

const props = withDefaults(defineProps<{
  items: T[]
  modelValue?: number
  direction?: 'horizontal' | 'vertical'
  autoplay?: boolean
  interval?: number
  loop?: boolean
  arrows?: boolean
  indicators?: boolean
  label?: string
}>(), { direction: 'horizontal', autoplay: false, interval: 4000, loop: true, arrows: true, indicators: true, label: '轮播内容' })
const emit = defineEmits<{
  'update:modelValue': [index: number]
  'change': [index: number]
}>()
const swipe = ref<{ scrollTo: (index: number, behavior?: ScrollBehavior) => void }>()
const current = ref(Number.isFinite(props.modelValue) ? Math.max(0, Math.trunc(props.modelValue!)) : 0)
const hovered = ref(false)
const focused = ref(false)
const hidden = ref(true)
let timer: ReturnType<typeof setTimeout> | undefined
const paused = computed(() => !props.autoplay || hovered.value || focused.value || hidden.value || props.items.length < 2)
function select(index: number) {
  if (!props.items.length) {
    return
  }
  const maximum = props.items.length - 1
  const target = props.loop ? (index % props.items.length + props.items.length) % props.items.length : Math.max(0, Math.min(index, maximum))
  swipe.value?.scrollTo(target)
}
function prev() {
  select(current.value - 1)
}
function next() {
  select(current.value + 1)
}
function update(index: number) {
  current.value = index
  emit('update:modelValue', index)
  emit('change', index)
  schedule()
}
function schedule() {
  clearTimeout(timer)
  if (paused.value || (!props.loop && current.value >= props.items.length - 1)) {
    return
  }
  timer = setTimeout(() => {
    next()
    schedule()
  }, Math.max(500, Number.isFinite(props.interval) ? props.interval : 4000))
}
function onFocusout(event: FocusEvent) {
  if (!(event.relatedTarget instanceof Node) || !(event.currentTarget as HTMLElement).contains(event.relatedTarget)) {
    focused.value = false
  }
}
function onVisibility() {
  hidden.value = document.hidden
}
watch([paused, () => props.interval, () => props.items.length, () => props.loop], schedule)
watch(() => props.modelValue, (value) => {
  if (value !== undefined && Number.isFinite(value) && value !== current.value) {
    select(value)
  }
})
onMounted(() => {
  onVisibility()
  document.addEventListener('visibilitychange', onVisibility)
  schedule()
})
onScopeDispose(() => {
  clearTimeout(timer)
  if (typeof document !== 'undefined') {
    document.removeEventListener('visibilitychange', onVisibility)
  }
})
defineExpose({ prev, next, scrollTo: select, activeIndex: current })
</script>

<template>
  <section class="yd-carousel yd-surface" role="region" aria-roledescription="轮播" :aria-label="label" @mouseenter="hovered = true" @mouseleave="hovered = false" @focusin="focused = true" @focusout="onFocusout">
    <YdSmoothSwipe ref="swipe" :items="items" :model-value="modelValue ?? current" :direction="direction" :label="label" class="yd-carousel-viewport" @update:model-value="update">
      <template #item="{ item, index, active }">
        <div class="yd-carousel-slide" role="group" aria-roledescription="幻灯片" :aria-label="`${index + 1} / ${items.length}`" :inert="!active">
          <slot name="item" :item="item" :index="index" :active="active">
            <slot :item="item" :index="index" :active="active" />
          </slot>
        </div>
      </template>
    </YdSmoothSwipe>
    <div v-if="arrows && items.length > 1" class="yd-carousel-arrows" :class="{ 'is-vertical': direction === 'vertical' }">
      <Button variant="outline" size="icon" aria-label="上一张" :disabled="!loop && current === 0" @click="prev">
        <Icon :name="direction === 'vertical' ? 'morph:chevron-up' : 'morph:chevron-left'" />
      </Button>
      <Button variant="outline" size="icon" aria-label="下一张" :disabled="!loop && current === items.length - 1" @click="next">
        <Icon :name="direction === 'vertical' ? 'morph:chevron-down' : 'morph:chevron-right'" />
      </Button>
    </div>
    <nav v-if="indicators && items.length > 1" class="yd-carousel-indicators" aria-label="选择幻灯片">
      <button v-for="(_, index) in items" :key="index" type="button" class="yd-carousel-dot yd-focus yd-animated" :class="{ 'is-active': index === current }" :aria-current="index === current ? 'true' : undefined" :aria-label="`第 ${index + 1} 张`" @click="select(index)" />
    </nav>
    <span class="yd-carousel-status" :aria-live="paused ? 'polite' : 'off'" aria-atomic="true">第 {{ items.length ? current + 1 : 0 }} 张，共 {{ items.length }} 张</span>
  </section>
</template>

<style scoped>
.yd-carousel {
  position: relative;
  overflow: hidden;
}

.yd-carousel-viewport {
  height: 100%;
}

.yd-carousel-slide {
  height: 100%;
}

.yd-carousel-arrows {
  position: absolute;
  inset: 50% 0.75rem auto;
  display: flex;
  justify-content: space-between;
  pointer-events: none;
  transform: translateY(-50%);
}

.yd-carousel-arrows > * {
  pointer-events: auto;
  background: oklch(var(--background) / 90%);
  backdrop-filter: blur(8px);
}

.yd-carousel-arrows.is-vertical {
  inset: 0.75rem auto 0.75rem 50%;
  flex-direction: column;
  transform: translateX(-50%);
}

.yd-carousel-indicators {
  position: absolute;
  inset: auto 0 0.75rem;
  display: flex;
  gap: 0.3rem;
  justify-content: center;
}

.yd-carousel-dot {
  position: relative;
  width: 24px;
  height: 24px;
  border-radius: 50%;
}

.yd-carousel-dot::after {
  position: absolute;
  inset: 8px;
  content: "";
  background: oklch(var(--foreground) / 35%);
  border-radius: 999px;
  transition: inset var(--yd-motion-duration-normal), background var(--yd-motion-duration-normal);
}

.yd-carousel-dot.is-active::after {
  inset: 8px 2px;
  background: oklch(var(--primary));
}

.yd-carousel-status {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  white-space: nowrap;
  clip-path: inset(50%);
}
</style>
