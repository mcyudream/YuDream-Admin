import type { IFilterXSSOptions } from 'xss'
import filterXSS from 'xss'

/** UI 渲染协议，不授予工作区、MCP 或工具执行权限。 */
export interface YdAgentReference {
  id: string
  title: string
  url?: string
  path?: string
  excerpt: string
  startLine?: number
  endLine?: number
}

export interface YdAgentToolCall {
  id: string
  name: string
  label?: string
  status: 'queued' | 'running' | 'awaiting-approval' | 'success' | 'error' | 'cancelled'
  description?: string
  input?: string
  output?: string
  error?: string
  durationMs?: number
  /** 仅显示重试入口；执行由宿主处理 toolRetry 事件。 */
  retry?: boolean
}

export type YdAgentRenderBlock
  = | { type: 'markdown', text: string }
    | { type: 'reference', reference: YdAgentReference }
    | { type: 'tool', tool: YdAgentToolCall }

export interface YdAgentRenderDocument {
  version: 1
  blocks: YdAgentRenderBlock[]
}

export interface YdAgentReferenceEntry {
  reference: YdAgentReference
  number: number
}

const referenceMarker = /\[\[ref:([^\]\r\n]+)\]\]/g
const referencePrefix = '#yd-ref-'

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function optionalStrings(value: Record<string, unknown>, fields: string[]): boolean {
  return fields.every(field => value[field] === undefined || typeof value[field] === 'string')
}

function referenceValid(value: unknown): value is YdAgentReference {
  if (!isRecord(value) || typeof value.id !== 'string' || !value.id.trim() || typeof value.title !== 'string' || typeof value.excerpt !== 'string') {
    return false
  }
  if (!optionalStrings(value, ['url', 'path'])) {
    return false
  }
  for (const field of ['startLine', 'endLine']) {
    const line = value[field]
    if (line !== undefined && (typeof line !== 'number' || !Number.isSafeInteger(line) || line < 1)) {
      return false
    }
  }
  return value.startLine === undefined || value.endLine === undefined || (value.endLine as number) >= (value.startLine as number)
}

function toolValid(value: unknown): value is YdAgentToolCall {
  if (!isRecord(value) || typeof value.id !== 'string' || !value.id.trim() || typeof value.name !== 'string' || !value.name.trim()) {
    return false
  }
  if (typeof value.status !== 'string' || !['queued', 'running', 'awaiting-approval', 'success', 'error', 'cancelled'].includes(value.status)) {
    return false
  }
  if (!optionalStrings(value, ['label', 'description', 'input', 'output', 'error'])) {
    return false
  }
  if (value.retry !== undefined && typeof value.retry !== 'boolean') {
    return false
  }
  return value.durationMs === undefined || (typeof value.durationMs === 'number' && Number.isFinite(value.durationMs) && value.durationMs >= 0)
}

/** 校验 UI 文档版本及块结构；拒绝无效输入，不猜测类型或转换字符串 ID。 */
export function parseYdAgentRenderDocument(value: unknown): YdAgentRenderDocument | undefined {
  if (!isRecord(value) || value.version !== 1 || !Array.isArray(value.blocks)) {
    return undefined
  }
  const blocks: YdAgentRenderBlock[] = []
  for (const block of value.blocks) {
    if (!isRecord(block)) {
      return undefined
    }
    if (block.type === 'markdown' && typeof block.text === 'string') {
      blocks.push({ type: 'markdown', text: block.text })
    }
    else if (block.type === 'reference' && referenceValid(block.reference)) {
      blocks.push({ type: 'reference', reference: { ...block.reference } })
    }
    else if (block.type === 'tool' && toolValid(block.tool)) {
      blocks.push({ type: 'tool', tool: { ...block.tool } })
    }
    else {
      return undefined
    }
  }
  return { version: 1, blocks }
}

/** 只允许明确的 HTTP(S) 外链，工作区路径交给宿主的引用事件。 */
export function safeYdAgentReferenceUrl(value?: string): string | undefined {
  const url = value?.trim()
  if (!url || !/^https?:\/\//i.test(url) || Array.from(url).some(char => char.charCodeAt(0) <= 32 || char.charCodeAt(0) === 127)) {
    return undefined
  }
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : undefined
  }
  catch {
    return undefined
  }
}

/** 使用 Markdown 链接安全的片段编码，ID 仍保留字符串精度。 */
function encodeReferencePart(value: string): string {
  return encodeURIComponent(value).replace(/[!'()*]/g, char => `%${char.charCodeAt(0).toString(16).toUpperCase()}`)
}

export function ydAgentReferenceAnchor(id: string, scope?: string): string {
  return `${referencePrefix}${scope === undefined ? '' : `${encodeReferencePart(scope)}:`}${encodeReferencePart(id)}`
}

export function resolveYdAgentReferenceAnchor(anchor: string, scope?: string): string | undefined {
  const prefix = `${referencePrefix}${scope === undefined ? '' : `${encodeReferencePart(scope)}:`}`
  if (!anchor.startsWith(prefix)) {
    return undefined
  }
  try {
    return decodeURIComponent(anchor.slice(prefix.length))
  }
  catch {
    return undefined
  }
}

/** 只处理 Markdown 正文，示例代码中的引用标记与 HTML 保持原样。 */
function mapMarkdownProse(text: string, transform: (prose: string) => string): string {
  let fence: { char: string, length: number } | undefined
  return text.split(/(\r?\n)/).map((line) => {
    const delimiter = line.match(/^[ \t]{0,3}(`{3,}(?!`)|~{3,}(?!~))(.*)$/)
    if (fence) {
      if (delimiter && delimiter[1][0] === fence.char && delimiter[1].length >= fence.length && !delimiter[2].trim()) {
        fence = undefined
      }
      return line
    }
    if (delimiter) {
      fence = { char: delimiter[1][0], length: delimiter[1].length }
      return line
    }
    const runs = line.matchAll(/`+/g)
    let opening: { index: number, length: number } | undefined
    let output = ''
    let offset = 0
    for (const match of runs) {
      if (!opening) {
        opening = { index: match.index, length: match[0].length }
      }
      else if (match[0].length === opening.length) {
        const end = match.index + match[0].length
        output += transform(line.slice(offset, opening.index)) + line.slice(opening.index, end)
        offset = end
        opening = undefined
      }
    }
    return output + transform(line.slice(offset))
  }).join('')
}

/** 按正文标记或引用块的首次出现顺序编号，重复 ID 沿用同一来源。 */
export function buildYdAgentReferenceIndex(document: YdAgentRenderDocument): Map<string, YdAgentReferenceEntry> {
  const references = new Map<string, YdAgentReference>()
  for (const block of document.blocks) {
    if (block.type === 'reference' && !references.has(block.reference.id)) {
      references.set(block.reference.id, block.reference)
    }
  }
  const index = new Map<string, YdAgentReferenceEntry>()
  const add = (id: string) => {
    const reference = references.get(id)
    if (reference && !index.has(id)) {
      index.set(id, { reference, number: index.size + 1 })
    }
  }
  for (const block of document.blocks) {
    if (block.type === 'reference') {
      add(block.reference.id)
    }
    else if (block.type === 'markdown') {
      mapMarkdownProse(block.text, (prose) => {
        for (const match of prose.matchAll(referenceMarker)) {
          add(match[1].trim())
        }
        return prose
      })
    }
  }
  return index
}

/** 保留 Markdown 格式；已知引用转换为受控链接，HTML 安全由局部 sanitize 处理。 */
export function renderYdAgentMarkdown(text: string, references: ReadonlyMap<string, YdAgentReferenceEntry>, scope?: string): string {
  return mapMarkdownProse(text, (prose) => {
    return prose.replace(referenceMarker, (marker, rawId: string) => {
      const id = rawId.trim()
      const entry = references.get(id)
      return entry ? `[${entry.number}](${ydAgentReferenceAnchor(id, scope)})` : marker
    })
  })
}

function escapeAttribute(value: string): string {
  return value.replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

const answerSanitizerOptions: IFilterXSSOptions = {
  whiteList: {
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
  },
  css: false,
  stripIgnoreTag: true,
  stripIgnoreTagBody: ['script', 'style', 'iframe', 'object', 'embed', 'template'],
  onTagAttr(tag, name, value) {
    if (name === 'class') {
      const classes = value.split(/\s+/).filter(className => /^(?:hljs-[\w-]+|md-editor-[\w-]+|language-[\w-]+)$/.test(className)).join(' ')
      return classes ? `class="${escapeAttribute(classes)}"` : ''
    }
    if (tag === 'a' && name === 'href') {
      const id = resolveYdAgentReferenceAnchor(value)
      if (id !== undefined) {
        return `href="${escapeAttribute(value)}"`
      }
      const url = safeYdAgentReferenceUrl(value)
      return url ? `href="${escapeAttribute(url)}" target="_blank" rel="noopener noreferrer"` : ''
    }
    if (tag === 'img' && name === 'src') {
      const url = safeYdAgentReferenceUrl(value)
      return url ? `src="${escapeAttribute(url)}"` : ''
    }
  },
}

/** 仅用于当前回答的 Markdown 预览，不改变全局编辑器配置。 */
export function sanitizeYdAgentHtml(html: string): string {
  return filterXSS(html, answerSanitizerOptions)
}
