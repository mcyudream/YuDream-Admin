<template>
  <BaseChart v-bind="$attrs" :dataset="pieDataset" :theme="theme" :height="height" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChartDataset, ChartTheme } from '../types'
import BaseChart from './BaseChart.vue'

const props = defineProps<{
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  height?: number | string
}>()

const pieDataset = computed<ChartDataset>(() => ({
  ...props.dataset,
  chartType: 'pie' as const,
}))
</script>
