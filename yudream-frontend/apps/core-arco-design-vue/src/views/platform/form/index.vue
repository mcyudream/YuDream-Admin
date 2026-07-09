<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { DynamicForm, DynamicFormStatus, FormStatistics } from '@/api/modules/platform-form'
import apiForm from '@/api/modules/platform-form'
import DynamicFormRenderer from './components/DynamicFormRenderer.vue'
import FormSubmissionPanel from './components/FormSubmissionPanel.vue'

const router = useRouter()
const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const previewLoading = ref(false)
const statisticsLoading = ref(false)
const actionLoading = ref('')
const rows = ref<DynamicForm[]>([])
const selected = ref<DynamicForm | null>(null)
const previewForm = ref<DynamicForm | null>(null)
const previewVisible = ref(false)
const submissionVisible = ref(false)
const statisticsVisible = ref(false)
const statistics = ref<FormStatistics | null>(null)

const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive<{ keyword: string, status: DynamicFormStatus | '' }>({ keyword: '', status: '' })

const statusOptions: { label: string, value: DynamicFormStatus | '' }[] = [
  { label: '全部状态', value: '' },
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]

const tableColumns = computed<TableColumn<DynamicForm>[]>(() => [
  { accessorKey: 'name', header: '表单名称', width: 190, fixed: 'left' },
  { accessorKey: 'code', header: '编码', width: 160 },
  { id: 'status', header: '状态', width: 100, align: 'center' },
  { id: 'allowAnonymous', header: '公开填写', width: 110, align: 'center' },
  { accessorKey: 'publishedAt', header: '发布时间', width: 180 },
  { accessorKey: 'updateTime', header: '更新时间', width: 180 },
  { id: 'operation', header: '操作', width: 470, align: 'center', fixed: 'right' },
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
    pagination.total = Number(res.data.total || 0)
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

function openCreate() {
  router.push('/platform/form/designer')
}

function openDesigner(row: DynamicForm) {
  router.push({ path: '/platform/form/designer', query: { id: String(row.id) } })
}

async function openPreview(row: DynamicForm) {
  selected.value = row
  previewForm.value = row
  previewVisible.value = true
  previewLoading.value = true
  try {
    const res = await apiForm.detail(row.id)
    previewForm.value = res.data
    selected.value = res.data
  }
  finally {
    previewLoading.value = false
  }
}

function openSubmissions(row: DynamicForm) {
  selected.value = row
  submissionVisible.value = true
}

async function openStatistics(row: DynamicForm) {
  selected.value = row
  statistics.value = null
  statisticsVisible.value = true
  statisticsLoading.value = true
  try {
    const res = await apiForm.statistics(row.id)
    statistics.value = res.data
  }
  finally {
    statisticsLoading.value = false
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
    content: `确认删除动态表单「${row.name}」吗？已产生的提交记录会保留在数据库中。`,
    onConfirm: async () => {
      await apiForm.delete(row.id)
      toast.success('表单已删除')
      await load()
    },
  })
}

async function copyPublicUrl(row: DynamicForm) {
  const url = `${window.location.origin}/forms/${row.code}`
  await navigator.clipboard.writeText(url)
  toast.success('公开链接已复制')
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
</script>

<template>
  <div>
    <FaPageHeader title="动态表单" class="mb-0">
      <FaButton v-auth="'platform:form:edit'" @click="openCreate">
        <FaIcon name="i-ri:add-line" />
        新建表单
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1280px]"
        border
        stripe
        column-visibility
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_220px_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="表单名称 / 编码 / 描述" @keydown.enter="load" @clear="load" />
              <FaSelect v-model="search.status" :options="statusOptions" placeholder="状态" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">重置</FaButton>
                <FaButton :loading="loading" @click="load">
                  <FaIcon name="i-ri:search-line" />
                  筛选
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
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
            <FaButton v-auth="'platform:form:view'" size="sm" variant="outline" @click="openPreview(row.original)">查看</FaButton>
            <FaButton v-auth="'platform:form:edit'" size="sm" variant="outline" @click="openDesigner(row.original)">设计</FaButton>
            <FaButton v-auth="'platform:form:submission:view'" size="sm" variant="outline" @click="openSubmissions(row.original)">提交结果</FaButton>
            <FaButton v-auth="'platform:form:statistics:view'" size="sm" variant="ghost" @click="openStatistics(row.original)">统计</FaButton>
            <FaButton size="sm" variant="ghost" :disabled="row.original.status !== 'PUBLISHED'" @click="copyPublicUrl(row.original)">公开链接</FaButton>
            <FaButton v-auth="'platform:form:publish'" size="sm" variant="ghost" :loading="actionLoading === `publish-${row.original.id}`" :disabled="row.original.status === 'PUBLISHED'" @click="publish(row.original)">
              发布
            </FaButton>
            <FaButton v-auth="'platform:form:publish'" size="sm" variant="ghost" :loading="actionLoading === `unpublish-${row.original.id}`" :disabled="row.original.status !== 'PUBLISHED'" @click="unpublish(row.original)">
              下线
            </FaButton>
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
    </FaPageMain>

    <FaModal v-model="previewVisible" :title="previewForm ? `${previewForm.name} / 表单详情` : '表单详情'" class="sm:max-w-4xl">
      <section v-loading="previewLoading" class="preview-panel">
        <DynamicFormRenderer v-if="previewForm" :form="previewForm" readonly />
      </section>
    </FaModal>

    <FaModal v-model="submissionVisible" :title="selected ? `${selected.name} / 提交结果` : '提交结果'" class="sm:max-w-6xl">
      <FormSubmissionPanel :form="selected" :form-id="selected?.id" :form-name="selected?.name" embedded />
    </FaModal>

    <FaModal v-model="statisticsVisible" :title="selected ? `${selected.name} / 结果统计` : '结果统计'" class="sm:max-w-4xl">
      <section v-loading="statisticsLoading" class="statistics-panel">
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
    </FaModal>
  </div>
</template>

<style scoped>
.table-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.table-actions {
  justify-content: center;
  flex-wrap: wrap;
}

.preview-panel {
  max-height: min(70vh, 720px);
  overflow-y: auto;
  padding-right: 4px;
}

.statistics-panel {
  display: grid;
  gap: 14px;
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
  .stat-grid {
    grid-template-columns: 1fr;
  }
}
</style>
