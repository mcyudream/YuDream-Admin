<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import YdLoading from './YdLoading.vue'

const props = withDefaults(defineProps<{
  load?: () => Promise<unknown>
  hasMore?: boolean
  distance?: number
  disabled?: boolean
  root?: HTMLElement | string | (() => HTMLElement | null)
  immediate?: boolean
  loadingText?: string
  finishedText?: string
  errorText?: string
}>(), { hasMore: true, distance: 120, immediate: true, loadingText: '正在加载', finishedText: '没有更多内容', errorText: '加载失败' })
const emit = defineEmits<{ load: [] }>()
const loadingModel = defineModel<boolean>('loading', { default: false })
const finishedModel = defineModel<boolean>('finished', { default: false })
const sentinel = ref<HTMLElement>()
const error = ref(false)
let observer: IntersectionObserver | undefined
let rootElement: HTMLElement | null = null
let generation = 0
let armed = true
const canLoad = computed(() => !props.disabled && !loadingModel.value && !finishedModel.value && props.hasMore)
function resolveRoot() {
  const value = typeof props.root === 'function' ? props.root() : props.root
  if (typeof value === 'string') {
    return document.querySelector<HTMLElement>(value)
  }
  return value || null
}
function check() {
  if (!canLoad.value || !armed || !sentinel.value) {
    return
  }
  const rect = sentinel.value.getBoundingClientRect()
  const rootRect = rootElement?.getBoundingClientRect()
  const visible = rootRect
    ? rect.top <= rootRect.bottom + props.distance && rect.bottom >= rootRect.top - props.distance
    : rect.top <= window.innerHeight + props.distance && rect.bottom >= -props.distance
  if (visible) {
    void loadMore()
  }
}
async function loadMore() {
  if (!canLoad.value || !armed) {
    return
  }
  armed = false
  error.value = false
  loadingModel.value = true
  const current = generation
  try {
    if (props.load) {
      await props.load()
      if (current === generation) {
        loadingModel.value = false
      }
    }
    else {
      emit('load')
    }
  }
  catch {
    if (current === generation) {
      error.value = true
      loadingModel.value = false
      armed = true
    }
  }
  finally {
    if (current === generation && props.load) {
      loadingModel.value = false
      armed = true
    }
    if (current === generation && props.immediate) {
      requestAnimationFrame(check)
    }
  }
}
function reset() {
  generation++
  error.value = false
  armed = true
  loadingModel.value = false
  finishedModel.value = false
  requestAnimationFrame(check)
}
function observe() {
  observer?.disconnect()
  rootElement = resolveRoot()
  if (!sentinel.value || typeof IntersectionObserver === 'undefined') {
    return
  }
  observer = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) {
      void loadMore()
    }
  }, { root: rootElement, rootMargin: `0px 0px ${Math.max(0, props.distance)}px 0px` })
  observer.observe(sentinel.value)
}
watch(() => props.root, observe)
watch([canLoad, loadingModel], () => {
  if (loadingModel.value) {
    armed = false
  }
  else if (!props.load) {
    armed = true
  }
  if (!loadingModel.value && props.immediate) {
    requestAnimationFrame(check)
  }
})
onMounted(() => {
  observe()
  if (props.immediate) {
    requestAnimationFrame(check)
  }
})
onBeforeUnmount(() => {
  generation++
  observer?.disconnect()
})
defineExpose({ reset, check })
</script>

<template>
  <div class="yd-infinite-scroll">
    <slot />
    <div ref="sentinel" class="yd-infinite-scroll__sentinel" aria-hidden="true" />
    <div v-if="loadingModel" class="yd-infinite-scroll__status" role="status">
      <YdLoading :size="16" :text="loadingText" />
    </div>
    <button v-else-if="error" type="button" class="yd-infinite-scroll__retry" @click="armed = true; loadMore()">
      {{ errorText }}，点击重试
    </button>
    <div v-else-if="finishedModel || !hasMore" class="yd-infinite-scroll__status">
      {{ finishedText }}
    </div>
    <div v-else-if="!load && !loadingModel" class="yd-infinite-scroll__retry" role="status">
      滚动加载中…
    </div>
  </div>
</template>

<style scoped>
.yd-infinite-scroll__sentinel { width: 100%; height: 1px; pointer-events: none; }
.yd-infinite-scroll__status { display: flex; align-items: center; justify-content: center; gap: 0.5rem; padding: 0.75rem; color: oklch(var(--muted-foreground)); font-size: 0.875rem; }
.yd-infinite-scroll__retry { display: block; margin: 0.5rem auto; padding: 0.4rem 0.75rem; color: oklch(var(--muted-foreground)); background: transparent; border: 0; cursor: pointer; }
</style>
