import {onScopeDispose, reactive, ref} from 'vue'
import { authoritativeAguiText } from './agui-result'

/**
 * yd AI 对话流式问答 composable。
 *
 * 支持两种协议与两种传输：
 * - 协议 `yd`：自定义 SSE（delta / tool / citations / done / error），用于 Wiki 问答等既有端点。
 * - 协议 `agui`：AG-UI 事件（RUN_STARTED / TEXT_MESSAGE_CHUNK / TOOL_CALL_* / ACTIVITY_* / RUN_FINISHED / RUN_ERROR），
 *   同时支持 SSE 与 WebSocket 两种传输。
 *
 * WebSocket 使用「一问一连接」模型：每次 send 建立一条 WS，发送请求帧，收到 RUN_FINISHED/RUN_ERROR 或服务端关闭后结束。
 */

export interface YdChatCitation {
  title: string
  path?: string
  nodeId?: string
  spaceSlug?: string
  spaceName?: string
  sourceUrl?: string
  /** 检索命中的原文片段，用于定位并高亮文章中的具体段落 */
  excerpt?: string
  /** 引用页面中的相关图片（站内文件地址 + 说明） */
  images?: { url?: string, caption?: string }[]
}

export interface YdChatToolEvent {
  toolCallId?: string
  toolName?: string
  /** 工具动作（如 CMS 画布的 replace-page / add-html），来自结构化工具结果顶层 action 字段 */
  action?: string
  message?: string
  status?: 'executing' | 'complete' | 'error' | 'cancelled'
  payload?: Record<string, unknown>
}

export interface YdChatRetrievalHit {
  score?: number
  kind?: string
  nodeId?: string
  title: string
  path?: string
  spaceSlug?: string
  spaceName?: string
  sourceUrl?: string
  excerpt?: string
}

export interface YdChatGraphNode {
  id: string
  title: string
  type?: string
  role?: string
  score?: number
  path?: string
}

export interface YdChatGraphEdge {
  source: string
  target: string
  weight?: number
  signal?: string
}

export interface YdChatGraph {
  query?: string
  nodes: YdChatGraphNode[]
  edges: YdChatGraphEdge[]
}

export interface YdChatActivity {
  messageId?: string
  activityType: 'wiki-progress' | 'wiki-retrieval' | 'wiki-graph' | string
  phase?: string
  status?: 'running' | 'complete' | 'error' | 'cancelled' | string
  title?: string
  content?: string
  query?: string
  hits?: YdChatRetrievalHit[]
  graph?: YdChatGraph
}

export interface YdChatAction {
  label: string
  action: 'copy' | 'open' | 'submit' | string
  value: string
}

export interface YdChatAttachment {
  fileId?: string
  fileName: string
  contentType?: string
  size?: number
  kind?: 'IMAGE' | 'DOCUMENT' | 'FILE' | string
  url?: string
  extractedText?: string
  dataUrl?: string
}

export interface YdChatMessage {
  id?: string
  role: 'user' | 'assistant'
  content: string
  /** 深度思考 / reasoning，独立于正文流式展示 */
  reasoning?: string
  citations?: YdChatCitation[]
  tools?: YdChatToolEvent[]
  activities?: YdChatActivity[]
  actions?: YdChatAction[]
  attachments?: YdChatAttachment[]
  /** 错误气泡 */
  error?: boolean
  /** 生成中（已占位的 assistant 消息，等待或正在流式输出） */
  pending?: boolean
}

export interface YdChatHistoryTurn {
  role: string
  content: string
}

export type YdChatProtocol = 'yd' | 'agui'
export type YdChatTransport = 'sse' | 'websocket'

export interface UseYdChatStreamOptions {
  /** 流式端点完整 URL（含 DEV proxy 前缀），可为函数以读取最新值 */
  endpoint: string | (() => string)
  /** 流协议，默认 `yd`（自定义 SSE） */
  protocol?: YdChatProtocol
  /** 传输方式，默认 `sse` */
  transport?: YdChatTransport
  /** 构造请求体，history 为不含本轮问题的历史 */
  buildBody?: (question: string, history: YdChatHistoryTurn[], attachments?: YdChatAttachment[]) => Record<string, unknown>
  /** WebSocket 请求帧（不传时复用 buildBody，最终缺省为 { question, history }） */
  buildMessage?: (question: string, history: YdChatHistoryTurn[], attachments?: YdChatAttachment[]) => unknown
  /** WebSocket 鉴权 token（用于拼接 ?token= 或写入首帧） */
  getWebSocketToken?: () => string | undefined
  /** 携带历史的最大轮数（默认 10） */
  historyLimit?: number
  /** 收到工具调用事件（如检索开始/完成） */
  onTool?: (tool: YdChatToolEvent) => void
  /**
   * v2 客户端工具请求（AG-UI TOOL_CALL_REQUEST，仅 WebSocket 传输）：
   * 在调用方真实执行工具并返回结果，框架负责把结果帧回传服务端继续模型循环。
   */
  onToolCallRequest?: (request: YdChatToolCallRequest) => Promise<YdChatToolCallReply>
  /** 一条回答完成 */
  onDone?: (message: YdChatMessage) => void
  /** 回答失败 */
  onError?: (message: YdChatMessage, error: unknown) => void
}

export interface YdChatToolCallRequest {
  toolCallId: string
  toolName: string
  args: Record<string, unknown>
}

export type YdChatToolCallReply =
  | { ok: true, result?: Record<string, unknown> }
  | { ok: false, error: string }

interface SseEventFrame {
  event: string
  data: string
}

export function useYdChatStream(options: UseYdChatStreamOptions) {
  const messages = ref<YdChatMessage[]>([])
  const streaming = ref(false)
  /** 流式过程中的工具状态提示（如「正在检索知识库」） */
  const toolHint = ref('')
  let abortController: AbortController | null = null
  let socket: WebSocket | null = null
  let manuallyStopped = false

  function resolveEndpoint(): string {
    return typeof options.endpoint === 'function' ? options.endpoint() : options.endpoint
  }

  function buildHistory(): YdChatHistoryTurn[] {
    const limit = options.historyLimit ?? 10
    return messages.value
      .filter(item => !item.error && !item.pending)
      .slice(-limit)
      .map(item => ({role: item.role, content: item.content}))
  }

  async function send(text: string, attachments?: YdChatAttachment[]): Promise<void> {
    const question = text.trim()
    if (!question || streaming.value) {
      return
    }
    const history = buildHistory()
    const userId = `user-${Date.now()}`
    messages.value.push({id: userId, role: 'user', content: question, attachments: attachments ?? []})
    // 后续流事件必须修改响应式代理；修改 push 前的原始对象不会触发 Vue 视图更新。
    const answer = reactive<YdChatMessage>({
      id: `assistant-${Date.now()}`,
      role: 'assistant',
      content: '',
      pending: true,
      citations: [],
      tools: [],
      activities: [],
    })
    messages.value.push(answer)
    streaming.value = true
    toolHint.value = ''
    manuallyStopped = false

    const transport = options.transport ?? 'sse'
    try {
      if (transport === 'websocket') {
        await readWebSocket(answer, question, history, attachments)
      } else {
        await readServerSentEvents(answer, question, history, attachments)
      }
      if (answer.error) {
        options.onError?.(answer, new Error(answer.content || '问答失败，请稍后重试'))
      } else {
        // Custom SSE endpoints can close normally without a terminal frame. A normal
        // transport completion must not leave process rows in their running state.
        settleRunningState(answer, 'complete')
        finalizeAnswer(answer)
        options.onDone?.(answer)
      }
    } catch (error) {
      if (isAbort(error)) {
        // 用户主动停止：保留已生成的部分，并收敛运行中的过程状态。
        settleRunningState(answer, 'cancelled')
        answer.pending = false
        if (!answer.content && !answer.reasoning && !answer.tools?.length && !answer.activities?.length) {
          messages.value = messages.value.filter(item => item !== answer)
        }
        return
      }
      answer.pending = false
      answer.error = true
      settleRunningState(answer, 'error')
      answer.content = answer.content || (error instanceof Error ? error.message : '问答失败，请稍后重试')
      options.onError?.(answer, error)
    } finally {
      closeTransport()
      streaming.value = false
      toolHint.value = ''
    }
  }

  function finalizeAnswer(answer: YdChatMessage) {
    answer.pending = false
    if (!answer.content && !answer.reasoning && !answer.tools?.length && !answer.activities?.length) {
      answer.content = '（没有回答）'
    }
  }

  function isAbort(error: unknown): boolean {
    return error instanceof DOMException && error.name === 'AbortError'
  }

  function abortError(): DOMException {
    return new DOMException('生成已停止', 'AbortError')
  }

  function closeTransport() {
    if (abortController) {
      abortController.abort()
      abortController = null
    }
    if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
      socket.close()
    }
    socket = null
  }

  /** v2 客户端工具：真实执行后经同一条 WebSocket 回传结果帧，服务端模型循环据此继续 */
  async function replyToolCall(request: YdChatToolCallRequest) {
    // 固定本轮请求对应的 socket；工具执行期间不要依赖全局引用是否被其他收尾逻辑清空。
    const current = socket
    let reply: YdChatToolCallReply
    let timeoutId: ReturnType<typeof setTimeout> | undefined
    try {
      const execution = options.onToolCallRequest
        ? options.onToolCallRequest(request)
        : Promise.resolve<YdChatToolCallReply>({ ok: false, error: '当前客户端未注册画布工具执行器' })
      const timeout = new Promise<YdChatToolCallReply>((resolve) => {
        timeoutId = setTimeout(() => resolve({ ok: false, error: '客户端画布工具执行超时' }), 20000)
      })
      reply = await Promise.race([execution, timeout])
    } catch (error) {
      reply = { ok: false, error: error instanceof Error ? error.message : '工具执行失败' }
    } finally {
      if (timeoutId !== undefined) {
        clearTimeout(timeoutId)
      }
    }
    if (current && current.readyState === WebSocket.OPEN) {
      current.send(JSON.stringify({
        type: 'TOOL_RESULT',
        toolCallId: request.toolCallId,
        ok: reply.ok,
        ...(reply.ok ? { result: reply.result ?? {} } : { error: reply.error }),
      }))
    }
  }

  // ---------------------------------------------------------------- SSE

  async function readServerSentEvents(answer: YdChatMessage, question: string, history: YdChatHistoryTurn[], attachments?: YdChatAttachment[]): Promise<void> {
    const controller = new AbortController()
    abortController = controller
    const token = localStorage.getItem('token')
    const body = options.buildBody
      ? options.buildBody(question, history, attachments)
      : {question, history, attachments}
    let terminal: 'finished' | 'error' | null = null

    const response = await fetch(resolveEndpoint(), {
      method: 'POST',
      headers: {
        'Accept': 'text/event-stream',
        'Content-Type': 'application/json',
        'Accept-Language': 'zh-CN',
        ...(token ? {Authorization: token} : {}),
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    })
    if (!response.ok || !response.body) {
      throw new Error(await readErrorMessage(response))
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (!terminal) {
      const {done, value} = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, {stream: true})
      const frames = parseSseFrames(buffer)
      buffer = frames.rest
      for (const frame of frames.items) {
        terminal = handleFrame(frame, answer) ?? terminal
        if (terminal) {
          break
        }
      }
    }
    if (terminal) {
      await reader.cancel()
      return
    }
    buffer += decoder.decode()
    // EOF may arrive immediately after the terminal event without the SSE blank-line delimiter.
    const tail = parseSseFrames(buffer.endsWith('\n\n') ? buffer : `${buffer}\n\n`)
    for (const frame of tail.items) {
      terminal = handleFrame(frame, answer) ?? terminal
      if (terminal) {
        break
      }
    }
    if (options.protocol === 'agui' && !terminal) {
      throw new Error('流式连接在回答完成前中断')
    }
  }

  function parseSseFrames(buffer: string): { items: SseEventFrame[], rest: string } {
    const frames: SseEventFrame[] = []
    const blocks = buffer.split(/\r?\n\r?\n/)
    const rest = blocks.pop() ?? ''
    for (const block of blocks) {
      let event = 'message'
      const dataLines: string[] = []
      for (const rawLine of block.split(/\r?\n/)) {
        const line = rawLine.replace(/\r$/, '')
        if (line.startsWith('event:')) {
          event = line.slice(6).trim() || 'message'
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).replace(/^ /, ''))
        } else if (line.startsWith(':')) {
          // 心跳注释行，忽略
          continue
        }
      }
      if (dataLines.length) {
        frames.push({event, data: dataLines.join('\n')})
      }
    }
    return {items: frames, rest}
  }

  // ---------------------------------------------------------------- WebSocket

  function readWebSocket(answer: YdChatMessage, question: string, history: YdChatHistoryTurn[], attachments?: YdChatAttachment[]): Promise<void> {
    return new Promise<void>((resolve, reject) => {
      const endpoint = toWebSocketUrl(resolveEndpoint(), options.getWebSocketToken?.())
      let settled = false
      let terminalReceived = false
      let currentSocket: WebSocket

      function finish(error?: unknown) {
        if (settled) {
          return
        }
        settled = true
        if (socket === currentSocket) {
          socket = null
        }
        if (error) {
          reject(error)
        } else {
          resolve()
        }
      }

      try {
        currentSocket = new WebSocket(endpoint)
        socket = currentSocket
      } catch (error) {
        finish(error)
        return
      }

      currentSocket.onopen = () => {
        const payload = options.buildMessage
          ? options.buildMessage(question, history, attachments)
          : options.buildBody
            ? options.buildBody(question, history, attachments)
            : {question, history, attachments}
        currentSocket.send(JSON.stringify(payload))
      }

      currentSocket.onmessage = (event) => {
        let parsed: unknown
        try {
          parsed = JSON.parse(String(event.data))
        } catch {
          return
        }
        const result = handleAguiEvent(parsed, answer)
        if (result === 'finished' || result === 'error') {
          terminalReceived = true
          currentSocket.close()
          finish()
        }
      }

      currentSocket.onerror = () => {
        // onclose 会随后触发；这里仅记录，避免重复 reject
      }

      currentSocket.onclose = () => {
        if (settled) {
          return
        }
        if (manuallyStopped) {
          finish(abortError())
          return
        }
        if (!terminalReceived) {
          finish(new Error('WebSocket 在回答完成前中断'))
          return
        }
        finish()
      }
    })
  }

  function toWebSocketUrl(httpUrl: string, token?: string): string {
    const url = new URL(httpUrl, window.location.origin)
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
    if (token) {
      url.searchParams.set('token', token)
    }
    return url.toString()
  }

  // ---------------------------------------------------------------- 事件分发

  function handleFrame(frame: SseEventFrame, answer: YdChatMessage): 'finished' | 'error' | null {
    if (options.protocol === 'agui') {
      let parsed: unknown
      try {
        parsed = JSON.parse(frame.data)
      } catch {
        return null
      }
      const result = handleAguiEvent(parsed, answer)
      return result === 'finished' || result === 'error' ? result : null
    }

    // 自定义 yd 协议
    let event: Record<string, unknown>
    try {
      event = JSON.parse(frame.data)
    } catch {
      return null
    }
    switch (event.type) {
      case 'delta':
        // 首帧正文不解除 pending：pending 表示“本轮运行进行中”，仅在 done/error/终止时收敛，
        // 否则流式中途就会露出复制/重新回答等完成态操作。
        answer.content += typeof event.text === 'string' ? event.text : ''
        break
      case 'reasoning':
        answer.reasoning = (answer.reasoning ?? '') + (typeof event.text === 'string' ? event.text : '')
        break
      case 'tool':
        upsertTool(answer, {
          toolName: typeof event.toolName === 'string' ? event.toolName : undefined,
          message: typeof event.message === 'string' ? event.message : undefined,
          status: 'executing',
        })
        toolHint.value = (typeof event.message === 'string' && event.message) || '正在调用工具…'
        options.onTool?.(answer.tools?.at(-1)!)
        break
      case 'citations':
        if (Array.isArray(event.citations)) {
          answer.citations = normalizeCitations(event.citations)
        }
        break
      case 'done':
        answer.pending = false
        break
      case 'error':
        answer.pending = false
        answer.error = true
        answer.content = answer.content || String(event.message || '问答失败，请稍后重试')
        break
    }
    return null
  }

  function handleAguiEvent(raw: unknown, answer: YdChatMessage): 'finished' | 'error' | 'continue' {
    const event = (raw && typeof raw === 'object' ? raw : {}) as Record<string, unknown>
    const type = String(event.type ?? '')
    switch (type) {
      case 'RUN_STARTED':
        return 'continue'
      case 'TEXT_MESSAGE_CHUNK':
        // 同上：正文增量不解除 pending
        answer.content += typeof event.delta === 'string' ? event.delta : ''
        return 'continue'
      case 'THINKING_TEXT_MESSAGE_START':
      case 'THINKING_TEXT_MESSAGE_CONTENT':
      case 'THINKING_TEXT_MESSAGE_END':
        answer.reasoning = (answer.reasoning ?? '') + (typeof event.delta === 'string' ? event.delta : '')
        return 'continue'
      case 'TOOL_CALL_START':
        upsertTool(answer, {
          toolCallId: typeof event.toolCallId === 'string' ? event.toolCallId : undefined,
          toolName: typeof event.toolCallName === 'string' ? event.toolCallName : undefined,
          status: 'executing',
        })
        toolHint.value = `正在调用${answer.tools?.at(-1)?.toolName ?? '工具'}…`
        options.onTool?.(answer.tools?.at(-1)!)
        return 'continue'
      case 'TOOL_CALL_REQUEST': {
        // v2 客户端工具：服务端挂起模型循环，等待本端在画布真实执行后回传结果
        const toolCallId = typeof event.toolCallId === 'string' ? event.toolCallId : ''
        const toolName = typeof event.toolCallName === 'string' ? event.toolCallName : ''
        const args = (event.content && typeof event.content === 'object' ? event.content : {}) as Record<string, unknown>
        upsertTool(answer, { toolCallId, toolName, status: 'executing' })
        toolHint.value = `正在执行${toolName || '画布工具'}…`
        options.onTool?.(answer.tools?.at(-1)!)
        void replyToolCall({ toolCallId, toolName, args })
        return 'continue'
      }
      case 'TOOL_CALL_RESULT': {
        const content = parseStructuredContent(event.content)
        const payload = normalizePayload(content)
        const toolName = typeof event.toolCallName === 'string'
          ? event.toolCallName
          : typeof content?.toolName === 'string'
            ? content.toolName
            : undefined
        upsertTool(answer, {
          toolCallId: typeof event.toolCallId === 'string' ? event.toolCallId : undefined,
          toolName,
          action: typeof content?.action === 'string' ? content.action : undefined,
          message: typeof event.message === 'string' ? event.message : extractToolMessage(content),
          status: 'complete',
          payload,
        })
        if (toolName === 'wiki.search' && Array.isArray(payload?.hits)) {
          upsertActivity(answer, normalizeRetrievalActivity({
            ...payload,
            title: typeof content?.message === 'string' ? content.message : undefined,
          }, answer.id))
        }
        options.onTool?.(answer.tools?.at(-1)!)
        return 'continue'
      }
      case 'ACTIVITY_SNAPSHOT': {
        const activity = normalizeActivity(
          event.content,
          typeof event.messageId === 'string' ? event.messageId : answer.id,
          typeof event.activityType === 'string' ? event.activityType : undefined,
        )
        if (activity) {
          upsertActivity(answer, activity)
          updateToolHint(activity)
        }
        return 'continue'
      }
      case 'ACTIVITY_DELTA': {
        const messageId = typeof event.messageId === 'string' ? event.messageId : answer.id
        const patch = normalizePatch(event.patch ?? event.content)
        if (patch.length) {
          applyActivityPatch(answer, messageId, patch, typeof event.activityType === 'string' ? event.activityType : undefined)
          const activity = answer.activities?.find(item => (item.messageId || answer.id) === (messageId || answer.id) && item.activityType === event.activityType)
            ?? answer.activities?.find(item => (item.messageId || answer.id) === (messageId || answer.id))
          if (activity) updateToolHint(activity)
        }
        return 'continue'
      }
      case 'RUN_FINISHED':
        applyAguiResult(event, answer)
        settleRunningState(answer, 'complete')
        answer.pending = false
        return 'finished'
      case 'RUN_ERROR':
        settleRunningState(answer, 'error')
        answer.pending = false
        answer.error = true
        answer.content = answer.content || String(event.message || '问答失败，请稍后重试')
        return 'error'
      default:
        return 'continue'
    }
  }

  function settleRunningState(answer: YdChatMessage, status: 'complete' | 'error' | 'cancelled') {
    answer.tools?.forEach((tool) => {
      if (!tool.status || tool.status === 'executing') {
        tool.status = status
      }
    })
    answer.activities?.forEach((activity) => {
      if (isRunningActivityStatus(activity.status)) {
        activity.status = status
      }
    })
  }

  function isRunningActivityStatus(status?: string): boolean {
    const normalized = status?.trim().toLowerCase().replaceAll('_', '-')
    return !normalized || ['running', 'pending', 'started', 'executing', 'processing', 'in-progress'].includes(normalized)
  }

  function applyAguiResult(event: Record<string, unknown>, answer: YdChatMessage) {
    const result = event.result
    if (!result || typeof result !== 'object') {
      return
    }
    const typed = result as Record<string, unknown>
    if (Array.isArray(typed.citations)) {
      answer.citations = normalizeCitations(typed.citations)
    }
    if (Array.isArray(typed.actions)) {
      answer.actions = normalizeActions(typed.actions)
    }
    answer.content = authoritativeAguiText(answer.content, typed.content)
    answer.reasoning = authoritativeAguiText(answer.reasoning, typed.reasoning)
  }

  function upsertTool(answer: YdChatMessage, patch: YdChatToolEvent) {
    if (!answer.tools) {
      answer.tools = []
    }
    const existing = patch.toolCallId
      ? answer.tools.find(item => item.toolCallId === patch.toolCallId)
      : answer.tools[answer.tools.length - 1]
    if (existing && (patch.status === 'complete' || patch.status === 'error')) {
      Object.assign(existing, patch)
      return
    }
    answer.tools.push(patch)
  }

  function extractToolMessage(content: Record<string, unknown> | null): string {
    return content && typeof content.message === 'string' ? content.message : ''
  }

  function parseStructuredContent(content: unknown): Record<string, unknown> | null {
    if (content && typeof content === 'object' && !Array.isArray(content)) {
      return content as Record<string, unknown>
    }
    if (typeof content !== 'string') {
      return null
    }
    try {
      const parsed = JSON.parse(content)
      return parsed && typeof parsed === 'object' && !Array.isArray(parsed)
        ? parsed as Record<string, unknown>
        : null
    } catch {
      return null
    }
  }

  function parseJsonContent(content: unknown): unknown {
    if (typeof content !== 'string') return content
    try {
      return JSON.parse(content)
    } catch {
      return content
    }
  }

  function normalizePayload(content: Record<string, unknown> | null): Record<string, unknown> | undefined {
    if (!content) {
      return undefined
    }
    const payload = content.payload
    if (payload && typeof payload === 'object' && !Array.isArray(payload)) {
      return payload as Record<string, unknown>
    }
    return content
  }

  function normalizeActivity(content: unknown, messageId?: string, eventActivityType?: string): YdChatActivity | null {
    const typed = parseStructuredContent(content)
    if (!typed) {
      return null
    }
    const activityType = typeof typed.activityType === 'string' ? typed.activityType : eventActivityType || ''
    if (!activityType) {
      return null
    }
    const activity: YdChatActivity = {
      messageId: typeof typed.messageId === 'string' ? typed.messageId : messageId,
      activityType,
      phase: typeof typed.phase === 'string' ? typed.phase : undefined,
      status: typeof typed.status === 'string' ? typed.status : undefined,
      title: typeof typed.title === 'string' ? typed.title : undefined,
      content: typeof typed.content === 'string' ? typed.content : undefined,
      query: typeof typed.query === 'string' ? typed.query : undefined,
    }
    if (activityType === 'wiki-retrieval') {
      activity.hits = normalizeRetrievalHits(typed.hits)
    }
    if (activityType === 'wiki-graph') {
      activity.graph = normalizeGraph(typed.graph)
    }
    return activity
  }

  function normalizeRetrievalActivity(payload: Record<string, unknown>, messageId?: string): YdChatActivity | null {
    const hits = normalizeRetrievalHits(payload.hits)
    if (!hits.length) {
      return null
    }
    return {
      messageId,
      activityType: 'wiki-retrieval',
      status: 'complete',
      title: typeof payload.title === 'string' ? payload.title : '检索知识库',
      content: typeof payload.content === 'string' ? payload.content : undefined,
      query: typeof payload.query === 'string' ? payload.query : undefined,
      hits,
    }
  }

  function normalizeRetrievalHits(raw: unknown): YdChatRetrievalHit[] {
    if (!Array.isArray(raw)) {
      return []
    }
    return raw.flatMap((item) => {
      if (!item || typeof item !== 'object') return []
      const source = item as Record<string, unknown>
      const title = typeof source.title === 'string' ? source.title : ''
      if (!title) return []
      return [{
        title,
        score: typeof source.score === 'number' ? source.score : undefined,
        kind: typeof source.kind === 'string' ? source.kind : undefined,
        nodeId: source.nodeId == null ? undefined : String(source.nodeId),
        path: source.path == null ? undefined : String(source.path),
        spaceSlug: source.spaceSlug == null ? undefined : String(source.spaceSlug),
        spaceName: source.spaceName == null ? undefined : String(source.spaceName),
        sourceUrl: source.sourceUrl == null ? undefined : String(source.sourceUrl),
        excerpt: typeof source.excerpt === 'string'
          ? source.excerpt
          : typeof source.content === 'string'
            ? source.content
            : undefined,
      }]
    })
  }

  function normalizeGraph(raw: unknown): YdChatGraph | undefined {
    const source = raw && typeof raw === 'object' ? raw as Record<string, unknown> : null
    if (!source) return undefined
    const nodes = Array.isArray(source.nodes) ? source.nodes.flatMap((item) => {
      if (!item || typeof item !== 'object') return []
      const node = item as Record<string, unknown>
      const id = node.id == null ? '' : String(node.id)
      const title = typeof node.title === 'string' ? node.title : ''
      return id && title ? [{
        id,
        title,
        type: typeof node.type === 'string' ? node.type : undefined,
        role: typeof node.role === 'string' ? node.role : undefined,
        score: typeof node.score === 'number' ? node.score : undefined,
        path: node.path == null ? undefined : String(node.path),
      }] : []
    }) : []
    const edges = Array.isArray(source.edges) ? source.edges.flatMap((item) => {
      if (!item || typeof item !== 'object') return []
      const edge = item as Record<string, unknown>
      const sourceId = edge.source == null ? '' : String(edge.source)
      const target = edge.target == null ? '' : String(edge.target)
      return sourceId && target ? [{
        source: sourceId,
        target,
        weight: typeof edge.weight === 'number' ? edge.weight : undefined,
        signal: typeof edge.signal === 'string' ? edge.signal : undefined,
      }] : []
    }) : []
    return {query: typeof source.query === 'string' ? source.query : undefined, nodes, edges}
  }

  function upsertActivity(answer: YdChatMessage, patch: YdChatActivity | null) {
    if (!patch) return
    answer.activities ??= []
    const key = patch.messageId || answer.id
    const existing = answer.activities.find(item => (item.messageId || answer.id) === key && item.activityType === patch.activityType)
    if (existing) {
      Object.assign(existing, patch)
      if (patch.hits?.length) existing.hits = patch.hits
      if (patch.graph) existing.graph = patch.graph
      return
    }
    // A new structured activity advances the visible lifecycle. Close the previous
    // progress step so accepted/retrieve/generate do not keep spinning forever.
    const runningProgress = [...answer.activities].reverse().find(item => item.activityType === 'wiki-progress' && (!item.status || item.status === 'running'))
    if (runningProgress) runningProgress.status = 'completed'
    answer.activities.push({...patch, messageId: key})
  }

  function updateToolHint(activity: YdChatActivity) {
    toolHint.value = [activity.title, activity.content].filter(Boolean).join('：') || '正在处理…'
  }

  type JsonPatchOperation = { op: 'add' | 'remove' | 'replace', path: string, value?: unknown }

  function normalizePatch(content: unknown): JsonPatchOperation[] {
    const parsed = parseJsonContent(content)
    const source = parseStructuredContent(parsed)
    const raw = Array.isArray(parsed) ? parsed : (source?.patch ?? source?.operations)
    if (!Array.isArray(raw)) return []
    return raw.flatMap((item) => {
      if (!item || typeof item !== 'object') return []
      const operation = item as Record<string, unknown>
      const op = operation.op
      const path = operation.path
      return (op === 'add' || op === 'remove' || op === 'replace') && typeof path === 'string'
        ? [{op, path, value: operation.value}]
        : []
    })
  }

  function applyActivityPatch(answer: YdChatMessage, messageId: string | undefined, patches: JsonPatchOperation[], activityType?: string) {
    answer.activities ??= []
    const current = answer.activities.find(item => (item.messageId || answer.id) === (messageId || answer.id) && (!activityType || item.activityType === activityType))
      ?? {messageId: messageId || answer.id, activityType: activityType || 'wiki-progress'}
    if (!answer.activities.includes(current)) answer.activities.push(current)
    for (const patch of patches) applyObjectPatch(current as unknown as Record<string, unknown>, patch)
  }

  function applyObjectPatch(target: Record<string, unknown>, patch: JsonPatchOperation) {
    const parts = patch.path.split('/').slice(1).map(part => part.replace(/~1/g, '/').replace(/~0/g, '~'))
    if (!parts.length) return
    let parent: Record<string, unknown> | unknown[] = target
    for (const part of parts.slice(0, -1)) {
      const key = Array.isArray(parent) ? Number(part) : part
      const value = parent[key as never]
      if (!value || typeof value !== 'object') parent[key as never] = {} as never
      parent = parent[key as never] as Record<string, unknown> | unknown[]
    }
    const key = Array.isArray(parent) ? (patch.path.endsWith('/-') ? parent.length : Number(parts.at(-1))) : parts.at(-1)!
    if (patch.op === 'remove') {
      if (Array.isArray(parent)) parent.splice(Number(key), 1)
      else delete parent[key as string]
    } else if (Array.isArray(parent) && patch.op === 'add') parent.splice(Number(key), 0, patch.value)
    else parent[key as never] = patch.value as never
  }

  function normalizeCitations(raw: unknown[]): YdChatCitation[] {
    return raw
      .map((item) => {
        const source = item && typeof item === 'object' ? item as Record<string, unknown> : {}
        return {
          title: String(source.title ?? ''),
          path: source.path == null ? undefined : String(source.path),
          nodeId: source.nodeId == null ? undefined : String(source.nodeId),
          spaceSlug: source.spaceSlug == null ? undefined : String(source.spaceSlug),
          spaceName: source.spaceName == null ? undefined : String(source.spaceName),
          sourceUrl: source.sourceUrl == null ? undefined : String(source.sourceUrl),
          excerpt: source.excerpt == null ? undefined : String(source.excerpt),
          images: Array.isArray(source.images)
            ? (source.images as Record<string, unknown>[])
                .filter(image => image && typeof image === 'object' && image.url)
                .map(image => ({ url: String(image.url), caption: image.caption == null ? undefined : String(image.caption) }))
            : undefined,
        }
      })
      .filter(item => item.title)
  }

  function normalizeActions(raw: unknown[]): YdChatAction[] {
    return raw.flatMap((item) => {
      const source = item && typeof item === 'object' ? item as Record<string, unknown> : {}
      const label = String(source.label ?? '').trim()
      if (!label) {
        return []
      }
      return [{
        label,
        action: typeof source.action === 'string' ? source.action : '',
        value: String(source.value ?? ''),
      }]
    })
  }

  async function readErrorMessage(response: Response): Promise<string> {
    try {
      const text = await response.text()
      const parsed = JSON.parse(text) as { message?: string, msg?: string }
      return parsed.message || parsed.msg || `请求失败（${response.status}）`
    } catch {
      return `请求失败（${response.status}）`
    }
  }

  /** 停止当前生成 */
  function stop(): void {
    manuallyStopped = true
    abortController?.abort()
    socket?.close()
  }

  /** 清空对话（同时中止进行中的生成） */
  function clear(): void {
    stop()
    messages.value = []
    toolHint.value = ''
  }

  onScopeDispose(stop)

  return {
    messages,
    streaming,
    toolHint,
    send,
    stop,
    clear,
  }
}
