/* eslint-disable test/no-import-node-test -- 当前工作区使用 Node 原生测试，无 vitest 依赖。 */
import type { YdAgentMode, YdAgentSendRequest } from '@yudream/components'
import type { TestContext } from 'node:test'
import assert from 'node:assert/strict'
import test from 'node:test'
import { setTimeout as wait } from 'node:timers/promises'
import { effectScope } from 'vue'
import { useAgentWorkbenchDemo } from './useAgentWorkbenchDemo.ts'

function fixture(context: TestContext, stepMs = 1) {
  const scope = effectScope()
  const demo = scope.run(() => useAgentWorkbenchDemo({ stepMs, chunkMs: 1 }))!
  context.after(() => scope.stop())
  return demo
}

function request(demo: ReturnType<typeof useAgentWorkbenchDemo>, mode: YdAgentMode = 'code'): YdAgentSendRequest {
  demo.mode.value = mode
  return {
    text: '检查深色主题',
    attachments: [],
    sessionId: demo.sessionId.value,
    workspaceId: demo.workspaceId.value,
    model: demo.models[0],
    mode,
    skillCodes: demo.skills.value.filter(item => item.enabled).map(item => item.code),
    contextPaths: demo.files.value.filter(item => item.included).map(item => item.path),
  }
}

test('code waits for approval before publishing changes and records tools with references', async (context) => {
  const demo = fixture(context)
  await demo.send(request(demo))
  assert.equal(demo.current.value.harness.status, 'waiting')
  assert.deepEqual(demo.current.value.changes, [])
  const document = demo.current.value.renderDocuments[demo.current.value.messages.at(-1)!.id!]
  assert.ok(document.blocks.some(block => block.type === 'tool' && block.tool.status === 'awaiting-approval'))
  await demo.approvalDecision(demo.current.value.approvals[0].id, true)
  assert.equal(demo.current.value.harness.status, 'completed')
  assert.equal(demo.current.value.changes.length, 2)
  assert.ok(demo.current.value.messages.at(-1)!.content.includes('[[ref:theme]]'))
  assert.ok(demo.current.value.renderDocuments[demo.current.value.messages.at(-1)!.id!].blocks.some(block => block.type === 'reference'))
})

test('rejecting approval cancels queued tools and produces no changes', async (context) => {
  const demo = fixture(context)
  await demo.send(request(demo))
  await demo.approvalDecision(demo.current.value.approvals[0].id, false)
  await wait(8)
  assert.equal(demo.current.value.harness.status, 'cancelled')
  assert.equal(demo.current.value.changes.length, 0)
  assert.equal(demo.current.value.approvals.length, 0)
  const document = demo.current.value.renderDocuments[demo.current.value.messages.at(-1)!.id!]
  assert.ok(document.blocks.some(block => block.type === 'tool' && block.tool.status === 'cancelled'))
})

test('stopping during a timer prevents delayed activity and diff publication', async (context) => {
  const demo = fixture(context, 12)
  const run = demo.send(request(demo))
  demo.stop()
  await run
  const snapshot = JSON.stringify(demo.current.value)
  await wait(20)
  assert.equal(JSON.stringify(demo.current.value), snapshot)
  assert.equal(demo.current.value.harness.status, 'cancelled')
  assert.equal(demo.current.value.changes.length, 0)
})

test('switching sessions cancels the prior run and preserves separate histories', async (context) => {
  const demo = fixture(context, 12)
  const run = demo.send(request(demo))
  demo.sessionId.value = 'component-plan'
  const otherHistory = JSON.stringify(demo.current.value.messages)
  await run
  await wait(20)
  assert.equal(JSON.stringify(demo.current.value.messages), otherHistory)
  assert.equal(demo.current.value.messages.length, 1)
  demo.sessionId.value = 'theme-fix'
  assert.equal(demo.current.value.harness.status, 'cancelled')
  assert.equal(demo.current.value.messages.length, 2)
  assert.equal(demo.current.value.changes.length, 0)
})

test('read-only workspace refuses code while ask and plan keep changes empty', async (context) => {
  const demo = fixture(context)
  demo.workspaceId.value = 'docs-local'
  await demo.send(request(demo))
  assert.match(demo.current.value.messages.at(-1)!.content, /只读/)
  assert.equal(demo.current.value.activities.length, 0)
  assert.equal(demo.current.value.changes.length, 0)
  for (const mode of ['ask', 'plan'] as const) {
    await demo.send(request(demo, mode))
    assert.equal(demo.current.value.harness.status, 'completed')
    assert.equal(demo.current.value.changes.length, 0)
    assert.equal(demo.current.value.approvals.length, 0)
    assert.equal(demo.current.value.activities.some(item => item.kind === 'edit'), false)
  }
})

test('retry runs a fresh local demonstration and requires a new approval', async (context) => {
  const demo = fixture(context)
  demo.failCheck.value = true
  await demo.send(request(demo))
  await demo.approvalDecision(demo.current.value.approvals[0].id, true)
  assert.equal(demo.current.value.harness.status, 'error')
  const document = demo.current.value.renderDocuments[demo.current.value.messages.at(-1)!.id!]
  const tool = document.blocks.find(block => block.type === 'tool' && block.tool.retry)
  assert.ok(tool?.type === 'tool')
  await demo.toolRetry(tool.tool)
  assert.equal(demo.current.value.harness.status, 'waiting')
  assert.equal(demo.current.value.messages.length, 4)
  assert.equal(demo.current.value.changes.length, 0)
  await demo.approvalDecision(demo.current.value.approvals[0].id, true)
  assert.equal(demo.current.value.harness.status, 'completed')
})

test('create, rename, pin and remove update local session state including the final session', (context) => {
  const demo = fixture(context)
  demo.createSession()
  const created = demo.sessions.value.find(session => session.id === demo.sessionId.value)!
  demo.renameSession(created.id, '新的主题任务')
  demo.pinSession(created)
  assert.equal(created.title, '新的主题任务')
  assert.equal(created.pinned, true)
  demo.removeSession(created)
  assert.notEqual(demo.sessionId.value, created.id)
  assert.equal(demo.sessions.value.length, 2)
  demo.removeSession(demo.sessions.value[0])
  demo.removeSession(demo.sessions.value[0])
  assert.equal(demo.sessions.value.length, 1)
  assert.equal(demo.current.value.messages.length, 0)
})
