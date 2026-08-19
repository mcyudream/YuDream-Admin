<script setup lang="ts">
import type {
  AgentTraceDetail,
  AgentTracePage,
  AgentTraceStep,
} from '@/api/modules/platform-devtools'
import apiDevtools from '@/api/modules/platform-devtools'
import AssetSection from './AssetSection.vue'

const store = usePluginDevtoolsStore()
const toast = useFaToast()

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

const filterSource = ref('')
const filterStatus = ref('')
const page = ref<AgentTracePage | null>(null)
const pageLoading = ref(false)
const pageNum = ref(1)
const pageSize = ref(10)

const selectedTraceId = ref<string | null>(null)
const detail = ref<AgentTraceDetail | null>(null)
const detailLoading = ref(false)

/** 选中的实时 trace（来自 SSE 累积，运行中的执行只有这里能看到步骤） */
const selectedLive = computed(() => store.liveTraces.find(item => item.traceId === selectedTraceId.value) || null)

onMounted(loadPage)

watch([filterSource, filterStatus], () => {
  pageNum.value = 1
  loadPage()
})

async function loadPage() {
  pageLoading.value = true
  try {
    const res = await apiDevtools.traces({
      source: filterSource.value || undefined,
      status: filterStatus.value || undefined,
      page: pageNum.value,
      size: pageSize.value,
    })
    page.value = res.data
  }
  finally {
    pageLoading.value = false
  }
}

function openTrace(traceId: string, live: boolean) {
  selectedTraceId.value = traceId
  detail.value = null
  if (!live) {
    loadDetail(traceId)
  }
}

async function loadDetail(traceId: string) {
  detailLoading.value = true
  try {
    const res = await apiDevtools.traceDetail(traceId)
    detail.value = res.data
  }
  catch {
    selectedTraceId.value = null
  }
  finally {
    detailLoading.value = false
  }
}

function backToList() {
  selectedTraceId.value = null
  detail.value = null
}

const detailSteps = computed<AgentTraceStep[]>(() => {
  if (selectedLive.value) {
    return [...selectedLive.value.steps].sort((a, b) => a.seq - b.seq)
  }
  return detail.value?.steps || []
})

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

function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString()
}

function formatDuration(ms?: number) {
  if (ms === undefined || ms === null) {
    return '-'
  }
  return ms >= 1000 ? `${(ms / 1000).toFixed(2)}s` : `${ms}ms`
}

/** 导出追踪详情为 JSON，用于缺陷上报与离线分析 */
function exportTrace() {
  const payload = detail.value || selectedLive.value
  if (!payload) {
    return
  }
  const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `agent-trace-${payload.traceId}.json`
  link.click()
  URL.revokeObjectURL(url)
  toast.success('追踪已导出')
}
</script>

<template>
  <div class="traces-panel">
    <!-- 详情视图 -->
    <template v-if="selectedTraceId">
      <div class="trace-detail__header">
        <FaButton variant="outline" size="sm" @click="backToList">
          <FaIcon name="i-ri:arrow-left-line" />
          返回列表
        </FaButton>
        <div class="trace-detail__meta">
          <FaTag :variant="statusVariant(selectedLive?.status || detail?.status)" class="text-xs">
            {{ statusText(selectedLive?.status || detail?.status) }}
          </FaTag>
          <span class="text-xs">{{ selectedLive?.agentName || detail?.agentName || '-' }}</span>
          <span class="text-xs text-secondary-foreground/60">{{ sourceText(selectedLive?.source || detail?.source) }}</span>
          <span class="text-xs text-secondary-foreground/60">{{ formatDuration(selectedLive?.durationMs ?? detail?.durationMs) }}</span>
        </div>
        <FaButton variant="outline" size="sm" :disabled="!detail && !selectedLive" @click="exportTrace">
          <FaIcon name="i-ri:download-line" />
          导出 JSON
        </FaButton>
      </div>

      <div v-if="detailLoading" class="panel-empty">
        正在加载追踪详情…
      </div>
      <template v-else>
        <div v-if="detail?.input" class="trace-io">
          <div class="trace-io__label">
            输入
          </div>
          <pre>{{ detail.input }}</pre>
        </div>
        <div v-if="detail?.finalOutput" class="trace-io">
          <div class="trace-io__label">
            最终输出
          </div>
          <pre>{{ detail.finalOutput }}</pre>
        </div>
        <div v-if="detail?.error || selectedLive?.error" class="trace-io trace-io--error">
          <div class="trace-io__label">
            错误
          </div>
          <pre>{{ detail?.error || selectedLive?.error }}</pre>
        </div>
        <div v-if="detail?.usage" class="trace-usage text-xs">
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
          </FaCollapsible>
          <div v-if="!detailSteps.length" class="panel-empty">
            {{ selectedLive ? '等待步骤事件…' : '无步骤记录' }}
          </div>
        </div>
      </template>
    </template>

    <!-- 列表视图 -->
    <template v-else>
      <div class="traces-filters">
        <FaSelect v-model="filterSource" :options="sourceOptions" class="shrink-0 w-28" />
        <FaSelect v-model="filterStatus" :options="statusOptions" class="shrink-0 w-28" />
        <FaButton variant="outline" size="icon" :loading="pageLoading" title="刷新" @click="loadPage">
          <FaIcon name="i-ri:refresh-line" />
        </FaButton>
      </div>

      <AssetSection title="实时执行" icon="i-ri:pulse-line" :count="store.liveTraces.filter(t => t.status === 'RUNNING').length" default-open>
        <div v-for="trace in store.liveTraces" :key="trace.traceId" class="trace-row" @click="openTrace(trace.traceId, true)">
          <FaTag :variant="statusVariant(trace.status)" class="text-xs shrink-0">
            {{ statusText(trace.status) }}
          </FaTag>
          <div class="trace-row__main">
            <div class="trace-row__title">
              {{ trace.agentName || trace.agentCode || trace.traceId }}
            </div>
            <div class="trace-row__sub">
              {{ sourceText(trace.source) }}<template v-if="trace.ownerPluginCode">
                · {{ trace.ownerPluginCode }}
              </template> · {{ trace.steps.length }} 步
            </div>
          </div>
          <span class="text-xs text-secondary-foreground/60 shrink-0">{{ formatDuration(trace.durationMs) }}</span>
        </div>
        <div v-if="!store.liveTraces.length" class="asset-empty">
          暂无实时执行，触发任意 Agent 调用后此处实时显示
        </div>
      </AssetSection>

      <AssetSection title="历史记录" icon="i-ri:history-line" :count="page?.total ?? 0" default-open>
        <div v-for="trace in page?.list || []" :key="trace.traceId" class="trace-row" @click="openTrace(trace.traceId, false)">
          <FaTag :variant="statusVariant(trace.status)" class="text-xs shrink-0">
            {{ statusText(trace.status) }}
          </FaTag>
          <div class="trace-row__main">
            <div class="trace-row__title">
              {{ trace.agentName || trace.agentCode || trace.traceId }}
            </div>
            <div class="trace-row__sub">
              {{ sourceText(trace.source) }}<template v-if="trace.ownerPluginCode">
                · {{ trace.ownerPluginCode }}
              </template> · {{ trace.stepCount }} 步 · {{ formatTime(trace.startTime) }}
            </div>
          </div>
          <span class="text-xs text-secondary-foreground/60 shrink-0">{{ formatDuration(trace.durationMs) }}</span>
        </div>
        <div v-if="!pageLoading && !(page?.list?.length)" class="asset-empty">
          暂无历史追踪
        </div>
        <FaPagination
          v-if="(page?.total ?? 0) > pageSize"
          v-model:page="pageNum"
          v-model:size="pageSize"
          :total="page?.total ?? 0"
          layout="total, ->, pager"
          class="mt-2"
          @page-change="loadPage"
        />
      </AssetSection>
    </template>
  </div>
</template>

<style scoped>
.traces-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px 0;
}

.traces-filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.trace-row {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
}

.trace-row:hover {
  background: var(--color-fill-2);
}

.trace-row__main {
  flex: 1;
  min-width: 0;
}

.trace-row__title {
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-row__sub {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.trace-detail__header {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.trace-detail__meta {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.trace-steps {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.trace-step {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.trace-step__trigger {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-width: 0;
  padding: 8px 10px;
  cursor: pointer;
  user-select: none;
}

.trace-step__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: left;
}

.trace-step__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0 10px 10px;
}

.trace-io {
  padding: 8px 10px;
  border-radius: 6px;
  background: var(--color-fill-2);
}

.trace-io__label {
  margin-bottom: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.trace-io pre {
  margin: 0;
  color: var(--color-text-2);
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.trace-io--error {
  background: var(--color-danger-1, rgba(245, 63, 63, 0.08));
}

.trace-io--error pre {
  color: var(--color-danger-6, #f53f3f);
}

.trace-usage {
  color: var(--color-text-3);
}

.panel-empty {
  padding: 32px 0;
  color: var(--color-text-3);
  font-size: 13px;
  text-align: center;
}

.asset-empty {
  padding: 12px 0;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}
</style>
