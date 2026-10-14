import type { MaybeRefOrGetter } from 'vue'
import type { UseYdChatStreamOptions, YdChatMessage, YdChatRunSnapshot, YdChatToolCallReply, YdChatToolCallRequest } from './useYdChatStream'
import { computed, onScopeDispose, ref, toValue, watch } from 'vue'
import { createYdChatStream } from './useYdChatStream'

export interface YdPersistentChatPort {
  latest: (contextKey: string) => Promise<YdChatRunSnapshot | undefined>
  snapshot: (runId: string) => Promise<YdChatRunSnapshot>
  cancel: (runId: string) => Promise<YdChatRunSnapshot>
  toolResult: (runId: string, request: YdChatToolCallRequest, reply: YdChatToolCallReply) => Promise<void>
  track?: (runId: string) => void
}

export interface UseYdPersistentChatStreamOptions extends UseYdChatStreamOptions {
  ownerKey: MaybeRefOrGetter<string>
  contextKey: MaybeRefOrGetter<string>
  toolsReady?: MaybeRefOrGetter<boolean>
  persistence: YdPersistentChatPort
}

interface Binding { id: symbol, options: UseYdPersistentChatStreamOptions }
interface PendingTool {
  runId: string
  request: YdChatToolCallRequest
  resolve: (reply: YdChatToolCallReply) => void
  executing?: symbol
  promise: Promise<YdChatToolCallReply>
}
interface PersistentEntry {
  owner: string
  context: string
  runtime: ReturnType<typeof createYdChatStream>
  bindings: Map<symbol, Binding>
  pending: Map<string, PendingTool>
  acknowledged: Map<string, YdChatToolCallReply>
  resolvedReplies: Map<string, YdChatToolCallReply>
  repliesInFlight: Map<string, Promise<void>>
  tracked?: string
}
const entries = new Map<string, PersistentEntry>()
const keyOf = (owner: string, context: string) => JSON.stringify([owner, context])
const toolKey = (runId: string, toolCallId: string) => JSON.stringify([runId, toolCallId])
const metadataKey = (owner: string, context: string) => `yd.ai.run.${encodeURIComponent(owner)}.${encodeURIComponent(context)}`

function activeBinding(entry: PersistentEntry): Binding | undefined {
  return [...entry.bindings.values()].reverse().find(binding => toValue(binding.options.toolsReady ?? true))
}

function drain(entry: PersistentEntry) {
  const binding = activeBinding(entry)
  if (!binding?.options.onToolCallRequest) {
    return
  }
  for (const [id, pending] of entry.pending) {
    if (pending.executing || pending.runId !== entry.runtime.runId.value) {
      continue
    }
    pending.executing = binding.id
    void binding.options.onToolCallRequest(pending.request).then((reply) => {
      if (pending.request.toolName === 'cms.ask.user' && !entry.bindings.has(binding.id)) {
        return
      }
      if (entry.pending.get(id) !== pending || pending.executing !== binding.id) {
        return
      }
      entry.pending.delete(id)
      entry.resolvedReplies.set(id, reply)
      pending.resolve(reply)
    }).catch((error) => {
      if (entry.pending.get(id) !== pending || pending.executing !== binding.id) {
        return
      }
      entry.pending.delete(id)
      const reply: YdChatToolCallReply = { ok: false, error: error instanceof Error ? error.message : '客户端工具执行失败' }
      entry.resolvedReplies.set(id, reply)
      pending.resolve(reply)
    })
  }
}

function requestTool(entry: PersistentEntry, runId: string, request: YdChatToolCallRequest): Promise<YdChatToolCallReply> {
  const key = toolKey(runId, request.toolCallId)
  const existing = entry.pending.get(key)
  if (existing) {
    return existing.promise
  }
  const acknowledged = entry.acknowledged.get(key)
  if (acknowledged) {
    return Promise.resolve(acknowledged)
  }
  const resolved = entry.resolvedReplies.get(key)
  if (resolved) {
    return Promise.resolve(resolved)
  }
  let resolve!: (reply: YdChatToolCallReply) => void
  const promise = new Promise<YdChatToolCallReply>((resolvePromise) => {
    resolve = resolvePromise
  })
  entry.pending.set(key, { runId, request, resolve, promise })
  drain(entry)
  return promise
}

function submitReply(entry: PersistentEntry, options: UseYdPersistentChatStreamOptions, runId: string, request: YdChatToolCallRequest, reply: YdChatToolCallReply): Promise<void> {
  const key = toolKey(runId, request.toolCallId)
  if (entry.acknowledged.has(key)) {
    return Promise.resolve()
  }
  const existing = entry.repliesInFlight.get(key)
  if (existing) {
    return existing
  }
  const pending = options.persistence.toolResult(runId, request, reply).then(() => {
    entry.acknowledged.set(key, reply)
  }).finally(() => entry.repliesInFlight.delete(key))
  entry.repliesInFlight.set(key, pending)
  return pending
}

function getEntry(options: UseYdPersistentChatStreamOptions): PersistentEntry {
  const owner = toValue(options.ownerKey)
  const context = toValue(options.contextKey)
  if (!owner || !context) {
    throw new Error('持久 AI 会话必须提供登录用户与稳定上下文标识')
  }
  const key = keyOf(owner, context)
  const cached = entries.get(key)
  if (cached) {
    return cached
  }
  const entry = { owner, context, bindings: new Map(), pending: new Map(), acknowledged: new Map(), resolvedReplies: new Map(), repliesInFlight: new Map() } as PersistentEntry
  const current = () => [...entry.bindings.values()].at(-1)?.options
  entry.runtime = createYdChatStream({
    endpoint: () => {
      const endpoint = current()?.endpoint ?? options.endpoint
      return typeof endpoint === 'function' ? endpoint() : endpoint
    },
    protocol: 'agui',
    transport: 'websocket',
    durable: true,
    buildBody: (question, history, attachments) => current()?.buildBody?.(question, history, attachments) ?? { question, attachments },
    getWebSocketToken: () => (current() ?? options).getWebSocketToken?.(),
    onTool: tool => current()?.onTool?.(tool),
    onDone: message => current()?.onDone?.(message),
    onError: (message, error) => {
      current()?.onError?.(message, error)
      const runId = entry.runtime.runId.value
      if (runId) {
        void options.persistence.snapshot(runId).then((snapshot) => {
          if (!['RUNNING', 'WAITING'].includes(snapshot.status)) {
            void entry.runtime.resumeRun(snapshot)
          }
        }).catch(() => {
        })
      }
    },
    onToolCallRequest: async (request) => {
      const runId = entry.runtime.runId.value
      const key = toolKey(runId, request.toolCallId)
      if (entry.pending.has(key) || entry.resolvedReplies.has(key) || entry.acknowledged.has(key)) {
        return requestTool(entry, runId, request)
      }
      const snapshot = await options.persistence.snapshot(runId)
      if (runId !== entry.runtime.runId.value) {
        throw new Error('会话已切换，已忽略旧工具请求')
      }
      const pending = snapshot.pendingFrontendTools?.find(item => item.toolCallId === request.toolCallId)
      if (!pending) {
        throw new Error('该工具请求已结束，已忽略旧事件回放')
      }
      if (pending.responseRecorded) {
        const reply: YdChatToolCallReply = { ok: true, result: { responseRecorded: true } }
        entry.resolvedReplies.set(key, reply)
        return reply
      }
      return requestTool(entry, runId, request)
    },
    submitToolResult: (runId, request, reply) => submitReply(entry, options, runId, request, reply),
    onRunUpdate: (runId, cursor) => {
      localStorage.setItem(metadataKey(owner, context), JSON.stringify({ runId, cursor }))
      if (entry.tracked !== runId) {
        entry.tracked = runId
        options.persistence.track?.(runId)
      }
    },
  })
  entries.set(key, entry)
  return entry
}

/** Disconnect only this user's browser subscriptions; backend runs and checkpoints remain intact. */
export function clearYdPersistentChatOwner(owner: string) {
  for (const [key, entry] of entries) {
    if (entry.owner === owner) {
      entry.runtime.stop()
      entry.bindings.clear()
      entries.delete(key)
    }
  }
}

export function useYdPersistentChatStream(options: UseYdPersistentChatStreamOptions) {
  const id = Symbol('persistent-chat-view')
  const emptyMessages = ref<YdChatMessage[]>([])
  const current = computed(() => toValue(options.ownerKey) && toValue(options.contextKey) ? getEntry(options) : undefined)
  const requireCurrent = () => {
    const entry = current.value
    if (!entry) {
      throw new Error('请先登录后使用 AI 会话')
    }
    return entry
  }
  let bound: PersistentEntry | undefined
  function detach() {
    if (!bound) {
      return
    }
    bound.bindings.delete(id)
    for (const pending of bound.pending.values()) {
      if (pending.executing === id && pending.request.toolName === 'cms.ask.user') {
        pending.executing = undefined
      }
    }
    bound = undefined
  }
  async function restore() {
    const entry = current.value
    if (!entry) {
      return
    }
    let snapshot: YdChatRunSnapshot | undefined
    try {
      const stored = JSON.parse(localStorage.getItem(metadataKey(entry.owner, entry.context)) || 'null') as { runId?: string } | null
      snapshot = await options.persistence.latest(entry.context)
      if (!snapshot && stored?.runId) {
        snapshot = await options.persistence.snapshot(stored.runId)
      }
    }
    catch {
      snapshot = await options.persistence.latest(entry.context)
    }
    if (entry !== current.value || !snapshot) {
      return
    }
    void entry.runtime.resumeRun(snapshot)
    for (const request of snapshot.pendingFrontendTools || []) {
      const key = toolKey(snapshot.id, request.toolCallId)
      if (entry.pending.has(key) || entry.acknowledged.has(key)) {
        continue
      }
      const normalized = { toolCallId: request.toolCallId, toolName: request.name, args: request.arguments }
      if (request.responseRecorded) {
        entry.resolvedReplies.set(key, { ok: true, result: { responseRecorded: true } })
      }
      void requestTool(entry, snapshot.id, normalized).then(reply => submitReply(entry, options, snapshot!.id, normalized, reply)).catch(error => options.onError?.(entry.runtime.messages.value.at(-1) ?? { role: 'assistant', content: '' }, error))
    }
    drain(entry)
  }
  watch(current, (entry) => {
    detach()
    if (!entry) {
      return
    }
    bound = entry
    entry.bindings.set(id, { id, options })
    drain(entry)
    void restore().catch(error => options.onError?.(entry.runtime.messages.value.at(-1) ?? { role: 'assistant', content: '' }, error))
  }, { immediate: true })
  watch(() => toValue(options.toolsReady ?? true), () => {
    if (bound) {
      drain(bound)
    }
  })
  onScopeDispose(detach)
  return {
    messages: computed({ get: () => current.value?.runtime.messages.value ?? emptyMessages.value, set: (value) => {
      requireCurrent().runtime.messages.value = value
    } }),
    streaming: computed(() => current.value?.runtime.streaming.value ?? false),
    toolHint: computed(() => current.value?.runtime.toolHint.value ?? ''),
    runId: computed(() => current.value?.runtime.runId.value ?? ''),
    cursor: computed(() => current.value?.runtime.cursor.value ?? '0'),
    runStatus: computed(() => current.value?.runtime.runStatus.value ?? 'IDLE'),
    connectionState: computed(() => current.value?.runtime.connectionState.value ?? 'disconnected'),
    send: (text: string, attachments?: Parameters<ReturnType<typeof createYdChatStream>['send']>[1]) => requireCurrent().runtime.send(text, attachments),
    stop: async () => {
      const entry = requireCurrent()
      if (!entry.runtime.streaming.value) {
        return
      }
      if (!entry.runtime.runId.value) {
        throw new Error('任务仍在登记，请稍后再次停止')
      }
      if (entry.runtime.runId.value && ['RUNNING', 'WAITING'].includes(entry.runtime.runStatus.value)) {
        await options.persistence.cancel(entry.runtime.runId.value)
      }
      entry.runtime.stop()
    },
    clear: async () => {
      const entry = requireCurrent()
      if (entry.runtime.runId.value && ['RUNNING', 'WAITING'].includes(entry.runtime.runStatus.value)) {
        await options.persistence.cancel(entry.runtime.runId.value)
      }
      entry.runtime.clear()
    },
    restore,
    acknowledgeTool: (toolCallId: string, reply: YdChatToolCallReply) => {
      const entry = requireCurrent()
      const key = toolKey(entry.runtime.runId.value, toolCallId)
      const pending = entry.pending.get(key)
      entry.acknowledged.set(key, reply)
      entry.resolvedReplies.set(key, reply)
      pending?.resolve(reply)
      entry.pending.delete(key)
      return pending?.promise ?? Promise.resolve(reply)
    },
  }
}
