import type { MaybeRefOrGetter } from 'vue'
import type { AiRunSnapshot } from '@/api/modules/platform-ai-runs'
import { computed, ref, toValue, watch } from 'vue'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { isActiveAiRun } from '@/utils/ai-run-stream'

/** Business tasks share the root's resumable event transport, including when their panel is destroyed. */
export function useAiRunTask(taskSessionId: MaybeRefOrGetter<string | undefined>) {
  const runtime = useAiAssistantStore()
  const starting = ref(false)
  const error = ref('')
  const run = computed(() => Object.values(runtime.runs).filter(item => item.sessionId === toValue(taskSessionId)).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))[0])
  const running = computed(() => starting.value || (!!run.value && isActiveAiRun(run.value.status)))
  const activities = computed(() => (run.value?.activities || []).map((activity, index) => ({
    key: `${activity.phase || index}`,
    title: activity.title || activity.phase || '执行进度',
    description: activity.content,
    status: activity.status === 'complete' || activity.status === 'completed' ? 'success' as const : activity.status === 'error' ? 'error' as const : activity.status === 'running' ? 'running' as const : 'pending' as const,
  })))
  const failure = computed(() => error.value || run.value?.error || (run.value && isActiveAiRun(run.value.status) && runtime.connections[run.value.id] === 'disconnected' ? '进度连接已断开，任务仍在后端运行' : ''))
  watch(() => [toValue(taskSessionId), runtime.lifecycleVersion()] as const, async ([id]) => {
    const started = runtime.lifecycleVersion()
    error.value = ''
    if (id) {
      try {
        await runtime.restoreTask(id)
      }
      catch (cause) {
        if (started === runtime.lifecycleVersion() && id === toValue(taskSessionId)) {
          error.value = cause instanceof Error ? cause.message : '恢复 AI 任务失败'
        }
      }
    }
  }, { immediate: true })

  async function start(create: () => Promise<{ data: AiRunSnapshot }>) {
    if (running.value) {
      return
    }
    starting.value = true
    error.value = ''
    const started = runtime.lifecycleVersion()
    const sessionId = toValue(taskSessionId)
    try {
      runtime.attachRun((await create()).data, started)
    }
    catch (cause) {
      if (started === runtime.lifecycleVersion() && sessionId === toValue(taskSessionId)) {
        error.value = cause instanceof Error ? cause.message : '启动 AI 任务失败'
      }
    }
    finally {
      starting.value = false
    }
  }

  async function reconnect() {
    if (!run.value) {
      return
    }
    error.value = ''
    try {
      await runtime.reconnect(run.value.id)
    }
    catch (cause) {
      error.value = cause instanceof Error ? cause.message : '恢复进度失败'
    }
  }

  async function stop() {
    if (run.value) {
      try {
        await runtime.stop(run.value.id)
      }
      catch (cause) {
        error.value = cause instanceof Error ? cause.message : '停止任务失败'
      }
    }
  }
  return { run, running, failure, activities, start, stop, reconnect }
}
