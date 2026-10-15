<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  deadline: number | Date | string
  format?: string
  autoStart?: boolean
}>(), { format: 'HH:mm:ss', autoStart: true })
const emit = defineEmits<{ finish: [] }>()
const running = ref(props.autoStart)
const now = ref(Date.now())
let timer: ReturnType<typeof setInterval> | undefined
const deadlineMs = computed(() => props.deadline instanceof Date ? props.deadline.getTime() : typeof props.deadline === 'number' ? props.deadline : Date.parse(props.deadline))
const remaining = computed(() => Math.max(0, deadlineMs.value - now.value))
const parts = computed(() => {
  const total = Math.floor(remaining.value / 1000)
  return { HH: String(Math.floor(total / 3600)).padStart(2, '0'), mm: String(Math.floor(total / 60) % 60).padStart(2, '0'), ss: String(total % 60).padStart(2, '0'), DD: String(Math.floor(total / 86400)).padStart(2, '0') }
})
const text = computed(() => props.format.replace(/DD|HH|mm|ss/g, token => parts.value[token as keyof typeof parts.value]))
function tick() {
  now.value = Date.now()
  if (remaining.value <= 0) {
    stop()
    emit('finish')
  }
}
function start() {
  if (running.value) {
    return
  }
  running.value = true
  tick()
  timer = setInterval(tick, 250)
}
function stop() {
  running.value = false
  if (timer) {
    clearInterval(timer)
  }
  timer = undefined
}
function reset() {
  now.value = Date.now()
  start()
}
watch(() => props.deadline, reset)
onMounted(() => {
  if (running.value) {
    tick()
    timer = setInterval(tick, 250)
  }
})
onBeforeUnmount(stop)
defineExpose({ start, stop, reset })
</script>

<template>
  <time class="yd-countdown" :datetime="new Date(deadlineMs).toISOString()"><slot :remaining="remaining" :text="text">{{ text }}</slot></time>
</template>
