/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createYdPageBuilderFacadeMethod, isYdPageBuilderUploadCurrent } from './page-builder-lifecycle'

test('PageBuilder facade methods reject calls after editor disposal', () => {
  let active = true
  let reads = 0
  const getSnapshot = createYdPageBuilderFacadeMethod(() => active, () => {
    reads += 1
    return { htmlContent: '<p>valid</p>' }
  })
  assert.deepEqual(getSnapshot(), { htmlContent: '<p>valid</p>' })
  active = false
  assert.throws(() => getSnapshot(), /页面构建器实例已释放/)
  assert.equal(reads, 1)
})

test('PageBuilder facade mutation methods reject instead of touching a disposed engine', () => {
  let mutated = false
  const setComponents = createYdPageBuilderFacadeMethod(() => false, (_html: string) => { mutated = true })
  assert.throws(() => setComponents('<main/>'), /页面构建器实例已释放/)
  assert.equal(mutated, false)
})

test('PageBuilder upload completion belongs only to the same live editor instance', () => {
  const original = {}
  const replacement = {}
  const destroyed = new Set<object>()
  const isCurrent = (captured: object | null, current: object | null, disposed = false) =>
    isYdPageBuilderUploadCurrent(captured, current, disposed, editor => destroyed.has(editor))

  assert.equal(isCurrent(original, original), true)
  assert.equal(isCurrent(original, replacement), false)
  assert.equal(isCurrent(original, original, true), false)
  destroyed.add(original)
  assert.equal(isCurrent(original, original), false)
  assert.equal(isCurrent(null, null), false)
})
