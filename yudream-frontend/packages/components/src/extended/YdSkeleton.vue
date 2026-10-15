<script setup lang="ts">
withDefaults(defineProps<{
  variant?: 'text' | 'circle' | 'rect' | 'button' | 'image'
  animated?: boolean
  count?: number
  width?: string | number
  height?: string | number
  radius?: string | number
}>(), { variant: 'text', animated: true, count: 1 })
</script>

<template>
  <span
    v-for="index in Math.max(1, count)"
    :key="index"
    class="yd-skeleton"
    :class="[`yd-skeleton--${variant}`, { 'yd-skeleton--animated': animated }]"
    :style="{ width: typeof width === 'number' ? `${width}px` : width, height: typeof height === 'number' ? `${height}px` : height, borderRadius: typeof radius === 'number' ? `${radius}px` : radius }"
    aria-hidden="true"
  />
</template>

<style scoped>
.yd-skeleton { display: block; min-width: 2rem; min-height: 0.75rem; overflow: hidden; background: oklch(var(--muted)); }
.yd-skeleton--text { height: 0.875rem; width: 100%; border-radius: 0.25rem; }
.yd-skeleton--circle { width: 2.5rem; height: 2.5rem; border-radius: 50%; }
.yd-skeleton--rect { width: 100%; height: 6rem; border-radius: var(--yd-surface-radius); }
.yd-skeleton--button { width: 6rem; height: var(--yd-control-height); border-radius: var(--yd-control-radius); }
.yd-skeleton--image { width: 8rem; height: 6rem; border-radius: var(--yd-surface-radius); }
.yd-skeleton--animated { background-image: linear-gradient(100deg, transparent 25%, oklch(var(--background) / 0.6) 50%, transparent 75%); background-size: 200% 100%; animation: yd-skeleton-shimmer 1.5s ease-in-out infinite; }
@keyframes yd-skeleton-shimmer { to { background-position-x: -200%; } }
@media (prefers-reduced-motion: reduce) { .yd-skeleton--animated { animation-duration: 3s; } }
</style>
