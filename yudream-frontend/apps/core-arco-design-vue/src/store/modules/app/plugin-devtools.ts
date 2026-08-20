import type { Ref } from 'vue'
import type {
  AgentTraceEventPayload,
  AgentTraceStatus,
  AgentTraceStep,
  PluginDevProject,
  PluginDevProjectSavePayload,
  PluginDevtoolsStatus,
  PluginLifecycleEventPayload,
} from '@/api/modules/platform-devtools'
import apiDevtools from '@/api/modules/platform-devtools'
import eventBus from '@/utils/eventBus'

/** 抽屉页面标识，与左侧竖向导航一一对应 */
export type PluginDevtoolsPage = 'overview' | 'plugins' | 'traces' | 'audit' | 'settings'

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
const LIVE_TRACE_LIMIT = 20
const RECONNECT_DELAY_MS = 3_000
const ACTIVE_PAGE_STORAGE_KEY = 'pluginDevtoolsPage'
const DEVTOOLS_PAGES: PluginDevtoolsPage[] = ['overview', 'plugins', 'traces', 'audit', 'settings']

function hydrateActivePage(): PluginDevtoolsPage {
  const saved = localStorage.getItem(ACTIVE_PAGE_STORAGE_KEY) as PluginDevtoolsPage | null
  return saved && DEVTOOLS_PAGES.includes(saved) ? saved : 'overview'
}

export const usePluginDevtoolsStore = defineStore('pluginDevtools', () => {
  const status = ref<PluginDevtoolsStatus | null>(null)
  const statusLoaded = ref(false)
  const statusError = ref('')
  const drawerOpen = ref(false)
  const unreadCount = ref(0)
  const activePage = ref<PluginDevtoolsPage>(hydrateActivePage())

  const devProjects = ref<PluginDevProject[]>([])
  const devProjectsLoading = ref(false)

  const lifecycleEvents = ref<PluginLifecycleEventPayload[]>([])
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

  function setActivePage(page: PluginDevtoolsPage) {
    activePage.value = page
    localStorage.setItem(ACTIVE_PAGE_STORAGE_KEY, page)
  }

  /** 面板登记与配置文件合并后的开发项目清单（不受开发模式开关过滤） */
  async function loadDevProjects() {
    devProjectsLoading.value = true
    try {
      const res = await apiDevtools.devProjects()
      devProjects.value = res.data || []
    }
    finally {
      devProjectsLoading.value = false
    }
  }

  async function addDevProject(payload: PluginDevProjectSavePayload) {
    await apiDevtools.addDevProject(payload)
    await Promise.all([loadDevProjects(), loadStatus(true)])
  }

  async function removeDevProject(code: string) {
    await apiDevtools.removeDevProject(code)
    await Promise.all([loadDevProjects(), loadStatus(true)])
  }

  async function reloadDevPlugin(code: string) {
    await apiDevtools.reload(code)
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
    activePage,
    devProjects,
    devProjectsLoading,
    lifecycleEvents,
    liveTraces,
    lifecycleConnected,
    traceConnected,
    loadStatus,
    setActivePage,
    loadDevProjects,
    addDevProject,
    removeDevProject,
    reloadDevPlugin,
    connect,
    disconnect,
    openDrawer,
    closeDrawer,
    clearEvents,
  }
})
