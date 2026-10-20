import type { YdChatActivity, YdChatToolEvent } from '@yudream/components'
import type { ChatSendPayload } from './platform-chat'
import type { ApiResponse } from './system-client'
import type { AiRunEnvelope } from '@/utils/ai-run-stream'
import { AiRunStreamError, parseAiRunEnvelope, parseAiSseFrames } from '@/utils/ai-run-stream'
import systemClient from './system-client'

export interface AiFrontendRoute {
  name: string
  title: string
  path: string
}

export interface AiFrontendAction {
  code: string
  label: string
  routeName: string
}

export interface AiFrontendContext {
  route?: { name: string, title: string, path?: string }
  routes?: AiFrontendRoute[]
  actions?: AiFrontendAction[]
  /** 当前页面可用引导编码目录（按权限过滤），仅在引导工具开启时上报。 */
  tours?: string[]
  navigationToolsEnabled?: boolean
  highlightToolsEnabled?: boolean
  tourToolsEnabled?: boolean
}

export interface AiFrontendToolRequest {
  toolCallId: string
  name: 'ui.navigate' | 'ui.highlight' | 'ui.tour' | 'cms.studio.read' | 'cms.studio.apply' | 'cms.studio.validate'
  arguments: { routeName?: string, actionCode?: string, tourCode?: string, [key: string]: unknown }
  /** 仅由宿主运行观察器注入，模型参数不能指定。 */
  runId?: string
  expiresAt?: number | string
}

export interface AiRunSnapshot {
  id: string
  sessionId: string
  module: string
  status: string
  cursor: string
  content?: string
  structuredContent?: string
  reasoning?: string
  tools?: YdChatToolEvent[]
  activities?: YdChatActivity[]
  result?: Record<string, unknown>
  error?: string
  createdAt?: string
  updatedAt?: string
  pendingFrontendTools?: Array<{ toolCallId: string, name: string, arguments: Record<string, unknown>, expiresAt?: number | string, responseRecorded?: boolean }>
  resumeSupported?: boolean
  inputText?: string
  pendingInteractions?: Array<{ requestId: string, kind: 'confirmation' | 'external_execution', toolName: string, arguments: Record<string, unknown> }>
  generations?: Array<{ callId: string, content: string, reasoning?: string, structured?: boolean, providerCode?: string, modelCode?: string, activities?: YdChatActivity[], tools?: YdChatToolEvent[] }>
}

export interface AiAssistantRunRequest extends ChatSendPayload {
  frontendContext?: AiFrontendContext
  contextEnabled: boolean
  frontendToolsEnabled: boolean
}

const runPath = (id: string, scope: 'personal' | 'system' = 'personal') => `api/platform/ai/${scope === 'system' ? 'system-runs' : 'runs'}/${encodeURIComponent(id)}`

export const startAiAssistantRun = (sessionId: string, data: AiAssistantRunRequest) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/chat/sessions/${encodeURIComponent(sessionId)}/runs`, data)
export const listAiRuns = (sessionId?: string) => systemClient.get<unknown, ApiResponse<AiRunSnapshot[]>>('api/platform/ai/runs', { params: sessionId ? { sessionId } : {}, skipGlobalToast: true })
export const getAiRun = (id: string) => systemClient.get<unknown, ApiResponse<AiRunSnapshot>>(runPath(id))
export const cancelAiRun = (id: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`${runPath(id)}/cancel`)
export const resumeAiRun = (id: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`${runPath(id)}/resume`)
export const respondAiInteraction = (id: string, requestId: string, response: { approved?: boolean, output?: Record<string, unknown> }) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`${runPath(id)}/interactions/${encodeURIComponent(requestId)}`, response)
export const replyAiFrontendTool = (runId: string, toolCallId: string, reply: { ok: boolean, result?: Record<string, unknown>, error?: string }) => systemClient.post<unknown, ApiResponse<void>>(`${runPath(runId)}/frontend-tools/${encodeURIComponent(toolCallId)}`, reply)
export const listAiSystemRuns = (activeOnly = true) => systemClient.get<unknown, ApiResponse<AiRunSnapshot[]>>('api/platform/ai/system-runs', { params: { activeOnly } })
export const getAiSystemRun = (id: string) => systemClient.get<unknown, ApiResponse<AiRunSnapshot>>(runPath(id, 'system'))
export const cancelAiSystemRun = (id: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`${runPath(id, 'system')}/cancel`)
export const respondAiSystemInteraction = (id: string, requestId: string, approved: boolean) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`${runPath(id, 'system')}/interactions/${encodeURIComponent(requestId)}`, { approved })

/** A dropped subscription never cancels the server run. Resume with its last applied cursor. */
export async function subscribeAiRun(id: string, after: string, signal: AbortSignal, onEvent: (event: AiRunEnvelope) => void, scope: 'personal' | 'system' = 'personal'): Promise<void> {
  const base = (import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')
  const token = localStorage.getItem('token')
  const response = await fetch(`${base}/${runPath(id, scope)}/events?after=${encodeURIComponent(after)}`, {
    headers: { 'Accept': 'text/event-stream', 'Accept-Language': 'zh-CN', ...(token ? { Authorization: token } : {}) },
    signal,
  })
  if (!response.ok || !response.body || !response.headers.get('content-type')?.includes('text/event-stream')) {
    let message = `AI 进度连接失败（${response.status}）`
    let status = response.status
    try {
      const result = await response.json() as { code?: number, message?: string }
      message = result.message || message
      if (typeof result.code === 'number' && result.code >= 400) {
        status = result.code
      }
      else if (response.ok) {
        status = 400
        message = result.message || 'AI 进度接口没有返回事件流'
      }
    }
    catch {
      /* non-JSON network response */
    }
    throw new AiRunStreamError(message, status)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  const dispatch = (text: string) => {
    const parsed = parseAiSseFrames(text)
    for (const frame of parsed.frames) {
      const event = parseAiRunEnvelope(frame, id)
      if (event) {
        onEvent(event)
      }
    }
    return parsed.rest
  }
  try {
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer = dispatch(buffer + decoder.decode(value, { stream: true }))
      if (buffer.length > 4 * 1024 * 1024) {
        throw new AiRunStreamError('AI 进度事件超过大小限制')
      }
    }
    buffer += decoder.decode()
    if (buffer.trim()) {
      dispatch(`${buffer}\n\n`)
    }
  }
  finally {
    await reader.cancel().catch(() => {
    })
    reader.releaseLock()
  }
}
