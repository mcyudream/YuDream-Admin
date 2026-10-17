/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { sanitizeYdMarkdownHtml, shouldUploadYdMarkdownImages } from './markdown-render'
import { shouldEnableYdMarkdownHtml } from './markdown-engine-config'

test('Markdown sanitizer preserves normal formatting while removing active HTML and event handlers', () => {
  const sanitized = sanitizeYdMarkdownHtml('<p>safe <strong>text</strong><img src="x" onerror="alert(1)"></p><script>alert(2)</script>')
  assert.match(sanitized, /<strong>text<\/strong>/)
  assert.doesNotMatch(sanitized, /<script|onerror/i)
  assert.doesNotMatch(sanitized, /alert\(/i)
})

test('Markdown sanitizer allows encoded wiki links and rejects executable URL protocols', () => {
  const sanitized = sanitizeYdMarkdownHtml('<a href="wiki://Page%20Title">page</a><a href="javascript:alert(1)">unsafe</a>')
  assert.match(sanitized, /href="wiki:\/\/Page%20Title"/)
  assert.doesNotMatch(sanitized, /javascript:/)
})

test('raw HTML parsing is enabled only for YD Markdown editor instances', () => {
  assert.equal(shouldEnableYdMarkdownHtml('yd-markdown-editor-v-0'), true)
  assert.equal(shouldEnableYdMarkdownHtml('agent-answer-v-0'), false)
  assert.equal(shouldEnableYdMarkdownHtml('md-editor-v3'), false)
})

test('Markdown sanitizer keeps safe generated SVG icons and strips script, handlers, and references', () => {
  const icon = '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" onload="alert(1)"><g fill="none" stroke="currentColor"><circle cx="12" cy="12" r="8" /><path d="M4 12 L20 12" href="javascript:alert(2)" /></g><script>alert(3)</script><foreignObject><img src="x" onerror="alert(4)"></foreignObject></svg>'
  const sanitized = sanitizeYdMarkdownHtml(icon)
  assert.match(sanitized, /<svg xmlns="http:\/\/www\.w3\.org\/2000\/svg" viewbox="0 0 24 24">/)
  assert.match(sanitized, /<circle cx="12" cy="12" r="8"\s*\/>/)
  assert.match(sanitized, /<path d="M4 12 L20 12"\s*\/>/)
  assert.doesNotMatch(sanitized, /onload|onerror|href|javascript:|<script|foreignobject/i)
  assert.doesNotMatch(sanitized, /alert\(/i)
})

test('Markdown sanitizer preserves md-editor collapsible code headers while removing event handlers', () => {
  const generated = '<details data-line="4" class="md-editor-code" open ontoggle="alert(1)"><summary class="md-editor-code-head" onclick="alert(2)"><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path d="M4 12 L20 12" /></svg></summary><pre class="md-editor-code-body"><code class="language-js">const answer = 42</code></pre></details>'
  const sanitized = sanitizeYdMarkdownHtml(generated)
  assert.match(sanitized, /<details data-line="4" class="md-editor-code" open>/)
  assert.match(sanitized, /<summary class="md-editor-code-head">/)
  assert.match(sanitized, /<svg[^>]*><path d="M4 12 L20 12"\s*\/><\/svg>/)
  assert.match(sanitized, /<pre class="md-editor-code-body"><code class="language-js">const answer = 42<\/code><\/pre>/)
  assert.doesNotMatch(sanitized, /ontoggle|onclick/i)
})

test('Markdown sanitizer keeps md-editor mermaid trigger attributes so diagrams still render', () => {
  const block = '<div class="md-editor-mermaid" data-mermaid-theme="dark" data-mermaid-block="12" data-mermaid-revision="0" data-closed="true" data-line="12">flowchart TD</div>'
  const sanitized = sanitizeYdMarkdownHtml(block)
  assert.match(sanitized, /class="md-editor-mermaid"/)
  assert.match(sanitized, /data-mermaid-theme="dark"/)
  assert.match(sanitized, /data-mermaid-block="12"/)
  assert.match(sanitized, /data-mermaid-revision="0"/)
  assert.match(sanitized, /data-closed="true"/)
  assert.match(sanitized, /data-line="12"/)

  const cached = '<p class="md-editor-mermaid" data-processed="" data-content="flowchart TD">cached</p>'
  const sanitizedCached = sanitizeYdMarkdownHtml(cached)
  assert.match(sanitizedCached, /data-processed=""/)
  assert.match(sanitizedCached, /data-content="flowchart TD"/)
})

test('Markdown sanitizer rejects out-of-range mermaid trigger attribute values and injected handlers', () => {
  const forged = '<div class="md-editor-mermaid" data-mermaid-revision="0&quot; onmouseover=&quot;alert(1)" data-mermaid-block="not-a-number" data-closed="maybe" onclick="alert(2)">x</div>'
  const sanitized = sanitizeYdMarkdownHtml(forged)
  assert.doesNotMatch(sanitized, /onmouseover|onclick/i)
  assert.doesNotMatch(sanitized, /alert\(/i)
  assert.doesNotMatch(sanitized, /data-mermaid-block="not-a-number"/)
  assert.doesNotMatch(sanitized, /data-closed="maybe"/)
})

test('read-only and disabled Markdown editors cannot invoke image upload handlers', () => {
  assert.equal(shouldUploadYdMarkdownImages(true, false, 1), false)
  assert.equal(shouldUploadYdMarkdownImages(false, true, 1), false)
  assert.equal(shouldUploadYdMarkdownImages(false, false, 0), false)
  assert.equal(shouldUploadYdMarkdownImages(false, false, 1), true)
})
