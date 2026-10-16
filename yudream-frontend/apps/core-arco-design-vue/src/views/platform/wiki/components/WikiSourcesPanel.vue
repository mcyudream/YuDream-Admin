<script setup lang="ts">
import type { YdFilePickerInstance } from '@yudream/components'
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
const expandedSource = ref('')

const sortedSources = computed(() => [...sources.value].sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0)))

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

function pickFile() {
  fileInput.value?.open()
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
        <!-- 导入工具栏 -->
        <YdCard class="src-import">
          <div class="src-import__grid">
            <label class="src-field">
              <span><YdIcon name="morph:folder" /> 目标目录</span>
              <YdInput v-model="folderPath" placeholder="/" />
            </label>
            <div class="src-field">
              <span><YdIcon name="morph:cloud-upload" /> 上传文件</span>
              <div>
                <YdButton variant="outline" :loading="uploading" @click="pickFile">
                  <YdIcon name="morph:upload" /> 选择文件上传
                </YdButton>
                <YdFilePicker ref="fileInput" label="选择资料文件" @select="onFileChange">
                  <template #default />
                </YdFilePicker>
              </div>
            </div>
            <div class="src-field">
              <span><YdIcon name="i-ri:markdown-line" /> 在线文档</span>
              <div>
                <YdButton variant="outline" @click="openTextEditor()">
                  <YdIcon name="morph:feather" /> 新建 Markdown 文档
                </YdButton>
              </div>
            </div>
            <label class="src-field src-field--urls">
              <span><YdIcon name="morph:link" /> 批量 URL 导入（每行一个）</span>
              <YdTextarea v-model="urlsText" :rows="3" placeholder="https://example.com/docs/a&#10;https://example.com/docs/b" />
            </label>
            <div class="src-field src-field--action">
              <YdButton :loading="importing" @click="importUrls">
                <YdIcon name="morph:cloud-download" /> 导入 URL
              </YdButton>
            </div>
          </div>
        </YdCard>

        <!-- 资料列表 -->
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
        <div v-else-if="sortedSources.length" class="src-list">
          <YdCard v-for="source in pagedSources" :key="source.id" class="src-card">
            <div class="src-card__head">
              <div class="src-card__title">
                <YdIcon :name="source.kind === 'URL' ? 'morph:globe' : source.kind === 'TEXT' ? 'i-ri:markdown-line' : 'morph:file'" class="src-card__kind-icon" />
                <strong>{{ source.title }}</strong>
                <span class="src-card__path">{{ source.folderPath }} · {{ source.format || source.mimeType }}</span>
              </div>
              <div class="src-card__badges">
                <span class="src-badge" :style="{ color: extractionStatusLabel(source.extractionStatus).color, background: `${extractionStatusLabel(source.extractionStatus).color}1a` }">
                  抽取 · {{ extractionStatusLabel(source.extractionStatus).label }}
                </span>
                <span class="src-badge" :style="{ color: ingestStatusLabel(source.ingestStatus).color, background: `${ingestStatusLabel(source.ingestStatus).color}1a` }">
                  摄入 · {{ ingestStatusLabel(source.ingestStatus).label }}
                </span>
              </div>
            </div>

            <p v-if="source.extractionError || source.ingestError" class="src-card__error">
              <YdIcon name="morph:triangle-alert" /> {{ source.extractionError || source.ingestError }}
            </p>

            <div v-if="(source.images || []).length" class="src-card__images">
              <YdButton
                v-for="(img, index) in source.images.slice(0, expandedSource === source.id ? source.images.length : 6)"
                :key="img.fileObjectId"
                variant="ghost"
                type="button"
                class="src-thumb"
                :title="img.caption || `第 ${img.pageNumber} 页图片`"
                :aria-label="img.caption || `预览第 ${img.pageNumber} 页图片`"
                @click="previewImage(source, index)"
              >
                <img v-if="img.url" :src="resolveApiFileUrl(img.url)" :alt="img.caption || '资料图片'" loading="lazy">
                <span v-else class="src-thumb__placeholder"><YdIcon name="morph:image" /></span>
                <span v-if="img.caption" class="src-thumb__caption">{{ img.caption }}</span>
              </YdButton>
              <YdButton
                v-if="source.images.length > 6"
                variant="ghost"
                type="button"
                class="src-thumb src-thumb--more"
                :aria-expanded="expandedSource === source.id"
                @click="expandedSource = expandedSource === source.id ? '' : source.id"
              >
                {{ expandedSource === source.id ? '收起' : `+${source.images.length - 6}` }}
              </YdButton>
            </div>

            <div class="src-card__footer">
              <span class="src-card__count"><YdIcon name="morph:image" /> {{ (source.images || []).length }} 张图片</span>
              <span v-if="source.url" class="src-card__url">{{ source.url }}</span>
              <div class="src-card__actions">
                <YdButton v-if="source.kind === 'TEXT'" size="sm" variant="outline" @click="openTextEditor(source)">
                  <YdIcon name="morph:pencil" /> 编辑
                </YdButton>
                <YdButton size="sm" @click="ingest(source)">
                  <YdIcon name="morph:play" /> 摄入
                </YdButton>
                <YdButton size="sm" variant="outline" :disabled="!(source.images || []).length" @click="caption(source)">
                  <YdIcon name="morph:sparkles" /> 补 caption
                </YdButton>
                <YdButton size="sm" variant="ghost" class="src-danger" @click="removeSource(source)">
                  <YdIcon name="morph:trash-2" /> 删除
                </YdButton>
              </div>
            </div>
          </YdCard>
        </div>
        <YdPagination
          v-if="sortedSources.length > pageSize"
          v-model:page="page"
          v-model:size="pageSize"
          :total="sortedSources.length"
          class="src-pagination"
        />
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

.src-import__grid {
  display: grid;
  grid-template-columns: 160px auto auto minmax(280px, 1fr) auto;
  gap: 14px;
  align-items: end;
}

.src-field {
  display: grid;
  gap: 6px;
  align-content: start;
}

.src-field > span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: var(--color-text-3);
  font-size: 12px;
}

.src-field--action {
  align-self: end;
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

.src-list {
  display: grid;
  gap: 12px;
}

.src-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.src-card__title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.src-card__kind-icon {
  color: var(--color-text-3);
  font-size: 17px;
}

.src-card__title strong {
  font-size: 14px;
}

.src-card__path {
  color: var(--color-text-3);
  font-size: 12px;
}

.src-card__badges {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.src-badge {
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  white-space: nowrap;
}

.src-card__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: 8px;
  background: #ef444412;
  color: #dc2626;
  font-size: 12px;
}

.src-card__images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.src-thumb {
  position: relative;
  width: 96px;
  height: 72px;
  padding: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-fill-2);
  cursor: zoom-in;
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
  color: var(--color-text-3);
}

.src-thumb__caption {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  overflow: hidden;
  padding: 2px 5px;
  background: rgb(0 0 0 / 55%);
  color: #fff;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.src-thumb--more {
  display: grid;
  place-items: center;
  color: var(--color-text-3);
  cursor: pointer;
  font-size: 12px;
}

.src-card__footer {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}

.src-card__count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.src-card__url {
  overflow: hidden;
  max-width: 360px;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.src-card__actions {
  display: flex;
  gap: 8px;
  margin-left: auto;
}

.src-danger {
  color: #dc2626;
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

/* 在线文档编辑器高度由 WikiMarkdownEditor 的 height 属性控制（YdMarkdownEditor 不透传 class，类选择器无效） */

.src-text-hint {
  margin: 0;
  color: var(--color-text-3);
  font-size: 12px;
}

@media (max-width: 1100px) {
  .src-import__grid {
    grid-template-columns: 1fr 1fr;
  }
}

/* 窄屏：导入区真单列，卡片操作换行到独立一行，长文编辑区让位给视口 */
@media (max-width: 768px) {
  .src-inner {
    padding: 12px;
  }

  .src-import__grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .src-card__actions {
    width: 100%;
    margin-left: 0;
    flex-wrap: wrap;
  }

  .src-card__url {
    max-width: 100%;
  }
}
</style>
