<script setup lang="ts">
import type { AIMessageContent, ChatMessagesData, ChatRequestParams, ChatServiceConfig, SSEChunkData } from '@tdesign-vue-next/chat'
import type { Editor } from 'grapesjs'
import type { CmsAgentSelectOption } from '../config/cms-agent-options'
import type { CmsBlockDefinition, CmsBlockKind } from '../config/cms-blocks'
import type { FileObject } from '@/api/modules/files'
import type { AiToolCallResult, CmsChatHistoryMessage } from '@/api/modules/platform-ai'
import type { CmsAiChatAttachmentMeta, CmsAiChatSession, CmsAiChatSessionSummary } from '@/utils/cms-ai-chat-history'
import type { CmsSiteLayoutMode } from '@/utils/cms-chrome'
import { registerHandler, removeHandler } from '@jboltai/tokui'
import { ActivityRenderer, ChatContent, ChatMessage, ChatSender, ToolCallRenderer, useChat } from '@tdesign-vue-next/chat'
import { Select as TSelect, Switch as TSwitch, Tooltip as TTooltip } from 'tdesign-vue-next'
import apiFiles from '@/api/modules/files'
import apiAi from '@/api/modules/platform-ai'
import apiCms from '@/api/modules/platform-cms'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { clearCmsAiChatTarget, cmsAiChatTargetKey, deleteCmsAiChatSession, getCmsAiChatSession, listCmsAiChatSessionSummaries, saveCmsAiChatSession } from '@/utils/cms-ai-chat-history'
import { canvasFitZoom, chromeCanvasPreviewCss, chromeFrameTemplate, cmsCanvasDevices, extractHomeContent } from '@/utils/cms-chrome'
import { renderCmsMarkdown, renderCmsVariables, resolveCmsTemplateRows, sanitizeCmsHtml } from '@/utils/cms-template-render'
import { resolveCmsAgentCode } from '../config/cms-agent-options'
import { cmsBlocks, toBlockDefinition } from '../config/cms-blocks'
import { orderedAddHtmlAssets } from '../config/cms-canvas-tool-order'
import { isNearChatBottom, scrollChatToBottom } from '../config/cms-chat-auto-scroll'
import { cmsGrapesPlugins, cmsGrapesPluginsOpts, localizeCmsPluginBlocks } from '../config/cms-grapes-plugins'
import { registerCmsAguiRenderers } from './cms-agui-renderers'
import CmsCodeEditor from './CmsCodeEditor.vue'
import TokuiBlock from './TokuiBlock.vue'
import '@tdesign-vue-next/chat/es/style/index.css'
import 'grapesjs/dist/css/grapes.min.css'

registerCmsAguiRenderers()

interface GrapesSavePayload {
  htmlContent: string
  cssContent: string
  jsContent: string
  builderProjectJson: string
}

type RightPanelTab = 'ai' | 'layers' | 'traits' | 'styles' | 'source'
type LeftWorkspace = 'blocks' | 'media'
type CanvasDevice = 'desktop' | 'tablet' | 'mobile'
interface CanvasSelectionSnapshot {
  label: string
  type?: string
  tagName?: string
  selectorHint?: string
  classes: string[]
  attributes: Record<string, unknown>
  styles: Record<string, unknown>
  text: string
  html: string
}

interface AskOption {
  title: string
  desc?: string
}

interface AguiActivityContent {
  type: string
  data: {
    activityType: string
    content: Record<string, unknown>
  }
}

interface AguiToolCallContent {
  type: string
  data: {
    toolCallId: string
    toolCallName: string
  }
}

interface TextStreamContent {
  type: string
  data: string
}

interface CodeCompletionItem {
  label: string
  type?: 'class' | 'constant' | 'enum' | 'function' | 'interface' | 'keyword' | 'namespace' | 'operator' | 'property' | 'text' | 'type' | 'variable'
  apply?: string
  detail?: string
  info?: string
}

const props = defineProps<{
  htmlContent?: string
  cssContent?: string
  jsContent?: string
  builderProjectJson?: string
  title?: string
  aiEnabled?: boolean
  aiAgentOptions?: CmsAgentSelectOption[]
  chromeFrame?: boolean
  chromeLayout?: CmsSiteLayoutMode
  templatePreviewContext?: Record<string, any>
  historyTargetType?: 'page' | 'home'
  historyTargetId?: string | null
  historyTargetLabel?: string
}>()

const emit = defineEmits<{
  close: []
  save: [payload: GrapesSavePayload]
}>()

const toast = useFaToast()
const appSettingsStore = useAppSettingsStore()
const editorEl = ref<HTMLElement>()
const blocksEl = ref<HTMLElement>()
const layersEl = ref<HTMLElement>()
const traitsEl = ref<HTMLElement>()
const stylesEl = ref<HTMLElement>()
const mediaInput = ref<HTMLInputElement>()
const aiAttachmentInput = ref<HTMLInputElement>()
const aiChatScrollEl = ref<HTMLElement>()
const mediaItems = ref<FileObject[]>([])
const loadingMedia = ref(false)
const aiAgentCode = ref('')
const aiThinkingEnabled = ref(false)
const aiPrompt = ref('')
const aiAttachments = ref<any[]>([])
const chatHistorySummaries = ref<CmsAiChatSessionSummary[]>([])
const selectedChatHistory = ref<CmsAiChatSession | null>(null)
const chatHistoryLoading = ref(false)
const chatHistoryHasMore = ref(false)
const chatHistoryOffset = ref(0)
const chatHistoryLoadedOnce = ref(false)
const rightPanelTab = ref<RightPanelTab>(props.aiEnabled ? 'ai' : 'layers')
const leftWorkspace = ref<LeftWorkspace>('blocks')
const leftSidebarCollapsed = ref(false)
const rightSidebarCollapsed = ref(false)
const previewActive = ref(false)
const activeDevice = ref<CanvasDevice>('desktop')
const editorDirty = ref(false)
const editorReady = ref(false)
const canvasRevision = ref(0)
const selectedSourceCode = ref('')
const selectedSourceDirty = ref(false)
const selectedSourceError = ref('')
const pageJsContent = ref(props.jsContent || '')
const aiSuggestions = [
  '只优化当前选中的元素',
  '把选中元素改成更醒目的 CTA',
  '优化首屏视觉，让层次更清晰',
  '新增三列功能卡片并统一按钮样式',
  '根据样图调整版式和配色',
  '优化移动端排版和间距',
]
const aiBlockSuggestions = [
  { label: '添加 Hero 首屏', prompt: '在画布末尾添加一个居中的 Hero 首屏区块' },
  { label: '添加三列特性', prompt: '在画布末尾添加一个三列特性区块' },
  { label: '添加 CTA 行动召唤', prompt: '在画布末尾添加一个 CTA 行动召唤区块' },
  { label: '添加客户评价', prompt: '在画布末尾添加一个客户评价区块' },
]
// AI 需求澄清：当模型调用 cms.ask.user 时，用 TokUI 渲染的一段可点击选项 DSL。
const pendingAskDsl = ref('')
const pendingAskOptions = ref<AskOption[]>([])
let editor: Editor | null = null
let currentAiSession: CmsAiChatSession | null = null
let canvasRefreshTimer: ReturnType<typeof setTimeout> | null = null
let selectedSourceSyncTimer: ReturnType<typeof setTimeout> | null = null
let templatePreviewTimer: ReturnType<typeof setTimeout> | null = null
let pageJsSyncTimer: ReturnType<typeof setTimeout> | null = null
let blockPanelObserver: MutationObserver | null = null
let isRefreshingBlockPanel = false
let aiChatScrollFrame: number | null = null
let aiChatAutoFollow = true

const BLOCK_FAVORITES_KEY = 'yb:cms:block-favorites'
const USER_BLOCKS_KEY = 'yb:cms:user-blocks'
const blockCategories = ['全部', '收藏', '原子', '布局', '文字', '基础组件', '表单', '导航', '媒体', '动态数据', '高级', '预制']
const blockSearch = ref('')
const blockCategory = ref('全部')
const blockFavorites = ref<string[]>(loadBlockFavorites())
const userBlocks = ref<CmsBlockDefinition[]>([])
const backendBlocks = ref<CmsBlockDefinition[]>([])
const canvasZoom = ref(100)
const showSaveBlockDialog = ref(false)
const saveBlockForm = ref({
  name: '',
  category: '预制',
  kind: 'preset' as CmsBlockKind,
  description: '',
})

watch(() => props.templatePreviewContext, () => {
  if (editor) {
    scheduleCanvasTemplatePreview(editor, 120)
  }
}, { deep: true })

watch(() => props.chromeLayout, (layout, previousLayout) => {
  if (!editor || !props.chromeFrame || !layout || layout === previousLayout) {
    return
  }
  rebuildHomeChrome(currentHomeContent(), layout)
}, { flush: 'post' })

const rightPanelTabs = computed(() => {
  const tabs: { label: string, value: RightPanelTab, icon: string }[] = [
    { label: '图层', value: 'layers', icon: 'i-ri:stack-line' },
    { label: '属性', value: 'traits', icon: 'i-ri:settings-3-line' },
    { label: '样式', value: 'styles', icon: 'i-ri:palette-line' },
    { label: '源码', value: 'source', icon: 'i-ri:code-s-slash-line' },
  ]
  return props.aiEnabled
    ? [{ label: 'AI', value: 'ai' as const, icon: 'i-ri:sparkling-2-line' }, ...tabs]
    : tabs
})

const canvasStats = computed(() => {
  if (rightPanelTab.value !== 'ai') {
    return []
  }
  canvasRevision.value
  const instance = editor
  if (!instance) {
    return [
      { label: 'HTML', value: '待加载' },
      { label: 'CSS', value: '待加载' },
      { label: 'JS', value: '待加载' },
      { label: '项目源', value: '待加载' },
    ]
  }
  return [
    { label: 'HTML', value: `${instance.getHtml().length} 字符` },
    { label: 'CSS', value: `${fullCanvasCss(instance).length} 字符` },
    { label: 'JS', value: `${pageJsContent.value.length} 字符` },
    { label: '项目源', value: '保存时生成' },
  ]
})

const selectedCanvasSummary = computed(() => {
  if (rightPanelTab.value !== 'ai') {
    return '未选择元素'
  }
  canvasRevision.value
  const snapshot = currentSelectionSnapshot()
  if (!snapshot) {
    return '未选择元素'
  }
  const name = snapshot.label || snapshot.tagName || '元素'
  const text = compactText(snapshot.text, 24)
  return text ? `${name}：${text}` : name
})

const selectedSourceSummary = computed(() => {
  if (rightPanelTab.value !== 'source') {
    return '未选择'
  }
  canvasRevision.value
  const snapshot = currentSelectionSnapshot()
  if (!snapshot) {
    return '未选择'
  }
  const tag = snapshot.tagName ? `<${snapshot.tagName}>` : snapshot.type || '元素'
  const selector = snapshot.selectorHint ? ` · ${snapshot.selectorHint}` : ''
  return `${tag}${selector}`
})

const hasSelectedComponent = computed(() => {
  canvasRevision.value
  return Boolean(editor?.getSelected())
})

const selectedSourceLocked = computed(() => {
  canvasRevision.value
  return Boolean(props.chromeFrame && isProtectedChromeComponent(editor?.getSelected()))
})


const cmsVariableContext = computed(() => {
  const copyright = appSettingsStore.settings.app.copyright
  const siteName = appSettingsStore.siteName || 'YuDream'
  const siteDescription = appSettingsStore.siteDescription || ''
  const logo = appSettingsStore.logo || ''
  const currentYear = String(new Date().getFullYear())
  return {
    syntax: '{{path.to.value}}',
    usage: '生成 CMS HTML 时优先保留变量占位符，最终由公开站点渲染时替换；不要把站点名称、系统 Logo、版权年份等系统值写死。',
    variables: [
      { key: '{{cms.pages.latest.count}}', label: 'Latest CMS page count', example: '6' },
      { key: '{{knowledge.spaces.count}}', label: 'Public knowledge space count', example: '2' },
      { key: '{{knowledge.pages.count}}', label: 'Published knowledge page count', example: '12' },
      { key: '{{site.name}}', label: '站点名称', example: siteName },
      { key: '{{site.description}}', label: '站点描述', example: siteDescription || '当前页面摘要或站点描述' },
      { key: '{{site.logo}}', label: '站点 Logo URL', example: logo, html: '<img src="{{site.logo}}" alt="{{site.name}}">' },
      { key: '{{system.name}}', label: '系统/站点名称别名', example: siteName },
      { key: '{{system.description}}', label: '系统描述别名', example: siteDescription || '站点描述' },
      { key: '{{system.logo}}', label: '系统 Logo URL 别名', example: logo, html: '<img src="{{system.logo}}" alt="{{system.name}}">' },
      { key: '{{system.currentYear}}', label: '当前年份', example: currentYear },
      { key: '{{system.copyright.company}}', label: '版权公司', example: copyright.company || '' },
      { key: '{{system.copyright.website}}', label: '版权网站', example: copyright.website || '' },
      { key: '{{system.copyright.dates}}', label: '版权年份范围', example: copyright.dates || currentYear },
      { key: '{{page.title}}', label: '页面标题', example: props.title || '当前页面' },
      { key: '{{page.summary}}', label: '页面摘要', example: '页面摘要' },
      { key: '{{auth.welcome}}', label: '登录欢迎语', example: '欢迎回来，用户昵称' },
      { key: '{{user.nickname}}', label: '当前用户昵称', example: '访客或用户昵称' },
      { key: '{{navigation.count}}', label: '导航数量', example: '3' },
      { key: '{{pages.count}}', label: '公开页面数量', example: '6' },
    ],
    repeats: [
      { key: 'data-yb-repeat="cms.pages.latest"', itemFields: ['{{item.title}}', '{{item.url}}', '{{item.summary}}'], description: 'Latest published CMS pages' },
      { key: 'data-yb-repeat="knowledge.pages"', itemFields: ['{{item.title}}', '{{item.url}}', '{{item.summary}}'], description: 'Published knowledge pages' },
      { key: 'data-yb-repeat="knowledge.latest"', itemFields: ['{{item.title}}', '{{item.url}}', '{{item.content}}'], description: 'Latest knowledge content' },
      { key: 'data-yb-repeat="knowledge.spaces"', itemFields: ['{{item.title}}', '{{item.url}}', '{{item.summary}}'], description: 'Public knowledge spaces' },
      { key: 'data-yb-repeat="navigation"', itemFields: ['{{item.label}}', '{{item.url}}'], description: '重复渲染导航项' },
      { key: 'data-yb-repeat="pages"', itemFields: ['{{item.title}}', '{{item.url}}', '{{item.summary}}'], description: '重复渲染公开页面卡片' },
      { key: 'data-yb-repeat="categories"', itemFields: ['{{item.name}}', '{{item.url}}', '{{item.count}}'], description: '重复渲染分类' },
      { key: 'data-yb-repeat="tags"', itemFields: ['{{item.name}}', '{{item.url}}', '{{item.count}}'], description: '重复渲染标签' },
    ],
    visibility: [
      { key: 'data-visible-when="guest"', description: '仅访客可见' },
      { key: 'data-visible-when="logged-in"', description: '仅登录用户可见' },
    ],
  }
})

const cmsVariableCount = computed(() => cmsVariableContext.value.variables.length)

const favoriteBlockIds = computed(() => new Set(blockFavorites.value))

const selectedBreadcrumbs = computed(() => {
  canvasRevision.value
  const component = editor?.getSelected() as any
  if (!component)
    return []
  const crumbs: { label: string; component: any }[] = []
  let current: any = component
  while (current) {
    const label = String(current.getName?.() || current.get?.('tagName') || current.get?.('type') || '元素')
    crumbs.unshift({ label, component: current })
    current = current.parent?.()
  }
  return crumbs
})

const htmlSourceCompletions = computed<CodeCompletionItem[]>(() => {
  if (rightPanelTab.value !== 'source') {
    return []
  }
  canvasRevision.value
  const context = cmsVariableContext.value
  const snapshot = currentSelectionSnapshot()
  const tagCompletions = ['section', 'article', 'div', 'header', 'footer', 'main', 'nav', 'h1', 'h2', 'h3', 'p', 'a', 'button', 'img', 'ul', 'li']
    .map(tag => ({
      label: tag,
      type: 'type' as const,
      apply: `<${tag}></${tag}>`,
      detail: 'HTML 标签',
    }))
  const attributeCompletions: CodeCompletionItem[] = [
    { label: 'class', type: 'property', apply: 'class=""', detail: 'CSS 类名' },
    { label: 'id', type: 'property', apply: 'id=""', detail: '元素 ID' },
    { label: 'href', type: 'property', apply: 'href=""', detail: '链接地址' },
    { label: 'src', type: 'property', apply: 'src=""', detail: '资源地址' },
    { label: 'alt', type: 'property', apply: 'alt=""', detail: '图片替代文本' },
    { label: 'data-yb-repeat', type: 'property', apply: 'data-yb-repeat=""', detail: 'CMS 循环数据' },
    { label: 'data-visible-when', type: 'property', apply: 'data-visible-when=""', detail: 'CMS 可见条件' },
  ]
  const variableCompletions = context.variables.map(item => ({
    label: item.key,
    type: 'variable' as const,
    apply: item.key,
    detail: item.label,
    info: item.example ? `示例：${item.example}` : undefined,
  }))
  const repeatCompletions = context.repeats.map(item => ({
    label: item.key,
    type: 'property' as const,
    apply: item.key,
    detail: 'CMS 循环',
    info: `${item.description}：${item.itemFields.join('、')}`,
  }))
  const visibilityCompletions = context.visibility.map(item => ({
    label: item.key,
    type: 'property' as const,
    apply: item.key,
    detail: item.description,
  }))
  const snippetCompletions: CodeCompletionItem[] = [
    { label: 'repeat.cms.latest', type: 'property', apply: '<section data-yb-repeat="cms.pages.latest">\n  <a href="{{item.url}}">{{item.title}}</a>\n  <p>{{item.summary}}</p>\n</section>', detail: 'Latest CMS pages' },
    { label: 'repeat.knowledge.latest', type: 'property', apply: '<section data-yb-repeat="knowledge.latest">\n  <a href="{{item.url}}">{{item.title}}</a>\n  <p>{{item.summary}}</p>\n</section>', detail: 'Latest knowledge pages' },
    { label: 'content.html', type: 'property', apply: '<div data-yb-html="{{item.htmlContent}}"></div>', detail: 'Sanitized HTML content' },
    { label: 'content.markdown', type: 'property', apply: '<div data-yb-markdown="{{item.markdownContent}}"></div>', detail: 'Markdown content' },
    { label: 'section.yb-ai-section', type: 'class', apply: '<section class="yb-ai-section">\n  \n</section>', detail: 'CMS 区块片段' },
    { label: 'img.site.logo', type: 'variable', apply: '<img src="{{site.logo}}" alt="{{site.name}}">', detail: '系统 Logo 图片' },
    { label: 'a.cms.link', type: 'type', apply: '<a href="">\n  {{page.title}}\n</a>', detail: '链接片段' },
    { label: 'repeat.navigation', type: 'property', apply: '<nav data-yb-repeat="navigation">\n  <a href="{{item.url}}">{{item.label}}</a>\n</nav>', detail: '导航循环' },
    { label: 'visible.guest', type: 'property', apply: '<div data-visible-when="guest">\n  \n</div>', detail: '访客可见' },
    { label: 'visible.logged-in', type: 'property', apply: '<div data-visible-when="logged-in">\n  \n</div>', detail: '登录用户可见' },
  ]
  const currentClassCompletions = (snapshot?.classes || []).map(item => ({
    label: `.${item}`,
    type: 'class' as const,
    apply: item,
    detail: '当前元素类名',
  }))
  return [
    ...snippetCompletions,
    ...variableCompletions,
    ...repeatCompletions,
    ...visibilityCompletions,
    ...attributeCompletions,
    ...currentClassCompletions,
    ...tagCompletions,
  ]
})

const cssSourceCompletions = computed<CodeCompletionItem[]>(() => {
  if (rightPanelTab.value !== 'source') {
    return []
  }
  canvasRevision.value
  const snapshot = currentSelectionSnapshot()
  const selectors: CodeCompletionItem[] = []
  const id = String(snapshot?.attributes.id || '').trim()
  if (id) {
    selectors.push({ label: `#${id}`, type: 'class', apply: `#${cssEscape(id)}`, detail: '当前元素 ID 选择器' })
  }
  snapshot?.classes.forEach((item) => {
    selectors.push({ label: `.${item}`, type: 'class', apply: `.${cssEscape(item)}`, detail: '当前元素类选择器' })
  })
  if (snapshot?.tagName) {
    selectors.push({ label: snapshot.tagName, type: 'type', apply: snapshot.tagName, detail: '当前元素标签选择器' })
  }
  return [
    ...selectors,
    { label: 'color', type: 'property', apply: 'color: ;', detail: '文本颜色' },
    { label: 'background', type: 'property', apply: 'background: ;', detail: '背景' },
    { label: 'display', type: 'property', apply: 'display: ;', detail: '布局方式' },
    { label: 'grid-template-columns', type: 'property', apply: 'grid-template-columns: ;', detail: '网格列' },
    { label: 'gap', type: 'property', apply: 'gap: ;', detail: '间距' },
    { label: 'padding', type: 'property', apply: 'padding: ;', detail: '内边距' },
    { label: 'margin', type: 'property', apply: 'margin: ;', detail: '外边距' },
    { label: 'border-radius', type: 'property', apply: 'border-radius: ;', detail: '圆角' },
  ]
})

const jsSourceCompletions = computed<CodeCompletionItem[]>(() => {
  if (rightPanelTab.value !== 'source') {
    return []
  }
  canvasRevision.value
  const snapshot = currentSelectionSnapshot()
  const selectors: CodeCompletionItem[] = []
  const id = String(snapshot?.attributes.id || '').trim()
  if (id) {
    selectors.push({
      label: `query #${id}`,
      type: 'function',
      apply: `document.querySelector('#${cssEscape(id)}')`,
      detail: '当前元素查询',
    })
  }
  snapshot?.classes.forEach((item) => {
    selectors.push({
      label: `query .${item}`,
      type: 'function',
      apply: `document.querySelector('.${cssEscape(item)}')`,
      detail: '当前类名查询',
    })
  })
  return [
    ...selectors,
    { label: 'cmsContext', type: 'variable', apply: 'window.__YU_CMS_CONTEXT__', detail: 'CMS 运行时上下文' },
    { label: 'cmsReady', type: 'function', apply: 'window.__YU_CMS_READY__?.(() => {\n  \n})', detail: 'CMS 页面就绪后执行' },
    { label: 'registerCleanup', type: 'function', apply: 'window.__YU_CMS_REGISTER_CLEANUP__?.(() => {\n  \n})', detail: '注册脚本清理函数' },
    { label: 'DOMContentLoaded', type: 'function', apply: 'document.addEventListener(\'DOMContentLoaded\', () => {\n  \n})', detail: '页面加载完成' },
    { label: 'querySelector', type: 'function', apply: 'document.querySelector(\'\')', detail: '查询单个元素' },
    { label: 'querySelectorAll', type: 'function', apply: 'document.querySelectorAll(\'\').forEach((el) => {\n  \n})', detail: '批量查询元素' },
    { label: 'addEventListener', type: 'function', apply: 'addEventListener(\'click\', () => {\n  \n})', detail: '绑定事件' },
    { label: 'classList.toggle', type: 'function', apply: 'classList.toggle(\'is-active\')', detail: '切换类名' },
  ]
})

const selectedRelatedCss = computed(() => {
  if (rightPanelTab.value !== 'source') {
    return '/* 打开源码面板后显示关联 CSS */'
  }
  canvasRevision.value
  const snapshot = currentSelectionSnapshot()
  if (!snapshot) {
    return '/* 选择画布元素后显示关联 CSS */'
  }
  const relatedCss = extractRelatedCss(fullCanvasCss(), snapshot)
  return relatedCss ? formatCssText(relatedCss) : '/* 暂未匹配到选中元素的关联 CSS */'
})

const chatHistoryTargetType = computed(() => props.historyTargetType || 'page')
const chatHistoryTargetId = computed(() => String(props.historyTargetId || props.title || 'draft'))
const chatHistoryTargetKey = computed(() => cmsAiChatTargetKey(chatHistoryTargetType.value, chatHistoryTargetId.value))
const chatHistoryTargetLabel = computed(() => props.historyTargetLabel || props.title || (chatHistoryTargetType.value === 'home' ? '首页' : '未命名页面'))
const selectedAiAgentOption = computed(() => props.aiAgentOptions?.find(item => item.value === aiAgentCode.value) || null)

const aiDefaultMessages = computed<ChatMessagesData[]>(() => [
  {
    id: 'cms-ai-welcome',
    role: 'assistant',
    status: 'complete',
    content: [
      {
        type: 'markdown',
        data: '我可以读取当前 GrapesJS 画布、CSS、JS 和项目 JSON，并按你的描述直接修改或增加区块。你也可以附一张样图让我参考。',
      },
    ],
  },
])

const aiSenderProps = computed(() => ({
  loading: false,
  placeholder: '输入 / 唤起插件和技能，或描述要如何修改当前画布',
  textareaProps: {
    autosize: { minRows: 3, maxRows: 6 },
  },
  attachmentsProps: {
    items: aiAttachments.value,
    overflow: 'scrollX' as const,
  },
  actions: ['uploadImage', 'send'],
  onFileSelect: async (event: CustomEvent<File[]>) => {
    await attachAiFiles(Array.from(event.detail || []))
  },
  onFileRemove: (event: CustomEvent<any[]>) => {
    aiAttachments.value = event.detail || []
  },
} as any))

async function attachAiFiles(files: File[]) {
  try {
    aiAttachments.value = await Promise.all(files.map(toAttachmentItem))
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '样图读取失败')
  }
}

function selectAiAttachment() {
  aiAttachmentInput.value?.click()
}

function onAiAttachmentInput(event: Event) {
  const input = event.target as HTMLInputElement
  void attachAiFiles(Array.from(input.files || []))
  input.value = ''
}

const aiMessageProps = {
  avatar: (item: { role?: string }) => item.role === 'user' ? '你' : 'AI',
  name: (item: { role?: string }) => item.role === 'user' ? '你' : 'YuDream AI',
} as any

const aiChatServiceConfig = computed<ChatServiceConfig>(() => ({
  endpoint: apiAi.generateCmsPageStreamEndpoint(),
  stream: true,
  protocol: 'agui',
  timeout: 1_800_000,
  onRequest: async (params: ChatRequestParams) => {
    if (!editor || !props.aiEnabled) {
      throw new Error('AI 能力未启用')
    }
    const prompt = String(params.prompt || '').trim()
    if (!prompt && aiAttachments.value.length === 0) {
      throw new Error('请输入修改想法或添加样图')
    }
    const attachments = params.attachments || aiAttachments.value
    pendingAskDsl.value = ''
    const session = await ensureActiveSession()
    const history = collectHistoryMessages(session)
    appendUserTurn(session, prompt, attachments)
    return apiAi.generateCmsPageStreamRequest(buildAiPayload(prompt, attachments, history))
  },
  onMessage: (chunk: SSEChunkData, _message, parsedResult) => {
    const tool = aguiToolResult(chunk)
    if (tool) {
      if (isCanvasTool(tool)) {
        applyAiTool(tool)
      }
      if (isAskUserTool(tool)) {
        showAskUserUi(tool)
      }
    }
    trackAguiHistoryContent(parsedResult)
    return parsedResult || null
  },
  onComplete: (isAborted: boolean) => {
    if (isAborted) {
      return
    }
    clearAiAttachments()
    void finishAiHistorySession('complete')
  },
  onError: (error: Error | Response) => {
    const message = error instanceof Response ? `${error.status} ${error.statusText}` : error.message
    trackAiHistoryContent({
      ...markdownChunk(`\n\n调用失败：${message}`),
      status: 'error',
    } as AIMessageContent)
    void finishAiHistorySession('error')
    toast.error('AI 调用失败', { description: message })
  },
}))

const {
  chatEngine: aiChatEngine,
  messages: aiChatMessages,
  status: aiChatStatus,
} = useChat({
  defaultMessages: aiDefaultMessages.value,
  chatServiceConfig: aiChatServiceConfig.value,
})

const aiChatLoading = computed(() => aiChatStatus.value === 'pending' || aiChatStatus.value === 'streaming')
const aiRenderedMessages = computed(() => aiChatMessages.value.map(message => ({
  message,
  contents: messageContents(message),
})))

watch(aiRenderedMessages, () => {
  scheduleAiChatScroll()
}, { flush: 'post' })

function onAiChatScroll() {
  if (aiChatScrollEl.value) {
    aiChatAutoFollow = isNearChatBottom(aiChatScrollEl.value)
  }
}

function scheduleAiChatScroll(force = false) {
  if (force) {
    aiChatAutoFollow = true
  }
  if (!aiChatAutoFollow || aiChatScrollFrame !== null) {
    return
  }
  aiChatScrollFrame = requestAnimationFrame(() => {
    aiChatScrollFrame = null
    if (aiChatAutoFollow && aiChatScrollEl.value) {
      scrollChatToBottom(aiChatScrollEl.value)
    }
  })
}

async function sendAiPrompt(prompt: string) {
  const content = String(prompt || aiPrompt.value || '').trim()
  if (!content && aiAttachments.value.length === 0) {
    return
  }
  aiPrompt.value = ''
  scheduleAiChatScroll(true)
  await aiChatEngine.value.sendUserMessage({
    prompt: content,
    attachments: aiAttachments.value,
  })
}

function onAiFollowUp(event: CustomEvent<string>) {
  const message = event.detail
  if (typeof message === 'string' && message.trim()) {
    void sendAiPrompt(message.trim())
  }
}

function messageContents(item: ChatMessagesData): any[] {
  const source = Array.isArray(item.content) ? item.content : [item.content]
  const merged: any[] = []
  const activityIndexes = new Map<string, number>()
  const toolCallIndexes = new Map<string, number>()

  source.forEach((content) => {
    if (isAguiActivityContent(content)) {
      const activityType = String(content.data.activityType)
      const index = activityIndexes.get(activityType)
      if (index === undefined) {
        activityIndexes.set(activityType, merged.length)
        merged.push(content)
      }
      else {
        merged[index] = content
      }
      return
    }

    if (isAguiToolCallContent(content)) {
      const toolCallId = String(content.data?.toolCallId || content.type)
      const index = toolCallIndexes.get(toolCallId)
      if (index === undefined) {
        toolCallIndexes.set(toolCallId, merged.length)
        merged.push(content)
      }
      else {
        merged[index] = content
      }
      return
    }

    const previous = merged.at(-1)
    if (isTextStreamContent(previous) && isTextStreamContent(content) && previous.type === content.type) {
      merged[merged.length - 1] = {
        ...content,
        data: `${previous.data || ''}${content.data || ''}`,
      }
      return
    }
    merged.push(content)
  })
  return merged
}

function isTextStreamContent(content: unknown): content is TextStreamContent {
  if (!content || typeof content !== 'object') {
    return false
  }
  const value = content as TextStreamContent
  return typeof value.type === 'string'
    && !value.type.startsWith('activity-')
    && !value.type.startsWith('toolcall-')
    && !['reasoning', 'thinking'].includes(value.type)
    && typeof value.data === 'string'
}

function isAguiActivityContent(content: unknown): content is AguiActivityContent {
  if (!content || typeof content !== 'object') {
    return false
  }
  const value = content as AguiActivityContent
  return typeof value.type === 'string'
    && value.type.startsWith('activity-')
    && typeof value.data?.activityType === 'string'
    && Boolean(value.data.activityType)
    && Boolean(value.data.content && typeof value.data.content === 'object')
}

function isAguiToolCallContent(content: unknown): content is AguiToolCallContent {
  if (!content || typeof content !== 'object') {
    return false
  }
  const value = content as AguiToolCallContent
  return typeof value.type === 'string'
    && value.type.startsWith('toolcall-')
    && typeof value.data?.toolCallId === 'string'
    && typeof value.data.toolCallName === 'string'
}

watch(() => props.aiEnabled, (enabled) => {
  if (!enabled && rightPanelTab.value === 'ai') {
    rightPanelTab.value = 'layers'
  }
  if (enabled && !rightPanelTabs.value.some(tab => tab.value === rightPanelTab.value)) {
    rightPanelTab.value = 'ai'
  }
})

watch(() => props.aiAgentOptions, (options) => {
  aiAgentCode.value = resolveCmsAgentCode(options || [], aiAgentCode.value)
}, { deep: true })

watch([rightPanelTab, chatHistoryTargetKey], () => {
  if (rightPanelTab.value === 'ai') {
    void loadChatHistory(true)
    void restoreActiveSession()
  }
  if (rightPanelTab.value === 'source') {
    syncSelectedSource()
  }
})

watch(pageJsContent, () => {
  canvasRevision.value += 1
  if (editorReady.value) {
    editorDirty.value = true
  }
  scheduleCanvasJsSync()
})

watch([blockSearch, blockCategory, blockFavorites], () => {
  refreshBlockPanel()
}, { flush: 'post' })

onMounted(async () => {
  const { default: grapes } = await import('grapesjs')
  editor = grapes.init({
    container: editorEl.value!,
    height: '100%',
    width: 'auto',
    fromElement: false,
    storageManager: false,
    undoManager: { trackSelection: false },
    selectorManager: { componentFirst: true },
    keepUnusedStyles: true,
    blockManager: { appendTo: blocksEl.value! },
    layerManager: { appendTo: layersEl.value! },
    traitManager: { appendTo: traitsEl.value! },
    styleManager: {
      appendTo: stylesEl.value!,
      sectors: styleSectors(),
    },
    deviceManager: {
      devices: cmsCanvasDevices(),
    },
    canvas: {
      styles: [],
    },
    plugins: cmsGrapesPlugins(),
    pluginsOpts: cmsGrapesPluginsOpts(),
    panels: { defaults: [] },
  })
  localizeCmsPluginBlocks(editor)
  registerDynamicTypes(editor)
  registerBlocks(editor, false)
  loadAndRegisterUserBlocks(editor, false)
  editor.BlockManager.render()
  observeBlockPanel()
  refreshBlockPanel()
  readCanvasZoom()
  requestAnimationFrame(fitCanvasToWorkspace)
  window.addEventListener('keydown', onKeyDown, true)
  window.addEventListener('cms-ai-follow-up', onAiFollowUp as EventListener)
  loadInitialContent(editor)
  removeLayoutBlocks(editor)
  injectCanvasHighlightStyle(editor)
  injectCanvasChromePreviewStyle(editor)
  scheduleCanvasTemplatePreview(editor, 80)
  editor.on('component:add component:remove style:update undo redo', () => {
    editorDirty.value = true
    scheduleCanvasRefresh(80)
    scheduleSelectedSourceSync(80)
    scheduleCanvasTemplatePreview(editor!, 100)
  })
  editor.on('load', () => injectCanvasChromePreviewStyle(editor!))
  editor.on('load', () => refreshBlockPanel())
  editor.on('component:update', () => {
    scheduleCanvasRefresh(220)
    scheduleSelectedSourceSync(220)
    scheduleCanvasTemplatePreview(editor!, 260)
  })
  editor.on('component:selected component:deselected', () => {
    clearScheduledCanvasWork()
    canvasRevision.value += 1
    syncSelectedSource(true)
  })
  canvasRevision.value += 1
  editorReady.value = true
  readCanvasZoom()
  scheduleCanvasJsSync(250)
  const mountedEditor = editor
  requestAnimationFrame(() => {
    if (editor === mountedEditor) {
      void loadAndRegisterBackendBlocks(mountedEditor)
    }
  })
  await loadMedia()
  aiAgentCode.value = resolveCmsAgentCode(props.aiAgentOptions || [], aiAgentCode.value)
  registerHandler('pick', onPickOption)
  if (rightPanelTab.value === 'ai') {
    void loadChatHistory(true)
    void restoreActiveSession()
  }
})

onBeforeUnmount(() => {
  editorReady.value = false
  clearScheduledCanvasWork()
  removeHandler('pick')
  window.removeEventListener('keydown', onKeyDown, true)
  window.removeEventListener('cms-ai-follow-up', onAiFollowUp as EventListener)
  blockPanelObserver?.disconnect()
  blockPanelObserver = null
  if (aiChatScrollFrame !== null) {
    cancelAnimationFrame(aiChatScrollFrame)
    aiChatScrollFrame = null
  }
  runCanvasJsDisposers(editor?.Canvas.getDocument()?.defaultView)
  editor?.destroy()
  editor = null
})

function loadInitialContent(instance: Editor) {
  if (props.chromeFrame) {
    instance.setComponents(chromeFrameTemplate(props.htmlContent || '', props.chromeLayout))
    if (props.cssContent) {
      instance.setStyle(props.cssContent)
    }
    lockChromeFrameComponents(instance)
    return
  }
  if (props.builderProjectJson) {
    try {
      instance.loadProjectData(JSON.parse(props.builderProjectJson))
      return
    }
    catch {
      toast.warning('构建器源数据解析失败，已回退 HTML 内容')
    }
  }
  instance.setComponents(props.htmlContent || starterHtml())
  if (props.cssContent) {
    instance.setStyle(props.cssContent)
  }
}

function scheduleCanvasRefresh(delay = 160) {
  if (canvasRefreshTimer) {
    clearTimeout(canvasRefreshTimer)
  }
  canvasRefreshTimer = setTimeout(() => {
    canvasRefreshTimer = null
    canvasRevision.value += 1
  }, delay)
}

function scheduleSelectedSourceSync(delay = 160) {
  if (rightPanelTab.value !== 'source' || selectedSourceDirty.value) {
    return
  }
  if (selectedSourceSyncTimer) {
    clearTimeout(selectedSourceSyncTimer)
  }
  selectedSourceSyncTimer = setTimeout(() => {
    selectedSourceSyncTimer = null
    syncSelectedSource()
  }, delay)
}

function clearScheduledCanvasWork() {
  if (canvasRefreshTimer) {
    clearTimeout(canvasRefreshTimer)
    canvasRefreshTimer = null
  }
  if (selectedSourceSyncTimer) {
    clearTimeout(selectedSourceSyncTimer)
    selectedSourceSyncTimer = null
  }
  if (templatePreviewTimer) {
    clearTimeout(templatePreviewTimer)
    templatePreviewTimer = null
  }
  if (pageJsSyncTimer) {
    clearTimeout(pageJsSyncTimer)
    pageJsSyncTimer = null
  }
}

function save() {
  if (!editor) {
    return
  }
  removeLayoutBlocks(editor)
  emit('save', {
    htmlContent: props.chromeFrame ? extractHomeContent(editor.getHtml()) : editor.getHtml(),
    cssContent: fullCanvasCss(),
    jsContent: stripScriptTags(pageJsContent.value),
    builderProjectJson: props.chromeFrame ? '' : JSON.stringify(editor.getProjectData()),
  })
  editorDirty.value = false
}

function onKeyDown(event: KeyboardEvent) {
  const ctrlOrCmd = event.ctrlKey || event.metaKey
  if (ctrlOrCmd && event.key.toLowerCase() === 's') {
    event.preventDefault()
    save()
    return
  }
  if (ctrlOrCmd && event.key.toLowerCase() === 'z') {
    event.preventDefault()
    if (event.shiftKey) {
      editor?.runCommand('core:redo')
    }
    else {
      editor?.runCommand('core:undo')
    }
    return
  }
  if ((event.key === 'Delete' || event.key === 'Backspace') && editor?.getSelected() && !isEditingText()) {
    event.preventDefault()
    editor.runCommand('core:component-delete')
  }
}

function isEditingText() {
  const active = document.activeElement
  if (!active)
    return false
  if (['INPUT', 'TEXTAREA'].includes(active.tagName))
    return true
  return active.getAttribute('contenteditable') === 'true' || active.closest('[contenteditable="true"]') !== null
}

function readCanvasZoom() {
  try {
    const zoom = (editor?.Canvas as any)?.getZoom?.()
    if (typeof zoom === 'number') {
      canvasZoom.value = zoom > 10 ? Math.round(zoom) : Math.round(zoom * 100)
    }
    else {
      canvasZoom.value = 100
    }
  }
  catch {
    canvasZoom.value = 100
  }
}

function resetCanvasZoom() {
  try {
    (editor?.Canvas as any)?.setZoom?.(100)
    readCanvasZoom()
  }
  catch {
    // ignore
  }
}

function duplicateSelected() {
  const selected = editor?.getSelected() as any
  if (!selected)
    return
  const clone = selected.clone?.()
  const parent = selected.parent?.()
  if (clone && parent) {
    parent.append(clone)
    editor?.select(clone)
  }
}

function loadBlockFavorites(): string[] {
  try {
    const raw = localStorage.getItem(BLOCK_FAVORITES_KEY)
    return raw ? JSON.parse(raw) : []
  }
  catch {
    return []
  }
}

function persistBlockFavorites() {
  localStorage.setItem(BLOCK_FAVORITES_KEY, JSON.stringify(blockFavorites.value))
}

function toggleBlockFavorite(id: string) {
  const set = new Set(blockFavorites.value)
  if (set.has(id))
    set.delete(id)
  else
    set.add(id)
  blockFavorites.value = Array.from(set)
  persistBlockFavorites()
  refreshBlockPanel()
}

function loadUserBlocks(): CmsBlockDefinition[] {
  try {
    const raw = localStorage.getItem(USER_BLOCKS_KEY)
    return raw ? JSON.parse(raw) : []
  }
  catch {
    return []
  }
}

function persistUserBlocks(blocks: CmsBlockDefinition[]) {
  localStorage.setItem(USER_BLOCKS_KEY, JSON.stringify(blocks))
}

function loadAndRegisterUserBlocks(instance: Editor, render = true) {
  userBlocks.value = loadUserBlocks().map(normalizeBlockAttributes)
  userBlocks.value.forEach(block => instance.BlockManager.add(block.id, block, { silent: true }))
  if (render) {
    instance.BlockManager.render()
  }
}

function changeCanvasZoom(delta: number) {
  try {
    const next = Math.min(150, Math.max(25, canvasZoom.value + delta))
    ;(editor?.Canvas as any)?.setZoom?.(next)
    canvasZoom.value = next
  }
  catch {
    // Canvas zoom is optional in older GrapesJS builds.
  }
}

function togglePreview() {
  if (!editor) {
    return
  }
  if (previewActive.value) {
    editor.stopCommand('preview')
  }
  else {
    editor.runCommand('preview')
  }
  previewActive.value = !previewActive.value
}

async function loadAndRegisterBackendBlocks(instance: Editor) {
  try {
    const res = await apiCms.blockList({ page: 1, size: 200, kind: undefined })
    const blocks = (res.data.records || [])
      .filter(block => block.enabled)
      .map(toBlockDefinition)
      .map(normalizeBlockAttributes)
    if (editor !== instance) {
      return
    }
    backendBlocks.value = blocks
    const builtInIds = new Set(cmsBlocks().map(block => block.id))
    blocks.forEach((block) => {
      if (!builtInIds.has(block.id)) {
        instance.BlockManager.add(block.id, block, { silent: true })
      }
    })
    instance.BlockManager.render()
    refreshBlockPanel()
  }
  catch (error) {
    console.warn('[YuDream CMS] 后端区块加载失败', error)
  }
}

function observeBlockPanel() {
  if (!blocksEl.value || blockPanelObserver)
    return
  blockPanelObserver = new MutationObserver((mutations) => {
    const blockTreeChanged = mutations.some(mutation => Array.from(mutation.addedNodes).some(isBlockPanelTreeNode))
    if (blockTreeChanged && !isRefreshingBlockPanel)
      refreshBlockPanel()
  })
  blockPanelObserver.observe(blocksEl.value, { childList: true, subtree: true })
}

function isBlockPanelTreeNode(node: Node) {
  if (!(node instanceof Element)) {
    return false
  }
  return node.matches('.gjs-block, .gjs-block-category, .gjs-blocks-c')
    || Boolean(node.querySelector('.gjs-block, .gjs-block-category, .gjs-blocks-c'))
}

function refreshBlockPanel() {
  if (!editor || !blocksEl.value)
    return
  isRefreshingBlockPanel = true
  try {
    const blocks = blocksEl.value.querySelectorAll('.gjs-block')
    blocks.forEach((el) => {
      const htmlEl = el as HTMLElement
      const id = htmlEl.dataset.id || ''
      const block = editor!.BlockManager.get(id) as any
      const def = block ? (block.attributes as CmsBlockDefinition) : null
      htmlEl.classList.toggle('hidden', !blockMatchesFilter(id, block))

      let badge = htmlEl.querySelector('.gjs-block-kind-badge') as HTMLElement | null
      if (def?.kind && !badge) {
        badge = document.createElement('span')
        badge.className = 'gjs-block-kind-badge'
        htmlEl.appendChild(badge)
      }
      if (def?.kind && badge) {
        const badgeText = def.kind === 'atomic' ? '原子' : '预制'
        if (badge.textContent !== badgeText) {
          badge.textContent = badgeText
        }
        if (badge.dataset.kind !== def.kind) {
          badge.dataset.kind = def.kind
        }
      }

      let star = htmlEl.querySelector('.gjs-block-favorite') as HTMLButtonElement | null
      if (!star) {
        star = document.createElement('button')
        star.type = 'button'
        star.className = 'gjs-block-favorite'
        star.title = '收藏'
        star.addEventListener('click', (e) => {
          e.stopPropagation()
          toggleBlockFavorite(id)
        })
        htmlEl.appendChild(star)
      }
      star.classList.toggle('is-active', favoriteBlockIds.value.has(id))
    })
  }
  finally {
    isRefreshingBlockPanel = false
  }
}

function openSaveBlockDialog() {
  const selected = editor?.getSelected() as any
  if (!selected) {
    toast.warning('请先在画布中选择一个元素')
    return
  }
  saveBlockForm.value = {
    name: String(selected.getName?.() || selected.get?.('tagName') || '自定义区块'),
    category: '预制',
    kind: 'preset',
    description: '',
  }
  showSaveBlockDialog.value = true
}

async function confirmSaveBlock() {
  const name = saveBlockForm.value.name.trim()
  if (!name) {
    toast.warning('请输入区块名称')
    return
  }
  const selected = editor?.getSelected() as any
  if (!selected || !editor)
    return
  const html = String(selected.toHTML?.() || '')
  const css = fullCanvasCss()
  const category = saveBlockForm.value.category.trim() || '预制'
  const kind = saveBlockForm.value.kind
  const description = saveBlockForm.value.description.trim()
  const code = `user-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
  const payload = {
    code,
    name,
    description,
    category,
    kind: kind === 'preset' ? 'PRESET' as const : 'ATOMIC' as const,
    htmlContent: html,
    cssContent: css,
    jsContent: '',
    enabled: true,
  }
  try {
    await apiCms.createBlock(payload)
    toast.success('已保存为后端区块')
    showSaveBlockDialog.value = false
    await loadAndRegisterBackendBlocks(editor)
    refreshBlockPanel()
  }
  catch (error) {
    console.warn('[YuDream CMS] 后端区块保存失败，回退到本地存储', error)
    const blocks = loadUserBlocks()
    const newBlock: CmsBlockDefinition = normalizeBlockAttributes({
      id: code,
      label: name,
      category,
      kind,
      media: genericBlockPreview(),
      content: html,
      css,
      description,
    })
    blocks.push(newBlock)
    persistUserBlocks(blocks)
    userBlocks.value = blocks
    editor.BlockManager.add(newBlock.id, newBlock)
    showSaveBlockDialog.value = false
    toast.success('已保存为本地自定义区块')
    refreshBlockPanel()
  }
}

function genericBlockPreview() {
  return `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="42" viewBox="0 0 64 42" fill="none">
    <rect width="64" height="42" rx="6" fill="#f1f5f9"/>
    <rect x="8" y="10" width="48" height="6" rx="2" fill="#cbd5e1"/>
    <rect x="8" y="22" width="36" height="4" rx="2" fill="#94a3b8"/>
  </svg>`
}

function formatCanvasCss() {
  if (!editor)
    return
  const css = fullCanvasCss()
  editor.setStyle(formatCssText(css))
  canvasRevision.value += 1
  toast.success('CSS 已格式化')
}

function formatPageJs() {
  const formatted = formatJsCode(pageJsContent.value)
  if (formatted && formatted !== pageJsContent.value) {
    pageJsContent.value = formatted
    toast.success('JS 已格式化')
  }
}

function formatJsCode(code: string) {
  let out = ''
  let indent = 0
  const tokens = code.split(/(\{|\}|;)/)
  for (const token of tokens) {
    const trimmed = token.trim()
    if (!trimmed) {
      if (token.includes('\n'))
        out += '\n'
      continue
    }
    if (trimmed === '}')
      indent = Math.max(0, indent - 1)
    out += `${'  '.repeat(indent)}${trimmed}`
    if (trimmed === '{') {
      out += '\n'
      indent += 1
    }
    else if (trimmed === ';') {
      out += '\n'
    }
    else {
      out += '\n'
    }
  }
  return out.trimEnd()
}

function lockChromeFrameComponents(instance: Editor) {
  instance.getWrapper()?.find('[data-yb-chrome], [data-yb-layout-slot]').forEach(component => lockChromeComponentTree(component))
}

function lockChromeComponentTree(component: any) {
  component?.set?.({
    removable: false,
    draggable: false,
    droppable: false,
    copyable: false,
    editable: false,
    traits: [],
  })
  component?.components?.().models?.forEach((child: any) => lockChromeComponentTree(child))
}

function restoreLockedLayout() {
  if (!editor || !props.chromeFrame) {
    return
  }
  const homeContent = extractHomeContent(editor.getHtml())
  const css = fullCanvasCss()
  editor.setComponents(chromeFrameTemplate(homeContent, props.chromeLayout))
  editor.setStyle(css)
  lockChromeFrameComponents(editor)
  injectCanvasChromePreviewStyle(editor)
  injectCanvasTemplatePreview(editor)
  canvasRevision.value += 1
  save()
}

function removeLayoutBlocks(instance: Editor) {
  instance.getWrapper()?.find('[data-yb-system-nav]').forEach(component => component.remove())
}

function command(command: string) {
  editor?.runCommand(command)
}

function setDevice(device: 'desktop' | 'tablet' | 'mobile') {
  editor?.setDevice(device)
  activeDevice.value = device
  requestAnimationFrame(fitCanvasToWorkspace)
}

function fitCanvasToWorkspace() {
  if (!editor || !editorEl.value) {
    return
  }
  const selected = editor.DeviceManager.getSelected() as any
  const width = Number.parseInt(String(selected?.get?.('width') || selected?.attributes?.width || ''), 10)
  if (!Number.isFinite(width) || width <= 0) {
    return
  }
  const zoom = canvasFitZoom(editorEl.value.clientWidth, width)
  ;(editor.Canvas as any)?.setZoom?.(zoom)
  canvasZoom.value = zoom
}

function useSuggestion(suggestion: string) {
  aiPrompt.value = suggestion
}

function useBlockSuggestion(prompt: string) {
  void sendAiPrompt(prompt)
}

function sessionStorageId(targetKey = chatHistoryTargetKey.value) {
  return `session:${targetKey}`
}

// 一个页面对应一个持久会话：优先复用内存中的当前会话，其次从 IndexedDB 载入，最后新建空会话。
async function ensureActiveSession(): Promise<CmsAiChatSession> {
  if (currentAiSession && currentAiSession.targetKey === chatHistoryTargetKey.value) {
    return currentAiSession
  }
  const existing = await getCmsAiChatSession(sessionStorageId())
  if (existing) {
    currentAiSession = existing
    return existing
  }
  const now = Date.now()
  currentAiSession = {
    id: sessionStorageId(),
    targetKey: chatHistoryTargetKey.value,
    targetType: chatHistoryTargetType.value,
    targetId: chatHistoryTargetId.value,
    targetLabel: chatHistoryTargetLabel.value,
    title: chatHistoryTargetLabel.value,
    preview: '',
    model: selectedAiAgentOption.value?.label || aiAgentCode.value || undefined,
    thinkingEnabled: aiThinkingEnabled.value,
    attachments: [],
    createdAt: now,
    updatedAt: now,
    messages: [],
  }
  return currentAiSession
}

// 向当前会话追加一轮对话（1 条用户消息 + 1 条待流式的助手消息）。
function appendUserTurn(session: CmsAiChatSession, prompt: string, attachments: any[] = []) {
  const turnId = createHistoryId()
  const now = Date.now()
  const displayPrompt = prompt || `参考样图调整当前页面：${firstImageAttachment(attachments)?.name || '样图'}`
  const attachmentMetas = attachmentMetaList(attachments)
  const attachmentText = attachmentMetas.length
    ? `\n\n附件：${attachmentMetas.map(item => item.name || item.fileType || '文件').join('、')}`
    : ''
  session.messages.push(
    {
      id: `${turnId}-user`,
      role: 'user',
      status: 'complete',
      content: [
        {
          type: 'text',
          data: `${displayPrompt}${attachmentText}`,
        },
      ],
    } as ChatMessagesData,
    {
      id: `${turnId}-assistant`,
      role: 'assistant',
      status: 'streaming',
      content: [],
    } as ChatMessagesData,
  )
  if (!session.title) {
    session.title = compactText(displayPrompt, 36)
  }
  session.preview = compactText(displayPrompt, 120)
  session.model = selectedAiAgentOption.value?.label || aiAgentCode.value || session.model
  session.thinkingEnabled = aiThinkingEnabled.value
  session.updatedAt = now
  if (attachmentMetas.length) {
    session.attachments = attachmentMetas
  }
}

function trackAiHistoryContent<T extends AIMessageContent>(content: T): T {
  if (!currentAiSession) {
    return content
  }
  const assistant = currentAiSession.messages.findLast(item => item.role === 'assistant')
  if (!assistant) {
    return content
  }
  const target = assistant as Extract<ChatMessagesData, { role: 'assistant' }>
  const existing = target.content?.find(item => item.id && item.id === content.id)
  if (existing && content.strategy === 'merge') {
    mergeAiContent(existing, content)
  }
  else {
    target.content = [...(target.content || []), cloneAiContent(content)]
  }
  target.status = content.status === 'error' ? 'error' : 'streaming'
  currentAiSession.updatedAt = Date.now()
  return content
}

async function finishAiHistorySession(status: 'complete' | 'error') {
  if (!currentAiSession) {
    return
  }
  const session = currentAiSession
  const assistant = session.messages.findLast(item => item.role === 'assistant')
  if (assistant) {
    assistant.status = status
  }
  session.updatedAt = Date.now()
  session.preview = compactText(messageText(assistant), 120) || session.preview
  // 页面级会话：持久化但保留在内存中，供后续追问累积多轮上下文。
  await saveCmsAiChatSession(session)
  await loadChatHistory(true)
}

// 把已完成的历史轮次抽取为纯文本消息，供后端注入多轮上下文（不含画布大字段）。
function collectHistoryMessages(session: CmsAiChatSession, maxTurns = 8): CmsChatHistoryMessage[] {
  const messages: CmsChatHistoryMessage[] = []
  for (const message of session.messages) {
    if (message.role !== 'user' && message.role !== 'assistant') {
      continue
    }
    const text = messageText(message).trim()
    if (!text) {
      continue
    }
    messages.push({ role: message.role, content: text })
  }
  const maxMessages = Math.max(0, maxTurns) * 2
  return maxMessages > 0 && messages.length > maxMessages
    ? messages.slice(messages.length - maxMessages)
    : messages
}

async function loadChatHistory(reset = false) {
  if (chatHistoryLoading.value) {
    return
  }
  chatHistoryLoading.value = true
  try {
    if (reset) {
      chatHistoryOffset.value = 0
      chatHistorySummaries.value = []
      selectedChatHistory.value = null
    }
    const page = await listCmsAiChatSessionSummaries(chatHistoryTargetKey.value, chatHistoryOffset.value, 8)
    chatHistorySummaries.value = reset ? page.items : [...chatHistorySummaries.value, ...page.items]
    chatHistoryOffset.value = chatHistorySummaries.value.length
    chatHistoryHasMore.value = page.hasMore
    chatHistoryLoadedOnce.value = true
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '会话记录加载失败')
  }
  finally {
    chatHistoryLoading.value = false
  }
}

async function openChatHistory(summary: CmsAiChatSessionSummary) {
  try {
    selectedChatHistory.value = await getCmsAiChatSession(summary.id) || null
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '会话详情加载失败')
  }
}

function restoreChatHistory() {
  if (!selectedChatHistory.value) {
    return
  }
  aiChatEngine.value.setMessages([...aiDefaultMessages.value, ...selectedChatHistory.value.messages], 'replace')
  scheduleAiChatScroll(true)
}

async function deleteSelectedChatHistory() {
  if (!selectedChatHistory.value) {
    return
  }
  await deleteCmsAiChatSession(selectedChatHistory.value.id)
  selectedChatHistory.value = null
  await loadChatHistory(true)
  toast.success('会话记录已删除')
}

// 把当前页面已持久化的会话恢复到聊天区，实现「一个页面 = 一段可持续的对话」。
async function restoreActiveSession() {
  try {
    const session = await ensureActiveSession()
    const messages = session.messages.length
      ? [...aiDefaultMessages.value, ...session.messages]
      : [...aiDefaultMessages.value]
    aiChatEngine.value.setMessages(messages, 'replace')
    scheduleAiChatScroll(true)
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '会话恢复失败')
  }
}

// 一键清空当前页面会话，方便重新开始聊天。
async function clearChatHistory() {
  if (!window.confirm(`确认清空“${chatHistoryTargetLabel.value}”的 AI 对话记录并重新开始吗？`)) {
    return
  }
  await clearCmsAiChatTarget(chatHistoryTargetKey.value)
  currentAiSession = null
  pendingAskDsl.value = ''
  selectedChatHistory.value = null
  aiChatEngine.value.setMessages([...aiDefaultMessages.value], 'replace')
  scheduleAiChatScroll(true)
  await loadChatHistory(true)
  toast.success('对话记录已清空')
}

function buildAiPayload(prompt: string, attachments: any[] = [], history: CmsChatHistoryMessage[] = []) {
  if (!editor) {
    throw new Error('构建器未初始化')
  }
  removeLayoutBlocks(editor)
  const image = firstImageAttachment(attachments)
  return {
    target: props.historyTargetType,
    title: props.title || '',
    siteName: appSettingsStore.siteName || 'YuDream',
    prompt: prompt || `参考样图调整当前页面：${image?.name || '样图'}`,
    pageType: 'GrapesJS 可视化页面',
    style: props.chromeFrame
      ? `当前是首页完整站点画布，布局模式为 ${props.chromeLayout || 'HEADER_FOOTER'}。Header、首页主体和布局对应的 Footer/版权栏是一个整体。只能修改 Header/Footer 的 CSS 和首页主体内容，不能修改固定壳 HTML、菜单层级、Logo、认证入口或数据绑定。`
      : '保持当前页面风格，按用户要求增量修改；如果用户要求重构，可以替换为更完整的设计。',
    agentCode: aiAgentCode.value || undefined,
    imageDataUrl: image?.url || undefined,
    currentHtml: editor.getHtml(),
    currentCss: fullCanvasCss(),
    currentJs: pageJsContent.value,
    currentProjectJson: JSON.stringify(editor.getProjectData()),
    currentSelectionJson: currentSelectionJson(),
    cmsVariableContextJson: cmsVariableContextJson(),
    thinkingEnabled: aiThinkingEnabled.value,
    history,
  }
}

function currentSelectionJson() {
  const snapshot = currentSelectionSnapshot()
  return snapshot ? JSON.stringify(snapshot) : ''
}

function cmsVariableContextJson() {
  return JSON.stringify(cmsVariableContext.value)
}

function currentSelectionSnapshot(): CanvasSelectionSnapshot | null {
  const component = editor?.getSelected() as any
  if (!component) {
    return null
  }
  const attributes = normalizeObject(readComponentValue(component, 'getAttributes'))
  const classes = readComponentClasses(component)
  const tagName = String(component.get?.('tagName') || '').toLowerCase()
  const type = String(component.get?.('type') || '')
  const html = String(component.toHTML?.() || '')
  const text = component.view?.el?.textContent || stripHtml(html)
  return {
    label: String(component.getName?.() || attributes.id || classes[0] || tagName || type || '选中元素'),
    type,
    tagName,
    selectorHint: selectionSelectorHint(tagName, attributes, classes),
    classes,
    attributes,
    styles: normalizeObject(readComponentValue(component, 'getStyle')),
    text: compactText(text, 600),
    html: compactText(html, 1400),
  }
}

function syncSelectedSource(force = false) {
  if (!force && rightPanelTab.value !== 'source') {
    return
  }
  if (selectedSourceDirty.value && !force) {
    return
  }
  const component = editor?.getSelected() as any
  if (!component) {
    selectedSourceCode.value = ''
    selectedSourceDirty.value = false
    selectedSourceError.value = ''
    return
  }
  selectedSourceCode.value = String(component.toHTML?.() || '')
  selectedSourceDirty.value = false
  selectedSourceError.value = ''
}

function markSelectedSourceDirty() {
  selectedSourceDirty.value = true
  selectedSourceError.value = ''
}

function formatSelectedSource() {
  if (!hasSelectedComponent.value) {
    toast.warning('请先在画布中选择一个元素')
    return
  }
  const formatted = formatHtmlFragment(selectedSourceCode.value)
  if (formatted && formatted !== selectedSourceCode.value) {
    selectedSourceCode.value = formatted
    markSelectedSourceDirty()
  }
}

function applySelectedSource() {
  const component = editor?.getSelected() as any
  if (!editor || !component) {
    toast.warning('请先在画布中选择一个元素')
    return
  }
  if (props.chromeFrame && isProtectedChromeComponent(component)) {
    selectedSourceError.value = 'Header/Footer 固定结构只读，不能应用 HTML 源码'
    toast.warning(selectedSourceError.value)
    return
  }
  const html = selectedSourceCode.value.trim()
  if (!html) {
    selectedSourceError.value = '源码不能为空'
    return
  }
  try {
    const added = replaceComponent(component, html)
    const next = firstComponent(added)
    if (next && typeof editor.select === 'function') {
      editor.select(next)
    }
    selectedSourceDirty.value = false
    selectedSourceError.value = ''
    canvasRevision.value += 1
    syncSelectedSource(true)
    toast.success('选中元素源码已应用')
  }
  catch (error) {
    const message = error instanceof Error ? error.message : '源码应用失败'
    selectedSourceError.value = message
    toast.error('源码应用失败', { description: message })
  }
}

function readComponentValue(component: any, method: string) {
  try {
    return typeof component?.[method] === 'function' ? component[method]() : {}
  }
  catch {
    return {}
  }
}

function readComponentClasses(component: any) {
  try {
    const classes = typeof component.getClasses === 'function' ? component.getClasses() : []
    return Array.isArray(classes) ? classes.map(item => String(item)).filter(Boolean) : []
  }
  catch {
    return []
  }
}

function selectionSelectorHint(tagName: string, attributes: Record<string, unknown>, classes: string[]) {
  const id = String(attributes.id || '').trim()
  if (id) {
    return `#${cssEscape(id)}`
  }
  if (classes.length) {
    return `${tagName || ''}.${classes.map(cssEscape).join('.')}`.trim()
  }
  return tagName || ''
}

function formatHtmlFragment(html: string) {
  const source = html.trim()
  if (!source || typeof document === 'undefined') {
    return source
  }
  const template = document.createElement('template')
  template.innerHTML = source
  const nodes = Array.from(template.content.childNodes)
  return nodes.map(node => formatHtmlNode(node, 0)).filter(Boolean).join('\n') || source
}

function formatHtmlNode(node: ChildNode, level: number): string {
  const indent = '  '.repeat(level)
  if (node.nodeType === Node.TEXT_NODE) {
    const text = String(node.textContent || '').replace(/\s+/g, ' ').trim()
    return text ? `${indent}${escapeHtmlText(text)}` : ''
  }
  if (node.nodeType === Node.COMMENT_NODE) {
    return `${indent}<!-- ${String(node.textContent || '').trim()} -->`
  }
  if (node.nodeType !== Node.ELEMENT_NODE) {
    return ''
  }
  const element = node as Element
  const tag = element.tagName.toLowerCase()
  const attrs = formatHtmlAttributes(element)
  const childNodes = Array.from(element.childNodes).filter(item => item.nodeType !== Node.TEXT_NODE || String(item.textContent || '').trim())
  if (isVoidHtmlTag(tag)) {
    return `${indent}<${tag}${attrs}>`
  }
  if (childNodes.length === 0) {
    return `${indent}<${tag}${attrs}></${tag}>`
  }
  if (childNodes.length === 1 && childNodes[0].nodeType === Node.TEXT_NODE) {
    return `${indent}<${tag}${attrs}>${escapeHtmlText(String(childNodes[0].textContent || '').replace(/\s+/g, ' ').trim())}</${tag}>`
  }
  const children = childNodes.map(child => formatHtmlNode(child, level + 1)).filter(Boolean).join('\n')
  return `${indent}<${tag}${attrs}>\n${children}\n${indent}</${tag}>`
}

function formatHtmlAttributes(element: Element) {
  const attrs = Array.from(element.attributes)
    .map(attr => attr.value === '' ? attr.name : `${attr.name}="${escapeHtmlAttribute(attr.value)}"`)
    .join(' ')
  return attrs ? ` ${attrs}` : ''
}

function isVoidHtmlTag(tag: string) {
  return new Set(['area', 'base', 'br', 'col', 'embed', 'hr', 'img', 'input', 'link', 'meta', 'param', 'source', 'track', 'wbr']).has(tag)
}

function escapeHtmlText(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function escapeHtmlAttribute(value: string) {
  return escapeHtmlText(value).replace(/"/g, '&quot;')
}

function extractRelatedCss(css: string, snapshot: CanvasSelectionSnapshot) {
  if (!css.trim()) {
    return ''
  }
  const matches = collectMatchingCssRules(css, snapshot)
  return matches.join('\n\n').trim()
}

function collectMatchingCssRules(css: string, snapshot: CanvasSelectionSnapshot): string[] {
  const matches: string[] = []
  let index = 0
  while (index < css.length) {
    const openIndex = findNextCssChar(css, '{', index)
    if (openIndex < 0) {
      break
    }
    const prelude = css.slice(index, openIndex).trim()
    const closeIndex = findMatchingCssBrace(css, openIndex)
    if (closeIndex < 0) {
      break
    }
    const body = css.slice(openIndex + 1, closeIndex)
    if (prelude.startsWith('@')) {
      const nestedMatches = collectMatchingCssRules(body, snapshot)
      if (nestedMatches.length > 0) {
        matches.push(`${prelude} {\n${indentCssLines(nestedMatches.join('\n\n'))}\n}`)
      }
    }
    else if (cssSelectorPreludeMatches(prelude, snapshot)) {
      matches.push(`${prelude} {${body}}`)
    }
    index = closeIndex + 1
  }
  return matches
}

function cssSelectorPreludeMatches(prelude: string, snapshot: CanvasSelectionSnapshot) {
  return prelude
    .split(',')
    .map(selector => selector.trim())
    .some(selector => cssSelectorMatchesSnapshot(selector, snapshot))
}

function cssSelectorMatchesSnapshot(selector: string, snapshot: CanvasSelectionSnapshot) {
  const normalized = selector
    .replace(/::?[\w-]+(?:\([^)]*\))?/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  if (!normalized) {
    return false
  }
  const id = String(snapshot.attributes.id || '').trim()
  if (id && containsCssIdentifier(normalized, `#${id}`)) {
    return true
  }
  if (snapshot.classes.some(item => containsCssIdentifier(normalized, `.${item}`))) {
    return true
  }
  if (snapshot.tagName && new RegExp(`(^|[\\s>+~,(])${escapeRegExp(snapshot.tagName)}(?=$|[\\s.#:[>+~,)])`, 'i').test(normalized)) {
    return true
  }
  if (Object.keys(snapshot.attributes).some(key => key && normalized.includes(`[${key}`))) {
    return true
  }
  return cssElementMatchesSelector(normalized, snapshot)
}

function cssElementMatchesSelector(selector: string, snapshot: CanvasSelectionSnapshot) {
  if (typeof document === 'undefined') {
    return false
  }
  try {
    const element = document.createElement(snapshot.tagName || 'div')
    const id = String(snapshot.attributes.id || '').trim()
    if (id) {
      element.id = id
    }
    snapshot.classes.forEach(item => element.classList.add(item))
    Object.entries(snapshot.attributes).forEach(([key, value]) => {
      if (key && value !== undefined && value !== null) {
        element.setAttribute(key, String(value))
      }
    })
    return element.matches(selector)
  }
  catch {
    return false
  }
}

function containsCssIdentifier(selector: string, identifier: string) {
  const raw = escapeRegExp(identifier)
  const escaped = cssEscape(identifier.slice(1))
  const prefix = identifier[0] === '#' ? '#' : '\\.'
  return new RegExp(`(^|[^a-zA-Z0-9_-])(?:${raw}|${prefix}${escapeRegExp(escaped)})(?=$|[^a-zA-Z0-9_-])`).test(selector)
}

function findNextCssChar(css: string, char: string, from: number) {
  let quote = ''
  for (let index = from; index < css.length; index += 1) {
    const current = css[index]
    const next = css[index + 1]
    if (!quote && current === '/' && next === '*') {
      index = css.indexOf('*/', index + 2)
      if (index < 0) {
        return -1
      }
      index += 1
      continue
    }
    if (quote) {
      if (current === '\\') {
        index += 1
      }
      else if (current === quote) {
        quote = ''
      }
      continue
    }
    if (current === '"' || current === '\'') {
      quote = current
      continue
    }
    if (current === char) {
      return index
    }
  }
  return -1
}

function findMatchingCssBrace(css: string, openIndex: number) {
  let depth = 0
  let quote = ''
  for (let index = openIndex; index < css.length; index += 1) {
    const current = css[index]
    const next = css[index + 1]
    if (!quote && current === '/' && next === '*') {
      index = css.indexOf('*/', index + 2)
      if (index < 0) {
        return -1
      }
      index += 1
      continue
    }
    if (quote) {
      if (current === '\\') {
        index += 1
      }
      else if (current === quote) {
        quote = ''
      }
      continue
    }
    if (current === '"' || current === '\'') {
      quote = current
      continue
    }
    if (current === '{') {
      depth += 1
    }
    if (current === '}') {
      depth -= 1
      if (depth === 0) {
        return index
      }
    }
  }
  return -1
}

function formatCssText(css: string) {
  let result = ''
  let indent = 0
  let quote = ''
  const appendIndent = () => {
    result += '  '.repeat(Math.max(indent, 0))
  }
  for (let index = 0; index < css.length; index += 1) {
    const current = css[index]
    if (quote) {
      result += current
      if (current === '\\') {
        result += css[index + 1] || ''
        index += 1
      }
      else if (current === quote) {
        quote = ''
      }
      continue
    }
    if (current === '"' || current === '\'') {
      quote = current
      result += current
      continue
    }
    if (current === '{') {
      result = result.trimEnd()
      result += ' {\n'
      indent += 1
      appendIndent()
      continue
    }
    if (current === '}') {
      indent -= 1
      result = result.trimEnd()
      result += '\n'
      appendIndent()
      result += '}\n'
      continue
    }
    if (current === ';') {
      result += ';\n'
      appendIndent()
      continue
    }
    if (/\s/.test(current)) {
      if (!/\s$/.test(result)) {
        result += ' '
      }
      continue
    }
    result += current
  }
  return result.trim()
}

function indentCssLines(css: string) {
  return css.split('\n').map(line => line.trim() ? `  ${line}` : line).join('\n')
}

async function toAttachmentItem(file: File) {
  if (!file.type.startsWith('image/')) {
    throw new Error('请选择图片文件')
  }
  if (file.size > 4 * 1024 * 1024) {
    throw new Error('样图不能超过 4MB')
  }
  return {
    key: `${file.name}-${file.size}-${file.lastModified}`,
    name: file.name,
    fileType: file.type.startsWith('image/') ? 'image' : 'txt',
    size: file.size,
    url: await fileToDataUrl(file),
  }
}

function firstImageAttachment(attachments: any[]) {
  return attachments.find(item => item?.fileType === 'image' && item?.url)
}

function markdownChunk(data: string): AIMessageContent {
  return {
    type: 'markdown',
    id: 'cms-ai-stream',
    data,
    strategy: 'merge',
  }
}

function aguiToolResult(chunk: SSEChunkData): AiToolCallResult | null {
  const event = chunk.data as Record<string, unknown> | undefined
  if (event?.type !== 'TOOL_CALL_RESULT' || typeof event.content !== 'string') {
    return null
  }
  try {
    return JSON.parse(event.content) as AiToolCallResult
  }
  catch {
    return null
  }
}

function trackAguiHistoryContent(content: AIMessageContent | AIMessageContent[] | null | undefined) {
  if (Array.isArray(content)) {
    content.forEach(trackAiHistoryContent)
  }
  else if (content) {
    trackAiHistoryContent(content)
  }
}

function isCanvasTool(tool?: AiToolCallResult) {
  return Boolean(
    (tool?.toolName?.startsWith('cms.canvas.') && tool.toolName !== 'cms.canvas.validate')
    || tool?.toolName === 'cms.chrome.style',
  )
}

function isAskUserTool(tool?: AiToolCallResult) {
  return tool?.toolName === 'cms.ask.user'
}

// 展示 AI 的澄清问题与可点击选项（后端已生成 TokUI DSL）。
function showAskUserUi(tool?: AiToolCallResult) {
  const dsl = String(tool?.payload?.tokui || '')
  if (dsl) {
    pendingAskDsl.value = dsl
  }
  pendingAskOptions.value = parseAskOptions(tool?.payload?.options)
}

// 用户点击某个选项后，作为下一轮消息发送，形成「AI 问 → 用户选 → 继续」的闭环。
function onPickOption(_data: unknown, _event: Event, element: HTMLElement) {
  const card = element.closest('.tokui-suggestion') as HTMLElement | null
  const title = card?.querySelector('.tokui-suggestion__title')?.textContent?.trim()
    || element.textContent?.trim()
    || ''
  if (!title) {
    return
  }
  chooseAskOption(title)
}

function chooseAskOption(title: string) {
  pendingAskDsl.value = ''
  pendingAskOptions.value = []
  aiPrompt.value = title
}

function parseAskOptions(raw: unknown): AskOption[] {
  if (!Array.isArray(raw)) {
    return []
  }
  return raw
    .map((item) => {
      if (typeof item === 'string') {
        return { title: item }
      }
      if (item && typeof item === 'object') {
        const record = item as Record<string, unknown>
        return {
          title: String(record.title || record.label || '').trim(),
          desc: String(record.desc || record.description || '').trim() || undefined,
        }
      }
      return { title: '' }
    })
    .filter(item => item.title)
}

function applyAiTool(tool?: AiToolCallResult): boolean {
  if (!editor || !tool) {
    return false
  }
  if (!tool.toolName?.startsWith('cms.canvas.') && tool.toolName !== 'cms.chrome.style') {
    toast.warning(`暂不支持的 AI 工具：${tool.toolName || '未知工具'}`)
    return false
  }
  const payload = tool.payload || {}
  const action = tool.action || 'replace-page'
  if (tool.toolName === 'cms.chrome.style' && action === 'validate') {
    toast.success(tool.message || 'Header/Footer 结构校验完成')
    return true
  }
  if (props.chromeFrame && action === 'load-project') {
    toast.warning('首页画布的 Header/Footer 结构已锁定，不能加载完整 Project JSON')
    return false
  }
  if (action === 'load-project' || (action === 'replace-page' && payload.builderProjectJson)) {
    try {
      if (props.chromeFrame) {
        toast.warning('首页画布的 Header/Footer 结构已锁定，不能加载完整 Project JSON')
        return false
      }
      editor.loadProjectData(JSON.parse(String(payload.builderProjectJson)))
      if (hasPayloadKey(payload, 'jsContent')) {
        setCanvasJs(String(payload.jsContent || ''))
      }
      canvasRevision.value += 1
      return true
    }
    catch {
      toast.warning('AI 返回的 Project JSON 无法解析，已继续应用 HTML/CSS')
    }
  }
  if (action === 'add-html') {
    orderedAddHtmlAssets(payload).forEach((asset) => {
      if (asset.kind === 'html') {
        if (props.chromeFrame) {
          appendHomeContent(asset.content)
        }
        else {
          highlightAddedComponents(editor!.addComponents(asset.content))
        }
      }
      else if (asset.kind === 'css') {
        appendCanvasCss(asset.content)
      }
      else {
        appendCanvasJs(asset.content)
      }
    })
    canvasRevision.value += 1
    return true
  }
  if (action === 'replace-page' || action === 'set-html') {
    if (props.chromeFrame) {
      if (!replaceHomeContent(String(payload.htmlContent || ''))) {
        return false
      }
    }
    else {
      editor.setComponents(String(payload.htmlContent || ''))
    }
  }
  if (action === 'replace-page' || action === 'set-css') {
    editor.setStyle(String(payload.cssContent || ''))
  }
  if (action === 'set-js' || (action === 'replace-page' && hasPayloadKey(payload, 'jsContent'))) {
    setCanvasJs(String(payload.jsContent || ''))
  }
  if (action === 'append-css' && payload.cssContent) {
    appendCanvasCss(String(payload.cssContent))
  }
  if (action === 'append-js' && payload.jsContent) {
    appendCanvasJs(String(payload.jsContent))
  }
  if (action === 'remove-selector' && payload.selector) {
    editor.getWrapper()?.find(String(payload.selector)).forEach((component) => {
      if (props.chromeFrame && isProtectedChromeComponent(component)) {
        toast.warning('Header/Footer 结构已锁定，不能删除其中的组件')
        return
      }
      component.remove()
    })
  }
  if ([
    'replace-selected',
    'set-selected-html',
    'append-to-selected',
    'prepend-to-selected',
    'set-selected-text',
    'set-attributes',
    'set-styles',
    'add-class',
    'remove-class',
    'remove-selected',
  ].includes(action)) {
    applySelectedComponentTool(action, payload)
  }
  if (['set-selected-html', 'append-to-selected', 'prepend-to-selected'].includes(action) && payload.cssContent) {
    appendCanvasCss(String(payload.cssContent))
  }
  canvasRevision.value += 1
  return true
}

function hasPayloadKey(payload: Record<string, any>, key: string) {
  return Object.prototype.hasOwnProperty.call(payload, key)
}

function applySelectedComponentTool(action: string, payload: Record<string, any>) {
  const component = resolveToolTarget(payload)
  if (!component) {
    toast.warning('请先在画布中选择一个元素，AI 才能执行局部操作。')
    return
  }
  if (props.chromeFrame && isProtectedChromeComponent(component) && action !== 'set-styles') {
    toast.warning('Header/Footer 结构和数据绑定已锁定，只能修改样式')
    return
  }
  if (action === 'replace-selected') {
    replaceComponent(component, String(payload.htmlContent || ''))
  }
  else if (action === 'set-selected-html') {
    component.components?.(String(payload.htmlContent || ''))
  }
  else if (action === 'append-to-selected') {
    appendToComponent(component, String(payload.htmlContent || ''), false)
  }
  else if (action === 'prepend-to-selected') {
    appendToComponent(component, String(payload.htmlContent || ''), true)
  }
  else if (action === 'set-selected-text') {
    const text = String(payload.textContent || payload.htmlContent || '')
    component.components?.(escapeHtml(text))
  }
  else if (action === 'set-attributes') {
    const attributes = normalizeObject(payload.attributes)
    if (Object.keys(attributes).length) {
      component.addAttributes?.(attributes)
    }
  }
  else if (action === 'set-styles') {
    const styles = normalizeStylePatch(payload.styles || payload.style)
    if (Object.keys(styles).length) {
      component.addStyle?.(styles)
    }
  }
  else if (action === 'add-class') {
    splitClassNames(payload.className).forEach(name => component.addClass?.(name))
  }
  else if (action === 'remove-class') {
    splitClassNames(payload.className).forEach(name => component.removeClass?.(name))
  }
  else if (action === 'remove-selected') {
    component.remove?.()
  }
  highlightComponent(component)
}

function resolveToolTarget(payload: Record<string, any>) {
  const selector = String(payload.selector || '').trim()
  if (selector) {
    const matched = editor?.getWrapper()?.find(selector) || []
    if (matched.length > 0) {
      return matched[0] as any
    }
  }
  return editor?.getSelected() as any
}

function replaceHomeContent(html: string): boolean {
  rebuildHomeChrome(html)
  return true
}

function appendHomeContent(html: string) {
  rebuildHomeChrome(`${currentHomeContent()}${html}`)
}

function currentHomeContent() {
  const canvasHtml = editor?.getHtml() || ''
  const extracted = extractHomeContent(canvasHtml)
  if (extracted) {
    return extracted
  }
  const main = canvasHtml.match(/<main[^>]*site-builder-home[^>]*>([\s\S]*?)<\/main>/i)
  return main?.[1] || props.htmlContent || ''
}

function rebuildHomeChrome(homeContent: string, layoutMode: CmsSiteLayoutMode = props.chromeLayout || 'HEADER_FOOTER') {
  if (!editor) {
    return
  }
  const css = fullCanvasCss()
  editor.setComponents(chromeFrameTemplate(homeContent, layoutMode))
  editor.setStyle(css)
  lockChromeFrameComponents(editor)
  injectCanvasChromePreviewStyle(editor)
  injectCanvasTemplatePreview(editor)
}

function isProtectedChromeComponent(component: any) {
  let current = component
  while (current) {
    const attributes = normalizeObject(readComponentValue(current, 'getAttributes'))
    if (attributes['data-yb-chrome'] || attributes['data-yb-layout-slot']) {
      return true
    }
    current = current.parent?.()
  }
  return false
}

function firstComponent(value: unknown): any {
  if (Array.isArray(value)) {
    return value[0]
  }
  const models = (value as { models?: unknown[] } | undefined)?.models
  if (Array.isArray(models)) {
    return models[0]
  }
  return value
}

function replaceComponent(component: any, html: string) {
  if (!html.trim()) {
    return undefined
  }
  if (typeof component.replaceWith === 'function') {
    const next = component.replaceWith(html)
    highlightAddedComponents(next)
    return next
  }
  const parent = component.parent?.()
  if (parent?.components) {
    const index = typeof component.index === 'function' ? component.index() : undefined
    component.remove?.()
    const added = parent.components().add(html, typeof index === 'number' ? { at: index } : undefined)
    highlightAddedComponents(added)
    return added
  }
  return undefined
}

function appendToComponent(component: any, html: string, prepend: boolean) {
  if (!html.trim()) {
    return
  }
  const children = component.components?.()
  const added = children?.add ? children.add(html, prepend ? { at: 0 } : undefined) : component.append?.(html)
  highlightAddedComponents(added)
}

function normalizeStylePatch(value: unknown): Record<string, string> {
  if (typeof value === 'string') {
    return Object.fromEntries(
      value.split(';')
        .map(item => item.trim())
        .filter(Boolean)
        .map((item) => {
          const index = item.indexOf(':')
          return index > 0 ? [item.slice(0, index).trim(), item.slice(index + 1).trim()] : ['', '']
        })
        .filter(([key, item]) => key && item),
    )
  }
  return Object.fromEntries(Object.entries(normalizeObject(value)).map(([key, item]) => [key, String(item)]))
}

function splitClassNames(value: unknown) {
  return String(value || '').split(/\s+/).map(item => item.trim()).filter(Boolean)
}

// 向画布 iframe 注入高亮动画样式（非页面 CSS，不会被导出/保存）。
function injectCanvasHighlightStyle(instance: Editor) {
  try {
    const doc = instance.Canvas.getDocument()
    if (!doc || doc.getElementById('yb-ai-block-enter-style')) {
      return
    }
    const style = doc.createElement('style')
    style.id = 'yb-ai-block-enter-style'
    style.textContent = `
      @keyframes ybAiBlockEnter {
        0% { opacity: 0; transform: translateY(16px); }
        100% { opacity: 1; transform: translateY(0); }
      }
      .yb-ai-block-enter {
        animation: ybAiBlockEnter 0.5s ease-out;
        outline: 2px solid rgba(59, 130, 246, 0.6);
        outline-offset: 2px;
        transition: outline-color 0.9s ease-out;
      }
    `
    doc.head?.appendChild(style)
  }
  catch {
    // 画布尚未就绪时忽略，不影响主流程。
  }
}

// 增量追加样式：在现有 CSS 之后拼接新片段，避免整表替换丢失已有样式。
function injectCanvasChromePreviewStyle(instance: Editor) {
  if (!props.chromeFrame) {
    return
  }
  try {
    const doc = instance.Canvas.getDocument()
    if (!doc) {
      return
    }
    const style = doc.getElementById('yb-cms-chrome-preview-style') || doc.createElement('style')
    style.id = 'yb-cms-chrome-preview-style'
    style.textContent = chromeCanvasPreviewCss(props.chromeLayout)
    if (!style.parentNode) {
      doc.head?.appendChild(style)
    }
  }
  catch {
    // The canvas can still finish mounting after the first style injection attempt.
  }
}

function injectCanvasTemplatePreview(instance: Editor) {
  const context = props.templatePreviewContext
  if (!context) {
    return
  }
  try {
    const doc = instance.Canvas.getDocument()
    if (!doc?.body) {
      return
    }
    renderCanvasNavigation(doc, context)
    doc.querySelectorAll('[data-yb-repeat]').forEach((element) => {
      const template = element.getAttribute('data-yb-preview-template') || element.innerHTML
      element.setAttribute('data-yb-preview-template', template)
      const key = element.getAttribute('data-yb-repeat') || ''
      const rows = resolveCmsTemplateRows(key, context)
      element.innerHTML = rows.map((item, index) => renderCmsVariables(
        template,
        { item, index: String(index + 1) },
        context,
      )).join('')
    })
    replaceCanvasTemplateValues(doc.body, context)
    doc.querySelectorAll('[data-yb-html]').forEach((element) => {
      element.innerHTML = sanitizeCmsHtml(element.getAttribute('data-yb-html') || '')
      element.removeAttribute('data-yb-html')
    })
    doc.querySelectorAll('[data-yb-markdown]').forEach((element) => {
      element.innerHTML = renderCmsMarkdown(element.getAttribute('data-yb-markdown') || '')
      element.removeAttribute('data-yb-markdown')
    })
    const loggedIn = String(context.auth?.isLoggedIn || '') === 'true'
    doc.querySelectorAll('[data-visible-when]').forEach((element) => {
      const rule = element.getAttribute('data-visible-when')
      const visible = rule === 'logged-in' ? loggedIn : rule === 'guest' ? !loggedIn : true
      ;(element as HTMLElement).style.display = visible ? '' : 'none'
    })
  }
  catch {
    // Preview data must never prevent the GrapesJS editor from loading.
  }
}

function blockMatchesFilter(id: string, block: any) {
  const category = blockCategoryLabel(block?.get?.('category') ?? block?.attributes?.category)
  const kind = String(block?.get?.('kind') ?? block?.attributes?.kind ?? '')
  if (blockCategory.value === '收藏' && !favoriteBlockIds.value.has(id)) {
    return false
  }
  if (blockCategory.value === '原子' && kind !== 'atomic') {
    return false
  }
  if (!['全部', '收藏', '原子'].includes(blockCategory.value) && category !== blockCategory.value) {
    return false
  }
  const search = blockSearch.value.trim().toLowerCase()
  if (!search) {
    return true
  }
  const label = String(block?.get?.('label') ?? block?.attributes?.label ?? '').replace(/<[^>]+>/g, ' ')
  const description = String(block?.get?.('description') ?? block?.attributes?.description ?? '')
  return `${label} ${category} ${description}`.toLowerCase().includes(search)
}

function blockCategoryLabel(category: any) {
  if (typeof category === 'string') {
    return category
  }
  return String(category?.get?.('label') ?? category?.get?.('id') ?? category?.label ?? category?.id ?? '')
}

function scheduleCanvasTemplatePreview(instance: Editor, delay = 180) {
  if (templatePreviewTimer) {
    clearTimeout(templatePreviewTimer)
  }
  templatePreviewTimer = setTimeout(() => {
    templatePreviewTimer = null
    if (editor === instance) {
      injectCanvasTemplatePreview(instance)
    }
  }, delay)
}

function renderCanvasNavigation(doc: Document, context: Record<string, any>) {
  const items = Array.isArray(context.chromeNavigation)
    ? context.chromeNavigation
    : Array.isArray(context.navigation) ? context.navigation : []
  doc.querySelectorAll('[data-yb-chrome-navigation]').forEach((element) => {
    element.innerHTML = items.map(item => renderCanvasNavigationItem(item, context)).join('')
  })
}

function renderCanvasNavigationItem(item: Record<string, any>, context: Record<string, any>): string {
  const children = Array.isArray(item.children) ? item.children : []
  const link = renderCmsVariables(
    children.length ? '<a href="{{item.url}}">{{item.label}} <span>⌄</span></a>' : '<a href="{{item.url}}">{{item.label}}</a>',
    { item },
    context,
  )
  if (!children.length) {
    return `<div class="site-nav-item">${link}</div>`
  }
  const childLinks = children
    .map(child => renderCmsVariables('<a href="{{item.url}}">{{item.label}}</a>', { item: child }, context))
    .join('')
  return `<div class="site-nav-item">${link}<div class="site-nav-dropdown">${childLinks}</div></div>`
}

function replaceCanvasTemplateValues(node: Node, context: Record<string, any>) {
  if (node.nodeType === 3) {
    node.textContent = renderCmsVariables(node.textContent || '', {}, context)
    return
  }
  if (node.nodeType !== 1) {
    return
  }
  const element = node as Element
  Array.from(element.attributes).forEach((attribute) => {
    if (attribute.name !== 'data-yb-preview-template' && attribute.value.includes('{{')) {
      element.setAttribute(attribute.name, renderCmsVariables(attribute.value, {}, context))
    }
  })
  Array.from(node.childNodes).forEach(child => replaceCanvasTemplateValues(child, context))
}

function appendCanvasCss(css: string) {
  if (!editor || !css.trim()) {
    return
  }
  const existing = fullCanvasCss()
  editor.setStyle(`${existing}\n${css}`.trim())
}

function fullCanvasCss(instance: Editor | null = editor) {
  return instance?.getCss({ keepUnusedStyles: true }) || ''
}

function setCanvasJs(js: string) {
  pageJsContent.value = stripScriptTags(js)
}

function appendCanvasJs(js: string) {
  const clean = stripScriptTags(js)
  if (!clean.trim()) {
    return
  }
  pageJsContent.value = [pageJsContent.value, clean].filter(item => item.trim()).join('\n\n')
}

function scheduleCanvasJsSync(delay = 450) {
  if (pageJsSyncTimer) {
    clearTimeout(pageJsSyncTimer)
  }
  pageJsSyncTimer = setTimeout(() => {
    pageJsSyncTimer = null
    syncCanvasJs()
  }, delay)
}

function syncCanvasJs() {
  if (!editor) {
    return
  }
  try {
    const doc = editor.Canvas.getDocument()
    if (!doc) {
      return
    }
    runCanvasJsDisposers(doc.defaultView)
    doc.querySelectorAll('script[data-yb-cms-page-script]').forEach(item => item.remove())
    const code = stripScriptTags(pageJsContent.value).trim()
    if (!code) {
      return
    }
    const lifecycleIssue = canvasJsLifecycleIssue(code)
    if (lifecycleIssue) {
      console.warn(`[YuDream CMS] JS preview skipped: ${lifecycleIssue}`)
      return
    }
    const script = doc.createElement('script')
    script.dataset.ybCmsPageScript = 'true'
    script.textContent = `
      window.__YU_CMS_CONTEXT__ = ${JSON.stringify(cmsVariableContext.value)};
      window.__YU_CMS_DISPOSERS__ = window.__YU_CMS_DISPOSERS__ || [];
      window.__YU_CMS_DISPOSERS__.splice(0).forEach(function(dispose) {
        try { dispose(); } catch (error) { console.warn('[YuDream CMS] cleanup failed', error); }
      });
      window.__YU_CMS_REGISTER_CLEANUP__ = function(dispose) {
        if (typeof dispose === 'function') window.__YU_CMS_DISPOSERS__.push(dispose);
      };
      window.__YU_CMS_READY__ = function(callback) {
        if (typeof callback !== 'function') return;
        if (document.readyState === 'loading') {
          document.addEventListener('DOMContentLoaded', callback, { once: true });
          return;
        }
        window.queueMicrotask ? window.queueMicrotask(callback) : setTimeout(callback, 0);
      };
      (function() {
        ${code}
      })();
    `
    doc.body?.appendChild(script)
  }
  catch (error) {
    console.warn('[YuDream CMS] JS preview failed', error)
  }
}

function canvasJsLifecycleIssue(code: string): string {
  const normalized = code.toLowerCase()
  const registersCleanup = normalized.includes('window.__yu_cms_register_cleanup__')
  if (normalized.includes('requestanimationframe(')
    && (!registersCleanup || !normalized.includes('cancelanimationframe('))) {
    return 'requestAnimationFrame 缺少 cancelAnimationFrame 清理'
  }
  if (normalized.includes('setinterval(')
    && (!registersCleanup || !normalized.includes('clearinterval('))) {
    return 'setInterval 缺少 clearInterval 清理'
  }
  if (normalized.includes('addeventlistener(')
    && (!registersCleanup || !normalized.includes('removeeventlistener('))) {
    return 'addEventListener 缺少 removeEventListener 清理'
  }
  if (normalized.includes('setanimationloop(')
    && (!registersCleanup || !normalized.includes('setanimationloop(null'))) {
    return 'Three.js setAnimationLoop 缺少 setAnimationLoop(null) 清理'
  }
  return ''
}

function runCanvasJsDisposers(canvasWindow: (Window & typeof globalThis) | null | undefined) {
  if (!canvasWindow) {
    return
  }
  const runtimeWindow = canvasWindow as typeof canvasWindow & { __YU_CMS_DISPOSERS__?: unknown[] }
  const disposers = Array.isArray(runtimeWindow.__YU_CMS_DISPOSERS__)
    ? runtimeWindow.__YU_CMS_DISPOSERS__.splice(0)
    : []
  disposers.forEach((dispose) => {
    if (typeof dispose !== 'function') {
      return
    }
    try {
      dispose()
    }
    catch (error) {
      console.warn('[YuDream CMS] cleanup failed', error)
    }
  })
}

function stripScriptTags(value: string) {
  return String(value || '')
    .replace(/<script[^>]*>/gi, '')
    .replace(/<\/script>/gi, '')
    .trim()
}

// 让新追加的区块平滑滚动进入视野，并短暂高亮，营造「一片片生成」的视觉反馈。
function highlightAddedComponents(added: unknown) {
  const components = Array.isArray(added) ? added : [added]
  const first = components[0]
  highlightComponent(first)
}

function highlightComponent(component: unknown) {
  const item = Array.isArray(component) ? component[0] : component
  const el = (item as { view?: { el?: HTMLElement } } | undefined)?.view?.el
  if (!el) {
    return
  }
  requestAnimationFrame(() => {
    try {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
    }
    catch {
      el.scrollIntoView()
    }
    el.classList.add('yb-ai-block-enter')
    setTimeout(() => el.classList.remove('yb-ai-block-enter'), 1400)
  })
}

function clearAiAttachments() {
  aiAttachments.value = []
}

function mergeAiContent(existing: AIMessageContent, incoming: AIMessageContent) {
  if (existing.type === 'markdown' && incoming.type === 'markdown') {
    existing.data = `${existing.data || ''}${incoming.data || ''}`
    return
  }
  if (existing.type === 'thinking' && incoming.type === 'thinking') {
    existing.data = {
      title: incoming.data.title || existing.data.title || '思考中',
      text: [existing.data.text, incoming.data.text].filter(Boolean).join('\n'),
    }
    return
  }
  Object.assign(existing, cloneAiContent(incoming))
}

function cloneAiContent<T>(content: T): T {
  return JSON.parse(JSON.stringify(content)) as T
}

function messageText(message?: ChatMessagesData | null) {
  return message?.content?.map(contentText).filter(Boolean).join('\n') || ''
}

function contentText(content: unknown) {
  const item = content as Record<string, any>
  if (item.type === 'text' || item.type === 'markdown') {
    return String(item.data || '')
  }
  if (item.type === 'thinking') {
    return String(item.data?.text || '')
  }
  if (item.type === 'attachment' && Array.isArray(item.data)) {
    return `附件：${item.data.map((file: CmsAiChatAttachmentMeta) => file.name || file.fileType || '文件').join('、')}`
  }
  return ''
}

function attachmentMetaList(attachments: any[]): CmsAiChatAttachmentMeta[] {
  return attachments.map(item => ({
    name: item?.name,
    fileType: item?.fileType,
    size: item?.size,
  }))
}

function compactText(value: string, maxLength: number) {
  const text = String(value || '').replace(/\s+/g, ' ').trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}...` : text
}

function stripHtml(value: string) {
  return String(value || '').replace(/<[^>]*>/g, ' ')
}

function normalizeObject(value: unknown): Record<string, unknown> {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>)
      .filter(([key, item]) => key && item !== undefined && item !== null && String(item).trim() !== ''),
  )
}

function cssEscape(value: string) {
  return globalThis.CSS?.escape ? globalThis.CSS.escape(value) : value.replace(/[^a-zA-Z0-9_-]/g, '\\$&')
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function createHistoryId() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`
}

function formatHistoryTime(value: number) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function roleLabel(role?: string) {
  return role === 'user' ? '你' : 'AI'
}

function fileToDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsDataURL(file)
  })
}

async function loadMedia() {
  loadingMedia.value = true
  try {
    const res = await apiFiles.page({ page: 1, size: 48, module: 'cms', publicAccess: true })
    mediaItems.value = res.data.records
  }
  finally {
    loadingMedia.value = false
  }
}

function pickMedia() {
  mediaInput.value?.click()
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
  const data = new FormData()
  data.append('file', file)
  data.append('module', 'cms')
  data.append('publicAccess', 'true')
  await apiFiles.upload(data)
  toast.success('媒体已上传')
  await loadMedia()
}

function insertImage(item: FileObject) {
  if (!editor) {
    return
  }
  const url = toBackendAssetUrl(item.url)
  editor.addComponents(`<img src="${escapeAttr(url)}" alt="${escapeAttr(item.originalName || 'CMS 图片')}" style="max-width:100%; border-radius:12px;">`)
}

function registerDynamicTypes(instance: Editor) {
  instance.DomComponents.addType('yb-repeat', {
    isComponent: el => el.hasAttribute?.('data-yb-repeat'),
    model: {
      defaults: {
        traits: [
          {
            type: 'select',
            name: 'data-yb-repeat',
            label: '动态数据',
            options: [
              { id: 'pages', name: '公开页面' },
              { id: 'categories', name: '分类' },
              { id: 'tags', name: '标签' },
              { id: 'navigation', name: '导航' },
              { id: 'navUsers', name: '头像用户' },
            ],
          },
        ],
      },
    },
  })
  instance.DomComponents.addType('yb-visible', {
    isComponent: el => el.hasAttribute?.('data-visible-when'),
    model: {
      defaults: {
        traits: [
          {
            type: 'select',
            name: 'data-visible-when',
            label: '可见条件',
            options: [
              { id: 'guest', name: '游客' },
              { id: 'logged-in', name: '已登录' },
            ],
          },
        ],
      },
    },
  })
}

function normalizeBlockAttributes(block: CmsBlockDefinition): CmsBlockDefinition {
  return {
    ...block,
    attributes: {
      ...(block.attributes || {}),
      'data-id': block.id,
    },
  }
}

function registerBlocks(instance: Editor, render = true) {
  cmsBlocks().map(normalizeBlockAttributes).forEach(block => instance.BlockManager.add(block.id, block, { silent: true }))
  if (render) {
    instance.BlockManager.render()
  }
}

function styleSectors() {
  return [
    {
      name: '布局',
      open: true,
      properties: ['display', 'position', 'top', 'right', 'bottom', 'left', 'width', 'height', 'margin', 'padding'],
    },
    {
      name: '文字',
      open: false,
      properties: ['font-family', 'font-size', 'font-weight', 'color', 'line-height', 'text-align'],
    },
    {
      name: '外观',
      open: false,
      properties: ['background-color', 'border', 'border-radius', 'box-shadow', 'opacity'],
    },
  ]
}

function starterHtml() {
  return `<main style="padding:40px 20px; background:#f8fafc; color:#0f172a;">
  <section style="max-width:1120px; margin:0 auto; padding:56px 48px; border-radius:24px; background:#fff; border:1px solid #e5e7eb;">
    <h1 style="margin:0; font-size:56px;">${escapeHtml(props.title || '新页面')}</h1>
    <p style="margin:18px 0 0; color:#64748b;">从左侧拖入区块，使用右侧面板调整样式和动态属性。</p>
  </section>
</main>`
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function escapeAttr(value: string) {
  return escapeHtml(value)
}

function selectBreadcrumb(component: any) {
  editor?.select(component)
}
</script>

<template>
  <div
    class="grapes-shell"
    :class="{
      'is-preview': previewActive,
      'is-left-collapsed': leftSidebarCollapsed,
      'is-right-collapsed': rightSidebarCollapsed,
    }"
  >
    <header class="grapes-header">
      <div class="grapes-header__brand">
        <span class="grapes-header__mark"><FaIcon name="i-ri:layout-masonry-line" /></span>
        <div>
          <strong>{{ title || 'CMS 构建器' }}</strong>
          <span :class="{ 'is-dirty': editorDirty }">{{ editorDirty ? '有未保存修改' : editorReady ? '所有修改已保存' : '正在初始化画布' }}</span>
        </div>
      </div>
      <div v-if="selectedBreadcrumbs.length" class="grapes-header__breadcrumbs">
        <button
          v-for="(crumb, index) in selectedBreadcrumbs"
          :key="index"
          type="button"
          @click="selectBreadcrumb(crumb.component)"
        >
          {{ crumb.label }}
        </button>
      </div>
      <div class="grapes-header__actions">
        <div class="workbench-command-group">
          <TTooltip content="撤销" trigger="hover"><button type="button" class="workbench-icon-button" @click="command('core:undo')"><FaIcon name="i-ri:arrow-go-back-line" /></button></TTooltip>
          <TTooltip content="重做" trigger="hover"><button type="button" class="workbench-icon-button" @click="command('core:redo')"><FaIcon name="i-ri:arrow-go-forward-line" /></button></TTooltip>
          <TTooltip content="复制选中元素" trigger="hover"><button type="button" class="workbench-icon-button" :disabled="!hasSelectedComponent" @click="duplicateSelected"><FaIcon name="i-ri:file-copy-line" /></button></TTooltip>
          <TTooltip content="删除选中元素" trigger="hover"><button type="button" class="workbench-icon-button is-danger" :disabled="!hasSelectedComponent" @click="command('core:component-delete')"><FaIcon name="i-ri:delete-bin-line" /></button></TTooltip>
        </div>
        <div class="workbench-device-switch" aria-label="画布设备">
          <TTooltip content="桌面" trigger="hover"><button type="button" :class="{ active: activeDevice === 'desktop' }" @click="setDevice('desktop')"><FaIcon name="i-ri:computer-line" /></button></TTooltip>
          <TTooltip content="平板" trigger="hover"><button type="button" :class="{ active: activeDevice === 'tablet' }" @click="setDevice('tablet')"><FaIcon name="i-ri:tablet-line" /></button></TTooltip>
          <TTooltip content="手机" trigger="hover"><button type="button" :class="{ active: activeDevice === 'mobile' }" @click="setDevice('mobile')"><FaIcon name="i-ri:smartphone-line" /></button></TTooltip>
        </div>
        <div class="workbench-command-group grapes-header__zoom">
          <button type="button" class="workbench-icon-button" @click="changeCanvasZoom(-10)"><FaIcon name="i-ri:subtract-line" /></button>
          <button type="button" class="workbench-zoom-value" @click="resetCanvasZoom">{{ canvasZoom }}%</button>
          <button type="button" class="workbench-icon-button" @click="changeCanvasZoom(10)"><FaIcon name="i-ri:add-line" /></button>
        </div>
        <div class="workbench-command-group">
          <TTooltip :content="previewActive ? '退出预览' : '预览页面'" trigger="hover"><button type="button" class="workbench-icon-button" :class="{ active: previewActive }" @click="togglePreview"><FaIcon :name="previewActive ? 'i-ri:edit-box-line' : 'i-ri:eye-line'" /></button></TTooltip>
          <TTooltip v-if="chromeFrame" content="还原固定布局" trigger="hover"><button type="button" class="workbench-icon-button" @click="restoreLockedLayout"><FaIcon name="i-ri:layout-line" /></button></TTooltip>
          <TTooltip content="保存为区块" trigger="hover"><button type="button" class="workbench-icon-button" :disabled="!hasSelectedComponent" @click="openSaveBlockDialog"><FaIcon name="i-ri:archive-drawer-line" /></button></TTooltip>
        </div>
        <FaButton size="sm" :disabled="!editorReady" @click="save">
          <FaIcon name="i-ri:save-3-line" />
          {{ editorDirty ? '保存修改' : '保存' }}
        </FaButton>
        <TTooltip content="关闭构建器" trigger="hover"><button type="button" class="workbench-icon-button" @click="emit('close')"><FaIcon name="i-ri:close-line" /></button></TTooltip>
      </div>
    </header>

    <div class="grapes-body">
      <aside class="grapes-sidebar left" :class="{ collapsed: leftSidebarCollapsed }">
        <div class="sidebar-rail-head">
          <div class="left-workspace-tabs" role="tablist" aria-label="素材工作区">
            <button type="button" :class="{ active: leftWorkspace === 'blocks' }" @click="leftWorkspace = 'blocks'; leftSidebarCollapsed = false"><FaIcon name="i-ri:layout-grid-line" /><span>区块</span></button>
            <button type="button" :class="{ active: leftWorkspace === 'media' }" @click="leftWorkspace = 'media'; leftSidebarCollapsed = false"><FaIcon name="i-ri:image-2-line" /><span>媒体</span></button>
          </div>
          <TTooltip :content="leftSidebarCollapsed ? '展开左侧栏' : '收起左侧栏'" trigger="hover"><button type="button" class="workbench-icon-button sidebar-collapse-button" @click="leftSidebarCollapsed = !leftSidebarCollapsed"><FaIcon :name="leftSidebarCollapsed ? 'i-ri:sidebar-unfold-line' : 'i-ri:sidebar-fold-line'" /></button></TTooltip>
        </div>
        <section v-show="leftWorkspace === 'blocks'" class="block-panel">
          <div class="block-panel__search-wrap">
            <FaIcon name="i-ri:search-line" class="block-panel__search-icon" />
            <input
              v-model="blockSearch"
              type="text"
              placeholder="搜索区块..."
              class="block-panel__search"
            >
          </div>
          <div class="block-panel__tabs">
            <button
              v-for="cat in blockCategories"
              :key="cat"
              type="button"
              :class="{ active: blockCategory === cat }"
              @click="blockCategory = cat"
            >
              {{ cat }}
            </button>
          </div>
          <div ref="blocksEl" />
        </section>
        <section v-show="leftWorkspace === 'media'" class="media-panel">
          <div class="media-head">
            <div><h3>媒体资源</h3><span>{{ mediaItems.length }} 项</span></div>
            <FaButton size="sm" @click="pickMedia"><FaIcon name="i-ri:upload-cloud-2-line" />上传</FaButton>
          </div>
          <input ref="mediaInput" type="file" accept="image/*" hidden @change="uploadMedia">
          <div class="media-grid">
            <button v-for="item in mediaItems" :key="item.id" type="button" @click="insertImage(item)">
              <img :src="toBackendAssetUrl(item.url)" :alt="item.originalName || 'CMS 图片'">
            </button>
          </div>
        </section>
      </aside>

      <main class="grapes-canvas">
        <div ref="editorEl" class="grapes-editor" />
        <footer class="canvas-statusbar">
          <span><i :class="{ ready: editorReady }" />{{ editorReady ? '画布就绪' : '加载中' }}</span>
          <span>{{ activeDevice === 'desktop' ? '桌面' : activeDevice === 'tablet' ? '平板' : '手机' }}</span>
          <span>{{ canvasZoom }}%</span>
          <span v-if="selectedBreadcrumbs.length">{{ selectedBreadcrumbs.at(-1)?.label }}</span>
        </footer>
      </main>

      <aside class="grapes-sidebar right" :class="{ collapsed: rightSidebarCollapsed }">
        <div class="sidebar-rail-head right-sidebar-head">
        <div class="right-tabs" role="tablist" aria-label="构建器右侧面板">
          <button
            v-for="tab in rightPanelTabs"
            :key="tab.value"
            type="button"
            :class="{ active: rightPanelTab === tab.value }"
            role="tab"
            :aria-selected="rightPanelTab === tab.value"
            @click="rightPanelTab = tab.value; rightSidebarCollapsed = false"
          >
            <FaIcon :name="tab.icon" />
            <span>{{ tab.label }}</span>
          </button>
        </div>
          <TTooltip :content="rightSidebarCollapsed ? '展开检查器' : '收起检查器'" trigger="hover"><button type="button" class="workbench-icon-button sidebar-collapse-button" @click="rightSidebarCollapsed = !rightSidebarCollapsed"><FaIcon :name="rightSidebarCollapsed ? 'i-ri:sidebar-unfold-line' : 'i-ri:sidebar-fold-line'" /></button></TTooltip>
        </div>

        <section v-if="aiEnabled && rightPanelTab === 'ai'" class="right-panel ai-panel active">
          <div class="builder-chatbot-wrap">
            <div ref="aiChatScrollEl" class="builder-chatbot t-chat--normal" @scroll.passive="onAiChatScroll">
              <ChatMessage
                v-for="entry in aiRenderedMessages"
                :key="entry.message.id"
                :role="entry.message.role"
                :status="entry.message.status"
                :content="entry.contents"
                :name="aiMessageProps.name(entry.message)"
                :placement="entry.message.role === 'user' ? 'right' : 'left'"
              >
                <template #avatar>
                  <span class="cms-ai-avatar" :class="entry.message.role === 'user' ? 'is-user' : 'is-assistant'">
                    {{ entry.message.role === 'user' ? '你' : 'AI' }}
                  </span>
                </template>
                <template #content>
                <div class="cms-agui-message-content">
                  <template v-for="(content, index) in entry.contents" :key="`${entry.message.id}-${index}`">
                    <ActivityRenderer
                      v-if="isAguiActivityContent(content)"
                      :activity="content.data"
                    />
                    <ToolCallRenderer
                      v-else-if="isAguiToolCallContent(content)"
                      :tool-call="content.data"
                    />
                    <div
                      v-else
                      :class="{ 'cms-ai-content-error': entry.message.status === 'error' && content?.type === 'text' }"
                    >
                      <ChatContent
                        :content="content"
                        :role="entry.message.role"
                      />
                    </div>
                  </template>
                </div>
                </template>
              </ChatMessage>
            </div>
            <ChatSender
              v-model="aiPrompt"
              :loading="aiChatLoading"
              :placeholder="aiSenderProps.placeholder"
              :textarea-props="aiSenderProps.textareaProps"
              :attachments-props="aiSenderProps.attachmentsProps"
              @send="sendAiPrompt"
              @stop="aiChatEngine.abortChat()"
              @file-select="aiSenderProps.onFileSelect?.({ detail: $event.files })"
              @remove="aiSenderProps.onFileRemove?.({ detail: $event })"
            >
              <template #footer-prefix>
                <TTooltip content="添加样图" trigger="hover">
                  <button type="button" class="ai-attachment-button" aria-label="添加样图" @click="selectAiAttachment">
                    <FaIcon name="i-ri:image-add-line" />
                  </button>
                </TTooltip>
                <div v-if="aiAgentOptions?.length" class="ai-model-select">
                  <TTooltip content="切换 Agent" trigger="hover">
                    <TSelect
                      v-model="aiAgentCode"
                      class="ai-model-select__control"
                      :options="aiAgentOptions"
                      size="small"
                    />
                  </TTooltip>
                </div>
                <label class="ai-thinking-switch">
                  <TSwitch v-model="aiThinkingEnabled" size="small" />
                  <span>深度思考</span>
                </label>
              </template>
            </ChatSender>
            <input ref="aiAttachmentInput" class="ai-attachment-input" type="file" accept="image/*" multiple @change="onAiAttachmentInput">
            <section v-if="pendingAskDsl" class="ai-ask" aria-label="AI 需求澄清">
              <TokuiBlock :dsl="pendingAskDsl" />
              <div v-if="pendingAskOptions.length" class="ai-ask-options">
                <button v-for="option in pendingAskOptions" :key="option.title" type="button" @click="chooseAskOption(option.title)">
                  <strong>{{ option.title }}</strong>
                  <span v-if="option.desc">{{ option.desc }}</span>
                </button>
              </div>
            </section>
            <section class="ai-history" aria-label="AI 会话记录">
              <div class="ai-history__head">
                <div>
                  <strong>会话记录</strong>
                  <span>{{ chatHistoryTargetLabel }}</span>
                </div>
                <div class="ai-history__actions">
                  <button type="button" @click="clearChatHistory">
                    清空并重开
                  </button>
                </div>
              </div>

              <div v-if="chatHistorySummaries.length" class="ai-history__list">
                <button
                  v-for="item in chatHistorySummaries"
                  :key="item.id"
                  type="button"
                  :class="{ active: selectedChatHistory?.id === item.id }"
                  @click="openChatHistory(item)"
                >
                  <strong>{{ item.title || '未命名会话' }}</strong>
                  <small>{{ formatHistoryTime(item.updatedAt) }} · {{ item.model || '默认 Agent' }}</small>
                  <span>{{ item.preview }}</span>
                </button>
              </div>
              <div v-else class="ai-history__empty">
                {{ chatHistoryLoadedOnce ? '暂无会话记录' : '打开 AI 面板后按需加载记录' }}
              </div>

              <button
                v-if="chatHistoryHasMore"
                type="button"
                class="ai-history__more"
                :disabled="chatHistoryLoading"
                @click="loadChatHistory(false)"
              >
                加载更多
              </button>

              <div v-if="selectedChatHistory" class="ai-history__detail">
                <div class="ai-history__detail-head">
                  <strong>{{ selectedChatHistory.title }}</strong>
                  <button type="button" @click="selectedChatHistory = null">
                    关闭
                  </button>
                </div>
                <div class="ai-history__detail-actions">
                  <button type="button" @click="restoreChatHistory">
                    恢复到聊天区
                  </button>
                  <button type="button" @click="deleteSelectedChatHistory">
                    删除本条
                  </button>
                </div>
                <article v-for="message in selectedChatHistory.messages" :key="message.id">
                  <strong>{{ roleLabel(message.role) }}</strong>
                  <p>{{ compactText(messageText(message), 360) }}</p>
                </article>
              </div>
            </section>
          </div>

          <p class="ai-tools-hint">AI 可以使用系统预设区块快速搭建页面。</p>

          <div class="ai-suggestions" aria-label="AI 快捷指令">
            <button v-for="suggestion in aiSuggestions" :key="suggestion" type="button" @click="useSuggestion(suggestion)">
              {{ suggestion }}
            </button>
          </div>

          <div class="ai-suggestions ai-block-suggestions" aria-label="AI 添加区块">
            <button v-for="item in aiBlockSuggestions" :key="item.label" type="button" @click="useBlockSuggestion(item.prompt)">
              {{ item.label }}
            </button>
          </div>

          <div class="ai-context">
            <span>选中 {{ selectedCanvasSummary }}</span>
            <span>变量 {{ cmsVariableCount }} 个</span>
            <span v-for="item in canvasStats" :key="item.label">
              {{ item.label }} {{ item.value }}
            </span>
          </div>
        </section>

        <section class="right-panel" :class="{ active: rightPanelTab === 'layers' }">
          <h3>图层</h3>
          <div ref="layersEl" />
        </section>
        <section class="right-panel" :class="{ active: rightPanelTab === 'traits' }">
          <h3>属性</h3>
          <div ref="traitsEl" />
        </section>
        <section class="right-panel" :class="{ active: rightPanelTab === 'styles' }">
          <h3>样式</h3>
          <div ref="stylesEl" />
        </section>
        <section class="right-panel source-panel" :class="{ active: rightPanelTab === 'source' }">
          <div class="source-panel__head">
            <div>
              <h3>源码</h3>
              <span>{{ selectedSourceSummary }}</span>
            </div>
            <div class="source-panel__actions">
              <FaButton variant="outline" size="sm" :disabled="!hasSelectedComponent || !selectedSourceCode.trim()" @click="formatSelectedSource">
                <FaIcon name="i-ri:align-left" />
                格式化
              </FaButton>
              <FaButton size="sm" :disabled="!hasSelectedComponent || selectedSourceLocked || !selectedSourceDirty" @click="applySelectedSource">
                <FaIcon name="i-ri:check-line" />
                应用
              </FaButton>
            </div>
          </div>
          <div class="source-panel__body">
            <section class="source-editor-section source-editor-section--html">
              <div class="source-editor-section__head">
                <strong>HTML</strong>
                <span>支持高亮、补全和源码格式化</span>
              </div>
              <CmsCodeEditor
                v-model="selectedSourceCode"
                class="source-panel__editor"
                language="html"
                :disabled="!hasSelectedComponent || selectedSourceLocked"
                :completions="htmlSourceCompletions"
                @update:model-value="markSelectedSourceDirty"
              />
            </section>
            <section class="source-editor-section source-editor-section--css">
              <div class="source-editor-section__head">
                <strong>关联 CSS</strong>
                <div class="source-editor-section__actions">
                  <FaButton variant="outline" size="sm" @click="formatCanvasCss">
                    <FaIcon name="i-ri:align-left" />
                    格式化
                  </FaButton>
                  <span>根据当前选择自动匹配</span>
                </div>
              </div>
              <CmsCodeEditor
                class="source-panel__editor"
                :model-value="selectedRelatedCss"
                language="css"
                disabled
                :completions="cssSourceCompletions"
              />
            </section>
            <section class="source-editor-section source-editor-section--js">
              <div class="source-editor-section__head">
                <strong>页面 JS</strong>
                <div class="source-editor-section__actions">
                  <FaButton variant="outline" size="sm" @click="formatPageJs">
                    <FaIcon name="i-ri:align-left" />
                    格式化
                  </FaButton>
                  <span>自动注入预览并随页面保存</span>
                </div>
              </div>
              <CmsCodeEditor
                v-model="pageJsContent"
                class="source-panel__editor"
                language="javascript"
                :completions="jsSourceCompletions"
              />
            </section>
          </div>
          <div class="source-panel__status" :class="{ error: selectedSourceError }">
            <span v-if="selectedSourceError">{{ selectedSourceError }}</span>
            <span v-else-if="selectedSourceDirty">未应用</span>
            <span v-else-if="selectedSourceLocked">Header/Footer 固定结构只读</span>
            <span v-else>{{ hasSelectedComponent ? '已同步' : '未选择元素' }}</span>
          </div>
        </section>
      </aside>
    </div>

    <div v-if="showSaveBlockDialog" class="save-block-modal" role="dialog" aria-modal="true" aria-labelledby="save-block-title">
      <div class="save-block-modal__overlay" @click="showSaveBlockDialog = false" />
      <div class="save-block-modal__content">
        <h3 id="save-block-title">保存为区块</h3>
        <label class="save-block-modal__field">
          <span>名称</span>
          <input v-model="saveBlockForm.name" type="text" placeholder="区块名称">
        </label>
        <label class="save-block-modal__field">
          <span>分类</span>
          <input v-model="saveBlockForm.category" type="text" placeholder="预制">
        </label>
        <label class="save-block-modal__field">
          <span>类型</span>
          <select v-model="saveBlockForm.kind">
            <option value="atomic">原子</option>
            <option value="preset">预制</option>
          </select>
        </label>
        <label class="save-block-modal__field">
          <span>描述</span>
          <textarea v-model="saveBlockForm.description" rows="2" placeholder="可选描述" />
        </label>
        <div class="save-block-modal__actions">
          <FaButton variant="outline" size="sm" @click="showSaveBlockDialog = false">取消</FaButton>
          <FaButton size="sm" @click="confirmSaveBlock">保存</FaButton>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
:deep(.cms-agui-activity),
:deep(.cms-agui-tool) {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  box-sizing: border-box;
  width: 100%;
  margin: 6px 0;
  padding: 9px 10px;
  color: var(--td-text-color-primary, #1f2937);
  background: var(--td-bg-color-secondarycontainer, #f5f7fa);
  border: 1px solid var(--td-component-border, #dcdfe6);
  border-radius: 6px;
}

:deep(.cms-agui-activity__dot),
:deep(.cms-agui-tool__state) {
  flex: 0 0 auto;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  background: var(--td-brand-color, #0052d9);
  border-radius: 50%;
}

:deep(.cms-agui-activity__body),
:deep(.cms-agui-tool__body) {
  min-width: 0;
}

:deep(.cms-agui-activity strong),
:deep(.cms-agui-tool strong) {
  display: block;
  font-size: 13px;
  line-height: 20px;
  font-weight: 600;
}

:deep(.cms-agui-activity p),
:deep(.cms-agui-tool p) {
  margin: 2px 0 0;
  overflow-wrap: anywhere;
  color: var(--td-text-color-secondary, #5e6d82);
  font-size: 12px;
  line-height: 18px;
}

:deep(.cms-agui-tool__meta) {
  color: var(--td-brand-color, #0052d9) !important;
  font-weight: 600;
}

:deep(.cms-agui-tool__preview) {
  display: -webkit-box;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

:deep(.cms-agui-tool.is-complete .cms-agui-tool__state) {
  background: var(--td-success-color, #00a870);
}

:deep(.cms-agui-tool.is-error .cms-agui-tool__state) {
  background: var(--td-error-color, #d54941);
}

:deep(.cms-agui-tool.is-executing .cms-agui-tool__state) {
  animation: cms-agui-pulse 1.2s ease-in-out infinite;
}

:deep(.cms-agui-tool__action) {
  flex: 0 0 auto;
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--td-brand-color, #0052d9);
  border-radius: 4px;
  background: transparent;
  color: var(--td-brand-color, #0052d9);
  font-size: 12px;
  line-height: 18px;
  cursor: pointer;
}

:deep(.cms-agui-tool__action:hover) {
  background: var(--td-brand-color-light, #e8f0ff);
}

:deep(.cms-agui-card) {
  display: grid;
  gap: 12px;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-left: 3px solid rgb(var(--primary-6));
  border-radius: 6px;
  background: var(--color-bg-1);
}

:deep(.cms-agui-card.is-success) { border-left-color: rgb(var(--success-6)); }
:deep(.cms-agui-card.is-warning) { border-left-color: rgb(var(--warning-6)); }
:deep(.cms-agui-card.is-danger) { border-left-color: rgb(var(--danger-6)); }
:deep(.cms-agui-card__head) { display: grid; gap: 4px; }
:deep(.cms-agui-card__head strong) { color: var(--color-text-1); font-size: 14px; }
:deep(.cms-agui-card__head p) { margin: 0; color: var(--color-text-2); font-size: 12px; line-height: 1.6; }
:deep(.cms-agui-card__fields) { display: grid; grid-template-columns: repeat(auto-fit, minmax(120px, 1fr)); gap: 8px; margin: 0; }
:deep(.cms-agui-card__field) { display: grid; gap: 2px; min-width: 0; padding: 8px; border-radius: 4px; background: var(--color-fill-1); }
:deep(.cms-agui-card__field dt) { color: var(--color-text-3); font-size: 10px; }
:deep(.cms-agui-card__field dd) { margin: 0; overflow-wrap: anywhere; color: var(--color-text-1); font-size: 13px; }
:deep(.cms-agui-card__actions) { display: flex; flex-wrap: wrap; gap: 8px; }
:deep(.cms-agui-card__actions button) { min-height: 30px; padding: 0 10px; border: 1px solid var(--color-border-2); border-radius: 4px; background: var(--color-bg-1); color: rgb(var(--primary-6)); font-size: 12px; cursor: pointer; }
:deep(.cms-agui-card__actions button:hover) { border-color: rgb(var(--primary-6)); background: rgb(var(--primary-1)); }

.ai-attachment-input {
  display: none;
}

.ai-attachment-button {
  display: grid;
  width: 28px;
  height: 28px;
  padding: 0;
  color: var(--td-text-color-secondary, #5e6d82);
  background: transparent;
  border: 0;
  place-items: center;
  cursor: pointer;
}

.ai-attachment-button:hover {
  color: var(--td-brand-color, #0052d9);
}

.cms-ai-avatar {
  display: grid;
  width: 28px;
  height: 28px;
  color: #fff;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 600;
  place-items: center;
}

.cms-ai-avatar.is-assistant {
  background: var(--td-brand-color, #0052d9);
}

.cms-ai-avatar.is-user {
  background: #64748b;
}

.builder-chatbot :deep(.t-chat__inner) {
  margin-bottom: 8px;
}

.builder-chatbot :deep(.t-chat__avatar) {
  margin: 2px 8px 0 0;
  padding-top: 0;
}

.builder-chatbot :deep(.t-chat__detail) {
  min-width: 0;
  max-width: calc(100% - 36px);
  padding: 0 4px;
}

.builder-chatbot :deep(.t-chat__name) {
  margin-bottom: 2px;
  color: var(--td-text-color-secondary, #5e6d82);
  font-size: 12px;
  line-height: 18px;
}

.builder-chatbot :deep(.t-chat__inner.user .t-chat__avatar) {
  margin: 2px 0 0 8px;
}

.cms-agui-message-content {
  width: 100%;
  min-width: 0;
}

.cms-ai-content-error {
  color: var(--td-error-color, #d54941);
}

@keyframes cms-agui-pulse {
  50% { opacity: .35; }
}

.grapes-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100vh;
  color: #1f2937;
  background: #eef1f5;
}

.grapes-header,
.grapes-header__actions,
.media-head,
.media-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

.grapes-header {
  position: relative;
  z-index: 20;
  flex-wrap: nowrap;
  justify-content: space-between;
  min-height: 54px;
  gap: 12px;
  padding: 7px 10px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04);
}

.grapes-header__brand {
  display: flex;
  flex: 0 1 240px;
  min-width: 160px;
  align-items: center;
  gap: 9px;
}

.grapes-header__mark {
  display: grid !important;
  flex: 0 0 34px;
  width: 34px;
  height: 34px;
  border-radius: 7px;
  background: #0f766e;
  color: #fff !important;
  font-size: 17px !important;
  place-items: center;
}

.grapes-header strong,
.grapes-header span {
  display: block;
}

.grapes-header span {
  color: #64748b;
  font-size: 12px;
}

.grapes-header__brand span.is-dirty {
  color: #b45309;
}

.grapes-header__actions {
  flex: 0 0 auto;
  gap: 6px;
}

.grapes-header__breadcrumbs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  max-width: 420px;
  padding: 0 12px;
}

.grapes-header__breadcrumbs button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 8px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #475569;
  font-size: 11px;
  cursor: pointer;
}

.grapes-header__breadcrumbs button:hover {
  border-color: #0f766e;
  color: #0f766e;
  background: #f0fdfa;
}

.grapes-header__breadcrumbs button:not(:last-child)::after {
  content: '/';
  margin-left: 4px;
  color: #cbd5e1;
}

.grapes-header__zoom {
  font-variant-numeric: tabular-nums;
}

.workbench-command-group,
.workbench-device-switch {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 3px;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #f8fafc;
}

.workbench-icon-button,
.workbench-device-switch button,
.workbench-zoom-value {
  display: inline-grid;
  flex: 0 0 auto;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #526176;
  font-size: 15px;
  line-height: 1;
  place-items: center;
  cursor: pointer;
  transition: background-color 0.15s, color 0.15s;
}

.workbench-icon-button:hover:not(:disabled),
.workbench-device-switch button:hover,
.workbench-device-switch button.active,
.workbench-icon-button.active {
  background: #fff;
  color: #0f766e;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
}

.workbench-icon-button.is-danger:hover:not(:disabled) {
  color: #dc2626;
}

.workbench-icon-button:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.workbench-zoom-value {
  width: 46px;
  color: #334155;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
}

.block-panel {
  --yb-block-bg: #fff;
  --yb-block-bg-hover: #fff;
  --yb-block-border: #e5e7eb;
  --yb-block-border-hover: #0f766e;
  --yb-block-text: #334155;
  --yb-block-text-hover: #0f766e;
  --yb-block-muted: #64748b;
  --yb-block-bg-2: #f8fafc;
  --yb-primary: #0f766e;
  --yb-primary-soft: #ecfdf5;
  --yb-star: #f59e0b;

  display: grid;
  gap: 12px;
}

:global(.dark) .block-panel {
  --yb-block-bg: #1e293b;
  --yb-block-bg-hover: #1e293b;
  --yb-block-border: #334155;
  --yb-block-border-hover: #2dd4bf;
  --yb-block-text: #e2e8f0;
  --yb-block-text-hover: #2dd4bf;
  --yb-block-muted: #94a3b8;
  --yb-block-bg-2: #0f172a;
  --yb-primary: #2dd4bf;
  --yb-primary-soft: #134e4a;
  --yb-star: #fbbf24;
}

.block-panel__search-wrap {
  position: relative;
}

.block-panel__search-icon {
  position: absolute;
  top: 50%;
  left: 12px;
  transform: translateY(-50%);
  color: var(--yb-block-muted);
  font-size: 14px;
  pointer-events: none;
}

.block-panel__search {
  width: 100%;
  height: 34px;
  padding: 0 12px 0 34px;
  border: 1px solid var(--yb-block-border);
  border-radius: 999px;
  background: var(--yb-block-bg-2);
  color: var(--yb-block-text);
  font-size: 13px;
  outline: none;
  transition: border-color 0.2s, background 0.2s, box-shadow 0.2s;
}

.block-panel__search::placeholder {
  color: var(--yb-block-muted);
}

.block-panel__search:focus {
  border-color: var(--yb-primary);
  background: var(--yb-block-bg);
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.12);
}

:global(.dark) .block-panel__search:focus {
  box-shadow: 0 0 0 3px rgba(45, 212, 191, 0.15);
}

.block-panel__tabs {
  display: flex;
  flex-wrap: nowrap;
  gap: 6px;
  overflow-x: auto;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.block-panel__tabs::-webkit-scrollbar {
  display: none;
}

.block-panel__tabs button {
  flex: 0 0 auto;
  padding: 5px 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: var(--yb-block-bg-2);
  color: var(--yb-block-muted);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.block-panel__tabs button:hover {
  border-color: var(--yb-block-border);
  background: var(--yb-block-bg);
  color: var(--yb-block-text);
}

.block-panel__tabs button.active {
  border-color: var(--yb-primary);
  background: var(--yb-primary-soft);
  color: var(--yb-primary);
  font-weight: 600;
}

:deep(.gjs-block-category) {
  overflow: hidden;
  border: 0 !important;
  border-bottom: 1px solid var(--yb-block-border) !important;
  background: transparent !important;
}

:deep(.gjs-block-category .gjs-title) {
  display: flex;
  align-items: center;
  min-height: 34px;
  gap: 4px;
  margin: 0;
  padding: 0 4px !important;
  border: 0 !important;
  border-radius: 0;
  background: transparent !important;
  color: var(--yb-block-text);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 34px;
  transition: color 0.15s, background-color 0.15s;
}

:deep(.gjs-block-category .gjs-title:hover) {
  background: var(--yb-block-bg-2) !important;
  color: var(--yb-block-text-hover);
}

:deep(.gjs-block-category .gjs-caret-icon) {
  display: grid;
  width: 18px;
  height: 18px;
  margin: 0 2px 0 0 !important;
  color: var(--yb-block-muted);
  place-items: center;
}

:deep(.gjs-block-category:not(:has(.gjs-block:not(.hidden)))) {
  display: none;
}

:deep(.gjs-blocks-c) {
  display: grid !important;
  grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  gap: 8px !important;
  padding: 8px 0 12px !important;
  background: transparent !important;
}

:deep(.gjs-block-category:not(.gjs-open) .gjs-blocks-c) {
  display: none !important;
}

:deep(.gjs-block) {
  position: relative;
  display: flex !important;
  flex-direction: column;
  justify-content: center !important;
  gap: 6px;
  width: auto !important;
  min-width: 0 !important;
  min-height: 94px !important;
  margin: 0 !important;
  padding: 10px 7px 22px !important;
  float: none !important;
  border: 1px solid var(--yb-block-border);
  border-radius: 7px;
  background: var(--yb-block-bg);
  color: var(--yb-block-text);
  box-shadow: none;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.15s;
}

:deep(.gjs-block:hover) {
  border-color: var(--yb-block-border-hover);
  color: var(--yb-block-text-hover);
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.08);
  transform: translateY(-1px);
}

:global(.dark) :deep(.gjs-block:hover) {
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.25);
}

:deep(.gjs-block.hidden) {
  display: none !important;
}

:deep(.gjs-block-label) {
  width: 100%;
  min-width: 0;
  padding: 0 4px !important;
  overflow: visible;
  color: inherit;
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.35;
  text-align: center;
  text-overflow: clip;
  white-space: normal;
  word-break: keep-all;
  overflow-wrap: normal;
}

:deep(.gjs-block__media) {
  display: grid;
  width: 100%;
  height: 42px;
  margin: 0 !important;
  overflow: hidden;
  place-items: center;
  pointer-events: none;
}

:deep(.gjs-block__media > *) {
  max-width: 100%;
  max-height: 42px;
}

:deep(.gjs-block-kind-badge) {
  position: absolute;
  right: 5px;
  bottom: 5px;
  padding: 1px 5px;
  border-radius: 999px;
  background: rgba(226, 232, 240, 0.85);
  color: var(--yb-block-muted);
  font-size: 9px;
  font-weight: 700;
  line-height: 1.2;
  pointer-events: none;
  backdrop-filter: blur(2px);
}

:deep(.gjs-block-kind-badge[data-kind="atomic"]) {
  background: rgba(236, 253, 245, 0.9);
  color: #047857;
}

:deep(.gjs-block-kind-badge[data-kind="preset"]) {
  background: rgba(239, 246, 255, 0.9);
  color: #1d4ed8;
}

:global(.dark) :deep(.gjs-block-kind-badge) {
  background: rgba(51, 65, 85, 0.85);
  color: var(--yb-block-muted);
}

:global(.dark) :deep(.gjs-block-kind-badge[data-kind="atomic"]) {
  background: rgba(6, 78, 59, 0.85);
  color: #34d399;
}

:global(.dark) :deep(.gjs-block-kind-badge[data-kind="preset"]) {
  background: rgba(30, 58, 138, 0.85);
  color: #60a5fa;
}

:deep(.gjs-block-favorite) {
  position: absolute;
  top: 4px;
  right: 4px;
  display: grid;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 0;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--yb-block-muted);
  font-size: 12px;
  line-height: 1;
  place-items: center;
  cursor: pointer;
  opacity: 0.7;
  transition: opacity 0.15s, color 0.15s, transform 0.1s;
  backdrop-filter: blur(2px);
}

:global(.dark) :deep(.gjs-block-favorite) {
  background: rgba(30, 41, 59, 0.8);
  color: var(--yb-block-muted);
}

:deep(.gjs-block-favorite)::before {
  content: '☆';
}

:deep(.gjs-block-favorite.is-active)::before {
  content: '★';
}

:deep(.gjs-block-favorite:hover) {
  opacity: 1;
  color: var(--yb-star);
  transform: scale(1.1);
}

:deep(.gjs-block-favorite.is-active) {
  opacity: 1;
  color: var(--yb-star);
}

.source-editor-section__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.save-block-modal {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  padding: 20px;
}

.save-block-modal__overlay {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
}

.save-block-modal__content {
  position: relative;
  z-index: 1;
  display: grid;
  gap: 14px;
  width: min(100%, 400px);
  padding: 20px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.2);
}

.save-block-modal__content h3 {
  margin: 0;
  font-size: 16px;
}

.save-block-modal__field {
  display: grid;
  gap: 6px;
  font-size: 12px;
  color: #475569;
}

.save-block-modal__field input,
.save-block-modal__field select,
.save-block-modal__field textarea {
  width: 100%;
  padding: 8px 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
  color: #0f172a;
  font-size: 13px;
  outline: none;
}

.save-block-modal__field input:focus,
.save-block-modal__field select:focus,
.save-block-modal__field textarea:focus {
  border-color: #0f766e;
  background: #fff;
}

.save-block-modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 4px;
}

.grapes-body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 360px;
  min-height: 0;
  transition: grid-template-columns 0.2s ease;
}

.grapes-shell.is-left-collapsed .grapes-body {
  grid-template-columns: 50px minmax(0, 1fr) 360px;
}

.grapes-shell.is-right-collapsed .grapes-body {
  grid-template-columns: 280px minmax(0, 1fr) 50px;
}

.grapes-shell.is-left-collapsed.is-right-collapsed .grapes-body {
  grid-template-columns: 50px minmax(0, 1fr) 50px;
}

.grapes-shell.is-preview .grapes-body {
  grid-template-columns: 0 minmax(0, 1fr) 0;
}

.grapes-sidebar {
  display: grid;
  align-content: start;
  gap: 14px;
  min-width: 0;
  overflow: auto;
  padding: 12px;
  border-color: #e5e7eb;
  background: #fff;
}

.grapes-sidebar.collapsed {
  gap: 6px;
  padding: 7px;
  overflow: hidden;
}

.grapes-sidebar.collapsed > section,
.grapes-shell.is-preview .grapes-sidebar {
  visibility: hidden;
  pointer-events: none;
}

.grapes-sidebar.collapsed .sidebar-rail-head {
  visibility: visible;
  pointer-events: auto;
}

.sidebar-rail-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}

.left-workspace-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  flex: 1;
  gap: 3px;
  padding: 3px;
  border-radius: 7px;
  background: #f1f5f9;
}

.left-workspace-tabs button {
  display: flex;
  min-width: 0;
  height: 32px;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 0;
  border-radius: 5px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}

.left-workspace-tabs button.active {
  background: #fff;
  color: #0f766e;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
}

.grapes-sidebar.left.collapsed .sidebar-rail-head,
.grapes-sidebar.right.collapsed .sidebar-rail-head {
  flex-direction: column;
}

.grapes-sidebar.left.collapsed .left-workspace-tabs {
  grid-template-columns: 1fr;
  width: 36px;
}

.grapes-sidebar.left.collapsed .left-workspace-tabs button span,
.grapes-sidebar.right.collapsed .right-tabs button span {
  display: none;
}

.sidebar-collapse-button {
  border: 1px solid #e2e8f0;
}

.grapes-sidebar.left {
  border-right: 1px solid #e5e7eb;
}

.grapes-sidebar.right {
  grid-template-rows: auto minmax(0, 1fr);
  align-content: stretch;
  gap: 10px;
  overflow: hidden;
  border-left: 1px solid #e5e7eb;
}

.grapes-sidebar section {
  display: grid;
  gap: 10px;
}

.grapes-sidebar h3 {
  margin: 0;
  font-size: 14px;
}

.grapes-canvas {
  display: grid;
  grid-template-rows: minmax(0, 1fr) 28px;
  min-width: 0;
  min-height: 0;
  background: #e9edf2;
}

.grapes-editor {
  height: 100%;
  min-height: 0;
}

.canvas-statusbar {
  display: flex;
  align-items: center;
  gap: 16px;
  min-width: 0;
  padding: 0 12px;
  border-top: 1px solid #dbe2ea;
  background: #fff;
  color: #64748b;
  font-size: 11px;
}

.canvas-statusbar span {
  display: inline-flex;
  min-width: 0;
  align-items: center;
  gap: 6px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.canvas-statusbar i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #94a3b8;
}

.canvas-statusbar i.ready {
  background: #10b981;
}

.media-head {
  justify-content: space-between;
}

.media-head > div {
  display: grid;
  gap: 2px;
}

.media-head span {
  color: #94a3b8;
  font-size: 11px;
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.media-grid button {
  overflow: hidden;
  aspect-ratio: 1;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #f8fafc;
}

.media-grid img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.right-tabs {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(56px, 1fr));
  gap: 4px;
  padding: 4px;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  background: #f8fafc;
}

.right-sidebar-head {
  align-items: stretch;
}

.right-sidebar-head .right-tabs {
  flex: 1;
}

.grapes-sidebar.right.collapsed .right-tabs {
  grid-template-columns: 1fr;
  width: 36px;
  padding: 2px;
}

.right-tabs button {
  display: inline-flex;
  min-width: 0;
  height: 34px;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  cursor: pointer;
}

.right-tabs button.active {
  background: #fff;
  color: #0f766e;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.1);
}

.right-tabs button span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.right-panel {
  display: none !important;
  min-height: 0;
  overflow: auto;
}

.right-panel.active {
  display: grid !important;
  gap: 10px;
  align-content: start;
}

.ai-panel.active {
  grid-template-rows: minmax(0, 1fr) auto auto auto;
  height: 100%;
  gap: 12px;
  overflow: hidden;
}

.ai-context {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
}

.source-panel.active {
  grid-template-rows: auto minmax(0, 1fr) auto;
  height: 100%;
  overflow: hidden;
}

.source-panel__head,
.source-panel__actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.source-panel__head {
  justify-content: space-between;
  min-width: 0;
}

.source-panel__head > div:first-child {
  min-width: 0;
}

.source-panel__head h3 {
  margin: 0;
}

.source-panel__head span {
  display: block;
  max-width: 190px;
  overflow: hidden;
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-panel__actions {
  flex: 0 0 auto;
}

.source-panel__body {
  display: grid;
  gap: 10px;
  min-height: 0;
  overflow: auto;
  padding-right: 2px;
}

.source-editor-section {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  gap: 6px;
  min-height: 240px;
}

.source-editor-section--html {
  min-height: 360px;
}

.source-editor-section--css {
  min-height: 250px;
}

.source-editor-section--js {
  min-height: 280px;
}

.source-editor-section__head {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.source-editor-section__head strong {
  color: #0f172a;
  font-size: 12px;
}

.source-editor-section__head span {
  overflow: hidden;
  color: #94a3b8;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.source-panel__editor {
  width: 100%;
  height: 100%;
  min-height: 0;
}

.source-panel__status {
  min-height: 20px;
  color: #64748b;
  font-size: 12px;
}

.source-panel__status.error {
  color: #dc2626;
}

.ai-thinking-switch {
  display: inline-flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 6px;
  color: #64748b;
  font-size: 12px;
  white-space: nowrap;
}

.ai-suggestions {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 2px;
}

.ai-suggestions::-webkit-scrollbar {
  display: none;
}

.ai-suggestions button {
  display: inline-flex;
  flex: 0 0 auto;
  max-width: 180px;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 999px;
  background: #fff;
  color: #475569;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.05);
}

.ai-suggestions button:hover {
  border-color: #cbd5e1;
  color: #0f172a;
}

.ai-tools-hint {
  margin: 0 0 8px;
  color: #64748b;
  font-size: 12px;
  line-height: 1.5;
}

.ai-block-suggestions button {
  border-color: #ccfbf1;
  background: #f0fdfa;
  color: #0d9488;
}

.ai-block-suggestions button:hover {
  border-color: #99f6e4;
  background: #e6fbf7;
  color: #0f766e;
}

.ai-ask {
  padding: 12px;
  margin-bottom: 8px;
  border: 1px solid #dbeafe;
  border-radius: 14px;
  background: rgba(239, 246, 255, 0.9);
}

.ai-ask-options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 10px;
}

.ai-ask-options button {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid #bfdbfe;
  border-radius: 10px;
  background: #fff;
  color: #1e3a8a;
  text-align: left;
  cursor: pointer;
}

.ai-ask-options button:hover {
  border-color: #60a5fa;
  background: #eff6ff;
}

.ai-ask-options strong,
.ai-ask-options span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-ask-options strong {
  font-size: 12px;
}

.ai-ask-options span {
  color: #64748b;
  font-size: 11px;
}

.ai-history {
  display: grid;
  gap: 8px;
  max-height: 280px;
  min-height: 0;
  padding: 10px;
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.92);
}

.ai-history__head,
.ai-history__actions,
.ai-history__detail-head,
.ai-history__detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-history__head,
.ai-history__detail-head {
  justify-content: space-between;
}

.ai-history__head strong,
.ai-history__detail-head strong {
  display: block;
  color: #0f172a;
  font-size: 13px;
}

.ai-history__head span {
  display: block;
  max-width: 160px;
  overflow: hidden;
  color: #64748b;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-history button {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #475569;
  cursor: pointer;
}

.ai-history button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.ai-history__actions button,
.ai-history__detail-actions button,
.ai-history__detail-head button,
.ai-history__more {
  min-height: 28px;
  padding: 0 8px;
  font-size: 12px;
}

.ai-history__list {
  display: grid;
  gap: 6px;
  max-height: 116px;
  overflow: auto;
}

.ai-history__list > button {
  display: grid;
  gap: 3px;
  padding: 8px;
  text-align: left;
}

.ai-history__list > button.active,
.ai-history__list > button:hover {
  border-color: #0f766e;
  background: #ecfdf5;
}

.ai-history__list strong,
.ai-history__list small,
.ai-history__list span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ai-history__list strong {
  color: #0f172a;
  font-size: 12px;
}

.ai-history__list small,
.ai-history__list span,
.ai-history__empty {
  color: #64748b;
  font-size: 11px;
}

.ai-history__detail {
  display: grid;
  gap: 8px;
  max-height: 140px;
  overflow: auto;
  padding-top: 8px;
  border-top: 1px solid #e5e7eb;
}

.ai-history__detail article {
  display: grid;
  gap: 4px;
  padding: 8px;
  border-radius: 8px;
  background: #f8fafc;
}

.ai-history__detail article strong {
  color: #0f766e;
  font-size: 12px;
}

.ai-history__detail article p {
  margin: 0;
  color: #475569;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.builder-chatbot-wrap {
  position: relative;
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto auto;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}

.builder-chatbot {
  height: 100%;
  min-height: 0;
  overflow: auto;
}

.builder-chatbot :deep(t-chatbot) {
  display: grid;
  grid-template-rows: minmax(0, 1fr) auto;
  height: 100%;
  min-height: 0;
  background: transparent;
}

.builder-chatbot :deep(t-chat-list),
.builder-chatbot :deep(.t-chat-list) {
  min-height: 0;
  overflow: auto;
}

.builder-chatbot :deep(t-chat-sender),
.builder-chatbot :deep(.t-chat-sender) {
  overflow: hidden;
  border: 1px solid #e5e7eb;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.08);
}

.builder-chatbot :deep(textarea) {
  font-size: 13px;
  line-height: 1.7;
}

.ai-model-select {
  display: flex;
  align-items: center;
}

.ai-model-select__control {
  width: 128px;
}

.ai-model-select__control :deep(.t-input) {
  height: 30px;
  border-radius: 999px;
  padding: 0 12px;
  background: #f8fafc;
  box-shadow: none;
}

.ai-model-select__control :deep(.t-input.t-is-focused) {
  box-shadow: none;
}

:deep(.gjs-one-bg) {
  background-color: #fff;
}

:deep(.gjs-two-color) {
  color: #475569;
}

:deep(.gjs-three-bg) {
  background-color: #f8fafc;
}

:deep(.gjs-four-color),
:deep(.gjs-four-color-h:hover) {
  color: #0f766e;
}

:deep(.gjs-editor),
:deep(.gjs-pn-panel),
:deep(.gjs-pn-commands),
:deep(.gjs-pn-options),
:deep(.gjs-pn-devices-c),
:deep(.gjs-blocks-c),
:deep(.gjs-sm-sectors),
:deep(.gjs-layers),
:deep(.gjs-traits) {
  background: #fff;
  color: #0f172a;
}

:deep(.gjs-cv-canvas) {
  background: #eef2f7;
}

:deep(.gjs-cv-canvas__frames) {
  background: #f8fafc;
}

:deep(.gjs-cv-canvas__frame) {
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.12);
}

:deep(.gjs-block-category),
:deep(.gjs-sm-sector),
:deep(.gjs-layer),
:deep(.gjs-trt-trait) {
  border-color: #e5e7eb;
  background: #fff;
  color: #0f172a;
}

:deep(.gjs-block-category .gjs-title),
:deep(.gjs-sm-sector .gjs-sm-sector-title),
:deep(.gjs-trt-header),
:deep(.gjs-layer-title) {
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  font-weight: 700;
}

:deep(.gjs-sm-sector) {
  border: 0;
  border-bottom: 1px solid #e5e7eb;
}

:deep(.gjs-sm-sector .gjs-sm-sector-title),
:deep(.gjs-trt-header),
:deep(.gjs-layer-title) {
  min-height: 36px;
  padding: 9px 10px;
  border: 0;
  border-radius: 0;
  background: #f8fafc;
  font-size: 12px;
  letter-spacing: 0;
}

:deep(.gjs-sm-properties) {
  gap: 8px;
  padding: 10px 8px 12px;
}

:deep(.gjs-sm-property) {
  box-sizing: border-box;
  min-width: 0;
  padding: 4px;
}

:deep(.gjs-sm-label),
:deep(.gjs-trt-trait .gjs-label) {
  margin-bottom: 4px;
  color: #64748b;
  font-size: 11px;
}

:deep(.gjs-field),
:deep(.gjs-input-holder),
:deep(.gjs-select) {
  min-height: 32px;
  border: 1px solid #dfe5ec;
  border-radius: 6px;
  box-shadow: none;
}

:deep(.gjs-layer) {
  min-height: 34px;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.gjs-layer-title) {
  background: #fff;
  font-weight: 500;
}

:deep(.gjs-layer-title:hover) {
  background: #f8fafc;
}

:deep(.gjs-layer.gjs-selected .gjs-layer-title) {
  background: #ecfdf5;
  color: #0f766e;
}

:deep(.gjs-trt-trait) {
  gap: 8px;
  padding: 8px 10px;
  border-bottom: 1px solid #f1f5f9;
}

:deep(.cms-block-preview) {
  position: relative;
  display: grid;
  gap: 6px;
  width: 100%;
  height: 58px;
  padding: 8px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #f8fafc;
}

:deep(.cms-block-preview span),
:deep(.cms-block-preview strong),
:deep(.cms-block-preview i),
:deep(.cms-block-preview em) {
  display: block;
  min-width: 0;
  border-radius: 999px;
  background: #cbd5e1;
}

:deep(.cms-block-preview.section) {
  align-content: end;
  background: linear-gradient(135deg, #ecfeff, #f8fafc);
}

:deep(.cms-block-preview.section span) {
  width: 38%;
  height: 8px;
  background: #0f766e;
}

:deep(.cms-block-preview.section strong) {
  width: 72%;
  height: 14px;
  background: #334155;
}

:deep(.cms-block-preview.container) {
  padding: 10px 16px;
}

:deep(.cms-block-preview.container strong) {
  width: 100%;
  height: 30px;
  border: 1px dashed #94a3b8;
  border-radius: 8px;
  background: #fff;
}

:deep(.cms-block-preview.container span) {
  position: absolute;
  inset: 18px 28px;
  border-radius: 6px;
  background: #e2e8f0;
}

:deep(.cms-block-preview.grid) {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

:deep(.cms-block-preview.grid.three) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

:deep(.cms-block-preview.grid span) {
  height: 100%;
  border-radius: 7px;
  background: #e0f2fe;
}

:deep(.cms-block-preview.text.heading strong) {
  width: 80%;
  height: 16px;
  background: #0f172a;
}

:deep(.cms-block-preview.text.heading span) {
  width: 54%;
  height: 8px;
}

:deep(.cms-block-preview.text.paragraph span) {
  height: 7px;
}

:deep(.cms-block-preview.text.paragraph span:nth-child(1)) {
  width: 92%;
}

:deep(.cms-block-preview.text.paragraph span:nth-child(2)) {
  width: 78%;
}

:deep(.cms-block-preview.text.paragraph span:nth-child(3)) {
  width: 62%;
}

:deep(.cms-block-preview.button) {
  place-items: center;
}

:deep(.cms-block-preview.button span) {
  width: 64px;
  height: 24px;
  border-radius: 7px;
  background: #0f766e;
}

:deep(.cms-block-preview.image) {
  place-items: center;
  background: linear-gradient(135deg, #e2e8f0, #f8fafc);
}

:deep(.cms-block-preview.image span) {
  width: 54px;
  height: 32px;
  border-radius: 8px;
  background: linear-gradient(135deg, #94a3b8 0 45%, #cbd5e1 45% 100%);
}

:deep(.cms-block-preview.card) {
  padding: 10px;
  background: #fff;
}

:deep(.cms-block-preview.card strong) {
  width: 64%;
  height: 12px;
  background: #334155;
}

:deep(.cms-block-preview.card span) {
  height: 7px;
}

:deep(.cms-block-preview.card span:nth-child(2)) {
  width: 88%;
}

:deep(.cms-block-preview.card span:nth-child(3)) {
  width: 54%;
}

:deep(.cms-block-preview.divider) {
  place-items: center;
}

:deep(.cms-block-preview.divider span) {
  width: 88%;
  height: 2px;
}

:deep(.cms-block-preview.repeat) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

:deep(.cms-block-preview.repeat span) {
  height: 100%;
  border-radius: 7px;
  background: #ecfdf5;
}

:deep(.cms-block-preview.page-card) {
  grid-template-rows: 24px 8px 7px;
}

:deep(.cms-block-preview.page-card i) {
  border-radius: 7px;
  background: #cbd5e1;
}

:deep(.cms-block-preview.page-card strong) {
  width: 76%;
  height: 8px;
  background: #0f172a;
}

:deep(.cms-block-preview.page-card span) {
  width: 58%;
  height: 7px;
}

:deep(.cms-block-preview.tag) {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

:deep(.cms-block-preview.tag span) {
  width: 42px;
  height: 20px;
  border-radius: 999px;
  background: #dcfce7;
}

:deep(.cms-block-preview.visible strong) {
  width: 44%;
  height: 12px;
  background: #0f766e;
}

:deep(.cms-block-preview.visible span) {
  width: 76%;
  height: 8px;
}
:deep(.cms-block-preview.spacer) {
  place-items: center;
  background: #f8fafc;
}

:deep(.cms-block-preview.spacer span) {
  width: 100%;
  height: 6px;
  border-radius: 2px;
  background: #cbd5e1;
}

:deep(.cms-block-preview.badge) {
  display: flex;
  align-items: center;
  gap: 5px;
  flex-wrap: wrap;
}

:deep(.cms-block-preview.badge span) {
  height: 18px;
  border-radius: 999px;
  background: #dcfce7;
}

:deep(.cms-block-preview.badge span:nth-child(1)) {
  width: 42px;
}

:deep(.cms-block-preview.badge span:nth-child(2)) {
  width: 28px;
}

:deep(.cms-block-preview.list-item),
:deep(.cms-block-preview.quote),
:deep(.cms-block-preview.link) {
  place-items: center start;
  padding: 0 8px;
}

:deep(.cms-block-preview.list-item span),
:deep(.cms-block-preview.quote span),
:deep(.cms-block-preview.link span) {
  width: 64%;
  height: 8px;
  border-radius: 2px;
  background: #64748b;
}

:deep(.cms-block-preview.quote span) {
  border-left: 3px solid #0f766e;
  padding-left: 6px;
  width: 78%;
}

:deep(.cms-block-preview.link span) {
  width: 50%;
  height: 7px;
  text-decoration: underline;
  background: #0f766e;
}

:deep(.cms-block-preview.hero-center) {
  align-content: center;
  justify-items: center;
  text-align: center;
  gap: 8px;
}

:deep(.cms-block-preview.hero-center strong) {
  width: 62%;
  height: 14px;
  background: #0f172a;
}

:deep(.cms-block-preview.hero-center span) {
  width: 76%;
  height: 7px;
}

:deep(.cms-block-preview.hero-center em) {
  width: 46px;
  height: 20px;
  border-radius: 4px;
  background: #0f766e;
}

:deep(.cms-block-preview.hero-split) {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  align-items: center;
}

:deep(.cms-block-preview.hero-split-text) {
  display: grid;
  gap: 6px;
}

:deep(.cms-block-preview.hero-split-text strong) {
  width: 80%;
  height: 12px;
  background: #0f172a;
}

:deep(.cms-block-preview.hero-split-text span) {
  width: 100%;
  height: 6px;
}

:deep(.cms-block-preview.hero-split-text em) {
  width: 52px;
  height: 16px;
  border-radius: 4px;
  background: #0f766e;
}

:deep(.cms-block-preview.hero-split-media) {
  height: 100%;
  border-radius: 7px;
  background: #e2e8f0;
}

:deep(.cms-block-preview.features-3) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

:deep(.cms-block-preview.features-3 span) {
  height: 100%;
  border-radius: 8px;
  background: #f0fdfa;
  border: 1px solid #e5e7eb;
}

:deep(.cms-block-preview.cta-box) {
  align-content: center;
  justify-items: center;
  text-align: center;
  gap: 8px;
  padding: 10px;
  background: #0f766e;
}

:deep(.cms-block-preview.cta-box strong) {
  width: 58%;
  height: 12px;
  background: #ffffff;
}

:deep(.cms-block-preview.cta-box span) {
  width: 74%;
  height: 6px;
  background: rgba(255, 255, 255, .7);
}

:deep(.cms-block-preview.cta-box em) {
  width: 50px;
  height: 18px;
  border-radius: 4px;
  background: #ffffff;
}

:deep(.cms-block-preview.testimonial) {
  gap: 10px;
  padding: 10px;
}

:deep(.cms-block-preview.testimonial > span) {
  width: 92%;
  height: 7px;
}

:deep(.cms-block-preview.testimonial-avatar) {
  display: flex;
  align-items: center;
  gap: 8px;
}

:deep(.cms-block-preview.testimonial-avatar i) {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #e2e8f0;
}

:deep(.cms-block-preview.testimonial-avatar div) {
  display: grid;
  gap: 4px;
}

:deep(.cms-block-preview.testimonial-avatar strong) {
  width: 46px;
  height: 7px;
  background: #0f172a;
}

:deep(.cms-block-preview.testimonial-avatar em) {
  width: 32px;
  height: 5px;
  background: #94a3b8;
}

:deep(.cms-block-preview.pricing-3) {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

:deep(.cms-block-preview.pricing-3 span) {
  height: 100%;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid #e5e7eb;
}

:deep(.cms-block-preview.pricing-3 span:nth-child(2)) {
  border-color: #0f766e;
}

:deep(.cms-block-preview.footer-simple) {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 1fr;
  gap: 10px;
  padding: 10px;
  background: #0f172a;
}

:deep(.cms-block-preview.footer-simple .footer-col-wide),
:deep(.cms-block-preview.footer-simple .footer-col) {
  display: grid;
  gap: 6px;
  align-content: start;
}

:deep(.cms-block-preview.footer-simple .footer-col-wide strong) {
  width: 52%;
  height: 8px;
  background: #ffffff;
}

:deep(.cms-block-preview.footer-simple .footer-col-wide span) {
  width: 80%;
  height: 5px;
  background: #64748b;
}

:deep(.cms-block-preview.footer-simple .footer-col strong) {
  width: 54%;
  height: 7px;
  background: #ffffff;
}

:deep(.cms-block-preview.footer-simple .footer-col span) {
  width: 66%;
  height: 5px;
  background: #64748b;
}


:deep(.gjs-field),
:deep(.gjs-field input),
:deep(.gjs-field select),
:deep(.gjs-field textarea),
:deep(.gjs-input-holder input),
:deep(.gjs-select select) {
  border-color: #e5e7eb;
  background: #f8fafc;
  color: #0f172a;
}

:deep(.gjs-sm-property),
:deep(.gjs-clm-tags),
:deep(.gjs-layer-caret),
:deep(.gjs-layer-name),
:deep(.gjs-trt-trait__wrp) {
  color: #334155;
}

@media (max-width: 1180px) {
  .grapes-body {
    grid-template-columns: 220px minmax(0, 1fr) 300px;
  }

  .grapes-header__breadcrumbs {
    display: none;
  }

  .grapes-header__brand {
    flex-basis: 180px;
  }

  .grapes-shell.is-left-collapsed .grapes-body {
    grid-template-columns: 50px minmax(0, 1fr) 300px;
  }

  .grapes-shell.is-right-collapsed .grapes-body {
    grid-template-columns: 220px minmax(0, 1fr) 50px;
  }

  .grapes-shell.is-left-collapsed.is-right-collapsed .grapes-body {
    grid-template-columns: 50px minmax(0, 1fr) 50px;
  }

  .grapes-shell.is-preview .grapes-body {
    grid-template-columns: 0 minmax(0, 1fr) 0;
  }
}
</style>
