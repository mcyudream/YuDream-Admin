import type { SiteDocument, StudioContainer, StudioNode } from '../types/site-studio'
import { freshStudioNodeTree } from './site-studio-snippets'
import { validateStudioNodes } from './site-studio-validation'

/**
 * 网站工作台区块注册表（参照 WordPress 区块插入器）：区块是“可插入的节点模板”，
 * 每次插入都通过 create() 生成全新节点与 ID，插入后与来源无联动。
 * 宿主核心区块在此注册；用户区块模板与插件区块经同一注册表接入，统一走文档校验。
 */
export type StudioBlockSource = 'core' | 'template' | 'plugin'

export interface StudioBlockCategory {
  key: string
  label: string
}

export interface StudioBlockDefinition {
  /** 命名空间/名称，如 core/heading、template/<id>、plugin-code/hero */
  name: string
  title: string
  category: string
  icon: string
  description?: string
  keywords?: string[]
  source: StudioBlockSource
  create: () => StudioNode[]
}

export const STUDIO_BLOCK_CATEGORIES: StudioBlockCategory[] = [
  { key: 'text', label: '文本' },
  { key: 'media', label: '媒体' },
  { key: 'design', label: '设计' },
  { key: 'layout', label: '布局' },
  { key: 'widgets', label: '小组件' },
  { key: 'custom', label: '我的模板' },
]

interface NodeOptions {
  text?: string
  attributes?: Record<string, string>
  styles?: Record<string, string>
  children?: StudioNode[]
}

function el(type: string, tag: string, options: NodeOptions = {}): StudioNode {
  return {
    id: crypto.randomUUID(),
    type,
    tag,
    attributes: { ...options.attributes },
    styles: { ...options.styles },
    children: options.children ?? [],
    ...(options.text !== undefined ? { text: options.text } : {}),
  }
}

const SECTION_STYLES = { 'display': 'flex', 'flex-direction': 'column', 'gap': '16px', 'padding': '24px', 'min-height': '80px', 'min-width': '0' }
const BUTTON_STYLES = { 'display': 'inline-block', 'padding': '10px 20px', 'background': 'var(--accent, #0f766e)', 'color': '#ffffff', 'text-decoration': 'none' }
const MEDIA_STYLES = { 'max-width': '100%', 'height': 'auto' }

function columns(count: number): StudioNode {
  return el('columns', 'div', {
    styles: { 'display': 'grid', 'grid-template-columns': `repeat(${count}, minmax(0, 1fr))`, 'gap': '24px' },
    children: Array.from({ length: count }, (_, index) => el('column', 'div', {
      styles: { 'display': 'flex', 'flex-direction': 'column', 'gap': '12px', 'min-width': '0' },
      children: [el('text', 'p', { text: `第 ${index + 1} 栏内容` })],
    })),
  })
}

const CORE_BLOCKS: StudioBlockDefinition[] = [
  { name: 'core/paragraph', title: '段落', category: 'text', icon: 'morph:pilcrow', keywords: ['文本', 'text', 'p'], description: '一段正文文本', source: 'core', create: () => [el('text', 'p', { text: '在这里输入正文。' })] },
  { name: 'core/heading', title: '标题', category: 'text', icon: 'morph:heading', keywords: ['heading', 'h2', '标题'], description: '分节标题', source: 'core', create: () => [el('heading', 'h2', { text: '标题' })] },
  { name: 'core/list', title: '列表', category: 'text', icon: 'morph:list', keywords: ['ul', '项目符号'], description: '无序列表', source: 'core', create: () => [el('list', 'ul', { children: ['第一项', '第二项', '第三项'].map(text => el('list-item', 'li', { text })) })] },
  { name: 'core/quote', title: '引用', category: 'text', icon: 'morph:quote', keywords: ['blockquote', '引言'], description: '突出显示的引语', source: 'core', create: () => [el('quote', 'blockquote', { styles: { 'margin': '0', 'padding': '8px 16px', 'border-left': '4px solid var(--accent, #0f766e)' }, children: [el('text', 'p', { text: '引用的内容。' })] })] },
  { name: 'core/code', title: '代码', category: 'text', icon: 'morph:code', keywords: ['pre', 'code'], description: '保留格式的代码片段', source: 'core', create: () => [el('code', 'pre', { text: 'console.log(\'hello\')', styles: { 'padding': '12px', 'overflow': 'auto', 'background': '#f4f4f5', 'font-family': 'monospace' } })] },
  { name: 'core/table', title: '表格', category: 'text', icon: 'morph:table', keywords: ['table', '数据'], description: '两行两列的表格', source: 'core', create: () => [el('table', 'table', { styles: { 'width': '100%', 'border-collapse': 'collapse' }, children: [el('table-body', 'tbody', { children: [0, 1].map(row => el('table-row', 'tr', { children: [0, 1].map(cell => el('table-cell', 'td', { text: `单元格 ${row + 1}-${cell + 1}`, styles: { padding: '8px', border: '1px solid #e4e4e7' } })) })) })] })] },
  { name: 'core/image', title: '图片', category: 'media', icon: 'morph:image', keywords: ['img', '照片'], description: '单张图片', source: 'core', create: () => [el('image', 'img', { attributes: { alt: '' }, styles: MEDIA_STYLES })] },
  { name: 'core/gallery', title: '图库', category: 'media', icon: 'morph:images', keywords: ['gallery', '相册'], description: '三列图片网格', source: 'core', create: () => [el('gallery', 'div', { styles: { 'display': 'grid', 'grid-template-columns': 'repeat(3, minmax(0, 1fr))', 'gap': '12px' }, children: [0, 1, 2].map(() => el('image', 'img', { attributes: { alt: '' }, styles: { width: '100%', height: 'auto' } })) })] },
  { name: 'core/video', title: '视频', category: 'media', icon: 'morph:video', keywords: ['video', '影片'], description: '内嵌视频播放器', source: 'core', create: () => [el('video', 'video', { attributes: { controls: '', playsinline: '', preload: 'metadata' }, styles: MEDIA_STYLES })] },
  { name: 'core/audio', title: '音频', category: 'media', icon: 'morph:music', keywords: ['audio', '声音'], description: '内嵌音频播放器', source: 'core', create: () => [el('audio', 'audio', { attributes: { controls: '' } })] },
  { name: 'core/media-text', title: '媒体与文本', category: 'media', icon: 'morph:layout-panel-left', keywords: ['图文', 'media'], description: '左侧媒体、右侧文字', source: 'core', create: () => [el('media-text', 'div', { styles: { 'display': 'grid', 'grid-template-columns': 'repeat(2, minmax(0, 1fr))', 'gap': '24px', 'align-items': 'center' }, children: [el('image', 'img', { attributes: { alt: '' }, styles: { width: '100%', height: 'auto' } }), el('container', 'div', { styles: { 'display': 'flex', 'flex-direction': 'column', 'gap': '12px' }, children: [el('heading', 'h3', { text: '标题' }), el('text', 'p', { text: '介绍这张图片或视频。' })] })] })] },
  { name: 'core/button', title: '按钮', category: 'design', icon: 'morph:mouse-pointer-click', keywords: ['button', '链接按钮', 'cta'], description: '引导点击的按钮链接', source: 'core', create: () => [el('button', 'a', { text: '了解更多', attributes: { href: '#' }, styles: BUTTON_STYLES })] },
  { name: 'core/buttons', title: '按钮组', category: 'design', icon: 'morph:rectangle-ellipsis', keywords: ['buttons'], description: '并排的多个按钮', source: 'core', create: () => [el('buttons', 'div', { styles: { 'display': 'flex', 'flex-wrap': 'wrap', 'gap': '12px' }, children: [el('button', 'a', { text: '主要操作', attributes: { href: '#' }, styles: BUTTON_STYLES }), el('button', 'a', { text: '次要操作', attributes: { href: '#' }, styles: { ...BUTTON_STYLES, background: 'transparent', color: 'var(--accent, #0f766e)', border: '1px solid currentColor' } })] })] },
  { name: 'core/card', title: '卡片', category: 'design', icon: 'morph:square', keywords: ['card', '框'], description: '带边框的内容卡片', source: 'core', create: () => [el('card', 'div', { styles: { 'display': 'flex', 'flex-direction': 'column', 'gap': '8px', 'padding': '20px', 'border': '1px solid #e4e4e7' }, children: [el('heading', 'h3', { text: '卡片标题' }), el('text', 'p', { text: '卡片说明文字。' })] })] },
  { name: 'core/separator', title: '分隔线', category: 'design', icon: 'morph:minus', keywords: ['hr', 'divider'], description: '水平分隔线', source: 'core', create: () => [el('separator', 'hr', { styles: { 'border': '0', 'border-top': '1px solid #e4e4e7', 'margin': '24px 0' } })] },
  { name: 'core/spacer', title: '间隔', category: 'design', icon: 'morph:move-vertical', keywords: ['spacer', '留白'], description: '可调整高度的空白', source: 'core', create: () => [el('spacer', 'div', { styles: { height: '48px' } })] },
  { name: 'core/section', title: '区块', category: 'layout', icon: 'morph:rectangle-horizontal', keywords: ['section', '分区'], description: '页面分区，内部可继续插入', source: 'core', create: () => [el('section', 'section', { styles: SECTION_STYLES })] },
  { name: 'core/group', title: '容器', category: 'layout', icon: 'morph:box', keywords: ['group', 'div', 'container'], description: '纵向排列的分组容器', source: 'core', create: () => [el('container', 'div', { styles: SECTION_STYLES })] },
  { name: 'core/columns-2', title: '两栏', category: 'layout', icon: 'morph:columns-2', keywords: ['columns', '分栏'], description: '等宽两栏', source: 'core', create: () => [columns(2)] },
  { name: 'core/columns-3', title: '三栏', category: 'layout', icon: 'morph:columns-3', keywords: ['columns', '分栏'], description: '等宽三栏', source: 'core', create: () => [columns(3)] },
  { name: 'core/cover', title: '封面', category: 'layout', icon: 'morph:panel-top', keywords: ['cover', 'hero', '横幅'], description: '带背景色的大标题横幅', source: 'core', create: () => [el('cover', 'section', { styles: { 'display': 'flex', 'flex-direction': 'column', 'align-items': 'center', 'justify-content': 'center', 'gap': '16px', 'min-height': '360px', 'padding': '48px 24px', 'background': 'var(--accent, #0f766e)', 'color': '#ffffff', 'text-align': 'center' }, children: [el('heading', 'h1', { text: '醒目的封面标题' }), el('text', 'p', { text: '一句话介绍。' }), el('button', 'a', { text: '开始使用', attributes: { href: '#' }, styles: { ...BUTTON_STYLES, background: '#ffffff', color: 'var(--accent, #0f766e)' } })] })] },
  { name: 'core/link', title: '链接', category: 'widgets', icon: 'morph:link', keywords: ['a', 'href'], description: '文字链接', source: 'core', create: () => [el('link', 'a', { text: '链接文字', attributes: { href: '#' } })] },
  { name: 'core/details', title: '详情', category: 'widgets', icon: 'morph:chevrons-down-up', keywords: ['details', '折叠', 'faq'], description: '可展开收起的问答', source: 'core', create: () => [el('details', 'details', { children: [el('summary', 'summary', { text: '常见问题？' }), el('text', 'p', { text: '展开后显示的回答。' })] })] },
]

/** 区块注册表；宿主核心区块默认注册，其他来源（模板、插件）按名称注册/替换/注销。 */
export function createStudioBlockRegistry(initial: StudioBlockDefinition[] = CORE_BLOCKS) {
  const blocks = new Map<string, StudioBlockDefinition>()
  const register = (definition: StudioBlockDefinition) => {
    if (!/^[a-z0-9][\w-]*\/[\w-]+$/i.test(definition.name)) {
      throw new Error(`区块名称无效：${definition.name}`)
    }
    blocks.set(definition.name, definition)
  }
  initial.forEach(register)
  return {
    register,
    unregister: (name: string) => blocks.delete(name),
    get: (name: string) => blocks.get(name),
    list: () => [...blocks.values()],
  }
}

/** 搜索按标题、名称、关键词与说明匹配，标题前缀命中优先。 */
export function searchStudioBlocks(blocks: StudioBlockDefinition[], query: string) {
  const term = query.trim().toLowerCase()
  if (!term) {
    return blocks
  }
  const score = (block: StudioBlockDefinition) => {
    const title = block.title.toLowerCase()
    if (title.startsWith(term)) {
      return 3
    }
    if (title.includes(term) || block.name.toLowerCase().includes(term)) {
      return 2
    }
    return [...(block.keywords ?? []), block.description ?? ''].some(word => word.toLowerCase().includes(term)) ? 1 : 0
  }
  return blocks.map(block => ({ block, rank: score(block) })).filter(item => item.rank > 0).sort((a, b) => b.rank - a.rank).map(item => item.block)
}

/** 生成可插入的节点：每次都是新 ID，且必须通过文档节点校验，来源不可信时也不会污染文档。 */
export function instantiateStudioBlock(block: StudioBlockDefinition): StudioNode[] {
  const nodes = block.create()
  if (!Array.isArray(nodes) || !nodes.length) {
    throw new Error(`区块「${block.title}」没有生成内容`)
  }
  validateStudioNodes(nodes)
  return nodes
}

export type StudioInsertPlacement = 'after' | 'inside'

/**
 * 计算插入位置（WordPress 语义）：有选中时默认插在选中区块之后；选择“内部”且选中节点可含子节点时追加到其内部；
 * 无选中时追加到当前页面或主题区域末尾。
 */
export function resolveStudioInsertTarget(
  document: SiteDocument,
  context: { region: 'page' | 'header' | 'footer', pageId: string, selectedId?: string, placement?: StudioInsertPlacement },
): { target: StudioContainer, index?: number } {
  const roots = context.region === 'page'
    ? document.pages.find(page => page.id === context.pageId)?.root
    : document.theme[context.region]
  const rootTarget: StudioContainer = context.region === 'page' ? { pageId: context.pageId } : { region: context.region }
  if (!roots) {
    throw new Error('当前页面不存在')
  }
  if (!context.selectedId) {
    return { target: rootTarget }
  }
  const locate = (nodes: StudioNode[], parent: StudioNode | null): { node: StudioNode, parent: StudioNode | null, index: number } | null => {
    for (const [index, node] of nodes.entries()) {
      if (node.id === context.selectedId) {
        return { node, parent, index }
      }
      const found = locate(node.children, node)
      if (found) {
        return found
      }
    }
    return null
  }
  const found = locate(roots, null)
  if (!found) {
    return { target: rootTarget }
  }
  if (context.placement === 'inside' && canContainStudioBlocks(found.node)) {
    return { target: { parentId: found.node.id } }
  }
  return { target: found.parent ? { parentId: found.parent.id } : rootTarget, index: found.index + 1 }
}

const LEAF_TAGS = new Set(['img', 'video', 'audio', 'hr', 'br', 'source', 'track', 'col', 'wbr'])
export function canContainStudioBlocks(node: StudioNode) {
  return node.text === undefined && !LEAF_TAGS.has(node.tag)
}

/** 服务端用户区块模板的最小形状（与 API 返回一致）。 */
export interface StudioBlockTemplateLike {
  id: string
  name: string
  category: string
  description?: string
  keywords?: string[]
  nodesJson: string
}

/** 用户模板 → 区块定义：名称为 template/<id>，每次插入解析内容并重建全部节点 ID。 */
export function templateToStudioBlock(template: StudioBlockTemplateLike): StudioBlockDefinition {
  return {
    name: `template/${template.id}`,
    title: template.name,
    category: STUDIO_BLOCK_CATEGORIES.some(category => category.key === template.category) ? template.category : 'custom',
    icon: 'morph:layout-template',
    description: template.description || '用户区块模板',
    keywords: template.keywords ?? [],
    source: 'template',
    create: () => {
      const parsed: unknown = JSON.parse(template.nodesJson)
      if (!Array.isArray(parsed)) {
        throw new TypeError(`模板「${template.name}」内容无效`)
      }
      return (parsed as StudioNode[]).map(freshStudioNodeTree)
    },
  }
}

/** 用最新模板列表替换注册表中的全部模板区块（删除的模板随之注销）。 */
export function syncStudioTemplateBlocks(registry: ReturnType<typeof createStudioBlockRegistry>, templates: StudioBlockTemplateLike[]) {
  registry.list().filter(block => block.source === 'template').forEach(block => registry.unregister(block.name))
  templates.forEach(template => registry.register(templateToStudioBlock(template)))
}

/** 宿主默认注册表（单例）：工作台插入器、用户模板与后续插件区块共用。 */
export const studioBlockRegistry = createStudioBlockRegistry()
