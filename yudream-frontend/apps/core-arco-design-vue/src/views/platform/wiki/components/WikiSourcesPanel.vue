<script setup lang="ts">
import type { ActionMenuItem, TableColumn, YdFilePickerInstance } from '@yudream/components'
import type { WikiSource } from '@/api/modules/platform-wiki'
import { computed, inject, onMounted, ref, watch } from 'vue'
import {
  captionWikiSourceImages,
  createWikiTextSource,
  deleteWikiSource,
  enqueueWikiIngest,
  fetchWikiSources,
  importWikiUrls,
  updateWikiTextSource,
  uploadWikiSource,
} from '@/api/modules/platform-wiki'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { resolveApiFileUrl, rewriteApiFileUrls } from '@/utils/api-file-url'
import { extractionStatusLabel, ingestStatusLabel, wikiWorkbenchKey } from '../wiki-utils'
import WikiMarkdownEditor from './WikiMarkdownEditor.vue'

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()
const imagePreview = useYdImagePreview()
const aiRuntime = useAiAssistantStore()
const captionRunId = ref('')
const captionProgressOpen = ref(false)

const sources = ref<WikiSource[]>([])
const loading = ref(false)
const loadError = ref('')
const folderPath = ref('/')
const urlsText = ref('')
const importing = ref(false)
const uploading = ref(false)
const fileInput = ref<YdFilePickerInstance | null>(null)

const sortedSources = computed(() => [...sources.value].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0)))

// 表格列：资料（标题 + 目录）｜类型｜处理状态（抽取/摄入 + 失败原因）｜图片；细节动作收在行尾菜单
const columns: TableColumn<WikiSource>[] = [
  { accessorKey: 'title', header: '资料', minWidth: 260 },
  { id: 'format', header: '类型', width: 110 },
  { id: 'status', header: '处理状态', width: 210 },
  { id: 'images', header: '图片', width: 150 },
]

const actions: ActionMenuItem<WikiSource>[] = [
  { label: '摄入', icon: 'morph:play', onClick: source => ingest(source) },
  { label: '编辑内容', icon: 'morph:pencil', visible: source => source.kind === 'TEXT', onClick: source => openTextEditor(source) },
  { label: '补 caption', icon: 'morph:sparkles', visible: source => Boolean((source.images || []).length), onClick: source => caption(source) },
  { label: '删除', icon: 'morph:trash-2', danger: true, onClick: source => removeSource(source) },
]

function sourceKindIcon(source: WikiSource): string {
  if (source.kind === 'URL') {
    return 'morph:globe'
  }
  return source.kind === 'TEXT' ? 'morph:feather' : 'morph:file'
}

/** 处理状态：抽取与摄入并列展示，失败原因仍跟在状态后面 */
function sourceStatuses(source: WikiSource) {
  const extraction = extractionStatusLabel(source.extractionStatus)
  const ingest = ingestStatusLabel(source.ingestStatus)
  return [
    { prefix: '抽取', label: extraction.label, color: extraction.color },
    { prefix: '摄入', label: ingest.label, color: ingest.color },
  ]
}

// 客户端分页（资料可能很多，避免一次性长列表）
const page = ref(1)
const pageSize = ref(10)
const pagedSources = computed(() => {
  const start = (page.value - 1) * pageSize.value
  return sortedSources.value.slice(start, start + pageSize.value)
})

async function load() {
  if (!store.spaceId.value) {
    sources.value = []
    loadError.value = ''
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await fetchWikiSources(store.spaceId.value)
    sources.value = res.data || []
    page.value = 1
  }
  catch (exception) {
    // 保留错误与重试入口，不用空列表掩盖失败
    loadError.value = exception instanceof Error ? exception.message : '资料加载失败，请稍后重试'
  }
  finally {
    loading.value = false
  }
}

async function onFileChange(files: File[]) {
  const file = files[0]
  fileInput.value?.reset()
  if (!file || !store.spaceId.value) {
    return
  }
  uploading.value = true
  try {
    await uploadWikiSource(store.spaceId.value, folderPath.value || '/', file)
    toast.success(`「${file.name}」已导入，开始抽取`)
    await load()
  }
  finally {
    uploading.value = false
  }
}

async function importUrls() {
  const urls = urlsText.value.split('\n').map(item => item.trim()).filter(Boolean)
  if (!store.spaceId.value || !urls.length) {
    toast.warning('请粘贴至少一个 URL')
    return
  }
  importing.value = true
  try {
    await importWikiUrls(store.spaceId.value, { folderPath: folderPath.value || '/', urls })
    toast.success(`已导入 ${urls.length} 个 URL`)
    urlsText.value = ''
    await load()
  }
  finally {
    importing.value = false
  }
}

function removeSource(source: WikiSource) {
  modal.confirm({
    title: '删除资料',
    content: `确定删除资料「${source.title}」？将级联清理它生成的 Wiki 页面。`,
    onConfirm: async () => {
      await deleteWikiSource(source.id)
      toast.success('已删除')
      await load()
      await store.reloadTree()
    },
  })
}

async function caption(source: WikiSource) {
  if (!(source.images || []).length) {
    toast.warning('该资料没有图片')
    return
  }
  const response = await captionWikiSourceImages(source.id)
  aiRuntime.attachRun(response.data)
  captionRunId.value = response.data.id
  captionProgressOpen.value = true
  toast.success('图片描述任务已启动，关闭面板后继续执行')
}

watch(() => aiRuntime.runs[captionRunId.value]?.status, (status) => {
  if (status === 'COMPLETED') {
    void load()
  }
})

async function ingest(source: WikiSource) {
  if (!store.spaceId.value) {
    return
  }
  await enqueueWikiIngest(store.spaceId.value, source.id)
  toast.success('已加入摄入队列')
  store.openPanel('ingest')
}

function previewImage(source: WikiSource, index: number) {
  const urls = (source.images || []).map(img => resolveApiFileUrl(img.url)).filter(Boolean)
  if (urls.length) {
    imagePreview.open(urls, index)
  }
}

// 在线 Markdown 文档：新建 / 编辑
const textEditorVisible = ref(false)
const textSaving = ref(false)
const textEditingId = ref('')
const textTitle = ref('')
const textContent = ref('')

function openTextEditor(source?: WikiSource) {
  textEditingId.value = source?.id || ''
  textTitle.value = source?.title || ''
  // 开发环境预览需要 /proxy 前缀；保存时后端会把 /proxy/api/files 规范化回 /api/files
  textContent.value = rewriteApiFileUrls(source?.extractedText || '')
  textEditorVisible.value = true
}

async function saveTextSource() {
  if (!store.spaceId.value) {
    return
  }
  if (!textTitle.value.trim()) {
    toast.warning('请填写资料标题')
    return
  }
  if (!textContent.value.trim()) {
    toast.warning('请编写资料内容')
    return
  }
  textSaving.value = true
  try {
    if (textEditingId.value) {
      await updateWikiTextSource(textEditingId.value, { title: textTitle.value.trim(), content: textContent.value })
      toast.success('已保存，重新摄入中')
    }
    else {
      await createWikiTextSource(store.spaceId.value, {
        folderPath: folderPath.value || '/',
        title: textTitle.value.trim(),
        content: textContent.value,
      })
      toast.success('在线文档已创建，开始摄入')
    }
    textEditorVisible.value = false
    await load()
  }
  finally {
    textSaving.value = false
  }
}

onMounted(load)
watch(() => store.spaceId.value, load)
</script>

<template>
  <div class="src-panel">
    <YdScrollArea class="src-scroll">
      <div class="src-inner">
        <!-- 导入区：第一行定位置与上传/新建，第二行批量 URL（输入与动作对齐，按钮不再挤成一条） -->
        <section class="src-import" aria-label="导入资料">
          <div class="src-import__row">
            <label class="src-field src-field--folder">
              <span><YdIcon name="morph:folder" /> 目标目录</span>
              <YdInput v-model="folderPath" placeholder="/" aria-label="目标目录" />
            </label>
            <div class="src-field">
              <span><YdIcon name="morph:cloud-upload" /> 上传文件</span>
              <YdFilePicker ref="fileInput" label="选择资料文件" @select="onFileChange">
                <template #default="{ open }">
                  <YdButton variant="outline" :loading="uploading" @click="open">
                    <YdIcon name="morph:upload" /> 选择文件上传
                  </YdButton>
                </template>
              </YdFilePicker>
            </div>
            <div class="src-field">
              <span><YdIcon name="morph:feather" /> 在线文档</span>
              <YdButton variant="outline" @click="openTextEditor()">
                <YdIcon name="morph:feather" /> 新建 Markdown 文档
              </YdButton>
            </div>
          </div>
          <div class="src-import__row src-import__row--urls">
            <label class="src-field src-field--urls">
              <span><YdIcon name="morph:link" /> 批量 URL 导入（每行一个）</span>
              <YdTextarea v-model="urlsText" :rows="3" placeholder="https://example.com/docs/a&#10;https://example.com/docs/b" aria-label="批量导入 URL" />
            </label>
            <YdButton :loading="importing" @click="importUrls">
              <YdIcon name="morph:cloud-download" /> 导入 URL
            </YdButton>
          </div>
        </section>

        <!-- 资料表：自适应表格，状态与图片直接进列，行尾菜单收细节动作 -->
        <div v-if="loadError" class="src-error">
          <YdAlert variant="destructive" title="资料加载失败" :description="loadError" />
          <YdButton variant="outline" size="sm" :loading="loading" @click="load">
            <YdIcon name="morph:rotate-cw" /> 重新加载
          </YdButton>
        </div>
        <div v-if="loading && !sortedSources.length" class="src-loading">
          <YdLoading :size="32" text="正在加载资料…" />
        </div>
        <YdEmpty
          v-else-if="!sortedSources.length && !loadError"
          icon="morph:database"
          title="暂无资料"
          description="上传文件或导入 URL，系统会自动抽取文本与图片"
        />
        <template v-else-if="sortedSources.length">
          <YdResponsiveEnhancedTable
            row-key="id"
            stripe
            border
            column-visibility
            table-class="min-w-[860px]"
            :columns="columns"
            :actions="actions"
            :data="pagedSources"
          >
            <template #cell-title="{ row }">
              <div class="src-cell">
                <div class="src-cell__title">
                  <YdIcon :name="sourceKindIcon(row.original)" class="src-cell__icon" />
                  <strong>{{ row.original.title }}</strong>
                </div>
                <span class="src-cell__path" :title="`${row.original.folderPath} · ${row.original.kind}`">
                  {{ row.original.folderPath }} · {{ row.original.kind }}
                </span>
              </div>
            </template>
            <template #cell-format="{ row }">
              <span class="src-cell__meta">{{ row.original.format || row.original.mimeType || '-' }}</span>
            </template>
            <template #cell-status="{ row }">
              <div class="src-cell__status">
                <span v-for="item in sourceStatuses(row.original)" :key="item.prefix" class="src-status">
                  <span class="src-status__dot" :style="{ background: item.color }" />
                  {{ item.prefix }} · {{ item.label }}
                </span>
                <span
                  v-if="row.original.extractionError || row.original.ingestError"
                  class="src-status__error"
                  :title="row.original.extractionError || row.original.ingestError"
                >
                  <YdIcon name="morph:triangle-alert" />
                  {{ row.original.extractionError || row.original.ingestError }}
                </span>
              </div>
            </template>
            <template #cell-images="{ row }">
              <div v-if="(row.original.images || []).length" class="src-thumbs">
                <YdButton
                  v-for="(img, index) in row.original.images.slice(0, 3)"
                  :key="img.fileObjectId"
                  variant="ghost"
                  type="button"
                  class="src-thumb"
                  :title="img.caption || `第 ${img.pageNumber} 页图片`"
                  :aria-label="img.caption || `预览第 ${img.pageNumber} 页图片`"
                  @click="previewImage(row.original, index)"
                >
                  <img v-if="img.url" :src="resolveApiFileUrl(img.url)" :alt="img.caption || '资料图片'" loading="lazy">
                  <span v-else class="src-thumb__placeholder"><YdIcon name="morph:image" /></span>
                </YdButton>
                <span v-if="row.original.images.length > 3" class="src-thumbs__more">+{{ row.original.images.length - 3 }}</span>
              </div>
              <span v-else class="src-cell__meta">无</span>
            </template>
          </YdResponsiveEnhancedTable>
          <YdPagination
            v-if="sortedSources.length > pageSize"
            v-model:page="page"
            v-model:size="pageSize"
            :total="sortedSources.length"
            class="src-pagination"
          />
        </template>
      </div>
    </YdScrollArea>

    <!-- 在线 Markdown 文档编辑器 -->
    <YdModal
      v-model="textEditorVisible"
      :title="textEditingId ? '编辑在线文档' : '新建在线文档'"
      class="w-[min(960px,92vw)]"
      content-class="max-h-[min(70vh,720px)]"
      show-cancel-button
      :confirm-button-loading="textSaving"
      @confirm="saveTextSource"
    >
      <div class="src-text-form">
        <YdInput v-model="textTitle" placeholder="资料标题（展示在资料列表与引用中）" />
        <WikiMarkdownEditor v-model="textContent" aria-label="资料内容编辑" height="min(52vh, 520px)" />
        <p class="src-text-hint">
          保存后自动进入摄入队列，生成对应的 Wiki 页面；内容变更会触发重新摄入。
        </p>
      </div>
    </YdModal>
    <YdDrawer v-model="captionProgressOpen" title="图片描述的实际 AI 进度" :footer="false" content-class="w-[760px] max-w-full">
      <AiRunProgress v-if="captionRunId" :run-id="captionRunId" />
    </YdDrawer>
  </div>
</template>

<style scoped>
.src-panel {
  height: 100%;
  min-height: 0;
  background: var(--color-fill-1);
}

.src-scroll {
  height: 100%;
}

.src-inner {
  display: grid;
  gap: 14px;
  padding: 16px;
}

.src-import {
  display: grid;
  gap: 12px;
  padding: 14px 16px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

/* 导入区两行：位置 + 上传/新建一行，批量 URL 一行；按钮与输入底部对齐 */
.src-import__row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  align-items: flex-end;
}

.src-field {
  display: grid;
  gap: 6px;
  align-content: start;
}

.src-field > span {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.src-field--folder {
  width: 180px;
}

.src-field--urls {
  flex: 1 1 320px;
  min-width: 0;
}

/* YdAlert 没有默认插槽：重试动作必须与告警相邻，不能作为子节点传入 */
.src-error {
  display: grid;
  gap: 8px;
}

.src-loading {
  display: flex;
  justify-content: center;
  padding: 56px 0;
}

/* —— 资料表单元格 —— */
.src-cell {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.src-cell__title {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.src-cell__title strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.src-cell__icon {
  flex-shrink: 0;
  font-size: 15px;
  color: var(--color-text-3);
}

.src-cell__path,
.src-cell__meta {
  overflow: hidden;
  font-size: 12px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.src-cell__status {
  display: grid;
  gap: 3px;
  min-width: 0;
}

.src-status {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.src-status__dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.src-status__error {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  overflow: hidden;
  max-width: 100%;
  font-size: 11px;
  color: rgb(var(--danger-6));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.src-thumbs {
  display: flex;
  gap: 6px;
  align-items: center;
}

.src-thumb {
  position: relative;
  width: 40px;
  height: 32px;
  padding: 0;
  overflow: hidden;
  cursor: zoom-in;
  background: var(--color-fill-2);
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
}

.src-thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.src-thumb__placeholder {
  display: grid;
  height: 100%;
  place-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.src-thumbs__more {
  font-size: 12px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}

.src-pagination {
  display: flex;
  justify-content: flex-end;
}

/* 在线文档长文弹窗：正文区高度受视口约束并内部滚动（content-class 用全局工具类），编辑区自身也为视口内高度 */
.src-text-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}

.src-text-hint {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
}

@media (max-width: 900px) {
  .src-import__row {
    flex-direction: column;
    align-items: stretch;
  }

  .src-field--folder {
    width: 100%;
  }
}

@media (max-width: 768px) {
  .src-inner {
    padding: 12px;
  }
}
</style>
