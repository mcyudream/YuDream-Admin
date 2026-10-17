<script setup lang="ts">
import type { YdMarkdownToolbar, YdSegmentedOption, YdTreeNode, YdWorkspaceRailItem, YdWorkspaceStatusItem, YdWorkspaceTab } from '@yudream/components'
import type { WikiSpaceCoverFields } from '../wiki-admin-api'
import type { WikiDraftFields } from '../wiki-editor-state'
import type { WikiAuthorCommentAnchor } from './wiki-author-comments-api'
import type { WikiNode, WikiSpace } from '@/api/modules/platform-wiki'
import type { WikiAnchorBlock, WikiAnchorBlockType, WikiQuoteAnchorMatch } from '@/views/wiki/wiki-quote-anchor'
import { useMediaQuery } from '@vueuse/core'
import { useIsMobile, YdTree } from '@yudream/components'
import { computed, inject, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { deleteWikiNode, publishWikiNode, saveWikiNode, unpublishWikiNode } from '@/api/modules/platform-wiki'
import { rewriteApiFileUrls } from '@/utils/api-file-url'
import { buildWikiQuoteAnchor, WIKI_QUOTE_ANCHOR_LIMITS } from '@/views/wiki/wiki-quote-anchor'
import { createWikiDocuments } from '../wiki-documents'
import { wikiDraftFields } from '../wiki-editor-state'
import {
  pageTypeDot,
  pageTypeLabel,
  pageTypeOptions,
  resolveWikiLink,
  wikiImageUploadHint,
  wikilinksToMarkdown,
  wikiWorkbenchKey,
} from '../wiki-utils'
import { toWikiAuthorCommentAnchor, wikiNormalizedOffsetAt } from './wiki-author-comments-api'
import WikiAuthorComments from './WikiAuthorComments.vue'
import WikiMarkdownEditor from './WikiMarkdownEditor.vue'

/**
 * 作者编辑区：VS Code 式工作区（活动栏 / 目录栏 / 多标签编辑器 / 属性栏 / 状态栏）。
 *
 * 1. 多标签多文件：每个打开的页面对应一个文档标签，各自持有独立草稿与保存态，
 *    切换标签不丢未保存内容（草稿归 `wiki-documents` 管理，组件只渲染当前激活文档）；
 * 2. 分栏可拖拽：目录栏与属性栏宽度由 YdWorkspaceLayout 承载（可拖拽、可收起、按 storage-key 记忆）；
 * 3. 窄屏：外壳自动退化为单面板 + 面板切换器，不再为移动端维护第二套模板；
 * 4. 未保存是「可离开」的：切标签不拦截，只有关闭标签、切换知识库、离开工作区才确认。
 */
type WikiTreeNode = YdTreeNode & {
  nodeType: 'DIRECTORY' | 'PAGE'
  isDirectory: boolean
  icon: string
  expandedIcon: string
  published: boolean
  typeLabel: string
  typeDot?: string
}

const store = inject(wikiWorkbenchKey)!
const toast = useYdToast()
const modal = useYdModal()

const documents = createWikiDocuments()
const activeDocument = documents.active
const saving = ref(false)
const publishing = ref(false)
const viewMode = ref<'edit' | 'preview'>('edit')
const treeKeyword = ref('')
const expandedTreeKeys = ref<string[]>([])
const selectedTreeKeys = ref<string[]>([])
/** 活动栏视图：目录（页面树）与大纲（正文标题） */
const rail = ref('files')
const sidebarOpen = ref(true)
const asideOpen = ref(false)
const pane = ref<'sidebar' | 'main' | 'aside'>('main')
const asideTab = ref<string>('meta')
/** 属性栏分组折叠态：基础信息与关联默认展开，来源默认收起（属性栏窄，先看结构） */
const asideGroups = ref({ base: true, relations: true, sources: false })
const isMobile = useIsMobile()
// 窄屏（<1024px）默认收起属性栏，让正文优先；≥1280px 默认展开
const isWide = useMediaQuery('(min-width: 1280px)')
const editorRef = ref<InstanceType<typeof WikiMarkdownEditor> | null>(null)
const linkVisible = ref(false)
const linkedPageId = ref('')
const space = computed(() => store.space.value)
const node = computed<WikiNode | null>(() => activeDocument.value?.node ?? null)
const isPage = computed(() => node.value?.nodeType === 'PAGE')
const dirty = computed(() => activeDocument.value?.editor.dirty.value ?? false)
const conflict = computed(() => activeDocument.value?.editor.state.conflict ?? false)
/** 未打开任何标签时的占位草稿：受控输入仍可绑定，但不可保存 */
const emptyDraft = reactive<WikiDraftFields>({
  title: '',
  body: '',
  pageType: 'concept',
  related: [],
  tags: [],
  sources: [],
  summary: '',
})
/** 当前激活文档的草稿字段；写回直接落在该文档的草稿里，标签标题与正文共用同一份数据 */
const editorFields = computed<WikiDraftFields>(() => activeDocument.value?.editor.state.fields ?? emptyDraft)

const railItems: YdWorkspaceRailItem[] = [
  { value: 'files', label: '目录', icon: 'morph:list-tree' },
  { value: 'outline', label: '大纲', icon: 'morph:list' },
]

const tabs = computed<YdWorkspaceTab[]>(() => documents.documents.value.map(document => ({
  key: document.key,
  label: document.editor.state.fields.title || document.node.title || '未命名',
  icon: document.node.nodeType === 'DIRECTORY' ? 'morph:folder' : 'morph:file-text',
  dirty: document.editor.dirty.value,
  title: document.node.path || document.node.title,
})))

const tabsModel = computed({
  get: () => documents.activeKey.value,
  set: (key: string) => {
    documents.activeKey.value = key
  },
})

const bodyCharCount = computed(() => editorFields.value.body.replace(/\s/g, '').length)

/**
 * 顶部保存态与主操作：
 * 主按钮永远是当前可执行的那个——页面有改动时发布按钮就是「保存并发布」（publish 内部先保存再发布），
 * 保存只作为次要动作保留；目录没有发布动作，保存即主操作。
 */
const statusText = computed(() => conflict.value ? '服务端已更新' : saving.value ? '保存中…' : dirty.value ? '未保存' : '已保存')
const statusIcon = computed(() => conflict.value ? 'morph:triangle-alert' : dirty.value ? 'morph:circle-dot' : 'morph:circle-check')
const statusToneClass = computed(() => conflict.value
  ? 'wiki-author__status--conflict'
  : dirty.value ? 'wiki-author__status--dirty' : 'wiki-author__status--saved')
const saveButtonVariant = computed<'default' | 'outline'>(() => isPage.value ? 'outline' : 'default')
const publishLabel = computed(() => dirty.value
  ? '保存并发布'
  : node.value?.publishedVersionId ? '重新发布' : '发布')

const statusItems = computed<YdWorkspaceStatusItem[]>(() => {
  const current = node.value
  if (!current) {
    return [{ text: '未打开页面', icon: 'morph:file-text', tone: 'muted' }]
  }
  const items: YdWorkspaceStatusItem[] = [
    { text: current.path || current.slug || '-', icon: 'morph:file-text', title: '页面路径' },
    { text: isPage.value ? pageTypeLabel(editorFields.value.pageType) : '目录', tone: 'muted' },
    { text: `${bodyCharCount.value} 字`, tone: 'muted' },
    { text: current.publishedVersionId ? '已发布' : '未发布', tone: current.publishedVersionId ? 'success' : 'muted' },
  ]
  if (conflict.value) {
    items.push({ text: '服务端已更新，需重新载入', icon: 'morph:triangle-alert', tone: 'danger', align: 'end' })
  }
  else if (dirty.value) {
    items.push({ text: saving.value ? '保存中…' : '未保存', icon: 'morph:circle-dot', tone: 'warning', align: 'end' })
  }
  else {
    items.push({ text: '已保存', icon: 'morph:circle-check', tone: 'success', align: 'end' })
  }
  items.push({ text: `${documents.documents.value.length} 个标签`, tone: 'muted', align: 'end', title: '已打开页面' })
  return items
})

const viewModeOptions: YdSegmentedOption<'edit' | 'preview'>[] = [
  { label: '编辑', value: 'edit' },
  { label: '预览', value: 'preview' },
]
const asideTabs = computed(() => {
  const tabs = [
    { label: '属性', value: 'meta', icon: 'morph:sliders-horizontal' },
    { label: '操作', value: 'actions', icon: 'morph:wrench' },
  ]
  // 评论线程挂在属性栏第三个标签：只对页面出现，沿用现有面板宽度，不再新增常驻栏
  if (isPage.value) {
    tabs.push({ label: '评论', value: 'comments', icon: 'morph:message-circle' })
  }
  return tabs
})

const publicPreviewUrl = computed(() => {
  const current = node.value
  const currentSpace = space.value
  if (!current || !currentSpace?.publicReadEnabled || !current.publishedVersionId) {
    return ''
  }
  return `/wiki/${encodeURIComponent(currentSpace.slug)}/${encodeURI(current.path || current.slug)}`
})
/** 内联图片可见性提示：图片一律私有，公开页展示依赖待接入的发布态代理 */
const imageVisibilityHint = computed(() => wikiImageUploadHint())

const publicPreviewHint = computed(() => {
  const currentSpace = space.value
  if (!currentSpace?.publicReadEnabled) {
    return '知识库未开启公开阅读，暂无公开页'
  }
  if (!node.value?.publishedVersionId) {
    return '当前页面尚未发布，暂无公开页'
  }
  return '预览公开页'
})

const pageOptions = computed(() => store.flatTree.value
  .filter(item => item.nodeType === 'PAGE' && item.id !== node.value?.id)
  .map(item => ({ label: item.path ? `${item.title} · ${item.path}` : item.title, value: item.id })))

const filteredTree = computed(() => {
  const keyword = treeKeyword.value.trim().toLocaleLowerCase()
  const byId = new Map(store.flatTree.value.map(item => [item.id, item]))
  const matches = new Set(store.flatTree.value.filter((item) => {
    if (!keyword) {
      return true
    }
    return `${item.title} ${item.path || ''}`.toLocaleLowerCase().includes(keyword)
  }).map(item => item.id))

  // 搜索保留命中节点的全部祖先，目录链路才能完整展开
  if (keyword) {
    for (const id of [...matches]) {
      let current = byId.get(id)
      while (current?.parentId) {
        matches.add(current.parentId)
        current = byId.get(current.parentId)
      }
    }
  }

  const treeNodes = new Map<string, WikiTreeNode>()
  const roots: WikiTreeNode[] = []
  for (const item of store.flatTree.value) {
    if (!matches.has(item.id)) {
      continue
    }
    const isDirectory = item.nodeType === 'DIRECTORY'
    const pageType = item.pageType ?? ''
    const treeNode: WikiTreeNode = {
      key: item.id,
      label: item.title,
      children: [],
      nodeType: item.nodeType,
      isDirectory,
      icon: isDirectory ? 'morph:folder' : 'morph:file-text',
      expandedIcon: 'morph:folder-open',
      published: !isDirectory && Boolean(item.publishedVersionId),
      typeLabel: !isDirectory && pageType ? pageTypeLabel(pageType) : '',
      typeDot: !isDirectory && pageType ? pageTypeDot(pageType) : undefined,
    }
    treeNodes.set(item.id, treeNode)
    const parent = item.parentId ? treeNodes.get(item.parentId) : undefined
    if (parent?.children) {
      parent.children.push(treeNode)
    }
    else {
      roots.push(treeNode)
    }
  }
  return roots
})

function treeNodeView(treeNode: YdTreeNode | undefined) {
  return treeNode as WikiTreeNode | undefined
}

function treeNodeIcon(treeNode: YdTreeNode | undefined, expanded: boolean) {
  const view = treeNodeView(treeNode)
  if (!view?.isDirectory) {
    return view?.icon ?? 'morph:file-text'
  }
  return expanded ? view.expandedIcon : view.icon
}

function nodeFromTree(treeNode: YdTreeNode | undefined): WikiNode | undefined {
  return treeNode ? store.flatTree.value.find(item => item.id === String(treeNode.key)) : undefined
}

/** 目录点选：打开或激活对应标签，不再拦截未保存内容（多标签下切换本就无损） */
function onTreeSelection(treeNode: YdTreeNode | null) {
  const item = nodeFromTree(treeNode ?? undefined)
  if (!item) {
    // 再次点击当前节点会取消树选中：编辑器保持原页面，只把选中态同步回来
    selectedTreeKeys.value = node.value ? [node.value.id] : []
    return
  }
  openDocument(item)
}

function openDocument(item: WikiNode) {
  documents.open(store.spaceId.value, item)
  if (isMobile.value) {
    pane.value = 'main'
  }
}

watch(treeKeyword, () => {
  if (!treeKeyword.value.trim()) {
    return
  }
  // 搜索时展开全部目录，命中的页面及其祖先链路才能显示
  expandedTreeKeys.value = store.flatTree.value
    .filter(item => item.nodeType === 'DIRECTORY')
    .map(item => item.id)
})

const previewMarkdown = computed(() => rewriteApiFileUrls(wikilinksToMarkdown(editorFields.value.body)))
// 工具栏按用途分组：文本样式 | 结构 | 插入 | 历史操作，再换行放视图切换（'-' 与 '=' 是组件库的分组分隔符）
const wikiToolbars: YdMarkdownToolbar[] = [
  'bold',
  'italic',
  '-',
  'title',
  'quote',
  'unorderedList',
  'orderedList',
  'task',
  '-',
  'codeRow',
  'code',
  'link',
  'image',
  'table',
  'mermaid',
  '-',
  'revoke',
  'next',
  '=',
  'preview',
  'previewOnly',
  'catalog',
]

// 页面正文标题层级（活动栏「大纲」视图：点击跳转到对应标题，当前项高亮）
const outline = computed(() => {
  if (!isPage.value) {
    return []
  }
  const lines = editorFields.value.body.split('\n')
  const items: { level: number, text: string }[] = []
  for (const line of lines) {
    const match = /^(#{1,4})\s/.exec(line)
    if (!match) {
      continue
    }
    const text = line.slice(match[0].length).trim().replace(/[#*`]/g, '').trim()
    if (text) {
      items.push({ level: match[1].length, text })
    }
  }
  return items.slice(0, 60)
})

/** 大纲当前项：只在点击后标记，避免与编辑器自带目录的滚动高亮互相打架 */
const outlineActive = ref(-1)
/** 编辑面容器：编辑态滚动定位在 CodeMirror 行上找（预览态用 previewHostRef） */
const editorHostRef = ref<HTMLElement | null>(null)

function openLinkPicker() {
  linkedPageId.value = ''
  linkVisible.value = true
}

function openPublicPreview() {
  if (!publicPreviewUrl.value) {
    return
  }
  window.open(publicPreviewUrl.value, '_blank', 'noopener')
}

function insertWikiLink() {
  const target = store.flatTree.value.find(item => item.id === linkedPageId.value && item.nodeType === 'PAGE')
  if (!target) {
    toast.warning('请选择知识库页面')
    return
  }
  editorRef.value?.insert(selectedText => ({
    targetValue: `[[${target.title}${selectedText ? `|${selectedText}` : ''}]]`,
    select: false,
  }))
  linkVisible.value = false
  editorRef.value?.focus()
}

// —— 离开与关闭：只有真正会丢草稿的动作才确认 ——

async function closeDocument(key: string) {
  const document = documents.documents.value.find(item => item.key === key)
  if (!document) {
    return
  }
  if (!document.editor.dirty.value && !document.editor.state.conflict) {
    documents.close(key)
    return
  }
  modal.confirm({
    title: '关闭未保存的标签？',
    content: `「${document.editor.state.fields.title || document.node.title}」的修改尚未保存，关闭将丢失这些修改。`,
    confirmButtonText: '关闭并放弃',
    onConfirm: () => documents.close(key),
  })
}

async function confirmLeaveWorkspace(): Promise<boolean> {
  const pending = documents.documents.value.filter(item => item.editor.dirty.value || item.editor.state.conflict)
  if (!pending.length) {
    return true
  }
  return new Promise((resolve) => {
    modal.confirm({
      title: '离开未保存的页面？',
      content: `${pending.length} 个标签有未保存的修改，继续将丢失这些修改。`,
      confirmButtonText: '放弃修改',
      onConfirm: () => {
        documents.reset()
        resolve(true)
      },
      onCancel: () => resolve(false),
    })
  })
}

const unregisterGuard = store.registerEditorGuard({
  confirmLeave: confirmLeaveWorkspace,
  dirty: () => documents.anyDirty.value || documents.anyConflict.value,
})
onBeforeUnmount(unregisterGuard)

// 激活标签 → 工作台选中态（地址栏、评论与内链面板都跟随 store.selectedNode）
watch(() => documents.activeKey.value, () => {
  const current = activeDocument.value
  selectedTreeKeys.value = current ? [current.nodeId] : []
  if (current && store.selectedNode.value?.id !== current.nodeId) {
    void store.selectNode(current.node)
  }
}, { immediate: true })

// 其它面板（评论、内链、入口页）选中页面 → 打开或激活标签
watch(() => store.selectedNode.value?.id, (id) => {
  if (!id || activeDocument.value?.nodeId === id) {
    return
  }
  const item = store.selectedNode.value
  if (item) {
    documents.open(store.spaceId.value, item)
  }
})

// 目录刷新拿回新快照：同步元数据并让草稿按冲突语义处理，不静默覆盖未保存内容
watch(() => store.tree.value, () => documents.syncNodes(store.spaceId.value, store.tree.value))
// 切换知识库：只保留目标知识库的标签
watch(() => store.spaceId.value, id => documents.keepSpace(id))

// 进入编辑区默认展开属性栏（宽屏）；窄屏交给外壳的面板切换器
watch([isWide, isMobile], ([wide, mobile]) => {
  if (mobile) {
    return
  }
  if (wide) {
    asideOpen.value = true
  }
}, { immediate: true })

async function saveNode(): Promise<boolean> {
  const document = activeDocument.value
  if (!document || !document.spaceId || saving.value || document.editor.state.conflict) {
    return false
  }
  saving.value = true
  const submitted = document.editor.ticket()
  const fields = JSON.parse(submitted.snapshot) as WikiDraftFields
  try {
    // 目录节点只提交标题等目录信息；页面正文与页面元数据仅对 PAGE 提交
    const payload: Partial<WikiNode> = {
      id: document.nodeId,
      title: fields.title,
      slug: document.node.slug,
      nodeType: document.node.nodeType,
      sort: document.node.sort,
    }
    if (document.node.nodeType === 'PAGE') {
      payload.body = fields.body
      payload.pageType = fields.pageType
      payload.sources = [...fields.sources]
      payload.related = [...fields.related]
      payload.tags = [...fields.tags]
      payload.summary = fields.summary
    }
    await saveWikiNode(document.spaceId, payload)
    document.editor.acknowledge(submitted)
    toast.success('已保存')
    await store.reloadTree()
    return true
  }
  finally {
    saving.value = false
  }
}

async function publish() {
  const document = activeDocument.value
  if (!document || saving.value || publishing.value || document.editor.state.conflict) {
    return
  }
  publishing.value = true
  try {
    if (document.editor.dirty.value && (!await saveNode() || document.editor.dirty.value)) {
      return
    }
    await publishWikiNode(document.nodeId)
    toast.success('已发布')
    await store.reloadTree()
  }
  finally {
    publishing.value = false
  }
}

async function unpublish() {
  const document = activeDocument.value
  if (!document) {
    return
  }
  await unpublishWikiNode(document.nodeId)
  toast.success('已取消发布')
  await store.reloadTree()
}

// 远端版本变化后重新载入：丢弃当前标签的本地草稿并取回远端快照
function reloadRemote() {
  const document = activeDocument.value
  if (!document || !document.editor.state.conflict) {
    return
  }
  modal.confirm({
    title: '重新载入远端版本？',
    content: '将放弃当前标签未保存的修改，用服务端最新内容覆盖编辑器。',
    confirmButtonText: '重新载入',
    onConfirm: () => {
      document.editor.discard()
      toast.success('已重新载入远端版本')
    },
  })
}

// 删除页面 / 目录（目录需先清空子节点，后端会拦截）
const deletingNodeId = ref('')

function removeNode(item: WikiNode) {
  const isDir = item.nodeType === 'DIRECTORY'
  modal.confirm({
    title: `删除${isDir ? '目录' : '页面'}`,
    content: `确定删除「${item.title}」吗？${isDir ? '目录内存在子节点时无法删除。' : '删除后不可恢复。'}`,
    onConfirm: async () => {
      const opened = documents.findByNode(item.id)
      if (opened && (opened.editor.dirty.value || opened.editor.state.conflict) && !await confirmLeaveWorkspace()) {
        return
      }
      deletingNodeId.value = item.id
      try {
        await deleteWikiNode(item.id)
        documents.closeByNode(item.id)
        toast.success('已删除')
        await store.reloadTree()
      }
      finally {
        deletingNodeId.value = ''
      }
    },
  })
}

function removeTreeItem(treeNode: YdTreeNode | undefined) {
  const item = nodeFromTree(treeNode)
  if (item) {
    removeNode(item)
  }
}

// 新建页面 / 目录
const createVisible = ref(false)
const creating = ref(false)
const createForm = ref({ title: '', slug: '', nodeType: 'PAGE' as 'PAGE' | 'DIRECTORY' })

function openCreate(nodeType: 'PAGE' | 'DIRECTORY') {
  createForm.value = { title: '', slug: '', nodeType }
  createVisible.value = true
}

async function createNode() {
  if (!store.spaceId.value || !createForm.value.title.trim()) {
    toast.warning('请填写标题')
    return
  }
  creating.value = true
  try {
    const parent = node.value?.nodeType === 'DIRECTORY' ? node.value : null
    await saveWikiNode(store.spaceId.value, {
      title: createForm.value.title.trim(),
      slug: createForm.value.slug.trim() || createForm.value.title.trim().toLowerCase().replace(/\s+/g, '-'),
      nodeType: createForm.value.nodeType,
      parentId: parent?.id,
      sort: 0,
      body: '',
      pageType: 'concept',
    })
    toast.success('已创建')
    createVisible.value = false
    await store.reloadTree()
  }
  finally {
    creating.value = false
  }
}

async function onPreviewLinkClick({ event }: { event: MouseEvent }) {
  const title = resolveWikiLink(event)
  if (title) {
    await store.openPage({ title })
  }
}

function removeSourceChip(source: string) {
  if (!node.value) {
    return
  }
  modal.confirm({
    title: '移除资料来源关联',
    content: `仅从页面元数据中移除「${source}」的关联标记，不会删除资料本身。`,
    onConfirm: () => {
      editorFields.value.sources = editorFields.value.sources.filter(item => item !== source)
    },
  })
}

// —— 预览态选中文字评论 ——
// 选区只从作者预览渲染出的 Markdown 取（.md-editor-preview），不使用 CodeMirror 源码偏移；
// 锚点草稿只随创建请求提交，评论标记不写进正文，也不影响保存/发布。
// 持久锚点评论只对「已发布且预览与已保存内容一致」的页面开放：草稿里的新文字绝不当成已发布版本提交。

/** 预览正文块选择器：只取最内层文本块（li/td 内含 p 时以 p 为准），保证捕获与定位用同一套块模型 */
const PREVIEW_BLOCK_SELECTOR = 'p, h1, h2, h3, h4, h5, h6, li, td, th, pre'
const PREVIEW_CROSS_BLOCK_HINT = '首版只支持同一段落、标题、列表项或表格单元格内的选区'

interface PreviewBlockEntry {
  element: HTMLElement
  block: WikiAnchorBlock
}

const previewHostRef = ref<HTMLElement | null>(null)
const previewRevision = ref(0)
const previewQuote = ref<{ anchor: WikiAuthorCommentAnchor, top: number, left: number } | null>(null)
const previewQuoteHint = ref<{ text: string, top: number, left: number } | null>(null)

function escapeHeadingText(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/** 在编辑面或预览面定位同名标题元素：编辑态匹配 Markdown 源行，预览态匹配渲染后的标题 */
function locateHeading(text: string): HTMLElement | null {
  const lines = editorHostRef.value?.querySelectorAll<HTMLElement>('.cm-line')
  if (lines?.length) {
    const pattern = new RegExp(`^#{1,4}\\s+${escapeHeadingText(text)}\\s*$`)
    const hit = [...lines].find(line => pattern.test((line.textContent || '').trim()))
    if (hit) {
      return hit
    }
  }
  const previewHeadings = previewHostRef.value?.querySelectorAll<HTMLElement>('h1, h2, h3, h4')
  return [...(previewHeadings || [])].find(node => (node.textContent || '').trim() === text) || null
}

async function jumpToHeading(index: number) {
  const item = outline.value[index]
  if (!item) {
    return
  }
  outlineActive.value = index
  await nextTick()
  locateHeading(item.text)?.scrollIntoView({ block: 'start', behavior: 'smooth' })
}

const accountStore = useAppAccountStore()
const isLogin = computed(() => accountStore.isLogin)

/** 空间评论能力：公开阅读 + 评论开关都由服务端空间配置下发（共享 WikiSpace 类型未声明，运行时字段在） */
const spaceCommentEnabled = computed(() => {
  const current = space.value as (WikiSpace & WikiSpaceCoverFields) | null
  return current?.publicReadEnabled === true && current.commentEnabled === true
})

/** 评论入口（顶栏按钮 / 属性栏标签）只对开启评论的页面出现，避免给未开放能力做假入口 */
const commentEntryVisible = computed(() => isPage.value && spaceCommentEnabled.value)

const commentPanelEnabled = computed(() => spaceCommentEnabled.value && Boolean(node.value?.publishedVersionId))

const commentDisabledHint = computed(() => {
  if (!spaceCommentEnabled.value) {
    return '知识库未开启公开评论：评论接口不可用，界面不提供假入口。'
  }
  return '当前页面尚未发布：评论只针对已发布页面，发布后可查看与发表评论。'
})

/** 预览正文与当前已保存内容是否一致：body 才是锚点匹配依据，其它元数据字段变化不影响引用 */
const previewMatchesSaved = computed(() => {
  const current = activeDocument.value
  return current ? editorFields.value.body === wikiDraftFields(current.node).body : false
})

/** 预览与已保存/已发布内容不一致（草稿态）：评论可读，但锚点定位仅供参考 */
const previewDiffers = computed(() => !previewMatchesSaved.value || conflict.value)

/** 发表锚点评论的闸门：任一不满足都只说明原因，不提交、不静默退化为整页评论 */
const commentCreateBlockedReason = computed(() => {
  const current = node.value
  if (!current || !isPage.value) {
    return '目录节点没有正文，不能发表锚点评论。'
  }
  if (!spaceCommentEnabled.value) {
    return '知识库未开放公开评论，暂不能发表评论。'
  }
  if (!current.publishedVersionId) {
    return '当前页面尚未发布：锚点评论只针对已发布版本，请先发布当前内容。'
  }
  if (conflict.value) {
    return '服务端内容已更新，本地修改尚未保存：请先重新载入远端版本，再对已发布正文选择文字评论。'
  }
  if (!previewMatchesSaved.value) {
    return '预览内容与已保存/已发布内容不一致：请先保存并发布，再对已发布正文选择文字评论。'
  }
  if (!isLogin.value) {
    return '登录后才能发表评论；匿名评论未开放。'
  }
  return ''
})
const canCreateComment = computed(() => commentCreateBlockedReason.value === '')

/** 预览渲染容器：优先正文元素 `.md-editor-preview`，退回外层包装，保证选择只发生在渲染出的 Markdown 上 */
function previewRoot(): HTMLElement | null {
  const host = previewHostRef.value
  if (!host) {
    return null
  }
  return host.querySelector<HTMLElement>('.md-editor-preview') ?? host.querySelector<HTMLElement>('.md-editor-preview-wrapper')
}

function previewBlockOf(element: HTMLElement, ordinal: number): WikiAnchorBlock {
  const cell = element.closest<HTMLTableCellElement>('td, th')
  const listItem = element.closest('li')
  let type: WikiAnchorBlockType = 'paragraph'
  if (cell) {
    type = 'tableCell'
  }
  else if (listItem) {
    type = 'listItem'
  }
  else if (element.closest('blockquote')) {
    type = 'blockquote'
  }
  else if (element.tagName === 'PRE') {
    type = 'codeBlock'
  }
  else if (/^H[1-6]$/.test(element.tagName)) {
    type = 'heading'
  }
  const block: WikiAnchorBlock = { ordinal, type, text: element.textContent ?? '' }
  if (cell) {
    const row = (cell.parentElement as HTMLTableRowElement | null)?.rowIndex
    const column = cell.cellIndex
    if (typeof row === 'number' && row >= 0 && column >= 0) {
      block.tableRow = row
      block.tableCell = column
    }
  }
  return block
}

/** 预览正文块（含原始 DOM 引用）：遍历一次生成的序号即捕获与定位共用的块序号 */
function previewBlockEntries(): PreviewBlockEntry[] {
  const root = previewRoot()
  if (!root) {
    return []
  }
  return Array.from(root.querySelectorAll<HTMLElement>(PREVIEW_BLOCK_SELECTOR))
    .filter(element => !element.querySelector(PREVIEW_BLOCK_SELECTOR))
    .map((element, ordinal) => ({ element, block: previewBlockOf(element, ordinal) }))
}

const previewBlocks = computed(() => {
  void previewRevision.value
  return previewBlockEntries()
})

/** 传给评论面板的块数据：窄屏切到属性面板后预览 DOM 已卸载，保留最后一次采集结果做只读定位 */
const commentBlocks = ref<WikiAnchorBlock[]>([])
watch(previewBlocks, (entries) => {
  if (entries.length) {
    commentBlocks.value = entries.map(entry => entry.block)
  }
}, { immediate: true })

function previewBlockEntryFor(node: Node): PreviewBlockEntry | null {
  return previewBlocks.value.find(entry => entry.element === node || entry.element.contains(node)) ?? null
}

/** 块内原始文本偏移：块内没有嵌套正文块，Range.toString() 长度即 textContent 前缀长度 */
function textOffsetWithin(block: HTMLElement, node: Node, offset: number): number {
  const range = document.createRange()
  try {
    range.setStart(block, 0)
    range.setEnd(node, offset)
  }
  catch {
    return 0
  }
  return range.toString().length
}

function previewQuotePosition(rect: DOMRect): { top: number, left: number } {
  const host = previewHostRef.value?.getBoundingClientRect()
  if (!host) {
    return { top: 0, left: 0 }
  }
  const above = rect.top - host.top - 34
  return {
    top: above >= 4 ? above : rect.bottom - host.top + 6,
    left: Math.max(Math.min(rect.right - host.left - 72, Math.max(host.width - 96, 4)), 4),
  }
}

function clearPreviewQuote() {
  previewQuote.value = null
  previewQuoteHint.value = null
}

/** 新一次手势开始：先丢弃上一次冻结的选区，避免旧引用跟着新选择一起提交 */
function onPreviewGestureStart() {
  clearPreviewQuote()
}

/** 选区结束（指针或键盘）：冻结快照，之后浏览器选区被折叠也不影响提交 */
function onPreviewSelectionGesture() {
  const root = previewRoot()
  const selection = window.getSelection()
  if (!commentEntryVisible.value || !root || !selection || selection.isCollapsed || selection.rangeCount === 0) {
    clearPreviewQuote()
    return
  }
  const range = selection.getRangeAt(0)
  if (!root.contains(range.startContainer) || !root.contains(range.endContainer)) {
    clearPreviewQuote()
    return
  }
  const startEntry = previewBlockEntryFor(range.startContainer)
  const endEntry = previewBlockEntryFor(range.endContainer)
  const position = previewQuotePosition(range.getBoundingClientRect())
  if (!startEntry || !endEntry || startEntry.element !== endEntry.element) {
    // 跨块或落在非正文块的选区：明确提示，不截断也不按块边界猜测
    previewQuote.value = null
    previewQuoteHint.value = startEntry && endEntry ? { text: PREVIEW_CROSS_BLOCK_HINT, ...position } : null
    return
  }
  const block = startEntry.block
  const start = wikiNormalizedOffsetAt(block.text, textOffsetWithin(startEntry.element, range.startContainer, range.startOffset))
  const end = wikiNormalizedOffsetAt(block.text, textOffsetWithin(startEntry.element, range.endContainer, range.endOffset))
  const anchor = buildWikiQuoteAnchor({
    blockText: block.text,
    start,
    end,
    blockOrdinal: block.ordinal,
    blockType: block.type,
    tableRow: block.tableRow,
    tableCell: block.tableCell,
  })
  const payload = toWikiAuthorCommentAnchor(anchor, block.text)
  if (!payload) {
    previewQuote.value = null
    previewQuoteHint.value = { text: `请至少选择 ${WIKI_QUOTE_ANCHOR_LIMITS.minExact} 个可见字符`, ...position }
    return
  }
  previewQuote.value = { anchor: payload, ...position }
}

/** 打开评论线程：桌面展开属性栏，窄屏切到属性面板，统一落在评论标签 */
function openCommentsPanel() {
  asideTab.value = 'comments'
  if (isMobile.value) {
    pane.value = 'aside'
  }
  else {
    asideOpen.value = true
  }
}

/** 选区入口：先把冻结的选区引用带进评论面板，再打开线程 */
function openCommentComposer() {
  if (!previewQuote.value) {
    return
  }
  openCommentsPanel()
}

/** 定位评论引用：只滚动/临时高亮预览 DOM，不改正文，也不写进 Markdown */
async function locateCommentAnchor(match: WikiQuoteAnchorMatch) {
  if (isMobile.value && pane.value !== 'main') {
    pane.value = 'main'
    await nextTick()
  }
  const entry = previewBlocks.value.find(item => item.block.ordinal === match.blockOrdinal)
  if (!entry) {
    return
  }
  entry.element.scrollIntoView({ block: 'center', behavior: 'smooth' })
  entry.element.classList.add('author-anchor-flash')
  window.setTimeout(() => entry.element.classList.remove('author-anchor-flash'), 1600)
}

// 预览 DOM 异步重渲染（图片解析、草稿更新）后重新采集正文块，避免沿用过期块序号
let previewObserver: MutationObserver | undefined
let previewRefreshQueued = false

function schedulePreviewRefresh() {
  if (previewRefreshQueued) {
    return
  }
  previewRefreshQueued = true
  requestAnimationFrame(() => {
    previewRefreshQueued = false
    previewRevision.value++
  })
}

watch(previewHostRef, (host) => {
  previewObserver?.disconnect()
  previewObserver = undefined
  if (!host) {
    return
  }
  previewObserver = new MutationObserver(schedulePreviewRefresh)
  previewObserver.observe(host, { childList: true, subtree: true, characterData: true })
  schedulePreviewRefresh()
}, { flush: 'post' })

// 切换标签、视图与正文变化后旧选区不再指向当前预览
watch([() => documents.activeKey.value, viewMode, isMobile, () => editorFields.value.body], () => clearPreviewQuote())

// 切到目录节点时评论标签不存在，回到属性标签避免空白内容区
watch(isPage, (value) => {
  if (!value && asideTab.value === 'comments') {
    asideTab.value = 'meta'
  }
})

onBeforeUnmount(() => {
  previewObserver?.disconnect()
  previewObserver = undefined
})
</script>

<template>
  <YdWorkspaceLayout
    v-model:rail="rail"
    v-model:sidebar-open="sidebarOpen"
    v-model:aside-open="asideOpen"
    v-model:pane="pane"
    class="wiki-author"
    storage-key="wiki-author"
    :rail-items="railItems"
    :sidebar-label="rail === 'outline' ? '大纲' : '目录'"
    main-label="编辑"
    aside-label="属性"
    :status-items="statusItems"
    aria-label="知识库编辑器"
  >
    <!-- 顶部工具条：一行三段——上下文 / 标题与保存态 / 动作；状态紧跟标题，主按钮随当前可执行动作切换 -->
    <template #header>
      <div class="author-bar">
        <div class="author-bar__left">
          <YdTooltip text="返回知识库">
            <YdButton variant="ghost" size="icon-sm" aria-label="返回知识库" @click="store.openEntry()">
              <YdIcon name="morph:arrow-left" />
            </YdButton>
          </YdTooltip>
          <YdTooltip v-if="!isMobile" :text="sidebarOpen ? '收起目录栏' : '展开目录栏'">
            <YdButton
              variant="ghost"
              size="icon-sm"
              :aria-label="sidebarOpen ? '收起目录栏' : '展开目录栏'"
              :aria-pressed="sidebarOpen"
              @click="sidebarOpen = !sidebarOpen"
            >
              <YdIcon :name="sidebarOpen ? 'morph:panel-left-close' : 'morph:panel-left-open'" />
            </YdButton>
          </YdTooltip>
          <span class="author-bar__space" :title="space?.name || '未选择知识库'">{{ space?.name || '未选择' }}</span>
          <template v-if="node">
            <YdIcon name="morph:chevron-right" class="author-bar__sep" />
            <span class="author-bar__kind">{{ node.nodeType === 'DIRECTORY' ? '目录' : '页面' }}</span>
          </template>
        </div>

        <!-- 标题与保存状态同组：状态跟在页面名后面，不再飘到工具条另一端 -->
        <div class="author-bar__title">
          <YdInput
            v-model="editorFields.title"
            class="wiki-author__title-input"
            :disabled="!node"
            :placeholder="node?.nodeType === 'DIRECTORY' ? '目录名称' : '页面标题'"
            aria-label="页面标题"
          />
          <span :class="['wiki-author__status', statusToneClass]" role="status">
            <YdIcon :name="statusIcon" :class="{ 'wiki-author__status-icon--pulse': dirty && !conflict }" />
            <span class="wiki-author__status-text">{{ statusText }}</span>
          </span>
        </div>

        <div class="author-bar__right">
          <div v-if="isPage" class="author-bar__mode">
            <YdSegmented
              v-model="viewMode"
              size="sm"
              :options="viewModeOptions"
              aria-label="编辑或预览"
            >
              <template #option="{ option }">
                <YdIcon :name="option.value === 'edit' ? 'morph:pencil' : 'morph:eye'" />
              </template>
            </YdSegmented>
          </div>
          <YdButton
            :variant="saveButtonVariant"
            :loading="saving"
            :disabled="!node || !dirty || conflict || publishing"
            @click="saveNode"
          >
            <YdIcon name="morph:save" /> 保存
          </YdButton>
          <YdButton v-if="isPage" :loading="publishing" :disabled="!node || saving || conflict" @click="publish">
            <YdIcon name="morph:send" /> {{ publishLabel }}
          </YdButton>
          <YdTooltip :text="publicPreviewHint">
            <YdButton
              variant="ghost"
              size="icon-sm"
              aria-label="预览公开页"
              :disabled="!publicPreviewUrl"
              @click="openPublicPreview"
            >
              <YdIcon name="morph:external-link" />
            </YdButton>
          </YdTooltip>
          <YdTooltip v-if="commentEntryVisible" text="页面评论">
            <YdButton variant="ghost" size="icon-sm" aria-label="打开页面评论" @click="openCommentsPanel">
              <YdIcon name="morph:message-circle" />
            </YdButton>
          </YdTooltip>
          <YdTooltip text="进入治理区">
            <YdButton variant="ghost" size="icon-sm" aria-label="进入治理区" @click="store.openManage()">
              <YdIcon name="morph:settings-2" />
            </YdButton>
          </YdTooltip>
          <YdTooltip v-if="!isMobile" :text="asideOpen ? '收起属性栏' : '展开属性栏'">
            <YdButton
              variant="ghost"
              size="icon-sm"
              :aria-label="asideOpen ? '收起属性栏' : '展开属性栏'"
              :aria-pressed="asideOpen"
              @click="asideOpen = !asideOpen"
            >
              <YdIcon :name="asideOpen ? 'morph:panel-right-close' : 'morph:panel-right-open'" />
            </YdButton>
          </YdTooltip>
        </div>
      </div>
    </template>

    <template #sidebar>
      <!-- 目录栏：页面树（活动栏第一项） -->
      <div v-if="rail === 'files'" class="author-tree">
        <div class="author-tree__toolbar">
          <YdInput v-model="treeKeyword" class="author-tree__search" clearable placeholder="搜索页面…" aria-label="搜索知识库页面">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdTooltip text="新建页面">
            <YdButton size="icon-sm" variant="ghost" aria-label="新建页面" @click="openCreate('PAGE')">
              <YdIcon name="morph:file-plus" />
            </YdButton>
          </YdTooltip>
          <YdTooltip text="新建目录">
            <YdButton size="icon-sm" variant="ghost" aria-label="新建目录" @click="openCreate('DIRECTORY')">
              <YdIcon name="morph:folder-plus" />
            </YdButton>
          </YdTooltip>
          <YdTooltip text="刷新目录">
            <YdButton size="icon-sm" variant="ghost" :loading="store.loadingTree.value" aria-label="刷新目录" @click="store.reloadTree()">
              <YdIcon name="morph:refresh-cw" />
            </YdButton>
          </YdTooltip>
        </div>
        <YdScrollArea class="author-tree__list">
          <YdTree
            v-model:selected-keys="selectedTreeKeys"
            v-model:expanded-keys="expandedTreeKeys"
            :data="filteredTree"
            :default-expand-all="true"
            @selection-change="onTreeSelection"
          >
            <template #default="{ node: treeNode, expanded }">
              <span class="author-node">
                <span class="author-node__main">
                  <YdIcon
                    class="author-node__icon"
                    :class="{ 'author-node__icon--dir': treeNodeView(treeNode)?.isDirectory }"
                    :name="treeNodeIcon(treeNode, expanded)"
                  />
                  <span class="author-node__title" :title="treeNode?.label">{{ treeNode?.label }}</span>
                  <span v-if="treeNodeView(treeNode)?.typeLabel" class="author-node__type" :title="treeNodeView(treeNode)?.typeLabel">
                    <span class="author-node__type-dot" :style="{ background: treeNodeView(treeNode)?.typeDot }" />
                    {{ treeNodeView(treeNode)?.typeLabel }}
                  </span>
                  <span v-if="treeNodeView(treeNode)?.published" class="author-node__dot" title="已发布" aria-label="已发布" />
                </span>
                <YdButton
                  class="author-node__delete"
                  :class="{ 'author-node__delete--busy': deletingNodeId === treeNode?.key }"
                  variant="ghost"
                  size="icon-sm"
                  :title="`删除${treeNodeView(treeNode)?.isDirectory ? '目录' : '页面'}`"
                  :aria-label="`删除${treeNodeView(treeNode)?.isDirectory ? '目录' : '页面'}${treeNode?.label ? ` ${treeNode.label}` : ''}`"
                  @click.stop="removeTreeItem(treeNode)"
                >
                  <YdIcon name="morph:trash-2" />
                </YdButton>
              </span>
            </template>
            <template #empty>
              <div class="author-tree__empty">
                <YdIcon name="morph:folder-open" />
                <p>{{ treeKeyword ? '没有匹配的页面' : '暂无页面：到治理区「资料库」导入资料，或在这里新建页面' }}</p>
              </div>
            </template>
          </YdTree>
        </YdScrollArea>
      </div>

      <!-- 大纲：当前页面正文标题（活动栏第二项），点击跳转到对应标题 -->
      <div v-else class="author-outline-view">
        <div class="author-outline-view__head">
          <span class="author-outline-view__title">正文标题</span>
          <span class="author-outline-view__count">{{ outline.length }}</span>
        </div>
        <YdScrollArea class="author-outline-view__list">
          <div v-if="outline.length" class="author-outline">
            <YdButton
              v-for="(item, index) in outline"
              :key="`${index}-${item.text}`"
              variant="ghost"
              size="sm"
              class="author-outline__item"
              :class="{ 'author-outline__item--active': outlineActive === index }"
              :style="{ paddingLeft: `${8 + (item.level - 1) * 10}px` }"
              :title="item.text"
              @click="jumpToHeading(index)"
            >
              {{ item.text }}
            </YdButton>
          </div>
          <p v-else class="author-outline-view__empty">
            {{ node ? '当前正文还没有标题' : '打开页面后可查看标题大纲' }}
          </p>
        </YdScrollArea>
      </div>
    </template>

    <template #main>
      <div class="author-main">
        <YdWorkspaceTabs
          v-model="tabsModel"
          :tabs="tabs"
          addable
          aria-label="已打开的页面标签"
          @close="closeDocument"
          @add="openCreate('PAGE')"
        />

        <div v-if="!node" class="author-empty">
          <YdEmpty
            icon="morph:list-tree"
            title="选择一个页面开始编辑"
            description="目录中点击页面即可打开标签，可同时打开多个页面；页面内容支持 [[wikilink]] 语法，预览中可点击跳转。"
          />
        </div>

        <template v-else>
          <div v-if="conflict" class="author-conflict" role="alert">
            <YdIcon name="morph:triangle-alert" />
            <span>服务端内容已更新，本地修改尚未保存。核对后重新载入远端版本再继续编辑。</span>
            <YdButton variant="outline" size="sm" @click="reloadRemote">
              <YdIcon name="morph:rotate-cw" /> 重新载入
            </YdButton>
          </div>
          <div v-if="isPage && viewMode === 'edit'" class="author-body__editor">
            <!-- 编辑面也是「白卡」：卡片头部放路径与插入动作，正文编辑区留白交给编辑器自己 -->
            <section class="author-editor-card" aria-label="页面正文">
              <header class="author-editor-card__head">
                <span class="author-editor-card__path" :title="node.path || node.slug">{{ node.path || node.slug }}</span>
                <YdButton variant="ghost" size="sm" :disabled="!pageOptions.length" @click="openLinkPicker">
                  <YdIcon name="morph:link" /> 插入知识库页面
                </YdButton>
              </header>
              <div ref="editorHostRef" class="author-body__md">
                <WikiMarkdownEditor
                  ref="editorRef"
                  :key="node.id"
                  v-model="editorFields.body"
                  height="100%"
                  show-toolbar-name
                  :catalog-max-depth="4"
                  :toolbars="wikiToolbars"
                  @save="saveNode"
                />
              </div>
            </section>
          </div>
          <YdScrollArea
            v-else-if="isPage"
            class="author-body__preview"
            @mousedown="onPreviewGestureStart"
            @mouseup="onPreviewSelectionGesture"
            @keyup="onPreviewSelectionGesture"
          >
            <div ref="previewHostRef" class="author-markdown">
              <WikiMarkdownEditor
                :model-value="previewMarkdown"
                readonly
                aria-label="Wiki 页面预览"
                height="auto"
                @link-click="onPreviewLinkClick"
              />
              <YdButton
                v-if="previewQuote"
                class="author-quote-entry"
                size="sm"
                :style="{ top: `${previewQuote.top}px`, left: `${previewQuote.left}px` }"
                @mousedown.stop.prevent
                @click="openCommentComposer"
              >
                <YdIcon name="morph:quote" /> 评论选中文字
              </YdButton>
              <span
                v-else-if="previewQuoteHint"
                class="author-quote-hint"
                role="status"
                :style="{ top: `${previewQuoteHint.top}px`, left: `${previewQuoteHint.left}px` }"
              >
                {{ previewQuoteHint.text }}
              </span>
            </div>
          </YdScrollArea>
          <div v-else class="author-note">
            <YdIcon name="morph:folder-open" />
            <p>目录节点不包含正文。用目录工具栏的「新页面 / 新目录」可在该目录下创建，标题可直接在顶部修改。</p>
          </div>
        </template>
      </div>
    </template>

    <template #aside>
      <YdTabs v-model="asideTab" :list="asideTabs" class="author-aside__tabs">
        <template #meta>
          <YdScrollArea class="author-aside__scroll">
            <div class="author-aside__inner">
              <div v-if="!node" class="author-aside__empty">
                <YdEmpty icon="morph:mouse-pointer-click" title="未打开页面" description="在目录中选择页面后可编辑属性。" />
              </div>
              <template v-else>
                <!-- 属性按语义分组折叠：基础信息 / 标签与关联 / 来源，扫读时先见分组标题 -->
                <section class="author-aside__group">
                  <YdCollapsible v-model="asideGroups.base">
                    <template #trigger="{ open }">
                      <span class="author-aside__group-head">
                        <span class="author-aside__group-title">基础信息</span>
                        <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" class="author-aside__group-chevron" />
                      </span>
                    </template>
                    <div class="author-aside__group-body">
                      <div class="author-aside__field">
                        <span class="author-aside__label">路径</span>
                        <span class="author-aside__value">{{ node.path || node.slug || '-' }}</span>
                      </div>
                      <div class="author-aside__field">
                        <span class="author-aside__label">节点类型</span>
                        <span class="author-aside__value">{{ node.nodeType === 'DIRECTORY' ? '目录' : '页面' }}</span>
                      </div>
                      <template v-if="isPage">
                        <label class="author-aside__field">
                          <span class="author-aside__label">页面类型</span>
                          <YdSelect v-model="editorFields.pageType" :options="pageTypeOptions" aria-label="页面类型" />
                        </label>
                        <label class="author-aside__field">
                          <span class="author-aside__label">摘要</span>
                          <YdInput v-model="editorFields.summary" placeholder="一句话摘要（展示在页面头部）" aria-label="页面摘要" />
                        </label>
                      </template>
                    </div>
                  </YdCollapsible>
                </section>

                <section v-if="isPage" class="author-aside__group">
                  <YdCollapsible v-model="asideGroups.relations">
                    <template #trigger="{ open }">
                      <span class="author-aside__group-head">
                        <span class="author-aside__group-title">标签与关联</span>
                        <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" class="author-aside__group-chevron" />
                      </span>
                    </template>
                    <div class="author-aside__group-body">
                      <label class="author-aside__field">
                        <span class="author-aside__label">标签</span>
                        <YdInputTag v-model="editorFields.tags" size="small" placeholder="回车添加标签" allow-clear aria-label="页面标签" />
                      </label>
                      <label class="author-aside__field">
                        <span class="author-aside__label">相关页面</span>
                        <YdInputTag v-model="editorFields.related" size="small" placeholder="wikilink 标题" allow-clear aria-label="相关页面" />
                      </label>
                    </div>
                  </YdCollapsible>
                </section>

                <section v-if="isPage" class="author-aside__group">
                  <YdCollapsible v-model="asideGroups.sources">
                    <template #trigger="{ open }">
                      <span class="author-aside__group-head">
                        <span class="author-aside__group-title">来源</span>
                        <span class="author-aside__group-count">{{ editorFields.sources.length }}</span>
                        <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" class="author-aside__group-chevron" />
                      </span>
                    </template>
                    <div class="author-aside__group-body">
                      <div v-if="editorFields.sources.length" class="author-aside__field">
                        <span class="author-aside__label">资料来源</span>
                        <span v-for="source in editorFields.sources" :key="source" class="author-source-chip">
                          {{ source }}
                          <YdButton variant="ghost" size="icon-sm" :aria-label="`移除资料来源 ${source}`" @click="removeSourceChip(source)">
                            <YdIcon name="morph:x" />
                          </YdButton>
                        </span>
                      </div>
                      <p v-else class="author-aside__hint">
                        还没有关联资料来源；来源标记由摄入与生成流程写入。
                      </p>
                      <div class="author-aside__field">
                        <span class="author-aside__label">内联图片</span>
                        <span class="author-aside__value">{{ imageVisibilityHint }}</span>
                      </div>
                    </div>
                  </YdCollapsible>
                </section>
              </template>
            </div>
          </YdScrollArea>
        </template>
        <template #actions>
          <YdScrollArea class="author-aside__scroll">
            <div class="author-aside__inner">
              <div v-if="!node" class="author-aside__empty">
                <YdEmpty icon="morph:mouse-pointer-click" title="未打开页面" description="在目录中选择页面后可执行操作。" />
              </div>
              <template v-else>
                <div class="author-aside__field">
                  <span class="author-aside__label">发布状态</span>
                  <span class="author-aside__value">
                    {{ node.publishedVersionId ? '已发布' : '未发布' }} · 索引 {{ node.indexStatus || '未知' }}
                  </span>
                </div>
                <div class="author-aside__actions">
                  <YdButton
                    v-if="isPage"
                    variant="outline"
                    size="sm"
                    :disabled="!node.publishedVersionId || saving || publishing"
                    @click="unpublish"
                  >
                    <YdIcon name="morph:undo-2" /> 取消发布
                  </YdButton>
                  <YdButton
                    variant="ghost"
                    size="sm"
                    :disabled="!publicPreviewUrl"
                    :title="publicPreviewHint"
                    @click="openPublicPreview"
                  >
                    <YdIcon name="morph:external-link" /> 预览公开页
                  </YdButton>
                  <YdButton variant="ghost" size="sm" @click="store.openManage('overview')">
                    <YdIcon name="morph:settings-2" /> 治理管理
                  </YdButton>
                </div>
                <div class="author-aside__danger">
                  <span class="author-aside__label">危险操作</span>
                  <p class="author-aside__hint">
                    {{ node.nodeType === 'DIRECTORY' ? '目录内存在子节点时无法删除。' : '删除后不可恢复，公开页会同时下线。' }}
                  </p>
                  <YdButton
                    variant="destructive"
                    size="sm"
                    :loading="deletingNodeId === node.id"
                    @click="removeNode(node)"
                  >
                    <YdIcon name="morph:trash-2" /> 删除{{ node.nodeType === 'DIRECTORY' ? '目录' : '页面' }}
                  </YdButton>
                </div>
              </template>
            </div>
          </YdScrollArea>
        </template>
        <template v-if="isPage" #comments>
          <WikiAuthorComments
            :space-slug="space?.slug || ''"
            :node-id="node?.id || ''"
            :enabled="commentPanelEnabled"
            :disabled-hint="commentDisabledHint"
            :can-create="canCreateComment"
            :create-disabled-reason="commentCreateBlockedReason"
            :pending-anchor="previewQuote?.anchor ?? null"
            :blocks="commentBlocks"
            :preview-differs="previewDiffers"
            @locate="locateCommentAnchor"
            @clear-anchor="clearPreviewQuote"
          />
        </template>
      </YdTabs>
    </template>
  </YdWorkspaceLayout>

  <!-- 移动端保存条：固定栏不遮挡正文（宿主已为其预留内边距） -->
  <YdFixedBar v-if="isMobile && node" position="bottom">
    <div class="author-savebar">
      <span class="author__state" :class="{ 'author__state--saved': !dirty && !conflict }" role="status">
        <YdIcon :name="conflict ? 'morph:triangle-alert' : dirty ? 'morph:circle-dot' : 'morph:circle-check'" />
        {{ conflict ? '服务端已更新，请重新载入' : saving ? '保存中…' : dirty ? '有未保存更改' : '已保存' }}
      </span>
      <YdButton v-if="conflict" variant="outline" size="sm" @click="reloadRemote">
        <YdIcon name="morph:rotate-cw" /> 重新载入
      </YdButton>
      <YdButton :variant="saveButtonVariant" size="sm" :loading="saving" :disabled="!dirty || conflict || publishing" @click="saveNode">
        <YdIcon name="morph:save" /> 保存
      </YdButton>
      <YdButton v-if="isPage" size="sm" :loading="publishing" :disabled="saving || conflict" @click="publish">
        <YdIcon name="morph:send" /> {{ publishLabel }}
      </YdButton>
    </div>
  </YdFixedBar>

  <YdModal
    v-model="linkVisible"
    title="插入知识库页面"
    show-cancel-button
    :confirm-button-disabled="!linkedPageId"
    @confirm="insertWikiLink"
  >
    <label class="author-field">
      <span>目标页面</span>
      <YdSelect v-model="linkedPageId" :options="pageOptions" allow-search placeholder="搜索标题或路径" />
    </label>
  </YdModal>
  <YdModal
    v-model="createVisible"
    :title="createForm.nodeType === 'PAGE' ? '新建页面' : '新建目录'"
    show-cancel-button
    :confirm-button-loading="creating"
    @confirm="createNode"
  >
    <div class="author-create-form">
      <label class="author-field">
        <span>标题</span>
        <YdInput v-model="createForm.title" placeholder="标题" />
      </label>
      <label class="author-field">
        <span>slug（留空自动生成）</span>
        <YdInput v-model="createForm.slug" placeholder="url-friendly-slug" />
      </label>
      <p v-if="node?.nodeType === 'DIRECTORY'" class="author-field-hint">
        将创建在当前目录「{{ node.title }}」下
      </p>
    </div>
  </YdModal>
</template>

<style scoped>
/* 工作区外壳（YdWorkspaceLayout）提供高度与背景，编辑区只填满自己的面板 */
.author {
  flex: 1;
  min-height: 0;
}

.author__state {
  display: inline-flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.author__state--saved {
  color: var(--color-text-3);
}

/* 主区：标签条固定、正文区域自己滚动 */
.author-main {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
}

.author-empty {
  display: grid;
  flex: 1;
  place-content: center;
  justify-items: center;
  min-height: 0;
  padding: 24px 16px;
}

.author-conflict {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding: 8px 16px;
  font-size: 12px;
  color: var(--color-text-1);
  background: var(--color-fill-2);
  border-bottom: 1px solid var(--color-border-2);
}

/* 正文容器不滚动：滚动权交给 MdEditor / YdScrollArea，避免双重滚动条与头部被裁 */
.author-body__editor {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  padding: 12px 16px 16px;
  overflow: hidden;
  background: var(--color-fill-1);
}

/* 编辑面与预览面同为「白卡」：卡片头放路径与插入动作，正文区由编辑器自己滚动 */
.author-editor-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.author-editor-card__head {
  display: flex;
  flex: 0 0 auto;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 5px 8px 5px 14px;
  border-bottom: 1px solid var(--color-border-2);
}

.author-editor-card__path {
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.author-body__md {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

/* 编辑面是 flex 列：编辑器外壳（含错误提示）与编辑区域一起撑满面板高度 */
.author-body__md :deep(.yd-markdown-editor-shell) {
  flex: 1;
  min-height: 0;
}

.author-body__md :deep(.yd-markdown-editor) {
  flex: 1;
  min-height: 0;
}

/* 编辑器跟随面板高度：YdMarkdownEditor 的 MdEditor 由内联 height 控制，这里统一压到容器高度 */
.author-body__md :deep(.md-editor) {
  height: 100% !important;
  min-height: 0 !important;
}

/* 编辑区分屏预览：浅灰地面 + 白色文档卡片，右侧明确是「渲染结果」而不是又一块编辑器 */
.author-body__md :deep(.md-editor-preview-wrapper) {
  background: var(--color-fill-1);
}

.author-body__md :deep(.md-editor-preview) {
  padding: 16px 24px 32px;
  margin: 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

/* 预览面：浅灰地面 + 白色文档卡片，明确「这里是渲染结果」，与编辑面区分 */
.author-body__preview {
  flex: 1;
  min-height: 0;
  background: var(--color-fill-1);
}

.author-markdown {
  position: relative;
  max-width: 880px;
  padding: 24px 32px 40px;
  margin: 16px auto 24px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.author-markdown :deep(.md-editor-preview-wrapper) {
  padding: 0;
}

.author-markdown :deep(a[href^='wiki://']) {
  color: var(--color-text-1);
  text-decoration: none;
  cursor: pointer;
  border-bottom: 1px dashed var(--color-border-3);
}

/* 选区评论入口：浮动在预览之上，不参与正文，也不进入复制文本 */
.author-quote-entry,
.author-quote-hint {
  position: absolute;
  z-index: 2;
}

.author-quote-entry {
  box-shadow: 0 2px 10px rgb(0 0 0 / 12%);
}

.author-quote-hint {
  max-width: 260px;
  padding: 4px 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-2);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 4px;
  box-shadow: 0 2px 10px rgb(0 0 0 / 8%);
}

/* 定位评论引用：只做临时阅读层高亮，不改 Markdown、不写入正文 */
.author-markdown :deep(.author-anchor-flash) {
  background: var(--color-fill-2);
  border-radius: 4px;
  box-shadow: 0 0 0 3px var(--color-fill-3);
}

.author-note {
  display: grid;
  flex: 1;
  gap: 8px;
  place-content: center;
  justify-items: center;
  padding: 24px 16px;
  font-size: 12px;
  color: var(--color-text-3);
  text-align: center;
}

.author-note p {
  max-width: 360px;
  margin: 0;
}

/* —— 目录栏 —— */
.author-tree {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.author-tree__toolbar {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
  padding: 10px 10px 6px;
}

.author-tree__search {
  flex: 1;
  min-width: 0;
}

.author-tree__list {
  flex: 1;
  min-height: 0;
  padding: 4px 8px 10px;
}

/* 树行沿用系统「菜单管理」树的规范：只把标题槽拉满、hover 用最浅填充，
   选中态交给组件库默认样式，不自绘内边距/圆角/主色底（自绘会与系统其余树不一致） */
.author-tree__list :deep(.arco-tree-node-title) {
  width: 100%;
}

.author-tree__list :deep(.arco-tree-node-title:hover) {
  background: var(--color-fill-1);
}

.author-node {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  min-height: 32px;
  font-size: 13px;
  line-height: 1.45;
  text-align: left;
}

/* 图标 + 标题 + 元信息同组：整组可收缩，标题省略号，徽标不参与压缩，行内不溢出 */
.author-node__main {
  display: flex;
  flex: 1;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.author-node__icon {
  flex: 0 0 18px;
  width: 18px;
  height: 18px;
  font-size: 15px;
  color: var(--color-text-3);
}

.author-node__icon--dir {
  color: var(--color-text-2);
}

.author-node__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 类型徽标：淡色小圆点 + 灰字标签，替代整块灰底文字，目录树不再杂乱 */
.author-node__type {
  display: inline-flex;
  flex: 0 0 auto;
  gap: 5px;
  align-items: center;
  font-size: 11px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.author-node__type-dot {
  flex-shrink: 0;
  width: 6px;
  height: 6px;
  border-radius: 50%;
}

.author-node__dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  background: var(--color-text-4);
  border-radius: 50%;
}

.author-node__delete {
  flex: 0 0 auto;
  color: var(--color-text-3);
  opacity: 0;
  transition: opacity var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.author-node:hover .author-node__delete,
.author-node__delete:focus-visible,
.author-node__delete--busy {
  opacity: 1;
}

.author-node__delete:hover {
  color: rgb(var(--danger-6));
}

.author-tree__empty {
  display: grid;
  gap: 8px;
  justify-items: center;
  padding: 32px 12px;
  font-size: 12px;
  color: var(--color-text-3);
  text-align: center;
}

.author-tree__empty :deep(svg) {
  font-size: 26px;
}

/* —— 大纲栏 —— */
.author-outline-view {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.author-outline-view__head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 6px;
  font-size: 12px;
  color: var(--color-text-3);
}

.author-outline-view__count {
  font-variant-numeric: tabular-nums;
}

.author-outline-view__list {
  flex: 1;
  min-height: 0;
  padding: 2px 8px 12px;
}

.author-outline-view__empty {
  margin: 0;
  padding: 0 4px;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 大纲条目：按层级缩进的可点行，当前项用最浅填充 + 左侧墨色指示条 */
.author-outline {
  display: grid;
  gap: 1px;
  width: 100%;
}

.author-outline__item.author-outline__item {
  display: flex;
  justify-content: flex-start;
  width: 100%;
  height: auto;
  min-height: 28px;
  padding: 4px 8px;
  overflow: hidden;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  color: var(--color-text-2);
  text-align: left;
  text-overflow: ellipsis;
  border-radius: 6px;
  white-space: nowrap;
}

.author-outline__item.author-outline__item:hover {
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

.author-outline__item.author-outline__item--active {
  font-weight: 600;
  color: var(--color-text-1);
  background: var(--color-fill-1);
  box-shadow: inset 2px 0 0 0 var(--color-text-1);
}

/* —— 属性栏 —— */
.author-aside__tabs {
  height: 100%;
  min-height: 0;
}

.author-aside__tabs :deep([data-slot="tabs-content"]) {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
}

.author-aside__tabs :deep([data-slot="tabs-list"]) {
  flex-shrink: 0;
}

.author-aside__scroll {
  flex: 1;
  min-height: 0;
}

.author-aside__inner {
  padding: 12px;
}

.author-aside__empty {
  padding: 24px 8px;
}

.author-aside__field {
  display: grid;
  gap: 5px;
  margin-bottom: 12px;
}

.author-aside__label {
  font-size: 12px;
  color: var(--color-text-3);
}

.author-aside__value {
  font-size: 12px;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.author-aside__actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-bottom: 12px;
}

.author-aside__danger {
  display: grid;
  gap: 6px;
  padding-top: 12px;
  border-top: 1px solid var(--color-border-2);
}

.author-aside__hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.author-source-chip {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  justify-self: start;
  max-width: 100%;
  padding: 2px 8px;
  font-size: 11px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 999px;
}

.author-source-chip :deep([data-slot="button"]) {
  width: 22px;
  height: 22px;
  padding: 0;
}

.author-savebar {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: flex-end;
}

.author-savebar .author__state {
  margin-right: auto;
}

.author-field {
  display: grid;
  gap: 6px;
}

.author-field > span {
  font-size: 13px;
  color: var(--color-text-3);
}

.author-create-form {
  display: grid;
  gap: 14px;
}

.author-field-hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
<style scoped>
/* 工具条：一行三段（上下文 / 标题与状态 / 动作），用色阶与一条底边分隔，不用玻璃态、渐变与悬浮位移 */
.author-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-width: 0;
}

.author-bar__left {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.author-bar__space {
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 160px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  white-space: nowrap;
}

.author-bar__sep {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-4);
}

.author-bar__kind {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

/* 标题与保存态同组：状态紧跟在页面名后面 */
.author-bar__title {
  display: flex;
  flex: 1 1 260px;
  gap: 8px;
  align-items: center;
  min-width: 200px;
}

.author-bar__right {
  display: flex;
  flex-shrink: 0;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-left: auto;
}

.author-bar__mode {
  display: flex;
  align-items: center;
}

.wiki-author__title-input {
  flex: 1;
  min-width: 160px;
}

.wiki-author__title-input :deep(input) {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

/* 保存态三色成体系：成功 / 警告 / 危险，浅底 + 文字色，不靠渐变区分 */
.wiki-author__status {
  display: inline-flex;
  flex-shrink: 0;
  gap: 5px;
  align-items: center;
  padding: 3px 10px;
  font-size: 12px;
  border-radius: 999px;
  white-space: nowrap;
}

.wiki-author__status--saved {
  color: rgb(var(--success-7));
  background: rgb(var(--success-1));
}

.wiki-author__status--dirty {
  color: rgb(var(--warning-7));
  background: rgb(var(--warning-1));
}

.wiki-author__status--conflict {
  color: rgb(var(--danger-7));
  background: rgb(var(--danger-1));
}

.wiki-author__status-icon--pulse {
  animation: wiki-author-pulse 1.8s ease-in-out infinite;
}

@keyframes wiki-author-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.35;
  }
}

.wiki-author__status-text {
  font-variant-numeric: tabular-nums;
}

/* 属性栏分组：折叠头就是分组标题行，展开内容与标题共用内边距 */
.author-aside__group + .author-aside__group {
  margin-top: 4px;
  border-top: 1px solid var(--color-border-1);
}

.author-aside__group :deep([data-slot="collapsible-trigger"]) {
  display: block;
  width: 100%;
  padding: 0;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.author-aside__group :deep([data-slot="collapsible-trigger"]:focus-visible) {
  outline: 2px solid var(--yd-focus-ring);
  outline-offset: -2px;
}

.author-aside__group-head {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  padding: 8px 0 6px;
}

.author-aside__group-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-2);
}

.author-aside__group-count {
  font-size: 11px;
  color: var(--color-text-3);
  font-variant-numeric: tabular-nums;
}

.author-aside__group-chevron {
  margin-left: auto;
  font-size: 13px;
  color: var(--color-text-3);
}

.author-aside__group-body {
  display: grid;
  gap: 10px;
  padding-bottom: 8px;
}

@media (width <= 768px) {
  .author-bar__space {
    max-width: 96px;
  }

  .author-bar__title {
    flex: 1 1 100%;
  }

  .author-insert,
  .author-body__editor {
    padding-right: 12px;
    padding-left: 12px;
  }

  .author-markdown {
    padding: 16px 12px 24px;
    margin: 8px;
  }
}
</style>
