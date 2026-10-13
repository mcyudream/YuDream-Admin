<script setup lang="ts">
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

withDefaults(defineProps<{
  icon?: string
  title?: string
  description?: string
}>(), { icon: 'morph:inbox', title: '暂无内容' })
</script>

<template>
  <div class="yd-empty yd-pad">
    <div class="yd-empty-icon">
      <slot name="icon">
        <FaIcon :name="icon" />
      </slot>
    </div>
    <strong><slot name="title">{{ title }}</slot></strong>
    <p v-if="description || $slots.description" class="yd-muted">
      <slot name="description">
        {{ description }}
      </slot>
    </p>
    <slot name="action">
      <slot />
    </slot>
  </div>
</template>

<style scoped>
.yd-empty {
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.yd-empty-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  font-size: 24px;
  color: oklch(var(--muted-foreground));
  background: oklch(var(--muted));
  border: 1px dashed oklch(var(--border));
  border-radius: var(--yd-surface-radius);
}

p {
  max-width: 36ch;
  margin: 0;
  font-size: 0.875rem;
}
</style>
