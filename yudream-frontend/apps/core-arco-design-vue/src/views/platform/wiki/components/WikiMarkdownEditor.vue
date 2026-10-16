<script setup lang="ts">
import type { YdMarkdownImage, YdMarkdownImageUploadHandler, YdMarkdownInsert, YdMarkdownLinkClick, YdMarkdownToolbar } from '@yudream/components'
import { YdMarkdownEditor } from '@yudream/components'
import { computed, isRef, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { authenticatedWikiImageUrl, releaseWikiImageBlobs, uploadWikiImage } from '../wiki-images'
import { wikiFileContentPath } from '../wiki-utils'

/**
 * Wiki 专用 Markdown 编辑器：在 YdMarkdownEditor 之上只做两件 Wiki 的事。
 *
 * 1. 上传走 Wiki 专用通道（module=wiki-image，**固定私有** publicAccess=false），
 *    不复用 theme-center 的 CmsMarkdownEditor（它固定 publicAccess=true，会泄露私有库图片）。
 *    不按空间公开状态推导可见性：公开库里的未发布草稿图片也会因此匿名可读。
 * 2. 私有图片用登录态取回并换成本地 blob 地址预览：img 标签无法携带鉴权头，
 *    这样既不用把图片改成匿名可读，作者也能在编辑器与预览里看到真实图片。
 */
const props = withDefaults(defineProps<{
  modelValue?: string
  toolbars?: YdMarkdownToolbar[]
  showToolbarName?: boolean
  catalogMaxDepth?: number
  readonly?: boolean
  height?: string
  placeholder?: string
  /** 只读预览的可访问名称（YdMarkdownEditor 的 ariaLabel） */
  ariaLabel?: string
}>(), {
  modelValue: '',
  height: '420px',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'save': [markdown: string]
  'linkClick': [payload: YdMarkdownLinkClick]
}>()

const toast = useYdToast()
const editorRef = ref<InstanceType<typeof YdMarkdownEditor> | null>(null)

function insert(generate: YdMarkdownInsert) {
  editorRef.value?.insert(generate)
}

function focus() {
  editorRef.value?.focus()
}

defineExpose({ insert, focus })

/** YdMarkdownEditor 通过 defineExpose 暴露 element：类型上是 Ref，实例代理会按 ref 解包，两种形态都兼容 */
function editorElement(): HTMLElement | null {
  const exposed = editorRef.value as unknown as { element?: HTMLElement | { value?: HTMLElement | null } | null } | null
  const raw = exposed?.element
  if (!raw) {
    return null
  }
  if (isRef(raw)) {
    return (raw.value as HTMLElement | null | undefined) ?? null
  }
  return raw as HTMLElement
}

const editorValue = computed({
  get: () => props.modelValue || '',
  set: value => emit('update:modelValue', value),
})

const uploadImages: YdMarkdownImageUploadHandler = async (files, callback) => {
  if (!files.length) {
    callback([])
    return
  }
  try {
    const images = await Promise.all(files.map((file): Promise<YdMarkdownImage> => uploadWikiImage(file)))
    callback(images)
    toast.success('图片已上传', { description: '图片按私有文件保存，仅登录后可读' })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '图片上传失败')
    callback([])
  }
}

// —— 私有图片预览：登录态取回 → blob 地址 ——
let observer: MutationObserver | undefined
let scheduled = false

function scheduleImageResolve() {
  if (scheduled) {
    return
  }
  scheduled = true
  requestAnimationFrame(() => {
    scheduled = false
    void resolveImages()
  })
}

async function resolveImages() {
  const container = editorElement()
  if (!container) {
    return
  }
  const images = container.querySelectorAll<HTMLImageElement>('img[src]')
  for (const image of images) {
    const path = wikiFileContentPath(image.getAttribute('src') || '')
    if (!path || image.dataset.wikiFile === path) {
      continue
    }
    // 标记在解析前：避免同一张图被并发重复请求，也避免 src 改写触发二次处理
    image.dataset.wikiFile = path
    try {
      const blobUrl = await authenticatedWikiImageUrl(path)
      if (image.isConnected && image.dataset.wikiFile === path) {
        image.src = blobUrl
      }
    }
    catch {
      // 无权限或已删除：保留原始地址，显示浏览器默认失败态，不伪造图片
    }
  }
}

function observeEditor() {
  observer?.disconnect()
  observer = undefined
  const container = editorElement()
  if (!container) {
    return
  }
  // 图片一律私有，必须用登录态解析后才能在工作台显示（img 无法携带鉴权头）
  observer = new MutationObserver(() => scheduleImageResolve())
  observer.observe(container, { childList: true, subtree: true, attributes: true, attributeFilter: ['src'] })
  scheduleImageResolve()
}

onMounted(() => {
  void nextTick(() => observeEditor())
})
watch(() => props.modelValue, () => scheduleImageResolve())
onBeforeUnmount(() => {
  observer?.disconnect()
  observer = undefined
  releaseWikiImageBlobs()
})
</script>

<template>
  <YdMarkdownEditor
    ref="editorRef"
    v-model="editorValue"
    class="wiki-markdown-editor"
    :height="height"
    :placeholder="placeholder"
    :toolbars="toolbars"
    :show-toolbar-name="showToolbarName"
    :catalog-max-depth="catalogMaxDepth"
    :readonly="readonly"
    :aria-label="ariaLabel"
    :image-upload="uploadImages"
    @save="value => emit('save', value)"
    @link-click="payload => emit('linkClick', payload)"
  />
</template>
