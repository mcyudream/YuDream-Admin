<template>
  <div ref="chartRef" class="dataviz-graph-chart" :style="containerStyle" />
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue'
import { computed, ref } from 'vue'
import type { ChartDataset, ChartTheme } from '../types'
import { useD3Graph } from '../composables'

const props = withDefaults(defineProps<{
  dataset: ChartDataset
  theme?: ChartTheme
  height?: number | string
}>(), {
  theme: 'light',
  height: 400,
})

const chartRef = ref<HTMLElement | null>(null)

const containerStyle = computed<CSSProperties>(() => ({
  width: '100%',
  height: typeof props.height === 'number' ? `${props.height}px` : props.height,
}))

useD3Graph(chartRef, computed(() => props.dataset), computed(() => props.theme))
</script>

<style scoped>
.dataviz-graph-chart {
  position: relative;
}
</style>
