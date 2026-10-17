<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, StatusColumn, TableColumn, UserColumn, YdTablePageAction } from '@yudream/components'
import type { DynamicForm, DynamicFormStatus, FormStatistics } from '@/api/modules/platform-form'
import { useTablePage } from '@yudream/components'
import apiForm from '@/api/modules/platform-form'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import DynamicFormRenderer from './components/DynamicFormRenderer.vue'
import FormSubmissionPanel from './components/FormSubmissionPanel.vue'

const router = useRouter()
const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()

const previewLoading = ref(false)
const statisticsLoading = ref(false)
const actionLoading = ref('')
const loadError = ref('')
const statisticsError = ref('')
const selected = ref<DynamicForm | null>(null)
const previewForm = ref<DynamicForm | null>(null)
const previewVisible = ref(false)
const submissionVisible = ref(false)
const statisticsVisible = ref(false)
const statistics = ref<FormStatistics | null>(null)

/** 状态词表：与后端 DynamicFormStatus 枚举一一对应，搜索下拉与状态列共用 */
const statusOptions = [
  { label: '草稿', value: 'DRAFT' },
  { label: '已发布', value: 'PUBLISHED' },
  { label: '已停用', value: 'DISABLED' },
]

/** 搜索选项数据源：标识与 DynamicFormPageQuery#status 的 optionsSource 对应 */
const optionsSources: SearchOptionsSource[] = [
  { id: 'formStatusOptions', loader: () => statusOptions },
]

/** 最近一次成功加载的分页快照：刷新失败时回填，保留已读数据而不是清空表格 */
const lastLoadedPage = shallowRef<{ records: DynamicForm[], total: number, page: number, size: number } | null>(null)

/** 列表：搜索条件来自 DynamicFormPageQuery 的 @Searchable 注解，分页由后端切片 */
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
} = useTablePage<DynamicForm>({
  queryClass: 'online.yudream.base.application.platform.form.query.DynamicFormPageQuery',
  initialSize: 10,
  loadApi: async (query) => {
    try {
      const res = await apiForm.page({
        page: query.page,
        size: query.size,
        keyword: query.keyword || undefined,
        status: query.status || undefined,
      })
      loadError.value = ''
      const pageResult = {
        records: res.data.records,
        total: Number(res.data.total || 0),
        page: res.data.page,
        size: res.data.size,
      }
      lastLoadedPage.value = pageResult
      return pageResult
    }
    catch (error) {
      // 刷新失败时保留已读数据；首次加载失败返回空结果，由错误结果页兜底。
      loadError.value = errorText(error, '请求表单列表失败，请稍后重试。')
      return lastLoadedPage.value ?? {
        records: [],
        total: 0,
        page: query.page,
        size: query.size,
      }
    }
  },
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

const hasFilters = computed(() => Boolean(String(searchQuery.keyword || '').trim() || searchQuery.status))

// Long 计数经全局 ToStringSerializer 落为字符串，动画数值必须先转回有限数字。
const statisticTiles = computed(() => [
  { key: 'total', title: '总提交', hint: '累计收到的提交数量', icon: 'morph:inbox', value: toCountNumber(statistics.value?.total) },
  { key: 'today', title: '今日提交', hint: '按服务器当天统计', icon: 'morph:calendar-check', value: toCountNumber(statistics.value?.today) },
  { key: 'last7Days', title: '近 7 天', hint: '最近 7 个自然日', icon: 'morph:trending-up', value: toCountNumber(statistics.value?.last7Days) },
])

/** 名称身份列固定左侧（桌面），移动卡片头部复用 */
const formUserColumn: UserColumn<DynamicForm> = {
  nameKey: 'name',
  header: '表单名称',
  width: 220,
  fixed: 'left',
}

/** 状态统一走 statusColumn：桌面徽章与卡片底部徽章共用同一映射 */
const formStatusColumn: StatusColumn<DynamicForm> = {
  statusKey: 'status',
  header: '状态',
  width: 100,
  statusMap: {
    DRAFT: { label: '草稿', variant: 'secondary', tone: 'neutral' },
    PUBLISHED: { label: '已发布', variant: 'default', tone: 'success' },
    DISABLED: { label: '已停用', variant: 'destructive', tone: 'danger' },
  },
}

/** 列：公开编码是技术字段、更新时间是审计字段，都可勾选但默认不展示 */
const tableColumns: TableColumn<DynamicForm>[] = [
  { accessorKey: 'code', header: '编码', width: 180, defaultHidden: true },
  { id: 'allowAnonymous', header: '公开填写', width: 110, align: 'center' },
  { accessorKey: 'publishedAt', header: '发布时间', width: 180 },
  { accessorKey: 'updateTime', header: '更新时间', width: 180, defaultHidden: true },
]

/** 行操作统一收进内置操作下拉：权限决定菜单项是否出现，行状态只决定是否禁用 */
const formActions: ActionMenuItem<DynamicForm>[] = [
  { label: '查看', icon: 'morph:eye', visible: () => auth('platform:form:view'), onClick: openPreview },
  { label: '设计', icon: 'morph:pencil-ruler', visible: () => auth('platform:form:edit'), onClick: openDesigner },
  { label: '提交结果', icon: 'morph:inbox', visible: () => auth('platform:form:submission:view'), onClick: openSubmissions },
  { label: '统计', icon: 'morph:chart-column', visible: () => auth('platform:form:statistics:view'), onClick: openStatistics },
  { label: '公开链接', icon: 'morph:link', disabled: row => row.status !== 'PUBLISHED', onClick: copyPublicUrl },
  { label: '发布', icon: 'morph:send', visible: () => auth('platform:form:publish'), disabled: row => row.status === 'PUBLISHED', onClick: publish },
  { label: '下线', icon: 'morph:circle-pause', visible: () => auth('platform:form:publish'), disabled: row => row.status !== 'PUBLISHED', onClick: unpublish },
  { label: '删除', icon: 'morph:trash-2', danger: true, visible: () => auth('platform:form:delete'), onClick: confirmDelete },
]

/** 页头操作：新建表单是常用操作直接显示，权限语义沿用原页头按钮 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('platform:form:edit')
    ? [{ label: '新建表单', icon: 'morph:plus', onClick: openCreate }]
    : []),
])

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
  await loadStatistics(row.id)
}

async function loadStatistics(id: string) {
  statisticsLoading.value = true
  statisticsError.value = ''
  try {
    const res = await apiForm.statistics(id)
    statistics.value = res.data
  }
  catch (error) {
    statisticsError.value = errorText(error, '获取表单统计失败，请稍后重试。')
  }
  finally {
    statisticsLoading.value = false
  }
}

function retryStatistics() {
  if (selected.value) {
    loadStatistics(selected.value.id)
  }
}

async function publish(row: DynamicForm) {
  await runPublicationAction(row.id, async () => {
    await apiForm.publish(row.id)
    toast.success('表单已发布')
  })
}

async function unpublish(row: DynamicForm) {
  await runPublicationAction(row.id, async () => {
    await apiForm.unpublish(row.id)
    toast.success('表单已取消发布')
  })
}

async function runPublicationAction(id: string, action: () => Promise<void>) {
  if (actionLoading.value) {
    return
  }
  actionLoading.value = id
  try {
    await action()
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

function toCountNumber(value?: number | string | null) {
  const parsed = Number(value ?? 0)
  return Number.isFinite(parsed) ? parsed : 0
}

function errorText(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message : ''
  return message && message !== '请求失败' ? message : fallback
}
</script>

<template>
  <div>
    <YdTablePageLayout
      title="动态表单"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 表单总数与说明是只读情报，按规范放页头而不占操作位 -->
      <template #page-title>
        <span class="text-sm text-muted-foreground">共 {{ pagination.total }} 个表单 · 设计并发布公开表单，跟踪提交结果与字段统计；发布后可通过公开链接或接口收集填写。</span>
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
        <!-- 首次加载失败：没有可展示数据时以结果页兜底 -->
        <div v-if="loadError && !rows.length" class="form-surface">
          <YdResult status="error" title="表单列表加载失败" :description="loadError">
            <YdButton @click="load">
              重新加载
            </YdButton>
          </YdResult>
        </div>

        <div v-else-if="!loading && !rows.length" class="form-surface">
          <YdEmpty
            :illustration="hasFilters ? 'no-result' : 'empty'"
            :title="hasFilters ? '没有匹配的表单' : '还没有动态表单'"
            :description="hasFilters ? '调整关键字或状态筛选后重试。' : '创建第一个表单，设计字段并发布后即可收集提交。'"
          >
            <YdButton v-if="hasFilters" variant="outline" @click="resetSearch">
              清除筛选
            </YdButton>
            <YdButton v-else v-auth="'platform:form:edit'" @click="openCreate">
              <YdIcon name="morph:plus" />
              新建表单
            </YdButton>
          </YdEmpty>
        </div>

        <div v-else class="flex flex-col h-full min-h-0">
          <YdAlert
            v-if="loadError"
            class="mb-3 shrink-0"
            variant="destructive"
            icon="morph:triangle-alert"
            title="列表刷新失败"
            :description="`${loadError}当前展示的是最近一次成功加载的数据。`"
          />
          <YdLoadingOverlay :loading="loading || Boolean(actionLoading)" class="flex-1 min-h-0">
            <YdResponsiveEnhancedTable
              row-key="id"
              table-root-class="rounded-lg overflow-hidden"
              table-class="min-w-[820px]"
              column-visibility
              :user-column="formUserColumn"
              :status-column="formStatusColumn"
              :actions="formActions"
              :columns="tableColumns"
              :data="rows"
            >
              <template #cell-code="{ row }">
                <span v-if="row.original.code" class="break-all">{{ row.original.code }}</span>
                <span v-else class="text-muted-foreground/40">-</span>
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

    <YdModal v-model="previewVisible" :title="previewForm ? `${previewForm.name} / 表单详情` : '表单详情'" class="sm:max-w-4xl">
      <YdLoadingOverlay :loading="previewLoading">
        <section class="preview-panel">
          <div v-if="previewForm" class="preview-meta">
            <YdTag :variant="statusVariant(previewForm.status)">
              {{ statusText(previewForm.status) }}
            </YdTag>
            <code>{{ previewForm.code }}</code>
            <span>{{ previewForm.allowAnonymous ? '公开填写' : '登录后填写' }}</span>
            <span v-if="previewForm.publishedAt">发布于 {{ dateText(previewForm.publishedAt) }}</span>
          </div>
          <DynamicFormRenderer v-if="previewForm" :form="previewForm" readonly />
        </section>
      </YdLoadingOverlay>
    </YdModal>

    <YdModal v-model="submissionVisible" :title="selected ? `${selected.name} / 提交结果` : '提交结果'" class="sm:max-w-6xl">
      <FormSubmissionPanel :form="selected" :form-id="selected?.id" :form-name="selected?.name" embedded />
    </YdModal>

    <YdModal v-model="statisticsVisible" :title="selected ? `${selected.name} / 结果统计` : '结果统计'" class="sm:max-w-4xl">
      <YdLoadingOverlay :loading="statisticsLoading">
        <section class="statistics-panel">
          <template v-if="statisticsError">
            <YdResult status="error" title="统计加载失败" :description="statisticsError">
              <YdButton @click="retryStatistics">
                重新加载
              </YdButton>
            </YdResult>
          </template>
          <template v-else>
            <YdStats>
              <YdStat v-for="tile in statisticTiles" :key="tile.key" :title="tile.title" :description="tile.hint">
                <template #value>
                  <YdAnimatedCountTo :value="tile.value" />
                </template>
                <template #figure>
                  <span class="stat-tile__figure"><YdIcon :name="tile.icon" /></span>
                </template>
              </YdStat>
            </YdStats>

            <div v-if="statistics?.fields?.length" class="field-stats">
              <div class="field-stats__heading">
                <strong>字段统计</strong>
                <span>按字段展示填写量与该字段出现最多的取值</span>
              </div>
              <article v-for="field in statistics.fields" :key="field.field" class="field-stat">
                <header class="field-stat__head">
                  <code>{{ field.field }}</code>
                  <span class="field-stat__counts">
                    <span>填写 {{ field.filled }}</span>
                    <span>空值 {{ field.empty }}</span>
                  </span>
                </header>
                <div class="value-list">
                  <span v-for="item in field.topValues" :key="item.value" class="value-chip">
                    <span class="value-chip__value">{{ item.value }}</span>
                    <em class="value-chip__count">{{ item.count }}</em>
                  </span>
                  <em v-if="!field.topValues.length" class="value-list__empty">暂无可统计值</em>
                </div>
              </article>
            </div>
            <div v-else class="form-surface">
              <YdEmpty
                illustration="no-result"
                title="暂无可统计的字段"
                description="该表单还没有提交记录，或提交内容尚未产生可统计的取值。"
              />
            </div>
          </template>
        </section>
      </YdLoadingOverlay>
    </YdModal>
  </div>
</template>

<style scoped>
/* 页面级状态容器：与表格同宽的安静表面，承载错误、空态与统计空态。 */
.form-surface {
  padding: 32px 20px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 12px;
}

.preview-panel {
  display: grid;
  gap: 14px;
  max-height: min(70vh, 720px);
  padding-right: 4px;
  overflow-y: auto;
}

.preview-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
  padding-bottom: 12px;
  font-size: 12px;
  color: var(--color-text-3);
  border-bottom: 1px solid var(--color-border-2);
}

.preview-meta code {
  padding: 2px 8px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 6px;
}

.statistics-panel {
  display: grid;
  gap: 18px;
}

.stat-tile__figure {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  font-size: 18px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 10px;
}

.field-stats {
  display: grid;
  gap: 10px;
}

.field-stats__heading {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  align-items: baseline;
  justify-content: space-between;
}

.field-stats__heading > strong {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-1);
}

.field-stats__heading > span {
  font-size: 12px;
  color: var(--color-text-3);
}

/* 字段项只用一个平面容器 + 行内计数，避免再套卡片。 */
.field-stat {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.field-stat__head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  align-items: baseline;
  justify-content: space-between;
}

.field-stat__head code {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.field-stat__counts {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  font-size: 12px;
  color: var(--color-text-3);
}

.value-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.value-chip {
  display: inline-flex;
  gap: 6px;
  align-items: baseline;
  max-width: 100%;
  padding: 3px 8px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 6px;
}

.value-chip__value {
  min-width: 0;
  overflow-wrap: anywhere;
}

.value-chip__count {
  font-style: normal;
  color: var(--color-text-3);
}

.value-list__empty {
  font-size: 12px;
  font-style: normal;
  color: var(--color-text-3);
}
</style>
