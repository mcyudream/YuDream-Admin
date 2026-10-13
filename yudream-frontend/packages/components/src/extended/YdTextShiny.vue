<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  duration?: number
  delay?: number
}>(), { duration: 3, delay: 0 })
</script>

<template>
  <span class="yd-text-shiny yd-animated" :style="{ animationDuration: `calc(${duration}s * var(--yd-motion-loop-scale))`, animationDelay: `${delay}s` }"><slot /></span>
</template>

<style scoped>
.yd-text-shiny {
  display: inline-block;
  color: oklch(var(--foreground));
  background: linear-gradient(110deg, currentcolor 35%, oklch(var(--muted-foreground)) 50%, currentcolor 65%);
  background-clip: text;
  background-size: 250% 100%;
  animation: yd-shine 3s linear infinite;
  -webkit-text-fill-color: transparent;
}

@keyframes yd-shine {
  from {
    background-position: 100% 0;
  }

  to {
    background-position: -100% 0;
  }
}
</style>
