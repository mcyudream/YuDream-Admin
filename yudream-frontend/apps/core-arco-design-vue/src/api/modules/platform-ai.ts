import type { ApiResponse } from './system-client'
import type { AgentApplication } from './platform-agent'
import { prepareApiEncryption } from '@/utils/api-encryption'
import systemClient from './system-client'

export interface CmsChatHistoryMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface CmsPageGeneratePayload {
  target?: 'page' | 'home' | 'header' | 'footer'
  agentCode?: string
  title?: string
  prompt: string
  pageType?: string
  template?: string
  style?: string
  siteName?: string
  imageDataUrl?: string
  currentHtml?: string
  currentCss?: string
  currentJs?: string
  currentProjectJson?: string
  currentSelectionJson?: string
  cmsVariableContextJson?: string
  thinkingEnabled?: boolean
  history?: CmsChatHistoryMessage[]
}

export interface CmsPageGenerateResult {
  title?: string
  summary?: string
  htmlContent?: string
  cssContent?: string
  jsContent?: string
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
  /** CMS/AI 场景的可用 Agent 列表（platform:ai:generate 权限，不走 Agent 管理台权限） */
  availableAgents: () => {
    return systemClient.get<unknown, ApiResponse<AgentApplication[]>>('api/platform/ai/agents/available')
  },
  generateCmsPage: (data: CmsPageGeneratePayload) => {
    return systemClient.post<unknown, ApiResponse<CmsPageGenerateResult>>('api/platform/ai/cms/pages/generate', data)
  },
  generateCmsPageStreamEndpoint: () => {
    return streamEndpoint('/api/platform/ai/cms/pages/generate/stream')
  },
  generateCmsPageAguiWsEndpoint: () => {
    return streamEndpoint('/api/platform/ai/cms/pages/generate/ws')
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
