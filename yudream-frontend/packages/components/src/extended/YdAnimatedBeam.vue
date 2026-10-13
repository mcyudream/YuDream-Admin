<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { useEventListener, useResizeObserver } from '@vueuse/core'
import { computed, onMounted, ref, useId, watch } from 'vue'
import './extended.css'

const props = withDefaults(defineProps<{
  containerRef?: HTMLElement | null
  fromRef?: HTMLElement | null
  toRef?: HTMLElement | null
  curvature?: number
  reverse?: boolean
  duration?: number
  delay?: number
  pathWidth?: number
  pathColor?: string
  beamColor?: string
  startXOffset?: number
  startYOffset?: number
  endXOffset?: number
  endYOffset?: number
}>(), { curvature: 0, duration: 4, delay: 0, pathWidth: 2, startXOffset: 0, startYOffset: 0, endXOffset: 0, endYOffset: 0 })
const svg = ref<SVGSVGElement>()
const path = ref('')
const id = useId()
function measure() {
  const parent = props.containerRef || svg.value?.parentElement
  if (!parent || !props.fromRef || !props.toRef) {
    path.value = ''
    return
  }
  const base = parent.getBoundingClientRect()
  const start = props.fromRef.getBoundingClientRect()
  const end = props.toRef.getBoundingClientRect()
  const x1 = start.left + start.width / 2 - base.left + props.startXOffset
  const y1 = start.top + start.height / 2 - base.top + props.startYOffset
  const x2 = end.left + end.width / 2 - base.left + props.endXOffset
  const y2 = end.top + end.height / 2 - base.top + props.endYOffset
  path.value = `M ${x1} ${y1} Q ${(x1 + x2) / 2} ${(y1 + y2) / 2 - props.curvature} ${x2} ${y2}`
}
useResizeObserver([() => props.containerRef || svg.value?.parentElement, () => props.fromRef, () => props.toRef], measure)
useEventListener('resize', measure)
watch(() => [props.fromRef, props.toRef, props.curvature, props.startXOffset, props.startYOffset, props.endXOffset, props.endYOffset], measure, { flush: 'post' })
onMounted(measure)
const style = computed<CSSProperties>(() => ({ animationDuration: `calc(${props.duration}s * var(--yd-motion-loop-scale))`, animationDelay: `${props.delay}s`, animationDirection: props.reverse ? 'reverse' : 'normal' }))
defineExpose({ refresh: measure })
</script>

<template>
  <svg ref="svg" class="yd-beam" aria-hidden="true"><path :id="id" :d="path" fill="none" :stroke="pathColor || 'oklch(var(--border))'" :stroke-width="pathWidth" /><path :d="path" pathLength="100" fill="none" :stroke="beamColor || 'var(--yd-emphasis)'" :stroke-width="pathWidth" stroke-linecap="round" stroke-dasharray="12 88" class="yd-beam-light yd-animated" :style="style" /></svg>
</template>

<style scoped>
.yd-beam {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  overflow: visible;
  pointer-events: none;
}

.yd-beam-light {
  animation: yd-beam-flow 4s linear infinite;
}

@keyframes yd-beam-flow {
  from {
    stroke-dashoffset: 100;
  }

  to {
    stroke-dashoffset: 0;
  }
}
</style>
