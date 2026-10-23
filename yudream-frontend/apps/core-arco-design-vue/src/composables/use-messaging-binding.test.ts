import type { MessagingBindingCode, MessagingBindingTarget } from '../api/modules/profile'
import assert from 'node:assert/strict'
// 根包无 vitest 依赖，本地测试统一用 node:test 运行
// eslint-disable-next-line test/no-import-node-test
import { test } from 'node:test'
import { effectScope } from 'vue'
import { bindingCodeExpired, bindingProtocolLabel, bindingTargetLabel, useMessagingBinding } from './use-messaging-binding'

const official: MessagingBindingTarget = { connectionId: '90071992547409931', name: '官方连接', botName: '官方机器人', protocol: 'official', bound: false }
const milky: MessagingBindingTarget = { connectionId: '90071992547409932', name: 'Milky 连接', protocol: 'milky', bound: true }
const code: MessagingBindingCode = { code: 'TEST123', expiresAt: '2030-01-01T00:00:00Z' }

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((done) => {
    resolve = done
  })
  return { promise, resolve }
}

function fixture(overrides: Partial<Parameters<typeof useMessagingBinding>[0]> = {}) {
  const scope = effectScope()
  const model = scope.run(() => useMessagingBinding({
    listMessagingBindingTargets: async () => ({ data: [{ ...official }, { ...milky }] }),
    issueMessagingBindingCode: async () => ({ data: { ...code } }),
    ...overrides,
  }))!
  return { model, stop: () => scope.stop() }
}

test('loads real statuses and distinguishes an empty list from a failed refresh', async () => {
  let fail = false
  let rows = [official, milky]
  const { model, stop } = fixture({ listMessagingBindingTargets: async () => {
    if (fail) {
      throw new Error('暂时不可用')
    }
    return { data: rows }
  } })
  try {
    await model.load()
    assert.equal(model.boundCount.value, 1)
    fail = true
    await model.load()
    assert.equal(model.targets.value.length, 2)
    assert.equal(model.loadError.value, '暂时不可用')
    fail = false
    rows = []
    await model.load()
    assert.equal(model.targets.value.length, 0)
    assert.equal(model.loadError.value, '')
  }
  finally { stop() }
})

test('sends a string connection ID and never marks issuance as a completed binding', async () => {
  const calls: string[] = []
  const { model, stop } = fixture({ issueMessagingBindingCode: async (id) => {
    calls.push(id)
    return { data: code }
  } })
  try {
    await model.load()
    await model.issue(official)
    assert.deepEqual(calls, [official.connectionId])
    assert.equal(model.bindingCode.value?.connectionId, official.connectionId)
    assert.equal(model.bindingCode.value?.botName, official.botName)
    assert.equal(model.targets.value[0].bound, false)
    assert.equal(model.boundCount.value, 1)
  }
  finally { stop() }
})

test('serializes generation across connections and drops the previous code', async () => {
  const pending = deferred<{ data: MessagingBindingCode }>()
  let calls = 0
  const { model, stop } = fixture({ issueMessagingBindingCode: async () => ++calls === 1 ? { data: code } : pending.promise })
  try {
    await model.load()
    await model.issue(official)
    const generating = model.issue(milky)
    assert.equal(model.bindingCode.value === null, true)
    assert.equal(model.issuingConnectionId.value, milky.connectionId)
    await model.issue(official)
    assert.equal(calls, 2)
    pending.resolve({ data: { ...code, code: 'LATEST' } })
    await generating
    assert.equal(model.bindingCode.value?.connectionId, milky.connectionId)
    assert.equal(model.bindingCode.value?.code, 'LATEST')
    assert.equal(model.issuingConnectionId.value, '')
  }
  finally { stop() }
})

test('generation failure is visible and retry does not retain an old command', async () => {
  let fail = true
  const { model, stop } = fixture({ issueMessagingBindingCode: async () => {
    if (fail) {
      throw new Error('连接已停用')
    }
    return { data: code }
  } })
  try {
    await model.load()
    await model.issue(official)
    assert.equal(model.issueError.value, '连接已停用')
    assert.equal(model.bindingCode.value === null, true)
    assert.equal(model.issuingConnectionId.value, '')
    fail = false
    await model.issue(official)
    assert.equal(model.issueError.value, '')
    assert.equal(model.bindingCode.value?.code, code.code)
  }
  finally { stop() }
})

test('rejects a code for another connection and an unavailable target', async () => {
  let calls = 0
  const { model, stop } = fixture({ issueMessagingBindingCode: async () => {
    calls++
    return { data: { ...code, connectionId: milky.connectionId } }
  } })
  try {
    await model.load()
    await model.issue({ ...official, connectionId: 'unknown' })
    assert.equal(calls, 0)
    await model.issue(official)
    assert.equal(model.bindingCode.value === null, true)
    assert.match(model.issueError.value, /不一致/)
  }
  finally { stop() }
})

test('refresh removes a code whose connection is no longer enabled', async () => {
  let rows = [official, milky]
  const { model, stop } = fixture({ listMessagingBindingTargets: async () => ({ data: rows }) })
  try {
    await model.load()
    await model.issue(official)
    rows = [milky]
    await model.load()
    assert.equal(model.bindingCode.value === null, true)
  }
  finally { stop() }
})

test('disposing the card ignores late load and generation results', async () => {
  const pendingLoad = deferred<{ data: MessagingBindingTarget[] }>()
  const a = fixture({ listMessagingBindingTargets: () => pendingLoad.promise })
  const loading = a.model.load()
  a.stop()
  pendingLoad.resolve({ data: [official] })
  await loading
  assert.equal(a.model.targets.value.length, 0)

  const pendingCode = deferred<{ data: MessagingBindingCode }>()
  const b = fixture({ issueMessagingBindingCode: () => pendingCode.promise })
  await b.model.load()
  const generating = b.model.issue(official)
  b.stop()
  pendingCode.resolve({ data: code })
  await generating
  assert.equal(b.model.bindingCode.value, null)
})

test('expiry treats the deadline and malformed dates as unusable', () => {
  assert.equal(bindingCodeExpired(code, Date.parse(code.expiresAt) - 1), false)
  assert.equal(bindingCodeExpired(code, Date.parse(code.expiresAt)), true)
  assert.equal(bindingCodeExpired({ ...code, expiresAt: 'invalid' }, 0), true)
})

test('connection labels preserve protocol and the best available bot name', () => {
  assert.equal(bindingTargetLabel(official), '官方机器人')
  assert.equal(bindingTargetLabel({ botName: ' ', connectionName: '连接名' }), '连接名')
  assert.equal(bindingTargetLabel({}), '未命名连接')
  assert.equal(bindingProtocolLabel('official'), '官方 QQ 机器人')
  assert.equal(bindingProtocolLabel('milky'), 'Milky')
})
