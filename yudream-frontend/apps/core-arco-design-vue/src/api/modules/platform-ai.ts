import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface CmsPageGeneratePayload {
  title?: string
  prompt: string
  pageType?: string
  style?: string
  siteName?: string
}

export interface CmsPageGenerateResult {
  title?: string
  summary?: string
  htmlContent?: string
  cssContent?: string
  builderProjectJson?: string
  markdownContent?: string
}

export default {
  generateCmsPage: (data: CmsPageGeneratePayload) => {
    return systemClient.post<unknown, ApiResponse<CmsPageGenerateResult>>('api/platform/ai/cms/pages/generate', data)
  },
}
