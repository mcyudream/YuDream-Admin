<script setup lang="ts">
import axios from 'axios'
import { filesize } from 'filesize'
import { computed, onMounted, onUnmounted, ref, useTemplateRef } from 'vue'
import ButtonGroup from '../button-group/index.vue'
import Button from '../button/index.vue'
import Icon from '../icon/index.vue'

defineOptions({
  name: 'BuiltInFileUpload',
})

const props = withDefaults(defineProps<{
  action?: string
  method?: string
  headers?: Headers | Record<string, any>
  data?: Record<string, any>
  name?: string
  afterUpload?: (response: any) => string | Promise<string>
  beforeUpload?: (file: File) => boolean | Promise<boolean>
  httpRequest?: (options: FileUploadRequestOptions) => any | Promise<any>
  multiple?: boolean
  max?: number
  directory?: boolean
  disabled?: boolean
  description?: string
}>(), {
  action: '',
  method: 'post',
  headers: () => ({}),
  data: () => ({}),
  name: 'file',
  multiple: false,
  max: 0,
  directory: false,
  disabled: false,
  description: '拖放或点击上传',
})

const emits = defineEmits<{
  onSuccess: [response: any, file: File]
  onClick: [fileItem: FileItem, index: number]
}>()

const fileList = defineModel<FileItem[]>('modelValue', { required: true })

export interface FileItem {
  name: string
  size: number
  url?: string
  status?: 'uploading' | 'success' | 'error'
  progress?: number
  file?: File
}

export interface FileUploadRequestOptions {
  action: string
  method: string
  headers: Headers | Record<string, any>
  data: Record<string, any>
  name: string
  file: File
  onProgress: (percent: number) => void
}

const containerRef = useTemplateRef<HTMLElement>('containerRef')
const fileInputRef = useTemplateRef<HTMLInputElement>('fileInputRef')
const isDragging = ref(false)
const isHoveringContainer = ref(false)
const isContainerFocused = ref(false)
const isMaxReached = computed(() => props.max > 0 && fileList.value.length >= props.max)
const canHandlePaste = computed(() =>
  !props.disabled
  && !isMaxReached.value
  && !props.directory
  && (isHoveringContainer.value || isContainerFocused.value),
)

function handleDragOver(e: DragEvent) {
  e.preventDefault()
  if (props.disabled || isMaxReached.value) {
    return
  }
  isDragging.value = true
}

function handleDragLeave(e: DragEvent) {
  e.preventDefault()
  isDragging.value = false
}

function handleDrop(e: DragEvent) {
  e.preventDefault()
  if (props.disabled || isMaxReached.value) {
    return
  }
  isDragging.value = false
  if (e.dataTransfer?.files) {
    handleFiles(e.dataTransfer.files)
  }
}

function onPaste(e: ClipboardEvent) {
  if (e.defaultPrevented || !canHandlePaste.value) {
    return
  }
  const filesFromItems = [...(e.clipboardData?.items ?? [])]
    .filter(item => item.kind === 'file')
    .map(item => item.getAsFile())
    .filter((file): file is File => file !== null)
  const files = filesFromItems.length > 0
    ? filesFromItems
    : [...(e.clipboardData?.files ?? [])]

  if (files.length > 0 && handleFiles(files)) {
    e.preventDefault()
  }
}

function onContainerFocusIn() {
  isContainerFocused.value = true
}

function onContainerFocusOut(e: FocusEvent) {
  const nextFocusedElement = e.relatedTarget
  if (nextFocusedElement instanceof Node && containerRef.value?.contains(nextFocusedElement)) {
    return
  }
  isContainerFocused.value = false
}

onMounted(() => {
  window.addEventListener('paste', onPaste)
})

onUnmounted(() => {
  window.removeEventListener('paste', onPaste)
})

function onSelectFile(files: FileList | File[] | null) {
  handleFiles(files)
}

function handleFiles(files: FileList | File[] | null) {
  if (!files || props.disabled || isMaxReached.value) {
    return false
  }
  const selectedFiles = [...files].filter(file => file instanceof File)
  const remain = props.max > 0 ? props.max - fileList.value.length : selectedFiles.length
  if (remain <= 0) {
    return false
  }
  if (fileInputRef.value) {
    fileInputRef.value.value = ''
  }
  const filesToAdd = selectedFiles.slice(0, remain)
  if (filesToAdd.length === 0) {
    return false
  }
  filesToAdd.forEach((file) => {
    void uploadFile(file)
  })
  return true
}

function getHeadersObject(headers: Headers | Record<string, any>) {
  if (!(headers instanceof Headers)) {
    return { ...headers }
  }
  const headersObj: Record<string, any> = {}
  headers.forEach((value, key) => {
    headersObj[key] = value
  })
  return headersObj
}

async function defaultHttpRequest(options: FileUploadRequestOptions) {
  const formData = new FormData()
  Object.entries(options.data).forEach(([key, value]) => {
    formData.append(key, value)
  })
  formData.append(options.name, options.file)
  const response = await axios({
    url: options.action,
    method: options.method,
    headers: getHeadersObject(options.headers),
    data: formData,
    onUploadProgress: (progressEvent) => {
      if (progressEvent.total) {
        options.onProgress(Math.round((progressEvent.loaded * 100) / progressEvent.total))
      }
    },
  })
  return response.data
}

async function uploadFile(file: File, index?: number) {
  const canUpload = await props.beforeUpload?.(file)
  if (canUpload === false) {
    return
  }

  if (index === undefined) {
    fileList.value.push({
      name: file.name,
      size: file.size,
      status: 'uploading',
      progress: 0,
      file,
    })
  }
  else {
    // 重试：同一行回到上传中，否则失败态会一直挂到请求结束，行内进度也看不见
    const retried = fileList.value[index]
    if (retried) {
      retried.status = 'uploading'
      retried.progress = 0
    }
  }
  const currentFileIndex = index ?? fileList.value.length - 1
  try {
    const response = await (props.httpRequest ?? defaultHttpRequest)({
      action: props.action,
      method: props.method,
      headers: props.headers,
      data: props.data,
      name: props.name,
      file,
      onProgress: (percent) => {
        fileList.value[currentFileIndex].progress = percent
      },
    })
    const url = await props.afterUpload?.(response)
    if (url) {
      fileList.value[currentFileIndex].url = url
    }
    emits('onSuccess', response, file)
    fileList.value[currentFileIndex].status = 'success'
  }
  catch {
    fileList.value[currentFileIndex].status = 'error'
  }
}

function removeFile(idx: number) {
  fileList.value.splice(idx, 1)
}

/** 文件类型图标：按扩展名给出可辨识的图形，未知类型回落到通用文件图标 */
function fileIcon(name: string) {
  const extension = name.slice(name.lastIndexOf('.') + 1).toLowerCase()
  if (['png', 'jpg', 'jpeg', 'gif', 'webp', 'svg', 'bmp', 'avif', 'ico'].includes(extension)) {
    return 'morph:file-image'
  }
  if (['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz'].includes(extension)) {
    return 'morph:file-archive'
  }
  if (['xls', 'xlsx', 'csv', 'ods'].includes(extension)) {
    return 'morph:file-spreadsheet'
  }
  if (['mp4', 'mov', 'webm', 'mkv', 'avi'].includes(extension)) {
    return 'morph:file-video'
  }
  if (['mp3', 'wav', 'flac', 'ogg', 'm4a'].includes(extension)) {
    return 'morph:file-audio'
  }
  if (['doc', 'docx', 'pdf', 'md', 'txt', 'ppt', 'pptx', 'rtf'].includes(extension)) {
    return 'morph:file-text'
  }
  return 'morph:file'
}

const dropzoneHint = computed(() => {
  if (props.disabled) {
    return '上传已停用'
  }
  if (isMaxReached.value) {
    return `已达上限 ${props.max} 个`
  }
  return props.directory ? '点击选择文件夹' : '可拖放、粘贴或点击选择'
})
</script>

<template>
  <div
    ref="containerRef"
    class="yd-file-upload"
    :tabindex="props.disabled ? undefined : 0"
    @mouseenter="isHoveringContainer = true"
    @mouseleave="isHoveringContainer = false"
    @focusin="onContainerFocusIn"
    @focusout="onContainerFocusOut"
  >
    <button
      type="button"
      class="yd-file-upload__dropzone"
      :class="{ 'yd-file-upload__dropzone--dragging': isDragging }"
      :disabled="props.disabled || isMaxReached"
      @dragover="handleDragOver"
      @dragleave="handleDragLeave"
      @drop="handleDrop"
      @click="fileInputRef?.click()"
    >
      <slot>
        <span class="yd-file-upload__dropzone-icon">
          <Icon name="morph:upload" />
        </span>
        <span class="yd-file-upload__dropzone-title">{{ props.description }}</span>
        <span class="yd-file-upload__dropzone-hint">{{ dropzoneHint }}</span>
      </slot>
      <input
        ref="fileInputRef"
        type="file"
        :multiple="props.directory || props.multiple"
        :webkitdirectory="props.directory || undefined"
        :directory="props.directory || undefined"
        :disabled="props.disabled"
        class="hidden"
        @change="e => onSelectFile((e.target as HTMLInputElement).files)"
      >
    </button>

    <ul v-if="fileList.length > 0" class="yd-file-upload__list">
      <li
        v-for="(item, index) in fileList"
        :key="item.name + index"
        class="yd-file-upload__item"
        :class="{
          'yd-file-upload__item--error': item.status === 'error',
          'yd-file-upload__item--uploading': item.status === 'uploading',
        }"
        @click="emits('onClick', item, index)"
      >
        <span class="yd-file-upload__thumb" aria-hidden="true">
          <Icon :name="fileIcon(item.name)" />
        </span>

        <span class="yd-file-upload__body">
          <span class="yd-file-upload__name" :title="item.name">{{ item.name }}</span>
          <span class="yd-file-upload__meta">
            <span class="yd-file-upload__size">{{ filesize(item.size, { standard: 'jedec' }) }}</span>
            <span v-if="item.status === 'uploading'" class="yd-file-upload__meta-text">上传中 {{ item.progress ?? 0 }}%</span>
            <span v-else-if="item.status === 'error'" class="yd-file-upload__meta-text yd-file-upload__meta-text--error">上传失败</span>
          </span>
          <span v-if="item.status === 'uploading'" class="yd-file-upload__bar" aria-hidden="true">
            <span class="yd-file-upload__bar-fill" :style="{ width: `${item.progress ?? 0}%` }" />
          </span>
        </span>

        <span class="yd-file-upload__state">
          <span v-if="item.status === 'uploading'" class="yd-file-upload__spinner" aria-hidden="true" />
          <Icon v-else-if="item.status === 'success'" name="morph:circle-check" class="yd-file-upload__state-icon yd-file-upload__state-icon--success" />
          <Icon v-else-if="item.status === 'error'" name="morph:circle-alert" class="yd-file-upload__state-icon yd-file-upload__state-icon--error" />
        </span>

        <ButtonGroup v-if="!props.disabled" class="yd-file-upload__actions">
          <Button v-if="item.status === 'error'" variant="ghost" size="icon-sm" aria-label="重新上传" @click.stop="uploadFile(item.file!, index)">
            <Icon name="morph:refresh-cw" />
          </Button>
          <Button v-if="item.status !== 'uploading'" variant="ghost" size="icon-sm" aria-label="移除文件" @click.stop="removeFile(index)">
            <Icon name="morph:trash-2" class="yd-file-upload__remove-icon" />
          </Button>
        </ButtonGroup>
      </li>
    </ul>
  </div>
</template>

<style scoped>
/* 上传区与文件行同宽、同列：文件列表不再是固定宽度的磁贴网格 */
.yd-file-upload {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.yd-file-upload__dropzone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 160px;
  padding: 16px;
  cursor: pointer;
  background: transparent;
  border: 2px dashed var(--color-border-3);
  border-radius: var(--yd-control-radius);
  transition:
    border-color var(--yd-motion-duration-fast) var(--yd-motion-ease),
    background var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.yd-file-upload__dropzone:hover:not(:disabled) {
  background: var(--color-fill-1);
  border-color: var(--color-border-4);
}

.yd-file-upload__dropzone--dragging:not(:disabled) {
  background: oklch(var(--primary) / 8%);
  border-color: oklch(var(--primary));
}

.yd-file-upload__dropzone:disabled {
  cursor: not-allowed;
  opacity: var(--yd-disabled-opacity, 0.52);
}

.yd-file-upload__dropzone-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  margin-bottom: 10px;

  /* 图标根节点是 size-[1em]，尺寸由字号决定 */
  font-size: 20px;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  border-radius: 50%;
}

.yd-file-upload__dropzone-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
}

.yd-file-upload__dropzone-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--color-text-3);
}

.yd-file-upload__list {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.yd-file-upload__item {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
  padding: 8px 10px;
  background: var(--color-fill-1);
  border-radius: var(--yd-control-radius);
  transition: background var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.yd-file-upload__item:hover {
  background: var(--color-fill-2);
}

.yd-file-upload__item--error {
  background: color-mix(in srgb, var(--yd-status-danger) 8%, var(--color-bg-1));
}

.yd-file-upload__thumb {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 18px;
  color: var(--color-text-2);
  background: var(--color-bg-1);
  border-radius: calc(var(--yd-control-radius) - 4px);
}

.yd-file-upload__body {
  display: grid;
  flex: 1;
  gap: 1px;
  min-width: 0;
}

.yd-file-upload__name {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-1);
  white-space: nowrap;
}

.yd-file-upload__meta {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  overflow: hidden;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-3);
  white-space: nowrap;
}

.yd-file-upload__size {
  flex: 0 0 auto;
}

/* 窄行只截断状态文案，不回卷成两行把行高撑乱 */
.yd-file-upload__meta-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.yd-file-upload__meta-text::before {
  margin-right: 6px;
  content: "·";
}

.yd-file-upload__meta-text--error {
  color: var(--yd-status-danger);
}

.yd-file-upload__bar {
  display: block;
  width: 100%;
  height: 3px;
  margin-top: 5px;
  overflow: hidden;
  background: var(--color-fill-3);
  border-radius: 999px;
}

.yd-file-upload__bar-fill {
  display: block;
  height: 100%;
  background: oklch(var(--primary));
  border-radius: inherit;
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.yd-file-upload__state {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: 20px;
  color: var(--color-text-3);
}

.yd-file-upload__state-icon {
  font-size: 16px;
}

.yd-file-upload__state-icon--success {
  color: var(--yd-status-success);
}

.yd-file-upload__state-icon--error {
  color: var(--yd-status-danger);
}

.yd-file-upload__spinner {
  width: 16px;
  height: 16px;
  border: 2px solid var(--color-fill-3);
  border-top-color: oklch(var(--primary));
  border-radius: 50%;
  animation: yd-file-upload-spin 0.8s linear infinite;
}

.yd-file-upload__remove-icon {
  color: var(--color-text-3);
}

/* 行操作原先浮在文件名上：改为占位后仅切换透明度，悬停不再遮挡内容 */
.yd-file-upload__actions {
  flex: none;
  opacity: 0;
  transition: opacity var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.yd-file-upload__item:hover .yd-file-upload__actions,
.yd-file-upload__item:focus-within .yd-file-upload__actions {
  opacity: 1;
}

@media (hover: none) {
  .yd-file-upload__actions {
    opacity: 1;
  }
}

/* 窄行里状态文案比文件大小重要：有状态时让出空间，别把「上传失败」「上传中」截掉 */
@media (width <= 480px) {
  .yd-file-upload__item:is(.yd-file-upload__item--error, .yd-file-upload__item--uploading) .yd-file-upload__size,
  .yd-file-upload__item:is(.yd-file-upload__item--error, .yd-file-upload__item--uploading) .yd-file-upload__meta-text::before {
    display: none;
  }
}

@keyframes yd-file-upload-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .yd-file-upload__spinner {
    animation-duration: 1.6s;
  }
}
</style>
