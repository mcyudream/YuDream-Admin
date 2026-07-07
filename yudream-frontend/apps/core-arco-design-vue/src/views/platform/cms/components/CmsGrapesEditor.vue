<script setup lang="ts">
import type { AIMessageContent, ChatMessagesData, ChatRequestParams, ChatServiceConfig, SSEChunkData } from '@tdesign-vue-next/chat'
import type * as grapesjs from 'grapesjs'
import type { Editor } from 'grapesjs'
import type { FileObject } from '@/api/modules/files'
import type { AiStreamEnvelope, AiToolCallResult, CmsChatHistoryMessage, CmsPageGenerateResult } from '@/api/modules/platform-ai'
import type { CmsAiChatAttachmentMeta, CmsAiChatSession, CmsAiChatSessionSummary } from '@/utils/cms-ai-chat-history'
import { registerHandler, removeHandler } from '@jboltai/tokui'
import { Chatbot } from '@tdesign-vue-next/chat'
import { Select as TSelect, Switch as TSwitch, Tooltip as TTooltip } from 'tdesign-vue-next'
import apiFiles from '@/api/modules/files'
import apiAi from '@/api/modules/platform-ai'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { clearCmsAiChatTarget, cmsAiChatTargetKey, deleteCmsAiChatSession, getCmsAiChatSession, listCmsAiChatSessionSummaries, saveCmsAiChatSession } from '@/utils/cms-ai-chat-history'
import CmsCodeEditor from './CmsCodeEditor.vue'
import TokuiBlock from './TokuiBlock.vue'
import '@tdesign-vue-next/chat/es/style/index.css'
import 'grapesjs/dist/css/grapes.min.css'

interface GrapesSavePayload {
  htmlContent: string
  cssContent: string
  jsContent: string
  builderProjectJson: string
}

type RightPanelTab = 'ai' | 'layers' | 'traits' | 'styles' | 'source'
interface AiModelSelectOption {
  label: string
  value: string
  providerCode?: string
  modelCode?: string
}

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
  aiModelOptions?: AiModelSelectOption[]
  historyTargetType?: 'page' | 'home'
  historyTargetId?: string | number | null
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
const chatbotRef = ref<{
  addPrompt?: (prompt: string, autoFocus?: boolean) => void
  setMessages?: (messages: ChatMessagesData[], mode?: 'replace' | 'prepend' | 'append') => void
  clearMessages?: () => void
}>()
const mediaItems = ref<FileObject[]>([])
const loadingMedia = ref(false)
const aiModel = ref('')
const aiThinkingEnabled = ref(false)
const aiAttachments = ref<any[]>([])
const chatHistorySummaries = ref<CmsAiChatSessionSummary[]>([])
const selectedChatHistory = ref<CmsAiChatSession | null>(null)
const chatHistoryLoading = ref(false)
const chatHistoryHasMore = ref(false)
const chatHistoryOffset = ref(0)
const chatHistoryLoadedOnce = ref(false)
const rightPanelTab = ref<RightPanelTab>(props.aiEnabled ? 'ai' : 'layers')
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
const reasoningActions = new Set(['reasoning'])
const silentProgressActions = new Set(['heartbeat', 'request', 'subscribed', 'stream-complete', 'complete'])
// AI 需求澄清：当模型调用 cms.ask.user 时，用 TokUI 渲染的一段可点击选项 DSL。
const pendingAskDsl = ref('')
const pendingAskOptions = ref<AskOption[]>([])
let editor: Editor | null = null
let pendingAiResult: CmsPageGenerateResult | null = null
let currentAiSession: CmsAiChatSession | null = null
let canvasRefreshTimer: ReturnType<typeof setTimeout> | null = null
let selectedSourceSyncTimer: ReturnType<typeof setTimeout> | null = null

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
    { label: 'CSS', value: `${(instance.getCss() || '').length} 字符` },
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
  const relatedCss = extractRelatedCss(editor?.getCss() || '', snapshot)
  return relatedCss ? formatCssText(relatedCss) : '/* 暂未匹配到选中元素的关联 CSS */'
})

const chatHistoryTargetType = computed(() => props.historyTargetType || 'page')
const chatHistoryTargetId = computed(() => String(props.historyTargetId || props.title || 'draft'))
const chatHistoryTargetKey = computed(() => cmsAiChatTargetKey(chatHistoryTargetType.value, chatHistoryTargetId.value))
const chatHistoryTargetLabel = computed(() => props.historyTargetLabel || props.title || (chatHistoryTargetType.value === 'home' ? '首页' : '未命名页面'))
const selectedAiModelOption = computed(() => props.aiModelOptions?.find(item => item.value === aiModel.value) || props.aiModelOptions?.[0] || null)

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
    try {
      aiAttachments.value = await Promise.all(Array.from(event.detail || []).map(toAttachmentItem))
    }
    catch (error) {
      toast.error(error instanceof Error ? error.message : '样图读取失败')
    }
  },
  onFileRemove: (event: CustomEvent<any[]>) => {
    aiAttachments.value = event.detail || []
  },
} as any))

const aiMessageProps = {
  avatar: (item: { role?: string }) => item.role === 'user' ? '你' : 'AI',
  name: (item: { role?: string }) => item.role === 'user' ? '你' : 'YuDream AI',
} as any

function normalizeAiStreamChunk(chunk: SSEChunkData): AiStreamEnvelope<Record<string, any>> {
  const data = (chunk.data || {}) as AiStreamEnvelope<Record<string, any>> & Record<string, any>
  if (String(data.event || '').startsWith('ai.')) {
    return {
      event: data.event,
      action: data.action,
      module: data.module,
      traceId: data.traceId,
      timestamp: data.timestamp,
      payload: data.payload || {},
    }
  }
  if (chunk.event === 'delta') {
    return { event: 'ai.message', action: 'delta', payload: { content: data.content } }
  }
  if (chunk.event === 'tool') {
    return { event: 'ai.tool', action: data.tool?.action || 'tool', payload: { tool: data.tool } }
  }
  if (chunk.event === 'result') {
    return { event: 'ai.result', action: 'complete', payload: { result: data.result } }
  }
  if (chunk.event === 'error') {
    return { event: 'ai.error', action: 'failed', payload: { message: data.message || data.content } }
  }
  return { event: chunk.event || 'ai.message', action: data.action, payload: data.payload || data }
}

const aiChatServiceConfig = computed<ChatServiceConfig>(() => ({
  endpoint: apiAi.generateCmsPageStreamEndpoint(),
  stream: true,
  timeout: 180000,
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
  isValidChunk: (chunk: SSEChunkData) => [
    'ai.message',
    'ai.progress',
    'ai.tool',
    'ai.result',
    'ai.error',
    'delta',
    'tool',
    'result',
    'error',
  ].includes(chunk.event || ''),
  onMessage: (chunk: SSEChunkData) => {
    const envelope = normalizeAiStreamChunk(chunk)
    if (envelope.event === 'ai.progress') {
      const content = String(envelope.payload?.content || '')
      const action = String(envelope.action || '')
      if (silentProgressActions.has(action)) {
        return null
      }
      if (reasoningActions.has(action)) {
        if (!aiThinkingEnabled.value) {
          return null
        }
        return trackAiHistoryContent(thinkingChunk(content))
      }
      return trackAiHistoryContent(progressChunk(formatProgress(envelope.action, content)))
    }
    if (envelope.event === 'ai.message') {
      return trackAiHistoryContent(markdownChunk(String(envelope.payload?.content || '')))
    }
    if (envelope.event === 'ai.tool') {
      const tool = envelope.payload?.tool
      if (isCanvasTool(tool)) {
        applyAiTool(tool)
      }
      if (isAskUserTool(tool)) {
        showAskUserUi(tool)
        return trackAiHistoryContent(markdownChunk(`\n\n${tool?.message || '请选择一个方向以便我继续。'}`))
      }
      return trackAiHistoryContent(markdownChunk(formatToolMessage(tool)))
    }
    if (envelope.event === 'ai.result') {
      const result = envelope.payload?.result || null
      pendingAiResult = result?.tools?.length ? null : result
      return trackAiHistoryContent(markdownChunk('\n\n画布操作已完成。'))
    }
    if (envelope.event === 'ai.error') {
      return trackAiHistoryContent({
        ...markdownChunk(`\n\n生成失败：${envelope.payload?.message || envelope.payload?.content || '未知错误'}`),
        status: 'error',
      } as AIMessageContent)
    }
    return null
  },
  onComplete: (isAborted: boolean) => {
    if (isAborted) {
      return
    }
    if (pendingAiResult) {
      applyAiResult(pendingAiResult)
      pendingAiResult = null
      clearAiAttachments()
      const completeChunk = trackAiHistoryContent(markdownChunk('\n\n画布已更新。'))
      void finishAiHistorySession('complete')
      return completeChunk
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

watch(() => props.aiEnabled, (enabled) => {
  if (!enabled && rightPanelTab.value === 'ai') {
    rightPanelTab.value = 'layers'
  }
  if (enabled && !rightPanelTabs.value.some(tab => tab.value === rightPanelTab.value)) {
    rightPanelTab.value = 'ai'
  }
})

watch(() => props.aiModelOptions, (options) => {
  if (!options?.length) {
    aiModel.value = ''
    return
  }
  if (!options.some(item => item.value === aiModel.value)) {
    aiModel.value = options[0].value
  }
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
  syncCanvasJs()
})

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
    blockManager: { appendTo: blocksEl.value! },
    layerManager: { appendTo: layersEl.value! },
    traitManager: { appendTo: traitsEl.value! },
    styleManager: {
      appendTo: stylesEl.value!,
      sectors: styleSectors(),
    },
    deviceManager: {
      devices: [
        { id: 'desktop', name: '桌面', width: '' },
        { id: 'tablet', name: '平板', width: '768px' },
        { id: 'mobile', name: '手机', width: '390px' },
      ],
    },
    canvas: {
      styles: [],
    },
    panels: { defaults: [] },
  })
  registerDynamicTypes(editor)
  registerBlocks(editor)
  loadInitialContent(editor)
  removeLayoutBlocks(editor)
  injectCanvasHighlightStyle(editor)
  editor.on('component:add component:remove style:update undo redo load', () => {
    scheduleCanvasRefresh(80)
    scheduleSelectedSourceSync(80)
  })
  editor.on('component:update', () => {
    scheduleCanvasRefresh(220)
    scheduleSelectedSourceSync(220)
  })
  editor.on('component:selected component:deselected', () => {
    clearScheduledCanvasWork()
    canvasRevision.value += 1
    syncSelectedSource(true)
  })
  canvasRevision.value += 1
  syncCanvasJs()
  await loadMedia()
  aiModel.value = props.aiModelOptions?.[0]?.value || ''
  registerHandler('pick', onPickOption)
  if (rightPanelTab.value === 'ai') {
    void loadChatHistory(true)
    void restoreActiveSession()
  }
})

onBeforeUnmount(() => {
  clearScheduledCanvasWork()
  removeHandler('pick')
  editor?.destroy()
  editor = null
})

function loadInitialContent(instance: Editor) {
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
}

function save() {
  if (!editor) {
    return
  }
  removeLayoutBlocks(editor)
  emit('save', {
    htmlContent: editor.getHtml(),
    cssContent: editor.getCss() || '',
    jsContent: stripScriptTags(pageJsContent.value),
    builderProjectJson: JSON.stringify(editor.getProjectData()),
  })
}

function removeLayoutBlocks(instance: Editor) {
  instance.getWrapper()?.find('[data-yb-system-nav]').forEach(component => component.remove())
}

function command(command: string) {
  editor?.runCommand(command)
}

function setDevice(device: 'desktop' | 'tablet' | 'mobile') {
  editor?.setDevice(device)
}

function useSuggestion(suggestion: string) {
  chatbotRef.value?.addPrompt?.(suggestion, true)
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
    model: selectedAiModelOption.value?.label || aiModel.value || undefined,
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
  session.model = selectedAiModelOption.value?.label || aiModel.value || session.model
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
  currentAiSession.preview = compactText(messageText(target), 120) || currentAiSession.preview
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
  chatbotRef.value?.setMessages?.([...aiDefaultMessages.value, ...selectedChatHistory.value.messages], 'replace')
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
    chatbotRef.value?.setMessages?.(messages, 'replace')
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
  chatbotRef.value?.setMessages?.([...aiDefaultMessages.value], 'replace')
  await loadChatHistory(true)
  toast.success('对话记录已清空')
}

function buildAiPayload(prompt: string, attachments: any[] = [], history: CmsChatHistoryMessage[] = []) {
  if (!editor) {
    throw new Error('构建器未初始化')
  }
  removeLayoutBlocks(editor)
  const image = firstImageAttachment(attachments)
  const modelOption = selectedAiModelOption.value
  return {
    title: props.title || '',
    siteName: appSettingsStore.siteName || 'YuDream',
    prompt: prompt || `参考样图调整当前页面：${image?.name || '样图'}`,
    pageType: 'GrapesJS 可视化页面',
    style: '保持当前页面风格，按用户要求增量修改；如果用户要求重构，可以替换为更完整的设计。不要生成系统导航栏和页脚，它们由站点 Layout 渲染。',
    providerCode: modelOption?.providerCode,
    modelCode: modelOption?.modelCode || aiModel.value || undefined,
    model: modelOption?.modelCode || aiModel.value || undefined,
    imageDataUrl: image?.url || undefined,
    currentHtml: editor.getHtml(),
    currentCss: editor.getCss(),
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
    syncCanvasJs()
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

function progressChunk(data: string): AIMessageContent {
  return {
    type: 'markdown',
    id: 'cms-ai-progress',
    data,
    strategy: 'merge',
  }
}

function thinkingChunk(text: string): AIMessageContent {
  return {
    type: 'thinking',
    id: 'cms-ai-thinking',
    data: {
      title: '思考中',
      text,
    },
    status: 'streaming',
    strategy: 'merge',
  } as AIMessageContent
}

function formatProgress(action?: string, content?: string) {
  const safeText = content || '处理中...'
  const safeLabelMap: Record<string, string> = {
    'accepted': '已接收',
    'analysis': '分析',
    'request': '请求',
    'subscribed': '连接',
    'reasoning': '思考',
    'heartbeat': '心跳',
    'tool-start': '工具',
    'tool-complete': '工具',
    'first-delta': '输出',
    'stream-complete': '汇总',
    'complete': '完成',
  }
  return `\n\n> ${safeLabelMap[String(action || '')] || '进度'}：${safeText}`
}

function formatToolMessage(tool?: AiToolCallResult) {
  const safeName = tool?.toolName || '未知工具'
  const safeAction = tool?.action || '执行'
  const safeMessage = tool?.message || '工具调用完成'
  return `\n\n> 工具：${safeName} / ${safeAction}\n\n${safeMessage}`
}

function isCanvasTool(tool?: AiToolCallResult) {
  return tool?.toolName === 'cms.canvas.patch'
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
  chatbotRef.value?.addPrompt?.(title, true)
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

function applyAiResult(result: CmsPageGenerateResult) {
  if (result.tools?.length) {
    result.tools.filter(isCanvasTool).forEach(applyAiTool)
    return
  }
  if (!editor) {
    return
  }
  if (result.builderProjectJson) {
    try {
      editor.loadProjectData(JSON.parse(result.builderProjectJson))
      if (result.jsContent) {
        setCanvasJs(result.jsContent)
      }
      return
    }
    catch {
      toast.warning('AI 返回的 Project JSON 无法解析，已使用 HTML/CSS 更新画布')
    }
  }
  if (result.htmlContent) {
    editor.setComponents(result.htmlContent)
  }
  if (result.cssContent) {
    editor.setStyle(result.cssContent)
  }
  if (result.jsContent) {
    setCanvasJs(result.jsContent)
  }
}

function applyAiTool(tool?: AiToolCallResult) {
  if (!editor || !tool) {
    return
  }
  if (tool.toolName !== 'cms.canvas.patch') {
    toast.warning(`暂不支持的 AI 工具：${tool.toolName || '未知工具'}`)
    return
  }
  const payload = tool.payload || {}
  const action = tool.action || 'replace-page'
  if (action === 'load-project' || (action === 'replace-page' && payload.builderProjectJson)) {
    try {
      editor.loadProjectData(JSON.parse(String(payload.builderProjectJson)))
      if (hasPayloadKey(payload, 'jsContent')) {
        setCanvasJs(String(payload.jsContent || ''))
      }
      canvasRevision.value += 1
      return
    }
    catch {
      toast.warning('AI 返回的 Project JSON 无法解析，已继续应用 HTML/CSS')
    }
  }
  if (action === 'replace-page' || action === 'set-html') {
    editor.setComponents(String(payload.htmlContent || ''))
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
  if (action === 'add-html' && payload.htmlContent) {
    const added = editor.addComponents(String(payload.htmlContent))
    highlightAddedComponents(added)
  }
  if (action === 'add-html' && payload.jsContent) {
    appendCanvasJs(String(payload.jsContent))
  }
  if (action === 'remove-selector' && payload.selector) {
    editor.getWrapper()?.find(String(payload.selector)).forEach(component => component.remove())
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
  syncCanvasJs()
  canvasRevision.value += 1
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
function appendCanvasCss(css: string) {
  if (!editor || !css.trim()) {
    return
  }
  const existing = editor.getCss() || ''
  editor.setStyle(`${existing}\n${css}`.trim())
}

function setCanvasJs(js: string) {
  pageJsContent.value = stripScriptTags(js)
  syncCanvasJs()
}

function appendCanvasJs(js: string) {
  const clean = stripScriptTags(js)
  if (!clean.trim()) {
    return
  }
  pageJsContent.value = [pageJsContent.value, clean].filter(item => item.trim()).join('\n\n')
  syncCanvasJs()
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
    doc.querySelectorAll('script[data-yb-cms-page-script]').forEach(item => item.remove())
    const code = stripScriptTags(pageJsContent.value).trim()
    if (!code) {
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

function registerBlocks(instance: Editor) {
  cmsBlocks().forEach(block => instance.BlockManager.add(block.id || '', block))
}

function cmsBlocks(): grapesjs.BlockProperties[] {
  return [
    {
      id: 'yb-section',
      label: '区段 Section',
      category: '布局',
      media: preview('section'),
      content: `<section style="padding:56px 48px; background:#ffffff;">
  <div style="max-width:1120px; margin:0 auto;"></div>
</section>`,
    },
    {
      id: 'yb-container',
      label: '内容容器',
      category: '布局',
      media: preview('container'),
      content: `<div style="max-width:1120px; margin:0 auto; padding:24px;"></div>`,
    },
    {
      id: 'yb-grid-2',
      label: '双列布局',
      category: '布局',
      media: preview('grid2'),
      content: `<div style="display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:20px;">
  <div style="min-height:120px; padding:20px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
  <div style="min-height:120px; padding:20px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
</div>`,
    },
    {
      id: 'yb-grid-3',
      label: '三列布局',
      category: '布局',
      media: preview('grid3'),
      content: `<div style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px;">
  <div style="min-height:120px; padding:18px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
  <div style="min-height:120px; padding:18px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
  <div style="min-height:120px; padding:18px; border:1px dashed #cbd5e1; border-radius:12px;"></div>
</div>`,
    },
    {
      id: 'yb-heading',
      label: '标题 Heading',
      category: '文字',
      media: preview('heading'),
      content: `<h2 style="margin:0 0 14px; color:#0f172a; font-size:40px; line-height:1.1; font-weight:900;">页面标题</h2>`,
    },
    {
      id: 'yb-paragraph',
      label: '段落 Paragraph',
      category: '文字',
      media: preview('paragraph'),
      content: `<p style="margin:0 0 16px; color:#475569; font-size:16px; line-height:1.8;">这里填写正文内容，可以在右侧面板调整字体、颜色、行高和间距。</p>`,
    },
    {
      id: 'yb-button',
      label: '按钮 Button',
      category: '基础组件',
      media: preview('button'),
      content: `<a href="/site" style="display:inline-flex; align-items:center; justify-content:center; min-height:42px; padding:0 18px; border-radius:8px; background:#0f766e; color:#ffffff; font-weight:800; text-decoration:none;">立即查看</a>`,
    },
    {
      id: 'yb-image',
      label: '图片 Image',
      category: '媒体',
      media: preview('image'),
      content: `<img src="{{site.logo}}" alt="图片" style="display:block; width:100%; max-width:720px; aspect-ratio:16/9; object-fit:cover; border-radius:12px; background:#e2e8f0;">`,
    },
    {
      id: 'yb-card',
      label: '卡片 Card',
      category: '基础组件',
      media: preview('card'),
      content: `<article style="display:grid; width:min(100%, 380px); gap:12px; padding:22px; border:1px solid #e5e7eb; border-radius:12px; background:#ffffff; box-shadow:0 10px 28px rgba(15,23,42,.06);">
  <h3 style="margin:0; color:#0f172a; font-size:22px;">卡片标题</h3>
  <p style="margin:0; color:#64748b; line-height:1.7;">卡片内容描述。</p>
</article>`,
    },
    {
      id: 'yb-divider',
      label: '分割线 Divider',
      category: '基础组件',
      media: preview('divider'),
      content: `<hr style="width:100%; margin:28px 0; border:0; border-top:1px solid #e5e7eb;">`,
    },
    {
      id: 'yb-repeat-wrapper',
      label: '动态循环容器',
      category: '动态数据',
      media: preview('repeat'),
      content: `<div data-yb-repeat="pages" style="display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:18px;"></div>`,
    },
    {
      id: 'yb-page-card',
      label: '页面数据卡片',
      category: '动态数据',
      media: preview('pageCard'),
      content: `<article style="display:grid; gap:12px; padding:18px; border:1px solid #e5e7eb; border-radius:12px; background:#ffffff;">
  <img src="{{item.coverImageUrl}}" alt="{{item.title}}" style="width:100%; aspect-ratio:16/10; object-fit:cover; border-radius:10px; background:#e2e8f0;">
  <small style="color:#0f766e; font-weight:800;">{{item.category}} · {{item.publishedAt}}</small>
  <h3 style="margin:0; color:#0f172a; font-size:22px;">{{item.title}}</h3>
  <p style="margin:0; color:#64748b;">{{item.excerpt}}</p>
  <a href="{{item.url}}" style="color:#0f766e; font-weight:800;">阅读全文</a>
</article>`,
    },
    {
      id: 'yb-tag-link',
      label: '分类/标签链接',
      category: '动态数据',
      media: preview('tag'),
      content: `<a href="{{item.url}}" style="display:inline-flex; align-items:center; min-height:34px; padding:0 12px; border-radius:999px; background:#ecfdf5; color:#047857; font-weight:700; text-decoration:none;">{{item.label}} · {{item.count}}</a>`,
    },
    {
      id: 'yb-guest-box',
      label: '游客可见容器',
      category: '动态数据',
      media: preview('visible'),
      content: `<div data-visible-when="guest" style="padding:20px; border:1px dashed #cbd5e1; border-radius:12px;">游客可见内容</div>`,
    },
    {
      id: 'yb-user-box',
      label: '登录可见容器',
      category: '动态数据',
      media: preview('visible'),
      content: `<div data-visible-when="logged-in" style="padding:20px; border:1px dashed #cbd5e1; border-radius:12px;">{{auth.welcome}}</div>`,
    },
  ]
}

function preview(type: string) {
  const previewMap: Record<string, string> = {
    section: '<div class="cms-block-preview section"><span></span><strong></strong></div>',
    container: '<div class="cms-block-preview container"><strong></strong><span></span></div>',
    grid2: '<div class="cms-block-preview grid two"><span></span><span></span></div>',
    grid3: '<div class="cms-block-preview grid three"><span></span><span></span><span></span></div>',
    heading: '<div class="cms-block-preview text heading"><strong></strong><span></span></div>',
    paragraph: '<div class="cms-block-preview text paragraph"><span></span><span></span><span></span></div>',
    button: '<div class="cms-block-preview button"><span></span></div>',
    image: '<div class="cms-block-preview image"><span></span></div>',
    card: '<div class="cms-block-preview card"><strong></strong><span></span><span></span></div>',
    divider: '<div class="cms-block-preview divider"><span></span></div>',
    repeat: '<div class="cms-block-preview repeat"><span></span><span></span><span></span></div>',
    pageCard: '<div class="cms-block-preview page-card"><i></i><strong></strong><span></span></div>',
    tag: '<div class="cms-block-preview tag"><span></span><span></span><span></span></div>',
    visible: '<div class="cms-block-preview visible"><strong></strong><span></span></div>',
  }
  return previewMap[type] || '<div class="cms-block-preview"></div>'
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
</script>

<template>
  <div class="grapes-shell">
    <header class="grapes-header">
      <div>
        <strong>{{ title || 'CMS 构建器' }}</strong>
        <span>GrapesJS 可视化编辑</span>
      </div>
      <div class="grapes-header__actions">
        <FaButton variant="outline" size="sm" @click="command('core:undo')">
          <FaIcon name="i-ri:arrow-go-back-line" />
        </FaButton>
        <FaButton variant="outline" size="sm" @click="command('core:redo')">
          <FaIcon name="i-ri:arrow-go-forward-line" />
        </FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('desktop')">
          桌面
        </FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('tablet')">
          平板
        </FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('mobile')">
          手机
        </FaButton>
        <FaButton size="sm" @click="save">
          <FaIcon name="i-ri:save-3-line" />
          保存
        </FaButton>
        <FaButton variant="outline" size="sm" @click="emit('close')">
          <FaIcon name="i-ri:close-line" />
        </FaButton>
      </div>
    </header>

    <div class="grapes-body">
      <aside class="grapes-sidebar left">
        <section>
          <h3>区块</h3>
          <div ref="blocksEl" />
        </section>
        <section>
          <div class="media-head">
            <h3>媒体</h3>
            <FaButton size="sm" variant="outline" :loading="loadingMedia" @click="loadMedia">
              <FaIcon name="i-ri:refresh-line" />
            </FaButton>
          </div>
          <div class="media-actions">
            <FaButton size="sm" @click="pickMedia">
              <FaIcon name="i-ri:upload-cloud-2-line" />
              上传
            </FaButton>
            <input ref="mediaInput" type="file" accept="image/*" hidden @change="uploadMedia">
          </div>
          <div class="media-grid">
            <button v-for="item in mediaItems" :key="item.id" type="button" @click="insertImage(item)">
              <img :src="toBackendAssetUrl(item.url)" :alt="item.originalName || 'CMS 图片'">
            </button>
          </div>
        </section>
      </aside>

      <main class="grapes-canvas">
        <div ref="editorEl" class="grapes-editor" />
      </main>

      <aside class="grapes-sidebar right">
        <div class="right-tabs" role="tablist" aria-label="构建器右侧面板">
          <button
            v-for="tab in rightPanelTabs"
            :key="tab.value"
            type="button"
            :class="{ active: rightPanelTab === tab.value }"
            role="tab"
            :aria-selected="rightPanelTab === tab.value"
            @click="rightPanelTab = tab.value"
          >
            <FaIcon :name="tab.icon" />
            <span>{{ tab.label }}</span>
          </button>
        </div>

        <section v-if="aiEnabled" class="right-panel ai-panel" :class="{ active: rightPanelTab === 'ai' }">
          <div class="builder-chatbot-wrap">
            <Chatbot
              ref="chatbotRef"
              class="builder-chatbot"
              layout="single"
              :default-messages="aiDefaultMessages"
              :chat-service-config="aiChatServiceConfig"
              :sender-props="aiSenderProps"
              :message-props="aiMessageProps"
            >
              <template #sender-footer-prefix>
                <div v-if="aiModelOptions?.length" class="ai-model-select">
                  <TTooltip content="切换模型" trigger="hover">
                    <TSelect
                      v-model="aiModel"
                      class="ai-model-select__control"
                      :options="aiModelOptions"
                      size="small"
                    />
                  </TTooltip>
                </div>
                <label class="ai-thinking-switch">
                  <TSwitch v-model="aiThinkingEnabled" size="small" />
                  <span>深度思考</span>
                </label>
              </template>
            </Chatbot>
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
                  <button type="button" :disabled="chatHistoryLoading" @click="loadChatHistory(true)">
                    刷新
                  </button>
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
                  <small>{{ formatHistoryTime(item.updatedAt) }} · {{ item.model || '默认模型' }}</small>
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

          <div class="ai-suggestions" aria-label="AI 快捷指令">
            <button v-for="suggestion in aiSuggestions" :key="suggestion" type="button" @click="useSuggestion(suggestion)">
              {{ suggestion }}
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
              <FaButton variant="outline" size="sm" :disabled="!hasSelectedComponent" @click="syncSelectedSource(true)">
                <FaIcon name="i-ri:refresh-line" />
              </FaButton>
              <FaButton variant="outline" size="sm" :disabled="!hasSelectedComponent || !selectedSourceCode.trim()" @click="formatSelectedSource">
                <FaIcon name="i-ri:align-left" />
                格式化
              </FaButton>
              <FaButton size="sm" :disabled="!hasSelectedComponent || !selectedSourceDirty" @click="applySelectedSource">
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
                :disabled="!hasSelectedComponent"
                :completions="htmlSourceCompletions"
                @update:model-value="markSelectedSourceDirty"
              />
            </section>
            <section class="source-editor-section source-editor-section--css">
              <div class="source-editor-section__head">
                <strong>关联 CSS</strong>
                <span>根据当前选择自动匹配</span>
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
                <span>自动注入预览并随页面保存</span>
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
            <span v-else>{{ hasSelectedComponent ? '已同步' : '未选择元素' }}</span>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.grapes-shell {
  display: grid;
  grid-template-rows: auto minmax(0, 1fr);
  height: 100vh;
  background: #f8fafc;
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
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid #e5e7eb;
  background: #fff;
}

.grapes-header strong,
.grapes-header span {
  display: block;
}

.grapes-header span {
  color: #64748b;
  font-size: 12px;
}

.grapes-body {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr) 360px;
  min-height: 0;
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
  min-width: 0;
  min-height: 0;
}

.grapes-editor {
  height: 100%;
}

.media-head {
  justify-content: space-between;
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
  grid-template-rows: minmax(0, 1fr) auto;
  gap: 10px;
  min-height: 0;
  overflow: hidden;
}

.builder-chatbot {
  height: 100%;
  min-height: 0;
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

:deep(.gjs-block) {
  width: 100%;
  min-height: 116px;
  margin: 0 0 10px;
  padding: 10px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #334155;
  box-shadow: none;
}

:deep(.gjs-block:hover) {
  border-color: #0f766e;
  color: #0f766e;
  box-shadow: 0 8px 22px rgba(15, 118, 110, 0.12);
}

:deep(.gjs-block-label) {
  color: inherit;
  font-size: 12px;
  font-weight: 700;
  line-height: 1.35;
}

:deep(.gjs-block__media) {
  width: 100%;
  margin: 0 0 8px;
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
    grid-template-columns: 220px minmax(0, 1fr);
  }

  .grapes-sidebar.right {
    display: none;
  }
}
</style>
