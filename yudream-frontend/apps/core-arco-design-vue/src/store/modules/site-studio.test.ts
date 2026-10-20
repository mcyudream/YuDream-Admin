// 运行：tsx --test --experimental-test-module-mocks src/store/modules/site-studio.test.ts
// API 模块依赖 Vite 虚拟模块，测试内替换为空实现；这里只覆盖本地编辑管道。
import type { SiteDocument } from '@/types/site-studio'
import assert from 'node:assert/strict'
import test, { mock } from 'node:test'
import { createStudioDocument, createStudioNode } from '@/utils/site-studio'

let serverJson = ''
mock.module('@/api/modules/platform-cms-studio', { defaultExport: { get: async () => ({ data: { revision: '1', documentJson: serverJson } }) } })
const { createPinia, setActivePinia } = await import('pinia')
const { useSiteStudioStore } = await import('./site-studio')

function setup() {
  setActivePinia(createPinia())
  const store = useSiteStudioStore()
  const doc: SiteDocument = createStudioDocument('测试')
  const section = createStudioNode('section')
  const text = createStudioNode('text')
  section.children = [text]
  doc.pages[0]!.root = [section]
  store.loadDocumentLocally(doc)
  store.savedJson = null
  return { store, section, text }
}

test('dirty compares content, not key order, and no-op commands add no history', async () => {
  const { store, text } = setup()
  const reversed = (value: unknown): unknown => Array.isArray(value)
    ? value.map(reversed)
    : value && typeof value === 'object' ? Object.fromEntries(Object.entries(value).reverse().map(([key, item]) => [key, reversed(item)])) : value
  serverJson = JSON.stringify(reversed(store.doc))
  await store.load('default')
  assert.equal(store.dirty, false, 'a server echo with another key order is clean')
  store.execute({ type: 'set-text', nodeId: text.id, text: '文本' })
  assert.equal(store.canUndo, false, 'unchanged text must not create an undo entry')
  assert.equal(store.dirty, false)
  store.execute({ type: 'set-text', nodeId: text.id, text: '新文本' })
  assert.equal(store.dirty, true)
  store.undo()
  assert.equal(store.dirty, false)
  assert.equal(store.canUndo, false)
  store.redo()
  assert.equal(store.dirty, true)
})

test('undo restores the exact insertion order of style declarations', () => {
  const { store, section } = setup()
  store.execute({ type: 'set-style', nodeId: section.id, styles: { 'place-items': 'center' } })
  store.execute({ type: 'set-style', nodeId: section.id, styles: { 'align-items': 'start' } })
  store.execute({ type: 'set-style', nodeId: section.id, styles: { color: 'red' } })
  store.undo()
  const node = store.doc!.pages[0]!.root[0]!
  assert.deepEqual(Object.keys(node.styles), ['place-items', 'align-items'])
  store.redo()
  assert.deepEqual(Object.keys(store.doc!.pages[0]!.root[0]!.styles), ['place-items', 'align-items', 'color'])
})

test('consecutive edits with one mergeKey share an undo entry; other edits break the chain', () => {
  const { store, text, section } = setup()
  for (const value of ['你', '你好', '你好世界']) {
    store.execute({ type: 'set-text', nodeId: text.id, text: value }, { mergeKey: `text:${text.id}` })
  }
  store.undo()
  assert.equal(store.canUndo, false, 'a typed phrase is undone in one step')
  assert.equal(store.doc!.pages[0]!.root[0]!.children[0]!.text, '文本')

  store.execute({ type: 'set-text', nodeId: text.id, text: 'A' }, { mergeKey: `text:${text.id}` })
  store.execute({ type: 'set-style', nodeId: section.id, styles: { color: 'red' } })
  store.execute({ type: 'set-text', nodeId: text.id, text: 'AB' }, { mergeKey: `text:${text.id}` })
  store.undo()
  assert.equal(store.doc!.pages[0]!.root[0]!.children[0]!.text, 'A')
  assert.equal(store.doc!.pages[0]!.root[0]!.styles.color, 'red')

  // Undo itself ends the chain: typing after an undo starts a new entry.
  store.execute({ type: 'set-text', nodeId: text.id, text: 'AC' }, { mergeKey: `text:${text.id}` })
  store.undo()
  assert.equal(store.doc!.pages[0]!.root[0]!.children[0]!.text, 'A')
})

test('merge window expires', () => {
  const { store, text } = setup()
  const now = mock.method(Date, 'now', () => 1_000)
  store.execute({ type: 'set-text', nodeId: text.id, text: '一' }, { mergeKey: 'k' })
  now.mock.mockImplementation(() => 2_500)
  store.execute({ type: 'set-text', nodeId: text.id, text: '一二' }, { mergeKey: 'k' })
  now.mock.restore()
  store.undo()
  assert.equal(store.doc!.pages[0]!.root[0]!.children[0]!.text, '一')
})

test('failed commands leave document and history untouched', () => {
  const { store, section } = setup()
  const before = JSON.stringify(store.doc)
  assert.throws(() => store.execute({ type: 'set-text', nodeId: section.id, text: 'x' }))
  assert.equal(JSON.stringify(store.doc), before)
  assert.equal(store.canUndo, false)
})
