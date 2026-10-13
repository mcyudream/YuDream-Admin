<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  duration?: number
  delay?: number
  borderWidth?: number
  colorFrom?: string
  colorTo?: string
}>(), { delay: 0, borderWidth: 2, colorFrom: 'var(--yd-emphasis)', colorTo: 'oklch(var(--muted-foreground))' })
</script>

<template>
  <span class="yd-border-beam" :style="{ padding: `${borderWidth}px` }" aria-hidden="true"><span class="yd-border-orbit yd-animated" :style="{ animationDuration: `calc(${duration === undefined ? 'var(--yd-decoration-duration)' : `${duration}ms`} * var(--yd-motion-loop-scale))`, animationDelay: `${delay}ms`, background: `conic-gradient(from 0deg, transparent 65%, ${colorFrom}, ${colorTo}, transparent)` }" /></span>
</template>

<style scoped>
.yd-border-beam {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  border-radius: inherit;
  mask: linear-gradient(#fff 0 0) content-box, linear-gradient(#fff 0 0);
  mask-composite: exclude;
}

.yd-border-orbit {
  position: absolute;
  inset: -150%;
  animation: yd-border-orbit 6s linear infinite;
}

@keyframes yd-border-orbit {
  to {
    rotate: 360deg;
  }
}
</style>
