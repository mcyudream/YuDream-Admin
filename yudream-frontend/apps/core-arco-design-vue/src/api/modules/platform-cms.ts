import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type PageStatus = 'DRAFT' | 'PUBLISHED'
export type PageTemplate = 'DEFAULT' | 'LANDING' | 'DOC' | 'BLANK'
export type HomeSectionType = 'HERO' | 'FEATURE' | 'CONTENT' | 'CTA'
export type CmsBlockKind = 'ATOMIC' | 'PRESET'

export interface CmsPageParams {
  page: number
  size: number
  keyword?: string
  category?: string
  tag?: string
  /** 目标主题编码；缺省为当前激活的公开站主题 */
  theme?: string
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
  id: string
  title: string
  slug: string
  summary?: string
  excerpt?: string
  coverImageUrl?: string
  categories?: string[]
  tags?: string[]
  markdownContent?: string
  htmlContent?: string
  cssContent?: string
  jsContent?: string
  builderProjectJson?: string
  seoTitle?: string
  seoDescription?: string
  template?: PageTemplate
  status: PageStatus
  /** 页面归属的主题编码（default 或插件 code） */
  themeCode?: string
  /** 主题内由插件托管的来源插件编码 */
  sourcePluginCode?: string | null
  /** 公开站载荷携带的主题公开配置（敏感字段已剔除） */
  themeConfig?: Record<string, any>
  publishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface CmsPagePayload {
  title: string
  slug: string
  summary?: string
  excerpt?: string
  coverImageUrl?: string
  categories?: string[]
  tags?: string[]
  markdownContent?: string
  htmlContent?: string
  cssContent?: string
  jsContent?: string
  builderProjectJson?: string
  seoTitle?: string
  seoDescription?: string
  template?: PageTemplate
  status: PageStatus
}

export interface HomePageLayout {
  id?: string
  /** 布局归属的主题编码（default 或插件 code） */
  themeCode?: string
  title?: string
  subtitle?: string
  /** 该布局当前应用的首页方案编码 */
  theme?: string
  heroImageUrl?: string
  settings?: Record<string, string>
  sections: HomeSection[]
  published: boolean
  /** 公开站载荷携带的主题公开配置（敏感字段已剔除） */
  themeConfig?: Record<string, any>
  createTime?: string
  updateTime?: string
}

export type HomePagePresetSource = 'USER' | 'PLUGIN' | 'SNAPSHOT'

export interface HomePagePreset {
  code: string
  name: string
  description?: string
  source: HomePagePresetSource
  pluginCode?: string
  /** 方案归属的主题编码（default 或插件 code） */
  themeCode?: string
  sectionCount?: number
  /** 是否为当前首页布局正在使用的方案 */
  active?: boolean
  createTime?: string
  updateTime?: string
}

export interface CmsTemplateItem {
  id: string
  source: 'cms' | 'knowledge' | 'knowledge-space'
  title: string
  slug?: string
  summary?: string
  excerpt?: string
  url: string
  content?: string
  htmlContent?: string
  markdownContent?: string
  spaceSlug?: string
  path?: string
  createdAt?: string
  publishedAt?: string
  updatedAt?: string
}

export interface CmsTemplateContext {
  cms: {
    pages: {
      latest: CmsTemplateItem[]
    }
  }
  knowledge: {
    spaces: CmsTemplateItem[]
    pages: CmsTemplateItem[]
    latest: CmsTemplateItem[]
    featured: CmsTemplateItem[]
  }
  /** 主题块提供者贡献的数据，键为块 code；块不可用时缺省 */
  blocks?: Record<string, any>
}

export interface CmsTemplateContextQuery {
  cmsLatestLimit?: number
  knowledgeSpacesLimit?: number
  knowledgePagesLimit?: number
  knowledgeLatestLimit?: number
  knowledgeFeaturedLimit?: number
  /** 模板引用到的主题块 code 列表 */
  blocks?: string[]
  /** 块数据条数上限 */
  blockLimit?: number
}

export interface CmsBlock {
  id: string
  code: string
  name: string
  description?: string
  category?: string
  kind: CmsBlockKind
  icon?: string
  previewImageUrl?: string
  htmlContent?: string
  cssContent?: string
  jsContent?: string
  builderProjectJson?: string
  tags?: string[]
  enabled: boolean
  builtin: boolean
  sort?: number
  createTime?: string
  updateTime?: string
}

export interface CmsBlockPayload {
  code: string
  name: string
  description?: string
  category?: string
  kind: CmsBlockKind
  icon?: string
  previewImageUrl?: string
  htmlContent?: string
  cssContent?: string
  jsContent?: string
  builderProjectJson?: string
  tags?: string[]
  enabled: boolean
}

export async function fetchPublicCmsChrome(): Promise<HomePageLayout | null> {
  const baseUrl = import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY
    ? '/proxy/'
    : `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/`
  try {
    const response = await fetch(`${baseUrl}api/public/cms/home`, { headers: { 'Accept-Language': 'zh-CN' } })
    if (!response.ok) return null
    const result = await response.json() as { code?: number, data?: HomePageLayout }
    return result.code === 200 ? result.data || null : null
  }
  catch {
    return null
  }
}

export default {
  page: (params: CmsPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<CmsPage>>>('api/platform/cms/pages', { params })
  },
  createPage: (data: CmsPagePayload, theme?: string) => {
    return systemClient.post<unknown, ApiResponse<CmsPage>>('api/platform/cms/pages', data, { params: { theme } })
  },
  updatePage: (id: string, data: CmsPagePayload) => {
    return systemClient.put<unknown, ApiResponse<CmsPage>>(`api/platform/cms/pages/${id}`, data)
  },
  deletePage: (id: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/cms/pages/${id}`)
  },
  publish: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/pages/${id}/publish`)
  },
  unpublish: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/pages/${id}/unpublish`)
  },
  home: (theme?: string) => {
    return systemClient.get<unknown, ApiResponse<HomePageLayout>>('api/platform/cms/home', { params: { theme } })
  },
  saveHome: (data: HomePageLayout, theme?: string) => {
    return systemClient.put<unknown, ApiResponse<HomePageLayout>>('api/platform/cms/home', data, { params: { theme } })
  },
  presets: (theme?: string) => {
    return systemClient.get<unknown, ApiResponse<HomePagePreset[]>>('api/platform/cms/presets', { params: { theme } })
  },
  savePreset: (data: { name: string, description?: string }, theme?: string) => {
    return systemClient.post<unknown, ApiResponse<HomePagePreset>>('api/platform/cms/presets', data, { params: { theme } })
  },
  applyPreset: (code: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/presets/${encodeURIComponent(code)}/apply`)
  },
  deletePreset: (code: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/cms/presets/${encodeURIComponent(code)}`)
  },
  publicHome: () => {
    return systemClient.get<unknown, ApiResponse<HomePageLayout>>('api/public/cms/home')
  },
  publicPage: (slug: string) => {
    return systemClient.get<unknown, ApiResponse<CmsPage>>('api/public/cms/pages', { params: { slug } })
  },
  publicPages: (params: CmsPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<CmsPage>>>('api/public/cms/pages/list', { params })
  },
  publicTemplateContext: (params?: CmsTemplateContextQuery) => {
    return systemClient.get<unknown, ApiResponse<CmsTemplateContext>>('api/public/cms/template-context', { params })
  },
  blockList: (params: { page: number, size: number, keyword?: string, category?: string, kind?: CmsBlockKind }) => {
    return systemClient.get<unknown, ApiResponse<PageResult<CmsBlock>>>('api/platform/cms/blocks', { params })
  },
  blockDetail: (id: string) => {
    return systemClient.get<unknown, ApiResponse<CmsBlock>>(`api/platform/cms/blocks/${id}`)
  },
  createBlock: (data: CmsBlockPayload) => {
    return systemClient.post<unknown, ApiResponse<CmsBlock>>('api/platform/cms/blocks', data)
  },
  updateBlock: (id: string, data: CmsBlockPayload) => {
    return systemClient.put<unknown, ApiResponse<CmsBlock>>(`api/platform/cms/blocks/${id}`, data)
  },
  deleteBlock: (id: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/cms/blocks/${id}`)
  },
  enableBlock: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/blocks/${id}/enable`)
  },
  disableBlock: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/cms/blocks/${id}/disable`)
  },
  blockCategories: () => {
    return systemClient.get<unknown, ApiResponse<string[]>>('api/platform/cms/blocks/categories')
  },
}
