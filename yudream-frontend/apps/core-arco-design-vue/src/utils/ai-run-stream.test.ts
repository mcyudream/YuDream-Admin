import assert from 'node:assert/strict'
import test from 'node:test'
import { AiRunStreamError, canReconnectAiRun, isActiveAiRun, isNewAiCursor, parseAiRunEnvelope, parseAiSseFrames } from './ai-run-stream.ts'

test('durable stream keeps partial CRLF frames and ignores heartbeat comments', () => {
  const first = parseAiSseFrames(': heartbeat\r\n\r\nid: 8\r\nevent: ai.message\r\ndata: {"runId":"run-1",')
  assert.equal(first.frames.length, 0)
  const next = parseAiSseFrames(`${first.rest}"payload":{"content":"你好"}}\r\n\r\n`)
  assert.deepEqual(parseAiRunEnvelope(next.frames[0], 'run-1'), {
    event: 'ai.message', action: undefined, module: undefined, traceId: undefined, timestamp: undefined,
    cursor: '8', runId: 'run-1', payload: { content: '你好' },
  })
  assert.equal(next.rest, '')
})

test('SSE joins multiline data and preserves IDs without numeric conversion', () => {
  const parsed = parseAiSseFrames('id: 9007199254740993\ndata: {\ndata: "event":"ai.progress","payload":{}\ndata: }\n\n')
  assert.equal(parseAiRunEnvelope(parsed.frames[0], 'run-1')?.cursor, '9007199254740993')
  assert.equal(isNewAiCursor('9007199254740993', '9007199254740992'), true)
  assert.equal(isNewAiCursor('9007199254740992', '9007199254740993'), false)
  assert.equal(isNewAiCursor('8', '8'), false)
})

test('stream rejects events from another run and malformed envelope cursors', () => {
  assert.equal(parseAiRunEnvelope({ event: 'ai.message', id: '1', data: '{"runId":"other","payload":{}}' }, 'mine'), undefined)
  assert.equal(parseAiRunEnvelope({ event: 'ai.message', id: 'abc', data: '{"payload":{}}' }, 'mine'), undefined)
  assert.equal(parseAiRunEnvelope({ event: 'ai.message', id: '1', data: '{"payload":[]}' }, 'mine'), undefined)
  assert.equal(parseAiRunEnvelope({ event: 'ai.message', data: '{"cursor":9007199254740993,"payload":{}}' }, 'mine'), undefined)
})

test('subscription EOF remains a running state; only backend terminal states finish it', () => {
  assert.equal(isActiveAiRun('RUNNING'), true)
  assert.equal(isActiveAiRun('WAITING_FOR_INPUT'), true)
  assert.equal(isActiveAiRun('COMPLETED'), false)
  assert.equal(canReconnectAiRun(new AiRunStreamError('no access', 403)), false)
  assert.equal(canReconnectAiRun(new AiRunStreamError('upstream unavailable', 502)), true)
  assert.equal(canReconnectAiRun(new TypeError('Failed to fetch')), true)
})
