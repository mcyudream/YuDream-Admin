import type {
  HostProcess,
  HostResourceSnapshot,
  HostThreadHotspot,
  JvmGc,
  JvmMemoryPool,
  ResourceMetricPoint,
} from '@/api/modules/system-monitor'

export type AnalysisSeverity = 'ok' | 'watch' | 'warn'

export interface AnalysisFinding {
  id: string
  severity: AnalysisSeverity
  title: string
  summary: string
  action: string
}

export interface SpendItem {
  name: string
  detail: string
  hint: string
  current?: boolean
}

export interface ResourceAnalysis {
  severity: AnalysisSeverity
  headline: string
  summary: string
  findings: AnalysisFinding[]
  memorySpenders: SpendItem[]
  cpuSpenders: SpendItem[]
}

export const RESOURCE_GLOSSARY = [
  { term: 'CPU 使用率', meaning: '整台机器当前有多少计算能力正在被占用。接近 100% 说明处理器已经很忙，新请求会变慢。' },
  { term: '负载', meaning: '最近 1/5/15 分钟里排队等待 CPU 的任务量。数值明显高于逻辑核数时，说明机器在排队而不是刚好用满。' },
  { term: '主机内存', meaning: '操作系统看到的物理内存占用，包含本程序和其他进程。高了不一定是当前 Java 程序的问题。' },
  { term: '程序内存 / Heap', meaning: '当前 JVM 堆内存 used / max。这是 Java 对象真正存放的地方，也是程序内存使用率的主要口径。' },
  { term: 'RSS', meaning: '进程实际占用的物理内存，包含堆、堆外、线程栈和本地库。通常会比 Heap 更大。' },
  { term: 'Non-Heap', meaning: '堆以外的 JVM 内存，常见是类元数据、JIT 代码和直接缓冲区，不会被普通 GC 回收对象。' },
  { term: 'Metaspace', meaning: '存放类的元数据。持续上涨通常意味着类加载过多或没有卸载，例如频繁热加载插件。' },
  { term: 'Code Cache', meaning: 'JIT 编译后的机器码。占满后编译会停，吞吐可能下降。' },
  { term: 'Direct Buffer', meaning: '堆外直接内存，常被网络、文件和 NIO 使用。Heap 不高但 RSS 很高时要看这里。' },
  { term: 'GC', meaning: '垃圾回收。次数多、停顿长说明对象创建太快或堆偏小，表现为 CPU 升高、接口变慢。' },
  { term: '磁盘使用率', meaning: '各分区已用容量占比。空间紧张时日志、上传和数据库都可能写失败。' },
  { term: '网络速率', meaning: '当前网卡每秒收发的数据量，用来判断是不是在大量传输，而不是累计流量。' },
  { term: '线程状态', meaning: 'RUNNABLE 表示正在干活；BLOCKED / WAITING / TIMED_WAITING 表示在等锁或等待，不一定是热点。' },
]

export function analyzeResource(
  snapshot: HostResourceSnapshot | null,
  history: ResourceMetricPoint[],
): ResourceAnalysis {
  if (!snapshot) {
    return {
      severity: 'ok',
      headline: '还没有采集到数据',
      summary: '打开页面后会自动拉取实时快照。有数据后会结合 CPU、内存、进程和线程给出优化方向。',
      findings: [],
      memorySpenders: [],
      cpuSpenders: [],
    }
  }

  const cpu = snapshot.cpuUsagePercent
  const hostMemory = percentOf(snapshot.memoryUsedBytes, snapshot.memoryTotalBytes) ?? snapshot.memoryUsagePercent
  const heap = percentOf(snapshot.jvmHeapUsedBytes, snapshot.jvmHeapMaxBytes)
  const disk = percentOf(snapshot.diskUsedBytes, snapshot.diskTotalBytes) ?? snapshot.diskUsagePercent
  const swap = percentOf(snapshot.swapUsedBytes, snapshot.swapTotalBytes)
  const currentJvm = (snapshot.topProcesses || []).find(item => item.currentJvm)
  const hottestProcess = [...(snapshot.topProcesses || [])].sort((a, b) => (b.rssBytes || 0) - (a.rssBytes || 0))[0]
  const hottestThread = [...(snapshot.topThreads || [])].sort((a, b) => (b.cpuTimeMs || 0) - (a.cpuTimeMs || 0))[0]
  const hottestPool = [...(snapshot.memoryPools || [])].sort((a, b) => (b.usedBytes || 0) - (a.usedBytes || 0))[0]
  const gcShare = gcTimeShare(snapshot.garbageCollectors || [], snapshot.jvmUptimeMs)
  const avgCpu = average(history.map(point => point.cpuUsagePercent))
  const avgHeap = average(history.map(point => percentOf(point.jvmHeapUsedBytes, point.jvmHeapMaxBytes)))
  const heapRising = isRising(history.map(point => percentOf(point.jvmHeapUsedBytes, point.jvmHeapMaxBytes)))
  const findings: AnalysisFinding[] = []

  pushFinding(findings, cpuFinding(cpu, avgCpu, hottestThread, hottestProcess, snapshot.cpuLogicalCount))
  pushFinding(findings, heapFinding(heap, avgHeap, heapRising, hottestPool, snapshot))
  pushFinding(findings, rssFinding(snapshot, currentJvm, hottestProcess, heap))
  pushFinding(findings, hostMemoryFinding(hostMemory, swap, hottestProcess, currentJvm))
  pushFinding(findings, diskFinding(disk, snapshot))
  pushFinding(findings, gcFinding(gcShare, snapshot.garbageCollectors || []))
  pushFinding(findings, threadFinding(hottestThread, snapshot.jvmThreadCount))

  const severity = worstSeverity(findings)
  return {
    severity,
    headline: buildHeadline(severity, findings, currentJvm, hottestProcess),
    summary: buildSummary(snapshot, findings, currentJvm, hottestProcess, hottestThread, hottestPool),
    findings,
    memorySpenders: memorySpenders(snapshot, currentJvm),
    cpuSpenders: cpuSpenders(snapshot, currentJvm),
  }
}

export function describePool(pool: JvmMemoryPool) {
  const name = pool.name || '未知内存池'
  return {
    title: name,
    hint: poolHint(name, pool.type),
  }
}

export function severityLabel(severity: AnalysisSeverity) {
  if (severity === 'warn') {
    return '需要关注'
  }
  if (severity === 'watch') {
    return '建议观察'
  }
  return '目前平稳'
}

export function severityTag(severity: AnalysisSeverity): 'destructive' | 'outline' | 'secondary' {
  if (severity === 'warn') {
    return 'destructive'
  }
  if (severity === 'watch') {
    return 'outline'
  }
  return 'secondary'
}

function cpuFinding(
  cpu: number | undefined,
  avgCpu: number | undefined,
  hottestThread: HostThreadHotspot | undefined,
  hottestProcess: HostProcess | undefined,
  logicalCount?: number,
): AnalysisFinding | null {
  const current = cpu ?? avgCpu
  if (current == null) {
    return null
  }
  if (current < 70 && (avgCpu == null || avgCpu < 65)) {
    return null
  }
  const threadHint = hottestThread?.name
    ? `累计 CPU 最高的线程是「${hottestThread.name}」${hottestThread.state ? `，状态 ${hottestThread.state}` : ''}。`
    : '可以打开线程列表看谁一直在跑。'
  const processHint = hottestProcess && !hottestProcess.currentJvm
    ? `本机 RSS 最高的进程是 ${processTitle(hottestProcess)}，不一定是当前 Java 程序。`
    : '如果高占用集中在当前 JVM，优先查业务线程和定时任务。'
  const loadHint = logicalCount
    ? `这台机器有 ${logicalCount} 个逻辑核，CPU 长时间高于 85% 时新请求会开始排队。`
    : ''
  return {
    id: 'cpu',
    severity: current >= 85 || (avgCpu != null && avgCpu >= 80) ? 'warn' : 'watch',
    title: current >= 85 ? 'CPU 已经比较满' : 'CPU 占用偏高',
    summary: `当前 CPU ${formatPercent(cpu)}，所选时间窗均值 ${formatPercent(avgCpu)}。${loadHint}`,
    action: `${threadHint} ${processHint}`,
  }
}

function heapFinding(
  heap: number | undefined,
  avgHeap: number | undefined,
  rising: boolean,
  hottestPool: JvmMemoryPool | undefined,
  snapshot: HostResourceSnapshot,
): AnalysisFinding | null {
  if (heap == null) {
    return null
  }
  if (heap < 75 && !rising) {
    return null
  }
  const poolText = hottestPool
    ? `当前最大的 JVM 内存池是「${hottestPool.name}」（${poolHint(hottestPool.name, hottestPool.type)}），已用 ${formatBytes(hottestPool.usedBytes)}。`
    : '可以看 JVM 内存池确认对象主要堆在哪一代。'
  const trend = rising ? '最近一段时间 Heap 还在抬升，不像短暂抖动。' : '目前是高位运行，先观察是否伴随 GC 变慢。'
  return {
    id: 'heap',
    severity: heap >= 90 ? 'warn' : 'watch',
    title: heap >= 90 ? '程序堆内存快用满' : '程序堆内存占用偏高',
    summary: `Heap ${formatBytes(snapshot.jvmHeapUsedBytes)} / ${formatBytes(snapshot.jvmHeapMaxBytes)}，使用率 ${formatPercent(heap)}，时间窗均值 ${formatPercent(avgHeap)}。${trend}`,
    action: `${poolText} 若接口变慢，优先查大对象、缓存膨胀或 -Xmx 是否偏小，而不是先加机器内存。`,
  }
}

function rssFinding(
  snapshot: HostResourceSnapshot,
  currentJvm: HostProcess | undefined,
  hottestProcess: HostProcess | undefined,
  heap?: number,
): AnalysisFinding | null {
  if (!currentJvm?.rssBytes || !snapshot.jvmHeapUsedBytes) {
    return null
  }
  const rss = currentJvm.rssBytes
  const heapUsed = snapshot.jvmHeapUsedBytes
  if (rss <= heapUsed * 1.8 || rss - heapUsed < 256 * 1024 * 1024) {
    return null
  }
  const nativeHint = snapshot.jvmDirectUsedBytes
    ? `Direct Buffer 约 ${formatBytes(snapshot.jvmDirectUsedBytes)}，Non-Heap 约 ${formatBytes(snapshot.jvmNonHeapUsedBytes)}。`
    : `Non-Heap 约 ${formatBytes(snapshot.jvmNonHeapUsedBytes)}。`
  const other = hottestProcess && !hottestProcess.currentJvm
    ? `同时本机还有 ${processTitle(hottestProcess)} 占用 ${formatBytes(hottestProcess.rssBytes)}。`
    : '当前进程就是本机 RSS 最高的之一。'
  return {
    id: 'rss',
    severity: rss > heapUsed * 3 ? 'warn' : 'watch',
    title: '进程实际内存比 Java 堆大不少',
    summary: `当前 JVM RSS ${formatBytes(rss)}，Heap 只有 ${formatBytes(heapUsed)}（${formatPercent(heap)}）。说明还有堆外内存、线程栈或本地库。${nativeHint}`,
    action: `${other} 如果 Heap 并不高，不要只调 -Xmx；应检查直接内存、线程数、插件本地库或文件缓存。`,
  }
}

function hostMemoryFinding(
  hostMemory: number | undefined,
  swap: number | undefined,
  hottestProcess: HostProcess | undefined,
  currentJvm: HostProcess | undefined,
): AnalysisFinding | null {
  if ((hostMemory == null || hostMemory < 80) && (swap == null || swap < 20)) {
    return null
  }
  const otherProcess = hottestProcess && !hottestProcess.currentJvm
  return {
    id: 'host-memory',
    severity: (hostMemory != null && hostMemory >= 90) || (swap != null && swap >= 40) ? 'warn' : 'watch',
    title: otherProcess ? '主机内存偏高，主要不在当前程序' : '主机物理内存偏高',
    summary: `主机内存 ${formatPercent(hostMemory)}${swap ? `，交换分区 ${formatPercent(swap)}` : ''}。${
      otherProcess
        ? `RSS 最高的是 ${processTitle(hottestProcess)}（${formatBytes(hottestProcess.rssBytes)}），当前 JVM 是 ${formatBytes(currentJvm?.rssBytes)}。`
        : `当前 JVM RSS ${formatBytes(currentJvm?.rssBytes)}，已经是本机主要占用者之一。`
    }`,
    action: otherProcess
      ? '先确认是不是数据库、浏览器、其他 Java 服务把机器吃满。给当前程序加内存前，先看整机还有没有被别人占住。'
      : '可以结合 Heap / RSS 判断是 Java 堆膨胀还是整机内存不够。Swap 升高时优先减少内存占用，避免来回换页。',
  }
}

function diskFinding(disk: number | undefined, snapshot: HostResourceSnapshot): AnalysisFinding | null {
  const hottest = [...(snapshot.disks || [])].sort((a, b) => (b.usagePercent || 0) - (a.usagePercent || 0))[0]
  const value = hottest?.usagePercent ?? disk
  if (value == null || value < 80) {
    return null
  }
  return {
    id: 'disk',
    severity: value >= 90 ? 'warn' : 'watch',
    title: value >= 90 ? '磁盘空间已经很紧' : '磁盘占用偏高',
    summary: hottest
      ? `${hottest.mount || hottest.name || '某个分区'} 已用 ${formatPercent(hottest.usagePercent)}（${formatBytes(hottest.usedBytes)} / ${formatBytes(hottest.totalBytes)}）。`
      : `磁盘总体使用率 ${formatPercent(disk)}。`,
    action: '先清理日志、备份和上传目录。空间耗尽时应用会突然无法写文件，看起来会像程序卡死。',
  }
}

function gcFinding(share: number | undefined, collectors: JvmGc[]): AnalysisFinding | null {
  if (share == null || share < 5) {
    return null
  }
  const detail = collectors.map(item => `${item.name || 'GC'} ${item.collectionCount ?? 0} 次 / ${formatDuration(item.collectionTimeMs)}`).join('，')
  return {
    id: 'gc',
    severity: share >= 10 ? 'warn' : 'watch',
    title: '垃圾回收占用了不少运行时间',
    summary: `GC 累计耗时约占进程运行时间的 ${formatPercent(share)}。${detail}`,
    action: '先看 Heap 是否长期贴近上限，以及是否有频繁 Full GC。常见原因是缓存过大、对象创建太快或堆设置不合理。',
  }
}

function threadFinding(hottestThread: HostThreadHotspot | undefined, threadCount?: number): AnalysisFinding | null {
  if (!hottestThread?.name) {
    return null
  }
  const busy = hottestThread.state === 'RUNNABLE' && (hottestThread.cpuTimeMs || 0) > 10_000
  const waiting = hottestThread.state === 'BLOCKED' || hottestThread.state === 'WAITING' || hottestThread.state === 'TIMED_WAITING'
  if (!busy && !(waiting && (threadCount || 0) > 300)) {
    return null
  }
  const stack = (hottestThread.stackTop || []).slice(0, 2).join(' / ')
  return {
    id: 'thread',
    severity: busy ? 'watch' : 'watch',
    title: busy ? `CPU 热点线程：${hottestThread.name}` : `线程数量偏多，热点线程在等待`,
    summary: `线程 ${hottestThread.name} 状态 ${hottestThread.state || '-'}，累计 CPU ${formatDuration(hottestThread.cpuTimeMs)}。当前 JVM 共 ${threadCount ?? '-'} 条线程。`,
    action: stack
      ? `栈顶接近：${stack}。若是业务线程，优先查对应接口或定时任务；若是 GC / 编译线程，回到堆内存和 JIT 配置。`
      : '结合线程列表判断是计算热点还是锁等待。线程特别多时还要检查线程池有没有泄漏。',
  }
}

function memorySpenders(snapshot: HostResourceSnapshot, currentJvm?: HostProcess): SpendItem[] {
  const processItems = (snapshot.topProcesses || []).slice(0, 5).map(item => ({
    name: processTitle(item),
    detail: formatBytes(item.rssBytes),
    hint: item.currentJvm
      ? `当前 Java 进程，Heap ${formatBytes(snapshot.jvmHeapUsedBytes)}，RSS 还包含堆外内存。`
      : '其他进程占用的物理内存，不是当前管理端自己的堆。',
    current: item.currentJvm,
  }))
  const poolItems = (snapshot.memoryPools || [])
    .slice()
    .sort((a, b) => (b.usedBytes || 0) - (a.usedBytes || 0))
    .slice(0, 4)
    .map(pool => ({
      name: pool.name || '内存池',
      detail: formatBytes(pool.usedBytes),
      hint: poolHint(pool.name, pool.type),
      current: true,
    }))
  if (currentJvm) {
    return [...processItems.slice(0, 3), ...poolItems]
  }
  return [...processItems, ...poolItems].slice(0, 8)
}

function cpuSpenders(snapshot: HostResourceSnapshot, currentJvm?: HostProcess): SpendItem[] {
  const processItems = [...(snapshot.topProcesses || [])]
    .sort((a, b) => (b.cpuPercent || 0) - (a.cpuPercent || 0))
    .slice(0, 3)
    .map(item => ({
      name: processTitle(item),
      detail: formatPercent(item.cpuPercent),
      hint: item.currentJvm ? '当前 JVM 的累计 CPU 占比。' : '其他进程的累计 CPU，用来判断机器是不是被别人占满。',
      current: item.currentJvm,
    }))
  const threadItems = (snapshot.topThreads || []).slice(0, 4).map(item => ({
    name: item.name || `线程 ${item.threadId || '-'}`,
    detail: formatDuration(item.cpuTimeMs),
    hint: item.state === 'RUNNABLE'
      ? '正在运行，更像计算热点。'
      : `${item.state || '未知状态'}，累计 CPU 高也可能只是活得久。`,
    current: true,
  }))
  if (!currentJvm && processItems.length === 0) {
    return threadItems
  }
  return [...processItems, ...threadItems]
}

function buildHeadline(
  severity: AnalysisSeverity,
  findings: AnalysisFinding[],
  currentJvm?: HostProcess,
  hottestProcess?: HostProcess,
) {
  if (findings.length === 0) {
    return '当前资源使用比较平稳，没有明显瓶颈'
  }
  if (findings[0]?.id === 'host-memory' && hottestProcess && !hottestProcess.currentJvm) {
    return `机器内存主要花在 ${processTitle(hottestProcess)}，不完全是当前程序`
  }
  if (findings[0]?.id === 'rss' && currentJvm) {
    return '当前程序实际占用的物理内存明显高于 Java 堆'
  }
  return findings[0]?.title || severityLabel(severity)
}

function buildSummary(
  snapshot: HostResourceSnapshot,
  findings: AnalysisFinding[],
  currentJvm?: HostProcess,
  hottestProcess?: HostProcess,
  hottestThread?: HostThreadHotspot,
  hottestPool?: JvmMemoryPool,
) {
  if (findings.length === 0) {
    return `CPU ${formatPercent(snapshot.cpuUsagePercent)}，主机内存 ${formatPercent(snapshot.memoryUsagePercent)}，程序 Heap ${formatPercent(percentOf(snapshot.jvmHeapUsedBytes, snapshot.jvmHeapMaxBytes))}。下面的趋势图用来确认是不是刚刚抖动。`
  }
  const parts = [
    currentJvm ? `当前 JVM RSS ${formatBytes(currentJvm.rssBytes)}` : '',
    hottestProcess && !hottestProcess.currentJvm ? `本机内存最高进程是 ${processTitle(hottestProcess)}` : '',
    hottestPool ? `JVM 最大内存池是 ${hottestPool.name}` : '',
    hottestThread?.name ? `线程热点是 ${hottestThread.name}` : '',
  ].filter(Boolean)
  return `${findings[0]?.summary} ${parts.join('；')}。`
}

function poolHint(name?: string, type?: string) {
  const text = `${name || ''} ${type || ''}`.toLowerCase()
  if (text.includes('metaspace')) {
    return '类的元数据。涨得快通常和类加载、热更新有关。'
  }
  if (text.includes('code')) {
    return 'JIT 编译后的代码缓存。'
  }
  if (text.includes('compressed class')) {
    return '压缩类空间，和 Metaspace 一起看。'
  }
  if (text.includes('eden') || text.includes('survivor') || text.includes('young')) {
    return '年轻代，短生命周期对象会先堆在这里。'
  }
  if (text.includes('old') || text.includes('tenured') || text.includes('old gen')) {
    return '老年代，长期存活对象和缓存容易堆在这里。'
  }
  if (text.includes('direct') || text.includes('buffer')) {
    return '堆外直接内存，网络和文件 IO 常用。'
  }
  if (type === 'HEAP' || text.includes('heap') || text.includes('g1')) {
    return 'Java 堆的一部分，影响程序内存使用率。'
  }
  if (type === 'NON_HEAP') {
    return '非堆内存，不直接等于业务对象占用。'
  }
  return 'JVM 内存池的一块区域，用来看内存具体花在哪。'
}

function processTitle(process?: HostProcess) {
  if (!process) {
    return '未知进程'
  }
  return `${process.name || '进程'}${process.pid != null ? ` #${process.pid}` : ''}`
}

function gcTimeShare(collectors: JvmGc[], uptimeMs?: number) {
  if (!uptimeMs || uptimeMs <= 0) {
    return undefined
  }
  const total = collectors.reduce((sum, item) => sum + (item.collectionTimeMs || 0), 0)
  if (total <= 0) {
    return undefined
  }
  return (total / uptimeMs) * 100
}

function isRising(values: Array<number | undefined>) {
  const numbers = values.filter((item): item is number => item != null)
  if (numbers.length < 8) {
    return false
  }
  const split = Math.floor(numbers.length / 3)
  const head = average(numbers.slice(0, split))
  const tail = average(numbers.slice(-split))
  return head != null && tail != null && tail - head >= 12
}

function average(values: Array<number | undefined>) {
  const numbers = values.filter((item): item is number => item != null && !Number.isNaN(item))
  if (numbers.length === 0) {
    return undefined
  }
  return numbers.reduce((sum, item) => sum + item, 0) / numbers.length
}

function worstSeverity(findings: AnalysisFinding[]): AnalysisSeverity {
  if (findings.some(item => item.severity === 'warn')) {
    return 'warn'
  }
  if (findings.some(item => item.severity === 'watch')) {
    return 'watch'
  }
  return 'ok'
}

function pushFinding(findings: AnalysisFinding[], finding: AnalysisFinding | null) {
  if (finding) {
    findings.push(finding)
  }
}

function percentOf(used?: number, total?: number) {
  if (used == null || total == null || total <= 0) {
    return undefined
  }
  return (used / total) * 100
}

function formatPercent(value?: number) {
  return value == null ? '-' : `${Number(value).toFixed(1)}%`
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

function formatDuration(ms?: number) {
  if (!ms) {
    return '-'
  }
  const total = Math.floor(ms / 1000)
  const hoursPart = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  if (hoursPart > 48) {
    return `${Math.floor(hoursPart / 24)} 天`
  }
  if (hoursPart > 0) {
    return `${hoursPart} 小时 ${minutes} 分`
  }
  if (minutes > 0) {
    return `${minutes} 分 ${seconds} 秒`
  }
  return `${seconds} 秒`
}
