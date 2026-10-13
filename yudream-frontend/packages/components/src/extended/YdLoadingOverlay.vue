<script setup lang="ts">
import type { YdLoadingType } from './YdLoading.vue'
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import YdLoading from './YdLoading.vue'

defineOptions({ name: 'YdLoadingOverlay' })
const props = withDefaults(defineProps<{
  loading: boolean
  text?: string
  type?: YdLoadingType
  size?: number
  blockInteraction?: boolean
}>(), { text: '加载中', type: 'chase', size: 28, blockInteraction: true })
const content = ref<HTMLElement>()
const overlay = ref<HTMLElement>()
let previousFocus: HTMLElement | null = null
let revision = 0

watch(() => props.loading && props.blockInteraction, async (blocked) => {
  const current = ++revision
  if (blocked) {
    const active = document.activeElement
    previousFocus = active instanceof HTMLElement && content.value?.contains(active) ? active : null
  }
  await nextTick()
  if (current !== revision) {
    return
  }
  if (blocked && previousFocus) {
    overlay.value?.focus({ preventScroll: true })
  }
  else if (!blocked && previousFocus) {
    if (previousFocus.isConnected && !previousFocus.matches(':disabled, [aria-disabled="true"]')) {
      previousFocus.focus({ preventScroll: true })
    }
    previousFocus = null
  }
}, { flush: 'pre' })
onBeforeUnmount(() => {
  revision++
  previousFocus = null
})
</script>

<template>
  <div class="yd-loading-overlay" :aria-busy="loading || undefined">
    <div ref="content" class="yd-loading-overlay__content" :inert="loading && blockInteraction || undefined">
      <slot />
    </div>
    <div v-if="loading" ref="overlay" class="yd-loading-overlay__mask" :class="{ 'yd-loading-overlay__mask--passive': !blockInteraction }" tabindex="-1">
      <slot name="loading">
        <YdLoading :text="text" :type="type" :size="size" />
      </slot>
    </div>
  </div>
</template>

<style scoped>
.yd-loading-overlay { position: relative; min-width: 0; }
.yd-loading-overlay__mask { position: absolute; z-index: 10; inset: 0; display: flex; align-items: center; justify-content: center; padding: 12px; border-radius: inherit; outline: none; background: color-mix(in srgb, var(--color-bg-2) 88%, transparent); backdrop-filter: blur(2px); }
.yd-loading-overlay__mask--passive { pointer-events: none; }
.yd-loading-overlay__content { min-width: 0; }
</style>
