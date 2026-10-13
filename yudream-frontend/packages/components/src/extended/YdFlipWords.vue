<script setup lang="ts">
import { useDocumentVisibility, useIntervalFn } from '@vueuse/core'
import { computed, onMounted, ref, watch } from 'vue'
import { useYdMotion } from './motion'
import './extended.css'

const props = withDefaults(defineProps<{
  words: string[]
  duration?: number
}>(), { duration: 2800 })
const { reduced } = useYdMotion()
const visibility = useDocumentVisibility()
const index = ref(0)
const mounted = ref(false)
const word = computed(() => props.words[index.value % Math.max(1, props.words.length)] || '')
const { pause, resume } = useIntervalFn(() => {
  index.value++
}, () => Math.max(500, Number.isFinite(props.duration) ? props.duration : 2800) * (reduced.value ? 1.5 : 1), { immediate: false })
watch([visibility, () => props.words.length, mounted], ([visible, count, ready]) => {
  if (!ready || visible !== 'visible' || count < 2) {
    pause()
  }
  else {
    resume()
  }
}, { immediate: true })
onMounted(() => {
  mounted.value = true
})
</script>

<template>
  <span class="yd-flip-words" :class="{ reduced }"><Transition name="yd-word" mode="out-in"><span :key="word">{{ word }}</span></Transition></span>
</template>

<style scoped>
.yd-flip-words {
  display: inline-grid;
  font-weight: 650;
  color: var(--yd-emphasis);
  perspective: 500px;
}

.yd-word-enter-active,
.yd-word-leave-active {
  transition: transform var(--yd-motion-duration-slow) var(--yd-motion-ease-spring), opacity var(--yd-motion-duration-normal);
}

.yd-word-enter-from {
  opacity: 0;
  transform: rotateX(-70deg) translateY(0.4em);
}

.yd-word-leave-to {
  opacity: 0;
  transform: rotateX(70deg) translateY(-0.4em);
}

.reduced .yd-word-enter-from {
  transform: rotateX(-15deg) translateY(0.08em);
}

.reduced .yd-word-leave-to {
  transform: rotateX(15deg) translateY(-0.08em);
}
</style>
