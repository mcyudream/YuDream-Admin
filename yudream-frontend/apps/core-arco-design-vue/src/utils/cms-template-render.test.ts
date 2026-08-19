import assert from 'node:assert/strict'
import test from 'node:test'
import { evaluateCmsTemplateCondition, parseCmsTemplateFor, renderCmsMarkdown, renderCmsVariables, resolveCmsTemplateLimit, resolveCmsTemplateRows, sanitizeCmsHtml } from './cms-template-render'

const context = {
  cms: { pages: { latest: [{ title: 'Latest page', url: '/site/latest' }] } },
  knowledge: { latest: [{ title: 'Latest knowledge', url: '/wiki/docs/latest' }] },
}

test('resolves nested CMS template paths for repeat data', () => {
  assert.equal(resolveCmsTemplateRows('knowledge.latest', context)[0].title, 'Latest knowledge')
  assert.equal(resolveCmsTemplateRows('cms.pages.latest', context)[0].url, '/site/latest')
})

test('escapes ordinary variables and removes executable HTML', () => {
  assert.equal(renderCmsVariables('{{item.title}}', { item: { title: '<script>alert(1)</script>' } }), '&lt;script&gt;alert(1)&lt;/script&gt;')
  assert.equal(sanitizeCmsHtml('<img src="x" onerror="alert(1)"><script>alert(2)</script>'), '<img src="x">')
})

test('parses CMS for/if/limit template directives safely', () => {
  assert.deepEqual(parseCmsTemplateFor('wiki in knowledge.latest'), { itemName: 'wiki', path: 'knowledge.latest' })
  assert.equal(parseCmsTemplateFor('knowledge.latest'), null)
  assert.equal(resolveCmsTemplateLimit('3', context), 3)
  assert.equal(resolveCmsTemplateLimit('{{cms.pageSize}}', { cms: { pageSize: 6 } }), 6)
  assert.equal(evaluateCmsTemplateCondition('knowledge.latest.count > 0', context), true)
  assert.equal(evaluateCmsTemplateCondition('knowledge.latest.count == 0', context), false)
  assert.equal(evaluateCmsTemplateCondition('cms.pages.latest.count == 1', context), true)
  assert.equal(evaluateCmsTemplateCondition('!knowledge.missing', context), true)
  assert.equal(evaluateCmsTemplateCondition('window.location', context), false)
})

test('renders markdown template content without executable markup', () => {
  assert.equal(renderCmsMarkdown('# Title\n\n- **Latest**'), '<h1>Title</h1><ul><li><strong>Latest</strong></li></ul>')
  assert.equal(renderCmsMarkdown('<script>alert(1)</script>'), '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>')
})
