import assert from 'node:assert/strict'
// eslint-disable-next-line test/no-import-node-test
import { test } from 'node:test'
import { runYdLoaderSteps, YdLoaderStepError } from './loader-runner'

test('顺序执行并把前序结果传入后续动作', async () => {
  const positions: number[] = []
  const result = await runYdLoaderSteps([{ text: 'first', name: 'id', action: () => '9007199254740993' }, { text: 'second', name: 'copy', action: ({ results }) => results.id }], new AbortController().signal, index => positions.push(index))
  assert.deepEqual(positions, [0, 1])
  assert.equal(result.results.copy, '9007199254740993')
  assert.equal(result.cancelled, false)
})
test('取消后当前动作的迟到结果不写入，也不执行后续动作', async () => {
  const controller = new AbortController()
  let resolve: (value: string) => void = () => {
  }
  let next = false
  const job = runYdLoaderSteps([{ text: 'pending', name: 'late', action: () => new Promise<string>((done) => {
    resolve = done
  }) }, { text: 'next', action: () => {
    next = true
  } }], controller.signal, () => {
  })
  controller.abort()
  resolve('ignored')
  const result = await job
  assert.equal(result.cancelled, true)
  assert.equal(next, false)
  assert.equal(result.results.late, undefined)
})
test('失败准确报告步骤与原始错误，停止后续步骤', async () => {
  const reason = new Error('network')
  let next = false
  await assert.rejects(runYdLoaderSteps([{ text: 'fail', action: () => {
    throw reason
  } }, { text: 'next', action: () => {
    next = true
  } }], new AbortController().signal, () => {
  }), (error: unknown) => error instanceof YdLoaderStepError && error.index === 0 && error.reason === reason)
  assert.equal(next, false)
})
test('开始前已经取消不触发步骤事件和动作', async () => {
  const controller = new AbortController()
  controller.abort()
  let called = false
  const result = await runYdLoaderSteps([{ text: 'unused', action: () => {
    called = true
  } }], controller.signal, () => {
    called = true
  })
  assert.equal(result.cancelled, true)
  assert.equal(called, false)
})
