<template>
  <section class="student-info-panel">
    <header class="student-info-panel__head">
      <div>
        <span v-if="eyebrow">{{ eyebrow }}</span>
        <strong>{{ title }}</strong>
      </div>
      <slot name="header" />
    </header>
    <slot />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  eyebrow?: string
}>()
</script>
