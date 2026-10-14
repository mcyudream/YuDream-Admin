import type { ApiResponse } from './system-client'
import { normalizeAiQuota, normalizeAiQuotaConfig, normalizeAiQuotaReservations, parseAiTokenCount } from '@/utils/ai-quota'
import systemClient from './system-client'

export type ChatScopeType = 'GENERAL' | 'AGENT' | 'WIKI' | 'CONSOLE'

export interface ChatSession {
  id: string
  userId?: string
  title: string
  scopeType?: ChatScopeType
  agentCode?: string
  spaceSlug?: string
  providerCode?: string
  modelCode?: string
  messageCount: number
  pinned: boolean
  lastMessageAt?: string
  createTime?: string
}

export interface ChatUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface ChatCitation {
  title: string
  path?: string
  nodeId?: string
  excerpt?: string
  spaceSlug?: string
  spaceName?: string
  sourceUrl?: string
  images?: { url?: string, caption?: string }[]
}

export interface ChatContextRef {
  type: string
  target: string
  label?: string
}

export interface ChatToolCall {
  toolCallId?: string
  toolName?: string
  status?: 'executing' | 'complete' | 'error' | 'cancelled'
  message?: string
  payload?: Record<string, unknown>
}

export interface ChatActivity {
  activityType?: string
  phase?: string
  status?: 'running' | 'complete' | 'error' | 'cancelled'
  title?: string
  content?: string
  query?: string
  hits?: Array<{ score?: number, kind?: string, nodeId?: string, title: string, path?: string, excerpt?: string }>
  graph?: {
    query?: string
    nodes: Array<{ id: string, title: string, type?: string, role?: string, score?: number, path?: string }>
    edges: Array<{ source: string, target: string, weight?: number, signal?: string }>
  }
}

export interface ChatAttachment {
  fileId?: string
  fileName: string
  contentType?: string
  size?: number
  kind?: 'IMAGE' | 'DOCUMENT' | 'FILE' | string
  url?: string
  extractedText?: string
  dataUrl?: string
}

export interface ChatMessage {
  id: string
  sessionId: string
  userId?: string
  role: 'USER' | 'ASSISTANT' | 'SYSTEM' | 'TOOL'
  content: string
  reasoning?: string
  citations?: ChatCitation[]
  tools?: ChatToolCall[]
  activities?: ChatActivity[]
  attachments?: ChatAttachment[]
  usage?: ChatUsage
  status?: 'PENDING' | 'STREAMING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
  errorMessage?: string
  createTime?: string
}

export interface ChatQuota {
  userId: string
  usageDate: string
  usedTokens: number
  reservedTokens?: number
  uncertainTokens?: number
  limitTokens: number
  remainingTokens: number
}

export interface ChatSendPayload {
  sessionId?: string
  scopeType?: ChatScopeType
  agentCode?: string
  spaceSlug?: string
  providerCode?: string
  modelCode?: string
  contextRefs?: ChatContextRef[]
  question: string
  attachments?: ChatAttachment[]
}

export const listChatSessions = () => systemClient.get<unknown, ApiResponse<ChatSession[]>>('api/platform/chat/sessions', { skipGlobalToast: true })
export const createChatSession = (data: Partial<ChatSession>) => systemClient.post<unknown, ApiResponse<ChatSession>>('api/platform/chat/sessions', data)
export const updateChatSession = (id: string, data: Partial<ChatSession>) => systemClient.patch<unknown, ApiResponse<ChatSession>>(`api/platform/chat/sessions/${id}`, data)
export const deleteChatSession = (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/chat/sessions/${id}`)
export const listChatMessages = (id: string) => systemClient.get<unknown, ApiResponse<ChatMessage[]>>(`api/platform/chat/sessions/${id}/messages`)
export async function fetchMyChatQuota() {
  const response = await systemClient.get<unknown, ApiResponse<unknown>>('api/platform/chat/quota/me', { skipGlobalToast: true })
  return { ...response, data: normalizeAiQuota(response.data) }
}
export async function fetchChatQuotaConfig() {
  const response = await systemClient.get<unknown, ApiResponse<unknown>>('api/platform/chat/quota/config')
  return { ...response, data: normalizeAiQuotaConfig(response.data) }
}
export async function updateChatQuotaConfig(dailyTokenLimit: number, systemDailyTokenLimit?: number) {
  const data = { dailyTokenLimit: parseAiTokenCount(dailyTokenLimit, 'dailyTokenLimit'), ...(systemDailyTokenLimit === undefined ? {} : { systemDailyTokenLimit: parseAiTokenCount(systemDailyTokenLimit, 'systemDailyTokenLimit') }) }
  const response = await systemClient.put<unknown, ApiResponse<unknown>>('api/platform/chat/quota/config', data)
  return { ...response, data: normalizeAiQuotaConfig(response.data) }
}

export type ChatQuotaReservation = ReturnType<typeof normalizeAiQuotaReservations>[number]
export async function listUnresolvedChatQuota(params: { userId?: string, usageDate?: string } = {}) {
  const response = await systemClient.get<unknown, ApiResponse<unknown>>('api/platform/chat/quota/unresolved', { params })
  return { ...response, data: normalizeAiQuotaReservations(response.data) }
}
export function reconcileChatQuota(data: Pick<ChatQuotaReservation, 'reservationId' | 'userId' | 'usageDate'> & { promptTokens: number, completionTokens: number, totalTokens: number }) {
  const promptTokens = parseAiTokenCount(data.promptTokens, 'promptTokens')
  const completionTokens = parseAiTokenCount(data.completionTokens, 'completionTokens')
  const totalTokens = parseAiTokenCount(data.totalTokens, 'totalTokens')
  if (!Number.isSafeInteger(promptTokens + completionTokens) || totalTokens !== promptTokens + completionTokens) {
    throw new Error('核对总用量必须等于输入与输出 token 用量之和')
  }
  return systemClient.post<unknown, ApiResponse<void>>('api/platform/chat/quota/reconcile', { ...data, promptTokens, completionTokens, totalTokens })
}

export const chatStreamEndpoint = (sessionId: string) => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/chat/sessions/${sessionId}/stream`

export const chatStreamOnceEndpoint = () => `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/api/platform/chat/stream-once`

export async function uploadChatAttachment(file: File): Promise<ChatAttachment> {
  const form = new FormData()
  form.append('file', file)
  const token = localStorage.getItem('token')
  const baseUrl = `${(import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')}/`
  const response = await fetch(`${baseUrl}api/platform/chat/attachments`, {
    method: 'POST',
    headers: token ? { Authorization: token } : {},
    body: form,
  })
  if (!response.ok) {
    throw new Error('附件上传失败')
  }
  const result = await response.json() as { code: number, message?: string, data?: ChatAttachment }
  if (result.code !== 200) {
    throw new Error(result.message || '附件上传失败')
  }
  return result.data as ChatAttachment
}
