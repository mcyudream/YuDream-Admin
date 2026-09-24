<script setup lang="ts">
import type { YdGraphEdge, YdGraphNode, YdGraphNodeEvent } from '@yudream/components'
import type {
  AboutLatestVersion,
  AboutOverview,
  PluginGraph,
  PluginGraphNode,
  PluginGraphNodeStatus,
} from '@/api/modules/system-about'
import { YdGraphCanvas } from '@yudream/components'
import apiAbout from '@/api/modules/system-about'

defineOptions({ name: 'SystemAbout' })

const toast = useFaToast()

const overview = ref<AboutOverview | null>(null)
const overviewLoading = ref(false)

const latestEntries = ref<AboutLatestVersion[]>([])
const latestEnabled = ref(true)
const latestLoading = ref(false)

const graph = ref<PluginGraph | null>(null)
const graphLoading = ref(false)
const selectedCode = ref<string | null>(null)

/** 本地构建的契约包当前版本：构建期从各包 package.json 注入（workspace 依赖拿不到真实版本） */
const localPackageVersions = __YUDREAM_PACKAGE_VERSIONS__

interface VersionRow {
  key: string
  name: string
  kind: string
  current: string
  /** 无上游发布源可探测（框架本体） */
  localOnly?: boolean
}

const versionRows = computed<VersionRow[]>(() => [
  {
    key: 'framework',
    name: 'YuDream Admin 框架',
    kind: '本地构建',
    // 版本由 Maven 资源过滤注入；IDE 直接从源码运行时该文件是未解析的占位符，按「源码运行」呈现
    current: overview.value?.framework.version || '源码运行（未注入构建版本）',
    localOnly: true,
  },
  { key: 'spi', name: 'Plugin SPI（插件契约）', kind: 'Maven', current: overview.value?.spi.version || '' },
  { key: 'plugin-sdk', name: '@yudream/plugin-sdk', kind: 'npm', current: localPackageVersions['plugin-sdk'] },
  { key: 'components', name: '@yudream/components', kind: 'npm', current: localPackageVersions.components },
  { key: 'dataviz', name: '@yudream/dataviz', kind: 'npm', current: localPackageVersions.dataviz },
])

const latestByKey = computed(() => new Map(latestEntries.value.map(entry => [entry.key, entry])))

/** 宽松 SemVer 比较：只比较数字段与预发布标记的存在性，够用于「可更新」提示 */
function compareVersions(left: string, right: string): number {
  const parse = (value: string) => {
    const match = value.trim().replace(/^v/, '').match(/^(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Z.-]+))?/i)
    if (!match) {
      return null
    }
    return { nums: [Number(match[1]), Number(match[2]), Number(match[3])], prerelease: match[4] ?? '' }
  }
  const a = parse(left)
  const b = parse(right)
  if (!a || !b) {
    return 0
  }
  for (let index = 0; index < 3; index++) {
    if (a.nums[index] !== b.nums[index]) {
      return a.nums[index] - b.nums[index]
    }
  }
  if (a.prerelease === b.prerelease) {
    return 0
  }
  return a.prerelease === '' ? 1 : -1
}

type VersionState = 'loading' | 'disabled' | 'local' | 'unknown' | 'error' | 'latest' | 'outdated' | 'ahead'

function versionState(row: VersionRow): { state: VersionState, text: string, variant: 'default' | 'secondary' | 'destructive' | 'outline' } {
  if (row.localOnly) {
    return { state: 'local', text: '本地构建', variant: 'secondary' }
  }
  if (!latestEnabled.value) {
    return { state: 'disabled', text: '探测已关闭', variant: 'outline' }
  }
  if (latestLoading.value && latestEntries.value.length === 0) {
    return { state: 'loading', text: '探测中…', variant: 'outline' }
  }
  const entry = latestByKey.value.get(row.key)
  if (!entry) {
    return { state: 'unknown', text: '未探测', variant: 'outline' }
  }
  if (entry.error || !entry.latest) {
    return { state: 'error', text: '获取失败', variant: 'destructive' }
  }
  if (!row.current) {
    return { state: 'unknown', text: `最新 ${entry.latest}`, variant: 'outline' }
  }
  const diff = compareVersions(row.current, entry.latest)
  if (diff < 0) {
    return { state: 'outdated', text: `可更新至 ${entry.latest}`, variant: 'default' }
  }
  if (diff > 0) {
    return { state: 'ahead', text: '领先发布源', variant: 'secondary' }
  }
  return { state: 'latest', text: '已是最新', variant: 'secondary' }
}

/** sigma 画布在 WebGL 里渲染，CSS var() 需在挂载后解析成具体颜色 */
function resolveCssColor(expr: string): string {
  const probe = document.createElement('div')
  probe.style.color = expr
  probe.style.display = 'none'
  document.body.appendChild(probe)
  const resolved = getComputedStyle(probe).color
  probe.remove()
  return resolved
}

interface GraphPalette {
  enabled: string
  loaded: string
  idle: string
  error: string
  edgeHard: string
  edgeSoft: string
}

function buildPalette(): GraphPalette {
  return {
    enabled: resolveCssColor('rgb(var(--success-6))'),
    loaded: resolveCssColor('var(--color-text-2)'),
    idle: resolveCssColor('var(--color-text-4)'),
    error: resolveCssColor('rgb(var(--danger-6))'),
    edgeHard: resolveCssColor('var(--color-text-3)'),
    edgeSoft: resolveCssColor('var(--color-text-4)'),
  }
}

const palette = ref<GraphPalette | null>(null)

const STATUS_TEXT: Record<PluginGraphNodeStatus, string> = {
  ENABLED: '已启用',
  LOADED: '已加载',
  INSTALLED: '已安装',
  DISABLED: '已禁用',
  ERROR: '异常',
  MISSING: '缺失',
}

function nodeColor(status: PluginGraphNodeStatus): string {
  const colors = palette.value
  if (!colors) {
    return '#888888'
  }
  if (status === 'ENABLED') {
    return colors.enabled
  }
  if (status === 'ERROR' || status === 'MISSING') {
    return colors.error
  }
  if (status === 'LOADED') {
    return colors.loaded
  }
  return colors.idle
}

/**
 * 同心环布局（确定性，同一份数据每次同一张图）：
 * 内圈 = 不依赖任何插件的「基础插件」（被依赖方），向外逐层是依赖层级更深的插件，
 * 最外圈放与依赖无关的独立插件；x 轴做椭圆拉伸以贴合宽屏画布，避免圆形被高度限制压小。
 */
function computeRingLayout(nodes: string[], edges: { source: string, target: string }[]) {
  const outgoing = new Map<string, string[]>()
  const connected = new Set<string>()
  for (const edge of edges) {
    const list = outgoing.get(edge.source) ?? []
    list.push(edge.target)
    outgoing.set(edge.source, list)
    connected.add(edge.source)
    connected.add(edge.target)
  }
  // 依赖深度 = 到最远被依赖方的最长链路（无依赖者为 0）；迭代松弛，天然容忍数据里的环
  const depth = new Map<string, number>(nodes.map(code => [code, 0]))
  for (let pass = 0; pass < nodes.length; pass++) {
    let changed = false
    for (const code of nodes) {
      for (const target of outgoing.get(code) ?? []) {
        const candidate = (depth.get(target) ?? 0) + 1
        if ((depth.get(code) ?? 0) < candidate) {
          depth.set(code, candidate)
          changed = true
        }
      }
    }
    if (!changed) {
      break
    }
  }
  const rings = new Map<number, string[]>()
  for (const code of [...nodes].sort()) {
    const key = connected.has(code) ? (depth.get(code) ?? 0) : -1
    const members = rings.get(key) ?? []
    members.push(code)
    rings.set(key, members)
  }
  const depthKeys = [...rings.keys()].filter(key => key >= 0).sort((a, b) => a - b)
  const indexOfRing = new Map(depthKeys.map((key, index) => [key, index]))
  const positions = new Map<string, { x: number, y: number }>()
  for (const [key, members] of rings) {
    const ringIndex = key === -1 ? depthKeys.length : (indexOfRing.get(key) ?? 0)
    const radius = 130 + ringIndex * 115
    // 每环错开起始角，避免相邻环的节点完全径向对齐、连线叠在一起
    const angleOffset = ringIndex * 0.35
    members.forEach((code, index) => {
      const angle = (2 * Math.PI * index) / members.length + angleOffset
      positions.set(code, {
        x: Math.cos(angle) * radius * 1.5,
        y: Math.sin(angle) * radius,
      })
    })
  }
  return positions
}

/** 节点尺寸按关联度数放大，hub 更醒目；标签只给枢纽与异常节点，避免密集处糊成一片 */
const graphNodes = computed<YdGraphNode[]>(() => {
  if (!graph.value) {
    return []
  }
  const edges = graph.value.edges
  const degree = new Map<string, number>()
  for (const edge of edges) {
    degree.set(edge.source, (degree.get(edge.source) ?? 0) + 1)
    degree.set(edge.target, (degree.get(edge.target) ?? 0) + 1)
  }
  const positions = computeRingLayout(graph.value.nodes.map(node => node.code), edges)
  return graph.value.nodes.map((node) => {
    const nodeDegree = degree.get(node.code) ?? 0
    const position = positions.get(node.code) ?? { x: 0, y: 0 }
    return {
      id: node.code,
      x: position.x,
      y: position.y,
      label: node.name || node.code,
      size: Math.min(10 + nodeDegree * 3, 26),
      color: nodeColor(node.status),
      forceLabel: node.status === 'ERROR' || node.status === 'MISSING' || nodeDegree >= 5,
      data: {
        subLabel: node.version ? `v${node.version}` : node.status,
        clusterLabel: STATUS_TEXT[node.status] ?? node.status,
      },
    }
  })
})

const graphEdges = computed<YdGraphEdge[]>(() => {
  if (!graph.value) {
    return []
  }
  const colors = palette.value
  return graph.value.edges.map(edge => ({
    id: `${edge.source}->${edge.target}:${edge.kind}`,
    source: edge.source,
    target: edge.target,
    type: 'arrow',
    label: edge.kind === 'HARD' ? 'depend' : 'softdepend',
    size: edge.kind === 'HARD' ? 2 : 1,
    color: edge.kind === 'HARD' ? colors?.edgeHard : colors?.edgeSoft,
    data: { kind: edge.kind },
  }))
})

const nodeByCode = computed(() => new Map((graph.value?.nodes ?? []).map(node => [node.code, node])))

const selectedNode = computed<PluginGraphNode | null>(() => {
  if (!selectedCode.value) {
    return null
  }
  return nodeByCode.value.get(selectedCode.value) ?? null
})

const selectedRelations = computed(() => {
  if (!selectedCode.value || !graph.value) {
    return { dependsOn: [] as string[], dependedBy: [] as string[] }
  }
  const dependsOn: string[] = []
  const dependedBy: string[] = []
  for (const edge of graph.value.edges) {
    if (edge.source === selectedCode.value) {
      dependsOn.push(`${edge.target}${edge.kind === 'SOFT' ? '（软）' : ''}`)
    }
    if (edge.target === selectedCode.value) {
      dependedBy.push(`${edge.source}${edge.kind === 'SOFT' ? '（软）' : ''}`)
    }
  }
  return { dependsOn, dependedBy }
})

function onGraphNodeClick(payload: YdGraphNodeEvent) {
  selectedCode.value = payload.node
}

function onGraphStageClick() {
  selectedCode.value = null
}

async function loadOverview() {
  overviewLoading.value = true
  try {
    const res = await apiAbout.overview()
    overview.value = res.data
  }
  finally {
    overviewLoading.value = false
  }
}

async function loadLatest() {
  latestLoading.value = true
  try {
    const res = await apiAbout.latest()
    latestEnabled.value = res.data.enabled
    latestEntries.value = res.data.entries ?? []
  }
  catch {
    latestEntries.value = []
    toast.error('错误', { description: '最新版本探测失败，请稍后在页面内重试' })
  }
  finally {
    latestLoading.value = false
  }
}

async function loadGraph() {
  graphLoading.value = true
  try {
    const res = await apiAbout.pluginGraph()
    graph.value = res.data
    selectedCode.value = null
  }
  finally {
    graphLoading.value = false
  }
}

function statusVariant(status: PluginGraphNodeStatus): 'default' | 'secondary' | 'destructive' {
  if (status === 'ENABLED') {
    return 'default'
  }
  if (status === 'ERROR' || status === 'MISSING') {
    return 'destructive'
  }
  return 'secondary'
}

onMounted(() => {
  palette.value = buildPalette()
  loadOverview()
  loadLatest()
  loadGraph()
})
</script>

<template>
  <div>
    <FaPageHeader title="关于系统" class="mb-0">
      <template #description>
        框架与契约包的当前/最新版本、插件依赖与装载状态总览。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <div v-loading="overviewLoading" class="gap-4 grid grid-cols-1 lg:grid-cols-2">
        <FaCard title="框架运行时">
          <div class="version-grid">
            <span class="version-label">框架版本</span>
            <span class="version-value font-mono">{{ overview?.framework.version || '源码运行（未注入构建版本）' }}</span>
            <span class="version-label">构建时间</span>
            <span class="version-value">{{ overview?.framework.buildTime || '—' }}</span>
            <span class="version-label">Spring Boot</span>
            <span class="version-value font-mono">{{ overview?.framework.springBootVersion ?? '-' }}</span>
            <span class="version-label">Java</span>
            <span class="version-value font-mono">{{ overview?.framework.javaVersion ?? '-' }}</span>
            <span class="version-label">操作系统</span>
            <span class="version-value">{{ overview?.framework.osName }} {{ overview?.framework.osArch }}</span>
          </div>
        </FaCard>

        <FaCard title="插件装载">
          <div class="flex gap-6">
            <div class="stat">
              <div class="stat-value">
                {{ overview?.pluginTotal ?? '-' }}
              </div>
              <div class="stat-label">
                已安装
              </div>
            </div>
            <div class="stat">
              <div class="stat-value stat-value--ok">
                {{ overview?.pluginEnabled ?? '-' }}
              </div>
              <div class="stat-label">
                已启用
              </div>
            </div>
            <div class="stat">
              <div class="stat-value" :class="{ 'stat-value--error': (overview?.pluginError ?? 0) > 0 }">
                {{ overview?.pluginError ?? '-' }}
              </div>
              <div class="stat-label">
                异常
              </div>
            </div>
          </div>
          <p class="text-sm text-secondary-foreground mt-4">
            依赖与装载状态见下方依赖图；插件的安装、启用与回滚在「平台 → 插件管理」操作。
          </p>
        </FaCard>
      </div>
    </FaPageMain>

    <FaPageMain>
      <FaCard title="契约版本">
        <template #header>
          <div class="flex w-full items-center justify-between">
            <span>契约版本</span>
            <FaButton size="sm" variant="outline" :loading="latestLoading" @click="loadLatest">
              重新探测最新版本
            </FaButton>
          </div>
        </template>
        <div class="contract-table">
          <div class="contract-row contract-row--head">
            <span>包</span>
            <span>类型</span>
            <span>当前版本</span>
            <span>状态</span>
          </div>
          <div v-for="row in versionRows" :key="row.key" class="contract-row">
            <span>{{ row.name }}</span>
            <span><FaTag variant="outline">{{ row.kind }}</FaTag></span>
            <span class="font-mono">{{ row.current || '未知' }}</span>
            <span>
              <FaTag :variant="versionState(row).variant">{{ versionState(row).text }}</FaTag>
            </span>
          </div>
        </div>
        <p class="text-xs text-secondary-foreground mt-3">
          最新版本由后端只读探测发布仓库（Nexus Maven/npm 公共组）得出并缓存 10 分钟；探测失败不阻塞页面。
        </p>
      </FaCard>
    </FaPageMain>

    <FaPageMain>
      <FaCard title="插件依赖图">
        <template #header>
          <div class="flex w-full items-center justify-between">
            <span>插件依赖与装载状态</span>
            <FaButton size="sm" variant="outline" :loading="graphLoading" @click="loadGraph">
              刷新
            </FaButton>
          </div>
        </template>
        <div v-loading="graphLoading" class="graph-wrap">
          <div v-if="!graphLoading && graphNodes.length === 0" class="graph-empty">
            暂无已安装插件
          </div>
          <YdGraphCanvas
            v-else
            class="graph-canvas"
            :nodes="graphNodes"
            :edges="graphEdges"
            :selected-id="selectedCode"
            @click-node="onGraphNodeClick"
            @click-stage="onGraphStageClick"
          >
            <template #overlay>
              <div class="graph-side">
                <!-- 图例默认收成胶囊，悬停/聚焦展开，避免遮住内圈节点 -->
                <div class="graph-panel graph-legend-panel">
                  <div class="graph-panel-title">
                    <FaIcon name="i-ri:information-line" />
                    图例
                  </div>
                  <div class="graph-legend-body">
                    <div class="graph-legend">
                      <span><i class="dot" :style="{ background: palette?.enabled }" />已启用</span>
                      <span><i class="dot" :style="{ background: palette?.loaded }" />已加载</span>
                      <span><i class="dot" :style="{ background: palette?.idle }" />已安装/禁用</span>
                      <span><i class="dot" :style="{ background: palette?.error }" />异常/缺失</span>
                    </div>
                    <div class="graph-legend">
                      <span><i class="line" :style="{ background: palette?.edgeHard }" />硬依赖 depend</span>
                      <span><i class="line line--soft" :style="{ background: palette?.edgeSoft }" />软依赖 softdepend</span>
                    </div>
                    <p class="graph-hint">
                      边方向：依赖方 → 被依赖方。内圈为被依赖的基础插件，向外逐层是依赖层级更深的插件，最外圈是与依赖无关的独立插件。点击节点查看详情，点击空白取消选中。
                    </p>
                  </div>
                </div>

                <div v-if="selectedNode" class="graph-panel graph-detail-panel">
                  <div class="graph-panel-title">
                    <FaIcon name="i-ri:apps-2-line" />
                    {{ selectedNode.name }}
                  </div>
                  <div class="graph-detail">
                    <div>
                      <FaTag :variant="statusVariant(selectedNode.status)">
                        {{ STATUS_TEXT[selectedNode.status] ?? selectedNode.status }}
                      </FaTag>
                      <span v-if="selectedNode.version" class="text-xs font-mono ml-2">v{{ selectedNode.version }}</span>
                    </div>
                    <div v-if="selectedNode.errorMessage" class="graph-error">
                      {{ selectedNode.errorMessage }}
                    </div>
                    <div v-if="selectedRelations.dependsOn.length">
                      依赖：{{ selectedRelations.dependsOn.join('、') }}
                    </div>
                    <div v-if="selectedRelations.dependedBy.length">
                      被依赖：{{ selectedRelations.dependedBy.join('、') }}
                    </div>
                  </div>
                </div>
              </div>
            </template>
          </YdGraphCanvas>
        </div>
      </FaCard>
    </FaPageMain>
  </div>
</template>

<style scoped>
.version-grid {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 8px 16px;
  font-size: 13px;
}

.version-label {
  color: var(--color-text-3);
}

.version-value {
  color: var(--color-text-1);
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-value {
  font-size: 28px;
  font-weight: 600;
  color: var(--color-text-1);
}

.stat-value--ok {
  color: rgb(var(--success-6));
}

.stat-value--error {
  color: rgb(var(--danger-6));
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-3);
}

.contract-table {
  display: flex;
  flex-direction: column;
  font-size: 13px;
}

.contract-row {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.6fr);
  gap: 12px;
  align-items: center;
  padding: 10px 4px;
  border-bottom: 1px solid var(--color-border-2);
}

.contract-row:last-child {
  border-bottom: none;
}

.contract-row--head {
  color: var(--color-text-3);
  font-size: 12px;
}

.graph-wrap {
  height: 520px;
  position: relative;
}

.graph-empty {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-text-3);
}

.graph-canvas {
  height: 100%;
  width: 100%;
}

.graph-side {
  position: absolute;
  top: 12px;
  left: 12px;
  right: 12px;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  pointer-events: none;
}

.graph-side > * {
  pointer-events: auto;
}

/* 图例收成胶囊，悬停或聚焦时展开；详情面板固定在右上 */
.graph-legend-panel {
  width: 96px;
  max-height: 44px;
  overflow: hidden;
  transition: width 0.18s ease, max-height 0.18s ease;
}

.graph-legend-panel:hover,
.graph-legend-panel:focus-within {
  width: 336px;
  max-height: 460px;
}

.graph-legend-body {
  opacity: 0;
  transition: opacity 0.18s ease;
}

.graph-legend-panel:hover .graph-legend-body,
.graph-legend-panel:focus-within .graph-legend-body {
  opacity: 1;
}

.graph-detail-panel {
  max-width: 320px;
}

.graph-panel {
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  padding: 12px;
  font-size: 12px;
  color: var(--color-text-2);
}

.graph-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 600;
  color: var(--color-text-1);
  margin-bottom: 8px;
}

.graph-legend {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 6px;
}

.graph-legend span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  display: inline-block;
}

.line {
  width: 18px;
  height: 2px;
  display: inline-block;
}

.line--soft {
  height: 1px;
}

.graph-hint {
  color: var(--color-text-3);
  margin-top: 4px;
}

.graph-detail {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.graph-error {
  color: rgb(var(--danger-6));
  word-break: break-all;
}
</style>
