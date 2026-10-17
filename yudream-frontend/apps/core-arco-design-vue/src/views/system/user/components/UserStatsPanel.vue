<script setup lang="ts">
import type { ChartDataset } from '@yudream/dataviz'
import type { UserStats } from '@/api/modules/system-user'
import { YdStat, YdStats } from '@yudream/components'
import { BarChart, LineChart, PieChart } from '@yudream/dataviz'
import { computed, ref, watch } from 'vue'

/**
 * 用户管理「情报板」：KPI 条 + 四张图表。
 * 图表只在宽屏挂载（窄屏由 YdTablePageLayout 传 narrow，页面据此 v-if），
 * 避免手机上白白初始化图表实例；数据来自 `GET /api/system/users/stats`（仓储全量聚合）。
 */
const props = defineProps<{
  stats?: UserStats
  loading?: boolean
  /** 统计区是否展开（布局传入）：首次展开后才挂载图表 */
  expanded?: boolean
  /** 窄屏：只保留 KPI 条，图表不挂载（省掉图表实例初始化开销） */
  narrow?: boolean
}>()

/** 图表主题跟随宿主明暗模式（与 ChartWidget 判定方式一致） */
/** 图表是否已挂载：首次展开后常驻，默认折叠时不做任何图表初始化 */
const everExpanded = ref(false)
watch(() => props.expanded, (expanded) => {
  if (expanded) {
    everExpanded.value = true
  }
}, { immediate: true })
// 数据到位后才挂载图表：避免用占位数据画出假图，也省掉加载期的图表初始化
const chartsMounted = computed(() => !props.narrow && everExpanded.value && Boolean(props.stats))

const chartTheme = computed<'light' | 'dark'>(() => {
  if (typeof document === 'undefined') {
    return 'light'
  }
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
})

function toCount(value: string | number | undefined): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

const total = computed(() => toCount(props.stats?.total))
const verified = computed(() => toCount(props.stats?.verifiedCount))
const active = computed(() => toCount(props.stats?.activeCount))
const disabled = computed(() => toCount(props.stats?.disabledCount))
const newToday = computed(() => toCount(props.stats?.newToday))
const newThisWeek = computed(() => toCount(props.stats?.newThisWeek))
const unverified = computed(() => Math.max(total.value - verified.value, 0))

function ratioText(count: number): string {
  if (total.value === 0) {
    return '占比 —'
  }
  return `占比 ${(count / total.value * 100).toFixed(1)}%`
}

/** 近 30 天注册趋势 */
const trendDataset = computed<ChartDataset>(() => {
  const rows = props.stats?.registeredTrend ?? []
  return {
    chartType: 'line',
    dimensions: ['日期', '新增'],
    source: rows.map(row => [row.date.slice(5), toCount(row.count)]),
    label: `近 ${props.stats?.trendDays ?? 30} 天新增注册`,
    series: [{ type: 'line', name: '新增', smooth: true }],
  }
})

/** 角色分布：取前 6 + 其余合并，避免环形图图例溢出 */
const roleDataset = computed<ChartDataset>(() => {
  const rows = (props.stats?.roleDistribution ?? []).map(row => ({ name: row.name, count: toCount(row.count) }))
  const head = rows.slice(0, 6)
  const tailCount = rows.slice(6).reduce((sum, row) => sum + row.count, 0)
  const source = head.map(row => [row.name, row.count])
  if (tailCount > 0) {
    source.push([`其他 ${rows.length - head.length} 个角色`, tailCount])
  }
  return {
    chartType: 'pie',
    dimensions: ['角色', '人数'],
    source: source.length > 0 ? source : [['暂无角色分配', 1]],
    label: '角色分布',
  }
})

/** 状态构成：验证 / 启用两条口径 */
const statusDataset = computed<ChartDataset>(() => ({
  chartType: 'bar',
  dimensions: ['状态', '人数'],
  source: [
    ['已验证', verified.value],
    ['未验证', unverified.value],
    ['启用', active.value],
    ['停用', disabled.value],
  ],
  label: '状态构成',
}))

/** 资料完善：各维度已填写人数 */
const completenessDataset = computed<ChartDataset>(() => ({
  chartType: 'bar',
  dimensions: ['资料项', '人数'],
  source: (props.stats?.completeness ?? []).map(row => [row.name, toCount(row.count)]),
  label: '资料完善',
}))
</script>

<template>
  <div class="user-stats">
    <YdStats>
      <YdStat title="总用户" :value="total" description="含启用与停用" />
      <YdStat title="邮箱已验证" :value="verified" :description="ratioText(verified)" />
      <YdStat title="已启用" :value="active" :description="ratioText(active)" />
      <YdStat
        title="本周新增"
        :value="newThisWeek"
        :description="newToday > 0 ? `今日 +${newToday}` : '今日 +0'"
      />
    </YdStats>

    <div v-if="chartsMounted" class="user-stats__charts">
      <section class="user-stats__chart">
        <LineChart :dataset="trendDataset" :theme="chartTheme" :height="190" />
      </section>
      <section class="user-stats__chart">
        <PieChart :dataset="roleDataset" :theme="chartTheme" :height="190" />
      </section>
      <section class="user-stats__chart">
        <BarChart :dataset="statusDataset" :theme="chartTheme" :height="190" />
      </section>
      <section class="user-stats__chart">
        <BarChart :dataset="completenessDataset" :theme="chartTheme" :height="190" />
      </section>
    </div>
  </div>
</template>

<style scoped>
.user-stats {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
  padding-top: 4px;
}

/* auto-fit + minmax 自适应：宽屏一行四张，窄屏自动折行，不需要媒体查询 */
.user-stats__charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
  min-width: 0;
}

.user-stats__chart {
  min-width: 0;
  padding: 8px 12px 4px;
  background: oklch(var(--card));
  border: 1px solid oklch(var(--border));
  border-radius: 8px;
}

/* ECharts 会给 canvas 写内联宽度，不限制的话网格项的 min-content 会把它自己挤到下一行 */
.user-stats__chart :deep(.dataviz-base-chart),
.user-stats__chart :deep(canvas) {
  max-width: 100%;
  min-width: 0;
}
</style>
