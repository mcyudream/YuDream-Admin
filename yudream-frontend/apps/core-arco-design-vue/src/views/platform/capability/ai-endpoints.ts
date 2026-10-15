/**
 * AI 端点配置（v2）解析与序列化。
 *
 * 契约要点：
 * - 聊天、向量化（Embedding）、重排序（Rerank）是各自独立的端点，各自带用途、协议、基址、凭据与模型清单；
 * - 旧配置把 embeddingModels/rerankModels 挂在聊天端点上，这里在读取时合成为独立端点并在保存时迁移为显式端点；
 * - 保存时不写回旧字段，避免同一模型在两处定义。
 */
import type { AiEndpointUsage, AiProtocol } from './ai-presets'
import { findPreset, presetsFor, protocolDefaultPath } from './ai-presets'

export interface AiModelDraft {
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

export interface AiEndpointDraft {
  code: string
  name: string
  icon: string
  presetId: string
  usage: AiEndpointUsage
  protocol: AiProtocol
  baseUrl: string
  completionsPath: string
  apiKey: string
  proxyUrl: string
  defaultModel: string
  temperature: string
  extraBody: string
  enabled: boolean
  models: AiModelDraft[]
}

export interface AiDiscoveredModelCandidate {
  code: string
  name: string
  kind: string
  selected: boolean
}

const USAGE_ORDER: AiEndpointUsage[] = ['chat', 'embedding', 'rerank']

export function createAiModel(model = '', name = ''): AiModelDraft {
  return {
    code: model || name,
    name: name || model,
    model: model || name,
    temperature: '',
    reasoningEffort: '',
    thinkingEnabled: false,
    extraBody: '',
    kind: 'chat',
    vision: false,
  }
}

export function createEndpointDraft(usage: AiEndpointUsage, presetId = ''): AiEndpointDraft {
  const preset = findPreset(presetId)
  const protocol: AiProtocol = preset?.protocol ?? defaultProtocol(usage)
  return {
    code: '',
    name: preset?.label ?? '',
    icon: preset?.icon ?? 'morph:cloud',
    presetId: preset?.id ?? '',
    usage,
    protocol,
    baseUrl: preset?.baseUrl ?? '',
    completionsPath: preset?.path ?? protocolDefaultPath(protocol),
    apiKey: '',
    proxyUrl: '',
    defaultModel: preset?.defaultModel ?? '',
    temperature: usage === 'chat' ? '0.4' : '',
    extraBody: '',
    enabled: true,
    models: [],
  }
}

export function defaultProtocol(usage: AiEndpointUsage): AiProtocol {
  if (usage === 'embedding') {
    return 'EMBEDDING'
  }
  if (usage === 'rerank') {
    return 'RERANK'
  }
  return 'CHAT'
}

export function endpointsByUsage(endpoints: AiEndpointDraft[], usage: AiEndpointUsage): AiEndpointDraft[] {
  return endpoints.filter(endpoint => endpoint.usage === usage)
}

export function firstChatEndpoint(endpoints: AiEndpointDraft[]): AiEndpointDraft | undefined {
  return endpoints.find(endpoint => endpoint.usage === 'chat' && endpoint.enabled)
    ?? endpoints.find(endpoint => endpoint.usage === 'chat')
}

export function modelOptionsOf(endpoint: AiEndpointDraft | undefined) {
  return (endpoint?.models ?? [])
    .map((model, index) => ({
      label: model.name || model.model || model.code || `模型 ${index + 1}`,
      value: model.code || model.model,
    }))
    .filter(option => Boolean(option.value))
}

/** 应用预设：只填连接参数，保留已有模型、凭据与启用状态。 */
export function applyPreset(endpoint: AiEndpointDraft, presetId: string): void {
  const preset = findPreset(presetId)
  endpoint.presetId = preset?.id ?? ''
  if (!preset) {
    return
  }
  endpoint.name = preset.label
  endpoint.icon = preset.icon
  endpoint.protocol = preset.protocol
  endpoint.baseUrl = preset.baseUrl
  endpoint.completionsPath = preset.path ?? protocolDefaultPath(preset.protocol)
  endpoint.defaultModel = endpoint.defaultModel || preset.defaultModel || ''
  if (preset.defaultModel && !endpoint.models.length) {
    endpoint.models.push(createAiModel(preset.defaultModel))
  }
}

/** 切换协议时同步默认路径，但不覆盖管理员手改过的自定义路径。 */
export function applyProtocol(endpoint: AiEndpointDraft, protocol: AiProtocol): void {
  const previousDefault = protocolDefaultPath(endpoint.protocol)
  endpoint.protocol = protocol
  if (!endpoint.completionsPath || endpoint.completionsPath === previousDefault) {
    endpoint.completionsPath = protocolDefaultPath(protocol)
  }
}

/** 合并「获取模型」勾选结果：已存在的模型不覆盖，返回新增数量。 */
export function mergeDiscoveredModels(endpoint: AiEndpointDraft, candidates: AiDiscoveredModelCandidate[]): number {
  const existing = new Set(endpoint.models.map(model => model.model.trim().toLowerCase()).filter(Boolean))
  let added = 0
  for (const candidate of candidates) {
    if (!candidate.selected) {
      continue
    }
    const key = candidate.code.trim().toLowerCase()
    if (!key || existing.has(key)) {
      continue
    }
    endpoint.models.push({
      ...createAiModel(candidate.code, candidate.name || candidate.code),
      kind: endpoint.usage,
    })
    existing.add(key)
    added += 1
  }
  if (!endpoint.defaultModel || !endpoint.models.some(model => model.code === endpoint.defaultModel)) {
    endpoint.defaultModel = endpoint.models[0]?.code || ''
  }
  return added
}

interface RawEntry {
  code?: string
  name?: string
  type?: string
  usage?: string
  protocol?: string
  baseUrl?: string
  completionsPath?: string
  path?: string
  apiKey?: string
  proxyUrl?: string
  defaultModel?: string
  temperature?: string
  extraBody?: unknown
  enabled?: boolean
  models?: unknown
  embeddingModels?: unknown
  rerankModels?: unknown
}

export function parseEndpointDrafts(config: Record<string, string>): AiEndpointDraft[] {
  const raw = (config?.providers ?? '').trim()
  const entries: RawEntry[] = raw ? safeParseEntries(raw) : legacyEntry(config)
  const endpoints: AiEndpointDraft[] = []
  for (const entry of entries) {
    endpoints.push(toEndpointDraft(entry))
  }
  migrateLegacyModelLists(entries, endpoints)
  return orderEndpoints(dedupeEndpoints(endpoints))
}

function safeParseEntries(raw: string): RawEntry[] {
  try {
    const parsed = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((item): item is RawEntry => Boolean(item) && typeof item === 'object') : []
  }
  catch {
    return []
  }
}

/** 老版扁平字段（model/models/baseUrl/...）等价为一个聊天端点。 */
function legacyEntry(config: Record<string, string>): RawEntry[] {
  const model = text(config.model || config.defaultModel)
  if (!model && !text(config.baseUrl)) {
    return []
  }
  const models = splitModels(text(config.models) || model)
  return [{
    code: text(config.providerCode) || 'default',
    name: text(config.providerName) || 'Default AI',
    baseUrl: text(config.baseUrl),
    apiKey: text(config.apiKey),
    proxyUrl: text(config.proxyUrl),
    defaultModel: model,
    temperature: text(config.temperature) || '0.4',
    extraBody: text(config.extraBody),
    models: models.map(item => ({ code: item, model: item, name: item })),
    embeddingModels: splitModels(text(config.embeddingModel)),
    rerankModels: splitModels(text(config.rerankModel)),
  }]
}

function toEndpointDraft(entry: RawEntry): AiEndpointDraft {
  const usage = normalizeUsage(text(entry.usage))
  const protocol = normalizeProtocol(text(entry.protocol), usage)
  const code = text(entry.code)
  const baseUrl = text(entry.baseUrl)
  const preset = matchPreset(usage, protocol, baseUrl)
  return {
    code,
    name: text(entry.name) || preset?.label || code,
    icon: preset?.icon ?? 'morph:cloud',
    presetId: preset?.id ?? '',
    usage,
    protocol,
    baseUrl,
    completionsPath: text(entry.completionsPath) || text(entry.path) || protocolDefaultPath(protocol),
    apiKey: text(entry.apiKey),
    proxyUrl: text(entry.proxyUrl),
    defaultModel: text(entry.defaultModel),
    temperature: text(entry.temperature) || (usage === 'chat' ? '0.4' : ''),
    extraBody: extraBodyText(entry.extraBody),
    enabled: entry.enabled !== false,
    models: toModelDrafts(entry.models, usage),
  }
}

/** 旧配置的向量化/重排模型（旧数组字段或 kind 标记）迁到对应用途的独立端点。 */
function migrateLegacyModelLists(entries: RawEntry[], endpoints: AiEndpointDraft[]): void {
  entries.forEach((entry, index) => {
    const source = endpoints[index]
    if (!source || source.usage !== 'chat') {
      return
    }
    const moved: Record<'embedding' | 'rerank', string[]> = { embedding: [], rerank: [] }
    source.models = source.models.filter((model) => {
      const kind = model.kind.toLowerCase()
      if (kind === 'embedding' || kind === 'rerank') {
        moved[kind].push(model.model)
        return false
      }
      return true
    })
    moved.embedding.push(...stringArray(entry.embeddingModels))
    moved.rerank.push(...stringArray(entry.rerankModels))
    for (const usage of ['embedding', 'rerank'] as const) {
      const models = [...new Set(moved[usage].map(item => item.trim()).filter(Boolean))]
      if (!models.length) {
        continue
      }
      const target = ensureUsageEndpoint(endpoints, source, usage)
      const existing = new Set(target.models.map(model => model.model.trim().toLowerCase()))
      for (const model of models) {
        if (existing.has(model.toLowerCase())) {
          continue
        }
        target.models.push({ ...createAiModel(model), kind: usage })
        existing.add(model.toLowerCase())
      }
      if (!target.defaultModel) {
        target.defaultModel = target.models[0]?.code || ''
      }
    }
  })
}

function ensureUsageEndpoint(endpoints: AiEndpointDraft[], source: AiEndpointDraft, usage: 'embedding' | 'rerank'): AiEndpointDraft {
  const existing = endpoints.find(endpoint => endpoint.usage === usage && endpoint.code === source.code)
  if (existing) {
    return existing
  }
  const preset = matchPreset(usage, defaultProtocol(usage), source.baseUrl)
  const created: AiEndpointDraft = {
    ...createEndpointDraft(usage, preset?.id ?? ''),
    code: source.code,
    name: `${source.name || source.code} ${usage === 'embedding' ? '向量化' : '重排序'}`,
    baseUrl: source.baseUrl,
    apiKey: source.apiKey,
    proxyUrl: source.proxyUrl,
    enabled: source.enabled,
  }
  endpoints.push(created)
  return created
}

function dedupeEndpoints(endpoints: AiEndpointDraft[]): AiEndpointDraft[] {
  const seen = new Set<string>()
  return endpoints.filter((endpoint) => {
    const key = `${endpoint.usage}|${endpoint.code.trim().toLowerCase()}`
    if (seen.has(key)) {
      return false
    }
    seen.add(key)
    return true
  })
}

function orderEndpoints(endpoints: AiEndpointDraft[]): AiEndpointDraft[] {
  return [...endpoints].sort((left, right) => USAGE_ORDER.indexOf(left.usage) - USAGE_ORDER.indexOf(right.usage))
}

export function serializeEndpointDrafts(endpoints: AiEndpointDraft[]): string {
  return JSON.stringify(endpoints.map(endpoint => ({
    code: endpoint.code.trim(),
    name: endpoint.name.trim(),
    usage: endpoint.usage,
    protocol: endpoint.protocol,
    baseUrl: endpoint.baseUrl.trim(),
    completionsPath: endpoint.completionsPath.trim() || protocolDefaultPath(endpoint.protocol),
    apiKey: endpoint.apiKey.trim(),
    proxyUrl: endpoint.proxyUrl.trim(),
    defaultModel: endpoint.defaultModel.trim() || endpoint.models[0]?.code?.trim() || '',
    temperature: endpoint.usage === 'chat' ? (endpoint.temperature.trim() || '0.4') : '',
    extraBody: endpoint.usage === 'chat' ? endpoint.extraBody.trim() : '',
    enabled: endpoint.enabled,
    models: endpoint.models.map(model => ({
      code: model.code.trim(),
      name: model.name.trim(),
      model: model.model.trim(),
      kind: endpoint.usage,
      temperature: endpoint.usage === 'chat' ? model.temperature.trim() : '',
      reasoningEffort: endpoint.usage === 'chat' ? model.reasoningEffort.trim() : '',
      thinkingEnabled: endpoint.usage === 'chat' ? model.thinkingEnabled : false,
      extraBody: endpoint.usage === 'chat' ? model.extraBody.trim() : '',
      vision: endpoint.usage === 'chat' ? model.vision : false,
    })),
  })), null, 2)
}

/** 校验端点草稿；返回错误文案或 null。 */
export function validateEndpointDrafts(endpoints: AiEndpointDraft[]): string | null {
  if (!endpoints.length) {
    return '请至少配置一个 AI 端点'
  }
  if (!endpoints.some(endpoint => endpoint.usage === 'chat')) {
    return '请至少配置一个聊天端点'
  }
  const seen = new Set<string>()
  for (const endpoint of endpoints) {
    const label = endpoint.name || endpoint.code || '未命名端点'
    if (!endpoint.code.trim()) {
      return `${label} 缺少端点编码`
    }
    const key = `${endpoint.usage}|${endpoint.code.trim().toLowerCase()}`
    if (seen.has(key)) {
      return `同一用途下的端点编码重复：${endpoint.code}`
    }
    seen.add(key)
    if (!endpoint.baseUrl.trim()) {
      return `${label} 缺少 API 地址`
    }
    if (!endpoint.models.length) {
      return `${label} 至少需要一个模型`
    }
    if (endpoint.models.some(model => !model.code.trim() || !model.model.trim())) {
      return `${label} 存在模型编码或模型名为空`
    }
  }
  return null
}

function matchPreset(usage: AiEndpointUsage, protocol: AiProtocol, baseUrl: string) {
  const normalized = baseUrl.trim().replace(/\/+$/, '').toLowerCase()
  if (!normalized) {
    return undefined
  }
  return presetsFor(usage).find(preset =>
    preset.protocol === protocol && preset.baseUrl.replace(/\/+$/, '').toLowerCase() === normalized)
}

function normalizeUsage(value: string): AiEndpointUsage {
  const normalized = value.trim().toLowerCase()
  if (normalized === 'embedding' || normalized === 'embeddings' || normalized === 'vector') {
    return 'embedding'
  }
  if (normalized === 'rerank' || normalized === 'reranking' || normalized === 'reranker') {
    return 'rerank'
  }
  return 'chat'
}

function normalizeProtocol(value: string, usage: AiEndpointUsage): AiProtocol {
  const normalized = value.trim().replace(/-/g, '_').toUpperCase()
  if (normalized === 'ANTH' || normalized === 'ANTHROPIC' || normalized === 'ANTHROPIC_MESSAGES') {
    return usage === 'chat' ? 'ANTH' : defaultProtocol(usage)
  }
  if (normalized === 'RESPONSE' || normalized === 'RESPONSES' || normalized === 'OPENAI_RESPONSES') {
    return usage === 'chat' ? 'RESPONSE' : defaultProtocol(usage)
  }
  if (usage !== 'chat') {
    return defaultProtocol(usage)
  }
  return 'CHAT'
}

function toModelDrafts(raw: unknown, usage: AiEndpointUsage): AiModelDraft[] {
  if (!Array.isArray(raw)) {
    return []
  }
  return raw
    .map((item) => {
      if (typeof item === 'string') {
        return { ...createAiModel(item.trim()), kind: usage }
      }
      if (!item || typeof item !== 'object') {
        return undefined
      }
      const entry = item as Record<string, unknown>
      const model = text(entry.model) || text(entry.name) || text(entry.code)
      if (!model) {
        return undefined
      }
      return {
        code: text(entry.code) || model,
        name: text(entry.name) || model,
        model,
        temperature: text(entry.temperature),
        reasoningEffort: text(entry.reasoningEffort),
        thinkingEnabled: entry.thinkingEnabled === true || entry.thinkingEnabled === 'true',
        extraBody: extraBodyText(entry.extraBody),
        kind: text(entry.kind) || usage,
        vision: entry.vision === true,
      } satisfies AiModelDraft
    })
    .filter((item): item is AiModelDraft => Boolean(item))
}

function extraBodyText(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  if (typeof value === 'string') {
    return value
  }
  try {
    return JSON.stringify(value, null, 2)
  }
  catch {
    return ''
  }
}

function stringArray(value: unknown): string[] {
  if (Array.isArray(value)) {
    return value.map(item => text(item)).filter(Boolean)
  }
  return splitModels(text(value))
}

function splitModels(value: string): string[] {
  return value.split(/[,，\n\r]/).map(item => item.trim()).filter(Boolean)
}

function text(value: unknown): string {
  if (value === null || value === undefined) {
    return ''
  }
  return String(value).trim()
}
