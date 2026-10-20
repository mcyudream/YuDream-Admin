import type { SiteDocument, StudioCommand, StudioNode, StudioPage } from '../types/site-studio'
import { renderCmsMarkdown } from './cms-template-render'
import { parseStudioHtml } from './site-studio-html'
import { validateStudioNodes, validateStudioPath, validateStudioUrl } from './site-studio-validation'

/**
 * 旧版 CMS（apiCms.home / apiCms.page 数据）到站点工作台的定向复制迁移。
 * 本模块只做纯转换与预检计划：不发请求、不改站点文档、不保存、不发布。
 * 承诺边界（不伪装无损）：
 * - 页面 htmlContent 走 parseStudioHtml，严格只接受 992/768 断点与安全元素，失败逐项上报；
 * - 页面 jsContent 非空一律拒绝（脚本无法无损迁移）；
 * - 页面 markdownContent 可保留为纯文本，或用既有安全渲染（renderCmsMarkdown）后解析；
 * - 仅含旧构建器工程数据（builderProjectJson）的页面无法无损迁移；
 * - 旧版首页主内容按 settings.homeHtml/homeCss 迁移；settings.homeJs 非空先于任何解析拒绝；
 *   settings.homeProjectJson 单独存在时明确拒绝，其余未映射 settings 字段逐项警告，不静默丢弃；
 * - DOM 锚点 id 默认原样保留（href #锚点继续有效），仅在与当前文档冲突或树内重复时重映射，
 *   并全树同步改写本地 href #锚点，绝不静默删除链接目标；
 * - 外部资源链接保持原样引用，绝不自动搬运离线副本。
 */

export const DEFAULT_IMPORTED_HOME_PATH = '/imported-home'
export const FALLBACK_PAGE_PATH_BASE = '/imported-page'
/** 与 validateStudioDocument 的页面数量上限保持一致 */
export const MIGRATION_MAX_PAGES = 200

/** 结构化窄类型：与 apiCms 的 CmsPage / HomePageLayout 结构兼容，模块自身不依赖 API 模块 */
export interface MigrationHomeSection {
  type?: string
  title?: string
  subtitle?: string
  mediaUrl?: string
  actionText?: string
  actionUrl?: string
  settings?: Record<string, string>
  visible?: boolean
}

export interface MigrationHome {
  title?: string
  subtitle?: string
  heroImageUrl?: string
  sections?: MigrationHomeSection[]
  /** 旧版首页布局配置：主内容 homeHtml/homeCss/homeJs 与旧构建器 homeProjectJson 都在这里 */
  settings?: Record<string, string>
}

export interface MigrationArticle {
  id: string
  title?: string
  slug?: string
  markdownContent?: string
  htmlContent?: string
  cssContent?: string
  jsContent?: string
  builderProjectJson?: string
}

export type MigrationMarkdownMode = 'text' | 'render'

/** 迁移绑定：预检时捕获，执行前逐字段复验，杜绝切主题/换文档/草稿变化后的误导入 */
export interface MigrationContextBinding {
  contextKey: string
  documentId: string
  editRevision: string
}

export interface MigrationContextSnapshot {
  contextKey: string
  documentId: string
  editRevision: string
}

export interface MigrationNotice {
  sourceId: string
  title: string
  reason: string
}

export interface MigrationPageEntry {
  sourceId: string
  title: string
  sourceSlug: string
  path: string
}

export interface LegacyMigrationPlan {
  commands: StudioCommand[]
  homeEntry?: MigrationPageEntry
  pageEntries: MigrationPageEntry[]
  failures: MigrationNotice[]
  warnings: MigrationNotice[]
  externalUrls: string[]
}

export interface PlanLegacyMigrationInput {
  document: SiteDocument
  home?: MigrationHome | null
  includeHome: boolean
  homePath: string
  articles: MigrationArticle[]
  markdownMode: MigrationMarkdownMode
}

/** slug → 站内路径的安全转换：仅保留 [\w-]，逐段清洗后拼成 /a/b；无有效字符时退化为 '/'（由调用方兜底） */
export function slugToStudioPath(slug: string): string {
  const segments = String(slug ?? '').trim().toLowerCase().replace(/^\/+|\/+$/g, '').split('/').map(segment => segment.replace(/[^\w-]+/g, '-').replace(/-+/g, '-').replace(/^-|-$/g, '')).filter(Boolean)
  return `/${segments.join('/')}`
}

/** 为页面 slug 冲突分配唯一路径：优先原样，冲突后依次 -imported、-imported-2…（大小写不敏感） */
export function reserveUniqueStudioPath(base: string, used: Set<string>): string {
  const normalizedBase = base === '/' ? FALLBACK_PAGE_PATH_BASE : base
  if (!used.has(normalizedBase.toLowerCase())) {
    used.add(normalizedBase.toLowerCase())
    return normalizedBase
  }
  for (let suffix = 1; ; suffix++) {
    const candidate = suffix === 1 ? `${normalizedBase}-imported` : `${normalizedBase}-imported-${suffix}`
    if (!used.has(candidate.toLowerCase())) {
      used.add(candidate.toLowerCase())
      return candidate
    }
  }
}

/** 首页副本路径裁决：不能覆盖当前根 /；格式必须合法；不得与现有页面冲突（大小写不敏感） */
export function resolveHomeCopyPath(candidate: string, used: Set<string>): { path: string } | { error: string } {
  const trimmed = String(candidate ?? '').trim() || DEFAULT_IMPORTED_HOME_PATH
  if (trimmed === '/') {
    return { error: '首页副本不能覆盖当前根路径 /，请改用其他路径或默认 /imported-home' }
  }
  try {
    validateStudioPath(trimmed)
  }
  catch {
    return { error: `首页路径「${trimmed}」无效：必须是 /about 格式的站内绝对路径` }
  }
  if (used.has(trimmed.toLowerCase())) {
    return { error: `首页路径「${trimmed}」已被现有页面占用，请选择新路径或改用默认 /imported-home` }
  }
  used.add(trimmed.toLowerCase())
  return { path: trimmed }
}

export function isSameMigrationContext(binding: MigrationContextBinding, current: MigrationContextSnapshot): boolean {
  return binding.contextKey === current.contextKey
    && binding.documentId === current.documentId
    && binding.editRevision === current.editRevision
}

const REFERENCE_ATTRS = ['src', 'poster', 'href'] as const

/** 收集节点树里的外部引用地址（http[s] 绝对地址；站内路径/锚点/data URL 不算外链） */
export function collectStudioReferenceUrls(nodes: StudioNode[]): string[] {
  const urls = new Set<string>()
  const visit = (node: StudioNode) => {
    for (const key of REFERENCE_ATTRS) {
      const value = node.attributes[key]
      if (value && /^https?:\/\//i.test(value)) {
        urls.add(value)
      }
    }
    for (const value of Object.values(node.styles)) {
      for (const match of value.matchAll(/url\(([^)]+)\)/g)) {
        const url = match[1]!.trim().replace(/^['"]|['"]$/g, '').trim()
        if (/^https?:\/\//i.test(url)) {
          urls.add(url)
        }
      }
    }
    node.children.forEach(visit)
  }
  nodes.forEach(visit)
  return [...urls]
}

/** 收集节点树里的 DOM id（attributes.id），供迁移副本做锚点冲突预留 */
export function collectStudioDomIds(nodes: StudioNode[]): Set<string> {
  const ids = new Set<string>()
  const visit = (node: StudioNode) => {
    if (node.attributes.id !== undefined) {
      ids.add(node.attributes.id)
    }
    node.children.forEach(visit)
  }
  nodes.forEach(visit)
  return ids
}

/**
 * 节点树换发 studio 节点 UUID 并剥离原 data-studio-id。
 * DOM 锚点 id 默认原样保留（href #锚点继续有效）；仅在与当前文档冲突（reservedDomIds）
 * 或树内重复时重映射为全新 id，并在全树同步改写本地 href #锚点——绝不静默删除链接目标。
 */
export function freshenStudioNodes(nodes: StudioNode[], reservedDomIds: Set<string> = new Set()): StudioNode[] {
  const domIdMap = new Map<string, string>()
  const remap = (node: StudioNode): StudioNode => {
    const attributes: Record<string, string> = {}
    for (const [key, value] of Object.entries(node.attributes)) {
      if (key !== 'data-studio-id') {
        attributes[key] = value
      }
    }
    const copy: StudioNode = {
      ...node,
      id: crypto.randomUUID(),
      attributes,
      children: node.children.map(remap),
    }
    if (node.responsive) {
      copy.responsive = { ...node.responsive }
    }
    const domId = attributes.id
    if (domId !== undefined) {
      if (domIdMap.has(domId)) {
        // 树内重复：#锚点 语义由第一次出现决定，这次出现换全新 id
        attributes.id = crypto.randomUUID()
      }
      else if (reservedDomIds.has(domId)) {
        // 与当前文档已有 DOM id 冲突：重映射，后续全树改写指向它的 href
        const fresh = crypto.randomUUID()
        domIdMap.set(domId, fresh)
        attributes.id = fresh
      }
      else {
        domIdMap.set(domId, domId)
      }
    }
    return copy
  }
  const result = nodes.map(remap)
  const relink = (node: StudioNode) => {
    const href = node.attributes.href
    if (href?.startsWith('#')) {
      const mapped = domIdMap.get(href.slice(1))
      if (mapped && mapped !== href.slice(1)) {
        node.attributes.href = `#${mapped}`
      }
    }
    node.children.forEach(relink)
  }
  result.forEach(relink)
  return result
}

function textNode(text: string, tag = 'p', type = 'text'): StudioNode {
  return { id: crypto.randomUUID(), type, tag, text, attributes: {}, styles: {}, children: [] }
}

function linkNode(href: string, text: string): StudioNode {
  return { id: crypto.randomUUID(), type: 'link', tag: 'a', text, attributes: { href }, styles: {}, children: [] }
}

function imageNode(src: string, alt: string): StudioNode {
  return { id: crypto.randomUUID(), type: 'image', tag: 'img', attributes: { src, alt }, styles: {}, children: [] }
}

/** 验证旧 CMS 里的引用地址；非法地址直接让所在条目失败，不静默丢弃内容 */
function assertReferenceUrl(value: string | undefined, where: string, field: string): string {
  const url = String(value ?? '').trim()
  try {
    validateStudioUrl(url)
  }
  catch {
    throw new Error(`${where} 的 ${field}「${url}」不是安全的站内/HTTP(S) 地址，已拒绝迁移`)
  }
  return url
}

interface HomeConversion {
  root: StudioNode[]
  externalUrls: string[]
  warnings: string[]
}

function convertHomeSection(section: MigrationHomeSection, index: number): { node: StudioNode, warnings: string[] } {
  const label = section.title?.trim() || `第 ${index + 1} 个分区`
  const warnings: string[] = []
  const children: StudioNode[] = []
  // 布局级标题用 h1，各分区标题一律 h2，避免副本出现多个 h1
  if (section.title?.trim()) {
    children.push(textNode(section.title.trim(), 'h2', 'heading'))
  }
  if (section.subtitle?.trim()) {
    children.push(textNode(section.subtitle.trim()))
  }
  if (section.mediaUrl?.trim()) {
    const url = assertReferenceUrl(section.mediaUrl, `分区「${label}」`, '媒体地址')
    children.push(imageNode(url, section.title?.trim() || '分区媒体'))
  }
  if (section.actionUrl?.trim()) {
    const url = assertReferenceUrl(section.actionUrl, `分区「${label}」`, '按钮链接')
    children.push(linkNode(url, section.actionText?.trim() || url))
  }
  else if (section.actionText?.trim()) {
    warnings.push(`分区「${label}」的按钮文字没有对应链接，已保留为纯文本`)
    children.push(textNode(section.actionText.trim()))
  }
  if (section.settings && Object.keys(section.settings).length) {
    warnings.push(`分区「${label}」包含 ${Object.keys(section.settings).length} 个未映射的配置字段（settings），迁移不携带这些配置`)
  }
  return { node: { id: crypto.randomUUID(), type: 'section', tag: 'section', attributes: {}, styles: {}, children }, warnings }
}

const MAPPED_HOME_SETTINGS_KEYS = ['homeHtml', 'homeCss', 'homeJs', 'homeProjectJson']

/**
 * 旧版首页布局 → 站点节点树。
 * 主内容优先级：settings.homeHtml（配 homeCss 严格解析）> 标题/主图 + 分区；
 * settings.homeJs 非空先于任何解析拒绝；settings.homeProjectJson 单独存在时明确拒绝；
 * 其余未映射 settings 字段逐项警告，不静默丢弃。
 */
export function convertLegacyHome(home: MigrationHome, reservedDomIds: Set<string> = new Set()): HomeConversion {
  const settings = home.settings || {}
  // JS 先于任何解析拒绝：无浏览器 DOM 的环境也能得到明确的失败原因
  if (settings.homeJs?.trim()) {
    throw new Error('旧版首页包含自定义 JS（settings.homeJs 非空），无法无损迁移；请保留在旧工作台访问')
  }
  const homeHtml = settings.homeHtml?.trim() || ''
  const homeCss = settings.homeCss?.trim() || ''
  const homeProjectJson = settings.homeProjectJson?.trim() || ''
  const title = home.title?.trim() || ''
  const subtitle = home.subtitle?.trim() || ''
  const heroUrl = home.heroImageUrl?.trim() || ''
  const sections = (home.sections || []).filter(section => section.visible !== false)
  const root: StudioNode[] = []
  const warnings: string[] = []
  if (title || subtitle || heroUrl) {
    const children: StudioNode[] = []
    if (title) {
      children.push(textNode(title, 'h1', 'heading'))
    }
    if (subtitle) {
      children.push(textNode(subtitle))
    }
    if (heroUrl) {
      const url = assertReferenceUrl(heroUrl, '首页布局', '主图地址')
      children.push(imageNode(url, title || '首页主图'))
    }
    root.push({ id: crypto.randomUUID(), type: 'section', tag: 'section', attributes: {}, styles: {}, children })
  }
  if (homeHtml) {
    // 严格解析主内容：仅支持 992/768 断点、安全元素与受限 CSS；失败会抛出明确原因
    root.push(...freshenStudioNodes(parseStudioHtml(homeHtml, homeCss), reservedDomIds))
    if (sections.length) {
      warnings.push(`旧版首页同时包含 settings.homeHtml 主内容与 ${sections.length} 个分区，两者均已迁移，请按需删减`)
    }
  }
  else if (homeProjectJson) {
    if (!root.length) {
      throw new Error('旧版首页仅包含旧构建器工程数据（settings.homeProjectJson），无法无损迁移到站点工作台')
    }
    warnings.push('旧版首页的 settings.homeProjectJson 构建器工程数据未迁移，仅迁移了标题/主图/分区等显式字段')
  }
  for (const [index, section] of sections.entries()) {
    const converted = convertHomeSection(section, index)
    root.push(converted.node)
    warnings.push(...converted.warnings)
  }
  if (!root.length) {
    throw new Error('旧版首页没有可迁移的内容（标题、主图、homeHtml 与分区均为空）')
  }
  const unmappedKeys = Object.keys(settings).filter(key => !MAPPED_HOME_SETTINGS_KEYS.includes(key))
  if (unmappedKeys.length) {
    warnings.push(`首页布局包含 ${unmappedKeys.length} 个未迁移的配置字段（settings：${unmappedKeys.slice(0, 8).join('、')}${unmappedKeys.length > 8 ? ' 等' : ''}），请人工确认这些配置`)
  }
  validateStudioNodes(root)
  return { root, externalUrls: collectStudioReferenceUrls(root), warnings }
}

interface ArticleConversion {
  root: StudioNode[]
  externalUrls: string[]
  warnings: string[]
}

/** 单篇文章 → 站点节点树。任何不支持的情况抛错，由预检逐项记录，绝不产出半真半假的页面 */
function convertLegacyArticle(article: MigrationArticle, markdownMode: MigrationMarkdownMode, reservedDomIds: Set<string>): ArticleConversion {
  if (article.jsContent && article.jsContent.trim()) {
    throw new Error('页面包含自定义 JS（jsContent 非空），无法无损迁移；请保留在旧工作台访问')
  }
  const warnings: string[] = []
  let root: StudioNode[]
  if (article.htmlContent && article.htmlContent.trim()) {
    // 严格解析：仅支持 992/768 断点、安全元素与受限 CSS；失败会抛出明确原因
    root = parseStudioHtml(article.htmlContent, article.cssContent || '', [])
    root = freshenStudioNodes(root, reservedDomIds)
  }
  else if (article.markdownContent && article.markdownContent.trim()) {
    if (markdownMode === 'text') {
      warnings.push('Markdown 以纯文本迁移：保留原始标记字符，不渲染排版')
      root = [textNode(article.markdownContent)]
    }
    else {
      warnings.push('Markdown 经内置安全渲染后解析：表格、代码块等高级语法会退化为普通段落')
      root = freshenStudioNodes(parseStudioHtml(renderCmsMarkdown(article.markdownContent)), reservedDomIds)
    }
  }
  else if (article.builderProjectJson && article.builderProjectJson.trim()) {
    throw new Error('页面仅包含旧构建器工程数据（builderProjectJson），无法无损迁移到站点工作台')
  }
  else {
    throw new Error('页面没有可迁移的内容（HTML 与 Markdown 均为空）')
  }
  validateStudioNodes(root)
  return { root, externalUrls: collectStudioReferenceUrls(root), warnings }
}

function pageName(article: MigrationArticle): string {
  return article.title?.trim() || article.slug?.trim() || '未命名页面'
}

/** 预检：纯函数。只读输入，产出可执行的一次性 add-page batch 与逐项失败/警告/外链清单 */
export function planLegacyMigration(input: PlanLegacyMigrationInput): LegacyMigrationPlan {
  const { document } = input
  const failures: MigrationNotice[] = []
  const warnings: MigrationNotice[] = []
  const externalUrls = new Set<string>()
  const commands: StudioCommand[] = []
  const pageEntries: MigrationPageEntry[] = []
  let homeEntry: MigrationPageEntry | undefined

  const used = new Set<string>(document.pages.map(page => page.path.toLowerCase()))
  const plannedCount = (input.includeHome && input.home ? 1 : 0) + input.articles.length
  if (document.pages.length + plannedCount > MIGRATION_MAX_PAGES) {
    failures.push({ sourceId: '', title: '页面数量上限', reason: `新增 ${plannedCount} 个页面副本后将超过站点 ${MIGRATION_MAX_PAGES} 页上限（当前 ${document.pages.length} 页），请减少所选页面` })
    return { commands: [], pageEntries: [], failures, warnings, externalUrls: [] }
  }

  // 当前文档已占用的 DOM 锚点 id 全部计入预留：迁移副本的锚点冲突时重映射，不与页头页脚/既有页面撞目标
  const reservedDomIds = collectStudioDomIds([...document.theme.header, ...document.theme.footer, ...document.pages.flatMap(page => page.root)])

  if (input.includeHome && input.home) {
    try {
      const conversion = convertLegacyHome(input.home, reservedDomIds)
      const decision = resolveHomeCopyPath(input.homePath, used)
      if ('error' in decision) {
        failures.push({ sourceId: 'home', title: '旧版首页', reason: decision.error })
      }
      else {
        const page: StudioPage = { id: crypto.randomUUID(), name: input.home.title?.trim() || '旧版首页', path: decision.path, root: conversion.root }
        commands.push({ type: 'add-page', page })
        homeEntry = { sourceId: 'home', title: page.name, sourceSlug: '', path: page.path }
        conversion.externalUrls.forEach(url => externalUrls.add(url))
        conversion.warnings.forEach(reason => warnings.push({ sourceId: 'home', title: '旧版首页', reason }))
      }
    }
    catch (error) {
      failures.push({ sourceId: 'home', title: '旧版首页', reason: error instanceof Error ? error.message : '旧版首页转换失败' })
    }
  }

  for (const article of input.articles) {
    const title = pageName(article)
    try {
      const conversion = convertLegacyArticle(article, input.markdownMode, reservedDomIds)
      const base = slugToStudioPath(article.slug ?? '')
      const path = reserveUniqueStudioPath(base, used)
      if (path !== base) {
        warnings.push({ sourceId: article.id, title, reason: `slug「${article.slug || '（空）'}」无法直接使用，已转换为唯一路径 ${path}` })
      }
      const page: StudioPage = { id: crypto.randomUUID(), name: title, path, root: conversion.root }
      commands.push({ type: 'add-page', page })
      pageEntries.push({ sourceId: article.id, title, sourceSlug: article.slug || '', path })
      conversion.externalUrls.forEach(url => externalUrls.add(url))
      conversion.warnings.forEach(reason => warnings.push({ sourceId: article.id, title, reason }))
    }
    catch (error) {
      failures.push({ sourceId: article.id, title, reason: error instanceof Error ? error.message : '页面转换失败' })
    }
  }

  return {
    commands,
    ...(homeEntry ? { homeEntry } : {}),
    pageEntries,
    failures,
    warnings,
    externalUrls: [...externalUrls],
  }
}
