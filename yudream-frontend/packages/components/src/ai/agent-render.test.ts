import type { YdAgentRenderDocument } from './agent-render'
import assert from 'node:assert/strict'
// The workspace runs these pure rendering checks with node:test via tsx.
// eslint-disable-next-line test/no-import-node-test
import { test as it } from 'node:test'
import { buildYdAgentReferenceIndex, parseYdAgentRenderDocument, renderYdAgentMarkdown, resolveYdAgentReferenceAnchor, safeYdAgentReferenceUrl, sanitizeYdAgentHtml, ydAgentReferenceAnchor } from './agent-render'

it('reference links only accept explicit HTTP(S) sources', () => {
  assert.equal(safeYdAgentReferenceUrl('https://example.com/source?a=1'), 'https://example.com/source?a=1')
  assert.equal(safeYdAgentReferenceUrl(' HTTP://example.com/source '), 'http://example.com/source')
  for (const value of ['javascript:alert(1)', 'data:text/html,<script>', '//example.com', '/src/index.ts', 'file:///secret', 'https://example.com/\nsecret']) {
    assert.equal(safeYdAgentReferenceUrl(value), undefined)
  }
})

it('reference IDs cannot escape the Markdown link and preserve large IDs', () => {
  const id = '9007199254740993/a b\')!'
  const anchor = ydAgentReferenceAnchor(id)
  assert.equal(resolveYdAgentReferenceAnchor(anchor), id)
  assert.equal(/[()!' ]/.test(anchor), false)
  assert.equal(resolveYdAgentReferenceAnchor('#yd-ref-%ZZ'), undefined)
  assert.equal(resolveYdAgentReferenceAnchor('#other'), undefined)
})

it('answer scopes isolate reference links while resolving the original string ID', () => {
  const id = '9007199254740993/theme:颜色'
  const first = ydAgentReferenceAnchor(id, 'answer:1')
  const second = ydAgentReferenceAnchor(id, 'answer:2')
  assert.notEqual(first, second)
  assert.equal(resolveYdAgentReferenceAnchor(first, 'answer:1'), id)
  assert.equal(resolveYdAgentReferenceAnchor(first, 'answer:2'), undefined)
  const references = new Map([[id, { number: 1, reference: { id, title: '主题', excerpt: '原文' } }]])
  assert.equal(renderYdAgentMarkdown(`引用 [[ref:${id}]]`, references, 'answer:1'), `引用 [1](${first})`)
  assert.equal(sanitizeYdAgentHtml(`<a href="${first}">1</a>`), `<a href="${first}">1</a>`)
  assert.equal(ydAgentReferenceAnchor('theme'), '#yd-ref-theme')
})

it('reference numbers follow first use, repeat IDs stay stable, missing markers stay visible', () => {
  const document: YdAgentRenderDocument = { version: 1, blocks: [
    { type: 'markdown', text: '参考 [[ref:b]] 与 [[ref:a]]、[[ref:missing]]，再次 [[ref:b]]。' },
    { type: 'reference', reference: { id: 'a', title: '文件 A', path: 'src/a.ts', excerpt: '原文 A' } },
    { type: 'reference', reference: { id: 'b', title: '文件 B', path: 'src/b.ts', excerpt: '原文 B' } },
    { type: 'reference', reference: { id: 'b', title: '重复 B', excerpt: '重复' } },
  ] }
  const references = buildYdAgentReferenceIndex(document)
  assert.equal(references.get('b')?.number, 1)
  assert.equal(references.get('b')?.reference.title, '文件 B')
  assert.equal(references.get('a')?.number, 2)
  assert.equal(renderYdAgentMarkdown((document.blocks[0] as { text: string }).text, references), '参考 [1](#yd-ref-b) 与 [2](#yd-ref-a)、[[ref:missing]]，再次 [1](#yd-ref-b)。')
})

it('reference substitution preserves literal inline and fenced code', () => {
  const text = '<img src=x onerror=alert(1)> [[ref:a]]\n`<script> [[ref:a]]`\n```html\n<script> [[ref:a]]\n```\n**结束**'
  const document: YdAgentRenderDocument = { version: 1, blocks: [
    { type: 'markdown', text },
    { type: 'reference', reference: { id: 'a', title: '来源', excerpt: '<script>作为原文</script>' } },
  ] }
  assert.equal(renderYdAgentMarkdown(text, buildYdAgentReferenceIndex(document)), '<img src=x onerror=alert(1)> [1](#yd-ref-a)\n`<script> [[ref:a]]`\n```html\n<script> [[ref:a]]\n```\n**结束**')
})

it('local HTML sanitizer removes scripts, handlers and unsafe links without breaking source links', () => {
  const html = '<script>alert(1)</script><img src="https://example.com/p.png" onerror="alert(2)"><a href="javascript:alert(3)">危险</a><p onclick="alert(4)">正文</p><a href="https://example.com/source">官方来源</a><a href="#yd-ref-src%2Fa.ts">工作区</a><pre><code class="language-html">&lt;script&gt;示例&lt;/script&gt;</code></pre><span class="fixed inset-0 hljs-keyword">样式</span>'
  const result = sanitizeYdAgentHtml(html)
  assert.equal(/<script|onerror|onclick|javascript:|alert\(/.test(result), false)
  assert.match(result, /<img src="https:\/\/example.com\/p.png">/)
  assert.match(result, /href="https:\/\/example.com\/source" target="_blank" rel="noopener noreferrer"/)
  assert.match(result, /href="#yd-ref-src%2Fa.ts"/)
  assert.match(result, /&lt;script&gt;示例&lt;\/script&gt;/)
  assert.match(result, /class="hljs-keyword"/)
  assert.equal(/fixed|inset-0/.test(result), false)
})

it('render document validates blocks and preserves string IDs without coercion', () => {
  const value = { version: 1, blocks: [
    { type: 'markdown', text: '**回答**' },
    { type: 'reference', reference: { id: '9007199254740993', title: '工作区', path: 'src/a.ts', excerpt: '原文', startLine: 1, endLine: 2 } },
    { type: 'tool', tool: { id: '__proto__', name: 'files.read', status: 'success', output: '结果', durationMs: 10 } },
  ] }
  assert.deepEqual(parseYdAgentRenderDocument(value), value)
  for (const invalid of [null, [], { version: 2, blocks: [] }, { version: 1, blocks: [{}] }, { version: 1, blocks: [{ type: 'html', text: 'HTML' }] }, { version: 1, blocks: [{ type: 'markdown', text: 1 }] }]) {
    assert.equal(parseYdAgentRenderDocument(invalid), undefined)
  }
})

it('malformed reference and tool fields cannot reach the renderer', () => {
  const reference = { id: 'file', title: '文件', excerpt: '原文' }
  const tool = { id: 'tool', name: 'files.read', status: 'running' }
  for (const patch of [{ id: Number('9007199254740993') }, { excerpt: null }, { url: false }, { startLine: 0 }, { startLine: 5, endLine: 2 }, { endLine: Number.NaN }]) {
    assert.equal(parseYdAgentRenderDocument({ version: 1, blocks: [{ type: 'reference', reference: { ...reference, ...patch } }] }), undefined)
  }
  for (const patch of [{ id: 5 }, { status: 'unknown' }, { output: {} }, { retry: 'yes' }, { durationMs: -1 }, { durationMs: Number.NaN }]) {
    assert.equal(parseYdAgentRenderDocument({ version: 1, blocks: [{ type: 'tool', tool: { ...tool, ...patch } }] }), undefined)
  }
})
