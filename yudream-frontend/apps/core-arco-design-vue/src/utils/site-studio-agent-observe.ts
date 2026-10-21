import type { SiteDocument, StudioNode, StudioStyleClass } from '../types/site-studio'

/**
 * 设计助手的低噪声观察工具（纯函数，前端工具执行器调用）：
 * outline 紧凑树、find 检索、inspect 少量节点全量、theme 主题与站点数据摘要。
 * 编译/导入生成的哈希类（x-* / site-root-*）与内嵌 data: 资源不进模型视野，减少无效 token。
 */

const GENERATED_CLASS = /^(?:x-|site-root-)/
const TEXT_CLIP = 48
const VALUE_CLIP = 160

export type StudioAgentRegion = 'page' | 'header' | 'footer' | 'all'

interface Located { node: StudioNode, region: string, parent?: StudioNode, depth: number }

function clip(value: string, size: number) {
  const flat = value.replace(/\s+/g, ' ').trim()
  return flat.length > size ? `${flat.slice(0, size)}…` : flat
}

/** 属性值给模型：内嵌资源只报长度，长值截断 */
export function agentValue(value: string) {
  return value.startsWith('data:') ? `data:…（内嵌 ${value.length} 字符）` : clip(value, VALUE_CLIP)
}

export function themeClassesOf(node: StudioNode) {
  return (node.attributes.class || '').split(/\s+/).filter(name => name && !GENERATED_CLASS.test(name))
}

function bindingsOf(node: StudioNode) {
  return Object.entries(node.attributes).filter(([key]) => key.startsWith('data-yd-')).map(([key, value]) => value === 'true' || value === '' ? key.slice(8) : `${key.slice(8)}=${clip(value, 40)}`)
}

function regions(document: SiteDocument, pageId: string, region: StudioAgentRegion) {
  const page = document.pages.find(item => item.id === pageId)
  const all: [string, StudioNode[]][] = [['page', page?.root ?? []], ['header', document.theme.header], ['footer', document.theme.footer]]
  return region === 'all' ? all : all.filter(([name]) => name === region)
}

function walk(document: SiteDocument, pageId: string, region: StudioAgentRegion) {
  const out: Located[] = []
  const visit = (nodes: StudioNode[], name: string, depth: number, parent?: StudioNode) => {
    for (const node of nodes) {
      out.push({ node, region: name, parent, depth })
      visit(node.children, name, depth + 1, node)
    }
  }
  for (const [name, nodes] of regions(document, pageId, region)) {
    visit(nodes, name, 0)
  }
  return out
}

/** 一行节点摘要：id <tag> .主题类 «文本» [绑定] */
export function describeAgentNode(node: StudioNode) {
  const classes = themeClassesOf(node)
  const bindings = bindingsOf(node)
  const text = node.text?.trim() ? ` «${clip(node.text, TEXT_CLIP)}»` : ''
  const href = node.attributes.href ? ` →${clip(node.attributes.href, 40)}` : ''
  return `${node.id} <${node.tag}>${classes.length ? ` .${classes.join('.')}` : ''}${text}${href}${bindings.length ? ` [${bindings.join(' ')}]` : ''}`
}

export function studioOutline(document: SiteDocument, pageId: string, options: { region?: StudioAgentRegion, rootId?: string, depth?: number, maxLines?: number } = {}) {
  const maxDepth = Math.min(Math.max(options.depth ?? 6, 1), 12)
  const maxLines = Math.min(Math.max(options.maxLines ?? 400, 20), 800)
  let nodes = walk(document, pageId, options.region ?? 'page')
  if (options.rootId) {
    const root = nodes.find(item => item.node.id === options.rootId)
    if (!root) {
      throw new Error(`节点不存在或不在所选区域：${options.rootId}`)
    }
    const inside = new Set<StudioNode>()
    const collect = (node: StudioNode) => {
      inside.add(node)
      node.children.forEach(collect)
    }
    collect(root.node)
    nodes = nodes.filter(item => inside.has(item.node)).map(item => ({ ...item, depth: item.depth - root.depth }))
  }
  const lines: string[] = []
  let hidden = 0
  let currentRegion = ''
  for (const item of nodes) {
    if (item.depth >= maxDepth) {
      hidden++
      continue
    }
    if (!options.rootId && item.region !== currentRegion) {
      currentRegion = item.region
      lines.push(`# ${item.region === 'page' ? '页面' : item.region === 'header' ? '页头（全站共享）' : '页脚（全站共享）'}`)
    }
    const deeper = item.depth === maxDepth - 1 && item.node.children.length ? ` (+${item.node.children.length} 子节点，加深 depth 或用 rootId 展开)` : ''
    lines.push(`${'  '.repeat(item.depth)}${describeAgentNode(item.node)}${deeper}`)
  }
  return { lines: lines.slice(0, maxLines), truncated: lines.length > maxLines, nodeCount: nodes.length, hiddenByDepth: hidden }
}

export type StudioFindField = 'any' | 'text' | 'class' | 'tag' | 'attr' | 'binding'

export function studioFind(document: SiteDocument, pageId: string, options: { query: string, field?: StudioFindField, region?: StudioAgentRegion, limit?: number }) {
  const query = options.query.trim().toLowerCase()
  if (!query) {
    throw new Error('query 不能为空')
  }
  const field = options.field ?? 'any'
  const limit = Math.min(Math.max(options.limit ?? 30, 1), 100)
  const parents = new Map<StudioNode, StudioNode | undefined>()
  const nodes = walk(document, pageId, options.region ?? 'all')
  nodes.forEach(item => parents.set(item.node, item.parent))
  const hit = (node: StudioNode) => {
    const has = (value: string) => value.toLowerCase().includes(query)
    const text = () => has(node.text ?? '')
    const klass = () => themeClassesOf(node).some(has)
    const tag = () => node.tag.toLowerCase() === query || node.type.toLowerCase() === query
    const attr = () => Object.entries(node.attributes).some(([key, value]) => !key.startsWith('data-yd-') && key !== 'class' && (has(key) || (!value.startsWith('data:') && has(value))))
    const binding = () => bindingsOf(node).some(has)
    switch (field) {
      case 'text': return text()
      case 'class': return klass()
      case 'tag': return tag()
      case 'attr': return attr()
      case 'binding': return binding()
      default: return text() || klass() || tag() || attr() || binding()
    }
  }
  const path = (node: StudioNode) => {
    const chain: string[] = []
    for (let current = parents.get(node); current; current = parents.get(current)) {
      const classes = themeClassesOf(current)
      chain.unshift(`${current.tag}${classes[0] ? `.${classes[0]}` : ''}`)
    }
    return chain.slice(-4).join(' > ')
  }
  const matches = nodes.filter(item => hit(item.node))
  return { total: matches.length, matches: matches.slice(0, limit).map(item => ({ id: item.node.id, region: item.region, path: path(item.node), summary: describeAgentNode(item.node) })) }
}

function classSummary(item: StudioStyleClass) {
  return {
    name: item.name,
    styles: clip(Object.entries(item.styles).map(([key, value]) => `${key}:${value}`).join('; '), 240),
    ...(item.states ? { states: Object.keys(item.states) } : {}),
    ...(item.responsive ? { responsive: Object.keys(item.responsive) } : {}),
  }
}

export function studioInspect(document: SiteDocument, pageId: string, nodeIds: string[]) {
  if (!nodeIds.length || nodeIds.length > 20) {
    throw new Error('nodeIds 需要 1–20 个')
  }
  const nodes = walk(document, pageId, 'all')
  const parents = new Map(nodes.map(item => [item.node.id, item]))
  const classes = new Map((document.theme.classes ?? []).map(item => [item.name, item]))
  const used = new Set<string>()
  const result = nodeIds.map((id) => {
    const located = parents.get(id)
    if (!located) {
      return { id, missing: '节点不存在，或在其他页面（切换页面后再读）' }
    }
    const { node } = located
    themeClassesOf(node).forEach(name => used.add(name))
    const ancestors: string[] = []
    for (let current = located.parent; current; current = parents.get(current.id)?.parent) {
      ancestors.unshift(describeAgentNode(current))
    }
    return {
      id,
      region: located.region,
      parentId: located.parent?.id ?? null,
      ancestors: ancestors.slice(-5),
      type: node.type,
      tag: node.tag,
      ...(node.text !== undefined ? { text: node.text } : {}),
      attributes: Object.fromEntries(Object.entries(node.attributes).map(([key, value]) => [key, agentValue(value)])),
      styles: node.styles,
      ...(node.responsive ? { responsive: node.responsive } : {}),
      childIds: node.children.map(child => child.id),
      children: node.children.slice(0, 12).map(describeAgentNode),
    }
  })
  return { nodes: result, classes: [...used].filter(name => classes.has(name)).map(name => classSummary(classes.get(name)!)) }
}

export type StudioThemeSection = 'all' | 'classes' | 'tokens' | 'content' | 'features' | 'pages'

export function studioThemeSummary(document: SiteDocument, options: { section?: StudioThemeSection, query?: string } = {}) {
  const section = options.section ?? 'all'
  const query = options.query?.trim().toLowerCase() ?? ''
  const want = (name: StudioThemeSection) => section === 'all' || section === name
  const out: Record<string, unknown> = {}
  if (want('classes')) {
    const usage = new Map<string, number>()
    const count = (nodes: StudioNode[]) => nodes.forEach((node) => {
      themeClassesOf(node).forEach(name => usage.set(name, (usage.get(name) ?? 0) + 1))
      count(node.children)
    })
    count(document.theme.header)
    count(document.theme.footer)
    document.pages.forEach(page => count(page.root))
    const all = (document.theme.classes ?? []).filter(item => !query || item.name.includes(query) || Object.entries(item.styles).some(([key, value]) => `${key}:${value}`.toLowerCase().includes(query)))
    const limit = section === 'classes' ? 200 : 60
    out.classes = { total: all.length, items: all.slice(0, limit).map(item => ({ ...classSummary(item), used: usage.get(item.name) ?? 0 })), more: all.length > limit ? '用 query 缩小范围' : undefined }
  }
  if (want('tokens')) {
    out.tokens = Object.fromEntries(Object.entries(document.theme.tokens).map(([key, value]) => [key, agentValue(value)]))
    out.fonts = (document.theme.fonts ?? []).map(font => font.family)
    out.keyframes = (document.theme.keyframes ?? []).map(item => item.name)
  }
  if (want('content')) {
    const content = document.features?.content
    out.content = content
      ? content.sections.map(item => ({
          code: item.code,
          title: item.title,
          fields: item.fields.map((field) => {
            const raw = field.key in content.values ? content.values[field.key] : field.defaultValue
            const value = Array.isArray(raw) ? `列表 ${raw.length} 条：${clip(JSON.stringify(raw), 200)}` : typeof raw === 'string' ? agentValue(raw) : raw
            return { key: field.key, label: field.label, type: field.type, ...(field.effect ? { effect: field.effect } : {}), ...(field.itemFields ? { itemFields: field.itemFields.map(sub => `${sub.key}:${sub.type}`) } : {}), value, filled: field.key in content.values }
          }),
        }))
      : '未声明站点内容模型'
  }
  if (want('features')) {
    const features = document.features ?? {}
    out.features = {
      navigation: features.navigation ? { appendAuto: features.navigation.appendAuto, items: features.navigation.items.map(item => ({ id: item.id, label: item.label, url: item.url, ...(item.requires ? { requires: item.requires } : {}), ...(item.children ? { children: item.children.map(child => `${child.label} → ${child.url}`) } : {}) })) } : '自动导航（站点页面 + 插件页面）',
      dataSources: (features.dataSources ?? []).map(item => ({ source: `adapter:${item.id}`, label: item.label, endpoint: item.endpoint })),
      publicSkin: features.publicSkin ? { chrome: features.publicSkin.chrome, pixel: features.publicSkin.pixel, classes: features.publicSkin.classes } : '未启用',
    }
  }
  if (want('pages')) {
    out.pages = document.pages.map(page => ({ id: page.id, name: page.name, path: page.path, ...(page.settings?.shell ? { shell: true } : {}), ...(page.settings?.hostRoutes ? { hostRoutes: page.settings.hostRoutes } : {}), ...(page.settings?.requires ? { requires: page.settings.requires } : {}) }))
  }
  return out
}
