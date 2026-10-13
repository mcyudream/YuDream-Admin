<script setup lang="ts">
import type { ChartDataset } from '@/api/modules/platform-dataviz'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiDataviz from '@/api/modules/platform-dataviz'

const props = defineProps<{ card: DashboardCard, onOpen?: (card?: DashboardCard) => void }>()
const loading = ref(true)
const error = ref('')
const dataset = ref<ChartDataset | null>(null)
const selected = ref<number | null>(null)
const config = computed(() => ({
  DATAVIZ_USER_REGISTRATION: { source: 'system', metric: 'user-registration', unit: '位新用户', action: '查看用户', empty: '这段时间还没有新增用户' },
  DATAVIZ_DEPT_CREATED: { source: 'system', metric: 'dept-created', unit: '个新部门', action: '查看部门', empty: '这段时间组织结构保持稳定' },
  DATAVIZ_LOG_ACTIVITY: { source: 'system', metric: 'log-activity', unit: '次接口调用', action: '查看日志', empty: '这段时间还没有接口访问记录' },
  DATAVIZ_CAPABILITY_STATS: { source: 'capability', metric: 'type', unit: '项平台能力', action: '查看能力', empty: '暂时没有平台能力' },
}[props.card.component || ''] || { source: 'capability', metric: 'type', unit: '项平台能力', action: '查看能力', empty: '暂时没有平台能力' }))
const isCategory = computed(() => config.value.source === 'capability')
const rows = computed(() => {
  const dimensions = dataset.value?.dimensions || ['name', 'value']
  return (dataset.value?.source || []).map((row) => {
    const values = Array.isArray(row) ? row : dimensions.map(key => row[key])
    const value = Number(values[1])
    return { label: String(values[0] ?? ''), value: Number.isFinite(value) ? value : 0 }
  })
})
const total = computed(() => rows.value.reduce((sum, row) => sum + row.value, 0))
const max = computed(() => Math.max(1, ...rows.value.map(row => row.value)))
const points = computed(() => rows.value.map((row, i) => ({
  ...row,
  x: 8 + i * 284 / Math.max(1, rows.value.length - 1),
  y: 90 - row.value / max.value * 72,
})))
const line = computed(() => points.value.map(point => `${point.x},${point.y}`).join(' '))
const area = computed(() => points.value.length ? `8,104 ${line.value} 292,104` : '')
const active = computed(() => selected.value === null ? rows.value.at(-1) : rows.value[selected.value])
const format = (value: number) => new Intl.NumberFormat('zh-CN').format(value)
const shortDate = (label: string) => label.replace(/^\d{4}[-/](\d{1,2})[-/](\d{1,2})$/, '$1/$2')
onMounted(load)
async function load() {
  loading.value = true
  error.value = ''
  try {
    const result = await apiDataviz.queryDataset({
      chartType: isCategory.value ? 'bar' : 'line',
      datasetQuery: { source: config.value.source, metric: config.value.metric },
    })
    dataset.value = result.data
  }
  catch (cause) { error.value = cause instanceof Error ? cause.message : '数据暂不可用' }
  finally { loading.value = false }
}
</script>

<template>
  <div class="dashboard-card__content dashboard-chart-stats" :aria-busy="loading">
    <div v-if="loading" class="trend-state">
      <FaIcon name="morph:loader-circle" class="animate-spin" />正在读取数据
    </div>
    <div v-else-if="error" class="trend-state">
      <FaIcon name="morph:cloud-off" /><span>{{ error }}</span>
      <FaButton variant="ghost" size="sm" @click="load">
        重试
      </FaButton>
    </div>
    <template v-else>
      <div class="trend-summary">
        <div><strong>{{ format(total) }}</strong><span>{{ config.unit }}</span></div>
        <span class="trend-period">{{ isCategory ? '按能力分类' : `近 ${rows.length} 天` }}</span>
      </div>
      <div v-if="isCategory" class="trend-categories">
        <div v-for="row in rows" :key="row.label" class="trend-category">
          <span>{{ row.label }}</span><b>{{ format(row.value) }}</b>
          <meter :value="row.value" :max="max" :aria-label="`${row.label}：${row.value}`" />
        </div>
        <p v-if="!rows.length" class="trend-empty">
          {{ config.empty }}
        </p>
      </div>
      <div v-else-if="rows.length && total === 0" class="trend-zero">
        <p>{{ config.empty }}</p>
        <ol class="trend-zero-history" aria-label="每日统计">
          <li v-for="row in rows" :key="row.label" :aria-label="`${row.label}：${format(row.value)}${config.unit}`">
            <strong>{{ format(row.value) }}</strong><span>{{ shortDate(row.label) }}</span>
          </li>
        </ol>
      </div>
      <div v-else class="trend-plot">
        <div class="trend-caption" aria-live="polite">
          <span>{{ total === 0 ? config.empty : (active ? `${shortDate(active.label)} · ${format(active.value)}${config.unit}` : '暂无数据') }}</span>
        </div>
        <svg v-if="points.length" viewBox="0 0 300 112" preserveAspectRatio="none" role="img" :aria-label="`最近 ${rows.length} 天，共 ${format(total)}${config.unit}`">
          <path d="M8 30 H292 M8 66 H292 M8 104 H292" class="trend-grid" />
          <polygon :points="area" class="trend-area" />
          <polyline :points="line" class="trend-line" />
          <circle v-for="(point, i) in points" :key="point.label" :cx="point.x" :cy="point.y" :r="selected === i ? 4 : 2.4" class="trend-dot" />
        </svg>
        <div class="trend-hit-area" @mouseleave="selected = null">
          <button v-for="(row, i) in rows" :key="row.label" type="button" :aria-label="`${row.label}：${format(row.value)}${config.unit}`" :title="`${row.label}：${format(row.value)}`" @mouseenter="selected = i" @focus="selected = i" @blur="selected = null" @click="selected = i" />
        </div>
        <div v-if="rows.length" class="trend-dates">
          <span>{{ shortDate(rows[0].label) }}</span><span>{{ shortDate(rows.at(-1)!.label) }}</span>
        </div>
      </div>
      <div class="trend-footer">
        <span>{{ isCategory ? `${rows.length} 类能力` : '按日汇总' }}</span>
        <FaButton v-if="card.actionPath" variant="ghost" size="sm" @click="onOpen?.(card)">
          {{ config.action }}<FaIcon name="morph:arrow-up-right" />
        </FaButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
.dashboard-chart-stats {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.trend-summary {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
}

.trend-summary > div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}

.trend-summary strong {
  font-size: clamp(36px, 11cqi, 48px);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.05;
  color: var(--color-text-1);
  letter-spacing: -0.05em;
}

.trend-summary span,
.trend-caption,
.trend-dates,
.trend-footer > span {
  font-size: 12px;
  color: var(--color-text-3);
}

.trend-period {
  flex-shrink: 0;
  padding-bottom: 4px;
}

.trend-zero {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 12px;
  justify-content: space-around;
  min-height: 0;
}

.trend-zero p {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.trend-zero-history {
  display: flex;
  gap: 4px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.trend-zero-history li {
  display: grid;
  flex: 1;
  gap: 8px;
  min-width: 0;
  padding: 12px 0 0;
  text-align: center;
  border-top: 2px solid var(--color-border-2);
}

.trend-zero-history strong {
  font-size: 18px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-2);
}

.trend-zero-history span {
  font-size: 11px;
  color: var(--color-text-3);
}

.trend-plot {
  position: relative;
  display: flex;
  flex: 1;
  flex-direction: column;
  justify-content: flex-end;
  min-height: 95px;
}

.trend-caption {
  padding-bottom: 5px;
  margin-bottom: auto;
}

.trend-plot svg {
  display: block;
  width: 100%;
  height: 100%;
  min-height: 60px;
  max-height: 140px;
  overflow: visible;
}

.trend-grid {
  fill: none;
  stroke: var(--color-border-1);
  stroke-width: 1;
  stroke-dasharray: 3 5;
}

.trend-area {
  opacity: 0.07;
  fill: var(--yd-dashboard-info, var(--color-text-2));
}

.trend-line {
  fill: none;
  stroke: var(--yd-dashboard-info, var(--color-text-2));
  stroke-width: 2;
  vector-effect: non-scaling-stroke;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.trend-dot {
  fill: var(--color-bg-2);
  stroke: var(--yd-dashboard-info, var(--color-text-2));
  stroke-width: 1.5;
  vector-effect: non-scaling-stroke;
}

.trend-hit-area {
  position: absolute;
  inset: 20px 0;
  display: flex;
}

.trend-hit-area button {
  flex: 1;
  min-width: 0;
  cursor: crosshair;
  background: transparent;
  border: 0;
  border-radius: 4px;
}

.trend-hit-area button:focus-visible {
  outline: 1px dashed var(--color-text-3);
}

.trend-dates {
  display: flex;
  justify-content: space-between;
  padding-top: 4px;
}

.trend-footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.trend-categories {
  display: grid;
  flex: 1;
  gap: 10px;
  align-content: center;
  overflow: auto;
}

.trend-category {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 5px 12px;
  font-size: 12px;
  color: var(--color-text-2);
}

.trend-category meter {
  grid-column: 1 / -1;
  width: 100%;
  height: 6px;
  border: 0;
  border-radius: 8px;
}

.trend-category meter::-webkit-meter-bar {
  background: var(--color-fill-2);
  border: 0;
}

.trend-category meter::-webkit-meter-optimum-value {
  background: var(--yd-dashboard-info, var(--color-text-2));
  border-radius: 8px;
}

.trend-state {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.trend-empty {
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
