<template>
  <div ref="chartRef" class="dataviz-base-chart" :style="containerStyle" />
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { computed, ref } from 'vue'
import type { ChartDataset, ChartTheme } from '../types'
import { useECharts } from '../composables'
import { buildEChartsOption } from '../utils'

const props = withDefaults(defineProps<{
  dataset: ChartDataset
  theme?: ChartTheme
  height?: number | string
}>(), {
  theme: 'light',
  height: 300,
})

const chartRef = ref<HTMLElement | null>(null)

const containerStyle = computed<CSSProperties>(() => ({
  width: '100%',
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
}))

const option = computed(() => buildEChartsOption(props.dataset, props.theme))

useECharts(chartRef, option, computed(() => props.theme))
</script>

<style scoped>
.dataviz-base-chart {
  position: relative;
}
</style>
