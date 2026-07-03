import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type PageStatus = 'DRAFT' | 'PUBLISHED'
export type HomeSectionType = 'HERO' | 'FEATURE' | 'CONTENT' | 'CTA'

export interface CmsPageParams {
  page: number
  size: number
  keyword?: string
}

export interface HomeSection {
  id?: string
  type: HomeSectionType
  title?: string
  subtitle?: string
  mediaUrl?: string
  actionText?: string
  actionUrl?: string
  settings?: Record<string, string>
  sort?: number
  visible?: boolean
}

export interface CmsPage {
  id: number
  title: string
  slug: string
  summary?: string
  markdownContent?: string
  seoTitle?: string
  seoDescription?: string
  status: PageStatus
  publishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface CmsPagePayload {
  title: string
  slug: string
  summary?: string
  markdownContent?: string
  seoTitle?: string
  seoDescription?: string
  status: PageStatus
}

export interface HomePageLayout {
  id?: number
  title?: string
  subtitle?: string
  theme?: string
  heroImageUrl?: string
  settings?: Record<string, string>
  sections: HomeSection[]
  published: boolean
  createTime?: string
  updateTime?: string
}

export default {
  page: (params: CmsPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<CmsPage>>>('api/platform/cms/pages', { params })
  },
  createPage: (data: CmsPagePayload) => {
    return systemClient.post<unknown, ApiResponse<CmsPage>>('api/platform/cms/pages', data)
  },
  updatePage: (id: number, data: CmsPagePayload) => {
    return systemClient.put<unknown, ApiResponse<CmsPage>>(`api/platform/cms/pages/${id}`, data)
  },
  publish: (id: number) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/pages/${id}/publish`)
  },
  unpublish: (id: number) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/pages/${id}/unpublish`)
  },
  home: () => {
    return systemClient.get<unknown, ApiResponse<HomePageLayout>>('api/platform/cms/home')
  },
  saveHome: (data: HomePageLayout) => {
    return systemClient.put<unknown, ApiResponse<HomePageLayout>>('api/platform/cms/home', data)
  },
  publicHome: () => {
    return systemClient.get<unknown, ApiResponse<HomePageLayout>>('api/public/cms/home')
  },
  publicPage: (slug: string) => {
    return systemClient.get<unknown, ApiResponse<CmsPage>>('api/public/cms/pages', { params: { slug } })
  },
}
