<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { PluginMarketPublication, PluginPublicationStatus } from '@/api/modules/platform-plugin-market-source'
import apiMarketSource, { PLUGIN_PUBLICATION_STATUS_OPTIONS } from '@/api/modules/platform-plugin-market-source'
import { PLUGIN_MARKET_CATEGORIES } from '@/api/modules/plugin-market-public'
import { buildCiTemplateFiles } from './ci-templates'

interface PluginGroup {
  code: string
  displayName: string
  latest: PluginMarketPublication
  versions: PluginMarketPublication[]
}

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()

const loading = ref(false)
const loadFailed = ref(false)
const publishHelpOpen = ref(false)
const publications = ref<PluginMarketPublication[]>([])
const versionColumns: TableColumn<PluginMarketPublication>[] = [
  { accessorKey: 'pluginVersion', header: '版本', width: 120 },
  { id: 'status', header: '状态', width: 180 },
  { id: 'size', header: '大小', width: 100 },
  { id: 'created', header: '提交时间', width: 190 },
  { id: 'operation', header: '操作', width: 220, fixed: 'right' },
]
const pagination = reactive({ page: 1, size: 10, total: 0 })
const statusFilter = ref<PluginPublicationStatus | ''>('')
const reviewRequired = ref(true)
const skipReview = computed(() => auth('platform:plugin-market-source:publish'))
const marketOrigin = window.location.origin
const publicV2Url = `${marketOrigin}/api/public/plugin-market`
const ciTemplateVisible = ref(false)
const ciTemplateTab = ref('market-job')
const ciTemplateFiles = computed(() => buildCiTemplateFiles(marketOrigin))
const ciTemplateTabList = computed(() => ciTemplateFiles.value.map(file => ({
  label: file.label,
  value: file.id,
})))
const currentCiTemplate = computed(() => ciTemplateFiles.value.find(file => file.id === ciTemplateTab.value) || ciTemplateFiles.value[0])

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFile = ref<File | null>(null)
const fileInput = ref<InstanceType<typeof YdFilePicker> | null>(null)
const uploadForm = reactive({
  releaseNotes: '',
  category: '',
  tags: '',
  license: '',
  compatibility: '',
})

const editVisible = ref(false)
const editSaving = ref(false)
const editingPublication = ref<PluginMarketPublication | null>(null)
const editForm = reactive({
  displayName: '',
  description: '',
  releaseNotes: '',
  license: '',
  category: '',
  tags: '',
  compatibility: '',
})
const actingPublicationId = ref('')

const categoryOptions = computed(() => PLUGIN_MARKET_CATEGORIES.map(name => ({ label: name, value: name })))
const statusOptions = computed(() => [
  { label: '全部状态', value: '' },
  ...PLUGIN_PUBLICATION_STATUS_OPTIONS,
])

const groups = computed<PluginGroup[]>(() => {
  const map = new Map<string, PluginMarketPublication[]>()
  for (const item of publications.value) {
    const list = map.get(item.code) || []
    list.push(item)
    map.set(item.code, list)
  }
  return [...map.entries()].map(([code, versions]) => {
    const sorted = [...versions].sort((left, right) => (right.createTime || '').localeCompare(left.createTime || ''))
    const latest = sorted[0]
    return {
      code,
      displayName: latest.displayName || code,
      latest,
      versions: sorted,
    }
  })
})

onMounted(load)

async function load() {
  loading.value = true
  loadFailed.value = false
  try {
    const [pageRes, reviewRes] = await Promise.all([
      apiMarketSource.publications({
        mine: true,
        status: statusFilter.value || undefined,
        page: pagination.page,
        size: pagination.size,
      }),
      apiMarketSource.reviewRequired().catch(() => ({ data: true })),
    ])
    publications.value = pageRes.data.records || []
    pagination.total = pageRes.data.total || 0
    reviewRequired.value = reviewRes.data
  }
  catch {
    loadFailed.value = true
    toast.error('加载我的插件失败')
  }
  finally {
    loading.value = false
  }
}

function onPageChange() {
  void load()
}

function onSizeChange() {
  pagination.page = 1
  void load()
}

function onStatusChange() {
  pagination.page = 1
  void load()
}

function openUpload(group?: PluginGroup) {
  uploadFile.value = null
  uploadForm.releaseNotes = ''
  uploadForm.category = group?.latest.category || ''
  uploadForm.tags = (group?.latest.tags || []).join(', ')
  uploadForm.license = group?.latest.license || ''
  uploadForm.compatibility = group?.latest.compatibility && Object.keys(group.latest.compatibility).length
    ? JSON.stringify(group.latest.compatibility, null, 2)
    : ''
  uploadVisible.value = true
}

function onUploadFileChange(files: File[]) {
  uploadFile.value = files[0] || null
  fileInput.value?.reset()
}

function parseCompatibility(raw: string): Record<string, string> | undefined | false {
  if (!raw.trim()) {
    return undefined
  }
  try {
    const parsed = JSON.parse(raw.trim()) as unknown
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
      toast.error('兼容性必须是 JSON 对象，例如 {"host":">=2.16.0"}')
      return false
    }
    return Object.fromEntries(
      Object.entries(parsed as Record<string, unknown>).map(([key, value]) => [key, String(value)]),
    )
  }
  catch {
    toast.error('兼容性必须是合法 JSON')
    return false
  }
}

async function submitUpload() {
  if (!uploadFile.value) {
    toast.error('请选择插件 JAR')
    return
  }
  const compatibility = parseCompatibility(uploadForm.compatibility)
  if (compatibility === false) {
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', uploadFile.value)
    if (uploadForm.releaseNotes.trim()) {
      data.append('releaseNotes', uploadForm.releaseNotes.trim())
    }
    if (uploadForm.category) {
      data.append('category', uploadForm.category)
    }
    if (uploadForm.tags.trim()) {
      data.append('tags', uploadForm.tags.trim())
    }
    const metadata: Record<string, unknown> = {}
    if (uploadForm.license.trim()) {
      metadata.license = uploadForm.license.trim()
    }
    if (compatibility) {
      metadata.compatibility = compatibility
    }
    if (Object.keys(metadata).length) {
      data.append('metadata', JSON.stringify(metadata))
    }
    const res = await apiMarketSource.uploadPublication(data)
    uploadVisible.value = false
    toast.success(res.data.status === 'PUBLISHED' ? '已直接发布' : '已提交，等待审核后对外可见')
    await load()
  }
  finally {
    uploading.value = false
  }
}

function openEdit(row: PluginMarketPublication) {
  editingPublication.value = row
  editForm.displayName = row.displayName || ''
  editForm.description = row.description || ''
  editForm.releaseNotes = row.releaseNotes || ''
  editForm.license = row.license || ''
  editForm.category = row.category || ''
  editForm.tags = (row.tags || []).join(', ')
  editForm.compatibility = row.compatibility && Object.keys(row.compatibility).length
    ? JSON.stringify(row.compatibility, null, 2)
    : ''
  editVisible.value = true
}

async function submitEdit() {
  if (!editingPublication.value) {
    return
  }
  const compatibility = parseCompatibility(editForm.compatibility)
  if (compatibility === false) {
    return
  }
  editSaving.value = true
  try {
    await apiMarketSource.updatePublication(editingPublication.value.id, {
      displayName: editForm.displayName.trim(),
      description: editForm.description.trim(),
      releaseNotes: editForm.releaseNotes.trim(),
      license: editForm.license.trim(),
      category: editForm.category,
      tags: editForm.tags.split(/[,，]/).map(item => item.trim()).filter(Boolean),
      compatibility: compatibility ?? {},
    })
    editVisible.value = false
    toast.success('发布物已更新，无需重新审核')
    await load()
  }
  finally {
    editSaving.value = false
  }
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

async function copyText(text: string, success: string) {
  await navigator.clipboard.writeText(text)
  toast.success(success)
}

async function copyPublicUrl() {
  await copyText(publicV2Url, '已复制 v2 源基址')
}

async function copyMarketOrigin() {
  await copyText(marketOrigin, '已复制 YUDREAM_MARKET_URL')
}

function openCiTemplates() {
  ciTemplateTab.value = 'market-job'
  ciTemplateVisible.value = true
}

async function copyCurrentCiTemplate() {
  const file = currentCiTemplate.value
  await copyText(file.content, `已复制 ${file.filename}`)
}

async function copyAllCiTemplates() {
  const bundled = ciTemplateFiles.value
    .map(file => `# ===== ${file.filename} =====\n${file.content.replace(/\s+$/, '')}`)
    .join('\n\n')
  await copyText(`${bundled}\n`, '已复制全部 CI 模板文件')
}

function publicationStatusVariant(status: PluginPublicationStatus) {
  return status === 'PUBLISHED' ? 'default' : status === 'PENDING' ? 'secondary' : 'destructive'
}

function publicationStatusText(status: PluginPublicationStatus) {
  return PLUGIN_PUBLICATION_STATUS_OPTIONS.find(item => item.value === status)?.label || status
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '—'
}

function formatSize(bytes?: number) {
  if (!bytes) {
    return '—'
  }
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

const uploadHint = computed(() => {
  if (skipReview.value) {
    return '你拥有跳过审核权限，提交后将直接对外可见。'
  }
  return reviewRequired.value ? '当前开启审核，提交后进入待审核。' : '当前免审核，提交后直接对外可见。'
})
</script>

<template>
  <div>
    <YdPageHeader title="插件发布" class="mb-0">
      <template #description>
        管理你发布到本机市场源的插件：上传新版本、编辑分类标签，或通过 API Key 走流水线发布。
      </template>
      <YdButton variant="outline" @click="openCiTemplates">
        <YdIcon name="morph:code" />
        CI 模板
      </YdButton>
      <YdButton v-auth="'platform:plugin-market-source:upload'" @click="openUpload()">
        <YdIcon name="morph:upload" />
        上传插件
      </YdButton>
    </YdPageHeader>
    <YdPageMain>
      <div class="text-sm mb-3 px-3 py-2 border border-[var(--color-border-2)] rounded-lg bg-[var(--color-fill-1)] flex flex-wrap gap-2 items-center">
        <YdIcon name="morph:info" class="shrink-0" />
        <span class="flex-1 min-w-0">{{ uploadHint }} 同一插件版本不可覆盖。</span>
      </div>
      <YdCollapsible v-model="publishHelpOpen" class="mb-3 border border-[var(--color-border-2)] rounded-lg">
        <template #trigger="{ open }">
          <span class="text-sm px-3 py-2 text-left flex gap-2 w-full items-center justify-between">
            <span>流水线发布说明与源地址</span>
            <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
          </span>
        </template>
        <div class="text-sm text-secondary-foreground/60 px-3 py-3 border-t border-[var(--color-border-2)] space-y-2">
          <p>用具备 upload 权限的 API Key 走同一发布端点；升版上传新 JAR，分类与标签可事后编辑。</p>
          <div class="flex flex-wrap gap-2 items-center">
            <code class="text-xs min-w-0 break-all">{{ publicV2Url }}</code>
            <YdButton variant="link" size="sm" @click="copyPublicUrl">
              复制 v2 基址
            </YdButton>
          </div>
          <p>CI 模板包含 .gitlab-ci.yml、publish:market、上传脚本和 release/plugins.txt，可在页首查看并复制。</p>
        </div>
      </YdCollapsible>

      <div class="mb-3 flex flex-wrap gap-2 items-center justify-between">
        <YdSelect v-model="statusFilter" :options="statusOptions" class="w-full sm:w-44" aria-label="筛选发布状态" @update:model-value="onStatusChange" />
        <span class="text-xs text-secondary-foreground/60">共 {{ pagination.total }} 条发布记录</span>
      </div>

      <YdLoadingOverlay :loading="loading">
        <YdEmpty v-if="!loading && loadFailed" title="发布记录加载失败" description="请检查网络后重试。" illustration="error">
          <template #action>
            <YdButton variant="outline" size="sm" @click="load">
              重新加载
            </YdButton>
          </template>
        </YdEmpty>
        <YdEmpty v-else-if="!loading && !groups.length" :title="statusFilter ? '当前状态没有发布记录' : '还没有发布物'" :description="statusFilter ? '切换状态筛选查看其他版本。' : '上传插件 JAR，开始发布第一个版本。'" :illustration="statusFilter ? 'no-result' : 'empty'">
          <template #action>
            <YdButton v-if="!statusFilter" v-auth="'platform:plugin-market-source:upload'" size="sm" @click="openUpload()">
              上传插件
            </YdButton>
          </template>
        </YdEmpty>
        <div v-else class="gap-3 grid">
          <YdCard v-for="group in groups" :key="group.code" class="w-full">
            <div class="flex flex-wrap gap-3 items-start justify-between">
              <div class="min-w-0">
                <div class="flex flex-wrap gap-2 items-center">
                  <span class="text-base font-semibold">{{ group.displayName }}</span>
                  <YdTag variant="secondary">
                    {{ group.code }}
                  </YdTag>
                  <YdTag v-if="group.latest.category" variant="secondary">
                    {{ group.latest.category }}
                  </YdTag>
                </div>
                <div class="text-sm text-secondary-foreground/60 mt-1">
                  最新 {{ group.latest.pluginVersion }} · {{ publicationStatusText(group.latest.status) }} · {{ formatTime(group.latest.createTime) }}
                </div>
                <div v-if="group.latest.tags?.length" class="mt-2 flex flex-wrap gap-1">
                  <YdTag v-for="tag in group.latest.tags" :key="tag" variant="secondary">
                    {{ tag }}
                  </YdTag>
                </div>
              </div>
              <YdButton v-auth="'platform:plugin-market-source:upload'" variant="outline" size="sm" @click="openUpload(group)">
                升版上传
              </YdButton>
            </div>
            <YdResponsiveTable
              row-key="id"
              table-root-class="mt-3 rounded-lg overflow-hidden"
              table-class="min-w-[720px]"
              border
              :columns="versionColumns"
              :data="group.versions"
            >
              <template #cell-status="{ row }">
                <YdTag :variant="publicationStatusVariant(row.original.status)">
                  {{ publicationStatusText(row.original.status) }}
                </YdTag>
                <div v-if="row.original.reviewNote" class="text-xs text-secondary-foreground/60 mt-1 max-w-[240px] truncate" :title="row.original.reviewNote">
                  {{ row.original.reviewNote }}
                </div>
              </template>
              <template #cell-size="{ row }">
                {{ formatSize(row.original.sizeBytes) }}
              </template>
              <template #cell-created="{ row }">
                {{ formatTime(row.original.createTime) }}
              </template>
              <template #cell-operation="{ row }">
                <div class="flex flex-wrap gap-2">
                  <YdButton variant="link" size="sm" :disabled="actingPublicationId === row.original.id" @click="openEdit(row.original)">
                    编辑
                  </YdButton>
                  <YdButton v-if="row.original.status === 'PUBLISHED'" variant="destructive" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmUnpublish(row.original)">
                    下架
                  </YdButton>
                  <YdButton variant="destructive" size="sm" :disabled="actingPublicationId === row.original.id" @click="confirmDelete(row.original)">
                    删除
                  </YdButton>
                </div>
              </template>
              <template #card="{ row }">
                <YdCard class="w-full">
                  <div class="flex gap-2 items-center justify-between">
                    <strong class="break-all">{{ row.pluginVersion }}</strong>
                    <YdTag :variant="publicationStatusVariant(row.status)">
                      {{ publicationStatusText(row.status) }}
                    </YdTag>
                  </div>
                  <p v-if="row.reviewNote" class="text-xs text-secondary-foreground/60 mt-2 break-all">
                    {{ row.reviewNote }}
                  </p>
                  <div class="text-xs text-secondary-foreground/60 mt-2 flex flex-wrap gap-x-4 gap-y-1">
                    <span>大小 {{ formatSize(row.sizeBytes) }}</span><span>提交 {{ formatTime(row.createTime) }}</span>
                  </div>
                  <div class="mt-3 pt-3 border-t border-[var(--color-border-2)] flex flex-wrap gap-2">
                    <YdButton variant="link" size="sm" :disabled="actingPublicationId === row.id" @click="openEdit(row)">
                      编辑
                    </YdButton>
                    <YdButton v-if="row.status === 'PUBLISHED'" variant="destructive" size="sm" :disabled="actingPublicationId === row.id" @click="confirmUnpublish(row)">
                      下架
                    </YdButton>
                    <YdButton variant="destructive" size="sm" :disabled="actingPublicationId === row.id" @click="confirmDelete(row)">
                      删除
                    </YdButton>
                  </div>
                </YdCard>
              </template>
            </YdResponsiveTable>
          </YdCard>
        </div>
      </YdLoadingOverlay>

      <YdPagination
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :sizes="[10, 20, 50]"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onSizeChange"
      />

      <YdModal v-model="uploadVisible" title="上传插件" show-cancel-button class="sm:max-w-xl" :confirm-loading="uploading" @confirm="submitUpload">
        <YdForm :model="uploadForm" layout="vertical">
          <YdFormItem label="插件 JAR">
            <YdFilePicker ref="fileInput" accept=".jar" label="选择插件 JAR" class="hidden" @select="onUploadFileChange">
              <template #default />
            </YdFilePicker>
            <div class="flex gap-2 items-center">
              <YdButton variant="outline" @click="fileInput?.open()">
                <YdIcon name="morph:file-plus" />
                选择文件
              </YdButton>
              <span class="text-sm text-secondary-foreground/60 min-w-0 break-all">{{ uploadFile?.name || '未选择' }}</span>
            </div>
            <div class="text-xs text-secondary-foreground/60 mt-1">
              元数据从 JAR 内 plugin.yml 自动解析。{{ uploadHint }}
            </div>
          </YdFormItem>
          <YdFormItem label="分类（可选）">
            <YdSelect v-model="uploadForm.category" allow-clear :options="categoryOptions" placeholder="未分类" />
          </YdFormItem>
          <YdFormItem label="标签（可选，逗号分隔）">
            <YdInput v-model="uploadForm.tags" placeholder="如 chat, wiki" />
          </YdFormItem>
          <YdFormItem label="许可证（可选）">
            <YdInput v-model="uploadForm.license" placeholder="如 MIT、Apache-2.0" />
          </YdFormItem>
          <YdFormItem label="兼容性 JSON（可选）">
            <YdTextarea v-model="uploadForm.compatibility" :rows="3" placeholder="{&quot;host&quot;:&quot;>=2.16.0&quot;}" />
          </YdFormItem>
          <YdFormItem label="发布说明（可选）">
            <YdTextarea v-model="uploadForm.releaseNotes" :rows="4" placeholder="本版本更新内容；换行会合并为空格" />
          </YdFormItem>
        </YdForm>
      </YdModal>

      <YdModal v-model="editVisible" title="编辑发布物" show-cancel-button class="sm:max-w-xl" :confirm-loading="editSaving" @confirm="submitEdit">
        <YdForm :model="editForm" layout="vertical">
          <div class="text-xs text-secondary-foreground/60 mb-2">
            {{ editingPublication?.code }}@{{ editingPublication?.pluginVersion }} · 编辑展示信息与分类标签，不重置审核状态。
          </div>
          <YdFormItem label="显示名称">
            <YdInput v-model="editForm.displayName" placeholder="显示名称" />
          </YdFormItem>
          <YdFormItem label="描述">
            <YdTextarea v-model="editForm.description" :rows="3" placeholder="插件简介" />
          </YdFormItem>
          <YdFormItem label="分类">
            <YdSelect v-model="editForm.category" allow-clear :options="categoryOptions" placeholder="未分类" />
          </YdFormItem>
          <YdFormItem label="标签（逗号分隔）">
            <YdInput v-model="editForm.tags" placeholder="如 chat, wiki" />
          </YdFormItem>
          <YdFormItem label="许可证">
            <YdInput v-model="editForm.license" placeholder="如 MIT" />
          </YdFormItem>
          <YdFormItem label="发布说明">
            <YdTextarea v-model="editForm.releaseNotes" :rows="4" placeholder="本版本更新内容" />
          </YdFormItem>
          <YdFormItem label="兼容性 JSON（可选）">
            <YdTextarea v-model="editForm.compatibility" :rows="3" placeholder="{&quot;host&quot;:&quot;>=2.16.0&quot;}" />
          </YdFormItem>
        </YdForm>
      </YdModal>

      <YdModal
        v-model="ciTemplateVisible"
        title="流水线 CI 模板"
        class="sm:max-w-5xl"
        maximizable
        :footer="false"
      >
        <div class="text-sm space-y-3">
          <p class="text-secondary-foreground/60">
            把下列文件放进插件仓对应路径。CI 变量
            <code>YUDREAM_MARKET_URL</code>
            填宿主根地址（不要带
            <code>/api/public/plugin-market</code>
            ），
            <code>YUDREAM_MARKET_API_KEY</code>
            勾选
            <code>platform:plugin-market-source:upload</code>
            并设为受保护、掩码。job 只在受保护
            <code>v*</code>
            tag 上调度，按
            <code>release/plugins.txt</code>
            批量上传最终 JAR。
          </p>
          <div class="flex flex-wrap gap-2 items-center">
            <code class="text-xs min-w-0 break-all">YUDREAM_MARKET_URL={{ marketOrigin }}</code>
            <YdButton variant="link" size="sm" @click="copyMarketOrigin">
              复制变量值
            </YdButton>
            <YdButton variant="outline" size="sm" @click="copyCurrentCiTemplate">
              <YdIcon name="morph:copy" />
              复制当前文件
            </YdButton>
            <YdButton variant="outline" size="sm" @click="copyAllCiTemplates">
              复制全部文件
            </YdButton>
          </div>
          <YdTabs v-model="ciTemplateTab" :list="ciTemplateTabList" class="w-full" list-class="flex-wrap justify-start" content-class="mt-3">
            <template v-for="file in ciTemplateFiles" :key="file.id" #[file.id]>
              <div class="space-y-2">
                <div class="flex flex-wrap gap-2 items-center">
                  <code class="text-xs">{{ file.filename }}</code>
                  <span class="text-xs text-secondary-foreground/60">{{ file.description }}</span>
                </div>
                <div class="max-h-[60vh] overflow-auto">
                  <YdCode :code="file.content" :language="file.filename.endsWith('.yml') ? 'yaml' : file.filename.endsWith('.sh') ? 'bash' : 'plaintext'" :title="file.filename" />
                </div>
              </div>
            </template>
          </YdTabs>
        </div>
      </YdModal>
    </YdPageMain>
  </div>
</template>
