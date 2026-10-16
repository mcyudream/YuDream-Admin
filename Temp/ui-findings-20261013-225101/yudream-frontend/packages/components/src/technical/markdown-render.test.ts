/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { sanitizeYdMarkdownHtml, shouldUploadYdMarkdownImages } from './markdown-render'

test('Markdown sanitizer preserves normal formatting while removing active HTML and event handlers', () => {
  const sanitized = sanitizeYdMarkdownHtml('<p>safe <strong>text</strong><img src="x" onerror="alert(1)"></p><script>alert(2)</script>')
  assert.match(sanitized, /<strong>text<\/strong>/)
  assert.doesNotMatch(sanitized, /<script|onerror/i)
  assert.doesNotMatch(sanitized, /alert\(/i)
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

test('read-only and disabled Markdown editors cannot invoke image upload handlers', () => {
  assert.equal(shouldUploadYdMarkdownImages(true, false, 1), false)
  assert.equal(shouldUploadYdMarkdownImages(false, true, 1), false)
  assert.equal(shouldUploadYdMarkdownImages(false, false, 0), false)
  assert.equal(shouldUploadYdMarkdownImages(false, false, 1), true)
})
