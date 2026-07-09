<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { DynamicForm, FormSubmission } from '@/api/modules/platform-form'
import apiForm from '@/api/modules/platform-form'
import { saveExcelResponse } from '@/utils/excel'
import DynamicFormRenderer from './DynamicFormRenderer.vue'

const props = defineProps<{
  form?: DynamicForm | null
  formId?: string | null
  formName?: string
  embedded?: boolean
}>()

const loading = ref(false)
const exporting = ref(false)
const detailVisible = ref(false)
const detailRow = ref<FormSubmission | null>(null)
const rows = ref<FormSubmission[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })

const columns = computed<TableColumn<FormSubmission>[]>(() => {
  const base: TableColumn<FormSubmission>[] = [
    { accessorKey: 'id', header: '提交 ID', width: 170, fixed: 'left' },
    { id: 'data', header: '提交内容', width: 560 },
    { accessorKey: 'submitterId', header: '提交用户', width: 140 },
    { accessorKey: 'submitterIp', header: '来源 IP', width: 160 },
    { accessorKey: 'submittedAt', header: '提交时间', width: 180 },
  ]
  if (props.form) {
    base.push({ id: 'operation', header: '操作', width: 100, align: 'center', fixed: 'right' })
  }
  return base
})

watch(() => props.formId, () => {
  pagination.page = 1
  load()
}, { immediate: true })

async function load() {
  if (!props.formId) {
    rows.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  try {
    const res = await apiForm.submissions(props.formId, {
      page: pagination.page,
      size: pagination.size,
    })
    rows.value = res.data.records
    pagination.total = Number(res.data.total || 0)
  }
  finally {
    loading.value = false
  }
}

async function exportExcel() {
  if (!props.formId) {
    return
  }
  exporting.value = true
  try {
    const res = await apiForm.exportSubmissions(props.formId)
    saveExcelResponse(res, `${props.formName || '表单'}-提交结果.xlsx`)
  }
  finally {
    exporting.value = false
  }
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

function openDetail(row: FormSubmission) {
  detailRow.value = row
  detailVisible.value = true
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function dataPairs(data?: Record<string, unknown>) {
  return Object.entries(data || {}).map(([key, value]) => ({ key, value: formatValue(value) }))
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
  <section class="submission-panel" :class="{ embedded }">
    <div class="panel-header">
      <div class="panel-title">
        <strong>{{ formName || '提交结果' }}</strong>
        <span>{{ pagination.total }} 份提交</span>
      </div>
      <div class="panel-actions">
        <FaButton v-auth="'platform:form:submission:export'" variant="outline" :disabled="!formId" :loading="exporting" @click="exportExcel">
          <FaIcon name="i-ri:file-excel-2-line" />
          导出
        </FaButton>
      </div>
    </div>

    <FaTable
      v-loading="loading"
      row-key="id"
      table-root-class="rounded-lg overflow-hidden"
      table-class="min-w-[1200px]"
      border
      stripe
      column-visibility
      :columns="columns"
      :data="rows"
    >
      <template #cell-data="{ row }">
        <div v-if="dataPairs(row.original.data).length" class="data-pairs">
          <span v-for="item in dataPairs(row.original.data)" :key="item.key">
            <b>{{ item.key }}</b>
            {{ item.value }}
          </span>
        </div>
        <span v-else>-</span>
      </template>
      <template #cell-submittedAt="{ row }">
        {{ dateText(row.original.submittedAt) }}
      </template>
      <template #cell-operation="{ row }">
        <FaButton v-if="form" size="sm" variant="outline" @click="openDetail(row.original)">查看</FaButton>
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

    <FaModal v-model="detailVisible" :title="form ? `${form.name} / 提交详情` : '提交详情'" class="sm:max-w-4xl">
      <section v-if="form && detailRow" class="submission-detail">
        <DynamicFormRenderer :form="form" :model-value="detailRow.data" readonly />
      </section>
    </FaModal>
  </section>
</template>

<style scoped>
.submission-panel {
  display: grid;
  gap: 14px;
}

.submission-panel.embedded {
  padding-top: 4px;
}

.panel-header {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.panel-title {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.panel-title strong,
.panel-title span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.panel-title span {
  color: var(--color-text-3);
  font-size: 13px;
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.data-pairs {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: 100%;
}

.data-pairs span {
  display: inline-flex;
  max-width: 100%;
  gap: 5px;
  padding: 4px 8px;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: var(--color-text-2);
  font-size: 12px;
}

.data-pairs b {
  color: var(--color-text-1);
  font-weight: 600;
}

.submission-detail {
  max-height: min(70vh, 720px);
  overflow-y: auto;
  padding-right: 4px;
}

@media (max-width: 720px) {
  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .panel-actions {
    justify-content: flex-start;
  }
}
</style>
