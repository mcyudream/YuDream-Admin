<script setup lang="ts">
/**
 * AI 端点配置：聊天 / 向量化 / 重排序三类独立端点的分层编辑。
 *
 * - 左栏按用途分组列出端点，右栏只编辑当前端点；
 * - 连接 / 模型 / 高级兼容分区，低频字段折叠，避免一屏铺满密集表单；
 * - 「获取模型」按端点协议请求远端模型列表，勾选后合并到当前端点，不覆盖已登记模型。
 */
import type { AiDiscoveredModelCandidate, AiEndpointDraft } from './ai-endpoints'
import type { AiEndpointUsage, AiProtocol } from './ai-presets'
import apiCapability from '@/api/modules/platform-capability'
import {
  applyPreset,
  applyProtocol,
  createAiModel,
  createEndpointDraft,
  endpointsByUsage,
  mergeDiscoveredModels,
  modelOptionsOf,
} from './ai-endpoints'
import {
  AI_USAGE_HINTS,
  AI_USAGE_LABELS,
  AI_USAGE_ORDER,
  presetsFor,
  protocolOptionsFor,
} from './ai-presets'

const endpoints = defineModel<AiEndpointDraft[]>('endpoints', { required: true })
const defaultProvider = defineModel<string>('defaultProvider', { required: true })
const defaultModel = defineModel<string>('defaultModel', { required: true })

const props = withDefaults(defineProps<{
  /** 能力未启用时后端拒绝模型发现，前端同步禁用并说明原因。 */
  capabilityEnabled?: boolean
}>(), {
  capabilityEnabled: false,
})

const toast = useYdToast()

const activeIndex = ref(0)
const presetPickerUsage = ref<AiEndpointUsage | null>(null)
const discoverOpen = ref(false)
const discoverLoading = ref(false)
const discoverListed = ref(true)
const discoverMessage = ref('')
const discoverCandidates = ref<AiDiscoveredModelCandidate[]>([])

const active = computed(() => {
  const index = Math.min(Math.max(activeIndex.value, 0), Math.max(endpoints.value.length - 1, 0))
  return endpoints.value[index]
})
const chatEndpoints = computed(() => endpointsByUsage(endpoints.value, 'chat'))
const defaultEndpoint = computed(() => chatEndpoints.value.find(item => item.code === defaultProvider.value))
const defaultModelOptions = computed(() => modelOptionsOf(defaultEndpoint.value))
const presetOptions = computed(() => {
  const usage = presetPickerUsage.value
  return usage ? presetsFor(usage).map(preset => ({ label: preset.label, value: preset.id })) : []
})
const activePresetOptions = computed(() =>
  [{ label: '自定义', value: '' }, ...presetsFor(active.value?.usage ?? 'chat').map(preset => ({ label: preset.label, value: preset.id }))])
const selectedCandidates = computed(() => discoverCandidates.value.filter(item => item.selected))
const modelCount = computed(() => active.value?.models.length ?? 0)

watch(chatEndpoints, (list) => {
  if (!list.some(item => item.code === defaultProvider.value)) {
    defaultProvider.value = list[0]?.code ?? ''
  }
}, { deep: true, immediate: true })

watch(defaultEndpoint, (endpoint) => {
  const options = modelOptionsOf(endpoint)
  if (!options.some(option => option.value === defaultModel.value)) {
    defaultModel.value = options[0]?.value ?? ''
  }
}, { immediate: true })

function protocolLabel(protocol: AiProtocol) {
  return protocolOptionsFor('chat').find(option => option.value === protocol)?.label
    ?? (protocol === 'EMBEDDING' ? 'Embedding' : protocol === 'RERANK' ? 'Rerank' : protocol)
}

function endpointTitle(endpoint: AiEndpointDraft) {
  return endpoint.name || endpoint.code || `未命名${AI_USAGE_LABELS[endpoint.usage]}`
}

function selectEndpoint(endpoint: AiEndpointDraft) {
  activeIndex.value = endpoints.value.indexOf(endpoint)
}

function addEndpointFromPreset(presetId: string) {
  const usage = presetPickerUsage.value
  if (!usage) {
    return
  }
  const draft = createEndpointDraft(usage, presetId)
  draft.code = uniqueCode(usage, presetId || usage)
  endpoints.value.push(draft)
  activeIndex.value = endpoints.value.length - 1
  presetPickerUsage.value = null
  if (presetId === 'custom-chat' || presetId === 'custom-embedding' || presetId === 'custom-rerank' || !presetId) {
    toast.info('请补齐端点编码、API 地址与凭据')
  }
}

function uniqueCode(usage: AiEndpointUsage, base: string) {
  const taken = new Set(endpoints.value.filter(item => item.usage === usage).map(item => item.code))
  const seed = base.replace(/-(chat|embedding|rerank)$/, '') || usage
  if (!taken.has(seed)) {
    return seed
  }
  let index = 2
  while (taken.has(`${seed}-${index}`)) {
    index += 1
  }
  return `${seed}-${index}`
}

function removeEndpoint() {
  const endpoint = active.value
  if (!endpoint) {
    return
  }
  const index = endpoints.value.indexOf(endpoint)
  endpoints.value.splice(index, 1)
  activeIndex.value = Math.max(0, Math.min(index, endpoints.value.length - 1))
}

function changeProtocol(value: unknown) {
  if (active.value) {
    applyProtocol(active.value, value as AiProtocol)
  }
}

function changePreset(value: unknown) {
  if (active.value) {
    applyPreset(active.value, typeof value === 'string' ? value : '')
  }
}

function addModel() {
  active.value?.models.push({
    ...createAiModel('', ''),
    kind: active.value.usage,
  })
}

function removeModel(index: number) {
  const endpoint = active.value
  if (!endpoint) {
    return
  }
  endpoint.models.splice(index, 1)
  if (!endpoint.models.some(model => model.code === endpoint.defaultModel)) {
    endpoint.defaultModel = endpoint.models[0]?.code ?? ''
  }
}

function modelRegistered(code: string) {
  const key = code.trim().toLowerCase()
  return (active.value?.models ?? []).some(model => model.model.trim().toLowerCase() === key)
}

async function openDiscovery() {
  const endpoint = active.value
  if (!endpoint) {
    return
  }
  discoverOpen.value = true
  discoverLoading.value = true
  discoverMessage.value = ''
  discoverCandidates.value = []
  discoverListed.value = true
  try {
    const res = await apiCapability.discoverModels({
      providerCode: endpoint.code || undefined,
      usage: endpoint.usage,
      protocol: endpoint.protocol,
      baseUrl: endpoint.baseUrl,
      apiKey: endpoint.apiKey,
      proxyUrl: endpoint.proxyUrl,
    })
    discoverListed.value = res.data.listed
    discoverMessage.value = res.data.message || ''
    discoverCandidates.value = res.data.models.map(model => ({
      code: model.code,
      name: model.name || model.code,
      kind: model.kind || endpoint.usage,
      selected: !modelRegistered(model.code),
    }))
  }
  catch {
    discoverListed.value = false
    discoverMessage.value = '获取模型失败，请检查端点地址、凭据与网络后重试'
  }
  finally {
    discoverLoading.value = false
  }
}

function toggleAllCandidates(selected: boolean) {
  discoverCandidates.value.forEach((candidate) => {
    candidate.selected = selected
  })
}

function confirmDiscovery() {
  const endpoint = active.value
  if (!endpoint) {
    return
  }
  const added = mergeDiscoveredModels(endpoint, discoverCandidates.value)
  discoverOpen.value = false
  toast.success(added ? `已登记 ${added} 个模型` : '没有需要新增的模型')
}
</script>

<template>
  <div class="ai-config">
    <div class="ai-config__defaults">
      <label class="ai-field">
        <span>默认聊天端点</span>
        <YdSelect
          v-model="defaultProvider"
          :options="chatEndpoints.map(item => ({ label: endpointTitle(item), value: item.code }))"
          placeholder="选择默认聊天端点"
          class="w-full"
        />
      </label>
      <label class="ai-field">
        <span>默认模型</span>
        <YdSelect v-model="defaultModel" :options="defaultModelOptions" placeholder="选择默认模型" class="w-full" />
      </label>
      <p class="ai-config__defaults-hint">
        默认端点与模型用于对话与工具调用；向量化、重排序端点由知识库等使用方按编码单独选择。
      </p>
    </div>

    <div class="ai-config__body">
      <aside class="ai-config__nav">
        <template v-for="usage in AI_USAGE_ORDER" :key="usage">
          <div class="ai-config__group">
            <span>{{ AI_USAGE_LABELS[usage] }}</span>
            <YdTag variant="secondary">{{ endpointsByUsage(endpoints, usage).length }}</YdTag>
          </div>
          <YdButton
            v-for="endpoint in endpointsByUsage(endpoints, usage)"
            :key="`${usage}-${endpoint.code}-${endpoint.name}`"
            variant="ghost"
            type="button"
            class="ai-endpoint"
            :class="{ 'is-active': active === endpoint }"
            :aria-pressed="active === endpoint"
            @click="selectEndpoint(endpoint)"
          >
            <YdIcon :name="endpoint.icon" />
            <span class="ai-endpoint__name">{{ endpointTitle(endpoint) }}</span>
            <small>{{ protocolLabel(endpoint.protocol) }}</small>
          </YdButton>
          <YdButton size="sm" variant="outline" class="ai-config__add" @click="presetPickerUsage = usage">
            <YdIcon name="morph:plus" />
            添加{{ AI_USAGE_LABELS[usage] }}
          </YdButton>
        </template>
      </aside>

      <section v-if="active" class="ai-editor">
        <header class="ai-editor__head">
          <div class="ai-editor__title">
            <YdIcon :name="active.icon" class="ai-editor__icon" />
            <div>
              <strong>{{ endpointTitle(active) }}</strong>
              <div class="ai-editor__meta">
                <YdTag variant="secondary">{{ AI_USAGE_LABELS[active.usage] }}</YdTag>
                <YdTag variant="secondary">{{ protocolLabel(active.protocol) }}</YdTag>
                <YdTag :variant="active.enabled ? 'default' : 'secondary'">
                  {{ active.enabled ? '已启用' : '已停用' }}
                </YdTag>
              </div>
            </div>
          </div>
          <div class="ai-editor__tools">
            <span class="ai-editor__switch-label">启用</span>
            <YdSwitch v-model="active.enabled" />
            <YdButton size="sm" variant="ghost" title="删除端点" @click="removeEndpoint">
              <YdIcon name="morph:trash-2" />
            </YdButton>
          </div>
        </header>

        <p class="ai-editor__hint">
          {{ AI_USAGE_HINTS[active.usage] }}
        </p>

        <div class="ai-editor__section">
          <div class="ai-editor__section-title">
            连接
          </div>
          <div class="ai-editor__grid">
            <label class="ai-field">
              <span>供应商标识</span>
              <YdInput v-model="active.code" placeholder="openai" />
            </label>
            <label class="ai-field">
              <span>显示名称</span>
              <YdInput v-model="active.name" placeholder="OpenAI" />
            </label>
            <label class="ai-field">
              <span>供应商预设</span>
              <YdSelect
                :model-value="active.presetId"
                :options="activePresetOptions"
                class="w-full"
                @update:model-value="changePreset"
              />
            </label>
            <label v-if="active.usage === 'chat'" class="ai-field">
              <span>协议</span>
              <YdSelect
                :model-value="active.protocol"
                :options="protocolOptionsFor('chat')"
                class="w-full"
                @update:model-value="changeProtocol"
              />
            </label>
            <label class="ai-field ai-field--wide">
              <span>API 地址</span>
              <YdInput v-model="active.baseUrl" placeholder="https://api.openai.com/v1" />
            </label>
            <label class="ai-field">
              <span>端点路径</span>
              <YdInput v-model="active.completionsPath" :placeholder="active.completionsPath" />
            </label>
            <label class="ai-field">
              <span>API Key</span>
              <YdInput v-model="active.apiKey" type="password" placeholder="sk-..." />
              <small class="ai-field__hint">凭据随能力配置保存，清空即移除</small>
            </label>
          </div>
        </div>

        <div class="ai-editor__section">
          <div class="ai-editor__section-head">
            <div class="ai-editor__section-title">
              模型（{{ modelCount }}）
            </div>
            <div class="ai-editor__section-tools">
              <YdButton
                v-auth="'platform:capability:test'"
                size="sm"
                variant="outline"
                :disabled="!capabilityEnabled || !active.baseUrl"
                :title="capabilityEnabled ? '按端点协议获取远端模型列表' : '需先启用 AI 能力'"
                @click="openDiscovery"
              >
                <YdIcon name="morph:cloud-download" />
                获取模型
              </YdButton>
              <YdButton size="sm" variant="outline" @click="addModel">
                <YdIcon name="morph:plus" />
                添加模型
              </YdButton>
            </div>
          </div>

          <p v-if="!capabilityEnabled" class="ai-editor__note">
            能力未启用时无法请求远端模型列表；可先手工登记模型，启用后再获取。
          </p>

          <div class="ai-model-list">
            <div v-for="(model, modelIndex) in active.models" :key="modelIndex" class="ai-model-row" :data-usage="active.usage">
              <label class="ai-field">
                <span>显示名称</span>
                <YdInput v-model="model.name" size="sm" placeholder="GPT-4o mini" @blur="model.code ||= model.name" />
              </label>
              <label class="ai-field">
                <span>模型名</span>
                <YdInput v-model="model.model" size="sm" placeholder="gpt-4o-mini" />
              </label>
              <template v-if="active.usage === 'chat'">
                <label class="ai-field ai-field--narrow">
                  <span>温度</span>
                  <YdInput v-model="model.temperature" size="sm" type="number" placeholder="0.4" />
                </label>
                <label class="ai-field ai-field--narrow">
                  <span>推理力度</span>
                  <YdInput v-model="model.reasoningEffort" size="sm" placeholder="high" />
                </label>
                <div class="ai-field ai-field--switch">
                  <span>思考</span>
                  <YdSwitch v-model="model.thinkingEnabled" />
                </div>
                <div class="ai-field ai-field--switch">
                  <span>视觉</span>
                  <YdSwitch v-model="model.vision" />
                </div>
              </template>
              <div class="ai-model-row__op">
                <YdButton size="sm" variant="ghost" title="删除模型" @click="removeModel(modelIndex)">
                  <YdIcon name="morph:trash-2" />
                </YdButton>
              </div>
            </div>
            <div v-if="!active.models.length" class="ai-editor__empty">
              还没有模型。用「获取模型」从远端拉取，或手工添加。
            </div>
          </div>

          <label v-if="active.usage === 'chat' && active.models.length" class="ai-field ai-field--inline">
            <span>默认模型</span>
            <YdSelect v-model="active.defaultModel" :options="modelOptionsOf(active)" class="w-full" />
          </label>
          <p class="ai-field__hint">
            {{ active.usage === 'chat'
              ? '默认模型用于未指定模型的对话请求；思考与推理力度按模型能力填写。'
              : '该端点的模型清单只服务本用途，与聊天端点各自独立。' }}
          </p>
        </div>

        <details class="ai-editor__advanced">
          <summary>高级兼容</summary>
          <div class="ai-editor__grid ai-editor__advanced-body">
            <label v-if="active.usage === 'chat'" class="ai-field">
              <span>默认温度</span>
              <YdInput v-model="active.temperature" type="number" placeholder="0.4" />
            </label>
            <label class="ai-field">
              <span>代理地址</span>
              <YdInput v-model="active.proxyUrl" placeholder="http://127.0.0.1:7890" />
            </label>
            <label v-if="active.usage === 'chat'" class="ai-field ai-field--wide">
              <span v-pre>额外请求体（JSON，支持 {{model}}、{{providerCode}} 等模板变量）</span>
              <YdTextarea v-model="active.extraBody" :rows="2" input-class="font-mono" placeholder="{ }" />
            </label>
          </div>
        </details>
      </section>

      <section v-else class="ai-editor ai-editor--empty">
        还没有端点。左侧选择用途后「添加端点」，或直接使用预设快速开始。
      </section>
    </div>

    <YdModal
      :model-value="presetPickerUsage !== null"
      :title="`添加${presetPickerUsage ? AI_USAGE_LABELS[presetPickerUsage] : ''}`"
      :footer="false"
      content-class="sm:max-w-2xl"
      @update:model-value="presetPickerUsage = null"
    >
      <p class="ai-picker__hint">
        {{ presetPickerUsage ? AI_USAGE_HINTS[presetPickerUsage] : '' }}
      </p>
      <div class="ai-picker__grid">
        <YdButton
          v-for="option in presetOptions"
          :key="option.value"
          variant="outline"
          type="button"
          class="ai-picker__item"
          @click="addEndpointFromPreset(option.value)"
        >
          {{ option.label }}
        </YdButton>
      </div>
    </YdModal>

    <YdModal
      v-model="discoverOpen"
      title="获取模型"
      :footer="false"
      content-class="sm:max-w-2xl"
    >
      <div class="ai-discover">
        <p class="ai-discover__hint">
          {{ discoverMessage || (discoverListed ? '正在读取远端模型列表…' : '该端点没有可用的模型列表接口') }}
        </p>
        <div v-if="discoverCandidates.length" class="ai-discover__tools">
          <span class="ai-discover__count">
            共 {{ discoverCandidates.length }} 个，已登记 {{ discoverCandidates.length - selectedCandidates.length }} 个
          </span>
          <YdButton size="sm" variant="link" @click="toggleAllCandidates(true)">
            全选
          </YdButton>
          <YdButton size="sm" variant="link" @click="toggleAllCandidates(false)">
            全不选
          </YdButton>
        </div>
        <ul v-if="discoverCandidates.length" class="ai-discover__list">
          <li v-for="candidate in discoverCandidates" :key="candidate.code" class="ai-discover__item">
            <YdCheckbox v-model="candidate.selected">
              <strong>{{ candidate.name }}</strong>
              <span class="ai-discover__code">{{ candidate.code }}</span>
            </YdCheckbox>
            <YdTag v-if="modelRegistered(candidate.code)" variant="secondary">已登记</YdTag>
          </li>
        </ul>
        <div class="ai-discover__footer">
          <YdButton variant="outline" @click="discoverOpen = false">
            取消
          </YdButton>
          <YdButton :disabled="discoverLoading || !discoverCandidates.length" :loading="discoverLoading" @click="confirmDiscovery">
            登记所选（{{ selectedCandidates.length }}）
          </YdButton>
        </div>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
.ai-config {
  display: grid;
  gap: 14px;
}

.ai-config__defaults {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}

.ai-config__defaults-hint {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.ai-config__body {
  display: grid;
  grid-template-columns: minmax(200px, 250px) minmax(0, 1fr);
  gap: 14px;
  align-items: start;
}

.ai-config__nav {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.ai-config__group {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 4px 2px 2px;
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 700;
}

.ai-config__add {
  justify-content: center;
  margin-bottom: 4px;
}

.ai-endpoint {
  display: grid;
  grid-template-columns: 20px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid transparent;
  border-radius: 7px;
  color: var(--color-text-2);
  text-align: left;
}

.ai-endpoint small {
  grid-column: 2;
  color: var(--color-text-3);
  font-size: 11px;
}

.ai-endpoint.is-active {
  border-color: var(--color-border-3);
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.ai-endpoint__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-editor {
  display: grid;
  min-width: 0;
  gap: 14px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.ai-editor--empty {
  color: var(--color-text-3);
  font-size: 13px;
}

.ai-editor__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.ai-editor__title {
  display: flex;
  min-width: 0;
  gap: 10px;
}

.ai-editor__icon {
  width: 30px;
  height: 30px;
  flex: none;
  padding: 4px;
  border-radius: 7px;
  background: var(--color-fill-2);
}

.ai-editor__title strong {
  color: var(--color-text-1);
  font-size: 15px;
}

.ai-editor__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 4px;
}

.ai-editor__tools {
  display: inline-flex;
  flex: none;
  gap: 10px;
  align-items: center;
  color: var(--color-text-2);
  font-size: 12px;
}

.ai-editor__hint,
.ai-editor__note {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.ai-editor__section {
  display: grid;
  gap: 10px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

.ai-editor__section-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.ai-editor__section-title {
  color: var(--color-text-1);
  font-size: 13px;
  font-weight: 700;
}

.ai-editor__section-tools {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-editor__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.ai-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.ai-field > span {
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
}

.ai-field--wide {
  grid-column: 1 / -1;
}

.ai-field--inline {
  grid-template-columns: 90px minmax(0, 1fr);
  align-items: center;
}

.ai-field__hint {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.6;
}

.ai-model-list {
  display: grid;
  gap: 8px;
}

.ai-model-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.2fr) 88px 104px 64px 64px 40px;
  gap: 10px;
  align-items: end;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
}

.ai-model-row[data-usage='embedding'],
.ai-model-row[data-usage='rerank'] {
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.2fr) 40px;
}

.ai-field--switch {
  align-items: center;
  justify-items: start;
}

.ai-model-row__op {
  display: flex;
  justify-content: flex-end;
}

.ai-editor__empty {
  padding: 18px;
  border: 1px dashed var(--color-border-2);
  border-radius: 8px;
  color: var(--color-text-3);
  font-size: 13px;
  text-align: center;
}

.ai-editor__advanced {
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
}

.ai-editor__advanced summary {
  padding: 10px 14px;
  color: var(--color-text-2);
  cursor: pointer;
  font-size: 13px;
  list-style: none;
}

.ai-editor__advanced summary::-webkit-details-marker {
  display: none;
}

.ai-editor__advanced summary::before {
  margin-right: 6px;
  color: var(--color-text-3);
  content: '▸';
}

.ai-editor__advanced[open] summary::before {
  content: '▾';
}

.ai-editor__advanced-body {
  padding: 0 14px 14px;
}

.ai-picker__hint {
  margin: 0 0 10px;
  color: var(--color-text-3);
  font-size: 12px;
}

.ai-picker__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}

.ai-picker__item {
  justify-content: flex-start;
}

.ai-discover {
  display: grid;
  gap: 12px;
}

.ai-discover__hint {
  margin: 0;
  color: var(--color-text-2);
  font-size: 13px;
}

.ai-discover__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.ai-discover__count {
  margin-right: auto;
  color: var(--color-text-3);
  font-size: 12px;
}

.ai-discover__list {
  display: grid;
  max-height: 46vh;
  gap: 6px;
  margin: 0;
  padding: 0;
  overflow-y: auto;
  list-style: none;
}

.ai-discover__item {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 7px;
  background: var(--color-bg-1);
}

.ai-discover__code {
  margin-left: 8px;
  color: var(--color-text-3);
  font-size: 12px;
}

.ai-discover__footer {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .ai-config__body {
    grid-template-columns: 1fr;
  }

  .ai-editor__grid {
    grid-template-columns: 1fr;
  }

  .ai-field--inline {
    grid-template-columns: 1fr;
  }

  .ai-model-row,
  .ai-model-row[data-usage='embedding'],
  .ai-model-row[data-usage='rerank'] {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .ai-model-row__op {
    grid-column: 2;
  }
}

@media (max-width: 640px) {
  .ai-model-row,
  .ai-model-row[data-usage='embedding'],
  .ai-model-row[data-usage='rerank'] {
    grid-template-columns: 1fr;
  }

  .ai-model-row__op {
    grid-column: 1;
    justify-content: flex-start;
  }
}
</style>
