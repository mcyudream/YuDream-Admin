export type CmsTemplateObject = Record<string, any>

export function resolveCmsTemplatePath(path: string, context: CmsTemplateObject = {}) {
  return path.split('.').reduce<any>((target, key) => {
    if (Array.isArray(target) && key === 'count') {
      return target.length
    }
    return target?.[key]
  }, context)
}

export function resolveCmsTemplateRows(path: string, context: CmsTemplateObject = {}) {
  const value = resolveCmsTemplatePath(path, context)
  return Array.isArray(value) ? value : []
}

export function renderCmsVariables(value: string, localContext: CmsTemplateObject = {}, rootContext: CmsTemplateObject = {}) {
  const context = { ...rootContext, ...localContext }
  return value.replace(/\{\{\s*([\w.]+)\s*}}/g, (_, path: string) => escapeCmsHtml(String(resolveCmsTemplatePath(path, context) ?? '')))
}

/** `data-yb-for="item in knowledge.latest"` 的安全解析结果。 */
export function parseCmsTemplateFor(value?: string | null) {
  const matched = String(value || '').trim().match(/^([A-Za-z_]\w*)\s+in\s+([\w.]+)$/)
  return matched ? { itemName: matched[1], path: matched[2] } : null
}

/** 读取 data-yb-limit，支持固定数字或 {{site.xxx}} 变量；最大值由调用方控制。 */
export function resolveCmsTemplateLimit(value: string | null | undefined, context: CmsTemplateObject = {}, fallback?: number) {
  const raw = String(value || '').trim()
  if (!raw) {
    return fallback
  }
  const variable = raw.match(/^\{\{\s*([\w.]+)\s*}}$/)
  const resolved = variable ? resolveCmsTemplatePath(variable[1], context) : raw
  const numeric = Number(resolved)
  return Number.isInteger(numeric) && numeric >= 0 ? numeric : fallback
}

/**
 * 供 data-yb-if 使用的受限条件表达式：支持路径真假、!路径、==/!= 与数字比较。
 * 不执行 JavaScript，避免模板内容通过 eval 触达公开页运行时。
 */
export function evaluateCmsTemplateCondition(expression: string | null | undefined, context: CmsTemplateObject = {}): boolean {
  const source = String(expression || '').trim()
  if (!source) {
    return false
  }
  if (source.includes('||')) {
    return source.split('||').some(part => evaluateCmsTemplateCondition(part, context))
  }
  if (source.includes('&&')) {
    return source.split('&&').every(part => evaluateCmsTemplateCondition(part, context))
  }
  if (source.startsWith('!') && !source.startsWith('!=')) {
    return !evaluateCmsTemplateCondition(source.slice(1), context)
  }
  const comparison = source.match(/^(.+?)\s*(===|==|!==|!=|>=|<=|>|<)\s*(.+)$/)
  if (comparison) {
    const left = resolveCmsConditionValue(comparison[1], context)
    const right = resolveCmsConditionValue(comparison[3], context)
    switch (comparison[2]) {
      case '===':
      case '==': return String(left ?? '') === String(right ?? '')
      case '!==':
      case '!=': return String(left ?? '') !== String(right ?? '')
      case '>': return Number(left) > Number(right)
      case '>=': return Number(left) >= Number(right)
      case '<': return Number(left) < Number(right)
      case '<=': return Number(left) <= Number(right)
    }
  }
  return Boolean(resolveCmsConditionValue(source, context))
}

function resolveCmsConditionValue(raw: string, context: CmsTemplateObject): unknown {
  const value = raw.trim()
  if ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith("'") && value.endsWith("'"))) {
    return value.slice(1, -1)
  }
  if (value === 'true') return true
  if (value === 'false') return false
  if (/^-?\d+(?:\.\d+)?$/.test(value)) return Number(value)
  return resolveCmsTemplatePath(value, context)
}

export function sanitizeCmsHtml(value?: string) {
  return (value || '')
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
}

/**
 * 剥离页面 CSS 中的全局/裸元素规则（*、body、header、a:hover 等），避免污染站点外壳的页头页脚。
 * 带类名/ID 的选择器（如 a.yb-ai-btn、main.yb-ai-page）保留；@media 内的规则逐条处理。
 */
export function sanitizeCmsCss(value?: string) {
  return (value || '').replace(/([^{}@]+)\{[^{}]*\}/g, (rule, selectors: string) => {
    const unsafe = selectors.split(',').some((selector) => {
      const s = selector.trim()
      if (/^(\*|html|body)$/i.test(s)) {
        return true
      }
      // 裸元素选择器（后面不带类名/ID）才判定为全局污染
      return /^(header|footer|nav|main|section|article|aside|div|span|a|img|p|h[1-6]|ul|ol|li|button|input|summary|details|strong|em|table|thead|tbody|tr|td|th)(?![.\-#\w])/i.test(s)
    })
    return unsafe ? '' : rule
  })
}

/**
 * 给页面 CSS 的每条规则加作用域前缀（如 .site-article），让页面样式：
 * 1. 不外溢到站点页头/页脚与其它页面；
 * 2. 凭借更高优先级压过外壳/历史遗留的全局同名类。
 * @media/@supports 等条件规则递归处理内部；@keyframes/@font-face 原样保留。
 */
export function scopeCmsCss(value: string | undefined, scope: string): string {
  const css = value || ''
  let result = ''
  let index = 0
  while (index < css.length) {
    const braceOpen = css.indexOf('{', index)
    if (braceOpen === -1) {
      result += css.slice(index)
      break
    }
    const selector = css.slice(index, braceOpen).trim()
    let depth = 1
    let cursor = braceOpen + 1
    while (cursor < css.length && depth > 0) {
      if (css[cursor] === '{') {
        depth++
      }
      else if (css[cursor] === '}') {
        depth--
      }
      cursor++
    }
    const body = css.slice(braceOpen + 1, cursor - 1)
    index = cursor
    if (!selector) {
      continue
    }
    if (/^@(media|supports|layer|container)\b/i.test(selector)) {
      result += `${selector}{${scopeCmsCss(body, scope)}}`
    }
    else if (/^@/.test(selector)) {
      // @keyframes/@font-face 等：内部不是选择器规则，原样保留
      result += `${selector}{${body}}`
    }
    else {
      const scoped = selector.split(',').map((part) => {
        const item = part.trim()
        if (!item) {
          return scope
        }
        if (/^(:root|html|body)\b/i.test(item)) {
          return item.replace(/^(:root|html|body)\b/i, scope)
        }
        return `${scope} ${item}`
      }).join(', ')
      result += `${scoped} { ${body.trim()} }\n`
    }
  }
  return result
}

export function renderCmsMarkdown(markdown?: string) {
  const lines = escapeCmsHtml(markdown || '').split(/\r?\n/)
  const html: string[] = []
  let inList = false
  for (const line of lines) {
    const listMatch = line.match(/^\s*[-*]\s+(.+)$/)
    if (listMatch) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${renderCmsInlineMarkdown(listMatch[1])}</li>`)
      continue
    }
    if (inList) {
      html.push('</ul>')
      inList = false
    }
    if (line.startsWith('### ')) {
      html.push(`<h3>${renderCmsInlineMarkdown(line.slice(4))}</h3>`)
    }
    else if (line.startsWith('## ')) {
      html.push(`<h2>${renderCmsInlineMarkdown(line.slice(3))}</h2>`)
    }
    else if (line.startsWith('# ')) {
      html.push(`<h1>${renderCmsInlineMarkdown(line.slice(2))}</h1>`)
    }
    else if (line.trim()) {
      html.push(`<p>${renderCmsInlineMarkdown(line)}</p>`)
    }
  }
  if (inList) {
    html.push('</ul>')
  }
  return html.join('')
}

function renderCmsInlineMarkdown(value: string) {
  return value
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
}

export function escapeCmsHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}
