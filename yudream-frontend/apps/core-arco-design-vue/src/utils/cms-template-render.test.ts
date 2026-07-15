import assert from 'node:assert/strict'
import test from 'node:test'
import { renderCmsMarkdown, renderCmsVariables, resolveCmsTemplateRows, sanitizeCmsHtml } from './cms-template-render'

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

test('renders markdown template content without executable markup', () => {
  assert.equal(renderCmsMarkdown('# Title\n\n- **Latest**'), '<h1>Title</h1><ul><li><strong>Latest</strong></li></ul>')
  assert.equal(renderCmsMarkdown('<script>alert(1)</script>'), '<p>&lt;script&gt;alert(1)&lt;/script&gt;</p>')
})
