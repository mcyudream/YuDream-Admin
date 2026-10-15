<script setup lang="ts">
import type { AiEndpointDraft } from './ai-endpoints'
import type { CapabilityItem, CapabilityStatus, CapabilityType } from '@/api/modules/platform-capability'
import { YdStateIllustration } from '@yudream/components'
import apiCapability from '@/api/modules/platform-capability'
import { refreshDynamicRoutes } from '@/router/dynamic'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { createEndpointDraft, firstChatEndpoint, modelOptionsOf, parseEndpointDrafts, serializeEndpointDrafts, validateEndpointDrafts } from './ai-endpoints'
import AiEndpointConfig from './AiEndpointConfig.vue'

interface CapabilityGroup {
  type: CapabilityType
  title: string
  icon: string
}

interface CapabilityConfigField {
  key: string
  label: string
  placeholder?: string
  type?: 'text' | 'password' | 'number' | 'textarea'
}

const toast = useYdToast()
const router = useRouter()
const appFeatureStore = useAppFeatureStore()

const loading = ref(false)
const saving = ref(false)
const actionLoading = ref('')
const rows = ref<CapabilityItem[]>([])
const selectedCode = ref('')
const configEditing = ref(false)
const editingCapabilityCode = ref('')
const configText = ref('{}')
const configDraft = ref<Record<string, string>>({})
/** AI 端点草稿：聊天 / 向量化 / 重排序各自独立，由 AiEndpointConfig 维护。 */
const aiEndpoints = ref<AiEndpointDraft[]>([])
const testMessage = ref('平台能力测试消息')
const keyword = ref('')
const selectedType = ref<CapabilityType | 'ALL'>('ALL')
const selectedStatus = ref<CapabilityStatus | 'ALL'>('ALL')
const sseStatus = ref('未连接')
const wsStatus = ref('未连接')
let websocket: WebSocket | null = null
let sseAbort: AbortController | null = null

/**
 * 能力图标兜底：后端下发的 Remix 图标里 file-eye-line 不在离线集合中，会渲染成空白。
 * 这里按能力编码给 Morphicons 等价图标，避免任何能力出现"没有图标"的卡片。
 */
const capabilityIconFallbacks: Record<string, string> = {
  'sse': 'morph:radio-tower',
  'ai': 'morph:sparkles',
  'agent': 'morph:bot',
  'websocket': 'morph:refresh-cw',
  'dataviz': 'morph:pie-chart',
  'rabbitmq': 'morph:messages-square',
  'api-docs': 'morph:file-text',
  'milky': 'morph:message-circle',
  'message-render': 'morph:image',
  'integration': 'morph:terminal',
  'document-template': 'morph:file-text',
  'form': 'morph:clipboard-list',
  'wiki': 'morph:book-open',
  'neo4j': 'morph:share-2',
  'cms': 'morph:layout-dashboard',
  'plugin-market-source': 'morph:store',
  'file-preview': 'morph:file-scan',
  'inbound-mail': 'morph:mail',
}

function capabilityIcon(item: CapabilityItem) {
  // 后端仍在下发 Remix 图标名，离线集合里可能缺失（如 file-eye-line）会渲染成空白，
  // 因此有策展图标的能力优先用 Morphicons，其余仍用后端图标。
  return capabilityIconFallbacks[item.code] || item.icon || 'morph:puzzle'
}

const groups: CapabilityGroup[] = [
  { type: 'REALTIME', title: '实时通信', icon: 'morph:activity' },
  { type: 'MESSAGING', title: '消息队列', icon: 'morph:message-square' },
  { type: 'DOCUMENTATION', title: '接口文档', icon: 'morph:file-text' },
  { type: 'INTEGRATION', title: '集成运行', icon: 'morph:square-terminal' },
  { type: 'DOCUMENT', title: '文档生成', icon: 'morph:file-text' },
  { type: 'CONTENT', title: '内容定制', icon: 'i-ri:layout-masonry-line' },
  { type: 'GRAPH', title: '图数据库', icon: 'morph:share-2' },
  { type: 'AI', title: 'AI 助手', icon: 'morph:sparkles' },
  { type: 'DISTRIBUTION', title: '插件分发', icon: 'morph:store' },
]

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value))
const configFields = computed(() => fieldsOf(selected.value?.code))
const isAiConfig = computed(() => selected.value?.code === 'ai')
const usesStructuredConfig = computed(() => isAiConfig.value || configFields.value.length > 0)
const dependencyRows = computed(() => dependencyStatus(selected.value))
const missingDependencies = computed(() => dependencyRows.value.filter(item => !item.enabled))
const canEnableSelected = computed(() => !selected.value?.enabled && missingDependencies.value.length === 0)
const summary = computed(() => {
  const enabled = rows.value.filter(item => item.enabled).length
  const error = rows.value.filter(item => item.status === 'ERROR').length
  const disabled = rows.value.filter(item => !item.enabled && item.status !== 'ERROR').length
  return { total: rows.value.length, enabled, error, disabled }
})

/**
 * 状态条：计数即筛选入口，一行取代四张统计卡，把高度留给能力目录。
 */
const statusChips = computed(() => {
  const { total, enabled, disabled, error } = summary.value
  return [
    { key: 'ALL', label: '全部', count: total, tone: 'neutral' },
    { key: 'ENABLED', label: '运行中', count: enabled, tone: 'ok' },
    { key: 'DISABLED', label: '未启用', count: disabled, tone: 'muted' },
    { key: 'ERROR', label: '异常', count: error, tone: error > 0 ? 'bad' : 'muted' },
  ] as { key: CapabilityStatus | 'ALL', label: string, count: number, tone: string }[]
})

/** 状态条右侧的窄条比例指示，只读展示，不做交互。 */
const statusRatio = computed(() => {
  const { total, enabled, disabled, error } = summary.value
  const percent = (value: number) => (total ? Math.round(value / total * 100) : 0)
  return {
    segments: [
      { key: 'enabled', percent: percent(enabled), tone: 'ok', label: `运行中 ${enabled}` },
      { key: 'disabled', percent: percent(disabled), tone: 'muted', label: `未启用 ${disabled}` },
      { key: 'error', percent: percent(error), tone: 'bad', label: `异常 ${error}` },
    ],
    enabledPercent: percent(enabled),
  }
})
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase()
  return rows.value.filter((item) => {
    const matchesQuery = !query || [item.name, item.code, item.description, typeText(item.type)]
      .some(value => value?.toLocaleLowerCase().includes(query))
    const matchesType = selectedType.value === 'ALL' || item.type === selectedType.value
    const matchesStatus = selectedStatus.value === 'ALL' || item.status === selectedStatus.value
    return matchesQuery && matchesType && matchesStatus
  })
})
const typeOptions = computed(() => [
  { label: '全部类别', value: 'ALL' as const },
  ...groups.filter(group => rows.value.some(item => item.type === group.type)).map(group => ({ label: group.title, value: group.type })),
])
const selectedTypeText = computed(() => selected.value ? typeText(selected.value.type) : '')

watch(filteredRows, (list) => {
  if (selectedCode.value && !list.some(item => item.code === selectedCode.value)) {
    if (list.length) {
      selectedCode.value = list[0].code
    }
    if (configEditing.value) {
      cancelConfig()
    }
  }
  else if (!selectedCode.value && list.length) {
    selectedCode.value = list[0].code
  }
}, { immediate: true })

onMounted(load)
onBeforeUnmount(() => {
  closeSse()
  closeWs()
})

async function load() {
  loading.value = true
  try {
    const res = await apiCapability.list()
    rows.value = dedupeCapabilities(res.data)
    selectedCode.value = rows.value.some(item => item.code === selectedCode.value)
      ? selectedCode.value
      : rows.value[0]?.code ?? ''
  }
  finally {
    loading.value = false
  }
}

/** 接口可能返回同一编码的多条记录（重复注册）：优先保留已启用、带运行指标与健康信息的一条。 */
function dedupeCapabilities(list: CapabilityItem[]) {
  const byCode = new Map<string, CapabilityItem>()
  for (const item of list) {
    const current = byCode.get(item.code)
    if (!current || capabilityScore(item) > capabilityScore(current)) {
      byCode.set(item.code, item)
    }
  }
  return [...byCode.values()]
}

function capabilityScore(item: CapabilityItem) {
  return (item.enabled ? 4 : 0)
    + (Object.keys(item.metrics || {}).length ? 2 : 0)
    + (item.healthMessage ? 1 : 0)
}

async function toggleCapability(item: CapabilityItem) {
  if (!item.enabled) {
    const missing = dependencyStatus(item).filter(dependency => !dependency.enabled)
    if (missing.length) {
      toast.error('依赖能力未启用', { description: `请先启用：${missing.map(item => item.name).join('、')}` })
      return
    }
  }
  actionLoading.value = `${item.code}:toggle`
  try {
    const res = item.enabled ? await apiCapability.disable(item.code) : await apiCapability.enable(item.code)
    replaceItem(res.data)
    await Promise.all([
      refreshDynamicRoutes(router),
      appFeatureStore.load(true),
    ])
    toast.success(item.enabled ? '已禁用' : '已启用')
  }
  finally {
    actionLoading.value = ''
  }
}

const catalogRef = ref<HTMLElement>()
const hasMoreBelow = ref(false)

/** 目录未滚到底时显示底部渐隐，提示还有能力没露出。 */
function syncCatalogOverflow() {
  const element = catalogRef.value
  hasMoreBelow.value = Boolean(element) && element!.scrollHeight - element!.scrollTop - element!.clientHeight > 8
}

watch(filteredRows, () => {
  nextTick(syncCatalogOverflow)
})

function selectCapability(item: CapabilityItem) {
  if (saving.value) {
    return
  }
  if (configEditing.value) {
    cancelConfig()
  }
  selectedCode.value = item.code
}

function clearFilters() {
  keyword.value = ''
  selectedType.value = 'ALL'
  selectedStatus.value = 'ALL'
}

function openConfig(item: CapabilityItem) {
  selectedCode.value = item.code
  configDraft.value = { ...(item.config || {}) }
  for (const key of Object.keys(item.secretConfigured || {})) {
    configDraft.value[key] = ''
  }
  if (item.code === 'ai') {
    aiEndpoints.value = parseEndpointDrafts(item.config || {})
    if (!aiEndpoints.value.length) {
      aiEndpoints.value = [createEndpointDraft('chat', 'openai')]
    }
    const chat = firstChatEndpoint(aiEndpoints.value)
    configDraft.value.defaultProvider ||= chat?.code || ''
    configDraft.value.defaultModel ||= modelOptionsOf(chat)[0]?.value || ''
  }
  configText.value = JSON.stringify(item.config || {}, null, 2)
  editingCapabilityCode.value = item.code
  configEditing.value = true
}

function cancelConfig() {
  configEditing.value = false
  editingCapabilityCode.value = ''
}

async function saveConfig() {
  if (!editingCapabilityCode.value) {
    return
  }
  const config = usesStructuredConfig.value ? normalizedConfig() : parseJsonConfig()
  if (!config) {
    return
  }
  saving.value = true
  try {
    const res = await apiCapability.updateConfig(editingCapabilityCode.value, config)
    replaceItem(res.data)
    configEditing.value = false
    editingCapabilityCode.value = ''
    toast.success('配置已保存')
  }
  catch {
    toast.error('保存失败，请检查配置并重试')
  }
  finally {
    saving.value = false
  }
}

async function testCapability(item: CapabilityItem) {
  actionLoading.value = `${item.code}:test`
  try {
    const res = await apiCapability.test(item.code, testMessage.value)
    if (res.data.success) {
      toast.success('测试成功', { description: res.data.message })
    }
    else {
      toast.error('测试失败', { description: res.data.message })
    }
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

/** SSE 端点已要求登录：EventSource 无法携带 Authorization 头，改用 fetch 流式读取 */
async function connectSse() {
  closeSse()
  sseStatus.value = '连接中'
  const controller = new AbortController()
  sseAbort = controller
  try {
    const response = await fetch(httpEndpoint('/api/platform/sse/connect'), {
      headers: { Authorization: localStorage.getItem('token') || '', Accept: 'text/event-stream' },
      signal: controller.signal,
    })
    if (!response.ok || !response.body) {
      sseStatus.value = `连接失败（HTTP ${response.status}）`
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let eventName = 'message'
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      const rawLines = decoder.decode(value, { stream: true }).split('\n')
      for (const rawLine of rawLines) {
        const line = rawLine.replace(/\r$/, '')
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        }
        else if (line.startsWith('data:')) {
          const data = line.slice(5).trim()
          if (eventName === 'connected') {
            sseStatus.value = `已连接：${data}`
          }
          else if (eventName === 'capability-test') {
            toast.info('SSE 消息', { description: data })
          }
          eventName = 'message'
        }
      }
    }
    sseStatus.value = '未连接'
  }
  catch {
    if (!controller.signal.aborted) {
      sseStatus.value = '连接异常'
    }
  }
}

function closeSse() {
  sseAbort?.abort()
  sseAbort = null
  sseStatus.value = '未连接'
}

function connectWs() {
  closeWs()
  // WS 端点握手已要求登录：浏览器无法携带 Authorization 头，经查询参数传入令牌
  const token = localStorage.getItem('token') || ''
  websocket = new WebSocket(`${wsEndpoint('/api/platform/ws')}?token=${encodeURIComponent(token)}`)
  wsStatus.value = '连接中'
  websocket.onopen = () => wsStatus.value = '已连接'
  websocket.onmessage = event => toast.info('WebSocket 消息', { description: event.data })
  websocket.onerror = () => wsStatus.value = '连接异常'
  websocket.onclose = () => wsStatus.value = '未连接'
}

function closeWs() {
  websocket?.close()
  websocket = null
  wsStatus.value = '未连接'
}

function replaceItem(item: CapabilityItem) {
  const index = rows.value.findIndex(row => row.code === item.code)
  if (index >= 0) {
    rows.value[index] = item
  }
}

function httpEndpoint(path: string) {
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${path}`
}

function wsEndpoint(path: string) {
  const base = new URL(import.meta.env.VITE_APP_API_BASEURL || window.location.origin)
  const protocol = base.protocol === 'https:' ? 'wss' : 'ws'
  return `${protocol}://${base.host}${path}`
}

function typeText(type: CapabilityType) {
  return groups.find(group => group.type === type)?.title || type
}

function statusText(status: CapabilityStatus) {
  const map: Record<CapabilityStatus, string> = {
    ENABLED: '运行中',
    DISABLED: '未启用',
    ERROR: '异常',
  }
  return map[status]
}

function statusVariant(status: CapabilityStatus) {
  return status === 'ENABLED' ? 'default' : status === 'ERROR' ? 'destructive' : 'secondary'
}

/** 运行指标的展示名称：后端返回英文键，这里映射为管理员可读的中文（未知键原样显示）。 */
const metricLabels: Record<string, string> = {
  connections: '当前连接',
  sessions: '会话数',
  messages: '消息数',
  providers: '已配置供应商',
  providerCount: '供应商数量',
  modelCount: '模型数量',
  protocols: '使用协议',
  embeddingEndpoints: '向量化端点',
  embeddingModelCount: '向量化模型数',
  rerankEndpoints: '重排序端点',
  rerankModelCount: '重排序模型数',
  apiKeyConfigured: '已配置密钥',
  proxyEnabled: '启用代理',
  queue: '队列',
  exchange: '交换机',
  nodes: '节点数',
  documents: '文档数',
  swaggerUiPath: 'Swagger UI 路径',
  openApiPath: 'OpenAPI 描述路径',
  renderer: '渲染引擎',
  pageType: '页面类型',
  baseUrl: '服务地址',
  reachable: '服务可达',
  runtime: '运行时',
  pythonCommand: 'Python 命令',
  designer: '表单设计器',
  mailboxId: '收件箱标识',
  host: '主机',
  uri: '连接地址',
  database: '数据库',
  virtualHost: 'Virtual Host',
  routingKey: '路由键',
  timeout: '连接超时（毫秒）',
}

interface MetricEntry {
  key: string
  label: string
  type: 'tags' | 'boolean' | 'text' | 'number' | 'link' | 'path'
  tags: string[]
  text: string
  number: number
  ok: boolean
  icon: string
}

/** 指标图标与单位：让每个数字有语义，而不是一排同样的空白卡。 */
const metricIcons: Record<string, string> = {
  connections: 'morph:plug-zap',
  sessions: 'morph:radio',
  messages: 'morph:message-square',
  providers: 'morph:boxes',
  providerCount: 'morph:boxes',
  modelCount: 'morph:brain-circuit',
  protocols: 'morph:git-branch',
  embeddingEndpoints: 'morph:waypoints',
  embeddingModelCount: 'morph:waypoints',
  rerankEndpoints: 'morph:list-ordered',
  rerankModelCount: 'morph:list-ordered',
  apiKeyConfigured: 'morph:key-round',
  proxyEnabled: 'morph:network',
  nodes: 'morph:circle-dot',
  documents: 'morph:file-text',
}

function metricIcon(key: string) {
  return metricIcons[key] || 'morph:activity'
}

/** 指标值可能是数组、布尔、数字或长字符串：转成可读的标签列表/数字/文本，避免原始 JSON 直接进卡片。 */
function metricEntries(item?: CapabilityItem): MetricEntry[] {
  return Object.entries(item?.metrics || {}).map(([key, value]) => {
    const label = metricLabels[key] || key
    const icon = metricIcon(key)
    if (Array.isArray(value)) {
      const tags = value.map(entry => (entry === null || entry === undefined ? '-' : String(entry))).filter(Boolean)
      return { key, label, icon, type: 'tags' as const, tags, text: tags.join('、'), number: tags.length, ok: tags.length > 0 }
    }
    if (typeof value === 'boolean') {
      return { key, label, icon, type: 'boolean' as const, tags: [], text: value ? '是' : '否', number: value ? 1 : 0, ok: value }
    }
    if (typeof value === 'number') {
      return { key, label, icon, type: 'number' as const, tags: [], text: String(value), number: value, ok: true }
    }
    if (value && typeof value === 'object') {
      const text = Object.entries(value as Record<string, unknown>)
        .map(([innerKey, innerValue]) => `${metricLabels[innerKey] || innerKey} ${String(innerValue)}`)
        .join('；')
      return { key, label, icon, type: 'text' as const, tags: [], text: text || '-', number: 0, ok: true }
    }
    const text = value === null || value === undefined || value === '' ? '-' : String(value)
    // 只有完整 URL 才做外链；站内路径用等宽文本 + 复制，点开没有意义。
    const isUrl = /^(?:https?:)?\/\//.test(text)
    const isPath = !isUrl && /^\/[\w./-]+$/.test(text)
    const type = isUrl ? 'link' as const : isPath ? 'path' as const : 'text' as const
    return { key, label, icon, type, tags: [], text, number: 0, ok: text !== '-' }
  })
}

/** AI 能力的模型分布：只用接口上报的数字，不虚构数据。 */
const aiModelBreakdown = computed(() => {
  const metrics = selected.value?.metrics || {}
  const total = Number(metrics.modelCount ?? 0)
  const embedding = Number(metrics.embeddingModelCount ?? 0)
  const rerank = Number(metrics.rerankModelCount ?? 0)
  const chat = Math.max(0, total - embedding - rerank)
  const sum = chat + embedding + rerank
  return {
    total,
    sum,
    items: [
      { key: 'chat', label: '对话', value: chat, icon: 'morph:messages-square' },
      { key: 'embedding', label: '向量化', value: embedding, icon: 'morph:waypoints' },
      { key: 'rerank', label: '重排序', value: rerank, icon: 'morph:list-ordered' },
    ].map(item => ({ ...item, percent: sum ? Math.round(item.value / sum * 100) : 0 })),
  }
})

const dependencyReady = computed(() => dependencyRows.value.filter(item => item.enabled).length)
const dependencyPercent = computed(() => dependencyRows.value.length
  ? Math.round(dependencyReady.value / dependencyRows.value.length * 100)
  : 100)
const dependencySummary = computed(() => {
  if (!dependencyRows.value.length) {
    return '该能力不依赖其他能力'
  }
  return missingDependencies.value.length
    ? `需先启用：${missingDependencies.value.map(item => item.name).join('、')}`
    : '依赖全部满足，可以启用'
})

/** 未上报运行指标时展示已保存的连接配置，避免整块空白。 */
const configHighlights = computed(() => {
  const config = selected.value?.config || {}
  const secretKeys = Object.keys(selected.value?.secretConfigured || {})
  const fields = fieldsOf(selected.value?.code)
  const entries = fields
    .filter(field => field.type !== 'password' && config[field.key])
    .map(field => ({ key: field.key, label: field.label, value: String(config[field.key]) }))
  const baseUrl = config.baseUrl || config.uri || config.host
  if (baseUrl && !entries.some(item => item.value === baseUrl)) {
    entries.unshift({ key: 'endpoint', label: '服务地址', value: String(baseUrl) })
  }
  return { entries, secretCount: secretKeys.length }
})

/** 检查时间带 7 位小数时截断到秒，避免长串撑破信息卡。 */
function formatTime(value?: string) {
  if (!value) {
    return '-'
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : value
}

function dependencyStatus(item?: CapabilityItem) {
  return (item?.dependencies || []).map((code) => {
    const dependency = rows.value.find(row => row.code === code)
    return {
      code,
      name: dependency?.name || code,
      icon: dependency ? capabilityIcon(dependency) : 'morph:puzzle',
      enabled: Boolean(dependency?.enabled),
      available: Boolean(dependency),
    }
  })
}

/** 概览区展示的数字指标；AI 的模型数已由环形图承载，避免重复。 */
const breakdownKeys = ['modelCount', 'embeddingModelCount', 'rerankModelCount']
const healthNumbers = computed(() => metricEntries(selected.value)
  .filter(metric => metric.type === 'number' && !(aiModelBreakdown.value.sum && breakdownKeys.includes(metric.key))))
/** 事实清单：标签、布尔、链接等逐行展示，窄栏也读得顺。 */
const healthFacts = computed(() => metricEntries(selected.value).filter(metric => metric.type !== 'number'))

/** 环形图用 conic-gradient 表达三段占比，颜色只由强调色与透明层次决定，不引入私有色板。 */
const donutStyle = computed(() => {
  const items = aiModelBreakdown.value.items.filter(item => item.value > 0)
  const sum = items.reduce((total, item) => total + item.value, 0)
  if (!sum) {
    return {}
  }
  let cursor = 0
  const stops = items.map((item) => {
    const from = cursor / sum * 360
    cursor += item.value
    const to = cursor / sum * 360
    const base = 'var(--yd-emphasis, var(--color-text-1))'
    const color = item.key === 'chat'
      ? base
      : item.key === 'embedding'
        ? `color-mix(in oklab, ${base} 62%, transparent)`
        : `color-mix(in oklab, ${base} 32%, transparent)`
    return `${color} ${from}deg ${to}deg`
  })
  return { background: `conic-gradient(${stops.join(', ')})` }
})

/** 依赖项可点击跳转：省去在目录里再找一次。 */
function focusCapability(code: string) {
  const target = rows.value.find(row => row.code === code)
  if (!target) {
    return
  }
  if (configEditing.value) {
    cancelConfig()
  }
  selectedCode.value = target.code
}

function fieldsOf(code?: string): CapabilityConfigField[] {
  const map: Record<string, CapabilityConfigField[]> = {
    'sse': [
      { key: 'timeout', label: '连接超时（毫秒）', placeholder: '300000', type: 'number' },
    ],
    'neo4j': [
      { key: 'uri', label: 'Neo4j URI', placeholder: 'bolt://localhost:7687' },
      { key: 'username', label: '用户名', placeholder: 'neo4j' },
      { key: 'password', label: '密码', placeholder: '留空不修改', type: 'password' },
      { key: 'database', label: '数据库', placeholder: 'neo4j' },
    ],
    'rabbitmq': [
      { key: 'host', label: '主机', placeholder: 'localhost' },
      { key: 'port', label: '端口', placeholder: '35672', type: 'number' },
      { key: 'username', label: '用户名', placeholder: 'guest' },
      { key: 'password', label: '密码', placeholder: 'guest', type: 'password' },
      { key: 'virtualHost', label: 'Virtual Host', placeholder: '/' },
      { key: 'exchange', label: '交换机', placeholder: 'yudream.capability' },
      { key: 'queue', label: '队列', placeholder: 'yudream.capability.test' },
      { key: 'routingKey', label: '路由键', placeholder: 'capability.test' },
    ],
    'file-preview': [
      { key: 'baseUrl', label: 'kkFileView 服务地址（浏览器可达）', placeholder: 'http(s)://站点/kkfileview' },
      { key: 'callbackBaseUrl', label: '回源基址（留空按请求推导）', placeholder: 'http://backend:8080' },
      { key: 'officePreviewType', label: 'Office 预览类型', placeholder: 'pdf' },
      { key: 'tokenTtlSeconds', label: '签名地址时效（秒）', placeholder: '1800', type: 'number' },
      { key: 'maxPreviewSizeMb', label: '预览大小上限（MB）', placeholder: '200', type: 'number' },
    ],
    'inbound-mail': [
      { key: 'mailboxId', label: '收件箱标识', placeholder: 'default' },
      { key: 'host', label: 'IMAP 主机', placeholder: 'imap.example.com' },
      { key: 'port', label: '端口', placeholder: '993', type: 'number' },
      { key: 'username', label: '用户名', placeholder: 'verify@example.com' },
      { key: 'password', label: '密码或授权码', placeholder: '留空不修改', type: 'password' },
      { key: 'folder', label: '文件夹', placeholder: 'INBOX' },
    ],
    'form': [
      { key: 'maxUploadSizeMb', label: '上传文件大小上限（MB）', placeholder: '100', type: 'number' },
    ],
  }
  return code ? map[code] || [] : []
}

/** 保存前归一化：AI 走端点契约（保留配额等既有配置键），其他结构化能力按字段收集。 */
function normalizedConfig() {
  if (selected.value?.code === 'ai') {
    const error = validateEndpointDrafts(aiEndpoints.value)
    if (error) {
      toast.error(error)
      return null
    }
    const chat = firstChatEndpoint(aiEndpoints.value)
    const endpoint = aiEndpoints.value.find(item => item.usage === 'chat' && item.code === configDraft.value.defaultProvider) || chat
    const options = modelOptionsOf(endpoint)
    const selectedModel = configDraft.value.defaultModel?.trim() || ''
    const defaultModel = options.some(option => option.value === selectedModel) ? selectedModel : (options[0]?.value || '')
    if (!endpoint || !defaultModel) {
      toast.error('默认模型必须属于默认聊天端点')
      return null
    }
    endpoint.defaultModel = defaultModel
    return {
      // 能力配置整体替换，必须带上配额、附件等既有键，避免保存端点时丢失。
      ...(selected.value.config || {}),
      providers: serializeEndpointDrafts(aiEndpoints.value),
      defaultProvider: endpoint.code,
      defaultModel,
    }
  }
  return Object.fromEntries(
    configFields.value.map(field => [field.key, String(configDraft.value[field.key] ?? '').trim()]),
  )
}

function parseJsonConfig() {
  try {
    return JSON.parse(configText.value || '{}') as Record<string, string>
  }
  catch {
    toast.error('配置格式错误', { description: '请输入合法 JSON 对象' })
    return null
  }
}
</script>

<template>
  <div>
    <YdPageHeader title="平台能力" class="mb-0">
      <template #description>
        管理 SSE、WebSocket、RabbitMQ、API 文档、集成运行等可选能力的启停、配置、健康状态与测试消息。
      </template>
    </YdPageHeader>

    <YdPageMain>
      <div class="capability-toolbar">
        <YdInput v-model="keyword" clearable placeholder="搜索能力名称、编码或说明" class="capability-search" />
        <YdSelect v-model="selectedType" :options="typeOptions" class="capability-filter" />
        <YdButton v-if="keyword || selectedType !== 'ALL' || selectedStatus !== 'ALL'" variant="ghost" @click="clearFilters">
          重置筛选
        </YdButton>
        <YdButton variant="outline" :loading="loading" @click="load">
          <YdIcon name="morph:refresh-cw" />刷新
        </YdButton>
      </div>

      <div class="capability-workbench">
        <section class="capability-browser" :class="{ 'has-more': hasMoreBelow }">
          <div class="capability-status-bar">
            <YdAction
              v-for="chip in statusChips"
              :key="chip.key"
              class="status-chip"
              :class="{ 'is-active': selectedStatus === chip.key }"
              :data-tone="chip.tone"
              :aria-pressed="selectedStatus === chip.key"
              @click="selectedStatus = chip.key"
            >
              <i v-if="chip.tone === 'ok' || chip.tone === 'bad'" class="status-chip__dot" />
              <span>{{ chip.label }}</span>
              <strong>{{ chip.count }}</strong>
            </YdAction>
            <span class="status-ratio__label">运行中 {{ statusRatio.enabledPercent }}%</span>
            <div class="status-ratio" role="img" :aria-label="statusRatio.segments.map(item => item.label).join('，')">
              <span class="status-ratio__track">
                <i
                  v-for="segment in statusRatio.segments"
                  :key="segment.key"
                  :data-tone="segment.tone"
                  :style="{ width: `${segment.percent}%` }"
                />
              </span>
              <span class="status-ratio__label">运行中 {{ statusRatio.enabledPercent }}%</span>
            </div>
          </div>
          <YdLoadingOverlay :loading="loading">
            <div v-if="filteredRows.length" ref="catalogRef" class="capability-catalog" @scroll="syncCatalogOverflow">
              <YdAction
                v-for="(item, cardIndex) in filteredRows"
                :key="item.code"
                class="capability-card"
                :class="{ 'is-selected': selected?.code === item.code }"
                :style="{ '--card-index': Math.min(cardIndex, 12) }"
                :data-status="item.status"
                :aria-pressed="selected?.code === item.code"
                @click="selectCapability(item)"
              >
                <span class="capability-card__icon"><YdIcon :name="capabilityIcon(item)" /></span>
                <span class="capability-card__name">{{ item.name }}</span>
                <span class="capability-card__meta">{{ typeText(item.type) }} · {{ item.code }}</span>
                <span class="capability-card__description" :title="item.description">{{ item.description || '暂无说明' }}</span>
                <span class="capability-card__state">{{ statusText(item.status) }}</span>
                <YdIcon name="morph:chevron-right" class="capability-card__chevron" />
              </YdAction>
            </div>
            <YdEmpty v-else-if="rows.length" title="没有匹配的能力" description="调整搜索词或筛选条件后重试。">
              <YdButton variant="outline" @click="clearFilters">
                清除筛选
              </YdButton>
            </YdEmpty>
            <YdEmpty v-else title="暂无平台能力" description="当前服务未返回可管理的能力。" />
          </YdLoadingOverlay>
        </section>

        <section v-if="selected" class="capability-detail">
          <template v-if="configEditing">
            <div class="detail-header">
              <div class="detail-title">
                <div class="detail-icon">
                  <YdIcon :name="selected.icon || 'morph:puzzle'" />
                </div>
                <div><h2>配置 {{ selected.name }}</h2><p>{{ selectedTypeText }} · {{ statusText(selected.status) }}</p></div>
              </div>
              <div class="detail-actions">
                <YdButton variant="ghost" @click="cancelConfig">
                  取消
                </YdButton>
                <YdButton :loading="saving" @click="saveConfig">
                  <YdIcon name="morph:check" />保存配置
                </YdButton>
              </div>
            </div>
            <div class="config-body">
              <AiEndpointConfig
                v-if="isAiConfig"
                v-model:endpoints="aiEndpoints"
                v-model:default-provider="configDraft.defaultProvider"
                v-model:default-model="configDraft.defaultModel"
                :capability-enabled="Boolean(selected?.enabled)"
              />
              <div v-else-if="usesStructuredConfig" class="config-form">
                <label v-for="field in configFields" :key="field.key" class="config-field">
                  <span>{{ field.label }}</span>
                  <small v-if="selected?.secretConfigured?.[field.key]" class="config-field__hint">已配置，留空不修改</small>
                  <YdTextarea v-if="field.type === 'textarea'" v-model="configDraft[field.key]" rows="5" input-class="font-mono" :placeholder="field.placeholder" />
                  <YdInput v-else v-model="configDraft[field.key]" :type="field.type || 'text'" :placeholder="field.placeholder" />
                </label>
              </div>
              <YdTextarea v-else v-model="configText" rows="12" class="w-full" input-class="font-mono min-h-72" />
            </div>
            <div class="config-footer">
              <YdButton variant="ghost" @click="cancelConfig">
                取消
              </YdButton>
              <YdButton :loading="saving" @click="saveConfig">
                <YdIcon name="morph:check" />保存配置
              </YdButton>
            </div>
          </template>
          <template v-else>
            <div class="detail-header">
              <div class="detail-title">
                <div class="detail-icon">
                  <YdIcon :name="selected.icon || 'morph:puzzle'" />
                </div>
                <div><h2>{{ selected.name }}</h2><p>{{ selected.description || '暂无说明' }}</p></div>
              </div>
              <div class="detail-actions">
                <YdButton v-auth="'platform:capability:config'" variant="outline" @click="openConfig(selected)">
                  <YdIcon name="morph:settings-2" />配置
                </YdButton>
                <YdButton v-auth="'platform:capability:test'" variant="outline" :disabled="!selected.enabled" :loading="actionLoading === `${selected.code}:test`" @click="testCapability(selected)">
                  <YdIcon name="morph:send" />测试
                </YdButton>
                <YdButton v-auth="selected.enabled ? 'platform:capability:disable' : 'platform:capability:enable'" :variant="selected.enabled ? 'destructive' : 'default'" :disabled="!selected.enabled && !canEnableSelected" :loading="actionLoading === `${selected.code}:toggle`" @click="toggleCapability(selected)">
                  <YdIcon :name="selected.enabled ? 'morph:circle-pause' : 'morph:circle-play'" />{{ selected.enabled ? '禁用' : '启用' }}
                </YdButton>
              </div>
            </div>
            <div class="detail-grid">
              <div class="info-cell">
                <span>状态</span><YdTag :variant="statusVariant(selected.status)">
                  {{ statusText(selected.status) }}
                </YdTag>
              </div>
              <div class="info-cell">
                <span>类别</span><strong>{{ selectedTypeText }}</strong>
              </div>
              <div class="info-cell">
                <span>编码</span><code>{{ selected.code }}</code>
              </div>
              <div class="info-cell">
                <span>检查时间</span><strong>{{ formatTime(selected.checkedAt) }}</strong>
              </div>
            </div>
            <div v-if="dependencyRows.length" class="dependency-panel">
              <div class="section-title">
                <span>依赖能力</span>
                <YdTag :variant="missingDependencies.length ? 'destructive' : 'default'">
                  {{ missingDependencies.length ? '未满足' : '已满足' }}
                </YdTag>
              </div>
              <div class="dependency-progress">
                <YdProgress :model-value="dependencyPercent" class="dependency-progress__bar" />
                <span>{{ dependencyReady }} / {{ dependencyRows.length }} 已启用</span>
              </div>
              <div class="dependency-list">
                <YdAction
                  v-for="dependency in dependencyRows"
                  :key="dependency.code"
                  class="dependency-item"
                  :class="{ 'is-missing': !dependency.enabled }"
                  @click="focusCapability(dependency.code)"
                >
                  <span class="dependency-item__icon"><YdIcon :name="dependency.icon" /></span>
                  <span class="dependency-item__body">
                    <strong>{{ dependency.name }}</strong>
                    <code>{{ dependency.code }}</code>
                  </span>
                  <YdTag :variant="dependency.enabled ? 'default' : dependency.available ? 'secondary' : 'destructive'">
                    {{ dependency.enabled ? '已启用' : dependency.available ? '未启用' : '不可用' }}
                  </YdTag>
                  <YdIcon name="morph:arrow-right" class="dependency-item__jump" />
                </YdAction>
              </div>
              <p class="dependency-tip">
                {{ dependencySummary }}
              </p>
            </div>

            <div class="health-panel">
              <div class="section-title">
                <span>健康信息</span>
                <YdTag :variant="statusVariant(selected.status)">
                  {{ selected.healthMessage || statusText(selected.status) }}
                </YdTag>
              </div>

              <template v-if="metricEntries(selected).length">
                <div v-if="healthNumbers.length || aiModelBreakdown.sum" class="health-overview">
                  <div
                    v-if="aiModelBreakdown.sum"
                    class="health-donut"
                    :style="donutStyle"
                    role="img"
                    :aria-label="`模型分布：${aiModelBreakdown.items.map(item => `${item.label} ${item.value} 个`).join('，')}`"
                  >
                    <div class="health-donut__hole">
                      <strong><YdAnimatedCountTo :value="aiModelBreakdown.total" /></strong>
                      <span>模型</span>
                    </div>
                  </div>
                  <div class="health-overview__body">
                    <ul v-if="healthNumbers.length" class="health-overview__stats">
                      <li v-for="metric in healthNumbers" :key="metric.key">
                        <span>{{ metric.label }}</span>
                        <strong><YdAnimatedCountTo :value="metric.number" /></strong>
                      </li>
                    </ul>
                    <ul v-if="aiModelBreakdown.sum" class="health-overview__legend">
                      <li v-for="item in aiModelBreakdown.items" :key="item.key">
                        <i :data-usage="item.key" />
                        <span>{{ item.label }}</span>
                        <strong>{{ item.value }}</strong>
                        <small v-if="item.percent">{{ item.percent }}%</small>
                      </li>
                    </ul>
                  </div>
                </div>

                <ul v-if="healthFacts.length" class="health-facts">
                  <li v-for="metric in healthFacts" :key="metric.key">
                    <span class="health-facts__label">{{ metric.label }}</span>
                    <div class="health-facts__value">
                      <template v-if="metric.type === 'tags'">
                        <YdTag v-for="tag in metric.tags" :key="tag" variant="secondary">
                          {{ tag }}
                        </YdTag>
                        <span v-if="!metric.tags.length" class="health-facts__muted">无</span>
                      </template>
                      <YdTag v-else-if="metric.type === 'boolean'" :variant="metric.ok ? 'default' : 'secondary'">
                        <YdIcon :name="metric.ok ? 'morph:check' : 'morph:minus'" />
                        {{ metric.text }}
                      </YdTag>
                      <a v-else-if="metric.type === 'link'" class="health-facts__link" :href="metric.text" target="_blank" rel="noopener">
                        <YdIcon name="morph:external-link" />{{ metric.text }}
                      </a>
                      <template v-else-if="metric.type === 'path'">
                        <code class="health-facts__path">{{ metric.text }}</code>
                        <YdCopyButton :text="metric.text" label="" />
                      </template>
                      <span v-else>{{ metric.text }}</span>
                    </div>
                  </li>
                </ul>
              </template>

              <div v-else class="health-empty">
                <YdStateIllustration :name="selected.status === 'ERROR' ? 'error' : 'empty'" size="sm" />
                <div>
                  <strong>{{ selected.enabled ? '该能力暂未上报运行指标' : '启用后即可查看运行指标' }}</strong>
                  <p>{{ selected.enabled ? '当前状态正常，无需处理。' : '可以先检查下方连接配置，再启用能力。' }}</p>
                </div>
              </div>

              <div v-if="configHighlights.entries.length || configHighlights.secretCount" class="health-config">
                <div class="section-title">
                  <span>当前配置</span>
                  <YdTag v-if="configHighlights.secretCount" variant="secondary">
                    {{ configHighlights.secretCount }} 项凭据已配置
                  </YdTag>
                </div>
                <ul>
                  <li v-for="entry in configHighlights.entries" :key="entry.key">
                    <span>{{ entry.label }}</span>
                    <code>{{ entry.value }}</code>
                    <YdCopyButton :text="entry.value" label="" />
                    <a
                      v-if="entry.key === 'endpoint' && /^https?:/.test(entry.value)"
                      :href="entry.value"
                      target="_blank"
                      rel="noopener"
                      class="health-config__open"
                    >打开</a>
                  </li>
                </ul>
              </div>
            </div>

            <div v-if="selected.code === 'sse' || selected.code === 'websocket'" class="debug-panel">
              <div class="section-title">
                <span>连接调试</span>
              </div>
              <div v-if="selected.code === 'sse'" class="debug-row">
                <span>SSE：{{ sseStatus }}</span><YdInput v-model="testMessage" placeholder="测试消息内容" class="debug-message" /><YdButton size="sm" variant="outline" :disabled="!selected.enabled" @click="connectSse">
                  连接
                </YdButton><YdButton size="sm" variant="ghost" @click="closeSse">
                  断开
                </YdButton>
              </div>
              <div v-if="selected.code === 'websocket'" class="debug-row">
                <span>WebSocket：{{ wsStatus }}</span><YdInput v-model="testMessage" placeholder="测试消息内容" class="debug-message" /><YdButton size="sm" variant="outline" :disabled="!selected.enabled" @click="connectWs">
                  连接
                </YdButton><YdButton size="sm" variant="ghost" @click="closeWs">
                  断开
                </YdButton>
              </div>
            </div>
          </template>
        </section>
      </div>
    </YdPageMain>
  </div>
</template>

<style scoped>
.capability-toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) minmax(160px, 220px) minmax(150px, 190px) auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 24px;
}

/* 两栏各自限制高度并独立滚动：目录滚动不再带动右侧详情，页面也不会超出可视区，
   底部留白因此始终保留（外壳页签栏等高度变化只影响留白的大小，不会造成溢出）。 */
.capability-workbench { display:grid; grid-template-columns:minmax(300px, 0.78fr) minmax(0, 1.7fr); gap:16px; align-items:stretch; }
.capability-workbench > * { max-height:var(--capability-pane-max-height, calc(100dvh - 272px)); min-height:280px; }
.capability-browser { position:relative; display:grid; grid-template-rows:auto auto minmax(0,1fr); gap:16px; min-width:0; min-height:0; }
/* 底部渐隐：提示目录还有更多内容，滚到底自动消失 */
.capability-browser::after { position:absolute; right:0; bottom:0; left:0; height:36px; background:linear-gradient(to top, var(--color-bg-2), transparent); content:''; opacity:0; pointer-events:none; transition:opacity .2s ease; }
.capability-browser.has-more::after { opacity:1; }
.capability-browser :deep(.yd-loading-overlay) { min-height:0; height:100%; }
.capability-browser :deep(.yd-loading-overlay__content) { display:grid; grid-template-rows:minmax(0,1fr); min-height:0; height:100%; }
/* 状态条：计数即筛选，一行取代四张统计卡，高度全部还给能力目录 */
.capability-status-bar { position:relative; display:flex; flex-wrap:wrap; gap:5px; align-items:center; min-width:0; padding-bottom:7px; }
.status-chip { display:inline-flex; gap:5px; align-items:center; padding:4px 9px; border:1px solid var(--color-border-2); border-radius:999px; background:var(--color-bg-1); color:var(--color-text-2); font-size:11.5px; transition:border-color .16s ease, background-color .16s ease, transform .16s cubic-bezier(.32,.72,0,1); }
.status-chip:hover { transform:translateY(-1px); border-color:var(--color-border-3); background:var(--color-fill-1); }
.status-chip.is-active { border-color:var(--color-text-2); background:var(--color-fill-2); color:var(--color-text-1); }
.status-chip strong { color:var(--color-text-1); font-size:12px; font-weight:650; font-variant-numeric:tabular-nums; }
.status-chip__dot { width:7px; height:7px; flex:none; border-radius:50%; background:currentColor; color:var(--color-text-3); opacity:.5; }
.status-chip[data-tone='ok'] .status-chip__dot { color:var(--yd-emphasis, var(--color-text-1)); opacity:1; }
.status-chip[data-tone='bad'] .status-chip__dot { color:var(--color-danger-6); opacity:1; }
/* 比例条贴在状态条底部：视觉保留，高度为零 */
.status-ratio { position:absolute; right:0; bottom:0; left:0; height:3px; }
.status-ratio__track { display:flex; overflow:hidden; width:100%; height:100%; border-radius:999px; background:var(--color-fill-2); }
.status-ratio__track i { display:block; height:100%; background:var(--yd-emphasis, var(--color-text-1)); transition:width .45s cubic-bezier(.32,.72,0,1); }
.status-ratio__track i[data-tone='muted'] { background:var(--color-text-3); opacity:.4; }
.status-ratio__track i[data-tone='bad'] { background:var(--color-danger-6); }
.status-ratio__label { display:none; }
.capability-list-heading { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:0; }
.capability-list-heading h2 { margin:0; color:var(--color-text-1); font-size:18px; font-weight:650; }
.capability-list-heading p { margin:4px 0 0; color:var(--color-text-3); font-size:13px; }
.capability-catalog { container-type:inline-size; display:grid; gap:6px; max-height:100%; min-height:0; overflow-y:auto; overscroll-behavior:contain; padding:2px 8px 2px 2px; }
/* 目录卡片：左侧色条代表状态，第二行末尾用同色文字声明状态；窄栏两行、宽栏追加归类 */
.capability-card { position:relative; display:grid; grid-template-columns:auto minmax(0,1fr) auto; grid-template-areas:'icon name state' 'icon desc desc'; column-gap:10px; row-gap:3px; align-items:center; width:100%; min-width:0; padding:10px 12px; border:1px solid var(--color-border-2); border-left-width:3px; border-radius:10px; background:var(--color-bg-1); color:var(--color-text-1); text-align:left; transition:border-color .18s ease, background-color .18s ease, transform .18s cubic-bezier(.32,.72,0,1), box-shadow .18s ease; animation:capability-card-in .32s cubic-bezier(.32,.72,0,1) both; animation-delay:calc(var(--card-index, 0) * 24ms); }
.capability-card:hover { transform:translateY(-2px); background:var(--color-fill-1); box-shadow:0 6px 18px -12px oklch(0 0 0 / 45%); }
.capability-card:hover .capability-card__icon { transform:scale(1.06) rotate(-2deg); }
.capability-card.is-selected { background:var(--color-fill-2); border-color:var(--color-text-3); }
.capability-card__icon { display:grid; grid-area:icon; width:30px; height:30px; place-items:center; border-radius:9px; background:var(--color-fill-2); color:var(--color-text-2); font-size:16px; transition:transform .18s cubic-bezier(.32,.72,0,1); }
.capability-card__name { grid-area:name; overflow:hidden; font-size:14px; font-weight:600; text-overflow:ellipsis; white-space:nowrap; }
.capability-card__description { grid-area:desc; overflow:hidden; color:var(--color-text-3); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.capability-card__state { display:flex; grid-area:state; justify-self:end; align-items:center; font-size:11.5px; white-space:nowrap; }
.capability-card__meta { display:none; color:var(--color-text-3); font-size:11px; white-space:nowrap; }
.capability-card__chevron { display:none; color:var(--color-text-3); }

/* 状态即卡片配色：运行中用强调色，未启用走中性弱化，异常用危险色并轻微染色 */
.capability-card[data-status='ENABLED'] { border-left-color:var(--yd-emphasis, var(--color-text-1)); }
.capability-card[data-status='ENABLED'] .capability-card__state { color:var(--yd-emphasis, var(--color-text-1)); }
.capability-card[data-status='DISABLED'] { border-left-color:var(--color-border-3); }
.capability-card[data-status='DISABLED'] .capability-card__state { color:var(--color-text-3); }
.capability-card[data-status='DISABLED'] .capability-card__icon { opacity:.72; }
.capability-card[data-status='ERROR'] { border-left-color:var(--color-danger-6); background:color-mix(in oklab, var(--color-danger-6) 6%, var(--color-bg-1)); }
.capability-card[data-status='ERROR'] .capability-card__state { color:var(--color-danger-6); font-weight:600; }
@keyframes capability-card-in { from { opacity:0; transform:translateY(8px); } to { opacity:1; transform:none; } }

/* 容器够宽才追加归类与右箭头，窄栏保持两行不挤压 */
@container (min-width: 400px) {
  .capability-card { grid-template-columns:auto minmax(0,1fr) auto auto; grid-template-areas:'icon name state chevron' 'icon desc meta chevron'; }
  .capability-card__meta { display:block; grid-area:meta; }
  .capability-card__chevron { display:block; grid-area:chevron; align-self:center; transition:transform .18s cubic-bezier(.32,.72,0,1); }
  .capability-card:hover .capability-card__chevron { transform:translateX(3px); color:var(--color-text-1); }
}
/* align-content:start：grid 默认会把行拉伸填满容器，短内容会被撑出大片空白。 */
.capability-detail { display:grid; gap:20px; align-content:start; container-type:inline-size; min-width:0; min-height:0; overflow-y:auto; overscroll-behavior:contain; padding:20px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-2); scrollbar-gutter:stable; }
.detail-header { display:flex; gap:16px; align-items:flex-start; justify-content:space-between; }
.detail-title { display:flex; min-width:0; gap:12px; align-items:center; }
.detail-title h2 { margin:0 0 4px; font-size:18px; font-weight:650; }
.detail-title p { margin:0; line-height:1.5; }
.detail-actions { display:flex; flex-wrap:wrap; gap:8px; justify-content:flex-end; }
.detail-grid,.metric-grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(100%,160px),1fr)); gap:10px; }
/* 宽详情里四条基础信息排成一行，避免 3+1 的落单换行。 */
@container (min-width: 520px) {
  .detail-grid { grid-template-columns:repeat(4,minmax(0,1fr)); }
}
/* 信息卡：值是标签时必须靠上靠左，grid 默认拉伸会把标签撑成大色块。 */
.info-cell { display:grid; gap:8px; align-content:start; justify-items:start; padding:12px 14px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-1); }
.info-cell > span { color:var(--color-text-3); font-size:12px; }
.info-cell > strong { color:var(--color-text-1); font-size:15px; font-weight:600; overflow-wrap:anywhere; }
.info-cell code { color:var(--color-text-2); font-size:13px; overflow-wrap:anywhere; }

/* 健康信息：概览（环形图 + 关键数字）与逐行事实清单，避免一格一卡的堆叠 */
.health-overview { display:grid; grid-template-columns:auto minmax(0,1fr); gap:20px; align-items:center; padding:16px 18px; border:1px solid var(--color-border-2); border-radius:12px; background:linear-gradient(180deg, var(--color-fill-1), var(--color-bg-1)); }
.health-overview__body { display:grid; gap:12px; min-width:0; }
.health-overview__stats { display:flex; flex-wrap:wrap; gap:28px; margin:0; padding:0; list-style:none; }
.health-overview__stats li { display:grid; gap:2px; }
.health-overview__stats span { color:var(--color-text-3); font-size:12px; }
.health-overview__stats strong { color:var(--color-text-1); font-size:26px; font-weight:650; line-height:1.15; }
.health-overview__stats .yd-number-flow { font-size:26px; font-weight:650; color:var(--color-text-1); }
.health-overview__legend { display:flex; flex-wrap:wrap; gap:16px; margin:0; padding:0; list-style:none; }
.health-overview__legend li { display:inline-flex; gap:6px; align-items:center; color:var(--color-text-2); font-size:13px; }
.health-overview__legend i { width:9px; height:9px; border-radius:3px; background:var(--yd-emphasis, var(--color-text-1)); }
.health-overview__legend i[data-usage='embedding'] { opacity:.62; }
.health-overview__legend i[data-usage='rerank'] { opacity:.32; }
.health-overview__legend strong { color:var(--color-text-1); font-weight:600; }
.health-overview__legend small { color:var(--color-text-3); }
.health-donut { display:grid; width:104px; height:104px; place-items:center; border-radius:50%; }
.health-donut__hole { display:grid; width:70px; height:70px; place-content:center; border-radius:50%; background:var(--color-bg-2); text-align:center; }
.health-donut__hole strong { color:var(--color-text-1); font-size:20px; font-weight:650; line-height:1.1; }
.health-donut__hole .yd-number-flow { font-size:20px; font-weight:650; color:var(--color-text-1); }
.health-donut__hole span { color:var(--color-text-3); font-size:11px; }

.health-facts { display:grid; gap:0; margin:0; padding:0; list-style:none; }
.health-facts li { display:grid; grid-template-columns:minmax(96px, 132px) minmax(0,1fr); gap:16px; align-items:center; padding:11px 2px; border-bottom:1px solid var(--color-border-2); }
.health-facts li:last-child { border-bottom:none; }
.health-facts__label { color:var(--color-text-3); font-size:13px; }
.health-facts__value { display:flex; flex-wrap:wrap; gap:6px; align-items:center; min-width:0; color:var(--color-text-1); font-size:14px; font-weight:600; }
.health-facts__muted { color:var(--color-text-3); font-weight:400; }
.health-facts__link { display:inline-flex; gap:6px; align-items:center; color:var(--color-text-1); font-size:13px; overflow-wrap:anywhere; text-decoration:underline; text-decoration-color:var(--color-border-3); text-underline-offset:3px; }
.health-facts__path { padding:3px 8px; border-radius:6px; background:var(--color-fill-2); color:var(--color-text-2); font-size:12px; overflow-wrap:anywhere; }

.health-empty { display:flex; flex-wrap:wrap; gap:14px; align-items:center; padding:18px; border:1px dashed var(--color-border-2); border-radius:12px; background:var(--color-fill-1); }
.health-empty strong { color:var(--color-text-1); font-size:14px; }
.health-empty p { margin:6px 0 0; color:var(--color-text-3); font-size:12px; line-height:1.6; }

.health-config { display:grid; gap:10px; }
.health-config ul { display:grid; gap:8px; margin:0; padding:0; list-style:none; }
.health-config li { display:grid; grid-template-columns:auto minmax(0,1fr) auto auto; gap:10px; align-items:center; padding:9px 12px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-1); }
.health-config li > span { color:var(--color-text-3); font-size:12px; }
.health-config code { overflow:hidden; color:var(--color-text-1); font-size:12px; text-overflow:ellipsis; white-space:nowrap; }
.health-config__open { color:var(--color-text-2); font-size:12px; text-decoration:underline; text-underline-offset:3px; }
.dependency-panel,.health-panel,.debug-panel { display:grid; gap:12px; padding-top:18px; border-top:1px solid var(--color-border-2); }
.section-title { display:flex; gap:8px; align-items:center; justify-content:space-between; color:var(--color-text-1); font-weight:650; }
.section-title span { display:inline-flex; gap:6px; align-items:center; }
/* 依赖能力：进度条 + 可点击跳转的依赖行 */
.dependency-progress { display:flex; gap:12px; align-items:center; }
.dependency-progress__bar { flex:1 1 auto; min-width:120px; }
.dependency-progress > span { color:var(--color-text-3); font-size:12px; white-space:nowrap; }
.dependency-list { display:grid; gap:8px; }
.dependency-item { display:grid; grid-template-columns:32px minmax(0,1fr) auto 16px; gap:12px; align-items:center; width:100%; min-width:0; padding:10px 12px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-1); text-align:left; transition:border-color .16s ease, background-color .16s ease, transform .16s cubic-bezier(.32,.72,0,1); }
.dependency-item:hover { transform:translateX(2px); border-color:var(--color-border-3); background:var(--color-fill-1); }
.dependency-item:hover .dependency-item__jump { opacity:1; transform:none; }
.dependency-item.is-missing { border-style:dashed; }
.dependency-item__icon { display:grid; width:30px; height:30px; place-items:center; border-radius:8px; background:var(--color-fill-2); color:var(--color-text-2); font-size:16px; }
.dependency-item__body { display:grid; gap:2px; min-width:0; }
.dependency-item__body strong { color:var(--color-text-1); font-size:13px; font-weight:600; }
.dependency-item__body code { color:var(--color-text-3); font-size:11px; }
.dependency-item__jump { opacity:0; color:var(--color-text-3); transform:translateX(-3px); transition:opacity .16s ease, transform .16s cubic-bezier(.32,.72,0,1); }
.dependency-tip { margin:0; color:var(--color-text-3); font-size:12px; }
.dependency-tip:not(:empty) { color:var(--color-text-3); }
.dependency-panel .dependency-tip { color:var(--color-danger-6); }
.dependency-panel .dependency-tip:only-child { color:var(--color-text-3); }
.debug-row { display:flex; flex-wrap:wrap; gap:8px; align-items:center; }
.debug-row > span { color:var(--color-text-2); }
.debug-message { flex:1 1 220px; min-width:180px; }
.empty-state { padding:20px; color:var(--color-text-3); text-align:center; }
.config-body { display:grid; gap:16px; margin-top:16px; }
.config-form { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(100%,260px),1fr)); gap:16px; }
.config-field { display:grid; gap:7px; min-width:0; }
.config-field > span { color:var(--color-text-2); font-size:13px; font-weight:600; }
.config-field__hint { color:var(--color-text-3); font-size:12px; font-weight:400; line-height:1.5; }
.config-footer { display:flex; gap:8px; justify-content:flex-end; margin-top:20px; padding-top:14px; border-top:1px solid var(--color-border-2); }
/* 雅致/紧凑外观与系统「减少动态效果」下关闭位移与入场动画 */
:global([data-admin-style='calm']) .capability-card,
:global([data-admin-style='compact']) .capability-card { animation:none; }
@media (prefers-reduced-motion: reduce) {
  .capability-card { animation:none; }
  .capability-card:hover,
  .health-metric:hover,
  .dependency-item:hover { transform:none; }
  .capability-card__dot.is-live { animation:none; }
}
@media (max-width: 760px) {
  .capability-workbench { grid-template-columns:minmax(0,1fr); height:auto; min-height:0; }
  .capability-browser { grid-template-rows:auto auto auto; min-height:0; }
  .capability-catalog { grid-template-columns:repeat(2,minmax(0,1fr)); max-height:min(560px, 58dvh); }
  .capability-browser :deep(.yd-loading-overlay),
  .capability-browser :deep(.yd-loading-overlay__content) { height:auto; }
  .capability-detail { max-height:none; overflow:visible; overscroll-behavior:auto; }
  .capability-card { grid-template-columns:36px minmax(0,1fr) 12px; gap:10px; padding:13px; }
  .capability-card__icon { width:36px; height:36px; }

  .capability-list-heading { align-items:flex-start; }
  .detail-header { flex-direction:column; align-items:stretch; }
  .detail-actions { justify-content:flex-start; }
  .config-footer { position:sticky; bottom:0; z-index:2; justify-content:stretch; padding:12px; background:var(--color-bg-2); }
  .config-footer > * { flex:1; }
}
@media (max-width: 420px) {
  .capability-toolbar { grid-template-columns:minmax(0,1fr); }
  .capability-search { grid-column:1; }
  .status-chip { padding:6px 10px; }
  .capability-list-heading { align-items:center; }
  .capability-catalog { grid-template-columns:minmax(0,1fr); }
  .capability-card { grid-template-columns:36px minmax(0,1fr) 12px; gap:10px; padding:13px; }
  .capability-card__icon { width:36px; height:36px; }
  .capability-detail { padding:16px; }
  .health-overview { grid-template-columns:minmax(0,1fr); justify-items:center; text-align:center; }
  .health-overview__stats { justify-content:center; }
  .health-facts li { grid-template-columns:minmax(0,1fr); gap:6px; }
  .detail-actions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }
  .debug-row > span { flex-basis:100%; }
}
</style>
