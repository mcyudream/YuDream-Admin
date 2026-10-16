<script setup lang="ts">
import type { YdMarkdownEditorInstance, YdMarkdownImage, YdMarkdownImageUploadHandler, YdMarkdownToolbar } from '@yudream/components'
import { computed, ref } from 'vue'
import apiFiles from '@/api/modules/files'
import { toBackendAssetUrl } from '@/utils/backend-url'

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  toolbars?: YdMarkdownToolbar[]
  showToolbarName?: boolean
  catalogMaxDepth?: number
}>(), {
  modelValue: '',
  placeholder: '在这里编写 Markdown 内容...',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': [markdown: string]
}>()

const toast = useYdToast()
const markdownEditor = ref<YdMarkdownEditorInstance | null>(null)

function insert(generate: Parameters<YdMarkdownEditorInstance['insert']>[0]) {
  markdownEditor.value?.insert(generate)
}

function focus() {
  markdownEditor.value?.focus()
}

defineExpose({ insert, focus })

const editorValue = computed({
  get: () => props.modelValue || '',
  set: value => emit('update:modelValue', value),
})

const uploadMarkdownImages: YdMarkdownImageUploadHandler = async (files, callback) => {
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

async function uploadMarkdownImage(file: File): Promise<YdMarkdownImage> {
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
  <YdMarkdownEditor
    ref="markdownEditor"
    v-model="editorValue"
    :placeholder="placeholder"
    language="zh-CN"
    preview-theme="github"
    code-theme="github"
    height="640px"
    :toolbars="toolbars"
    :show-toolbar-name="showToolbarName"
    :catalog-max-depth="catalogMaxDepth"
    :image-upload="uploadMarkdownImages"
    @save="value => emit('save', value)"
  />
</template>
