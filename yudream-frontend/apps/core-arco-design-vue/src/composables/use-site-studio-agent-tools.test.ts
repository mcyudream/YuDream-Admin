import type { SiteDocument, StudioNode } from '../types/site-studio.ts'
import assert from 'node:assert/strict'
import test from 'node:test'
import { STUDIO_AGENT_GUIDE } from '../utils/site-studio-agent-guide.ts'
import { studioFind, studioInspect, studioOutline, studioThemeSummary } from '../utils/site-studio-agent-observe.ts'
import { contrastRatio, parseCssColor } from '../utils/site-studio-check.ts'
import { applyStudioCommand, createStudioDocument } from '../utils/site-studio.ts'
import { executeStudioAgentTool, STUDIO_AGENT_TOOLS } from './use-site-studio-agent.ts'

function node(id: string, tag: string, attributes: Record<string, string> = {}, children: StudioNode[] = [], text?: string): StudioNode {
  return { id, type: tag, tag, attributes, styles: {}, children, ...(text === undefined ? {} : { text }) }
}

function site(): SiteDocument {
  const document = createStudioDocument('测试站')
  const card = node('card-1', 'a', { 'class': 'card x-abc123', 'href': '/wiki', 'data-yd-item': 'true', 'data-yd-href': 'url' }, [node('card-title', 'h3', { 'data-yd-text': 'title' }, [], '知识库')])
  document.pages[0]!.root = [
    node('hero', 'section', { 'class': 'hero site-root-xyz', 'data-yd-content-bg': 'heroImage' }, [
      node('hero-title', 'h1', { 'data-yd-content': 'heroTitle' }, [], '欢迎来到本站'),
      node('hero-img', 'img', { src: `data:image/png;base64,${'A'.repeat(400)}`, alt: '' }),
    ]),
    node('grid', 'div', { 'class': 'grid', 'data-yd-source': 'content:cards' }, [card]),
  ]
  document.theme.classes = [{ name: 'card', styles: { padding: '20px', background: '#fff' }, states: { hover: { background: '#eee' } } }, { name: 'grid', styles: { display: 'grid' } }, { name: 'hero', styles: { padding: '80px 0' } }]
  document.features = {
    content: {
      sections: [{ code: 'home', title: '首页', fields: [
        { key: 'heroTitle', label: '标题', type: 'text', defaultValue: '欢迎来到本站' },
        { key: 'cards', label: '卡片', type: 'list', itemFields: [{ key: 'title', label: '标题', type: 'text' }, { key: 'url', label: '链接', type: 'link' }], defaultValue: [{ title: '知识库', url: '/wiki' }] },
      ] }],
      values: {},
    },
  }
  return document
}

test('outline is compact: theme classes only, bindings shown, embedded data hidden, depth capped', () => {
  const document = site()
  const outline = studioOutline(document, document.pages[0]!.id)
  const text = outline.lines.join('\n')
  assert.match(text, /hero <section> \.hero \[content-bg=heroImage\]/)
  assert.match(text, /card-1 <a> \.card .*\[item href=url\]/)
  assert.doesNotMatch(text, /x-abc123|site-root-xyz|base64/)
  const shallow = studioOutline(document, document.pages[0]!.id, { depth: 1 })
  assert.equal(shallow.lines.filter(line => !line.startsWith('#')).length, 2)
  assert.match(shallow.lines.join('\n'), /\+2 子节点/)
  assert.deepEqual(studioOutline(document, document.pages[0]!.id, { region: 'all', rootId: 'grid' }).lines.map(line => line.trim().split(' ')[0]), ['grid', 'card-1', 'card-title'])
})

test('find locates by text, class and binding with ancestor paths', () => {
  const document = site()
  const pageId = document.pages[0]!.id
  assert.deepEqual(studioFind(document, pageId, { query: '欢迎' }).matches.map(item => item.id), ['hero-title'])
  assert.deepEqual(studioFind(document, pageId, { query: 'card', field: 'class' }).matches.map(item => item.id), ['card-1'])
  const binding = studioFind(document, pageId, { query: 'content:cards', field: 'binding' })
  assert.equal(binding.matches[0]!.id, 'grid')
  assert.equal(studioFind(document, pageId, { query: '知识库', field: 'text' }).matches[0]!.path, 'div.grid > a.card')
})

test('inspect returns full node, truncated embedded data, ancestors and used class definitions', () => {
  const document = site()
  const result = studioInspect(document, document.pages[0]!.id, ['card-1', 'hero-img', 'nope'])
  const card = result.nodes[0] as Record<string, unknown>
  assert.deepEqual(card.childIds, ['card-title'])
  assert.match(String((card.ancestors as string[])[0]), /^grid <div>/)
  assert.match(String((result.nodes[1] as { attributes: Record<string, string> }).attributes.src), /^data:…（内嵌 \d+ 字符）$/)
  assert.ok('missing' in (result.nodes[2] as object))
  assert.deepEqual(result.classes.map(item => item.name), ['card'])
  assert.deepEqual(result.classes[0]!.states, ['hover'])
  assert.throws(() => studioInspect(document, document.pages[0]!.id, []))
})

test('theme summary reports class usage, content values and filters by query', () => {
  const document = site()
  const all = studioThemeSummary(document) as { classes: { items: { name: string, used: number }[] }, content: { fields: { key: string, value: unknown, filled: boolean }[] }[] }
  assert.equal(all.classes.items.find(item => item.name === 'card')!.used, 1)
  assert.equal(all.content[0]!.fields[0]!.value, '欢迎来到本站')
  assert.equal(all.content[0]!.fields[0]!.filled, false)
  assert.match(String(all.content[0]!.fields[1]!.value), /^列表 1 条/)
  const filtered = studioThemeSummary(document, { section: 'classes', query: 'grid' }) as { classes: { items: { name: string }[] }, content?: unknown }
  assert.deepEqual(filtered.classes.items.map(item => item.name), ['grid'])
  assert.equal(filtered.content, undefined)
})

test('guide exposes topics on demand', () => {
  for (const topic of ['commands', 'styles', 'bindings', 'content', 'effects', 'pages', 'checklist']) {
    assert.ok(STUDIO_AGENT_GUIDE[topic]!.body.length > 40, topic)
  }
  assert.match(STUDIO_AGENT_GUIDE.commands!.body, /set-content/)
  assert.match(STUDIO_AGENT_GUIDE.bindings!.body, /data-yd-empty/)
})

function context(document: SiteDocument) {
  const state = { document, contextKey: 'studio-test', pageId: document.pages[0]!.id, revision: 'r1', selectedNodeId: '' }
  const args = { contextKey: state.contextKey, documentId: document.id, pageId: state.pageId }
  const execute = (command: Parameters<typeof applyStudioCommand>[1]) => {
    state.document = applyStudioCommand(state.document, command)
    state.revision = 'r2'
  }
  return { state, args, execute }
}

test('executor routes observation tools with strict arguments', async () => {
  const f = context(site())
  assert.ok(STUDIO_AGENT_TOOLS.includes('cms.studio.check'))
  const outline = await executeStudioAgentTool('cms.studio.outline', { ...f.args, depth: 3 }, () => f.state, f.execute)
  assert.equal(outline.revision, 'r1')
  assert.ok(Array.isArray(outline.lines))
  await assert.rejects(executeStudioAgentTool('cms.studio.outline', { ...f.args, depth: 99 }, () => f.state, f.execute), /depth/)
  await assert.rejects(executeStudioAgentTool('cms.studio.find', { ...f.args }, () => f.state, f.execute), /query/)
  await assert.rejects(executeStudioAgentTool('cms.studio.inspect', { ...f.args, nodeIds: ['hero'], command: {} }, () => f.state, f.execute), /未授权参数/)
  await assert.rejects(executeStudioAgentTool('cms.studio.exec', { ...f.args }, () => f.state, f.execute), /未授权的设计器工具/)
  const guide = await executeStudioAgentTool('cms.studio.guide', { ...f.args }, () => f.state, f.execute)
  assert.ok(Array.isArray(guide.topics))
})

test('set-content and set-class change data narrowly and stay validated', async () => {
  const f = context(site())
  await executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r1', command: { type: 'batch', commands: [
    { type: 'set-content', values: { heroTitle: '新的标题', cards: [{ title: '插件市场', url: '/market' }] } },
    { type: 'set-class', name: 'card', styles: { padding: '24px' } },
    { type: 'set-class', name: 'badge', styles: { color: '#2563eb' } },
  ] } }, () => f.state, f.execute)
  assert.equal(f.state.document.features!.content!.values.heroTitle, '新的标题')
  assert.deepEqual(f.state.document.theme.classes!.find(item => item.name === 'card')!.styles, { padding: '24px' })
  assert.deepEqual(f.state.document.theme.classes!.find(item => item.name === 'card')!.states, { hover: { background: '#eee' } })
  assert.ok(f.state.document.theme.classes!.some(item => item.name === 'badge'))
  f.state.revision = 'r2'
  await executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r2', command: { type: 'batch', commands: [{ type: 'set-content', values: {}, remove: ['heroTitle'] }, { type: 'set-class', name: 'badge', remove: true }] } }, () => f.state, f.execute)
  assert.equal('heroTitle' in f.state.document.features!.content!.values, false)
  assert.equal(f.state.document.theme.classes!.some(item => item.name === 'badge'), false)
  // 值不符合字段类型、写入未声明字段时整批拒绝
  await assert.rejects(executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r2', command: { type: 'set-content', values: { unknownField: 'x' } } }, () => f.state, f.execute))
  await assert.rejects(executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r2', command: { type: 'set-content', values: { cards: [{ title: 'x', url: 'javascript:alert(1)' }] } } }, () => f.state, f.execute))
  const bare = context(createStudioDocument('无内容'))
  await assert.rejects(executeStudioAgentTool('cms.studio.apply', { ...bare.args, expectedRevision: 'r1', command: { type: 'set-content', values: { a: 1 } } }, () => bare.state, bare.execute), /站点内容模型/)
})

test('contrast helpers follow WCAG and blend translucent text', () => {
  assert.deepEqual(parseCssColor('rgb(255, 255, 255)'), [255, 255, 255, 1])
  assert.deepEqual(parseCssColor('rgba(0, 0, 0, 0.5)'), [0, 0, 0, 0.5])
  assert.equal(parseCssColor('oklch(0.5 0.1 200)'), null)
  assert.equal(Math.round(contrastRatio([0, 0, 0, 1], [255, 255, 255, 1])), 21)
  assert.ok(contrastRatio([119, 119, 119, 1], [255, 255, 255, 1]) > 4.4)
  assert.ok(contrastRatio([0, 0, 0, 0.2], [255, 255, 255, 1]) < 3)
})

test('video content fields accept media URLs and reject non-URL values', async () => {
  const document = site()
  document.features!.content!.sections[0]!.fields.push({ key: 'heroVideo', label: '宣传视频', type: 'video', defaultValue: '' })
  const f = context(document)
  await executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r1', command: { type: 'set-content', values: { heroVideo: '/api/files/123/content' } } }, () => f.state, f.execute)
  assert.equal(f.state.document.features!.content!.values.heroVideo, '/api/files/123/content')
  f.state.revision = 'r2'
  await executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r2', command: { type: 'set-content', values: { heroVideo: 'https://cdn.example.com/intro.mp4' } } }, () => f.state, f.execute)
  for (const bad of ['javascript:alert(1)', '#fff', 'data:video/mp4;base64,AAAA']) {
    await assert.rejects(executeStudioAgentTool('cms.studio.apply', { ...f.args, expectedRevision: 'r2', command: { type: 'set-content', values: { heroVideo: bad } } }, () => f.state, f.execute), bad)
  }
})
