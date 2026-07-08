<template>
  <div class="mc-trend-chart">
    <div class="mc-trend-summary">
      <div>
        <span>当前在线</span>
        <strong>{{ latestOnline }}</strong>
      </div>
      <div>
        <span>峰值</span>
        <strong>{{ peakOnline }}</strong>
      </div>
      <div>
        <span>样本</span>
        <strong>{{ normalizedItems.length }}</strong>
      </div>
    </div>

    <div v-if="points.length" class="mc-chart-frame">
      <svg class="mc-trend-svg" viewBox="0 0 640 220" role="img" aria-label="在线人数趋势">
        <line
          v-for="(line, index) in gridLines"
          :key="`${line.value}-${index}`"
          class="mc-trend-grid"
          :x1="plotLeft"
          :x2="plotRight"
          :y1="line.y"
          :y2="line.y"
        />
        <text
          v-for="(line, index) in gridLines"
          :key="`label-${line.value}-${index}`"
          class="mc-trend-axis"
          :x="plotLeft - 8"
          :y="line.y + 4"
          text-anchor="end"
        >
          {{ line.value }}
        </text>
        <path class="mc-trend-area" :d="areaPath" />
        <path class="mc-trend-line" :d="linePath" />
        <circle
          v-for="(point, index) in visibleDots"
          :key="`${point.checkedAt}-${index}`"
          class="mc-trend-dot"
          :cx="point.x"
          :cy="point.y"
          r="3"
        />
      </svg>
      <div class="mc-trend-labels">
        <span>{{ firstLabel }}</span>
        <span>{{ lastLabel }}</span>
      </div>
    </div>
    <div v-else class="mc-empty compact">暂无趋势数据</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MinecraftStatusSnapshot, TimeValue } from '../types'

const props = defineProps<{
  items: MinecraftStatusSnapshot[]
  maxPlayers?: number
  formatTime: (value?: TimeValue) => string
}>()

const plotLeft = 42
const plotRight = 624
const plotTop = 18
const plotBottom = 178
const plotWidth = plotRight - plotLeft
const plotHeight = plotBottom - plotTop

const normalizedItems = computed(() => props.items
  .map(item => ({
    ...item,
    checkedAt: normalizeTime(item.checkedAt),
    onlinePlayers: Math.max(Number(item.onlinePlayers || 0), 0),
    maxPlayers: Math.max(Number(item.maxPlayers || 0), 0),
  }))
  .filter(item => item.checkedAt > 0)
  .sort((first, second) => first.checkedAt - second.checkedAt))

const latestOnline = computed(() => normalizedItems.value.at(-1)?.onlinePlayers ?? 0)
const peakOnline = computed(() => Math.max(0, ...normalizedItems.value.map(item => item.onlinePlayers)))
const yMax = computed(() => Math.max(1, Number(props.maxPlayers || 0), ...normalizedItems.value.map(item => item.maxPlayers), peakOnline.value))

const points = computed(() => {
  const items = normalizedItems.value
  const firstAt = items[0]?.checkedAt ?? 0
  const lastAt = items.at(-1)?.checkedAt ?? firstAt
  const span = Math.max(lastAt - firstAt, 1)
  return items.map(item => {
    const x = items.length === 1 ? plotRight : plotLeft + ((item.checkedAt - firstAt) / span) * plotWidth
    const y = yFor(item.onlinePlayers)
    return { ...item, x, y }
  })
})

const visibleDots = computed(() => {
  if (points.value.length <= 48) {
    return points.value
  }
  return points.value.filter((_, index) => index === points.value.length - 1)
})

const linePath = computed(() => points.value.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' '))
const areaPath = computed(() => {
  const items = points.value
  if (!items.length) {
    return ''
  }
  const first = items[0]
  const last = items[items.length - 1]
  return `${linePath.value} L ${last.x.toFixed(1)} ${plotBottom} L ${first.x.toFixed(1)} ${plotBottom} Z`
})

const gridLines = computed(() => [yMax.value, Math.floor(yMax.value / 2), 0].map(value => ({
  value,
  y: yFor(value),
})))

const firstLabel = computed(() => props.formatTime(normalizedItems.value[0]?.checkedAt))
const lastLabel = computed(() => props.formatTime(normalizedItems.value.at(-1)?.checkedAt))

function yFor(value: number) {
  return plotBottom - (Math.max(value, 0) / yMax.value) * plotHeight
}

function normalizeTime(value: TimeValue) {
  if (value == null || value === '') {
    return 0
  }
  if (value instanceof Date) {
    return validTimestamp(value.getTime())
  }
  if (Array.isArray(value)) {
    return normalizeDateArray(value)
  }
  if (typeof value === 'number') {
    return validTimestamp(value)
  }
  const text = value.trim()
  if (!text) {
    return 0
  }
  const numeric = Number(text)
  if (Number.isFinite(numeric)) {
    return validTimestamp(numeric)
  }
  return validTimestamp(Date.parse(text))
}

function normalizeDateArray(value: number[]) {
  if (value.length < 3) {
    return 0
  }
  const [year, month, day, hour = 0, minute = 0, second = 0, nano = 0] = value
  return validTimestamp(new Date(year, month - 1, day, hour, minute, second, Math.floor(nano / 1000000)).getTime())
}

function validTimestamp(value: number) {
  if (!Number.isFinite(value) || value <= 0) {
    return 0
  }
  return value < 10000000000 ? value * 1000 : value
}
</script>
