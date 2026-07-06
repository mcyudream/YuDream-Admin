<script setup lang="ts">
import type { ChartDataset } from '@/api/modules/platform-dataviz'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import apiDataviz from '@/api/modules/platform-dataviz'
import ChartWidget from '@/components/chart/ChartWidget.vue'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

const props = defineProps<Props>()

const loading = ref(false)
const error = ref<string | null>(null)
const dataset = ref<ChartDataset | null>(null)
const chartHeight = computed(() => props.card.component === 'DATAVIZ_CAPABILITY_STATS' ? 188 : '100%')
const chartConfig = computed(() => {
  switch (props.card.component) {
    case 'DATAVIZ_USER_REGISTRATION':
      return {
        chartType: 'line' as const,
        source: 'system',
        metric: 'user-registration',
        fallback: '按日期统计最近 8 天管理端用户注册趋势。',
      }
    case 'DATAVIZ_DEPT_CREATED':
      return {
        chartType: 'line' as const,
        source: 'system',
        metric: 'dept-created',
        fallback: '按日期统计最近 8 天组织部门新增趋势。',
      }
    case 'DATAVIZ_LOG_ACTIVITY':
      return {
        chartType: 'line' as const,
        source: 'system',
        metric: 'log-activity',
        fallback: '按日期统计最近 8 天接口访问日志趋势。',
      }
    default:
      return {
        chartType: 'bar' as const,
        source: 'capability',
        metric: 'type',
        fallback: '按能力类型统计当前管理端可用能力。',
      }
  }
})

onMounted(loadDataset)

async function loadDataset() {
  loading.value = true
  error.value = null
  try {
    const config = chartConfig.value
    const res = await apiDataviz.queryDataset({
      chartType: config.chartType,
      datasetQuery: { source: config.source, metric: config.metric },
    })
    dataset.value = res.data
  }
  catch (err) {
    error.value = err instanceof Error ? err.message : '图表数据暂不可用'
  }
  finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="dashboard-card__content dashboard-chart-stats">
    <div class="chart-area">
      <div v-if="loading" class="state">
        <a-spin />
      </div>
      <div v-else-if="error" class="state error">
        {{ error }}
      </div>
      <ChartWidget v-else-if="dataset" class="chart-widget" :dataset="dataset" :height="chartHeight" />
    </div>

    <div class="chart-footer">
      <span>{{ card.description || chartConfig.fallback }}</span>
      <button v-if="card.actionPath" type="button" class="chart-open-button" title="管理" @click="props.onOpen?.(card)">
        <FaIcon name="i-ri:arrow-right-line" />
      </button>
    </div>
  </div>
</template>

<style scoped>
.dashboard-chart-stats {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.chart-area {
  position: relative;
  flex: 1;
  min-height: 0;
  min-width: 0;
}

.chart-widget {
  display: block;
  width: 100%;
  height: 100%;
}

.state {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 148px;
  color: var(--color-text-3);
}

.state.error {
  padding: 12px;
  font-size: 13px;
  text-align: center;
  color: rgb(var(--danger-6));
  background: var(--color-fill-1);
  border-radius: 8px;
}

.chart-footer {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  flex: 0 0 auto;
  height: 24px;
  min-width: 0;
}

.chart-footer span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.chart-open-button {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 24px;
  height: 24px;
  padding: 0;
  color: var(--color-text-3);
  cursor: pointer;
  background: transparent;
  border: 1px solid transparent;
  border-radius: 6px;
}

.chart-open-button:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border-color: var(--color-border-2);
}
</style>
