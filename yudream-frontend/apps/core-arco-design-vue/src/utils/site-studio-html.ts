import type { Declaration, Rule } from 'postcss'
import type { SiteDocument, StudioCompileOptions, StudioNode } from '../types/site-studio'
import { parse } from 'postcss'
import { STUDIO_BREAKPOINTS, STUDIO_LIMITS, STUDIO_VOID_TAGS, studioAssert, validateStudioDocument, validateStudioNodes, validateStudioStyle } from './site-studio-validation'

export function escapeStudioHtml(value: string) {
  return value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', '\'': '&#39;' })[char]!)
}
function selector(id: string) {
  return `[data-studio-id="${id}"]`
}
function declarations(styles: Record<string, string>) {
  return Object.entries(styles).map(([key, value]) => `${key}:${value}`).join(';')
}
function renderNode(node: StudioNode): string {
  const attributes: Record<string, string> = { ...node.attributes, 'data-studio-id': node.id, 'data-studio-type': node.type }
  if (attributes.target === '_blank') {
    attributes.rel = [...new Set(`${attributes.rel ?? ''} noopener noreferrer`.trim().split(/\s+/))].join(' ')
  }
  const attrs = Object.entries(attributes).map(([key, value]) => ` ${key}="${escapeStudioHtml(value)}"`).join('')
  if (STUDIO_VOID_TAGS.has(node.tag)) {
    return `<${node.tag}${attrs}>`
  }
  return `<${node.tag}${attrs}>${node.text !== undefined ? escapeStudioHtml(node.text) : node.children.map(renderNode).join('')}</${node.tag}>`
}
function renderCss(nodes: StudioNode[], scope = '') {
  const base: string[] = []
  const tablet: string[] = []
  const mobile: string[] = []
  const rule = (node: StudioNode, styles: Record<string, string>) => `${scope}${selector(node.id)}{${declarations(styles)}}`
  const visit = (node: StudioNode) => {
    if (Object.keys(node.styles).length) {
      base.push(rule(node, node.styles))
    }
    if (node.responsive?.tablet && Object.keys(node.responsive.tablet).length) {
      tablet.push(rule(node, node.responsive.tablet))
    }
    if (node.responsive?.mobile && Object.keys(node.responsive.mobile).length) {
      mobile.push(rule(node, node.responsive.mobile))
    }
    node.children.forEach(visit)
  }
  nodes.forEach(visit)
  return [...base, ...(tablet.length ? [`@media (max-width: ${STUDIO_BREAKPOINTS.tablet}px){${tablet.join('\n')}}`] : []), ...(mobile.length ? [`@media (max-width: ${STUDIO_BREAKPOINTS.mobile}px){${mobile.join('\n')}}`] : [])].join('\n')
}
export function compileStudioNodes(nodes: StudioNode[]): {
  html: string
  css: string
} {
  validateStudioNodes(nodes)
  return { html: nodes.map(renderNode).join(''), css: renderCss(nodes) }
}
export function compileStudioPage(document: SiteDocument, pageId: string, options: StudioCompileOptions = {}): {
  html: string
  css: string
} {
  validateStudioDocument(document)
  const page = document.pages.find(page => page.id === pageId)
  studioAssert(page, '页面不存在')
  const scope = `[data-studio-document="${document.id}"]`
  const chrome = options.includeChrome !== false
  const tokens = Object.fromEntries(Object.entries(document.theme.tokens).map(([key, value]) => [key.startsWith('--') ? key : `--${key}`, value]))
  const html = `<div data-studio-document="${document.id}">${chrome ? `<header data-studio-region="header">${document.theme.header.map(renderNode).join('')}</header>` : ''}<main data-studio-region="page">${page.root.map(renderNode).join('')}</main>${chrome ? `<footer data-studio-region="footer">${document.theme.footer.map(renderNode).join('')}</footer>` : ''}</div>`
  return { html, css: [`${scope}{${declarations(tokens)}}`, renderCss([...(chrome ? document.theme.header : []), ...page.root, ...(chrome ? document.theme.footer : [])], `${scope} `)].filter(Boolean).join('\n') }
}
export function exportStudioStaticHtml(document: SiteDocument, pageId: string, options: StudioCompileOptions = {}) {
  const output = compileStudioPage(document, pageId, options)
  const title = document.pages.find(page => page.id === pageId)!.name
  // No execution bridge, external stylesheet, or engine project JSON is exported.
  return `<!doctype html>\n<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1"><meta name="referrer" content="no-referrer"><title>${escapeStudioHtml(title)}</title><style>${output.css}</style></head><body>${output.html}</body></html>`
}
interface CssEntry {
  rule: Rule
  declarations: Declaration[]
  breakpoint?: 'tablet' | 'mobile'
  order: number
}
function cssEntries(css: string): CssEntry[] {
  studioAssert(new TextEncoder().encode(css).length <= STUDIO_LIMITS.bytes && !/[\\<>]/.test(css), 'CSS 太大或包含不允许的转义/HTML')
  const root = parse(css)
  root.walkAtRules((at) => {
    studioAssert(at.name.toLowerCase() === 'media', '只支持可映射至模型断点的 @media，禁止外部导入或其他 at-rule')
    studioAssert(at.parent?.type === 'root' && /^\(\s*max-width\s*:\s*(?:992|768)px\s*\)$/i.test(at.params), '仅支持 992px/tablet 与 768px/mobile 的 max-width 断点')
  })
  root.walkDecls((decl) => {
    studioAssert(decl.parent?.type === 'rule', 'CSS 声明必须位于规则内')
    validateStudioStyle(decl.prop, decl.value)
  })
  const entries: CssEntry[] = []
  root.walkRules((rule) => {
    studioAssert(rule.parent?.type === 'root' || rule.parent?.type === 'atrule', '不支持嵌套 CSS 选择器')
    const declarations = rule.nodes.filter((node): node is Declaration => node.type === 'decl')
    for (const item of rule.selectors) {
      // The AST represents per-node base/responsive declarations, not pseudo states.
      studioAssert(item === ':root' || !/[:{}\\]/.test(item), '伪类/伪元素不能无损映射至站点 AST')
    }
    entries.push({ rule, declarations, order: entries.length, ...(rule.parent?.type === 'atrule' ? { breakpoint: /768/.test(rule.parent.params) ? 'mobile' as const : 'tablet' as const } : {}) })
  })
  return entries
}
function specificity(selector: string) {
  const withoutAttributes = selector.replace(/\[[^\]]*\]/g, '')
  return [(withoutAttributes.match(/#[\w-]+/g) ?? []).length, (selector.match(/\[[^\]]*\]/g) ?? []).length + (withoutAttributes.match(/\.[\w-]+/g) ?? []).length, (withoutAttributes.replace(/[#.][\w-]+/g, '').match(/(?:^|[\s>+~])[a-z][\w-]*/gi) ?? []).length]
}
function compare(a: number[], b: number[]) {
  for (let i = 0; i < a.length; i++) {
    if (a[i] !== b[i]) {
      return a[i]! - b[i]!
    }
  }
  return 0
}
function extractStyles(element: Element, entries: CssEntry[], inline: string | null) {
  const buckets: Record<string, Record<string, {
    value: string
    rank: number[]
  }>> = { desktop: {}, tablet: {}, mobile: {} }
  for (const entry of entries) {
    let best: number[] | undefined
    for (const item of entry.rule.selectors) {
      if (item === ':root') {
        studioAssert(entry.declarations.every(decl => decl.prop.startsWith('--')), ':root 只支持主题变量')
        continue
      }
      let matches: boolean
      try {
        matches = element.matches(item)
      }
      catch {
        throw new Error('不支持的 CSS 选择器')
      }
      if (matches) {
        const score = specificity(item)
        if (!best || compare(score, best) > 0) {
          best = score
        }
      }
    }
    if (!best) {
      continue
    }
    const bucket = buckets[entry.breakpoint ?? 'desktop']!
    for (const decl of entry.declarations) {
      const rank = [decl.important ? 1 : 0, 0, ...best, entry.order]
      if (!bucket[decl.prop] || compare(rank, bucket[decl.prop]!.rank) >= 0) {
        bucket[decl.prop] = { value: `${decl.value}${decl.important ? ' !important' : ''}`, rank }
      }
    }
  }
  if (inline) {
    const root = parse(`a{${inline}}`)
    studioAssert(root.nodes.length === 1 && root.first?.type === 'rule', '无效内联样式')
    for (const decl of root.first.nodes) {
      studioAssert(decl.type === 'decl' || decl.type === 'comment', '内联 CSS 只能有声明')
      if (decl.type !== 'decl') {
        continue
      }
      validateStudioStyle(decl.prop, decl.value)
      const rank = [decl.important ? 1 : 0, 1, 0, 0, 0, 0]
      const bucket = buckets.desktop!
      if (!bucket[decl.prop] || compare(rank, bucket[decl.prop]!.rank) >= 0) {
        bucket[decl.prop] = { value: `${decl.value}${decl.important ? ' !important' : ''}`, rank }
      }
    }
  }
  const values = (name: string) => Object.fromEntries(Object.entries(buckets[name]!).map(([key, item]) => [key, item.value]))
  return { styles: values('desktop'), ...(Object.keys(buckets.tablet!).length || Object.keys(buckets.mobile!).length ? { responsive: { ...(Object.keys(buckets.tablet!).length ? { tablet: values('tablet') } : {}), ...(Object.keys(buckets.mobile!).length ? { mobile: values('mobile') } : {}) } } : {}) }
}
/** DOMParser is intentionally required: regex is never used to sanitize HTML. */
export function parseStudioHtml(html: string, css = '', previous: StudioNode[] = []): StudioNode[] {
  studioAssert(typeof DOMParser !== 'undefined', 'HTML 导入需要浏览器 DOMParser')
  studioAssert(new TextEncoder().encode(html).length <= STUDIO_LIMITS.bytes, 'HTML 超过 2 MiB 限制')
  validateStudioNodes(previous)
  const parsed = new DOMParser().parseFromString(html, 'text/html')
  studioAssert(!parsed.querySelector('script,iframe,object,embed,svg,math,link,base,template,form,input,textarea,select'), 'HTML 含不允许的活动内容')
  const embeddedStyles = [...parsed.querySelectorAll('style')]
  const entries = cssEntries([css, ...embeddedStyles.map(style => style.textContent ?? '')].join('\n'))
  embeddedStyles.forEach(style => style.remove())
  const previousById = new Map<string, StudioNode>()
  const index = (nodes: StudioNode[]) => nodes.forEach((node) => {
    previousById.set(node.id, node)
    index(node.children)
  })
  index(previous)
  const ids = new Set<string>()
  let count = 0
  const uniqueId = (value?: string | null) => {
    const id = value || crypto.randomUUID()
    studioAssert(!ids.has(id), 'HTML 含重复 data-studio-id')
    ids.add(id)
    return id
  }
  const convert = (element: Element, depth: number): StudioNode => {
    studioAssert(depth <= STUDIO_LIMITS.depth && ++count <= STUDIO_LIMITS.nodes, 'HTML 节点数量或深度超限')
    const id = uniqueId(element.getAttribute('data-studio-id'))
    const previousNode = previousById.get(id)
    const attributes = Object.fromEntries([...element.attributes].filter(attr => !['style', 'data-studio-id', 'data-studio-type'].includes(attr.name)).map(attr => [attr.name, attr.value]))
    const node: StudioNode = { id, type: element.getAttribute('data-studio-type') || previousNode?.type || element.tagName.toLowerCase(), tag: element.tagName.toLowerCase(), attributes, ...extractStyles(element, entries, element.getAttribute('style')), children: [] }
    if (element.children.length === 0) {
      const text = element.textContent ?? ''
      if (!STUDIO_VOID_TAGS.has(node.tag) && (text || previousNode?.text !== undefined || !previousNode)) {
        node.text = text
      }
    }
    else {
      for (const child of element.childNodes) {
        if (child.nodeType === 1) {
          node.children.push(convert(child as Element, depth + 1))
        }
        else if (child.nodeType === 3 && child.textContent) {
          studioAssert(++count <= STUDIO_LIMITS.nodes, 'HTML 节点数超限')
          node.children.push({ id: uniqueId(), type: 'text', tag: 'span', text: child.textContent, attributes: {}, styles: {}, children: [] })
        }
        else {
          studioAssert(child.nodeType === 8, '不支持的 HTML 节点')
        }
      }
    }
    return node
  }
  // The compiled page shell is presentation only and never becomes a second AST root.
  const shell = parsed.body.querySelector('[data-studio-document]')
  const source = shell?.querySelector('[data-studio-region="page"]') ?? shell ?? parsed.body
  const nodes: StudioNode[] = []
  for (const child of source.childNodes) {
    if (child.nodeType === 1) {
      nodes.push(convert(child as Element, 1))
    }
    else if (child.nodeType === 3 && child.textContent?.trim()) {
      nodes.push({ id: uniqueId(), type: 'text', tag: 'span', text: child.textContent, attributes: {}, styles: {}, children: [] })
    }
    else {
      studioAssert(child.nodeType === 3 || child.nodeType === 8, '不支持的 HTML 节点')
    }
  }
  validateStudioNodes(nodes)
  return nodes
}
