<script setup lang="ts">
import type { TableColumn, YdTablePickerQuery, YdTablePickerResult } from '@yudream/components'
import type { AgentModelOption } from '@/api/modules/platform-agent'
import type { GraphTable } from '@/api/modules/platform-graph'
import type { WikiSpace } from '@/api/modules/platform-wiki'
import { computed, inject, onMounted, ref, watch } from 'vue'
import apiAgent from '@/api/modules/platform-agent'
import apiGraph from '@/api/modules/platform-graph'
import { saveWikiSpace } from '@/api/modules/platform-wiki'
import { wikiWorkbenchKey } from '../wiki-utils'
import WikiCoverPanel from './WikiCoverPanel.vue'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()

const form = ref<Partial<WikiSpace>>({})
const saving = ref(false)
const activeSection = ref<string>('basic')
const models = ref<AgentModelOption[]>([])
const catalogLoading = ref(false)
const catalogError = ref('')
const graphTables = ref<GraphTable[]>([])
const graphTableError = ref('')

// 设置分区：YdSettingsLayout 负责分区导航与滚动，保存放在页头，不再用页底固定条与多层卡片
const sectionList = [
  { label: '基本信息', value: 'basic', icon: 'morph:info' },
  { label: '封面', value: 'cover', icon: 'morph:image' },
  { label: 'AI 模型', value: 'models', icon: 'morph:cpu' },
  { label: '检索与切分', value: 'retrieval', icon: 'morph:search' },
  { label: '网络搜索', value: 'websearch', icon: 'morph:globe' },
  { label: '目录监听', value: 'watch', icon: 'morph:folder-sync' },
]

const graphTableColumns: TableColumn<GraphTable>[] = [
  { accessorKey: 'name', header: '图表名称', minWidth: 180 },
  { accessorKey: 'code', header: '编码', minWidth: 180 },
  { accessorKey: 'status', header: '状态', width: 100 },
]

const graphTableValue = computed<string[]>({
  get: () => form.value.graphTableCode ? [form.value.graphTableCode] : [],
  set: (value) => { form.value.graphTableCode = value[0] || undefined },
})

const graphTableInitialLabels = computed<Record<string, string>>(() => {
  const selected = graphTables.value.find(table => table.code === form.value.graphTableCode)
  return selected ? { [selected.code]: `${selected.name}（${selected.code}）` } : {}
})

async function fetchGraphTables(query: YdTablePickerQuery): Promise<YdTablePickerResult<GraphTable>> {
  const result = (await apiGraph.pageTables({ ...query, status: 'ACTIVE' })).data
  return { list: result.records, total: result.total }
}

watch(() => store.space.value, (space) => {
  form.value = space ? { ...space } : {}
}, { immediate: true })

/** 表单是否偏离当前知识库配置：治理区离开设置分区前的确认依据 */
const dirty = computed(() => {
  const space = store.space.value
  return Boolean(space) && JSON.stringify(form.value) !== JSON.stringify({ ...space })
})

defineExpose({ dirty })

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
const rerankFilter = (model: AgentModelOption) => model.kind === 'rerank'

const chatModelOptions = computed(() => modelOptions(form.value.chatProviderCode, chatFilter))
const ingestModelOptions = computed(() => modelOptions(form.value.ingestProviderCode, chatFilter))
const visionModelOptions = computed(() => modelOptions(form.value.visionProviderCode, visionFilter))
const embeddingModelOptions = computed(() => modelOptions(form.value.embeddingProviderCode, embeddingFilter))
const rerankModelOptions = computed(() => modelOptions(form.value.rerankProviderCode, rerankFilter))
const graphModelOptions = computed(() => modelOptions(form.value.graphProviderCode, chatFilter))

function onProviderChange(field: 'chat' | 'ingest' | 'vision' | 'embedding' | 'rerank' | 'graph', value: unknown) {
  const providerCode = typeof value === 'string' ? value : ''
  ;(form.value as Record<string, unknown>)[`${field}ProviderCode`] = providerCode || undefined
  ;(form.value as Record<string, unknown>)[`${field}ModelCode`] = undefined
  if (!providerCode) {
    return
  }
  // 自动选中该供应商下的默认/首个可用模型，避免只选供应商导致模型为空（后端要求成对配置）
  const filterMap = { chat: chatFilter, ingest: chatFilter, vision: visionFilter, embedding: embeddingFilter, rerank: rerankFilter, graph: chatFilter }
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
  catalogError.value = ''
  try {
    const res = await apiAgent.catalog()
    models.value = res.data?.models || []
  }
  catch (exception) {
    // 模型目录失败要留可见原因与重试入口，不能只留下空下拉
    models.value = []
    catalogError.value = exception instanceof Error ? exception.message : '模型目录加载失败'
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
  const filterMap = { chat: chatFilter, ingest: chatFilter, vision: visionFilter, embedding: embeddingFilter, rerank: rerankFilter, graph: chatFilter }
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

async function loadGraphTables() {
  graphTableError.value = ''
  try {
    const res = await apiGraph.pageTables({ page: 1, size: 100 })
    graphTables.value = res.data?.records || []
  }
  catch (exception) {
    graphTables.value = []
    graphTableError.value = exception instanceof Error ? exception.message : '逻辑图表列表加载失败'
  }
}

onMounted(loadCatalog)
onMounted(loadGraphTables)
</script>

<template>
  <div class="set-panel">
    <div v-if="form.id" class="settings-page-shell">
      <YdSettingsLayout
        v-model="activeSection"
        :list="sectionList"
        surface
        scroll
        class="set-layout"
      >
        <template #header>
          <div class="set-header">
            <div class="set-header__text">
              <h2 class="set-header__title">
                知识库设置
              </h2>
              <p class="set-header__desc">
                修改后点击保存才会写入；模型与索引参数会影响后续摄入、检索与问答。
              </p>
            </div>
            <YdButton :loading="saving" @click="save">
              <YdIcon name="morph:save" /> 保存设置
            </YdButton>
          </div>
        </template>

        <template #basic>
          <YdSettingsSection title="基本信息" description="名称、公开路径与自动生成方向">
            <div class="set-grid">
              <label class="set-field">
                <span>名称</span>
                <YdInput v-model="form.name" />
              </label>
              <label class="set-field">
                <span>路径 slug</span>
                <YdInput v-model="form.slug" />
              </label>
              <label class="set-field set-field--full">
                <span>描述</span>
                <YdInput v-model="form.description" />
              </label>
              <label class="set-field set-field--full">
                <span>Purpose（方向意图，指导自动生成页面）</span>
                <YdTextarea v-model="form.purpose" :rows="3" />
              </label>
              <label class="set-field set-field--full">
                <span>Schema（结构规则）</span>
                <YdTextarea v-model="form.schemaContent" :rows="3" />
              </label>
              <label class="set-field">
                <span>语言</span>
                <YdSelect v-model="form.language" :options="languageOptions" placeholder="默认中文" />
              </label>
            </div>
          </YdSettingsSection>
        </template>

        <template #cover>
          <YdSettingsSection title="封面" description="知识库入口卡片与公开门户的封面；封面操作立即生效，不随「保存设置」提交">
            <WikiCoverPanel embedded />
          </YdSettingsSection>
        </template>

        <template #models>
          <YdSettingsSection title="AI 模型" description="供应商与模型均从平台模型目录中选择，需要成对配置">
            <div class="set-grid set-grid--models">
              <div class="set-model">
                <span class="set-model__label"><YdIcon name="morph:message-circle" /> 问答模型（chat）</span>
                <YdSelect
                  :model-value="form.chatProviderCode"
                  :options="providerOptions"
                  placeholder="选择供应商"
                  @change="(value: unknown) => onProviderChange('chat', value)"
                />
                <YdSelect v-model="form.chatModelCode" :options="chatModelOptions" placeholder="选择模型" />
              </div>
              <div class="set-model">
                <span class="set-model__label"><YdIcon name="morph:feather" /> 摄入写作模型（ingest）</span>
                <YdSelect
                  :model-value="form.ingestProviderCode"
                  :options="providerOptions"
                  placeholder="选择供应商"
                  @change="(value: unknown) => onProviderChange('ingest', value)"
                />
                <YdSelect v-model="form.ingestModelCode" :options="ingestModelOptions" placeholder="选择模型" />
              </div>
              <div class="set-model">
                <span class="set-model__label"><YdIcon name="morph:eye" /> 视觉模型（图片 caption）</span>
                <YdSelect
                  :model-value="form.visionProviderCode"
                  :options="providerOptions"
                  placeholder="选择供应商"
                  @change="(value: unknown) => onProviderChange('vision', value)"
                />
                <YdSelect v-model="form.visionModelCode" :options="visionModelOptions" placeholder="选择支持视觉的模型" />
              </div>
              <div class="set-model">
                <span class="set-model__label"><YdIcon name="morph:feather" /> 向量模型（embedding）</span>
                <YdSelect
                  :model-value="form.embeddingProviderCode"
                  :options="providerOptions"
                  placeholder="选择供应商"
                  @change="(value: unknown) => onProviderChange('embedding', value)"
                />
                <YdSelect v-model="form.embeddingModelCode" :options="embeddingModelOptions" placeholder="选择向量模型" />
              </div>
              <div class="set-model">
                <span class="set-model__label"><YdIcon name="morph:waypoints" /> 图谱模型（graph）</span>
                <YdSelect
                  :model-value="form.graphProviderCode"
                  :options="providerOptions"
                  placeholder="选择供应商"
                  @change="(value: unknown) => onProviderChange('graph', value)"
                />
                <YdSelect v-model="form.graphModelCode" :options="graphModelOptions" placeholder="选择模型" />
              </div>
              <div class="set-model">
                <span class="set-model__label"><YdIcon name="morph:arrow-down-up" /> 重排序模型（rerank）</span>
                <YdSelect
                  :model-value="form.rerankProviderCode"
                  :options="providerOptions"
                  placeholder="留空沿用向量供应商"
                  @change="(value: unknown) => onProviderChange('rerank', value)"
                />
                <YdSelect v-model="form.rerankModelCode" :options="rerankModelOptions" placeholder="选择重排模型" />
                <p v-if="!form.rerankEnabled" class="set-model__note">
                  检索里的「重排序」开关当前关闭，选好模型后到「检索与切分」开启才会生效。
                </p>
                <p v-else-if="form.rerankProviderCode && !rerankModelOptions.length" class="set-model__note">
                  该供应商没有可用的重排序模型：请先在「平台能力 → AI 助手」的供应商配置里补充 rerank 模型，再回来选择。
                </p>
              </div>
              <p v-if="catalogLoading" class="set-hint">
                <YdIcon name="morph:loader-circle" /> 正在加载模型目录…
              </p>
              <!-- YdAlert 没有默认插槽：重试动作与告警相邻 -->
              <div v-else-if="catalogError" class="set-error">
                <YdAlert variant="destructive" title="模型目录加载失败" :description="catalogError" />
                <YdButton variant="outline" size="sm" :loading="catalogLoading" @click="loadCatalog">
                  <YdIcon name="morph:rotate-cw" /> 重新加载
                </YdButton>
              </div>
            </div>
          </YdSettingsSection>
        </template>

        <template #retrieval>
          <YdSettingsSection title="检索与切分" description="公开访问、召回策略与索引参数">
            <div class="set-grid">
              <label class="set-switch">
                <YdSwitch v-model="form.publicReadEnabled" />
                <span>公开访问<small>允许未登录访问 /wiki/{{ form.slug }}</small></span>
              </label>
              <label class="set-switch">
                <YdSwitch v-model="form.externalSearchEnabled" />
                <span>外部检索<small>允许公开检索接口</small></span>
              </label>
              <label class="set-switch">
                <YdSwitch v-model="form.sourceGroundedDefault" />
                <span>默认只读原文<small>检索默认命中资料原文</small></span>
              </label>
              <label class="set-switch">
                <YdSwitch v-model="form.graphEnabled" />
                <span>启用图谱<small>构建知识图谱用于扩展召回</small></span>
              </label>
              <label class="set-switch">
                <YdSwitch v-model="form.queryExpansionEnabled" />
                <span>查询扩展<small>检索时改写/扩展查询</small></span>
              </label>
              <label class="set-switch">
                <YdSwitch v-model="form.rerankEnabled" />
                <span>重排序<small>检索结果 rerank</small></span>
              </label>
              <label class="set-field set-field--full">
                <span>逻辑图表（Neo4j）</span>
                <YdTablePicker
                  v-model="graphTableValue"
                  :columns="graphTableColumns"
                  :fetcher="fetchGraphTables"
                  row-key="code"
                  label-key="name"
                  :multiple="false"
                  :initial-labels="graphTableInitialLabels"
                  title="选择逻辑图表"
                  placeholder="请选择逻辑图表"
                />
                <small class="set-hint">向量索引与知识图谱必须写入所选逻辑图表；未选择时不允许索引，图表在「平台 → 图数据库」维护。</small>
                <!-- YdAlert 没有默认插槽：重试动作与告警相邻 -->
                <div v-if="graphTableError" class="set-error">
                  <YdAlert variant="destructive" title="逻辑图表列表加载失败" :description="graphTableError" />
                  <YdButton variant="outline" size="sm" @click="loadGraphTables">
                    <YdIcon name="morph:rotate-cw" /> 重新加载
                  </YdButton>
                </div>
              </label>
              <label class="set-field">
                <span>命中图片上限（hitImageLimit）</span>
                <YdNumberField :model-value="form.hitImageLimit ?? 4" :min="0" :max="12" @update:model-value="(value: number) => form.hitImageLimit = value" />
                <small class="set-hint">检索/问答每条命中最多随带的相关图片数，0 表示不带图片</small>
              </label>
              <label class="set-field">
                <span>Top K</span>
                <YdNumberField :model-value="form.topK ?? 8" :min="1" :max="50" @update:model-value="(value: number) => form.topK = value" />
              </label>
              <label class="set-field">
                <span>切分大小（chunkSize）</span>
                <YdNumberField :model-value="form.chunkSize ?? 800" :min="200" :max="4000" :step="100" @update:model-value="(value: number) => form.chunkSize = value" />
              </label>
              <label class="set-field">
                <span>切分重叠（chunkOverlap）</span>
                <YdNumberField :model-value="form.chunkOverlap ?? 120" :min="0" :max="1000" :step="20" @update:model-value="(value: number) => form.chunkOverlap = value" />
              </label>
              <label class="set-field">
                <span>上下文窗口 tokens</span>
                <YdNumberField :model-value="form.contextWindowTokens ?? 64000" :min="4096" :step="4096" @update:model-value="(value: number) => form.contextWindowTokens = value" />
              </label>
            </div>
          </YdSettingsSection>
        </template>

        <template #websearch>
          <YdSettingsSection title="网络搜索" description="深度研究使用的外部搜索通道">
            <div class="set-grid">
              <label class="set-field">
                <span>供应商</span>
                <YdSelect v-model="form.webSearchProviderCode" :options="webSearchProviderOptions" placeholder="不启用" />
              </label>
              <label class="set-field">
                <span>搜索引擎（SearXNG）</span>
                <YdInput v-model="form.webSearchEngine" placeholder="google / bing…" />
              </label>
              <label class="set-field set-field--full">
                <span>实例地址（SearXNG）</span>
                <YdInput v-model="form.webSearchInstanceUrl" placeholder="https://searx.example.com" />
              </label>
              <label class="set-field set-field--full">
                <span>API Key（Tavily / SerpApi）</span>
                <YdInput v-model="form.webSearchApiKey" type="password" placeholder="留空则不修改" />
              </label>
            </div>
          </YdSettingsSection>
        </template>

        <template #watch>
          <YdSettingsSection title="目录监听" description="服务器本地目录变更自动导入">
            <div class="set-grid">
              <label class="set-switch">
                <YdSwitch v-model="form.watchEnabled" />
                <span>监听文件夹<small>服务器本地目录变更自动导入</small></span>
              </label>
              <label class="set-field">
                <span>监听路径</span>
                <YdInput v-model="form.watchFolderPath" placeholder="/data/wiki-watch" :disabled="!form.watchEnabled" />
              </label>
            </div>
          </YdSettingsSection>
        </template>
      </YdSettingsLayout>
    </div>

    <div v-else class="set-empty">
      <YdIcon name="morph:settings" />
      <strong>请先选择知识库</strong>
    </div>
  </div>
</template>

<style scoped>
.set-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

/* YdSettingsLayout 的 scroll 模式要求有界外壳：分区导航与页头固定，只有内容区滚动 */
.settings-page-shell {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.set-layout {
  flex: 1;
  min-height: 0;
}

.set-header {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.set-header__text {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.set-header__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-text-1);
}

.set-header__desc {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
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
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-text-3);
}

/* 模型一行一个：左侧模型名，右侧供应商与模型两个下拉；窄屏折行成两段 */
.set-grid--models {
  grid-template-columns: minmax(0, 1fr);
  gap: 10px;
}

.set-model {
  display: grid;
  grid-template-columns: minmax(150px, 190px) minmax(140px, 220px) minmax(0, 1fr);
  gap: 10px;
  align-items: center;
  padding: 10px 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.set-model__label {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.set-model__note {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

@media (width <= 1280px) {
  .set-model {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .set-model__label,
  .set-model__note {
    grid-column: 1 / -1;
  }
}

.set-model__label :deep(svg) {
  color: var(--color-text-2);
}

/* YdAlert 没有默认插槽：重试动作必须与告警相邻 */
.set-error {
  display: grid;
  grid-column: 1 / -1;
  gap: 8px;
}

.set-hint {
  display: flex;
  gap: 6px;
  align-items: center;
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.set-switch {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 14px 16px;
  cursor: pointer;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.set-switch > span {
  display: grid;
  gap: 3px;
  font-size: 13px;
  line-height: 1.5;
}

.set-switch small {
  font-size: 11px;
  color: var(--color-text-3);
}

.set-empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 60px 0;
  color: var(--color-text-3);
}

.set-empty :deep(svg) {
  font-size: 36px;
  color: var(--color-text-2);
}

@media (width <= 900px) {
  .set-grid {
    grid-template-columns: 1fr;
  }
}
</style>
