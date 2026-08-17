import type { YdChatMessage } from '@yudream/components'

/** 会话摘要（列表展示用，不含消息体） */
export interface YdAgentChatSessionMeta {
  id: string
  title: string
  /** 最后一条助手回复摘要 */
  preview?: string
  pinned?: boolean
  /** 会话携带的业务上下文（如 Agent 编码、模型、开关状态） */
  meta?: Record<string, unknown>
  messageCount?: number
  updatedAt?: number
}

/** 完整会话（含消息体） */
export interface YdAgentChatSession extends YdAgentChatSessionMeta {
  messages: YdChatMessage[]
}

/**
 * 会话持久化适配器：由使用方注入（IndexedDB / 后端 API / localStorage 均可），
 * 面板只关心「列表 / 载入 / 保存 / 删除」四个动作。
 */
export interface YdAgentChatSessionStore {
  list: () => Promise<YdAgentChatSessionMeta[]>
  load: (id: string) => Promise<YdAgentChatSession | null>
  save: (session: YdAgentChatSession) => Promise<void>
  remove: (id: string) => Promise<void>
}
