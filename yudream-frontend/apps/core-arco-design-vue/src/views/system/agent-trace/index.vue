<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type {
  AgentTraceDetail,
  AgentTraceQueryParams,
  AgentTraceStats,
  AgentTraceStep,
  AgentTraceSummary,
} from '@/api/modules/platform-agent-trace'
import apiExcel from '@/api/modules/system-excel'
import apiTrace from '@/api/modules/platform-agent-trace'
import { saveExcelResponse } from '@/utils/excel'

const loading = ref(false)
const statsLoading = ref(false)
const clearing = ref(false)
const deleting = ref(false)
const detailLoading = ref(false)
const rows = ref<AgentTraceSummary[]>([])
const stats = ref<AgentTraceStats | null>(null)
const detail = ref<AgentTraceDetail | null>(null)
const detailVisible = ref(false)
const modal = useFaModal()
const toast = useFaToast()
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({
  keyword: '',
  source: '',
  status: '',
  agentCode: '',
  pluginCode: '',
  startTime: '',
  endTime: '',
})

const sourceOptions = [
  { label: '全部来源', value: '' },
  { label: '聊天', value: 'CHAT' },
  { label: 'Wiki', value: 'WIKI' },
  { label: 'CMS', value: 'CMS' },
  { label: '调试', value: 'DEBUG' },
  { label: '插件', value: 'PLUGIN' },
  { label: '系统', value: 'SYSTEM' },
]
const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '执行中', value: 'RUNNING' },
  { label: '成功', value: 'SUCCEEDED' },
  { label: '失败', value: 'FAILED' },
]

const tableColumns = computed<TableColumn<AgentTraceSummary>[]>(() => [
  { id: 'status', header: '状态', width: 90, align: 'center' },
  { id: 'source', header: '来源', width: 90 },
  { id: 'agent', header: 'Agent', width: 220 },
  { accessorKey: 'ownerPluginCode', header: '插件', width: 140 },
  { accessorKey: 'stepCount', header: '步数', width: 80, align: 'right' },
  { id: 'durationMs', header: '耗时', width: 100, align: 'right' },
  { id: 'input', header: '输入摘要', width: 280 },
  { id: 'startTime', header: '开始时间', width: 180 },
  { id: 'error', header: '错误', width: 220 },
  { id: 'actions', header: '操作', width: 168, align: 'center' },
])

onMounted(() => {
  load()
  loadStats()
})

function queryParams(extra?: Partial<AgentTraceQueryParams>): AgentTraceQueryParams {
  return {
    keyword: search.keyword || undefined,
    source: search.source || undefined,
    status: search.status || undefined,
    agentCode: search.agentCode || undefined,
    pluginCode: search.pluginCode || undefined,
    startTime: search.startTime || undefined,
    endTime: search.endTime || undefined,
    ...extra,
  }
}

async function load() {
  loading.value = true
  try {
    const res = await apiTrace.page(queryParams({
      page: pagination.page,
      size: pagination.size,
    }))
    rows.value = res.data.list || []
    pagination.total = res.data.total || 0
  }
  finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await apiTrace.stats(queryParams())
    stats.value = res.data
  }
  finally {
    statsLoading.value = false
  }
}

function refresh() {
  pagination.page = 1
  load()
  loadStats()
}

function resetSearch() {
  search.keyword = ''
  search.source = ''
  search.status = ''
  search.agentCode = ''
  search.pluginCode = ''
  search.startTime = ''
  search.endTime = ''
  refresh()
}

function onPageChange(page: number) {
  pagination.page = page
  load()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  load()
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
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
  return map[source || ''] || source || '-'
}

function agentText(row: AgentTraceSummary) {
  return row.agentName || row.agentCode || row.agentId || '-'
}

function successRate() {
  const total = stats.value?.total || 0
  if (!total) {
    return '-'
  }
  return `${(((stats.value?.succeeded || 0) / total) * 100).toFixed(1)}%`
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
  const res = await apiExcel.exportAgentTraces({ ...queryParams() } as Record<string, unknown>)
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
    await loadStats()
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
    await loadStats()
  }
  finally {
    clearing.value = false
  }
}

const detailSteps = computed<AgentTraceStep[]>(() => detail.value?.steps || [])
</script>

<template>
  <div>
    <FaPageHeader title="Agent 执行流" class="mb-0">
      <FaButton variant="outline" :loading="loading || statsLoading" @click="refresh">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton v-auth="'system:monitor:agent-trace:export'" variant="outline" @click="exportExcel">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
      <FaButton v-auth="'system:monitor:agent-trace:delete'" variant="destructive" :loading="clearing" @click="confirmClear">
        <FaIcon name="i-ri:delete-bin-6-line" />
        清空
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="stats-grid mb-4">
        <FaCard>
          <div class="stat-label">总次数</div>
          <div class="stat-value">{{ stats?.total ?? 0 }}</div>
        </FaCard>
        <FaCard>
          <div class="stat-label">成功 / 失败 / 执行中</div>
          <div class="stat-value">{{ stats?.succeeded ?? 0 }} / {{ stats?.failed ?? 0 }} / {{ stats?.running ?? 0 }}</div>
        </FaCard>
        <FaCard>
          <div class="stat-label">成功率</div>
          <div class="stat-value">{{ successRate() }}</div>
        </FaCard>
        <FaCard>
          <div class="stat-label">平均 / 最长耗时</div>
          <div class="stat-value">{{ formatDuration(stats?.avgDurationMs) }} / {{ formatDuration(stats?.maxDurationMs) }}</div>
        </FaCard>
        <FaCard>
          <div class="stat-label">Token（prompt / completion / 合计）</div>
          <div class="stat-value">{{ stats?.promptTokens ?? 0 }} / {{ stats?.completionTokens ?? 0 }} / {{ stats?.totalTokens ?? 0 }}</div>
        </FaCard>
      </div>

      <div v-if="stats?.sources?.length || stats?.agents?.length" class="bucket-row mb-4">
        <div v-if="stats?.sources?.length" class="bucket-group">
          <span class="bucket-label">来源分布</span>
          <FaTag v-for="item in stats.sources" :key="`source-${item.key}`" variant="outline">
            {{ item.label }} {{ item.count }}
          </FaTag>
        </div>
        <div v-if="stats?.agents?.length" class="bucket-group">
          <span class="bucket-label">Agent 分布</span>
          <FaTag v-for="item in stats.agents" :key="`agent-${item.key}`" variant="outline">
            {{ item.label || item.key || '未标注' }} {{ item.count }}
          </FaTag>
        </div>
      </div>

      <FaResponsiveTable
        v-loading="loading"
        row-key="traceId"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1500px]"
        border
        stripe
        column-visibility
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="search-grid">
              <FaInput v-model="search.keyword" clearable placeholder="Agent / 插件 / 追踪ID / 输入 / 错误" @keydown.enter="refresh" @clear="refresh" />
              <FaSelect v-model="search.source" :options="sourceOptions" placeholder="来源" />
              <FaSelect v-model="search.status" :options="statusOptions" placeholder="状态" />
              <FaInput v-model="search.agentCode" clearable placeholder="Agent 编码" @keydown.enter="refresh" />
              <FaInput v-model="search.pluginCode" clearable placeholder="插件编码" @keydown.enter="refresh" />
              <FaInput v-model="search.startTime" type="datetime-local" />
              <FaInput v-model="search.endTime" type="datetime-local" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">
                  重置
                </FaButton>
                <FaButton :loading="loading" @click="refresh">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">
            {{ statusText(row.original.status) }}
          </FaTag>
        </template>
        <template #cell-source="{ row }">
          {{ sourceText(row.original.source) }}
        </template>
        <template #cell-agent="{ row }">
          <div class="path-cell">
            <span>{{ agentText(row.original) }}</span>
            <small v-if="row.original.agentCode && row.original.agentCode !== row.original.agentName">{{ row.original.agentCode }}</small>
          </div>
        </template>
        <template #cell-durationMs="{ row }">
          {{ formatDuration(row.original.durationMs) }}
        </template>
        <template #cell-input="{ row }">
          <span class="line-clamp-1">{{ row.original.input || '-' }}</span>
        </template>
        <template #cell-startTime="{ row }">
          {{ dateText(row.original.startTime) }}
        </template>
        <template #cell-error="{ row }">
          <span class="line-clamp-1">{{ row.original.error || '-' }}</span>
        </template>
        <template #cell-actions="{ row }">
          <div class="flex justify-center gap-1">
            <FaButton size="sm" variant="outline" @click="openDetail(row.original)">
              详情
            </FaButton>
            <FaButton v-auth="'system:monitor:agent-trace:delete'" size="sm" variant="destructive" :loading="deleting" @click="confirmDelete(row.original)">
              删除
            </FaButton>
          </div>
        </template>
        <template #card="{ row }">
          <FaCard class="w-full">
            <div class="flex flex-col gap-3">
              <div class="flex items-start justify-between gap-2">
                <div class="flex min-w-0 flex-col gap-1">
                  <span class="break-all text-base font-semibold">{{ agentText(row) }}</span>
                  <span class="text-sm text-secondary-foreground/70">{{ sourceText(row.source) }} · {{ row.stepCount ?? 0 }} 步 · {{ formatDuration(row.durationMs) }}</span>
                </div>
                <FaTag :variant="statusVariant(row.status)">
                  {{ statusText(row.status) }}
                </FaTag>
              </div>
              <div v-if="row.input" class="line-clamp-2 text-sm">
                {{ row.input }}
              </div>
              <div class="flex flex-wrap gap-x-4 gap-y-1 text-sm">
                <span v-if="row.ownerPluginCode">插件：{{ row.ownerPluginCode }}</span>
                <span>时间：{{ dateText(row.startTime) }}</span>
              </div>
              <div v-if="row.error" class="break-all text-sm">
                错误：{{ row.error }}
              </div>
              <div class="flex gap-2">
                <FaButton size="sm" variant="outline" @click="openDetail(row)">
                  详情
                </FaButton>
                <FaButton v-auth="'system:monitor:agent-trace:delete'" size="sm" variant="destructive" @click="confirmDelete(row)">
                  删除
                </FaButton>
              </div>
            </div>
          </FaCard>
        </template>
      </FaResponsiveTable>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>

    <FaModal v-model="detailVisible" :title="detail ? `${detail.agentName || detail.agentCode || '执行流'} / 详情` : '执行流详情'" class="sm:max-w-5xl">
      <div v-if="detailLoading" class="panel-empty">
        正在加载追踪详情…
      </div>
      <template v-else-if="detail">
        <div class="detail-meta">
          <FaTag :variant="statusVariant(detail.status)">
            {{ statusText(detail.status) }}
          </FaTag>
          <span>{{ sourceText(detail.source) }}</span>
          <span v-if="detail.ownerPluginCode">{{ detail.ownerPluginCode }}</span>
          <span>{{ formatDuration(detail.durationMs) }}</span>
          <span>{{ dateText(detail.startTime) }}</span>
          <FaButton size="sm" variant="outline" @click="exportJson(detail)">
            导出 JSON
          </FaButton>
        </div>
        <div v-if="detail.input" class="trace-io">
          <div class="trace-io__label">输入</div>
          <pre>{{ detail.input }}</pre>
        </div>
        <div v-if="detail.finalOutput" class="trace-io">
          <div class="trace-io__label">最终输出</div>
          <pre>{{ detail.finalOutput }}</pre>
        </div>
        <div v-if="detail.reasoning" class="trace-io">
          <div class="trace-io__label">思考过程</div>
          <pre>{{ detail.reasoning }}</pre>
        </div>
        <div v-if="detail.error" class="trace-io trace-io--error">
          <div class="trace-io__label">错误</div>
          <pre>{{ detail.error }}</pre>
        </div>
        <div v-if="detail.usage" class="trace-usage">
          Token：prompt {{ detail.usage.promptTokens }} / completion {{ detail.usage.completionTokens }} / 共 {{ detail.usage.totalTokens }}
        </div>
        <div class="trace-steps">
          <FaCollapsible v-for="step in detailSteps" :key="step.seq" class="trace-step">
            <template #trigger="{ open }">
              <div class="trace-step__trigger">
                <FaTag :variant="statusVariant(step.status)" class="text-xs shrink-0">
                  {{ statusText(step.status) }}
                </FaTag>
                <span class="trace-step__title">#{{ step.seq }} {{ step.nodeTitle || step.nodeId || '步骤' }}</span>
                <FaTag v-if="step.toolName" variant="outline" class="text-xs shrink-0">
                  {{ step.toolName }}
                </FaTag>
                <span class="text-xs text-secondary-foreground/60 shrink-0">{{ formatDuration(step.durationMs) }}</span>
                <FaIcon :name="open ? 'i-ri:arrow-up-s-line' : 'i-ri:arrow-down-s-line'" class="shrink-0 size-4" />
              </div>
            </template>
            <div class="trace-step__body">
              <div v-if="step.inputSummary" class="trace-io">
                <div class="trace-io__label">输入</div>
                <pre>{{ step.inputSummary }}</pre>
              </div>
              <div v-if="step.reasoning" class="trace-io">
                <div class="trace-io__label">思考过程</div>
                <pre>{{ step.reasoning }}</pre>
              </div>
              <div v-if="step.toolDetail" class="trace-io">
                <div class="trace-io__label">工具调用 {{ step.toolName }}</div>
                <pre>{{ step.toolDetail }}</pre>
              </div>
              <div v-if="step.outputSummary" class="trace-io">
                <div class="trace-io__label">输出</div>
                <pre>{{ step.outputSummary }}</pre>
              </div>
              <div v-if="step.message" class="trace-io trace-io--error">
                <pre>{{ step.message }}</pre>
              </div>
            </div>
          </FaCollapsible>
          <div v-if="!detailSteps.length" class="panel-empty">
            无步骤记录
          </div>
        </div>
      </template>
    </FaModal>
  </div>
</template>

<style scoped>
.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.stat-label {
  color: var(--color-text-3);
  font-size: 12px;
}

.stat-value {
  margin-top: 6px;
  color: var(--color-text-1);
  font-size: 18px;
  font-weight: 600;
  word-break: break-all;
}

.bucket-row {
  display: grid;
  gap: 10px;
}

.bucket-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.bucket-label {
  color: var(--color-text-3);
  font-size: 12px;
}

.search-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 12px;
}

@media (min-width: 768px) {
  .search-grid {
    grid-template-columns: minmax(220px, 1.4fr) 140px 140px minmax(140px, 1fr) minmax(140px, 1fr) 190px 190px auto;
    align-items: center;
  }
}

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
</style>
