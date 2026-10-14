import type { YdAgentActivity, YdAgentApproval, YdAgentHarnessState, YdAgentModel, YdAgentSendRequest, YdAgentSession, YdChatMessage } from '@yudream/components'
import type { AiFrontendContext, AiFrontendToolRequest, AiRunSnapshot } from '@/api/modules/platform-ai-runs'
import type { ChatMessage, ChatQuota, ChatSendPayload, ChatSession } from '@/api/modules/platform-chat'
import type { AiAssistantPreferences } from '@/utils/ai-assistant-context'
import type { AiRunEnvelope } from '@/utils/ai-run-stream'
import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import apiAi from '@/api/modules/platform-ai'
import { cancelAiRun, getAiRun, listAiRuns, replyAiFrontendTool, respondAiInteraction, resumeAiRun, startAiAssistantRun, subscribeAiRun } from '@/api/modules/platform-ai-runs'
import { createChatSession, deleteChatSession, fetchMyChatQuota, listChatMessages, listChatSessions, updateChatSession } from '@/api/modules/platform-chat'
import { defaultAiAssistantPreferences, frontendContextForPreferences, parseAiAssistantPreferences, parseAiFrontendToolRequest } from '@/utils/ai-assistant-context'
import { projectAiRunActivities, projectAiRunActivity, projectAiRunTool } from '@/utils/ai-run-projection'
import { canReconnectAiRun, isActiveAiRun, isNewAiCursor } from '@/utils/ai-run-stream'

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function messageProjection(item: ChatMessage): YdChatMessage | undefined {
  if (item.role !== 'USER' && item.role !== 'ASSISTANT') {
    return undefined
  }
  return {
    id: item.id,
    role: item.role === 'USER' ? 'user' : 'assistant',
    content: item.content || '',
    reasoning: item.reasoning,
    tools: item.tools,
    activities: item.activities?.filter(activity => !!activity.activityType).map(activity => ({ ...activity, activityType: activity.activityType! })),
    citations: item.citations,
    attachments: item.attachments,
    pending: item.status === 'PENDING' || item.status === 'STREAMING',
    error: item.status === 'FAILED',
  }
}

function statusProjection(status?: string): YdAgentHarnessState['status'] {
  switch (status?.toUpperCase()) {
    case 'QUEUED': case 'RUNNING': return 'running'
    case 'WAITING': case 'WAITING_FOR_INPUT': return 'waiting'
    case 'COMPLETED': return 'completed'
    case 'FAILED': return 'error'
    case 'CANCELLED': return 'cancelled'
    default: return 'idle'
  }
}

/** Root-owned subscriptions outlive every view/panel. Backend snapshots/events are the conversation authority. */
export const useAiAssistantStore = defineStore('aiAssistant', () => {
  const open = ref(false)
  const initialized = ref(false)
  const loading = ref(false)
  const sending = ref(false)
  const error = ref('')
  const sessions = ref<ChatSession[]>([])
  const sessionId = ref('')
  const modelId = ref('')
  const models = ref<YdAgentModel[]>([])
  const preferences = ref<AiAssistantPreferences>({ ...defaultAiAssistantPreferences })
  const quota = ref<ChatQuota>()
  const messagesBySession = ref<Record<string, YdChatMessage[]>>({})
  const runs = ref<Record<string, AiRunSnapshot>>({})
  const frontendRequests = ref<Record<string, { runId: string, request: AiFrontendToolRequest }>>({})
  const connections = ref<Record<string, 'connected' | 'reconnecting' | 'disconnected'>>({})
  const phases = ref<Record<string, string>>({})
  const controllers = new Map<string, AbortController>()
  const reconnectTimers = new Map<string, ReturnType<typeof setTimeout>>()
  const retries = new Map<string, number>()
  const consumedTools = new Set<string>()
  let owner = ''
  let epoch = 0
  let observerInitialized = false
  let observerLoading: Promise<void> | undefined
  const lifecycle = ref(0)

  const activeRun = computed(() => Object.values(runs.value).filter(run => run.sessionId === sessionId.value).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))[0])
  const messages = computed(() => messagesBySession.value[sessionId.value] || [])
  const activeSession = computed(() => sessions.value.find(session => session.id === sessionId.value))
  const busy = computed(() => sending.value || (!!activeRun.value && isActiveAiRun(activeRun.value.status)))
  const activeRunCount = computed(() => Object.values(runs.value).filter(run => isActiveAiRun(run.status)).length)
  const harness = computed<YdAgentHarnessState>(() => ({
    name: 'AgentScope Harness',
    runId: activeRun.value?.id,
    status: sending.value ? 'running' : statusProjection(activeRun.value?.status),
    phase: activeRun.value ? phases.value[activeRun.value.id] : undefined,
  }))
  const sessionItems = computed<YdAgentSession[]>(() => sessions.value.map(session => ({
    id: session.id,
    title: session.title,
    pinned: session.pinned,
    messageCount: session.messageCount,
    updatedAt: session.lastMessageAt,
    status: statusProjection(Object.values(runs.value).find(run => run.sessionId === session.id && isActiveAiRun(run.status))?.status),
  })))
  const activities = computed<YdAgentActivity[]>(() => (messages.value.at(-1)?.activities || []).map((activity, index) => ({
    id: `${activity.messageId || activeRun.value?.id || 'activity'}:${activity.phase || index}`,
    title: activity.title || activity.phase || '执行进度',
    kind: 'context',
    status: activity.status === 'complete' || activity.status === 'completed' ? 'complete' : activity.status === 'error' ? 'error' : activity.status === 'waiting' ? 'waiting' : activity.status === 'cancelled' ? 'complete' : 'running',
    detail: activity.content,
  })))
  const approvals = computed<YdAgentApproval[]>(() => Object.entries(frontendRequests.value).filter(([, item]) => item.runId === activeRun.value?.id).map(([id, item]) => ({
    id,
    title: item.request.name === 'ui.navigate' ? '助手请求打开页面' : '助手请求定位页面操作',
    description: item.request.name === 'ui.navigate' ? `打开已授权页面：${item.request.arguments.routeName}` : `定位操作：${item.request.arguments.actionCode}`,
    scope: '只执行本次导航或高亮，操作按钮仍由你点击',
  })))

  const preferenceKey = () => `yd.aiAssistant.preferences.${owner}`
  function setPreferences(patch: Partial<AiAssistantPreferences>) {
    preferences.value = parseAiAssistantPreferences({ ...preferences.value, ...patch })
    if (owner) {
      localStorage.setItem(preferenceKey(), JSON.stringify(preferences.value))
    }
  }
  function setError(value: unknown) {
    error.value = value instanceof Error ? value.message : 'AI 助手操作失败'
  }

  function reset(nextOwner = '') {
    epoch++
    lifecycle.value = epoch
    for (const controller of controllers.values()) {
      controller.abort()
    }
    for (const timer of reconnectTimers.values()) {
      clearTimeout(timer)
    }
    controllers.clear()
    reconnectTimers.clear()
    retries.clear()
    consumedTools.clear()
    owner = nextOwner
    observerInitialized = false
    observerLoading = undefined
    open.value = false
    initialized.value = false
    loading.value = false
    sending.value = false
    error.value = ''
    sessions.value = []
    sessionId.value = ''
    models.value = []
    modelId.value = ''
    runs.value = {}
    messagesBySession.value = {}
    frontendRequests.value = {}
    connections.value = {}
    phases.value = {}
    quota.value = undefined
    try {
      preferences.value = parseAiAssistantPreferences(JSON.parse(localStorage.getItem(preferenceKey()) || 'null'))
    }
    catch {
      preferences.value = { ...defaultAiAssistantPreferences }
    }
  }

  async function initializeObserver(userId: string) {
    if (!userId) {
      return
    }
    if (owner !== userId) {
      reset(userId)
    }
    if (observerInitialized) {
      return
    }
    if (observerLoading) {
      return observerLoading
    }
    const started = epoch
    const pending = listAiRuns().then((response) => {
      if (started !== epoch) {
        return
      }
      for (const run of response.data.slice().sort((a, b) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')))) {
        attachRun(run)
      }
      observerInitialized = true
    }).catch((cause) => {
      if (started === epoch) {
        setError(cause)
      }
    }).finally(() => {
      if (started === epoch) {
        observerLoading = undefined
      }
    })
    observerLoading = pending
    return pending
  }

  async function initialize(userId: string) {
    if (!userId || (owner === userId && (initialized.value || loading.value))) {
      return
    }
    if (owner !== userId) {
      reset(userId)
    }
    const started = epoch
    loading.value = true
    const results = await Promise.allSettled([listChatSessions(), initializeObserver(userId), apiAi.availableModels(), fetchMyChatQuota()])
    if (started !== epoch) {
      return
    }
    if (results[0].status === 'fulfilled') {
      sessions.value = results[0].value.data
    }
    else {
      setError(results[0].reason)
    }
    if (results[2].status === 'fulfilled') {
      models.value = results[2].value.data.filter(model => model.configured && model.kind.toLowerCase() === 'chat').map(model => ({
        id: `${model.providerCode}/${model.modelCode}`,
        label: `${model.providerName} · ${model.modelName}`,
        providerCode: model.providerCode,
        modelCode: model.modelCode,
      }))
      modelId.value = models.value[0]?.id || ''
    }
    if (results[3].status === 'fulfilled') {
      quota.value = results[3].value.data
    }
    initialized.value = true
    loading.value = false
    const last = localStorage.getItem(`yd.aiAssistant.session.${owner}`)
    const selected = sessions.value.find(session => session.id === last) || sessions.value[0]
    if (selected) {
      await selectSession(selected.id)
    }
    if (started !== epoch) {
      return
    }
    for (const run of Object.values(runs.value)) {
      if (isActiveAiRun(run.status)) {
        connect(run.id)
      }
    }
  }

  function answerFor(run: AiRunSnapshot): YdChatMessage {
    const list = messagesBySession.value[run.sessionId] ??= []
    let answer = list.find(message => message.id === `run:${run.id}`)
    if (!answer) {
      answer = list.findLast(message => message.role === 'assistant' && message.pending)
      if (answer) {
        answer.id = `run:${run.id}`
      }
      else {
        list.push({ id: `run:${run.id}`, role: 'assistant', content: '', pending: isActiveAiRun(run.status), tools: [], activities: [] })
        answer = list.at(-1)!
      }
    }
    return answer
  }

  function installSnapshot(raw: AiRunSnapshot) {
    const run = { ...raw, cursor: String(raw.cursor || '0'), activities: projectAiRunActivities(raw.activities, raw.id) }
    const current = runs.value[run.id]
    // A snapshot HTTP response may lag behind events already delivered by the live subscription.
    if (current && isNewAiCursor(current.cursor, run.cursor)) {
      return
    }
    runs.value[run.id] = run
    const businessChat = run.sessionId.startsWith('wiki:') || run.sessionId.startsWith('public-wiki:') || run.sessionId.startsWith('cms-') || run.sessionId.startsWith('agent-debug:')
    if (businessChat && run.inputText) {
      const list = taskMessages(run.sessionId)
      const optimistic = list.findLast(message => message.role === 'user' && message.context?.pendingRunInput && message.content === run.inputText)
      if (optimistic) {
        optimistic.id = `input:${run.id}`
        optimistic.context = {}
      }
      else if (!list.some(message => message.id === `input:${run.id}`)) {
        list.push({ id: `input:${run.id}`, role: 'user', content: run.inputText })
      }
    }
    if (businessChat || isActiveAiRun(run.status) || taskMessages(run.sessionId).some(message => message.id === `run:${run.id}`)) {
      const answer = answerFor(run)
      answer.content = run.content || (typeof run.result?.answer === 'string' ? run.result.answer : '')
      answer.reasoning = run.reasoning
      answer.pending = isActiveAiRun(run.status)
      answer.error = run.status === 'FAILED'
      answer.tools = (run.tools || []).flatMap((tool) => {
        const projected = projectAiRunTool(tool)
        return projected ? [projected] : []
      })
      answer.activities = (run.activities || []).flatMap((activity) => {
        const projected = projectAiRunActivity(activity, run.id)
        return projected ? [projected] : []
      })
      if (Array.isArray(run.result?.citations)) {
        answer.citations = run.result.citations as NonNullable<YdChatMessage['citations']>
      }
    }
    if (isActiveAiRun(run.status)) {
      const answer = answerFor(run)
      answer.content = run.content || ''
      answer.reasoning = run.reasoning || ''
      answer.tools = (run.tools || []).flatMap((tool) => {
        const projected = projectAiRunTool(tool)
        return projected ? [projected] : []
      })
      answer.activities = (run.activities || []).flatMap((activity) => {
        const projected = projectAiRunActivity(activity, run.id)
        return projected ? [projected] : []
      })
      answer.pending = true
      for (const request of run.pendingFrontendTools || []) {
        queueFrontendRequest(run.id, request)
      }
    }
  }

  async function loadMessages(id: string) {
    const started = epoch
    const response = await listChatMessages(id)
    if (started !== epoch) {
      return
    }
    messagesBySession.value[id] = response.data.flatMap((message) => {
      const projected = messageProjection(message)
      return projected ? [projected] : []
    })
    const run = Object.values(runs.value).find(item => item.sessionId === id && isActiveAiRun(item.status))
    if (run) {
      installSnapshot(run)
    }
  }

  async function selectSession(id: string) {
    if (!sessions.value.some(session => session.id === id)) {
      return
    }
    sessionId.value = id
    if (owner) {
      localStorage.setItem(`yd.aiAssistant.session.${owner}`, id)
    }
    const session = activeSession.value
    const matchedModel = models.value.find(model => model.providerCode === session?.providerCode && model.modelCode === session?.modelCode)
    if (matchedModel) {
      modelId.value = matchedModel.id
    }
    try {
      await loadMessages(id)
      const started = epoch
      const response = await listAiRuns(id)
      if (started !== epoch) {
        return
      }
      for (const run of response.data) {
        if (controllers.has(run.id)) {
          continue
        }
        installSnapshot(run)
        if (isActiveAiRun(run.status)) {
          connect(run.id)
        }
      }
    }
    catch (cause) {
      setError(cause)
    }
  }

  async function createSession(data: Partial<ChatSession> = {}) {
    const started = epoch
    const response = await createChatSession({ title: '新的对话', scopeType: 'GENERAL', ...data })
    if (started !== epoch) {
      return
    }
    sessions.value.unshift(response.data)
    messagesBySession.value[response.data.id] = []
    await selectSession(response.data.id)
    return response.data.id
  }

  async function updateSession(id: string, data: Partial<ChatSession>) {
    const started = epoch
    const response = await updateChatSession(id, data)
    if (started !== epoch) {
      return
    }
    sessions.value = sessions.value.map(session => session.id === id ? response.data : session)
  }

  async function removeSession(id: string) {
    if (Object.values(runs.value).some(run => run.sessionId === id && isActiveAiRun(run.status))) {
      throw new Error('请先停止正在执行的会话，再删除')
    }
    const started = epoch
    await deleteChatSession(id)
    if (started !== epoch) {
      return
    }
    sessions.value = sessions.value.filter(session => session.id !== id)
    delete messagesBySession.value[id]
    if (sessionId.value === id) {
      sessionId.value = ''
      if (sessions.value[0]) {
        await selectSession(sessions.value[0].id)
      }
    }
  }

  async function refreshQuota() {
    const started = epoch
    try {
      const response = await fetchMyChatQuota()
      if (started === epoch) {
        quota.value = response.data
      }
    }
    catch {
      /* keep the last known authoritative quota */
    }
  }

  async function send(request: YdAgentSendRequest, frontendContext?: AiFrontendContext, scope: Partial<ChatSendPayload> = {}) {
    if (busy.value || !request.text.trim()) {
      return
    }
    error.value = ''
    const started = epoch
    let id = sessionId.value
    const model = request.model || models.value.find(item => item.id === modelId.value)
    sending.value = true
    try {
      if (!id) {
        id = (await createSession()) || ''
      }
      if (started !== epoch) {
        return
      }
      if (!id) {
        throw new Error('会话尚未创建，请稍后重试')
      }
      const session = sessions.value.find(session => session.id === id)
      const response = await startAiAssistantRun(id, {
        ...scope,
        sessionId: id,
        question: request.text,
        providerCode: model?.providerCode,
        modelCode: model?.modelCode,
        scopeType: scope.scopeType ?? session?.scopeType,
        agentCode: scope.agentCode ?? session?.agentCode,
        spaceSlug: scope.spaceSlug ?? session?.spaceSlug,
        attachments: request.attachments,
        contextEnabled: preferences.value.routeAwareness || preferences.value.pageAwareness,
        frontendToolsEnabled: preferences.value.navigationTools || preferences.value.highlightTools,
        frontendContext: frontendContext ? frontendContextForPreferences(frontendContext, preferences.value) : undefined,
      })
      if (started !== epoch) {
        return
      }
      installSnapshot(response.data)
      await loadMessages(id)
      if (started !== epoch) {
        return
      }
      if (isActiveAiRun(response.data.status)) {
        connect(response.data.id)
      }
      await refreshQuota()
    }
    catch (cause) {
      if (started === epoch) {
        setError(cause)
      }
    }
    finally {
      if (started === epoch) {
        sending.value = false
      }
    }
  }

  function queueFrontendRequest(runId: string, raw: unknown) {
    const request = parseAiFrontendToolRequest(raw)
    if (!request) {
      return
    }
    if (typeof request.expiresAt === 'number' && request.expiresAt <= Date.now()) {
      return
    }
    const id = `${runId}:${request.toolCallId}`
    if (!consumedTools.has(id)) {
      frontendRequests.value[id] = { runId, request }
    }
  }

  function applyEvent(event: AiRunEnvelope) {
    const run = runs.value[event.runId]
    if (!run || !isNewAiCursor(event.cursor, run.cursor)) {
      return
    }
    run.cursor = event.cursor
    const answer = answerFor(run)
    const payload = event.payload
    if (typeof payload.callId === 'string') {
      run.generations ??= []
      let generation = run.generations.find(item => item.callId === payload.callId)
      if (!generation) {
        run.generations.push({ callId: payload.callId, content: '', reasoning: '', activities: [], tools: [] })
        generation = run.generations.at(-1)!
      }
      if (event.event === 'ai.generation') {
        if (event.action === 'reasoning') {
          generation.reasoning = (generation.reasoning || '') + (typeof payload.content === 'string' ? payload.content : '')
        }
        else {
          generation.content += typeof payload.content === 'string' ? payload.content : ''
        }
        generation.structured = payload.structured === true
        generation.providerCode = typeof payload.providerCode === 'string' ? payload.providerCode : generation.providerCode
        generation.modelCode = typeof payload.modelCode === 'string' ? payload.modelCode : generation.modelCode
      }
      else if (event.event === 'ai.message' && event.action === 'reasoning') {
        generation.reasoning = (generation.reasoning || '') + (typeof payload.content === 'string' ? payload.content : '')
      }
      else if (event.event === 'ai.progress') {
        const activity = projectAiRunActivity(payload.activity, run.id)
        if (activity) {
          (generation.activities ??= []).push(activity)
        }
      }
      else if (event.event === 'ai.tool') {
        const tool = projectAiRunTool(payload.tool, event.action)
        if (tool) {
          (generation.tools ??= []).push(tool)
        }
      }
      return
    }
    switch (event.event) {
      case 'ai.input':
        if (typeof payload.content === 'string') {
          const list = taskMessages(run.sessionId)
          const optimistic = list.findLast(message => message.role === 'user' && message.context?.pendingRunInput && message.content === payload.content)
          if (optimistic) {
            optimistic.id = `input:${run.id}`
            optimistic.context = undefined
          }
          else if (!list.some(message => message.id === `input:${run.id}`)) {
            list.splice(Math.max(0, list.indexOf(answer)), 0, { id: `input:${run.id}`, role: 'user', content: payload.content })
          }
        }
        break
      case 'ai.message':
        if (event.action === 'reasoning') {
          answer.reasoning = (answer.reasoning || '') + (typeof payload.content === 'string' ? payload.content : '')
        }
        else if (event.action === 'delta') {
          answer.content += typeof payload.content === 'string' ? payload.content : ''
        }
        run.content = answer.content
        run.reasoning = answer.reasoning
        break
      case 'ai.structured':
        if (event.action === 'delta') {
          run.structuredContent = (run.structuredContent || '') + (typeof payload.content === 'string' ? payload.content : '')
        }
        break
      case 'ai.progress': {
        if (event.action === 'waiting') {
          run.status = 'WAITING'
          const raw = record(payload.activity)
          if (Array.isArray(raw.pendingInteractions)) {
            run.pendingInteractions = raw.pendingInteractions as NonNullable<AiRunSnapshot['pendingInteractions']>
          }
        }
        else if (event.action === 'accepted' || event.action === 'running') {
          run.status = 'RUNNING'
        }
        const patch = projectAiRunActivity(payload.activity, run.id)
        if (!patch) {
          break
        }
        phases.value[run.id] = patch.title || patch.phase || ''
        answer.activities ??= []
        const previous = answer.activities.find(item => item.phase === patch.phase && item.activityType === patch.activityType)
        if (previous) {
          Object.assign(previous, patch)
        }
        else {
          answer.activities.push(patch)
        }
        run.activities = answer.activities
        break
      }
      case 'ai.tool': {
        const patch = projectAiRunTool(payload.tool, event.action)
        if (!patch) {
          break
        }
        answer.tools ??= []
        const previous = patch.toolCallId ? answer.tools.find(item => item.toolCallId === patch.toolCallId) : undefined
        if (previous) {
          Object.assign(previous, patch)
        }
        else {
          answer.tools.push(patch)
        }
        run.tools = answer.tools
        break
      }
      case 'ai.frontend':
        if (event.action === 'request') {
          queueFrontendRequest(run.id, payload)
        }
        else if (typeof payload.toolCallId === 'string') {
          delete frontendRequests.value[`${run.id}:${payload.toolCallId}`]
        }
        break
      case 'ai.result': {
        const result = record(payload.result)
        run.result = result
        if (typeof result.content === 'string') {
          answer.content = result.content
        }
        else if (typeof result.answer === 'string') {
          answer.content = result.answer
        }
        if (Array.isArray(result.citations)) {
          answer.citations = result.citations as NonNullable<YdChatMessage['citations']>
        }
        if (typeof result.reasoning === 'string') {
          answer.reasoning = result.reasoning
        }
        run.content = answer.content
        run.reasoning = answer.reasoning
        run.status = event.action === 'cancelled' ? 'CANCELLED' : 'COMPLETED'
        finishRun(run)
        break
      }
      case 'ai.error':
        run.status = event.action === 'cancelled' ? 'CANCELLED' : 'FAILED'
        run.error = typeof payload.message === 'string' ? payload.message : 'AI 运行失败'
        answer.error = run.status === 'FAILED'
        if (!answer.content) {
          answer.content = run.error
        }
        finishRun(run)
        break
    }
  }

  function finishRun(run: AiRunSnapshot) {
    const answer = answerFor(run)
    answer.pending = false
    run.pendingInteractions = []
    answer.error = run.status === 'FAILED'
    if (!answer.content && run.error) {
      answer.content = run.error
    }
    if (run.status === 'FAILED' && run.error) {
      error.value = run.error
    }
    for (const activity of answer.activities || []) {
      if (activity.status === 'running') {
        activity.status = run.status === 'FAILED' ? 'error' : run.status === 'CANCELLED' ? 'cancelled' : 'complete'
      }
    }
    for (const tool of answer.tools || []) {
      if (tool.status === 'executing') {
        tool.status = run.status === 'FAILED' ? 'error' : run.status === 'CANCELLED' ? 'cancelled' : 'complete'
      }
    }
    for (const [key, item] of Object.entries(frontendRequests.value)) {
      if (item.runId === run.id) {
        delete frontendRequests.value[key]
      }
    }
    controllers.get(run.id)?.abort()
    void refreshQuota()
    if (sessions.value.some(session => session.id === run.sessionId)) {
      void loadMessages(run.sessionId).catch(setError)
      const started = epoch
      void listChatSessions().then((response) => {
        if (started === epoch) {
          sessions.value = response.data
        }
      }).catch(setError)
    }
  }

  function connect(id: string) {
    const run = runs.value[id]
    if (!run || !isActiveAiRun(run.status) || controllers.has(id)) {
      return
    }
    const started = epoch
    const controller = new AbortController()
    controllers.set(id, controller)
    connections.value[id] = 'connected'
    let failure: unknown
    void subscribeAiRun(id, run.cursor, controller.signal, (event) => {
      if (started !== epoch) {
        return
      }
      if (!runs.value[id] || !isNewAiCursor(event.cursor, runs.value[id].cursor)) {
        return
      }
      retries.set(id, 0)
      applyEvent(event)
    }).catch((cause) => {
      failure = cause
    }).finally(async () => {
      if (controllers.get(id) !== controller) {
        return
      }
      controllers.delete(id)
      if (started !== epoch || !isActiveAiRun(runs.value[id]?.status || '')) {
        return
      }
      if (controller.signal.aborted) {
        return
      }
      // A crashed writer may persist its terminal status before it can append a terminal SSE frame.
      try {
        const snapshot = await getAiRun(id)
        if (started !== epoch) {
          return
        }
        installSnapshot(snapshot.data)
        if (!isActiveAiRun(snapshot.data.status)) {
          finishRun(snapshot.data)
          return
        }
      }
      catch (cause) {
        failure ??= cause
      }
      if (started !== epoch) {
        return
      }
      if (controllers.has(id)) {
        return
      }
      const attempt = (retries.get(id) || 0) + 1
      retries.set(id, attempt)
      if (attempt > 5 || (failure && !canReconnectAiRun(failure))) {
        connections.value[id] = 'disconnected'
        error.value = failure instanceof Error ? failure.message : 'AI 进度暂时断开；任务仍在后端执行，请恢复连接'
        return
      }
      connections.value[id] = 'reconnecting'
      reconnectTimers.set(id, setTimeout(() => {
        reconnectTimers.delete(id)
        if (started === epoch) {
          connect(id)
        }
      }, Math.min(1000 * 2 ** (attempt - 1), 15000)))
    })
  }

  async function reconnect(runId?: string) {
    const run = runId ? runs.value[runId] : activeRun.value
    if (!run) {
      return
    }
    const started = epoch
    const response = await getAiRun(run.id)
    if (started !== epoch) {
      return
    }
    controllers.get(run.id)?.abort()
    controllers.delete(run.id)
    const timer = reconnectTimers.get(run.id)
    if (timer) {
      clearTimeout(timer)
    }
    reconnectTimers.delete(run.id)
    retries.delete(run.id)
    error.value = ''
    installSnapshot(response.data)
    if (isActiveAiRun(response.data.status)) {
      connect(run.id)
    }
    else if (sessions.value.some(session => session.id === run.sessionId)) {
      await loadMessages(run.sessionId)
    }
  }

  async function stop(runId?: string) {
    const run = runId ? runs.value[runId] : activeRun.value
    if (!run || !isActiveAiRun(run.status)) {
      return
    }
    const started = epoch
    const response = await cancelAiRun(run.id)
    if (started !== epoch) {
      return
    }
    installSnapshot(response.data)
    if (!isActiveAiRun(response.data.status)) {
      finishRun(response.data)
    }
  }

  function lifecycleVersion() {
    return lifecycle.value
  }

  async function respondInteraction(runId: string, requestId: string, approved: boolean) {
    const started = epoch
    attachRun((await respondAiInteraction(runId, requestId, { approved })).data, started)
  }

  function taskMessages(taskSessionId: string) {
    return messagesBySession.value[taskSessionId] ??= []
  }

  function appendTaskInput(taskSessionId: string, text: string) {
    taskMessages(taskSessionId).push({ id: `input:${crypto.randomUUID()}`, role: 'user', content: text, context: { pendingRunInput: true } })
  }

  async function resume(runId?: string) {
    const run = runId ? runs.value[runId] : activeRun.value
    if (!run?.resumeSupported) {
      throw new Error('该任务没有可接续的 Harness 检查点')
    }
    const started = epoch
    const response = await resumeAiRun(run.id)
    attachRun(response.data, started)
  }

  function attachRun(run: AiRunSnapshot, expectedEpoch = epoch) {
    if (expectedEpoch !== epoch) {
      return
    }
    installSnapshot(run)
    if (isActiveAiRun(run.status)) {
      connect(run.id)
    }
  }

  async function restoreTask(taskSessionId: string) {
    const started = epoch
    const response = await listAiRuns(taskSessionId)
    if (started !== epoch) {
      return
    }
    for (const run of response.data.slice().sort((a, b) => String(a.createdAt || '').localeCompare(String(b.createdAt || '')))) {
      if (!controllers.has(run.id)) {
        attachRun(run)
      }
    }
  }

  async function decideFrontendTool(id: string, approved: boolean, execute: (request: AiFrontendToolRequest) => Promise<Record<string, unknown>>) {
    const pending = frontendRequests.value[id]
    if (!pending || consumedTools.has(id)) {
      return
    }
    consumedTools.add(id)
    delete frontendRequests.value[id]
    const started = epoch
    let reply: { ok: boolean, result?: Record<string, unknown>, error?: string }
    if (!approved) {
      reply = { ok: false, error: '用户拒绝本次前端工具操作' }
    }
    else {
      try {
        const snapshot = (await getAiRun(pending.runId)).data
        if (started !== epoch) {
          return
        }
        const current = snapshot.pendingFrontendTools?.find(request => request.toolCallId === pending.request.toolCallId)
        if (!current) {
          throw new Error('该前端工具请求已经结束')
        }
        reply = current.responseRecorded ? { ok: true, result: { responseRecorded: true } } : { ok: true, result: await execute(pending.request) }
      }
      catch (cause) {
        reply = { ok: false, error: cause instanceof Error ? cause.message : '前端工具操作失败' }
      }
    }
    try {
      await replyAiFrontendTool(pending.runId, pending.request.toolCallId, reply)
    }
    catch (cause) {
      consumedTools.delete(id)
      throw cause
    }
  }

  return {
    open,
    initialized,
    loading,
    sending,
    error,
    sessions,
    sessionId,
    activeSession,
    modelId,
    models,
    preferences,
    quota,
    messages,
    runs,
    activeRun,
    activeRunCount,
    busy,
    harness,
    sessionItems,
    activities,
    approvals,
    frontendRequests,
    connections,
    initialize,
    initializeObserver,
    reset,
    selectSession,
    createSession,
    updateSession,
    removeSession,
    send,
    stop,
    reconnect,
    refreshQuota,
    setPreferences,
    setError,
    decideFrontendTool,
    attachRun,
    restoreTask,
    lifecycleVersion,
    resume,
    respondInteraction,
    taskMessages,
    appendTaskInput,
  }
})
