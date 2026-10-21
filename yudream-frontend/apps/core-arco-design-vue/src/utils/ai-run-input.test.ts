import type { YdChatMessage } from '@yudream/components'
import assert from 'node:assert/strict'
import { it } from 'node:test'
import { upsertAiRunInput } from './ai-run-input.ts'
import { projectAiRunActivities } from './ai-run-projection.ts'

it('persisted input and replay use the same message ID', () => {
  const messages: YdChatMessage[] = [{ id: '9007199254740993', role: 'user', content: '修改页面' }]
  upsertAiRunInput(messages, 'run-1', '修改页面', true, '9007199254740993')
  upsertAiRunInput(messages, 'run-1', '修改页面', true)
  assert.equal(messages.length, 1)
})

it('identical text in separate runs remains separate', () => {
  const messages: YdChatMessage[] = []
  upsertAiRunInput(messages, 'run-1', '继续', true, '1')
  upsertAiRunInput(messages, 'run-2', '继续', true, '2')
  upsertAiRunInput(messages, 'run-2', '继续', true, '2')
  assert.deepEqual(messages.map(message => message.id), ['1', '2'])
})

it('business inputs replace optimistic entries and precede their answer', () => {
  const messages: YdChatMessage[] = [{ id: 'local', role: 'user', content: '继续', context: { pendingRunInput: true } }, { id: 'run:1', role: 'assistant', content: '' }]
  upsertAiRunInput(messages, '1', '继续', false)
  upsertAiRunInput(messages, '1', '继续', false)
  assert.deepEqual(messages.map(message => message.id), ['input:1', 'run:1'])
})

it('persisted progress without graph arrays is safe for the message list', () => {
  const activities = projectAiRunActivities([{ activityType: 'harness', phase: 'waiting', graph: { query: '' } }], 'message')
  assert.equal(activities[0]?.graph, undefined)
  assert.equal(activities[0]?.phase, 'waiting')
})
