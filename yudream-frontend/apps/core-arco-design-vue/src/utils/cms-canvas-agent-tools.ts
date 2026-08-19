import type { Block, Component, Editor } from 'grapesjs'

/**
 * CMS Agent v2 客户端画布工具执行器：工具在浏览器 GrapesJS 画布上真实执行，
 * 结果（含报错）经 WebSocket 回传服务端模型循环，形成 read-after-write 闭环。
 */

export interface CanvasToolExecOptions {
  /** 读取画布完整 CSS，供 get_outline(resource=css) 分页返回 */
  getCss?: () => string
  /** 读取页面 JS，供 get_outline(resource=js) 分页返回 */
  getJs?: () => string
  /** 追加 scoped CSS 到画布样式 */
  appendCss: (css: string) => void
  /** 无定位目标时的默认插入（普通页面追加到末尾；首页画布追加到 Header/Footer 之间的内容区） */
  appendDefault: (html: string) => Component[]
  /** 锁定的站点外壳组件（Header/Footer）不可删改 */
  isProtected?: (component: Component) => boolean
  /** 每次画布变更后回调（刷新版本号/脏标记） */
  onChanged?: () => void
}

// 纲要通过单个 WebSocket TOOL_RESULT 帧回传；限制节点数，避免复杂页面生成过大的模型上下文与传输帧。
const OUTLINE_NODE_CAP = 120
const HTML_EXCERPT_CAP = 3000

export function executeCanvasTool(
  editor: Editor,
  toolName: string,
  args: Record<string, unknown>,
  opts: CanvasToolExecOptions,
): Record<string, unknown> {
  switch (toolName) {
    case 'cms.canvas.get_outline':
      return getOutline(editor, args, opts)
    case 'cms.canvas.get_selected':
      return getSelected(editor)
    case 'cms.canvas.find':
      return findComponents(editor, args)
    case 'cms.canvas.read_component':
      return readComponent(requireTarget(editor, args))
    case 'cms.canvas.update_text':
      return updateText(requireTarget(editor, args, opts), args, opts)
    case 'cms.canvas.update_html':
      return updateHtml(requireTarget(editor, args, opts), args, opts)
    case 'cms.canvas.update_style':
      return updateStyle(requireTarget(editor, args, opts), args, opts)
    case 'cms.canvas.append_css':
      return appendCss(args, opts)
    case 'cms.canvas.update_attributes':
      return updateAttributes(requireTarget(editor, args, opts), args, opts)
    case 'cms.canvas.insert_html':
      return insertHtml(editor, args, opts)
    case 'cms.canvas.remove_component':
      return removeComponent(requireTarget(editor, args, opts), opts)
    case 'cms.canvas.list_blocks':
      return listBlocks(editor)
    case 'cms.canvas.insert_block':
      return insertBlock(editor, args, opts)
    case 'cms.ask.user':
      return { message: '已向用户展示澄清选项', asked: true }
    default:
      throw new Error(`不支持的客户端画布工具：${toolName}`)
  }
}

interface OutlineNode {
  id: string
  tag: string
  type: string
  classes: string[]
  text?: string
  children?: OutlineNode[]
  childCount?: number
  truncated?: boolean
}

function getOutline(editor: Editor, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const resource = typeof args.resource === 'string' ? args.resource.trim().toLowerCase() : 'html'
  const cursor = Math.max(0, Number(args.cursor) || 0)
  const limit = Math.min(120, Math.max(1, Number(args.limit) || (resource === 'html' ? 60 : 120)))
  if (resource === 'css' || resource === 'js') {
    return getTextResource(resource, resource === 'css' ? (opts.getCss?.() || '') : (opts.getJs?.() || ''), cursor, limit)
  }
  if (resource !== 'html') {
    throw new Error('get_outline 的 resource 只能是 html、css 或 js')
  }
  return getHtmlOutlinePage(editor, args, cursor, limit)
}

function getTextResource(resource: 'css' | 'js', source: string, cursor: number, limit: number): Record<string, unknown> {
  const lines = source.replace(/\r\n/g, '\n').split('\n')
  const page = lines.slice(cursor, cursor + limit)
  const nextCursor = cursor + page.length
  return {
    message: page.length ? `已读取 ${resource.toUpperCase()} 第 ${cursor + 1}-${nextCursor} 行` : `${resource.toUpperCase()} 已读取完毕`,
    resource,
    cursor,
    nextCursor: nextCursor < lines.length ? nextCursor : null,
    hasMore: nextCursor < lines.length,
    startLine: page.length ? cursor + 1 : null,
    endLine: page.length ? nextCursor : null,
    totalLines: lines.length,
    content: page.join('\n'),
  }
}

function getHtmlOutlinePage(editor: Editor, args: Record<string, unknown>, cursor: number, limit: number): Record<string, unknown> {
  const maxDepth = Math.max(1, Number(args.maxDepth) || 6)
  const wrapper = editor.getWrapper()
  if (!wrapper) {
    return { message: '画布为空', resource: 'html', cursor, nextCursor: null, hasMore: false, nodeCount: 0, nodes: [] }
  }
  const allNodes: Array<OutlineNode & { depth: number, parentId?: string }> = []
  const state = { truncated: false }
  collectOutlineNodes(wrapper.components().map((child: Component) => child), 1, maxDepth, undefined, allNodes, state)
  const page = allNodes.slice(cursor, cursor + limit)
  const nextCursor = cursor + page.length
  return {
    message: state.truncated ? `已读取 HTML 纲要第 ${cursor + 1}-${nextCursor} 个节点（结果已限制规模）` : page.length ? `已读取 HTML 纲要第 ${cursor + 1}-${nextCursor} 个节点` : 'HTML 纲要已读取完毕',
    resource: 'html',
    cursor,
    nextCursor: nextCursor < allNodes.length ? nextCursor : null,
    hasMore: nextCursor < allNodes.length,
    nodeCount: allNodes.length,
    truncated: state.truncated,
    nodes: page,
  }
}

function collectOutlineNodes(
  components: Component[],
  depth: number,
  maxDepth: number,
  parentId: string | undefined,
  output: Array<OutlineNode & { depth: number, parentId?: string }>,
  state: { truncated: boolean },
) {
  for (const component of components) {
    if (output.length >= OUTLINE_NODE_CAP) {
      state.truncated = true
      return
    }
    const node = outlineNodeSummary(component)
    output.push({ ...node, depth, ...(parentId ? { parentId } : {}) })
    if (depth < maxDepth && component.components().length) {
      collectOutlineNodes(component.components().map((child: Component) => child), depth + 1, maxDepth, node.id, output, state)
      if (state.truncated) {
        return
      }
    }
  }
}

/** 读取组件标签名：textnode/注释等节点的模型上可能没有 getTagName，做兜底防止整棵纲要树崩掉 */
function tagNameOf(component: Component): string {
  try {
    if (typeof (component as any).getTagName === 'function') {
      return String((component as any).getTagName() || 'div')
    }
    return String((component as any).get?.('tagName') || 'div')
  }
  catch {
    return 'div'
  }
}

function classesOf(component: Component): string[] {
  try {
    if (typeof (component as any).getClasses === 'function') {
      return (component as any).getClasses()
    }
  }
  catch {
    // ignore
  }
  return []
}

function outlineNodeSummary(component: Component): OutlineNode {
  const node: OutlineNode = {
    id: component.getId(),
    tag: tagNameOf(component),
    type: String(component.get('type') || ''),
    classes: classesOf(component),
    childCount: component.components().length,
  }
  const text = textExcerpt(component)
  if (text) {
    node.text = text
  }
  return node
}

function textExcerpt(component: Component): string {
  if (component.is('textnode')) {
    return truncate(String(component.get('content') || '').replace(/\s+/g, ' ').trim(), 40)
  }
  const textChild = component.findType('textnode')[0]
  if (textChild) {
    return truncate(String(textChild.get('content') || '').replace(/\s+/g, ' ').trim(), 40)
  }
  return ''
}

function truncate(value: string, max: number): string {
  return value.length > max ? `${value.slice(0, max)}…` : value
}

function summarize(component: Component): Record<string, unknown> {
  return {
    id: component.getId(),
    tag: tagNameOf(component),
    type: String(component.get('type') || ''),
    classes: classesOf(component),
    text: textExcerpt(component),
  }
}

function getSelected(editor: Editor): Record<string, unknown> {
  const selected = editor.getSelected()
  if (!selected) {
    return { message: '当前没有选中元素', selected: null }
  }
  return { message: '已读取选中元素', selected: { ...summarize(selected), html: truncate(selected.toHTML(), HTML_EXCERPT_CAP) } }
}

function findComponents(editor: Editor, args: Record<string, unknown>): Record<string, unknown> {
  const selector = typeof args.selector === 'string' ? args.selector.trim() : ''
  const text = typeof args.text === 'string' ? args.text.trim() : ''
  const limit = Math.max(1, Number(args.limit) || 10)
  if (!selector && !text) {
    throw new Error('find 需要 selector 或 text 至少一项')
  }
  const wrapper = editor.getWrapper()
  if (!wrapper) {
    return { message: '画布为空', matched: [] }
  }
  let matched: Component[] = []
  if (selector) {
    try {
      matched = wrapper.find(selector)
    }
    catch {
      throw new Error(`选择器无效：${selector}`)
    }
  }
  if (text) {
    const lowered = text.toLowerCase()
    const walk = (component: Component) => {
      if (component.findType('textnode').some(node => String(node.get('content') || '').toLowerCase().includes(lowered))) {
        matched.push(component)
      }
      component.components().forEach(walk)
    }
    walk(wrapper)
  }
  const total = matched.length
  return {
    message: total ? `匹配到 ${total} 个组件` : '没有匹配的组件',
    total,
    matched: matched.slice(0, limit).map(summarize),
  }
}

function readComponent(component: Component): Record<string, unknown> {
  return {
    message: '已读取组件详情',
    ...summarize(component),
    html: truncate(component.toHTML(), HTML_EXCERPT_CAP),
    styles: component.getStyle(),
    attributes: component.getAttributes(),
    childCount: component.components().length,
  }
}

function updateText(component: Component, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const text = typeof args.text === 'string' ? args.text : ''
  component.components(text)
  opts.onChanged?.()
  return { message: '文本已更新', ...summarize(component), text: truncate(text, 60) }
}

function updateHtml(component: Component, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const html = typeof args.html === 'string' ? args.html.trim() : ''
  if (!html) {
    throw new Error('update_html 缺少 html 内容')
  }
  component.components(html)
  const css = typeof args.css === 'string' ? args.css.trim() : ''
  if (css) {
    opts.appendCss(css)
  }
  opts.onChanged?.()
  return { message: '内部 HTML 已替换', ...summarize(component), childCount: component.components().length }
}

function updateStyle(component: Component, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const styles = args.styles
  if (!styles || typeof styles !== 'object' || Array.isArray(styles)) {
    throw new Error('update_style 需要 styles 样式对象')
  }
  component.addStyle(styles as Record<string, string>)
  opts.onChanged?.()
  return { message: '样式已更新', ...summarize(component), styles: component.getStyle() }
}

function appendCss(args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const css = typeof args.css === 'string' ? args.css.trim() : ''
  if (!css) {
    throw new Error('append_css 缺少 css 内容')
  }
  opts.appendCss(css)
  opts.onChanged?.()
  return { message: 'CSS 规则已合并', cssLength: css.length }
}

function updateAttributes(component: Component, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const attributes = args.attributes
  if (!attributes || typeof attributes !== 'object' || Array.isArray(attributes)) {
    throw new Error('update_attributes 需要 attributes 属性对象')
  }
  const merged: Record<string, unknown> = { ...component.getAttributes() }
  Object.entries(attributes as Record<string, unknown>).forEach(([key, value]) => {
    if (value === null || value === undefined) {
      delete merged[key]
    }
    else {
      merged[key] = value
    }
  })
  component.setAttributes(merged)
  opts.onChanged?.()
  return { message: '属性已更新', ...summarize(component), attributes: component.getAttributes() }
}

function insertHtml(editor: Editor, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const html = typeof args.html === 'string' ? args.html.trim() : ''
  if (!html) {
    throw new Error('insert_html 缺少 html 内容')
  }
  const inserted = insertContent(editor, args, html, opts)
  const css = typeof args.css === 'string' ? args.css.trim() : ''
  if (css) {
    opts.appendCss(css)
  }
  opts.onChanged?.()
  return { message: `已插入 ${inserted.length} 个组件`, inserted: inserted.map(summarize) }
}

function insertBlock(editor: Editor, args: Record<string, unknown>, opts: CanvasToolExecOptions): Record<string, unknown> {
  const blockId = typeof args.blockId === 'string' ? args.blockId.trim() : ''
  if (!blockId) {
    throw new Error('insert_block 缺少 blockId')
  }
  const block = editor.BlockManager.get(blockId)
  if (!block) {
    throw new Error(`预设区块不存在：${blockId}，请先调用 cms.canvas.list_blocks 获取可用列表`)
  }
  const content = block.get('content')
  const inserted = insertContent(editor, args, content as string | Record<string, unknown>, opts)
  opts.onChanged?.()
  return { message: `已插入预设区块「${block.get('label')}」`, inserted: inserted.map(summarize) }
}

function insertContent(
  editor: Editor,
  args: Record<string, unknown>,
  content: string | Record<string, unknown>,
  opts: CanvasToolExecOptions,
): Component[] {
  const targetId = typeof args.targetId === 'string' ? args.targetId.trim() : ''
  const position = typeof args.position === 'string' ? args.position.trim() : 'after'
  if (!targetId) {
    if (typeof content === 'string') {
      return opts.appendDefault(content)
    }
    const wrapper = editor.getWrapper()
    if (!wrapper) {
      throw new Error('画布为空，无法插入')
    }
    return wrapper.append(content as never) as Component[]
  }
  const target = findById(editor, targetId)
  if (!target) {
    throw new Error(`定位组件不存在：${targetId}（画布可能已变化，请重新读取纲要）`)
  }
  if (opts.isProtected?.(target)) {
    throw new Error('目标位于锁定的站点外壳（Header/Footer）内，不能在其中插入')
  }
  if (position === 'append') {
    return target.append(content as never) as Component[]
  }
  if (position === 'prepend') {
    return target.append(content as never, { at: 0 }) as Component[]
  }
  const parent = target.parent()
  if (!parent) {
    throw new Error('目标组件没有父级，无法在其前后插入')
  }
  const index = target.index()
  const at = position === 'before' ? index : index + 1
  return parent.append(content as never, { at }) as Component[]
}

function removeComponent(component: Component, opts: CanvasToolExecOptions): Record<string, unknown> {
  const summary = summarize(component)
  component.remove()
  opts.onChanged?.()
  return { message: '组件已删除', removed: summary }
}

function listBlocks(editor: Editor): Record<string, unknown> {
  const blocks = editor.BlockManager.getAll()
    .map((block: Block) => ({
      id: block.get('id'),
      label: block.get('label'),
      category: typeof block.get('category') === 'string' ? block.get('category') : (block.get('category') as { getLabel?: () => string })?.getLabel?.() || '',
    }))
    .slice(0, 60)
  return { message: `共 ${blocks.length} 个可用区块`, blocks }
}

function requireTarget(editor: Editor, args: Record<string, unknown>, opts?: CanvasToolExecOptions): Component {
  const id = typeof args.id === 'string' ? args.id.trim() : ''
  if (!id) {
    throw new Error('缺少组件 id')
  }
  const component = findById(editor, id)
  if (!component) {
    throw new Error(`组件不存在：${id}（画布可能已变化，请用 cms.canvas.get_outline 重新读取）`)
  }
  if (opts?.isProtected?.(component)) {
    throw new Error('目标组件位于锁定的站点外壳（Header/Footer）内，不能修改或删除')
  }
  return component
}

function findById(editor: Editor, id: string): Component | undefined {
  let found: Component | undefined
  const walk = (component: Component) => {
    if (found) {
      return
    }
    if (component.getId() === id) {
      found = component
      return
    }
    component.components().forEach(walk)
  }
  const wrapper = editor.getWrapper()
  if (wrapper) {
    walk(wrapper)
  }
  return found
}
