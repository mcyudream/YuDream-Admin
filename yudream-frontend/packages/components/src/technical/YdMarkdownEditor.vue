<script setup lang="ts">
import type { ExposeParam, HeadList } from 'md-editor-v3'
import type { YdMarkdownHeading, YdMarkdownImage, YdMarkdownImageUploadHandler, YdMarkdownInsert, YdMarkdownLinkClick, YdMarkdownToolbar } from './markdown-editor.types'
import { MdEditor, MdPreview } from 'md-editor-v3'
import { computed, onBeforeUnmount, onMounted, ref, useId, watch } from 'vue'
import { resolveYdEditorFieldAttrs } from './code-editor-model'
import { createYdMarkdownUploadCallback, createYdMarkdownUploadLifecycle } from './markdown-editor-lifecycle'
import { resolveYdMarkdownEditorTheme } from './markdown-editor.types'
import { sanitizeYdMarkdownHtml, shouldUploadYdMarkdownImages } from './markdown-render'
import './markdown-engine-config'
import 'md-editor-v3/lib/style.css'

defineOptions({ name: 'YdMarkdownEditor', inheritAttrs: false })

const props = withDefaults(defineProps<{
  modelValue?: string
  placeholder?: string
  height?: string
  language?: string
  previewTheme?: string
  codeTheme?: string
  readonly?: boolean
  disabled?: boolean
  error?: string
  id?: string
  ariaLabel?: string
  ariaInvalid?: boolean | string
  ariaDescribedby?: string
  imageUpload?: YdMarkdownImageUploadHandler
  toolbars?: YdMarkdownToolbar[]
  toolbarsExclude?: YdMarkdownToolbar[]
  showToolbarName?: boolean
  catalogLayout?: 'fixed' | 'flat'
  catalogMaxDepth?: number
}>(), {
  modelValue: '',
  placeholder: '在这里编写 Markdown 内容...',
  height: '420px',
  language: 'zh-CN',
  previewTheme: 'github',
  codeTheme: 'github',
  readonly: false,
  disabled: false,
  error: '',
  ariaLabel: 'Markdown 编辑器',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'uploadError': [error: unknown]
  'linkClick': [payload: YdMarkdownLinkClick]
  'catalogChange': [headings: YdMarkdownHeading[]]
  'save': [markdown: string]
}>()

const uploadLifecycle = createYdMarkdownUploadLifecycle()
watch(() => [props.readonly, props.disabled] as const, () => uploadLifecycle.invalidate(), { flush: 'sync' })

const root = ref<HTMLElement | null>(null)
const editor = ref<ExposeParam | null>(null)
const darkMode = ref(false)
const editorValue = computed({
  get: () => props.modelValue,
  set: (value: string) => emit('update:modelValue', value),
})
const fieldAttrs = computed(() => resolveYdEditorFieldAttrs({ id: props.id, invalid: props.ariaInvalid, describedby: props.ariaDescribedby, error: props.error }))
const errorId = computed(() => props.id ? `${props.id}-error` : undefined)
const isReadOnly = computed(() => props.readonly || props.disabled)
const mdEditorId = `yd-markdown-editor-${useId()}`
function editorInput() {
  return root.value?.querySelector<HTMLElement>('.md-editor-input, [contenteditable="true"], textarea, [role="textbox"]')
}

function syncAccessibility() {
  if (isReadOnly.value) {
    const preview = root.value
    if (!preview) {
      return
    }
    preview.setAttribute('role', 'region')
    preview.setAttribute('tabindex', props.disabled ? '-1' : '0')
    preview.setAttribute('aria-label', props.ariaLabel)
    preview.removeAttribute('aria-readonly')
    preview.setAttribute('aria-disabled', String(props.disabled))
    preview.setAttribute('aria-invalid', String(fieldAttrs.value.invalid))
    if (fieldAttrs.value.id) {
      preview.id = fieldAttrs.value.id
    }
    else {
      preview.removeAttribute('id')
    }
    if (fieldAttrs.value.describedby) {
      preview.setAttribute('aria-describedby', fieldAttrs.value.describedby)
    }
    else {
      preview.removeAttribute('aria-describedby')
    }
    return
  }
  root.value?.removeAttribute('role')
  root.value?.removeAttribute('tabindex')
  root.value?.removeAttribute('id')
  root.value?.removeAttribute('aria-label')
  root.value?.removeAttribute('aria-readonly')
  root.value?.removeAttribute('aria-disabled')
  root.value?.removeAttribute('aria-invalid')
  root.value?.removeAttribute('aria-describedby')
  const input = editorInput()
  if (!input) {
    return
  }
  if (fieldAttrs.value.id) {
    input.id = fieldAttrs.value.id
  }
  else {
    input.removeAttribute('id')
  }
  input.setAttribute('aria-label', props.ariaLabel)
  input.setAttribute('aria-readonly', String(isReadOnly.value))
  input.setAttribute('aria-disabled', String(props.disabled))
  input.setAttribute('aria-invalid', String(fieldAttrs.value.invalid))
  if (fieldAttrs.value.describedby) {
    input.setAttribute('aria-describedby', fieldAttrs.value.describedby)
  }
  else {
    input.removeAttribute('aria-describedby')
  }
}

function focus() {
  if (props.disabled) {
    return
  }
  if (isReadOnly.value) {
    root.value?.focus()
  }
  else {
    editorInput()?.focus()
  }
}

function blur() {
  if (isReadOnly.value) {
    root.value?.blur()
  }
  else {
    editorInput()?.blur()
  }
}

function insert(generate: YdMarkdownInsert) {
  if (!isReadOnly.value) {
    editor.value?.insert(generate)
  }
}

function handleCatalogChange(headings: HeadList[]) {
  emit('catalogChange', headings.map(({ text, level, line, active }) => ({ text, level, line, active })))
}

function handlePreviewClick(event: MouseEvent) {
  if (!isReadOnly.value || !(event.target instanceof Element)) {
    return
  }
  const anchor = event.target.closest<HTMLAnchorElement>('a[href]')
  if (!anchor || !root.value?.contains(anchor)) {
    return
  }
  emit('linkClick', { href: anchor.getAttribute('href') ?? '', anchor, event })
}

async function handleUploadImage(files: File[], callback: (images: YdMarkdownImage[]) => void) {
  const ticket = uploadLifecycle.ticket()
  const complete = createYdMarkdownUploadCallback(uploadLifecycle, ticket, callback)
  if (!shouldUploadYdMarkdownImages(props.readonly, props.disabled, files.length) || !props.imageUpload) {
    complete([])
    return
  }

  try {
    await props.imageUpload(files, complete)
  }
  catch (error) {
    if (!uploadLifecycle.isActive(ticket)) {
      return
    }
    emit('uploadError', error)
    complete([])
  }
}

defineExpose({ focus, blur, insert, element: root })

let editorObserver: MutationObserver | undefined
let themeObserver: MutationObserver | undefined

onMounted(() => {
  const syncTheme = () => {
    darkMode.value = document.documentElement.classList.contains('dark')
  }
  syncTheme()
  themeObserver = new MutationObserver(syncTheme)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  if (root.value) {
    editorObserver = new MutationObserver(syncAccessibility)
    editorObserver.observe(root.value, { childList: true, subtree: true })
  }
  syncAccessibility()
})

watch([() => props.id, () => props.ariaLabel, () => props.error, () => props.ariaInvalid, () => props.ariaDescribedby, isReadOnly], syncAccessibility)

onBeforeUnmount(() => {
  uploadLifecycle.dispose()
  editorObserver?.disconnect()
  themeObserver?.disconnect()
})
</script>

<template>
  <div
    ref="root"
    @click.capture="handlePreviewClick"
    class="yd-markdown-editor"
    :class="{ 'yd-markdown-editor--disabled': disabled, 'yd-markdown-editor--invalid': Boolean(error) }"
    :style="isReadOnly ? { minHeight: height } : undefined"
  >
    <MdPreview
      v-if="isReadOnly"
      :model-value="modelValue"
      :language="language"
      :preview-theme="previewTheme"
      :code-theme="codeTheme"
      :id="mdEditorId"
      :theme="resolveYdMarkdownEditorTheme(darkMode)"
      :sanitize="sanitizeYdMarkdownHtml"
      no-img-zoom-in
      :style="{ minHeight: height }"
      @click="disabled ? $event.preventDefault() : undefined"
    />
    <MdEditor
      v-else
      ref="editor"
      v-model="editorValue"
      :language="language"
      :preview-theme="previewTheme"
      :code-theme="codeTheme"
      :id="mdEditorId"
      :theme="resolveYdMarkdownEditorTheme(darkMode)"
      :placeholder="placeholder"
      :style="{ height }"
      :sanitize="sanitizeYdMarkdownHtml"
      :toolbars="toolbars"
      :toolbars-exclude="toolbarsExclude"
      :show-toolbar-name="showToolbarName"
      :catalog-layout="catalogLayout"
      :catalog-max-depth="catalogMaxDepth"
      @on-get-catalog="handleCatalogChange"
      @on-save="value => emit('save', value)"
      @on-upload-img="handleUploadImage"
    />
    <p v-if="error" :id="errorId" class="yd-markdown-editor__error" role="alert">
      {{ error }}
    </p>
  </div>
</template>

<style>
.yd-markdown-editor {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.yd-markdown-editor:focus-within {
  border-color: rgb(var(--primary-6));
}

.yd-markdown-editor--disabled {
  opacity: 0.68;
}

.yd-markdown-editor--invalid {
  border-color: rgb(var(--danger-6));
}

.yd-markdown-editor .md-editor {
  border: 0;
  background: var(--color-bg-1);
}

.yd-markdown-editor .md-editor-toolbar-wrapper {
  border-bottom-color: var(--color-border-2);
}

.yd-markdown-editor .md-editor-input,
.yd-markdown-editor .md-editor-preview-wrapper {
  background: var(--color-bg-1);
}

.yd-markdown-editor .md-editor-code {
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
  background: var(--color-fill-1);
}

.yd-markdown-editor .md-editor-code-head {
  display: flex;
  min-height: 32px;
  align-items: center;
  padding: 0 8px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-fill-2);
}

.yd-markdown-editor .md-editor-code-flag {
  display: none;
}

.yd-markdown-editor .md-editor-code-action {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.yd-markdown-editor .md-editor-code-lang,
.yd-markdown-editor .md-editor-copy-button {
  color: var(--color-text-3);
  font-size: 12px;
}

.yd-markdown-editor .md-editor-code pre {
  margin: 0;
  border-radius: 0;
  background: transparent;
}

.yd-markdown-editor__error {
  margin: 0;
  padding: 0 10px 8px;
  color: rgb(var(--danger-6));
  font-size: 12px;
}

@media (max-width: 1100px) {
  .yd-markdown-editor .md-editor {
    min-height: 360px;
  }
}
</style>
