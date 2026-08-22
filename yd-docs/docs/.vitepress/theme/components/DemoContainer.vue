<script setup lang="ts">
withDefaults(defineProps<{
  padded?: boolean
}>(), {
  padded: true,
})
</script>

<template>
  <div class="yd-demo-container" :class="{ 'yd-demo-container--padded': padded }">
    <slot />
  </div>
</template>

<style scoped>
.yd-demo-container {
  min-width: 0;
  overflow-x: auto;
  border: 1px solid var(--color-border-2, var(--vp-c-divider));
  border-radius: var(--border-radius-medium, 6px);
  background: var(--color-bg-2, var(--vp-c-bg-soft));
}

.yd-demo-container--padded {
  padding: 16px;
}

.yd-demo-container :deep(> *) {
  max-width: 100%;
}
</style>
