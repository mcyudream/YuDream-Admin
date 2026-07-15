import assert from 'node:assert/strict'
import test from 'node:test'
import { isNearChatBottom, scrollChatToBottom } from './cms-chat-auto-scroll'

test('keeps following when the chat viewport is near the bottom', () => {
  assert.equal(isNearChatBottom({ scrollTop: 420, clientHeight: 500, scrollHeight: 960 }), true)
  assert.equal(isNearChatBottom({ scrollTop: 300, clientHeight: 500, scrollHeight: 960 }), false)
})

test('scrolls the chat viewport to its latest content', () => {
  const viewport = { scrollTop: 120, clientHeight: 500, scrollHeight: 960 }

  scrollChatToBottom(viewport)

  assert.equal(viewport.scrollTop, 960)
})
