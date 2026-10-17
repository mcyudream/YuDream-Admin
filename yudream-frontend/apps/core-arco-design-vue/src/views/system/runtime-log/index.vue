<script setup lang="ts">
import type { YdListToolbarStatusItem } from '@yudream/components'
import type { SystemLogItem, SystemLogLevel, SystemLogModuleGroup, SystemLogStats } from '@/api/modules/system-log'
import apiSystemLog from '@/api/modules/system-log'
import { saveExcelResponse } from '@/utils/excel'

/** 首屏历史条数；更早的历史由「加载更早的日志」按页补齐 */
const PAGE_SIZE = 200
/** 渲染上限：实时追加超过上限后从最旧一行开始丢弃，避免 DOM 无限增长 */
const MAX_ROWS = 1000
/** 缓冲统计轮询间隔：级别计数与缓存深度跟随实时流滚动 */
const STATS_REFRESH_MS = 15_000
/** 断流后的自动重连退避，用尽后由「重新连接」手动恢复 */
const RETRY_DELAYS = [2_000, 5_000, 10_000]
/** 距底部超过该距离视为用户离开尾部，暂停自动滚动 */
const FOLLOW_THRESHOLD = 60
/** 显示偏好（换行/线程/行高）本地记忆，运维习惯跨会话保持 */
const DISPLAY_STORAGE_KEY = 'yudream:runtime-log:display'
const LEVELS: SystemLogLevel[] = ['ERROR', 'WARN', 'INFO', 'DEBUG', 'TRACE']

type ConnectionState = 'connecting' | 'live' | 'closed'

const { auth } = useAppAuth()
const toast = useYdToast()
const modal = useYdModal()

const loading = ref(false)
const loadingMore = ref(false)
const loadError = ref('')
const clearing = ref(false)
const downloading = ref(false)
const savingSettings = ref(false)
const settingsOpen = ref(false)
const displayOpen = ref(false)

const rows = ref<SystemLogItem[]>([])
const historyCount = ref(0)
const historyPage = ref(1)
const expanded = ref<Set<number>>(new Set())
const logPanel = ref<HTMLElement | null>(null)

/** 筛选条件：keyword 是输入框草稿，activeKeyword 是已生效条件（查询、高亮与流订阅都以它为准） */
const filters = reactive<{ level: string, keyword: string }>({ level: '', keyword: '' })
const activeKeyword = ref('')
const appliedLevel = ref('')
const selectedModules = ref<string[]>([])
const moduleOptions = ref<{ label: string, value: string }[]>([])

const stats = reactive<{ size: number, droppedCount: number, maxEntries: number, levelCounts: Record<string, number> }>({
  size: 0,
  droppedCount: 0,
  maxEntries: 0,
  levelCounts: {},
})

const connection = ref<ConnectionState>('connecting')
const authExpired = ref(false)
const streamError = ref('')
const followTail = ref(true)
const pendingCount = ref(0)

const display = reactive({ wrap: true, showThread: true, compact: false })

const docker = reactive<{ enabled: boolean, transport: string, containers: string[], socket: string, tail: number, loaded: boolean }>({
  enabled: false,
  transport: 'auto',
  containers: [],
  socket: '/var/run/docker.sock',
  tail: 200,
  loaded: false,
})

const transportOptions = [
  { label: '自动（socket 优先，回退 CLI）', value: 'auto' },
  { label: 'CLI（docker logs 子进程）', value: 'cli' },
  { label: 'Socket（Docker Engine API）', value: 'socket' },
]

let abortController: AbortController | null = null
let retryTimer: number | null = null
let retryAttempt = 0
let statsTimer: number | null = null
let applyTimer: number | null = null

const modulesQuery = computed(() => selectedModules.value.join(','))
const hasActiveFilter = computed(() => Boolean(filters.level || activeKeyword.value || selectedModules.value.length))

/** 已加载行的级别分布：仅在后端未提供缓存分布时兜底 */
const loadedLevelCounts = computed(() => {
  const counts: Record<string, number> = {}
  for (const entry of rows.value) {
    counts[entry.level] = (counts[entry.level] || 0) + 1
  }
  return counts
})

/**
 * 级别计数：优先用后端缓存分布（与筛选无关的全局计数）。
 * 后端没有该字段时退回已加载行统计——按级别筛选后其余级别必然为 0，此时不给数字，避免把「没加载」显示成「没有」。
 */
function levelCount(level: SystemLogLevel): number | undefined {
  const fromStats = stats.levelCounts[level]
  if (fromStats !== undefined) {
    return fromStats
  }
  if (appliedLevel.value) {
    return undefined
  }
  return loadedLevelCounts.value[level] || 0
}

/** 级别计数即筛选：ERROR / WARN 用语义色，其余中性（计数来自缓存全量，不随当前筛选收窄） */
const levelStatusItems = computed<YdListToolbarStatusItem[]>(() => [
  { value: '', label: '全部', count: stats.size, tone: 'neutral' },
  ...LEVELS.map(level => ({
    value: level,
    label: level,
    count: levelCount(level),
    tone: level === 'ERROR' ? 'bad' as const : level === 'WARN' ? 'muted' as const : 'neutral' as const,
  })),
])

/** 渲染行：日期变化处插入分隔行，避免整列时间重复日期 */
interface LogViewRow { key: string, entry: SystemLogItem, dayLabel: string }
const viewRows = computed<LogViewRow[]>(() => {
  let lastDay = ''
  return rows.value.map((entry) => {
    const day = entry.time?.slice(0, 10) || ''
    const dayLabel = day && day !== lastDay ? day : ''
    lastDay = day
    return { key: `${entry.sequence}`, entry, dayLabel }
  })
})

const hasMoreHistory = computed(() => !loadError.value
  && rows.value.length < MAX_ROWS - PAGE_SIZE
  && historyCount.value < stats.size)

const connectionText = computed(() => {
  if (connection.value === 'live') {
    return '实时连接'
  }
  if (connection.value === 'connecting') {
    return '连接中…'
  }
  return authExpired.value ? '登录已失效' : '连接已断开'
})

const jumpLabel = computed(() => pendingCount.value > 0 ? `${pendingCount.value} 条新日志` : '回到最新')

onMounted(() => {
  loadDisplayPrefs()
  document.addEventListener('visibilitychange', onVisibilityChange)
  statsTimer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void loadStats()
    }
  }, STATS_REFRESH_MS)
  if (auth('system:runtime-log:config')) {
    void loadDockerSettings()
  }
  void (async () => {
    await Promise.all([loadModules(), loadStats()])
    await loadHistory()
    connectStream()
  })()
})

onBeforeUnmount(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  cancelScheduledApply()
  clearRetry()
  abortStream()
  if (statsTimer) {
    window.clearInterval(statsTimer)
    statsTimer = null
  }
})

watch(() => filters.level, (level) => {
  if (level === appliedLevel.value) {
    return
  }
  scheduleApply()
})

// 跟随开关恢复即回到最新：与底部「N 条新日志」提示共用同一收尾动作
watch(followTail, (following) => {
  if (following) {
    pendingCount.value = 0
    scrollToBottom(true)
  }
})

watch(display, () => {
  try {
    window.localStorage.setItem(DISPLAY_STORAGE_KEY, JSON.stringify({ ...display }))
  }
  catch { /* 隐私模式等存储不可用时忽略，偏好仅在本次会话生效 */ }
}, { deep: true })

function loadDisplayPrefs() {
  try {
    const raw = window.localStorage.getItem(DISPLAY_STORAGE_KEY)
    if (!raw) {
      return
    }
    const saved = JSON.parse(raw) as Partial<typeof display>
    if (typeof saved.wrap === 'boolean') {
      display.wrap = saved.wrap
    }
    if (typeof saved.showThread === 'boolean') {
      display.showThread = saved.showThread
    }
    if (typeof saved.compact === 'boolean') {
      display.compact = saved.compact
    }
  }
  catch { /* 损坏的本地偏好直接忽略 */ }
}

async function loadModules() {
  try {
    const [modulesRes, groupsRes] = await Promise.all([
      apiSystemLog.modules(),
      apiSystemLog.moduleGroups().catch(() => ({ data: [] as SystemLogModuleGroup[] })),
    ])
    moduleOptions.value = flattenModuleOptions(groupsRes.data || [], modulesRes.data || [])
  }
  catch { /* 模块列表加载失败不阻塞页面 */ }
}

/** 模块候选按后端分组顺序摊平去重：模块名本身已自解释，候选保留原始名称与日志行一致 */
function flattenModuleOptions(groups: SystemLogModuleGroup[], modules: string[]) {
  const seen = new Set<string>()
  const options: { label: string, value: string }[] = []
  for (const group of groups) {
    for (const module of group.modules) {
      if (seen.has(module)) {
        continue
      }
      seen.add(module)
      options.push({ label: module, value: module })
    }
  }
  for (const module of modules) {
    if (!seen.has(module)) {
      seen.add(module)
      options.push({ label: module, value: module })
    }
  }
  return options
}

async function loadStats() {
  try {
    const res = await apiSystemLog.stats()
    applyStats(res.data)
  }
  catch { /* 统计加载失败不阻塞页面 */ }
}

function applyStats(data: SystemLogStats) {
  stats.size = countOf(data.size)
  stats.droppedCount = countOf(data.droppedCount)
  stats.maxEntries = countOf(data.maxEntries)
  const counts: Record<string, number> = {}
  for (const [level, value] of Object.entries(data.levelCounts || {})) {
    counts[level] = countOf(value)
  }
  stats.levelCounts = counts
}

/** 后端 Long 走全局序列化返回字符串，计数一律转数字后再参与运算 */
function countOf(value: number | string | undefined | null) {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

async function loadHistory() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await apiSystemLog.page({
      level: filters.level || undefined,
      modules: modulesQuery.value || undefined,
      keyword: activeKeyword.value || undefined,
      page: 1,
      size: PAGE_SIZE,
    })
    // 后端返回最新在前，反转为时间正序展示
    rows.value = (res.data.records || []).slice().reverse()
    historyCount.value = rows.value.length
    historyPage.value = 1
    scrollToBottom(true)
  }
  catch (error) {
    loadError.value = error instanceof Error ? error.message : '日志加载失败'
    rows.value = []
    historyCount.value = 0
  }
  finally {
    loading.value = false
  }
}

/** 向前翻页补历史：以滚动区内首行做锚点，插入后把锚点行钉回原位，阅读位置不跳 */
async function loadMoreHistory() {
  if (loadingMore.value || !hasMoreHistory.value) {
    return
  }
  loadingMore.value = true
  const anchorSequence = rows.value[0]?.sequence
  const panel = logPanel.value
  const anchorTop = anchorSequence === undefined || !panel
    ? null
    : panel.querySelector<HTMLElement>(`[data-seq="${anchorSequence}"]`)?.getBoundingClientRect().top ?? null
  try {
    const res = await apiSystemLog.page({
      level: filters.level || undefined,
      modules: modulesQuery.value || undefined,
      keyword: activeKeyword.value || undefined,
      page: historyPage.value + 1,
      size: PAGE_SIZE,
    })
    const older = (res.data.records || []).slice().reverse()
    if (!older.length) {
      // 历史已到底：把已知总数对齐，收起加载入口
      historyCount.value = Math.max(historyCount.value, stats.size)
      return
    }
    historyPage.value += 1
    historyCount.value += older.length
    const merged = [...older, ...rows.value]
    rows.value = merged.length > MAX_ROWS ? merged.slice(merged.length - MAX_ROWS) : merged
    await nextTick()
    if (panel && anchorTop !== null && anchorSequence !== undefined) {
      const moved = panel.querySelector<HTMLElement>(`[data-seq="${anchorSequence}"]`)?.getBoundingClientRect().top
      if (moved !== undefined) {
        panel.scrollTop += moved - anchorTop
      }
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '加载更早日志失败')
  }
  finally {
    loadingMore.value = false
  }
}

function scheduleApply() {
  cancelScheduledApply()
  applyTimer = window.setTimeout(() => {
    applyTimer = null
    void applyFilters()
  }, 260)
}

function cancelScheduledApply() {
  if (applyTimer) {
    window.clearTimeout(applyTimer)
    applyTimer = null
  }
}

/** 筛选变更统一入口：重启历史查询与实时流，条件立即对缓存与后续日志同时生效 */
async function applyFilters() {
  cancelScheduledApply()
  clearRetry()
  abortStream()
  appliedLevel.value = filters.level
  activeKeyword.value = filters.keyword.trim()
  expanded.value = new Set()
  pendingCount.value = 0
  followTail.value = true
  await Promise.all([loadHistory(), loadStats()])
  connectStream()
}

function resetFilters() {
  filters.level = ''
  filters.keyword = ''
  selectedModules.value = []
  void applyFilters()
}

function onModuleChange() {
  scheduleApply()
}

function connectStream() {
  abortStream()
  clearRetry()
  streamError.value = ''
  const token = localStorage.getItem('token')
  const base = import.meta.env.VITE_APP_API_BASEURL
  const params = new URLSearchParams()
  if (appliedLevel.value) {
    params.set('level', appliedLevel.value)
  }
  if (modulesQuery.value) {
    params.set('modules', modulesQuery.value)
  }
  if (activeKeyword.value) {
    params.set('keyword', activeKeyword.value)
  }
  const query = params.toString()
  const url = `${base}api/system/logs/stream${query ? `?${query}` : ''}`

  const controller = new AbortController()
  abortController = controller
  connection.value = 'connecting'
  void fetch(url, { headers: token ? { Authorization: token } : {}, signal: controller.signal }).then(async (response) => {
    if (!response.ok || !response.body) {
      // 401/403 说明凭据失效：不再自动重连，避免刷新令牌被反复打爆
      authExpired.value = response.status === 401 || response.status === 403
      throw new Error(`实时流建立失败（HTTP ${response.status}）`)
    }
    authExpired.value = false
    retryAttempt = 0
    await readStream(response.body)
  }).catch((error) => {
    if (controller.signal.aborted) {
      return
    }
    streamError.value = error instanceof Error ? error.message : '实时流中断'
  }).finally(() => {
    // 仅当仍是当前流时才更新连接状态，避免旧流的 finally 覆盖新流状态
    if (abortController !== controller) {
      return
    }
    connection.value = 'closed'
    scheduleRetry()
  })
}

async function readStream(body: ReadableStream<Uint8Array>) {
  const reader = body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const chunk = await reader.read()
    if (chunk.done) {
      break
    }
    buffer += decoder.decode(chunk.value, { stream: true })
    const blocks = buffer.split(/\n\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      const lines = block.split(/\n/)
      const dataLine = lines.find(line => line.startsWith('data:'))
      if (!dataLine) {
        continue
      }
      const event = lines.find(line => line.startsWith('event:'))?.slice(6).trim() || 'message'
      if (event === 'connected') {
        // 后端在流建立后立即发 connected：以它作为「真正实时」的信号，并顺手校准缓存统计
        connection.value = 'live'
        void loadStats()
        continue
      }
      if (event !== 'log') {
        continue
      }
      try {
        appendEntry(JSON.parse(dataLine.slice(5).trim()) as SystemLogItem)
      }
      catch { /* 忽略畸形事件 */ }
    }
  }
}

function scheduleRetry() {
  if (authExpired.value || retryAttempt >= RETRY_DELAYS.length) {
    return
  }
  const delay = RETRY_DELAYS[retryAttempt]
  retryAttempt += 1
  clearRetry()
  retryTimer = window.setTimeout(() => {
    retryTimer = null
    connectStream()
  }, delay)
}

function clearRetry() {
  if (retryTimer) {
    window.clearTimeout(retryTimer)
    retryTimer = null
  }
  retryAttempt = 0
}

function abortStream() {
  abortController?.abort()
  abortController = null
}

function manualReconnect() {
  authExpired.value = false
  clearRetry()
  connectStream()
}

function onVisibilityChange() {
  // 息屏/切走后回来的流常已死；自动重连退避用尽时在这里补一次机会
  if (document.visibilityState === 'visible' && connection.value === 'closed' && !authExpired.value && !retryTimer) {
    connectStream()
  }
}

function appendEntry(entry: SystemLogItem) {
  rows.value.push(entry)
  const overflow = rows.value.length - MAX_ROWS
  if (overflow > 0) {
    rows.value.splice(0, overflow)
    historyCount.value = Math.max(0, historyCount.value - overflow)
  }
  stats.levelCounts[entry.level] = (stats.levelCounts[entry.level] || 0) + 1
  stats.size = Math.min(stats.size + 1, stats.maxEntries || stats.size + 1)
  if (followTail.value) {
    scrollToBottom()
  }
  else {
    pendingCount.value += 1
  }
}

function scrollToBottom(force = false) {
  if (!force && !followTail.value) {
    return
  }
  nextTick(() => {
    const panel = logPanel.value
    if (panel) {
      panel.scrollTop = panel.scrollHeight
    }
  })
}

function onScroll() {
  const panel = logPanel.value
  if (!panel) {
    return
  }
  const distance = panel.scrollHeight - panel.scrollTop - panel.clientHeight
  if (distance < FOLLOW_THRESHOLD) {
    pendingCount.value = 0
    followTail.value = true
  }
  else if (followTail.value) {
    followTail.value = false
  }
}

function resumeFollow() {
  followTail.value = true
  scrollToBottom(true)
}

function toggleRow(sequence: number) {
  const next = new Set(expanded.value)
  if (next.has(sequence)) {
    next.delete(sequence)
  }
  else {
    next.add(sequence)
  }
  expanded.value = next
}

function shortTime(time?: string) {
  if (!time) {
    return '--:--:--'
  }
  return time.length >= 19 ? time.slice(11) : time
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, char => ({
    '&': '&amp;',
    '<': '&lt;',
    '>': '&gt;',
    '"': '&quot;',
    '\'': '&#39;',
  }[char] as string))
}

/** 关键字命中高亮：先转义再按字面量包裹，避免日志内容里的 HTML 被当作标记执行 */
function highlight(value?: string | null) {
  if (!value) {
    return ''
  }
  const escaped = escapeHtml(value)
  const keyword = activeKeyword.value
  if (!keyword) {
    return escaped
  }
  const pattern = new RegExp(escapeHtml(keyword).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi')
  return escaped.replace(pattern, match => `<mark class="log-hit">${match}</mark>`)
}

/** 整行复制内容：时间、级别、模块、线程、logger、消息与（如有）异常堆栈，便于直接贴到工单 */
function copyText(entry: SystemLogItem) {
  const head = [entry.time, entry.level, `[${entry.module}]`, entry.thread, entry.logger].filter(Boolean).join(' ')
  return entry.throwable ? `${head}\n${entry.message}\n${entry.throwable}` : `${head}\n${entry.message}`
}

async function downloadLogs() {
  downloading.value = true
  try {
    const res = await apiSystemLog.download({
      level: filters.level || undefined,
      modules: modulesQuery.value || undefined,
      keyword: activeKeyword.value || undefined,
    })
    saveExcelResponse(res as unknown as { data: Blob, headers: Record<string, string> }, '系统日志.log')
  }
  finally {
    downloading.value = false
  }
}

function confirmClear() {
  modal.confirm({
    title: '确认清空系统日志',
    content: '确认清空内存中的系统日志缓存吗？该操作仅清空当前缓存的运行日志，日志文件不受影响。',
    onConfirm: clearLogs,
  })
}

async function clearLogs() {
  clearing.value = true
  try {
    const res = await apiSystemLog.clear()
    toast.success(`已清空 ${res.data || 0} 条系统日志`)
    rows.value = []
    historyCount.value = 0
    expanded.value = new Set()
    pendingCount.value = 0
    await loadStats()
  }
  finally {
    clearing.value = false
  }
}

async function loadDockerSettings() {
  try {
    const res = await apiSystemLog.dockerSettings()
    docker.enabled = res.data.enabled
    docker.transport = res.data.transport
    docker.containers = [...(res.data.containers || [])]
    docker.socket = res.data.socket
    docker.tail = Number(res.data.tail) || 0
    docker.loaded = true
  }
  catch { /* 配置加载失败不阻塞页面 */ }
}

function openSettings() {
  settingsOpen.value = true
  if (!docker.loaded) {
    void loadDockerSettings()
  }
}

async function saveDockerSettings() {
  savingSettings.value = true
  try {
    const res = await apiSystemLog.updateDockerSettings({
      enabled: docker.enabled,
      transport: docker.transport,
      containers: docker.containers.map(item => String(item).trim()).filter(Boolean),
      socket: docker.socket,
      tail: docker.tail,
    })
    docker.enabled = res.data.enabled
    docker.transport = res.data.transport
    docker.containers = [...(res.data.containers || [])]
    docker.socket = res.data.socket
    docker.tail = Number(res.data.tail) || 0
    docker.loaded = true
    settingsOpen.value = false
    toast.success('容器日志配置已保存')
    await loadStats()
  }
  finally {
    savingSettings.value = false
  }
}
</script>

<template>
  <!-- 单根页面容器：路由过渡要求页面组件有元素根，抽屉放同一根下 -->
  <div class="runtime-log-page">
    <YdTablePageLayout title="系统日志" :show-search="false">
      <template #page-title>
        <YdButton v-if="auth('system:runtime-log:config')" variant="outline" @click="openSettings">
          <YdIcon name="morph:container" />
          容器日志
        </YdButton>
        <YdButton v-if="auth('system:runtime-log:download')" variant="outline" :loading="downloading" @click="downloadLogs">
          <YdIcon name="morph:download" />
          下载日志
        </YdButton>
        <YdButton v-if="auth('system:runtime-log:delete')" variant="destructive" :loading="clearing" @click="confirmClear">
          <YdIcon name="morph:trash-2" />
          清空缓存
        </YdButton>
      </template>

      <template #table>
        <div class="log-console">
          <YdListToolbar
            v-model:keyword="filters.keyword"
            v-model:status="filters.level"
            placeholder="搜索消息 / logger / 异常"
            search-width="240"
            :status-items="levelStatusItems"
            @search="applyFilters"
            @clear="applyFilters"
          >
            <template #filters>
              <YdMultiSelect
                v-model="selectedModules"
                :options="moduleOptions"
                :max-tag-count="2"
                placeholder="全部模块"
                empty-text="没有匹配的模块"
                aria-label="按模块筛选日志"
                @change="onModuleChange"
              />
            </template>
            <template #actions>
              <YdButton @click="applyFilters">
                <YdIcon name="morph:search" />
                筛选
              </YdButton>
              <YdButton variant="outline" @click="resetFilters">
                重置
              </YdButton>
              <YdPopover v-model:open="displayOpen" align="end">
                <YdButton variant="outline">
                  <YdIcon name="morph:sliders-horizontal" />
                  显示
                </YdButton>
                <template #panel>
                  <div class="display-panel">
                    <div class="display-row">
                      <span>自动换行</span>
                      <YdSwitch v-model="display.wrap" />
                    </div>
                    <div class="display-row">
                      <span>显示线程</span>
                      <YdSwitch v-model="display.showThread" />
                    </div>
                    <div class="display-row">
                      <span>紧凑行高</span>
                      <YdSwitch v-model="display.compact" />
                    </div>
                  </div>
                </template>
              </YdPopover>
            </template>
          </YdListToolbar>

          <div
            class="log-panel"
            :class="{ 'is-compact': display.compact, 'is-nowrap': !display.wrap }"
          >
            <div ref="logPanel" class="log-scroll" @scroll.passive="onScroll">
              <div v-if="loading && !rows.length" class="log-state">
                <YdLoading text="正在读取日志…" />
              </div>

              <div v-else-if="loadError" class="log-state">
                <YdResult status="error" title="日志加载失败" :description="loadError" illustration-size="sm">
                  <YdButton variant="outline" @click="loadHistory">
                    <YdIcon name="morph:refresh-cw" />
                    重试
                  </YdButton>
                </YdResult>
              </div>

              <div v-else-if="!rows.length" class="log-state">
                <YdEmpty
                  :icon="hasActiveFilter ? 'morph:search-x' : 'morph:scroll-text'"
                  :illustration="hasActiveFilter ? 'no-result' : 'empty'"
                  illustration-size="sm"
                  :title="hasActiveFilter ? '没有匹配的日志' : '暂无运行日志'"
                  :description="hasActiveFilter ? '当前级别、模块或关键字条件太窄，放宽后重试。' : '系统产生日志后会实时出现在这里。'"
                >
                  <YdButton v-if="hasActiveFilter" variant="outline" @click="resetFilters">
                    清除筛选
                  </YdButton>
                </YdEmpty>
              </div>

              <template v-else>
                <div v-if="hasMoreHistory" class="log-more">
                  <YdButton variant="ghost" size="sm" :loading="loadingMore" @click="loadMoreHistory">
                    <YdIcon name="morph:chevrons-up" />
                    加载更早的日志
                  </YdButton>
                </div>

                <div
                  v-for="row in viewRows"
                  :key="row.key"
                  v-memo="[row.entry.sequence, row.dayLabel, expanded.has(row.entry.sequence), display.compact, display.wrap, display.showThread, activeKeyword]"
                  :data-seq="row.entry.sequence"
                >
                  <div v-if="row.dayLabel" class="log-day">
                    {{ row.dayLabel }}
                  </div>
                  <div class="log-row" :data-level="row.entry.level">
                    <div class="log-row-head">
                      <YdAction
                        type="button"
                        class="log-row-meta"
                        :aria-expanded="expanded.has(row.entry.sequence)"
                        :aria-label="`展开 ${row.entry.level} ${row.entry.module} 日志详情`"
                        @click="toggleRow(row.entry.sequence)"
                      >
                        <span class="log-time" :title="row.entry.time">{{ shortTime(row.entry.time) }}</span>
                        <span class="log-level">{{ row.entry.level }}</span>
                        <span class="log-module">{{ row.entry.module }}</span>
                        <span v-if="display.showThread" class="log-thread" :title="row.entry.thread">{{ row.entry.thread }}</span>
                        <YdIcon
                          :name="expanded.has(row.entry.sequence) ? 'morph:chevron-down' : 'morph:chevron-right'"
                          class="log-chevron"
                          :class="{ 'is-alert': row.entry.throwable }"
                        />
                      </YdAction>
                      <div class="log-row-tools">
                        <YdCopyButton :text="copyText(row.entry)" class="log-copy" />
                      </div>
                    </div>

                    <div class="log-message" v-html="highlight(row.entry.message)" />

                    <div v-if="expanded.has(row.entry.sequence)" class="log-detail">
                      <div class="log-detail-meta">
                        <span>logger <b>{{ row.entry.logger }}</b></span>
                        <span v-if="row.entry.traceId">traceId <b>{{ row.entry.traceId }}</b></span>
                        <span v-if="row.entry.throwable" class="log-detail-alert">含异常堆栈</span>
                      </div>
                      <pre v-if="row.entry.throwable" class="log-stack" v-html="highlight(row.entry.throwable)" />
                      <div v-if="row.entry.throwable" class="log-detail-actions">
                        <YdCopyButton :text="row.entry.throwable" label="复制异常堆栈" class="log-copy" />
                      </div>
                    </div>
                  </div>
                </div>
              </template>
            </div>

            <div v-if="!followTail" class="log-jump">
              <YdButton size="sm" @click="resumeFollow">
                <YdIcon name="morph:arrow-down-to-line" />
                {{ jumpLabel }}
              </YdButton>
            </div>
          </div>
        </div>
      </template>

      <template #footer>
        <div class="log-status">
          <div class="log-status__group">
            <span class="log-live" :data-state="connection">{{ connectionText }}</span>
            <YdButton v-if="connection === 'closed'" size="sm" variant="outline" @click="manualReconnect">
              <YdIcon name="morph:refresh-cw" />
              重新连接
            </YdButton>
            <span v-if="authExpired" class="log-status__hint">登录状态已失效，重新登录后自动恢复实时流</span>
            <span v-else-if="streamError && connection === 'closed'" class="log-status__hint">{{ streamError }}</span>
          </div>
          <div class="log-status__group">
            <span v-if="docker.loaded" class="log-status__hint">
              容器日志 {{ docker.enabled ? `已采集 ${docker.containers.length} 个容器` : '未启用' }}
            </span>
            <span>显示 <b>{{ rows.length }}</b> 条</span>
            <span>缓存 <b>{{ stats.size }}</b> / {{ stats.maxEntries }}</span>
            <span v-if="stats.droppedCount > 0">已自动清理 <b>{{ stats.droppedCount }}</b> 条</span>
            <span class="log-status__follow">
              跟随最新
              <YdSwitch v-model="followTail" />
            </span>
          </div>
        </div>
      </template>
    </YdTablePageLayout>

    <YdDrawer
      v-model="settingsOpen"
      title="容器日志采集"
      description="开启后与宿主日志合并采集指定的 docker 容器日志，用于定位中间件问题。"
      content-class="w-full max-w-full sm:w-[520px] sm:max-w-[520px]"
      confirm-button-text="保存配置"
      :confirm-button-loading="savingSettings"
      show-cancel-button
      @confirm="saveDockerSettings"
    >
      <div class="docker-form">
        <p class="docker-hint">
          容器日志与宿主日志共用同一份缓存、级别筛选与关键字搜索；启用后新增日志会立即出现在控制台。
        </p>
        <div class="docker-row">
          <span class="docker-label">启用采集</span>
          <YdSwitch v-model="docker.enabled" />
        </div>
        <div class="docker-row">
          <span class="docker-label">传输方式</span>
          <YdSelect v-model="docker.transport" :options="transportOptions" class="docker-control" />
        </div>
        <div class="docker-block">
          <span class="docker-label">容器清单</span>
          <YdInputTag v-model="docker.containers" placeholder="输入容器名回车，如 mongodb" />
          <span class="docker-tip">留空表示采集全部运行中的容器；通常只需填写中间件容器。</span>
        </div>
        <div class="docker-row">
          <span class="docker-label">Socket 路径</span>
          <YdInput v-model="docker.socket" placeholder="/var/run/docker.sock" class="docker-control" />
        </div>
        <div class="docker-row">
          <span class="docker-label">初始条数</span>
          <YdNumberField v-model="docker.tail" :min="1" :max="100000" />
        </div>
      </div>
    </YdDrawer>
  </div>
</template>

<style scoped>
/* 单根页面容器：把整高链路从路由容器传到 YdTablePageLayout */
.runtime-log-page {
  height: 100%;
}

.log-console {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: 100%;
  min-height: 0;
  padding-top: 16px;
}

/* 日志控制台是刻意固定的深色面（终端观感，明暗主题一致）。
 * 这里把组件消费的主题变量在本容器内重绑为深色值：控制台内的 YD 组件
 * （按钮/复制/空态/结果/加载）无需逐处改样式即可在深色面上保持可读。 */
.log-panel {
  --background: 0.17 0.013 253;
  --foreground: 0.91 0.008 250;
  --card: 0.2 0.014 253;
  --card-foreground: 0.91 0.008 250;
  --popover: 0.2 0.014 253;
  --popover-foreground: 0.91 0.008 250;
  --muted: 0.26 0.014 253;
  --muted-foreground: 0.68 0.019 252;
  --accent: 0.28 0.016 253;
  --accent-foreground: 0.95 0.006 250;
  --border: 0.31 0.016 253;
  --input: 0.31 0.016 253;
  --ring: 0.68 0.09 250;
  --primary: 0.68 0.1 248;
  --primary-foreground: 0.17 0.013 253;
  --secondary: 0.26 0.014 253;
  --secondary-foreground: 0.91 0.008 250;
  --destructive: 0.64 0.19 25;

  /* 行内配色：整个控制台共用一套，级别、命中高亮与堆栈都从这里取色 */
  --log-surface: 0.16 0.012 253;
  --log-surface-hover: 0.2 0.014 253;
  --log-line: 0.31 0.016 253;
  --log-text: 0.91 0.008 250;
  --log-text-muted: 0.7 0.018 252;
  --log-text-faint: 0.57 0.016 252;
  --log-accent: 0.79 0.09 235;
  --log-danger: 0.72 0.18 25;
  --log-warning: 0.82 0.14 80;
  --log-hit: 0.86 0.15 88;

  position: relative;
  display: flex;
  flex: 1;
  min-height: 0;
  overflow: hidden;
  color: oklch(var(--log-text));
  background: oklch(var(--log-surface));
  border: 1px solid oklch(var(--log-line));
  border-radius: 8px;
  font-size: 12.5px;
  line-height: 1.6;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
}

.log-scroll {
  flex: 1;
  min-width: 0;
  min-height: 0;
  overflow: auto;
  padding: 6px 0;
  overscroll-behavior: contain;
}

.log-state {
  display: grid;
  height: 100%;
  padding: 24px;
  place-items: center;
}

.log-more {
  display: flex;
  justify-content: center;
  padding: 4px 0 8px;
}

.log-day {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 6px 16px 4px;
  color: oklch(var(--log-text-faint));
  font-size: 11px;
  letter-spacing: 0.04em;
}

.log-day::before,
.log-day::after {
  height: 1px;
  background: oklch(var(--log-line));
  content: '';
  flex: 1;
}

.log-row {
  padding: 3px 14px 3px 11px;
  border-left: 3px solid transparent;
  transition: background-color 0.12s ease;
}

.log-row:hover {
  background: oklch(var(--log-surface-hover));
}

.log-row[data-level='ERROR'] {
  background: color-mix(in srgb, oklch(var(--log-danger)) 9%, transparent);
  border-left-color: oklch(var(--log-danger));
}

.log-row[data-level='WARN'] {
  background: color-mix(in srgb, oklch(var(--log-warning)) 8%, transparent);
  border-left-color: oklch(var(--log-warning));
}

.log-row-head {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.log-row-meta {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
  min-width: 0;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.log-time {
  flex: none;
  color: oklch(var(--log-text-faint));
  font-variant-numeric: tabular-nums;
}

.log-level {
  flex: none;
  width: 52px;
  padding: 0 4px;
  color: oklch(var(--log-text-muted));
  font-size: 10.5px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-align: center;
  background: color-mix(in srgb, oklch(var(--log-text-faint)) 14%, transparent);
  border-radius: 4px;
}

.log-row[data-level='ERROR'] .log-level {
  color: oklch(var(--log-danger));
  background: color-mix(in srgb, oklch(var(--log-danger)) 18%, transparent);
}

.log-row[data-level='WARN'] .log-level {
  color: oklch(var(--log-warning));
  background: color-mix(in srgb, oklch(var(--log-warning)) 18%, transparent);
}

.log-row[data-level='INFO'] .log-level {
  color: oklch(var(--log-accent));
  background: color-mix(in srgb, oklch(var(--log-accent)) 16%, transparent);
}

.log-row[data-level='DEBUG'] .log-message,
.log-row[data-level='TRACE'] .log-message {
  color: oklch(var(--log-text-muted));
}

.log-row[data-level='TRACE'] .log-message {
  color: oklch(var(--log-text-faint));
}

.log-module {
  flex: none;
  color: oklch(var(--log-accent));
}

.log-thread {
  flex: none;
  color: oklch(var(--log-text-muted));
}

.log-chevron {
  flex: none;
  color: oklch(var(--log-text-faint));
}

.log-chevron.is-alert {
  color: oklch(var(--log-warning));
}

.log-row-tools {
  display: flex;
  flex: none;
  opacity: 0;
  transition: opacity 0.14s ease;
}

.log-row:hover .log-row-tools,
.log-row:focus-within .log-row-tools {
  opacity: 1;
}

.log-message {
  padding-left: 8px;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.log-message :deep(.log-hit) {
  color: oklch(var(--log-hit));
  background: color-mix(in srgb, oklch(var(--log-hit)) 26%, transparent);
  border-radius: 3px;
  padding: 0 1px;
}

.log-detail {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 6px 0 6px 8px;
  padding: 8px 12px;
  background: oklch(var(--log-surface-hover));
  border: 1px solid oklch(var(--log-line));
  border-radius: 6px;
}

.log-detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  color: oklch(var(--log-text-faint));
  font-size: 11px;
}

.log-detail-meta b {
  color: oklch(var(--log-text-muted));
  font-weight: 500;
}

.log-detail-alert {
  color: oklch(var(--log-warning));
}

.log-stack {
  margin: 0;
  overflow-wrap: anywhere;
  color: oklch(var(--log-danger));
  font-family: inherit;
  white-space: pre-wrap;
}

.log-detail-actions {
  display: flex;
  justify-content: flex-end;
}

.log-jump {
  position: absolute;
  bottom: 14px;
  left: 50%;
  transform: translateX(-50%);
}

/* 不换行：整块横向滚动，长消息保持一行，便于按列扫读 */
.log-panel.is-nowrap .log-scroll {
  overflow-x: auto;
}

.log-panel.is-nowrap .log-row,
.log-panel.is-nowrap .log-day {
  width: max-content;
  min-width: 100%;
}

.log-panel.is-nowrap .log-message {
  white-space: pre;
}

.log-panel.is-compact {
  font-size: 11.5px;
  line-height: 1.4;
}

.log-panel.is-compact .log-row {
  padding-top: 1px;
  padding-bottom: 1px;
}

.log-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-2);
  font-size: 12px;
}

.log-status__group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  align-items: center;
}

.log-status b {
  color: var(--color-text-1);
  font-variant-numeric: tabular-nums;
}

.log-status__hint {
  color: var(--color-text-3);
}

.log-status__follow {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.log-live {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.log-live::before {
  width: 8px;
  height: 8px;
  background: var(--color-border-3);
  border-radius: 50%;
  content: '';
}

.log-live[data-state='live']::before {
  background: var(--yd-status-success);
  box-shadow: 0 0 6px color-mix(in srgb, var(--yd-status-success) 70%, transparent);
}

.log-live[data-state='connecting']::before {
  background: var(--yd-status-warning);
  animation: log-live-pulse 1.4s ease-in-out infinite;
}

.log-live[data-state='closed']::before {
  background: var(--yd-status-danger);
}

@keyframes log-live-pulse {
  50% {
    opacity: 0.35;
  }
}

.display-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 168px;
  padding: 2px;
}

.display-row {
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-1);
  font-size: 13px;
}

.docker-form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.docker-hint,
.docker-tip {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.docker-row {
  display: flex;
  gap: 12px;
  align-items: center;
}

.docker-label {
  flex: none;
  width: 84px;
  color: var(--color-text-2);
  font-size: 13px;
}

.docker-control {
  width: 260px;
}

.docker-block {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.docker-block .docker-label {
  width: auto;
}

@media (width <= 768px) {
  .log-thread {
    display: none;
  }

  /* 窄屏没有可靠的悬停：复制入口常驻，避免只能长按选择文本 */
  .log-row-tools {
    opacity: 1;
  }

  /* 触屏下每行都有复制入口，压小一号免得占掉正文宽度 */
  .log-row-tools :deep(.log-copy) {
    height: 22px;
    padding: 0 6px;
    font-size: 11px;
  }
}
</style>
