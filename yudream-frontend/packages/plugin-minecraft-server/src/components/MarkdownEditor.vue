<script setup lang="ts">
import type { UploadImgEvent } from 'md-editor-v3'
import { useFaToast } from '@fantastic-admin/components'
import { MdEditor } from 'md-editor-v3'
import { computed } from 'vue'

type UploadImageResult = string | {
  url: string
  alt?: string
  title?: string
}

interface MarkdownUploadImage {
  url: string
  alt: string
  title: string
}

const props = defineProps<{
  modelValue: string
  uploadImage?: (file: File) => Promise<UploadImageResult>
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const toast = useFaToast()

const editorValue = computed({
  get: () => props.modelValue || '',
  set: value => emit('update:modelValue', value),
})

const handleUploadImg: UploadImgEvent = async (files, callback) => {
  if (!props.uploadImage) {
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
  if (!props.uploadImage) {
    throw new Error('当前插件未接入图片上传')
  }
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  const result = await props.uploadImage(file)
  if (typeof result === 'string') {
    return {
      url: result,
      alt: file.name,
      title: file.name,
    }
  }
  return {
    url: result.url,
    alt: result.alt || file.name,
    title: result.title || result.alt || file.name,
  }
}
</script>

<template>
  <div class="mc-markdown">
    <MdEditor
      v-model="editorValue"
      language="zh-CN"
      preview-theme="github"
      code-theme="github"
      :no-upload-img="!uploadImage"
      :style="{ height: '460px' }"
      @on-upload-img="handleUploadImg"
    />
  </div>
</template>
