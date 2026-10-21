import type { YdPageBuilderSetupHandler } from '@yudream/components'
import type { StudioGrapesModel, StudioNode } from '../types/site-studio'
import { parse } from 'postcss'
import { escapeStudioHtml, parseStudioHtml } from './site-studio-html'
import { studioAssert, validateStudioNodes } from './site-studio-validation'
/** HTML content is escaped before it reaches Grapes; no engine project is persisted. */
export function studioNodesToGrapesModel(nodes: StudioNode[]): StudioGrapesModel[] {
  validateStudioNodes(nodes)
  const convert = (node: StudioNode): StudioGrapesModel => ({
    type: node.tag === 'img' ? 'image' : node.tag === 'a' ? 'link' : node.text !== undefined ? 'text' : 'default',
    tagName: node.tag,
    attributes: { ...node.attributes, 'data-studio-id': node.id, 'data-studio-type': node.type },
    style: { ...node.styles },
    ...(node.text !== undefined ? { content: escapeStudioHtml(node.text) } : { components: node.children.map(convert) }),
  })
  return nodes.map(convert)
}
/** Uses the same DOMParser boundary for models and HTML, never trusts `content` as text. */
export function studioGrapesModelToNodes(models: StudioGrapesModel[], previous: StudioNode[] = []): StudioNode[] {
  const render = (model: StudioGrapesModel, depth: number): string => {
    studioAssert(depth <= 32, 'Grapes 模型层级超限')
    const tag = model.tagName ?? 'div'
    studioAssert(/^[a-z][a-z0-9]*$/.test(tag), '无效 Grapes 标签')
    const attributes = Object.entries(model.attributes ?? {}).map(([key, value]) => {
      studioAssert(/^[a-z][a-z0-9-]*$/.test(key), '无效 Grapes 属性')
      return ` ${key}="${escapeStudioHtml(value)}"`
    }).join('')
    const style = Object.entries(model.style ?? {}).map(([key, value]) => `${key}:${value}`).join(';')
    studioAssert(!(model.content && model.components?.length), 'Grapes content 与 components 不能混用')
    return `<${tag}${attributes}${style ? ` style="${escapeStudioHtml(style)}"` : ''}>${model.components?.map(child => render(child, depth + 1)).join('') ?? model.content ?? ''}</${tag}>`
  }
  return parseStudioHtml(models.map(model => render(model, 1)).join(''), '', previous)
}
export function createStudioGrapesAdapter() {
  const setup: YdPageBuilderSetupHandler = (editor) => {
    const assign = (component: {
      getAttributes: () => Record<string, unknown>
      addAttributes: (attributes: Record<string, string>) => unknown
    }) => {
      const attributes = component.getAttributes()
      if ('data-studio-document' in attributes || 'data-studio-region' in attributes) {
        return
      }
      const raw = attributes['data-studio-id']
      const id = typeof raw === 'string' && /^[a-z0-9][\w-]{0,127}$/i.test(raw) ? raw : crypto.randomUUID()
      if (raw !== id) {
        component.addAttributes({ 'data-studio-id': id })
      }
    }
    // Existing IDs are never rewritten on component:add: setComponents may emit add
    // before removal. Explicit clone events refresh the clone subtree only.
    editor.on('component:add', assign)
    editor.on('component:clone', component => component.onAll((child) => {
      const attributes = child.getAttributes()
      if (!('data-studio-document' in attributes) && !('data-studio-region' in attributes)) {
        child.addAttributes({ 'data-studio-id': crypto.randomUUID() })
      }
    }))
    editor.on('load', () => editor.getWrapper()?.onAll((component) => {
      if (!component.is('wrapper')) {
        assign(component)
      }
    }))
  }
  return { toModel: studioNodesToGrapesModel, fromModel: studioGrapesModelToNodes, parseHtml: parseStudioHtml, setup }
}

/** GrapesJS 自定义 CSS 解析结果（与引擎 ParsedCssRule 同构）。 */
export interface StudioCanvasCssRule {
  selectors: string
  style: Record<string, string>
  atRule?: string
  params?: string
}

/**
 * 画布 CSS 解析：引擎默认走浏览器 CSSOM，会把简写展开成长写、丢弃含 var() 的简写
 * （如 background: var(--accent)），再经画布回流写回文档，造成按钮背景丢失等不可逆损坏。
 * 这里改用 postcss 按原文保留每条声明；复杂选择器整串交给引擎（selectorsAdd）。
 */
export function parseStudioCanvasCss(css: string): StudioCanvasCssRule[] {
  const rules: StudioCanvasCssRule[] = []
  let root
  try {
    root = parse(css)
  }
  catch {
    return rules
  }
  root.walkRules((rule) => {
    const style: Record<string, string> = {}
    rule.each((node) => {
      if (node.type === 'decl') {
        style[node.prop] = node.important ? `${node.value} !important` : node.value
      }
    })
    const parent = rule.parent
    const atRule = parent && parent.type === 'atrule' ? parent as unknown as { name: string, params: string } : null
    rules.push({ selectors: rule.selector, style, ...(atRule ? { atRule: atRule.name, params: atRule.params } : {}) })
  })
  return rules
}
