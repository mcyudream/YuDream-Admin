<script setup lang="ts">
import type { PluginMarketPublication, PluginPublicationStatus } from '@/api/modules/platform-plugin-market-source'
import { PLUGIN_PUBLICATION_STATUS_OPTIONS } from '@/api/modules/platform-plugin-market-source'
import { PLUGIN_MARKET_CATEGORIES } from '@/api/modules/plugin-market-public'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'

interface PluginGroup {
  code: string
  displayName: string
  latest: PluginMarketPublication
  versions: PluginMarketPublication[]
}

const modal = useFaModal()
const toast = useFaToast()
const { auth } = useAppAuth()

const loading = ref(false)
const publications = ref<PluginMarketPublication[]>([])
const pagination = reactive({ page: 1, size: 50, total: 0 })
const statusFilter = ref<PluginPublicationStatus | ''>('')
const reviewRequired = ref(true)
const skipReview = computed(() => auth('platform:plugin-market-source:publish'))
const publicV2Url = `${window.location.origin}/api/public/plugin-market`

const uploadVisible = ref(false)
const uploading = ref(false)
const uploadFile = ref<File | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
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
    toast.error('加载我的插件失败')
  }
  finally {
    loading.value = false
  }
}

function onPageChange() {
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

function onUploadFileChange(event: Event) {
  uploadFile.value = (event.target as HTMLInputElement).files?.[0] || null
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

async function copyPublicUrl() {
  await navigator.clipboard.writeText(publicV2Url)
  toast.success('已复制 v2 源基址')
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
    <FaPageHeader title="插件发布" class="mb-0">
      <template #description>
        管理你发布到本机市场源的插件：上传新版本、编辑分类标签，或通过 API Key 走流水线发布。
      </template>
      <FaButton v-auth="'platform:plugin-market-source:upload'" @click="openUpload()">
        <FaIcon name="i-ri:upload-2-line" />
        上传插件
      </FaButton>
    </FaPageHeader>
    <FaPageMain>
      <div class="mb-4 grid gap-3 md:grid-cols-2">
        <div class="rounded-lg border p-3 text-sm">
          <div class="font-medium">发布提示</div>
          <p class="mt-1 text-secondary-foreground/60">{{ uploadHint }}</p>
          <p class="mt-1 text-xs text-secondary-foreground/60">
            {code}@{version} 不可覆盖。升版请上传新 JAR；分类与标签可事后编辑。
          </p>
        </div>
        <div class="rounded-lg border p-3 text-sm">
          <div class="font-medium">流水线发布</div>
          <p class="mt-1 text-secondary-foreground/60">
            用 API Key（勾选 upload）调用同一端点。模板：
            <code>templates/plugin-repo/ci/publish-to-market.sh</code>
            与
            <code>.gitlab-ci.yml.example</code>
            的
            <code>publish:market</code>
            job。
          </p>
          <div class="mt-2 flex flex-wrap items-center gap-2">
            <code class="min-w-0 break-all text-xs">{{ publicV2Url }}</code>
            <FaButton variant="link" size="sm" @click="copyPublicUrl">复制基址</FaButton>
          </div>
        </div>
      </div>

      <div class="mb-3 flex flex-wrap items-center gap-2">
        <FaSelect v-model="statusFilter" :options="statusOptions" class="w-40" @update:model-value="onStatusChange" />
      </div>

      <a-spin :loading="loading" class="block w-full">
        <div v-if="!groups.length && !loading" class="rounded-lg border p-10 text-center text-sm text-secondary-foreground/60">
          还没有你的发布物。点击右上角上传插件 JAR。
        </div>
        <div v-else class="grid gap-3">
          <FaCard v-for="group in groups" :key="group.code" class="w-full">
            <div class="flex flex-wrap items-start justify-between gap-3">
              <div class="min-w-0">
                <div class="flex flex-wrap items-center gap-2">
                  <span class="text-base font-semibold">{{ group.displayName }}</span>
                  <FaTag variant="secondary">{{ group.code }}</FaTag>
                  <FaTag v-if="group.latest.category" variant="secondary">{{ group.latest.category }}</FaTag>
                </div>
                <div class="mt-1 text-sm text-secondary-foreground/60">
                  最新 {{ group.latest.pluginVersion }} · {{ publicationStatusText(group.latest.status) }} · {{ formatTime(group.latest.createTime) }}
                </div>
                <div v-if="group.latest.tags?.length" class="mt-2 flex flex-wrap gap-1">
                  <FaTag v-for="tag in group.latest.tags" :key="tag" variant="secondary">{{ tag }}</FaTag>
                </div>
              </div>
              <FaButton v-auth="'platform:plugin-market-source:upload'" variant="outline" size="sm" @click="openUpload(group)">
                升版上传
              </FaButton>
            </div>
            <div class="mt-3 overflow-x-auto">
              <table class="w-full min-w-[720px] text-left text-sm">
                <thead class="text-secondary-foreground/60">
                  <tr>
                    <th class="py-2 font-normal">版本</th>
                    <th class="py-2 font-normal">状态</th>
                    <th class="py-2 font-normal">大小</th>
                    <th class="py-2 font-normal">提交时间</th>
                    <th class="py-2 font-normal">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in group.versions" :key="row.id" class="border-t">
                    <td class="py-2">{{ row.pluginVersion }}</td>
                    <td class="py-2">
                      <FaTag :variant="publicationStatusVariant(row.status)">{{ publicationStatusText(row.status) }}</FaTag>
                      <div v-if="row.reviewNote" class="mt-1 max-w-[240px] truncate text-xs text-secondary-foreground/60" :title="row.reviewNote">
                        {{ row.reviewNote }}
                      </div>
                    </td>
                    <td class="py-2">{{ formatSize(row.sizeBytes) }}</td>
                    <td class="py-2">{{ formatTime(row.createTime) }}</td>
                    <td class="py-2">
                      <div class="flex flex-wrap gap-2">
                        <FaButton variant="link" size="sm" :disabled="actingPublicationId === row.id" @click="openEdit(row)">
                          编辑
                        </FaButton>
                        <FaButton
                          v-if="row.status === 'PUBLISHED'"
                          variant="destructive"
                          size="sm"
                          :disabled="actingPublicationId === row.id"
                          @click="confirmUnpublish(row)"
                        >
                          下架
                        </FaButton>
                        <FaButton variant="destructive" size="sm" :disabled="actingPublicationId === row.id" @click="confirmDelete(row)">
                          删除
                        </FaButton>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </FaCard>
        </div>
      </a-spin>

      <FaPagination
        v-if="pagination.total > pagination.size"
        v-model:page="pagination.page"
        v-model:size="pagination.size"
        :total="pagination.total"
        :sizes="[20, 50, 100]"
        class="mt-3"
        @page-change="onPageChange"
        @size-change="onPageChange"
      />

      <FaModal v-model="uploadVisible" title="上传插件" show-cancel-button class="sm:max-w-xl" :confirm-loading="uploading" @confirm="submitUpload">
        <a-form :model="uploadForm" layout="vertical">
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
              元数据从 JAR 内 plugin.yml 自动解析。{{ uploadHint }}
            </div>
          </a-form-item>
          <a-form-item label="分类（可选）">
            <FaSelect v-model="uploadForm.category" allow-clear :options="categoryOptions" placeholder="未分类" />
          </a-form-item>
          <a-form-item label="标签（可选，逗号分隔）">
            <FaInput v-model="uploadForm.tags" placeholder="如 chat, wiki" />
          </a-form-item>
          <a-form-item label="许可证（可选）">
            <FaInput v-model="uploadForm.license" placeholder="如 MIT、Apache-2.0" />
          </a-form-item>
          <a-form-item label="兼容性 JSON（可选）">
            <FaTextarea v-model="uploadForm.compatibility" :rows="3" placeholder='{"host":">=2.16.0"}' />
          </a-form-item>
          <a-form-item label="发布说明（可选）">
            <FaTextarea v-model="uploadForm.releaseNotes" :rows="4" placeholder="本版本更新内容；换行会合并为空格" />
          </a-form-item>
        </a-form>
      </FaModal>

      <FaModal v-model="editVisible" title="编辑发布物" show-cancel-button class="sm:max-w-xl" :confirm-loading="editSaving" @confirm="submitEdit">
        <a-form :model="editForm" layout="vertical">
          <div class="mb-2 text-xs text-secondary-foreground/60">
            {{ editingPublication?.code }}@{{ editingPublication?.pluginVersion }} · 编辑展示信息与分类标签，不重置审核状态。
          </div>
          <a-form-item label="显示名称">
            <FaInput v-model="editForm.displayName" placeholder="显示名称" />
          </a-form-item>
          <a-form-item label="描述">
            <FaTextarea v-model="editForm.description" :rows="3" placeholder="插件简介" />
          </a-form-item>
          <a-form-item label="分类">
            <FaSelect v-model="editForm.category" allow-clear :options="categoryOptions" placeholder="未分类" />
          </a-form-item>
          <a-form-item label="标签（逗号分隔）">
            <FaInput v-model="editForm.tags" placeholder="如 chat, wiki" />
          </a-form-item>
          <a-form-item label="许可证">
            <FaInput v-model="editForm.license" placeholder="如 MIT" />
          </a-form-item>
          <a-form-item label="发布说明">
            <FaTextarea v-model="editForm.releaseNotes" :rows="4" placeholder="本版本更新内容" />
          </a-form-item>
          <a-form-item label="兼容性 JSON（可选）">
            <FaTextarea v-model="editForm.compatibility" :rows="3" placeholder='{"host":">=2.16.0"}' />
          </a-form-item>
        </a-form>
      </FaModal>
    </FaPageMain>
  </div>
</template>
