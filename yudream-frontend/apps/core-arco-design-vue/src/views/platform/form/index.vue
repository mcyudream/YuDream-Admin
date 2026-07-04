<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { DynamicForm, DynamicFormPayload, DynamicFormStatus, FormStatistics, FormSubmission } from '@/api/modules/platform-form'
import apiForm from '@/api/modules/platform-form'

interface DesignerRef {
  getRule: () => unknown[]
  getOption: () => Record<string, unknown>
  setRule: (rules: unknown[]) => void
  setOption: (option: Record<string, unknown>) => void
}

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const saving = ref(false)
const actionLoading = ref('')
const rows = ref<DynamicForm[]>([])
const selected = ref<DynamicForm | null>(null)
const activePanel = ref<'designer' | 'submissions' | 'statistics'>('designer')
const designerRef = ref<DesignerRef>()
const submissionRows = ref<FormSubmission[]>([])
const statistics = ref<FormStatistics | null>(null)

const pagination = reactive({ page: 1, size: 10, total: 0 })
const submissionPagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive<{ keyword: string, status: DynamicFormStatus | '' }>({ keyword: '', status: '' })

const form = reactive<DynamicFormPayload>({
  name: '',
  code: '',
  description: '',
  schemaJson: '',
  optionJson: '',
  allowAnonymous: true,
  status: 'DRAFT',
})

const statusOptions: { label: string, value: DynamicFormStatus | '' }[] = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]

const tableColumns = computed<TableColumn<DynamicForm>[]>(() => [
  { accessorKey: 'name', header: '表单名称', width: 180, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'allowAnonymous', header: '公开填写', width: 100, align: 'center' },
  { accessorKey: 'publishedAt', header: '发布时间', width: 180 },
  { accessorKey: 'updateTime', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 420, align: 'center', fixed: 'right' },
])

const submissionColumns = computed<TableColumn<FormSubmission>[]>(() => [
  { accessorKey: 'id', header: '提交 ID', width: 170, fixed: 'left' },
  { id: 'data', header: '提交内容', width: 520 },
  { accessorKey: 'submitterId', header: '提交用户', width: 140 },
  { accessorKey: 'submitterIp', header: '来源 IP', width: 160 },
  { accessorKey: 'submittedAt', header: '提交时间', width: 180 },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiForm.page({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
      status: search.status || undefined,
    })
    rows.value = res.data.records
    pagination.total = res.data.total
    if (!selected.value && rows.value.length) {
      await openDesigner(rows.value[0])
    }
    else if (selected.value) {
      selected.value = rows.value.find(item => item.id === selected.value?.id) || selected.value
    }
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.status = ''
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

async function openCreate() {
  selected.value = null
  activePanel.value = 'designer'
  Object.assign(form, {
    name: '新的动态表单',
    code: `form-${Date.now().toString().slice(-6)}`,
    description: '',
    schemaJson: JSON.stringify(defaultRules(), null, 2),
    optionJson: JSON.stringify(defaultOptions(), null, 2),
    allowAnonymous: true,
    status: 'DRAFT' as DynamicFormStatus,
  })
  await nextTick()
  applyDesignerState()
}

async function openDesigner(row: DynamicForm) {
  selected.value = row
  activePanel.value = 'designer'
  Object.assign(form, {
    name: row.name,
    code: row.code,
    description: row.description || '',
    schemaJson: row.schemaJson || JSON.stringify(defaultRules(), null, 2),
    optionJson: row.optionJson || JSON.stringify(defaultOptions(), null, 2),
    allowAnonymous: row.allowAnonymous !== false,
    status: row.status || 'DRAFT',
  })
  await nextTick()
  applyDesignerState()
}

function applyDesignerState() {
  const designer = designerRef.value
  if (!designer) {
    return
  }
  designer.setRule(parseJsonArray(form.schemaJson, defaultRules()))
  designer.setOption(parseJsonObject(form.optionJson, defaultOptions()))
}

async function saveDesigner() {
  const designer = designerRef.value
  if (!designer) {
    toast.error('表单设计器还未就绪')
    return
  }
  if (!form.name.trim() || !form.code.trim()) {
    toast.error('请填写表单名称和编码')
    return
  }
  saving.value = true
  try {
    const payload = normalizePayload(designer)
    const res = selected.value
      ? await apiForm.update(selected.value.id, payload)
      : await apiForm.create(payload)
    selected.value = res.data
    Object.assign(form, {
      ...payload,
      status: res.data.status,
    })
    toast.success('表单设计已保存')
    await load()
  }
  finally {
    saving.value = false
  }
}

function normalizePayload(designer: DesignerRef): DynamicFormPayload {
  return {
    name: form.name.trim(),
    code: form.code.trim(),
    description: form.description?.trim() || undefined,
    schemaJson: JSON.stringify(designer.getRule() || []),
    optionJson: JSON.stringify(designer.getOption() || {}),
    allowAnonymous: form.allowAnonymous,
    status: form.status,
  }
}

async function publish(row: DynamicForm) {
  actionLoading.value = `publish-${row.id}`
  try {
    await apiForm.publish(row.id)
    toast.success('表单已发布')
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

async function unpublish(row: DynamicForm) {
  actionLoading.value = `unpublish-${row.id}`
  try {
    await apiForm.unpublish(row.id)
    toast.success('表单已取消发布')
    await load()
  }
  finally {
    actionLoading.value = ''
  }
}

function confirmDelete(row: DynamicForm) {
  modal.confirm({
    title: '确认删除',
    content: `确认删除动态表单「${row.name}」吗？提交记录会保留在数据库中，但该表单入口将不可用。`,
    onConfirm: async () => {
      await apiForm.delete(row.id)
      if (selected.value?.id === row.id) {
        selected.value = null
      }
      toast.success('表单已删除')
      await load()
    },
  })
}

async function openSubmissions(row: DynamicForm) {
  selected.value = row
  activePanel.value = 'submissions'
  submissionPagination.page = 1
  await loadSubmissions()
}

async function loadSubmissions() {
  if (!selected.value) {
    return
  }
  loading.value = true
  try {
    const res = await apiForm.submissions(selected.value.id, {
      page: submissionPagination.page,
      size: submissionPagination.size,
    })
    submissionRows.value = res.data.records
    submissionPagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

async function openStatistics(row: DynamicForm) {
  selected.value = row
  activePanel.value = 'statistics'
  statistics.value = null
  const res = await apiForm.statistics(row.id)
  statistics.value = res.data
}

function onSubmissionPageChange(page: number) {
  submissionPagination.page = page
  loadSubmissions()
}

function onSubmissionSizeChange(size: number) {
  submissionPagination.size = size
  submissionPagination.page = 1
  loadSubmissions()
}

async function copyPublicUrl(row: DynamicForm) {
  const url = `${window.location.origin}/forms/${row.code}`
  await navigator.clipboard.writeText(url)
  toast.success('公开链接已复制')
}

function parseJsonArray(value: string | undefined, fallback: unknown[]) {
  try {
    const parsed = value ? JSON.parse(value) : fallback
    return Array.isArray(parsed) ? parsed : fallback
  }
  catch {
    return fallback
  }
}

function parseJsonObject(value: string | undefined, fallback: Record<string, unknown>) {
  try {
    const parsed = value ? JSON.parse(value) : fallback
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : fallback
  }
  catch {
    return fallback
  }
}

function defaultRules() {
  return [
    {
      type: 'input',
      field: 'name',
      title: '姓名',
      props: { placeholder: '请输入姓名' },
      validate: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    },
    {
      type: 'input',
      field: 'phone',
      title: '联系电话',
      props: { placeholder: '请输入联系电话' },
    },
    {
      type: 'textarea',
      field: 'message',
      title: '留言内容',
      props: { placeholder: '请输入内容', maxLength: 500, showWordLimit: true },
    },
  ]
}

function defaultOptions() {
  return {
    form: { layout: 'vertical' },
    submitBtn: false,
    resetBtn: false,
  }
}

function statusText(status: DynamicFormStatus) {
  const map: Record<DynamicFormStatus, string> = {
    DRAFT: '草稿',
    PUBLISHED: '已发布',
    DISABLED: '已停用',
  }
  return map[status] || status
}

function statusVariant(status: DynamicFormStatus) {
  if (status === 'PUBLISHED') {
    return 'default'
  }
  return status === 'DISABLED' ? 'destructive' : 'secondary'
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function dataText(data?: Record<string, unknown>) {
  if (!data || !Object.keys(data).length) {
    return '-'
  }
  return Object.entries(data)
    .map(([key, value]) => `${key}: ${formatValue(value)}`)
    .join('；')
}

function formatValue(value: unknown) {
  if (Array.isArray(value)) {
    return value.join(', ')
  }
  if (value && typeof value === 'object') {
    return JSON.stringify(value)
  }
  return value == null || value === '' ? '-' : String(value)
}
</script>

<template>
  <div>
    <FaPageHeader title="动态表单" class="mb-0">
      <FaButton v-auth="'platform:form:view'" variant="outline" :loading="loading" @click="load">
        <FaIcon name="i-ri:refresh-line" />
        刷新
      </FaButton>
      <FaButton v-auth="'platform:form:edit'" @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新建表单
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaSearchBar>
        <div class="form-search">
          <FaInput v-model="search.keyword" clearable placeholder="表单名称 / 编码 / 描述" @keydown.enter="load" @clear="load" />
          <FaSelect v-model="search.status" :options="statusOptions" />
          <div class="search-actions">
            <FaButton variant="outline" @click="resetSearch">重置</FaButton>
            <FaButton :loading="loading" @click="load">
              <FaIcon name="i-ri:search-line" />
              查询
            </FaButton>
          </div>
        </div>
      </FaSearchBar>

      <FaTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1320px]"
        border
        stripe
        column-visibility
        :columns="tableColumns"
        :data="rows"
      >
        <template #cell-status="{ row }">
          <FaTag :variant="statusVariant(row.original.status)">
            {{ statusText(row.original.status) }}
          </FaTag>
        </template>
        <template #cell-allowAnonymous="{ row }">
          {{ row.original.allowAnonymous ? '是' : '登录后填写' }}
        </template>
        <template #cell-publishedAt="{ row }">
          {{ dateText(row.original.publishedAt) }}
        </template>
        <template #cell-updateTime="{ row }">
          {{ dateText(row.original.updateTime) }}
        </template>
        <template #cell-operation="{ row }">
          <div class="table-actions">
            <FaButton size="sm" variant="outline" @click="openDesigner(row.original)">设计</FaButton>
            <FaButton v-auth="'platform:form:publish'" size="sm" variant="ghost" :loading="actionLoading === `publish-${row.original.id}`" :disabled="row.original.status === 'PUBLISHED'" @click="publish(row.original)">
              发布
            </FaButton>
            <FaButton v-auth="'platform:form:publish'" size="sm" variant="ghost" :loading="actionLoading === `unpublish-${row.original.id}`" :disabled="row.original.status !== 'PUBLISHED'" @click="unpublish(row.original)">
              取消发布
            </FaButton>
            <FaButton size="sm" variant="ghost" :disabled="row.original.status !== 'PUBLISHED'" @click="copyPublicUrl(row.original)">公开链接</FaButton>
            <FaButton v-auth="'platform:form:submission:view'" size="sm" variant="ghost" @click="openSubmissions(row.original)">提交</FaButton>
            <FaButton v-auth="'platform:form:statistics:view'" size="sm" variant="ghost" @click="openStatistics(row.original)">统计</FaButton>
            <FaButton v-auth="'platform:form:delete'" size="sm" variant="destructive" @click="confirmDelete(row.original)">删除</FaButton>
          </div>
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

      <section class="form-workbench">
        <div class="panel-tabs">
          <button type="button" :class="{ active: activePanel === 'designer' }" @click="activePanel = 'designer'">
            <FaIcon name="i-ri:drag-drop-line" />
            表单设计
          </button>
          <button type="button" :disabled="!selected" :class="{ active: activePanel === 'submissions' }" @click="selected && openSubmissions(selected)">
            <FaIcon name="i-ri:inbox-archive-line" />
            提交记录
          </button>
          <button type="button" :disabled="!selected" :class="{ active: activePanel === 'statistics' }" @click="selected && openStatistics(selected)">
            <FaIcon name="i-ri:bar-chart-box-line" />
            结果统计
          </button>
        </div>

        <section v-if="activePanel === 'designer'" class="designer-panel">
          <a-form :model="form" layout="vertical" class="meta-grid">
            <a-form-item label="表单名称" required>
              <FaInput v-model="form.name" />
            </a-form-item>
            <a-form-item label="表单编码" required>
              <FaInput v-model="form.code" placeholder="form-contact" :disabled="!!selected" />
            </a-form-item>
            <a-form-item label="状态">
              <FaSelect v-model="form.status" :options="statusOptions.filter(item => item.value)" />
            </a-form-item>
            <a-form-item label="公开填写">
              <FaSwitch v-model="form.allowAnonymous" />
            </a-form-item>
            <a-form-item label="描述" class="meta-full">
              <FaTextarea v-model="form.description" rows="3" />
            </a-form-item>
          </a-form>
          <div class="designer-toolbar">
            <span>{{ selected ? `正在编辑：${selected.name}` : '正在创建新表单' }}</span>
            <FaButton v-auth="'platform:form:edit'" :loading="saving" @click="saveDesigner">
              <FaIcon name="i-ri:save-3-line" />
              保存设计
            </FaButton>
          </div>
          <div class="designer-shell">
            <fc-designer ref="designerRef" height="680px" />
          </div>
        </section>

        <section v-else-if="activePanel === 'submissions'" class="submissions-panel">
          <div class="panel-header">
            <strong>{{ selected?.name || '-' }} 的提交记录</strong>
            <FaButton variant="outline" :loading="loading" @click="loadSubmissions">
              <FaIcon name="i-ri:refresh-line" />
              刷新
            </FaButton>
          </div>
          <FaTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1100px]"
            border
            stripe
            column-visibility
            :columns="submissionColumns"
            :data="submissionRows"
          >
            <template #cell-data="{ row }">
              <span class="line-clamp-2">{{ dataText(row.original.data) }}</span>
            </template>
            <template #cell-submittedAt="{ row }">
              {{ dateText(row.original.submittedAt) }}
            </template>
          </FaTable>
          <FaPagination
            v-model:page="submissionPagination.page"
            v-model:size="submissionPagination.size"
            :total="submissionPagination.total"
            class="mt-3"
            @page-change="onSubmissionPageChange"
            @size-change="onSubmissionSizeChange"
          />
        </section>

        <section v-else class="statistics-panel">
          <div class="stat-grid">
            <div class="stat-card">
              <span>总提交</span>
              <strong>{{ statistics?.total ?? 0 }}</strong>
            </div>
            <div class="stat-card">
              <span>今日提交</span>
              <strong>{{ statistics?.today ?? 0 }}</strong>
            </div>
            <div class="stat-card">
              <span>近 7 天</span>
              <strong>{{ statistics?.last7Days ?? 0 }}</strong>
            </div>
          </div>
          <div class="field-stats">
            <article v-for="field in statistics?.fields || []" :key="field.field" class="field-stat">
              <div class="field-stat__head">
                <strong>{{ field.field }}</strong>
                <span>填写 {{ field.filled }} / 空值 {{ field.empty }}</span>
              </div>
              <div class="value-list">
                <span v-for="item in field.topValues" :key="item.value">
                  {{ item.value }} · {{ item.count }}
                </span>
                <em v-if="!field.topValues.length">暂无可统计值</em>
              </div>
            </article>
          </div>
        </section>
      </section>
    </FaPageMain>
  </div>
</template>

<style scoped>
.form-search {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) minmax(160px, 220px) auto;
  gap: 12px;
  align-items: center;
}

.search-actions,
.table-actions,
.designer-toolbar,
.panel-header {
  display: flex;
  gap: 8px;
  align-items: center;
}

.search-actions,
.designer-toolbar,
.panel-header {
  justify-content: flex-end;
}

.table-actions {
  justify-content: center;
  flex-wrap: wrap;
}

.form-workbench {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.panel-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.panel-tabs button {
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

.panel-tabs button.active,
.panel-tabs button:not(:disabled):hover {
  border-color: rgb(var(--primary-6));
  color: rgb(var(--primary-6));
}

.panel-tabs button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.designer-panel,
.submissions-panel,
.statistics-panel {
  display: grid;
  gap: 14px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.meta-full {
  grid-column: 1 / -1;
}

.designer-toolbar {
  justify-content: space-between;
  color: var(--color-text-2);
}

.designer-shell {
  min-height: 680px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.panel-header {
  justify-content: space-between;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.stat-card,
.field-stat {
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.stat-card {
  display: grid;
  gap: 8px;
  padding: 16px;
}

.stat-card span {
  color: var(--color-text-3);
}

.stat-card strong {
  font-size: 28px;
}

.field-stats {
  display: grid;
  gap: 10px;
}

.field-stat {
  display: grid;
  gap: 10px;
  padding: 14px;
}

.field-stat__head {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.field-stat__head span,
.value-list em {
  color: var(--color-text-3);
}

.value-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.value-list span {
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-1);
  font-size: 12px;
}

@media (max-width: 900px) {
  .form-search,
  .meta-grid,
  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
