import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createYdPageBuilderFacadeMethod, isYdPageBuilderInstanceCurrent, resolveYdPageBuilderEngineContainer, shouldYdPageBuilderEmitReady } from './page-builder-lifecycle'

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
  const setComponents = createYdPageBuilderFacadeMethod(() => false, (_html: string) => {
    mutated = true
  })
  assert.throws(() => setComponents('<main/>'), /页面构建器实例已释放/)
  assert.equal(mutated, false)
})

test('PageBuilder instance-current guard accepts only the same live editor', () => {
  const original = {}
  const replacement = {}
  const destroyed = new Set<object>()
  const isCurrent = (captured: object | null, current: object | null, disposed = false) =>
    isYdPageBuilderInstanceCurrent(captured, current, disposed, editor => destroyed.has(editor))

  assert.equal(isCurrent(original, original), true)
  assert.equal(isCurrent(original, replacement), false)
  assert.equal(isCurrent(original, original, true), false)
  destroyed.add(original)
  assert.equal(isCurrent(original, original), false)
  assert.equal(isCurrent(null, null), false)
})

test('PageBuilder upload completion belongs only to the same live editor instance', () => {
  const original = {}
  const destroyed = new Set<object>()
  const isCurrent = (captured: object | null, current: object | null, disposed = false) =>
    isYdPageBuilderInstanceCurrent(captured, current, disposed, editor => destroyed.has(editor))

  assert.equal(isCurrent(original, original), true)
  assert.equal(isCurrent(original, original, true), false)
  destroyed.add(original)
  assert.equal(isCurrent(original, original), false)
})

test('PageBuilder pending change flush drops late timers after disposal', () => {
  const scheduled = {}
  const destroyed = new Set<object>()
  const flush = (current: object | null, disposed: boolean) =>
    isYdPageBuilderInstanceCurrent(scheduled, current, disposed, editor => destroyed.has(editor))

  assert.equal(flush(scheduled, false), true)
  assert.equal(flush(scheduled, true), false)
  assert.equal(flush(null, false), false)
})

function fakeElement(children: object[] = []): HTMLElement {
  return { children } as unknown as HTMLElement
}

test('PageBuilder engine takeover targets the dedicated engine container, never the Vue root', () => {
  const statusSpan = fakeElement()
  const root = fakeElement([fakeElement(), statusSpan])
  const engine = fakeElement()

  assert.equal(resolveYdPageBuilderEngineContainer(root, engine), engine)
  assert.notEqual(resolveYdPageBuilderEngineContainer(root, engine), root)
})

test('PageBuilder engine takeover bails out instead of falling back to the Vue root', () => {
  const rootWithStatus = fakeElement([fakeElement()])

  // 引擎容器尚未渲染：异步初始化放弃挂载，绝不把带状态节点的根元素交给 GrapesJS。
  assert.equal(resolveYdPageBuilderEngineContainer(rootWithStatus, null), null)
  assert.equal(resolveYdPageBuilderEngineContainer(null, null), null)
  // 防御：即使引擎与根是同一节点也拒绝。
  assert.equal(resolveYdPageBuilderEngineContainer(rootWithStatus, rootWithStatus), null)
})

test('PageBuilder ready emits exactly once after canvas load completes on a prepared engine', () => {
  const state = { disposed: false, readyEmitted: false, prepared: false, canvasLoaded: false }

  // 引擎 load 事件先于初始内容写入：不能提前就绪。
  state.canvasLoaded = true
  assert.equal(shouldYdPageBuilderEmitReady(state), false)

  // 初始内容写入完成：加载完成，允许就绪。
  state.prepared = true
  assert.equal(shouldYdPageBuilderEmitReady(state), true)

  // 迟到/重复的 load 事件：只发一次。
  state.readyEmitted = true
  assert.equal(shouldYdPageBuilderEmitReady(state), false)
})

test('PageBuilder ready never emits after disposal even on a late load event', () => {
  assert.equal(shouldYdPageBuilderEmitReady({ disposed: true, readyEmitted: false, prepared: true, canvasLoaded: true }), false)
  assert.equal(shouldYdPageBuilderEmitReady({ disposed: false, readyEmitted: false, prepared: true, canvasLoaded: false }), false)
  assert.equal(shouldYdPageBuilderEmitReady({ disposed: false, readyEmitted: false, prepared: false, canvasLoaded: true }), false)
})
