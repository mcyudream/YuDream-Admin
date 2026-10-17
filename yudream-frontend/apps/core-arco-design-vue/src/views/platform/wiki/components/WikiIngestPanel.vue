<script setup lang="ts">
import type { ActionMenuItem, TableColumn } from '@yudream/components'
import type { WikiIngestTask } from '@/api/modules/platform-wiki'
import { computed, inject, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import {
  cancelWikiIngestTask,
  clearWikiIngestTasks,
  deleteWikiIngestTask,
  fetchWikiIngestTasks,
  retryWikiIngestTask,
  wikiIngestEventsEndpoint,
} from '@/api/modules/platform-wiki'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { ingestStatusLabel, wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const account = useAppAccountStore()
const runProgressOpen = ref(false)
const selectedRunId = ref('')

function canViewAiRun(task: WikiIngestTask): boolean {
  return Boolean(task.aiRunId) && account.isLogin && String(task.requestedByUserId || '') === String(account.userId)
}

function viewAiRun(task: WikiIngestTask) {
  if (!canViewAiRun(task)) {
    return
  }
  selectedRunId.value = task.aiRunId!
  runProgressOpen.value = true
}

const tasks = ref<WikiIngestTask[]>([])
const loading = ref(false)
const loadError = ref('')
const live = ref(false)
let abortController: AbortController | null = null

const sortedTasks = computed(() => [...tasks.value].sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0)))
const runningCount = computed(() => tasks.value.filter(task => ['QUEUED', 'RUNNING'].includes(task.status)).length)

// 队列表格：任务｜状态（含失败原因）｜进度｜开始时间；取消/重试/删除收在行尾菜单
const taskColumns: TableColumn<WikiIngestTask>[] = [
  { accessorKey: 'taskType', header: '任务', minWidth: 180 },
  { id: 'status', header: '状态', width: 230 },
  { id: 'progress', header: '进度', width: 220 },
  { id: 'startedAt', header: '开始时间', width: 170 },
]

const taskActions: ActionMenuItem<WikiIngestTask>[] = [
  { label: 'AI 实际进度', icon: 'morph:bot', visible: task => canViewAiRun(task), onClick: task => viewAiRun(task) },
  { label: '取消', icon: 'morph:circle-stop', visible: task => ['QUEUED', 'RUNNING'].includes(task.status), danger: true, onClick: task => cancelTask(task) },
  { label: '重试', icon: 'morph:rotate-cw', visible: task => ['FAILED', 'CANCELLED'].includes(task.status), onClick: task => retryTask(task) },
  { label: '删除', icon: 'morph:trash-2', visible: task => task.status !== 'QUEUED', danger: true, onClick: task => deleteTask(task) },
]

function formatTaskTime(value?: string): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? '-'
    : date.toLocaleString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false })
}

// 客户端分页（任务可能很多）
const page = ref(1)
const pageSize = ref(10)
const pagedTasks = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return sortedTasks.value.slice(start, start + pageSize.value)
})

async function load() {
  if (!store.spaceId.value) {
    tasks.value = []
    loadError.value = ''
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchWikiIngestTasks(store.spaceId.value)
    tasks.value = res.data || []
    page.value = 1
  }
  catch (exception) {
    // 保留错误与重试入口，不用空列表掩盖失败
    loadError.value = exception instanceof Error ? exception.message : '摄入任务加载失败，请稍后重试'
  }
  finally {
    loading.value = false
  }
}

// SSE 实时进度（EventSource 不支持自定义头，用 fetch 流读取）
function subscribe() {
  close()
  if (!store.spaceId.value) {
    return
  }
  const controller = new AbortController()
  abortController = controller
  const token = localStorage.getItem('token')
  fetch(wikiIngestEventsEndpoint(store.spaceId.value), {
    headers: { Accept: 'text/event-stream', ...(token ? { Authorization: token } : {}) },
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.body) {
        return
      }
      live.value = true
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) {
          break
        }
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split('\n\n')
        buffer = blocks.pop() || ''
        for (const block of blocks) {
          const dataLine = block.split('\n').find(line => line.startsWith('data:'))
          if (!dataLine) {
            continue
          }
          try {
            const data = JSON.parse(dataLine.slice(5).trim())
            if (data.taskId || data.id) {
              const target = tasks.value.find(task => task.id === (data.taskId || data.id))
              if (target && typeof data.percent === 'number') {
                target.percent = data.percent
                if (data.phase) {
                  target.phase = data.phase
                }
                if (data.status) {
                  target.status = data.status
                }
              }
            }
            if (data.percent === 100 || data.completed || ['COMPLETED', 'FAILED', 'CANCELLED'].includes(data.status)) {
              await load()
              await store.reloadTree()
            }
          }
          catch {
            // 忽略心跳与解析错误
          }
        }
      }
    })
    .catch(() => {
      // 连接关闭或中止
    })
    .finally(() => {
      if (abortController === controller) {
        live.value = false
      }
    })
}

function close() {
  abortController?.abort()
  abortController = null
  live.value = false
}

function cancelTask(task: WikiIngestTask) {
  modal.confirm({
    title: '取消任务',
    content: `确定取消「${taskTypeLabel(task.taskType)}」任务？`,
    onConfirm: async () => {
      await cancelWikiIngestTask(task.id)
      toast.success('已请求取消')
      await load()
    },
  })
}

async function retryTask(task: WikiIngestTask) {
  await retryWikiIngestTask(task.id)
  toast.success('已重新入队')
  await load()
  subscribe()
}

function deleteTask(task: WikiIngestTask) {
  modal.confirm({
    title: '删除任务',
    content: task.status === 'RUNNING'
      ? '该任务正在运行，将先标记取消，结束后可从列表删除。继续？'
      : `确定删除「${taskTypeLabel(task.taskType)}」任务记录？`,
    onConfirm: async () => {
      await deleteWikiIngestTask(task.id)
      toast.success(task.status === 'RUNNING' ? '已请求取消' : '已删除')
      await load()
    },
  })
}

function clearTasks() {
  modal.confirm({
    title: '清空摄入队列',
    content: '将删除全部非运行中的任务记录，运行中的任务会被取消。确定清空？',
    onConfirm: async () => {
      const res = await clearWikiIngestTasks(store.spaceId.value)
      toast.success(`已清空 ${res.data ?? 0} 条任务记录`)
      await load()
    },
  })
}

function taskTypeLabel(taskType: string): string {
  const map: Record<string, string> = {
    INGEST_SOURCE: '资料摄入',
    REBUILD_INDEX: '重建索引',
    DEEP_RESEARCH: '深度研究',
    RESEARCH: '深度研究',
  }
  return map[taskType] || taskType
}

onMounted(() => {
  load()
  subscribe()
})
onActivated(() => {
  load()
  subscribe()
})
onBeforeUnmount(close)
watch(() => store.spaceId.value, () => {
  load()
  subscribe()
})
</script>

<template>
  <div class="ing-panel">
    <YdScrollArea class="ing-scroll">
      <div class="ing-inner">
        <div class="ing-head">
          <div class="ing-head__status">
            <span class="ing-live" :class="{ 'ing-live--on': live }" />
            <span>{{ live ? '实时进度已连接' : '实时进度未连接' }}</span>
            <YdTag v-if="runningCount" variant="secondary">
              {{ runningCount }} 个任务进行中
            </YdTag>
          </div>
          <div class="ing-head__actions">
            <YdButton v-if="sortedTasks.length" size="sm" variant="ghost" class="ing-danger" @click="clearTasks">
              <YdIcon name="morph:trash-2" /> 清空队列
            </YdButton>
            <YdButton size="sm" variant="outline" :loading="loading" @click="load">
              <YdIcon name="morph:refresh-cw" /> 刷新
            </YdButton>
          </div>
        </div>

        <div v-if="loadError" class="ing-error">
          <YdAlert variant="destructive" title="摄入任务加载失败" :description="loadError" />
          <YdButton variant="outline" size="sm" :loading="loading" @click="load">
            <YdIcon name="morph:rotate-cw" /> 重新加载
          </YdButton>
        </div>

        <div v-if="loading && !sortedTasks.length" class="ing-loading">
          <YdLoading :size="32" text="正在加载摄入任务…" />
        </div>
        <YdEmpty
          v-else-if="!sortedTasks.length && !loadError"
          icon="morph:layers"
          title="暂无摄入任务"
          description="在「资料源」面板对资料点击「摄入」，或在「深度研究」启动研究"
        />
        <template v-else-if="sortedTasks.length">
          <YdResponsiveEnhancedTable
            row-key="id"
            stripe
            border
            column-visibility
            table-class="min-w-[900px]"
            :columns="taskColumns"
            :actions="taskActions"
            :data="pagedTasks"
          >
            <template #cell-taskType="{ row }">
              <div class="ing-cell">
                <strong class="ing-cell__title">{{ taskTypeLabel(row.original.taskType) }}</strong>
                <span class="ing-cell__meta">{{ row.original.phase || '-' }}</span>
              </div>
            </template>
            <template #cell-status="{ row }">
              <div class="ing-cell">
                <span class="ing-status">
                  <span class="ing-status__dot" :style="{ background: ingestStatusLabel(row.original.status).color }" />
                  {{ ingestStatusLabel(row.original.status).label }}
                </span>
                <span class="ing-cell__meta">尝试 {{ row.original.attempts }}/{{ row.original.maxAttempts }}</span>
                <span v-if="row.original.errorMessage" class="ing-cell__error" :title="row.original.errorMessage">
                  <YdIcon name="morph:triangle-alert" /> {{ row.original.errorMessage }}
                </span>
              </div>
            </template>
            <template #cell-progress="{ row }">
              <div class="ing-progress-cell">
                <YdProgress
                  :model-value="row.original.percent"
                  class="ing-progress"
                  :class="{ 'ing-progress--failed': row.original.status === 'FAILED' }"
                />
                <span class="ing-cell__percent">{{ row.original.percent }}%</span>
              </div>
            </template>
            <template #cell-startedAt="{ row }">
              <span class="ing-cell__meta">{{ formatTaskTime(row.original.startedAt) }}</span>
            </template>
          </YdResponsiveEnhancedTable>
          <YdPagination
            v-if="sortedTasks.length > pageSize"
            v-model:page="page"
            v-model:size="pageSize"
            :total="sortedTasks.length"
            class="ing-pagination"
          />
        </template>
      </div>
    </YdScrollArea>
    <YdDrawer v-model="runProgressOpen" title="摄入任务的 AI 运行" :footer="false" content-class="w-[760px] max-w-full">
      <AiRunProgress v-if="selectedRunId" :run-id="selectedRunId" />
    </YdDrawer>
  </div>
</template>

<style scoped>
.ing-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.ing-scroll {
  height: 100%;
}

.ing-inner {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.ing-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ing-head__status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--color-text-3);
  font-size: 13px;
}

.ing-head__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ing-live {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: color-mix(in srgb, var(--color-text-3) 50%, transparent);
}

.ing-live--on {
  background: #10b981;
  box-shadow: 0 0 0 3px #10b98133;
}

/* YdAlert 没有默认插槽：重试动作必须与告警相邻，不能作为子节点传入 */
.ing-error {
  display: grid;
  gap: 8px;
}

.ing-loading {
  display: flex;
  justify-content: center;
  padding: 56px 0;
}

.ing-pagination {
  display: flex;
  justify-content: flex-end;
}

/* —— 队列表单元格 —— */
.ing-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.ing-cell__title {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ing-cell__meta {
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ing-cell__error {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  overflow: hidden;
  max-width: 100%;
  font-size: 11px;
  color: rgb(var(--danger-6));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ing-status {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.ing-status__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.ing-progress-cell {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ing-danger {
  color: rgb(var(--danger-6));
}

.ing-progress {
  flex: 1;
  min-width: 80px;
}

.ing-progress :deep(.progress),
.ing-progress :deep([data-slot='progress']) {
  width: 100%;
}

.ing-cell__percent {
  width: 44px;
  font-size: 12px;
  color: var(--color-text-1);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

@media (max-width: 768px) {
  .ing-inner {
    padding: 12px;
  }

  .ing-head {
    flex-wrap: wrap;
    gap: 8px;
    align-items: flex-start;
  }
}
</style>
