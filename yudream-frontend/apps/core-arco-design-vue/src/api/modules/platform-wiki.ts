import type { ApiResponse } from './system-client'
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
  neo4jConnectionCode?: string
  chunkSize: number
  chunkOverlap: number
  topK: number
  queryExpansionEnabled: boolean
  rerankEnabled: boolean
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

export interface WikiChatCitation { title: string; path: string; nodeId?: string }
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
export const captionWikiSourceImages = (id: string) => systemClient.post<unknown, ApiResponse<WikiSource>>(`api/platform/wiki/sources/${id}/caption-images`)

// 摄入队列
export const enqueueWikiIngest = (spaceId: string, sourceId: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/sources/${sourceId}/ingest`)
export const fetchWikiIngestTasks = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiIngestTask[]>>(`api/platform/wiki/spaces/${spaceId}/ingest-tasks`)
export const cancelWikiIngestTask = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/ingest-tasks/${id}/cancel`)
export const retryWikiIngestTask = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/ingest-tasks/${id}/retry`)
export const wikiIngestEventsEndpoint = (spaceId: string) => import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
  ? `/proxy/api/platform/wiki/spaces/${spaceId}/ingest-events`
  : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/spaces/${spaceId}/ingest-events`

// Lint / 审核 / 深度研究 / 图谱 / 迁移
export const lintWiki = (spaceId: string) => systemClient.post<unknown, ApiResponse<WikiLintReport>>(`api/platform/wiki/spaces/${spaceId}/lint`)
export const fetchWikiReviews = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiReviewItem[]>>(`api/platform/wiki/spaces/${spaceId}/reviews`)
export const fetchWikiPendingReviews = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiReviewItem[]>>(`api/platform/wiki/spaces/${spaceId}/reviews/pending`)
export const resolveWikiReview = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/reviews/${id}/resolve`)
export const dismissWikiReview = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/reviews/${id}/dismiss`)
export const executeWikiReview = (id: string, action: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/reviews/${id}/execute`, { action })
export const planWikiResearch = (spaceId: string, seed: string) => systemClient.post<unknown, ApiResponse<WikiResearchPlan>>(`api/platform/wiki/spaces/${spaceId}/research/plan`, { seed })
export const startWikiResearch = (spaceId: string, data: { topic: string; queries: string[] }) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/research/start`, data)
export const fetchWikiGraph = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiGraphSnapshot>>(`api/platform/wiki/spaces/${spaceId}/graph`)
export const exportWikiArchive = (spaceId: string) => systemClient.get<unknown, ApiResponse<string>>(`api/platform/wiki/spaces/${spaceId}/export`)
export const importWikiArchive = (content: string) => systemClient.post<unknown, ApiResponse<string>>('api/platform/wiki/import', { content })
export const rebuildWikiIndex = (spaceId: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/rebuild-index`)

// 智能问答（LLM 经 wiki.search 工具检索）
export const chatWiki = (spaceId: string, data: { question: string; history?: WikiChatTurn[] }) => systemClient.post<unknown, ApiResponse<WikiChatResult>>(`api/platform/wiki/spaces/${spaceId}/chat`, data)
// 流式问答端点（自定义 SSE：delta / tool / citations / done / error）
export const wikiChatStreamEndpoint = (spaceId: string) => import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
  ? `/proxy/api/platform/wiki/spaces/${spaceId}/chat/stream`
  : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/spaces/${spaceId}/chat/stream`

// 流式问答端点（AG-UI 协议）
export const wikiChatAguiEndpoint = (spaceId: string) => import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
  ? `/proxy/api/platform/wiki/spaces/${spaceId}/chat/agui`
  : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/spaces/${spaceId}/chat/agui`

// SSE 端点
export const wikiPublicationEventsEndpoint = (nodeId: string) => import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
  ? `/proxy/api/platform/wiki/nodes/${nodeId}/publication-events`
  : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/nodes/${nodeId}/publication-events`

// 公开端
export const fetchPublicWikiTree = (slug: string) => systemClient.get<unknown, ApiResponse<WikiNode[]>>(`api/public/wiki/${slug}/tree`)
export const fetchPublicWikiSpaces = () => systemClient.get<unknown, ApiResponse<WikiPublicSpace[]>>('api/public/wiki/spaces')

// 公开站点浮窗助手的 AG-UI 流式问答端点（无需登录）
export const wikiPublicChatAguiEndpoint = (slug: string) => import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
  ? `/proxy/api/public/wiki/${encodeURIComponent(slug)}/chat/agui`
  : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/public/wiki/${encodeURIComponent(slug)}/chat/agui`
export async function hasPublicWikiSpaces(): Promise<boolean> {
  const baseUrl = import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
    ? '/proxy/'
    : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/`
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
