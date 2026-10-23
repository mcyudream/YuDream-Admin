<script setup lang="ts">
// 设计视图主区：统一经 YdPageBuilder 承载画布，页面与主题区域（header/footer）
// 都从这里进出；所有编辑回流都转换成 StudioCommand，与 AI/属性面板共用同一管道。
// 断点使用引擎 deviceManager 的真实媒体查询（992/768），不靠外层容器宽度模拟。
// 缩放/适配/预览直接调用引擎 Canvas 与 Commands API；键盘编辑快捷键同时挂在
// 主文档与画布 iframe 文档上，输入框、弹窗与内联内容编辑时一律不截获。
import type { YdPageBuilderEditorFacade, YdPageBuilderOptions, YdPageBuilderSetupHandler, YdPageBuilderSnapshot } from '@yudream/components'
import type { StudioNode } from '@/types/site-studio'
import type { StudioParseReport } from '@/utils/site-studio'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import { useAppAuth } from '@/composables/app/auth'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import {
  compileStudioNodes,
  compileStudioPage,
  compileStudioThemeCss,
  createStudioGrapesAdapter,
  parseStudioHtml,
  STUDIO_BASE_CSS,
} from '@/utils/site-studio'
import { parseStudioCanvasCss } from '@/utils/site-studio-grapes'

defineOptions({ name: 'StudioDesignView' })

const store = useSiteStudioStore()
const toast = useYdToast()
const { auth } = useAppAuth()
const canEdit = computed(() => auth('platform:cms:edit'))
let disposed = false
const frameDocuments = new Set<Document>()

const ready = ref(false)
const builderError = ref('')
const facade = shallowRef<YdPageBuilderEditorFacade | null>(null)
const adapter = createStudioGrapesAdapter()

// ===== 画布缩放 / 适配 / 预览（真实引擎能力） =====

const canvasHost = ref<HTMLElement | null>(null)
const zoom = ref(100)
const previewMode = ref(false)
const ZOOM_MIN = 20
const ZOOM_MAX = 400
const ZOOM_STEP = 10

function clampZoom(value: number) {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, Math.round(value)))
}

function applyZoom(value: number) {
  if (disposed || !ready.value) {
    return
  }
  const editor = facade.value?.editor
  if (!editor) {
    return
  }
  zoom.value = clampZoom(value)
  editor.Canvas.setZoom(zoom.value)
}

function readZoom() {
  if (disposed || !ready.value) {
    return
  }
  const editor = facade.value?.editor
  if (editor) {
    zoom.value = clampZoom(editor.Canvas.getZoom())
  }
}

/** 适配宽度：按宿主可用宽度与当前画布帧宽计算缩放，桌面近距离 100% */
function fitCanvasWidth() {
  if (disposed || !ready.value) {
    return
  }
  const editor = facade.value?.editor
  const host = canvasHost.value
  if (!editor || !host) {
    return
  }
  const available = host.clientWidth - 24
  const frameWidth = editor.Canvas.getFrameEl()?.offsetWidth || 0
  if (available <= 0 || frameWidth <= 0) {
    applyZoom(100)
    return
  }
  applyZoom(Math.min(100, available / frameWidth * 100))
  void nextTick(() => readZoom())
}

function togglePreview() {
  if (disposed || !ready.value) {
    return
  }
  const editor = facade.value?.editor
  if (!editor || !ready.value) {
    return
  }
  if (previewMode.value) {
    editor.Commands.stop('core:preview')
  }
  else {
    editor.Commands.run('core:preview')
  }
}

const regionOptions = [
  { label: '页面内容', value: 'page' },
  { label: '页头 header', value: 'header' },
  { label: '页脚 footer', value: 'footer' },
]
const breakpointOptions = [
  { label: '桌面', value: 'desktop' },
  { label: '平板', value: 'tablet' },
  { label: '手机', value: 'mobile' },
]

/** 当前编辑目标的节点集合：页面根或主题区域 */
const targetNodes = computed<StudioNode[]>(() => {
  const document = store.doc
  if (!document) {
    return []
  }
  if (store.editRegion === 'page') {
    return store.activePage?.root || []
  }
  return document.theme[store.editRegion]
})

const compiled = computed(() => {
  const document = store.doc
  if (!document) {
    return null
  }
  if (store.editRegion === 'page') {
    // 编辑页面时连同主题页头页脚一起渲染（只读），画布与公开站所见一致；解析只取 page 区域
    return store.activePage ? compileStudioPage(document, store.activePage.id, { includeChrome: true, themeCss: false }) : null
  }
  const output = compileStudioNodes(document.theme[store.editRegion])
  const root = `[data-studio-document="${document.id}"]`
  const tokens = Object.entries(document.theme.tokens).map(([name, value]) => `${name.startsWith('--') ? name : `--${name}`}:${value}`).join(';')
  return { html: `<div data-studio-document="${document.id}">${output.html}</div>`, css: `${root}{${tokens}}\n${output.css}` }
})

/**
 * 主题级 CSS（字体/关键帧/样式类）不进引擎：以独立 <style> 注入画布文档，随文档变化原地更新。
 * 若进入引擎 CSS，快照回流会把样式类展开成逐节点样式，@font-face/@keyframes 也会被解析拒绝。
 */
const themeCss = computed(() => {
  try {
    return store.doc ? compileStudioThemeCss(store.doc) : ''
  }
  catch {
    return ''
  }
})
const THEME_STYLE_ID = 'yd-studio-theme-css'
function injectThemeCss(frameDocument: Document) {
  let style = frameDocument.getElementById(THEME_STYLE_ID)
  if (!style) {
    style = frameDocument.createElement('style')
    style.id = THEME_STYLE_ID
    frameDocument.head?.appendChild(style)
  }
  if (style.textContent !== themeCss.value) {
    style.textContent = themeCss.value
  }
}
/** 注入到引擎当前的画布文档：画布 iframe 会在初始化与设备切换时重建文档，必须每次取现值 */
function syncCanvasThemeCss() {
  const canvasDocument = facade.value?.editor.Canvas.getDocument?.()
  if (canvasDocument?.head) {
    injectThemeCss(canvasDocument)
  }
}
watch(themeCss, syncCanvasThemeCss)

/**
 * 画布实际输入。默认是文档编译结果；画布自身的无损编辑写回文档后，改为引擎刚发出的快照，
 * 构建器把与上次输出相同的输入识别为回声而不整体重建，选中、滚动与 iframe 状态得以保留。
 * 回声按 editRevision 精确对应：其后任何撤销、AI、属性面板改动都会重新编译回灌。
 */
// 回声需带上引擎项目 JSON，构建器按三项全等判定；编译输入不带项目 JSON，始终按 HTML/CSS 载入
interface CanvasInput { html: string, css: string, project?: string }
const canvasInput = shallowRef<CanvasInput | null>(null)
let canvasEcho: (Required<CanvasInput> & { revision: string }) | null = null
watch(compiled, (value) => {
  const echo = canvasEcho
  canvasEcho = null
  canvasInput.value = value && echo && echo.revision === store.editRevision ? { html: echo.html, css: echo.css, project: echo.project } : value
}, { immediate: true })

/** 画布内容被拒或未能写入文档时，按文档强制回灌，避免画布残留文档中不存在的内容 */
function resyncCanvas() {
  const value = compiled.value
  canvasEcho = null
  if (!value || disposed) {
    return
  }
  const current = canvasInput.value
  if (current?.html !== value.html || current?.css !== value.css || current?.project) {
    canvasInput.value = value
  }
  else if (facade.value && ready.value) {
    // 输入未变时构建器不会重放，直接经 facade 写回
    facade.value.setComponents(value.html)
    facade.value.setStyle(value.css)
  }
  reselectAfterRebuild()
}

const pageBuilderOptions = computed<YdPageBuilderOptions>(() => ({
  width: 'auto',
  // 不用引擎保护样式：getCss() 会把它拼进快照并回流文档。基础样式经 canvas.styles 以样式表链接注入画布，
  // 与公开站、草稿预览、静态导出同一份，且不属于引擎 CSS
  protectedCss: '',
  canvas: { styles: [`data:text/css;charset=utf-8,${encodeURIComponent(STUDIO_BASE_CSS)}`] },
  // 引擎默认 CSSOM 解析会展开简写、丢弃含 var() 的简写并回写文档（按钮背景丢失），改为按原文解析
  parser: { parserCss: parseStudioCanvasCss } as YdPageBuilderOptions['parser'],
  undoManager: { trackSelection: false },
  panels: { defaults: [] },
  deviceManager: {
    devices: [
      { id: 'desktop', name: '桌面', width: '', widthMedia: '' },
      { id: 'tablet', name: '平板', width: '992px', widthMedia: '992px' },
      { id: 'mobile', name: '手机', width: '390px', widthMedia: '768px' },
    ],
  },
}))

// ===== 画布选择 ↔ store 双向同步 =====

function selectedCanvasId(editor: NonNullable<YdPageBuilderEditorFacade>['editor']) {
  return editor.getSelected()?.getAttributes?.()['data-studio-id'] as string | undefined
}

/** 按 data-studio-id 在画布中定位组件并反映 store 的选中状态 */
function syncCanvasSelection(nodeId: string) {
  const facadeInstance = facade.value
  if (!facadeInstance || !ready.value) {
    return
  }
  const editor = facadeInstance.editor
  if (selectedCanvasId(editor) === nodeId) {
    return
  }
  if (!nodeId) {
    editor.select()
    return
  }
  const [component] = editor.getWrapper()?.find(`[data-studio-id="${nodeId}"]`) || []
  if (component) {
    editor.select(component)
  }
  else {
    editor.select()
  }
}

/** 页面编辑时画布里的主题页头页脚只作展示：不可选中、编辑、拖入或删除（在“页头/页脚”区域里编辑） */
const LOCKED_REGION = { selectable: false, hoverable: false, highlightable: false, editable: false, draggable: false, droppable: false, removable: false, copyable: false, badgable: false, layerable: false }
function lockThemeRegions() {
  const editor = facade.value?.editor
  if (disposed || !ready.value || !editor || store.editRegion !== 'page') {
    return
  }
  for (const region of editor.getWrapper()?.find('[data-studio-region="header"], [data-studio-region="footer"]') ?? []) {
    region.set(LOCKED_REGION)
    region.onAll(child => child.set(LOCKED_REGION))
  }
}

function onBuilderReady(instance: YdPageBuilderEditorFacade) {
  facade.value = instance
  ready.value = true
  builderError.value = ''
  lockThemeRegions()
  syncCanvasThemeCss()
  instance.editor.setDevice(store.breakpoint)
  readZoom()
  requestAnimationFrame(() => fitCanvasWidth())
}

function onBuilderError(error: Error) {
  builderError.value = error.message
}

function setupBuilder(editor: Parameters<YdPageBuilderSetupHandler>[0]) {
  adapter.setup(editor)
  // 引擎默认键位（undo/redo/删除/字母导航）只绑在主文档上，会绕过 StudioCommand
  // 管道并与工作台快捷键重复触发；统一移除，由下方键盘处理器走同一命令管道。
  for (const keymap of ['core:undo', 'core:redo', 'core:component-delete', 'core:component-next', 'core:component-prev', 'core:component-enter', 'core:component-exit']) {
    editor.Keymaps.remove(keymap)
  }
  // 必须绑定 this：GrapesJS Editor.on 读取 this.em，解构后直接调用会使画布初始化失败
  const on = editor.on.bind(editor) as (event: string, handler: (payload?: unknown) => void) => void
  on('component:selected', (payload) => {
    const component = payload as { getAttributes?: () => Record<string, unknown> } | undefined
    const id = component?.getAttributes?.()['data-studio-id']
    if (typeof id === 'string' && id) {
      store.selectedNodeId = id
    }
  })
  // 外部回灌会重建组件：主题页头页脚区域在创建时即锁定（区域内子节点此时已随之构建）
  on('component:add', (payload) => {
    const component = payload as { getAttributes?: () => Record<string, unknown>, set: (props: object) => void, onAll: (cb: (child: { set: (props: object) => void }) => void) => void } | undefined
    const region = component?.getAttributes?.()['data-studio-region']
    if (region === 'header' || region === 'footer') {
      component!.set(LOCKED_REGION)
      component!.onAll(child => child.set(LOCKED_REGION))
    }
  })
  on('component:deselected', () => {
    store.selectedNodeId = ''
  })
  on('canvas:zoom', () => {
    readZoom()
  })
  on('run:core:preview', () => {
    previewMode.value = true
  })
  on('stop:core:preview', () => {
    previewMode.value = false
  })
  // 画布 iframe 内的键盘事件不冒泡到主文档，加载完成后单独挂载同一处理器
  const bindFrameKeydown = (frame?: HTMLIFrameElement) => {
    const frameDocument = (frame?.contentWindow ?? null)?.document ?? null
    if (frameDocument) {
      frameDocuments.add(frameDocument)
      frameDocument.addEventListener('keydown', onStudioKeydown)
    }
  }
  on('destroy', () => {
    ready.value = false
    facade.value = null
    for (const document of frameDocuments) {
      document.removeEventListener('keydown', onStudioKeydown)
    }
    frameDocuments.clear()
  })
  bindFrameKeydown(editor.Canvas.getFrameEl())
  on('canvas:frame:load', (payload) => {
    bindFrameKeydown((payload as { el?: HTMLIFrameElement }).el)
    syncCanvasThemeCss()
  })
  on('canvas:frame:load:body', () => syncCanvasThemeCss())
  on('load', () => syncCanvasThemeCss())
}

function onCanvasChange(snapshot: YdPageBuilderSnapshot) {
  if (!store.doc || !ready.value || store.busy || !canEdit.value) {
    return
  }
  const previous = targetNodes.value
  const report: StudioParseReport = { mintedIds: 0, orphanRules: 0 }
  let nodes: StudioNode[]
  try {
    nodes = parseStudioHtml(snapshot.htmlContent, snapshot.cssContent, previous, report)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '画布内容解析失败，本次修改未应用')
    resyncCanvas()
    return
  }
  if (JSON.stringify(nodes) === JSON.stringify(previous)) {
    return
  }
  const canonical = compileStudioNodes(nodes)
  const baseline = compileStudioNodes(previous)
  if (canonical.html === baseline.html && canonical.css === baseline.css) {
    return
  }
  try {
    if (store.editRegion === 'page') {
      if (!store.activePageId) {
        return
      }
      store.executeFromCanvas({ type: 'update-page', pageId: store.activePageId, patch: { root: nodes } })
    }
    else {
      store.executeFromCanvas({ type: 'set-theme', [store.editRegion]: nodes })
    }
    // 新铸造的 ID 或孤立 CSS 只存在于画布：不登记回声，让编译结果回灌一次后收敛
    if (report.mintedIds === 0 && report.orphanRules === 0) {
      canvasEcho = { revision: store.editRevision, html: snapshot.htmlContent, css: snapshot.cssContent, project: snapshot.builderProjectJson }
    }
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '命令执行失败，本次修改未应用')
    resyncCanvas()
  }
}

// ===== 键盘快捷键：undo/redo 复制 删除 =====

const TEXT_CONTROLS = 'input, textarea, select'

function elementOf(target: EventTarget | null): Element | null {
  // 画布 iframe 内的元素属于另一个 realm，不能用 instanceof 判定
  return target && typeof (target as Element).closest === 'function' ? target as Element : null
}

function isTypingTarget(target: EventTarget | null): boolean {
  const element = elementOf(target)
  if (!element) {
    return false
  }
  const targetInput = 'isContentEditable' in element ? (element as HTMLElement).isContentEditable : false
  return Boolean(targetInput) || Boolean(element.closest(TEXT_CONTROLS))
}

function isDialogTarget(target: EventTarget | null): boolean {
  return Boolean(elementOf(target)?.closest('[role="dialog"]'))
}

function duplicateSelectedNode() {
  const id = store.selectedNodeId
  if (!id) {
    return
  }
  try {
    store.execute({ type: 'duplicate', nodeId: id })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '复制失败')
  }
}

function removeSelectedNode() {
  const id = store.selectedNodeId
  if (!id) {
    return
  }
  try {
    store.execute({ type: 'remove', nodeId: id })
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '删除失败')
  }
}

/** 主文档与画布文档共用：输入、弹窗、内联内容编辑与引擎工具区不截获 */
function onStudioKeydown(event: Event) {
  const keydown = event as KeyboardEvent
  if (disposed || !canEdit.value || previewMode.value || keydown.repeat || keydown.defaultPrevented || keydown.isComposing) {
    return
  }
  if (document.querySelector('[role="dialog"][data-state="open"]') || store.busy || isTypingTarget(keydown.target) || isDialogTarget(keydown.target)) {
    return
  }
  const editing = facade.value?.editor.getEditing?.()
  if (editing) {
    return
  }
  const key = keydown.key.toLowerCase()
  const mod = keydown.ctrlKey || keydown.metaKey
  const runStoreAction = (action: () => void) => {
    try {
      action()
    }
    catch (error) {
      toast.error(error instanceof Error ? error.message : '操作失败')
    }
  }
  if (mod && key === 'z') {
    keydown.preventDefault()
    runStoreAction(() => {
      if (keydown.shiftKey) {
        store.redo()
      }
      else {
        store.undo()
      }
    })
    return
  }
  if (mod && key === 'y') {
    keydown.preventDefault()
    runStoreAction(() => {
      store.redo()
    })
    return
  }
  if (mod && key === 'd' && !keydown.altKey) {
    keydown.preventDefault()
    duplicateSelectedNode()
    return
  }
  if (!mod && store.selectedNodeId && (key === 'delete' || key === 'backspace')) {
    keydown.preventDefault()
    removeSelectedNode()
  }
}

onMounted(() => {
  window.addEventListener('keydown', onStudioKeydown)
})

onBeforeUnmount(() => {
  disposed = true
  ready.value = false
  facade.value = null
  for (const document of frameDocuments) {
    document.removeEventListener('keydown', onStudioKeydown)
  }
  frameDocuments.clear()
  window.removeEventListener('keydown', onStudioKeydown)
})

// 断点切换到引擎真实设备（媒体查询随之生效），并按新宽度重新适配；
// 区域/页面切换清空选择避免串用
watch(() => store.breakpoint, (value) => {
  if (facade.value && ready.value) {
    facade.value.editor.setDevice(value)
    requestAnimationFrame(() => {
      fitCanvasWidth()
      readZoom()
    })
  }
})
watch(() => [store.editRegion, store.activePageId] as const, () => {
  store.selectedNodeId = ''
})

// 树/面包屑/属性面板改动统一经 store.selectedNodeId 收敛回画布选中
watch(() => store.selectedNodeId, (nodeId) => {
  syncCanvasSelection(nodeId)
})

// 外部改动回灌会整体重建画布，重建完成再把原选中反映回去（画布自身编辑走回声，不重建）
function reselectAfterRebuild() {
  if (!store.selectedNodeId) {
    return
  }
  void nextTick(() => {
    window.setTimeout(() => {
      if (!disposed && ready.value && store.doc && store.selectedNodeId) {
        syncCanvasSelection(store.selectedNodeId)
      }
    }, 0)
  })
}
watch(() => store.editRevision, reselectAfterRebuild)

// ===== 选中节点面包屑（编辑目标根 → 当前节点祖先链） =====

const breadcrumbRootLabel = computed(() => {
  if (store.editRegion === 'page') {
    return `页面：${store.activePage?.name || '—'}`
  }
  return store.editRegion === 'header' ? '页头' : '页脚'
})

const breadcrumbs = computed<StudioNode[]>(() => {
  const selectedId = store.selectedNodeId
  if (!selectedId) {
    return []
  }
  const path: StudioNode[] = []
  const visit = (nodes: StudioNode[], chain: StudioNode[]): boolean => {
    for (const node of nodes) {
      const next = [...chain, node]
      if (node.id === selectedId) {
        path.push(...next)
        return true
      }
      if (visit(node.children, next)) {
        return true
      }
    }
    return false
  }
  visit(targetNodes.value, [])
  return path
})

function crumbLabel(node: StudioNode) {
  return node.text !== undefined && node.text ? `${node.text}` : `${node.type} · ${node.tag}`
}
</script>

<template>
  <div class="studio-design flex flex-col h-full min-h-0">
    <div class="px-3 py-2 border-b bg-card flex flex-shrink-0 flex-wrap gap-2 items-center">
      <YdSegmented v-model="store.editRegion" :options="regionOptions" aria-label="编辑目标" />
      <template v-if="breadcrumbs.length">
        <nav class="text-xs flex flex-wrap gap-1 min-w-0 items-center" aria-label="选中节点路径">
          <YdAction
            type="button"
            class="studio-crumb text-muted-foreground"
            aria-label="清除选择并回到根"
            @click="store.selectedNodeId = ''"
          >
            {{ breadcrumbRootLabel }}
          </YdAction>
          <template v-for="(node, index) in breadcrumbs" :key="node.id">
            <span class="text-muted-foreground" aria-hidden="true">/</span>
            <YdAction
              type="button"
              class="studio-crumb"
              :aria-current="index === breadcrumbs.length - 1 ? 'true' : undefined"
              :aria-label="`选中${crumbLabel(node)}`"
              @click="store.selectedNodeId = node.id"
            >
              {{ crumbLabel(node) }}
            </YdAction>
          </template>
        </nav>
      </template>
      <span v-else class="text-xs text-muted-foreground">
        {{ store.editRegion === 'page' ? breadcrumbRootLabel : '主题公共区域' }}
      </span>
      <span class="flex-1" />
      <YdSegmented v-model="store.breakpoint" :options="breakpointOptions" aria-label="画布断点" />
      <div class="flex gap-0.5 items-center">
        <YdTooltip text="缩小">
          <YdButton
            variant="ghost"
            size="sm"
            aria-label="缩小画布"
            :disabled="!ready || zoom <= ZOOM_MIN"
            @click="applyZoom(zoom - ZOOM_STEP)"
          >
            <YdIcon name="morph:zoom-out" />
          </YdButton>
        </YdTooltip>
        <YdButton
          variant="ghost"
          size="sm"
          class="studio-zoom-value tabular-nums"
          aria-label="重置缩放为 100%"
          :disabled="!ready"
          @click="applyZoom(100)"
        >
          {{ zoom }}%
        </YdButton>
        <YdTooltip text="放大">
          <YdButton
            variant="ghost"
            size="sm"
            aria-label="放大画布"
            :disabled="!ready || zoom >= ZOOM_MAX"
            @click="applyZoom(zoom + ZOOM_STEP)"
          >
            <YdIcon name="morph:zoom-in" />
          </YdButton>
        </YdTooltip>
        <YdTooltip text="适配宽度">
          <YdButton
            variant="ghost"
            size="sm"
            aria-label="画布适配宽度"
            :disabled="!ready"
            @click="fitCanvasWidth"
          >
            <YdIcon name="morph:scan" />
          </YdButton>
        </YdTooltip>
        <YdButton
          variant="ghost"
          size="sm"
          class="ml-1"
          :aria-pressed="previewMode"
          :disabled="!ready"
          @click="togglePreview"
        >
          <YdIcon :name="previewMode ? 'morph:eye-off' : 'morph:eye'" />
          {{ previewMode ? '退出预览' : '预览' }}
        </YdButton>
      </div>
    </div>

    <div ref="canvasHost" class="bg-muted/30 flex-1 min-h-0 relative">
      <YdResult
        v-if="builderError"
        status="error"
        title="画布初始化失败"
        :description="builderError"
      />
      <template v-else-if="canvasInput">
        <div class="p-3 h-full min-h-0 overflow-auto">
          <div class="mx-auto bg-card h-full">
            <YdPageBuilder
              :key="`${store.themeCode}:${store.activePageId}:${store.editRegion}`"
              :disabled="store.busy || !canEdit"
              :html-content="canvasInput.html"
              :css-content="canvasInput.css"
              :builder-project-json="canvasInput.project"
              :editor-options="pageBuilderOptions"
              :setup="setupBuilder"
              height="100%"
              aria-label="站点页面画布"
              class="studio-canvas"
              @ready="onBuilderReady"
              @change="onCanvasChange"
              @error="onBuilderError"
            />
          </div>
        </div>
      </template>
      <YdEmpty
        v-else-if="store.editRegion === 'page' && !store.activePage"
        description="当前站点还没有页面，先在左侧「页面」里新建一个页面"
      />
      <YdLoading v-else-if="store.doc" />
    </div>
  </div>
</template>

<style scoped>
.studio-canvas {
  min-height: 0;
}
.studio-crumb {
  max-width: 9rem;
  overflow: hidden;
  padding: 2px 4px;
  border-radius: var(--yd-control-radius);
  font-size: 0.75rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.studio-crumb[aria-current='true'] {
  background: oklch(var(--muted));
}
.studio-zoom-value {
  min-width: 3.5rem;
  padding-inline: 6px;
  font-variant-numeric: tabular-nums;
}
</style>
