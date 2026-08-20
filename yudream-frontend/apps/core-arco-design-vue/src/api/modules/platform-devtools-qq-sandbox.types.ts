export type QqSandboxConversationType = 'PRIVATE' | 'GROUP'

export type QqSandboxRandomMode = 'REAL' | 'FORCE_HIT' | 'FORCE_MISS'

export type QqSandboxSessionStatus = 'CREATED' | 'CONNECTED' | 'CLOSED' | 'ERROR' | string

export interface QqSandboxPreset {
  code: string
  name: string
  description?: string
  conversationType?: QqSandboxConversationType
  pluginCode?: string
  command?: string
  policyConnectionId?: string
  botId?: string
  userId?: string
  groupId?: string
  nickname?: string
  avatar?: string
  content?: string
  randomMode?: QqSandboxRandomMode
}

export interface QqSandboxCreateSessionPayload {
  presetCode?: string
  conversationType: QqSandboxConversationType
  pluginCode: string
  policyConnectionId?: string
  botId?: string
  userId: string
  groupId?: string
  nickname?: string
  randomMode: QqSandboxRandomMode
}

export interface QqSandboxSession {
  sessionId: string
  status: QqSandboxSessionStatus
  conversationType: QqSandboxConversationType
  pluginCode: string
  policyConnectionId?: string
  botId?: string
  userId: string
  groupId?: string
  nickname?: string
  randomMode: QqSandboxRandomMode
  createdAt?: string
  expiresAt?: string
}

export interface QqSandboxSendMessagePayload {
  content: string
  senderId?: string
  nickname?: string
  mentionSelf: boolean
  mentions: string[]
  replyMessageId?: string
  clientMessageId?: string
}

export interface QqSandboxMessage {
  messageId: string
  sessionId?: string
  direction?: 'INBOUND' | 'OUTBOUND' | 'SYSTEM' | string
  senderId?: string
  senderName?: string
  messageType?: string
  content: string
  occurredAt?: string
  metadata?: Record<string, unknown>
}

export interface QqSandboxEvent<T = unknown> {
  event: string
  action?: string
  module?: string
  traceId?: string
  timestamp?: string
  payload: T
}

export interface QqSandboxLaunchPayload {
  pluginCode: string
  command?: string
  content?: string
}
