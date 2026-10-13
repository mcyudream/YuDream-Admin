<script setup lang="ts">
import { ref } from 'vue'
import './extended.css'

withDefaults(defineProps<{
  rotate?: 'x' | 'y'
}>(), { rotate: 'y' })
const flipped = ref(false)
</script>

<template>
  <div class="yd-flip" :class="{ flipped }" :style="{ '--yd-flip-axis': rotate === 'x' ? '1, 0, 0' : '0, 1, 0' }" @pointerenter="($event.pointerType === 'mouse') && (flipped = true)" @pointerleave="($event.pointerType === 'mouse') && (flipped = false)">
    <div class="yd-flip-inner yd-animated">
      <div class="yd-flip-front yd-surface yd-pad" :inert="flipped">
        <slot />
      </div><div class="yd-flip-back yd-surface yd-pad" :inert="!flipped">
        <slot name="back" />
      </div>
    </div>
    <button type="button" class="yd-flip-toggle yd-focus" :aria-pressed="flipped" aria-label="翻转卡片" @click="flipped = !flipped">
      ↻
    </button>
  </div>
</template>

<style scoped>
.yd-flip {
  position: relative;
  perspective: 1000px;
}

.yd-flip-inner {
  display: grid;
  transform-style: preserve-3d;
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring);
}

.flipped .yd-flip-inner {
  transform: rotate3d(var(--yd-flip-axis), 180deg);
}

.yd-flip-front,
.yd-flip-back {
  grid-area: 1 / 1;
  min-height: 160px;
  backface-visibility: hidden;
}

.yd-flip-back {
  transform: rotate3d(var(--yd-flip-axis), 180deg);
}

.yd-flip-toggle {
  position: absolute;
  inset: 10px 10px auto auto;
  width: 28px;
  height: 28px;
  color: oklch(var(--foreground));
  background: oklch(var(--muted));
  border-radius: 50%;
}
</style>
