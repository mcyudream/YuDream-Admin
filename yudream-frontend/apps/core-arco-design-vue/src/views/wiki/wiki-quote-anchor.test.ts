/* eslint-disable test/no-import-node-test */
import type { WikiAnchorBlock, WikiQuoteAnchor, WikiQuoteAnchorCaptureInput } from './wiki-quote-anchor'
import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  buildWikiQuoteAnchor,
  classifyWikiQuoteAnchor,
  findWikiQuoteAnchorRanges,
  isWikiQuoteAnchorScopeChanged,
  normalizeWikiVisibleText,
  resolveWikiQuoteAnchorRange,
  WIKI_QUOTE_ANCHOR_LIMITS,
} from './wiki-quote-anchor'

// 文本锚点只有数据模型与纯判别，不碰 DOM；用例锁定捕获边界、消歧顺序与四态语义。

type CaptureExtra = Partial<WikiQuoteAnchorCaptureInput> & { occurrence?: number }

/** 从块文本里按第 occurrence 次出现的选区捕获锚点（偏移按归一化文本坐标） */
function capture(blockText: string, selected: string, extra: CaptureExtra = {}) {
  const { occurrence = 0, ...input } = extra
  const text = normalizeWikiVisibleText(blockText)
  let start = -1
  for (let index = 0; index <= occurrence; index++) {
    start = text.indexOf(selected, start + 1)
  }
  assert.notEqual(start, -1, `选区「${selected}」未命中块文本`)
  return buildWikiQuoteAnchor({ blockText, start, end: start + selected.length, ...input })
}

function mustCapture(blockText: string, selected: string, extra: CaptureExtra = {}): WikiQuoteAnchor {
  const anchor = capture(blockText, selected, extra)
  assert.ok(anchor, '锚点捕获不应为空')
  return anchor
}

test('normalize folds whitespace runs and trims both edges', () => {
  assert.equal(normalizeWikiVisibleText('  插件\t化\n\n后台  '), '插件 化 后台')
  assert.equal(normalizeWikiVisibleText('a\u00A0\u00A0b\u3000c'), 'a b c')
  assert.equal(normalizeWikiVisibleText('第一段\r\n\r\n第二段'), '第一段 第二段')
  assert.equal(normalizeWikiVisibleText(''), '')
  assert.equal(normalizeWikiVisibleText(undefined), '')
  assert.equal(normalizeWikiVisibleText(null), '')
  const once = normalizeWikiVisibleText('  a \t b ')
  assert.equal(normalizeWikiVisibleText(once), once)
})

test('capture records exact with bounded context, block hints and offsets', () => {
  const paragraph = '先把 数据库 连接池 调大，再观察 数据库 慢查询。'
  const anchor = mustCapture(paragraph, '连接池', { blockOrdinal: 4, blockType: 'paragraph' })
  assert.equal(anchor.exact, '连接池')
  assert.equal(anchor.prefix, '先把 数据库')
  assert.equal(anchor.suffix, '调大，再观察 数据库 慢查询。')
  assert.equal(anchor.blockOrdinal, 4)
  assert.equal(anchor.blockType, 'paragraph')
  assert.equal(anchor.startOffset, paragraph.indexOf('连接池'))
  assert.equal(anchor.endOffset, paragraph.indexOf('连接池') + 3)
})

test('capture normalizes raw block text before slicing', () => {
  const raw = '先把\t数据库\n连接池 调大'
  const normalized = normalizeWikiVisibleText(raw)
  const anchor = mustCapture(raw, '数据库 连接池')
  assert.equal(anchor.exact, '数据库 连接池')
  assert.equal(anchor.prefix, '先把')
  assert.equal(anchor.suffix, '调大')
  assert.equal(anchor.startOffset, normalized.indexOf('数据库 连接池'))
})

test('capture clamps exact and context to the model bounds', () => {
  const { maxContext, maxExact } = WIKI_QUOTE_ANCHOR_LIMITS
  const head = '前'.repeat(maxContext + 20)
  const tail = '后'.repeat(maxContext + 20)
  const bounded = mustCapture(`${head}引用片段${tail}`, '引用片段')
  assert.equal(bounded.prefix, '前'.repeat(maxContext))
  assert.equal(bounded.suffix, '后'.repeat(maxContext))

  const long = '引'.repeat(maxExact + 30)
  const clipped = mustCapture(long, long)
  assert.equal(Array.from(clipped.exact).length, maxExact)
  assert.equal(clipped.startOffset, 0)
  assert.equal(clipped.endOffset, long.length)

  // 截断不拆代理对
  const emoji = '👍'.repeat(maxExact + 3)
  const emojiAnchor = mustCapture(emoji, emoji)
  assert.equal(emojiAnchor.exact, '👍'.repeat(maxExact))
})

test('capture rejects selections below minExact and clamps stray offsets', () => {
  assert.equal(WIKI_QUOTE_ANCHOR_LIMITS.minExact, 2)
  assert.equal(capture('引用', '引'), null)
  assert.equal(buildWikiQuoteAnchor({ blockText: '引用', start: 3, end: 3 }), null)
  assert.equal(buildWikiQuoteAnchor({ blockText: '', start: 0, end: 0 }), null)
  assert.equal(buildWikiQuoteAnchor({ blockText: '   ', start: 0, end: 3 }), null)
  assert.equal(buildWikiQuoteAnchor(undefined), null)
  assert.equal(buildWikiQuoteAnchor(null), null)
  // 越界钳制到块尾后不足最小长度 → 丢弃
  assert.equal(buildWikiQuoteAnchor({ blockText: 'abcd', start: 3, end: 99 }), null)
  assert.equal(buildWikiQuoteAnchor({ blockText: 'abcd', start: 2, end: 99 })?.exact, 'cd')
  // 反序选区按区间取
  assert.equal(buildWikiQuoteAnchor({ blockText: 'abcd', start: 4, end: 1 })?.exact, 'bcd')
})

test('repeated quote is disambiguated by surrounding context', () => {
  const paragraph = '数据库 连接池 已调到 200；数据库 慢查询 仍然偏多。'
  const blocks: WikiAnchorBlock[] = [{ ordinal: 0, type: 'paragraph', text: paragraph }]
  const first = mustCapture(paragraph, '数据库', { blockOrdinal: 0, blockType: 'paragraph' })
  const second = mustCapture(paragraph, '数据库', { blockOrdinal: 0, blockType: 'paragraph', occurrence: 1 })
  assert.equal(first.prefix, '')
  assert.equal(second.prefix, '数据库 连接池 已调到 200；')
  assert.equal(findWikiQuoteAnchorRanges(second, blocks).length, 2)

  const resolvedSecond = resolveWikiQuoteAnchorRange(second, blocks)
  assert.equal(resolvedSecond?.start, paragraph.indexOf('数据库', 1))
  assert.equal(resolvedSecond?.matchedBy, 'context')
  assert.equal(resolveWikiQuoteAnchorRange(first, blocks)?.start, 0)

  const classification = classifyWikiQuoteAnchor(second, blocks)
  assert.equal(classification.status, 'active')
  assert.equal(classification.inPlace, true)
  assert.equal(classification.candidates.length, 1)
})

test('table cell hints pick one cell among identical quote text', () => {
  const blocks: WikiAnchorBlock[] = [
    { ordinal: 0, type: 'tableCell', tableRow: 0, tableCell: 1, text: '超时 30 秒，单位秒' },
    { ordinal: 1, type: 'tableCell', tableRow: 1, tableCell: 1, text: '超时 30 秒，单位秒' },
  ]
  const anchor = mustCapture(blocks[1].text, '超时 30 秒', { blockOrdinal: 1, blockType: 'tableCell', tableRow: 1, tableCell: 1 })
  const match = resolveWikiQuoteAnchorRange(anchor, blocks)
  assert.equal(match?.blockOrdinal, 1)
  assert.equal(match?.tableRow, 1)
  assert.equal(match?.tableCell, 1)
  // 两个单元格的上下文完全相同，只能靠块/表格提示定到第二个
  assert.equal(match?.matchedBy, 'block-hint')
  assert.equal(classifyWikiQuoteAnchor(anchor, blocks).status, 'active')

  const byOrdinal = resolveWikiQuoteAnchorRange({ exact: '超时 30 秒', blockOrdinal: 0 }, blocks)
  assert.equal(byOrdinal?.blockOrdinal, 0)
  assert.equal(byOrdinal?.matchedBy, 'block-hint')
})

test('identical quotes with identical context stay ambiguous, never guessed', () => {
  const blocks: WikiAnchorBlock[] = [{ ordinal: 0, type: 'paragraph', text: '重试 3 次；重试 3 次。' }]
  const anchor: WikiQuoteAnchor = { exact: '重试 3 次', prefix: '', suffix: '' }
  const classification = classifyWikiQuoteAnchor(anchor, blocks)
  assert.equal(classification.status, 'ambiguous')
  assert.equal(classification.candidates.length, 2)
  assert.equal(classification.match, undefined)
  assert.equal(classification.inPlace, false)
  assert.equal(resolveWikiQuoteAnchorRange(anchor, blocks), null)

  // 捕获偏移只用于消歧，不直接构成命中
  const secondStart = blocks[0].text.indexOf('重试 3 次', 1)
  const withOffset: WikiQuoteAnchor = { exact: '重试 3 次', prefix: '', suffix: '', startOffset: secondStart }
  const resolved = resolveWikiQuoteAnchorRange(withOffset, blocks)
  assert.equal(resolved?.start, secondStart)
  assert.equal(resolved?.matchedBy, 'position')
})

test('missing quote is orphaned and the offset hint cannot invent a match', () => {
  const blocks: WikiAnchorBlock[] = [{ ordinal: 0, type: 'paragraph', text: '正文已经改写成完全不同的内容。' }]
  const anchor: WikiQuoteAnchor = {
    exact: '旧版本的引用',
    prefix: '前文',
    suffix: '后文',
    blockOrdinal: 0,
    blockType: 'paragraph',
    startOffset: 0,
    endOffset: 6,
  }
  const classification = classifyWikiQuoteAnchor(anchor, blocks)
  assert.equal(classification.status, 'orphaned')
  assert.equal(classification.candidates.length, 0)
  assert.equal(classification.match, undefined)
  assert.equal(findWikiQuoteAnchorRanges(anchor, blocks).length, 0)
  assert.equal(resolveWikiQuoteAnchorRange(anchor, blocks), null)
  assert.equal(classifyWikiQuoteAnchor(null, blocks).status, 'orphaned')
  assert.equal(classifyWikiQuoteAnchor({ exact: '   ' }, blocks).status, 'orphaned')
})

test('quote that survived a block shift is reanchorable, not active', () => {
  const target = '缓存 命中率 需要 关注。'
  const anchor = mustCapture(target, '命中率', { blockOrdinal: 1, blockType: 'paragraph' })
  const inserted: WikiAnchorBlock[] = [
    { ordinal: 0, type: 'paragraph', text: '前言。' },
    { ordinal: 1, type: 'paragraph', text: '新插入的一段说明。' },
    { ordinal: 2, type: 'paragraph', text: target },
  ]
  const classification = classifyWikiQuoteAnchor(anchor, inserted)
  assert.equal(classification.status, 'reanchorable')
  assert.equal(classification.inPlace, false)
  assert.equal(classification.match?.blockOrdinal, 2)
  assert.equal(classification.match?.start, target.indexOf('命中率'))
})

test('version identity is supplied separately and only shifts re-anchoring', () => {
  const paragraph = '缓存 命中率 需要 关注。'
  const blocks: WikiAnchorBlock[] = [{ ordinal: 0, type: 'paragraph', text: paragraph }]
  const anchor = mustCapture(paragraph, '命中率', { blockOrdinal: 0, blockType: 'paragraph' })

  assert.equal(classifyWikiQuoteAnchor(anchor, blocks).status, 'active')
  // contentHash 相同 ⇒ 即使 versionId/revision 前进，仍是同一段文本
  assert.equal(classifyWikiQuoteAnchor(anchor, blocks, {
    capturedScope: { versionId: '101', revision: '7', contentHash: 'sha-a' },
    currentScope: { versionId: '102', revision: '8', contentHash: 'sha-a' },
  }).status, 'active')
  // 正文内容变化 ⇒ 即使还能唯一命中也要重锚
  assert.equal(classifyWikiQuoteAnchor(anchor, blocks, {
    capturedScope: { versionId: '101', revision: '7', contentHash: 'sha-a' },
    currentScope: { versionId: '101', revision: '9', contentHash: 'sha-b' },
  }).status, 'reanchorable')
  assert.equal(isWikiQuoteAnchorScopeChanged(undefined, { contentHash: 'sha-b' }), false)
  assert.equal(isWikiQuoteAnchorScopeChanged({ revision: '7' }, { revision: '7' }), false)
  assert.equal(isWikiQuoteAnchorScopeChanged({ versionId: '1', revision: '2' }, { versionId: '1', revision: '3' }), true)
})

test('matching normalizes block text with the same rules as capture', () => {
  const anchor = mustCapture('接口\t说明：\n\n超时 30 秒', '超时 30 秒')
  const blocks: WikiAnchorBlock[] = [{ ordinal: 0, type: 'paragraph', text: '接口 说明： 超时\t30 秒' }]
  const classification = classifyWikiQuoteAnchor(anchor, blocks)
  assert.equal(classification.status, 'active')
  assert.equal(classification.match?.start, 7)
})

test('block ordinal falls back to array index and ranges keep reading order', () => {
  const blocks: WikiAnchorBlock[] = [
    { type: 'paragraph', text: '第一段 引用词 结束' },
    { type: 'paragraph', text: '第二段 引用词 结束' },
  ]
  const ranges = findWikiQuoteAnchorRanges({ exact: '引用词' }, blocks)
  assert.deepEqual(ranges.map(range => [range.blockOrdinal, range.start, range.matchedBy]), [
    [0, 4, 'exact'],
    [1, 4, 'exact'],
  ])
  assert.deepEqual(findWikiQuoteAnchorRanges(undefined, blocks), [])
})
