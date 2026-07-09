<script setup lang="ts">
defineProps<{
  title: string
  subtle?: string
}>()
</script>

<template>
  <section class="pp-panel">
    <header class="pp-panel-head">
      <div>
        <h3>{{ title }}</h3>
        <span v-if="subtle">{{ subtle }}</span>
      </div>
      <slot name="action" />
    </header>
    <slot />
  </section>
</template>
