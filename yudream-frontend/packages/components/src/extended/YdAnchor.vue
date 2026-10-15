<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

export interface YdAnchorItem { id: string, title: string, disabled?: boolean }
const props = withDefaults(defineProps<{
  items: YdAnchorItem[]
  container?: HTMLElement | string | (() => HTMLElement | null)
  offset?: number
  smooth?: boolean
  ariaLabel?: string
}>(), { offset: 8, smooth: true, ariaLabel: '页面目录' })
const emit = defineEmits<{ change: [id: string], click: [item: YdAnchorItem] }>()
const active = defineModel<string>('active', { default: '' })
const root = ref<HTMLElement | null>(null)
let frame = 0
const enabled = computed(() => props.items.filter(item => !item.disabled))
function resolveRoot() {
  const value = typeof props.container === 'function' ? props.container() : props.container
  if (typeof value === 'string') {
    return document.querySelector<HTMLElement>(value)
  }
  return value || null
}
function sections() {
  return enabled.value.map(item => ({ item, element: document.getElementById(item.id) })).filter(entry => entry.element)
}
function updateActive() {
  frame = 0
  const list = sections()
  if (!list.length) {
    return
  }
  const rootTop = root.value?.getBoundingClientRect().top ?? 0
  let current = list[0].item.id
  for (const entry of list) {
    if (entry.element!.getBoundingClientRect().top <= rootTop + props.offset + 1) {
      current = entry.item.id
    }
  }
  const target = list.at(-1)!
  const scroller = root.value
  const atBottom = scroller
    ? scroller.scrollTop + scroller.clientHeight >= scroller.scrollHeight - 2
    : window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 2
  if (atBottom) {
    current = target.item.id
  }
  if (active.value !== current) {
    active.value = current
    emit('change', current)
  }
}
function schedule() {
  if (!frame) {
    frame = requestAnimationFrame(updateActive)
  }
}
function scrollTo(id: string) {
  const item = props.items.find(value => value.id === id)
  const element = document.getElementById(id)
  if (!item || item.disabled || !element) {
    return
  }
  const currentRoot = resolveRoot()
  const rootTop = currentRoot?.getBoundingClientRect().top ?? 0
  const top = currentRoot
    ? currentRoot.scrollTop + element.getBoundingClientRect().top - rootTop - props.offset
    : window.scrollY + element.getBoundingClientRect().top - props.offset
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  if (currentRoot) {
    currentRoot.scrollTo({ top, behavior: props.smooth && !reduce ? 'smooth' : 'instant' })
  }
  else {
    window.scrollTo({ top, behavior: props.smooth && !reduce ? 'smooth' : 'instant' })
  }
  active.value = id
  emit('click', item)
}
watch(() => props.container, () => {
  root.value = resolveRoot()
  schedule()
})
onMounted(() => {
  root.value = resolveRoot()
  root.value?.addEventListener('scroll', schedule, { passive: true })
  window.addEventListener('scroll', schedule, { passive: true })
  window.addEventListener('resize', schedule)
  schedule()
})
onBeforeUnmount(() => {
  if (frame) {
    cancelAnimationFrame(frame)
  }
  root.value?.removeEventListener('scroll', schedule)
  window.removeEventListener('scroll', schedule)
  window.removeEventListener('resize', schedule)
})
defineExpose({ scrollTo, refresh: schedule })
</script>

<template>
  <nav class="yd-anchor" :aria-label="ariaLabel">
    <a v-for="item in items" :key="item.id" :href="`#${encodeURIComponent(item.id)}`" :aria-current="active === item.id ? 'location' : undefined" :aria-disabled="item.disabled || undefined" class="yd-anchor__link" :class="{ 'is-active': active === item.id, 'is-disabled': item.disabled }" @click.prevent="scrollTo(item.id)">
      <slot name="item" :item="item" :active="active === item.id">{{ item.title }}</slot>
    </a>
  </nav>
</template>

<style scoped>
.yd-anchor { display: grid; gap: 0.2rem; border-inline-start: 1px solid oklch(var(--border)); }
.yd-anchor__link { display: block; margin-inline-start: -1px; padding: 0.4rem 0.75rem; color: oklch(var(--muted-foreground)); text-decoration: none; border-inline-start: 2px solid transparent; }
.yd-anchor__link:hover, .yd-anchor__link.is-active { color: oklch(var(--foreground)); }
.yd-anchor__link.is-active { font-weight: 600; border-inline-start-color: oklch(var(--ring)); }
.yd-anchor__link.is-disabled { pointer-events: none; opacity: 0.5; }
</style>
