/* eslint-disable test/no-import-node-test */
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import { publicWikiImageProxyPath, publishedWikiImageUrl, rewritePublishedWikiImages, wikiFileObjectId } from './wiki-public-images'

// 公开文章图片只允许改写到发布态代理；原文档与无锚点场景保持原样（服务端 fail closed）。

function wikiFile(relativePath: string) {
  return readFileSync(join(import.meta.dirname, relativePath), 'utf8')
}

test('proxy path encodes slug, node and file ids', () => {
  assert.equal(publicWikiImageProxyPath('team wiki', '12', '34'), '/api/public/wiki/team%20wiki/nodes/12/images/34')
})

test('resolves site file ids and rejects everything else', () => {
  assert.equal(wikiFileObjectId('/api/files/1024/content'), '1024')
  assert.equal(wikiFileObjectId('/proxy/api/files/7/content'), '7')
  assert.equal(wikiFileObjectId('https://api.example.com/api/files/7/content'), '7')
  assert.equal(wikiFileObjectId('/api/files/7/content?t=1#x'), '7')
  assert.equal(wikiFileObjectId('/api/files/7/content/thumb'), null)
  assert.equal(wikiFileObjectId('https://cdn.example.com/a.png'), null)
  assert.equal(wikiFileObjectId('/uploads/a.png'), null)
  assert.equal(wikiFileObjectId(''), null)
  assert.equal(wikiFileObjectId(undefined), null)
})

test('rewrites markdown image urls to the published proxy and keeps alt with title', () => {
  const markdown = '前言\n\n![架构图](/api/files/1024/content "发布图")\n\n后记'
  assert.equal(
    rewritePublishedWikiImages(markdown, 'docs', '88'),
    '前言\n\n![架构图](/api/public/wiki/docs/nodes/88/images/1024 "发布图")\n\n后记',
  )
})

test('rewrites dev proxy prefixes and absolute origins, dropping query and hash', () => {
  assert.equal(
    rewritePublishedWikiImages('![](/proxy/api/files/9/content)', 'docs', '1'),
    '![](/api/public/wiki/docs/nodes/1/images/9)',
  )
  assert.equal(
    rewritePublishedWikiImages('![](https://api.example.com/api/files/9/content?t=1)', 'docs', '1'),
    '![](/api/public/wiki/docs/nodes/1/images/9)',
  )
})

test('rewrites html img src inside articles', () => {
  assert.equal(
    rewritePublishedWikiImages('<img src="/api/files/5/content" alt="图" />', 'docs', '2'),
    '<img src="/api/public/wiki/docs/nodes/2/images/5" alt="图" />',
  )
})

test('leaves links, thumbnails, external images and plain text untouched', () => {
  const markdown = [
    '[下载附件](/api/files/9/content)',
    '![](/api/files/9/content/thumb)',
    '![](https://cdn.example.com/a.png)',
    '正文提到 /api/files/9/content 但不在图片位置',
  ].join('\n')
  assert.equal(rewritePublishedWikiImages(markdown, 'docs', '1'), markdown)
})

test('never rewrites without a published node anchor', () => {
  const markdown = '![图](/api/files/9/content)'
  assert.equal(rewritePublishedWikiImages(markdown, '', '1'), markdown)
  assert.equal(rewritePublishedWikiImages(markdown, 'docs', ''), markdown)
  assert.equal(publishedWikiImageUrl('/api/files/9/content', '', '1'), '/api/files/9/content')
  assert.equal(publishedWikiImageUrl('/api/files/9/content', 'docs', ''), '/api/files/9/content')
})

// —— 结构性回归：公开页接线只走已发布节点锚点，评论区只认服务端能力声明 ——

test('article body rewrites through the published proxy; raw documents stay untouched', () => {
  const source = wikiFile('index.vue')
  assert.match(source, /rewritePublishedWikiImages\(wikilinksToMarkdown\([^)]*\), spaceSlug\.value, active\.value\?\.id \?\? ''\)/)
  // 原文档没有已发布节点锚点，正文图片保持原样，DTO 图片列表不渲染
  assert.match(source, /const documentMarkdown = computed\(\(\) => rewriteApiFileUrls\(documentDetail\.value\?\.content \?\? ''\)\)/)
  assert.doesNotMatch(source, /documentDetail\.value\?\.images/)
})

test('comments panel uses the local adapter and gates on the server capability', () => {
  const source = wikiFile('WikiPublicComments.vue')
  const controller = wikiFile('use-wiki-public-comments.ts')
  // 面板与正文引用线程共用 useWikiPublicComments 持有的数据；共享 platform-wiki 模块不参与评论契约
  assert.match(source, /from '\.\/use-wiki-public-comments'/)
  assert.doesNotMatch(source, /from '@\/api\/modules\/platform-wiki'/)
  assert.match(controller, /from '\.\/wiki-public-api'/)
  assert.doesNotMatch(controller, /from '@\/api\/modules\/platform-wiki'/)
  // 只认服务端 commentEnabled；拿不到能力声明不展示评论区
  assert.match(controller, /commentEnabled\.value = profile\?\.commentEnabled === true/)
  assert.match(source, /v-if="commentEnabled"/)
  // 匿名发表未开放：输入框只在登录态渲染，写操作交服务端再校验
  assert.match(source, /v-if="isLogin"/)
  assert.match(controller, /createPublicWikiComment/)
  // 待审核评论不得伪装成已发布列表项
  assert.doesNotMatch(controller, /comments\.value\s*\.\s*(?:unshift|push|splice)/)
  assert.match(source, /待审核/)
})
