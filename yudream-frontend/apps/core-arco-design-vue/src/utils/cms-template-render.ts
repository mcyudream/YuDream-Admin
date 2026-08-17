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
