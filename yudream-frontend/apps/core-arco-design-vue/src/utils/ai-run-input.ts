import type { YdChatMessage } from '@yudream/components'

/** 持久聊天按消息 ID 对齐；业务任务才使用合成输入，避免 SSE 重放复制已加载的消息。 */
export function upsertAiRunInput(messages: YdChatMessage[], runId: string, content: string, persistedChat: boolean, messageId?: string) {
  if (persistedChat && !messageId) {
    return
  }
  const id = messageId || `input:${runId}`
  if (messages.some(message => message.id === id)) {
    return
  }
  const optimistic = messages.findLast(message => message.role === 'user' && message.context?.pendingRunInput && message.content === content)
  if (optimistic) {
    optimistic.id = id
    optimistic.context = undefined
    return
  }
  const answerIndex = messages.findIndex(message => message.id === `run:${runId}`)
  messages.splice(answerIndex < 0 ? messages.length : answerIndex, 0, { id, role: 'user', content })
}
