<script setup lang="ts">
import type { FileObject } from '@/api/modules/files'
import { getPageBuilder, usePageBuilderModal } from '@myissue/vue-website-page-builder'
import apiFiles from '@/api/modules/files'
import { toBackendAssetUrl } from '@/utils/backend-url'

const toast = useFaToast()
const { closeMediaLibraryModal } = usePageBuilderModal()

const loading = ref(false)
const uploading = ref(false)
const media = ref<FileObject[]>([])
const fileInput = ref<HTMLInputElement>()
const search = reactive({ keyword: '', page: 1, size: 24, total: 0 })

onMounted(loadMedia)

async function loadMedia() {
  loading.value = true
  try {
    const res = await apiFiles.page({
      page: search.page,
      size: search.size,
      keyword: search.keyword || undefined,
      module: 'cms',
      publicAccess: true,
    })
    media.value = res.data.records
    search.total = res.data.total
  }
  finally {
    loading.value = false
  }
}

function pickFile() {
  fileInput.value?.click()
}

async function uploadMedia(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (!file.type.startsWith('image/')) {
    toast.error('请选择图片文件')
    return
  }
  uploading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('module', 'cms')
    data.append('publicAccess', 'true')
    await apiFiles.upload(data)
    toast.success('素材已上传')
    await loadMedia()
  }
  finally {
    uploading.value = false
  }
}

async function selectMedia(item: FileObject) {
  const url = toBackendAssetUrl(item.url)
  if (!url) {
    toast.error('素材地址无效')
    return
  }
  await getPageBuilder().applySelectedImage({ src: url })
  await navigator.clipboard?.writeText(url)
  toast.success('图片已应用，地址已复制')
  closeMediaLibraryModal()
}

function fileSizeText(size?: number) {
  if (!size) {
    return '-'
  }
  if (size < 1024 * 1024) {
    return `${Math.round(size / 1024)} KB`
  }
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
</script>

<template>
  <section class="cms-media-library">
    <div class="media-toolbar">
      <FaInput v-model="search.keyword" clearable placeholder="搜索素材名称" @keydown.enter="loadMedia" @clear="loadMedia" />
      <FaButton variant="outline" :loading="loading" @click="loadMedia">
        <FaIcon name="i-ri:refresh-line" />
      </FaButton>
      <FaButton :loading="uploading" @click="pickFile">
        <FaIcon name="i-ri:upload-cloud-2-line" />
        上传
      </FaButton>
      <input ref="fileInput" type="file" accept="image/*" hidden @change="uploadMedia">
    </div>

    <div v-if="loading" class="media-state">
      加载素材中...
    </div>
    <div v-else-if="!media.length" class="media-state">
      暂无素材，上传一张图片开始构建页面。
    </div>
    <div v-else class="media-grid">
      <button v-for="item in media" :key="item.id" type="button" class="media-item" @click="selectMedia(item)">
        <img :src="toBackendAssetUrl(item.url)" :alt="item.originalName || 'CMS 素材'">
        <span>{{ item.originalName || `素材 ${item.id}` }}</span>
        <small>{{ fileSizeText(item.size) }}</small>
      </button>
    </div>
  </section>
</template>

<style scoped>
.cms-media-library {
  display: grid;
  gap: 14px;
}

.media-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  max-height: 560px;
  overflow: auto;
}

.media-item {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 8px;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  background: var(--color-bg-2);
  color: var(--color-text-1);
  text-align: left;
}

.media-item:hover {
  border-color: rgb(var(--primary-6));
}

.media-item img {
  width: 100%;
  aspect-ratio: 4 / 3;
  border-radius: 6px;
  background: var(--color-fill-2);
  object-fit: cover;
}

.media-item span,
.media-item small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.media-item small,
.media-state {
  color: var(--color-text-3);
}

.media-state {
  display: grid;
  min-height: 180px;
  place-items: center;
}

@media (max-width: 820px) {
  .media-toolbar,
  .media-grid {
    grid-template-columns: 1fr;
  }
}
</style>
