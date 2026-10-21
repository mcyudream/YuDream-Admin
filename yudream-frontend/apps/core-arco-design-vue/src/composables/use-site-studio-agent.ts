import type { SiteDocument, StudioCommand, StudioNode } from '../types/site-studio'
import type { StudioAgentRegion, StudioFindField, StudioThemeSection } from '../utils/site-studio-agent-observe'
import type { StudioCheckViewport } from '../utils/site-studio-check'
import { applyStudioCommand, validateStudioDocument } from '../utils/site-studio'
import { studioAgentGuide } from '../utils/site-studio-agent-guide'
import { agentValue, studioFind, studioInspect, studioOutline, studioThemeSummary } from '../utils/site-studio-agent-observe'
import { checkStudioPage } from '../utils/site-studio-check'

/** 设计器工具：观察（read/outline/find/inspect/theme/guide）、修改（apply）、检查（validate/check）；名称与后端 CmsStudioClientTools.NAMES 一致 */
export const STUDIO_AGENT_TOOLS = ['cms.studio.outline', 'cms.studio.find', 'cms.studio.inspect', 'cms.studio.theme', 'cms.studio.guide', 'cms.studio.read', 'cms.studio.apply', 'cms.studio.validate', 'cms.studio.check'] as const
/** 观察当前修订的工具：调用过其一才允许本轮 apply */
export const STUDIO_AGENT_OBSERVE_TOOLS: readonly string[] = ['cms.studio.outline', 'cms.studio.find', 'cms.studio.inspect', 'cms.studio.theme', 'cms.studio.read']
const TOOL_ARGS: Record<string, string[]> = {
  'cms.studio.read': ['cursor', 'limit'],
  'cms.studio.outline': ['region', 'rootId', 'depth'],
  'cms.studio.find': ['query', 'field', 'region', 'limit'],
  'cms.studio.inspect': ['nodeIds'],
  'cms.studio.theme': ['section', 'query'],
  'cms.studio.guide': ['topic'],
  'cms.studio.apply': ['expectedRevision', 'command'],
  'cms.studio.validate': [],
  'cms.studio.check': ['viewports'],
}

export interface StudioAgentContext {
  document: SiteDocument
  selectedNodeId: string
  revision: string
  contextKey: string
  pageId: string
}

function requireValue(condition: unknown, message: string): asserts condition {
  if (!condition) {
    throw new Error(message)
  }
}
function object(value: unknown): Record<string, unknown> {
  requireValue(!!value && typeof value === 'object' && !Array.isArray(value), '命令必须为结构化对象')
  return value as Record<string, unknown>
}
const commandKeys: Record<string, string[]> = {
  'set-text': ['nodeId', 'text'],
  'update-node': ['nodeId', 'patch'],
  'set-style': ['nodeId', 'styles', 'breakpoint'],
  'insert': ['target', 'node', 'index'],
  'remove': ['nodeId'],
  'move': ['nodeId', 'target', 'index'],
  'duplicate': ['nodeId'],
  'set-theme': ['tokens', 'header', 'footer', 'classes', 'fonts', 'keyframes'],
  'set-features': ['features'],
  'set-content': ['values', 'remove'],
  'set-class': ['name', 'styles', 'responsive', 'states', 'remove'],
  'add-page': ['page'],
  'update-page': ['pageId', 'patch'],
  'remove-page': ['pageId'],
  'set-assets': ['assets'],
}
/** Reject unsupported keys before the model's document validator; never allow arbitrary Object.assign patches. */
export function parseStudioAgentCommand(value: unknown): StudioCommand {
  const raw = object(value)
  const type = raw.type
  requireValue(typeof type === 'string', '命令缺少 type')
  if (type === 'batch') {
    requireValue(Object.keys(raw).every(key => ['type', 'commands'].includes(key)), 'batch 包含未知字段')
    requireValue(Array.isArray(raw.commands) && raw.commands.length > 0 && raw.commands.length <= 200, 'batch 需要 1–200 个命令')
    return { type, commands: raw.commands.map((command) => {
      requireValue(object(command).type !== 'batch', 'batch 禁止嵌套')
      return parseStudioAgentCommand(command)
    }) }
  }
  const keys = commandKeys[type]
  requireValue(keys && Object.keys(raw).every(key => key === 'type' || keys.includes(key)), '未知命令或命令包含未授权字段')
  for (const key of ['nodeId', 'pageId']) {
    if (keys.includes(key)) {
      requireValue(typeof raw[key] === 'string' && !!raw[key], `命令缺少 ${key}`)
    }
  }
  if (type === 'set-text') {
    requireValue(typeof raw.text === 'string', 'text 必须是字符串')
  }
  if (type === 'update-node' || type === 'update-page') {
    const allowed = type === 'update-node' ? ['type', 'tag', 'text', 'attributes', 'styles', 'responsive', 'children'] : ['name', 'path', 'root', 'settings']
    requireValue(Object.keys(object(raw.patch)).every(key => allowed.includes(key)), 'patch 包含未授权字段')
  }
  if (keys.includes('target')) {
    const target = object(raw.target)
    requireValue(Object.keys(target).length === 1 && Object.entries(target).every(([key, val]) => ['pageId', 'parentId'].includes(key) ? typeof val === 'string' && !!val : key === 'region' && ['header', 'footer'].includes(String(val))), '无效命令目标')
  }
  if (raw.index !== undefined) {
    requireValue(Number.isInteger(raw.index) && Number(raw.index) >= 0, 'index 必须为非负整数')
  }
  if (type === 'set-style') {
    object(raw.styles)
  }
  if (type === 'insert') {
    object(raw.node)
  }
  if (type === 'add-page') {
    object(raw.page)
  }
  if (type === 'set-features') {
    object(raw.features)
  }
  if (type === 'set-content') {
    object(raw.values)
    requireValue(raw.remove === undefined || (Array.isArray(raw.remove) && raw.remove.every(key => typeof key === 'string')), 'remove 必须为字段键数组')
  }
  if (type === 'set-class') {
    requireValue(typeof raw.name === 'string' && !!raw.name, '命令缺少 name')
    requireValue(raw.remove === undefined || typeof raw.remove === 'boolean', 'remove 必须为布尔值')
    for (const key of ['styles', 'responsive', 'states']) {
      if (raw[key] !== undefined) {
        object(raw[key])
      }
    }
  }
  if (type === 'set-assets') {
    requireValue(Array.isArray(raw.assets), 'assets 必须为数组')
  }
  // Final shape and security invariants are checked by applyStudioCommand + validateStudioDocument on a clone.
  return raw as unknown as StudioCommand
}

/** Pure draft executor; run/session authorization is supplied by the mounted panel, never by model arguments. */
export async function executeStudioAgentTool(name: string, args: Record<string, unknown>, getContext: () => StudioAgentContext, executeCommand: (command: StudioCommand) => void): Promise<Record<string, unknown>> {
  const context = getContext()
  const { document, contextKey, pageId, revision } = context
  requireValue((STUDIO_AGENT_TOOLS as readonly string[]).includes(name), '未授权的设计器工具')
  const allowed = ['contextKey', 'documentId', 'pageId', ...TOOL_ARGS[name]!]
  requireValue(Object.keys(args).every(key => allowed.includes(key)), '工具包含未授权参数')
  requireValue(contextKey && contextKey.length <= 240 && args.contextKey === contextKey && args.documentId === document.id && args.pageId === pageId, '设计器上下文已变化，请重新读取当前文档')
  requireValue(document.pages.some(page => page.id === pageId), '当前页面不存在，请重新选择页面')
  const metadata = () => ({ contextKey, documentId: document.id, pageId, revision, selectedNodeId: context.selectedNodeId })
  if (name === 'cms.studio.validate') {
    try {
      validateStudioDocument(document)
      return { ...metadata(), valid: true, errors: [] }
    }
    catch (error) {
      return { ...metadata(), valid: false, errors: [error instanceof Error ? error.message : '文档校验失败'] }
    }
  }
  if (name === 'cms.studio.check') {
    const viewports = (args.viewports ?? ['mobile', 'desktop']) as StudioCheckViewport[]
    requireValue(Array.isArray(viewports) && viewports.length > 0 && viewports.every(item => item === 'mobile' || item === 'desktop'), 'viewports 只能是 mobile / desktop')
    return { ...metadata(), ...(await checkStudioPage(document, pageId, [...new Set(viewports)])) }
  }
  if (name === 'cms.studio.guide') {
    requireValue(args.topic === undefined || typeof args.topic === 'string', 'topic 必须为字符串')
    return { ...metadata(), ...studioAgentGuide(args.topic as string | undefined) }
  }
  validateStudioDocument(document)
  const region = (value: unknown, fallback: StudioAgentRegion): StudioAgentRegion => {
    requireValue(value === undefined || ['page', 'header', 'footer', 'all'].includes(String(value)), 'region 只能是 page / header / footer / all')
    return (value as StudioAgentRegion | undefined) ?? fallback
  }
  if (name === 'cms.studio.outline') {
    requireValue(args.rootId === undefined || typeof args.rootId === 'string', 'rootId 必须为字符串')
    requireValue(args.depth === undefined || (Number.isInteger(args.depth) && Number(args.depth) >= 1 && Number(args.depth) <= 12), 'depth 应为 1–12')
    return { ...metadata(), ...studioOutline(document, pageId, { region: region(args.region, args.rootId ? 'all' : 'page'), rootId: args.rootId as string | undefined, depth: args.depth as number | undefined }) }
  }
  if (name === 'cms.studio.find') {
    requireValue(typeof args.query === 'string' && args.query.trim().length > 0 && args.query.length <= 120, 'query 应为 1–120 字的检索词')
    requireValue(args.field === undefined || ['any', 'text', 'class', 'tag', 'attr', 'binding'].includes(String(args.field)), 'field 只能是 any/text/class/tag/attr/binding')
    requireValue(args.limit === undefined || (Number.isInteger(args.limit) && Number(args.limit) >= 1 && Number(args.limit) <= 100), 'limit 应为 1–100')
    return { ...metadata(), ...studioFind(document, pageId, { query: args.query as string, field: args.field as StudioFindField | undefined, region: region(args.region, 'all'), limit: args.limit as number | undefined }) }
  }
  if (name === 'cms.studio.inspect') {
    requireValue(Array.isArray(args.nodeIds) && args.nodeIds.every(id => typeof id === 'string'), 'nodeIds 必须为字符串数组')
    return { ...metadata(), ...studioInspect(document, pageId, args.nodeIds as string[]) }
  }
  if (name === 'cms.studio.theme') {
    requireValue(args.section === undefined || ['all', 'classes', 'tokens', 'content', 'features', 'pages'].includes(String(args.section)), 'section 只能是 all/classes/tokens/content/features/pages')
    requireValue(args.query === undefined || typeof args.query === 'string', 'query 必须为字符串')
    return { ...metadata(), ...studioThemeSummary(document, { section: args.section as StudioThemeSection | undefined, query: args.query as string | undefined }) }
  }
  if (name === 'cms.studio.read') {
    const cursor = args.cursor ?? 0
    const limit = args.limit ?? 60
    requireValue(Number.isInteger(cursor) && Number(cursor) >= 0 && Number.isInteger(limit) && Number(limit) >= 1 && Number(limit) <= 100, '分页参数无效，limit 应为 1–100')
    const nodes: Record<string, unknown>[] = []
    const visit = (items: StudioNode[], region: string, parentId?: string) => {
      for (const node of items) {
        const { children, ...fields } = node
        nodes.push({ ...fields, attributes: Object.fromEntries(Object.entries(fields.attributes).map(([key, value]) => [key, agentValue(value)])), parentId, region, childIds: children.map(child => child.id) })
        visit(children, region, node.id)
      }
    }
    // 当前页优先，避免编辑小页面也逐页读取整个站点并重复消耗模型额度。
    visit(document.pages.find(page => page.id === pageId)!.root, pageId)
    visit(document.theme.header, 'header')
    visit(document.theme.footer, 'footer')
    const end = Math.min(Number(cursor) + Number(limit), nodes.length)
    return { ...metadata(), document: { schemaVersion: document.schemaVersion, id: document.id, name: document.name, pages: document.pages.map(({ root, ...page }) => ({ ...page, rootIds: root.map(node => node.id) })), theme: { tokens: Object.fromEntries(Object.entries(document.theme.tokens).map(([key, value]) => [key, agentValue(value)])), headerIds: document.theme.header.map(node => node.id), footerIds: document.theme.footer.map(node => node.id) }, assets: document.assets.map(asset => ({ ...asset, url: agentValue(asset.url) })) }, nodes: nodes.slice(Number(cursor), end), total: nodes.length, nextCursor: end, hasMore: end < nodes.length, scope: '当前页面与共享页头页脚的本地 AST 草稿，不保存、不发布' }
  }
  requireValue(typeof args.expectedRevision === 'string' && args.expectedRevision === revision, '文档已被手工修改或切换，陈旧命令未执行，请重新 read')
  const parsed = parseStudioAgentCommand(args.command)
  const batch: StudioCommand = parsed.type === 'batch' ? parsed : { type: 'batch', commands: [parsed] }
  const plain = JSON.parse(JSON.stringify(document)) as SiteDocument
  const candidate = applyStudioCommand(plain, batch)
  validateStudioDocument(candidate)
  const fresh = getContext()
  requireValue(fresh.contextKey === contextKey && fresh.document.id === document.id && fresh.pageId === pageId && fresh.revision === revision, '执行前上下文已变化，未修改草稿，请重新 read')
  executeCommand(batch)
  // The panel waits for Vue props to reflect the controlled editor update before acknowledging.
  return { contextKey, documentId: document.id, pageId, previousRevision: revision, applied: true, commandCount: batch.commands.length, scope: '仅修改本地草稿；未保存、未发布' }
}
