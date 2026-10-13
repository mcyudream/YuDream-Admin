<script setup lang="ts">
import { useElementVisibility } from '@vueuse/core'
import { ref, watch } from 'vue'
import { useYdMotion } from './motion'
import './extended.css'

withDefaults(defineProps<{
  duration?: number
  delay?: number
  blur?: string
  yOffset?: number
}>(), { duration: 0.7, delay: 0.08, blur: '8px', yOffset: 16 })
const root = ref<HTMLElement>()
const visible = useElementVisibility(root)
const revealed = ref(false)
const { reduced } = useYdMotion()
watch(visible, (v) => {
  if (v) {
    revealed.value = true
  }
})
</script>

<template>
  <div ref="root" class="yd-reveal" :class="{ revealed }" :style="{ '--yd-reveal-time': `${reduced ? Math.min(duration, 0.3) : duration}s`, '--yd-reveal-delay': `${reduced ? delay * 0.5 : delay}s`, '--yd-reveal-blur': reduced ? '2px' : blur, '--yd-reveal-offset': `${reduced ? yOffset * 0.2 : yOffset}px` }">
    <slot />
  </div>
</template>

<style scoped>
.yd-reveal :deep(> *) {
  transition: opacity var(--yd-reveal-time), filter var(--yd-reveal-time), transform var(--yd-reveal-time) var(--yd-motion-ease-spring);
  transition-delay: var(--yd-reveal-delay);
}

.yd-reveal:not(.revealed) :deep(> *) {
  opacity: 0;
  filter: blur(var(--yd-reveal-blur));
  transform: translateY(var(--yd-reveal-offset));
}

.yd-reveal :deep(> :nth-child(2)) {
  transition-delay: calc(var(--yd-reveal-delay) * 2);
}

.yd-reveal :deep(> :nth-child(3)) {
  transition-delay: calc(var(--yd-reveal-delay) * 3);
}

.yd-reveal :deep(> :nth-child(4)) {
  transition-delay: calc(var(--yd-reveal-delay) * 4);
}
</style>
