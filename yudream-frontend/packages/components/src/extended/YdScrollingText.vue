<script setup lang="ts">
import YdMarquee from './YdMarquee.vue'

withDefaults(defineProps<{
  text?: string
  duration?: number
  reverse?: boolean
}>(), { duration: 18 })
</script>

<template>
  <YdMarquee :duration="duration" :reverse="reverse" class="yd-scrolling-text">
    <span><slot>{{ text }}</slot></span>
  </YdMarquee>
</template>

<style scoped>
.yd-scrolling-text {
  font-size: clamp(1.5rem, 5vw, 4rem);
  font-weight: 700;
  line-height: 1.3;
  letter-spacing: -0.04em;
}
</style>
