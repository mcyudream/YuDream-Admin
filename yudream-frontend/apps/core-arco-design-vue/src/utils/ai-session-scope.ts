import type { ChatSendPayload, ChatSession } from '@/api/modules/platform-chat'

type SessionScope = Pick<ChatSession, 'scopeType' | 'agentCode' | 'spaceSlug'>
type RequestedScope = Pick<ChatSendPayload, 'scopeType' | 'agentCode' | 'spaceSlug'>

/** 后端按持久会话分派任务；范围变化必须另建会话，不能只覆盖运行请求。 */
export function matchesAiSessionScope(session: SessionScope | undefined, request: RequestedScope): boolean {
  if (!session) {
    return false
  }
  // 全局助手没有显式切换范围时，继续当前普通会话。
  if (!request.scopeType) {
    return true
  }
  if ((session.scopeType || 'GENERAL') !== request.scopeType) {
    return false
  }
  if ((request.scopeType === 'AGENT' || request.scopeType === 'CONSOLE') && (session.agentCode || '') !== (request.agentCode || '')) {
    return false
  }
  return (session.spaceSlug || '') === (request.spaceSlug || '')
}
