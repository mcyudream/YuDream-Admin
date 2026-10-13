<script setup lang="ts">
import type { ChartDataset } from '@/api/modules/platform-dataviz'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiDataviz from '@/api/modules/platform-dataviz'
import { formatMetric, metricPoints, shortMetricDate } from '@/components/chart/metric-chart-options'
import SystemMetricChart from '@/components/chart/SystemMetricChart.vue'

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
const kind = computed(() => isCategory.value ? 'ranking' : config.value.metric === 'log-activity' ? 'line' : 'bar')
const rows = computed(() => metricPoints(dataset.value?.dimensions || ['name', 'value'], dataset.value?.source || []))
const hasValues = computed(() => rows.value.some(row => row.value !== null))
const total = computed(() => rows.value.reduce((sum, row) => sum + (row.value ?? 0), 0))
const allZero = computed(() => rows.value.length > 0 && rows.value.every(row => row.value === 0))
const active = computed(() => selected.value === null ? rows.value.at(-1) : rows.value[selected.value])
const period = computed(() => {
  if (isCategory.value) {
    return `${rows.value.length} 类能力`
  }
  const first = rows.value[0]
  const last = rows.value.at(-1)
  return first && last ? `${shortMetricDate(first.label)} — ${shortMetricDate(last.label)}` : '暂无记录'
})
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
        <div class="trend-total">
          <strong>{{ hasValues ? formatMetric(total) : '—' }}</strong>
          <span>{{ config.unit }}</span>
        </div>
        <span class="trend-period">{{ period }}</span>
      </div>
      <SystemMetricChart v-if="rows.length" :points="rows" :kind="kind" :unit="config.unit" :zero-message="config.empty" :label="`${card.title}，${period}`" @select="selected = $event" />
      <div v-else class="trend-state">
        <FaIcon name="morph:chart-no-axes-combined" />暂无统计记录
      </div>
      <div class="trend-footer">
        <div class="trend-caption" aria-live="polite" aria-atomic="true">
          <template v-if="selected !== null && active">
            <span>{{ shortMetricDate(active.label) }}</span>
            <b>{{ active.value === null ? '未上报' : `${formatMetric(active.value)} ${config.unit}` }}</b>
          </template>
          <span v-else-if="!hasValues">暂无统计数据</span>
          <span v-else-if="allZero">{{ rows.length }} {{ isCategory ? '类能力' : '天' }} · 均为 0</span>
          <template v-else-if="!isCategory && active">
            <span>最近一日</span><b>{{ active.value === null ? '未上报' : `${formatMetric(active.value)} ${config.unit}` }}</b>
          </template>
          <span v-else>按能力分类统计</span>
        </div>
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
  gap: 8px;
}

.trend-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  align-items: baseline;
  justify-content: space-between;
}

.trend-total {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: baseline;
}

.trend-total strong {
  font-size: clamp(30px, 9cqi, 38px);
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
  color: var(--color-text-1);
  letter-spacing: -0.045em;
}

.trend-total > span,
.trend-period,
.trend-caption,
.trend-footer > span {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

.trend-period {
  white-space: nowrap;
}

.trend-caption {
  display: flex;
  flex-wrap: wrap;
  gap: 0 8px;
  min-width: 0;
  min-height: 18px;
}

.trend-caption b {
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-2);
  white-space: nowrap;
}

.trend-footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
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
</style>
