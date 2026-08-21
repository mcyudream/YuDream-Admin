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

export interface QqSandboxConnectionOption {
  connectionId: string
  name: string
}

export interface QqSandboxSenderOption {
  qq: string
  nickname: string
  userId: string
  roles: string[]
}

export interface QqSandboxGroupOption {
  groupId: string
  groupName: string
}

export interface QqSandboxGroups {
  selfId?: string
  groups: QqSandboxGroupOption[]
}

export interface QqSandboxRoleOption {
  code: string
  name: string
}

export interface QqSandboxPresets {
  presets: QqSandboxPreset[]
  connections: QqSandboxConnectionOption[]
  senders: QqSandboxSenderOption[]
  roles: QqSandboxRoleOption[]
}

export interface QqSandboxCreateSessionPayload {
  presetCode?: string
  conversationType: QqSandboxConversationType
  /** 留空表示不限定插件，消息广播给全部已启用插件，与真实 QQ 群一致 */
  pluginCode?: string
  policyConnectionId?: string
  botId?: string
  userId: string
  groupId?: string
  nickname?: string
  randomMode: QqSandboxRandomMode
  /** 开启后插件侧判定为未绑定 QQ，用于验证未绑定分支 */
  forceUnbound?: boolean
  /** 角色模拟：null/缺省走真实角色，空数组表示无角色，否则为角色 code 列表 */
  simulateRoles?: string[] | null
}

export interface QqSandboxSession {
  sessionId: string
  status: QqSandboxSessionStatus
  conversationType: QqSandboxConversationType
  pluginCode?: string
  policyConnectionId?: string
  botId?: string
  userId: string
  groupId?: string
  nickname?: string
  randomMode: QqSandboxRandomMode
  createdAt?: string
  expiresAt?: string
}

export type QqSandboxEventType = 'message' | 'group_request' | 'button'

export interface QqSandboxSendMessagePayload {
  content: string
  senderId?: string
  nickname?: string
  mentionSelf: boolean
  mentions: string[]
  replyMessageId?: string
  clientMessageId?: string
  /** 事件类型：message 普通消息 / group_request 入群请求 / button 按钮回调 */
  type?: QqSandboxEventType
  /** type 为 button 时必填，路由到插件 onButton 交互 */
  buttonId?: string
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

export interface QqSandboxCaseSetup {
  /** 留空表示不限定插件 */
  pluginCode?: string
  policyConnectionId: string
  selfId?: string
  userId: string
  nickname?: string
  channelId: string
  scene: 'group' | 'private' | string
  randomMode: QqSandboxRandomMode
  forceUnbound: boolean
  /** null/缺省走真实角色，空数组表示无角色 */
  simulateRoles?: string[] | null
}

export interface QqSandboxCaseStep {
  senderId?: string
  nickname?: string
  content: string
  mentionSelf: boolean
  mentions: string[]
  replyMessageId?: string
  type?: QqSandboxEventType
  buttonId?: string
}

export interface QqSandboxCase {
  id: string
  name: string
  description?: string
  createdAt?: string
  updatedAt?: string
  setup: QqSandboxCaseSetup
  steps: QqSandboxCaseStep[]
}

export interface QqSandboxCaseSavePayload {
  id?: string
  name: string
  description?: string
  setup: QqSandboxCaseSetup
  steps: QqSandboxCaseStep[]
}
