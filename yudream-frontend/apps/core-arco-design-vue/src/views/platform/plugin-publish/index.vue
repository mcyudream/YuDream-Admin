<script setup lang="ts">
import type { ActionMenuItem, SearchOptionsSource, TableColumn, YdTablePageAction } from '@yudream/components'
import type { PluginMarketPublication } from '@/api/modules/platform-plugin-market-source'
import { useTablePage } from '@yudream/components'
import apiMarketSource, { PLUGIN_PUBLICATION_STATUS_OPTIONS } from '@/api/modules/platform-plugin-market-source'
import { PLUGIN_MARKET_CATEGORIES } from '@/api/modules/plugin-market-public'
import { fetchSearchConfig } from '@/api/modules/system-search-config'
import { formatPublicationSize, formatPublicationTime, publicationStatusText, publicationStatusVariant, publicationTitle } from '../plugin-market-source/publication-format'
import { buildCiTemplateFiles } from './ci-templates'

const modal = useYdModal()
const toast = useYdToast()
const { auth } = useAppAuth()

const publishHelpOpen = ref(false)
const reviewRequired = ref(true)
const skipReview = computed(() => auth('platform:plugin-market-source:publish'))
const marketOrigin = window.location.origin
const publicV2Url = `${marketOrigin}/api/public/plugin-market`

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
  { id: 'publication', header: '发布物', width: 280, fixed: 'left' },
  { accessorKey: 'pluginVersion', header: '版本', width: 110 },
  { accessorKey: 'category', header: '分类', width: 120 },
  { id: 'tags', header: '标签', width: 220, defaultHidden: true },
  { id: 'pubStatus', header: '状态', width: 170 },
  { id: 'size', header: '大小', width: 110, align: 'right' },
  { accessorKey: 'downloadCount', header: '下载', width: 90, align: 'right', defaultHidden: true },
  { accessorKey: 'createTime', header: '提交时间', width: 160 },
]

/**
 * 作者视角的发布物列表：只取自己的发布（mine），服务端分页。
 * 早前按插件分组渲染时，同一插件的版本会被分页切开、重复出现，且每行操作按钮挤在表格里。
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
      mine: true,
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
  loadSearchConfig: queryClass => fetchSearchConfig(queryClass).then(res => res.data),
})

/** 行操作：升版上传与编辑是作者高频动作，下架与删除按状态与权限出现。 */
const publicationActions: ActionMenuItem<PluginMarketPublication>[] = [
  {
    label: '升版上传',
    icon: 'morph:upload',
    visible: () => auth('platform:plugin-market-source:upload'),
    onClick: row => openEditMetaUpload(row),
  },
  {
    label: '编辑信息',
    icon: 'morph:pencil',
    visible: () => auth('platform:plugin-market-source:upload'),
    onClick: openEdit,
  },
  {
    label: '下架',
    icon: 'morph:archive',
    danger: true,
    visible: row => row.status === 'PUBLISHED' && auth('platform:plugin-market-source:upload'),
    onClick: confirmUnpublish,
  },
  {
    label: '删除',
    icon: 'morph:trash-2',
    danger: true,
    visible: () => auth('platform:plugin-market-source:upload'),
    onClick: confirmDelete,
  },
]

const pageActions = computed<YdTablePageAction[]>(() => [
  {
    label: '发布说明',
    icon: 'morph:info',
    variant: 'outline' as const,
    autoFold: true,
    onClick: () => {
      publishHelpOpen.value = true
    },
  },
  { label: 'CI 模板', icon: 'morph:code', variant: 'outline' as const, autoFold: true, onClick: openCiTemplates },
  ...(auth('platform:plugin-market-source:upload')
    ? [{ label: '上传插件', icon: 'morph:upload', onClick: () => openUpload() }]
    : []),
])

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

const categoryOptions = computed(() => PLUGIN_MARKET_CATEGORIES.map(name => ({ label: name, value: name })))

const uploadHint = computed(() => {
  if (skipReview.value) {
    return '你拥有跳过审核权限，提交后将直接对外可见。'
  }
  return reviewRequired.value ? '当前开启审核，提交后进入待审核。' : '当前免审核，提交后直接对外可见。'
})

onMounted(loadReviewRequired)

async function loadReviewRequired() {
  try {
    const res = await apiMarketSource.reviewRequired()
    reviewRequired.value = res.data
  }
  catch {
    // 拦截器已提示；保留默认文案，不影响上传
  }
}

/** 升版上传：从现有发布物带出分类、标签、许可证与兼容性，只换 JAR 与版本号 */
function openEditMetaUpload(row: PluginMarketPublication) {
  uploadFile.value = null
  uploadForm.releaseNotes = ''
  uploadForm.category = row.category || ''
  uploadForm.tags = (row.tags || []).join(', ')
  uploadForm.license = row.license || ''
  uploadForm.compatibility = row.compatibility && Object.keys(row.compatibility).length
    ? JSON.stringify(row.compatibility, null, 2)
    : ''
  uploadVisible.value = true
}

function openUpload() {
  uploadFile.value = null
  uploadForm.releaseNotes = ''
  uploadForm.category = ''
  uploadForm.tags = ''
  uploadForm.license = ''
  uploadForm.compatibility = ''
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
</script>

<template>
  <div>
    <!-- 单根页面容器：路由 out-in 过渡要求页面组件有元素根。多根模板（YdModal 等与布局并列）在 dev 下编译成 fragment，
         离开页面时 afterLeave 丢失、isLeaving 卡死，之后所有导航白屏 -->
    <YdTablePageLayout
      title="插件发布"
      :actions="pageActions"
      :actions-threshold="3"
    >
      <!-- 页头只放只读情报：审核口径与版本不可覆盖说明 -->
      <template #page-title>
        <span class="text-sm text-muted-foreground">{{ uploadHint }} 同一插件版本不可覆盖。</span>
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
            table-class="min-w-[1040px]"
            column-visibility
            :actions="publicationActions"
            :columns="tableColumns"
            :data="rows"
          >
            <template #empty>
              <span class="text-muted-foreground">
                {{ searchQuery.keyword || searchQuery.status ? '当前条件下没有发布记录，试试调整筛选' : '还没有发布物，上传插件 JAR 开始发布第一个版本' }}
              </span>
            </template>

            <template #cell-publication="{ row }">
              <div class="publication-identity">
                <span class="publication-identity__name">{{ publicationTitle(row.original) }}</span>
                <span class="publication-identity__meta">{{ row.original.code }}</span>
              </div>
            </template>

            <template #cell-tags="{ row }">
              <div class="publication-tags">
                <YdTag v-for="tag in row.original.tags || []" :key="tag" variant="secondary">
                  {{ tag }}
                </YdTag>
                <span v-if="!row.original.tags?.length" class="text-muted-foreground">—</span>
              </div>
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

            <template #cell-size="{ row }">
              {{ formatPublicationSize(row.original.sizeBytes) }}
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

    <!-- 流水线发布说明：不常用，收在操作栏的「更多」里 -->
    <YdModal
      v-model="publishHelpOpen"
      title="流水线发布说明与源地址"
      :footer="false"
      class="sm:max-w-xl"
    >
      <div class="text-sm text-secondary-foreground/60 space-y-2">
        <p>用具备 upload 权限的 API Key 走同一发布端点；升版上传新 JAR，分类与标签可事后编辑。</p>
        <div class="flex flex-wrap gap-2 items-center">
          <code class="text-xs min-w-0 break-all">{{ publicV2Url }}</code>
          <YdCopyButton :text="publicV2Url" label="复制 v2 基址" />
        </div>
        <p>CI 模板包含 .gitlab-ci.yml、publish:market、上传脚本和 release/plugins.txt，在上方「CI 模板」里查看并复制。</p>
      </div>
    </YdModal>

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
          <code>v*</code> tag 上调度，按
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
  </div>
</template>

<style scoped>
.publication-identity { display: grid; gap: 3px; min-width: 0; }
.publication-identity__name { color: var(--color-text-1); font-weight: 600; overflow-wrap: anywhere; }
.publication-identity__meta { color: var(--color-text-3); font-size: 11.5px; overflow-wrap: anywhere; }

.publication-tags { display: flex; flex-wrap: wrap; gap: 4px; }

.publication-status { display: grid; gap: 4px; justify-items: start; min-width: 0; }
.publication-status__note { display: block; max-width: 200px; overflow: hidden; color: var(--color-text-3); font-size: 11.5px; text-overflow: ellipsis; white-space: nowrap; }
</style>
