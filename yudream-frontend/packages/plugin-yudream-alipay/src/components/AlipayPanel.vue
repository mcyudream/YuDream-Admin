<template>
  <section class="alipay-panel">
    <header class="alipay-panel__head">
      <span>{{ eyebrow }}</span>
      <strong>{{ title }}</strong>
    </header>
    <slot />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  eyebrow: string
}>()
</script>
