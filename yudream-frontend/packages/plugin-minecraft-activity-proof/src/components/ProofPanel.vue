<script setup lang="ts">
defineProps<{
  title: string
  eyebrow?: string
}>()
</script>

<template>
  <section class="proof-panel">
    <header>
      <span v-if="eyebrow">{{ eyebrow }}</span>
      <h3>{{ title }}</h3>
      <slot name="action" />
    </header>
    <slot />
  </section>
</template>
