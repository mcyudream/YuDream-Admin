<script setup lang="ts">
import { computed, provide } from 'vue'

const props = withDefaults(defineProps<{
  color?: string
  size?: number
  border?: number
  radius?: number
}>(), { color: 'var(--yd-emphasis)', size: 280, border: 1 })
provide('yd-glowy-card', computed(() => props))
</script>

<template>
  <div class="yd-glowy-wrapper">
    <slot />
  </div>
</template>

<style scoped>
.yd-glowy-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: var(--yd-content-gap, 16px);
}
</style>
