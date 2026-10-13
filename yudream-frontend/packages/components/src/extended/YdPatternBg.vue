<script setup lang="ts">
import { computed } from 'vue'
import './extended.css'

const props = withDefaults(defineProps<{
  variant?: 'grid' | 'dots' | 'lines'
  size?: 'sm' | 'md' | 'lg'
  mask?: 'ellipse' | 'circle' | 'none'
  animate?: boolean
  direction?: 'top' | 'bottom' | 'left' | 'right' | 'top-right' | 'top-left' | 'bottom-right' | 'bottom-left'
  speed?: number
}>(), { variant: 'grid', size: 'md', mask: 'ellipse', direction: 'top', speed: 10000 })
const spacing = computed(() => ({ sm: 16, md: 28, lg: 44 })[props.size])
const offset = computed(() => `${props.direction.includes('left') ? -spacing.value : props.direction.includes('right') ? spacing.value : 0}px ${props.direction.includes('top') ? -spacing.value : props.direction.includes('bottom') ? spacing.value : 0}px`)
</script>

<template>
  <div class="yd-pattern">
    <div class="yd-pattern-layer yd-animated" :class="[variant, { animate }]" :style="{ 'backgroundSize': `${spacing}px ${spacing}px`, 'maskImage': mask === 'none' ? undefined : `radial-gradient(${mask}, black, transparent 75%)`, 'animationDuration': `calc(${speed}ms * var(--yd-motion-loop-scale))`, '--yd-pattern-offset': offset }" aria-hidden="true" /><div class="yd-pattern-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.yd-pattern {
  position: relative;
  min-height: 120px;
  overflow: hidden;
}

.yd-pattern-layer {
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: 0.5;
}

.yd-pattern-content {
  position: relative;
}

.grid {
  background-image: linear-gradient(oklch(var(--border)) 1px, transparent 1px), linear-gradient(90deg, oklch(var(--border)) 1px, transparent 1px);
}

.dots {
  background-image: radial-gradient(oklch(var(--muted-foreground)) 1px, transparent 1px);
}

.lines {
  background-image: linear-gradient(45deg, oklch(var(--border)) 2%, transparent 2%, transparent 50%, oklch(var(--border)) 50%, oklch(var(--border)) 52%, transparent 52%);
}

.animate {
  animation: yd-pattern-shift 10s linear infinite;
}

@keyframes yd-pattern-shift {
  to {
    background-position: var(--yd-pattern-offset);
  }
}
</style>
