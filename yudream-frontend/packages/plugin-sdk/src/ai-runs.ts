/** 用户所属的持久 AI 运行。所有标识与事件游标始终为字符串。 */
export interface YuDreamAiRunSnapshot {
  id: string
  sessionId: string
  module: string
  status: string
  cursor: string
  inputText?: string
  content?: string
  reasoning?: string
  structuredContent?: string
  result?: unknown
  error?: string
  createdAt?: string
  updatedAt?: string
  resumeSupported?: boolean
  tools?: unknown[]
  activities?: unknown[]
  generations?: Array<{ callId: string, content: string, reasoning?: string, structured?: boolean, providerCode?: string, modelCode?: string, activities?: unknown[], tools?: unknown[] }>
  pendingInteractions?: Array<{ requestId: string, kind: 'confirmation' | 'external_execution', toolName: string, arguments: Record<string, unknown> }>
  pendingFrontendTools?: Array<{ toolCallId: string, name: string, arguments: Record<string, unknown>, responseRecorded?: boolean, expiresAt?: number | string }>
}

export interface YuDreamAiRunEvent {
  event: string
  action?: string
  module?: string
  traceId?: string
  timestamp?: string | number
  cursor: string
  runId: string
  payload: Record<string, unknown>
}

export interface YuDreamPluginAiRunsClient {
  list: (sessionId?: string) => Promise<YuDreamAiRunSnapshot[]>
  get: (runId: string) => Promise<YuDreamAiRunSnapshot>
  /** AbortSignal 仅关闭观察连接；终止后台执行必须显式调用 cancel。 */
  subscribe: (runId: string, options: { after: string, signal: AbortSignal, onEvent: (event: YuDreamAiRunEvent) => void }) => Promise<void>
  /** 复用宿主根级会话投影；detach 只解绑本组件，后台与宿主观察继续。 */
  watch: (runId: string, onSnapshot: (snapshot: YuDreamAiRunSnapshot) => void, options?: { signal?: AbortSignal }) => Promise<() => void>
  cancel: (runId: string) => Promise<YuDreamAiRunSnapshot>
  resume: (runId: string) => Promise<YuDreamAiRunSnapshot>
  respond: (runId: string, requestId: string, answer: { approved?: boolean, output?: Record<string, unknown> }) => Promise<YuDreamAiRunSnapshot>
}

export interface YuDreamAiPageAction {
  code: string
  label: string
  permission: string
}
