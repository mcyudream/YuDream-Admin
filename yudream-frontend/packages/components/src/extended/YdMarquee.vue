<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  vertical?: boolean
  reverse?: boolean
  duration?: number
  gap?: string
  pauseOnHover?: boolean
}>(), { duration: 24, gap: '1rem', pauseOnHover: true })
</script>

<template>
  <div class="yd-marquee" :class="{ vertical, reverse, pausable: pauseOnHover }" :style="{ '--marquee-duration': `${duration}s`, '--marquee-gap': gap }">
    <div class="yd-marquee-track yd-animated">
      <div class="yd-marquee-copy">
        <slot />
      </div><div class="yd-marquee-copy yd-marquee-ghost" aria-hidden="true" inert>
        <slot />
      </div>
    </div>
  </div>
</template>

<style scoped>
.yd-marquee {
  min-width: 0;
  overflow: hidden;
}

.yd-marquee-track {
  display: flex;
  gap: var(--marquee-gap);
  width: max-content;
  animation: yd-marquee-x calc(var(--marquee-duration) * var(--yd-motion-loop-scale)) linear infinite;
}

.yd-marquee-copy {
  display: flex;
  flex-shrink: 0;
  gap: var(--marquee-gap);
  align-items: center;
}

.vertical .yd-marquee-track {
  flex-direction: column;
  width: auto;
  height: max-content;
  animation-name: yd-marquee-y;
}

.vertical .yd-marquee-copy {
  flex-direction: column;
}

.reverse .yd-marquee-track {
  animation-direction: reverse;
}

.pausable:hover .yd-marquee-track,
.yd-marquee:focus-within .yd-marquee-track {
  animation-play-state: paused;
}

@keyframes yd-marquee-x {
  to {
    transform: translateX(calc(-50% - var(--marquee-gap) / 2));
  }
}

@keyframes yd-marquee-y {
  to {
    transform: translateY(calc(-50% - var(--marquee-gap) / 2));
  }
}
</style>
