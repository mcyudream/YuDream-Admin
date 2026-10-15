<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  vertical?: boolean
  responsive?: boolean
}>(), { vertical: false, responsive: true })
</script>

<template>
  <div class="yd-stats" :class="{ 'yd-stats-vertical': vertical, 'yd-stats-responsive': responsive && !vertical }">
    <slot />
  </div>
</template>

<style scoped>
.yd-stats { display: flex; gap: var(--yd-content-gap); min-width: 0; }
.yd-stats-vertical { flex-direction: column; }
.yd-stats-responsive { flex-wrap: wrap; }
.yd-stats > :deep(*) { flex: 1 1 180px; min-width: 0; }
.yd-stats-vertical > :deep(*) { flex-basis: auto; }
</style>
