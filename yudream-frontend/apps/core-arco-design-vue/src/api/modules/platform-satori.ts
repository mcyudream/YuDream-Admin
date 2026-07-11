import type { ApiResponse, PageResult } from './system-client'
import type { ExcelBlobResponse } from '@/utils/excel'
import systemClient from './system-client'

export interface SatoriConnection {
  id: string
  name: string
  baseUrl: string
  platform: string
  userId: string
  enabled: boolean
  credentialConfigured: boolean
  createTime?: string
  updateTime?: string
}

export interface SatoriConnectionPayload {
  name: string
  baseUrl: string
  platform: string
  userId: string
  token?: string
}

export interface SatoriConnectionTest {
  success: boolean
  platform: string
  userId: string
  status?: string
  adapter?: string
  features: string[]
  testedAt?: string
}

export interface SatoriOperationLog {
  id: string
  level: 'INFO' | 'WARN' | 'ERROR'
  category: string
  action: string
  detail?: string
  occurredAt?: string
}

export interface SatoriConversation {
  channelId?: string
  guildId?: string
  targetUserId?: string
  name?: string
  type?: 'GROUP' | 'FRIEND' | string
  avatar?: string
}

export interface SatoriConversationPage {
  records: SatoriConversation[]
  next?: string
}

export interface SatoriChatMessage {
  id: string
  channelId?: string
  content?: string
  userId?: string
  userName?: string
  userAvatar?: string
  createdAt?: number | string
}

export interface SatoriChatMessagePage {
  records: SatoriChatMessage[]
  prev?: string
  next?: string
}

export interface SatoriChatMember {
  userId: string
  name?: string
  avatar?: string
}

export default {
  pageConnections: (params: { page: number, size: number, keyword?: string }) => systemClient.get<unknown, ApiResponse<PageResult<SatoriConnection>>>('api/platform/satori/connections', { params }),
  createConnection: (data: SatoriConnectionPayload) => systemClient.post<unknown, ApiResponse<SatoriConnection>>('api/platform/satori/connections', data),
  updateConnection: (id: string, data: SatoriConnectionPayload) => systemClient.put<unknown, ApiResponse<SatoriConnection>>(`api/platform/satori/connections/${id}`, data),
  enableConnection: (id: string) => systemClient.post<unknown, ApiResponse<SatoriConnection>>(`api/platform/satori/connections/${id}/enable`),
  disableConnection: (id: string) => systemClient.post<unknown, ApiResponse<SatoriConnection>>(`api/platform/satori/connections/${id}/disable`),
  testConnection: (id: string) => systemClient.post<unknown, ApiResponse<SatoriConnectionTest>>(`api/platform/satori/connections/${id}/test`),
  pageConnectionLogs: (id: string, params: { page: number, size: number }) => systemClient.get<unknown, ApiResponse<PageResult<SatoriOperationLog>>>(`api/platform/satori/connections/${id}/logs`, { params }),
  streamConnectionLogs: (id: string) => `api/platform/satori/connections/${id}/logs/stream`,
  streamConversationMessages: (id: string) => `api/platform/satori/connections/${id}/chat/stream`,
  conversations: (id: string, params?: { next?: string }) => systemClient.get<unknown, ApiResponse<SatoriConversationPage>>(`api/platform/satori/connections/${id}/conversations`, { params }),
  openDirectConversation: (id: string, userId: string) => systemClient.post<unknown, ApiResponse<SatoriConversation>>(`api/platform/satori/connections/${id}/conversations/direct`, undefined, { params: { userId } }),
  conversationMessages: (id: string, channelId: string, params?: { next?: string, limit?: number }) => systemClient.get<unknown, ApiResponse<SatoriChatMessagePage>>(`api/platform/satori/connections/${id}/conversations/${encodeURIComponent(channelId)}/messages`, { params }),
  conversationMembers: (id: string, params: { guildId?: string, next?: string }) => systemClient.get<unknown, ApiResponse<SatoriChatMember[]>>(`api/platform/satori/connections/${id}/conversations/members`, { params }),
  sendMessage: (id: string, data: { platform: string, userId: string, channelId: string, type: 'TEXT' | 'SATORI' | 'COMPOSITE', content: string, attachments?: Array<{ url: string, title?: string, contentType?: string }> }) => systemClient.post(`api/platform/satori/connections/${id}/messages`, data),
  sendMedia: (id: string, data: FormData) => systemClient.post(`api/platform/satori/connections/${id}/messages/media`, data),
  sendMentionAll: (id: string, data: { platform: string, userId: string, channelId: string, content: string }) => systemClient.post(`api/platform/satori/connections/${id}/messages/mention-all`, data),
  render: (data: { sourceType: 'HTML' | 'MARKDOWN' | 'URL', content: string, width?: number, transparent?: boolean }) => systemClient.post<unknown, ExcelBlobResponse>('api/platform/render', data, { responseType: 'blob' }),
}
