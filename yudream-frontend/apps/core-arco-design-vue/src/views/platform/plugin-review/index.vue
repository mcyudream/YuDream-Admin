<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, TableColumn, YdTablePageAction } from '@yudream/components'
import type { PluginMarketPublication } from '@/api/modules/platform-plugin-market-source'
import { useTablePage } from '@yudream/components'
import apiMarketSource, { PLUGIN_PUBLICATION_STATUS_OPTIONS } from '@/api/modules/platform-plugin-market-source'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import { formatPublicationSize, formatPublicationTime, publicationChannelText, publicationStatusText, publicationStatusVariant, publicationTitle } from '../plugin-market-source/publication-format'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()

const reviewRequired = ref(true)
const reviewSaving = ref(false)
const actingPublicationId = ref('')

/** 状态下拉：标识与 PluginMarketPublicationPageQuery#status 的 optionsSource 对应 */
const optionsSources: SearchOptionsSource[] = [
  {
    id: 'publicationStatusOptions',
    loader: () => [
      { label: '全部状态', value: '' },
      ...PLUGIN_PUBLICATION_STATUS_OPTIONS,
    ],
  },
]

const tableColumns: TableColumn<PluginMarketPublication>[] = [
  { id: 'publication', header: '发布物', width: 260, fixed: 'left' },
  { accessorKey: 'pluginVersion', header: '版本', width: 110 },
  { id: 'channel', header: '通道', width: 110 },
  { accessorKey: 'category', header: '分类', width: 120 },
  { id: 'pubStatus', header: '状态', width: 170 },
  { accessorKey: 'downloadCount', header: '下载', width: 90, align: 'right', defaultHidden: true },
  { accessorKey: 'createTime', header: '提交时间', width: 160 },
]

/**
 * 审核默认只看向待审核：页面进来就能处理待办，其它状态通过搜索区切换。
 * 搜索条件来自 PluginMarketPublicationPageQuery 的 @Searchable 注解（关键词 + 状态）。
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
} = useTablePage<PluginMarketPublication>({
  queryClass: 'online.yudream.base.application.platform.plugin.query.PluginMarketPublicationPageQuery',
  loadApi: async (query) => {
    const res = await apiMarketSource.publications({
      status: query.status || undefined,
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
  initialQuery: { status: 'PENDING' },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 行操作按状态与权限收敛：待审核才可通过/拒绝，已发布才可下架，删除始终可用。 */
const publicationActions: ActionMenuItem<PluginMarketPublication>[] = [
  {
    label: '通过',
    icon: 'morph:circle-check',
    visible: row => row.status === 'PENDING' && auth('platform:plugin-market-source:accept'),
    onClick: confirmAccept,
  },
  {
    label: '拒绝',
    icon: 'morph:circle-slash',
    danger: true,
    visible: row => row.status === 'PENDING' && auth('platform:plugin-market-source:accept'),
    onClick: confirmReject,
  },
  {
    label: '下架',
    icon: 'morph:archive',
    danger: true,
    visible: row => row.status === 'PUBLISHED' && auth('platform:plugin-market-source:delete'),
    onClick: confirmUnpublish,
  },
  {
    label: '删除',
    icon: 'morph:trash-2',
    danger: true,
    visible: () => auth('platform:plugin-market-source:delete'),
    onClick: confirmDelete,
  },
]

/** 页头操作：刷新待审核列表由搜索与翻页承担，本页只保留审核开关所在的设置区。 */
const pageActions = computed<YdTablePageAction[]>(() => [])

const pendingCountText = computed(() =>
  searchQuery.status === 'PENDING' ? `待审核 ${pagination.total} 条` : `共 ${pagination.total} 条记录`,
)

onMounted(loadReviewRequired)

async function loadReviewRequired() {
  try {
    const res = await apiMarketSource.reviewRequired()
    reviewRequired.value = res.data
  }
  catch {
    // 拦截器已提示；保留当前开关，避免失败时回落成错误状态
  }
}

async function toggleReviewRequired(value: boolean | undefined) {
  reviewSaving.value = true
  try {
    const res = await apiMarketSource.updateReviewRequired(value === true)
    reviewRequired.value = res.data
    toast.success(res.data ? '发布需审核已开启' : '发布需审核已关闭，新发布直接对外可见')
  }
  finally {
    reviewSaving.value = false
  }
}

function confirmAccept(row: PluginMarketPublication) {
  modal.confirm({
    title: '通过发布',
    content: `确认发布 ${row.code}@${row.pluginVersion} 吗？通过后立即对外可见。`,
    onConfirm: () => actOnPublication(row, api => api.acceptPublication(row.id), '已通过审核'),
  })
}

function confirmReject(row: PluginMarketPublication) {
  modal.confirm({
    title: '拒绝发布',
    content: `确认拒绝 ${row.code}@${row.pluginVersion} 吗？同版本不可重新上传，需发布新版本。`,
    onConfirm: () => actOnPublication(row, api => api.rejectPublication(row.id), '已拒绝'),
  })
}

function confirmUnpublish(row: PluginMarketPublication) {
  modal.confirm({
    title: '确认下架',
    content: `确认下架 ${row.code}@${row.pluginVersion} 吗？订阅方将不再拉取到该版本，文件保留备查。`,
    onConfirm: () => actOnPublication(row, api => api.unpublishPublication(row.id), '已下架'),
  })
}

function confirmDelete(row: PluginMarketPublication) {
  modal.confirm({
    title: '确认删除',
    content: `确认永久删除 ${row.code}@${row.pluginVersion} 吗？发布记录与 JAR 都会移除，不可恢复。`,
    onConfirm: () => actOnPublication(row, api => api.deletePublication(row.id), '已删除'),
  })
}

async function actOnPublication(row: PluginMarketPublication, action: (api: typeof apiMarketSource) => Promise<unknown>, success: string) {
  actingPublicationId.value = row.id
  try {
    await action(apiMarketSource)
    toast.success(success)
    await load()
  }
  finally {
    actingPublicationId.value = ''
  }
}
</script>

<template>
  <YdTablePageLayout
    title="发布审核"
    :actions="pageActions"
    :show-stats="auth('platform:plugin-market-source:edit')"
    stats-title="审核开关"
    :stats-default-expanded="true"
  >
    <template #page-title>
      <span class="text-sm text-muted-foreground">{{ pendingCountText }}</span>
    </template>

    <template #stats>
      <div class="review-switch">
        <div class="review-switch__body">
          <strong>发布需审核</strong>
          <p>开启后新上传进入待审核；关闭后界面与流水线发布直接对外可见。持有跳过审核权限的作者仍可直接发布。</p>
        </div>
        <div class="review-switch__action">
          <YdTag :variant="reviewRequired ? 'default' : 'secondary'">
            {{ reviewRequired ? '已开启' : '已关闭' }}
          </YdTag>
          <YdSwitch
            :model-value="reviewRequired"
            :disabled="reviewSaving"
            aria-label="发布需审核开关"
            @update:model-value="toggleReviewRequired"
          />
        </div>
      </div>
    </template>

    <template #search>
      <YdDynamicSearchForm
        v-model="searchQuery"
        :config="searchConfig"
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
          table-class="min-w-[1000px]"
          column-visibility
          :actions="publicationActions"
          :columns="tableColumns"
          :data="rows"
        >
          <template #empty>
            <span class="text-muted-foreground">
              {{ searchQuery.status === 'PENDING' ? '暂无待审核发布物，新的发布申请会出现在这里' : '当前条件下没有发布物' }}
            </span>
          </template>

          <template #cell-publication="{ row }">
            <div class="publication-identity">
              <span class="publication-identity__name">{{ publicationTitle(row.original) }}</span>
              <span class="publication-identity__meta">
                <code>{{ row.original.code }}</code>
                <span>· {{ formatPublicationSize(row.original.sizeBytes) }}</span>
              </span>
            </div>
          </template>

          <template #cell-channel="{ row }">
            <YdTag variant="secondary">
              {{ publicationChannelText(row.original.channel) }}
            </YdTag>
          </template>

          <template #cell-pubStatus="{ row }">
            <div class="publication-status">
              <YdTag :variant="publicationStatusVariant(row.original.status)">
                {{ publicationStatusText(row.original.status) }}
              </YdTag>
              <span
                v-if="row.original.reviewNote"
                class="publication-status__note"
                :title="row.original.reviewNote"
              >{{ row.original.reviewNote }}</span>
            </div>
          </template>

          <template #cell-createTime="{ row }">
            {{ formatPublicationTime(row.original.createTime) }}
          </template>
        </YdResponsiveEnhancedTable>
      </YdLoadingOverlay>
    </template>

    <template #footer>
      <YdPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :sizes="[10, 20, 50]"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />
    </template>
  </YdTablePageLayout>
</template>

<style scoped>
.review-switch { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; padding: 12px 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.review-switch__body { display: grid; gap: 4px; min-width: 0; flex: 1 1 320px; }
.review-switch__body strong { color: var(--color-text-1); font-size: 14px; }
.review-switch__body p { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.review-switch__action { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }

.publication-identity { display: grid; gap: 3px; min-width: 0; }
.publication-identity__name { color: var(--color-text-1); font-weight: 600; overflow-wrap: anywhere; }
.publication-identity__meta { display: flex; gap: 4px; align-items: center; color: var(--color-text-3); font-size: 11.5px; }
.publication-identity__meta code { color: var(--color-text-3); }

.publication-status { display: grid; gap: 4px; justify-items: start; min-width: 0; }
.publication-status__note { display: block; max-width: 180px; overflow: hidden; color: var(--color-text-3); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
</style>
