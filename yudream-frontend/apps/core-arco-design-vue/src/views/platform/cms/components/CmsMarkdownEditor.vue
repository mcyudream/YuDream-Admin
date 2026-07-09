<script setup lang="ts">
import type { UploadImgEvent } from 'md-editor-v3'
import apiFiles from '@/api/modules/files'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { MdEditor } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
}>(), {
  modelValue: '',
  placeholder: '在这里编写 Markdown 内容...',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const toast = useFaToast()

interface MarkdownUploadImage {
  url: string
  alt: string
  title: string
}

const editorValue = computed({
  get: () => props.modelValue || '',
  set: value => emit('update:modelValue', value),
})

const handleUploadImg: UploadImgEvent = async (files, callback) => {
  if (!files.length) {
    callback([])
    return
  }
  try {
    const urls = await Promise.all(files.map(uploadMarkdownImage))
    callback(urls)
    toast.success('图片已上传')
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '图片上传失败')
    callback([])
  }
}

async function uploadMarkdownImage(file: File): Promise<MarkdownUploadImage> {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  const data = new FormData()
  data.append('file', file)
  data.append('module', 'cms')
  data.append('publicAccess', 'true')
  const res = await apiFiles.upload(data)
  const url = toBackendAssetUrl(res.data.url)
  if (!url) {
    throw new Error('图片上传后未返回访问地址')
  }
  return {
    url,
    alt: res.data.originalName || file.name,
    title: res.data.originalName || file.name,
  }
}
</script>

<template>
  <section class="markdown-editor">
    <MdEditor
      v-model="editorValue"
      language="zh-CN"
      preview-theme="github"
      code-theme="github"
      :placeholder="placeholder"
      :style="{ height: '640px' }"
      @on-upload-img="handleUploadImg"
    />
  </section>
</template>

<style scoped>
.markdown-editor {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.markdown-editor :deep(.md-editor) {
  border: 0;
  background: var(--color-bg-1);
}

.markdown-editor :deep(.md-editor-toolbar-wrapper) {
  border-bottom-color: var(--color-border-2);
}

.markdown-editor :deep(.md-editor-input),
.markdown-editor :deep(.md-editor-preview-wrapper) {
  background: var(--color-bg-1);
}

@media (max-width: 1100px) {
  .markdown-editor :deep(.md-editor) {
    min-height: 560px;
  }
}
</style>
