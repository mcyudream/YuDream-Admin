import type { AiRunSnapshot } from '@/api/modules/platform-ai-runs'
import type { AiRunEnvelope } from '@/utils/ai-run-stream'
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { cancelAiSystemRun, getAiSystemRun, listAiSystemRuns, respondAiSystemInteraction, subscribeAiRun } from '@/api/modules/platform-ai-runs'
import { projectAiRunActivities, projectAiRunActivity, projectAiRunTool } from '@/utils/ai-run-projection'
import { canReconnectAiRun, isActiveAiRun, isNewAiCursor } from '@/utils/ai-run-stream'

/** Privileged system observers never put owner-0 records into personal assistant state. */
export const useAiSystemRunsStore = defineStore('aiSystemRuns', () => {
  const runs = ref<Record<string, AiRunSnapshot>>({})
  const connections = ref<Record<string, 'connected' | 'reconnecting' | 'disconnected'>>({})
  const error = ref('')
  const loading = ref(false)
  const lifecycle = ref(0)
  const controllers = new Map<string, AbortController>()
  const timers = new Map<string, ReturnType<typeof setTimeout>>()
  const retries = new Map<string, number>()
  let owner = ''
  function reset(nextOwner = '') {
    lifecycle.value++
    owner = nextOwner
    for (const controller of controllers.values()) {
      controller.abort()
    }
    for (const timer of timers.values()) {
      clearTimeout(timer)
    }
    controllers.clear()
    timers.clear()
    retries.clear()
    runs.value = {}
    connections.value = {}
    error.value = ''
    loading.value = false
  }
  function syncOwner(nextOwner: string) {
    if (owner !== nextOwner) {
      reset(nextOwner)
    }
  }
  function lifecycleVersion() {
    return lifecycle.value
  }
  function install(raw: AiRunSnapshot) {
    const current = runs.value[raw.id]
    if (current && isNewAiCursor(current.cursor, raw.cursor)) {
      return
    }
    runs.value[raw.id] = { ...raw, tools: (raw.tools || []).flatMap((value) => {
      const tool = projectAiRunTool(value)
      return tool ? [tool] : []
    }), activities: projectAiRunActivities(raw.activities, raw.id) }
  }
  function apply(event: AiRunEnvelope) {
    const run = runs.value[event.runId]
    if (!run || !isNewAiCursor(event.cursor, run.cursor)) {
      return
    }
    run.cursor = event.cursor
    const payload = event.payload
    if (typeof payload.callId === 'string') {
      const generations = run.generations ??= []
      let generation = generations.find(item => item.callId === payload.callId)
      if (!generation) {
        generations.push({ callId: payload.callId, content: '', reasoning: '' })
        generation = generations.at(-1)!
      }
      if (event.event === 'ai.generation' && event.action !== 'reasoning') {
        generation.content += typeof payload.content === 'string' ? payload.content : ''
        generation.structured = payload.structured === true
        generation.providerCode = typeof payload.providerCode === 'string' ? payload.providerCode : generation.providerCode
        generation.modelCode = typeof payload.modelCode === 'string' ? payload.modelCode : generation.modelCode
      }
      else if ((event.event === 'ai.generation' || event.event === 'ai.message') && event.action === 'reasoning') {
        generation.reasoning = (generation.reasoning || '') + (typeof payload.content === 'string' ? payload.content : '')
      }
      else if (event.event === 'ai.tool') {
        const tool = projectAiRunTool(payload.tool, event.action)
        if (tool) {
          (generation.tools ??= []).push(tool)
        }
      }
      else if (event.event === 'ai.progress') {
        const activity = projectAiRunActivity(payload.activity, run.id)
        if (activity) {
          (generation.activities ??= []).push(activity)
        }
      }
      return
    }
    if (event.event === 'ai.message') {
      if (event.action === 'reasoning') {
        run.reasoning = (run.reasoning || '') + (typeof payload.content === 'string' ? payload.content : '')
      }
      else if (event.action === 'delta') {
        run.content = (run.content || '') + (typeof payload.content === 'string' ? payload.content : '')
      }
    }
    else if (event.event === 'ai.structured') {
      run.structuredContent = (run.structuredContent || '') + (typeof payload.content === 'string' ? payload.content : '')
    }
    else if (event.event === 'ai.progress') {
      const activity = projectAiRunActivity(payload.activity, run.id)
      if (activity) {
        (run.activities ??= []).push(activity)
        if (activity.status === 'waiting') {
          run.status = 'WAITING'
        }
        else if (activity.status === 'running') {
          run.status = 'RUNNING'
        }
      }
      const raw = payload.activity as Record<string, unknown> | undefined
      if (Array.isArray(raw?.pendingInteractions)) {
        run.pendingInteractions = raw.pendingInteractions as AiRunSnapshot['pendingInteractions']
      }
    }
    else if (event.event === 'ai.tool') {
      const tool = projectAiRunTool(payload.tool, event.action)
      if (tool) {
        (run.tools ??= []).push(tool)
      }
    }
    else if (event.event === 'ai.frontend' && event.action === 'settled') {
      run.pendingInteractions = (run.pendingInteractions || []).filter(item => item.requestId !== payload.toolCallId)
    }
    else if (event.event === 'ai.result') {
      run.result = payload.result as Record<string, unknown>
      run.status = 'COMPLETED'
      run.pendingInteractions = []
    }
    else if (event.event === 'ai.error') {
      run.status = event.action === 'cancelled' ? 'CANCELLED' : 'FAILED'
      run.error = typeof payload.message === 'string' ? payload.message : '系统 AI 任务失败'
      run.pendingInteractions = []
    }
    if (!isActiveAiRun(run.status)) {
      for (const activity of run.activities || []) {
        if (activity.status === 'running') {
          activity.status = run.status === 'FAILED' ? 'error' : run.status === 'CANCELLED' ? 'cancelled' : 'complete'
        }
      }
      for (const tool of run.tools || []) {
        if (tool.status === 'executing') {
          tool.status = run.status === 'FAILED' ? 'error' : run.status === 'CANCELLED' ? 'cancelled' : 'complete'
        }
      }
      controllers.get(run.id)?.abort()
      connections.value[run.id] = 'disconnected'
    }
  }
  function connect(id: string) {
    const run = runs.value[id]
    if (!run || !isActiveAiRun(run.status) || controllers.has(id)) {
      return
    }
    const epoch = lifecycle.value
    const controller = new AbortController()
    controllers.set(id, controller)
    connections.value[id] = 'connected'
    let failure: unknown
    void subscribeAiRun(id, run.cursor, controller.signal, (event) => {
      if (epoch !== lifecycle.value || !isNewAiCursor(event.cursor, runs.value[id]?.cursor || '0')) {
        return
      }
      retries.set(id, 0)
      apply(event)
    }, 'system').catch((cause) => {
      failure = cause
    }).finally(async () => {
      if (controllers.get(id) !== controller) {
        return
      }
      controllers.delete(id)
      if (epoch !== lifecycle.value || controller.signal.aborted) {
        return
      }
      try {
        const snapshot = await getAiSystemRun(id)
        if (epoch !== lifecycle.value) {
          return
        }
        install(snapshot.data)
      }
      catch (cause) {
        failure ??= cause
      }
      if (epoch !== lifecycle.value || !isActiveAiRun(runs.value[id]?.status || '')) {
        return
      }
      const attempt = (retries.get(id) || 0) + 1
      retries.set(id, attempt)
      if (attempt > 5 || (failure && !canReconnectAiRun(failure))) {
        connections.value[id] = 'disconnected'
        error.value = failure instanceof Error ? failure.message : '系统任务进度已断开，请恢复连接'
        return
      }
      connections.value[id] = 'reconnecting'
      timers.set(id, setTimeout(() => {
        timers.delete(id)
        if (epoch === lifecycle.value) {
          connect(id)
        }
      }, Math.min(1000 * 2 ** (attempt - 1), 15000)))
    })
  }
  function attachRun(run: AiRunSnapshot, epoch = lifecycle.value) {
    if (epoch !== lifecycle.value) {
      return
    }
    install(run)
    connect(run.id)
  }
  async function load(userId: string, activeOnly = true) {
    syncOwner(userId)
    const epoch = lifecycle.value
    loading.value = true
    error.value = ''
    try {
      const response = await listAiSystemRuns(activeOnly)
      if (epoch !== lifecycle.value) {
        return
      }
      for (const run of response.data) {
        attachRun(run)
      }
    }
    catch (cause) {
      if (epoch === lifecycle.value) {
        error.value = cause instanceof Error ? cause.message : '读取系统 AI 任务失败'
      }
    }
    finally {
      if (epoch === lifecycle.value) {
        loading.value = false
      }
    }
  }
  async function reconnect(id: string) {
    const epoch = lifecycle.value
    const response = await getAiSystemRun(id)
    if (epoch !== lifecycle.value) {
      return
    }
    controllers.get(id)?.abort()
    controllers.delete(id)
    const timer = timers.get(id)
    if (timer) {
      clearTimeout(timer)
    }
    timers.delete(id)
    retries.delete(id)
    error.value = ''
    attachRun(response.data)
  }
  async function stop(id: string) {
    const epoch = lifecycle.value
    attachRun((await cancelAiSystemRun(id)).data, epoch)
  }
  async function respondInteraction(id: string, requestId: string, approved: boolean) {
    const epoch = lifecycle.value
    attachRun((await respondAiSystemInteraction(id, requestId, approved)).data, epoch)
  }
  return { runs, connections, error, loading, reset, syncOwner, load, lifecycleVersion, attachRun, reconnect, stop, respondInteraction }
})
