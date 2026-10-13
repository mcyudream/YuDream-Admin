<script setup lang="ts">
import { computed, useId } from 'vue'

const props = withDefaults(defineProps<{
  data: number[]
  width?: number
  height?: number
  type?: 'line' | 'area' | 'bar'
  color?: string
  strokeWidth?: number
  label?: string
}>(), { width: 180, height: 48, type: 'area', strokeWidth: 2, label: '数据趋势' })
const id = useId()
const values = computed(() => props.data.filter(Number.isFinite))
const points = computed(() => {
  const min = Math.min(...values.value, 0)
  const max = Math.max(...values.value, 0)
  const span = max - min || 1
  const pad = props.strokeWidth + 2
  return values.value.map((value, index) => ({ x: pad + index / Math.max(1, values.value.length - 1) * (props.width - 2 * pad), y: pad + (max - value) / span * (props.height - 2 * pad), value }))
})
const barWidth = computed(() => Math.max(1, (props.width - 2 * (props.strokeWidth + 2)) / Math.max(1, values.value.length) * 0.7))
const line = computed(() => points.value.map((p, i) => `${i ? 'L' : 'M'} ${p.x} ${p.y}`).join(' '))
const baseline = computed(() => {
  const min = Math.min(...values.value, 0)
  const max = Math.max(...values.value, 0)
  const pad = props.strokeWidth + 2
  return pad + max / (max - min || 1) * (props.height - 2 * pad)
})
const area = computed(() => points.value.length ? `${line.value} L ${points.value.at(-1)!.x} ${baseline.value} L ${points.value[0].x} ${baseline.value} Z` : '')
</script>

<template>
  <svg :width="width" :height="height" :viewBox="`0 0 ${width} ${height}`" role="img" :aria-label="`${label}：${values.join('、') || '暂无数据'}`" :style="{ color: color || 'var(--yd-emphasis)', maxWidth: '100%' }"><defs><linearGradient :id="id" x1="0" y1="0" x2="0" y2="1"><stop stop-color="currentColor" stop-opacity=".2" /><stop offset="1" stop-color="currentColor" stop-opacity="0" /></linearGradient></defs><template v-if="type === 'bar'"><rect v-for="(point, index) in points" :key="index" :x="strokeWidth + 2 + (index + 0.15) * (width - 2 * (strokeWidth + 2)) / Math.max(1, points.length)" :y="Math.min(point.y, baseline)" :width="barWidth" :height="Math.max(1, Math.abs(baseline - point.y))" rx="2" fill="currentColor" /></template><template v-else><path v-if="type === 'area'" :d="area" :fill="`url(#${id})`" /><path :d="line" fill="none" stroke="currentColor" :stroke-width="strokeWidth" stroke-linecap="round" stroke-linejoin="round" /><circle v-if="points.length === 1" :cx="points[0].x" :cy="points[0].y" :r="strokeWidth" fill="currentColor" /></template></svg>
</template>
