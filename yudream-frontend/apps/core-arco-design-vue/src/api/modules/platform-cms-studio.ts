import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface StudioState {
  themeCode: string
  revision: string
  documentJson: string | null
  publishedReleaseId: string | null
  updatedAt?: string
}
export interface StudioRelease { id: string, revision: string, createdAt?: string }
export interface StudioReleasePage { list: StudioRelease[], total: number | string }
export interface PublishedStudio { themeCode: string, releaseId: string, documentJson: string }
const base = (themeCode: string) => `api/platform/cms/studio/${encodeURIComponent(themeCode)}`

export default {
  get: (themeCode: string) => systemClient.get<unknown, ApiResponse<StudioState>>(base(themeCode)),
  saveDraft: (themeCode: string, expectedRevision: string, documentJson: string) => systemClient.put<unknown, ApiResponse<StudioState>>(`${base(themeCode)}/draft`, { expectedRevision, documentJson }),
  publish: (themeCode: string, expectedRevision: string) => systemClient.post<unknown, ApiResponse<StudioState>>(`${base(themeCode)}/publish`, { expectedRevision }),
  /** 停止使用新版站点：仅清线上指针，草稿与历史版本保留，公开站回退旧 CMS/原生主题 */
  unpublish: (themeCode: string, expectedRevision: string) => systemClient.post<unknown, ApiResponse<StudioState>>(`${base(themeCode)}/unpublish`, { expectedRevision }),
  releases: (themeCode: string, page: number, size = 12) => systemClient.get<unknown, ApiResponse<StudioReleasePage>>(`${base(themeCode)}/releases`, { params: { page, size } }),
  restore: (themeCode: string, releaseId: string, expectedRevision: string) => systemClient.post<unknown, ApiResponse<StudioState>>(`${base(themeCode)}/releases/${encodeURIComponent(releaseId)}/restore`, { expectedRevision }),
}

/** Public transport deliberately avoids the authenticated client and its login redirect interceptor. */
export async function fetchPublishedStudio(signal?: AbortSignal): Promise<PublishedStudio | null> {
  const baseUrl = `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/`
  const response = await fetch(`${baseUrl}api/public/cms/studio`, { signal, headers: { 'Accept-Language': 'zh-CN' } })
  // Older deployments do not expose the studio API yet; keep their CMS fully usable.
  if (response.status === 404) {
    return null
  }
  if (!response.ok) {
    throw new Error('网站发布文档加载失败，请重试')
  }
  // 原生 fetch 拿到的是未拦截的 BackendResult（{code,message,data}），不是 axios 拦截后的 ApiResponse
  const result = await response.json() as { code?: number, message?: string, data?: PublishedStudio | null }
  if (result.code !== 200) {
    throw new Error(result.message || '网站发布文档加载失败')
  }
  return result.data ?? null
}
