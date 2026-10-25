import type { WikiNode } from '@/api/modules/platform-wiki'
import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { groupWikiNavTree } from './wiki-nav-tree'

function node(overrides: Partial<WikiNode> & { id: string }): WikiNode {
  return {
    title: overrides.id,
    slug: overrides.id,
    sort: 0,
    nodeType: 'PAGE',
    indexStatus: 'INDEXED',
    children: [],
    ...overrides,
  }
}

describe('wiki nav tree grouping', () => {
  it('pulls source mirrors out of the tree and unifies generated pages under the LLM folder', () => {
    const tree = [
      node({ id: 'mirror-1', title: '服务器进入教程', path: 'source-348063099959513088' }),
      node({ id: 'mirror-2', title: 'YMCL使用教程', path: 'source-363701152480628736' }),
      node({
        id: 'llm-folder',
        title: 'LLM 生成',
        nodeType: 'DIRECTORY',
        children: [node({ id: 'gen-1', title: 'YMCL', path: 'llm-gen/ymcl' })],
      }),
      node({ id: 'page-index', title: '内容目录', path: 'index' }),
      node({ id: 'page-overview', title: '全局概要', path: 'overview' }),
    ]
    const { group, rest, mirrors } = groupWikiNavTree(tree)
    assert.deepEqual(mirrors.map(node => node.id), ['mirror-1', 'mirror-2'])
    assert.equal(rest.length, 0)
    assert.equal(group.title, 'LLM 生成')
    assert.deepEqual((group.children || []).map(node => node.title), ['YMCL', '内容目录', '全局概要'])
  })

  it('creates the LLM group when the folder does not exist yet', () => {
    const tree = [
      node({ id: 'mirror-1', title: '服务器进入教程', path: 'source-1' }),
      node({ id: 'page-1', title: '皮肤站', path: 'page-1' }),
    ]
    const { group, rest, mirrors } = groupWikiNavTree(tree)
    assert.equal(mirrors.length, 1)
    assert.equal(rest.length, 0)
    assert.equal(group.nodeType, 'DIRECTORY')
    assert.deepEqual((group.children || []).map(node => node.title), ['皮肤站'])
  })

  it('keeps unrelated folders and passes through a tree without mirrors', () => {
    const folder = node({
      id: 'other-folder',
      title: '其他',
      nodeType: 'DIRECTORY',
      children: [node({ id: 'child-1', title: '子页', path: 'other/child' })],
    })
    const tree = [node({ id: 'page-1', title: '页面', path: 'page-1' }), folder]
    const { group, rest, mirrors } = groupWikiNavTree(tree)
    assert.equal(mirrors.length, 0)
    assert.deepEqual(rest, [folder])
    assert.equal(group.title, 'LLM 生成')
    assert.deepEqual((group.children || []).map(node => node.title), ['页面'])
  })

  it('returns the first folder untouched when the tree has only folders', () => {
    const folder = node({
      id: 'only-folder',
      title: 'LLM 生成',
      nodeType: 'DIRECTORY',
      children: [node({ id: 'child-1', title: '子页', path: 'llm-gen/child' })],
    })
    const { group, rest, mirrors } = groupWikiNavTree([folder])
    assert.equal(mirrors.length, 0)
    assert.deepEqual(rest, [])
    assert.equal(group.id, 'only-folder')
  })

  it('survives an empty tree while data is loading', () => {
    const { group, rest, mirrors } = groupWikiNavTree([])
    assert.deepEqual(rest, [])
    assert.deepEqual(mirrors, [])
    assert.equal(group.nodeType, 'DIRECTORY')
    assert.deepEqual(group.children, [])
  })
})
