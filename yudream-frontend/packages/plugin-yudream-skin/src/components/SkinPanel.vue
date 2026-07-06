<template>
  <div class="panel" :class="{ stack }">
    <div v-if="$slots.header || title" class="panel-head">
      <strong>{{ title }}</strong>
      <slot name="header" />
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  stack?: boolean
}>()
</script>
