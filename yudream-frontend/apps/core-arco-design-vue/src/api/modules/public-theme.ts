import type { CmsTemplateItem } from './platform-cms'
import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface PublicThemeContextQuery {
  /** 请求的主题块编码列表；空表示不需要块数据 */
  blocks?: string[]
  /** 单块数据量上限 */
  limit?: number
  /** CMS 最新文章数量；空或 0 表示不需要 */
  cmsLatest?: number
}

export interface PublicThemeContext {
  themeCode: string
  themeConfig: Record<string, any>
  /** 插件主题块提供者贡献的数据，键为块 code；插件未装载/不支持当前主题时缺省 */
  blocks: Record<string, any>
  cmsPagesLatest: CmsTemplateItem[]
}

/**
 * 公开主题上下文（匿名）：Vue 原生主题页按需聚合主题配置、插件数据块与 CMS 最新文章。
 */
export default {
  context: (query?: PublicThemeContextQuery) => {
    return systemClient.get<unknown, ApiResponse<PublicThemeContext>>('api/public/theme/context', {
      params: {
        blocks: query?.blocks?.length ? query.blocks.join(',') : undefined,
        limit: query?.limit,
        cmsLatest: query?.cmsLatest,
      },
    })
  },
}
