import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test -- this workspace runs lightweight TS tests with Node.
import { test } from 'node:test'
import { createAgentDebugStreamParser, decodeAgentDebugEventBlock } from './agent-debug-stream'

test('decodes a named SSE event data block', () => {
  const value = decodeAgentDebugEventBlock('event: NODE_RUNNING\ndata: {"type":"NODE_RUNNING","nodeId":"llm-1","status":"RUNNING"}')
  assert.deepEqual(value, { type: 'NODE_RUNNING', nodeId: 'llm-1', status: 'RUNNING' })
})

test('reassembles events split across network chunks', () => {
  const events: Array<Record<string, unknown>> = []
  const parser = createAgentDebugStreamParser(event => events.push(event))

  parser.push('event: TEXT_MESSAGE_CHUNK\ndata: {"type":"TEXT_MESSAGE_')
  parser.push('CHUNK","delta":"你好"}\n\n')
  parser.push('data: {"type":"RUN_FINISHED","status":"COMPLETED"}\n\n')
  parser.finish()

  assert.deepEqual(events, [
    { type: 'TEXT_MESSAGE_CHUNK', delta: '你好' },
    { type: 'RUN_FINISHED', status: 'COMPLETED' },
  ])
})
