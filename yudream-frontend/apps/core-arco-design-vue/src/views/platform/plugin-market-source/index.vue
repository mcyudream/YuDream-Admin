<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdTablePageAction } from '@yudream/components'
import type { MarketSourceType, PluginMarketSource, PluginMarketSourcePayload } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'
import { useAppFeatureStore } from '@/store/modules/app/features'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()
const featureStore = useAppFeatureStore()
const localSourceEnabled = computed(() => featureStore.capabilityEnabled('plugin-market-source'))

const loading = ref(false)
const loadFailed = ref(false)
const syncing = ref(false)
const rows = ref<PluginMarketSource[]>([])
const keyword = ref('')
const statusFilter = ref<'ALL' | 'ENABLED' | 'DISABLED'>('ALL')
const typeFilter = ref<'ALL' | MarketSourceType>('ALL')
const publicEnabled = ref(true)
const publicSaving = ref(false)

const formVisible = ref(false)
const formSaving = ref(false)
const testing = ref(false)
const testMessage = ref('')
const testOk = ref(false)
const editing = ref<PluginMarketSource | null>(null)
const form = reactive<PluginMarketSourcePayload>({
  code: '',
  name: '',
  type: 'V2_API',
  rootUrl: '',
  token: '',
  sortOrder: 100,
})

const sourceTypeOptions: { label: string, value: MarketSourceType }[] = [
  { label: 'v2 协议源', value: 'V2_API' },
  { label: '静态索引（legacy）', value: 'STATIC_INDEX' },
]
const statusFilterOptions = [
  { label: '全部状态', value: 'ALL' as const },
  { label: '已启用', value: 'ENABLED' as const },
  { label: '已禁用', value: 'DISABLED' as const },
]
const typeFilterOptions = [
  { label: '全部类型', value: 'ALL' as const },
  { label: '本机源', value: 'LOCAL' as const },
  { label: 'v2 协议源', value: 'V2_API' as const },
  { label: '静态索引', value: 'STATIC_INDEX' as const },
]

const tableColumns: TableColumn<PluginMarketSource>[] = [
  { id: 'source', header: '市场源', width: 240, fixed: 'left' },
  { accessorKey: 'rootUrl', header: '源地址', width: 320 },
  { id: 'enabled', header: '状态', width: 100 },
  { id: 'sync', header: '同步', width: 180 },
  { accessorKey: 'pluginCount', header: '插件数', width: 90, align: 'right' },
  { accessorKey: 'sortOrder', header: '排序', width: 80, align: 'right', defaultHidden: true },
  { accessorKey: 'syncedAt', header: '同步时间', width: 170, defaultHidden: true },
]

/** 行操作统一收进操作菜单：同步只对远程源可见，删除对内置源不可用。 */
const sourceActions: ActionMenuItem<PluginMarketSource>[] = [
  {
    label: '同步目录',
    icon: 'morph:refresh-cw',
    visible: row => canSync(row) && auth('platform:plugin-market-source:run'),
    onClick: confirmSync,
  },
  {
    label: row => (row.enabled ? '禁用' : '启用'),
    icon: 'morph:power',
    visible: () => auth('platform:plugin-market-source:edit'),
    onClick: confirmToggle,
  },
  {
    label: '编辑',
    icon: 'morph:pencil',
    visible: () => auth('platform:plugin-market-source:edit'),
    onClick: row => openForm(row),
  },
  {
    label: '删除',
    icon: 'morph:trash-2',
    danger: true,
    visible: row => !row.builtIn && auth('platform:plugin-market-source:delete'),
    onClick: confirmRemove,
  },
]

/** 页头操作：全部同步属不常用动作，超出阈值时收进「更多」。 */
const pageActions = computed<YdTablePageAction[]>(() => [
  ...(auth('platform:plugin-market-source:run')
    ? [{
        label: '全部同步',
        icon: 'morph:refresh-cw',
        variant: 'outline' as const,
        autoFold: true,
        disabled: syncing.value,
        onClick: confirmSyncAll,
      }]
    : []),
  ...(auth('platform:plugin-market-source:create')
    ? [{ label: '添加市场源', icon: 'morph:plus', onClick: () => openForm() }]
    : []),
])

/**
 * 市场源是有界小集合（通常个位数）：关键词与筛选在本地完成，
 * 不为一次筛选多打一次往返，因此本页不提供分页。
 */
const filteredRows = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return rows.value.filter((row) => {
    const matchesKeyword = !query
      || [row.name, row.code, row.rootUrl].some(value => value?.toLowerCase().includes(query))
    const matchesStatus = statusFilter.value === 'ALL'
      || (statusFilter.value === 'ENABLED') === row.enabled
    const matchesType = typeFilter.value === 'ALL' || sourceTypeOf(row) === typeFilter.value
    return matchesKeyword && matchesStatus && matchesType
  })
})
const hasFilters = computed(() =>
  Boolean(keyword.value.trim()) || statusFilter.value !== 'ALL' || typeFilter.value !== 'ALL',
)
const enabledCount = computed(() => rows.value.filter(row => row.enabled).length)
const syncErrorCount = computed(() => rows.value.filter(row => row.syncedAt && row.syncStatus !== 'OK').length)
const summaryText = computed(() => `共 ${rows.value.length} 个市场源 · 启用 ${enabledCount.value}${syncErrorCount.value ? ` · 同步异常 ${syncErrorCount.value}` : ''}`)

const publicIndexUrl = `${window.location.origin}/api/public/plugin-market/index.json`
const publicV2Url = `${window.location.origin}/api/public/plugin-market`

onMounted(load)

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    try {
      const listRes = await apiMarketSource.list()
      rows.value = listRes.data
    }
    catch {
      loadFailed.value = true
      toast.error('加载市场源列表失败')
    }
    if (!localSourceEnabled.value) {
      publicEnabled.value = false
    }
    else {
      try {
        const publicRes = await apiMarketSource.publicEnabled()
        publicEnabled.value = publicRes.data === true
      }
      catch {
        // 拦截器已提示；保留当前开关，避免失败时回落成已开启
      }
    }
  }
  finally {
    loading.value = false
  }
}

function resetFilters() {
  keyword.value = ''
  statusFilter.value = 'ALL'
  typeFilter.value = 'ALL'
}

function openForm(row?: PluginMarketSource) {
  editing.value = row || null
  testMessage.value = ''
  Object.assign(form, row
    ? {
        code: row.code,
        name: row.name,
        type: row.type || (row.builtIn ? 'LOCAL' : 'STATIC_INDEX'),
        rootUrl: row.rootUrl,
        token: '',
        sortOrder: row.sortOrder ?? 100,
      }
    : {
        code: '',
        name: '',
        type: 'V2_API',
        rootUrl: '',
        token: '',
        sortOrder: 100,
      })
  formVisible.value = true
}

async function saveForm() {
  if (!editing.value && !form.code?.trim()) {
    toast.error('请填写市场源标识')
    return
  }
  if (!editing.value?.builtIn && !form.rootUrl?.trim()) {
    toast.error('请填写市场源地址')
    return
  }
  formSaving.value = true
  try {
    if (editing.value) {
      await apiMarketSource.update(editing.value.id, { ...form, code: undefined })
      toast.success('市场源已保存')
    }
    else {
      await apiMarketSource.create({ ...form })
      toast.success('市场源已添加，可点击同步拉取目录')
    }
    formVisible.value = false
    await load()
  }
  finally {
    formSaving.value = false
  }
}

async function testForm() {
  const rootUrl = form.rootUrl?.trim()
  if (!rootUrl) {
    toast.error('请先填写市场源地址')
    return
  }
  testing.value = true
  testMessage.value = ''
  try {
    const res = await apiMarketSource.test({ rootUrl, token: form.token || undefined, type: form.type === 'LOCAL' ? undefined : form.type })
    testOk.value = res.data.ok
    testMessage.value = res.data.message || (res.data.ok ? '连接成功' : '连接失败')
  }
  catch {
    testOk.value = false
    testMessage.value = '测试请求失败，请检查地址与网络'
  }
  finally {
    testing.value = false
  }
}

function confirmToggle(row: PluginMarketSource) {
  const enabling = !row.enabled
  modal.confirm({
    title: enabling ? '确认启用' : '确认禁用',
    content: `确认${enabling ? '启用' : '禁用'}市场源「${row.name}」吗？${enabling ? '' : '禁用后市场列表不再展示该源插件。'}`,
    onConfirm: async () => {
      await (enabling ? apiMarketSource.enable(row.id) : apiMarketSource.disable(row.id))
      toast.success(enabling ? '已启用' : '已禁用')
      await load()
    },
  })
}

function canSync(row: PluginMarketSource) {
  return row.type !== 'LOCAL' && !row.builtIn
}

function confirmSync(row: PluginMarketSource) {
  if (!canSync(row)) {
    toast.success('本机源无需同步，目录实时读取本机发布物')
    return
  }
  modal.confirm({
    title: '确认同步',
    content: `确认拉取市场源「${row.name}」的最新目录吗？`,
    onConfirm: async () => {
      await apiMarketSource.sync(row.id)
      toast.success('同步完成')
      await load()
    },
  })
}

function confirmSyncAll() {
  modal.confirm({
    title: '确认全部同步',
    content: '确认拉取全部已启用市场源的最新目录吗？视源数量可能需要一些时间。',
    onConfirm: async () => {
      syncing.value = true
      try {
        await apiMarketSource.syncAll()
        toast.success('同步完成')
        await load()
      }
      finally {
        syncing.value = false
      }
    },
  })
}

function confirmRemove(row: PluginMarketSource) {
  modal.confirm({
    title: '确认删除',
    content: `确认删除市场源「${row.name}」吗？其目录快照会一并删除，已安装插件不受影响。`,
    onConfirm: async () => {
      await apiMarketSource.remove(row.id)
      toast.success('已删除')
      await load()
    },
  })
}

function syncVariant(row: PluginMarketSource) {
  if (!row.syncedAt) {
    return 'secondary'
  }
  return row.syncStatus === 'OK' ? 'default' : 'destructive'
}

function syncText(row: PluginMarketSource) {
  if (!row.syncedAt) {
    return '未同步'
  }
  return row.syncStatus === 'OK' ? '正常' : '异常'
}

function formatTime(value?: string) {
  if (!value) {
    return '—'
  }
  const match = value.match(/^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2})/)
  return match ? `${match[1]} ${match[2]}` : value
}

async function togglePublicEnabled(value: boolean | undefined) {
  publicSaving.value = true
  try {
    const res = await apiMarketSource.updatePublicEnabled(value === true)
    publicEnabled.value = res.data
    toast.success(res.data ? '公开插件市场已开启' : '公开插件市场已关闭，社区页与对外协议停止服务')
  }
  finally {
    publicSaving.value = false
  }
}

function sourceTypeOf(row: PluginMarketSource): MarketSourceType {
  if (row.builtIn || row.type === 'LOCAL') {
    return 'LOCAL'
  }
  return row.type === 'V2_API' ? 'V2_API' : 'STATIC_INDEX'
}

function sourceTypeText(row: PluginMarketSource) {
  const map: Record<MarketSourceType, string> = {
    LOCAL: '本机源',
    V2_API: 'v2 协议',
    STATIC_INDEX: '静态索引',
  }
  return map[sourceTypeOf(row)]
}

function sourceAddress(row: PluginMarketSource) {
  return sourceTypeOf(row) === 'LOCAL' ? '本机发布物（进程内直读）' : row.rootUrl
}

function openPublicMarket() {
  window.open('/market', '_blank', 'noopener')
}
</script>

<template>
  <YdTablePageLayout
    title="市场源管理"
    :actions="pageActions"
    :actions-threshold="3"
    :show-stats="localSourceEnabled"
    stats-title="对外入口"
    :stats-default-expanded="true"
  >
    <!-- 页头只放只读情报：按钮一律走 actions -->
    <template #page-title>
      <span class="text-sm text-muted-foreground">{{ summaryText }}</span>
    </template>

    <template #stats>
      <div class="external-panel">
        <div class="external-item">
          <div class="external-item__body">
            <strong>公开插件市场</strong>
            <p>开启后对外提供 /market 社区页与 v2 源协议；关闭后公开入口与协议停止服务，后台发布、审核与订阅源不受影响。</p>
          </div>
          <div class="external-item__action">
            <YdTag :variant="publicEnabled ? 'default' : 'secondary'">
              {{ publicEnabled ? '已开启' : '已关闭' }}
            </YdTag>
            <YdSwitch
              v-auth="'platform:plugin-market-source:edit'"
              :model-value="publicEnabled"
              :disabled="publicSaving"
              aria-label="公开插件市场开关"
              @update:model-value="togglePublicEnabled"
            />
          </div>
        </div>

        <div class="external-item">
          <div class="external-item__body">
            <strong>对外 v2 源基址</strong>
            <p>其他实例添加源时类型选「v2 协议源」，地址填该基址。</p>
            <code>{{ publicV2Url }}</code>
          </div>
          <div class="external-item__action">
            <YdCopyButton :text="publicV2Url" label="复制" />
            <YdButton variant="outline" size="sm" :disabled="!publicEnabled" @click="openPublicMarket">
              <YdIcon name="morph:external-link" />
              公开社区
            </YdButton>
          </div>
        </div>

        <div class="external-item">
          <div class="external-item__body">
            <strong>legacy 静态索引</strong>
            <p>仍可用的 index.json 完整地址，仅供旧客户端订阅。</p>
            <code>{{ publicIndexUrl }}</code>
          </div>
          <div class="external-item__action">
            <YdCopyButton :text="publicIndexUrl" label="复制" />
          </div>
        </div>
      </div>
    </template>

    <template #search>
      <!-- 有界小集合：本地过滤，控件与表格页筛选行同版式 -->
      <div class="source-search">
        <label class="source-search__field">
          <span>关键词</span>
          <YdInput v-model="keyword" placeholder="名称 / 标识 / 地址" clearable />
        </label>
        <label class="source-search__field">
          <span>状态</span>
          <YdFilterSelect v-model="statusFilter" :options="statusFilterOptions" width="100%" placeholder="全部状态" />
        </label>
        <label class="source-search__field">
          <span>类型</span>
          <YdFilterSelect v-model="typeFilter" :options="typeFilterOptions" width="100%" placeholder="全部类型" />
        </label>
        <div class="source-search__actions">
          <YdButton v-if="hasFilters" variant="outline" @click="resetFilters">
            重置
          </YdButton>
        </div>
      </div>
    </template>

    <template #table>
      <YdLoadingOverlay :loading="loading">
        <YdResponsiveEnhancedTable
          row-key="id"
          table-root-class="rounded-lg overflow-hidden"
          table-class="min-w-[1040px]"
          column-visibility
          :actions="sourceActions"
          :columns="tableColumns"
          :data="filteredRows"
        >
          <template #empty>
            <span class="text-muted-foreground">
              {{ hasFilters ? '没有匹配的市场源，试试调整筛选条件' : (loadFailed ? '市场源加载失败，请稍后重试' : '还没有市场源，添加订阅源后可同步插件目录') }}
            </span>
          </template>

          <template #cell-source="{ row }">
            <div class="source-identity">
              <span class="source-identity__name">{{ row.original.name }}</span>
              <span class="source-identity__meta">
                <YdTag v-if="row.original.builtIn" variant="secondary">
                  内置
                </YdTag>
                <YdTag variant="secondary">
                  {{ sourceTypeText(row.original) }}
                </YdTag>
                <code>{{ row.original.code }}</code>
              </span>
            </div>
          </template>

          <template #cell-rootUrl="{ row }">
            <div class="source-address">
              <span>{{ sourceAddress(row.original) }}</span>
              <span v-if="row.original.tokenConfigured" class="source-address__token">
                <YdIcon name="morph:key-round" />
                已配置访问令牌
              </span>
            </div>
          </template>

          <template #cell-enabled="{ row }">
            <YdTag :variant="row.original.enabled ? 'default' : 'secondary'">
              {{ row.original.enabled ? '启用' : '禁用' }}
            </YdTag>
          </template>

          <template #cell-sync="{ row }">
            <div class="source-sync">
              <YdTag :variant="syncVariant(row.original)">
                {{ syncText(row.original) }}
              </YdTag>
              <span
                v-if="row.original.syncErrorMessage"
                class="source-sync__error"
                :title="row.original.syncErrorMessage"
              >{{ row.original.syncErrorMessage }}</span>
            </div>
          </template>

          <template #cell-syncedAt="{ row }">
            {{ formatTime(row.original.syncedAt) }}
          </template>
        </YdResponsiveEnhancedTable>
      </YdLoadingOverlay>
    </template>
  </YdTablePageLayout>

  <YdModal
      v-model="formVisible"
      :title="editing ? '编辑市场源' : '添加市场源'"
      show-cancel-button
      class="sm:max-w-2xl"
      :confirm-loading="formSaving"
      @confirm="saveForm"
    >
      <YdForm :model="form" layout="vertical">
        <div class="gap-x-4 grid grid-cols-1 md:grid-cols-2">
          <YdFormItem label="市场源标识">
            <YdInput v-model="form.code" :disabled="!!editing" placeholder="小写字母、数字或连字符，如 company-mirror" />
          </YdFormItem>
          <YdFormItem label="名称">
            <YdInput v-model="form.name" placeholder="市场源显示名称" />
          </YdFormItem>
        </div>
        <YdFormItem v-if="!editing?.builtIn" label="源类型">
          <YdSelect v-model="form.type" :options="sourceTypeOptions" :disabled="!!editing?.builtIn" />
        </YdFormItem>
        <YdFormItem v-if="editing?.builtIn" label="源类型">
          <YdInput model-value="本机源" disabled />
          <div class="text-xs text-secondary-foreground/60 mt-1">
            内置源固定为本机发布物，进程内直读，无需地址与同步。
          </div>
        </YdFormItem>
        <YdFormItem v-if="!editing?.builtIn" :label="form.type === 'V2_API' ? '源地址（v2 源基址）' : '源地址（index.json 完整地址）'">
          <div class="flex gap-2">
            <YdInput
              v-model="form.rootUrl"
              :placeholder="form.type === 'V2_API' ? 'https://example.com/api/public/plugin-market' : 'https://example.com/market/index.json'"
              class="flex-1"
            />
            <YdButton variant="outline" :loading="testing" @click="testForm">
              测试连接
            </YdButton>
          </div>
          <div v-if="testMessage" class="mt-1 flex gap-1 items-center">
            <YdTag :variant="testOk ? 'default' : 'destructive'">
              {{ testOk ? '成功' : '失败' }}
            </YdTag>
            <span class="text-xs text-secondary-foreground/60">{{ testMessage }}</span>
          </div>
        </YdFormItem>
        <div class="gap-x-4 grid grid-cols-1 md:grid-cols-[1fr_140px]">
          <YdFormItem label="访问令牌（可选）">
            <YdInput v-model="form.token" type="password" :placeholder="editing?.tokenConfigured ? '已配置，留白表示保留' : '私有源的 Bearer 令牌'" />
          </YdFormItem>
          <YdFormItem label="排序">
            <YdNumberField :model-value="form.sortOrder ?? 100" :min="0" class="w-full" @update:model-value="form.sortOrder = $event" />
          </YdFormItem>
        </div>
      </YdForm>
    </YdModal>
</template>

<style scoped>
/* 对外入口情报板：开关与对外地址各占一行，读起来像一条条事实而不是卡片墙 */
.external-panel { display: grid; gap: 12px; }
.external-item { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; justify-content: space-between; padding: 12px 14px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.external-item__body { display: grid; gap: 4px; min-width: 0; flex: 1 1 320px; }
.external-item__body strong { color: var(--color-text-1); font-size: 14px; }
.external-item__body p { margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.6; }
.external-item__body code { color: var(--color-text-2); font-size: 12px; overflow-wrap: anywhere; }
.external-item__action { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }

/* 搜索区：与 YdDynamicSearchForm 同版式（字段等宽、窄屏单列、操作按钮整行居右） */
.source-search { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 12px; align-items: end; }
.source-search__field { display: grid; gap: 6px; min-width: 0; }
.source-search__field > span { color: var(--color-text-3); font-size: 12px; }
.source-search__actions { display: flex; grid-column: 1 / -1; gap: 8px; justify-content: flex-end; padding-top: 4px; }

.source-identity { display: grid; gap: 3px; min-width: 0; }
.source-identity__name { color: var(--color-text-1); font-weight: 600; overflow-wrap: anywhere; }
.source-identity__meta { display: flex; gap: 6px; align-items: center; min-width: 0; }
.source-identity__meta code { color: var(--color-text-3); font-size: 11.5px; overflow-wrap: anywhere; }

.source-address { display: grid; gap: 3px; min-width: 0; overflow-wrap: anywhere; }
.source-address__token { display: inline-flex; gap: 4px; align-items: center; color: var(--color-text-3); font-size: 11.5px; }

.source-sync { display: grid; gap: 4px; justify-items: start; min-width: 0; }
.source-sync__error { display: block; max-width: 220px; overflow: hidden; color: var(--color-danger-6); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }

@media (max-width: 640px) {
  .source-search__actions { justify-content: stretch; }
  .source-search__actions > * { flex: 1; }
}
</style>
