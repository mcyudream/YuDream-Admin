/* eslint-disable test/no-import-node-test */
import type { WikiPublicComment } from './wiki-public-api'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { test } from 'node:test'
import {
  resolveWikiCommentAnchorState,
  wikiQuoteAnchorFromResponse,
  wikiQuoteAnchorPayload,
  wikiQuoteAnchorScopeFromResponse,
} from './wiki-comment-anchor'
import { buildWikiQuoteAnchor, isWikiQuoteAnchorScopeChanged } from './wiki-quote-anchor'

// 公开端文本锚点的线协议折算与 UI 接线。
// 线协议以后端 WikiCommentAnchorRequest / WikiCommentRes 为准：anchor 是扁平字段，
// versionId/revision/contentHash/anchorState 是它的兄弟字段；无锚点评论没有 PAGE 之类种类。
// 读源码的接线断言配合 vue-tsc/eslint 覆盖无法在单测里渲染的浮层与栏位切换。

function wikiFile(relativePath: string) {
  return readFileSync(join(import.meta.dirname, relativePath), 'utf8')
}

function anchorComment(overrides: Partial<WikiPublicComment> = {}): WikiPublicComment {
  return { id: '9001', content: '评论内容', anchor: { exact: '连接池' }, ...overrides }
}

test('quote payload submits the flat wire fields and never version identity', () => {
  const anchor = buildWikiQuoteAnchor({ blockText: '先把 连接池 调大', start: 3, end: 6, blockOrdinal: 4, blockType: 'paragraph' })
  assert.ok(anchor)
  const payload = wikiQuoteAnchorPayload(anchor)
  assert.ok(payload)
  assert.deepEqual(Object.keys(payload).sort(), ['blockOrdinal', 'blockType', 'endOffset', 'exact', 'prefix', 'startOffset', 'suffix'])
  assert.equal(payload.exact, '连接池')
  assert.equal(payload.prefix, '先把')
  assert.equal(payload.suffix, '调大')
  assert.equal(payload.blockOrdinal, 4)
  assert.equal(payload.blockType, 'paragraph')
  assert.equal(payload.startOffset, 3)
  assert.equal(payload.endOffset, 6)
  // 版本身份只在响应侧由服务端盖章，请求侧没有这些字段，也没有嵌套提示
  assert.equal('versionId' in payload, false)
  assert.equal('contentHash' in payload, false)
  assert.equal('blockHint' in payload, false)
  assert.equal('startHint' in payload, false)
  assert.equal(wikiQuoteAnchorPayload(undefined), undefined)
  assert.equal(wikiQuoteAnchorPayload({ exact: '  ' }), undefined)
})

test('table cell hints are only submitted when paired, other types never carry row/cell', () => {
  const paired = wikiQuoteAnchorPayload({ exact: '超时 30 秒', blockOrdinal: 1, blockType: 'tableCell', tableRow: 1, tableCell: 1 })
  assert.deepEqual(paired, { exact: '超时 30 秒', blockOrdinal: 1, blockType: 'tableCell', tableRow: 1, tableCell: 1 })
  // 表格行列缺失：整条块提示丢弃，绝不提交会被服务端拒绝的半截提示
  assert.deepEqual(wikiQuoteAnchorPayload({ exact: '超时', blockOrdinal: 1, blockType: 'tableCell' }), { exact: '超时' })
  assert.deepEqual(wikiQuoteAnchorPayload({ exact: '超时', blockOrdinal: 1, blockType: 'tableCell', tableRow: 0 }), { exact: '超时' })
  // 非表格类型不得携带行列
  assert.deepEqual(
    wikiQuoteAnchorPayload({ exact: '超时', blockOrdinal: 1, blockType: 'paragraph', tableRow: 0, tableCell: 0 }),
    { exact: '超时', blockOrdinal: 1, blockType: 'paragraph' },
  )
  // 块类型与序号必须成对
  assert.deepEqual(wikiQuoteAnchorPayload({ exact: '超时', blockOrdinal: 1 }), { exact: '超时' })
  // 起止偏移成对且有序才提交（服务端要求 start <= end）
  assert.deepEqual(
    wikiQuoteAnchorPayload({ exact: '超时', startOffset: 4, endOffset: 2 }),
    { exact: '超时' },
  )
})

test('response mapping keeps flat hints so repeated quotes stay disambiguated', () => {
  const flat = wikiQuoteAnchorFromResponse({ exact: '超时 30 秒', blockOrdinal: 1, blockType: 'tableCell', tableRow: 1, tableCell: 1, startOffset: 0, endOffset: 6 })
  assert.equal(flat?.blockOrdinal, 1)
  assert.equal(flat?.blockType, 'tableCell')
  assert.equal(flat?.tableRow, 1)
  assert.equal(flat?.tableCell, 1)
  assert.equal(flat?.startOffset, 0)
  assert.equal(flat?.endOffset, 6)
  // 无锚点/空引用（历史评论）不造锚点
  assert.equal(wikiQuoteAnchorFromResponse(null), null)
  assert.equal(wikiQuoteAnchorFromResponse(undefined), null)
  assert.equal(wikiQuoteAnchorFromResponse({ exact: '   ' }), null)
})

test('version identity and anchorState are read from the comment siblings', () => {
  const comment = anchorComment({ versionId: '101', revision: '7', contentHash: 'sha-a', anchorState: 'active' })
  assert.deepEqual(wikiQuoteAnchorScopeFromResponse(comment), { versionId: '101', revision: '7', contentHash: 'sha-a' })
  // 内容哈希一致（只重复发布）不算改版；正文改哈希才判改版
  assert.equal(isWikiQuoteAnchorScopeChanged(wikiQuoteAnchorScopeFromResponse(comment), { versionId: '102', revision: '8', contentHash: 'sha-a' }), false)
  assert.equal(isWikiQuoteAnchorScopeChanged(wikiQuoteAnchorScopeFromResponse(comment), { versionId: '101', revision: '9', contentHash: 'sha-b' }), true)
  // 无锚点评论没有版本身份，不臆断为改版
  assert.deepEqual(wikiQuoteAnchorScopeFromResponse({ id: '1', content: '整页评论' }), { versionId: undefined, revision: undefined, contentHash: undefined })
  assert.equal(isWikiQuoteAnchorScopeChanged(wikiQuoteAnchorScopeFromResponse({ id: '1', content: '整页评论' }), { versionId: '102' }), false)
})

test('state projection honours lowercase anchorState and never invents a page state', () => {
  // 无锚点评论（含历史评论）没有引用态：null，而不是 PAGE 之类的投影
  assert.equal(resolveWikiCommentAnchorState({ id: '1', content: '整页评论' }), null)
  assert.equal(resolveWikiCommentAnchorState({ id: '1', content: '整页评论', anchor: { exact: ' ' } }), null)
  assert.equal(resolveWikiCommentAnchorState(null), null)
  // 服务端小写状态直通
  assert.equal(resolveWikiCommentAnchorState(anchorComment({ anchorState: 'active' })), 'ACTIVE')
  assert.equal(resolveWikiCommentAnchorState(anchorComment({ anchorState: 'reanchorable' })), 'REANCHORABLE')
  assert.equal(resolveWikiCommentAnchorState(anchorComment({ anchorState: 'orphaned' })), 'ORPHANED')
  // 未知/缺失状态不视为可高亮
  assert.equal(resolveWikiCommentAnchorState(anchorComment({ anchorState: 'page' })), 'ORPHANED')
  assert.equal(resolveWikiCommentAnchorState(anchorComment()), 'ORPHANED')
  // 服务端 ORPHANED 永远压过本地命中
  assert.equal(
    resolveWikiCommentAnchorState(anchorComment({ anchorState: 'orphaned' }), { status: 'active', candidates: [], inPlace: true, scopeChanged: false }),
    'ORPHANED',
  )
  // 本地多处命中交人工，不猜
  assert.equal(
    resolveWikiCommentAnchorState(anchorComment({ anchorState: 'active' }), { status: 'ambiguous', candidates: [], inPlace: false, scopeChanged: false }),
    'AMBIGUOUS',
  )
  // 本地唯一命中 + 服务端改版 → 可重锚；服务端声明 active 而块已移动同样降级为可重锚
  assert.equal(
    resolveWikiCommentAnchorState(anchorComment({ anchorState: 'reanchorable' }), { status: 'active', candidates: [], inPlace: true, scopeChanged: true }),
    'REANCHORABLE',
  )
  assert.equal(
    resolveWikiCommentAnchorState(anchorComment({ anchorState: 'active' }), { status: 'reanchorable', candidates: [], inPlace: false, scopeChanged: false }),
    'REANCHORABLE',
  )
})

test('public api declares the flat anchor and sibling identity fields only', () => {
  const api = wikiFile('wiki-public-api.ts')
  for (const field of ['exact', 'prefix', 'suffix', 'blockOrdinal', 'blockType', 'tableRow', 'tableCell', 'startOffset', 'endOffset']) {
    assert.match(api, new RegExp(`\\b${field}\\??:`), `锚点线协议字段 ${field} 缺失`)
  }
  // 版本身份与状态与 anchor 平级；anchor 内不得再出现这些字段或状态枚举
  assert.match(api, /versionId\?: string/)
  assert.match(api, /revision\?: string/)
  assert.match(api, /contentHash\?: string/)
  assert.match(api, /anchorState\?: string/)
  assert.doesNotMatch(api, /blockHint\?:/)
  assert.doesNotMatch(api, /startHint\?:/)
  assert.doesNotMatch(api, /endHint\?:/)
  assert.doesNotMatch(api, /anchorState\?: 'ACTIVE'/)
  // 公开端只走本文件的局部适配层，不把评论契约塞回共享 platform-wiki 模块
  assert.doesNotMatch(api, /from '@\/api\/modules\/platform-wiki'/)
})

test('selection freezes before the toolbar entry and reuses the TOC observer', () => {
  const controller = wikiFile('use-wiki-quote-anchors.ts')
  // 冻结在 pointerup/keyup 完成，浮出入口只消费快照：点击按钮时浏览器选区可能已收起
  assert.match(controller, /window\.addEventListener\('pointerup', onPointerUp, true\)/)
  assert.match(controller, /window\.addEventListener\('keyup', onKeyUp, true\)/)
  assert.match(controller, /window\.addEventListener\('pointerdown', onPointerDown, true\)/)
  assert.match(controller, /WIKI_QUOTE_ENTRY_SELECTOR = '\[data-wiki-quote-entry\]'/)
  assert.match(controller, /event\.preventDefault\(\)/)
  // 不为正文再挂第二个 MutationObserver：重渲染统一由 TOC 观察者的 onRendered 回调 reapply
  assert.doesNotMatch(controller, /new MutationObserver/)
  assert.match(controller, /resolveWikiCommentAnchorState\(target\.comment, outcome\.classifications/)
  const page = wikiFile('index.vue')
  assert.match(page, /useWikiArticleToc\(articleRef, \{ onRendered: reapply \}\)/)
  assert.match(page, /data-wiki-quote-entry/)
  assert.match(page, /comment => !comment\.parentId && comment\.anchor\?\.exact/)
  // 选区问题（跨块/过短）明确提示，不静默截断
  assert.match(page, /selectionNotice/)
})

test('highlight decoration stays in the reading layer and reapplies idempotently', () => {
  const dom = wikiFile('wiki-quote-dom.ts')
  assert.match(dom, /range\.extractContents\(\)/)
  assert.match(dom, /range\.insertNode\(mark\)/)
  assert.doesNotMatch(dom, /innerHTML\s*=/)
  // 签名一致时跳过一次 DOM 重写，避免与观察者互相回写
  assert.match(dom, /WIKI_QUOTE_MARK_SIGNATURE_ATTRIBUTE/)
  assert.match(dom, /if \(marks\.length === resolved\.length && root\.getAttribute\(WIKI_QUOTE_MARK_SIGNATURE_ATTRIBUTE\) === signature\)/)
  // 同一块内才捕获：跨块与表格单元格外的选区一律拒绝
  assert.match(dom, /return \{ error: 'cross-block' \}/)
})

test('thread pane takes over the TOC column on desktop and moves into a drawer below 1180px', () => {
  const page = wikiFile('index.vue')
  // 复用右栏：有线程时常驻右栏，目录让位但保留工具条入口（不新增第三根栏）
  assert.match(page, /wiki-doc__shell--with-thread/)
  assert.match(page, /grid-template-columns: 248px minmax\(0, 1fr\) 340px/)
  assert.match(page, /v-if="showToc && !showThreadColumn"/)
  assert.match(page, /v-if="showThreadColumn"/)
  // 窄屏用 YdDrawer，窄屏断点与两侧面板互斥
  assert.match(page, /id="wiki-doc-comment-drawer"/)
  assert.match(page, /side="right"/)
  assert.match(page, /min-width: 1180px/)
  const thread = wikiFile('WikiCommentThread.vue')
  // 卡片/摘录点击滚动定位到唯一命中处；待审核提示不伪造成已发布
  assert.match(thread, /@click="emit\('locate', comment\.id\)"/)
  assert.match(thread, /审核通过后会展示在本面板/)
  assert.match(thread, /resolveWikiCommentAnchorState\(comment\)/)
  assert.doesNotMatch(thread, /'PAGE'/)
})
