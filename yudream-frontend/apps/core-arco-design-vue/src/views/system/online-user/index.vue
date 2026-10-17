<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdTablePageAction } from '@yudream/components'
import type { OnlineUser } from '@/api/modules/system-monitor'
import { useTablePage } from '@yudream/components'
import apiExcel from '@/api/modules/system-excel'
import apiMonitor from '@/api/modules/system-monitor'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import { saveExcelResponse } from '@/utils/excel'

const modal = useYdModal()
const toast = useYdToast()

const { auth } = useAppAuth()

/** 会话操作统一收进下拉 */
const sessionActions: ActionMenuItem<OnlineUser>[] = [
  { label: '强制下线', icon: 'morph:log-out', danger: true, visible: () => auth('system:monitor:online:kickout'), onClick: confirmKickout },
]

const tableColumns = computed<TableColumn<OnlineUser>[]>(() => [
  // 用户列自带头像与就地预览/编辑（替代只读的 userColumn，能力只增不减）
  { id: 'sessionUser', header: '用户', width: 220, fixed: 'left' },
  { accessorKey: 'username', header: '用户名', width: 140 },
  { accessorKey: 'nickname', header: '昵称', width: 140 },
  { accessorKey: 'email', header: '邮箱', width: 220 },
  { accessorKey: 'device', header: '设备', width: 120 },
  { id: 'timeout', header: '会话剩余时间', width: 150 },
  { id: 'token', header: '会话标识', width: 260 },
  { id: 'operation', header: '操作', width: 120, align: 'center', fixed: 'right' },
])

/**
 * 在线会话由后端分页（服务端在会话枚举结果上按最近活跃优先切片）；
 * 搜索条件来自 OnlineUserQuery 的 @Searchable 注解，页面无下拉字段故不登记选项数据源。
 */
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
} = useTablePage<OnlineUser>({
  queryClass: 'online.yudream.base.application.system.monitor.query.OnlineUserQuery',
  loadApi: async (query) => {
    const res = await apiMonitor.onlineUsers({
      keyword: query.keyword || undefined,
      page: query.page,
      size: query.size,
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

/** 页头操作：导出属不常用动作，超出阈值时收进「更多」 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('system:monitor:online:export')
    ? [{ label: '导出', icon: 'morph:file-spreadsheet', variant: 'outline' as const, autoFold: true, onClick: exportOnlineUsers }]
    : []),
])

/** 在线人数取全量总数，而不是当前分页条数 */
const onlineCountText = computed(() => `当前在线 ${pagination.total} 人`)

function ttlText(value?: number) {
  if (value == null) {
    return '-'
  }
  if (value < 0) {
    return '永久'
  }
  if (value < 60) {
    return `${value}s`
  }
  if (value < 3600) {
    return `${Math.floor(value / 60)}m`
  }
  return `${Math.floor(value / 3600)}h`
}

/** 剩余时间语义色：永久会话中性，不足 5 分钟提示即将失效，其余按有效态 */
function ttlTone(value?: number): 'success' | 'warning' | 'neutral' {
  if (value == null || value < 0) {
    return 'neutral'
  }
  return value < 300 ? 'warning' : 'success'
}

/** 长会话标识中间截断，完整值靠 title 悬浮查看 */
function truncateMiddle(value?: string | null, head = 8, tail = 4) {
  if (!value) {
    return ''
  }
  if (value.length <= head + tail + 1) {
    return value
  }
  return `${value.slice(0, head)}…${value.slice(-tail)}`
}

function confirmKickout(row: OnlineUser) {
  modal.confirm({
    title: '确认下线',
    content: `确认强制下线 ${row.username || row.userId || row.token} ？`,
    onConfirm: async () => {
      await apiMonitor.kickoutOnlineUser(row.token)
      toast.success('已下线')
      await load()
    },
  })
}

async function exportOnlineUsers() {
  const res = await apiExcel.exportOnlineUsers({ keyword: searchQuery.keyword || undefined })
  saveExcelResponse(res, '在线用户.xlsx')
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="在线用户"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 在线人数是页面情报而非操作，按纯文本呈现，按钮一律走 actions -->
      <template #page-title>
        <span class="text-sm text-muted-foreground">{{ onlineCountText }}</span>
      </template>

      <template #search="{ fold }">
        <YdDynamicSearchForm
          v-model="searchQuery"
          :config="searchConfig"
          :fold="fold"
          @search="search"
          @reset="resetSearch"
        />
      </template>

      <template #table>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveEnhancedTable
            row-key="token"
            table-root-class="rounded-lg overflow-hidden"
            table-class="min-w-[1080px]"
            column-visibility
            :actions="sessionActions"
            :columns="tableColumns"
            :data="rows"
          >
            <template #empty>
              <div class="empty-state">
                暂无在线会话，请确认已重启后端并重新登录
              </div>
            </template>
            <!-- 会话用户：头像 + 就地预览/编辑（同一用户多会话时复用详情缓存） -->
            <template #cell-sessionUser="{ row }">
              <RelatedEntity
                v-if="row.original.userId"
                entity-type="user"
                :entity-id="row.original.userId"
                :label="row.original.username || row.original.nickname"
                :secondary="row.original.email"
                :avatar="row.original.avatar"
                @changed="load"
              />
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-username="{ row }">
              <span v-if="row.original.username">{{ row.original.username }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-nickname="{ row }">
              <span v-if="row.original.nickname">{{ row.original.nickname }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-email="{ row }">
              <span v-if="row.original.email">{{ row.original.email }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-device="{ row }">
              <span v-if="row.original.device">{{ row.original.device }}</span>
              <span v-else class="text-muted-foreground/40">-</span>
            </template>
            <template #cell-timeout="{ row }">
              <span v-if="row.original.timeout == null" class="text-muted-foreground/40">-</span>
              <span v-else class="yd-status-tone" :data-tone="ttlTone(row.original.timeout)">
                {{ ttlText(row.original.timeout) }}
              </span>
            </template>
            <template #cell-token="{ row }">
              <code :title="row.original.token">{{ truncateMiddle(row.original.token) }}</code>
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
code {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}

.empty-state {
  padding: 32px;
  color: var(--color-text-3);
  text-align: center;
}
</style>
