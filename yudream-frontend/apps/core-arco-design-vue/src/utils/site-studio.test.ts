import type { SiteDocument, StudioNode } from '../types/site-studio'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import { applyStudioCommand, compileStudioPage, createStudioDocument, createStudioGrapesAdapter, createStudioNode, createStudioPackage, exportStudioStaticHtml, findStudioNode, importStudioPackage, studioNodesToGrapesModel, validateStudioDocument, validateStudioStyle, validateStudioUrl } from './site-studio'

function sample() {
  const doc = createStudioDocument('测试')
  const parent = createStudioNode('section')
  const text = createStudioNode('text')
  parent.children = [text]
  doc.pages[0]!.root = [parent]
  return { doc, parent, text }
}
test('stable UUIDs, immutable snapshots, duplicate and ordered move', () => {
  const { doc, parent, text } = sample()
  assert.match(doc.id, /^[\da-f-]{36}$/)
  const before = JSON.stringify(doc)
  const next = applyStudioCommand(doc, { type: 'set-text', nodeId: text.id, text: '已修改' })
  assert.equal(JSON.stringify(doc), before)
  assert.equal(findStudioNode(next, text.id)?.text, '已修改')
  const duplicate = applyStudioCommand(next, { type: 'duplicate', nodeId: parent.id })
  assert.notEqual(duplicate.pages[0]!.root[1]!.id, parent.id)
  assert.notEqual(duplicate.pages[0]!.root[1]!.children[0]!.id, text.id)
  const moved = applyStudioCommand(duplicate, { type: 'move', nodeId: text.id, target: { parentId: duplicate.pages[0]!.root[1]!.id }, index: 1 })
  assert.equal(findStudioNode(moved, parent.id)?.children.length, 0)
  assert.equal(JSON.stringify(doc), before, 'undo can restore the exact original snapshot')
})
test('atomic failure protects roots, cycles, duplicate ids and mixed content', () => {
  const { doc, parent, text } = sample()
  const before = JSON.stringify(doc)
  const invalid = [
    { type: 'remove', nodeId: doc.pages[0]!.id },
    { type: 'move', nodeId: parent.id, target: { parentId: text.id } },
    { type: 'insert', target: { pageId: doc.pages[0]!.id }, node: text },
    { type: 'set-text', nodeId: parent.id, text: '不能破坏结构' },
    { type: 'remove-page', pageId: doc.pages[0]!.id },
  ] as const
  for (const command of invalid) {
    assert.throws(() => applyStudioCommand(doc, command))
    assert.equal(JSON.stringify(doc), before)
  }
  assert.throws(() => applyStudioCommand(doc, { type: 'batch', commands: [{ type: 'set-text', nodeId: text.id, text: '临时' }, { type: 'remove', nodeId: 'absent' }] }))
  assert.equal(JSON.stringify(doc), before)
  assert.throws(() => applyStudioCommand(doc, { type: 'batch', commands: [{ type: 'batch', commands: [] }] }), /不能嵌套/)
  const next = applyStudioCommand(doc, { type: 'batch', commands: [{ type: 'set-text', nodeId: text.id, text: '完成' }, { type: 'set-style', nodeId: text.id, styles: { color: 'red' } }] })
  assert.equal(findStudioNode(next, text.id)?.styles.color, 'red')
})
test('compiles semantic chrome, escaped text, scoped tokens and media breakpoints without bridges', () => {
  const { doc, text } = sample()
  text.text = '<script>alert("x")</script> & 中文'
  text.styles = { color: 'var(--accent)' }
  text.responsive = { tablet: { 'font-size': '20px' }, mobile: { 'font-size': '16px' } }
  doc.theme.tokens = { accent: '#abc' }
  doc.theme.header = [createStudioNode('header')]
  doc.theme.footer = [createStudioNode('footer')]
  const output = compileStudioPage(doc, doc.pages[0]!.id)
  assert.match(output.html, /<section/)
  assert.match(output.html, /&lt;script&gt;alert\(&quot;x&quot;\)&lt;\/script&gt; &amp; 中文/)
  assert.match(output.html, new RegExp(doc.theme.header[0]!.id))
  assert.match(output.html, new RegExp(doc.theme.footer[0]!.id))
  assert.match(output.css, /data-studio-document=/)
  assert.match(output.css, /--accent:#abc/)
  assert.match(output.css, /max-width: 992px/)
  assert.match(output.css, /max-width: 768px/)
  assert.doesNotMatch(exportStudioStaticHtml(doc, doc.pages[0]!.id), /<script|builderProject|postMessage/)
  assert.match(exportStudioStaticHtml(doc, doc.pages[0]!.id), /^<!doctype html>/)
  assert.doesNotMatch(compileStudioPage(doc, doc.pages[0]!.id, { includeChrome: false }).html, /data-studio-region="header"/)
})
test('media URL and CSS safety share one fail-closed resource policy', () => {
  for (const url of ['/assets/image.png', 'https://example.test/a.png', '#part']) {
    assert.doesNotThrow(() => validateStudioUrl(url))
  }
  for (const mime of ['image/png', 'image/avif', 'video/mp4', 'audio/webm', 'font/woff2']) {
    const url = `data:${mime};base64,YQ==`
    assert.doesNotThrow(() => validateStudioUrl(url, true))
    assert.doesNotThrow(() => validateStudioStyle('background-image', `url("${url}")`))
  }
  for (const url of ['javascript:alert(1)', 'data:text/html;base64,YQ==', 'data:image/svg+xml;base64,YQ==', '//evil.test/x', 'https://user:secret@example.test/x', 'blob:example', '/\\evil', '/x\ny', 'data:image/png;base64,YR==']) {
    assert.throws(() => validateStudioUrl(url, true))
  }
  for (const value of ['url(javascript:alert(1))', 'url("data:image/svg+xml;base64,YQ==")', 'red;color:blue', 'expression(alert(1))', 'u\\72l(https://example.test)', 'red/*x*/', 'red\n']) {
    assert.throws(() => validateStudioStyle('color', value))
  }
  assert.doesNotThrow(() => validateStudioStyle('width', 'calc(100% - 20px)'))
})
test('long plain CSS tokens do not trigger repeated function-name backtracking', () => {
  const started = performance.now()
  validateStudioStyle('--large-token', 'a'.repeat(300_000))
  assert.ok(performance.now() - started < 2000, 'long values must remain interactive')
  assert.throws(() => validateStudioStyle('color', `${'a'.repeat(100_000)}(red)`), /不支持/)
})

test('strict portable site/theme package roundtrips embedded and referenced assets without fetching', () => {
  const { doc } = sample()
  doc.assets = [{ id: crypto.randomUUID(), name: '图', url: 'data:image/png;base64,YQ==', mimeType: 'image/png', kind: 'image' }, { id: crypto.randomUUID(), name: '音频', url: 'https://example.test/a.mp3', mimeType: 'audio/mpeg', kind: 'audio' }]
  for (const kind of ['site', 'theme'] as const) {
    const pack = createStudioPackage(doc, kind)
    assert.deepEqual(importStudioPackage(JSON.stringify(pack)), pack)
    assert.deepEqual(pack.assets.map(asset => asset.mode), kind === 'site' ? ['embedded', 'reference'] : [])
    pack.document.name = '独立副本'
    assert.equal(doc.name, '测试')
  }
  const pack = createStudioPackage(doc)
  pack.assets[0]!.url = 'https://wrong.test/x'
  assert.throws(() => importStudioPackage(pack))
  assert.throws(() => importStudioPackage({ ...createStudioPackage(doc), version: 2 }))
  assert.throws(() => importStudioPackage({ ...createStudioPackage(doc), credentials: 'secret' }))
})
test('theme packages omit private pages and retain only theme asset dependencies', () => {
  const { doc } = sample()
  const header = createStudioNode('image')
  header.attributes.src = '/assets/logo.png'
  doc.theme.header = [header]
  doc.assets = [
    { id: 'logo-asset', name: 'Logo', url: '/assets/logo.png', mimeType: 'image/png', kind: 'image' },
    { id: 'private-asset', name: 'Private page image', url: '/assets/private.png', mimeType: 'image/png', kind: 'image' },
  ]
  const theme = createStudioPackage(doc, 'theme')
  assert.equal(theme.document.pages.length, 1)
  assert.deepEqual(theme.document.pages[0]?.root, [])
  assert.deepEqual(theme.assets.map(asset => asset.id), ['logo-asset'])
  assert.deepEqual(importStudioPackage(JSON.stringify(theme)), theme)
  assert.equal(doc.pages[0]?.root.length, 1)
  const legacy = { ...createStudioPackage(doc, 'site'), kind: 'theme' }
  assert.equal(importStudioPackage(legacy).document.pages[0]?.root.length, 1)
})

test('page path, global ids and model boundaries are strict', () => {
  const { doc, parent } = sample()
  assert.throws(() => applyStudioCommand(doc, { type: 'add-page', page: { id: crypto.randomUUID(), name: '重复', path: '/', root: [] } }))
  const about = applyStudioCommand(doc, { type: 'add-page', page: { id: crypto.randomUUID(), name: '关于', path: '/About', root: [] } })
  assert.throws(() => applyStudioCommand(about, { type: 'add-page', page: { id: crypto.randomUUID(), name: '重复', path: '/about', root: [] } }))
  for (const path of ['/about/', '//about', '/about?x=1', '/../about']) {
    assert.throws(() => applyStudioCommand(doc, { type: 'update-page', pageId: doc.pages[0]!.id, patch: { path } }))
  }
  const unsafePatches: Partial<StudioNode>[] = [{ tag: 'script' }, { attributes: { onclick: 'alert(1)' } }, { attributes: { src: 'javascript:alert(1)' } }, { text: '混用' }]
  for (const patch of unsafePatches) {
    assert.throws(() => applyStudioCommand(doc, { type: 'update-node', nodeId: parent.id, patch }))
  }
  const models = studioNodesToGrapesModel(doc.pages[0]!.root)
  assert.equal(models[0]!.attributes!['data-studio-id'], parent.id)
  assert.equal(typeof createStudioGrapesAdapter().setup, 'function')
})
test('shared JSON contract fixtures are accepted and rejected identically by both tiers', () => {
  const fixture = JSON.parse(readFileSync(new URL('../../../../../tests/fixtures/site-studio-contract.json', import.meta.url), 'utf8')) as {
    valid: {
      name: string
      document: SiteDocument
    }[]
    invalid: {
      name: string
      document: unknown
    }[]
  }
  for (const item of fixture.valid) {
    assert.doesNotThrow(() => validateStudioDocument(item.document), item.name)
  }
  for (const item of fixture.invalid) {
    assert.throws(() => validateStudioDocument(item.document), item.name)
  }
})
// These tests also run under a real browser's DOMParser in the integration harness.
export function runStudioDomTests() {
  const previous = createStudioNode('text')
  const html = `<section data-studio-id="fixed-section">before <strong data-studio-id="fixed-bold">中文 &amp; text</strong> after</section>`
  return { previous, html }
}
