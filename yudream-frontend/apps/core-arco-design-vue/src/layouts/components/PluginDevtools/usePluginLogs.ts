import type { PluginLogEntry } from '@/api/modules/platform-devtools'
import apiDevtools from '@/api/modules/platform-devtools'

const ENTRY_LIMIT = 500

function parseLogEventBlock(block: string): PluginLogEntry | null {
  let eventName = 'message'
  const dataLines: string[] = []
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) {
      eventName = line.slice(6).trim()
    }
    else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }
  if (eventName !== 'plugin-log' || !dataLines.length) {
    return null
  }
  try {
    return JSON.parse(dataLines.join('\n'))
  }
  catch {
    return null
  }
}

/** 插件日志流：REST 拉最近清单 + SSE 追加实时日志，按 sequence 去重，暂停时缓存到缓冲区 */
export function usePluginLogs() {
  const pluginCode = ref<string>()
  const level = ref('')
  const keyword = ref('')
  const entries = ref<PluginLogEntry[]>([])
  const paused = ref(false)
  const connected = ref(false)
  const loading = ref(false)
  const streamError = ref('')

  let streamAbort: AbortController | null = null
  let pausedBuffer: PluginLogEntry[] = []
  let keywordTimer: ReturnType<typeof setTimeout> | null = null

  watch([pluginCode, level], () => {
    reload()
  })

  watch(keyword, () => {
    if (keywordTimer) {
      clearTimeout(keywordTimer)
    }
    keywordTimer = setTimeout(() => {
      keywordTimer = null
      reload()
    }, 400)
  })

  async function reload() {
    stopStream()
    entries.value = []
    pausedBuffer = []
    streamError.value = ''
    if (!pluginCode.value) {
      return
    }
    loading.value = true
    try {
      const res = await apiDevtools.pluginLogs(pluginCode.value, {
        level: level.value || undefined,
        keyword: keyword.value.trim() || undefined,
        limit: ENTRY_LIMIT,
      })
      entries.value = (res.data || []).sort((a, b) => a.sequence - b.sequence)
    }
    catch {
      // 拦截器已提示
    }
    finally {
      loading.value = false
    }
    connectStream()
  }

  function connectStream() {
    if (!pluginCode.value) {
      return
    }
    streamAbort = new AbortController()
    void consumeStream(apiDevtools.pluginLogsStreamUrl(pluginCode.value, level.value || undefined), streamAbort.signal)
  }

  function stopStream() {
    streamAbort?.abort()
    streamAbort = null
    connected.value = false
  }

  async function consumeStream(url: string, signal: AbortSignal) {
    const headers: Record<string, string> = { 'Accept': 'text/event-stream', 'Accept-Language': 'zh-CN' }
    const token = localStorage.getItem('token')
    if (token) {
      headers.Authorization = token
    }
    try {
      const response = await fetch(url, { headers, signal })
      if (!response.ok || !response.body) {
        throw new Error(`插件日志流连接失败（HTTP ${response.status}）`)
      }
      connected.value = true
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''
      while (true) {
        const chunk = await reader.read()
        if (chunk.done) {
          break
        }
        buffer += decoder.decode(chunk.value, { stream: true }).replaceAll('\r\n', '\n')
        let boundary = buffer.indexOf('\n\n')
        while (boundary >= 0) {
          const entry = parseLogEventBlock(buffer.slice(0, boundary))
          if (entry) {
            append(entry)
          }
          buffer = buffer.slice(boundary + 2)
          boundary = buffer.indexOf('\n\n')
        }
      }
    }
    catch (error: any) {
      if (!signal.aborted && error?.name !== 'AbortError') {
        streamError.value = error?.message || '插件日志流已断开'
      }
    }
    finally {
      if (!signal.aborted) {
        connected.value = false
      }
    }
  }

  function append(entry: PluginLogEntry) {
    // 关键字过滤只作用于 REST 查询；SSE 订阅无关键字参数，前端按关键字二次过滤保持一致
    const kw = keyword.value.trim().toLowerCase()
    if (kw) {
      const haystack = `${entry.message} ${entry.logger} ${entry.throwable || ''}`.toLowerCase()
      if (!haystack.includes(kw)) {
        return
      }
    }
    if (entries.value.some(item => item.sequence === entry.sequence)) {
      return
    }
    if (paused.value) {
      pausedBuffer.push(entry)
      if (pausedBuffer.length > ENTRY_LIMIT) {
        pausedBuffer.splice(0, pausedBuffer.length - ENTRY_LIMIT)
      }
      return
    }
    entries.value.push(entry)
    if (entries.value.length > ENTRY_LIMIT) {
      entries.value.splice(0, entries.value.length - ENTRY_LIMIT)
    }
  }

  function togglePause() {
    paused.value = !paused.value
    if (!paused.value && pausedBuffer.length) {
      const buffered = pausedBuffer
      pausedBuffer = []
      for (const entry of buffered) {
        append(entry)
      }
    }
  }

  function clear() {
    entries.value = []
    pausedBuffer = []
  }

  onBeforeUnmount(() => {
    if (keywordTimer) {
      clearTimeout(keywordTimer)
      keywordTimer = null
    }
    stopStream()
  })

  return {
    pluginCode,
    level,
    keyword,
    entries,
    paused,
    connected,
    loading,
    streamError,
    reload,
    togglePause,
    clear,
  }
}
