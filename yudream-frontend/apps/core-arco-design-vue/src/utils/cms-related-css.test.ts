import assert from 'node:assert/strict'
import test from 'node:test'
import { extractRelatedCss, mergeCss, replaceRelatedCss } from './cms-related-css'

const snapshot = { tagName: 'section', classes: ['yb-ai-wiki-grid'], attributes: {} }

test('extracts related rules inside media queries', () => {
  const css = '.other { color: red; } @media (max-width: 800px) { .yb-ai-wiki-grid { grid-template-columns: 1fr; } }'
  const result = extractRelatedCss(css, snapshot)
  assert.match(result, /@media/)
  assert.match(result, /grid-template-columns: 1fr/)
  assert.doesNotMatch(result, /color: red/)
})

test('replaces related rules and preserves unrelated rules', () => {
  const css = '.yb-ai-wiki-grid { display: grid; gap: 8px; } .other { color: red; }'
  const original = extractRelatedCss(css, snapshot)
  const result = replaceRelatedCss(css, original, '.yb-ai-wiki-grid { display: flex; gap: 12px; }')
  assert.match(result, /display: flex/)
  assert.match(result, /gap: 12px/)
  assert.match(result, /\.other/)
  assert.doesNotMatch(result, /display: grid/)
})

test('merges adjacent duplicate selectors without substring false positives', () => {
  const result = mergeCss('.card { color: red; }', '.card { gap: 8px; color: green; }')
  assert.equal((result.match(/\.card\s*\{/g) || []).length, 1)
  assert.match(result, /color: green/)
  assert.match(result, /gap: 8px/)

  const separated = mergeCss('.card { color: red; } .card-wide { color: blue; }', '.card { gap: 8px; }')
  assert.equal((separated.match(/\.card\s*\{/g) || []).length, 2)
  assert.match(separated, /\.card-wide/)
})

test('keeps same selector separate across media contexts', () => {
  const result = mergeCss('.card { color: red; }', '@media (max-width: 600px) { .card { color: blue; } }')
  assert.match(result, /color: red/)
  assert.match(result, /@media[\s\S]*color: blue/)
})

test('rejects selector edits in related CSS', () => {
  const css = '.card { color: red; }'
  const original = extractRelatedCss(css, { tagName: 'div', classes: ['card'], attributes: {} })
  assert.throws(() => replaceRelatedCss(css, original, '.new-card { color: blue; }'), /不能修改选择器/)
})

test('preserves imports and comments while merging declarations', () => {
  const result = mergeCss('@import url("base.css"); .card { color: red; /* keep; comment */ gap: 4px; }', '.card { color: blue; }')
  assert.match(result, /@import url\("base\.css"\);/)
  assert.match(result, /color: blue/)
  assert.match(result, /gap: 4px/)
})

test('rejects incomplete CSS before changing the current style', () => {
  assert.throws(() => mergeCss('.card { color: red; }', '.card { color: blue;'), /未闭合/)
})
