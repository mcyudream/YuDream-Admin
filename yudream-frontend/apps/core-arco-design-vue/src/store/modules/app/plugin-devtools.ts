import type { Ref } from 'vue'
import type {
  AgentTraceEventPayload,
  AgentTraceStatus,
  AgentTraceStep,
  PluginDevtoolsStatus,
  PluginLifecycleEventPayload,
} from '@/api/modules/platform-devtools'
import apiDevtools from '@/api/modules/platform-devtools'
import eventBus from '@/utils/eventBus'

/** 正在执行（或刚结束）的 Agent 追踪，经 SSE 增量累积，完成落库后可从详情接口取全量 */
export interface LiveTrace {
  traceId: string
  source: string
  ownerPluginCode?: string
  agentCode?: string
  agentName?: string
  status: AgentTraceStatus
  error?: string
  durationMs?: number
  steps: AgentTraceStep[]
  updatedAt: number
}

const LIFECYCLE_EVENT_LIMIT = 100
const TRACE_EVENT_LIMIT = 100
const LIVE_TRACE_LIMIT = 20
const RECONNECT_DELAY_MS = 3_000

export const usePluginDevtoolsStore = defineStore('pluginDevtools', () => {
  const status = ref<PluginDevtoolsStatus | null>(null)
  const statusLoaded = ref(false)
  const statusError = ref('')
  const drawerOpen = ref(false)
  const unreadCount = ref(0)

  const lifecycleEvents = ref<PluginLifecycleEventPayload[]>([])
  const traceEvents = ref<AgentTraceEventPayload[]>([])
  const liveTraces = ref<LiveTrace[]>([])

  const lifecycleConnected = ref(false)
  const traceConnected = ref(false)

  let started = false
  let lifecycleAbort: AbortController | null = null
  let traceAbort: AbortController | null = null

  async function loadStatus(force = false) {
    if (statusLoaded.value && !force) {
      return
    }
    statusLoaded.value = true
    statusError.value = ''
    try {
      const res = await apiDevtools.status()
      status.value = res.data
    }
    catch (error: any) {
      status.value = null
      statusError.value = error?.message || '开发者工具状态接口不可用'
    }
  }

  /** 状态可用且已授权后启动双 SSE 流；布局常驻，只需启动一次 */
  function connect() {
    if (started || !status.value) {
      return
    }
    started = true
    lifecycleAbort = new AbortController()
    traceAbort = new AbortController()
    void streamLoop(apiDevtools.lifecycleStreamUrl(), lifecycleAbort.signal, lifecycleConnected, handleLifecycleEvent)
    void streamLoop(apiDevtools.traceStreamUrl(), traceAbort.signal, traceConnected, handleTraceEvent)
  }

  function disconnect() {
    started = false
    lifecycleAbort?.abort()
    traceAbort?.abort()
    lifecycleAbort = null
    traceAbort = null
    lifecycleConnected.value = false
    traceConnected.value = false
  }

  function openDrawer() {
    drawerOpen.value = true
    unreadCount.value = 0
  }

  function closeDrawer() {
    drawerOpen.value = false
  }

  function clearEvents() {
    lifecycleEvents.value = []
    traceEvents.value = []
  }

  function notify() {
    if (!drawerOpen.value) {
      unreadCount.value += 1
    }
  }

  function handleLifecycleEvent(payload: PluginLifecycleEventPayload) {
    lifecycleEvents.value.unshift(payload)
    if (lifecycleEvents.value.length > LIFECYCLE_EVENT_LIMIT) {
      lifecycleEvents.value.length = LIFECYCLE_EVENT_LIMIT
    }
    notify()
    // 热重载联动：前端产物或整体重载成功后，通知当前插件页面重挂载 remote
    if (payload.success && (payload.action === 'FRONTEND_RELOAD' || payload.action === 'RELOAD')) {
      eventBus.emit('plugin-devtools:remote-reload', payload.pluginCode)
    }
  }

  function handleTraceEvent(payload: AgentTraceEventPayload) {
    traceEvents.value.unshift(payload)
    if (traceEvents.value.length > TRACE_EVENT_LIMIT) {
      traceEvents.value.length = TRACE_EVENT_LIMIT
    }
    notify()

    const existing = liveTraces.value.find(item => item.traceId === payload.traceId)
    if (payload.action === 'STARTED' || !existing) {
      const trace: LiveTrace = {
        traceId: payload.traceId,
        source: payload.source,
        ownerPluginCode: payload.ownerPluginCode,
        agentCode: payload.agentCode,
        agentName: payload.agentName,
        status: payload.status,
        error: payload.error,
        durationMs: payload.durationMs,
        steps: payload.step ? [payload.step] : [],
        updatedAt: Date.now(),
      }
      liveTraces.value = [trace, ...liveTraces.value.filter(item => item.traceId !== payload.traceId)]
      if (liveTraces.value.length > LIVE_TRACE_LIMIT) {
        liveTraces.value.length = LIVE_TRACE_LIMIT
      }
      return
    }
    existing.status = payload.status
    existing.error = payload.error ?? existing.error
    existing.durationMs = payload.durationMs ?? existing.durationMs
    existing.updatedAt = Date.now()
    if (payload.action === 'STEP' && payload.step) {
      const index = existing.steps.findIndex(step => step.seq === payload.step!.seq)
      if (index >= 0) {
        existing.steps.splice(index, 1, payload.step)
      }
      else {
        existing.steps.push(payload.step)
      }
    }
  }

  /** fetch 流式消费 SSE：EventSource 无法携带 Authorization 头，改用可读流手工解析 */
  async function streamLoop(
    url: string,
    signal: AbortSignal,
    connectedFlag: Ref<boolean>,
    onPayload: (payload: any) => void,
  ) {
    while (!signal.aborted) {
      try {
        await consumeSseStream(url, signal, () => {
          connectedFlag.value = true
        }, onPayload)
        connectedFlag.value = false
      }
      catch (error: any) {
        connectedFlag.value = false
        if (signal.aborted || error?.name === 'AbortError') {
          return
        }
      }
      if (!signal.aborted) {
        await new Promise(resolve => setTimeout(resolve, RECONNECT_DELAY_MS))
      }
    }
  }

  async function consumeSseStream(url: string, signal: AbortSignal, onConnected: () => void, onPayload: (payload: any) => void) {
    const headers: Record<string, string> = { 'Accept': 'text/event-stream', 'Accept-Language': 'zh-CN' }
    const token = localStorage.getItem('token')
    if (token) {
      headers.Authorization = token
    }
    const response = await fetch(url, { headers, signal })
    if (!response.ok || !response.body) {
      throw new Error(`事件流连接失败（HTTP ${response.status}）`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    function drain(complete = false) {
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        emitBlock(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
      if (complete && buffer.trim()) {
        emitBlock(buffer)
        buffer = ''
      }
    }

    function emitBlock(block: string) {
      let eventName = 'message'
      const dataLines: string[] = []
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        }
        else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trimStart())
        }
      }
      if (!dataLines.length) {
        return
      }
      const data = dataLines.join('\n')
      if (eventName === 'plugin-lifecycle' || eventName === 'agent-trace') {
        try {
          onPayload(JSON.parse(data))
        }
        catch {
          // 单条坏事件不影响整条流
        }
      }
    }

    while (true) {
      const chunk = await reader.read()
      if (chunk.done) {
        break
      }
      buffer += decoder.decode(chunk.value, { stream: true }).replaceAll('\r\n', '\n')
      drain()
      // 首包到达即视为已连接
      onConnected()
    }
    drain(true)
  }

  return {
    status,
    statusLoaded,
    statusError,
    drawerOpen,
    unreadCount,
    lifecycleEvents,
    traceEvents,
    liveTraces,
    lifecycleConnected,
    traceConnected,
    loadStatus,
    connect,
    disconnect,
    openDrawer,
    closeDrawer,
    clearEvents,
  }
})
