import filterXSS from 'xss'

const markdownWhitelist = {
  a: ['href', 'title'],
  p: [],
  br: [],
  hr: [],
  h1: [],
  h2: [],
  h3: [],
  h4: [],
  h5: [],
  h6: [],
  ul: [],
  ol: ['start'],
  li: [],
  strong: [],
  b: [],
  em: [],
  i: [],
  s: [],
  del: [],
  blockquote: [],
  img: ['src', 'alt', 'title'],
  table: [],
  thead: [],
  tbody: [],
  tr: [],
  th: ['colspan', 'rowspan'],
  td: ['colspan', 'rowspan'],
  code: ['class', 'language'],
  pre: ['class'],
  span: ['class'],
  div: ['class'],
  details: ['class', 'open', 'data-line'],
  summary: ['class'],
}
const svgWhitelist = {
  svg: ['xmlns', 'viewbox', 'width', 'height', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule', 'class'],
  g: ['fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule', 'class'],
  path: ['d', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'fill-rule', 'class'],
  circle: ['cx', 'cy', 'r', 'fill', 'stroke', 'stroke-width', 'class'],
  line: ['x1', 'y1', 'x2', 'y2', 'stroke', 'stroke-width', 'stroke-linecap', 'class'],
  rect: ['x', 'y', 'width', 'height', 'rx', 'ry', 'fill', 'stroke', 'stroke-width', 'class'],
  polyline: ['points', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'class'],
  polygon: ['points', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin', 'class'],
}
/**
 * md-editor 用这些 data-* 属性把 mermaid 块标记为「待渲染」并做修订号过滤；
 * 属性缺失时渲染器会静默跳过该块，所以必须放行——值域固定，逐项校验后再输出。
 */
const mermaidBlockAttributes: Record<string, RegExp> = {
  'data-mermaid-theme': /^(?:light|dark)$/,
  'data-mermaid-block': /^\d{1,7}$/,
  'data-mermaid-revision': /^\d{1,7}$/,
  'data-closed': /^(?:true|false)$/,
  'data-line': /^\d{1,7}$/,
}
const safeColor = /^(?:#[\da-f]{3,8}|[a-z]{1,20})$/i
const pathCharacters = new Set('0123456789 .,eE+-MmLlHhVvCcSsQqTtAaZz')
const pointCharacters = new Set('0123456789 .,eE+-')

function finiteNumber(value: string): boolean {
  return value.length > 0 && value.length <= 32 && Number.isFinite(Number(value))
}

function safeNumericList(value: string, characters: Set<string>): boolean {
  return value.length <= 4096 && [...value].every(character => characters.has(character))
}

function safeSvgAttribute(tag: string, name: string, value: string): string | undefined {
  if (name === 'xmlns') {
    return tag === 'svg' && value === 'http://www.w3.org/2000/svg' ? value : undefined
  }
  if (name === 'class') {
    return value.split(/\s+/).filter(className => /^(?:md-editor-[\w-]+|lucide(?:-[\w-]+)?)$/.test(className)).join(' ') || undefined
  }
  if (['fill', 'stroke'].includes(name)) {
    return safeColor.test(value) ? value : undefined
  }
  if (['width', 'height'].includes(name)) {
    const unitless = finiteNumber(value)
    const unitLength = value.match(/^(.{1,32})(?:px|em|rem|%)$/i)?.[1]
    return unitless || (unitLength !== undefined && finiteNumber(unitLength)) ? value : undefined
  }
  if (['stroke-width', 'cx', 'cy', 'r', 'x', 'y', 'x1', 'y1', 'x2', 'y2', 'rx', 'ry'].includes(name)) {
    return finiteNumber(value) ? value : undefined
  }
  if (name === 'viewbox') {
    const values = value.trim().split(/\s+/)
    return values.length === 4 && values.every(finiteNumber) ? value : undefined
  }
  if (name === 'd') {
    return safeNumericList(value, pathCharacters) ? value : undefined
  }
  if (name === 'points') {
    return safeNumericList(value, pointCharacters) ? value : undefined
  }
  if (name === 'stroke-linecap') {
    return /^(?:butt|round|square)$/i.test(value) ? value : undefined
  }
  if (name === 'stroke-linejoin') {
    return /^(?:miter|round|bevel)$/i.test(value) ? value : undefined
  }
  if (name === 'fill-rule') {
    return /^(?:nonzero|evenodd)$/i.test(value) ? value : undefined
  }
  return undefined
}

const markdownSanitizerOptions = {
  whiteList: { ...markdownWhitelist, ...svgWhitelist },
  css: false,
  stripIgnoreTagBody: ['script', 'style', 'iframe', 'object', 'embed', 'template', 'foreignobject'],
  onTagAttr(tag: string, name: string, value: string) {
    if (tag === 'a' && name === 'href' && /^wiki:\/\/(?:[A-Za-z0-9_.!~*'()-]|%[\da-f]{2})*$/i.test(value)) {
      try {
        decodeURIComponent(value.slice('wiki://'.length))
        return `href="${value.replace(/&/g, '&amp;').replace(/"/g, '&quot;')}"`
      }
      catch {
        return ''
      }
    }
    if (tag === 'details' && name === 'data-line') {
      return finiteNumber(value) && Number(value) >= 0 ? `${name}="${value}"` : ''
    }
    // mermaid 触发标记（div 待渲染块 / p 命中缓存块）：渲染器按这些属性筛选，丢了就永远不渲染。
    if ((tag === 'div' || tag === 'p') && Object.hasOwn(mermaidBlockAttributes, name)) {
      return mermaidBlockAttributes[name].test(value) ? `${name}="${value}"` : ''
    }
    if (tag === 'p' && name === 'data-processed') {
      return value === '' ? 'data-processed=""' : ''
    }
    if (tag === 'p' && name === 'data-content') {
      return `data-content="${value.replace(/&/g, '&amp;').replace(/"/g, '&quot;')}"`
    }
    if (Object.hasOwn(svgWhitelist, tag)) {
      const safe = safeSvgAttribute(tag, name, value)
      return safe === undefined ? '' : `${name}="${safe.replace(/&/g, '&amp;').replace(/"/g, '&quot;')}"`
    }
    return undefined
  },
}

/** Render user-authored Markdown as safe HTML in both edit previews and read-only views. */
export function sanitizeYdMarkdownHtml(html: string): string {
  return filterXSS(html, markdownSanitizerOptions)
}

export function shouldUploadYdMarkdownImages(readonly: boolean, disabled: boolean, fileCount: number): boolean {
  return !readonly && !disabled && fileCount > 0
}
