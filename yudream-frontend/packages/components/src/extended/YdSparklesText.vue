<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  text?: string
  sparklesCount?: number
  colors?: {
    first: string
    second: string
  }
}>(), { sparklesCount: 7 })
</script>

<template>
  <span class="yd-sparkles"><slot>{{ text }}</slot><span v-for="n in Math.min(24, Math.max(0, sparklesCount))" :key="n" aria-hidden="true" class="yd-sparkle yd-animated" :style="{ left: `${(n * 37) % 100}%`, top: `${(n * 61) % 100}%`, animationDelay: `${n * -0.37}s`, color: colors ? (n % 2 ? colors.first : colors.second) : undefined }">✦</span></span>
</template>

<style scoped>
.yd-sparkles {
  position: relative;
  display: inline-block;
  padding: 0.15em;
}

.yd-sparkle {
  position: absolute;
  font-size: 0.45em;
  color: var(--yd-emphasis);
  pointer-events: none;
  animation: yd-sparkle calc(var(--yd-decoration-duration) * var(--yd-motion-loop-scale)) ease-in-out infinite;
}

@keyframes yd-sparkle {
  0%,
  100% {
    opacity: 0;
    rotate: 0deg;
    scale: 0;
  }

  50% {
    opacity: 0.8;
    rotate: 100deg;
    scale: 1;
  }
}
</style>
