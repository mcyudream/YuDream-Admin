/* eslint-disable test/no-import-node-test */
import type { WikiNode } from '@/api/modules/platform-wiki'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createWikiDocuments, wikiDocumentKey } from './wiki-documents'

function page(id: string, spaceId = '1', title = `页面 ${id}`): WikiNode {
  return {
    id,
    spaceId,
    title,
    slug: `page-${id}`,
    nodeType: 'PAGE',
    sort: 0,
    body: `正文 ${id}`,
  } as unknown as WikiNode
}

test('打开多页各自持有草稿，切换标签不丢未保存内容', () => {
  const documents = createWikiDocuments()
  documents.open('1', page('a'))
  documents.open('1', page('b'))
  assert.equal(documents.documents.value.length, 2)
  assert.equal(documents.activeKey.value, wikiDocumentKey('1', 'b'))

  // 在 b 上改草稿，再切回 a：b 的草稿必须还在
  const b = documents.documents.value.find(doc => doc.nodeId === 'b')!
  b.editor.state.fields.body = '改了 b'
  assert.equal(documents.anyDirty.value, true)

  documents.open('1', page('a'))
  assert.equal(documents.activeKey.value, wikiDocumentKey('1', 'a'))
  assert.equal(b.editor.state.fields.body, '改了 b')
})

test('重复打开同一页面只激活标签，且不覆盖未保存草稿', () => {
  const documents = createWikiDocuments()
  documents.open('1', page('a'))
  const doc = documents.documents.value[0]!
  doc.editor.state.fields.body = '本地未保存'
  documents.open('1', page('b'))

  documents.open('1', page('a'))
  assert.equal(documents.documents.value.length, 2)
  assert.equal(doc.editor.state.fields.body, '本地未保存')
})

test('关闭标签后优先激活右邻，最后一个关闭后回到空态', () => {
  const documents = createWikiDocuments()
  documents.open('1', page('a'))
  documents.open('1', page('b'))
  documents.open('1', page('c'))

  documents.close(wikiDocumentKey('1', 'b'))
  assert.equal(documents.activeKey.value, wikiDocumentKey('1', 'c'))

  documents.close(wikiDocumentKey('1', 'c'))
  documents.close(wikiDocumentKey('1', 'a'))
  assert.equal(documents.activeKey.value, '')
  assert.equal(documents.documents.value.length, 0)
  assert.equal(documents.anyDirty.value, false)
})

test('目录刷新同步节点快照但不覆盖草稿', () => {
  const documents = createWikiDocuments()
  documents.open('1', page('a'))
  const doc = documents.documents.value[0]!
  doc.editor.state.fields.body = '草稿'
  doc.editor.state.fields.title = '改过的标题'

  // 服务端换了标题与正文：正文草稿保持，节点快照更新（publishedVersionId 等元数据来源）
  documents.syncNodes('1', [{ ...page('a'), title: '服务端标题', body: '服务端正文', publishedVersionId: '99' } as WikiNode])
  assert.equal(doc.editor.state.fields.body, '草稿')
  assert.equal(doc.editor.state.fields.title, '改过的标题')
  assert.equal(doc.node.publishedVersionId, '99')
})

test('切换知识库只保留该库标签，删除节点关闭对应标签', () => {
  const documents = createWikiDocuments()
  documents.open('1', page('a'))
  documents.open('2', page('b', '2'))

  documents.keepSpace('2')
  assert.equal(documents.documents.value.length, 1)
  assert.equal(documents.activeKey.value, wikiDocumentKey('2', 'b'))

  documents.closeByNode('b')
  assert.equal(documents.documents.value.length, 0)
})

test('文档键按知识库隔离，同 ID 页面不会互相覆盖', () => {
  const documents = createWikiDocuments()
  documents.open('1', page('same'))
  documents.open('2', page('same', '2'))
  assert.equal(documents.documents.value.length, 2)
  assert.notEqual(documents.documents.value[0]!.key, documents.documents.value[1]!.key)
})
