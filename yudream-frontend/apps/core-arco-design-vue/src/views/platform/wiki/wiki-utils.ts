import type { InjectionKey, Ref } from 'vue'
import type { ComputedRef } from 'vue'
import type { WikiNode, WikiSpace } from '@/api/modules/platform-wiki'

// 页面类型中文展示与配色（同时用于 FaTag 与图谱着色）
export interface PageTypeMeta {
  label: string
  color: string
}

export const pageTypeMeta: Record<string, PageTypeMeta> = {
  source_summary: { label: '资料摘要', color: 'rgb(var(--primary-3))' },
  entity: { label: '实体', color: 'rgb(var(--primary-4))' },
  concept: { label: '概念', color: 'rgb(var(--primary-6))' },
  synthesis: { label: '综合分析', color: 'rgb(var(--primary-7))' },
  comparison: { label: '对比', color: 'rgb(var(--primary-5))' },
  query: { label: '查询归档', color: 'rgb(var(--primary-8))' },
  research: { label: '深度研究', color: 'rgb(var(--primary-9))' },
  overview: { label: '全局概要', color: 'rgb(var(--primary-2))' },
  toc: { label: '内容目录', color: 'var(--color-text-2)' },
  log: { label: '操作日志', color: 'var(--color-text-3)' },
}

export function pageTypeLabel(pageType?: string): string {
  return (pageType && pageTypeMeta[pageType]?.label) || pageType || '概念'
}

export function pageTypeColor(pageType?: string): string {
  return (pageType && pageTypeMeta[pageType]?.color) || pageTypeMeta.concept.color
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
  RUNNING: { label: '运行中', color: 'rgb(var(--primary-6))' },
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
    RUNNING: { label: '抽取中', color: 'rgb(var(--primary-6))' },
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
  if (!href.startsWith('wiki://'))
    return null
  event.preventDefault()
  event.stopPropagation()
  try {
    return decodeURIComponent(href.slice('wiki://'.length))
  }
  catch {
    return href.slice('wiki://'.length)
  }
}

// 管理端工作台共享状态（provide/inject）
export interface WikiWorkbenchStore {
  spaces: Ref<WikiSpace[]>
  spaceId: Ref<string>
  space: ComputedRef<WikiSpace | null>
  tree: Ref<WikiNode[]>
  flatTree: ComputedRef<FlatWikiNode[]>
  selectedNode: Ref<WikiNode | null>
  loadingTree: Ref<boolean>
  reloadTree: () => Promise<void>
  /** 重新加载知识库列表（删除知识库后调用） */
  reloadSpaces: () => Promise<void>
  selectNode: (node: WikiNode | null) => void
  openPanel: (panel: string) => void
  // 按标题 / nodeId / path 打开页面（切到目录面板并选中）
  openPage: (ref: { title?: string, nodeId?: string, path?: string }) => void
  findNode: (ref: { title?: string, nodeId?: string, path?: string }) => WikiNode | null
}

export const wikiWorkbenchKey: InjectionKey<WikiWorkbenchStore> = Symbol('wiki-workbench')
