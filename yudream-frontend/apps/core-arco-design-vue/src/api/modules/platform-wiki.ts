import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface WikiSpace { id?: string; name: string; slug: string; description?: string; publicReadEnabled: boolean; externalSearchEnabled: boolean; embeddingProviderCode?: string; embeddingModelCode?: string; graphEnabled: boolean; graphProviderCode?: string; graphModelCode?: string; neo4jConnectionCode?: string; chunkSize: number; chunkOverlap: number; topK: number; queryExpansionEnabled: boolean; rerankEnabled: boolean }
export interface WikiNode { id: string; parentId?: string; title: string; slug: string; path?: string; nodeType: 'DIRECTORY' | 'PAGE'; sort: number; markdown?: string; publishedVersionId?: string; indexStatus: string; children: WikiNode[] }
export interface WikiSearchHit { score: number; nodeId: string; title: string; path: string; content: string; sourceUrl: string }
export interface WikiIndexSnapshot { chunks: Array<{ sequence: number; title: string; path: string; content: string }>; relations: Array<{ source: string; sourceType: string; relation: string; target: string; targetType: string; confidence: number }> }
export interface WikiPublicSpace { name: string; slug: string; description: string }
export const fetchWikiSpaces = () => systemClient.get<unknown, ApiResponse<WikiSpace[]>>('api/platform/wiki/spaces')
export const saveWikiSpace = (data: WikiSpace) => data.id ? systemClient.put<unknown, ApiResponse<WikiSpace>>(`api/platform/wiki/spaces/${data.id}`, data) : systemClient.post<unknown, ApiResponse<WikiSpace>>('api/platform/wiki/spaces', data)
export const fetchWikiTree = (spaceId: string) => systemClient.get<unknown, ApiResponse<WikiNode[]>>(`api/platform/wiki/spaces/${spaceId}/tree`)
export const saveWikiNode = (spaceId: string, data: Partial<WikiNode>) => data.id ? systemClient.put<unknown, ApiResponse<WikiNode>>(`api/platform/wiki/spaces/${spaceId}/nodes/${data.id}`, data) : systemClient.post<unknown, ApiResponse<WikiNode>>(`api/platform/wiki/spaces/${spaceId}/nodes`, data)
export const publishWikiNode = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}/publish`)
export const unpublishWikiNode = (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/wiki/nodes/${id}/unpublish`)
export const fetchWikiIndexSnapshot = (id: string) => systemClient.get<unknown, ApiResponse<WikiIndexSnapshot>>(`api/platform/wiki/nodes/${id}/index-results`)
export const testWikiSearch = (data: { spaceSlug: string; query: string; topK?: number; graphExpansion?: boolean }) => systemClient.post<unknown, ApiResponse<WikiSearchHit[]>>('api/platform/wiki/search-test', data)
export const wikiPublicationEventsEndpoint = (nodeId: string) => import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
  ? `/proxy/api/platform/wiki/nodes/${nodeId}/publication-events`
  : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/wiki/nodes/${nodeId}/publication-events`
export const fetchPublicWikiTree = (slug: string) => systemClient.get<unknown, ApiResponse<WikiNode[]>>(`api/public/wiki/${slug}/tree`)
export const fetchPublicWikiSpaces = () => systemClient.get<unknown, ApiResponse<WikiPublicSpace[]>>('api/public/wiki/spaces')
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
export const searchPublicWiki = (slug: string, data: { query: string; topK?: number; pathPrefix?: string; graphExpansion?: boolean }) => systemClient.post<unknown, ApiResponse<WikiSearchHit[]>>(`api/public/wiki/${slug}/search`, data)
