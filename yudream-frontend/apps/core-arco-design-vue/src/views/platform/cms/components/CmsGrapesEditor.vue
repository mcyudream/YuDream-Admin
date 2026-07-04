<script setup lang="ts">
import type grapesjs from 'grapesjs'
import type { Editor } from 'grapesjs'
import type { AIMessageContent, ChatMessagesData, ChatRequestParams, ChatServiceConfig, SSEChunkData } from '@tdesign-vue-next/chat'
import type { FileObject } from '@/api/modules/files'
import type { AiStreamEnvelope, AiToolCallResult, CmsPageGenerateResult } from '@/api/modules/platform-ai'
import { Chatbot } from '@tdesign-vue-next/chat'
import { Select as TSelect, Tooltip as TTooltip } from 'tdesign-vue-next'
import apiFiles from '@/api/modules/files'
import apiAi from '@/api/modules/platform-ai'
import { toBackendAssetUrl } from '@/utils/backend-url'
import '@tdesign-vue-next/chat/es/style/index.css'
import 'grapesjs/dist/css/grapes.min.css'

interface GrapesSavePayload {
  htmlContent: string
  cssContent: string
  builderProjectJson: string
}

type RightPanelTab = 'ai' | 'layers' | 'traits' | 'styles'

const props = defineProps<{
  htmlContent?: string
  cssContent?: string
  builderProjectJson?: string
  title?: string
  aiEnabled?: boolean
  aiModelOptions?: { label: string, value: string }[]
}>()

const emit = defineEmits<{
  close: []
  save: [payload: GrapesSavePayload]
}>()

const toast = useFaToast()
const editorEl = ref<HTMLElement>()
const blocksEl = ref<HTMLElement>()
const layersEl = ref<HTMLElement>()
const traitsEl = ref<HTMLElement>()
const stylesEl = ref<HTMLElement>()
const mediaInput = ref<HTMLInputElement>()
const chatbotRef = ref<{ addPrompt?: (prompt: string, autoFocus?: boolean) => void }>()
const mediaItems = ref<FileObject[]>([])
const loadingMedia = ref(false)
const aiModel = ref('')
const aiAttachments = ref<any[]>([])
const rightPanelTab = ref<RightPanelTab>(props.aiEnabled ? 'ai' : 'layers')
const canvasRevision = ref(0)
const aiSuggestions = [
  '优化首屏视觉，让层次更清晰',
  '新增三列功能卡片并统一按钮样式',
  '根据样图调整版式和配色',
  '优化移动端排版和间距',
]
let editor: Editor | null = null
let pendingAiResult: CmsPageGenerateResult | null = null

const rightPanelTabs = computed(() => {
  const tabs: { label: string, value: RightPanelTab, icon: string }[] = [
    { label: '图层', value: 'layers', icon: 'i-ri:stack-line' },
    { label: '属性', value: 'traits', icon: 'i-ri:settings-3-line' },
    { label: '样式', value: 'styles', icon: 'i-ri:palette-line' },
  ]
  return props.aiEnabled
    ? [{ label: 'AI', value: 'ai' as const, icon: 'i-ri:sparkling-2-line' }, ...tabs]
    : tabs
})

const canvasStats = computed(() => {
  canvasRevision.value
  if (!editor) {
    return [
      { label: 'HTML', value: '待加载' },
      { label: 'CSS', value: '待加载' },
      { label: '项目源', value: '待加载' },
    ]
  }
  return [
    { label: 'HTML', value: `${editor.getHtml().length} 字符` },
    { label: 'CSS', value: `${editor.getCss().length} 字符` },
    { label: '项目源', value: `${JSON.stringify(editor.getProjectData()).length} 字符` },
  ]
})

const aiDefaultMessages = computed<ChatMessagesData[]>(() => [
  {
    id: 'cms-ai-welcome',
    role: 'assistant',
    status: 'complete',
    content: [
      {
        type: 'markdown',
        data: '我可以读取当前 GrapesJS 画布、CSS 和项目 JSON，并按你的描述直接修改或增加区块。你也可以附一张样图让我参考。',
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
}))

const aiMessageProps = {
  avatar: (item: { role?: string }) => item.role === 'user' ? '你' : 'AI',
  name: (item: { role?: string }) => item.role === 'user' ? '你' : 'YuDream AI',
}

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
    return apiAi.generateCmsPageStreamRequest(buildAiPayload(prompt, params.attachments || aiAttachments.value))
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
    if (envelope.event === 'ai.message' || envelope.event === 'ai.progress') {
      return markdownChunk(String(envelope.payload?.content || ''))
    }
    if (envelope.event === 'ai.tool') {
      applyAiTool(envelope.payload?.tool)
      return null
    }
    if (envelope.event === 'ai.result') {
      const result = envelope.payload?.result || null
      pendingAiResult = result?.tools?.length ? null : result
      return markdownChunk('\n\n画布操作已完成。')
    }
    if (envelope.event === 'ai.error') {
      return {
        ...markdownChunk(`\n\n生成失败：${envelope.payload?.message || envelope.payload?.content || '未知错误'}`),
        status: 'error',
      } as AIMessageContent
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
      return markdownChunk('\n\n画布已更新。')
    }
  },
  onError: (error: Error | Response) => {
    const message = error instanceof Response ? `${error.status} ${error.statusText}` : error.message
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
  editor.on('component:add component:update component:remove style:update undo redo load', () => {
    canvasRevision.value += 1
  })
  canvasRevision.value += 1
  await loadMedia()
  aiModel.value = props.aiModelOptions?.[0]?.value || ''
})

onBeforeUnmount(() => {
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

function save() {
  if (!editor) {
    return
  }
  removeLayoutBlocks(editor)
  emit('save', {
    htmlContent: editor.getHtml(),
    cssContent: editor.getCss(),
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

function buildAiPayload(prompt: string, attachments: any[] = []) {
  if (!editor) {
    throw new Error('构建器未初始化')
  }
  removeLayoutBlocks(editor)
  const image = firstImageAttachment(attachments)
  return {
    title: props.title || '',
    prompt: prompt || `参考样图调整当前页面：${image?.name || '样图'}`,
    pageType: 'GrapesJS 可视化页面',
    style: '保持当前页面风格，按用户要求增量修改；如果用户要求重构，可以替换为更完整的设计。不要生成系统导航栏和页脚，它们由站点 Layout 渲染。',
    model: aiModel.value || undefined,
    imageDataUrl: image?.url || undefined,
    currentHtml: editor.getHtml(),
    currentCss: editor.getCss(),
    currentProjectJson: JSON.stringify(editor.getProjectData()),
  }
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

function applyAiResult(result: CmsPageGenerateResult) {
  if (result.tools?.length) {
    result.tools.forEach(applyAiTool)
    return
  }
  if (!editor) {
    return
  }
  if (result.builderProjectJson) {
    try {
      editor.loadProjectData(JSON.parse(result.builderProjectJson))
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
  if (action === 'add-html' && payload.htmlContent) {
    editor.addComponents(String(payload.htmlContent))
  }
  if (action === 'remove-selector' && payload.selector) {
    editor.getWrapper()?.find(String(payload.selector)).forEach(component => component.remove())
  }
  canvasRevision.value += 1
}

function clearAiAttachments() {
  aiAttachments.value = []
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
  cmsBlocks().forEach(block => instance.BlockManager.add(block.id, block))
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
        <FaButton variant="outline" size="sm" @click="setDevice('desktop')">桌面</FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('tablet')">平板</FaButton>
        <FaButton variant="outline" size="sm" @click="setDevice('mobile')">手机</FaButton>
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
              </template>
            </Chatbot>
          </div>

          <div class="ai-suggestions" aria-label="AI 快捷指令">
            <button v-for="suggestion in aiSuggestions" :key="suggestion" type="button" @click="useSuggestion(suggestion)">
              {{ suggestion }}
            </button>
          </div>

          <div class="ai-context">
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
  grid-template-columns: repeat(4, minmax(0, 1fr));
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

.builder-chatbot-wrap {
  position: relative;
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
