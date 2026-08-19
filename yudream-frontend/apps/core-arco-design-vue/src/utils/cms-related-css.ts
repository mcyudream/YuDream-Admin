export interface CmsCssSelectionSnapshot {
  tagName?: string
  classes: string[]
  attributes: Record<string, unknown>
}

interface CssNode {
  prelude: string
  body: string
  children?: CssNode[]
  raw?: boolean
}

function normalize(value: string) {
  return value.replace(/\/\*[^*]*\*+(?:[^/*][^*]*\*+)*/g, '').replace(/\s+/g, ' ').trim()
}

function findNext(source: string, char: string, from: number) {
  let quote = ''
  for (let index = from; index < source.length; index += 1) {
    const current = source[index]
    const next = source[index + 1]
    if (!quote && current === '/' && next === '*') {
      const end = source.indexOf('*/', index + 2)
      if (end < 0) throw new Error('CSS 注释未闭合')
      index = end + 1
      continue
    }
    if (quote) {
      if (current === '\\') index += 1
      else if (current === quote) quote = ''
      continue
    }
    if (current === '"' || current === '\'') {
      quote = current
      continue
    }
    if (current === char) return index
  }
  return -1
}

function matchingBrace(source: string, open: number) {
  let depth = 0
  let quote = ''
  for (let index = open; index < source.length; index += 1) {
    const current = source[index]
    const next = source[index + 1]
    if (!quote && current === '/' && next === '*') {
      const end = source.indexOf('*/', index + 2)
      if (end < 0) throw new Error('CSS 注释未闭合')
      index = end + 1
      continue
    }
    if (quote) {
      if (current === '\\') index += 1
      else if (current === quote) quote = ''
      continue
    }
    if (current === '"' || current === '\'') {
      quote = current
      continue
    }
    if (current === '{') depth += 1
    if (current === '}' && --depth === 0) return index
  }
  throw new Error('CSS 花括号未闭合')
}

function parse(source: string): CssNode[] {
  const nodes: CssNode[] = []
  let cursor = 0
  while (cursor < source.length) {
    const open = findNext(source, '{', cursor)
    const semicolon = findNext(source, ';', cursor)
    if (semicolon >= 0 && (open < 0 || semicolon < open)) {
      const prelude = source.slice(cursor, semicolon).trim()
      if (prelude) nodes.push({ prelude, body: '', raw: true })
      cursor = semicolon + 1
      continue
    }
    if (open < 0) {
      if (source.slice(cursor).replace(/\/\*[^*]*\*+(?:[^/*][^*]*\*+)*/g, '').trim()) throw new Error('CSS 规则缺少花括号')
      break
    }
    const prelude = source.slice(cursor, open).trim()
    if (!prelude) throw new Error('CSS 规则缺少选择器')
    const close = matchingBrace(source, open)
    const body = source.slice(open + 1, close)
    const children = prelude.startsWith('@') && findNext(body, '{', 0) >= 0 ? parse(body) : undefined
    nodes.push({ prelude, body, children })
    cursor = close + 1
  }
  return nodes
}

function render(nodes: CssNode[], level = 0): string {
  const indent = '  '.repeat(level)
  return nodes.map((node) => {
    if (node.raw) return `${indent}${node.prelude};`
    const body = node.children ? render(node.children, level + 1) : node.body.trim()
    if (node.children) return `${indent}${node.prelude.trim()} {\n${body}\n${indent}}`
    return `${indent}${node.prelude.trim()} {${body ? `\n${'  '.repeat(level + 1)}${body}\n${indent}` : ''}}`
  }).join('\n\n')
}

function selectorMatches(prelude: string, snapshot: CmsCssSelectionSnapshot) {
  return prelude.split(',').some((item) => {
    const selector = item.trim().replace(/::?[\w-]+(?:\([^)]*\))?/g, '').replace(/\s+/g, ' ')
    if (!selector) return false
    const id = String(snapshot.attributes.id || '').trim()
    if (id && new RegExp(`(^|[^a-zA-Z0-9_-])#${escapeRegExp(id)}(?=$|[^a-zA-Z0-9_-])`).test(selector)) return true
    if (snapshot.classes.some(item => new RegExp(`(^|[^a-zA-Z0-9_-])\\.${escapeRegExp(item)}(?=$|[^a-zA-Z0-9_-])`).test(selector))) return true
    if (snapshot.tagName && new RegExp(`(^|[\\s>+~(])${escapeRegExp(snapshot.tagName)}(?=$|[\\s.#:[>+~,)])`, 'i').test(selector)) return true
    if (Object.keys(snapshot.attributes).some(key => selector.includes(`[${key}`))) return true
    if (typeof document === 'undefined') return false
    try {
      const element = document.createElement(snapshot.tagName || 'div')
      if (id) element.id = id
      snapshot.classes.forEach(item => element.classList.add(item))
      Object.entries(snapshot.attributes).forEach(([key, value]) => {
        if (value !== undefined && value !== null) element.setAttribute(key, String(value))
      })
      return element.matches(selector)
    }
    catch {
      return false
    }
  })
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function matchingNodes(nodes: CssNode[], snapshot: CmsCssSelectionSnapshot): CssNode[] {
  return nodes.flatMap((node) => {
    if (node.children) {
      const children = matchingNodes(node.children, snapshot)
      return children.length ? [{ ...node, children }] : []
    }
    return !node.raw && selectorMatches(node.prelude, snapshot) ? [node] : []
  })
}

function declarations(body: string) {
  const result = new Map<string, string>()
  let cursor = 0
  let quote = ''
  let depth = 0
  let comment = false
  const push = (value: string) => {
    const index = value.indexOf(':')
    if (index <= 0) return
    const rawKey = value.slice(0, index).trim()
    const key = rawKey.startsWith('--') ? rawKey : rawKey.toLowerCase()
    if (key) result.set(key, value.trim())
  }
  for (let index = 0; index < body.length; index += 1) {
    const current = body[index]
    const next = body[index + 1]
    if (comment) {
      if (current === '*' && next === '/') {
        comment = false
        index += 1
      }
      continue
    }
    if (!quote && current === '/' && next === '*') {
      comment = true
      index += 1
      continue
    }
    if (quote) {
      if (current === '\\') index += 1
      else if (current === quote) quote = ''
    }
    else if (current === '"' || current === '\'') quote = current
    else if (current === '(' || current === '[') depth += 1
    else if (current === ')' || current === ']') depth -= 1
    else if (current === ';' && depth === 0) {
      push(body.slice(cursor, index))
      cursor = index + 1
    }
  }
  push(body.slice(cursor))
  return result
}

function mergeDeclarations(base: string, addition: string) {
  const merged = declarations(base)
  declarations(addition).forEach((value, key) => merged.set(key, value))
  return [...merged.values()].join(';\n')
}

function mergeNodes(base: CssNode[], addition: CssNode[]): CssNode[] {
  const result: CssNode[] = base.map(node => ({ ...node, children: node.children ? mergeNodes(node.children, []) : undefined }))
  addition.forEach((incoming) => {
    if (incoming.raw) {
      if (!result.some(node => node.raw && normalize(node.prelude) === normalize(incoming.prelude))) result.push(incoming)
      return
    }
    const previous = result.at(-1)
    if (previous && !previous.raw && normalize(previous.prelude) === normalize(incoming.prelude)) {
      if (incoming.children && previous.children) previous.children = mergeNodes(previous.children, incoming.children)
      else if (!incoming.children && !previous.children) previous.body = mergeDeclarations(previous.body, incoming.body)
      else result.push(incoming)
      return
    }
    result.push(incoming)
  })
  return result
}

function validateReplacement(original: CssNode[], edited: CssNode[]) {
  const allowed = new Set(original.filter(node => !node.raw).map(node => normalize(node.prelude)))
  edited.filter(node => !node.raw).forEach((node) => {
    if (node.children) validateReplacement(original.find(item => normalize(item.prelude) === normalize(node.prelude))?.children || [], node.children)
    else if (!allowed.has(normalize(node.prelude))) throw new Error('关联 CSS 只能编辑声明，不能修改选择器')
  })
}

function replaceNodes(base: CssNode[], original: CssNode[], edited: CssNode[]): CssNode[] {
  const consumedOriginal = new Set<number>()
  const consumedEdited = new Set<number>()
  return base.flatMap((node) => {
    const originalIndex = original.findIndex((item, index) => !consumedOriginal.has(index) && normalize(item.prelude) === normalize(node.prelude))
    if (originalIndex < 0) return [node]
    consumedOriginal.add(originalIndex)
    const originalNode = original[originalIndex]
    const editedIndex = edited.findIndex((item, index) => !consumedEdited.has(index) && normalize(item.prelude) === normalize(originalNode.prelude))
    if (editedIndex < 0) return []
    consumedEdited.add(editedIndex)
    const editedNode = edited[editedIndex]
    if (node.children && editedNode.children) {
      const children = replaceNodes(node.children, originalNode.children || [], editedNode.children)
      return children.length ? [{ ...node, children }] : []
    }
    return [{ ...node, body: editedNode.body }]
  })
}

export function extractRelatedCss(css: string, snapshot: CmsCssSelectionSnapshot) {
  if (!css.trim()) return ''
  return render(matchingNodes(parse(css), snapshot)).trim()
}

export function replaceRelatedCss(css: string, originalRelatedCss: string, editedRelatedCss: string) {
  const base = parse(css)
  const original = parse(originalRelatedCss)
  const edited = parse(editedRelatedCss)
  validateReplacement(original, edited)
  return render(replaceNodes(base, original, edited)).trim()
}

export function mergeCss(existing: string, addition: string) {
  if (!addition.trim()) return existing
  return render(mergeNodes(parse(existing || ''), parse(addition))).trim()
}
