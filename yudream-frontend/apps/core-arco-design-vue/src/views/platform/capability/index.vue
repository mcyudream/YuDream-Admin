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

const selected = computed(() => rows.value.find(item => item.code === selectedCode.value) || rows.value[0])
const configFields = computed(() => fieldsOf(selected.value?.code))
const isAiConfig = computed(() => selected.value?.code === 'ai')
const usesStructuredConfig = computed(() => isAiConfig.value || configFields.value.length > 0)
const dependencyRows = computed(() => dependencyStatus(selected.value))
const missingDependencies = computed(() => dependencyRows.value.filter(item => !item.enabled))
const canEnableSelected = computed(() => !selected.value?.enabled && missingDependencies.value.length === 0)
const summary = computed(() => {
  const enabled = rows.value.filter(item => item.enabled).length
  const error = rows.value.filter(item => item.status === 'ERROR').length
  return [
    { label: '能力总数', value: rows.value.length, icon: 'morph:list-tree' },
    { label: '已启用', value: enabled, icon: 'morph:circle-check', tone: 'ok' },
    { label: '异常', value: error, icon: 'morph:triangle-alert', tone: error > 0 ? 'bad' : 'ok' },
    { label: '实时连接', value: connectionCount(), icon: 'morph:activity' },
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
        <YdInput v-model="testMessage" placeholder="测试消息内容" class="flex-1 min-w-0" />
      </div>

      <div class="summary-grid">
        <div v-for="item in summary" :key="item.label" class="summary-item">
          <div class="summary-icon" :class="{ ok: item.tone === 'ok', bad: item.tone === 'bad' }">
            <YdIcon :name="item.icon" />
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
              <span><YdIcon :name="group.icon" /> {{ group.title }}</span>
              <YdTag variant="secondary">
                {{ itemsByType(group.type).length }}
              </YdTag>
            </div>
            <YdButton
              v-for="item in itemsByType(group.type)"
              :key="item.code"
              variant="ghost"
              class="capability-item"
              :class="{ active: selected?.code === item.code }"
              type="button"
              :aria-pressed="selected?.code === item.code"
              @click="selectCapability(item)"
            >
              <YdIcon :name="item.icon || 'morph:puzzle'" />
              <span>{{ item.name }}</span>
              <YdTag :variant="statusVariant(item.status)">
                {{ statusText(item.status) }}
              </YdTag>
            </YdButton>
          </template>
        </section>

        <section v-if="selected" class="capability-detail">
          <template v-if="configEditing">
            <div class="detail-header">
              <div class="detail-title">
                <div class="detail-icon">
                  <YdIcon :name="selected.icon || 'morph:puzzle'" />
                </div>
                <div>
                  <h2>配置 {{ selected.name }}</h2>
                  <p>{{ typeText(selected.type) }} · {{ statusText(selected.status) }}</p>
                </div>
              </div>
              <div class="detail-actions">
                <YdButton variant="ghost" @click="cancelConfig">
                  取消
                </YdButton>
                <YdButton :loading="saving" @click="saveConfig">
                  <YdIcon name="morph:check" />
                  保存配置
                </YdButton>
              </div>
            </div>

            <div class="config-body">
              <!-- AI 能力：端点用途切换（聊天 / 向量化 / 重排序）与端点编辑 -->
              <AiEndpointConfig
                v-if="isAiConfig"
                v-model:endpoints="aiEndpoints"
                v-model:default-provider="configDraft.defaultProvider"
                v-model:default-model="configDraft.defaultModel"
                :capability-enabled="Boolean(selected?.enabled)"
              />

              <!-- 其他结构化配置 -->
              <div v-else-if="usesStructuredConfig" class="config-form">
                <label v-for="field in configFields" :key="field.key" class="config-field">
                  <span>{{ field.label }}</span>
                  <small v-if="selected?.secretConfigured?.[field.key]" class="config-field__hint">
                    已配置，留空不修改
                  </small>
                  <YdTextarea
                    v-if="field.type === 'textarea'"
                    v-model="configDraft[field.key]"
                    rows="5"
                    input-class="font-mono"
                    :placeholder="field.placeholder"
                  />
                  <YdInput
                    v-else
                    v-model="configDraft[field.key]"
                    :type="field.type || 'text'"
                    :placeholder="field.placeholder"
                  />
                </label>
              </div>
              <YdTextarea v-else v-model="configText" rows="12" class="w-full" input-class="font-mono min-h-72" />
            </div>

            <div class="config-footer">
              <YdButton variant="ghost" @click="cancelConfig">
                取消
              </YdButton>
              <YdButton :loading="saving" @click="saveConfig">
                <YdIcon name="morph:check" />
                保存配置
              </YdButton>
            </div>
          </template>

          <template v-else>
            <div class="detail-header">
              <div class="detail-title">
                <div class="detail-icon">
                  <YdIcon :name="selected.icon || 'morph:puzzle'" />
                </div>
                <div>
                  <h2>{{ selected.name }}</h2>
                  <p>{{ selected.description }}</p>
                </div>
              </div>
              <div class="detail-actions">
                <YdButton v-auth="'platform:capability:config'" variant="outline" @click="openConfig(selected)">
                  <YdIcon name="morph:settings-2" />
                  配置
                </YdButton>
                <YdButton
                  v-auth="'platform:capability:test'"
                  variant="outline"
                  :disabled="!selected.enabled"
                  :loading="actionLoading === `${selected.code}:test`"
                  @click="testCapability(selected)"
                >
                  <YdIcon name="morph:send" />
                  测试
                </YdButton>
                <YdButton
                  v-auth="selected.enabled ? 'platform:capability:disable' : 'platform:capability:enable'"
                  :variant="selected.enabled ? 'destructive' : 'default'"
                  :disabled="!selected.enabled && !canEnableSelected"
                  :loading="actionLoading === `${selected.code}:toggle`"
                  @click="toggleCapability(selected)"
                >
                  <YdIcon :name="selected.enabled ? 'morph:circle-pause' : 'morph:circle-play'" />
                  {{ selected.enabled ? '禁用' : '启用' }}
                </YdButton>
              </div>
            </div>

            <div class="detail-grid">
              <div class="info-cell">
                <span>类型</span>
                <strong>{{ typeText(selected.type) }}</strong>
              </div>
              <div class="info-cell">
                <span>状态</span>
                <YdTag :variant="statusVariant(selected.status)">
                  {{ statusText(selected.status) }}
                </YdTag>
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
                <YdTag :variant="missingDependencies.length ? 'destructive' : 'default'">
                  {{ missingDependencies.length ? '未满足' : '已满足' }}
                </YdTag>
              </div>
              <div class="dependency-list">
                <div v-for="dependency in dependencyRows" :key="dependency.code" class="dependency-item">
                  <div>
                    <strong>{{ dependency.name }}</strong>
                    <code>{{ dependency.code }}</code>
                  </div>
                  <YdTag :variant="dependency.enabled ? 'default' : 'secondary'">
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
                  {{ selected.healthMessage || '-' }}
                </YdTag>
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
                <YdButton size="sm" variant="outline" :disabled="!selected.enabled" @click="connectSse">
                  连接
                </YdButton>
                <YdButton size="sm" variant="ghost" @click="closeSse">
                  断开
                </YdButton>
              </div>
              <div v-if="selected.code === 'websocket'" class="debug-row">
                <span>WebSocket：{{ wsStatus }}</span>
                <YdButton size="sm" variant="outline" :disabled="!selected.enabled" @click="connectWs">
                  连接
                </YdButton>
                <YdButton size="sm" variant="ghost" @click="closeWs">
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
  border-color: var(--color-border-3);
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

.config-field__hint {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
}

/* 配置视图：替换只读详情，长表单底部操作常驻可达 */
.config-body {
  display: grid;
  gap: 16px;
  margin-top: 16px;
}

.config-footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px solid var(--color-border-2);
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

  .config-footer {
    justify-content: stretch;
  }

  .config-footer > * {
    flex: 1;
  }
}
</style>
