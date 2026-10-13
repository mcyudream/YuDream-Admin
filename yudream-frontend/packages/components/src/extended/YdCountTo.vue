<script setup lang="ts">
import { computed, onMounted, onScopeDispose, ref, watch } from 'vue'
import { useYdMotion } from './motion'

const props = withDefaults(defineProps<{
  startVal?: number
  endVal: number
  autoplay?: boolean
  duration?: number
  delay?: number
  decimals?: number
  separator?: string
  prefix?: string
  suffix?: string
}>(), { startVal: 0, autoplay: true, duration: 1600, delay: 0, decimals: 0, separator: ',', prefix: '', suffix: '' })
const emit = defineEmits<{
  started: [
  ]
  finished: [
  ]
}>()
const { reduced, tempo } = useYdMotion()
const current = ref(Number.isFinite(props.startVal) ? props.startVal : 0)
let frame = 0
let timer: ReturnType<typeof setTimeout> | undefined
function stop() {
  if (typeof cancelAnimationFrame !== 'undefined') {
    cancelAnimationFrame(frame)
  }
  clearTimeout(timer)
}
function start() {
  stop()
  if (!Number.isFinite(props.endVal)) {
    return
  }
  emit('started')
  if (props.duration <= 0 || typeof requestAnimationFrame === 'undefined') {
    current.value = props.endVal
    emit('finished')
    return
  }
  const from = current.value
  const duration = Number.isFinite(props.duration) ? props.duration : 1600
  timer = setTimeout(() => {
    const began = performance.now()
    const target = props.endVal
    function tick(now: number) {
      const progress = Math.min(1, (now - began) / Math.max(1, duration * tempo.value))
      const eased = reduced.value ? progress * (2 - progress) : 1 - (1 - progress) ** 3
      current.value = from + (target - from) * eased
      if (progress < 1) {
        frame = requestAnimationFrame(tick)
      }
      else {
        emit('finished')
      }
    }
    frame = requestAnimationFrame(tick)
  }, Math.max(0, Number.isFinite(props.delay) ? props.delay : 0))
}
function reset() {
  stop()
  current.value = Number.isFinite(props.startVal) ? props.startVal : 0
}
const formatted = computed(() => {
  const decimals = Number.isFinite(props.decimals) ? Math.min(20, Math.max(0, Math.floor(props.decimals))) : 0
  const [integer, decimal] = current.value.toFixed(decimals).split('.')
  return props.prefix + integer.replace(/\B(?=(\d{3})+(?!\d))/g, props.separator) + (decimal ? `.${decimal}` : '') + props.suffix
})
onMounted(() => {
  if (props.autoplay) {
    start()
  }
})
watch(() => props.endVal, () => {
  if (props.autoplay) {
    start()
  }
})
onScopeDispose(stop)
defineExpose({ start, reset, stop })
</script>

<template>
  <span style="font-variant-numeric: tabular-nums;">{{ formatted }}</span>
</template>
