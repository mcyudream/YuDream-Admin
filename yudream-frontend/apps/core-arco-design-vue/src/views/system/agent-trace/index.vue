<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, StatusColumn, TableColumn, YdTablePageAction } from '@yudream/components'
import type { ChartDataset } from '@yudream/dataviz'
import type {
  AgentTraceDetail,
  AgentTraceQueryParams,
  AgentTraceStats,
  AgentTraceStep,
  AgentTraceSummary,
} from '@/api/modules/platform-agent-trace'
import { useMediaQuery } from '@vueuse/core'
import { useTablePage } from '@yudream/components'
import { BarChart } from '@yudream/dataviz'
import apiTrace from '@/api/modules/platform-agent-trace'
import apiExcel from '@/api/modules/system-excel'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import { saveExcelResponse } from '@/utils/excel'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()

const clearing = ref(false)
const deleting = ref(false)
const detailLoading = ref(false)
const detail = ref<AgentTraceDetail | null>(null)
const detailVisible = ref(false)

/** 来源词表：与 AgentTraceSource 枚举一一对应 */
const sourceOptions = [
  { label: '聊天', value: 'CHAT' },
  { label: 'Wiki', value: 'WIKI' },
  { label: 'CMS', value: 'CMS' },
  { label: '调试', value: 'DEBUG' },
  { label: '插件', value: 'PLUGIN' },
  { label: '系统', value: 'SYSTEM' },
]

/** 状态词表：与 AgentTraceStatus 枚举一一对应 */
const statusOptions = [
  { label: '执行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' },
]

/** 搜索选项数据源：标识与 AgentTracePageQuery 的 optionsSource 对应 */
const optionsSources: SearchOptionsSource[] = [
  { id: 'agentTraceSourceOptions', loader: () => sourceOptions },
  { id: 'agentTraceStatusOptions', loader: () => statusOptions },
]

const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<AgentTraceSummary>({
  queryClass: 'online.yudream.base.application.platform.agent.query.AgentTracePageQuery',
  loadApi: async (query) => {
    const res = await apiTrace.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      source: query.source || undefined,
      status: query.status || undefined,
      agentCode: query.agentCode || undefined,
      pluginCode: query.pluginCode || undefined,
      startTime: query.timeRangeStart || undefined,
      endTime: query.timeRangeEnd || undefined,
    })
    return {
      records: res.data.list || [],
      total: res.data.total || 0,
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
  initialSize: 10,
})

/** 统计情报区：跟随当前筛选条件（stats 端点与列表同一套过滤参数） */
const traceStats = ref<AgentTraceStats | null>(null)
const traceStatsLoading = ref(false)
const statsExpanded = ref(false)
const statsEverExpanded = ref(false)

/** 统计与列表共用同一套筛选参数 */
function statsParams(query: Record<string, any>) {
  return {
    keyword: query.keyword || undefined,
    source: query.source || undefined,
    status: query.status || undefined,
    agentCode: query.agentCode || undefined,
    pluginCode: query.pluginCode || undefined,
    startTime: query.timeRangeStart || undefined,
    endTime: query.timeRangeEnd || undefined,
  }
}

async function loadTraceStats() {
  traceStatsLoading.value = true
  try {
    traceStats.value = (await apiTrace.stats(statsParams(searchQuery as Record<string, any>))).data
  }
  catch {
    // 统计是情报板，不可用时不打断列表主流程
    traceStats.value = null
  }
  finally {
    traceStatsLoading.value = false
  }
}

/** 窄屏判定：窄屏只留 KPI，图表不挂载 */
const statsNarrow = useMediaQuery('(max-width: 768px)')

/** 图表只在首次展开且宽屏时挂载，省掉折叠态的图表初始化开销 */
const statsChartsMounted = computed(() => statsEverExpanded.value && !statsNarrow.value && Boolean(traceStats.value))
watch(statsExpanded, (expanded) => {
  if (expanded) {
    statsEverExpanded.value = true
    if (!traceStats.value) {
      void loadTraceStats()
    }
  }
}, { immediate: true })

/** 图表主题跟随宿主明暗模式 */
const chartTheme = computed<'light' | 'dark'>(() => {
  if (typeof document === 'undefined') {
    return 'light'
  }
  return document.documentElement.classList.contains('dark') ? 'dark' : 'light'
})

function bucketDataset(label: string, buckets?: Array<{ label: string, count: number }>): ChartDataset {
  return {
    chartType: 'bar',
    dimensions: ['分类', '次数'],
    source: (buckets ?? []).map(item => [item.label, Number(item.count) || 0]),
    label,
  }
}

const sourceDataset = computed(() => bucketDataset('来源分布', traceStats.value?.sources))
const agentDataset = computed(() => bucketDataset('Agent 分布', traceStats.value?.agents))

/** 统计口径派生：成功率与 Token 合计 */
const traceSuccessRate = computed(() => {
  const total = Number(traceStats.value?.total) || 0
  return total === 0 ? '—' : `${((Number(traceStats.value?.succeeded) || 0) / total * 100).toFixed(1)}%`
})

/** 状态语义色：执行中/成功/失败在长列表里一眼可辨 */
const traceStatusColumn: StatusColumn<AgentTraceSummary> = {
  statusKey: 'status',
  header: '状态',
  width: 90,
  statusMap: {
    RUNNING: { label: '执行中', variant: 'secondary', tone: 'warning' },
    SUCCEEDED: { label: '成功', variant: 'default', tone: 'success' },
    FAILED: { label: '失败', variant: 'destructive', tone: 'danger' },
  },
}

/** 页头操作：刷新为主操作，导出收进「更多」；清空执行流是危险操作 */
const pageActions = computed<YdTablePageAction[]>(() => [
  { label: '刷新', icon: 'morph:refresh-cw', variant: 'outline', onClick: refresh },
  ...(auth('system:monitor:agent-trace:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportExcel }]
    : []),
  ...(auth('system:monitor:agent-trace:delete')
    ? [{ label: '清空', icon: 'morph:trash-2', variant: 'destructive' as const, danger: true, disabled: clearing.value, onClick: confirmClear }]
    : []),
])

/** 行操作统一收进下拉，删除按权限显隐 */
const traceActions: ActionMenuItem<AgentTraceSummary>[] = [
  { label: '详情', icon: 'morph:eye', onClick: row => openDetail(row) },
  {
    label: '删除',
    icon: 'morph:trash-2',
    danger: true,
    visible: () => auth('system:monitor:agent-trace:delete'),
    disabled: () => deleting.value,
    onClick: row => confirmDelete(row),
  },
]

/** 追踪 ID 是行标识，固定左侧；其余列沿用原列表字段 */
const tableColumns = computed<TableColumn<AgentTraceSummary>[]>(() => [
  { id: 'traceId', header: '追踪 ID', width: 190, fixed: 'left', defaultHidden: true },
  { id: 'source', header: '来源', width: 90 },
  { id: 'agent', header: 'Agent', width: 220 },
  { accessorKey: 'ownerPluginCode', header: '插件', width: 140 },
  { accessorKey: 'stepCount', header: '步数', width: 80, align: 'right' },
  { id: 'durationMs', header: '耗时', width: 100, align: 'right' },
  { id: 'input', header: '输入摘要', width: 280, defaultHidden: true },
  { id: 'startTime', header: '开始时间', width: 180 },
  { id: 'error', header: '错误', width: 220 },
])

function refresh() {
  pagination.page = 1
  load()
  void loadTraceStats()
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : ''
}

function formatDuration(ms?: number | null) {
  if (ms === undefined || ms === null) {
    return '-'
  }
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`
}

function statusVariant(status?: string) {
  if (status === 'SUCCEEDED') {
    return 'default'
  }
  if (status === 'FAILED') {
    return 'destructive'
  }
  return 'secondary'
}

function statusText(status?: string) {
  const map: Record<string, string> = { RUNNING: '执行中', SUCCEEDED: '成功', FAILED: '失败', SKIPPED: '跳过' }
  return map[status || ''] || status || '-'
}

function sourceText(source?: string) {
  const map: Record<string, string> = { CHAT: '聊天', WIKI: 'Wiki', CMS: 'CMS', DEBUG: '调试', PLUGIN: '插件', SYSTEM: '系统' }
  return map[source || ''] || source || ''
}

function agentText(row: AgentTraceSummary) {
  return row.agentName || row.agentCode || row.agentId || ''
}

/** 长文本（追踪 ID、输入摘要、错误摘要）中间截断，完整值靠 title 悬浮查看 */
function truncateMiddle(value?: string | null, head = 8, tail = 4) {
  if (!value) {
    return ''
  }
  if (value.length <= head + tail + 1) {
    return value
  }
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

/** 导出参数沿用接口既有的 startTime / endTime 字符串字段；导出接口不经过查询对象，结束日期需显式收口 */
function queryParams(endOfDay = false): AgentTraceQueryParams {
  return {
    keyword: searchQuery.keyword || undefined,
    source: searchQuery.source || undefined,
    status: searchQuery.status || undefined,
    agentCode: searchQuery.agentCode || undefined,
    pluginCode: searchQuery.pluginCode || undefined,
    startTime: searchQuery.timeRangeStart || undefined,
    endTime: searchQuery.timeRangeEnd
      ? `${searchQuery.timeRangeEnd}${endOfDay ? ' 23:59:59' : ''}`
      : undefined,
  }
}

async function openDetail(row: AgentTraceSummary) {
  detailVisible.value = true
  detail.value = null
  detailLoading.value = true
  try {
    const res = await apiTrace.detail(row.traceId)
    detail.value = res.data
  }
  catch {
    detailVisible.value = false
  }
  finally {
    detailLoading.value = false
  }
}

function exportJson(payload: AgentTraceSummary | AgentTraceDetail) {
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `agent-trace-${payload.traceId}.json`
  link.click()
  URL.revokeObjectURL(url)
  toast.success('追踪已导出')
}

async function exportExcel() {
  const res = await apiExcel.exportAgentTraces({ ...queryParams(true) } as Record<string, unknown>)
  saveExcelResponse(res, 'Agent执行流.xlsx')
}

function confirmDelete(row: AgentTraceSummary) {
  modal.confirm({
    title: '确认删除执行流',
    content: `确认删除追踪 ${row.traceId} 吗？该操作不可恢复。`,
    onConfirm: () => deleteTrace(row.traceId),
  })
}

async function deleteTrace(traceId: string) {
  deleting.value = true
  try {
    await apiTrace.delete(traceId)
    toast.success('已删除执行流')
    if (detail.value?.traceId === traceId) {
      detailVisible.value = false
      detail.value = null
    }
    await load()
  }
  finally {
    deleting.value = false
  }
}

function confirmClear() {
  modal.confirm({
    title: '确认清空执行流',
    content: '将按当前筛选条件删除匹配的 Agent 执行流，该操作不可恢复。',
    onConfirm: clearTraces,
  })
}

async function clearTraces() {
  clearing.value = true
  try {
    const res = await apiTrace.clear(queryParams())
    toast.success(`已删除 ${res.data || 0} 条执行流`)
    pagination.page = 1
    await load()
  }
  finally {
    clearing.value = false
  }
}

const detailSteps = computed<AgentTraceStep[]>(() => detail.value?.steps || [])
</script>

<template>
  <div>
    <YdTablePageLayout
      title="Agent 执行流"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <template #stats="{ narrow, expanded }">
        <div class="pt-1 flex flex-col gap-4">
          <YdStats>
            <YdStat title="总次数" :value="Number(traceStats?.total) || 0" description="按当前筛选统计" />
            <YdStat
              title="成功 / 失败 / 执行中"
              :value="`${Number(traceStats?.succeeded) || 0} / ${Number(traceStats?.failed) || 0} / ${Number(traceStats?.running) || 0}`"
            />
            <YdStat title="成功率" :value="traceSuccessRate" />
            <YdStat
              title="平均 / 最长耗时"
              :value="`${formatDuration(traceStats?.avgDurationMs)} / ${formatDuration(traceStats?.maxDurationMs)}`"
            />
            <YdStat
              title="Token"
              :value="Number(traceStats?.totalTokens) || 0"
              :description="`输入 ${Number(traceStats?.promptTokens) || 0} · 输出 ${Number(traceStats?.completionTokens) || 0}`"
            />
          </YdStats>

          <!-- 窄屏只留 KPI；图表首次展开才挂载 -->
          <div v-if="statsChartsMounted" class="trace-stats__charts">
            <section class="trace-stats__chart">
              <BarChart :dataset="sourceDataset" :theme="chartTheme" :height="190" />
            </section>
            <section class="trace-stats__chart">
              <BarChart :dataset="agentDataset" :theme="chartTheme" :height="190" />
            </section>
          </div>
          <div v-else-if="expanded && !narrow && traceStatsLoading" class="text-sm text-muted-foreground">
            统计加载中…
          </div>
        </div>
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          :options-sources="optionsSources"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="traceId"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1500px]"

            column-visibility stripe border
            :columns="tableColumns"
            :data="rows"
            :status-column="traceStatusColumn"
            :actions="traceActions"
          >
            <template #cell-traceId="{ row }">
              <span v-if="row.original.traceId" :title="row.original.traceId">{{ truncateMiddle(row.original.traceId, 12, 8) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-source="{ row }">
              <span v-if="sourceText(row.original.source)">{{ sourceText(row.original.source) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-agent="{ row }">
              <div v-if="agentText(row.original)" class="path-cell">
                <span :title="agentText(row.original)">{{ truncateMiddle(agentText(row.original), 20, 10) }}</span>
                <small v-if="row.original.agentCode && row.original.agentCode !== row.original.agentName" :title="row.original.agentCode">{{ row.original.agentCode }}</small>
              </div>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-ownerPluginCode="{ row }">
              <span v-if="row.original.ownerPluginCode">{{ row.original.ownerPluginCode }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-stepCount="{ row }">
              <span v-if="row.original.stepCount != null">{{ row.original.stepCount }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-durationMs="{ row }">
              <span v-if="row.original.durationMs != null">{{ formatDuration(row.original.durationMs) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-input="{ row }">
              <span v-if="row.original.input" :title="row.original.input">{{ truncateMiddle(row.original.input, 36, 12) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-startTime="{ row }">
              <span v-if="row.original.startTime">{{ dateText(row.original.startTime) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-error="{ row }">
              <span v-if="row.original.error" :title="row.original.error">{{ truncateMiddle(row.original.error, 24, 8) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>

    <YdModal v-model="detailVisible" :title="detail ? `${detail.agentName || detail.agentCode || '执行流'} / 详情` : '执行流详情'" class="sm:max-w-5xl">
      <div v-if="detailLoading" class="panel-empty">
        正在加载追踪详情…
      </div>
      <template v-else-if="detail">
        <div class="detail-meta">
          <YdTag :variant="statusVariant(detail.status)">
            {{ statusText(detail.status) }}
          </YdTag>
          <span>{{ sourceText(detail.source) }}</span>
          <span v-if="detail.ownerPluginCode">{{ detail.ownerPluginCode }}</span>
          <span>{{ formatDuration(detail.durationMs) }}</span>
          <span>{{ dateText(detail.startTime) }}</span>
          <YdButton size="sm" variant="outline" @click="exportJson(detail)">
            导出 JSON
          </YdButton>
        </div>
        <div v-if="detail.input" class="trace-io">
          <div class="trace-io__label">
            输入
          </div>
          <pre>{{ detail.input }}</pre>
        </div>
        <div v-if="detail.finalOutput" class="trace-io">
          <div class="trace-io__label">
            最终输出
          </div>
          <pre>{{ detail.finalOutput }}</pre>
        </div>
        <div v-if="detail.reasoning" class="trace-io">
          <div class="trace-io__label">
            思考过程
          </div>
          <pre>{{ detail.reasoning }}</pre>
        </div>
        <div v-if="detail.error" class="trace-io trace-io--error">
          <div class="trace-io__label">
            错误
          </div>
          <pre>{{ detail.error }}</pre>
        </div>
        <div v-if="detail.usage" class="trace-usage">
          Token：prompt {{ detail.usage.promptTokens }} / completion {{ detail.usage.completionTokens }} / 共 {{ detail.usage.totalTokens }}
        </div>
        <div class="trace-steps">
          <YdCollapsible v-for="step in detailSteps" :key="step.seq" class="trace-step">
            <template #trigger="{ open }">
              <div class="trace-step__trigger">
                <YdTag :variant="statusVariant(step.status)" class="text-xs shrink-0">
                  {{ statusText(step.status) }}
                </YdTag>
                <span class="trace-step__title">#{{ step.seq }} {{ step.nodeTitle || step.nodeId || '步骤' }}</span>
                <YdTag v-if="step.toolName" variant="outline" class="text-xs shrink-0">
                  {{ step.toolName }}
                </YdTag>
                <span class="text-xs text-secondary-foreground/60 shrink-0">{{ formatDuration(step.durationMs) }}</span>
                <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" class="shrink-0 size-4" />
              </div>
            </template>
            <div class="trace-step__body">
              <div v-if="step.inputSummary" class="trace-io">
                <div class="trace-io__label">
                  输入
                </div>
                <pre>{{ step.inputSummary }}</pre>
              </div>
              <div v-if="step.reasoning" class="trace-io">
                <div class="trace-io__label">
                  思考过程
                </div>
                <pre>{{ step.reasoning }}</pre>
              </div>
              <div v-if="step.toolDetail" class="trace-io">
                <div class="trace-io__label">
                  工具调用 {{ step.toolName }}
                </div>
                <pre>{{ step.toolDetail }}</pre>
              </div>
              <div v-if="step.outputSummary" class="trace-io">
                <div class="trace-io__label">
                  输出
                </div>
                <pre>{{ step.outputSummary }}</pre>
              </div>
              <div v-if="step.message" class="trace-io trace-io--error">
                <pre>{{ step.message }}</pre>
              </div>
            </div>
          </YdCollapsible>
          <div v-if="!detailSteps.length" class="panel-empty">
            无步骤记录
          </div>
        </div>
      </template>
    </YdModal>
  </div>
</template>

<style scoped>
.path-cell {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.path-cell small {
  overflow: hidden;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
  color: var(--color-text-2);
  font-size: 13px;
}

.trace-io {
  margin-bottom: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}

.trace-io--error {
  border-color: var(--color-border-3);
}

.trace-io__label {
  margin-bottom: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.trace-io pre {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--color-text-2);
  font-size: 12px;
}

.trace-usage {
  margin-bottom: 12px;
  color: var(--color-text-3);
  font-size: 12px;
}

.trace-steps {
  display: grid;
  gap: 8px;
}

.trace-step__trigger {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.trace-step__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-step__body {
  padding-top: 8px;
}

.panel-empty {
  padding: 24px 0;
  color: var(--color-text-3);
  text-align: center;
}

/* 统计区图表：auto-fit 自适应，宽屏一行两张 */
.trace-stats__charts {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 12px;
  min-width: 0;
}

.trace-stats__chart {
  min-width: 0;
  padding: 8px 12px 4px;
  background: oklch(var(--card));
  border: 1px solid oklch(var(--border));
  border-radius: 8px;
}

.trace-stats__chart :deep(.dataviz-base-chart),
.trace-stats__chart :deep(canvas) {
  max-width: 100%;
  min-width: 0;
}
</style>
