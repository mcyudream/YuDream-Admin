<script setup lang="ts">
import { computed } from 'vue'
import { useYdMotion } from './motion'
import './extended.css'

const props = withDefaults(defineProps<{
  quantity?: number
  color?: string
  speed?: number
  size?: number
}>(), { quantity: 40, speed: 1, size: 3 })
const { preset, reduced } = useYdMotion()
const particles = computed(() => {
  const density = (preset.value === 'compact' ? 0.35 : preset.value === 'calm' ? 0.6 : 1) * (reduced.value ? 0.7 : 1)
  const count = Number.isFinite(props.quantity) && props.quantity > 0 ? Math.min(160, Math.max(1, Math.floor(props.quantity * density))) : 0
  return Array.from({ length: count }, (_, i) => ({ x: (i * 61.83) % 100, y: (i * 37.37) % 100, size: props.size * (0.5 + (i % 5) / 5), duration: (7 + i % 9) / Math.max(0.1, props.speed), delay: -i * 0.73 }))
})
</script>

<template>
  <div class="yd-particles" aria-hidden="true" :style="{ 'color': color || 'var(--yd-emphasis)', '--yd-particle-distance': reduced ? 0.3 : 1 }">
    <i v-for="(particle, i) in particles" :key="i" class="yd-animated" :style="{ left: `${particle.x}%`, top: `${particle.y}%`, width: `${particle.size}px`, height: `${particle.size}px`, animationDuration: `calc(${particle.duration}s * var(--yd-motion-loop-scale))`, animationDelay: `${particle.delay}s` }" />
  </div>
</template>

<style scoped>
.yd-particles {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
  border-radius: inherit;
}

.yd-particles i {
  position: absolute;
  background: currentcolor;
  border-radius: 50%;
  opacity: 0.35;
  animation: yd-particle 12s ease-in-out infinite alternate;
}

@keyframes yd-particle {
  from {
    opacity: 0.12;
    transform: translate(calc(-12px * var(--yd-particle-distance)), calc(18px * var(--yd-particle-distance)));
  }

  to {
    opacity: 0.7;
    transform: translate(calc(18px * var(--yd-particle-distance)), calc(-22px * var(--yd-particle-distance)));
  }
}
</style>
