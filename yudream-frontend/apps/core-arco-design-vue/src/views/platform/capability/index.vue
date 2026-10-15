<script setup lang="ts">
import type { AiEndpointDraft } from './ai-endpoints'
import type { CapabilityItem, CapabilityStatus, CapabilityType } from '@/api/modules/platform-capability'
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
const statusOptions: { label: string, value: CapabilityStatus | 'ALL' }[] = [
  { label: '全部状态', value: 'ALL' },
  { label: '运行中', value: 'ENABLED' },
  { label: '未启用', value: 'DISABLED' },
  { label: '异常', value: 'ERROR' },
] as const
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
}

interface MetricEntry {
  key: string
  label: string
  type: 'tags' | 'boolean' | 'text'
  tags: string[]
  text: string
  ok: boolean
}

/** 指标值可能是数组、布尔或长字符串：转成可读的标签列表或文本，避免直接把原始 JSON 塞进卡片。 */
function metricEntries(item?: CapabilityItem): MetricEntry[] {
  return Object.entries(item?.metrics || {}).map(([key, value]) => {
    const label = metricLabels[key] || key
    if (Array.isArray(value)) {
      const tags = value.map(entry => (entry === null || entry === undefined ? '-' : String(entry))).filter(Boolean)
      return { key, label, type: 'tags' as const, tags, text: tags.join('、'), ok: true }
    }
    if (typeof value === 'boolean') {
      return { key, label, type: 'boolean' as const, tags: [], text: value ? '是' : '否', ok: value }
    }
    if (value && typeof value === 'object') {
      const text = Object.entries(value as Record<string, unknown>)
        .map(([innerKey, innerValue]) => `${metricLabels[innerKey] || innerKey} ${String(innerValue)}`)
        .join('；')
      return { key, label, type: 'text' as const, tags: [], text: text || '-', ok: true }
    }
    return { key, label, type: 'text' as const, tags: [], text: value === null || value === undefined || value === '' ? '-' : String(value), ok: true }
  })
}

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
      enabled: Boolean(dependency?.enabled),
      available: Boolean(dependency),
    }
  })
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
        <YdSelect v-model="selectedStatus" :options="statusOptions" class="capability-filter" />
        <YdButton v-if="keyword || selectedType !== 'ALL' || selectedStatus !== 'ALL'" variant="ghost" @click="clearFilters">
          重置筛选
        </YdButton>
      </div>

      <div class="capability-workbench">
        <section class="capability-browser">
          <div class="capability-overview">
            <div><span>能力总数</span><strong>{{ summary.total }}</strong></div>
            <div><span>运行中</span><strong>{{ summary.enabled }}</strong></div>
            <div><span>未启用</span><strong>{{ summary.disabled }}</strong></div>
            <div :class="{ 'has-errors': summary.error > 0 }">
              <span>异常</span><strong>{{ summary.error }}</strong>
            </div>
          </div>
          <div class="capability-list-heading">
            <div>
              <h2>能力目录</h2>
              <p>{{ filteredRows.length }} / {{ rows.length }} 项能力</p>
            </div>
            <YdButton variant="outline" :loading="loading" @click="load">
              <YdIcon name="morph:refresh-cw" />刷新
            </YdButton>
          </div>
          <YdLoadingOverlay :loading="loading">
            <div v-if="filteredRows.length" class="capability-catalog">
              <YdAction v-for="item in filteredRows" :key="item.code" class="capability-card" :class="{ 'is-selected': selected?.code === item.code }" :aria-pressed="selected?.code === item.code" @click="selectCapability(item)">
                <span class="capability-card__icon"><YdIcon :name="item.icon || 'morph:puzzle'" /></span>
                <span class="capability-card__content">
                  <span class="capability-card__topline"><strong>{{ item.name }}</strong><YdTag :variant="statusVariant(item.status)">{{ statusText(item.status) }}</YdTag></span>
                  <span class="capability-card__description">{{ item.description || '暂无说明' }}</span>
                  <span class="capability-card__meta"><span>{{ typeText(item.type) }}</span><code>{{ item.code }}</code></span>
                </span>
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
                <span>依赖能力</span><YdTag :variant="missingDependencies.length ? 'destructive' : 'default'">
                  {{ missingDependencies.length ? '未满足' : '已满足' }}
                </YdTag>
              </div>
              <div class="dependency-list">
                <div v-for="dependency in dependencyRows" :key="dependency.code" class="dependency-item">
                  <div><strong>{{ dependency.name }}</strong><code>{{ dependency.code }}</code></div><YdTag :variant="dependency.enabled ? 'default' : 'secondary'">
                    {{ dependency.enabled ? '已启用' : dependency.available ? '未启用' : '不可用' }}
                  </YdTag>
                </div>
              </div>
              <p v-if="missingDependencies.length" class="dependency-tip">
                当前能力需要先启用依赖能力：{{ missingDependencies.map(item => item.name).join('、') }}
              </p>
            </div>
            <div class="health-panel">
              <div class="section-title">
                <span>健康信息</span>
                <YdTag :variant="statusVariant(selected.status)">
                  {{ selected.healthMessage || statusText(selected.status) }}
                </YdTag>
              </div>
              <dl v-if="metricEntries(selected).length" class="metric-grid">
                <div v-for="metric in metricEntries(selected)" :key="metric.key" class="metric-item">
                  <dt>{{ metric.label }}</dt>
                  <dd>
                    <template v-if="metric.type === 'tags'">
                      <span v-if="!metric.tags.length" class="metric-empty">无</span>
                      <YdTag v-for="tag in metric.tags" v-else :key="tag" variant="secondary">
                        {{ tag }}
                      </YdTag>
                    </template>
                    <YdTag v-else-if="metric.type === 'boolean'" :variant="metric.ok ? 'default' : 'secondary'">
                      {{ metric.text }}
                    </YdTag>
                    <span v-else class="metric-text">{{ metric.text }}</span>
                  </dd>
                </div>
              </dl>
              <div v-else class="empty-state">
                暂无运行指标
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
.capability-browser { display:grid; grid-template-rows:auto auto minmax(0,1fr); gap:16px; min-width:0; min-height:0; }
.capability-browser :deep(.yd-loading-overlay) { min-height:0; height:100%; }
.capability-browser :deep(.yd-loading-overlay__content) { display:grid; grid-template-rows:minmax(0,1fr); min-height:0; height:100%; }
.capability-overview { display:grid; grid-template-columns:repeat(4,minmax(0,1fr)); border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-2); }
.capability-overview > div { display:grid; gap:4px; min-width:0; padding:12px; }
.capability-overview > div + div { border-left:1px solid var(--color-border-2); }
.capability-overview span { color:var(--color-text-3); font-size:11px; }
.capability-overview strong { color:var(--color-text-1); font-size:18px; font-weight:650; }
.capability-overview .has-errors strong { color:var(--color-danger-6); }
.capability-list-heading { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:0; }
.capability-list-heading h2 { margin:0; color:var(--color-text-1); font-size:18px; font-weight:650; }
.capability-list-heading p { margin:4px 0 0; color:var(--color-text-3); font-size:13px; }
.capability-catalog { display:grid; gap:8px; max-height:100%; min-height:0; overflow-y:auto; overscroll-behavior:contain; padding:2px 8px 2px 2px; }
.capability-card { display:grid; grid-template-columns:40px minmax(0,1fr) 16px; align-items:start; gap:12px; width:100%; min-width:0; padding:14px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-1); color:var(--color-text-1); text-align:left; transition:border-color .16s ease, background-color .16s ease; }
.capability-card:hover,.capability-card.is-selected { border-color:var(--color-border-3); background:var(--color-fill-1); }
.capability-card.is-selected { box-shadow:inset 3px 0 var(--color-border-3); }
.capability-card__icon { display:grid; width:40px; height:40px; place-items:center; border-radius:8px; background:var(--color-fill-2); color:var(--color-text-2); font-size:20px; }
.capability-card__content { display:grid; gap:7px; min-width:0; }
.capability-card__topline { display:flex; flex-wrap:wrap; align-items:center; justify-content:space-between; gap:8px; }
.capability-card__topline strong { font-size:14px; font-weight:600; }
.capability-card__description { display:-webkit-box; min-height:34px; overflow:hidden; color:var(--color-text-3); font-size:12px; line-height:1.5; -webkit-box-orient:vertical; -webkit-line-clamp:2; }
.capability-card__meta { display:flex; flex-wrap:wrap; gap:8px; align-items:center; color:var(--color-text-3); font-size:11px; }
.capability-card__meta code { padding:2px 6px; border-radius:4px; background:var(--color-fill-2); }
.capability-card__chevron { align-self:center; color:var(--color-text-3); }
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

/* 健康指标：一行一个指标，标签在上、值在下，数组展开为标签，避免原始 JSON 撑破卡片 */
.metric-grid { grid-template-columns:repeat(auto-fit,minmax(min(100%,240px),1fr)); align-content:start; }
.metric-item { display:grid; gap:8px; min-width:0; padding:12px; border:1px solid var(--color-border-2); border-radius:8px; background:var(--color-bg-1); }
.metric-item dt { color:var(--color-text-3); font-size:12px; }
.metric-item dd { display:flex; flex-wrap:wrap; gap:6px; align-items:center; margin:0; min-width:0; }
.metric-text { color:var(--color-text-1); font-size:14px; font-weight:600; line-height:1.5; overflow-wrap:anywhere; }
.metric-empty { color:var(--color-text-3); font-size:13px; }
.dependency-panel,.health-panel,.debug-panel { display:grid; gap:12px; padding-top:18px; border-top:1px solid var(--color-border-2); }
.section-title { display:flex; gap:8px; align-items:center; justify-content:space-between; color:var(--color-text-1); font-weight:650; }
.section-title span { display:inline-flex; gap:6px; align-items:center; }
.dependency-list { display:grid; gap:8px; }
.dependency-item { display:flex; gap:10px; align-items:center; justify-content:space-between; padding:10px 12px; border:1px solid var(--color-border-2); border-radius:7px; background:var(--color-bg-1); }
.dependency-item div { display:flex; min-width:0; flex-wrap:wrap; gap:8px; align-items:center; }
.dependency-item strong { color:var(--color-text-1); }
.dependency-tip { margin:0; color:var(--color-danger-6); font-size:12px; }
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
@media (max-width: 760px) {
  .capability-workbench { grid-template-columns:minmax(0,1fr); height:auto; min-height:0; }
  .capability-browser { grid-template-rows:auto auto auto; min-height:0; }
  .capability-catalog { grid-template-columns:repeat(2,minmax(0,1fr)); max-height:min(560px, 58dvh); }
  .capability-browser :deep(.yd-loading-overlay),
  .capability-browser :deep(.yd-loading-overlay__content) { height:auto; }
  .capability-detail { max-height:none; overflow:visible; overscroll-behavior:auto; }
  .capability-card { grid-template-columns:36px minmax(0,1fr) 12px; gap:10px; padding:13px; }
  .capability-card__icon { width:36px; height:36px; }
  .capability-overview > div { padding:10px; }
  .capability-overview strong { font-size:16px; }
  .capability-list-heading { align-items:flex-start; }
  .detail-header { flex-direction:column; align-items:stretch; }
  .detail-actions { justify-content:flex-start; }
  .config-footer { position:sticky; bottom:0; z-index:2; justify-content:stretch; padding:12px; background:var(--color-bg-2); }
  .config-footer > * { flex:1; }
}
@media (max-width: 420px) {
  .capability-toolbar { grid-template-columns:minmax(0,1fr); }
  .capability-search { grid-column:1; }
  .capability-overview > div { padding:8px 6px; }
  .capability-overview span { font-size:10px; }
  .capability-overview strong { font-size:15px; }
  .capability-list-heading { align-items:center; }
  .capability-catalog { grid-template-columns:minmax(0,1fr); }
  .capability-card { grid-template-columns:36px minmax(0,1fr) 12px; gap:10px; padding:13px; }
  .capability-card__icon { width:36px; height:36px; }
  .capability-detail { padding:16px; }
  .detail-actions { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); }
  .debug-row > span { flex-basis:100%; }
}
</style>
