<script setup lang="ts">
import './extended.css'

defineProps<{
  title?: string
  value?: string | number
  description?: string
}>()
</script>

<template>
  <article class="yd-stat yd-surface">
    <div v-if="$slots.figure" class="yd-stat-figure">
      <slot name="figure" />
    </div>
    <div v-if="title || $slots.title" class="yd-stat-title">
      <slot name="title">
        {{ title }}
      </slot>
    </div>
    <div v-if="value !== undefined || $slots.value" class="yd-stat-value">
      <slot name="value">
        {{ value }}
      </slot>
    </div>
    <div v-if="description || $slots.description" class="yd-stat-description">
      <slot name="description">
        {{ description }}
      </slot>
    </div>
    <div v-if="$slots.actions" class="yd-stat-actions">
      <slot name="actions" />
    </div>
  </article>
</template>

<style scoped>
.yd-stat { position: relative; display: grid; align-content: start; gap: 6px; padding: 16px; }
.yd-stat-figure { justify-self: end; grid-row: 1 / span 3; color: var(--yd-emphasis); }
.yd-stat-title,.yd-stat-description { color: oklch(var(--muted-foreground)); font-size: 0.875rem; }
.yd-stat-value { font-size: 1.75rem; font-weight: 700; font-variant-numeric: tabular-nums; }
.yd-stat-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 6px; }
</style>
