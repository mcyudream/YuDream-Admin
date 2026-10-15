<script setup lang="ts">
/**
 * AI 端点配置：聊天 / 向量化 / 重排序三类独立端点的分层编辑。
 *
 * - 同级用途切换与端点选择器，避免嵌套固定侧栏；
 * - 连接 / 模型 / 高级兼容分区，模型参数单独编辑，避免密集表单；
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

withDefaults(defineProps<{
  /** 能力未启用时后端拒绝模型发现，前端同步禁用并说明原因。 */
  capabilityEnabled?: boolean
}>(), {
  capabilityEnabled: false,
})

const endpoints = defineModel<AiEndpointDraft[]>('endpoints', { required: true })
const defaultProvider = defineModel<string>('defaultProvider', { required: true })
const defaultModel = defineModel<string>('defaultModel', { required: true })
const toast = useYdToast()

const activeUsage = ref<AiEndpointUsage>('chat')
const activeEndpoint = shallowRef<AiEndpointDraft>()
const presetPickerUsage = ref<AiEndpointUsage | null>(null)
const discoverOpen = ref(false)
const discoverLoading = ref(false)
const discoverListed = ref(true)
const discoverMessage = ref('')
const discoverCandidates = ref<AiDiscoveredModelCandidate[]>([])
const discoverOwner = shallowRef<AiEndpointDraft>()
let discoverSequence = 0
const modelEditing = ref(false)
const modelIndex = ref(-1)
const modelDraft = ref(createAiModel())

const endpointsForUsage = computed(() => endpointsByUsage(endpoints.value, activeUsage.value))
const active = computed(() => endpointsForUsage.value.find(item => item === activeEndpoint.value)
  ?? endpointsForUsage.value[0])
const activeUsageOptions = computed(() => AI_USAGE_ORDER.map(usage => ({
  label: AI_USAGE_LABELS[usage],
  value: usage,
})))
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
const registeredCount = computed(() => discoverCandidates.value.filter(item => modelRegistered(item.code)).length)

watch(active, () => {
  discoverOpen.value = false
  modelEditing.value = false
})

watch(endpointsForUsage, (list) => {
  if (!activeEndpoint.value || !list.includes(activeEndpoint.value)) {
    activeEndpoint.value = list[0]
  }
}, { immediate: true })

watch(chatEndpoints, (list) => {
  if (!list.some(item => item.code === defaultProvider.value)) {
    defaultProvider.value = list[0]?.code ?? ''
  }
}, { deep: true, immediate: true })

watch(defaultModelOptions, (options) => {
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

function selectEndpointByCode(value: unknown) {
  discoverSequence += 1
  discoverOpen.value = false
  discoverLoading.value = false
  discoverCandidates.value = []
  modelEditing.value = false
  activeEndpoint.value = endpointsForUsage.value.find(item => item.code === value)
}

function addEndpointFromPreset(presetId: string) {
  const usage = presetPickerUsage.value
  if (!usage) {
    return
  }
  const draft = createEndpointDraft(usage, presetId)
  draft.code = uniqueCode(usage, presetId || usage)
  endpoints.value.push(draft)
  activeUsage.value = usage
  activeEndpoint.value = endpoints.value[endpoints.value.length - 1]
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
  activeEndpoint.value = endpointsForUsage.value[0]
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

function editModel(index = -1) {
  modelIndex.value = index
  modelDraft.value = index < 0 ? createAiModel() : { ...active.value!.models[index] }
  modelEditing.value = true
}

function saveModel() {
  const endpoint = active.value
  if (!endpoint || !modelDraft.value.model.trim()) {
    toast.error('请填写模型标识')
    return
  }
  const previous = modelIndex.value >= 0 ? endpoint.models[modelIndex.value] : undefined
  const draft = { ...modelDraft.value, kind: endpoint.usage }
  draft.model = draft.model.trim()
  if (!previous || previous.code === previous.model) {
    draft.code = draft.model
  }
  if (!previous || previous.name === previous.model) {
    draft.name = draft.model
  }
  draft.name ||= draft.model
  if (endpoint.models.some((model, index) => index !== modelIndex.value && model.model.trim().toLowerCase() === draft.model.toLowerCase())) {
    toast.error('此模型已登记')
    return
  }
  if (modelIndex.value < 0) {
    endpoint.models.push(draft)
  }
  else {
    endpoint.models[modelIndex.value] = draft
  }
  if (!endpoint.defaultModel || endpoint.defaultModel === previous?.code) {
    endpoint.defaultModel = draft.code
  }
  if (endpoint.usage === 'chat' && defaultProvider.value === endpoint.code && defaultModel.value === previous?.code) {
    defaultModel.value = draft.code
  }
  modelEditing.value = false
}

function removeModel(index: number) {
  const endpoint = active.value
  if (!endpoint) {
    return
  }
  const wasDefault = endpoint.defaultModel === endpoint.models[index]?.code
  const removedCode = endpoint.models[index]?.code
  endpoint.models.splice(index, 1)
  if (wasDefault) {
    endpoint.defaultModel = endpoint.models[0]?.code ?? ''
  }
  if (endpoint.usage === 'chat' && defaultProvider.value === endpoint.code && defaultModel.value === removedCode) {
    defaultModel.value = endpoint.defaultModel
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
  const sequence = ++discoverSequence
  discoverOwner.value = endpoint
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
    if (sequence !== discoverSequence || active.value !== endpoint) {
      return
    }
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
    if (sequence !== discoverSequence || active.value !== endpoint) {
      return
    }
    discoverListed.value = false
    discoverMessage.value = '获取模型失败，请检查端点地址、凭据与网络后重试'
  }
  finally {
    if (sequence === discoverSequence) {
      discoverLoading.value = false
    }
  }
}

function toggleAllCandidates(selected: boolean) {
  discoverCandidates.value.forEach((candidate) => {
    candidate.selected = selected
  })
}

function confirmDiscovery() {
  const endpoint = active.value
  if (!endpoint || endpoint !== discoverOwner.value) {
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
      <div class="ai-config__purpose-control">
        <span class="ai-config__eyebrow">端点类型</span>
        <YdSegmented v-model="activeUsage" :options="activeUsageOptions" aria-label="AI 端点用途" />
      </div>
      <label v-if="activeUsage === 'chat'" class="ai-field ai-config__default-endpoint">
        <span>聊天默认端点</span>
        <YdSelect
          v-model="defaultProvider"
          :options="chatEndpoints.map(item => ({ label: endpointTitle(item), value: item.code }))"
          placeholder="选择默认聊天端点"
          class="w-full"
        />
      </label>
      <label v-if="activeUsage === 'chat'" class="ai-field ai-config__default-model">
        <span>聊天默认模型</span>
        <YdSelect v-model="defaultModel" :options="defaultModelOptions" placeholder="选择默认模型" class="w-full" />
      </label>
      <p class="ai-config__defaults-hint">
        {{ activeUsage === 'chat'
          ? '默认端点和模型用于未指定目标的聊天请求；向量化与重排序由各自使用方单独选择。'
          : AI_USAGE_HINTS[activeUsage] }}
      </p>
    </div>

    <div v-if="endpointsForUsage.length" class="ai-config__endpoint-bar">
      <div class="ai-config__endpoint-picker">
        <span class="ai-config__eyebrow">当前{{ AI_USAGE_LABELS[activeUsage] }}</span>
        <YdSelect
          :model-value="active?.code ?? ''"
          :options="endpointsForUsage.map(item => ({ label: endpointTitle(item), value: item.code }))"
          :placeholder="`选择${AI_USAGE_LABELS[activeUsage]}`"
          class="w-full"
          @update:model-value="selectEndpointByCode"
        />
        <span class="ai-config__endpoint-count">{{ endpointsForUsage.length }} 个端点</span>
      </div>
      <YdButton size="sm" variant="outline" @click="presetPickerUsage = activeUsage">
        <YdIcon name="morph:plus" />
        添加端点
      </YdButton>
    </div>

    <div class="ai-config__editor-region">
      <section v-if="active" class="ai-editor" :data-usage="active.usage">
        <header class="ai-editor__head">
          <div class="ai-editor__identity">
            <YdIcon :name="active.icon" class="ai-editor__icon" />
            <div class="ai-editor__name">
              <strong>{{ endpointTitle(active) }}</strong>
              <span>{{ active.baseUrl || '尚未设置 API 地址' }}</span>
            </div>
          </div>
          <div class="ai-editor__tools">
            <YdTag variant="secondary">
              {{ protocolLabel(active.protocol) }}
            </YdTag>
            <YdSwitch v-model="active.enabled" aria-label="启用端点" />
            <YdPopconfirm title="移除此端点？" description="保存配置后生效；取消配置编辑可撤销。" confirm-variant="destructive" @confirm="removeEndpoint">
              <YdButton size="sm" variant="ghost" title="移除端点" aria-label="移除端点">
                <YdIcon name="morph:trash-2" />
              </YdButton>
            </YdPopconfirm>
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
              <YdButton size="sm" variant="outline" @click="editModel()">
                <YdIcon name="morph:plus" />
                添加模型
              </YdButton>
            </div>
          </div>

          <YdAlert v-if="!capabilityEnabled" class="ai-editor__note">
            AI 能力尚未启用。可以先整理并保存端点；启用能力后即可获取远端模型列表。
          </YdAlert>

          <div v-if="active.models.length" class="ai-model-list">
            <div v-for="(model, rowIndex) in active.models" :key="rowIndex" class="ai-model-row">
              <YdIcon :name="active.icon" class="ai-model-row__icon" />
              <div class="ai-model-row__identity">
                <strong>{{ model.name || model.model }}</strong>
                <span>{{ model.model }}</span>
              </div>
              <div v-if="active.usage === 'chat'" class="ai-model-row__badges">
                <YdTag v-if="model.vision" variant="secondary">
                  视觉
                </YdTag>
                <YdTag v-if="model.thinkingEnabled" variant="secondary">
                  思考
                </YdTag>
              </div>
              <div class="ai-model-row__op">
                <YdButton size="sm" variant="ghost" @click="editModel(rowIndex)">
                  编辑
                </YdButton>
                <YdButton size="sm" variant="ghost" :aria-label="`移除 ${model.name || model.model}`" @click="removeModel(rowIndex)">
                  <YdIcon name="morph:trash-2" />
                </YdButton>
              </div>
            </div>
          </div>
          <YdEmpty v-else title="尚未登记模型" description="获取远端列表，或手动填写模型标识。" />
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
      <YdEmpty
        v-else
        title="尚未配置此类端点"
        :description="`添加${AI_USAGE_LABELS[activeUsage]}后，可在此配置地址、凭据和模型。`"
      >
        <YdButton size="sm" variant="outline" @click="presetPickerUsage = activeUsage">
          <YdIcon name="morph:plus" />
          添加{{ AI_USAGE_LABELS[activeUsage] }}
        </YdButton>
      </YdEmpty>
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

    <YdModal v-model="modelEditing" :title="modelIndex < 0 ? '添加模型' : '编辑模型'" :footer="false" content-class="sm:max-w-lg">
      <div class="ai-model-editor">
        <label class="ai-field">
          <span>模型标识（必填）</span>
          <YdInput v-model="modelDraft.model" placeholder="服务商提供的 model ID" />
        </label>
        <label class="ai-field">
          <span>显示名称</span>
          <YdInput v-model="modelDraft.name" placeholder="便于识别的名称" />
        </label>
        <template v-if="active?.usage === 'chat'">
          <div class="ai-model-editor__switches">
            <label class="ai-model-editor__switch"><YdSwitch v-model="modelDraft.vision" />支持视觉</label>
            <label class="ai-model-editor__switch"><YdSwitch v-model="modelDraft.thinkingEnabled" />启用思考</label>
          </div>
          <details class="ai-editor__advanced">
            <summary>模型高级参数</summary>
            <div class="ai-model-editor__advanced">
              <label class="ai-field"><span>温度</span><YdInput v-model="modelDraft.temperature" type="number" placeholder="继承端点温度" /></label>
              <label class="ai-field"><span>推理力度</span><YdSelect v-model="modelDraft.reasoningEffort" :options="[{ label: '未指定', value: '' }, { label: '低', value: 'low' }, { label: '中', value: 'medium' }, { label: '高', value: 'high' }]" class="w-full" /></label>
              <label class="ai-field"><span>额外请求体（JSON）</span><YdTextarea v-model="modelDraft.extraBody" :rows="3" input-class="font-mono" /></label>
            </div>
          </details>
        </template>
        <div class="ai-model-editor__footer">
          <YdButton variant="outline" @click="modelEditing = false">
            取消
          </YdButton>
          <YdButton @click="saveModel">
            保存模型
          </YdButton>
        </div>
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
            共 {{ discoverCandidates.length }} 个，已登记 {{ registeredCount }} 个
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
            <YdTag v-if="modelRegistered(candidate.code)" variant="secondary">
              已登记
            </YdTag>
          </li>
        </ul>
        <div class="ai-discover__footer">
          <YdButton variant="outline" @click="discoverOpen = false">
            取消
          </YdButton>
          <YdButton :disabled="discoverLoading || !selectedCandidates.length" :loading="discoverLoading" @click="confirmDiscovery">
            登记所选（{{ selectedCandidates.length }}）
          </YdButton>
        </div>
      </div>
    </YdModal>
  </div>
</template>

<style scoped>
.ai-config {
  container-type: inline-size;
  display: grid;
  gap: 22px;
  min-width: 0;
}

.ai-config__eyebrow {
  color: var(--color-text-3);
  font-size: 12px;
  font-weight: 600;
}

.ai-config__purpose-control {
  display: grid;
  gap: 6px;
  align-content: start;
  min-width: 0;
}

.ai-config__defaults {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-1);
}

.ai-config__purpose-control {
  grid-column: 1 / -1;
}

.ai-config__defaults-hint {
  grid-column: 1 / -1;
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
  line-height: 1.6;
}

.ai-config__endpoint-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-end;
  justify-content: space-between;
}

.ai-config__endpoint-picker {
  display: grid;
  flex: 1 1 260px;
  grid-template-columns: minmax(0, 320px) auto;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.ai-config__endpoint-picker .ai-config__eyebrow {
  grid-column: 1 / -1;
}

.ai-config__endpoint-count {
  color: var(--color-text-3);
  font-size: 12px;
  white-space: nowrap;
}

.ai-config__editor-region {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.ai-editor {
  display: grid;
  min-width: 0;
  gap: 14px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.ai-editor__identity {
  display: grid;
  grid-template-columns: 30px minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.ai-editor__name {
  min-width: 0;
}

.ai-editor__icon {
  width: 30px;
  height: 30px;
  flex: none;
  padding: 4px;
  border-radius: 7px;
  background: var(--color-fill-2);
}

.ai-editor__name strong {
  display: block;
  overflow: hidden;
  color: var(--color-text-1);
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-editor__name span {
  display: block;
  overflow: hidden;
  margin-top: 2px;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  grid-template-columns: 28px minmax(0, 1fr) auto auto;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-1);
}

.ai-model-row__icon { width: 24px; height: 24px; }
.ai-model-row__identity { display: grid; gap: 2px; min-width: 0; }
.ai-model-row__identity strong { color: var(--color-text-1); font-size: 13px; font-weight: 600; overflow-wrap: anywhere; }
.ai-model-row__identity span { color: var(--color-text-3); font-size: 12px; overflow-wrap: anywhere; }
.ai-model-row__badges, .ai-model-row__op { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.ai-model-row__op { justify-content: flex-end; }
.ai-model-editor { display: grid; gap: 16px; }
.ai-model-editor__switches { display: flex; flex-wrap: wrap; gap: 18px; }
.ai-model-editor__switch { display: inline-flex; align-items: center; gap: 8px; color: var(--color-text-2); font-size: 13px; }
.ai-model-editor__advanced { display: grid; gap: 12px; padding: 0 14px 14px; }
.ai-model-editor__footer { display: flex; justify-content: flex-end; gap: 8px; margin-top: 8px; }

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

@container (max-width: 650px) {
  .ai-config__defaults,
  .ai-editor__grid { grid-template-columns: minmax(0, 1fr); }
  .ai-config__purpose-control,
  .ai-config__defaults-hint { grid-column: 1; }
  .ai-field--inline { grid-template-columns: minmax(0, 1fr); }
  .ai-model-row { grid-template-columns: 28px minmax(0, 1fr) auto; }
  .ai-model-row__badges { grid-column: 2; grid-row: 2; }
  .ai-model-row__op { grid-column: 3; grid-row: 1 / span 2; }
}

@container (max-width: 390px) {
  .ai-config__endpoint-picker { grid-template-columns: minmax(0, 1fr); }
  .ai-config__endpoint-picker .ai-config__eyebrow { grid-column: 1; }
  .ai-model-row__op { flex-direction: column; align-items: flex-end; }
}
</style>
