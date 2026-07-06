<template>
  <BaseChart v-bind="$attrs" :dataset="barDataset" :theme="theme" :height="height" />
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { ChartDataset } from '../types'
import BaseChart from './BaseChart.vue'

const props = defineProps<{
  dataset: ChartDataset
  theme?: 'light' | 'dark'
  height?: number | string
}>()

const barDataset = computed<ChartDataset>(() => ({
  ...props.dataset,
  chartType: 'bar' as const,
}))
</script>
