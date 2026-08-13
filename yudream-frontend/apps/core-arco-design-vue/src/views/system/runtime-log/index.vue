<script setup lang="ts">
import apiSystemLog from '@/api/modules/system-log'
import type { SystemLogItem } from '@/api/modules/system-log'
import { saveExcelResponse } from '@/utils/excel'

const MAX_ROWS = 1000

const loading = ref(false)
const clearing = ref(false)
const downloading = ref(false)
const saving = ref(false)
const connected = ref(false)
const followTail = ref(true)
const settingsOpen = ref(false)
const rows = ref<SystemLogItem[]>([])
const moduleOptions = ref<string[]>([])
const stats = reactive({ size: 0, droppedCount: 0, maxEntries: 10000 })
const expanded = ref<Set<number>>(new Set())
const logPanel = ref<HTMLElement | null>(null)
let abortController: AbortController | null = null

const filters = reactive<{ level: string; modules: string[]; keyword: string }>({
  level: '',
  modules: [],
  keyword: '',
})

const dockerSettings = reactive<{ enabled: boolean; transport: string; containers: string; socket: string; tail: number }>({
  enabled: false,
  transport: 'auto',
  containers: '',
  socket: '/var/run/docker.sock',
  tail: 200,
})

const levelOptions = [
  { label: '全部级别', value: '' },
  { label: 'TRACE', value: 'TRACE' },
  { label: 'DEBUG', value: 'DEBUG' },
  { label: 'INFO', value: 'INFO' },
  { label: 'WARN', value: 'WARN' },
  { label: 'ERROR', value: 'ERROR' },
]

const transportOptions = [
  { label: '自动（socket 优先，回退 CLI）', value: 'auto' },
  { label: 'CLI（docker logs 子进程）', value: 'cli' },
  { label: 'Socket（Docker Engine API）', value: 'socket' },
]

const moduleSelectOptions = computed(() => moduleOptions.value.map(name => ({ label: name, value: name })))

const modulesQuery = computed(() => filters.modules.join(','))

const toast = useFaToast()
const modal = useFaModal()

onMounted(async () => {
  await Promise.all([loadModules(), loadStats(), loadDockerSettings()])
  await loadHistory()
  connectStream()
})

onBeforeUnmount(() => {
  abortController?.abort()
  abortController = null
})

async function loadModules() {
  try {
    const res = await apiSystemLog.modules()
    moduleOptions.value = res.data || []
  }
  catch { /* 模块列表加载失败不阻塞页面 */ }
}

async function loadStats() {
  try {
    const res = await apiSystemLog.stats()
    Object.assign(stats, res.data)
  }
  catch { /* 统计加载失败不阻塞页面 */ }
}

async function loadDockerSettings() {
  try {
    const res = await apiSystemLog.dockerSettings()
    dockerSettings.enabled = res.data.enabled
    dockerSettings.transport = res.data.transport
    dockerSettings.containers = (res.data.containers || []).join(',')
    dockerSettings.socket = res.data.socket
    dockerSettings.tail = res.data.tail
  }
  catch { /* 配置加载失败不阻塞页面 */ }
}

async function loadHistory() {
  loading.value = true
  try {
    const res = await apiSystemLog.page({
      level: filters.level || undefined,
      modules: modulesQuery.value || undefined,
      keyword: filters.keyword || undefined,
      page: 1,
      size: 200,
    })
    // 后端返回最新在前，反转为时间正序展示
    rows.value = (res.data.records || []).slice().reverse()
    scrollToBottom()
  }
  finally {
    loading.value = false
  }
}

function connectStream() {
  abortController?.abort()
  const token = localStorage.getItem('token')
  const base = (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL
  const params = new URLSearchParams()
  if (filters.level) params.set('level', filters.level)
  if (modulesQuery.value) params.set('modules', modulesQuery.value)
  if (filters.keyword) params.set('keyword', filters.keyword)
  const query = params.toString()
  const url = `${base}api/system/logs/stream${query ? `?${query}` : ''}`

  const controller = new AbortController()
  abortController = controller
  connected.value = true
  void fetch(url, { headers: token ? { Authorization: token } : {}, signal: controller.signal }).then(async (response) => {
    if (!response.ok || !response.body) {
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const chunk = await reader.read()
      if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true })
      const blocks = buffer.split(/\n\n/)
      buffer = blocks.pop() || ''
      for (const block of blocks) {
        const eventLine = block.split(/\n/).find(line => line.startsWith('event:'))
        const dataLine = block.split(/\n/).find(line => line.startsWith('data:'))
        if (eventLine?.slice(6).trim() !== 'log' || !dataLine) continue
        try {
          const entry = JSON.parse(dataLine.slice(5).trim()) as SystemLogItem
          appendEntry(entry)
        }
        catch { /* 忽略畸形事件 */ }
      }
    }
  }).catch(() => { /* 网络中断，等待手动重连 */ }).finally(() => {
    // 仅当仍是当前流时才更新连接状态，避免旧流的 finally 覆盖新流状态
    if (abortController === controller) {
      connected.value = false
    }
  })
}

function appendEntry(entry: SystemLogItem) {
  rows.value.push(entry)
  if (rows.value.length > MAX_ROWS) {
    rows.value.splice(0, rows.value.length - MAX_ROWS)
  }
  scrollToBottom()
}

async function applyFilters() {
  // 立即停掉旧流，避免 loadHistory/loadStats 网络等待期间旧筛选条件继续追加日志
  abortController?.abort()
  await loadHistory()
  await loadStats()
  connectStream()
}

function resetFilters() {
  filters.level = ''
  filters.modules = []
  filters.keyword = ''
  void applyFilters()
}

function scrollToBottom() {
  if (!followTail.value) return
  nextTick(() => {
    const panel = logPanel.value
    if (panel) panel.scrollTop = panel.scrollHeight
  })
}

function onScroll() {
  const panel = logPanel.value
  if (!panel) return
  const distance = panel.scrollHeight - panel.scrollTop - panel.clientHeight
  followTail.value = distance < 60
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

function levelVariant(level: string): 'default' | 'destructive' | 'outline' | 'secondary' {
  switch (level) {
    case 'ERROR': return 'destructive'
    case 'WARN': return 'secondary'
    case 'DEBUG':
    case 'TRACE': return 'outline'
    default: return 'default'
  }
}

function levelClass(level: string) {
  return `log-level-${level.toLowerCase()}`
}

async function downloadLogs() {
  downloading.value = true
  try {
    const res = await apiSystemLog.download({
      level: filters.level || undefined,
      modules: modulesQuery.value || undefined,
      keyword: filters.keyword || undefined,
    })
    saveExcelResponse(res as unknown as { data: Blob; headers: Record<string, string> }, '系统日志.log')
  }
  finally {
    downloading.value = false
  }
}

function confirmClear() {
  modal.confirm({
    title: '确认清空系统日志',
    content: '确认清空内存中的系统日志缓存吗？该操作仅清空当前缓存的运行日志。',
    onConfirm: clearLogs,
  })
}

async function clearLogs() {
  clearing.value = true
  try {
    const res = await apiSystemLog.clear()
    toast.success(`已清空 ${res.data || 0} 条系统日志`)
    rows.value = []
    await loadStats()
  }
  finally {
    clearing.value = false
  }
}

async function saveDockerSettings() {
  saving.value = true
  try {
    const res = await apiSystemLog.updateDockerSettings({
      enabled: dockerSettings.enabled,
      transport: dockerSettings.transport,
      containers: dockerSettings.containers.split(',').map(item => item.trim()).filter(Boolean),
      socket: dockerSettings.socket,
      tail: dockerSettings.tail,
    })
    dockerSettings.enabled = res.data.enabled
    dockerSettings.transport = res.data.transport
    dockerSettings.containers = (res.data.containers || []).join(',')
    dockerSettings.socket = res.data.socket
    dockerSettings.tail = res.data.tail
    toast.success('容器日志配置已保存')
    await loadStats()
  }
  finally {
    saving.value = false
  }
}
</script>

<template>
  <div>
    <FaPageHeader title="系统日志" class="mb-0">
      <template #description>
        <span>实时查看宿主控制台运行日志（含插件、Milky 机器人及可选 docker 容器日志），自动缓存上限 {{ stats.maxEntries }} 条并自动清理。</span>
      </template>
      <FaButton v-auth="'system:runtime-log:config'" variant="outline" @click="settingsOpen = !settingsOpen">
        <FaIcon name="i-ri:database-2-line" />
        容器日志配置
      </FaButton>
      <FaButton v-auth="'system:runtime-log:download'" variant="outline" :loading="downloading" @click="downloadLogs">
        <FaIcon name="i-ri:download-2-line" />
        下载日志
      </FaButton>
      <FaButton v-auth="'system:runtime-log:delete'" variant="destructive" :loading="clearing" @click="confirmClear">
        <FaIcon name="i-ri:delete-bin-6-line" />
        清空缓存
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div v-if="settingsOpen" class="docker-settings">
        <div class="settings-title">容器日志采集配置</div>
        <div class="settings-grid">
          <div class="settings-field">
            <span class="field-label">启用采集</span>
            <FaSwitch v-model="dockerSettings.enabled" />
          </div>
          <div class="settings-field">
            <span class="field-label">传输方式</span>
            <FaSelect v-model="dockerSettings.transport" :options="transportOptions" class="w-[260px]" />
          </div>
          <div class="settings-field">
            <span class="field-label">容器清单（逗号分隔）</span>
            <FaInput v-model="dockerSettings.containers" placeholder="mongodb,redis,es" class="w-[320px]" />
          </div>
          <div class="settings-field">
            <span class="field-label">Socket 路径</span>
            <FaInput v-model="dockerSettings.socket" placeholder="/var/run/docker.sock" class="w-[320px]" />
          </div>
          <div class="settings-field">
            <span class="field-label">初始条数</span>
            <FaNumberField v-model="dockerSettings.tail" :min="1" :max="100000" class="w-[140px]" />
          </div>
        </div>
        <div class="settings-actions">
          <FaButton :loading="saving" @click="saveDockerSettings">
            <FaIcon name="i-ri:save-3-line" />
            保存配置
          </FaButton>
        </div>
      </div>

      <div class="log-toolbar">
        <FaSelect v-model="filters.level" :options="levelOptions" placeholder="日志级别" class="toolbar-level" @change="applyFilters" />
        <FaSelect v-model="filters.modules" multiple :options="moduleSelectOptions" placeholder="模块筛选" class="toolbar-module" @change="applyFilters" />
        <FaInput v-model="filters.keyword" placeholder="关键字（消息 / logger / 异常）" clearable class="toolbar-keyword" @clear="applyFilters" />
        <FaButton variant="outline" @click="applyFilters">
          筛选
        </FaButton>
        <FaButton @click="resetFilters">
          重置
        </FaButton>

        <div class="toolbar-spacer" />

        <div class="toolbar-meta">
          <span class="meta-stat">显示 <b>{{ rows.length }}</b> / 已缓存 <b>{{ stats.size }}</b> 条</span>
          <span v-if="stats.droppedCount > 0" class="meta-stat meta-dropped">已自动清理 <b>{{ stats.droppedCount }}</b> 条</span>
          <span class="meta-live" :class="connected ? 'is-on' : 'is-off'">
            <i class="live-dot" />
            {{ connected ? '实时' : '已断开' }}
          </span>
          <span class="meta-tail">
            自动滚动
            <FaSwitch v-model="followTail" />
          </span>
        </div>
      </div>

      <div ref="logPanel" class="log-panel" :class="{ 'is-loading': loading }" @scroll="onScroll">
        <div v-if="loading" class="log-empty">加载中…</div>
        <div v-else-if="!rows.length" class="log-empty">暂无日志，等待新的日志产生</div>
        <div v-else class="log-list">
          <div
            v-for="entry in rows"
            :key="entry.sequence"
            class="log-row"
            :class="levelClass(entry.level)"
          >
            <div class="log-row-meta" @click="toggleRow(entry.sequence)">
              <span class="log-time">{{ entry.time }}</span>
              <FaTag :variant="levelVariant(entry.level)" class="log-level">{{ entry.level }}</FaTag>
              <span class="log-module">{{ entry.module }}</span>
              <span class="log-thread">{{ entry.thread }}</span>
              <FaIcon
                v-if="entry.throwable"
                name="i-ri:error-warning-line"
                class="log-expand"
                :class="{ 'is-open': expanded.has(entry.sequence) }"
              />
            </div>
            <div class="log-message">{{ entry.message }}</div>
            <pre v-if="entry.throwable && expanded.has(entry.sequence)" class="log-stack">{{ entry.throwable }}</pre>
          </div>
        </div>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.docker-settings {
  padding: 14px;
  margin-bottom: 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.settings-title {
  margin-bottom: 12px;
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 600;
}

.settings-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 14px 24px;
}

.settings-field {
  display: flex;
  align-items: center;
  gap: 10px;
}

.field-label {
  flex-shrink: 0;
  color: var(--color-text-2);
  font-size: 12px;
}

.settings-actions {
  margin-top: 14px;
}

.log-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 12px;
  margin-bottom: 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.toolbar-level {
  width: 150px;
}

.toolbar-module {
  width: 240px;
}

.toolbar-keyword {
  width: 260px;
}

.toolbar-spacer {
  flex: 1;
}

.toolbar-meta {
  display: flex;
  align-items: center;
  gap: 16px;
  color: var(--color-text-2);
  font-size: 12px;
}

.meta-dropped {
  color: var(--color-warning);
}

.meta-live {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.live-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-border-3);
}

.is-on .live-dot {
  background: var(--color-success);
  box-shadow: 0 0 6px var(--color-success);
}

.is-off .live-dot {
  background: var(--color-danger);
}

.meta-tail {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.log-panel {
  height: calc(100vh - 260px);
  min-height: 360px;
  overflow: auto;
  padding: 6px 0;
  background: #0d1117;
  border: 1px solid #232b36;
  border-radius: 8px;
  font-family: 'JetBrains Mono', 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 12.5px;
  line-height: 1.55;
}

.log-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #8b949e;
}

.log-row {
  padding: 3px 16px 3px 13px;
  border-left: 3px solid transparent;
  transition: background-color 0.12s ease;
}

.log-row:hover {
  background: #161b22;
}

.log-row-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
  cursor: pointer;
}

.log-time {
  color: #6e7681;
  flex-shrink: 0;
}

.log-level {
  flex-shrink: 0;
  width: 54px;
  justify-content: center;
  margin: 0;
}

.log-module {
  color: #79c0ff;
  flex-shrink: 0;
}

.log-thread {
  color: #8b949e;
  flex-shrink: 0;
}

.log-expand {
  color: #f0883e;
  flex-shrink: 0;
  transition: transform 0.15s ease;
}

.log-expand.is-open {
  transform: rotate(180deg);
}

.log-message {
  padding-left: 8px;
  color: #c9d1d9;
  white-space: pre-wrap;
  word-break: break-word;
}

.log-row.log-level-error {
  border-left-color: #f85149;
  background: rgba(248, 81, 73, 0.07);
}

.log-row.log-level-error .log-message {
  color: #ff9b9b;
}

.log-row.log-level-warn {
  border-left-color: #d29922;
  background: rgba(210, 153, 34, 0.06);
}

.log-row.log-level-warn .log-message {
  color: #e3b341;
}

.log-row.log-level-debug {
  border-left-color: #8b949e;
}

.log-row.log-level-debug .log-message {
  color: #8b949e;
}

.log-row.log-level-trace {
  border-left-color: #444c56;
}

.log-row.log-level-trace .log-message {
  color: #6e7681;
}

.log-stack {
  margin: 4px 0 6px 8px;
  padding: 8px 12px;
  overflow-x: auto;
  color: #ff9b9b;
  background: #161b22;
  border: 1px solid #232b36;
  border-radius: 6px;
  font-family: inherit;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
