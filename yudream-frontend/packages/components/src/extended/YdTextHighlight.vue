<script setup lang="ts">
import { useElementVisibility } from '@vueuse/core'
import { ref } from 'vue'
import './extended.css'

withDefaults(defineProps<{
  delay?: number
  duration?: number
  from?: 'left' | 'right' | 'top' | 'bottom'
}>(), { delay: 0, duration: 900, from: 'left' })
const el = ref<HTMLElement>()
const visible = useElementVisibility(el)
</script>

<template>
  <mark ref="el" class="yd-highlight" :style="{ '--origin': from, '--scale-start': from === 'top' || from === 'bottom' ? 'scaleY(0)' : 'scaleX(0)' }"><span class="yd-highlight-bg yd-animated" :class="{ visible }" :style="{ transitionDuration: `${duration}ms`, transitionDelay: `${delay}ms` }" aria-hidden="true" /><span class="yd-highlight-text"><slot /></span></mark>
</template>

<style scoped>
.yd-highlight {
  position: relative;
  display: inline;
  padding-inline: 0.15em;
  color: inherit;
  background: transparent;
}

.yd-highlight-bg {
  position: absolute;
  inset: 0;
  background: color-mix(in oklab, var(--yd-emphasis) 15%, transparent);
  border-radius: 0.2em;
  transform: var(--scale-start);
  transform-origin: var(--origin);
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring);
}

.yd-highlight-bg.visible {
  transform: scale(1);
}

.yd-highlight-text {
  position: relative;
}
</style>
