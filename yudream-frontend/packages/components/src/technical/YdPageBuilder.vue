<script setup lang="ts">
import type { Editor, EditorConfig, UploadFileClb, UploadFileOptions } from 'grapesjs'
import type { YdPageBuilderInput } from './page-builder-model'
import type { YdPageBuilderEditorFacade, YdPageBuilderInstance, YdPageBuilderOptions, YdPageBuilderSetupHandler, YdPageBuilderSnapshot, YdPageBuilderUploadHandler } from './page-builder.types'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { createYdPageBuilderFacadeMethod, isYdPageBuilderInstanceCurrent, resolveYdPageBuilderEngineContainer, shouldYdPageBuilderEmitReady } from './page-builder-lifecycle'
import { resolveYdPageBuilderChangeMode, resolveYdPageBuilderLoad, sameYdPageBuilderSnapshot } from './page-builder-model'
import 'grapesjs/dist/css/grapes.min.css'
import '../theme/appearance.css'

defineOptions({ name: 'YdPageBuilder', inheritAttrs: false })

const props = withDefaults(defineProps<{
  htmlContent?: string
  cssContent?: string
  builderProjectJson?: string
  readOnly?: boolean
  disabled?: boolean
  height?: string
  ariaLabel?: string
  editorOptions?: YdPageBuilderOptions
  upload?: YdPageBuilderUploadHandler
  setup?: YdPageBuilderSetupHandler
}>(), {
  htmlContent: '',
  cssContent: '',
  builderProjectJson: '',
  readOnly: false,
  disabled: false,
  height: '100%',
  ariaLabel: '页面构建器',
})

const emit = defineEmits<{
  'ready': [editor: YdPageBuilderEditorFacade]
  'change': [snapshot: YdPageBuilderSnapshot]
  'update:htmlContent': [value: string]
  'update:cssContent': [value: string]
  'update:builderProjectJson': [value: string]
  'error': [error: Error]
}>()

const root = ref<HTMLElement | null>(null)
const engine = ref<HTMLElement | null>(null)
const initialized = ref(false)
const initializationError = ref('')
let editor: Editor | null = null
const destroyedEditors = new WeakSet<Editor>()
let facade: YdPageBuilderEditorFacade | null = null
let disposed = false
let prepared = false
let canvasLoaded = false
let applyingExternalValue = false
let readyEmitted = false
let changeTimer: ReturnType<typeof setTimeout> | null = null
let lastEmitted: YdPageBuilderSnapshot | null = null

function inputValue(): YdPageBuilderInput {
  return {
    htmlContent: props.htmlContent,
    cssContent: props.cssContent,
    builderProjectJson: props.builderProjectJson,
  }
}

function snapshotOf(instance: Editor): YdPageBuilderSnapshot {
  return {
    htmlContent: instance.getHtml() || '',
    cssContent: instance.getCss({ keepUnusedStyles: true }) || '',
    builderProjectJson: JSON.stringify(instance.getProjectData()),
  }
}

function reportError(cause: unknown) {
  const error = cause instanceof Error ? cause : new Error(String(cause))
  initializationError.value = error.message
  emit('error', error)
}

function makeFacade(instance: Editor): YdPageBuilderEditorFacade {
  const guarded = <TArgs extends unknown[], TResult>(operation: (...args: TArgs) => TResult) => createYdPageBuilderFacadeMethod(
    () => !disposed && editor === instance && !destroyedEditors.has(instance),
    operation,
  )
  return {
    get editor() {
      guarded(() => instance)()
      return instance
    },
    getSnapshot: guarded(() => snapshotOf(instance)),
    loadProjectData: guarded(project => instance.loadProjectData(project as Parameters<Editor['loadProjectData']>[0])),
    setComponents: guarded(html => instance.setComponents(html)),
    setStyle: guarded(css => instance.setStyle(css)),
    dispose: guarded(() => disposeEditor(instance)),
  }
}

function disposeEditor(instance = editor) {
  if (!instance || destroyedEditors.has(instance)) {
    return
  }
  destroyedEditors.add(instance)
  if (changeTimer) {
    clearTimeout(changeTimer)
    changeTimer = null
  }
  instance.off('update', scheduleChange)
  instance.off('load', onCanvasLoad)
  instance.destroy()
  if (editor === instance) {
    editor = null
    facade = null
    initialized.value = false
  }
}

function dispose() {
  disposed = true
  disposeEditor()
}

function maybeEmitReady() {
  if (!facade || !shouldYdPageBuilderEmitReady({ disposed, readyEmitted, prepared, canvasLoaded })) {
    return
  }
  readyEmitted = true
  initialized.value = true
  emit('ready', facade)
}

function onCanvasLoad() {
  canvasLoaded = true
  maybeEmitReady()
}

function applyInput(instance: Editor, input: YdPageBuilderInput, mode: 'project' | 'html') {
  const resolved = resolveYdPageBuilderLoad(input)
  if (resolved.kind === 'html' && resolved.projectError) {
    reportError(resolved.projectError)
  }
  applyingExternalValue = true
  try {
    if (mode === 'project' && resolved.kind === 'project') {
      instance.loadProjectData(resolved.project as Parameters<Editor['loadProjectData']>[0])
    }
    else {
      instance.setComponents(resolved.kind === 'html' ? resolved.htmlContent : input.htmlContent || '')
      instance.setStyle(resolved.kind === 'html' ? resolved.cssContent : input.cssContent || '')
    }
  }
  finally {
    applyingExternalValue = false
  }
  // 本次加载没有再出错时清除上一次的加载错误，保持状态真实；刚上报的错误不会被清除。
  if (!(resolved.kind === 'html' && resolved.projectError)) {
    initializationError.value = ''
  }
  lastEmitted = snapshotOf(instance)
}

function scheduleChange() {
  if (applyingExternalValue || disposed || !editor) {
    return
  }
  const scheduled = editor
  if (changeTimer) {
    clearTimeout(changeTimer)
  }
  changeTimer = setTimeout(() => {
    changeTimer = null
    const instance = editor
    // 迟到刷新：仅当调度时的实例仍是当前存活实例才产出快照。
    if (!instance || !isYdPageBuilderInstanceCurrent(scheduled, instance, disposed, candidate => destroyedEditors.has(candidate))) {
      return
    }
    const snapshot = snapshotOf(instance)
    if (lastEmitted && sameYdPageBuilderSnapshot(snapshot, lastEmitted)) {
      return
    }
    lastEmitted = snapshot
    emit('update:htmlContent', snapshot.htmlContent)
    emit('update:cssContent', snapshot.cssContent)
    emit('update:builderProjectJson', snapshot.builderProjectJson)
    emit('change', snapshot)
  }, 120)
}

function filesFromUploadEvent(event: DragEvent, options?: UploadFileOptions): File[] {
  if (options?.file) {
    return [options.file]
  }
  const transferFiles = event.dataTransfer?.files
  if (transferFiles?.length) {
    return Array.from(transferFiles)
  }
  const inputFiles = (event.target as HTMLInputElement | null)?.files
  return inputFiles ? Array.from(inputFiles) : []
}

async function uploadFiles(event: DragEvent, callback?: UploadFileClb, options?: UploadFileOptions) {
  const uploadEditor = editor
  if (!isYdPageBuilderInstanceCurrent(uploadEditor, editor, disposed, candidate => destroyedEditors.has(candidate))) {
    return
  }
  const handler = props.upload
  if (!handler) {
    throw new Error('当前构建器未配置媒体上传处理器')
  }
  const files = filesFromUploadEvent(event, options)
  if (!files.length) {
    if (isYdPageBuilderInstanceCurrent(uploadEditor, editor, disposed, candidate => destroyedEditors.has(candidate))) {
      callback?.({ data: [] })
    }
    return
  }
  try {
    const assets = await handler(files)
    if (!isYdPageBuilderInstanceCurrent(uploadEditor, editor, disposed, candidate => destroyedEditors.has(candidate))) {
      return
    }
    callback?.({ data: assets })
  }
  catch (cause) {
    if (!isYdPageBuilderInstanceCurrent(uploadEditor, editor, disposed, candidate => destroyedEditors.has(candidate))) {
      return
    }
    reportError(cause)
    throw cause
  }
}

async function initialize() {
  try {
    const grapes = await import('grapesjs')
    // 异步加载期间可能已卸载；引擎容器必须是专用子容器，根元素承载着 Vue 状态节点。
    const container = resolveYdPageBuilderEngineContainer(root.value, engine.value)
    if (disposed || !container) {
      return
    }
    const assetManager = {
      ...(props.editorOptions?.assetManager || {}),
      ...(props.upload ? { uploadFile: uploadFiles } : {}),
    }
    const config: EditorConfig = {
      ...props.editorOptions,
      container,
      height: props.height,
      fromElement: false,
      storageManager: false,
      assetManager,
    }
    const instance = grapes.default.init(config)
    editor = instance
    facade = makeFacade(instance)
    instance.on('load', onCanvasLoad)
    instance.on('update', scheduleChange)
    await props.setup?.(instance)
    if (disposed || editor !== instance) {
      disposeEditor(instance)
      return
    }
    applyingExternalValue = true
    try {
      const loaded = resolveYdPageBuilderLoad(inputValue())
      if (loaded.kind === 'html' && loaded.projectError) {
        reportError(loaded.projectError)
      }
      if (loaded.kind === 'project') {
        instance.loadProjectData(loaded.project as Parameters<Editor['loadProjectData']>[0])
      }
      else {
        instance.setComponents(loaded.htmlContent)
        instance.setStyle(loaded.cssContent)
      }
    }
    finally {
      applyingExternalValue = false
    }
    lastEmitted = snapshotOf(instance)
    prepared = true
    if (props.editorOptions?.headless || instance.Canvas.getFrameEl()?.contentDocument?.readyState === 'complete') {
      canvasLoaded = true
    }
    maybeEmitReady()
  }
  catch (cause) {
    if (!disposed) {
      reportError(cause)
      disposeEditor()
    }
  }
}

watch(
  () => [props.htmlContent, props.cssContent, props.builderProjectJson] as const,
  (next, previous) => {
    const instance = editor
    if (!instance || !previous || disposed) {
      return
    }
    const nextInput: YdPageBuilderInput = { htmlContent: next[0], cssContent: next[1], builderProjectJson: next[2] }
    const previousInput: YdPageBuilderInput = { htmlContent: previous[0], cssContent: previous[1], builderProjectJson: previous[2] }
    const mode = resolveYdPageBuilderChangeMode(nextInput, previousInput, lastEmitted)
    if (mode !== 'ignore') {
      applyInput(instance, nextInput, mode)
    }
  },
  { flush: 'post' },
)

onMounted(() => {
  void initialize()
})
onBeforeUnmount(dispose)
const instanceApi: YdPageBuilderInstance = { getEditor: () => facade, dispose }
defineExpose(instanceApi)
</script>

<template>
  <div
    ref="root"
    class="yd-page-builder"
    :class="{ 'yd-page-builder--disabled': disabled, 'yd-page-builder--readonly': readOnly }"
    :style="{ height }"
    :aria-label="ariaLabel"
    :aria-disabled="disabled"
    :aria-readonly="readOnly"
    :inert="disabled || readOnly"
    data-yd-page-builder
  >
    <div ref="engine" class="yd-page-builder__engine" />
    <p v-if="initializationError" class="yd-page-builder__error" role="alert">
      {{ initializationError }}
    </p>
    <span v-else-if="!initialized" class="yd-page-builder__status" role="status" aria-live="polite">正在加载页面构建器…</span>
  </div>
</template>

<style>
.yd-page-builder {
  --gjs-main-color: var(--color-bg-2);
  --gjs-primary-color: var(--color-bg-2);
  --gjs-secondary-color: var(--color-text-2);
  --gjs-tertiary-color: oklch(var(--primary));
  --gjs-quaternary-color: oklch(var(--primary));
  --gjs-font-color: var(--color-text-2);
  --gjs-font-color-active: var(--color-text-1);
  --gjs-main-dark-color: var(--color-fill-2);
  --gjs-secondary-dark-color: var(--color-fill-1);
  --gjs-main-light-color: var(--color-fill-2);
  --gjs-secondary-light-color: var(--color-text-3);
  --gjs-soft-light-color: var(--color-fill-1);
  --gjs-color-highlight: oklch(var(--primary));
  --gjs-color-blue: oklch(var(--primary));
  --gjs-color-red: rgb(var(--danger-6));
  --gjs-color-warn: rgb(var(--warning-6));
  --gjs-color-yellow: rgb(var(--warning-6));
  --gjs-color-green: rgb(var(--success-6));
  --gjs-light-border: var(--color-border-2);
  --gjs-arrow-color: var(--color-text-3);
  --gjs-dark-text-shadow: transparent;
  --gjs-main-font: inherit;
  --gjs-font-size: var(--yd-control-font-size);
  --gjs-animation-duration: var(--yd-motion-duration-fast);
  --gjs-input-padding: calc(var(--yd-control-height) / 8);
  position: relative;
  min-width: 0;
  overflow: hidden;
  color: var(--color-text-1);
  border-radius: var(--yd-control-radius);
  background: var(--color-bg-1);
}
.yd-page-builder--disabled { opacity: 0.68; }
/* 引擎子容器：GrapesJS 初始化/销毁会清空该容器并追加自己的 DOM，Vue 不渲染其子节点。 */
.yd-page-builder__engine { height: 100%; min-height: 0; }
.yd-page-builder__status, .yd-page-builder__error { position: absolute; z-index: 1; top: 8px; left: 8px; margin: 0; padding: 4px 8px; border-radius: 4px; background: var(--color-bg-1); color: var(--color-text-3); font-size: 12px; }
.yd-page-builder__error { color: rgb(var(--danger-6)); }
</style>
