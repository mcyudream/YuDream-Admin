import type { ComputedRef, InjectionKey, Ref } from 'vue'
import type { WikiNode, WikiSpace } from '@/api/modules/platform-wiki'

// 页面类型中文展示与配色（同时用于 YdTag 与图谱着色）
export interface PageTypeMeta {
  label: string
  color: string
  /** 目录树/列表里的类型圆点颜色（低饱和区分，不承担文字可读性） */
  dot?: string
}

export const pageTypeMeta: Record<string, PageTypeMeta> = {
  source_summary: { label: '资料摘要', color: 'var(--color-text-2)', dot: 'rgb(var(--gold-6))' },
  source_document: { label: '原文档', color: 'var(--color-text-2)', dot: 'rgb(var(--orange-6))' },
  entity: { label: '实体', color: 'var(--color-text-2)', dot: 'rgb(var(--green-6))' },
  concept: { label: '概念', color: 'var(--color-text-2)', dot: 'rgb(var(--arcoblue-6))' },
  synthesis: { label: '综合分析', color: 'var(--color-text-2)', dot: 'rgb(var(--purple-6))' },
  comparison: { label: '对比', color: 'var(--color-text-2)', dot: 'rgb(var(--cyan-6))' },
  query: { label: '查询归档', color: 'var(--color-text-2)', dot: 'rgb(var(--magenta-6))' },
  research: { label: '深度研究', color: 'var(--color-text-2)', dot: 'rgb(var(--pinkpurple-6))' },
  overview: { label: '全局概要', color: 'var(--color-text-2)', dot: 'rgb(var(--blue-6))' },
  toc: { label: '内容目录', color: 'var(--color-text-2)', dot: 'rgb(var(--lime-6))' },
  log: { label: '操作日志', color: 'var(--color-text-3)', dot: 'rgb(var(--gray-6))' },
}

export function pageTypeLabel(pageType?: string): string {
  return (pageType && pageTypeMeta[pageType]?.label) || pageType || '概念'
}

export function pageTypeColor(pageType?: string): string {
  return (pageType && pageTypeMeta[pageType]?.color) || pageTypeMeta.concept.color
}

/** 类型圆点颜色：未登记的类型回落中灰，不猜测语义 */
export function pageTypeDot(pageType?: string): string {
  return (pageType && pageTypeMeta[pageType]?.dot) || 'rgb(var(--gray-6))'
}

export const pageTypeOptions = Object.entries(pageTypeMeta).map(([value, meta]) => ({
  label: meta.label,
  value,
}))

// 摄入任务状态
export interface StatusMeta {
  label: string
  color: string
}

export const ingestStatusMeta: Record<string, StatusMeta> = {
  QUEUED: { label: '排队中', color: 'var(--color-text-3)' },
  RUNNING: { label: '运行中', color: 'var(--color-text-2)' },
  // 后端枚举是 SUCCEEDED / FAILED / CANCELLED；旧值一并保留，避免历史数据回落成原始英文
  SUCCEEDED: { label: '已完成', color: 'rgb(var(--success-6))' },
  COMPLETED: { label: '已完成', color: 'rgb(var(--success-6))' },
  FAILED: { label: '失败', color: 'rgb(var(--danger-6))' },
  CANCELLED: { label: '已取消', color: 'var(--color-text-2)' },
  PENDING: { label: '待处理', color: 'var(--color-text-3)' },
  SUCCESS: { label: '成功', color: 'rgb(var(--success-6))' },
}

export function ingestStatusLabel(status?: string): StatusMeta {
  return (status && ingestStatusMeta[status]) || { label: status || '-', color: 'var(--color-text-3)' }
}

// 抽取状态
export function extractionStatusLabel(status?: string): StatusMeta {
  const map: Record<string, StatusMeta> = {
    PENDING: { label: '待抽取', color: 'var(--color-text-3)' },
    RUNNING: { label: '抽取中', color: 'var(--color-text-2)' },
    COMPLETED: { label: '已抽取', color: 'rgb(var(--success-6))' },
    SUCCESS: { label: '已抽取', color: 'rgb(var(--success-6))' },
    FAILED: { label: '抽取失败', color: 'rgb(var(--danger-6))' },
    SKIPPED: { label: '已跳过', color: 'var(--color-text-2)' },
  }
  return (status && map[status]) || { label: status || '-', color: 'var(--color-text-3)' }
}

// 目录树拍平（附带深度）
export interface FlatWikiNode extends WikiNode {
  _depth: number
}

export function flattenTree(nodes: WikiNode[], depth = 0): FlatWikiNode[] {
  const out: FlatWikiNode[] = []
  for (const node of nodes || []) {
    out.push({ ...node, _depth: depth })
    out.push(...flattenTree(node.children || [], depth + 1))
  }
  return out
}

// [[wikilink]] → markdown 链接（wiki:// 协议，点击时在预览容器上拦截）
export function wikilinksToMarkdown(markdown: string): string {
  return (markdown || '').replace(/\[\[([^\][|]+)(?:\|([^\]]+))?\]\]/g, (_match, target: string, alias?: string) => {
    const title = target.trim()
    const text = (alias || target).trim()
    return `[${text}](wiki://${encodeURIComponent(title)})`
  })
}

// 从点击事件中解析 wiki:// 链接标题
export function resolveWikiLink(event: MouseEvent): string | null {
  const anchor = (event.target as HTMLElement | null)?.closest?.('a')
  const href = anchor?.getAttribute('href') || ''
  if (!href.startsWith('wiki://')) {
    return null
  }
  event.preventDefault()
  event.stopPropagation()
  try {
    return decodeURIComponent(href.slice('wiki://'.length))
  }
  catch {
    return href.slice('wiki://'.length)
  }
}

// Wiki 内联图片的可见性策略（纯函数：供编辑器与单测共用，不依赖接口模块）
/**
 * 内联图片一律私有：可见性不能按空间公开状态推导（公开库的未发布草稿图片会因此匿名可读）。
 * 公开页的内联图片需要发布态代理/提升契约，见 wiki-images.ts 顶部说明。
 */
export const WIKI_INLINE_IMAGE_POLICY = 'private' as const

/** 上传可见性提示：让作者明确知道图片对谁可见，以及公开页的未决项 */
export function wikiImageUploadHint(): string {
  return '图片始终按私有文件保存，仅登录后可读；公开页展示已发布页面的内联图片需要发布态代理地址（后端契约待接入）。'
}

/** 从 Markdown 图片地址解析站内文件内容路径（含缩略图），外链与非文件地址返回 null */
export function wikiFileContentPath(rawUrl?: string): string | null {
  const matched = /(\/api\/files\/\d+\/content(?:\/thumb)?)(?:[?#].*)?$/.exec(rawUrl || '')
  return matched ? matched[1] : null
}

// 管理端工作台共享状态（provide/inject）
/** 管理端三个工作面：知识库入口 / 作者编辑 / 治理管理 */
export type WikiWorkbenchView = 'entry' | 'author' | 'manage'

export interface WikiWorkbenchStore {
  spaces: Ref<WikiSpace[]>
  spaceId: Ref<string>
  space: ComputedRef<WikiSpace | null>
  tree: Ref<WikiNode[]>
  flatTree: ComputedRef<FlatWikiNode[]>
  selectedNode: Ref<WikiNode | null>
  loadingTree: Ref<boolean>
  /** 知识库列表三态，供入口视图区分「加载中 / 失败 / 真空」 */
  spacesLoading: Ref<boolean>
  spacesLoaded: Ref<boolean>
  spacesError: Ref<string>
  /** 当前工作面；本地状态驱动，切换时不触发路由导航（地址栏静默同步） */
  view: Ref<WikiWorkbenchView>
  /** 治理区当前分区 key（非治理视图时仍保留最近一次值） */
  activePanel: Ref<string>
  reloadTree: () => Promise<void>
  /** 重新加载知识库列表（删除知识库后调用） */
  reloadSpaces: () => Promise<void>
  /** 清空当前知识库作用域（当前知识库被删除后调用） */
  clearSpace: () => void
  selectNode: (node: WikiNode | null) => Promise<boolean>
  registerEditorGuard: (guard: { confirmLeave: () => Promise<boolean>, dirty: () => boolean }) => () => void
  /** 打开知识库入口 */
  openEntry: () => void
  /** 进入作者编辑区；可指定知识库与要定位的页面 */
  openAuthor: (target?: { space?: WikiSpace, node?: WikiNode | null }) => void
  /** 进入治理区；panel 为治理分区 key，可同时切换知识库 */
  openManage: (panel?: string, space?: WikiSpace) => void
  /** 切换到治理分区（等价 openManage，保留给既有面板调用） */
  openPanel: (panel: string) => void
  // 按标题 / nodeId / path 打开页面（切到作者编辑区并选中）
  openPage: (ref: { title?: string, nodeId?: string, path?: string }) => Promise<void>
  findNode: (ref: { title?: string, nodeId?: string, path?: string }) => WikiNode | null
}

export const wikiWorkbenchKey: InjectionKey<WikiWorkbenchStore> = Symbol('wiki-workbench')

// 治理区分区注册表：分组只描述导航结构，组件映射留在 WikiManageView，避免 utils 依赖具体面板
/** 能力尚未接入时的诚实说明：原因 + 缺失的后端契约，界面只陈述，不伪造数据 */
export interface WikiUnavailableCapability {
  reason: string
  contracts: string[]
}

export interface WikiManagePanelMeta {
  key: string
  label: string
  icon: string
  unavailable?: WikiUnavailableCapability
}

/**
 * 治理区一级区域：左侧导航只列区域，区域内的分区用横向子标签切换。
 * 分区 key 仍是 `?panel=` 的规范值，openPanel('ingest') 与旧深链保持兼容。
 */
export interface WikiManageAreaMeta {
  key: string
  label: string
  icon: string
  /** 该区域包含的分区，第一个为默认进入的分区 */
  panels: string[]
}

export const wikiManageAreas: WikiManageAreaMeta[] = [
  { key: 'overview', label: '概览', icon: 'morph:layout-dashboard', panels: ['overview'] },
  { key: 'content', label: '内容', icon: 'morph:layers', panels: ['sources', 'ingest', 'generated'] },
  { key: 'quality', label: '质量', icon: 'morph:shield-check', panels: ['review', 'lint', 'research'] },
  { key: 'insights', label: '洞察', icon: 'morph:radar', panels: ['search', 'graph'] },
  { key: 'collaboration', label: '协作', icon: 'morph:users', panels: ['members', 'comments'] },
  { key: 'settings', label: '设置', icon: 'morph:settings', panels: ['settings'] },
]

export function wikiManageAreaOf(panel: string): WikiManageAreaMeta {
  return wikiManageAreas.find(area => area.panels.includes(panel)) || wikiManageAreas[0]
}

export function wikiManageAreaPanels(areaKey: string): WikiManagePanelMeta[] {
  const area = wikiManageAreas.find(item => item.key === areaKey)
  return (area?.panels || []).map(key => wikiManagePanelMeta(key)).filter((item): item is WikiManagePanelMeta => Boolean(item))
}

export const wikiManagePanels: WikiManagePanelMeta[] = [
  { key: 'overview', label: '概览', icon: 'morph:layout-dashboard' },
  { key: 'sources', label: '资料库', icon: 'morph:database' },
  { key: 'ingest', label: '摄入队列', icon: 'morph:layers' },
  { key: 'generated', label: '生成内容', icon: 'morph:sparkles' },
  { key: 'review', label: '审核', icon: 'morph:list-checks' },
  { key: 'lint', label: 'Lint', icon: 'morph:shield-check' },
  { key: 'research', label: '深度研究', icon: 'morph:flask-conical' },
  { key: 'search', label: '检索', icon: 'morph:zoom-in' },
  { key: 'graph', label: '图谱', icon: 'morph:waypoints' },
  {
    key: 'members',
    label: '成员与权限',
    icon: 'morph:users',
    unavailable: {
      reason: '空间成员 ACL 没有后端契约：成员列表、主体选择与空间级授权都无法在此维护，界面不提供假入口。',
      contracts: [
        'GET/POST/PUT/DELETE /api/platform/wiki/spaces/{spaceId}/members[/{memberId}]，列表分页、更新带 CAS token',
        'SpaceACL（READ/COMMENT/EDIT/MANAGE）与 aclMode=LEGACY|RESTRICTED 语义，旧空间保持全局权限行为',
        '管理用例需同时通过能力闸门、全局权限码与空间实例 ACL，缺失/过期关系 fail closed',
      ],
    },
  },
  {
    key: 'comments',
    label: '评论',
    icon: 'morph:message-square',
    unavailable: {
      reason: '评论与审核接口没有后端契约：无法查看、置顶、隐藏或删除评论，界面不伪造评论数据。',
      contracts: [
        'GET /api/public/wiki/{spaceSlug}/nodes/{nodeId}/comments（分页、仅已发布节点上的公开评论）',
        '管理端 /api/platform/wiki/comments 分页与审核动作，状态 PENDING/PUBLISHED/HIDDEN/DELETED',
        '作者 ID 只从认证上下文推导，匿名策略默认关闭；管理员操作写审计',
      ],
    },
  },
  { key: 'settings', label: '设置', icon: 'morph:settings' },
]
export const wikiPendingCapabilities: { key: string, label: string, reason: string }[] = [
  {
    key: 'generated-source',
    label: '生成来源标记',
    reason: '页面/版本缺少生成 run、model、prompt revision 与 source refs 字段：「生成内容」暂按页面类型分组呈现现有页面。',
  },
]

export const defaultWikiManagePanel = 'overview'

export function isWikiManagePanel(key: unknown): key is string {
  return typeof key === 'string' && wikiManagePanels.some(panel => panel.key === key)
}

export function wikiManagePanelMeta(key: string): WikiManagePanelMeta | undefined {
  return wikiManagePanels.find(panel => panel.key === key)
}
