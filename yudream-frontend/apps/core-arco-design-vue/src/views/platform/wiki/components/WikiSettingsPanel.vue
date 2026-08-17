<script setup lang="ts">
import type { AgentModelOption } from '@/api/modules/platform-agent'
import type { WikiSpace } from '@/api/modules/platform-wiki'
import { computed, inject, onMounted, ref, watch } from 'vue'
import apiAgent from '@/api/modules/platform-agent'
import { deleteWikiSpace, saveWikiSpace } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()
const modal = useFaModal()

const form = ref<Partial<WikiSpace>>({})
const saving = ref(false)
const models = ref<AgentModelOption[]>([])
const catalogLoading = ref(false)

watch(() => store.space.value, (space) => {
  form.value = space ? { ...space } : {}
}, { immediate: true })

// 供应商下拉（按 providerCode 去重）
const providerOptions = computed(() => {
  const seen = new Map<string, string>()
  for (const model of models.value) {
    if (!seen.has(model.providerCode)) {
      seen.set(model.providerCode, model.providerName || model.providerCode)
    }
  }
  return [...seen.entries()].map(([value, label]) => ({ label, value }))
})

function modelOptions(providerCode: string | undefined, filter: (model: AgentModelOption) => boolean) {
  return models.value
    .filter(model => model.providerCode === providerCode && filter(model))
    .map(model => ({
      label: `${model.modelName || model.modelCode}${model.defaultModel ? '（默认）' : ''}${model.configured === false ? '（未配置）' : ''}`,
      value: model.modelCode,
    }))
}

const chatFilter = (model: AgentModelOption) => model.kind === 'chat'
const visionFilter = (model: AgentModelOption) => model.vision
const embeddingFilter = (model: AgentModelOption) => model.kind === 'embedding'

const chatModelOptions = computed(() => modelOptions(form.value.chatProviderCode, chatFilter))
const ingestModelOptions = computed(() => modelOptions(form.value.ingestProviderCode, chatFilter))
const visionModelOptions = computed(() => modelOptions(form.value.visionProviderCode, visionFilter))
const embeddingModelOptions = computed(() => modelOptions(form.value.embeddingProviderCode, embeddingFilter))
const graphModelOptions = computed(() => modelOptions(form.value.graphProviderCode, chatFilter))

function onProviderChange(field: 'chat' | 'ingest' | 'vision' | 'embedding' | 'graph', value: unknown) {
  const providerCode = typeof value === 'string' ? value : ''
  ;(form.value as Record<string, unknown>)[`${field}ProviderCode`] = providerCode || undefined
  ;(form.value as Record<string, unknown>)[`${field}ModelCode`] = undefined
  if (!providerCode) {
    return
  }
  // 自动选中该供应商下的默认/首个可用模型，避免只选供应商导致模型为空（后端要求成对配置）
  const filterMap = { chat: chatFilter, ingest: chatFilter, vision: visionFilter, embedding: embeddingFilter, graph: chatFilter }
  const candidates = models.value.filter(model => model.providerCode === providerCode && filterMap[field](model))
  const preferred = candidates.find(model => model.defaultModel) || candidates[0]
  if (preferred) {
    ;(form.value as Record<string, unknown>)[`${field}ModelCode`] = preferred.modelCode
  }
}

const webSearchProviderOptions = [
  { label: '不启用', value: '' },
  { label: 'Tavily', value: 'tavily' },
  { label: 'SerpApi', value: 'serpapi' },
  { label: 'SearXNG', value: 'searxng' },
]

const languageOptions = [
  { label: '中文', value: 'zh-CN' },
  { label: 'English', value: 'en' },
]

async function loadCatalog() {
  catalogLoading.value = true
  try {
    const res = await apiAgent.catalog()
    models.value = res.data?.models || []
  }
  catch {
    toast.warning('模型目录加载失败，模型下拉将为空')
  }
  finally {
    catalogLoading.value = false
  }
}

async function save() {
  if (!form.value.id) {
    return
  }
  // 保存兜底：供应商已选但模型为空时，用目录中的默认/首个模型补齐（后端要求 provider+model 成对）
  const filterMap = { chat: chatFilter, ingest: chatFilter, vision: visionFilter, embedding: embeddingFilter, graph: chatFilter }
  for (const field of Object.keys(filterMap) as (keyof typeof filterMap)[]) {
    const record = form.value as Record<string, unknown>
    const providerCode = record[`${field}ProviderCode`]
    if (typeof providerCode === 'string' && providerCode && !record[`${field}ModelCode`]) {
      const candidates = models.value.filter(model => model.providerCode === providerCode && filterMap[field](model))
      const preferred = candidates.find(model => model.defaultModel) || candidates[0]
      if (preferred) {
        record[`${field}ModelCode`] = preferred.modelCode
      }
    }
  }
  saving.value = true
  try {
    await saveWikiSpace(form.value as WikiSpace)
    toast.success('设置已保存')
    await store.reloadTree()
  }
  finally {
    saving.value = false
  }
}

const deleting = ref(false)

// 删除知识库：后端级联删除目录/页面、资料源、摄入任务与审核项
function removeSpace() {
  const space = store.space.value
  if (!space?.id) {
    return
  }
  modal.confirm({
    title: '删除知识库',
    content: `确定删除知识库「${space.name}」吗？其中的目录、页面、资料源、摄入任务与审核项将被一并删除，且不可恢复。`,
    onConfirm: async () => {
      deleting.value = true
      try {
        await deleteWikiSpace(space.id!)
        toast.success('知识库已删除')
        store.selectNode(null)
        store.spaceId.value = ''
        await store.reloadSpaces()
      }
      finally {
        deleting.value = false
      }
    },
  })
}

onMounted(loadCatalog)
</script>

<template>
  <div class="set-panel">
    <FaScrollArea class="set-scroll">
      <div v-if="form.id" class="set-inner">
        <FaCard title="基本信息" class="set-card">
          <div class="set-grid">
            <label class="set-field">
              <span>名称</span>
              <FaInput v-model="form.name" />
            </label>
            <label class="set-field">
              <span>路径 slug</span>
              <FaInput v-model="form.slug" />
            </label>
            <label class="set-field set-field--full">
              <span>描述</span>
              <FaInput v-model="form.description" />
            </label>
            <label class="set-field set-field--full">
              <span>Purpose（方向意图，指导自动生成页面）</span>
              <FaTextarea v-model="form.purpose" :rows="3" />
            </label>
            <label class="set-field set-field--full">
              <span>Schema（结构规则）</span>
              <FaTextarea v-model="form.schemaContent" :rows="3" />
            </label>
            <label class="set-field">
              <span>语言</span>
              <FaSelect v-model="form.language" :options="languageOptions" placeholder="默认中文" />
            </label>
          </div>
        </FaCard>

        <FaCard title="AI 模型" description="供应商与模型均从平台模型目录中选择" class="set-card">
          <div class="set-grid">
            <div class="set-model">
              <span class="set-model__label"><FaIcon name="i-ri:chat-3-line" /> 问答模型（chat）</span>
              <FaSelect
                :model-value="form.chatProviderCode"
                :options="providerOptions"
                placeholder="选择供应商"
                @change="(value: unknown) => onProviderChange('chat', value)"
              />
              <FaSelect v-model="form.chatModelCode" :options="chatModelOptions" placeholder="选择模型" />
            </div>
            <div class="set-model">
              <span class="set-model__label"><FaIcon name="i-ri:quill-pen-line" /> 摄入写作模型（ingest）</span>
              <FaSelect
                :model-value="form.ingestProviderCode"
                :options="providerOptions"
                placeholder="选择供应商"
                @change="(value: unknown) => onProviderChange('ingest', value)"
              />
              <FaSelect v-model="form.ingestModelCode" :options="ingestModelOptions" placeholder="选择模型" />
            </div>
            <div class="set-model">
              <span class="set-model__label"><FaIcon name="i-ri:eye-line" /> 视觉模型（图片 caption）</span>
              <FaSelect
                :model-value="form.visionProviderCode"
                :options="providerOptions"
                placeholder="选择供应商"
                @change="(value: unknown) => onProviderChange('vision', value)"
              />
              <FaSelect v-model="form.visionModelCode" :options="visionModelOptions" placeholder="选择支持视觉的模型" />
            </div>
            <div class="set-model">
              <span class="set-model__label"><FaIcon name="i-ri:vector-pen-line" /> 向量模型（embedding）</span>
              <FaSelect
                :model-value="form.embeddingProviderCode"
                :options="providerOptions"
                placeholder="选择供应商"
                @change="(value: unknown) => onProviderChange('embedding', value)"
              />
              <FaSelect v-model="form.embeddingModelCode" :options="embeddingModelOptions" placeholder="选择向量模型" />
            </div>
            <div class="set-model">
              <span class="set-model__label"><FaIcon name="i-ri:mind-map" /> 图谱模型（graph）</span>
              <FaSelect
                :model-value="form.graphProviderCode"
                :options="providerOptions"
                placeholder="选择供应商"
                @change="(value: unknown) => onProviderChange('graph', value)"
              />
              <FaSelect v-model="form.graphModelCode" :options="graphModelOptions" placeholder="选择模型" />
            </div>
            <p v-if="catalogLoading" class="set-hint"><FaIcon name="i-ri:loader-4-line" /> 正在加载模型目录…</p>
          </div>
        </FaCard>

        <FaCard title="检索与切分" class="set-card">
          <div class="set-grid">
            <label class="set-switch">
              <FaSwitch v-model="form.publicReadEnabled" />
              <span>公开访问<small>允许未登录访问 /wiki/{{ form.slug }}</small></span>
            </label>
            <label class="set-switch">
              <FaSwitch v-model="form.externalSearchEnabled" />
              <span>外部检索<small>允许公开检索接口</small></span>
            </label>
            <label class="set-switch">
              <FaSwitch v-model="form.sourceGroundedDefault" />
              <span>默认只读原文<small>检索默认命中资料原文</small></span>
            </label>
            <label class="set-switch">
              <FaSwitch v-model="form.graphEnabled" />
              <span>启用图谱<small>构建知识图谱用于扩展召回</small></span>
            </label>
            <label class="set-switch">
              <FaSwitch v-model="form.queryExpansionEnabled" />
              <span>查询扩展<small>检索时改写/扩展查询</small></span>
            </label>
            <label class="set-switch">
              <FaSwitch v-model="form.rerankEnabled" />
              <span>重排序<small>检索结果 rerank</small></span>
            </label>
            <label class="set-field">
              <span>Top K</span>
              <FaNumberField :model-value="form.topK ?? 8" :min="1" :max="50" @update:model-value="(value: number) => form.topK = value" />
            </label>
            <label class="set-field">
              <span>切分大小（chunkSize）</span>
              <FaNumberField :model-value="form.chunkSize ?? 800" :min="200" :max="4000" :step="100" @update:model-value="(value: number) => form.chunkSize = value" />
            </label>
            <label class="set-field">
              <span>切分重叠（chunkOverlap）</span>
              <FaNumberField :model-value="form.chunkOverlap ?? 120" :min="0" :max="1000" :step="20" @update:model-value="(value: number) => form.chunkOverlap = value" />
            </label>
            <label class="set-field">
              <span>上下文窗口 tokens</span>
              <FaNumberField :model-value="form.contextWindowTokens ?? 64000" :min="4096" :step="4096" @update:model-value="(value: number) => form.contextWindowTokens = value" />
            </label>
          </div>
        </FaCard>

        <FaCard title="网络搜索（深度研究用）" class="set-card">
          <div class="set-grid">
            <label class="set-field">
              <span>供应商</span>
              <FaSelect v-model="form.webSearchProviderCode" :options="webSearchProviderOptions" placeholder="不启用" />
            </label>
            <label class="set-field">
              <span>搜索引擎（SearXNG）</span>
              <FaInput v-model="form.webSearchEngine" placeholder="google / bing…" />
            </label>
            <label class="set-field set-field--full">
              <span>实例地址（SearXNG）</span>
              <FaInput v-model="form.webSearchInstanceUrl" placeholder="https://searx.example.com" />
            </label>
            <label class="set-field set-field--full">
              <span>API Key（Tavily / SerpApi）</span>
              <FaInput v-model="form.webSearchApiKey" type="password" placeholder="留空则不修改" />
            </label>
          </div>
        </FaCard>

        <FaCard title="目录监听" class="set-card">
          <div class="set-grid">
            <label class="set-switch">
              <FaSwitch v-model="form.watchEnabled" />
              <span>监听文件夹<small>服务器本地目录变更自动导入</small></span>
            </label>
            <label class="set-field">
              <span>监听路径</span>
              <FaInput v-model="form.watchFolderPath" placeholder="/data/wiki-watch" :disabled="!form.watchEnabled" />
            </label>
          </div>
        </FaCard>

        <FaCard title="危险操作" class="set-card">
          <div class="set-danger">
            <div>
              <strong>删除知识库</strong>
              <p>目录、页面、资料源、摄入任务与审核项将被一并删除，不可恢复。</p>
            </div>
            <FaButton variant="destructive" :loading="deleting" @click="removeSpace">
              <FaIcon name="i-ri:delete-bin-line" /> 删除知识库
            </FaButton>
          </div>
        </FaCard>

        <div class="set-actions">
          <FaButton :loading="saving" @click="save">
            <FaIcon name="i-ri:save-3-line" /> 保存设置
          </FaButton>
        </div>
      </div>

      <div v-else class="set-empty">
        <FaIcon name="i-ri:settings-3-line" />
        <strong>请先选择知识库</strong>
      </div>
    </FaScrollArea>
  </div>
</template>

<style scoped>
.set-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.set-scroll {
  height: 100%;
}

.set-inner {
  display: grid;
  gap: 20px;
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 24px 48px;
}

.set-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px 16px;
}

.set-field {
  display: grid;
  gap: 8px;
  align-content: start;
}

.set-field--full {
  grid-column: 1 / -1;
}

.set-field > span {
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.5;
}

.set-model {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  align-content: start;
}

.set-model__label {
  display: inline-flex;
  grid-column: 1 / -1;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
}

.set-model__label :deep(svg) {
  color: rgb(var(--primary-6));
}

.set-danger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.set-danger strong {
  color: var(--color-text-1);
  font-size: 13px;
}

.set-danger p {
  margin: 4px 0 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.set-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
}

.set-switch {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 14px 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  cursor: pointer;
}

.set-switch > span {
  display: grid;
  gap: 3px;
  font-size: 13px;
  line-height: 1.5;
}

.set-switch small {
  color: var(--color-text-3);
  font-size: 11px;
}

.set-actions {
  display: flex;
  justify-content: flex-end;
}

.set-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--color-text-3);
}

.set-empty :deep(svg) {
  color: rgb(var(--primary-6));
  font-size: 36px;
}

@media (max-width: 900px) {
  .set-grid {
    grid-template-columns: 1fr;
  }
}
</style>
