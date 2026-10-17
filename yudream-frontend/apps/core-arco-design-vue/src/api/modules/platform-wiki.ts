import type { ApiResponse, PageResult } from './system-client'
import type { AiRunSnapshot } from './platform-ai-runs'
import systemClient from './system-client'

export interface WikiSpace {
  id?: string
  name: string
  slug: string
  description?: string
  publicReadEnabled: boolean
  externalSearchEnabled: boolean
  embeddingProviderCode?: string
  embeddingModelCode?: string
  graphEnabled: boolean
  graphProviderCode?: string
  graphModelCode?: string
  graphTableCode?: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  queryExpansionEnabled: boolean
  rerankEnabled: boolean
  /** 重排序端点：未配置时检索回落到向量供应商的首个重排模型 */
  rerankProviderCode?: string
  rerankModelCode?: string
  hitImageLimit?: number
  purpose?: string
  schemaContent?: string
  language?: string
  chatProviderCode?: string
  chatModelCode?: string
  ingestProviderCode?: string
  ingestModelCode?: string
  visionProviderCode?: string
  visionModelCode?: string
  webSearchProviderCode?: string
  webSearchApiKey?: string
  webSearchInstanceUrl?: string
  webSearchEngine?: string
  contextWindowTokens: number
  sourceGroundedDefault: boolean
  watchEnabled: boolean
  watchFolderPath?: string
  /** 封面契约（NONE / SEEDED / UPLOAD）与稳定 seed；旧响应缺字段时按 NONE 处理 */
  coverType?: string
  coverSeed?: string
  coverFileObjectId?: string
  coverAlt?: string
  /** 访问模式：LEGACY 走全局权限码；RESTRICTED 只对空间成员授权开放（fail closed） */
  aclMode?: string
  /** 是否允许登录用户在公开页面发表评论；未开启时读者无法发新评论 */
  commentEnabled?: boolean
  /** 列表卡片聚合事实（由后端按空间补算）；旧响应或非列表端点可能缺字段 */
  pageCount?: number
  directoryCount?: number
  unpublishedPageCount?: number
  lastUpdatedAt?: string
  failedIngestCount?: number
}

export interface WikiNode {
  id: string
  parentId?: string
  title: string
  slug: string
  path?: string
  nodeType: 'DIRECTORY' | 'PAGE'
  sort: number
  markdown?: string
  body?: string
  publishedVersionId?: string
  indexStatus: string
  children: WikiNode[]
  pageType?: string
  sources?: string[]
  related?: string[]
  tags?: string[]
  summary?: string
}

export interface WikiSearchHit {
  score: number
  nodeId?: string
  sourceId?: string
  kind: string
  title: string
  path: string
  content: string
  sourceUrl?: string
  spaceSlug?: string
  spaceName?: string
  /** 命中页面正文引用的站内图片；alt 是原文图注，generatedCaption 是视觉模型描述 */
  images?: { index?: number, url?: string, alt?: string, generatedCaption?: string, caption?: string }[]
}

export interface WikiIndexSnapshot {
  chunks: Array<{ sequence: number; title: string; path: string; content: string }>
  relations: Array<{ source: string; sourceType: string; relation: string; target: string; targetType: string; confidence: number }>
}

export interface WikiPublicSpace { name: string; slug: string; description: string }

export interface WikiSourceImage {
  fileObjectId: string
  pageNumber: number
  sequence: number
  caption?: string
  captionStatus: string
  captionProviderCode?: string
  captionModelCode?: string
  width: number
  height: number
  contentType: string
  url?: string
}

export interface WikiSource {
  id: string
  spaceId: string
  folderPath: string
  fileName: string
  title: string
  kind: string
  url?: string
  mimeType: string
  format: string
  fileObjectId?: string
  contentHash: string
  extractedText: string
  extractionStatus: string
  extractionError?: string
  images: WikiSourceImage[]
  ingestStatus: string
  ingestError?: string
  ingestedAt?: string
  sort: number
  fileUrl?: string
}

export interface WikiIngestTask {
  id: string
  spaceId: string
  sourceId?: string
  requestedByUserId?: string
  aiRunId?: string
  taskType: string
  status: string
  attempts: number
  maxAttempts: number
  errorMessage?: string
  phase: string
  percent: number
  startedAt?: string
  finishedAt?: string
  sortOrder: number
  payloadJson?: string
}

export interface WikiReviewItem {
  id: string
  spaceId: string
  sourceId?: string
  itemType: string
  title: string
  description: string
  suggestedAction: string
  searchQueries: string[]
  pageTitles: string[]
  status: string
  resolvedAt?: string
  createTime: string
}

export interface WikiLintIssue {
  category: string
  severity: string
  title: string
  description: string
  pageTitles: string[]
  suggestedAction: string
  searchQueries: string[]
}

export interface WikiLintReport {
  generatedAt: string
  summary: string
  issues: WikiLintIssue[]
}

export interface WikiGraphSnapshot {
  nodes: Array<{ id: string; title: string; type: string; degree: number; community: string }>
  edges: Array<{ source: string; target: string; weight: number; signal: string }>
  communities: Array<{ id: string; label: string; nodeIds: string[]; size: number; cohesion: number; lowCohesion: boolean }>
  insights: Array<{ kind: string; title: string; description: string; nodeIds: string[]; searchQueries: string[] }>
}

export interface WikiResearchPlan { topic: string; rationale: string; queries: string[] }

export interface WikiChatCitation { title: string; path: string; nodeId?: string; excerpt?: string; images?: { url?: string, caption?: string }[] }
export interface WikiChatResult { answer: string; citations: WikiChatCitation[] }
export interface WikiChatTurn { role: string; content: string }

// 知识库与节点
export const fetchWikiSpaces = () => systemClient.get<unknown, ApiResponse<WikiSpace[]>>('api/platform/wiki/spaces')
export const saveWikiSpace = (data: WikiSpace) => data.id
  ? systemClient.put<unknown, ApiResponse<WikiSpace>>(`api/platform/wiki/spaces/${data.id}`, data)
  : systemClient.post<unknown, ApiResponse<WikiSpace>>('api/platform/wiki/spaces', data)
export const deleteWikiSpace = (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${id}`)
export const fetchWikiTree = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiNode[]>>(`api/platform/wiki/spaces/${spaceId}/tree`)
export const saveWikiNode = (spaceId: string, data: Partial<WikiNode>) => data.id
  ? systemClient.put<unknown, ApiResponse<WikiNode>>(`api/platform/wiki/spaces/${spaceId}/nodes/${data.id}`, data)
  : systemClient.post<unknown, ApiResponse<WikiNode>>(`api/platform/wiki/spaces/${spaceId}/nodes`, data)
export const deleteWikiNode = (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}`)
export const publishWikiNode = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}/publish`)
export const unpublishWikiNode = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}/unpublish`)
export const fetchWikiIndexSnapshot = (id: string) => systemClient.get<unknown, ApiResponse<WikiIndexSnapshot>>(`api/platform/wiki/nodes/${id}/index-results`)
export const testWikiSearch = (data: { spaceSlug: string; query: string; topK?: number; graphExpansion?: boolean; sourceGrounded?: boolean }) => systemClient.post<unknown, ApiResponse<WikiSearchHit[]>>('api/platform/wiki/search-test', data)

// 资料源
export const fetchWikiSources = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiSource[]>>(`api/platform/wiki/spaces/${spaceId}/sources`)
export const uploadWikiSource = (spaceId: string, folderPath: string, file: File) => {
  const form = new FormData()
  form.append('file', file)
  return systemClient.post<unknown, ApiResponse<WikiSource>>(
    `api/platform/wiki/spaces/${spaceId}/sources/upload?folderPath=${encodeURIComponent(folderPath)}`, form)
}
export const importWikiUrls = (spaceId: string, data: { folderPath?: string; urls: string[] }) => systemClient.post<unknown, ApiResponse<WikiSource[]>>(`api/platform/wiki/spaces/${spaceId}/sources/import-urls`, data)
export const createWikiTextSource = (spaceId: string, data: { folderPath?: string; title: string; content: string }) => systemClient.post<unknown, ApiResponse<WikiSource>>(`api/platform/wiki/spaces/${spaceId}/sources/text`, data)
export const updateWikiTextSource = (id: string, data: { title: string; content: string }) => systemClient.put<unknown, ApiResponse<WikiSource>>(`api/platform/wiki/sources/${id}/text`, data)
export const deleteWikiSource = (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/wiki/sources/${id}`)
export const captionWikiSourceImages = (id: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/wiki/sources/${id}/caption-images/runs`)
export const startWikiChatRun = (spaceId: string, data: { question: string, sessionId: string }) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/wiki/spaces/${spaceId}/chat/runs`, data)

// 摄入队列
export const enqueueWikiIngest = (spaceId: string, sourceId: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/sources/${sourceId}/ingest`)
export const fetchWikiIngestTasks = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiIngestTask[]>>(`api/platform/wiki/spaces/${spaceId}/ingest-tasks`)
export const cancelWikiIngestTask = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/ingest-tasks/${id}/cancel`)
export const retryWikiIngestTask = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/ingest-tasks/${id}/retry`)
export const deleteWikiIngestTask = (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/wiki/ingest-tasks/${id}`)
export const clearWikiIngestTasks = (spaceId: string) => systemClient.delete<unknown, ApiResponse<number>>(`api/platform/wiki/spaces/${spaceId}/ingest-tasks`)
export const wikiIngestEventsEndpoint = (spaceId: string) => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/spaces/${spaceId}/ingest-events`

// Lint / 审核 / 深度研究 / 图谱 / 迁移
export const lintWiki = (spaceId: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/wiki/spaces/${spaceId}/lint/runs`)
export const fetchWikiReviews = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiReviewItem[]>>(`api/platform/wiki/spaces/${spaceId}/reviews`)
export const fetchWikiPendingReviews = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiReviewItem[]>>(`api/platform/wiki/spaces/${spaceId}/reviews/pending`)
export const resolveWikiReview = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/reviews/${id}/resolve`)
export const dismissWikiReview = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/reviews/${id}/dismiss`)
export const executeWikiReview = (id: string, action: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/reviews/${id}/execute`, { action })
export const planWikiResearch = (spaceId: string, seed: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/wiki/spaces/${spaceId}/research/plan/runs`, { seed })
export const startWikiResearch = (spaceId: string, data: { topic: string; queries: string[] }) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/research/start`, data)
export const fetchWikiGraph = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiGraphSnapshot>>(`api/platform/wiki/spaces/${spaceId}/graph`)
export const exportWikiArchive = (spaceId: string) => systemClient.get<unknown, ApiResponse<string>>(`api/platform/wiki/spaces/${spaceId}/export`)
export const importWikiArchive = (content: string) => systemClient.post<unknown, ApiResponse<string>>('api/platform/wiki/import', { content })
export const rebuildWikiIndex = (spaceId: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/rebuild-index`)

// ---- 空间成员（实例 ACL）：全局权限码 + 空间 MANAGE 双闸门在后端 ----

export type WikiMemberSubjectType = 'USER' | 'ROLE'
/** 动作语义包含：MANAGE > EDIT > COMMENT > READ（高阶覆盖低阶，不再逐条重复勾选） */
export type WikiAclAction = 'READ' | 'COMMENT' | 'EDIT' | 'MANAGE'

export interface WikiSpaceMember {
  id: string
  spaceId?: string
  subjectType: WikiMemberSubjectType
  subjectId: string
  subjectName?: string
  actions: WikiAclAction[]
  createdByUserId?: string
  /** 调整授权时必须回传的期望版本（CAS） */
  version: number
}

export const fetchWikiSpaceMembers = (spaceId: string, params: { page?: number, size?: number }) => systemClient.get<unknown, ApiResponse<PageResult<WikiSpaceMember>>>(`api/platform/wiki/spaces/${spaceId}/members`, { params })
export const createWikiSpaceMember = (spaceId: string, data: { subjectType: WikiMemberSubjectType, subjectId: string, actions: WikiAclAction[] }) => systemClient.post<unknown, ApiResponse<WikiSpaceMember>>(`api/platform/wiki/spaces/${spaceId}/members`, data)
export const updateWikiSpaceMember = (spaceId: string, memberId: string, data: { actions: WikiAclAction[], expectedVersion: number }) => systemClient.put<unknown, ApiResponse<WikiSpaceMember>>(`api/platform/wiki/spaces/${spaceId}/members/${memberId}`, data)
export const deleteWikiSpaceMember = (spaceId: string, memberId: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/members/${memberId}`)
/** 启用空间成员授权（RESTRICTED）：后端保留既有成员，并确保至少一名可管理成员 */
export const enableWikiRestrictedAcl = (spaceId: string) => systemClient.post<unknown, ApiResponse<WikiSpace>>(`api/platform/wiki/spaces/${spaceId}/acl/restricted`)

// ---- 治理端评论审核：状态机 PENDING → PUBLISHED ⇄ HIDDEN，软删除可恢复 ----

export interface WikiCommentAnchor {
  exact: string
  prefix?: string
  suffix?: string
  blockOrdinal?: number
  blockType?: string
  tableRow?: number
  tableCell?: number
  startOffset?: number
  endOffset?: number
}

export interface WikiManagedComment {
  id: string
  /** 空间归属（仅治理端返回） */
  spaceId?: string
  nodeId?: string
  parentId?: string
  authorType?: string
  authorUserId?: string
  authorName?: string
  content: string
  anchor?: WikiCommentAnchor | null
  versionId?: string
  revision?: string
  contentHash?: string
  status?: string
  pinned?: boolean
  createTime?: string
  updateTime?: string
}

export const fetchWikiManagedComments = (spaceId: string, params: { page?: number, size?: number, status?: string }) => systemClient.get<unknown, ApiResponse<PageResult<WikiManagedComment>>>(`api/platform/wiki/spaces/${spaceId}/comments`, { params })
export type WikiCommentReviewAction = 'publish' | 'hide' | 'restore' | 'delete' | 'pin' | 'unpin'
export const reviewWikiComment = (commentId: string, action: WikiCommentReviewAction) => systemClient.post<unknown, ApiResponse<WikiManagedComment>>(`api/platform/wiki/comments/${commentId}/${action}`)


// 智能问答（LLM 经 wiki.search 工具检索）
export const chatWiki = (spaceId: string, data: { question: string; history?: WikiChatTurn[] }) => systemClient.post<unknown, ApiResponse<WikiChatResult>>(`api/platform/wiki/spaces/${spaceId}/chat`, data)
// 流式问答端点（自定义 SSE：delta / tool / citations / done / error）
export const wikiChatStreamEndpoint = (spaceId: string) => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/spaces/${spaceId}/chat/stream`

// 流式问答端点（AG-UI 协议）
export const wikiChatAguiEndpoint = (spaceId: string) => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/spaces/${spaceId}/chat/agui`

// SSE 端点
export const wikiPublicationEventsEndpoint = (nodeId: string) => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/nodes/${nodeId}/publication-events`

// 公开端
export const fetchPublicWikiTree = (slug: string) => systemClient.get<unknown, ApiResponse<WikiNode[]>>(`api/public/wiki/${slug}/tree`)
export const fetchPublicWikiSpaces = () => systemClient.get<unknown, ApiResponse<WikiPublicSpace[]>>('api/public/wiki/spaces')
export const startPublicWikiChatRun = (slug: string, question: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/public/wiki/${encodeURIComponent(slug)}/chat/user/runs`, { question })

// 公开站点浮窗助手的 AG-UI 流式问答端点（无需登录）
export const wikiPublicChatAguiEndpoint = (slug: string) => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/public/wiki/${encodeURIComponent(slug)}/chat/agui`
export async function hasPublicWikiSpaces(): Promise<boolean> {
  const baseUrl = `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/`
  try {
    const response = await fetch(`${baseUrl}api/public/wiki/spaces`, { headers: { 'Accept-Language': 'zh-CN' } })
    if (!response.ok) return false
    const result = await response.json() as { code?: number, data?: WikiPublicSpace[] }
    return result.code === 200 && Boolean(result.data?.length)
  }
  catch {
    return false
  }
}
export const searchPublicWiki = (slug: string, data: { query: string }) => systemClient.post<unknown, ApiResponse<WikiSearchHit[]>>(`api/public/wiki/${slug}/search`, data)
export const searchAllPublicWiki = (data: { query: string, spaceSlug?: string }) => systemClient.post<unknown, ApiResponse<WikiSearchHit[]>>('api/public/wiki/search', data)

// 公开端原文档目录：已摄入的原始资料
export interface WikiPublicDocument {
  id: string
  title: string
  folderPath?: string
  kind?: string
  format?: string
}
export interface WikiPublicDocumentDetail extends WikiPublicDocument {
  content?: string
  images?: { url?: string, caption?: string, width?: number, height?: number }[]
}
export const fetchPublicWikiDocuments = (slug: string) => systemClient.get<unknown, ApiResponse<WikiPublicDocument[]>>(`api/public/wiki/${slug}/documents`)
export const fetchPublicWikiDocument = (slug: string, sourceId: string) => systemClient.get<unknown, ApiResponse<WikiPublicDocumentDetail>>(`api/public/wiki/${slug}/documents/${sourceId}`)
