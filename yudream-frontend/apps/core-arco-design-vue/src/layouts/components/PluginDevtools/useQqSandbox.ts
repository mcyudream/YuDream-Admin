import type {
  QqSandboxCreateSessionPayload,
  QqSandboxEvent,
  QqSandboxPreset,
  QqSandboxSendMessagePayload,
  QqSandboxSession,
} from '@/api/modules/platform-devtools-qq-sandbox'
import apiQqSandbox from '@/api/modules/platform-devtools-qq-sandbox'

const EVENT_LIMIT = 300

function parseEventBlock(block: string): QqSandboxEvent | null {
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
    return null
  }
  try {
    const parsed = JSON.parse(dataLines.join('\n'))
    if (parsed && typeof parsed === 'object' && 'payload' in parsed) {
      return { event: eventName, ...parsed }
    }
    return { event: eventName, payload: parsed }
  }
  catch {
    return { event: eventName, payload: dataLines.join('\n') }
  }
}

export function useQqSandbox() {
  const presets = ref<QqSandboxPreset[]>([])
  const presetsLoading = ref(false)
  const session = ref<QqSandboxSession | null>(null)
  const events = ref<QqSandboxEvent[]>([])
  const selectedEvent = ref<QqSandboxEvent | null>(null)
  const creating = ref(false)
  const sending = ref(false)
  const connected = ref(false)
  const streamError = ref('')

  let streamAbort: AbortController | null = null

  async function loadPresets() {
    presetsLoading.value = true
    try {
      const res = await apiQqSandbox.presets()
      presets.value = res.data || []
    }
    finally {
      presetsLoading.value = false
    }
  }

  async function createSession(payload: QqSandboxCreateSessionPayload) {
    await closeSession()
    creating.value = true
    events.value = []
    selectedEvent.value = null
    streamError.value = ''
    try {
      const res = await apiQqSandbox.createSession(payload)
      session.value = res.data
      connectStream(res.data.sessionId)
      return res.data
    }
    finally {
      creating.value = false
    }
  }

  async function sendMessage(payload: QqSandboxSendMessagePayload) {
    if (!session.value || !payload.content.trim()) {
      return
    }
    sending.value = true
    try {
      const res = await apiQqSandbox.sendMessage(session.value.sessionId, {
        ...payload,
        content: payload.content.trim(),
      })
      if (res.data) {
        updateSessionStatus(res.data.metadata?.status)
        appendEvent({ event: 'message.accepted', timestamp: res.data.occurredAt, payload: res.data })
      }
    }
    finally {
      sending.value = false
    }
  }

  async function closeSession() {
    streamAbort?.abort()
    streamAbort = null
    connected.value = false
    const active = session.value
    session.value = null
    if (active?.sessionId) {
      await apiQqSandbox.deleteSession(active.sessionId)
    }
  }

  function resetTimeline() {
    events.value = []
    selectedEvent.value = null
  }

  function updateSessionStatus(status: unknown) {
    if (session.value && typeof status === 'string' && status) {
      session.value.status = status
    }
  }

  function appendEvent(event: QqSandboxEvent) {
    const payload = event.payload as Record<string, unknown> | null
    if (payload && typeof payload === 'object') {
      updateSessionStatus(payload.status)
    }
    events.value.push(event)
    if (events.value.length > EVENT_LIMIT) {
      events.value.splice(0, events.value.length - EVENT_LIMIT)
    }
  }

  function connectStream(sessionId: string) {
    streamAbort?.abort()
    streamAbort = new AbortController()
    void consumeStream(apiQqSandbox.streamUrl(sessionId), streamAbort.signal)
  }

  async function consumeStream(url: string, signal: AbortSignal) {
    const headers: Record<string, string> = { 'Accept': 'text/event-stream', 'Accept-Language': 'zh-CN' }
    const token = localStorage.getItem('token')
    if (token) {
      headers.Authorization = token
    }
    try {
      const response = await fetch(url, { headers, signal })
      if (!response.ok || !response.body) {
        throw new Error(`QQ 沙盒事件流连接失败（HTTP ${response.status}）`)
      }
      connected.value = true
      streamError.value = ''
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const chunk = await reader.read()
        if (chunk.done) {
          break
        }
        buffer += decoder.decode(chunk.value, { stream: true }).replaceAll('\r\n', '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const event = parseEventBlock(buffer.slice(0, boundary))
          if (event) {
            appendEvent(event)
          }
          buffer = buffer.slice(boundary + 2)
          boundary = buffer.indexOf('\n\n')
        }
      }
      const finalEvent = buffer.trim() ? parseEventBlock(buffer) : null
      if (finalEvent) {
        appendEvent(finalEvent)
      }
    }
    catch (error: any) {
      if (!signal.aborted && error?.name !== 'AbortError') {
        streamError.value = error?.message || 'QQ 沙盒事件流已断开'
      }
    }
    finally {
      if (!signal.aborted) {
        connected.value = false
      }
    }
  }

  onBeforeUnmount(() => {
    const sessionId = session.value?.sessionId
    streamAbort?.abort()
    streamAbort = null
    connected.value = false
    session.value = null
    if (sessionId) {
      void apiQqSandbox.deleteSession(sessionId).catch(() => undefined)
    }
  })

  return {
    presets,
    presetsLoading,
    session,
    events,
    selectedEvent,
    creating,
    sending,
    connected,
    streamError,
    loadPresets,
    createSession,
    sendMessage,
    closeSession,
    resetTimeline,
  }
}
