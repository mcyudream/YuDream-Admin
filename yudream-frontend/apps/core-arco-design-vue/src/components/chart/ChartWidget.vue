<script setup lang="ts">
import { computed } from 'vue'
import type { ChartDataset } from '@yudream/dataviz'
import { BarChart, BaseChart, GraphChart, LineChart, PieChart, SankeyChart } from '@yudream/dataviz'

const props = defineProps<{
  dataset: ChartDataset
  height?: number | string
}>()

const theme = computed<'light' | 'dark'>(() => {
  if (typeof document === 'undefined') {
    return 'light'
  }
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
})

const chartType = computed(() => props.dataset.chartType ?? 'bar')

const chartProps = computed(() => ({
  dataset: props.dataset,
  theme: theme.value,
  height: props.height,
}))
</script>

<template>
  <BarChart v-if="chartType === 'bar'" v-bind="chartProps" />
  <LineChart v-else-if="chartType === 'line'" v-bind="chartProps" />
  <PieChart v-else-if="chartType === 'pie'" v-bind="chartProps" />
  <SankeyChart v-else-if="chartType === 'sankey'" v-bind="chartProps" />
  <GraphChart v-else-if="chartType === 'graph'" v-bind="chartProps" />
  <BaseChart v-else v-bind="chartProps" />
</template>
