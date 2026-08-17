<script setup lang="ts">
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
import CmsMarkdownEditor from '../../cms/components/CmsMarkdownEditor.vue'
import { extractionStatusLabel, ingestStatusLabel, wikiWorkbenchKey } from '../wiki-utils'

const store = inject(wikiWorkbenchKey)!
const toast = useFaToast()
const modal = useFaModal()
const imagePreview = useFaImagePreview()

const sources = ref<WikiSource[]>([])
const loading = ref(false)
const folderPath = ref('/')
const urlsText = ref('')
const importing = ref(false)
const uploading = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
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
    return
  }
  loading.value = true
  try {
    const res = await fetchWikiSources(store.spaceId.value)
    sources.value = res.data || []
    page.value = 1
  }
  finally {
    loading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
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
  await captionWikiSourceImages(source.id)
  toast.success('图片描述已重新生成')
  await load()
}

async function ingest(source: WikiSource) {
  if (!store.spaceId.value) {
    return
  }
  await enqueueWikiIngest(store.spaceId.value, source.id)
  toast.success('已加入摄入队列')
  store.openPanel('ingest')
}

function previewImage(source: WikiSource, index: number) {
  const urls = (source.images || []).map(img => img.url || '').filter(Boolean)
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
  textContent.value = source?.extractedText || ''
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
    <FaScrollArea class="src-scroll">
      <div class="src-inner">
        <!-- 导入工具栏 -->
        <FaCard class="src-import">
          <div class="src-import__grid">
            <label class="src-field">
              <span><FaIcon name="i-ri:folder-line" /> 目标目录</span>
              <FaInput v-model="folderPath" placeholder="/" />
            </label>
            <div class="src-field">
              <span><FaIcon name="i-ri:upload-cloud-2-line" /> 上传文件</span>
              <div>
                <FaButton variant="outline" :loading="uploading" @click="pickFile">
                  <FaIcon name="i-ri:upload-2-line" /> 选择文件上传
                </FaButton>
                <input ref="fileInput" type="file" hidden @change="onFileChange">
              </div>
            </div>
            <div class="src-field">
              <span><FaIcon name="i-ri:markdown-line" /> 在线文档</span>
              <div>
                <FaButton variant="outline" @click="openTextEditor()">
                  <FaIcon name="i-ri:quill-pen-line" /> 新建 Markdown 文档
                </FaButton>
              </div>
            </div>
            <label class="src-field src-field--urls">
              <span><FaIcon name="i-ri:link" /> 批量 URL 导入（每行一个）</span>
              <FaTextarea v-model="urlsText" :rows="3" placeholder="https://example.com/docs/a&#10;https://example.com/docs/b" />
            </label>
            <div class="src-field src-field--action">
              <FaButton :loading="importing" @click="importUrls">
                <FaIcon name="i-ri:download-cloud-2-line" /> 导入 URL
              </FaButton>
            </div>
          </div>
        </FaCard>

        <!-- 资料列表 -->
        <div v-if="loading && !sources.length" class="src-loading">
          <FaIcon name="i-ri:loader-4-line" class="src-spin" /> 加载资料中…
        </div>
        <div v-else-if="!sortedSources.length" class="src-empty">
          <FaIcon name="i-ri:database-2-line" />
          <strong>暂无资料</strong>
          <p>上传文件或导入 URL，系统会自动抽取文本与图片</p>
        </div>
        <div v-else class="src-list">
          <FaCard v-for="source in pagedSources" :key="source.id" class="src-card">
            <div class="src-card__head">
              <div class="src-card__title">
                <FaIcon :name="source.kind === 'URL' ? 'i-ri:global-line' : source.kind === 'TEXT' ? 'i-ri:markdown-line' : 'i-ri:file-2-line'" class="src-card__kind-icon" />
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
              <FaIcon name="i-ri:error-warning-line" /> {{ source.extractionError || source.ingestError }}
            </p>

            <div v-if="(source.images || []).length" class="src-card__images">
              <button
                v-for="(img, index) in source.images.slice(0, expandedSource === source.id ? source.images.length : 6)"
                :key="img.fileObjectId"
                type="button"
                class="src-thumb"
                :title="img.caption || `第 ${img.pageNumber} 页图片`"
                @click="previewImage(source, index)"
              >
                <img v-if="img.url" :src="img.url" :alt="img.caption || '资料图片'" loading="lazy">
                <span v-else class="src-thumb__placeholder"><FaIcon name="i-ri:image-line" /></span>
                <span v-if="img.caption" class="src-thumb__caption">{{ img.caption }}</span>
              </button>
              <button
                v-if="source.images.length > 6"
                type="button"
                class="src-thumb src-thumb--more"
                @click="expandedSource = expandedSource === source.id ? '' : source.id"
              >
                {{ expandedSource === source.id ? '收起' : `+${source.images.length - 6}` }}
              </button>
            </div>

            <div class="src-card__footer">
              <span class="src-card__count"><FaIcon name="i-ri:image-line" /> {{ (source.images || []).length }} 张图片</span>
              <span v-if="source.url" class="src-card__url">{{ source.url }}</span>
              <div class="src-card__actions">
                <FaButton v-if="source.kind === 'TEXT'" size="sm" variant="outline" @click="openTextEditor(source)">
                  <FaIcon name="i-ri:edit-line" /> 编辑
                </FaButton>
                <FaButton size="sm" @click="ingest(source)">
                  <FaIcon name="i-ri:play-line" /> 摄入
                </FaButton>
                <FaButton size="sm" variant="outline" :disabled="!(source.images || []).length" @click="caption(source)">
                  <FaIcon name="i-ri:sparkling-2-line" /> 补 caption
                </FaButton>
                <FaButton size="sm" variant="ghost" class="src-danger" @click="removeSource(source)">
                  <FaIcon name="i-ri:delete-bin-line" /> 删除
                </FaButton>
              </div>
            </div>
          </FaCard>
        </div>
        <FaPagination
          v-if="sortedSources.length > pageSize"
          v-model:page="page"
          v-model:size="pageSize"
          :total="sortedSources.length"
          class="src-pagination"
        />
      </div>
    </FaScrollArea>

    <!-- 在线 Markdown 文档编辑器 -->
    <FaModal
      v-model="textEditorVisible"
      :title="textEditingId ? '编辑在线文档' : '新建在线文档'"
      class="w-[min(960px,92vw)]"
      show-cancel-button
      :confirm-button-loading="textSaving"
      @confirm="saveTextSource"
    >
      <div class="src-text-form">
        <FaInput v-model="textTitle" placeholder="资料标题（展示在资料列表与引用中）" />
        <CmsMarkdownEditor v-model="textContent" class="src-text-editor" />
        <p class="src-text-hint">
          保存后自动进入摄入队列，生成对应的 Wiki 页面；内容变更会触发重新摄入。
        </p>
      </div>
    </FaModal>
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

.src-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--color-text-3);
}

.src-spin {
  animation: src-rotate 1s linear infinite;
}

@keyframes src-rotate {
  to { transform: rotate(360deg); }
}

.src-empty {
  display: grid;
  justify-items: center;
  gap: 8px;
  padding: 60px 0;
  color: var(--color-text-3);
}

.src-empty :deep(svg) {
  color: rgb(var(--primary-6));
  font-size: 36px;
}

.src-empty strong {
  color: var(--color-text-1);
}

.src-empty p {
  margin: 0;
  font-size: 12px;
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
  color: rgb(var(--primary-6));
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

.src-text-form {
  display: grid;
  gap: 12px;
}

.src-text-editor {
  min-height: 380px;
}

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
</style>
