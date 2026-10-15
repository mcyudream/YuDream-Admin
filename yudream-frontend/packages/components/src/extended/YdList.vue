<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  divided?: boolean
  dense?: boolean
}>(), { divided: false, dense: false })
</script>

<template>
  <ul class="yd-list" :class="{ 'yd-list-divided': divided, 'yd-list-dense': dense }" :data-divided="divided || undefined">
    <slot />
  </ul>
</template>

<style scoped>
.yd-list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.yd-list-divided { gap: 0; }
.yd-list-dense { gap: 2px; }
:global(.yd-list-divided > .yd-list-row + .yd-list-row) { border-top: 1px solid oklch(var(--border)); border-radius: 0; }
</style>
