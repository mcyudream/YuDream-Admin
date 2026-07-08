<template>
  <section class="mc-panel">
    <header v-if="title || eyebrow" class="mc-panel__head">
      <div>
        <span v-if="eyebrow">{{ eyebrow }}</span>
        <h3 v-if="title">{{ title }}</h3>
      </div>
      <slot name="actions" />
    </header>
    <slot />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title?: string
  eyebrow?: string
}>()
</script>
