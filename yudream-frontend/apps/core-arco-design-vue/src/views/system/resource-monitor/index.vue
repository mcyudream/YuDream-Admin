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
  analyzeResource,
  describePool,
  METRIC_THRESHOLDS,
  metricTone,
  RESOURCE_GLOSSARY,
  severityLabel,
  severityTag,
} from './analysis'

/** 自动刷新间隔（秒），0 表示只手动刷新 */
const REFRESH_OPTIONS = [
  { label: '每 5 秒', value: 5 },
  { label: '每 10 秒', value: 10 },
  { label: '每 30 秒', value: 30 },
  { label: '仅手动刷新', value: 0 },
]
/** 网卡默认只列前几块：本机虚拟网卡很多，全量表格会把真正在跑流量的那几块淹掉 */
const NETWORK_TOP = 5
/** 各分区展开状态本地记忆（结论与明细默认收起） */
const SECTION_STORAGE_KEY = 'yudream:resource-monitor:sections'

const toast = useYdToast()

const loading = ref(false)
const historyLoading = ref(false)
const hours = ref(1)
const refreshSeconds = ref(5)
const lastUpdatedText = ref('')
const snapshotError = ref('')
const historyError = ref('')
const networkExpanded = ref(false)
const snapshot = ref<HostResourceSnapshot | null>(null)
const history = ref<ResourceMetricPoint[]>([])
const isMobile = useIsMobile()
const chartHeight = computed(() => isMobile.value ? 200 : 280)
let timer: number | null = null

/**
 * 分区折叠状态：结论与明细默认收起，首屏只留指标卡与趋势；折叠头仍带关键数字，信息不靠展开才能看到。
 * 只记住「展开」过的分区，未记录的按下表默认值走。
 */
type SectionId = 'analysis' | 'pools' | 'disks' | 'networks' | 'processes' | 'threads'
const SECTION_DEFAULTS: Record<SectionId, boolean> = {
  analysis: false,
  pools: false,
  disks: false,
  networks: false,
  processes: false,
  threads: false,
}
const sectionOpen = reactive<Record<SectionId, boolean>>({ ...SECTION_DEFAULTS })

function isSectionOpen(id: SectionId) {
  return sectionOpen[id]
}

function toggleSection(id: SectionId, open: boolean) {
  sectionOpen[id] = open
  try {
    window.localStorage.setItem(SECTION_STORAGE_KEY, JSON.stringify({ ...sectionOpen }))
  }
  catch { /* 隐私模式等存储不可用时忽略，偏好仅在本次会话生效 */ }
}

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
  const swapUsed = data?.swapUsedBytes
  const hostMemoryHint = [
    `${formatBytes(data?.memoryUsedBytes)} / ${formatBytes(data?.memoryTotalBytes)}`,
    swapUsed ? `交换 ${formatBytes(swapUsed)}` : '',
  ].filter(Boolean).join(' · ')
  return [
    {
      label: 'CPU',
      value: percentText(data?.cpuUsagePercent),
      hint: loadText(data),
      icon: 'morph:cpu',
      percent: numberOrNull(data?.cpuUsagePercent),
      tone: metricTone(numberOrNull(data?.cpuUsagePercent), METRIC_THRESHOLDS.cpu),
    },
    {
      label: '主机内存',
      value: percentText(hostMemoryUsage.value),
      hint: hostMemoryHint,
      icon: 'morph:database',
      percent: numberOrNull(hostMemoryUsage.value),
      tone: metricTone(numberOrNull(hostMemoryUsage.value), METRIC_THRESHOLDS.hostMemory),
    },
    {
      label: '程序内存',
      value: percentText(programHeapUsage.value),
      hint: [heapHint(data), rssHint].filter(Boolean).join(' · '),
      icon: 'morph:layers',
      percent: numberOrNull(programHeapUsage.value),
      tone: metricTone(numberOrNull(programHeapUsage.value), METRIC_THRESHOLDS.heap),
    },
    {
      label: '磁盘',
      value: percentText(diskUsage.value),
      hint: `${formatBytes(data?.diskUsedBytes)} / ${formatBytes(data?.diskTotalBytes)}`,
      icon: 'morph:hard-drive',
      percent: numberOrNull(diskUsage.value),
      tone: metricTone(numberOrNull(diskUsage.value), METRIC_THRESHOLDS.disk),
    },
    {
      label: '网络',
      value: formatRate(data?.networkRecvBytesPerSec),
      hint: `发送 ${formatRate(data?.networkSentBytesPerSec)}`,
      icon: 'morph:wifi',
      percent: null,
      tone: 'neutral' as const,
    },
    {
      label: '线程',
      value: data?.jvmThreadCount ?? '-',
      hint: data?.jvmDaemonThreadCount != null ? `守护 ${data.jvmDaemonThreadCount}` : '',
      icon: 'morph:list-tree',
      percent: null,
      tone: 'neutral' as const,
    },
  ]
})

/** 趋势读数：当前 / 区间均 / 区间峰（无点返回空串，由调用方决定是否渲染） */
type TrendKey = 'cpu' | 'memory' | 'disk' | 'network'
const trendMetrics = computed<Record<TrendKey, string>>(() => {
  const points = history.value
  return {
    cpu: percentTrend(points.map(point => numberOrNull(point.cpuUsagePercent))),
    memory: percentTrend(points.map(point => numberOrNull(percentOf(point.memoryUsedBytes, point.memoryTotalBytes) ?? point.memoryUsagePercent))),
    disk: percentTrend(points.map(point => numberOrNull(point.diskUsagePercent ?? percentOf(point.diskUsedBytes, point.diskTotalBytes)))),
    network: rateTrend(points),
  }
})

/** 网卡按实时速率排序，默认只看前几块；「全部」按钮展开完整的物理与虚拟网卡 */
const networkRows = computed(() => {
  const list = [...(snapshot.value?.networks || [])]
    .sort((a, b) => networkWeight(b) - networkWeight(a))
  return networkExpanded.value ? list : list.slice(0, NETWORK_TOP)
})

const networkHiddenCount = computed(() => Math.max(0, (snapshot.value?.networks?.length || 0) - networkRows.value.length))

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
    'time': formatTime(point.sampledAt),
    '主机内存': roundPercent(point.memoryUsagePercent ?? percentOf(point.memoryUsedBytes, point.memoryTotalBytes)),
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

const trendCharts = computed<Array<{ id: TrendKey, title: string, hint: string, dataset: ChartDataset }>>(() => [
  {
    id: 'cpu',
    title: 'CPU 使用率',
    hint: '整机处理器忙闲程度。曲线贴近 100% 表示机器已经在排队，不一定等于当前 Java 程序在忙。',
    dataset: cpuDataset.value,
  },
  {
    id: 'memory',
    title: '内存使用率',
    hint: '主机内存是整机物理占用；程序 Heap 才是当前 Java 对象占用。两条线接近，说明内存压力主要来自本程序。',
    dataset: memoryDataset.value,
  },
  {
    id: 'disk',
    title: '磁盘使用率',
    hint: '所有分区合计的容量占用。真正要清理时，请看下面的磁盘分区表，确认是哪个挂载点先满。',
    dataset: diskDataset.value,
  },
  {
    id: 'network',
    title: '网络速率 KB/s',
    hint: '当前每秒收发了多少数据，不是累计流量。突然升高通常对应下载、推送或大文件传输。',
    dataset: networkDataset.value,
  },
])

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

/* 折叠头摘要：收起后仍要能看出这一块有没有事，不靠展开才知道 */
const poolSummary = computed(() => {
  const pools = snapshot.value?.memoryPools || []
  if (!pools.length) {
    return ''
  }
  const biggest = [...pools].sort((a, b) => (b.usedBytes || 0) - (a.usedBytes || 0))[0]
  return `${pools.length} 个池 · 最大 ${biggest?.name || '未知'} ${formatBytes(biggest?.usedBytes)}`
})

const diskSummary = computed(() => {
  const disks = snapshot.value?.disks || []
  if (!disks.length) {
    return ''
  }
  const fullest = [...disks].sort((a, b) => (b.usagePercent || 0) - (a.usagePercent || 0))[0]
  return `${disks.length} 个分区 · 最满 ${fullest?.name || fullest?.mount || '未知'} ${percentText(fullest?.usagePercent)}`
})

/** 最满分区达到注意线时，摘要跟着变色（与指标卡同一套阈值） */
const diskSummaryTone = computed(() => diskTone(snapshot.value?.disks?.length
  ? Math.max(...(snapshot.value.disks.map(disk => Number(disk.usagePercent) || 0)))
  : undefined))

const networkSummary = computed(() => {
  const networks = snapshot.value?.networks || []
  if (!networks.length) {
    return ''
  }
  return `${networks.length} 块 · 接收 ${formatRate(snapshot.value?.networkRecvBytesPerSec)} / 发送 ${formatRate(snapshot.value?.networkSentBytesPerSec)}`
})

const processSummary = computed(() => {
  const top = [...(snapshot.value?.topProcesses || [])].sort((a, b) => (b.rssBytes || 0) - (a.rssBytes || 0))[0]
  return top ? `最高 ${top.name || '未知进程'} ${formatBytes(top.rssBytes)}` : ''
})

const threadSummary = computed(() => {
  const top = [...(snapshot.value?.topThreads || [])].sort((a, b) => (b.cpuTimeMs || 0) - (a.cpuTimeMs || 0))[0]
  return top ? `最高 ${top.name || '未知线程'} ${formatDuration(top.cpuTimeMs)}` : ''
})

onMounted(async () => {
  loadSectionPref()
  document.addEventListener('visibilitychange', onVisibilityChange)
  restartTimer()
  await refreshAll()
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityChange)
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
})

watch(refreshSeconds, restartTimer)

function loadSectionPref() {
  try {
    const raw = window.localStorage.getItem(SECTION_STORAGE_KEY)
    if (!raw) {
      return
    }
    const stored = JSON.parse(raw) as Partial<Record<SectionId, boolean>>
    for (const id of Object.keys(SECTION_DEFAULTS) as SectionId[]) {
      if (typeof stored[id] === 'boolean') {
        sectionOpen[id] = stored[id] as boolean
      }
    }
  }
  catch { /* 损坏的本地偏好直接忽略 */ }
}

/** 自动刷新只在页面可见时轮询；隐藏期间不打扰后端，切回可见立即补一次快照 */
function restartTimer() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
  const seconds = Number(refreshSeconds.value) || 0
  if (seconds <= 0) {
    return
  }
  timer = window.setInterval(() => {
    if (document.visibilityState === 'visible') {
      void loadSnapshot('auto')
    }
  }, seconds * 1000)
}

function onVisibilityChange() {
  if (document.visibilityState === 'visible') {
    void loadSnapshot('auto')
  }
}

async function refreshAll() {
  await Promise.all([loadSnapshot('manual'), loadHistory('manual')])
}

async function loadSnapshot(mode: 'auto' | 'manual' = 'auto') {
  if (mode === 'manual') {
    loading.value = true
  }
  try {
    const res = await apiMonitor.resource()
    snapshot.value = normalizeSnapshot(res.data)
    snapshotError.value = ''
    lastUpdatedText.value = formatClock(new Date())
  }
  catch (error) {
    // 自动轮询失败不弹 toast（每 5 秒一条会刷屏），改为页面顶部的常驻错误条
    snapshotError.value = error instanceof Error ? error.message : '实时快照暂时不可用'
    if (mode === 'manual') {
      toast.error('资源监控', { description: snapshotError.value })
    }
  }
  finally {
    loading.value = false
  }
}

async function loadHistory(mode: 'auto' | 'manual' = 'manual') {
  historyLoading.value = true
  try {
    const windowHours = Number(hours.value) || 1
    const res = await apiMonitor.resourceHistory({ hours: windowHours })
    history.value = res.data || []
    historyError.value = ''
  }
  catch (error) {
    historyError.value = error instanceof Error ? error.message : '历史趋势暂时不可用'
    if (mode === 'manual') {
      toast.error('资源监控', { description: historyError.value })
    }
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
    source: points.map((point) => {
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

/** 数字型快照字段经 Jackson 序列化后可能是字符串，统一转成 number 或 null */
function numberOrNull(value?: number | string | null) {
  if (value == null) {
    return null
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
}

/** 趋势读数：当前 / 区间均 / 区间峰（无点返回空串，由调用方决定是否渲染） */
function percentTrend(values: Array<number | null>) {
  const list = values.filter((value): value is number => value != null)
  if (!list.length) {
    return ''
  }
  const avg = list.reduce((total, value) => total + value, 0) / list.length
  return `当前 ${roundPercent(list[list.length - 1])}% · 均 ${roundPercent(avg)}% · 峰 ${roundPercent(Math.max(...list))}%`
}

function rateTrend(points: ResourceMetricPoint[]) {
  const recv = points.map(point => numberOrNull(point.networkRecvBytesPerSec) ?? 0)
  const sent = points.map(point => numberOrNull(point.networkSentBytesPerSec) ?? 0)
  if (!recv.length) {
    return ''
  }
  return `当前 ↓${formatRate(recv[recv.length - 1])} ↑${formatRate(sent[sent.length - 1])} · 峰 ↓${formatRate(Math.max(...recv))} ↑${formatRate(Math.max(...sent))}`
}

function networkWeight(item: HostNetwork) {
  return (item.recvBytesPerSec || 0) + (item.sentBytesPerSec || 0)
}

/** 磁盘使用率胶囊：越满越醒目，和顶部 KPI 同一套阈值 */
function diskTone(percent?: number) {
  return metricTone(numberOrNull(percent), METRIC_THRESHOLDS.disk)
}

function formatClock(date: Date) {
  return date.toLocaleTimeString('zh-CN', { hour12: false })
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
    <YdPageHeader title="资源监控" class="resource-header mb-0" main-class="min-w-0" default-class="w-full min-w-0 flex-wrap sm:w-auto">
      <template #description>
        <p class="host-meta">
          {{ hostMeta }}
        </p>
        <p v-if="lastUpdatedText" class="update-meta">
          更新于 {{ lastUpdatedText }}
          <template v-if="!refreshSeconds">
            · 自动刷新已关闭
          </template>
        </p>
      </template>
      <YdSelect v-model="hours" :options="hourOptions" class="min-w-0 w-full sm:w-[140px]" @update:model-value="loadHistory()" />
      <YdSelect v-model="refreshSeconds" :options="REFRESH_OPTIONS" class="min-w-0 w-full sm:w-[150px]" />
      <YdButton class="shrink-0" :loading="loading || historyLoading" @click="refreshAll">
        <YdIcon name="morph:refresh-cw" />
        刷新
      </YdButton>
    </YdPageHeader>

    <YdPageMain class="resource-main" main-class="min-w-0 overflow-x-hidden">
      <!-- 第一屏先给结论性数字：当前状态一眼可见，分析结论紧随其后 -->
      <YdStats class="kpi-strip">
        <YdStat
          v-for="item in stats"
          :key="item.label"
          :title="item.label"
          :description="item.hint"
        >
          <template #figure>
            <span class="kpi-icon" :data-tone="item.tone"><YdIcon :name="item.icon" /></span>
          </template>
          <template #value>
            <span class="kpi-value" :data-tone="item.tone">{{ item.value }}</span>
          </template>
          <template v-if="item.percent != null" #actions>
            <div class="kpi-bar" :data-tone="item.tone">
              <span :style="{ width: `${Math.min(100, Math.max(0, item.percent))}%` }" />
            </div>
          </template>
        </YdStat>
      </YdStats>
      <p v-if="snapshot?.notice" class="kpi-note">
        {{ snapshot.notice }}
      </p>

      <YdAlert
        v-if="snapshotError || historyError"
        variant="destructive"
        icon="morph:triangle-alert"
        title="监控数据获取失败"
        :description="`${[snapshotError, historyError].filter(Boolean).join('；')}（自动刷新仍在重试，也可点右上角刷新）`"
        class="monitor-error"
      />
      <YdCard class="analysis-panel section-card">
        <YdCollapsible :model-value="isSectionOpen('analysis')" @update:model-value="toggleSection('analysis', $event)">
          <template #trigger="{ open }">
            <div class="panel-head section-trigger">
              <span class="panel-head-title">优化分析</span>
              <YdTag :variant="severityTag(analysis.severity)">
                {{ severityLabel(analysis.severity) }}
              </YdTag>
              <span v-if="!open" class="panel-head-note" :title="analysis.headline">{{ analysis.headline }}</span>
              <span v-else class="panel-head-note">{{ analysis.findings.length ? `${analysis.findings.length} 条结论` : '暂无瓶颈' }}</span>
              <span class="panel-head-toggle">{{ open ? '收起' : '展开' }}</span>
              <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
            </div>
          </template>
          <p class="monitor-sub">
            {{ analysis.summary }}
          </p>
          <div v-if="analysis.findings.length" class="finding-list">
            <article v-for="item in analysis.findings" :key="item.id" class="finding-card">
              <div class="finding-head">
                <strong>{{ item.title }}</strong>
                <YdTag :variant="severityTag(item.severity)">
                  {{ severityLabel(item.severity) }}
                </YdTag>
              </div>
              <p>{{ item.summary }}</p>
              <p class="finding-action">
                {{ item.action }}
              </p>
            </article>
          </div>
          <p v-else class="monitor-sub">
            当前没有需要处理的资源瓶颈，趋势图可用于确认是否刚刚抖动。
          </p>
          <div class="spend-grid">
            <div>
              <div class="panel-title">
                <span>内存花在哪</span>
              </div>
              <p class="monitor-sub">
                把本机进程 RSS 和当前 JVM 内存池放在一起看：先分清是别人占内存，还是自己的堆/堆外在涨。
              </p>
              <ol v-if="analysis.memorySpenders.length" class="spend-list">
                <li v-for="item in analysis.memorySpenders" :key="`mem-${item.name}-${item.detail}`">
                  <div>
                    <strong>{{ item.name }}</strong>
                    <YdTag v-if="item.current" variant="secondary">
                      当前程序
                    </YdTag>
                  </div>
                  <span>{{ item.detail }}</span>
                  <p>{{ item.hint }}</p>
                </li>
              </ol>
              <p v-else class="monitor-sub">
                暂无进程或内存池数据
              </p>
            </div>
            <div>
              <div class="panel-title">
                <span>CPU 花在哪</span>
              </div>
              <p class="monitor-sub">
                先看本机进程，再看当前 JVM 线程。线程累计 CPU 高，可能是热点，也可能只是活得久。
              </p>
              <ol v-if="analysis.cpuSpenders.length" class="spend-list">
                <li v-for="item in analysis.cpuSpenders" :key="`cpu-${item.name}-${item.detail}`">
                  <div>
                    <strong>{{ item.name }}</strong>
                    <YdTag v-if="item.current" variant="secondary">
                      当前程序
                    </YdTag>
                  </div>
                  <span>{{ item.detail }}</span>
                  <p>{{ item.hint }}</p>
                </li>
              </ol>
              <p v-else class="monitor-sub">
                暂无进程或线程热点
              </p>
            </div>
          </div>
        </YdCollapsible>
      </YdCard>

      <div class="chart-grid">
        <YdCard v-for="chart in trendCharts" :key="chart.id">
          <template #header>
            <div class="panel-head">
              <div class="panel-head-main">
                <span class="panel-head-title">{{ chart.title }}</span>
                <p class="monitor-sub monitor-sub--clip" :title="chart.hint">
                  {{ chart.hint }}
                </p>
              </div>
              <span v-if="trendMetrics[chart.id]" class="panel-metric">{{ trendMetrics[chart.id] }}</span>
            </div>
          </template>
          <ChartWidget :dataset="chart.dataset" :height="chartHeight" />
        </YdCard>
      </div>

      <div class="monitor-grid">
        <YdCard>
          <YdCollapsible :model-value="isSectionOpen('pools')" @update:model-value="toggleSection('pools', $event)">
            <template #trigger="{ open }">
              <div class="panel-head section-trigger">
                <span class="panel-head-title">JVM 内存池</span>
                <span class="panel-head-note">{{ poolSummary }}</span>
                <span class="panel-head-toggle">{{ open ? '收起' : '展开' }}</span>
                <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
              </div>
            </template>
            <p class="monitor-sub">
              饼图按已用字节看内存花在哪一块。Heap 高看 Eden/Old，RSS 明显高于 Heap 时再看 Metaspace、Code Cache 或 Direct。
            </p>
            <ChartWidget :dataset="poolDataset" :height="chartHeight" />
            <ul class="pool-legend">
              <li v-for="pool in snapshot?.memoryPools || []" :key="pool.name">
                <strong>{{ pool.name }}</strong>
                <span>{{ formatBytes(pool.usedBytes) }} · {{ describePool(pool).hint }}</span>
              </li>
            </ul>
            <p class="monitor-sub monitor-sub--last">
              {{ gcText }}
            </p>
          </YdCollapsible>
        </YdCard>
        <YdCard>
          <YdCollapsible :model-value="isSectionOpen('disks')" @update:model-value="toggleSection('disks', $event)">
            <template #trigger="{ open }">
              <div class="panel-head section-trigger">
                <span class="panel-head-title">磁盘分区</span>
                <span class="panel-head-note" :data-tone="diskSummaryTone">{{ diskSummary }}</span>
                <span class="panel-head-toggle">{{ open ? '收起' : '展开' }}</span>
                <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
              </div>
            </template>
            <p class="monitor-sub">
              每个挂载点各自的容量。日志、上传和数据库文件通常不在同一个分区。
            </p>
            <YdResponsiveTable
              row-key="mount"
              table-root-class="rounded-md overflow-hidden"
              table-class="min-w-[640px]"

              stripe border
              :columns="diskColumns"
              :data="snapshot?.disks || []"
            >
              <template #cell-used="{ row }">
                {{ formatBytes(row.original.usedBytes) }} / {{ formatBytes(row.original.totalBytes) }}
              </template>
              <template #cell-usagePercent="{ row }">
                <span class="yd-status-tone" :data-tone="diskTone(row.original.usagePercent)">{{ percentText(row.original.usagePercent) }}</span>
              </template>
              <template #card="{ row }">
                <YdCard class="min-w-0 w-full">
                  <div class="card-row">
                    <strong>{{ row.name || row.mount }}</strong>
                    <span class="yd-status-tone" :data-tone="diskTone(row.usagePercent)">{{ percentText(row.usagePercent) }}</span>
                  </div>
                  <p class="monitor-sub">
                    {{ row.mount }} · {{ row.type || '未知类型' }}
                  </p>
                  <p class="monitor-sub">
                    {{ formatBytes(row.usedBytes) }} / {{ formatBytes(row.totalBytes) }}
                  </p>
                </YdCard>
              </template>
            </YdResponsiveTable>
          </YdCollapsible>
        </YdCard>
      </div>

      <YdCard class="section-card">
        <YdCollapsible :model-value="isSectionOpen('networks')" @update:model-value="toggleSection('networks', $event)">
          <template #trigger="{ open }">
            <div class="panel-head section-trigger">
              <span class="panel-head-title">网卡</span>
              <span class="panel-head-note">{{ networkSummary }}</span>
              <span class="panel-head-toggle">{{ open ? '收起' : '展开' }}</span>
              <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
            </div>
          </template>
          <div class="panel-note-row">
            <p class="monitor-sub">
              看具体哪块网卡在跑流量。虚拟网卡速率高不一定代表对外带宽打满；默认按实时速率排前 {{ NETWORK_TOP }} 块。
            </p>
            <YdButton
              v-if="(snapshot?.networks?.length || 0) > NETWORK_TOP"
              variant="ghost"
              size="sm"
              @click="networkExpanded = !networkExpanded"
            >
              <YdIcon :name="networkExpanded ? 'morph:chevron-up' : 'morph:chevron-down'" />
              {{ networkExpanded ? `只看前 ${NETWORK_TOP} 块` : `显示全部 ${snapshot?.networks?.length || 0} 块` }}
            </YdButton>
          </div>
          <YdResponsiveTable
            row-key="name"
            table-root-class="rounded-md overflow-hidden"
            table-class="min-w-[640px]"

            stripe border
            :columns="networkColumns"
            :data="networkRows"
          >
            <template #cell-name="{ row }">
              <span>{{ row.original.name }}</span>
              <YdTag v-if="row.original.ipv4" variant="secondary" class="ml-2">
                {{ row.original.ipv4 }}
              </YdTag>
            </template>
            <template #cell-recv="{ row }">
              {{ formatRate(row.original.recvBytesPerSec) }}
            </template>
            <template #cell-sent="{ row }">
              {{ formatRate(row.original.sentBytesPerSec) }}
            </template>
            <template #card="{ row }">
              <YdCard class="min-w-0 w-full">
                <div class="card-row">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.ipv4 || '-' }}</span>
                </div>
                <p class="monitor-sub">
                  {{ formatRate(row.recvBytesPerSec) }} ↓ / {{ formatRate(row.sentBytesPerSec) }} ↑
                </p>
              </YdCard>
            </template>
          </YdResponsiveTable>
          <p v-if="networkHiddenCount" class="monitor-sub monitor-sub--last">
            另有 {{ networkHiddenCount }} 块网卡未显示（多为虚拟网卡，速率较低）。
          </p>
        </YdCollapsible>
      </YdCard>

      <div class="monitor-grid">
        <YdCard>
          <YdCollapsible :model-value="isSectionOpen('processes')" @update:model-value="toggleSection('processes', $event)">
            <template #trigger="{ open }">
              <div class="panel-head section-trigger">
                <span class="panel-head-title">进程 RSS Top</span>
                <span class="panel-head-note">{{ processSummary }}</span>
                <span class="panel-head-toggle">{{ open ? '收起' : '展开' }}</span>
                <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
              </div>
            </template>
            <p class="monitor-sub">
              本机物理内存占用最高的进程。带「当前 JVM」的才是这个管理端；如果第一名是别的程序，先别给 Java 加堆。
            </p>
            <YdResponsiveTable
              row-key="pid"
              table-root-class="rounded-md overflow-hidden"
              table-class="min-w-[860px]"

              stripe border
              :columns="processColumns"
              :data="snapshot?.topProcesses || []"
            >
              <template #cell-name="{ row }">
                <span>{{ row.original.name }}</span>
                <YdTag v-if="row.original.currentJvm" variant="secondary" class="ml-2">
                  当前 JVM
                </YdTag>
              </template>
              <template #cell-rssBytes="{ row }">
                {{ formatBytes(row.original.rssBytes) }}
              </template>
              <template #cell-cpuPercent="{ row }">
                {{ percentText(row.original.cpuPercent) }}
              </template>
              <template #card="{ row }">
                <YdCard class="min-w-0 w-full">
                  <div class="card-row">
                    <strong>{{ row.name }} #{{ row.pid }}</strong>
                    <YdTag v-if="row.currentJvm" variant="secondary">
                      当前 JVM
                    </YdTag>
                  </div>
                  <p class="monitor-sub">
                    RSS {{ formatBytes(row.rssBytes) }} · CPU {{ percentText(row.cpuPercent) }}
                  </p>
                  <code>{{ row.commandLine || '-' }}</code>
                </YdCard>
              </template>
            </YdResponsiveTable>
          </YdCollapsible>
        </YdCard>
        <YdCard>
          <YdCollapsible :model-value="isSectionOpen('threads')" @update:model-value="toggleSection('threads', $event)">
            <template #trigger="{ open }">
              <div class="panel-head section-trigger">
                <span class="panel-head-title">线程 CPU Top</span>
                <span class="panel-head-note">{{ threadSummary }}</span>
                <span class="panel-head-toggle">{{ open ? '收起' : '展开' }}</span>
                <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
              </div>
            </template>
            <p class="monitor-sub">
              当前 JVM 里累计 CPU 最高的线程。RUNNABLE 更像计算热点；WAITING / BLOCKED 更像在等锁或 IO。
            </p>
            <YdResponsiveTable
              row-key="threadId"
              table-root-class="rounded-md overflow-hidden"
              table-class="min-w-[860px]"

              stripe border
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
                <YdCard class="min-w-0 w-full">
                  <div class="card-row">
                    <strong>{{ row.name }}</strong>
                    <span>{{ row.state || '-' }}</span>
                  </div>
                  <p class="monitor-sub">
                    CPU {{ formatDuration(row.cpuTimeMs) }}
                  </p>
                  <code>{{ stackPreview(row) }}</code>
                </YdCard>
              </template>
            </YdResponsiveTable>
          </YdCollapsible>
        </YdCard>
      </div>

      <YdCard class="glossary-panel section-card">
        <YdCollapsible v-model="glossaryOpen">
          <template #trigger="{ open }">
            <div class="panel-head glossary-trigger">
              <span class="panel-head-title">名词解释</span>
              <span class="glossary-toggle">{{ open ? '收起' : '展开全部' }}</span>
            </div>
          </template>
          <dl class="glossary-list">
            <div v-for="item in RESOURCE_GLOSSARY" :key="item.term">
              <dt>{{ item.term }}</dt>
              <dd>{{ item.meaning }}</dd>
            </div>
          </dl>
        </YdCollapsible>
        <dl v-if="!glossaryOpen" class="glossary-list glossary-preview">
          <div v-for="item in glossaryPreview" :key="item.term">
            <dt>{{ item.term }}</dt>
            <dd>{{ item.meaning }}</dd>
          </div>
        </dl>
      </YdCard>
    </YdPageMain>
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

.update-meta {
  margin: 2px 0 0;
  overflow-wrap: anywhere;
}

/* 指标卡片：默认 3 列，窄屏 2 列；卡片本体由 YdStat 提供统一表面。
 * 卡片内距与行距比 YdStat 默认略宽——六张卡铺满首屏，挤在一起最伤观感。 */
.kpi-strip {
  margin-bottom: 10px;
  gap: 14px;
}

.kpi-strip > :deep(.yd-stat) {
  flex: 1 1 30%;
  gap: 10px;
  padding: 18px 20px;
}

.kpi-strip > :deep(.yd-stat-actions) {
  margin-top: 2px;
}

.kpi-icon {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  color: var(--color-text-3);
  background: var(--color-fill-2);
  border-radius: var(--yd-control-radius);
  font-size: 16px;
}

.kpi-icon[data-tone='success'] {
  color: var(--yd-status-success);
  background: color-mix(in srgb, var(--yd-status-success) 12%, transparent);
}

.kpi-icon[data-tone='warning'] {
  color: var(--yd-status-warning);
  background: color-mix(in srgb, var(--yd-status-warning) 14%, transparent);
}

.kpi-icon[data-tone='danger'] {
  color: var(--yd-status-danger);
  background: color-mix(in srgb, var(--yd-status-danger) 12%, transparent);
}

.kpi-value[data-tone='warning'] {
  color: var(--yd-status-warning);
}

.kpi-value[data-tone='danger'] {
  color: var(--yd-status-danger);
}

.kpi-bar {
  width: 100%;
  height: 4px;
  overflow: hidden;
  background: var(--color-fill-2);
  border-radius: 999px;
}

.kpi-bar > span {
  display: block;
  height: 100%;
  background: var(--color-text-3);
  border-radius: inherit;
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.kpi-bar[data-tone='success'] > span {
  background: var(--yd-status-success);
}

.kpi-bar[data-tone='warning'] > span {
  background: var(--yd-status-warning);
}

.kpi-bar[data-tone='danger'] > span {
  background: var(--yd-status-danger);
}

.kpi-note {
  margin: 0 0 16px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.monitor-error {
  margin-bottom: 16px;
}

.monitor-sub {
  margin: 0 0 12px;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

/* 趋势卡片的说明：只留一行，完整文案走 title 悬浮，卡片头部不再被两行灰字撑高 */
.monitor-sub--clip {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  line-clamp: 1;
}

.monitor-sub--last {
  margin: 12px 0 0;
}

/* 说明文字与行尾按钮同排：按钮移出折叠触发器，避免点按钮连带折叠整段 */
.panel-note-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.panel-note-row .monitor-sub {
  margin: 0 0 12px;
}

.panel-head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

/* 折叠分区头：整行可点，标题在左、关键数字在右、箭头最后 */
.section-trigger {
  align-items: center;
  width: 100%;
  cursor: pointer;
  color: var(--color-text-1);
}

.panel-head-main {
  min-width: 0;
}

.panel-head-main .monitor-sub {
  margin: 4px 0 0;
}

.panel-head-title {
  flex: none;
  color: var(--color-text-1);
  font-weight: 600;
  overflow-wrap: anywhere;
}

/* 趋势卡片右上角的读数：当前 / 均 / 峰，避免只看曲线被抖动带偏 */
.panel-metric {
  flex: none;
  color: var(--color-text-2);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.panel-head-note,
.panel-head-toggle {
  margin-left: auto;
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
}

/* 折叠头里的关键数字：一行截断，完整值走 title */
.panel-head-note {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-head-note[data-tone='warning'] {
  color: var(--yd-status-warning);
}

.panel-head-note[data-tone='danger'] {
  color: var(--yd-status-danger);
}

.panel-head-toggle {
  flex: none;
  margin-left: 0;
}

.section-card,
.analysis-panel,
.glossary-panel {
  margin-bottom: 16px;
}

.chart-grid,
.monitor-grid,
.spend-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
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

/* 卡片内的小块（结论 / 花费清单 / 内存池图例 / 名词）：用淡底分组即可，
 * 不再各套一圈描边——卡片套卡片描边会让整页显得又挤又碎。 */
.finding-card,
.spend-list li,
.pool-legend li,
.glossary-list > div {
  min-width: 0;
  padding: 14px 16px;
  background: var(--color-fill-1);
  border-radius: 8px;
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
  .glossary-preview,
  .glossary-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  /* 窄屏指标卡两列即可：一列会把六张卡拉成很长的一条 */
  .kpi-strip > :deep(.yd-stat) {
    flex: 1 1 45%;
  }
}

@media (min-width: 768px) {
  .finding-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (min-width: 1180px) {
  .chart-grid,
  .monitor-grid,
  .spend-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
