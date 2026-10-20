import type { MigrationArticle, MigrationHome } from './site-studio-migration'
import assert from 'node:assert/strict'
import test from 'node:test'
import { applyStudioCommand, createStudioDocument, createStudioNode, validateStudioDocument } from './site-studio'
import { collectStudioDomIds, collectStudioReferenceUrls, freshenStudioNodes, isSameMigrationContext, planLegacyMigration, resolveHomeCopyPath, slugToStudioPath } from './site-studio-migration'
import { validateStudioPath } from './site-studio-validation'

function articleFixture(overrides: Partial<MigrationArticle> = {}): MigrationArticle {
  return { id: crypto.randomUUID(), title: '测试文章', slug: 'hello-world', ...overrides }
}

function homeFixture(overrides: Partial<MigrationHome> = {}): MigrationHome {
  return {
    title: '旧版站点',
    subtitle: '副标题',
    heroImageUrl: 'https://old-cms.example.test/hero.png',
    sections: [
      { type: 'HERO', title: '主视觉', subtitle: '介绍', mediaUrl: 'https://old-cms.example.test/a.png', actionText: '了解更多', actionUrl: 'https://old-cms.example.test/more', settings: { navigationJson: '[{"label":"旧导航"}]' } },
      { type: 'CTA', title: '行动', actionText: '注册', actionUrl: 'https://old-cms.example.test/join' },
    ],
    ...overrides,
  }
}

test('slug 到路径的安全转换只保留路径白名单字符并逐段清洗', () => {
  assert.equal(slugToStudioPath('hello-world'), '/hello-world')
  assert.equal(slugToStudioPath('Hello World!'), '/hello-world')
  assert.equal(slugToStudioPath('docs/guide-1'), '/docs/guide-1')
  assert.equal(slugToStudioPath('/about/'), '/about')
  assert.equal(slugToStudioPath('a//b'), '/a/b')
  // 中文字符不在路径白名单内：退化为空段，由调用方兜底为 /imported-page
  assert.equal(slugToStudioPath('关于我们'), '/')
  assert.equal(slugToStudioPath('  '), '/')
  assert.equal(validateStudioPath('/imported-page'), undefined)
})

test('slug 冲突按 -imported 序列命名唯一且大小写不敏感', () => {
  assert.equal(slugToStudioPath('About'), '/about')
  const doc = createStudioDocument('站点')
  doc.pages[0]!.path = '/about'
  const first = planLegacyMigration({
    document: doc,
    home: null,
    includeHome: false,
    homePath: '',
    articles: [articleFixture({ slug: 'about', markdownContent: '一' }), articleFixture({ slug: 'ABOUT', markdownContent: '二' }), articleFixture({ slug: 'about', markdownContent: '三' })],
    markdownMode: 'text',
  })
  assert.deepEqual(first.pageEntries.map(entry => entry.path), ['/about-imported', '/about-imported-2', '/about-imported-3'])
})

test('首页副本路径不能覆盖当前根，冲突时要求选择新路径或默认 /imported-home', () => {
  const used = new Set(['/'])
  assert.equal(Object.hasOwn(resolveHomeCopyPath('/', used), 'error'), true)
  assert.deepEqual(resolveHomeCopyPath('', used), { path: '/imported-home' })
  assert.deepEqual(resolveHomeCopyPath('  ', new Set()), { path: '/imported-home' })
  assert.deepEqual(resolveHomeCopyPath('/news-home', used), { path: '/news-home' })
  const conflict = resolveHomeCopyPath('/imported-home', new Set(['/imported-home']))
  assert.equal(Object.hasOwn(conflict, 'error'), true)
  const invalid = resolveHomeCopyPath('/bad path?', new Set())
  assert.equal(Object.hasOwn(invalid, 'error'), true)
})

test('纯转换：Markdown 纯文本迁移产出可校验页面，batch 只含 add-page 且 id 全新', () => {
  const doc = createStudioDocument('站点')
  const existingIds = new Set([doc.id, doc.pages[0]!.id])
  const article = articleFixture({ markdownContent: '# 标题\n\n正文段落' })
  const plan = planLegacyMigration({ document: doc, home: null, includeHome: false, homePath: '', articles: [article], markdownMode: 'text' })
  assert.equal(plan.failures.length, 0)
  assert.equal(plan.commands.length, 1)
  assert.equal(plan.pageEntries[0]!.path, '/hello-world')
  assert.ok(plan.commands.every(command => command.type === 'add-page'))
  const page = (plan.commands[0] as { page: { id: string, root: { id: string, attributes: Record<string, string>, text?: string }[] } }).page
  assert.match(page.id, /^[\da-f-]{36}$/)
  assert.ok(!existingIds.has(page.id))
  assert.equal(page.root[0]!.text, '# 标题\n\n正文段落')
  for (const node of plan.commands.flatMap(command => (command as { page: { root: { attributes: Record<string, string> }[] } }).page.root)) {
    assert.equal(node.attributes['data-studio-id'], undefined)
    assert.equal(node.attributes.id, undefined)
  }
  // batch 实际能被站点模型原子接受并通过整文档校验
  const next = applyStudioCommand(doc, { type: 'batch', commands: plan.commands })
  validateStudioDocument(next)
  assert.equal(next.pages.length, 2)
  assert.equal(next.pages[1]!.root[0]!.text, '# 标题\n\n正文段落')
  assert.ok(plan.warnings.some(warning => warning.reason.includes('纯文本')))
})

test('JS 非空页面被明确拒绝，且拒绝发生在任何 HTML 解析之前', () => {
  const doc = createStudioDocument('站点')
  const article = articleFixture({ htmlContent: '<section>内容</section>', jsContent: 'alert(1)' })
  const plan = planLegacyMigration({
    document: doc,
    home: null,
    includeHome: false,
    homePath: '',
    articles: [article],
    markdownMode: 'text',
  })
  assert.equal(plan.commands.length, 0)
  assert.equal(plan.failures.length, 1)
  assert.equal(plan.failures[0]!.sourceId, article.id)
  assert.match(plan.failures[0]!.reason, /jsContent/)
})

test('浏览器 DOMParser 缺失时不伪造解析：html 与渲染模式逐项失败并如实上报', () => {
  const doc = createStudioDocument('站点')
  const plan = planLegacyMigration({
    document: doc,
    home: null,
    includeHome: false,
    homePath: '',
    articles: [
      articleFixture({ id: 'html-page', htmlContent: '<section data-studio-id="x">内容</section>', cssContent: 'section{color:red}' }),
      articleFixture({ id: 'render-page', markdownContent: '# 渲染' }),
    ],
    markdownMode: 'render',
  })
  assert.equal(plan.commands.length, 0)
  assert.equal(plan.failures.length, 2)
  assert.match(plan.failures[0]!.reason, /DOMParser/)
  assert.match(plan.failures[1]!.reason, /DOMParser/)
  // 无 jsdom/假 DOM 冒充浏览器环境；DOM 路径只能在真实浏览器验证
  assert.equal(typeof (globalThis as { DOMParser?: unknown }).DOMParser, 'undefined')
})

test('仅旧构建器工程与空内容的页面被逐项拒绝', () => {
  const doc = createStudioDocument('站点')
  const plan = planLegacyMigration({
    document: doc,
    home: null,
    includeHome: false,
    homePath: '',
    articles: [articleFixture({ id: 'builder', builderProjectJson: '{"type":"grapes"}' }), articleFixture({ id: 'empty' })],
    markdownMode: 'text',
  })
  assert.equal(plan.commands.length, 0)
  assert.deepEqual(plan.failures.map(failure => failure.sourceId), ['builder', 'empty'])
  assert.match(plan.failures[0]!.reason, /builderProjectJson/)
  assert.match(plan.failures[1]!.reason, /没有可迁移的内容/)
})

test('首页布局映射为 section/h1/p/img/a 节点，外链保持引用并给出未映射配置警告', () => {
  const doc = createStudioDocument('站点')
  const plan = planLegacyMigration({ document: doc, home: homeFixture(), includeHome: true, homePath: '', articles: [], markdownMode: 'text' })
  assert.equal(plan.failures.length, 0)
  assert.equal(plan.homeEntry!.path, '/imported-home')
  // 主图、分区媒体与按钮链接都保持外链引用（不自动搬运离线副本）
  assert.deepEqual([...plan.externalUrls].sort(), ['https://old-cms.example.test/a.png', 'https://old-cms.example.test/hero.png', 'https://old-cms.example.test/join', 'https://old-cms.example.test/more'])
  assert.ok(plan.warnings.some(warning => warning.sourceId === 'home' && warning.reason.includes('settings')))
  const page = (plan.commands[0] as { page: { root: { tag: string, text?: string, attributes: Record<string, string>, children: { tag: string, text?: string, attributes: Record<string, string> }[] }[] } }).page
  const layout = page.root[0]!
  assert.equal(layout.tag, 'section')
  assert.equal(layout.children[0]!.tag, 'h1')
  assert.equal(layout.children[0]!.text, '旧版站点')
  const hero = page.root[1]!
  assert.equal(hero.children[0]!.tag, 'h2')
  assert.equal(hero.children[2]!.tag, 'img')
  assert.equal(hero.children[2]!.attributes.src, 'https://old-cms.example.test/a.png')
  assert.equal(hero.children[3]!.tag, 'a')
  assert.equal(hero.children[3]!.attributes.href, 'https://old-cms.example.test/more')
  const next = applyStudioCommand(doc, { type: 'batch', commands: plan.commands })
  validateStudioDocument(next)
  assert.equal(next.pages.length, 2)
  assert.equal(next.pages[1]!.path, '/imported-home')
})

test('首页路径冲突与不可见分区：失败逐项显示，不静默覆盖', () => {
  const doc = createStudioDocument('站点')
  doc.pages[0]!.path = '/imported-home'
  const home = homeFixture({ sections: [{ type: 'FEATURE', title: '隐藏', visible: false }, { type: 'FEATURE', title: '可见' }] })
  const plan = planLegacyMigration({ document: doc, home, includeHome: true, homePath: '/imported-home', articles: [], markdownMode: 'text' })
  assert.equal(plan.commands.length, 0)
  assert.equal(plan.failures.length, 1)
  assert.equal(plan.failures[0]!.sourceId, 'home')
  assert.match(plan.failures[0]!.reason, /已被现有页面占用/)
  // 不可见分区不产生失败，也不进入节点
  const ok = planLegacyMigration({ document: doc, home, includeHome: true, homePath: '/backup-home', articles: [], markdownMode: 'text' })
  assert.equal(ok.failures.length, 0)
  const tags = JSON.stringify((ok.commands[0] as { page: { root: { tag: string }[] } }).page)
  assert.doesNotMatch(tags, /隐藏/)
})

test('原输入未被修改：文档、文章与首页布局在预检后保持原 JSON', () => {
  const doc = createStudioDocument('站点')
  doc.pages[0]!.path = '/about'
  const documentBefore = JSON.stringify(doc)
  const home = homeFixture()
  const homeBefore = JSON.stringify(home)
  const article = articleFixture({ markdownContent: '# 正文' })
  const articleBefore = JSON.stringify(article)
  planLegacyMigration({ document: doc, home, includeHome: true, homePath: '/imported-home', articles: [article], markdownMode: 'text' })
  assert.equal(JSON.stringify(doc), documentBefore)
  assert.equal(JSON.stringify(home), homeBefore)
  assert.equal(JSON.stringify(article), articleBefore)
})

test('页面数量上限：超出 200 页的迁移整体拒绝且不产出命令', () => {
  let doc = createStudioDocument('站点')
  for (let index = 0; index < 199; index++) {
    doc = applyStudioCommand(doc, { type: 'add-page', page: { id: crypto.randomUUID(), name: `页${index}`, path: `/p-${index}`, root: [] } })
  }
  const plan = planLegacyMigration({ document: doc, home: null, includeHome: false, homePath: '', articles: [articleFixture(), articleFixture()], markdownMode: 'text' })
  assert.equal(plan.commands.length, 0)
  assert.equal(plan.failures.length, 1)
  assert.match(plan.failures[0]!.reason, /200/)
  const planHome = planLegacyMigration({ document: doc, home: homeFixture(), includeHome: true, homePath: '', articles: [articleFixture()], markdownMode: 'text' })
  assert.equal(planHome.commands.length, 0)
  assert.match(planHome.failures[0]!.reason, /200/)
})

test('迁移绑定逐字段复验，任一变化都视为上下文失效', () => {
  const binding = { contextKey: 'site-studio:default', documentId: 'doc-1', editRevision: 'rev-1#3' }
  assert.equal(isSameMigrationContext(binding, binding), true)
  assert.equal(isSameMigrationContext(binding, { ...binding, contextKey: 'site-studio:dark' }), false)
  assert.equal(isSameMigrationContext(binding, { ...binding, documentId: 'doc-2' }), false)
  assert.equal(isSameMigrationContext(binding, { ...binding, editRevision: 'rev-1#4' }), false)
})

test('外链收集只报告 http(s) 绝对地址，站内路径与锚点不算外链', () => {
  const nodes = [
    createStudioNode('image'),
    createStudioNode('link'),
  ]
  const [image, link] = nodes
  image!.attributes.src = 'https://cdn.example.test/p.png'
  link!.attributes.href = '/about'
  const section = createStudioNode('section')
  section.children = nodes
  assert.deepEqual(collectStudioReferenceUrls([section]), ['https://cdn.example.test/p.png'])
})

test('home.settings.homeJs 非空先于任何解析拒绝（无需 DOM 即可验证）', () => {
  const doc = createStudioDocument('站点')
  const plan = planLegacyMigration({
    document: doc,
    home: { settings: { homeHtml: '<section>主内容</section>', homeJs: 'alert(1)' } },
    includeHome: true,
    homePath: '',
    articles: [],
    markdownMode: 'text',
  })
  assert.equal(plan.commands.length, 0)
  assert.equal(plan.failures.length, 1)
  assert.equal(plan.failures[0]!.sourceId, 'home')
  assert.match(plan.failures[0]!.reason, /homeJs/)
  assert.doesNotMatch(plan.failures[0]!.reason, /DOMParser/)
})

test('home.settings.homeHtml 无 DOM 明确失败；homeProjectJson 单独存在明确拒绝；空白 homeJs 不误伤', () => {
  const doc = createStudioDocument('站点')
  const htmlPlan = planLegacyMigration({
    document: doc,
    home: { settings: { homeHtml: '<section>主内容</section>', homeCss: 'section{color:red}' } },
    includeHome: true,
    homePath: '',
    articles: [],
    markdownMode: 'text',
  })
  assert.equal(htmlPlan.commands.length, 0)
  assert.equal(htmlPlan.failures.length, 1)
  assert.equal(htmlPlan.failures[0]!.sourceId, 'home')
  assert.match(htmlPlan.failures[0]!.reason, /DOMParser/)
  const projectPlan = planLegacyMigration({
    document: doc,
    home: { settings: { homeProjectJson: '{"gjs":true}' } },
    includeHome: true,
    homePath: '',
    articles: [],
    markdownMode: 'text',
  })
  assert.equal(projectPlan.commands.length, 0)
  assert.equal(projectPlan.failures.length, 1)
  assert.match(projectPlan.failures[0]!.reason, /homeProjectJson/)
  const blankPlan = planLegacyMigration({
    document: doc,
    home: { title: '旧站', settings: { homeJs: '   ', homeHtml: '' } },
    includeHome: true,
    homePath: '',
    articles: [],
    markdownMode: 'text',
  })
  assert.equal(blankPlan.failures.length, 0)
  assert.equal(blankPlan.homeEntry!.path, '/imported-home')
})

test('首页布局未映射的 settings 字段逐项警告，不静默丢弃', () => {
  const doc = createStudioDocument('站点')
  const plan = planLegacyMigration({
    document: doc,
    home: { title: '旧站', settings: { navigationJson: '[{"label":"旧导航"}]', footerNote: '备注' } },
    includeHome: true,
    homePath: '',
    articles: [],
    markdownMode: 'text',
  })
  assert.equal(plan.failures.length, 0)
  const warning = plan.warnings.find(item => item.sourceId === 'home' && item.reason.includes('navigationJson'))
  assert.ok(warning)
  assert.match(warning!.reason, /footerNote/)
})

test('复制保留 DOM 锚点：id 默认保留，与当前文档冲突或树内重复才重映射并全树改写 href', () => {
  const section = createStudioNode('section')
  section.attributes.id = 'intro'
  section.attributes['data-studio-id'] = 'legacy-1'
  const link = createStudioNode('link')
  link.attributes.href = '#intro'
  section.children = [link]
  const nav = createStudioNode('section')
  nav.attributes.id = 'header'
  const navLink = createStudioNode('link')
  navLink.attributes.href = '#header'
  nav.children = [navLink]
  const duplicate = createStudioNode('section')
  duplicate.attributes.id = 'intro'
  const [freshSection, freshNav, freshDuplicate] = freshenStudioNodes([section, nav, duplicate], new Set(['header']))
  // 无冲突：DOM id 原样保留，锚点链接不动
  assert.equal(freshSection!.attributes.id, 'intro')
  assert.equal(freshSection!.children[0]!.attributes.href, '#intro')
  // 与当前文档冲突：重映射为全新 id，本地 href 全树同步改写
  assert.notEqual(freshNav!.attributes.id, 'header')
  assert.equal(freshNav!.children[0]!.attributes.href, `#${freshNav!.attributes.id}`)
  // 树内重复：第二次出现换新 id，#intro 仍指向第一次出现
  assert.notEqual(freshDuplicate!.attributes.id, 'intro')
  assert.equal(freshSection!.children[0]!.attributes.href, '#intro')
  // studio 节点 id 仍然全新，data-studio-id 剥离
  assert.notEqual(freshSection!.id, section.id)
  assert.equal(freshSection!.attributes['data-studio-id'], undefined)
})

test('collectStudioDomIds 收集全树 DOM id 供冲突预留', () => {
  const parent = createStudioNode('section')
  parent.attributes.id = 'wrap'
  const child = createStudioNode('image')
  child.attributes.id = 'pic'
  parent.children = [child]
  assert.deepEqual([...collectStudioDomIds([parent, createStudioNode('text')])].sort(), ['pic', 'wrap'])
})
