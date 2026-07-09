<script setup lang="ts">
import type { TableColumn } from '@fantastic-admin/components'
import type { ApiLog } from '@/api/modules/system-monitor'
import apiExcel from '@/api/modules/system-excel'
import apiMonitor from '@/api/modules/system-monitor'
import { saveExcelResponse } from '@/utils/excel'

const loading = ref(false)
const clearing = ref(false)
const rows = ref<ApiLog[]>([])
const modal = useFaModal()
const toast = useFaToast()
const pagination = reactive({ page: 1, size: 10, total: 0 })
const search = reactive<{ keyword: string; success: string }>({
  keyword: '',
  success: '',
})

const successOptions = [
  { label: '\u6210\u529f', value: 'true' },
  { label: '\u5931\u8d25', value: 'false' },
]
const successText = '\u6210\u529f'
const failText = '\u5931\u8d25'

const tableColumns = computed<TableColumn<ApiLog>[]>(() => [
  { id: 'success', header: '\u72b6\u6001', width: 90, align: 'center' },
  { accessorKey: 'method', header: '\u8bf7\u6c42\u65b9\u5f0f', width: 110 },
  { id: 'path', header: '\u63a5\u53e3\u5730\u5740', width: 320 },
  { accessorKey: 'status', header: '\u54cd\u5e94\u7801', width: 90, align: 'center' },
  { accessorKey: 'costMs', header: '\u8017\u65f6', width: 100, align: 'right' },
  { id: 'user', header: '\u64cd\u4f5c\u7528\u6237', width: 180 },
  { accessorKey: 'ip', header: 'IP', width: 140 },
  { id: 'createTime', header: '\u8bbf\u95ee\u65f6\u95f4', width: 180 },
  { id: 'errorMessage', header: '\u5f02\u5e38', width: 260 },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiMonitor.apiLogs({
      page: pagination.page,
      size: pagination.size,
      keyword: search.keyword || undefined,
      success: successValue(),
    })
    rows.value = res.data.records
    pagination.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

function resetSearch() {
  search.keyword = ''
  search.success = ''
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

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

function successValue() {
  if (search.success === 'true') {
    return true
  }
  if (search.success === 'false') {
    return false
  }
  return undefined
}

function userText(row: ApiLog) {
  if (!row.loginId) {
    return '-'
  }
  const name = row.nickname || row.username
  return name ? `${name} (${row.loginId})` : String(row.loginId)
}

async function exportApiLogs() {
  const res = await apiExcel.exportApiLogs({
    keyword: search.keyword || undefined,
    success: successValue(),
  })
  saveExcelResponse(res, '接口日志.xlsx')
}

function confirmClearApiLogs() {
  modal.confirm({
    title: '确认清空接口日志',
    content: '确认删除所有接口日志吗？该操作不可恢复。',
    onConfirm: clearApiLogs,
  })
}

async function clearApiLogs() {
  clearing.value = true
  try {
    const res = await apiMonitor.clearApiLogs()
    toast.success(`已删除 ${res.data || 0} 条接口日志`)
    pagination.page = 1
    await load()
  }
  finally {
    clearing.value = false
  }
}
</script>

<template>
  <div>
    <FaPageHeader title="&#25509;&#21475;&#26085;&#24535;" class="mb-0">
      <FaButton v-auth="'system:monitor:api-log:export'" variant="outline" @click="exportApiLogs">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
      <FaButton v-auth="'system:monitor:api-log:delete'" variant="destructive" :loading="clearing" @click="confirmClearApiLogs">
        <FaIcon name="i-ri:delete-bin-6-line" />
        清空日志
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <FaTable
        v-loading="loading"
        row-key="id"
        table-root-class="rounded-lg overflow-hidden"
        table-class="min-w-[1300px]"
        border
        stripe
        column-visibility
        :columns="tableColumns"
        :data="rows"
      >
        <template #toolbar>
          <FaSearchBar class="w-full">
            <div class="grid grid-cols-1 gap-3 md:grid-cols-[minmax(260px,1fr)_220px_auto] md:items-center">
              <FaInput v-model="search.keyword" clearable placeholder="&#25509;&#21475;&#22320;&#22336; / IP / &#35831;&#27714;&#26041;&#24335;" @keydown.enter="load" @clear="load" />
              <FaSelect v-model="search.success" :options="successOptions" placeholder="&#29366;&#24577;" />
              <div class="flex gap-2 md:justify-end">
                <FaButton variant="outline" @click="resetSearch">
                  &#37325;&#32622;
                </FaButton>
                <FaButton :loading="loading" @click="load">
                  <FaIcon name="i-ri:search-line" />
                  &#31579;&#36873;
                </FaButton>
              </div>
            </div>
          </FaSearchBar>
        </template>
        <template #cell-success="{ row }">
          <FaTag :variant="row.original.success ? 'default' : 'destructive'">
            {{ row.original.success ? successText : failText }}
          </FaTag>
        </template>
        <template #cell-path="{ row }">
          <div class="path-cell">
            <span>{{ row.original.path }}</span>
            <small v-if="row.original.query">{{ row.original.query }}</small>
          </div>
        </template>
        <template #cell-createTime="{ row }">
          {{ dateText(row.original.createTime) }}
        </template>
        <template #cell-user="{ row }">
          {{ userText(row.original) }}
        </template>
        <template #cell-errorMessage="{ row }">
          <span class="line-clamp-1">{{ row.original.errorMessage || '-' }}</span>
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
  </div>
</template>

<style scoped>
.path-cell {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.path-cell small {
  overflow: hidden;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
