<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PluginMarketPublication, PluginMarketSource, PluginMarketSourcePayload, PluginPublicationChannel, PluginPublicationStatus, MarketSourceType } from '@/api/modules/platform-plugin-market-source'
import { PLUGIN_PUBLICATION_STATUS_OPTIONS } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'

const modal = useFaModal()
const toast = useFaToast()

const loading = ref(false)
const syncing = ref(false)
const rows = ref<PluginMarketSource[]>([])

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

const columns = computed<TableColumn<PluginMarketSource>[]>(() => [
  { accessorKey: 'name', header: '名称', width: 200, fixed: 'left' },
  { id: 'type', header: '类型', width: 140 },
  { accessorKey: 'rootUrl', header: '源地址', width: 320 },
  { id: 'enabled', header: '状态', width: 90, align: 'center' },
  { id: 'sync', header: '同步', width: 170 },
  { accessorKey: 'pluginCount', header: '插件数', width: 90, align: 'right' },
  { accessorKey: 'sortOrder', header: '排序', width: 80, align: 'right' },
  { accessorKey: 'syncedAt', header: '同步时间', width: 180 },
  { id: 'operation', header: '操作', width: 210, align: 'center', fixed: 'right' },
])

const publicIndexUrl = `${window.location.origin}/api/public/plugin-market/index.json`
const publicV2Url = `${window.location.origin}/api/public/plugin-market`
const publications = ref<PluginMarketPublication[]>([])
const pubLoading = ref(false)
const pubStatusFilter = ref<'all' | PluginPublicationStatus>('all')
const reviewRequired = ref(true)
const reviewSaving = ref(false)
const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFile = ref<File | null>(null)
const uploadNotes = ref('')
const actingPublicationId = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const publicationFilterOptions = computed(() => [
  { label: '全部状态', value: 'all' },
  ...PLUGIN_PUBLICATION_STATUS_OPTIONS,
])
const publicationRows = computed(() => pubStatusFilter.value === 'all'
  ? publications.value
  : publications.value.filter(item => item.status === pubStatusFilter.value))

const publicationColumns = computed<TableColumn<PluginMarketPublication>[]>(() => [
  { id: 'plugin', header: '插件', width: 240, fixed: 'left' },
  { accessorKey: 'pluginVersion', header: '版本', width: 110 },
  { id: 'channel', header: '通道', width: 100, align: 'center' },
  { accessorKey: 'sha256', header: 'SHA-256', width: 150 },
  { id: 'size', header: '大小', width: 100, align: 'right' },
  { id: 'pubStatus', header: '状态', width: 100, align: 'center' },
  { accessorKey: 'createTime', header: '提交时间', width: 170 },
  { id: 'pubOperation', header: '操作', width: 190, align: 'center', fixed: 'right' },
])

onMounted(load)

async function load() {
  loading.value = true
  await loadPublications()
  try {
    const res = await apiMarketSource.list()
    rows.value = res.data
  }
  catch {
    toast.error('加载市场源列表失败')
  }
  finally {
    loading.value = false
  }
}

async function loadPublications() {
  pubLoading.value = true
  try {
    const [publicationRes, reviewRes] = await Promise.all([
      apiMarketSource.publications(),
      apiMarketSource.reviewRequired(),
    ])
    publications.value = publicationRes.data
    reviewRequired.value = reviewRes.data
  }
  catch {
    // 发布区加载失败不阻塞源管理
  }
  finally {
    pubLoading.value = false
  }
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

function confirmSync(row: PluginMarketSource) {
  if (row.type === 'LOCAL' || row.builtIn) {
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
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function onUploadFileChange(event: Event) {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

function openUpload() {
  uploadFile.value = null
  uploadNotes.value = ''
  uploadVisible.value = true
}

async function submitUpload() {
  if (!uploadFile.value) {
    toast.error('请选择插件 JAR')
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', uploadFile.value)
    if (uploadNotes.value.trim()) {
      data.append('releaseNotes', uploadNotes.value.trim())
    }
    await apiMarketSource.uploadPublication(data)
    uploadVisible.value = false
    toast.success(reviewRequired.value ? '发布物已提交，等待审核后对外可见' : '发布物已发布')
    await loadPublications()
  }
  finally {
    uploading.value = false
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

async function actOnPublication(row: PluginMarketPublication, action: (api: typeof apiMarketSource) => Promise<unknown>, success: string) {
  actingPublicationId.value = row.id
  try {
    await action(apiMarketSource)
    toast.success(success)
    await loadPublications()
  }
  finally {
    actingPublicationId.value = ''
  }
}

async function copyPublicUrl() {
  await navigator.clipboard.writeText(publicV2Url)
  toast.success('已复制')
}

function sourceTypeText(row: PluginMarketSource) {
  if (row.builtIn || row.type === 'LOCAL') {
    return '本机源'
  }
  return row.type === 'V2_API' ? 'v2 协议' : '静态索引'
}

function publicationStatusVariant(status: PluginPublicationStatus) {
  return status === 'PUBLISHED' ? 'default' : status === 'PENDING' ? 'secondary' : 'destructive'
}

function publicationStatusText(status: PluginPublicationStatus) {
  return PLUGIN_PUBLICATION_STATUS_OPTIONS.find(item => item.value === status)?.label || status
}

function channelText(channel: PluginPublicationChannel) {
  return channel === 'PIPELINE' ? '流水线' : '界面上传'
}

function formatSize(bytes?: number) {
  if (!bytes) {
    return '—'
  }
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <FaPageHeader title="市场源管理" class="mb-0">
    <template #description>
      管理插件市场的订阅来源：添加多个自托管或官方市场源，市场页会合并展示各源插件。内置源为本机发布物，无需远端地址。
    </template>
  </FaPageHeader>
  <FaPageMain>
    <FaResponsiveTable
      v-loading="loading"
      row-key="id"
      table-root-class="rounded-lg overflow-hidden"
      table-class="min-w-[1080px]"
      border
      stripe
      :columns="columns"
      :data="rows"
    >
      <template #toolbar>
        <div class="flex flex-wrap items-center justify-end gap-2">
          <FaButton v-auth="'platform:plugin-market-source:run'" variant="outline" :loading="syncing" @click="confirmSyncAll">
            <FaIcon name="i-ri:refresh-line" />
            全部同步
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:create'" @click="openForm()">
            <FaIcon name="i-ri:add-line" />
            添加市场源
          </FaButton>
        </div>
      </template>
      <template #cell-name="{ row }">
        <div class="flex flex-col gap-1">
          <div class="flex items-center gap-2">
            <span class="font-medium">{{ row.original.name }}</span>
            <FaTag v-if="row.original.builtIn" variant="secondary">内置</FaTag>
            <FaTag variant="secondary">{{ sourceTypeText(row.original) }}</FaTag>
          </div>
          <span class="text-xs text-secondary-foreground/60">{{ row.original.code }}</span>
        </div>
      </template>
      <template #cell-type="{ row }">
        <FaTag variant="secondary">{{ sourceTypeText(row.original) }}</FaTag>
      </template>
      <template #cell-rootUrl="{ row }">
        <span class="break-all text-sm">{{ row.original.builtIn || row.original.type === 'LOCAL' ? '本机发布物（进程内直读）' : row.original.rootUrl }}</span>
        <div v-if="row.original.tokenConfigured" class="text-xs text-secondary-foreground/60">
          <FaIcon name="i-ri:key-2-line" />
          已配置访问令牌
        </div>
      </template>
      <template #cell-enabled="{ row }">
        <FaTag :variant="row.original.enabled ? 'default' : 'secondary'">
          {{ row.original.enabled ? '启用' : '禁用' }}
        </FaTag>
      </template>
      <template #cell-sync="{ row }">
        <FaTag :variant="syncVariant(row.original)">{{ syncText(row.original) }}</FaTag>
        <div v-if="row.original.syncErrorMessage" class="mt-1 flex max-w-[200px] items-center gap-1" :title="row.original.syncErrorMessage">
          <FaTag variant="destructive">失败</FaTag>
          <span class="truncate text-xs text-secondary-foreground/60">{{ row.original.syncErrorMessage }}</span>
        </div>
      </template>
      <template #cell-operation="{ row }">
        <div class="flex justify-center gap-2">
          <FaButton v-if="row.original.type !== 'LOCAL' && !row.original.builtIn" v-auth="'platform:plugin-market-source:run'" variant="outline" size="sm" @click="confirmSync(row.original)">
            同步
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:edit'" variant="outline" size="sm" @click="confirmToggle(row.original)">
            {{ row.original.enabled ? '禁用' : '启用' }}
          </FaButton>
          <FaButton v-auth="'platform:plugin-market-source:edit'" variant="link" size="sm" @click="openForm(row.original)">
            编辑
          </FaButton>
          <FaButton v-if="!row.original.builtIn" v-auth="'platform:plugin-market-source:delete'" variant="destructive" size="sm" @click="confirmRemove(row.original)">
            删除
          </FaButton>
        </div>
      </template>

      <template #card="{ row }">
        <FaCard class="w-full">
          <div class="flex flex-col gap-3">
            <div class="flex items-center justify-between gap-2">
              <span class="text-base font-semibold break-all">{{ row.name }}</span>
              <FaTag :variant="row.enabled ? 'default' : 'secondary'">
                {{ row.enabled ? '启用' : '禁用' }}
              </FaTag>
            </div>
            <div class="flex flex-col gap-1 text-sm">
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">标识</span>
                <span class="break-all">{{ row.code }}<template v-if="row.builtIn">（内置）</template></span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">地址</span>
                <span class="break-all">{{ row.builtIn || row.type === 'LOCAL' ? '本机发布物（进程内直读）' : row.rootUrl }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">同步</span>
                <span>{{ syncText(row) }} · {{ formatTime(row.syncedAt) }}</span>
              </div>
              <div v-if="row.syncErrorMessage" class="flex items-center gap-1">
                <FaTag variant="destructive">失败</FaTag>
                <span class="break-all text-xs text-secondary-foreground/60">{{ row.syncErrorMessage }}</span>
              </div>
            </div>
            <div class="flex flex-wrap gap-2 border-t pt-3">
              <FaButton v-if="row.type !== 'LOCAL' && !row.builtIn" v-auth="'platform:plugin-market-source:run'" variant="outline" size="sm" @click="confirmSync(row)">
                同步
              </FaButton>
              <FaButton v-auth="'platform:plugin-market-source:edit'" variant="outline" size="sm" @click="confirmToggle(row)">
                {{ row.enabled ? '禁用' : '启用' }}
              </FaButton>
              <FaButton v-auth="'platform:plugin-market-source:edit'" variant="link" size="sm" @click="openForm(row)">
                编辑
              </FaButton>
              <FaButton v-if="!row.builtIn" v-auth="'platform:plugin-market-source:delete'" variant="destructive" size="sm" @click="confirmRemove(row)">
                删除
              </FaButton>
            </div>
          </div>
        </FaCard>
      </template>
    </FaResponsiveTable>

    <div class="mt-10 flex flex-wrap items-center justify-between gap-3">
      <div class="min-w-0">
        <div class="text-base font-semibold">发布管理</div>
        <div class="mt-1 text-sm text-secondary-foreground/60">
          本机作为自托管市场源对外提供插件；其他实例把下方 v2 源基址添加为市场源即可订阅。
        </div>
      </div>
      <div class="flex flex-wrap items-center gap-2">
        <span class="text-sm text-secondary-foreground/60">发布需审核</span>
        <FaSwitch
          v-auth="'platform:plugin-market-source:edit'"
          :model-value="reviewRequired"
          :disabled="reviewSaving"
          @update:model-value="toggleReviewRequired"
        />
        <FaButton v-auth="'platform:plugin-market-source:upload'" @click="openUpload()">
          <FaIcon name="i-ri:upload-2-line" />
          上传插件
        </FaButton>
      </div>
    </div>

    <div class="mt-3 flex flex-wrap items-center gap-x-2 gap-y-1 rounded-lg border p-3 text-sm">
      <FaIcon name="i-ri:link" class="shrink-0" />
      <span class="shrink-0 text-secondary-foreground/60">对外 v2 源基址</span>
      <code class="min-w-0 break-all">{{ publicV2Url }}</code>
      <FaButton variant="link" size="sm" @click="copyPublicUrl">复制</FaButton>
      <span class="w-full text-xs text-secondary-foreground/60">
        添加源时类型选「v2 协议源」，地址填该基址。legacy 静态索引仍可用：{{ publicIndexUrl }}
      </span>
    </div>

    <div class="mt-3 flex items-center gap-2">
      <FaSelect v-model="pubStatusFilter" :options="publicationFilterOptions" class="w-40" />
    </div>

    <FaResponsiveTable
      v-loading="pubLoading"
      class="mt-2"
      row-key="id"
      table-root-class="rounded-lg overflow-hidden"
      table-class="min-w-[980px]"
      border
      stripe
      :columns="publicationColumns"
      :data="publicationRows"
    >
      <template #cell-plugin="{ row }">
        <div class="flex flex-col gap-1">
          <span class="font-medium">{{ row.original.displayName || row.original.code }}</span>
          <span class="text-xs text-secondary-foreground/60">{{ row.original.code }}</span>
        </div>
      </template>
      <template #cell-channel="{ row }">
        <FaTag variant="secondary">{{ channelText(row.original.channel) }}</FaTag>
      </template>
      <template #cell-sha256="{ row }">
        <span class="break-all font-mono text-xs" :title="row.original.sha256">{{ row.original.sha256.slice(0, 16) }}…</span>
      </template>
      <template #cell-size="{ row }">
        {{ formatSize(row.original.sizeBytes) }}
      </template>
      <template #cell-pubStatus="{ row }">
        <FaTag :variant="publicationStatusVariant(row.original.status)">
          {{ publicationStatusText(row.original.status) }}
        </FaTag>
        <div v-if="row.original.reviewNote" class="mt-1 max-w-[160px] truncate text-xs text-secondary-foreground/60" :title="row.original.reviewNote">
          {{ row.original.reviewNote }}
        </div>
      </template>
      <template #cell-pubOperation="{ row }">
        <div class="flex justify-center gap-2">
          <template v-if="row.original.status === 'PENDING'">
            <FaButton v-auth="'platform:plugin-market-source:accept'" variant="outline" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmAccept(row.original)">
              通过
            </FaButton>
            <FaButton v-auth="'platform:plugin-market-source:accept'" variant="destructive" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmReject(row.original)">
              拒绝
            </FaButton>
          </template>
          <FaButton
            v-if="row.original.status === 'PUBLISHED'"
            v-auth="'platform:plugin-market-source:delete'"
            variant="destructive"
            size="sm"
            :disabled="actingPublicationId === row.original.id"
            @click="confirmUnpublish(row.original)"
          >
            下架
          </FaButton>
          <span v-if="row.original.status === 'REJECTED' || row.original.status === 'REVOKED'" class="text-xs text-secondary-foreground/60">
            {{ formatTime(row.original.reviewedAt) }}
          </span>
        </div>
      </template>

      <template #card="{ row }">
        <FaCard class="w-full">
          <div class="flex flex-col gap-3">
            <div class="flex items-center justify-between gap-2">
              <span class="text-base font-semibold break-all">{{ row.displayName || row.code }} {{ row.pluginVersion }}</span>
              <FaTag :variant="publicationStatusVariant(row.status)">
                {{ publicationStatusText(row.status) }}
              </FaTag>
            </div>
            <div class="flex flex-col gap-1 text-sm">
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">通道</span>
                <span>{{ channelText(row.channel) }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">校验和</span>
                <span class="break-all font-mono text-xs">{{ row.sha256 }}</span>
              </div>
              <div class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">提交时间</span>
                <span>{{ formatTime(row.createTime) }}</span>
              </div>
              <div v-if="row.reviewNote" class="flex gap-2">
                <span class="shrink-0 text-secondary-foreground/60">备注</span>
                <span class="break-all">{{ row.reviewNote }}</span>
              </div>
            </div>
            <div class="flex flex-wrap gap-2 border-t pt-3">
              <template v-if="row.status === 'PENDING'">
                <FaButton v-auth="'platform:plugin-market-source:accept'" variant="outline" size="sm" :disabled="actingPublicationId === row.id" @click="confirmAccept(row)">
                  通过
                </FaButton>
                <FaButton v-auth="'platform:plugin-market-source:accept'" variant="destructive" size="sm" :disabled="actingPublicationId === row.id" @click="confirmReject(row)">
                  拒绝
                </FaButton>
              </template>
              <FaButton
                v-if="row.status === 'PUBLISHED'"
                v-auth="'platform:plugin-market-source:delete'"
                variant="destructive"
                size="sm"
                :disabled="actingPublicationId === row.id"
                @click="confirmUnpublish(row)"
              >
                下架
              </FaButton>
            </div>
          </div>
        </FaCard>
      </template>
    </FaResponsiveTable>

    <FaModal
      v-model="formVisible"
      :title="editing ? '编辑市场源' : '添加市场源'"
      show-cancel-button
      class="sm:max-w-2xl"
      :confirm-loading="formSaving"
      @confirm="saveForm"
    >
      <a-form :model="form" layout="vertical">
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-2">
          <a-form-item label="市场源标识">
            <FaInput v-model="form.code" :disabled="!!editing" placeholder="小写字母、数字或连字符，如 company-mirror" />
          </a-form-item>
          <a-form-item label="名称">
            <FaInput v-model="form.name" placeholder="市场源显示名称" />
          </a-form-item>
        </div>
        <a-form-item v-if="!editing?.builtIn" label="源类型">
          <FaSelect v-model="form.type" :options="sourceTypeOptions" :disabled="!!editing?.builtIn" />
        </a-form-item>
        <a-form-item v-if="editing?.builtIn" label="源类型">
          <FaInput model-value="本机源" disabled />
          <div class="mt-1 text-xs text-secondary-foreground/60">
            内置源固定为本机发布物，进程内直读，无需地址与同步。
          </div>
        </a-form-item>
        <a-form-item v-if="!editing?.builtIn" :label="form.type === 'V2_API' ? '源地址（v2 源基址）' : '源地址（index.json 完整地址）'">
          <div class="flex gap-2">
            <FaInput
              v-model="form.rootUrl"
              :placeholder="form.type === 'V2_API' ? 'https://example.com/api/public/plugin-market' : 'https://example.com/market/index.json'"
              class="flex-1"
            />
            <FaButton variant="outline" :loading="testing" @click="testForm">
              测试连接
            </FaButton>
          </div>
          <div v-if="testMessage" class="mt-1 flex items-center gap-1">
            <FaTag :variant="testOk ? 'default' : 'destructive'">{{ testOk ? '成功' : '失败' }}</FaTag>
            <span class="text-xs text-secondary-foreground/60">{{ testMessage }}</span>
          </div>
        </a-form-item>
        <div class="grid grid-cols-1 gap-x-4 md:grid-cols-[1fr_140px]">
          <a-form-item label="访问令牌（可选）">
            <FaInput v-model="form.token" type="password" :placeholder="editing?.tokenConfigured ? '已配置，留白表示保留' : '私有源的 Bearer 令牌'" />
          </a-form-item>
          <a-form-item label="排序">
            <a-input-number v-model="form.sortOrder" :min="0" class="w-full" />
          </a-form-item>
        </div>
      </a-form>
    </FaModal>

    <FaModal v-model="uploadVisible" title="上传插件到市场源" show-cancel-button class="sm:max-w-xl" :confirm-loading="uploading" @confirm="submitUpload">
      <a-form :model="{ notes: uploadNotes, file: uploadFile }" layout="vertical">
        <a-form-item label="插件 JAR">
          <input ref="fileInput" type="file" accept=".jar" class="hidden" @change="onUploadFileChange">
          <div class="flex items-center gap-2">
            <FaButton variant="outline" @click="fileInput?.click()">
              <FaIcon name="i-ri:file-add-line" />
              选择文件
            </FaButton>
            <span class="min-w-0 break-all text-sm text-secondary-foreground/60">{{ uploadFile?.name || '未选择' }}</span>
          </div>
          <div class="mt-1 text-xs text-secondary-foreground/60">
            元数据从 JAR 内 plugin.yml 自动解析；{{ reviewRequired ? '提交后进入待审核。' : '当前免审核，提交后直接对外可见。' }}
          </div>
        </a-form-item>
        <a-form-item label="发布说明（可选）">
          <FaTextarea v-model="uploadNotes" :rows="4" placeholder="本版本更新内容；换行会合并为空格（对外契约单行）" />
        </a-form-item>
      </a-form>
    </FaModal>
  </FaPageMain>
</template>
