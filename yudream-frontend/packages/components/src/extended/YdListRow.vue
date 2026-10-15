<script setup lang="ts">
import './extended.css'

withDefaults(defineProps<{
  grow?: boolean
  wrap?: boolean
}>(), { grow: false, wrap: false })
</script>

<template>
  <li class="yd-list-row" :class="{ 'yd-list-row-grow': grow, 'yd-list-row-wrap': wrap }">
    <slot />
  </li>
</template>

<style scoped>
.yd-list-row {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 10px 12px;
  border-radius: var(--yd-control-radius);
}

.yd-list-row-grow > :nth-child(2) { flex: 1; min-width: 0; }
.yd-list-row-wrap { flex-wrap: wrap; }
</style>
