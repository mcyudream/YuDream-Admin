export type AgentDebugStreamEvent = Record<string, any> & {
  type?: string
}

export function decodeAgentDebugEventBlock(block: string): AgentDebugStreamEvent | null {
  const data = block
    .split('\n')
    .filter(line => line.startsWith('data:'))
    .map(line => line.slice(5).trimStart())
    .join('\n')

  if (!data || data === '[DONE]') {
    return null
  }
  return JSON.parse(data) as AgentDebugStreamEvent
}

export function createAgentDebugStreamParser(onEvent: (event: AgentDebugStreamEvent) => void) {
  let buffer = ''

  function drain(complete = false) {
    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      emit(buffer.slice(0, boundary))
      buffer = buffer.slice(boundary + 2)
      boundary = buffer.indexOf('\n\n')
    }
    if (complete && buffer.trim()) {
      emit(buffer)
      buffer = ''
    }
  }

  function emit(block: string) {
    const event = decodeAgentDebugEventBlock(block)
    if (event) {
      onEvent(event)
    }
  }

  return {
    push(chunk: string) {
      buffer += chunk.replaceAll('\r\n', '\n')
      drain()
    },
    finish() {
      drain(true)
    },
  }
}

export async function consumeAgentDebugStream(
  response: Response,
  onEvent: (event: AgentDebugStreamEvent) => void,
) {
  if (!response.ok) {
    throw new Error(`调试请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) {
    throw new Error('调试响应不支持流式读取')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  const parser = createAgentDebugStreamParser(onEvent)
  while (true) {
    const chunk = await reader.read()
    if (chunk.done) {
      break
    }
    parser.push(decoder.decode(chunk.value, { stream: true }))
  }
  parser.push(decoder.decode())
  parser.finish()
}
