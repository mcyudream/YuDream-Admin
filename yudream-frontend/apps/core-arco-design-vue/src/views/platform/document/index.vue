<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { GenerationStatus, TemplateStatus, WordGenerateData, WordGenerationRecord, WordTemplate, WordTemplatePayload } from '@/api/modules/platform-document'
import apiDocument from '@/api/modules/platform-document'

const modal = useFaModal()
const toast = useFaToast()

const activeTab = ref<'templates' | 'records'>('templates')
const loading = ref(false)
const actionLoading = ref('')
const rows = ref<WordTemplate[]>([])
const recordRows = ref<WordGenerationRecord[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive({ keyword: '' })

const fileInput = ref<HTMLInputElement>()
const replaceInput = ref<HTMLInputElement>()
const pendingFile = ref<File | null>(null)
const replacingTemplate = ref<WordTemplate | null>(null)
const formVisible = ref(false)
const editing = ref<WordTemplate | null>(null)
const placeholdersJson = ref('{}')
const form = reactive<WordTemplatePayload>({
  name: '',
  code: '',
  placeholders: {},
  description: '',
  status: 'ACTIVE',
})

const generateVisible = ref(false)
const generatingTemplate = ref<WordTemplate | null>(null)
const generateDataJson = ref('{}')
const lastRecord = ref<WordGenerationRecord | null>(null)

const tabs = [
  { key: 'templates', label: '模板管理', icon: 'i-ri:file-word-2-line' },
  { key: 'records', label: '生成记录', icon: 'i-ri:file-list-3-line' },
] as const
const statusOptions: { label: string, value: TemplateStatus }[] = [
  { label: '启用', value: 'ACTIVE' },
  { label: '停用', value: 'DISABLED' },
]

const templateColumns = computed<TableColumn<WordTemplate>[]>(() => [
  { accessorKey: 'name', header: '模板名称', width: 180, fixed: 'left' },
  { accessorKey: 'code', header: '模板编码', width: 160 },
  { accessorKey: 'originalFilename', header: '模板文件', width: 220 },
  { id: 'placeholders', header: '占位符', width: 260 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'updateTime', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 340, align: 'center', fixed: 'right' },
])

const recordColumns = computed<TableColumn<WordGenerationRecord>[]>(() => [
  { accessorKey: 'templateCode', header: '模板编码', width: 160, fixed: 'left' },
  { accessorKey: 'outputFilename', header: '生成文件', width: 260 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'operatorId', header: '操作人', width: 140 },
  { id: 'generatedAt', header: '生成时间', width: 180 },
  { id: 'errorMessage', header: '异常', width: 300 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

onMounted(load)

watch(activeTab, () => {
  pagination.page = 1
  pagination.total = 0
  load()
})

async function load() {
  loading.value = true
  try {
    const params = {
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
    }
    if (activeTab.value === 'templates') {
      const res = await apiDocument.pageTemplates(params)
      rows.value = res.data.records
      pagination.total = res.data.total
    }
    else {
      const res = await apiDocument.pageRecords(params)
      recordRows.value = res.data.records
      pagination.total = res.data.total
    }
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  pagination.page = 1
  load()
}

function onPageChange(page: number) {
  pagination.page = page
  load()
}

function onSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  load()
}

function pickCreateFile() {
  fileInput.value?.click()
}

function onCreateFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  pendingFile.value = file
  openCreate()
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    name: stripExt(pendingFile.value?.name || ''),
    code: '',
    placeholders: {},
    description: '',
    status: 'ACTIVE' as TemplateStatus,
  })
  placeholdersJson.value = '{}'
  formVisible.value = true
}

function openEdit(row: WordTemplate) {
  editing.value = row
  pendingFile.value = null
  Object.assign(form, {
    name: row.name,
    code: row.code,
    placeholders: row.placeholders || {},
    description: row.description || '',
    status: row.status || 'ACTIVE',
  })
  placeholdersJson.value = JSON.stringify(row.placeholders || {}, null, 2)
  formVisible.value = true
}

async function saveForm() {
  const placeholders = parsePlaceholdersJson(placeholdersJson.value)
  if (!placeholders) {
    return
  }
  const payload = { ...form, placeholders }
  if (editing.value) {
    await apiDocument.updateTemplate(editing.value.id, payload)
  }
  else {
    if (!pendingFile.value) {
      toast.error('请先选择 DOCX 模板文件')
      return
    }
    await apiDocument.uploadTemplate(pendingFile.value, payload)
  }
  toast.success('Word 模板已保存')
  formVisible.value = false
  pendingFile.value = null
  await load()
}

function pickReplaceFile(row: WordTemplate) {
  replacingTemplate.value = row
  replaceInput.value?.click()
}

async function onReplaceFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const target = replacingTemplate.value
  input.value = ''
  if (!file || !target) {
    return
  }
  await apiDocument.replaceTemplateFile(target.id, file)
  toast.success('模板文件已替换')
  replacingTemplate.value = null
  await load()
}

function confirmDisable(row: WordTemplate) {
  modal.confirm({
    title: '确认停用',
    content: `确认停用 Word 模板「${row.name}」吗？`,
    onConfirm: async () => {
      await apiDocument.disableTemplate(row.id)
      toast.success('Word 模板已停用')
      await load()
    },
  })
}

function openGenerate(row: WordTemplate) {
  generatingTemplate.value = row
  generateDataJson.value = JSON.stringify(defaultGenerateData(row), null, 2)
  lastRecord.value = null
  generateVisible.value = true
}

async function generate() {
  if (!generatingTemplate.value) {
    return
  }
  const data = parseJson(generateDataJson.value, '生成数据')
  if (!data) {
    return
  }
  actionLoading.value = 'generate'
  try {
    const res = await apiDocument.generate(generatingTemplate.value.id, data)
    lastRecord.value = res.data
    toast.success('Word 文档已生成')
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

function parsePlaceholdersJson(value: string) {
  const parsed = parseJson(value, '占位符')
  if (!parsed) {
    return null
  }
  const result: Record<string, string> = {}
  for (const [key, item] of Object.entries(parsed)) {
    if (item != null && typeof item !== 'string') {
      toast.error('占位符说明必须是字符串', { description: `请检查 ${key}` })
      return null
    }
    result[key] = item || ''
  }
  return result
}

function parseJson(value: string, label: string) {
  try {
    const parsed = value ? JSON.parse(value) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      toast.error(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed as WordGenerateData
  }
  catch {
    toast.error(`${label}格式错误`, { description: '请输入合法 JSON 对象' })
    return null
  }
}

function defaultGenerateData(row: WordTemplate) {
  const result: Record<string, string> = {}
  Object.entries(row.placeholders || {}).forEach(([key, label]) => {
    result[key] = label || ''
  })
  return result
}

function openFile(url?: string) {
  if (url) {
    window.open(url, '_blank', 'noopener,noreferrer')
  }
}

function stripExt(name: string) {
  return name.replace(/\.[^.]+$/, '')
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function statusText(status: TemplateStatus) {
  return status === 'ACTIVE' ? '启用' : '停用'
}

function statusVariant(status: TemplateStatus) {
  return status === 'ACTIVE' ? 'default' : 'secondary'
}

function generationText(status: GenerationStatus) {
  return status === 'SUCCESS' ? '成功' : '失败'
}

function generationVariant(status: GenerationStatus) {
  return status === 'SUCCESS' ? 'default' : 'destructive'
}
</script>

<template>
  <div>
    <FaPageHeader title="Word 模板" class="mb-0">
      <FaButton v-if="activeTab === 'templates'" v-auth="'platform:document:edit'" @click="pickCreateFile">
        <FaIcon name="i-ri:upload-cloud-2-line" />
        上传模板
      </FaButton>
      <input ref="fileInput" type="file" accept=".docx" hidden @change="onCreateFileChange">
      <input ref="replaceInput" type="file" accept=".docx" hidden @change="onReplaceFileChange">
    </FaPageHeader>

    <FaPageMain>
      <div class="document-tabs">
        <button v-for="tab in tabs" :key="tab.key" type="button" :class="{ active: activeTab === tab.key }" @click="activeTab = tab.key">
          <FaIcon :name="tab.icon" />
          <span>{{ tab.label }}</span>
        </button>
      </div>

      <section v-if="activeTab === 'templates'" class="template-guide" aria-label="Word 模板写法教程">
        <header class="template-guide-header">
          <div>
            <span class="guide-eyebrow">Template Guide</span>
            <h2>Word 模板写法教程</h2>
          </div>
          <p>在 Word 文档里写变量和循环，上传到这里维护；业务插件只需要选择已启用的模板。</p>
        </header>

        <div class="guide-grid">
          <article class="guide-section">
            <h3>1. 基础变量</h3>
            <p>正文、表格、页眉和页脚都支持变量替换。变量名来自生成数据 JSON。</p>
            <div class="guide-code-row">
              <code v-pre>{{activityName}}</code>
              <code>${activityName}</code>
              <code v-pre>{{student.name}}</code>
            </div>
            <p>点路径会读取嵌套对象，例如 <code v-pre>{{student.name}}</code> 对应 <code>student.name</code>。</p>
          </article>

          <article class="guide-section">
            <h3>2. 表格行循环</h3>
            <p>需要复制 Word 表格行时，在开始行写集合标记，在结束行写关闭标记，中间行会按集合数据重复。</p>
            <pre v-pre><code>{{#participants}}
{{index}}  {{studentName}}  {{className}}  {{studentNo}}
{{/participants}}</code></pre>
            <p>循环里的字段会优先读取当前成员，也可以用 <code v-pre>{{item.studentName}}</code> 明确访问当前成员。</p>
          </article>

          <article class="guide-section">
            <h3>3. 段落内循环</h3>
            <p>如果只想在同一段文字里拼接名单，可以把循环写在一个段落中。</p>
            <pre v-pre><code>参与成员：{{#participants}}{{studentName}}、{{/participants}}</code></pre>
          </article>

          <article class="guide-section">
            <h3>4. 名单表自动追加</h3>
            <p>活动证明这类名单表可以不写循环。表头包含“姓名”和“学号”时，传入 <code>participantTableAppend: true</code> 后会自动填充空位；空位满了会复制最后一行继续追加。</p>
            <p>推荐表头：<code>姓名 / 专业班级 / 学号 / 空列 / 姓名 / 专业班级 / 学号</code>。</p>
          </article>
        </div>

        <div class="guide-detail-grid">
          <section class="guide-section">
            <h3>活动证明常用数据</h3>
            <pre v-pre><code>{
  "proofNo": "NO.202607080001",
  "activityName": "Minecraft 星空社活动",
  "activityDate": "2026年7月8日",
  "college": "计算机科学与技术学院",
  "issuer": "Minecraft 星空社",
  "issueDate": "2026年7月8日",
  "serverName": "生存服",
  "currentSeasonName": "第 1 周目",
  "participantCount": 2,
  "participantTableAppend": true,
  "participants": [
    {
      "index": 1,
      "studentName": "张三",
      "className": "计科2401",
      "studentNo": "5120240001",
      "college": "计算机科学与技术学院",
      "playerName": "Steve",
      "effectiveOnlineMillis": 7200000
    }
  ]
}</code></pre>
          </section>

          <section class="guide-section guide-notes">
            <h3>维护建议</h3>
            <ul>
              <li>“占位符 JSON”用于记录模板变量说明，也会作为手动生成时的默认 JSON；真实替换以生成数据为准。</li>
              <li>变量名建议只用字母、数字、下划线、点和短横线，避免中文变量名。</li>
              <li>循环开始和结束标记建议放在表格行内，避免跨多个不连续表格。</li>
              <li>需要给活动证明使用的模板，先在这里上传并保持“启用”，再到学生信息下的“活动证明导出”选择。</li>
            </ul>
          </section>
        </div>
      </section>

      <FaSearchBar>
        <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_auto] md:items-center">
          <FaInput v-model="search.keyword" clearable placeholder="模板名称 / 编码 / 文件名" @keydown.enter="load" @clear="load" />
          <div class="flex gap-2 md:justify-end">
            <FaButton variant="outline" @click="resetSearch">
              重置
            </FaButton>
            <FaButton :loading="loading" @click="load">
              <FaIcon name="i-ri:search-line" />
              筛选
            </FaButton>
          </div>
        </div>
      </FaSearchBar>

      <div class="mx--4 my-3 border-t border-t-dashed" />

      <FaTable
        v-if="activeTab === 'templates'"
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1320px]"
        border
        stripe
        column-visibility
        :columns="templateColumns"
        :data="rows"
      >
        <template #cell-placeholders="{ row }">
          <div class="placeholder-tags">
            <FaTag v-for="(_, key) in row.original.placeholders || {}" :key="key" variant="secondary">
              {{ key }}
            </FaTag>
            <span v-if="!Object.keys(row.original.placeholders || {}).length">-</span>
          </div>
        </template>
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">
            {{ statusText(row.original.status) }}
          </FaTag>
        </template>
        <template #cell-updateTime="{ row }">
          {{ dateText(row.original.updateTime) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <FaButton v-auth="'platform:document:generate'" size="sm" variant="outline" :disabled="row.original.status !== 'ACTIVE'" @click="openGenerate(row.original)">
              生成
            </FaButton>
            <FaButton size="sm" variant="ghost" @click="openFile(row.original.templateFileUrl)">
              原文件
            </FaButton>
            <FaButton v-auth="'platform:document:edit'" size="sm" variant="ghost" @click="openEdit(row.original)">
              编辑
            </FaButton>
            <FaButton v-auth="'platform:document:edit'" size="sm" variant="ghost" @click="pickReplaceFile(row.original)">
              替换
            </FaButton>
            <FaButton v-auth="'platform:document:edit'" size="sm" variant="ghost" :disabled="row.original.status !== 'ACTIVE'" @click="confirmDisable(row.original)">
              停用
            </FaButton>
          </div>
        </template>
      </FaTable>

      <FaTable
        v-else
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1160px]"
        border
        stripe
        column-visibility
        :columns="recordColumns"
        :data="recordRows"
      >
        <template #cell-status="{ row }">
          <FaTag :variant="generationVariant(row.original.status)">
            {{ generationText(row.original.status) }}
          </FaTag>
        </template>
        <template #cell-generatedAt="{ row }">
          {{ dateText(row.original.generatedAt) }}
        </template>
        <template #cell-errorMessage="{ row }">
          <span class="line-clamp-1">{{ row.original.errorMessage || '-' }}</span>
        </template>
        <template #cell-operation="{ row }">
          <FaButton size="sm" variant="outline" :disabled="!row.original.outputFileUrl" @click="openFile(row.original.outputFileUrl)">
            下载
          </FaButton>
        </template>
      </FaTable>

      <FaPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </FaPageMain>

    <FaModal v-model="formVisible" :title="editing ? '编辑 Word 模板' : '上传 Word 模板'" show-cancel-button class="sm:max-w-4xl" @confirm="saveForm">
      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="模板名称" required>
            <FaInput v-model="form.name" />
          </a-form-item>
          <a-form-item label="模板编码" required>
            <FaInput v-model="form.code" :disabled="!!editing" />
          </a-form-item>
          <a-form-item label="状态">
            <FaSelect v-model="form.status" :options="statusOptions" />
          </a-form-item>
          <a-form-item label="模板文件">
            <FaInput :model-value="editing?.originalFilename || pendingFile?.name || '-'" disabled />
          </a-form-item>
          <a-form-item label="占位符 JSON" class="md:col-span-2">
            <FaTextarea v-model="placeholdersJson" rows="8" input-class="font-mono" placeholder="{ &quot;name&quot;: &quot;姓名&quot;, &quot;date&quot;: &quot;日期&quot; }" />
          </a-form-item>
          <a-form-item label="描述" class="md:col-span-2">
            <FaTextarea v-model="form.description" rows="4" />
          </a-form-item>
        </div>
      </a-form>
    </FaModal>

    <FaModal v-model="generateVisible" title="生成 Word 文档" show-cancel-button class="sm:max-w-4xl" :confirm-loading="actionLoading === 'generate'" @confirm="generate">
      <div class="generate-grid">
        <a-form :model="{}" layout="vertical">
          <a-form-item label="生成数据 JSON">
            <FaTextarea v-model="generateDataJson" rows="14" input-class="font-mono" />
          </a-form-item>
        </a-form>
        <section class="result-panel">
          <div class="section-title">
            最近结果
          </div>
          <pre>{{ lastRecord ? JSON.stringify(lastRecord, null, 2) : '尚未生成' }}</pre>
          <FaButton v-if="lastRecord?.outputFileUrl" variant="outline" @click="openFile(lastRecord.outputFileUrl)">
            <FaIcon name="i-ri:download-2-line" />
            下载生成文件
          </FaButton>
        </section>
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.document-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.document-tabs button {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  height: 36px;
  padding: 0 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
  color: var(--color-text-2);
}

.document-tabs button.active,
.document-tabs button:hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.template-guide {
  display: grid;
  gap: 14px;
  margin-bottom: 16px;
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: linear-gradient(180deg, var(--color-bg-2), var(--color-fill-1));
}

.template-guide-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(240px, 420px);
  gap: 16px;
  align-items: end;
}

.template-guide-header h2,
.guide-section h3 {
  margin: 0;
  color: var(--color-text-1);
}

.template-guide-header h2 {
  margin-top: 4px;
  font-size: 20px;
  font-weight: 700;
}

.template-guide-header p,
.guide-section p,
.guide-notes li {
  margin: 0;
  color: var(--color-text-2);
  line-height: 1.7;
}

.guide-eyebrow {
  color: rgb(var(--primary-6));
  font-size: 12px;
  font-weight: 700;
}

.guide-grid,
.guide-detail-grid {
  display: grid;
  gap: 12px;
}

.guide-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.guide-detail-grid {
  grid-template-columns: minmax(0, 1.15fr) minmax(300px, 0.85fr);
}

.guide-section {
  display: grid;
  gap: 10px;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
}

.guide-section h3 {
  font-size: 15px;
  font-weight: 700;
}

.guide-section code {
  display: inline-flex;
  max-width: 100%;
  padding: 2px 6px;
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.guide-code-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.guide-section pre {
  max-width: 100%;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: #15181d;
  color: #f4f7fb;
}

.guide-section pre code {
  display: block;
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  line-height: 1.6;
  white-space: pre;
}

.guide-notes ul {
  display: grid;
  gap: 8px;
  margin: 0;
  padding-left: 18px;
}

.placeholder-tags,
.table-actions {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 6px;
}

.table-actions {
  justify-content: center;
}

.generate-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(280px, 360px);
  gap: 16px;
  align-items: start;
}

.result-panel {
  display: grid;
  gap: 10px;
  min-width: 0;
}

.section-title {
  color: var(--color-text-1);
  font-weight: 700;
}

.result-panel pre {
  max-height: 420px;
  min-height: 220px;
  overflow: auto;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
  color: var(--color-text-1);
  font-size: 12px;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 900px) {
  .template-guide-header,
  .guide-grid,
  .guide-detail-grid {
    grid-template-columns: 1fr;
  }

  .generate-grid {
    grid-template-columns: 1fr;
  }
}
</style>
