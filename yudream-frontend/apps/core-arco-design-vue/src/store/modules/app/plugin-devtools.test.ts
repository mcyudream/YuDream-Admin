import assert from 'node:assert/strict'
import test from 'node:test'
import { shouldStopSseReconnect } from './plugin-devtools-sse.ts'

test('stops SSE reconnect on auth and client errors', () => {
  assert.equal(shouldStopSseReconnect(400), true)
  assert.equal(shouldStopSseReconnect(401), true)
  assert.equal(shouldStopSseReconnect(403), true)
  assert.equal(shouldStopSseReconnect(404), true)
  assert.equal(shouldStopSseReconnect(405), true)
})

test('retries transient server and network failures', () => {
  assert.equal(shouldStopSseReconnect(undefined), false)
  assert.equal(shouldStopSseReconnect(0), false)
  assert.equal(shouldStopSseReconnect(500), false)
  assert.equal(shouldStopSseReconnect(502), false)
})
