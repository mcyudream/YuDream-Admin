<script setup lang="ts">
import type { ActionMenuItem, PageQuery, PageResult, SearchOptionsSource, StatusColumn, TableColumn, YdTablePageAction } from '@yudream/components'
import type { AiRunSnapshot } from '@/api/modules/platform-ai-runs'
import { useTablePage } from '@yudream/components'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { useAiSystemRunsStore } from '@/store/modules/ai-system-runs'
import { isActiveAiRun } from '@/utils/ai-run-stream'

const runtime = useAiSystemRunsStore()
const account = useAppAccountStore()
const { auth } = useAppAuth()
const canView = computed(() => account.isLogin && auth('platform:ai:system:view'))
const canCancel = computed(() => auth('platform:ai:system:edit'))
const canApprove = computed(() => auth('platform:ai:system:accept'))
const selected = ref('')
const drawerOpen = ref(false)

/** 运行范围词表：接口 activeOnly 是布尔，控件值走字符串，提交前再还原成布尔 */
const activeOptions = [
  { label: '仅运行中任务', value: 'true' },
  { label: '全部任务', value: 'false' },
]

/** 搜索选项数据源：标识与 AiSystemRunsQuery#activeOnly 的 optionsSource 对应 */
const optionsSources: SearchOptionsSource[] = [
  { id: 'aiSystemRunActiveOptions', loader: () => activeOptions },
]

/** 只有显式选「全部任务」才关掉运行中过滤；清空控件回落页面默认（仅运行中） */
function resolveActiveOnly(value?: unknown) {
  return value !== 'false'
}

/** 已生效的运行范围：提交筛选后由 loadApi 从查询条件还原，先于 store 枚举收敛列表 */
const onlyActive = ref(true)

/** 实时投影：状态与游标随 SSE 事件更新，过滤与排序保持迁移前行为 */
const liveRuns = computed(() => Object.values(runtime.runs)
  .filter(run => !onlyActive.value || isActiveAiRun(run.status))
  .sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || ''))))

/**
 * 任务列表是实时流投影：接口按枚举上限返回未分页列表（全部任务最多 100 条 + 运行中），
 * 因此分页在页面内完成——useTablePage 承载搜索配置与页码状态，load 负责按筛选范围重新枚举并重连进度。
 */
const {
  loading,
  rows,
  pagination,
  searchQuery,
  searchConfig,
  refresh,
  search,
  resetSearch,
  onPageChange,
  onSizeChange,
} = useTablePage<AiRunSnapshot>({
  queryClass: 'online.yudream.base.application.platform.ai.query.AiSystemRunsQuery',
  initialQuery: { activeOnly: 'true' },
  optionsSources,
  // 账号与权限就绪后由下方 watch 触发，避免未登录时枚举
  autoLoad: false,
  // 返回的是实时投影的切片：显式标注入参与返回类型，避免与 useTablePage 的返回类型循环推断
  loadApi: async (query: PageQuery): Promise<PageResult<AiRunSnapshot>> => {
    onlyActive.value = resolveActiveOnly(query.activeOnly)
    await runtime.load(String(account.userId || ''), onlyActive.value)
    return { records: pageRuns(), total: liveRuns.value.length, page: pagination.page, size: pagination.size }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 当前页是实时投影的切片：翻页/改页大小只重切片，任务进度仍随事件更新 */
function pageRuns() {
  const start = (pagination.page - 1) * pagination.size
  return liveRuns.value.slice(start, start + pagination.size)
}

/** store 一变就把当前页同步回表格；总量收缩时回到最后一页 */
function syncPage() {
  const lastPage = Math.max(1, Math.ceil(liveRuns.value.length / pagination.size))
  if (pagination.page > lastPage) {
    pagination.page = lastPage
    return
  }
  rows.value = pageRuns()
  pagination.total = liveRuns.value.length
}

watch(liveRuns, syncPage)
watch([() => pagination.page, () => pagination.size], syncPage)

/** 权限与账号就绪后枚举一次（离开此页只解除观察，不会取消任务） */
watch([canView, () => String(account.userId)], () => {
  if (canView.value && account.userId) {
    void refresh()
  }
}, { immediate: true })

/** 页头操作：刷新重新枚举；单条任务操作走表格内置操作列，移动端卡片底部保留同一入口 */
const pageActions: YdTablePageAction[] = [
  { label: '刷新任务', icon: 'morph:refresh-cw', variant: 'outline', onClick: refresh },
]

/** 状态语义色：执行中给信息色，等待输入提示，失败标红，终态中性 */
const statusColumn: StatusColumn<AiRunSnapshot> = {
  statusKey: 'status',
  header: '状态',
  width: 110,
  statusMap: {
    QUEUED: { label: '排队中', variant: 'secondary', tone: 'neutral' },
    RUNNING: { label: '执行中', variant: 'default', tone: 'info' },
    WAITING: { label: '等待输入', variant: 'default', tone: 'warning' },
    COMPLETED: { label: '已完成', variant: 'secondary', tone: 'success' },
    FAILED: { label: '失败', variant: 'destructive', tone: 'danger' },
    CANCELLED: { label: '已停止', variant: 'secondary', tone: 'neutral' },
  },
}

/** 任务模块是行标识，固定左侧；会话标识与审计时间默认隐藏，需要时从列显隐菜单展开 */
const tableColumns: TableColumn<AiRunSnapshot>[] = [
  { accessorKey: 'module', header: '任务模块', width: 180, fixed: 'left' },
  { accessorKey: 'sessionId', header: '工作上下文', minWidth: 220, defaultHidden: true },
  { accessorKey: 'createdAt', header: '创建时间', width: 180, defaultHidden: true },
]

const rowActions: ActionMenuItem<AiRunSnapshot>[] = [
  { label: '查看实时进度', icon: 'morph:activity', onClick: open },
]

function label(status: string) {
  return ({ QUEUED: '排队中', RUNNING: '执行中', WAITING: '等待输入', COMPLETED: '已完成', FAILED: '失败', CANCELLED: '已停止' })[status] || status
}

function open(run: AiRunSnapshot) {
  selected.value = run.id
  drawerOpen.value = true
}
</script>

<template>
  <div>
    <YdTablePageLayout
      v-if="canView"
      title="AI 系统任务"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 只读情报：提示文案与实时快照总数，操作按钮一律走 actions -->
      <template #page-title>
        <span class="text-sm text-muted-foreground">观察系统后台任务的实际模型输出、工具和进度；离开此页不会取消任务。</span>
        <span class="text-sm text-muted-foreground">实时快照 · 共 {{ pagination.total }} 个任务</span>
      </template>

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
        <div class="flex flex-col gap-3 h-full min-h-0">
          <YdAlert v-if="runtime.error" variant="destructive" :title="runtime.error" class="shrink-0" />
          <YdLoadingOverlay :loading="loading || runtime.loading" class="flex-1 min-h-0">
            <YdResponsiveEnhancedTable
              row-key="id"
              table-root-class="rounded-lg overflow-hidden"
              table-class="min-w-[880px]"
              column-visibility
              :status-column="statusColumn"
              :actions="rowActions"
              :columns="tableColumns"
              :data="rows"
            >
              <template #cell-module="{ row }">
                <span class="font-medium break-all">{{ row.original.module }}</span>
              </template>
              <template #cell-sessionId="{ row }">
                <span v-if="row.original.sessionId" class="min-w-0 whitespace-normal break-all">{{ row.original.sessionId }}</span>
                <span v-else class="text-muted-foreground/40">-</span>
              </template>
              <template #cell-createdAt="{ row }">
                <span v-if="row.original.createdAt">{{ row.original.createdAt.replace('T', ' ').slice(0, 19) }}</span>
                <span v-else class="text-muted-foreground/40">-</span>
              </template>
              <template #card="{ row }">
                <YdCard class="w-full">
                  <div class="flex flex-col gap-3">
                    <div class="flex gap-2 items-center justify-between">
                      <span class="text-base font-semibold min-w-0 break-words">{{ row.module }}</span>
                      <YdTag :variant="row.status === 'FAILED' ? 'destructive' : 'secondary'">
                        {{ label(row.status) }}
                      </YdTag>
                    </div>
                    <div class="text-sm flex flex-col gap-1">
                      <div class="flex gap-2 min-w-0">
                        <span class="text-secondary-foreground/60 shrink-0">创建时间</span>
                        <span class="min-w-0 break-all">{{ row.createdAt || '-' }}</span>
                      </div>
                      <div class="flex gap-2 min-w-0">
                        <span class="text-secondary-foreground/60 shrink-0">工作上下文</span>
                        <span class="min-w-0 break-all">{{ row.sessionId || '-' }}</span>
                      </div>
                    </div>
                    <div class="pt-3 border-t flex flex-wrap gap-2">
                      <YdButton size="sm" variant="outline" @click="open(row)">
                        查看实时进度
                      </YdButton>
                    </div>
                  </div>
                </YdCard>
              </template>
            </YdResponsiveEnhancedTable>
          </YdLoadingOverlay>
        </div>
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
    <YdAlert v-else variant="destructive" title="没有查看系统 AI 任务的权限" />

    <YdDrawer v-model="drawerOpen" title="系统 AI 任务进度" :footer="false" content-class="w-[760px] max-w-full">
      <AiRunProgress v-if="selected && drawerOpen" :run-id="selected" scope="system" :can-cancel="canCancel" :can-approve="canApprove" />
    </YdDrawer>
  </div>
</template>
