<template>
  <section class="authlib-panel">
    <div class="authlib-panel__head">
      <div>
        <span v-if="eyebrow">{{ eyebrow }}</span>
        <strong>{{ title }}</strong>
      </div>
      <slot name="header" />
    </div>
    <slot />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  eyebrow?: string
}>()
</script>
