<script setup lang="ts">
import type { SearchOptionsSource, TableColumn, YdTablePageAction } from '@yudream/components'
import type { ApiLog } from '@/api/modules/system-monitor'
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

/** 调用结果词表：接口 success 是布尔，控件值走字符串，提交前再还原成布尔 */
const successOptions = [
  { label: successText, value: 'true' },
  { label: failText, value: 'false' },
]

/** 搜索选项数据源：标识与 ApiLogPageQuery#success 的 optionsSource 对应 */
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
} = useTablePage<ApiLog>({
  queryClass: 'online.yudream.base.application.system.monitor.query.ApiLogPageQuery',
  loadApi: async (query) => {
    const res = await apiMonitor.apiLogs({
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
  ...(auth('system:monitor:api-log:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportApiLogs }]
    : []),
  ...(auth('system:monitor:api-log:delete')
    ? [{ label: '清空日志', icon: 'morph:trash-2', variant: 'destructive' as const, danger: true, disabled: clearing.value, onClick: confirmClearApiLogs }]
    : []),
])

/**
 * 接口地址（人可读标识）钉在左侧，便于横向比对同一路径的调用；
 * 响应码列显式命名为 responseStatus，避免与增强表格内置的状态列 id 撞名。
 */
const tableColumns = computed<TableColumn<ApiLog>[]>(() => [
  { id: 'path', header: '接口地址', width: 320, fixed: 'left' },
  { accessorKey: 'method', header: '请求方式', width: 110 },
  { id: 'success', header: '状态', width: 90, align: 'center' },
  { id: 'responseStatus', accessorKey: 'status', header: '响应码', width: 90, align: 'center' },
  { accessorKey: 'costMs', header: '耗时', width: 100, align: 'right' },
  { id: 'user', header: '操作用户', width: 180 },
  { accessorKey: 'ip', header: 'IP', width: 140 },
  { id: 'createTime', header: '访问时间', width: 180 },
  { id: 'errorMessage', header: '异常', width: 260 },
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

/** 长文本（接口地址、异常摘要）中间截断，完整值靠 title 悬浮查看 */
function truncateMiddle(value?: string | null, head = 8, tail = 4) {
  if (!value) {
    return ''
  }
  if (value.length <= head + tail + 1) {
    return value
  }
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

/** 操作用户：昵称/用户名 + 登录 ID，取不到登录人时由单元格弱化展示 */
function userText(row: ApiLog) {
  if (!row.loginId) {
    return ''
  }
  const name = row.nickname || row.username
  return name ? `${name} (${row.loginId})` : String(row.loginId)
}

async function exportApiLogs() {
  const res = await apiExcel.exportApiLogs({
    keyword: searchQuery.keyword || undefined,
    success: resolveSuccessFilter(searchQuery.success),
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
    <YdTablePageLayout
      title="接口日志"
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
            table-class="min-w-[1300px]"

            stripe column-visibility border
            :columns="tableColumns"
            :data="rows"
          >
            <template #cell-path="{ row }">
              <div class="path-cell">
                <span :title="row.original.path">{{ truncateMiddle(row.original.path, 32, 14) }}</span>
                <small v-if="row.original.query" :title="row.original.query">{{ truncateMiddle(row.original.query, 32, 10) }}</small>
              </div>
            </template>
            <!-- 状态胶囊：成败一眼可辨（success 是布尔字段，语义色在这里直接给） -->
            <template #cell-success="{ row }">
              <span class="yd-status-tone" :data-tone="row.original.success ? 'success' : 'danger'">
                {{ row.original.success ? successText : failText }}
              </span>
            </template>
            <template #cell-responseStatus="{ row }">
              <span v-if="row.original.status != null">{{ row.original.status }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-costMs="{ row }">
              <span v-if="row.original.costMs != null">{{ row.original.costMs }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <!-- 操作用户：头像 + 就地预览/编辑，未登录调用没有 loginId 时退化为纯文本 -->
            <template #cell-user="{ row }">
              <RelatedEntity
                v-if="row.original.loginId"
                entity-type="user"
                :entity-id="row.original.loginId"
                :label="row.original.nickname || row.original.username"
                :secondary="row.original.nickname ? row.original.username : undefined"
                :avatar="row.original.avatarUrl"
                @changed="load"
              />
              <span v-else-if="userText(row.original)">{{ userText(row.original) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-ip="{ row }">
              <span v-if="row.original.ip">{{ row.original.ip }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-createTime="{ row }">
              <span v-if="row.original.createTime">{{ dateText(row.original.createTime) }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-errorMessage="{ row }">
              <span v-if="row.original.errorMessage" :title="row.original.errorMessage">{{ truncateMiddle(row.original.errorMessage, 24, 8) }}</span>
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
