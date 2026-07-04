<script setup lang="ts">
import type { CapabilityItem, CapabilityStatus, CapabilityType } from '@/api/modules/platform-capability'
import apiCapability from '@/api/modules/platform-capability'

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

const toast = useFaToast()

const loading = ref(false)
const actionLoading = ref('')
const rows = ref<CapabilityItem[]>([])
const selectedCode = ref('')
const configVisible = ref(false)
const configText = ref('{}')
const configDraft = ref<Record<string, string>>({})
const testMessage = ref('YuDream 平台能力测试消息')
const sseStatus = ref('未连接')
const wsStatus = ref('未连接')
let eventSource: EventSource | null = null
let websocket: WebSocket | null = null

const groups: CapabilityGroup[] = [
  { type: 'REALTIME', title: '实时通信', icon: 'i-ri:pulse-line' },
  { type: 'MESSAGING', title: '消息队列', icon: 'i-ri:message-3-line' },
  { type: 'DOCUMENTATION', title: '接口文档', icon: 'i-ri:file-list-3-line' },
  { type: 'INTEGRATION', title: '集成运行', icon: 'i-ri:terminal-box-line' },
  { type: 'DOCUMENT', title: '文档生成', icon: 'i-ri:file-word-2-line' },
  { type: 'CONTENT', title: '内容定制', icon: 'i-ri:layout-masonry-line' },
  { type: 'GRAPH', title: '图数据库', icon: 'i-ri:share-circle-line' },
  { type: 'AI', title: 'AI 助手', icon: 'i-ri:sparkling-2-line' },
]

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value) || rows.value[0])
const configFields = computed(() => fieldsOf(selected.value?.code))
const usesStructuredConfig = computed(() => configFields.value.length > 0)
const dependencyRows = computed(() => dependencyStatus(selected.value))
const missingDependencies = computed(() => dependencyRows.value.filter(item => !item.enabled))
const canEnableSelected = computed(() => !selected.value?.enabled && missingDependencies.value.length === 0)
const summary = computed(() => {
  const enabled = rows.value.filter(item => item.enabled).length
  const error = rows.value.filter(item => item.status === 'ERROR').length
  return [
    { label: '能力总数', value: rows.value.length, icon: 'i-ri:node-tree' },
    { label: '已启用', value: enabled, icon: 'i-ri:checkbox-circle-line', tone: 'ok' },
    { label: '异常', value: error, icon: 'i-ri:error-warning-line', tone: error > 0 ? 'bad' : 'ok' },
    { label: '实时连接', value: connectionCount(), icon: 'i-ri:pulse-line' },
  ]
})

onMounted(load)
onBeforeUnmount(() => {
  closeSse()
  closeWs()
})

async function load() {
  loading.value = true
  try {
    const res = await apiCapability.list()
    rows.value = res.data
    if (!selectedCode.value && rows.value.length) {
      selectedCode.value = rows.value[0].code
    }
  }
  finally {
    loading.value = false
  }
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
    toast.success(item.enabled ? '已禁用' : '已启用')
  }
  finally {
    actionLoading.value = ''
  }
}

function openConfig(item: CapabilityItem) {
  selectedCode.value = item.code
  configDraft.value = { ...(item.config || {}) }
  configText.value = JSON.stringify(item.config || {}, null, 2)
  configVisible.value = true
}

async function saveConfig() {
  if (!selected.value) {
    return
  }
  const config = usesStructuredConfig.value ? normalizedConfig() : parseJsonConfig()
  if (!config) {
    return
  }
  const res = await apiCapability.updateConfig(selected.value.code, config)
  replaceItem(res.data)
  configVisible.value = false
  toast.success('配置已保存')
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

function connectSse() {
  closeSse()
  eventSource = new EventSource(httpEndpoint('/api/platform/sse/connect'))
  sseStatus.value = '连接中'
  eventSource.addEventListener('connected', event => sseStatus.value = `已连接：${(event as MessageEvent).data}`)
  eventSource.addEventListener('capability-test', event => toast.info('SSE 消息', { description: (event as MessageEvent).data }))
  eventSource.onerror = () => sseStatus.value = '连接异常'
}

function closeSse() {
  eventSource?.close()
  eventSource = null
  sseStatus.value = '未连接'
}

function connectWs() {
  closeWs()
  websocket = new WebSocket(wsEndpoint('/api/platform/ws'))
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

function itemsByType(type: CapabilityType) {
  return rows.value.filter(item => item.type === type)
}

function connectionCount() {
  return rows.value.reduce((total, item) => {
    const value = item.metrics?.connections ?? item.metrics?.sessions ?? 0
    return total + Number(value || 0)
  }, 0)
}

function httpEndpoint(path: string) {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) {
    return `/proxy${path}`
  }
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}${path}`
}

function wsEndpoint(path: string) {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    return `${protocol}://${window.location.host}/proxy${path}`
  }
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

function metricEntries(item?: CapabilityItem) {
  return Object.entries(item?.metrics || {})
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
    sse: [
      { key: 'timeout', label: '连接超时（毫秒）', placeholder: '300000', type: 'number' },
    ],
    rabbitmq: [
      { key: 'host', label: '主机', placeholder: 'localhost' },
      { key: 'port', label: '端口', placeholder: '35672', type: 'number' },
      { key: 'username', label: '用户名', placeholder: 'guest' },
      { key: 'password', label: '密码', placeholder: 'guest', type: 'password' },
      { key: 'virtualHost', label: 'Virtual Host', placeholder: '/' },
      { key: 'exchange', label: '交换机', placeholder: 'yudream.capability' },
      { key: 'queue', label: '队列', placeholder: 'yudream.capability.test' },
      { key: 'routingKey', label: '路由键', placeholder: 'capability.test' },
    ],
    ai: [
      { key: 'baseUrl', label: '接口地址', placeholder: 'https://api.openai.com/v1' },
      { key: 'apiKey', label: 'API Key', placeholder: 'sk-...', type: 'password' },
      { key: 'model', label: '默认生成模型', placeholder: 'gpt-4o-mini' },
      { key: 'models', label: '可选生成模型', placeholder: 'gpt-4o-mini,deepseek-chat,qwen-vl-plus' },
      { key: 'temperature', label: '温度', placeholder: '0.4', type: 'number' },
      { key: 'thinkingEnabled', label: '深度思考', placeholder: 'false' },
      { key: 'extraBody', label: 'Extra Body', placeholder: '{ "enable_thinking": {{thinkingEnabled}} }', type: 'textarea' },
      { key: 'embeddingModel', label: '向量化模型', placeholder: 'text-embedding-3-small' },
      { key: 'rerankModel', label: '重排模型', placeholder: 'bge-reranker-v2' },
      { key: 'proxyUrl', label: '代理地址', placeholder: 'http://127.0.0.1:7890' },
    ],
  }
  return code ? map[code] || [] : []
}

function normalizedConfig() {
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
    <FaPageHeader title="平台能力" class="mb-0">
      <template #description>
        管理 SSE、WebSocket、RabbitMQ、API 文档、集成运行等可选能力的启停、配置、健康状态与测试消息。
      </template>
    </FaPageHeader>

    <FaPageMain>
      <div class="capability-toolbar">
        <FaInput v-model="testMessage" placeholder="测试消息内容" class="min-w-0 flex-1" />
        <FaButton :loading="loading" @click="load">
          <FaIcon name="i-ri:refresh-line" />
          刷新
        </FaButton>
      </div>

      <div class="summary-grid">
        <div v-for="item in summary" :key="item.label" class="summary-item">
          <div class="summary-icon" :class="{ ok: item.tone === 'ok', bad: item.tone === 'bad' }">
            <FaIcon :name="item.icon" />
          </div>
          <div class="summary-body">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </div>
      </div>

      <div class="capability-layout">
        <section class="capability-list">
          <template v-for="group in groups" :key="group.type">
            <div v-if="itemsByType(group.type).length" class="section-title">
              <span><FaIcon :name="group.icon" /> {{ group.title }}</span>
              <FaTag variant="secondary">{{ itemsByType(group.type).length }}</FaTag>
            </div>
            <button
              v-for="item in itemsByType(group.type)"
              :key="item.code"
              class="capability-item"
              :class="{ active: selected?.code === item.code }"
              type="button"
              @click="selectedCode = item.code"
            >
              <FaIcon :name="item.icon || 'i-ri:plugin-line'" />
              <span>{{ item.name }}</span>
              <FaTag :variant="statusVariant(item.status)">{{ statusText(item.status) }}</FaTag>
            </button>
          </template>
        </section>

        <section v-if="selected" class="capability-detail">
          <div class="detail-header">
            <div class="detail-title">
              <div class="detail-icon">
                <FaIcon :name="selected.icon || 'i-ri:plugin-line'" />
              </div>
              <div>
                <h2>{{ selected.name }}</h2>
                <p>{{ selected.description }}</p>
              </div>
            </div>
            <div class="detail-actions">
              <FaButton v-auth="'platform:capability:config'" variant="outline" @click="openConfig(selected)">
                <FaIcon name="i-ri:settings-4-line" />
                配置
              </FaButton>
              <FaButton
                v-auth="'platform:capability:test'"
                variant="outline"
                :disabled="!selected.enabled"
                :loading="actionLoading === `${selected.code}:test`"
                @click="testCapability(selected)"
              >
                <FaIcon name="i-ri:send-plane-line" />
                测试
              </FaButton>
              <FaButton
                v-auth="selected.enabled ? 'platform:capability:disable' : 'platform:capability:enable'"
                :variant="selected.enabled ? 'destructive' : 'default'"
                :disabled="!selected.enabled && !canEnableSelected"
                :loading="actionLoading === `${selected.code}:toggle`"
                @click="toggleCapability(selected)"
              >
                <FaIcon :name="selected.enabled ? 'i-ri:pause-circle-line' : 'i-ri:play-circle-line'" />
                {{ selected.enabled ? '禁用' : '启用' }}
              </FaButton>
            </div>
          </div>

          <div class="detail-grid">
            <div class="info-cell">
              <span>类型</span>
              <strong>{{ typeText(selected.type) }}</strong>
            </div>
            <div class="info-cell">
              <span>状态</span>
              <FaTag :variant="statusVariant(selected.status)">{{ statusText(selected.status) }}</FaTag>
            </div>
            <div class="info-cell">
              <span>编码</span>
              <code>{{ selected.code }}</code>
            </div>
            <div class="info-cell">
              <span>检查时间</span>
              <strong>{{ selected.checkedAt || '-' }}</strong>
            </div>
          </div>

          <div v-if="dependencyRows.length" class="dependency-panel">
            <div class="section-title">
              <span>依赖能力</span>
              <FaTag :variant="missingDependencies.length ? 'destructive' : 'default'">
                {{ missingDependencies.length ? '未满足' : '已满足' }}
              </FaTag>
            </div>
            <div class="dependency-list">
              <div v-for="dependency in dependencyRows" :key="dependency.code" class="dependency-item">
                <div>
                  <strong>{{ dependency.name }}</strong>
                  <code>{{ dependency.code }}</code>
                </div>
                <FaTag :variant="dependency.enabled ? 'default' : 'secondary'">
                  {{ dependency.enabled ? '已启用' : dependency.available ? '未启用' : '不可用' }}
                </FaTag>
              </div>
            </div>
            <p v-if="missingDependencies.length" class="dependency-tip">
              当前能力需要先启用依赖能力：{{ missingDependencies.map(item => item.name).join('、') }}
            </p>
          </div>

          <div class="health-panel">
            <div class="section-title">
              <span>健康信息</span>
              <FaTag :variant="statusVariant(selected.status)">{{ selected.healthMessage || '-' }}</FaTag>
            </div>
            <div v-if="metricEntries(selected).length" class="metric-grid">
              <div v-for="[key, value] in metricEntries(selected)" :key="key" class="metric-item">
                <span>{{ key }}</span>
                <strong>{{ value }}</strong>
              </div>
            </div>
            <div v-else class="empty-state">
              暂无运行指标
            </div>
          </div>

          <div v-if="selected.code === 'sse' || selected.code === 'websocket'" class="debug-panel">
            <div class="section-title">
              <span>连接调试</span>
            </div>
            <div v-if="selected.code === 'sse'" class="debug-row">
              <span>SSE：{{ sseStatus }}</span>
              <FaButton size="sm" variant="outline" :disabled="!selected.enabled" @click="connectSse">连接</FaButton>
              <FaButton size="sm" variant="ghost" @click="closeSse">断开</FaButton>
            </div>
            <div v-if="selected.code === 'websocket'" class="debug-row">
              <span>WebSocket：{{ wsStatus }}</span>
              <FaButton size="sm" variant="outline" :disabled="!selected.enabled" @click="connectWs">连接</FaButton>
              <FaButton size="sm" variant="ghost" @click="closeWs">断开</FaButton>
            </div>
          </div>
        </section>
      </div>
    </FaPageMain>

    <FaModal v-model="configVisible" title="能力配置" show-cancel-button class="sm:max-w-2xl" @confirm="saveConfig">
      <div v-if="usesStructuredConfig" class="config-form">
        <label v-for="field in configFields" :key="field.key" class="config-field">
          <span>{{ field.label }}</span>
          <FaTextarea
            v-if="field.type === 'textarea'"
            v-model="configDraft[field.key]"
            rows="5"
            input-class="font-mono"
            :placeholder="field.placeholder"
          />
          <FaInput
            v-else
            v-model="configDraft[field.key]"
            :type="field.type || 'text'"
            :placeholder="field.placeholder"
          />
        </label>
      </div>
      <FaTextarea v-else v-model="configText" rows="12" class="w-full" input-class="font-mono min-h-72" />
    </FaModal>
  </div>
</template>

<style scoped>
.capability-toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 14px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.summary-item,
.capability-list,
.capability-detail {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.summary-item {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 12px;
}

.summary-icon,
.detail-icon {
  display: grid;
  width: 38px;
  height: 38px;
  flex: none;
  place-items: center;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 19px;
}

.summary-body,
.info-cell,
.metric-item {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.summary-body span,
.info-cell span,
.metric-item span,
.detail-title p {
  color: var(--color-text-3);
  font-size: 12px;
}

.summary-body strong,
.info-cell strong,
.metric-item strong {
  color: var(--color-text-1);
  font-weight: 700;
}

.capability-layout {
  display: grid;
  grid-template-columns: minmax(260px, 330px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.capability-list {
  display: grid;
  gap: 8px;
  padding: 12px;
}

.section-title {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-1);
  font-weight: 700;
}

.section-title span {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.capability-item {
  display: grid;
  grid-template-columns: 22px minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 44px;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-1);
  text-align: left;
}

.capability-item.active,
.capability-item:hover {
  border-color: rgb(var(--primary-6));
  background: var(--color-fill-2);
}

.capability-item span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.capability-detail {
  min-width: 0;
  padding: 16px;
}

.detail-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.detail-title {
  display: flex;
  min-width: 0;
  gap: 12px;
}

.detail-title h2 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 700;
}

.detail-title p {
  margin: 0;
}

.detail-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.detail-grid,
.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: 10px;
  margin-top: 16px;
}

.info-cell,
.metric-item {
  min-height: 72px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.dependency-panel,
.health-panel,
.debug-panel {
  display: grid;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border-2);
}

.dependency-list {
  display: grid;
  gap: 8px;
}

.dependency-item {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.dependency-item div {
  display: flex;
  min-width: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.dependency-item strong {
  color: var(--color-text-1);
}

.dependency-tip {
  margin: 0;
  color: var(--color-danger-6);
  font-size: 12px;
}

.debug-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.debug-row span {
  min-width: 220px;
  color: var(--color-text-2);
}

.empty-state {
  padding: 24px;
  border: 1px dashed var(--color-border-2);
  border-radius: 6px;
  color: var(--color-text-3);
  text-align: center;
}

.config-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
}

.config-field {
  display: grid;
  gap: 6px;
}

.config-field span {
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
}

.ok {
  color: rgb(var(--success-6));
}

.bad {
  color: rgb(var(--danger-6));
}

@media (max-width: 900px) {
  .capability-toolbar,
  .detail-header {
    flex-direction: column;
    align-items: stretch;
  }

  .capability-layout {
    grid-template-columns: 1fr;
  }

  .detail-actions {
    justify-content: flex-start;
  }
}
</style>
