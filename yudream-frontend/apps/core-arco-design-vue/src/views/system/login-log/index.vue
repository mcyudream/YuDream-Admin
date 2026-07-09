<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { LoginLog } from '@/api/modules/system-monitor'
import apiExcel from '@/api/modules/system-excel'
import apiMonitor from '@/api/modules/system-monitor'
import { saveExcelResponse } from '@/utils/excel'

const loading = ref(false)
const clearing = ref(false)
const rows = ref<LoginLog[]>([])
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

const tableColumns = computed<TableColumn<LoginLog>[]>(() => [
  { id: 'success', header: '\u72b6\u6001', width: 90, align: 'center' },
  { accessorKey: 'username', header: '\u7528\u6237\u540d', width: 160 },
  { accessorKey: 'userId', header: '\u7528\u6237ID', width: 120 },
  { accessorKey: 'ip', header: 'IP', width: 150 },
  { id: 'message', header: '\u7ed3\u679c', width: 260 },
  { id: 'createTime', header: '\u767b\u5f55\u65f6\u95f4', width: 180 },
  { id: 'userAgent', header: '\u5ba2\u6237\u7aef', width: 360 },
])

onMounted(load)

async function load() {
  loading.value = true
  try {
    const res = await apiMonitor.loginLogs({
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

async function exportLoginLogs() {
  const res = await apiExcel.exportLoginLogs({
    keyword: search.keyword || undefined,
    success: successValue(),
  })
  saveExcelResponse(res, '登录日志.xlsx')
}

function confirmClearLoginLogs() {
  modal.confirm({
    title: '确认清空登录日志',
    content: '确认删除所有登录日志吗？该操作不可恢复。',
    onConfirm: clearLoginLogs,
  })
}

async function clearLoginLogs() {
  clearing.value = true
  try {
    const res = await apiMonitor.clearLoginLogs()
    toast.success(`已删除 ${res.data || 0} 条登录日志`)
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
    <FaPageHeader title="&#30331;&#24405;&#26085;&#24535;" class="mb-0">
      <FaButton v-auth="'system:monitor:login-log:export'" variant="outline" @click="exportLoginLogs">
        <FaIcon name="i-ri:file-excel-2-line" />
        导出
      </FaButton>
      <FaButton v-auth="'system:monitor:login-log:delete'" variant="destructive" :loading="clearing" @click="confirmClearLoginLogs">
        <FaIcon name="i-ri:delete-bin-6-line" />
        清空日志
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
              <FaInput v-model="search.keyword" clearable placeholder="&#29992;&#25143;&#21517; / IP / &#32467;&#26524;" @keydown.enter="load" @clear="load" />
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
        <template #cell-message="{ row }">
          <span class="line-clamp-1">{{ row.original.message || '-' }}</span>
        </template>
        <template #cell-createTime="{ row }">
          {{ dateText(row.original.createTime) }}
        </template>
        <template #cell-userAgent="{ row }">
          <span class="line-clamp-1">{{ row.original.userAgent || '-' }}</span>
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
