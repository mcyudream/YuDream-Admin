import type { StudioNode } from '../types/site-studio'
import assert from 'node:assert/strict'
import test from 'node:test'
import { createStudioNode } from './site-studio'
import {
  collectStudioTokenReferences,
  createStudioSnippetInsertion,
  deleteStudioSnippet,
  findMissingStudioTokens,
  freshStudioNodeTree,
  readStudioSnippets,
  saveStudioSnippet,
  STUDIO_SNIPPET_LIMITS,
  studioSnippetStorageKey,
} from './site-studio-snippets'

function fakeStorage(): Storage {
  const map = new Map<string, string>()
  return {
    get length() {
      return map.size
    },
    clear: () => map.clear(),
    getItem: key => map.get(key) ?? null,
    key: index => [...map.keys()][index] ?? null,
    removeItem: key => void map.delete(key),
    setItem: (key, value) => void map.set(key, String(value)),
  }
}

function sectionWithText(): StudioNode {
  const section = createStudioNode('section')
  const text = createStudioNode('text')
  section.styles = { background: 'var(--brand-color)', padding: '24px' }
  section.responsive = { mobile: { padding: '12px' } }
  section.attributes = { 'id': 'dom-section', 'data-studio-id': section.id, 'title': '促销' }
  section.children = [text]
  return section
}

test('snippet storage keys isolate by user', () => {
  assert.equal(studioSnippetStorageKey('123456'), 'site-studio:snippets:123456')
  assert.throws(() => studioSnippetStorageKey('../other'))
  const storage = fakeStorage()
  const node = sectionWithText()
  saveStudioSnippet({ name: '区块', node }, storage, 'user-a')
  assert.equal(readStudioSnippets(storage, 'user-a').length, 1)
  assert.deepEqual(readStudioSnippets(storage, 'user-b'), [])
})

test('saved snippet roundtrips nodes and collects theme token references', () => {
  const storage = fakeStorage()
  const node = sectionWithText()
  const snippet = saveStudioSnippet({ name: '  促销区块  ', node }, storage, 'u')
  assert.equal(snippet.name, '促销区块')
  assert.equal(snippet.nodes.length, 1)
  assert.equal(snippet.nodes[0]!.children[0]!.tag, 'p')
  assert.deepEqual(snippet.tokens, ['--brand-color'])
  const loaded = readStudioSnippets(storage, 'u')
  assert.equal(loaded.length, 1)
  assert.deepEqual(collectStudioTokenReferences(loaded[0]!.nodes), ['--brand-color'])
})

test('insertion regenerates all node ids and strips dom/selection ids', () => {
  const storage = fakeStorage()
  const node = sectionWithText()
  saveStudioSnippet({ name: '区块', node }, storage, 'u')
  const [stored] = readStudioSnippets(storage, 'u')
  const insertion = createStudioSnippetInsertion(stored, { region: 'footer' })
  assert.equal(insertion.command.type, 'insert')
  assert.deepEqual(insertion.command.target, { region: 'footer' })
  const root = insertion.command.node
  assert.notEqual(root.id, stored!.nodes[0]!.id)
  assert.deepEqual(collectStudioTokenReferences([root]), ['--brand-color'])
  assert.equal(root.attributes.id, undefined)
  assert.equal(root.attributes['data-studio-id'], undefined)
  assert.equal(root.attributes.title, '促销')
  assert.deepEqual(root.styles, { background: 'var(--brand-color)', padding: '24px' })
  assert.deepEqual(root.responsive?.mobile, { padding: '12px' })
  assert.notEqual(root.children[0]!.id, stored!.nodes[0]!.children[0]!.id)
  const [fromFirst, fromSecond] = [stored!.nodes[0]!.id, freshStudioNodeTree(stored!.nodes[0]!).id]
  assert.notEqual(fromFirst, fromSecond)
})

test('insertion re-validates untrusted snippets and rejects damaged payloads', () => {
  const storage = fakeStorage()
  saveStudioSnippet({ name: '区块', node: sectionWithText() }, storage, 'u')
  const [stored] = readStudioSnippets(storage, 'u')
  const cases: unknown[] = [
    null,
    'text',
    { ...stored, version: 2 },
    { ...stored, name: 'x'.repeat(STUDIO_SNIPPET_LIMITS.name + 1) },
    { ...stored, nodes: [] },
    { ...stored, tokens: 'no' },
    { ...stored, extra: 1 },
  ]
  for (const payload of cases) {
    assert.throws(() => createStudioSnippetInsertion(payload, { region: 'footer' }))
  }
  assert.throws(() => createStudioSnippetInsertion(null, { region: 'footer' }), /损坏/)
  assert.throws(() => createStudioSnippetInsertion({ ...stored, nodes: [{ ...stored!.nodes[0]!, tag: 'script' }] }, { region: 'footer' }))
})

test('read drops corrupted entries without losing valid neighbours', () => {
  const storage = fakeStorage()
  saveStudioSnippet({ name: '好的', node: createStudioNode('section') }, storage, 'u')
  const raw = storage.getItem(studioSnippetStorageKey('u'))!
  const snippets = JSON.parse(raw) as unknown[]
  const broken = [
    { version: 1, id: 'x', name: '坏', createdAt: 1, tokens: [], nodes: [{ ...createStudioNode('section'), tag: 'script' }] },
    { version: 1, id: '{bad json body', name: '坏', createdAt: 1, tokens: [], nodes: [createStudioNode('section')] },
  ]
  storage.setItem(studioSnippetStorageKey('u'), JSON.stringify([...broken, { __proto__: { polluted: true } }, ...snippets, 'not-object']))
  const survivors = readStudioSnippets(storage, 'u')
  assert.equal(survivors.length, 1)
  assert.equal(survivors[0]!.name, '好的')
  assert.equal(Object.hasOwn(globalThis, 'polluted'), false)
})

test('read tolerates unusable storage payloads entirely', () => {
  const storage = fakeStorage()
  storage.setItem(studioSnippetStorageKey('u'), '{not json')
  assert.deepEqual(readStudioSnippets(storage, 'u'), [])
  storage.setItem(studioSnippetStorageKey('u'), '{"fake":1}')
  assert.deepEqual(readStudioSnippets(storage, 'u'), [])
  storage.setItem(studioSnippetStorageKey('u'), '"string"')
  assert.deepEqual(readStudioSnippets(storage, 'u'), [])
})

test('save enforces name, node count, single root and byte limits', () => {
  const storage = fakeStorage()
  assert.throws(() => saveStudioSnippet({ name: '   ', node: createStudioNode('section') }, storage, 'u'))
  const longName = '名'.repeat(STUDIO_SNIPPET_LIMITS.name + 1)
  assert.throws(() => saveStudioSnippet({ name: longName, node: createStudioNode('section') }, storage, 'u'))
  // 大样式值用空格分块，避免共享校验器内单 token 长串的回溯热点（见测试报告）
  const big = createStudioNode('section')
  big.styles = { padding: 'A '.repeat(150 * 1024) }
  assert.throws(() => saveStudioSnippet({ name: '太大', node: big }, storage, 'u'), /过大/)
  const wide = createStudioNode('container')
  wide.children = Array.from({ length: STUDIO_SNIPPET_LIMITS.nodes }, () => createStudioNode('text'))
  assert.throws(() => saveStudioSnippet({ name: '太宽', node: wide }, storage, 'u'), /超过上限/)
})

test('save enforces per-user snippet count and total storage budget', () => {
  const storage = fakeStorage()
  const node = createStudioNode('section')
  for (let index = 0; index < STUDIO_SNIPPET_LIMITS.count; index++) {
    saveStudioSnippet({ name: `区块 ${index}`, node }, storage, 'u')
  }
  assert.throws(() => saveStudioSnippet({ name: '超量', node }, storage, 'u'), /已达上限/)
  const another = fakeStorage()
  const chunk = createStudioNode('section')
  // 单块约 256KB：块数与总预算余量不落在字节边界上，断言不依赖名称/时间戳的抖动；
  // 换行分块的样式值同时避免共享校验器单 token 长串回溯热点（热点修复见主会话）
  chunk.styles = { padding: 'B '.repeat(125 * 1024) }
  for (let index = 0; index < 4; index++) {
    saveStudioSnippet({ name: `内容块 ${index}`, node: chunk }, another, 'v')
  }
  assert.equal(readStudioSnippets(another, 'v').length, 4)
  assert.throws(() => saveStudioSnippet({ name: '最后一击', node: chunk }, another, 'v'), /已满/)
  assert.equal(readStudioSnippets(another, 'v').length, 4)
})

test('delete removes only the requested snippet and is idempotent', () => {
  const storage = fakeStorage()
  const first = saveStudioSnippet({ name: '一', node: createStudioNode('section') }, storage, 'u')
  const second = saveStudioSnippet({ name: '二', node: createStudioNode('text') }, storage, 'u')
  const third = saveStudioSnippet({ name: '三', node: createStudioNode('container') }, storage, 'u')
  deleteStudioSnippet(second.id, storage, 'u')
  let names = readStudioSnippets(storage, 'u').map(item => item.name)
  assert.deepEqual(names, [first.name, third.name])
  deleteStudioSnippet(second.id, storage, 'u')
  names = readStudioSnippets(storage, 'u').map(item => item.name)
  assert.deepEqual(names, ['一', '三'])
})

test('missing tokens are reported against the target theme', () => {
  // 目标主题采用不带 -- 前缀的变量名，应被视为同一变量
  const missing = findMissingStudioTokens(['--brand-color', '--missing'], { 'brand-color': 'red', '--other': 'blue' })
  assert.deepEqual(missing, ['--missing'])
})
