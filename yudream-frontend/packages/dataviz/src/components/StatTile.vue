<template>
  <div class="dataviz-stat-tile" :class="[`dataviz-stat-tile--${resolvedTheme}`]">
    <div class="dataviz-stat-tile__title">{{ title }}</div>
    <div class="dataviz-stat-tile__value">{{ value }}</div>
    <div v-if="trend !== undefined" class="dataviz-stat-tile__trend" :class="trendClass">
      {{ trendText }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  title: string
  value: number | string
  trend?: number
  theme?: 'light' | 'dark'
}>(), {
  theme: 'light',
})

const resolvedTheme = computed(() => props.theme)

const trendClass = computed(() => {
  if (props.trend === undefined) return ''
  return props.trend >= 0 ? 'dataviz-stat-tile__trend--up' : 'dataviz-stat-tile__trend--down'
})

const trendText = computed(() => {
  if (props.trend === undefined) return ''
  const sign = props.trend >= 0 ? '+' : ''
  return `${sign}${props.trend}%`
})
</script>

<style scoped>
.dataviz-stat-tile {
  padding: 16px;
  border-radius: 8px;
  border: 1px solid transparent;
  transition: background-color 0.2s ease;
}

.dataviz-stat-tile--light {
  background-color: #ffffff;
  border-color: #e5e7eb;
  color: #1f2937;
}

.dataviz-stat-tile--dark {
  background-color: #111827;
  border-color: #374151;
  color: #f9fafb;
}

.dataviz-stat-tile__title {
  font-size: 14px;
  opacity: 0.7;
  margin-bottom: 8px;
}

.dataviz-stat-tile__value {
  font-size: 28px;
  font-weight: 600;
  line-height: 1.2;
}

.dataviz-stat-tile__trend {
  margin-top: 8px;
  font-size: 13px;
  font-weight: 500;
}

.dataviz-stat-tile__trend--up {
  color: #10b981;
}

.dataviz-stat-tile__trend--down {
  color: #ef4444;
}
</style>
