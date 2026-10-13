<script setup lang="ts">
import FaButton from '../basic/button/index.vue'
import YdBorderBeam from './YdBorderBeam.vue'
import './extended.css'

defineProps<{
  disabled?: boolean
  loading?: boolean
  duration?: number
  colors?: string[]
}>()
</script>

<template>
  <FaButton class="yd-gradient-button yd-control" variant="outline" :disabled="disabled" :loading="loading">
    <slot /><YdBorderBeam v-if="!disabled" :duration="duration" :color-from="colors?.[0]" :color-to="colors?.[1]" />
  </FaButton>
</template>

<style scoped>
.yd-gradient-button {
  position: relative;
  padding-inline: 24px;
  box-shadow: var(--yd-surface-shadow);
  isolation: isolate;
}
</style>
