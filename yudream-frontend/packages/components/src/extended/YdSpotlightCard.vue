<script setup lang="ts">
import { ref } from 'vue'
import { useYdMotion } from './motion'
import './extended.css'

withDefaults(defineProps<{
  gradientSize?: number
  gradientColor?: string
  gradientOpacity?: number
}>(), { gradientSize: 260, gradientColor: 'var(--yd-emphasis)', gradientOpacity: 0.12 })
const { reduced } = useYdMotion()
const x = ref(0)
const y = ref(0)
const active = ref(false)
function move(event: PointerEvent) {
  if (event.pointerType === 'touch') {
    return
  }
  ;
  const box = (event.currentTarget as HTMLElement).getBoundingClientRect()
  x.value = event.clientX - box.left
  y.value = event.clientY - box.top
  active.value = true
}
</script>

<template>
  <div class="yd-surface yd-spotlight" @pointermove="move" @pointerleave="active = false">
    <div class="yd-light yd-animated" aria-hidden="true" :style="{ opacity: active ? gradientOpacity * (reduced ? 0.6 : 1) : 0, background: `radial-gradient(${gradientSize}px circle at ${x}px ${y}px, ${gradientColor}, transparent 75%)` }" /><div class="yd-pad yd-spotlight-content">
      <slot />
    </div>
  </div>
</template>

<style scoped>
.yd-spotlight {
  position: relative;
  overflow: hidden;
}

.yd-light {
  position: absolute;
  inset: 0;
  pointer-events: none;
  transition: opacity var(--yd-motion-duration-normal);
}

.yd-spotlight-content {
  position: relative;
  z-index: 1;
}
</style>
