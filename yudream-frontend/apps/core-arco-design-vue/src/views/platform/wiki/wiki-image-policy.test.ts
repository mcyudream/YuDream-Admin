/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { WIKI_INLINE_IMAGE_POLICY, wikiFileContentPath, wikiImageUploadHint } from './wiki-utils'

// 安全回归：Wiki 内联图片的可见性策略与预览地址解析。
// 图片一律按私有文件保存（不能按空间公开状态推导），工作台预览改用登录态 blob 地址；
// 公开页的内联图片需要发布态代理/提升契约，当前状态是显式未决，不能宣称安全。

function wikiFile(relativePath: string) {
  return readFileSync(join(import.meta.dirname, relativePath), 'utf8')
}

function componentFiles() {
  const dir = join(import.meta.dirname, 'components')
  return readdirSync(dir).filter(name => name.endsWith('.vue'))
}

test('inline images are always private, never derived from space state', () => {
  assert.equal(WIKI_INLINE_IMAGE_POLICY, 'private')
  const hint = wikiImageUploadHint()
  assert.match(hint, /始终按私有文件保存/)
  // 公开页展示是显式未决项，不声称已经可用
  assert.match(hint, /后端契约待接入/)
})

test('upload path never derives publicAccess from the space', () => {
  const source = wikiFile('wiki-images.ts')
  assert.match(source, /data\.append\('publicAccess', 'false'\)/)
  assert.doesNotMatch(source, /publicReadEnabled/)
  // 未发布草稿图片不能被匿名读取，因此也不能存在任何公开上传分支
  assert.doesNotMatch(source, /publicAccess',\s*(String\(|'true')/)
  assert.doesNotMatch(source, /uploadWikiImage\(file, /)
})

test('resolves same-origin file content urls for authenticated preview', () => {
  assert.equal(wikiFileContentPath('/api/files/1024/content'), '/api/files/1024/content')
  assert.equal(wikiFileContentPath('/api/files/1024/content/thumb'), '/api/files/1024/content/thumb')
})

test('resolves backend-prefixed urls and strips query or hash', () => {
  assert.equal(wikiFileContentPath('https://api.example.com/api/files/7/content'), '/api/files/7/content')
  assert.equal(wikiFileContentPath('/api/files/7/content?t=1#x'), '/api/files/7/content')
})

test('leaves external and non-file urls untouched', () => {
  assert.equal(wikiFileContentPath('https://cdn.example.com/a.png'), null)
  assert.equal(wikiFileContentPath('/uploads/a.png'), null)
  assert.equal(wikiFileContentPath(''), null)
  assert.equal(wikiFileContentPath(undefined), null)
})

// —— 结构性回归：Wiki 不能再用会强制公开的 CMS 上传通道 ——

test('wiki UI never imports the CMS markdown wrapper', () => {
  for (const name of componentFiles()) {
    const source = wikiFile(`components/${name}`)
    assert.doesNotMatch(source, /import\s+CmsMarkdownEditor/, `${name} 仍在复用 CmsMarkdownEditor（固定 publicAccess=true）`)
  }
})

test('inline image upload pins the wiki module and stays private', () => {
  const source = wikiFile('wiki-images.ts')
  assert.match(source, /export const WIKI_IMAGE_MODULE = 'wiki-image'/)
  assert.match(source, /data\.append\('module', WIKI_IMAGE_MODULE\)/)
  assert.match(source, /data\.append\('publicAccess', 'false'\)/)
  // 未决的发布态契约必须写在代码里，避免下次又被"顺手"改成公开
  assert.match(source, /发布态感知/)
})

test('editable wiki bodies use the wiki wrapper without space-derived visibility', () => {
  const wrapper = wikiFile('components/WikiMarkdownEditor.vue')
  assert.match(wrapper, /:image-upload="uploadImages"/)
  assert.match(wrapper, /uploadWikiImage\(file\)/)
  assert.doesNotMatch(wrapper, /wikiImagePublicAccess/)
  assert.doesNotMatch(wrapper, /:space=/)

  const author = wikiFile('components/WikiAuthorEditor.vue')
  assert.match(author, /import WikiMarkdownEditor from '\.\/WikiMarkdownEditor\.vue'/)
  assert.doesNotMatch(author, /:space="space"/)
  // 属性栏提示必须说明公开页仍未决
  assert.match(author, /wikiImageUploadHint\(\)/)

  const sources = wikiFile('components/WikiSourcesPanel.vue')
  assert.match(sources, /import WikiMarkdownEditor from '\.\/WikiMarkdownEditor\.vue'/)
  assert.doesNotMatch(sources, /:space="store\.space\.value"/)
})
