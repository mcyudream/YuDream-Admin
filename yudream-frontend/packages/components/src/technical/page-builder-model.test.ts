/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { resolveYdPageBuilderChangeMode, resolveYdPageBuilderLoad, sameYdPageBuilderSnapshot } from './page-builder-model'

test('优先恢复合法 GrapesJS 项目 JSON，保留 HTML/CSS 作为回退内容', () => {
  const loaded = resolveYdPageBuilderLoad({ htmlContent: '<main>fallback</main>', cssContent: 'main{color:red}', builderProjectJson: '{"pages":[]}' })
  assert.deepEqual(loaded, { kind: 'project', project: { pages: [] } })
})

test('项目 JSON 无效或不是 object 时回退 HTML/CSS 并返回可呈现的错误', () => {
  const invalid = resolveYdPageBuilderLoad({ htmlContent: '<p>fallback</p>', cssContent: 'p{margin:0}', builderProjectJson: '{bad' })
  assert.equal(invalid.kind, 'html')
  if (invalid.kind === 'html') {
    assert.equal(invalid.htmlContent, '<p>fallback</p>')
    assert.equal(invalid.cssContent, 'p{margin:0}')
    assert.ok(invalid.projectError instanceof Error)
  }
  const array = resolveYdPageBuilderLoad({ htmlContent: '<p/>', builderProjectJson: '[]' })
  assert.equal(array.kind, 'html')
  if (array.kind === 'html') {
    assert.match(array.projectError?.message || '', /JSON object/)
  }
})

test('父级回显上次 emitted snapshot 不重建项目，真正外部改值按项目/HTML 模式分别恢复', () => {
  const last = { htmlContent: '<p>a</p>', cssContent: 'p{color:red}', builderProjectJson: '{"pages":[1]}' }
  assert.equal(sameYdPageBuilderSnapshot(last, { ...last }), true)
  assert.equal(resolveYdPageBuilderChangeMode({ ...last }, { htmlContent: '', cssContent: '', builderProjectJson: '' }, last), 'ignore')
  assert.equal(resolveYdPageBuilderChangeMode({ ...last, builderProjectJson: '{"pages":[2]}' }, last, last), 'project')
  assert.equal(resolveYdPageBuilderChangeMode({ ...last, cssContent: 'p{color:blue}' }, last, last), 'html')
  assert.equal(resolveYdPageBuilderChangeMode({ ...last, builderProjectJson: '' }, last, last), 'html')
})
