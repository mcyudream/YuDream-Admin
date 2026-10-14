<script setup lang="ts">
import FaIcon from '../basic/icon/index.vue'
import './extended.css'

defineProps<{
  title: string
  digital: string | number
  titleTips?: string
  icon?: string
  description?: string
  trend?: 'up' | 'stable' | 'down'
}>()
</script>

<template>
  <article class="yd-surface yd-pad yd-stack yd-digital-card">
    <header class="yd-row yd-muted">
      <span :title="titleTips">{{ title }}</span><FaIcon v-if="icon" :name="icon" />
    </header>
    <div class="yd-digital-value">
      <slot name="value">
        {{ digital }}
      </slot>
    </div>
    <div v-if="description || trend || $slots.default" class="yd-row yd-muted">
      <FaIcon v-if="trend" :name="`morph:${trend === 'up' ? 'trending-up' : trend === 'down' ? 'trending-down' : 'minus'}`" /><slot>{{ description }}</slot>
    </div>
  </article>
</template>

<style scoped>
header {
  justify-content: space-between;
  font-size: 0.875rem;
}

.yd-digital-value {
  font-size: clamp(1.5rem, 3vw, 2.25rem);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.04em;
}

.yd-digital-card {
  position: relative;
  overflow: hidden;
}

.yd-digital-card::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  content: "";
  background: var(--yd-emphasis);
  opacity: 0.4;
}
</style>
