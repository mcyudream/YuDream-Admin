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

type AiProviderType = 'OPENAI' | 'OPENAI_COMPATIBLE' | 'KIMI' | 'DEEPSEEK'

interface AiModelDraft {
  code: string
  name: string
  model: string
  temperature: string
  reasoningEffort: string
  thinkingEnabled: boolean
  extraBody: string
  kind: string
  vision: boolean
}

interface AiProviderDraft {
  code: string
  name: string
  type: AiProviderType
  baseUrl: string
  completionsPath: string
  apiKey: string
  proxyUrl: string
  defaultModel: string
  temperature: string
  extraBody: string
  embeddingModelsText: string
  rerankModelsText: string
  enabled: boolean
  models: AiModelDraft[]
}

const toast = useFaToast()

const loading = ref(false)
const actionLoading = ref('')
const rows = ref<CapabilityItem[]>([])
const selectedCode = ref('')
const configVisible = ref(false)
const configText = ref('{}')
const configDraft = ref<Record<string, string>>({})
const aiProviders = ref<AiProviderDraft[]>([])
const testMessage = ref('YuDream 平台能力测试消息')
const sseStatus = ref('未连接')
const wsStatus = ref('未连接')
let eventSource: EventSource | null = null
let websocket: WebSocket | null = null

const aiProviderTypeOptions: { label: string, value: AiProviderType }[] = [
  { label: 'OpenAI', value: 'OPENAI' },
  { label: 'OpenAI 兼容', value: 'OPENAI_COMPATIBLE' },
  { label: 'Kimi', value: 'KIMI' },
  { label: 'DeepSeek', value: 'DEEPSEEK' },
]

const aiModelKindOptions = [
  { label: '对话', value: 'chat' },
  { label: 'Embedding', value: 'embedding' },
  { label: 'Rerank', value: 'rerank' },
]

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
const isAiConfig = computed(() => selected.value?.code === 'ai')
const usesStructuredConfig = computed(() => isAiConfig.value || configFields.value.length > 0)
const dependencyRows = computed(() => dependencyStatus(selected.value))
const missingDependencies = computed(() => dependencyRows.value.filter(item => !item.enabled))
const canEnableSelected = computed(() => !selected.value?.enabled && missingDependencies.value.length === 0)
const aiProviderOptions = computed(() => aiProviders.value.map((provider, index) => ({
  label: provider.name || provider.code || `供应商 ${index + 1}`,
  value: provider.code,
})).filter(item => item.value))
const aiDefaultModelOptions = computed(() => modelOptions(
  aiProviders.value.find(provider => provider.code === configDraft.value.defaultProvider) || aiProviders.value[0],
))
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
watch(() => configDraft.value.defaultProvider, () => {
  if (isAiConfig.value) {
    syncAiDefaultModel()
  }
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
  if (item.code === 'ai' && !configDraft.value.providers) {
    configDraft.value.providers = legacyAiProvidersJson(item.config || {})
    configDraft.value.defaultProvider ||= 'default'
    configDraft.value.defaultModel ||= item.config?.model || 'gpt-4o-mini'
  }
  if (item.code === 'ai') {
    aiProviders.value = parseAiProvidersConfig(configDraft.value)
    syncAiDefaults()
  }
  configText.value = JSON.stringify(item.config || {}, null, 2)
  configVisible.value = true
}

function legacyAiProvidersJson(config: Record<string, string>) {
  const model = config.model || 'gpt-4o-mini'
  const models = (config.models || model)
    .split(/[,，\n\r]/)
    .map(item => item.trim())
    .filter(Boolean)
  return JSON.stringify([
    {
      code: config.providerCode || 'default',
      name: config.providerName || 'Default AI',
      type: config.providerType || legacyAiProviderType(config.baseUrl || ''),
      baseUrl: config.baseUrl || 'https://api.openai.com/v1',
      apiKey: config.apiKey || '',
      proxyUrl: config.proxyUrl || '',
      defaultModel: model,
      temperature: config.temperature || '0.4',
      extraBody: config.extraBody || '',
      models: models.map(value => ({
        code: value,
        name: value,
        model: value,
        thinkingEnabled: config.thinkingEnabled === 'true',
        reasoningEffort: config.reasoningEffort || '',
      })),
      embeddingModels: config.embeddingModel ? [config.embeddingModel] : [],
      rerankModels: config.rerankModel ? [config.rerankModel] : [],
    },
  ], null, 2)
}

function legacyAiProviderType(baseUrl: string) {
  const value = baseUrl.toLowerCase()
  if (value.includes('moonshot') || value.includes('kimi')) {
    return 'KIMI'
  }
  if (value.includes('deepseek')) {
    return 'DEEPSEEK'
  }
  return 'OPENAI_COMPATIBLE'
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
      { key: 'providers', label: '供应商列表 JSON', placeholder: '[{ "code": "kimi", "type": "KIMI", "baseUrl": "https://api.moonshot.cn/v1", "apiKey": "", "models": [] }]', type: 'textarea' },
      { key: 'defaultProvider', label: '默认供应商编码', placeholder: 'openai' },
      { key: 'defaultModel', label: '默认模型编码', placeholder: 'gpt-4o-mini' },
    ],
  }
  return code ? map[code] || [] : []
}

function parseAiProvidersConfig(config: Record<string, string>) {
  try {
    const parsed = JSON.parse(config.providers || legacyAiProvidersJson(config))
    if (!Array.isArray(parsed)) {
      toast.error('AI 供应商配置需要是数组')
      return [createAiProvider()]
    }
    const providers = parsed.map((item, index) => normalizeAiProvider(item, index))
    return providers.length ? providers : [createAiProvider()]
  }
  catch {
    toast.error('AI 供应商配置格式错误', { description: '已使用默认供应商模板，请检查后保存' })
    return [createAiProvider()]
  }
}

function normalizeAiProvider(raw: unknown, index = 0): AiProviderDraft {
  const data = objectRecord(raw)
  const defaultCode = index === 0 ? 'openai' : `provider-${index + 1}`
  const defaultModel = textValue(data.defaultModel, textValue(data.model, 'gpt-4o-mini'))
  const rawModels = Array.isArray(data.models) ? data.models : [defaultModel]
  const models = rawModels.map((item, modelIndex) => normalizeAiModel(item, modelIndex)).filter(model => model.code || model.model)
  if (!models.length) {
    models.push(createAiModel(defaultModel))
  }
  return {
    code: textValue(data.code, defaultCode),
    name: textValue(data.name, defaultCode),
    type: normalizeAiProviderType(textValue(data.type, 'OPENAI_COMPATIBLE')),
    baseUrl: textValue(data.baseUrl, 'https://api.openai.com/v1'),
    completionsPath: textValue(data.completionsPath, '/chat/completions'),
    apiKey: textValue(data.apiKey),
    proxyUrl: textValue(data.proxyUrl),
    defaultModel: textValue(data.defaultModel, models[0]?.code || models[0]?.model || defaultModel),
    temperature: textValue(data.temperature, '0.4'),
    extraBody: extraBodyValue(data.extraBody),
    embeddingModelsText: toTextList(data.embeddingModels).join('\n'),
    rerankModelsText: toTextList(data.rerankModels).join('\n'),
    enabled: booleanValue(data.enabled, true),
    models,
  }
}

function normalizeAiModel(raw: unknown, index = 0): AiModelDraft {
  if (typeof raw === 'string') {
    return createAiModel(raw)
  }
  const data = objectRecord(raw)
  const model = textValue(data.model, textValue(data.name, textValue(data.code, `model-${index + 1}`)))
  return {
    code: textValue(data.code, model),
    name: textValue(data.name, model),
    model,
    temperature: textValue(data.temperature),
    reasoningEffort: textValue(data.reasoningEffort),
    thinkingEnabled: booleanValue(data.thinkingEnabled, false),
    extraBody: extraBodyValue(data.extraBody),
    kind: textValue(data.kind, 'chat'),
    vision: booleanValue(data.vision, false),
  }
}

function createAiProvider(): AiProviderDraft {
  return {
    code: 'openai',
    name: 'OpenAI',
    type: 'OPENAI',
    baseUrl: 'https://api.openai.com/v1',
    completionsPath: '/chat/completions',
    apiKey: '',
    proxyUrl: '',
    defaultModel: 'gpt-4o-mini',
    temperature: '0.4',
    extraBody: '',
    embeddingModelsText: 'text-embedding-3-small',
    rerankModelsText: '',
    enabled: true,
    models: [createAiModel('gpt-4o-mini', 'GPT-4o mini')],
  }
}

function createAiModel(model = 'gpt-4o-mini', name = model): AiModelDraft {
  return {
    code: model,
    name,
    model,
    temperature: '',
    reasoningEffort: '',
    thinkingEnabled: false,
    extraBody: '',
    kind: 'chat',
    vision: false,
  }
}

function addAiProvider() {
  const index = aiProviders.value.length + 1
  aiProviders.value.push({
    ...createAiProvider(),
    code: `provider-${index}`,
    name: `供应商 ${index}`,
    type: 'OPENAI_COMPATIBLE',
    apiKey: '',
  })
  syncAiDefaults()
}

function removeAiProvider(index: number) {
  aiProviders.value.splice(index, 1)
  syncAiDefaults()
}

function addAiModel(provider: AiProviderDraft) {
  const model = createAiModel(`model-${provider.models.length + 1}`)
  provider.models.push(model)
  provider.defaultModel ||= model.code
  syncAiDefaults()
}

function removeAiModel(provider: AiProviderDraft, index: number) {
  provider.models.splice(index, 1)
  if (!provider.models.some(model => model.code === provider.defaultModel)) {
    provider.defaultModel = provider.models[0]?.code || ''
  }
  syncAiDefaults()
}

function modelOptions(provider?: AiProviderDraft) {
  return (provider?.models || []).map((model, index) => ({
    label: model.name || model.code || model.model || `模型 ${index + 1}`,
    value: model.code || model.model,
  })).filter(item => item.value)
}

function syncAiDefaults() {
  const firstProvider = aiProviders.value[0]
  if (!firstProvider) {
    configDraft.value.defaultProvider = ''
    configDraft.value.defaultModel = ''
    return
  }
  if (!aiProviders.value.some(provider => provider.code === configDraft.value.defaultProvider)) {
    configDraft.value.defaultProvider = firstProvider.code
  }
  syncAiDefaultModel()
}

function syncAiDefaultModel() {
  const provider = aiProviders.value.find(item => item.code === configDraft.value.defaultProvider) || aiProviders.value[0]
  if (!provider) {
    configDraft.value.defaultModel = ''
    return
  }
  const options = modelOptions(provider)
  if (!options.some(option => option.value === provider.defaultModel)) {
    provider.defaultModel = options[0]?.value || ''
  }
  if (!options.some(option => option.value === configDraft.value.defaultModel)) {
    configDraft.value.defaultModel = provider.defaultModel || options[0]?.value || ''
  }
}

function serializeAiProviders() {
  return aiProviders.value.map(provider => ({
    code: provider.code.trim(),
    name: provider.name.trim(),
    type: provider.type,
    baseUrl: provider.baseUrl.trim().replace(/\/+$/, ''),
    completionsPath: provider.completionsPath.trim() || '/chat/completions',
    apiKey: provider.apiKey.trim(),
    proxyUrl: provider.proxyUrl.trim(),
    defaultModel: provider.defaultModel.trim() || provider.models[0]?.code?.trim() || '',
    temperature: textValue(provider.temperature, '0.4'),
    extraBody: provider.extraBody.trim(),
    models: provider.models.map(model => ({
      code: model.code.trim(),
      name: model.name.trim(),
      model: model.model.trim(),
      temperature: textValue(model.temperature),
      reasoningEffort: textValue(model.reasoningEffort),
      thinkingEnabled: model.thinkingEnabled,
      extraBody: model.extraBody.trim(),
      kind: model.kind.trim() || 'chat',
      vision: model.vision,
    })),
    embeddingModels: splitModelList(provider.embeddingModelsText),
    rerankModels: splitModelList(provider.rerankModelsText),
    enabled: provider.enabled,
  }))
}

function normalizedConfig() {
  if (selected.value?.code === 'ai') {
    syncAiDefaults()
    const providers = serializeAiProviders()
    if (!providers.length) {
      toast.error('请至少配置一个 AI 供应商')
      return null
    }
    const providerCodes = new Set<string>()
    for (const [index, provider] of providers.entries()) {
      if (!provider.code) {
        toast.error(`第 ${index + 1} 个供应商缺少编码`)
        return null
      }
      if (providerCodes.has(provider.code)) {
        toast.error(`供应商编码重复：${provider.code}`)
        return null
      }
      providerCodes.add(provider.code)
      if (!provider.baseUrl) {
        toast.error(`供应商 ${provider.code} 缺少 API 地址`)
        return null
      }
      if (!provider.models.length) {
        toast.error(`供应商 ${provider.code} 至少需要一个模型`)
        return null
      }
      for (const model of provider.models) {
        if (!model.code || !model.model) {
          toast.error(`供应商 ${provider.code} 存在模型编码或模型名为空`)
          return null
        }
      }
    }
    const defaultProvider = configDraft.value.defaultProvider?.trim() || providers[0].code
    const selectedProvider = providers.find(provider => provider.code === defaultProvider) || providers[0]
    const defaultModel = configDraft.value.defaultModel?.trim() || selectedProvider.defaultModel || selectedProvider.models[0]?.code || ''
    if (!selectedProvider.models.some(model => model.code === defaultModel || model.model === defaultModel)) {
      toast.error('默认模型必须属于默认供应商')
      return null
    }
    return {
      providers: JSON.stringify(providers, null, 2),
      defaultProvider: selectedProvider.code,
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

function objectRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function textValue(value: unknown, fallback = '') {
  if (value === null || value === undefined) {
    return fallback
  }
  return String(value)
}

function booleanValue(value: unknown, fallback: boolean) {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  if (typeof value === 'boolean') {
    return value
  }
  return String(value).trim().toLowerCase() === 'true'
}

function normalizeAiProviderType(value: string): AiProviderType {
  const normalized = value.trim().replace(/-/g, '_').toUpperCase()
  return aiProviderTypeOptions.some(item => item.value === normalized) ? normalized as AiProviderType : 'OPENAI_COMPATIBLE'
}

function extraBodyValue(value: unknown) {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  return JSON.stringify(value, null, 2)
}

function toTextList(value: unknown) {
  if (Array.isArray(value)) {
    return value.map(item => textValue(item).trim()).filter(Boolean)
  }
  return splitModelList(textValue(value))
}

function splitModelList(value: string) {
  return value
    .split(/[,，\n\r]/)
    .map(item => item.trim())
    .filter(Boolean)
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

    <FaModal
      v-model="configVisible"
      title="能力配置"
      show-cancel-button
      :class="isAiConfig ? 'sm:max-w-6xl' : 'sm:max-w-2xl'"
      @confirm="saveConfig"
    >
      <div v-if="isAiConfig" class="ai-config-editor">
        <div class="ai-config-toolbar">
          <label class="config-field">
            <span>默认供应商</span>
            <FaSelect
              v-model="configDraft.defaultProvider"
              :options="aiProviderOptions"
              placeholder="选择默认供应商"
              class="w-full"
            />
          </label>
          <label class="config-field">
            <span>默认模型</span>
            <FaSelect
              v-model="configDraft.defaultModel"
              :options="aiDefaultModelOptions"
              placeholder="选择默认模型"
              class="w-full"
            />
          </label>
          <FaButton variant="outline" class="ai-add-provider" @click="addAiProvider">
            <FaIcon name="i-ri:add-line" />
            添加供应商
          </FaButton>
        </div>

        <div class="ai-provider-list">
          <section v-for="(provider, providerIndex) in aiProviders" :key="providerIndex" class="ai-provider-card">
            <header class="ai-provider-header">
              <div>
                <strong>{{ provider.name || provider.code || `供应商 ${providerIndex + 1}` }}</strong>
                <span>{{ provider.type }} · {{ provider.baseUrl || '未配置 API 地址' }}</span>
              </div>
              <div class="ai-provider-actions">
                <span>启用</span>
                <FaSwitch v-model="provider.enabled" />
                <FaButton
                  size="sm"
                  variant="ghost"
                  :disabled="aiProviders.length <= 1"
                  @click="removeAiProvider(providerIndex)"
                >
                  <FaIcon name="i-ri:delete-bin-line" />
                </FaButton>
              </div>
            </header>

            <div class="ai-provider-grid">
              <label class="config-field">
                <span>供应商编码</span>
                <FaInput v-model="provider.code" placeholder="openai" @blur="syncAiDefaults" />
              </label>
              <label class="config-field">
                <span>显示名称</span>
                <FaInput v-model="provider.name" placeholder="OpenAI" />
              </label>
              <label class="config-field">
                <span>供应商类型</span>
                <FaSelect v-model="provider.type" :options="aiProviderTypeOptions" class="w-full" />
              </label>
              <label class="config-field">
                <span>默认模型</span>
                <FaSelect v-model="provider.defaultModel" :options="modelOptions(provider)" class="w-full" />
              </label>
              <label class="config-field ai-span-2">
                <span>API 地址</span>
                <FaInput v-model="provider.baseUrl" placeholder="https://api.openai.com/v1" />
              </label>
              <label class="config-field">
                <span>补全路径</span>
                <FaInput v-model="provider.completionsPath" placeholder="/chat/completions" />
              </label>
              <label class="config-field">
                <span>温度</span>
                <FaInput v-model="provider.temperature" type="number" placeholder="0.4" />
              </label>
              <label class="config-field ai-span-2">
                <span>API Key</span>
                <FaInput v-model="provider.apiKey" type="password" placeholder="sk-..." />
              </label>
              <label class="config-field ai-span-2">
                <span>代理地址</span>
                <FaInput v-model="provider.proxyUrl" placeholder="http://127.0.0.1:7890" />
              </label>
              <label class="config-field ai-span-2">
                <span>Embedding 模型</span>
                <FaTextarea v-model="provider.embeddingModelsText" rows="2" placeholder="每行一个模型编码" />
              </label>
              <label class="config-field ai-span-2">
                <span>Rerank 模型</span>
                <FaTextarea v-model="provider.rerankModelsText" rows="2" placeholder="每行一个模型编码" />
              </label>
              <label class="config-field ai-span-4">
                <span>供应商额外请求体</span>
                <FaTextarea v-model="provider.extraBody" rows="2" input-class="font-mono" placeholder="{ }" />
              </label>
            </div>

            <div class="ai-model-panel">
              <div class="ai-model-title">
                <span>模型列表</span>
                <FaButton size="sm" variant="outline" @click="addAiModel(provider)">
                  <FaIcon name="i-ri:add-line" />
                  添加模型
                </FaButton>
              </div>
              <div class="ai-model-list">
                <div v-for="(model, modelIndex) in provider.models" :key="modelIndex" class="ai-model-card">
                  <div class="ai-model-card__head">
                    <div class="ai-model-card__identity">
                      <strong>{{ model.name || model.code || `模型 ${modelIndex + 1}` }}</strong>
                      <span>{{ model.model || '未配置模型名' }}</span>
                    </div>
                    <FaButton
                      size="sm"
                      variant="ghost"
                      :disabled="provider.models.length <= 1"
                      title="删除模型"
                      @click="removeAiModel(provider, modelIndex)"
                    >
                      <FaIcon name="i-ri:delete-bin-line" />
                    </FaButton>
                  </div>

                  <div class="ai-model-basic">
                    <label class="config-field">
                      <span>编码</span>
                      <FaInput v-model="model.code" placeholder="gpt-4o-mini" @blur="syncAiDefaults" />
                    </label>
                    <label class="config-field">
                      <span>名称</span>
                      <FaInput v-model="model.name" placeholder="GPT-4o mini" />
                    </label>
                    <label class="config-field">
                      <span>模型名</span>
                      <FaInput v-model="model.model" placeholder="gpt-4o-mini" />
                    </label>
                    <label class="config-field">
                      <span>类型</span>
                      <FaSelect v-model="model.kind" :options="aiModelKindOptions" class="w-full" />
                    </label>
                  </div>

                  <div class="ai-model-options">
                    <div class="ai-model-option-group">
                      <span class="ai-model-group-title">参数</span>
                      <div class="ai-model-params">
                        <label class="config-field">
                          <span>温度</span>
                          <FaInput v-model="model.temperature" type="number" placeholder="0.4" />
                        </label>
                        <label class="config-field">
                          <span>推理强度</span>
                          <FaInput v-model="model.reasoningEffort" placeholder="medium" />
                        </label>
                      </div>
                    </div>
                    <div class="ai-model-option-group">
                      <span class="ai-model-group-title">能力</span>
                      <div class="ai-model-switches">
                        <label>
                          <span>思考</span>
                          <FaSwitch v-model="model.thinkingEnabled" />
                        </label>
                        <label>
                          <span>视觉</span>
                          <FaSwitch v-model="model.vision" />
                        </label>
                      </div>
                    </div>
                  </div>

                  <label class="config-field ai-model-extra">
                    <span>模型额外请求体</span>
                    <FaTextarea v-model="model.extraBody" rows="2" input-class="font-mono" placeholder="{ }" />
                  </label>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>
      <div v-else-if="usesStructuredConfig" class="config-form">
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

.ai-config-editor {
  display: grid;
  gap: 14px;
  max-height: min(72vh, 820px);
  overflow: auto;
  padding-right: 4px;
}

.ai-config-toolbar {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) minmax(180px, 1fr) auto;
  gap: 12px;
  align-items: end;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.ai-add-provider {
  min-height: 36px;
  white-space: nowrap;
}

.ai-provider-list {
  display: grid;
  gap: 14px;
}

.ai-provider-card {
  display: grid;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.ai-provider-header {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.ai-provider-header div:first-child {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.ai-provider-header strong {
  overflow: hidden;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-provider-header span {
  overflow-wrap: anywhere;
  color: var(--color-text-3);
  font-size: 12px;
}

.ai-provider-actions {
  display: inline-flex;
  flex: none;
  gap: 8px;
  align-items: center;
  color: var(--color-text-2);
  font-size: 12px;
}

.ai-provider-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.ai-span-2 {
  grid-column: span 2;
}

.ai-span-4 {
  grid-column: 1 / -1;
}

.ai-model-panel {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

.ai-model-title {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  color: var(--color-text-1);
  font-weight: 700;
}

.ai-model-list {
  display: grid;
  gap: 10px;
}

.ai-model-card {
  display: grid;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-1);
}

.ai-model-card__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  min-width: 0;
}

.ai-model-card__identity {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.ai-model-card__identity strong,
.ai-model-card__identity span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-model-card__identity strong {
  color: var(--color-text-1);
}

.ai-model-card__identity span {
  color: var(--color-text-3);
  font-size: 12px;
}

.ai-model-basic {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  min-width: 0;
}

.ai-model-options {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(180px, 240px);
  gap: 10px;
  align-items: end;
}

.ai-model-option-group {
  display: grid;
  min-width: 0;
  gap: 6px;
}

.ai-model-group-title {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
}

.ai-model-params {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.ai-model-switches {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.ai-model-switches label {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-height: 36px;
  padding: 0 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  color: var(--color-text-2);
  font-size: 12px;
}

.ai-model-extra {
  min-width: 0;
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

  .ai-config-toolbar,
  .ai-provider-grid {
    grid-template-columns: 1fr;
  }

  .ai-span-2,
  .ai-span-4 {
    grid-column: auto;
  }

  .ai-provider-header,
  .ai-model-title,
  .ai-model-card__head {
    flex-direction: column;
    align-items: stretch;
  }

  .ai-provider-actions {
    justify-content: flex-start;
  }

  .ai-model-basic,
  .ai-model-options,
  .ai-model-params,
  .ai-model-switches {
    grid-template-columns: 1fr;
  }
}
</style>
