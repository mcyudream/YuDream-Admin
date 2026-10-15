<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import './extended.css'

const emit = defineEmits<{
  navigate: [url: string]
  back: []
  forward: []
  refresh: []
}>()
const modelUrl = defineModel<string>('url', { default: 'https://example.com' })
const address = ref(modelUrl.value)
const history = ref([modelUrl.value])
const historyIndex = ref(0)
const canGoBack = computed(() => historyIndex.value > 0)
const canGoForward = computed(() => historyIndex.value < history.value.length - 1)
let navigatingInternally = false
watch(modelUrl, (url) => {
  if (navigatingInternally) {
    navigatingInternally = false
    return
  }
  address.value = url
  history.value = [url]
  historyIndex.value = 0
})
function navigate() {
  const url = address.value.trim()
  if (!url) {
    return
  }
  history.value = [...history.value.slice(0, historyIndex.value + 1), url]
  historyIndex.value = history.value.length - 1
  navigatingInternally = true
  modelUrl.value = url
  emit('navigate', url)
}
function back() {
  if (!canGoBack.value) {
    return
  }
  historyIndex.value -= 1
  address.value = history.value[historyIndex.value]
  navigatingInternally = true
  modelUrl.value = address.value
  emit('back')
  emit('navigate', address.value)
}
function forward() {
  if (!canGoForward.value) {
    return
  }
  historyIndex.value += 1
  address.value = history.value[historyIndex.value]
  navigatingInternally = true
  modelUrl.value = address.value
  emit('forward')
  emit('navigate', address.value)
}
</script>

<template>
  <section class="yd-mockup-browser yd-surface">
    <header class="yd-mockup-browser-toolbar">
      <span class="yd-mockup-browser-dots" aria-hidden="true"><i /><i /><i /></span>
      <div class="yd-mockup-browser-controls">
        <button type="button" aria-label="后退" :disabled="!canGoBack" @click="back">
          ‹
        </button>
        <button type="button" aria-label="前进" :disabled="!canGoForward" @click="forward">
          ›
        </button>
        <button type="button" aria-label="刷新" @click="emit('refresh')">
          ↻
        </button>
      </div>
      <form class="yd-mockup-browser-address-form" @submit.prevent="navigate">
        <input v-model="address" aria-label="预览地址">
      </form>
    </header>
    <div class="yd-mockup-browser-content">
      <slot :url="history[historyIndex]" :navigate="navigate" />
    </div>
  </section>
</template>

<style scoped>
.yd-mockup-browser { overflow: hidden; }
.yd-mockup-browser-toolbar { display: flex; gap: 12px; align-items: center; min-width: 0; padding: 10px 12px; border-bottom: 1px solid oklch(var(--border)); }
.yd-mockup-browser-dots { display: flex; gap: 5px; }
.yd-mockup-browser-dots i { width: 9px; height: 9px; background: oklch(var(--muted-foreground) / 45%); border-radius: 50%; }
.yd-mockup-browser-controls { display: flex; gap: 2px; }
.yd-mockup-browser-controls button { width: 28px; height: 28px; color: oklch(var(--foreground)); background: transparent; border: 0; border-radius: var(--yd-control-radius); cursor: pointer; }
.yd-mockup-browser-controls button:hover:not(:disabled) { background: oklch(var(--muted)); }
.yd-mockup-browser-controls button:disabled { opacity: 0.4; cursor: default; }
.yd-mockup-browser-address-form { min-width: 0; flex: 1; }
.yd-mockup-browser-address-form input { width: 100%; padding: 5px 10px; color: oklch(var(--muted-foreground)); background: oklch(var(--muted) / 45%); border: 1px solid transparent; border-radius: var(--yd-control-radius); font-size: 12px; }
.yd-mockup-browser-address-form input:focus-visible { outline: 2px solid oklch(var(--ring)); outline-offset: 1px; }
.yd-mockup-browser-content { min-width: 0; }
</style>
