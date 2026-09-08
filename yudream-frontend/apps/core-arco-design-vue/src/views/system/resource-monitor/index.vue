<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { ChartDataset } from '@yudream/dataviz'
import type {
  HostDisk,
  HostNetwork,
  HostProcess,
  HostResourceSnapshot,
  HostThreadHotspot,
  JvmGc,
  ResourceMetricPoint,
} from '@/api/modules/system-monitor'
import apiMonitor from '@/api/modules/system-monitor'
import ChartWidget from '@/components/chart/ChartWidget.vue'
import {
  RESOURCE_GLOSSARY,
  analyzeResource,
  describePool,
  severityLabel,
  severityTag,
} from './analysis'

const toast = useFaToast()

const loading = ref(false)
const historyLoading = ref(false)
const hours = ref(1)
const snapshot = ref<HostResourceSnapshot | null>(null)
const history = ref<ResourceMetricPoint[]>([])
const isMobile = useIsMobile()
const chartHeight = computed(() => isMobile.value ? 200 : 280)
let timer: number | null = null

const hourOptions = [
  { label: '近 1 小时', value: 1 },
  { label: '近 6 小时', value: 6 },
  { label: '近 24 小时', value: 24 },
]

const currentJvmProcess = computed(() => (snapshot.value?.topProcesses || []).find(item => item.currentJvm))

const hostMemoryUsage = computed(() => percentOf(snapshot.value?.memoryUsedBytes, snapshot.value?.memoryTotalBytes)
  ?? snapshot.value?.memoryUsagePercent)
const programHeapUsage = computed(() => percentOf(snapshot.value?.jvmHeapUsedBytes, snapshot.value?.jvmHeapMaxBytes))
const diskUsage = computed(() => percentOf(snapshot.value?.diskUsedBytes, snapshot.value?.diskTotalBytes)
  ?? snapshot.value?.diskUsagePercent)

const stats = computed(() => {
  const data = snapshot.value
  const rssHint = currentJvmProcess.value?.rssBytes
    ? `RSS ${formatBytes(currentJvmProcess.value.rssBytes)}`
    : ''
  return [
    {
      label: 'CPU',
      value: percentText(data?.cpuUsagePercent),
      hint: loadText(data),
      icon: 'i-ri:cpu-line',
    },
    {
      label: '主机内存',
      value: percentText(hostMemoryUsage.value),
      hint: `${formatBytes(data?.memoryUsedBytes)} / ${formatBytes(data?.memoryTotalBytes)}`,
      icon: 'i-ri:database-2-line',
    },
    {
      label: '程序内存',
      value: percentText(programHeapUsage.value),
      hint: [heapHint(data), rssHint].filter(Boolean).join(' · '),
      icon: 'i-ri:stack-line',
    },
    {
      label: '磁盘',
      value: percentText(diskUsage.value),
      hint: `${formatBytes(data?.diskUsedBytes)} / ${formatBytes(data?.diskTotalBytes)}`,
      icon: 'i-ri:hard-drive-2-line',
    },
    {
      label: '网络',
      value: formatRate(data?.networkRecvBytesPerSec),
      hint: `发送 ${formatRate(data?.networkSentBytesPerSec)}`,
      icon: 'i-ri:wifi-line',
    },
    {
      label: '线程',
      value: data?.jvmThreadCount ?? '-',
      hint: data?.jvmDaemonThreadCount != null ? `守护 ${data.jvmDaemonThreadCount}` : '',
      icon: 'i-ri:node-tree',
    },
  ]
})

const hostMeta = computed(() => {
  const data = snapshot.value
  if (!data) {
    return '等待采集'
  }
  const compact = [
    data.hostname,
    data.cpuName,
    data.javaVersion ? `Java ${data.javaVersion}` : '',
    data.jvmPid ? `PID ${data.jvmPid}` : '',
  ]
  const full = [
    data.hostname,
    `${data.osName || ''} ${data.osVersion || ''}`.trim(),
    data.architecture,
    data.cpuName,
    `${data.cpuPhysicalCount || 0} 物理 / ${data.cpuLogicalCount || 0} 逻辑`,
    data.javaVersion ? `Java ${data.javaVersion}` : '',
    data.jvmPid ? `PID ${data.jvmPid}` : '',
    data.jvmUptimeMs ? `运行 ${formatDuration(data.jvmUptimeMs)}` : '',
  ]
  return (isMobile.value ? compact : full).filter(Boolean).join(' · ')
})

const cpuDataset = computed(() => percentLineDataset(history.value, [
  { key: 'cpuUsagePercent', name: 'CPU' },
]))

const memoryDataset = computed<ChartDataset>(() => ({
  chartType: 'line',
  dimensions: ['time', '主机内存', '程序 Heap'],
  source: history.value.map(point => ({
    time: formatTime(point.sampledAt),
    主机内存: roundPercent(point.memoryUsagePercent ?? percentOf(point.memoryUsedBytes, point.memoryTotalBytes)),
    '程序 Heap': roundPercent(percentOf(point.jvmHeapUsedBytes, point.jvmHeapMaxBytes)),
  })),
}))

const diskDataset = computed(() => percentLineDataset(history.value, [
  { key: 'diskUsagePercent', name: '磁盘' },
]))

const networkDataset = computed<ChartDataset>(() => ({
  chartType: 'line',
  dimensions: ['time', '接收', '发送'],
  source: history.value.map(point => ({
    time: formatTime(point.sampledAt),
    接收: bytesToKb(point.networkRecvBytesPerSec),
    发送: bytesToKb(point.networkSentBytesPerSec),
  })),
}))

const poolDataset = computed<ChartDataset>(() => ({
  chartType: 'pie',
  dimensions: ['name', 'usedBytes'],
  source: (snapshot.value?.memoryPools || []).map(pool => ({
    name: pool.name || '未知',
    usedBytes: pool.usedBytes || 0,
  })),
}))

const diskColumns = computed<TableColumn<HostDisk>[]>(() => [
  { accessorKey: 'name', header: '卷', width: 180 },
  { accessorKey: 'mount', header: '挂载点', width: 180 },
  { accessorKey: 'type', header: '类型', width: 100 },
  { id: 'used', header: '已用 / 总量', width: 220 },
  { id: 'usagePercent', header: '使用率', width: 100, align: 'right' },
])

const networkColumns = computed<TableColumn<HostNetwork>[]>(() => [
  { accessorKey: 'name', header: '网卡', width: 140 },
  { accessorKey: 'ipv4', header: 'IPv4', width: 140 },
  { id: 'recv', header: '接收', width: 140, align: 'right' },
  { id: 'sent', header: '发送', width: 140, align: 'right' },
])

const processColumns = computed<TableColumn<HostProcess>[]>(() => [
  { accessorKey: 'pid', header: 'PID', width: 90 },
  { accessorKey: 'name', header: '进程', width: 180 },
  { accessorKey: 'user', header: '用户', width: 120 },
  { id: 'rssBytes', header: 'RSS', width: 120, align: 'right' },
  { id: 'cpuPercent', header: '累计 CPU', width: 110, align: 'right' },
  { accessorKey: 'commandLine', header: '命令行', width: 360 },
])

const threadColumns = computed<TableColumn<HostThreadHotspot>[]>(() => [
  { accessorKey: 'threadId', header: '线程 ID', width: 110 },
  { accessorKey: 'name', header: '线程', width: 220 },
  { accessorKey: 'state', header: '状态', width: 120 },
  { id: 'cpuTimeMs', header: 'CPU 时间', width: 120, align: 'right' },
  { id: 'stackTop', header: '栈顶', width: 420 },
])

const gcText = computed(() => (snapshot.value?.garbageCollectors || []).map(formatGc).join(' · ') || '暂无 GC 数据')
const glossaryOpen = ref(false)
const analysis = computed(() => analyzeResource(snapshot.value, history.value))
const glossaryPreview = RESOURCE_GLOSSARY.slice(0, 6)

onMounted(async () => {
  await refreshAll()
  timer = window.setInterval(() => {
    void loadSnapshot()
  }, 5000)
})

onUnmounted(() => {
  if (timer != null) {
    window.clearInterval(timer)
  }
})

async function refreshAll() {
  await Promise.all([loadSnapshot(), loadHistory()])
}

async function loadSnapshot() {
  loading.value = true
  try {
    const res = await apiMonitor.resource()
    snapshot.value = normalizeSnapshot(res.data)
  }
  catch {
    toast.error('资源监控', { description: '实时快照暂时不可用' })
  }
  finally {
    loading.value = false
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const windowHours = Number(hours.value) || 1
    const res = await apiMonitor.resourceHistory({ hours: windowHours })
    history.value = res.data || []
  }
  catch {
    toast.error('资源监控', { description: '历史趋势暂时不可用' })
  }
  finally {
    historyLoading.value = false
  }
}

function normalizeSnapshot(data?: HostResourceSnapshot | null): HostResourceSnapshot {
  return {
    disks: [],
    networks: [],
    memoryPools: [],
    garbageCollectors: [],
    topProcesses: [],
    topThreads: [],
    ...data,
  }
}

function percentLineDataset(
  points: ResourceMetricPoint[],
  series: Array<{ key: keyof ResourceMetricPoint, name: string }>,
): ChartDataset {
  return {
    chartType: 'line',
    dimensions: ['time', ...series.map(item => item.name)],
    source: points.map(point => {
      const row: Record<string, unknown> = { time: formatTime(point.sampledAt) }
      for (const item of series) {
        row[item.name] = roundPercent(Number(point[item.key] ?? 0))
      }
      return row
    }),
  }
}

function percentOf(used?: number, total?: number) {
  if (used == null || total == null || total <= 0) {
    return undefined
  }
  return (used / total) * 100
}

function roundPercent(value?: number) {
  return value == null ? 0 : Number(Number(value).toFixed(1))
}

function percentText(value?: number) {
  return value == null ? '-' : `${Number(value).toFixed(1)}%`
}

function heapHint(data?: HostResourceSnapshot | null) {
  if (!data) {
    return ''
  }
  return `Heap ${formatBytes(data.jvmHeapUsedBytes)} / ${formatBytes(data.jvmHeapMaxBytes)}`
}

function loadText(data?: HostResourceSnapshot | null) {
  if (!data) {
    return ''
  }
  const load = [data.load1, data.load5, data.load15]
    .filter(item => item != null)
    .map(item => Number(item).toFixed(2))
    .join(' / ')
  return load ? `负载 ${load}` : ''
}

function formatBytes(value?: number) {
  if (value == null || Number.isNaN(value)) {
    return '-'
  }
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = Math.max(0, Number(value))
  let unit = 0
  while (size >= 1024 && unit < units.length - 1) {
    size /= 1024
    unit += 1
  }
  return `${size >= 10 || unit === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unit]}`
}

function formatRate(value?: number) {
  return `${formatBytes(value)}/s`
}

function bytesToKb(value?: number) {
  return Number(((value || 0) / 1024).toFixed(1))
}

function formatDuration(ms?: number) {
  if (!ms) {
    return '-'
  }
  const total = Math.floor(ms / 1000)
  const hoursPart = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (hoursPart > 48) {
    return `${Math.floor(hoursPart / 24)} 天`
  }
  if (hoursPart > 0) {
    return `${hoursPart} 小时 ${minutes} 分`
  }
  return `${minutes} 分`
}

function formatTime(value?: string) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value.slice(11, 19) || value
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false })
}

function formatGc(item: JvmGc) {
  return `${item.name || 'GC'} ${item.collectionCount ?? 0} 次 / ${formatDuration(item.collectionTimeMs)}`
}

function stackPreview(row: HostThreadHotspot) {
  return (row.stackTop || []).slice(0, 2).join('\n') || '-'
}
</script>

<template>
  <div>
    <FaPageHeader title="资源监控" class="mb-0 resource-header" main-class="min-w-0" default-class="w-full min-w-0 flex-wrap sm:w-auto">
      <template #description>
        <p class="host-meta">{{ hostMeta }}</p>
      </template>
      <FaSelect v-model="hours" :options="hourOptions" class="w-full min-w-0 sm:w-[140px]" @update:model-value="loadHistory" />
      <FaButton class="shrink-0" :loading="loading || historyLoading" @click="refreshAll">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
    </FaPageHeader>

    <FaPageMain class="resource-main" main-class="min-w-0 overflow-x-hidden">
      <p v-if="snapshot?.notice" class="monitor-notice">{{ snapshot.notice }}</p>

      <section class="monitor-panel analysis-panel">
        <div class="panel-title">
          <span>优化分析</span>
          <FaTag :variant="severityTag(analysis.severity)">{{ severityLabel(analysis.severity) }}</FaTag>
        </div>
        <FaAlert
          :icon="analysis.severity === 'warn' ? 'i-ri:error-warning-line' : 'i-ri:lightbulb-line'"
          :title="analysis.headline"
          :description="analysis.summary"
          :variant="analysis.severity === 'warn' ? 'destructive' : 'default'"
          class="analysis-alert"
        />
        <div v-if="analysis.findings.length" class="finding-list">
          <article v-for="item in analysis.findings" :key="item.id" class="finding-card">
            <div class="finding-head">
              <strong>{{ item.title }}</strong>
              <FaTag :variant="severityTag(item.severity)">{{ severityLabel(item.severity) }}</FaTag>
            </div>
            <p>{{ item.summary }}</p>
            <p class="finding-action">{{ item.action }}</p>
          </article>
        </div>
        <p v-else class="monitor-sub">当前没有需要处理的资源瓶颈，趋势图可用于确认是否刚刚抖动。</p>
        <div class="spend-grid">
          <div>
            <div class="panel-title">
              <span>内存花在哪</span>
            </div>
            <p class="monitor-sub">把本机进程 RSS 和当前 JVM 内存池放在一起看：先分清是别人占内存，还是自己的堆/堆外在涨。</p>
            <ol v-if="analysis.memorySpenders.length" class="spend-list">
              <li v-for="item in analysis.memorySpenders" :key="`mem-${item.name}-${item.detail}`">
                <div>
                  <strong>{{ item.name }}</strong>
                  <FaTag v-if="item.current" variant="secondary">当前程序</FaTag>
                </div>
                <span>{{ item.detail }}</span>
                <p>{{ item.hint }}</p>
              </li>
            </ol>
            <p v-else class="monitor-sub">暂无进程或内存池数据</p>
          </div>
          <div>
            <div class="panel-title">
              <span>CPU 花在哪</span>
            </div>
            <p class="monitor-sub">先看本机进程，再看当前 JVM 线程。线程累计 CPU 高，可能是热点，也可能只是活得久。</p>
            <ol v-if="analysis.cpuSpenders.length" class="spend-list">
              <li v-for="item in analysis.cpuSpenders" :key="`cpu-${item.name}-${item.detail}`">
                <div>
                  <strong>{{ item.name }}</strong>
                  <FaTag v-if="item.current" variant="secondary">当前程序</FaTag>
                </div>
                <span>{{ item.detail }}</span>
                <p>{{ item.hint }}</p>
              </li>
            </ol>
            <p v-else class="monitor-sub">暂无进程或线程热点</p>
          </div>
        </div>
      </section>

      <div class="monitor-summary">
        <div v-for="item in stats" :key="item.label" class="summary-item">
          <div class="summary-icon">
            <FaIcon :name="item.icon" />
          </div>
          <div class="summary-body">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
            <em v-if="item.hint">{{ item.hint }}</em>
          </div>
        </div>
      </div>

      <div class="chart-grid">
        <section class="monitor-panel">
          <div class="panel-title">CPU 使用率</div>
          <p class="monitor-sub">整机处理器忙闲程度。曲线贴近 100% 表示机器已经在排队，不一定等于当前 Java 程序在忙。</p>
          <ChartWidget :dataset="cpuDataset" :height="chartHeight" />
        </section>
        <section class="monitor-panel">
          <div class="panel-title">内存使用率</div>
          <p class="monitor-sub">主机内存是整机物理占用；程序 Heap 才是当前 Java 对象占用。两条线接近，说明内存压力主要来自本程序。</p>
          <ChartWidget :dataset="memoryDataset" :height="chartHeight" />
        </section>
        <section class="monitor-panel">
          <div class="panel-title">磁盘使用率</div>
          <p class="monitor-sub">所有分区合计的容量占用。真正要清理时，请看下面的磁盘分区表，确认是哪个挂载点先满。</p>
          <ChartWidget :dataset="diskDataset" :height="chartHeight" />
        </section>
        <section class="monitor-panel">
          <div class="panel-title">网络速率 KB/s</div>
          <p class="monitor-sub">当前每秒收发了多少数据，不是累计流量。突然升高通常对应下载、推送或大文件传输。</p>
          <ChartWidget :dataset="networkDataset" :height="chartHeight" />
        </section>
      </div>

      <div class="monitor-grid">
        <section class="monitor-panel">
          <div class="panel-title">
            <span>JVM 内存池</span>
            <FaTag variant="secondary">{{ snapshot?.memoryPools.length || 0 }}</FaTag>
          </div>
          <p class="monitor-sub">饼图按已用字节看内存花在哪一块。Heap 高看 Eden/Old，RSS 明显高于 Heap 时再看 Metaspace、Code Cache 或 Direct。</p>
          <ChartWidget :dataset="poolDataset" :height="chartHeight" />
          <ul class="pool-legend">
            <li v-for="pool in snapshot?.memoryPools || []" :key="pool.name">
              <strong>{{ pool.name }}</strong>
              <span>{{ formatBytes(pool.usedBytes) }} · {{ describePool(pool).hint }}</span>
            </li>
          </ul>
          <p class="monitor-sub">{{ gcText }}</p>
        </section>
        <section class="monitor-panel">
          <div class="panel-title">磁盘分区</div>
          <p class="monitor-sub">每个挂载点各自的容量。日志、上传和数据库文件通常不在同一个分区。</p>
          <FaResponsiveTable
            row-key="mount"
            table-root-class="rounded-md overflow-hidden"
            table-class="min-w-[640px]"
            border
            stripe
            :columns="diskColumns"
            :data="snapshot?.disks || []"
          >
            <template #cell-used="{ row }">
              {{ formatBytes(row.original.usedBytes) }} / {{ formatBytes(row.original.totalBytes) }}
            </template>
            <template #cell-usagePercent="{ row }">
              {{ percentText(row.original.usagePercent) }}
            </template>
            <template #card="{ row }">
              <FaCard class="w-full min-w-0">
                <div class="card-row">
                  <strong>{{ row.name || row.mount }}</strong>
                  <span>{{ percentText(row.usagePercent) }}</span>
                </div>
                <p class="monitor-sub">{{ row.mount }} · {{ row.type || '未知类型' }}</p>
                <p class="monitor-sub">{{ formatBytes(row.usedBytes) }} / {{ formatBytes(row.totalBytes) }}</p>
              </FaCard>
            </template>
          </FaResponsiveTable>
        </section>
      </div>

      <section class="monitor-panel monitor-panel--wide">
        <div class="panel-title">网卡</div>
        <p class="monitor-sub">看具体哪块网卡在跑流量。虚拟网卡速率高不一定代表对外带宽打满。</p>
        <FaResponsiveTable
          row-key="name"
          table-root-class="rounded-md overflow-hidden"
          table-class="min-w-[640px]"
          border
          stripe
          :columns="networkColumns"
          :data="snapshot?.networks || []"
        >
          <template #cell-recv="{ row }">
            {{ formatRate(row.original.recvBytesPerSec) }}
          </template>
          <template #cell-sent="{ row }">
            {{ formatRate(row.original.sentBytesPerSec) }}
          </template>
            <template #card="{ row }">
            <FaCard class="w-full min-w-0">
              <div class="card-row">
                <strong>{{ row.name }}</strong>
                <span>{{ row.ipv4 || '-' }}</span>
              </div>
              <p class="monitor-sub">{{ formatRate(row.recvBytesPerSec) }} ↓ / {{ formatRate(row.sentBytesPerSec) }} ↑</p>
            </FaCard>
          </template>
        </FaResponsiveTable>
      </section>

      <div class="monitor-grid">
        <section class="monitor-panel">
          <div class="panel-title">
            <span>进程 RSS Top</span>
            <FaTag variant="secondary">{{ snapshot?.topProcesses.length || 0 }}</FaTag>
          </div>
          <p class="monitor-sub">本机物理内存占用最高的进程。带「当前 JVM」的才是这个管理端；如果第一名是别的程序，先别给 Java 加堆。</p>
          <FaResponsiveTable
            row-key="pid"
            table-root-class="rounded-md overflow-hidden"
            table-class="min-w-[860px]"
            border
            stripe
            :columns="processColumns"
            :data="snapshot?.topProcesses || []"
          >
            <template #cell-name="{ row }">
              <span>{{ row.original.name }}</span>
              <FaTag v-if="row.original.currentJvm" variant="secondary" class="ml-2">当前 JVM</FaTag>
            </template>
            <template #cell-rssBytes="{ row }">
              {{ formatBytes(row.original.rssBytes) }}
            </template>
            <template #cell-cpuPercent="{ row }">
              {{ percentText(row.original.cpuPercent) }}
            </template>
            <template #card="{ row }">
              <FaCard class="w-full min-w-0">
                <div class="card-row">
                  <strong>{{ row.name }} #{{ row.pid }}</strong>
                  <FaTag v-if="row.currentJvm" variant="secondary">当前 JVM</FaTag>
                </div>
                <p class="monitor-sub">RSS {{ formatBytes(row.rssBytes) }} · CPU {{ percentText(row.cpuPercent) }}</p>
                <code>{{ row.commandLine || '-' }}</code>
              </FaCard>
            </template>
          </FaResponsiveTable>
        </section>
        <section class="monitor-panel">
          <div class="panel-title">
            <span>线程 CPU Top</span>
            <FaTag variant="secondary">{{ snapshot?.topThreads.length || 0 }}</FaTag>
          </div>
          <p class="monitor-sub">当前 JVM 里累计 CPU 最高的线程。RUNNABLE 更像计算热点；WAITING / BLOCKED 更像在等锁或 IO。</p>
          <FaResponsiveTable
            row-key="threadId"
            table-root-class="rounded-md overflow-hidden"
            table-class="min-w-[860px]"
            border
            stripe
            :columns="threadColumns"
            :data="snapshot?.topThreads || []"
          >
            <template #cell-cpuTimeMs="{ row }">
              {{ formatDuration(row.original.cpuTimeMs) }}
            </template>
            <template #cell-stackTop="{ row }">
              <code>{{ stackPreview(row.original) }}</code>
            </template>
            <template #card="{ row }">
              <FaCard class="w-full min-w-0">
                <div class="card-row">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.state || '-' }}</span>
                </div>
                <p class="monitor-sub">CPU {{ formatDuration(row.cpuTimeMs) }}</p>
                <code>{{ stackPreview(row) }}</code>
              </FaCard>
            </template>
          </FaResponsiveTable>
        </section>
      </div>

      <section class="monitor-panel glossary-panel">
        <FaCollapsible v-model="glossaryOpen">
          <template #trigger="{ open }">
            <div class="panel-title glossary-trigger">
              <span>名词解释</span>
              <span class="glossary-toggle">{{ open ? '收起' : '展开全部' }}</span>
            </div>
          </template>
          <dl class="glossary-list">
            <div v-for="item in RESOURCE_GLOSSARY" :key="item.term">
              <dt>{{ item.term }}</dt>
              <dd>{{ item.meaning }}</dd>
            </div>
          </dl>
        </FaCollapsible>
        <dl v-if="!glossaryOpen" class="glossary-list glossary-preview">
          <div v-for="item in glossaryPreview" :key="item.term">
            <dt>{{ item.term }}</dt>
            <dd>{{ item.meaning }}</dd>
          </div>
        </dl>
      </section>
    </FaPageMain>
  </div>
</template>

<style scoped>
.resource-header :deep(.text-sm) {
  min-width: 0;
}

.resource-main {
  min-width: 0;
}

.resource-main :deep(.dataviz-base-chart) {
  max-width: 100%;
  min-width: 0;
}

.host-meta {
  margin: 0;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.monitor-notice,
.monitor-sub {
  margin: 0 0 12px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.monitor-notice {
  padding: 8px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.monitor-summary {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 16px;
}

.summary-item {
  display: flex;
  min-width: 0;
  gap: 12px;
  align-items: flex-start;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.summary-icon {
  display: grid;
  width: 36px;
  height: 36px;
  flex: none;
  place-items: center;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 18px;
}

.summary-body {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.summary-body span {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-body strong {
  overflow-wrap: anywhere;
  font-size: 20px;
  font-weight: 700;
  line-height: 1.3;
}

.summary-body em {
  color: var(--color-text-3);
  font-size: 12px;
  font-style: normal;
  line-height: 1.4;
  overflow-wrap: anywhere;
}

.chart-grid,
.monitor-grid,
.spend-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.monitor-panel {
  min-width: 0;
  overflow: hidden;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.monitor-panel--wide,
.analysis-panel,
.glossary-panel {
  margin-bottom: 16px;
}

.analysis-alert {
  margin-bottom: 16px;
}

.finding-list,
.pool-legend,
.glossary-list {
  display: grid;
  gap: 12px;
}

.finding-list {
  grid-template-columns: minmax(0, 1fr);
  margin-bottom: 16px;
}

.finding-card,
.spend-list li,
.pool-legend li,
.glossary-list > div {
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.finding-head,
.spend-list li > div,
.glossary-trigger,
.card-row {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.finding-head strong,
.spend-list li strong,
.card-row strong,
.card-row span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.finding-card p,
.spend-list p,
.pool-legend span,
.glossary-list dd {
  margin: 6px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.finding-card .finding-action {
  color: var(--color-text-2);
}

.pool-legend,
.glossary-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.spend-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding-left: 20px;
}

.spend-list li {
  display: grid;
  gap: 4px;
}

.spend-list li > span,
.pool-legend strong,
.glossary-list dt {
  color: var(--color-text-1);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.glossary-trigger {
  width: 100%;
  cursor: pointer;
}

.glossary-preview,
.glossary-list {
  margin-top: 12px;
  grid-template-columns: minmax(0, 1fr);
}

.panel-title {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  font-weight: 600;
}

.panel-title > span {
  min-width: 0;
  overflow-wrap: anywhere;
}

code {
  display: block;
  max-width: 100%;
  overflow: hidden;
  color: var(--color-text-2);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (min-width: 640px) {
  .monitor-summary {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .glossary-preview,
  .glossary-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 768px) {
  .finding-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1180px) {
  .monitor-summary {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .chart-grid,
  .monitor-grid,
  .spend-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
