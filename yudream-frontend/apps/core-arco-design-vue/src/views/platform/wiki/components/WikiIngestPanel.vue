<script setup lang="ts">
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
import { ingestStatusLabel, wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()
const modal = useFaModal()

const tasks = ref<WikiIngestTask[]>([])
const loading = ref(false)
const live = ref(false)
let abortController: AbortController | null = null

const sortedTasks = computed(() => [...tasks.value].sort((a, b) => (b.sortOrder ?? 0) - (a.sortOrder ?? 0)))
const runningCount = computed(() => tasks.value.filter(task => ['QUEUED', 'RUNNING'].includes(task.status)).length)

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
    return
  }
  loading.value = true
  try {
    const res = await fetchWikiIngestTasks(store.spaceId.value)
    tasks.value = res.data || []
    page.value = 1
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
    <FaScrollArea class="ing-scroll">
      <div class="ing-inner">
        <div class="ing-head">
          <div class="ing-head__status">
            <span class="ing-live" :class="{ 'ing-live--on': live }" />
            <span>{{ live ? '实时进度已连接' : '实时进度未连接' }}</span>
            <FaTag v-if="runningCount" variant="secondary">{{ runningCount }} 个任务进行中</FaTag>
          </div>
          <div class="ing-head__actions">
            <FaButton v-if="sortedTasks.length" size="sm" variant="ghost" class="ing-danger" @click="clearTasks">
              <FaIcon name="i-ri:delete-bin-line" /> 清空队列
            </FaButton>
            <FaButton size="sm" variant="outline" :loading="loading" @click="load">
              <FaIcon name="i-ri:refresh-line" /> 刷新
            </FaButton>
          </div>
        </div>

        <div v-if="!sortedTasks.length && !loading" class="ing-empty">
          <FaIcon name="i-ri:stack-line" />
          <strong>暂无摄入任务</strong>
          <p>在「资料源」面板对资料点击「摄入」，或在「深度研究」启动研究</p>
        </div>

        <div v-else class="ing-list">
          <FaCard v-for="task in pagedTasks" :key="task.id" class="ing-card">
            <div class="ing-card__row">
              <span
                class="ing-card__status"
                :style="{ color: ingestStatusLabel(task.status).color, background: `${ingestStatusLabel(task.status).color}1a` }"
              >
                {{ ingestStatusLabel(task.status).label }}
              </span>
              <strong class="ing-card__type">{{ taskTypeLabel(task.taskType) }}</strong>
              <span class="ing-card__phase">{{ task.phase }}</span>
              <div class="ing-card__actions">
                <FaButton
                  v-if="['QUEUED', 'RUNNING'].includes(task.status)"
                  size="sm"
                  variant="ghost"
                  class="ing-danger"
                  @click="cancelTask(task)"
                >
                  <FaIcon name="i-ri:stop-circle-line" /> 取消
                </FaButton>
                <FaButton
                  v-if="['FAILED', 'CANCELLED'].includes(task.status)"
                  size="sm"
                  variant="outline"
                  @click="retryTask(task)"
                >
                  <FaIcon name="i-ri:restart-line" /> 重试
                </FaButton>
                <FaButton
                  v-if="!['QUEUED'].includes(task.status)"
                  size="sm"
                  variant="ghost"
                  class="ing-danger"
                  @click="deleteTask(task)"
                >
                  <FaIcon name="i-ri:delete-bin-line" /> 删除
                </FaButton>
              </div>
            </div>
            <div class="ing-card__progress">
              <FaProgress
                :model-value="task.percent"
                class="ing-progress"
                :class="{ 'ing-progress--failed': task.status === 'FAILED' }"
              />
              <span class="ing-card__percent">{{ task.percent }}%</span>
              <span class="ing-card__attempts">尝试 {{ task.attempts }}/{{ task.maxAttempts }}</span>
            </div>
            <p v-if="task.errorMessage" class="ing-card__error">
              <FaIcon name="i-ri:error-warning-line" /> {{ task.errorMessage }}
            </p>
          </FaCard>
        </div>
        <FaPagination
          v-if="sortedTasks.length > pageSize"
          v-model:page="page"
          v-model:size="pageSize"
          :total="sortedTasks.length"
          class="ing-pagination"
        />
      </div>
    </FaScrollArea>
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

.ing-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--color-text-3);
}

.ing-empty :deep(svg) {
  color: rgb(var(--primary-6));
  font-size: 36px;
}

.ing-empty strong {
  color: var(--color-text-1);
}

.ing-empty p {
  margin: 0;
  font-size: 12px;
}

.ing-list {
  display: grid;
  gap: 10px;
}

.ing-pagination {
  display: flex;
  justify-content: flex-end;
}

.ing-card__row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ing-card__status {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
}

.ing-card__type {
  font-size: 14px;
}

.ing-card__phase {
  color: var(--color-text-3);
  font-size: 12px;
}

.ing-card__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.ing-danger {
  color: #dc2626;
}

.ing-card__progress {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 10px;
}

.ing-progress {
  flex: 1;
}

.ing-progress :deep(.progress),
.ing-progress :deep([data-slot='progress']) {
  width: 100%;
}

.ing-card__percent {
  width: 44px;
  color: var(--color-text-1);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.ing-card__attempts {
  color: var(--color-text-3);
  font-size: 12px;
}

.ing-card__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #ef444412;
  color: #dc2626;
  font-size: 12px;
}
</style>
