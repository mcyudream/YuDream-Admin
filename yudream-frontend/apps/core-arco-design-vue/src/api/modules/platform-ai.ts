import type { ApiResponse } from './system-client'
import systemClient from './system-client'
import { prepareApiEncryption } from '@/utils/api-encryption'

export interface CmsPageGeneratePayload {
  title?: string
  prompt: string
  pageType?: string
  style?: string
  siteName?: string
  model?: string
  imageDataUrl?: string
  currentHtml?: string
  currentCss?: string
  currentProjectJson?: string
  thinkingEnabled?: boolean
}

export interface CmsPageGenerateResult {
  title?: string
  summary?: string
  htmlContent?: string
  cssContent?: string
  builderProjectJson?: string
  markdownContent?: string
  tools?: AiToolCallResult[]
}

export interface AiToolCallResult {
  toolName?: string
  action?: string
  permissionCode?: string
  message?: string
  payload?: Record<string, any>
}

export interface AiStreamEnvelope<T = Record<string, any>> {
  event?: string
  action?: string
  module?: string
  traceId?: string
  timestamp?: number
  payload?: T
}

export default {
  generateCmsPage: (data: CmsPageGeneratePayload) => {
    return systemClient.post<unknown, ApiResponse<CmsPageGenerateResult>>('api/platform/ai/cms/pages/generate', data)
  },
  generateCmsPageStreamEndpoint: () => {
    return streamEndpoint('/api/platform/ai/cms/pages/generate/stream')
  },
  generateCmsPageStreamRequest: async (data: CmsPageGeneratePayload): Promise<RequestInit> => {
    const headers: Record<string, string> = {
      'Accept-Language': 'zh-CN',
      'Content-Type': 'application/json',
    }
    const token = localStorage.getItem('token')
    if (token) {
      headers.Authorization = token
    }
    let body: unknown = data
    const encrypted = await prepareApiEncryption('api/platform/ai/cms/pages/generate/stream', data)
    if (encrypted) {
      Object.assign(headers, encrypted.headers)
      body = encrypted.body
    }
    return {
      method: 'POST',
      headers,
      body: JSON.stringify(body),
    }
  },
}

function streamEndpoint(path: string) {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) {
    return `/proxy${path}`
  }
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${path}`
}
