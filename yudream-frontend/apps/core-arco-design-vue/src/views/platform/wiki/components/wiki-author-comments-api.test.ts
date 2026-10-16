/* eslint-disable test/no-import-node-test */
import type { WikiAnchorBlock, WikiQuoteAnchorCaptureInput } from '../../../wiki/wiki-quote-anchor'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import { buildWikiQuoteAnchor, classifyWikiQuoteAnchor, normalizeWikiVisibleText } from '../../../wiki/wiki-quote-anchor'
import {
  toWikiAuthorCommentAnchor,
  wikiAnchorFromAuthorComment,
  wikiAnchorScopeFromAuthorComment,
  wikiCommentBlockTypeFromServer,
  wikiCommentBlockTypeToServer,
  wikiCommentContextAdjacent,
  wikiNormalizedOffsetAt,
} from './wiki-author-comments-api'

function capture(blockText: string, selected: string, extra: Partial<WikiQuoteAnchorCaptureInput> = {}) {
  const text = normalizeWikiVisibleText(blockText)
  const start = text.indexOf(selected)
  assert.notEqual(start, -1, `选区「${selected}」未命中块文本`)
  return buildWikiQuoteAnchor({ blockText, start, end: start + selected.length, ...extra })
}

test('normalized offsets follow the same whitespace folding as the shared model', () => {
  const raw = '  数据库  连接池\t调大 '
  const normalized = normalizeWikiVisibleText(raw)
  assert.equal(normalized, '数据库 连接池 调大')
  assert.equal(wikiNormalizedOffsetAt(raw, 0), 0)
  assert.equal(wikiNormalizedOffsetAt(raw, 2), 0)
  assert.equal(wikiNormalizedOffsetAt(raw, 5), 3)
  assert.equal(wikiNormalizedOffsetAt(raw, 6), 3)
  assert.equal(wikiNormalizedOffsetAt(raw, 7), 4)
  assert.equal(wikiNormalizedOffsetAt(raw, 8), 5)
  assert.equal(wikiNormalizedOffsetAt(raw, 11), 8)
  assert.equal(wikiNormalizedOffsetAt(raw, raw.length), normalized.length)
  assert.equal(wikiNormalizedOffsetAt(raw, -5), 0)
  assert.equal(wikiNormalizedOffsetAt(raw, 999), normalized.length)
  assert.equal(wikiNormalizedOffsetAt('', 3), 0)
})

test('context is only reported as adjacent when no whitespace touches the quote', () => {
  const chinese = capture('欢迎来到快速开始页面。', '快速开始')
  assert.ok(chinese)
  assert.equal(wikiCommentContextAdjacent(chinese, '欢迎来到快速开始页面。', 'before'), true)
  assert.equal(wikiCommentContextAdjacent(chinese, '欢迎来到快速开始页面。', 'after'), true)

  const spaced = capture('welcome to quick start', 'quick')
  assert.ok(spaced)
  assert.equal(wikiCommentContextAdjacent(spaced, 'welcome to quick start', 'before'), false)
  assert.equal(wikiCommentContextAdjacent(spaced, 'welcome to quick start', 'after'), false)

  const head = capture('开头引用', '开头')
  assert.ok(head)
  assert.equal(wikiCommentContextAdjacent(head, '开头引用', 'before'), false)
  assert.equal(wikiCommentContextAdjacent(head, '开头引用', 'after'), true)
})

test('create payload keeps only quote text and flat location hints, never version identity', () => {
  const blockText = '欢迎来到快速开始页面。'
  const anchor = capture(blockText, '快速开始', { blockOrdinal: 2, blockType: 'paragraph' })
  assert.ok(anchor)
  assert.deepEqual(toWikiAuthorCommentAnchor(anchor, blockText), {
    exact: '快速开始',
    prefix: '欢迎来到',
    suffix: '页面。',
    blockType: 'paragraph',
    blockOrdinal: 2,
    startOffset: 4,
    endOffset: 8,
  })
})

test('create payload drops context that cannot match server-side adjacency', () => {
  const blockText = 'welcome to quick start'
  const anchor = capture(blockText, 'quick', { blockOrdinal: 0, blockType: 'paragraph' })
  assert.ok(anchor)
  const payload = toWikiAuthorCommentAnchor(anchor, blockText)
  assert.deepEqual(payload, {
    exact: 'quick',
    blockType: 'paragraph',
    blockOrdinal: 0,
    startOffset: 11,
    endOffset: 16,
  })
  assert.deepEqual(Object.keys(payload ?? {}).sort(), ['blockOrdinal', 'blockType', 'endOffset', 'exact', 'startOffset'])
})

test('create payload maps table cells and refuses invalid block hints', () => {
  const blockText = '连接池调大后观察慢查询'
  const cell = capture(blockText, '连接池', { blockOrdinal: 3, blockType: 'tableCell', tableRow: 1, tableCell: 2 })
  assert.ok(cell)
  assert.deepEqual(toWikiAuthorCommentAnchor(cell, blockText), {
    exact: '连接池',
    suffix: '调大后观察慢查询',
    blockType: 'tableCell',
    blockOrdinal: 3,
    tableRow: 1,
    tableCell: 2,
    startOffset: 0,
    endOffset: 3,
  })

  const broken = capture(blockText, '连接池', { blockOrdinal: 3, blockType: 'tableCell' })
  assert.ok(broken)
  const brokenPayload = toWikiAuthorCommentAnchor(broken, blockText)
  assert.equal(brokenPayload?.blockType, undefined)
  assert.equal(brokenPayload?.blockOrdinal, undefined)
  assert.equal(brokenPayload?.tableRow, undefined)
  assert.equal(brokenPayload?.tableCell, undefined)
  assert.equal(brokenPayload?.startOffset, 0)

  const paragraph = capture(blockText, '连接池', { blockOrdinal: 0, blockType: 'paragraph', tableRow: 1, tableCell: 2 })
  assert.ok(paragraph)
  assert.deepEqual(toWikiAuthorCommentAnchor(paragraph, blockText)?.blockType, 'paragraph')
  assert.equal(toWikiAuthorCommentAnchor(paragraph, blockText)?.tableRow, undefined)

  const unknown = capture(blockText, '连接池', { blockOrdinal: 1, blockType: 'customBlock' })
  assert.ok(unknown)
  const unknownPayload = toWikiAuthorCommentAnchor(unknown, blockText)
  assert.equal(unknownPayload?.blockType, undefined)
  assert.equal(unknownPayload?.blockOrdinal, undefined)
  assert.equal(unknownPayload?.exact, '连接池')
})

test('create payload rejects empty selections', () => {
  assert.equal(toWikiAuthorCommentAnchor(null, '正文'), null)
  assert.equal(toWikiAuthorCommentAnchor(undefined, '正文'), null)
  assert.equal(toWikiAuthorCommentAnchor({ exact: '   ' }, '正文'), null)
})

test('server anchors map back into the shared locator model', () => {
  const restored = wikiAnchorFromAuthorComment({
    exact: '  快速开始 ',
    prefix: '欢迎来到',
    suffix: '页面。',
    blockType: 'listItem',
    blockOrdinal: 5,
    startOffset: 4,
    endOffset: 8,
  })
  assert.deepEqual(restored, {
    exact: '快速开始',
    prefix: '欢迎来到',
    suffix: '页面。',
    blockOrdinal: 5,
    blockType: 'listItem',
    tableRow: undefined,
    tableCell: undefined,
    startOffset: 4,
    endOffset: 8,
  })
  const unknown = wikiAnchorFromAuthorComment({ exact: '引用', blockType: 'mystery', blockOrdinal: 1 })
  assert.equal(unknown?.blockType, undefined)
  assert.equal(unknown?.blockOrdinal, 1)
  assert.equal(wikiAnchorFromAuthorComment(null), null)
  assert.equal(wikiAnchorFromAuthorComment({ exact: ' ' }), null)
})

test('server scope keeps version identity in string form', () => {
  const anchor = { exact: '引用' }
  assert.deepEqual(wikiAnchorScopeFromAuthorComment({ id: '1', content: '评论', anchor, versionId: '200', revision: '3', contentHash: 'h' }), {
    versionId: '200',
    revision: '3',
    contentHash: 'h',
  })
  assert.equal(wikiAnchorScopeFromAuthorComment({ id: '1', content: '评论', anchor }), null)
  assert.equal(wikiAnchorScopeFromAuthorComment(null), null)
})

test('block type vocabulary maps both directions and ignores unknown values', () => {
  assert.equal(wikiCommentBlockTypeToServer('paragraph'), 'paragraph')
  assert.equal(wikiCommentBlockTypeToServer('blockquote'), 'blockquote')
  assert.equal(wikiCommentBlockTypeToServer('tableCell'), 'tableCell')
  assert.equal(wikiCommentBlockTypeToServer('mermaid'), undefined)
  assert.equal(wikiCommentBlockTypeToServer(undefined), undefined)
  assert.equal(wikiCommentBlockTypeFromServer('codeBlock'), 'codeBlock')
  assert.equal(wikiCommentBlockTypeFromServer('listItem'), 'listItem')
  assert.equal(wikiCommentBlockTypeFromServer('MYSTERY'), undefined)
  assert.equal(wikiCommentBlockTypeFromServer(null), undefined)
})

test('capture and server anchors classify against the same preview blocks', () => {
  const blocks: WikiAnchorBlock[] = [
    { ordinal: 0, type: 'heading', text: '安装手册' },
    { ordinal: 1, type: 'paragraph', text: '第一步：安装依赖。第二步：安装插件。' },
  ]
  const anchor = capture(blocks[1].text, '安装依赖', { blockOrdinal: 1, blockType: 'paragraph' })
  assert.ok(anchor)
  const payload = toWikiAuthorCommentAnchor(anchor, blocks[1].text)
  assert.ok(payload)
  const stamped = { id: '1', content: '评论', anchor: payload, versionId: '200', revision: '3', contentHash: 'hash-1', anchorState: 'active' as const }
  const restored = wikiAnchorFromAuthorComment(stamped.anchor)
  assert.ok(restored)
  const classification = classifyWikiQuoteAnchor(restored, blocks, {
    capturedScope: wikiAnchorScopeFromAuthorComment(stamped) ?? undefined,
    currentScope: { versionId: '200', revision: '3', contentHash: 'hash-1' },
  })
  assert.equal(classification.status, 'active')
  assert.equal(classification.match?.blockOrdinal, 1)

  const orphaned = classifyWikiQuoteAnchor(restored, [{ ordinal: 0, type: 'paragraph', text: '完全无关的正文' }])
  assert.equal(orphaned.status, 'orphaned')
  assert.equal(orphaned.match, undefined)
  const ambiguous = classifyWikiQuoteAnchor(restored, [
    { ordinal: 0, type: 'paragraph', text: '安装依赖和安装依赖都需要安装依赖' },
  ])
  assert.equal(ambiguous.status, 'ambiguous')
  assert.equal(ambiguous.match, undefined)
})
