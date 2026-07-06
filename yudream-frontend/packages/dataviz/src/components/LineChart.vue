<template>
  <BaseChart v-bind="$attrs" :dataset="lineDataset" :theme="theme" :height="height" />
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

const lineDataset = computed<ChartDataset>(() => ({
  ...props.dataset,
  chartType: 'line' as const,
}))
</script>
