<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { GenerationStatus, TemplateStatus, WordGenerationRecord, WordTemplate, WordTemplatePayload } from '@/api/modules/platform-document'
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
  const placeholders = parseJson(placeholdersJson.value, '占位符')
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

function parseJson(value: string, label: string) {
  try {
    const parsed = value ? JSON.parse(value) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      toast.error(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed as Record<string, string>
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
      <FaButton v-auth="'platform:document:view'" variant="outline" :loading="loading" @click="load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
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
  .generate-grid {
    grid-template-columns: 1fr;
  }
}
</style>
