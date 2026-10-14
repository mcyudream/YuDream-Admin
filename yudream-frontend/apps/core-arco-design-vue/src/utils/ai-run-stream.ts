/** SSE framing shared by durable AI runs. Buffers incomplete UTF-8/event frames without inventing deltas. */
export interface AiSseFrame {
  event: string
  id?: string
  data: string
}

export interface AiRunEnvelope {
  event: string
  action?: string
  module?: string
  traceId?: string
  timestamp?: number | string
  cursor: string
  runId: string
  payload: Record<string, unknown>
}

export function parseAiSseFrames(buffer: string): { frames: AiSseFrame[], rest: string } {
  const blocks = buffer.split(/\r?\n\r?\n/)
  const rest = blocks.pop() ?? ''
  const frames: AiSseFrame[] = []
  for (const block of blocks) {
    let event = 'message'
    let id: string | undefined
    const data: string[] = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith(':')) {
        continue
      }
      const separator = line.indexOf(':')
      const field = separator === -1 ? line : line.slice(0, separator)
      const value = separator === -1 ? '' : line.slice(separator + 1).replace(/^ /, '')
      if (field === 'event') {
        event = value
      }
      if (field === 'id' && !value.includes('\0')) {
        id = value
      }
      if (field === 'data') {
        data.push(value)
      }
    }
    if (data.length) {
      frames.push({ event, id, data: data.join('\n') })
    }
  }
  return { frames, rest }
}

export function parseAiRunEnvelope(frame: AiSseFrame, expectedRunId: string): AiRunEnvelope | undefined {
  let raw: unknown
  try {
    raw = JSON.parse(frame.data)
  }
  catch {
    return undefined
  }
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return undefined
  }
  const value = raw as Record<string, unknown>
  if (typeof value.cursor === 'number' && !Number.isSafeInteger(value.cursor)) {
    return undefined
  }
  const cursor = String(value.cursor ?? frame.id ?? '')
  const runId = typeof value.runId === 'string' ? value.runId : expectedRunId
  if (runId !== expectedRunId || !/^\d+$/.test(cursor)) {
    return undefined
  }
  if (!value.payload || typeof value.payload !== 'object' || Array.isArray(value.payload)) {
    return undefined
  }
  return {
    event: typeof value.event === 'string' ? value.event : frame.event,
    action: typeof value.action === 'string' ? value.action : undefined,
    module: typeof value.module === 'string' ? value.module : undefined,
    traceId: typeof value.traceId === 'string' ? value.traceId : undefined,
    timestamp: typeof value.timestamp === 'number' || typeof value.timestamp === 'string' ? value.timestamp : undefined,
    cursor,
    runId,
    payload: value.payload as Record<string, unknown>,
  }
}

/** Event cursors stay strings, including sequences above Number.MAX_SAFE_INTEGER. */
export function isNewAiCursor(cursor: string, previous: string): boolean {
  return /^\d+$/.test(cursor) && /^\d+$/.test(previous) && BigInt(cursor) > BigInt(previous)
}

export function isActiveAiRun(status: string): boolean {
  return ['QUEUED', 'RUNNING', 'WAITING', 'WAITING_FOR_INPUT'].includes(status.toUpperCase())
}

export class AiRunStreamError extends Error {
  constructor(message: string, public status?: number) {
    super(message)
  }
}

export function canReconnectAiRun(error: unknown): boolean {
  return !(error instanceof AiRunStreamError && error.status !== undefined && error.status >= 400 && error.status < 500)
}
