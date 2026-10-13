<script setup lang="ts">
import FaButton from '../basic/button/index.vue'
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

defineProps<{
  text?: string
  disabled?: boolean
  loading?: boolean
}>()
</script>

<template>
  <FaButton type="button" class="yd-interactive-button yd-control" :disabled="disabled" :loading="loading">
    <span class="yd-interactive-label yd-animated"><slot>{{ text }}</slot></span><FaIcon class="yd-interactive-arrow yd-animated" name="morph:arrow-up-right" />
  </FaButton>
</template>

<style scoped>
.yd-interactive-button {
  gap: 14px;
  padding-inline: 22px;
  overflow: hidden;
}

.yd-interactive-label,
.yd-interactive-arrow {
  transition: transform var(--yd-motion-duration-normal) var(--yd-motion-ease-spring);
}

@media (hover: hover) {
  .yd-interactive-button:hover:not(:disabled) .yd-interactive-arrow {
    transform: translate(3px, -3px) rotate(45deg);
  }

  .yd-interactive-button:hover:not(:disabled) .yd-interactive-label {
    transform: translateX(-2px);
  }
}
</style>
