<script setup lang="ts">
import type { SearchOptionsSource, TableColumn, YdTablePageAction } from '@yudream/components'
import type { LoginLog } from '@/api/modules/system-monitor'
import { useTablePage } from '@yudream/components'
import apiExcel from '@/api/modules/system-excel'
import apiMonitor from '@/api/modules/system-monitor'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import { saveExcelResponse } from '@/utils/excel'

const { auth } = useAppAuth()
const modal = useYdModal()
const toast = useYdToast()

const clearing = ref(false)
const successText = '成功'
const failText = '失败'

/** 登录结果词表：接口 success 是布尔，控件值走字符串，提交前再还原成布尔 */
const successOptions = [
  { label: successText, value: 'true' },
  { label: failText, value: 'false' },
]

/** 搜索选项数据源：标识与 LoginLogPageQuery#success 的 optionsSource 对应 */
const optionsSources: SearchOptionsSource[] = [
  { id: 'logSuccessOptions', loader: () => successOptions },
]

const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  load,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<LoginLog>({
  queryClass: 'online.yudream.base.application.system.monitor.query.LoginLogPageQuery',
  loadApi: async (query) => {
    const res = await apiMonitor.loginLogs({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      success: resolveSuccessFilter(query.success),
    })
    return {
      records: res.data.records,
      total: res.data.total,
      page: res.data.page,
      size: res.data.size,
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 页头操作：导出属不常用动作收进「更多」；清空日志是危险操作单独标红 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('system:monitor:login-log:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportLoginLogs }]
    : []),
  ...(auth('system:monitor:login-log:delete')
    ? [{ label: '清空日志', icon: 'morph:trash-2', variant: 'destructive' as const, danger: true, disabled: clearing.value, onClick: confirmClearLoginLogs }]
    : []),
])

/** 用户名（人可读标识）钉在左侧，状态列紧随后便于纵向扫读成败 */
const tableColumns = computed<TableColumn<LoginLog>[]>(() => [
  { accessorKey: 'username', header: '用户名', width: 160, fixed: 'left' },
  { id: 'success', header: '状态', width: 90, align: 'center' },
  { accessorKey: 'userId', header: '用户ID', width: 160 },
  { accessorKey: 'ip', header: 'IP', width: 150 },
  { id: 'message', header: '结果', width: 260 },
  { id: 'createTime', header: '登录时间', width: 180 },
  { id: 'userAgent', header: '客户端', width: 360 },
])

/** 搜索表单的成功/失败是字符串值，仅这两种取值转成接口需要的布尔 */
function resolveSuccessFilter(value?: string) {
  if (value === 'true') {
    return true
  }
  if (value === 'false') {
    return false
  }
  return undefined
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '-'
}

/** 长文本（结果摘要、客户端 UA）中间截断，完整值靠 title 悬浮查看 */
function truncateMiddle(value?: string | null, head = 8, tail = 4) {
  if (!value) {
    return ''
  }
  if (value.length <= head + tail + 1) {
    return value
  }
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

async function exportLoginLogs() {
  const res = await apiExcel.exportLoginLogs({
    keyword: searchQuery.keyword || undefined,
    success: resolveSuccessFilter(searchQuery.success),
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
    <YdTablePageLayout
      title="登录日志"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          :options-sources="optionsSources"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="id"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1280px]"

            stripe column-visibility border
            :columns="tableColumns"
            :data="rows"
          >
            <template #cell-username="{ row }">
              <span v-if="row.original.username">{{ row.original.username }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <!-- 状态胶囊：成败一眼可辨（success 是布尔字段，语义色在这里直接给） -->
            <template #cell-success="{ row }">
              <span class="yd-status-tone" :data-tone="row.original.success ? 'success' : 'danger'">
                {{ row.original.success ? successText : failText }}
              </span>
            </template>
            <template #cell-userId="{ row }">
              <span v-if="row.original.userId">{{ row.original.userId }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-ip="{ row }">
              <span v-if="row.original.ip">{{ row.original.ip }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-message="{ row }">
              <span v-if="row.original.message" :title="row.original.message">{{ truncateMiddle(row.original.message, 16, 6) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-createTime="{ row }">
              <span v-if="row.original.createTime">{{ dateText(row.original.createTime) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-userAgent="{ row }">
              <span v-if="row.original.userAgent" :title="row.original.userAgent">{{ truncateMiddle(row.original.userAgent, 32, 12) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
          </YdResponsiveEnhancedTable>
        </YdLoadingOverlay>
      </template>

      <template #footer>
        <YdPagination
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :total="pagination.total"
          @page-change="onPageChange"
          @size-change="onSizeChange"
        />
      </template>
    </YdTablePageLayout>
  </div>
</template>
