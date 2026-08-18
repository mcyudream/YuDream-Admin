/**
 * 轻量 AI 请求/流工具（对齐 antd-x XRequest / XStream）：
 * 当 useYdChatStream 的协议封装不满足自定义通道时，用这两个原语自行拼装。
 */

export interface YdRequestOptions {
  /** 鉴权 token 提供器（写入 Authorization 头） */
  getToken?: () => string | undefined
  /** 额外请求头 */
  headers?: Record<string, string>
  /** 请求超时（毫秒），默认 120_000 */
  timeoutMs?: number
  /** AbortSignal 外部取消 */
  signal?: AbortSignal
}

/** 统一 JSON 请求：注入 token、超时与错误规整 */
export async function ydRequest<T = unknown>(
  url: string,
  body?: unknown,
  options: YdRequestOptions = {},
): Promise<T> {
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(new DOMException('请求超时', 'TimeoutError')), options.timeoutMs ?? 120_000)
  const onAbort = () => controller.abort(options.signal?.reason)
  options.signal?.addEventListener('abort', onAbort, { once: true })
  try {
    const headers: Record<string, string> = {
      'Accept-Language': 'zh-CN',
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...options.headers,
    }
    const token = options.getToken?.()
    if (token) {
      headers.Authorization = token
    }
    const response = await fetch(url, {
      method: body === undefined ? 'GET' : 'POST',
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
    if (!response.ok) {
      throw new Error(`请求失败（HTTP ${response.status}）`)
    }
    return (await response.json()) as T
  }
  finally {
    clearTimeout(timeout)
    options.signal?.removeEventListener('abort', onAbort)
  }
}

export interface YdStreamEvent {
  /** SSE event 名，缺省为 message */
  event: string
  /** SSE data 原文 */
  data: string
}

/**
 * 逐事件读取 SSE 流（XStream  analog）：
 * 解析 `event:` / `data:` 帧并按序回调，支持多行 data 与外部取消。
 */
export async function readYdStream(
  response: Response,
  onEvent: (event: YdStreamEvent) => void,
): Promise<void> {
  if (!response.ok) {
    throw new Error(`流式请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) {
    throw new Error('响应不支持流式读取')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let eventName = 'message'
  let dataLines: string[] = []

  const flush = () => {
    if (!dataLines.length) {
      eventName = 'message'
      return
    }
    onEvent({ event: eventName, data: dataLines.join('\n') })
    eventName = 'message'
    dataLines = []
  }

  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''
    for (const rawLine of lines) {
      const line = rawLine.endsWith('\r') ? rawLine.slice(0, -1) : rawLine
      if (line === '') {
        flush()
      }
      else if (line.startsWith('event:')) {
        eventName = line.slice(6).trim() || 'message'
      }
      else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
  }
  buffer += decoder.decode()
  if (buffer.trim()) {
    for (const line of buffer.split('\n')) {
      if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).replace(/^ /, ''))
      }
    }
  }
  flush()
}
