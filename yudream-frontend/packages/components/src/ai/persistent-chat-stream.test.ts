import type { YdChatToolCallReply } from './useYdChatStream'
import assert from 'node:assert/strict'
import test from 'node:test'
import { effectScope } from 'vue'
import { clearYdPersistentChatOwner, useYdPersistentChatStream } from './useYdPersistentChatStream'

class TestSocket {
  static OPEN = 1
  static CONNECTING = 0
  static sockets: TestSocket[] = []
  readyState = 0
  sent: Array<Record<string, unknown>> = []
  onopen?: () => void
  onmessage?: (event: { data: string }) => void
  onclose?: (event: { code: number, reason: string }) => void
  onerror?: () => void
  constructor(public url: string) {
    TestSocket.sockets.push(this)
  }

  open() {
    this.readyState = 1
    this.onopen?.()
  }

  send(data: string) {
    this.sent.push(JSON.parse(data))
  }

  frame(data: Record<string, unknown>) {
    this.onmessage?.({ data: JSON.stringify(data) })
  }

  close() {
    this.readyState = 3
    this.onclose?.({ code: 1000, reason: '' })
  }

  drop() {
    this.readyState = 3
    this.onclose?.({ code: 1006, reason: 'network lost' })
  }
}

test('persistent CMS stream survives view disposal and reconnects the same run without a new submit', async () => {
  const originalSocket = globalThis.WebSocket
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  const values = new Map<string, string>()
  globalThis.WebSocket = TestSocket as unknown as typeof WebSocket
  globalThis.window = { location: { origin: 'https://example.test' } } as Window & typeof globalThis
  globalThis.localStorage = { getItem: key => values.get(key) ?? null, setItem: (key, value) => {
    values.set(key, value)
  } } as Storage
  TestSocket.sockets = []
  let cancellations = 0
  const options = {
    ownerKey: 'owner-1',
    contextKey: 'page:42',
    endpoint: '/cms/ws',
    buildBody: (question: string) => ({ prompt: question, contextKey: 'page:42' }),
    persistence: {
      latest: async () => undefined,
      snapshot: async () => ({ id: 'run-1', cursor: '2', status: 'RUNNING', content: '真实' }),
      cancel: async () => {
        cancellations++
        return { id: 'run-1', cursor: '4', status: 'CANCELLED' }
      },
      toolResult: async () => {
      },
    },
  }
  const firstScope = effectScope()
  const first = firstScope.run(() => useYdPersistentChatStream(options))!
  const completion = first.send('改好当前画布')
  await new Promise(resolve => setTimeout(resolve, 0))
  const original = TestSocket.sockets[0]
  await assert.rejects(first.stop(), /任务仍在登记/)
  assert.equal(original.readyState, TestSocket.CONNECTING)
  assert.equal(cancellations, 0)
  original.open()
  original.frame({ type: 'RUN_STARTED', runId: 'run-1', cursor: '1' })
  original.frame({ type: 'TEXT_MESSAGE_CHUNK', cursor: '2', delta: '真实' })
  firstScope.stop()
  assert.equal(original.readyState, TestSocket.OPEN)
  assert.equal(cancellations, 0)
  const secondScope = effectScope()
  const second = secondScope.run(() => useYdPersistentChatStream(options))!
  assert.equal(second.messages.value.at(-1)?.content, '真实')
  original.drop()
  await new Promise(resolve => setTimeout(resolve, 1100))
  const resumed = TestSocket.sockets.at(-1)!
  resumed.open()
  assert.deepEqual(resumed.sent[0], { resumeRunId: 'run-1', after: '2' })
  resumed.frame({ type: 'TEXT_MESSAGE_CHUNK', cursor: '2', delta: '重复帧' })
  resumed.frame({ type: 'TEXT_MESSAGE_CHUNK', cursor: '3', delta: '增量' })
  resumed.frame({ type: 'RUN_FINISHED', cursor: '4', result: { content: '真实增量' } })
  await completion
  assert.equal(second.messages.value.at(-1)?.content, '真实增量')
  assert.equal(second.messages.value.filter(message => message.role === 'user').length, 1)
  secondScope.stop()
  clearYdPersistentChatOwner('owner-1')
  globalThis.WebSocket = originalSocket
  globalThis.window = originalWindow
  globalThis.localStorage = originalStorage
})

test('duplicate requests execute once per run and identical opaque tool IDs remain independent in the next run', async () => {
  const originalSocket = globalThis.WebSocket
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  globalThis.WebSocket = TestSocket as unknown as typeof WebSocket
  globalThis.window = { location: { origin: 'https://example.test' } } as Window & typeof globalThis
  globalThis.localStorage = { getItem: () => null, setItem: () => {
  } } as unknown as Storage
  TestSocket.sockets = []
  let executions = 0
  let acknowledgements = 0
  let resolveReply!: (reply: YdChatToolCallReply) => void
  const scope = effectScope()
  const stream = scope.run(() => useYdPersistentChatStream({
    ownerKey: 'owner-2',
    contextKey: 'page:43',
    endpoint: '/cms/ws',
    onToolCallRequest: () => {
      executions++
      return new Promise((resolve) => {
        resolveReply = resolve
      })
    },
    persistence: {
      latest: async () => undefined,
      snapshot: async (runId: string) => ({ id: runId, cursor: '1', status: 'WAITING', pendingFrontendTools: [{ toolCallId: 'native-tool-1', name: 'cms.canvas.get_outline', arguments: {} }] }),
      cancel: async () => ({ id: 'run-2', cursor: '5', status: 'CANCELLED' }),
      toolResult: async () => {
        acknowledgements++
      },
    },
  }))!
  const completion = stream.send('执行页面工具')
  await new Promise(resolve => setTimeout(resolve, 0))
  const socket = TestSocket.sockets[0]
  socket.open()
  socket.frame({ type: 'RUN_STARTED', runId: 'run-2', cursor: '1' })
  const request = { type: 'TOOL_CALL_REQUEST', toolCallId: 'native-tool-1', toolCallName: 'cms.canvas.get_outline', content: {} }
  socket.frame({ ...request, cursor: '2' })
  socket.frame({ ...request, cursor: '3' })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(executions, 1)
  resolveReply({ ok: true, result: { content: '真实画布纲要' } })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(acknowledgements, 1)
  socket.frame({ ...request, cursor: '4' })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(executions, 1)
  assert.equal(acknowledgements, 1)
  socket.frame({ type: 'RUN_FINISHED', cursor: '5', result: { content: '完成' } })
  await completion
  const nextCompletion = stream.send('下一轮再次读取画布')
  await new Promise(resolve => setTimeout(resolve, 0))
  const nextSocket = TestSocket.sockets.at(-1)!
  nextSocket.open()
  nextSocket.frame({ type: 'RUN_STARTED', runId: 'run-2-next', cursor: '1' })
  nextSocket.frame({ ...request, cursor: '2' })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(executions, 2)
  resolveReply({ ok: true, result: { content: '更新后的画布纲要' } })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(acknowledgements, 2)
  nextSocket.frame({ type: 'RUN_FINISHED', cursor: '3', result: { content: '下一轮完成' } })
  await nextCompletion
  scope.stop()
  clearYdPersistentChatOwner('owner-2')
  globalThis.WebSocket = originalSocket
  globalThis.window = originalWindow
  globalThis.localStorage = originalStorage
})

test('snapshot pending request and WebSocket replay share one execution and one acknowledgement', async () => {
  const originalSocket = globalThis.WebSocket
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  globalThis.WebSocket = TestSocket as unknown as typeof WebSocket
  globalThis.window = { location: { origin: 'https://example.test' } } as Window & typeof globalThis
  globalThis.localStorage = { getItem: () => null, setItem: () => {
  } } as unknown as Storage
  TestSocket.sockets = []
  let executions = 0
  let acknowledgements = 0
  let resolveReply!: (reply: YdChatToolCallReply) => void
  const snapshot = { id: 'run-3', cursor: '1', status: 'WAITING', inputText: '查看画布', pendingFrontendTools: [{ toolCallId: 'native-tool-3', name: 'cms.canvas.get_outline', arguments: {} }] }
  const scope = effectScope()
  const stream = scope.run(() => useYdPersistentChatStream({
    ownerKey: 'owner-3',
    contextKey: 'page:44',
    endpoint: '/cms/ws',
    onToolCallRequest: () => {
      executions++
      return new Promise((resolve) => {
        resolveReply = resolve
      })
    },
    persistence: {
      latest: async () => snapshot,
      snapshot: async () => snapshot,
      cancel: async () => ({ ...snapshot, status: 'CANCELLED' }),
      toolResult: async () => {
        acknowledgements++
        await new Promise(resolve => setTimeout(resolve, 0))
      },
    },
  }))!
  await new Promise(resolve => setTimeout(resolve, 0))
  const socket = TestSocket.sockets[0]
  socket.open()
  assert.deepEqual(socket.sent[0], { resumeRunId: 'run-3', after: '1' })
  socket.frame({ type: 'TOOL_CALL_REQUEST', toolCallId: 'native-tool-3', toolCallName: 'cms.canvas.get_outline', content: {}, cursor: '2' })
  assert.equal(executions, 1)
  resolveReply({ ok: true, result: { content: '真实画布结构' } })
  await new Promise(resolve => setTimeout(resolve, 10))
  assert.equal(acknowledgements, 1)
  assert.equal(stream.messages.value[0].content, '查看画布')
  socket.frame({ type: 'ACTIVITY_SNAPSHOT', activityType: 'ai.generation', cursor: '3', content: { callId: 'aux-1', action: 'delta', content: '真实辅助输出' } })
  socket.frame({ type: 'ACTIVITY_SNAPSHOT', activityType: 'ai.generation', cursor: '4', content: { callId: 'aux-1', action: 'reasoning', content: '真实辅助推理' } })
  assert.equal(stream.messages.value.at(-1)?.content, '')
  assert.equal(stream.messages.value.at(-1)?.activities?.find(activity => activity.activityType === 'ai.generation')?.content, '真实辅助输出')
  assert.equal(stream.messages.value.at(-1)?.activities?.find(activity => activity.activityType === 'ai.generation.reasoning')?.content, '真实辅助推理')
  socket.frame({ type: 'RUN_FINISHED', cursor: '5', result: { content: '完成' } })
  scope.stop()
  clearYdPersistentChatOwner('owner-3')
  globalThis.WebSocket = originalSocket
  globalThis.window = originalWindow
  globalThis.localStorage = originalStorage
})

test('persisted canonical frontend result resumes without repeating its Canvas operation', async () => {
  const originalSocket = globalThis.WebSocket
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  globalThis.WebSocket = TestSocket as unknown as typeof WebSocket
  globalThis.window = { location: { origin: 'https://example.test' } } as Window & typeof globalThis
  globalThis.localStorage = { getItem: () => null, setItem: () => {
} } as unknown as Storage
  TestSocket.sockets = []
  let executions = 0
  let continuations = 0
  const snapshot = { id: 'run-4', cursor: '2', status: 'WAITING', pendingFrontendTools: [{ toolCallId: 'native-tool-4', name: 'cms.canvas.apply_patch', arguments: { op: 'insert' }, responseRecorded: true }] }
  const scope = effectScope()
  const stream = scope.run(() => useYdPersistentChatStream({
    ownerKey: 'owner-4', contextKey: 'page:45', endpoint: '/cms/ws',
    onToolCallRequest: async () => {
 executions++;
return { ok: true, result: { updated: true } }
},
    persistence: {
      latest: async () => snapshot,
      snapshot: async () => snapshot,
      cancel: async () => ({ ...snapshot, status: 'CANCELLED' }),
      toolResult: async (_id, _request, reply) => {
 assert.equal(reply.ok && reply.result?.responseRecorded, true);
continuations++
},
    },
  }))!
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(executions, 0)
  assert.equal(continuations, 1)
  const socket = TestSocket.sockets[0]
  socket.open()
  socket.frame({ type: 'TOOL_CALL_REQUEST', toolCallId: 'native-tool-4', toolCallName: 'cms.canvas.apply_patch', content: { op: 'insert' }, cursor: '3' })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(executions, 0)
  assert.equal(continuations, 1)
  socket.frame({ type: 'RUN_ERROR', code: 'CANCELLED', action: 'cancelled', message: '运行已取消', cursor: '4' })
  assert.equal(stream.runStatus.value, 'CANCELLED')
  assert.equal(stream.messages.value.at(-1)?.error, false)
  scope.stop()
  clearYdPersistentChatOwner('owner-4')
  globalThis.WebSocket = originalSocket
  globalThis.window = originalWindow
  globalThis.localStorage = originalStorage
})

test('manual canonical HTTP acknowledgement settles the waiting request and suppresses replay execution', async () => {
  const originalSocket = globalThis.WebSocket
  const originalWindow = globalThis.window
  const originalStorage = globalThis.localStorage
  globalThis.WebSocket = TestSocket as unknown as typeof WebSocket
  globalThis.window = { location: { origin: 'https://example.test' } } as Window & typeof globalThis
  globalThis.localStorage = { getItem: () => null, setItem: () => {
} } as unknown as Storage
  TestSocket.sockets = []
  let executions = 0
  let duplicateAcknowledgements = 0
  const snapshot = { id: 'run-manual', cursor: '1', status: 'WAITING', pendingFrontendTools: [{ toolCallId: 'native-ask', name: 'cms.ask.user', arguments: {} }] }
  const scope = effectScope()
  const stream = scope.run(() => useYdPersistentChatStream({
    ownerKey: 'owner-manual', contextKey: 'page:manual', endpoint: '/cms/ws',
    onToolCallRequest: () => {
      executions++
      return new Promise<YdChatToolCallReply>(() => {
})
    },
    persistence: {
      latest: async () => snapshot,
      snapshot: async () => snapshot,
      cancel: async () => ({ ...snapshot, status: 'CANCELLED' }),
      toolResult: async () => {
 duplicateAcknowledgements++
},
    },
  }))!
  await new Promise(resolve => setTimeout(resolve, 0))
  const canonical: YdChatToolCallReply = { ok: true, result: { selectedOption: '保留现有布局' } }
  const reply = await Promise.race([
    stream.acknowledgeTool('native-ask', canonical),
    new Promise<never>((_resolve, reject) => setTimeout(() => reject(new Error('手动回执未结束待处理请求')), 200)),
  ])
  assert.deepEqual(reply, canonical)
  const socket = TestSocket.sockets[0]
  socket.open()
  socket.frame({ type: 'TOOL_CALL_REQUEST', toolCallId: 'native-ask', toolCallName: 'cms.ask.user', content: {}, cursor: '2' })
  await new Promise(resolve => setTimeout(resolve, 0))
  assert.equal(executions, 1)
  assert.equal(duplicateAcknowledgements, 0)
  socket.frame({ type: 'RUN_FINISHED', cursor: '3', result: { content: '按真实选择完成' } })
  scope.stop()
  clearYdPersistentChatOwner('owner-manual')
  globalThis.WebSocket = originalSocket
  globalThis.window = originalWindow
  globalThis.localStorage = originalStorage
})
