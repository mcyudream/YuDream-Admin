<script setup lang="ts">
import type { ComputedRef } from 'vue'
import { inject } from 'vue'
import YdSpotlightCard from './YdSpotlightCard.vue'

const config = inject<ComputedRef<{
  color?: string
  size?: number
  border?: number
  radius?: number
}> | undefined>('yd-glowy-card', undefined)
</script>

<template>
  <YdSpotlightCard class="yd-glowy-card" :gradient-color="config?.color" :gradient-size="config?.size" :style="{ borderWidth: `${config?.border ?? 1}px`, borderRadius: config?.radius !== undefined ? `${config.radius}px` : undefined }">
    <slot />
  </YdSpotlightCard>
</template>

<style scoped>
.yd-glowy-card {
  flex: 1;
  min-width: 200px;
  transition: box-shadow var(--yd-motion-duration-normal);
}

.yd-glowy-card:hover {
  box-shadow: var(--yd-surface-shadow-hover), 0 0 0 1px oklch(var(--primary) / 25%);
}
</style>
