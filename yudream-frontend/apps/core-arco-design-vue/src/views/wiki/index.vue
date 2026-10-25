<script setup lang="ts">
import type { YdMarkdownLinkClick } from '@yudream/components'
import type { WikiQuoteAnchorTarget } from './use-wiki-quote-anchors'
import type { WikiNode, WikiPublicDocument, WikiPublicDocumentDetail, WikiPublicSpace } from '@/api/modules/platform-wiki'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchPublicWikiDocument, fetchPublicWikiDocuments, fetchPublicWikiSpaces, fetchPublicWikiTree } from '@/api/modules/platform-wiki'
import { rewriteApiFileUrls } from '@/utils/api-file-url'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import { pageTypeLabel, resolveWikiLink, wikilinksToMarkdown } from '../platform/wiki/wiki-utils'
import { useWikiArticleToc } from './use-wiki-article-toc'
import { useWikiChromeOffset } from './use-wiki-chrome-offset'
import { useWikiPublicComments } from './use-wiki-public-comments'
import { useWikiQuoteAnchors, WIKI_QUOTE_ENTRY_SELECTOR } from './use-wiki-quote-anchors'
import { copyWikiSiteTokens, resolveWikiPageRoot } from './use-wiki-site-tokens'
import WikiPublicChrome from './wiki-public-chrome.vue'
import { rewritePublishedWikiImages } from './wiki-public-images'
import WikiCommentThread from './WikiCommentThread.vue'
import WikiPublicComments from './WikiPublicComments.vue'
import WikiPublicNav from './WikiPublicNav.vue'

interface WikiCrumb { label: string, to?: string }
interface WikiHomeEntry { id: string, title: string, href: string, depth: number, directory: boolean }
interface WikiSourceRef { title: string, documentId: string }

/** 页面根的稳定标记：SiteChrome 主题就绪后会重建页面元素，composables 需要重新定位 */
const WIKI_DOC_ROOT_SELECTOR = '[data-wiki-page-root="doc"]'

const route = useRoute()
const router = useRouter()
const root = ref<HTMLElement | null>(null)
const articleRef = ref<HTMLElement | null>(null)

// ---- 空间与已发布目录 ----
const spaces = ref<WikiPublicSpace[]>([])
const tree = ref<WikiNode[]>([])
const treeLoading = ref(true)
const treeError = ref('')
const spaceSlug = computed(() => String(route.params.spaceSlug || ''))
const nodePath = computed(() => Array.isArray(route.params.nodePath) ? route.params.nodePath.join('/') : String(route.params.nodePath || ''))
const spaceMeta = computed(() => spaces.value.find(space => space.slug === spaceSlug.value))
const spaceName = computed(() => spaceMeta.value?.name || spaceSlug.value || '知识库')
const spaceHref = computed(() => `/wiki/${encodeURIComponent(spaceSlug.value)}`)
const pages = computed(() => flatten(tree.value).filter(node => node.nodeType === 'PAGE'))
const active = computed(() => pages.value.find(node => matchesPath(node)) || null)
const activePathKey = computed(() => active.value?.path || active.value?.slug || '')
// 已发布节点的正文图片改写到发布态代理；代理地址由服务端按“文件被该发布版本正文引用”复核
const activeMarkdown = computed(() => rewritePublishedWikiImages(stripFeishuFigureResidue(wikilinksToMarkdown(active.value?.body ?? active.value?.markdown ?? '')), spaceSlug.value, active.value?.id ?? ''))
const relatedPages = computed(() => (active.value?.related || [])
  .map(title => pages.value.find(node => node.title === title))
  .filter((node): node is WikiNode => Boolean(node)))

// ---- 原文档（可分享的原始资料阅读，兼容旧 ?doc=） ----
const documents = ref<WikiPublicDocument[]>([])
const documentsLoading = ref(false)
const documentDetail = ref<WikiPublicDocumentDetail | null>(null)
const documentLoading = ref(false)
const documentError = ref('')
const documentId = computed(() => String(route.query.doc || ''))
// 飞书导出残留的 <figure data-line>…<figcaption> 不是白名单 HTML，渲染时会被转义成字面文本；
// 其信息（图片名说明）已由图片 Markdown 语法承载，渲染前剥离整段
function stripFeishuFigureResidue(markdown: string): string {
  return markdown ? markdown.replace(/<figure[^>]*>[\s\S]*?<\/figure>/g, '').replace(/\n{3,}/g, '\n\n') : markdown
}

// 原文档没有“已发布节点”锚点：正文图片地址不改写，DTO 的 images 列表也不渲染，
// 私有文件一律保持匿名不可读，不把裸 /api/files/{id}/content 当作公开图片使用
const documentMarkdown = computed(() => stripFeishuFigureResidue(rewriteApiFileUrls(documentDetail.value?.content ?? '')))

// 请求代际：切空间/切文档后落地的旧响应直接丢弃，避免目录与正文串台
let treeRequestId = 0
let spacesRequestId = 0
let documentsRequestId = 0
let documentRequestId = 0

const isDocumentView = computed(() => Boolean(documentId.value))
const isSpaceHome = computed(() => !nodePath.value && !isDocumentView.value)
const pageMissing = computed(() => Boolean(nodePath.value) && !isDocumentView.value && !treeLoading.value && !treeError.value && !active.value)

// ---- 公开评论（整页入口 + 正文引用线程共用一份数据） ----
const commentController = useWikiPublicComments(() => ({ spaceSlug: spaceSlug.value, nodeId: active.value?.id || '' }))
// 只有根评论带锚点（回复继承根锚点），因此高亮目标只取根评论，避免同一段文字被重复装饰。
// 目标是整条评论：锚点是扁平线协议字段，versionId/revision/contentHash/anchorState 是它的兄弟字段。
const anchorTargets = computed<WikiQuoteAnchorTarget[]>(() => commentController.comments.value
  .filter(comment => !comment.parentId && comment.anchor?.exact)
  .map(comment => ({ commentId: comment.id, comment })))

const threadOpen = ref(false)
const activeThreadId = ref('')
const isWideLayout = ref(false)
const commentDrawerOpen = ref(false)
// 选区引用与阅读层高亮只服务“当前已发布页面”的只读预览：原文档与空间首页不接线
const quoteEnabled = computed(() => Boolean(active.value) && !isDocumentView.value)
// 不传 currentScope：节点的 publishedVersionId 与评论盖章的版本不是同一个判定口径，
// 客户端拿不到当前版本的 contentHash/revision，只比 versionId 会把「内容未变的重新发布」
// 误判为改版；改版与否以服务端按 contentHash 判定的 anchorState 为准。
const { statuses: anchorStatuses, pendingQuote, selectionNotice, dismissQuote, locate, activate, reapply } = useWikiQuoteAnchors({
  containerRef: articleRef,
  activeKey: computed(() => active.value?.id || documentId.value || ''),
  enabled: quoteEnabled,
  targets: anchorTargets,
  onOpenComment: commentId => openThread(commentId, true),
})

const { items: tocItems, activeId: tocActiveId, sync: syncToc, scrollTo: scrollToSection } = useWikiArticleToc(articleRef, { onRendered: reapply })
const { remeasure: remeasureChrome } = useWikiChromeOffset(root, WIKI_DOC_ROOT_SELECTOR)
const showToc = computed(() => Boolean(active.value) && !isDocumentView.value && tocItems.value.length > 0)
// 桌面端评论线程占用右侧栏（目录让位给线程，但保留工具条的目录入口）；窄屏改走右侧抽屉
const showThreadColumn = computed(() => threadOpen.value && isWideLayout.value && quoteEnabled.value)

// 上一页/下一页按已发布页面的阅读顺序推进
const pageIndex = computed(() => active.value ? pages.value.findIndex(node => node.id === active.value?.id) : -1)
const previousPage = computed(() => pageIndex.value > 0 ? pages.value[pageIndex.value - 1] : null)
const nextPage = computed(() => pageIndex.value >= 0 && pageIndex.value < pages.value.length - 1 ? pages.value[pageIndex.value + 1] : null)

// 面包屑：知识库 / 空间 / 祖先目录 / 当前页（目录没有可分享 URL，只作层级说明）
const ancestorDirectories = computed(() => active.value ? findAncestors(tree.value, active.value.id) || [] : [])
const breadcrumbs = computed<WikiCrumb[]>(() => {
  const items: WikiCrumb[] = [{ label: '知识库', to: '/wiki' }]
  if (!spaceSlug.value) {
    return items
  }
  items.push({ label: spaceName.value, to: spaceHref.value })
  if (isDocumentView.value) {
    items.push({ label: '原文档' })
    if (documentDetail.value) {
      items.push({ label: documentDetail.value.title })
    }
    return items
  }
  if (active.value) {
    for (const directory of ancestorDirectories.value) {
      items.push({ label: directory.title })
    }
    items.push({ label: active.value.title })
  }
  return items
})

// 原始来源：frontmatter 里的来源条目，能对上公开原文档的给出可点击链接，对不上只做文字说明
// （摄入写回的 sources 可能带前导 `/`，比较时先归一化）
const sourceRefs = computed<WikiSourceRef[]>(() => (active.value?.sources || [])
  .map(item => item.trim())
  .filter(Boolean)
  .map((raw) => {
    const normalized = raw.replace(/^\/+/, '')
    const document = documents.value.find(item => item.title === raw || item.id === raw || item.title === normalized || item.id === normalized)
    return { title: document?.title || raw, documentId: document?.id || '' }
  }))

// 空间首页：已发布目录（目录行只作分组，页面行给出真实链接）
const spaceHomeEntries = computed(() => collectEntries(tree.value))

// ---- 移动端：目录抽屉 + 折叠式本页目录（单一 document scroll） ----
const navDrawerOpen = ref(false)
const compactTocOpen = ref(false)

// YdDrawer 内容 teleport 到 body，脱离 .site-chrome 的变量继承域；
// 打开时把站点主题变量复制到抽屉面板，抽屉内的树与文字样式才和站点一致。
function copyDrawerTokens(selector: string) {
  const panel = document.querySelector<HTMLElement>(selector)
  const source = resolveWikiPageRoot(root.value, WIKI_DOC_ROOT_SELECTOR)
  if (!panel || !source) {
    return
  }
  copyWikiSiteTokens(panel, source)
}

function syncDrawerTokens() {
  copyDrawerTokens('.wiki-doc__drawer')
}

function syncThreadDrawerTokens() {
  copyDrawerTokens('.wiki-doc__thread-drawer')
}

// 抽屉锁定 body 时记下原值，关闭后恢复原样，不覆盖其它浮层的锁定
let bodyLocked = false
let previousBodyOverflow = ''
function setBodyLock(lock: boolean) {
  if (lock === bodyLocked) {
    return
  }
  bodyLocked = lock
  if (lock) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  }
  else {
    document.body.style.overflow = previousBodyOverflow
  }
}

function closeNavDrawer() {
  navDrawerOpen.value = false
}

function pageHref(node: WikiNode): string {
  return `${spaceHref.value}/${encodeURI(node.path || node.slug)}`
}

function open(node: WikiNode) {
  closeNavDrawer()
  void router.push({ path: pageHref(node) })
}

/**
 * 打开评论线程面板：窄屏走右侧抽屉（与目录抽屉互斥），桌面用右侧栏。
 * `scroll` 为真时（点击线程里的引用摘录/卡片）同时滚动到唯一命中的正文引用，
 * 歧义/失锚的评论不会滚动到错误文字。
 */
function openThread(commentId = '', scroll = false) {
  if (commentId) {
    activeThreadId.value = commentId
    activate(commentId)
    if (scroll) {
      locate(commentId)
    }
  }
  threadOpen.value = true
  if (!isWideLayout.value) {
    commentDrawerOpen.value = true
    navDrawerOpen.value = false
  }
}

/** 选中正文文字后从浮出入口进入：面板切到新引用草稿态（发表仍是服务端待审核） */
function startAnchoredComment() {
  if (!pendingQuote.value) {
    return
  }
  activeThreadId.value = ''
  threadOpen.value = true
  if (!isWideLayout.value) {
    commentDrawerOpen.value = true
    navDrawerOpen.value = false
  }
}

function closeThread() {
  threadOpen.value = false
  commentDrawerOpen.value = false
  activeThreadId.value = ''
}

/** 引用评论已提交（待审核）：收起浮出入口，不把待审核内容塞进已发布列表 */
function handleQuotePosted() {
  dismissQuote()
}

function flatten(nodes: WikiNode[]): WikiNode[] {
  return nodes.flatMap(node => [node, ...flatten(node.children || [])])
}

function collectEntries(nodes: WikiNode[], depth = 0): WikiHomeEntry[] {
  return nodes.flatMap((node) => {
    if (node.nodeType === 'DIRECTORY' && !(node.children || []).length) {
      return []
    }
    return [
      { id: node.id, title: node.title, href: pageHref(node), depth, directory: node.nodeType === 'DIRECTORY' },
      ...collectEntries(node.children || [], depth + 1),
    ]
  })
}

function findAncestors(nodes: WikiNode[], targetId: string, trail: WikiNode[] = []): WikiNode[] | null {
  for (const node of nodes) {
    if (node.id === targetId) {
      return trail
    }
    const found = findAncestors(node.children || [], targetId, [...trail, node])
    if (found) {
      return found
    }
  }
  return null
}

function matchesPath(node: WikiNode): boolean {
  return node.path === nodePath.value || node.slug === nodePath.value
}

async function loadTree() {
  const requestId = ++treeRequestId
  treeLoading.value = true
  treeError.value = ''
  try {
    const next = (await fetchPublicWikiTree(spaceSlug.value)).data || []
    if (requestId !== treeRequestId) {
      return
    }
    tree.value = next
  }
  catch (exception: any) {
    if (requestId !== treeRequestId) {
      return
    }
    tree.value = []
    treeError.value = exception?.message || '知识库无法访问'
  }
  finally {
    if (requestId === treeRequestId) {
      treeLoading.value = false
    }
  }
}

/** 空间名称/简介属于增强信息：失败时退回 slug，不阻断正文阅读 */
async function loadSpaces() {
  const requestId = ++spacesRequestId
  try {
    const next = (await fetchPublicWikiSpaces()).data || []
    if (requestId !== spacesRequestId) {
      return
    }
    spaces.value = next
  }
  catch {
    if (requestId !== spacesRequestId) {
      return
    }
    spaces.value = []
  }
}

async function loadDocuments() {
  const requestId = ++documentsRequestId
  documentsLoading.value = true
  try {
    const next = (await fetchPublicWikiDocuments(spaceSlug.value)).data || []
    if (requestId !== documentsRequestId) {
      return
    }
    documents.value = next
  }
  catch {
    if (requestId !== documentsRequestId) {
      return
    }
    // 原文档目录是附加信息：失败时隐藏该分组，不影响正文阅读
    documents.value = []
  }
  finally {
    if (requestId === documentsRequestId) {
      documentsLoading.value = false
    }
  }
}

function load() {
  void loadTree()
  void loadSpaces()
  void loadDocuments()
}

async function loadDocument(id: string) {
  const requestId = ++documentRequestId
  if (!id) {
    documentDetail.value = null
    documentError.value = ''
    documentLoading.value = false
    return
  }
  documentLoading.value = true
  documentError.value = ''
  documentDetail.value = null
  try {
    const detail = (await fetchPublicWikiDocument(spaceSlug.value, id)).data
    if (requestId !== documentRequestId) {
      return
    }
    documentDetail.value = detail
  }
  catch (exception: any) {
    if (requestId !== documentRequestId) {
      return
    }
    documentDetail.value = null
    documentError.value = exception?.message || '原文档无法访问'
  }
  finally {
    if (requestId === documentRequestId) {
      documentLoading.value = false
    }
  }
}

function onPreviewLinkClick({ event }: YdMarkdownLinkClick) {
  const title = resolveWikiLink(event)
  if (!title) {
    return
  }
  const target = pages.value.find(node => node.title === title)
  if (target) {
    open(target)
  }
}

function breadcrumbSeo() {
  return breadcrumbs.value.map(item => ({ name: item.label, path: item.to || route.fullPath }))
}

function applySeo() {
  const siteName = `${spaceName.value} Wiki`
  if (isDocumentView.value) {
    applyPublicSeo({
      title: `${documentDetail.value?.title || '原文档'} - 原文档`,
      description: `查看 ${spaceName.value} 知识库的原始资料：${documentDetail.value?.title || ''}`,
      canonicalPath: route.path,
      type: 'article',
      siteName,
      breadcrumbs: breadcrumbSeo(),
    })
    return
  }
  if (active.value) {
    applyPublicSeo({
      title: active.value.title,
      description: active.value.summary || `浏览 ${spaceName.value} 的公开文档。`,
      canonicalPath: route.path,
      type: 'article',
      siteName,
      breadcrumbs: breadcrumbSeo(),
    })
    return
  }
  applyPublicSeo({
    title: pageMissing.value ? '页面不存在或未发布' : spaceName.value,
    description: spaceMeta.value?.description || `浏览 ${spaceName.value} 的公开文档。`,
    canonicalPath: route.path,
    type: 'website',
    siteName,
    breadcrumbs: breadcrumbSeo(),
  })
}

// 目录抽屉只服务 <768px 的移动端单文档流：跨回桌面宽度必须收起，避免浮层压在正文上
let desktopQuery: MediaQueryList | undefined
// 1180px 以上评论线程常驻右侧栏；窄于此改用右侧抽屉（正文始终是单一 document scroll）
let wideQuery: MediaQueryList | undefined

function syncDrawerBreakpoint() {
  if (desktopQuery?.matches) {
    closeNavDrawer()
  }
}

function applyWideLayout(matches: boolean) {
  isWideLayout.value = matches
  if (!threadOpen.value) {
    commentDrawerOpen.value = false
    return
  }
  commentDrawerOpen.value = !matches
  if (matches) {
    closeNavDrawer()
  }
}

function onWideLayoutChange(event: MediaQueryListEvent) {
  applyWideLayout(event.matches)
}

onMounted(() => {
  load()
  desktopQuery = window.matchMedia('(min-width: 768px)')
  syncDrawerBreakpoint()
  desktopQuery.addEventListener('change', syncDrawerBreakpoint)
  wideQuery = window.matchMedia('(min-width: 1180px)')
  applyWideLayout(wideQuery.matches)
  wideQuery.addEventListener('change', onWideLayoutChange)
})

// 目录抽屉与评论抽屉互斥：同一时刻只保留一个浮层，body 锁定与站点变量复制共用同一套逻辑
watch(navDrawerOpen, (visible) => {
  if (visible) {
    commentDrawerOpen.value = false
  }
  setBodyLock(visible || commentDrawerOpen.value)
  if (visible) {
    void nextTick(syncDrawerTokens)
  }
})

watch(commentDrawerOpen, (visible) => {
  if (visible) {
    navDrawerOpen.value = false
  }
  setBodyLock(visible || navDrawerOpen.value)
  if (visible) {
    void nextTick(syncThreadDrawerTokens)
  }
})

// 选区浮出入口 teleport 到 body，同样要把站点主题变量复制过去才和阅读页一致
watch(pendingQuote, async (quote) => {
  if (!quote) {
    return
  }
  await nextTick()
  const entry = document.querySelector<HTMLElement>(WIKI_QUOTE_ENTRY_SELECTOR)
  const source = resolveWikiPageRoot(root.value, WIKI_DOC_ROOT_SELECTOR)
  if (entry && source) {
    copyWikiSiteTokens(entry, source)
  }
})

watch(spaceSlug, () => {
  tree.value = []
  documents.value = []
  documentDetail.value = null
  treeError.value = ''
  spaces.value = []
  closeNavDrawer()
  closeThread()
  compactTocOpen.value = false
  load()
  remeasureChrome()
})

// 空间与文档同时变化时都要重新取原文档，避免沿用上一个空间的详情
watch([spaceSlug, documentId], ([, id]) => void loadDocument(id), { immediate: true })

// 任何导航（含浏览器前进后退）都先收起移动端抽屉与评论线程，避免跨页残留
watch(() => route.fullPath, () => {
  closeNavDrawer()
  closeThread()
})

// 换页后回到顶部并重新生成目录；目录锚点与评论高亮都只在当前文章内有效
watch([() => active.value?.id, documentId], ([pageId], [previousPageId]) => {
  if (pageId && pageId !== previousPageId) {
    window.scrollTo({ top: 0 })
  }
  closeThread()
  void nextTick(syncToc)
})

watch([active, documentDetail, isSpaceHome, pageMissing, spaceMeta], applySeo, { immediate: true })

onBeforeUnmount(() => {
  treeRequestId += 1
  spacesRequestId += 1
  documentsRequestId += 1
  documentRequestId += 1
  desktopQuery?.removeEventListener('change', syncDrawerBreakpoint)
  wideQuery?.removeEventListener('change', onWideLayoutChange)
  if (bodyLocked) {
    setBodyLock(false)
  }
  clearPublicSeo()
})
</script>

<template>
  <WikiPublicChrome>
    <div ref="root" data-wiki-page-root="doc" class="wiki-doc">
      <div class="wiki-doc__shell" :class="{ 'wiki-doc__shell--with-toc': showToc && !showThreadColumn, 'wiki-doc__shell--with-thread': showThreadColumn }">
        <div class="wiki-doc__rail">
          <aside class="wiki-doc__rail-inner" aria-label="文档导航">
            <WikiPublicNav
              :tree="tree"
              :active-path="isDocumentView ? '' : activePathKey"
              :space-slug="spaceSlug"
              :documents="documents"
              :documents-loading="documentsLoading"
              :document-id="documentId"
              :tree-loading="treeLoading"
              :tree-error="treeError"
              @select="closeNavDrawer"
              @open-document="closeNavDrawer"
            />
          </aside>
        </div>

        <main class="wiki-doc__main">
          <div class="wiki-doc__toolbar" :class="{ 'wiki-doc__toolbar--pinned': showThreadColumn }">
            <YdButton class="wiki-doc__toolbar-nav" variant="outline" size="sm" type="button" aria-controls="wiki-doc-nav-drawer" @click="navDrawerOpen = true">
              <YdIcon name="morph:list-tree" /> 目录
            </YdButton>
            <YdButton
              v-if="showToc"
              class="wiki-doc__toolbar-toc"
              variant="ghost"
              size="sm"
              type="button"
              aria-controls="wiki-doc-compact-toc"
              :aria-expanded="compactTocOpen"
              @click="compactTocOpen = !compactTocOpen"
            >
              <YdIcon name="morph:list" /> 本页目录
            </YdButton>
          </div>
          <nav v-if="showToc && compactTocOpen" id="wiki-doc-compact-toc" class="wiki-doc__compact-toc" aria-label="本页目录">
            <a
              v-for="item in tocItems"
              :key="item.id"
              :href="`#${item.id}`"
              :class="[`is-level-${item.level}`, { 'is-active': item.id === tocActiveId }]"
              @click="scrollToSection(item, $event)"
            >{{ item.text }}</a>
          </nav>

          <template v-if="isDocumentView">
            <div v-if="documentLoading" class="wiki-doc__state"><YdLoading :size="26" text="原文档加载中…" /></div>
            <YdResult
              v-else-if="documentError"
              class="wiki-doc__state"
              status="error"
              title="原文档无法访问"
              :description="documentError"
            >
              <template #action>
                <YdButton type="button" variant="outline" @click="loadDocument(documentId)"><YdIcon name="morph:refresh-cw" /> 重试</YdButton>
              </template>
            </YdResult>
            <article v-else-if="documentDetail" class="wiki-doc__article">
              <YdBreadcrumb :items="breadcrumbs" />
              <h1 class="wiki-doc__title">{{ documentDetail.title }}</h1>
              <div class="wiki-doc__meta">
                <YdTag>原文档</YdTag>
                <YdTag v-if="documentDetail.folderPath && documentDetail.folderPath !== '/'">{{ documentDetail.folderPath }}</YdTag>
                <YdTag v-if="documentDetail.kind">{{ documentDetail.kind }}</YdTag>
              </div>
              <div class="wiki-doc__markdown"><YdMarkdownEditor :model-value="documentMarkdown" readonly height="auto" preview-theme="default" code-theme="github" aria-label="原文档内容" @link-click="onPreviewLinkClick" /></div>
            </article>
          </template>

          <YdResult
            v-else-if="treeError"
            class="wiki-doc__state"
            status="error"
            title="知识库无法访问"
            :description="treeError"
          >
            <template #action>
              <YdButton type="button" variant="outline" @click="load"><YdIcon name="morph:refresh-cw" /> 重新加载</YdButton>
            </template>
          </YdResult>
          <div v-else-if="treeLoading" class="wiki-doc__state"><YdLoading :size="26" text="正在加载文档…" /></div>

          <YdResult
            v-else-if="pageMissing"
            class="wiki-doc__state"
            status="404"
            title="页面不存在或未发布"
            description="该地址没有对应的已发布文档，可能已被移动、取消发布或链接有误。"
          >
            <template #action>
              <YdButton type="button" variant="outline" @click="router.push(spaceHref)"><YdIcon name="morph:arrow-left" /> 返回空间首页</YdButton>
            </template>
          </YdResult>

          <article v-else-if="active" class="wiki-doc__article">
            <YdBreadcrumb :items="breadcrumbs" />
            <h1 class="wiki-doc__title">{{ active.title }}</h1>
            <div v-if="active.pageType || active.tags?.length || active.path" class="wiki-doc__meta">
              <YdTag v-if="active.pageType">{{ pageTypeLabel(active.pageType) }}</YdTag>
              <YdTag v-for="tag in active.tags || []" :key="tag"># {{ tag }}</YdTag>
              <span v-if="active.path" class="wiki-doc__path"><YdIcon name="morph:corner-down-right" /> {{ active.path }}</span>
            </div>
            <p v-if="active.summary" class="wiki-doc__summary">{{ active.summary }}</p>
            <div v-if="selectionNotice" class="wiki-doc__notice">
              <YdAlert variant="default" icon="morph:info" :description="selectionNotice" />
            </div>
            <div ref="articleRef" class="wiki-doc__content">
              <div class="wiki-doc__markdown"><YdMarkdownEditor :model-value="activeMarkdown" readonly height="auto" preview-theme="default" code-theme="github" aria-label="知识库页面内容" @link-click="onPreviewLinkClick" /></div>
            </div>

            <section v-if="sourceRefs.length" class="wiki-doc__section">
              <h2 class="wiki-doc__section-title"><YdIcon name="morph:file-input" /> 原始来源</h2>
              <ul class="wiki-doc__sources">
                <li v-for="source in sourceRefs" :key="source.title">
                  <RouterLink v-if="source.documentId" class="wiki-doc__source" :to="{ query: { doc: source.documentId } }">
                    <YdIcon name="morph:file-text" /> {{ source.title }}
                  </RouterLink>
                  <span v-else class="wiki-doc__source wiki-doc__source--plain"><YdIcon name="morph:file-text" /> {{ source.title }}</span>
                </li>
              </ul>
            </section>

            <section v-if="relatedPages.length" class="wiki-doc__section">
              <h2 class="wiki-doc__section-title"><YdIcon name="morph:link" /> 相关页面</h2>
              <ul class="wiki-doc__related">
                <li v-for="node in relatedPages" :key="node.id">
                  <RouterLink class="wiki-doc__related-link" :to="pageHref(node)"><YdIcon name="morph:file-text" /> {{ node.title }}</RouterLink>
                </li>
              </ul>
            </section>

            <WikiPublicComments :controller="commentController" :statuses="anchorStatuses" @locate="openThread($event, true)" />

            <nav v-if="previousPage || nextPage" class="wiki-doc__pager" aria-label="上一篇/下一篇">
              <RouterLink v-if="previousPage" class="wiki-doc__pager-link wiki-doc__pager-link--prev" :to="pageHref(previousPage)">
                <small><YdIcon name="morph:arrow-left" /> 上一篇</small>
                <strong>{{ previousPage.title }}</strong>
              </RouterLink>
              <span v-else class="wiki-doc__pager-spacer" />
              <RouterLink v-if="nextPage" class="wiki-doc__pager-link wiki-doc__pager-link--next" :to="pageHref(nextPage)">
                <small>下一篇 <YdIcon name="morph:arrow-right" /></small>
                <strong>{{ nextPage.title }}</strong>
              </RouterLink>
            </nav>
          </article>

          <section v-else-if="pages.length" class="wiki-doc__home">
            <YdBreadcrumb :items="breadcrumbs" />
            <h1 class="wiki-doc__title">{{ spaceName }}</h1>
            <p v-if="spaceMeta?.description" class="wiki-doc__summary">{{ spaceMeta.description }}</p>
            <div class="wiki-doc__home-stats">
              <span><strong>{{ pages.length }}</strong> 篇已发布文档</span>
              <span v-if="documents.length"><strong>{{ documents.length }}</strong> 份原文档</span>
            </div>
            <section class="wiki-doc__section">
              <h2 class="wiki-doc__section-title"><YdIcon name="morph:folder-tree" /> 目录</h2>
              <YdList divided class="wiki-doc__entries">
                <YdListRow v-for="entry in spaceHomeEntries" :key="entry.id">
                  <RouterLink
                    v-if="!entry.directory"
                    class="wiki-doc__entry"
                    :class="`is-depth-${Math.min(entry.depth, 3)}`"
                    :to="entry.href"
                  >
                    <YdIcon name="morph:file-text" /> {{ entry.title }}
                  </RouterLink>
                  <span v-else class="wiki-doc__entry wiki-doc__entry--directory" :class="`is-depth-${Math.min(entry.depth, 3)}`">
                    <YdIcon name="morph:folder" /> {{ entry.title }}
                  </span>
                </YdListRow>
              </YdList>
            </section>
          </section>

          <YdEmpty
            v-else
            class="wiki-doc__state"
            icon="morph:book-open"
            title="暂无已发布页面"
            description="该知识库还没有公开的已发布文档。"
          >
            <YdButton type="button" variant="outline" @click="router.push('/wiki')"><YdIcon name="morph:arrow-left" /> 返回知识库列表</YdButton>
          </YdEmpty>
        </main>

        <aside v-if="showToc && !showThreadColumn" class="wiki-doc__toc" aria-label="本页目录">
          <div class="wiki-doc__toc-inner">
            <div class="wiki-doc__toc-title">本页目录</div>
            <a
              v-for="item in tocItems"
              :key="item.id"
              :href="`#${item.id}`"
              :class="[`is-level-${item.level}`, { 'is-active': item.id === tocActiveId }]"
              @click="scrollToSection(item, $event)"
            >{{ item.text }}</a>
          </div>
        </aside>

        <aside v-if="showThreadColumn" class="wiki-doc__thread" aria-label="文中评论">
          <div class="wiki-doc__thread-inner">
            <WikiCommentThread
              :controller="commentController"
              :statuses="anchorStatuses"
              :active-id="activeThreadId"
              :pending="pendingQuote"
              closable
              @locate="openThread($event, true)"
              @posted="handleQuotePosted"
              @close="closeThread"
            />
          </div>
        </aside>
      </div>

      <YdDrawer
        id="wiki-doc-nav-drawer"
        v-model="navDrawerOpen"
        side="left"
        title="文档目录"
        :footer="false"
        :show-confirm-button="false"
        content-class="wiki-doc__drawer w-[340px] max-w-[86vw]"
        @opened="syncDrawerTokens"
      >
        <WikiPublicNav
          :tree="tree"
          :active-path="isDocumentView ? '' : activePathKey"
          :space-slug="spaceSlug"
          :documents="documents"
          :documents-loading="documentsLoading"
          :document-id="documentId"
          :tree-loading="treeLoading"
          :tree-error="treeError"
          @select="closeNavDrawer"
          @open-document="closeNavDrawer"
        />
      </YdDrawer>

      <YdDrawer
        id="wiki-doc-comment-drawer"
        v-model="commentDrawerOpen"
        side="right"
        title="文中评论"
        :footer="false"
        :show-confirm-button="false"
        content-class="wiki-doc__thread-drawer w-[360px] max-w-[92vw] overflow-y-auto overscroll-contain"
        @opened="syncThreadDrawerTokens"
      >
        <WikiCommentThread
          :controller="commentController"
          :statuses="anchorStatuses"
          :active-id="activeThreadId"
          :pending="pendingQuote"
          closable
          @locate="openThread($event, true)"
          @posted="handleQuotePosted"
          @close="closeThread"
        />
      </YdDrawer>

      <Teleport to="body">
        <div
          v-if="pendingQuote?.position"
          class="wiki-doc__quote-entry"
          data-wiki-quote-entry
          :style="{ top: `${pendingQuote.position.top}px`, left: `${pendingQuote.position.left}px` }"
        >
          <YdButton type="button" size="sm" @click="startAnchoredComment">
            <YdIcon name="morph:message-square-quote" /> 评论
          </YdButton>
          <YdButton type="button" variant="ghost" size="sm" aria-label="取消引用" @click="dismissQuote">
            <YdIcon name="morph:x" />
          </YdButton>
        </div>
      </Teleport>
    </div>
  </WikiPublicChrome>
</template>

<style scoped>
.wiki-doc {
  /* 悬浮导航主题经 --neco-page-top 让位；sticky 偏移 = 主题让位 + 实测 chrome 页头底边 */
  --wiki-sticky-top: calc(var(--neco-page-top, 0px) + var(--wiki-chrome-offset, 0px));
  display: flex;
  min-height: 100%;
  flex: 1 1 auto;
  flex-direction: column;
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.wiki-doc__shell { display: grid; width: min(1320px, calc(100% - 40px)); flex: 1; grid-template-columns: 248px minmax(0, 1fr); margin: 0 auto; }
.wiki-doc__shell--with-toc { grid-template-columns: 248px minmax(0, 1fr) 188px; }
/* 评论线程常驻时由线程栏占用右侧，目录让位但保留工具条入口（避免第四根常驻栏） */
.wiki-doc__shell--with-thread { grid-template-columns: 248px minmax(0, 1fr) 340px; }
/* 左轨道：整列分隔线；目录只在自身溢出时滚动，正文始终是普通文档流 */
.wiki-doc__rail { min-width: 0; padding-right: 28px; border-right: 1px solid var(--yb-site-border, var(--color-border-2)); }
.wiki-doc__rail-inner {
  position: sticky;
  top: calc(var(--wiki-sticky-top) + 24px);
  max-height: calc(100vh - var(--wiki-sticky-top) - 48px);
  padding: 32px 0 48px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.wiki-doc__main { min-width: 0; padding: 32px clamp(24px, 4vw, 56px) 80px; }
.wiki-doc__toc { min-width: 0; padding-left: 20px; border-left: 1px solid var(--yb-site-border, var(--color-border-2)); }
.wiki-doc__toc-inner {
  position: sticky;
  top: calc(var(--wiki-sticky-top) + 32px);
  display: grid;
  gap: 6px;
  max-height: calc(100vh - var(--wiki-sticky-top) - 64px);
  padding: 32px 0 48px;
  overflow-y: auto;
  overscroll-behavior: contain;
  font-size: 13px;
}
.wiki-doc__toc-title { margin-bottom: 2px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; font-weight: 700; letter-spacing: 0.04em; }
/* 右侧评论线程：整列独立滚动，正文与移动端都保持 document scroll */
.wiki-doc__thread { min-width: 0; padding-left: 20px; border-left: 1px solid var(--yb-site-border, var(--color-border-2)); }
.wiki-doc__thread-inner {
  position: sticky;
  top: calc(var(--wiki-sticky-top) + 24px);
  max-height: calc(100vh - var(--wiki-sticky-top) - 48px);
  padding: 32px 0 48px;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.wiki-doc__toc a, .wiki-doc__compact-toc a { padding: 2px 0; color: var(--yb-site-text-2, var(--color-text-2)); line-height: 1.5; text-decoration: none; }
.wiki-doc__toc a:hover, .wiki-doc__compact-toc a:hover { color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-doc__toc a.is-active, .wiki-doc__compact-toc a.is-active { color: var(--yb-site-heading, var(--color-text-1)); font-weight: 700; }
.wiki-doc__toc a.is-level-3, .wiki-doc__compact-toc a.is-level-3 { padding-left: 12px; }
.wiki-doc__toc a.is-level-4, .wiki-doc__compact-toc a.is-level-4 { padding-left: 24px; font-size: 12px; }
/* 窄屏工具条：目录抽屉与本页目录折叠的入口，吸顶在站点页头之下且不遮挡正文 */
.wiki-doc__toolbar {
  position: sticky;
  top: var(--wiki-sticky-top);
  z-index: 5;
  display: flex;
  align-items: center;
  gap: 8px;
  margin: -8px 0 16px;
  padding: 8px 0;
  background: var(--yb-site-bg, var(--color-bg-1));
}
.wiki-doc__toolbar-nav { display: none; }
.wiki-doc__compact-toc {
  display: grid;
  gap: 4px;
  margin: 0 0 20px;
  padding: 12px 14px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  background: var(--yb-site-hover, var(--color-fill-1));
  font-size: 13px;
}
.wiki-doc__article, .wiki-doc__home { min-width: 0; }
.wiki-doc__title { margin: 14px 0 12px; color: var(--yb-site-heading, var(--color-text-1)); font-size: 34px; line-height: 1.25; overflow-wrap: anywhere; }
.wiki-doc__meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-bottom: 16px; }
.wiki-doc__path { display: inline-flex; align-items: center; gap: 4px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; overflow-wrap: anywhere; }
.wiki-doc__summary { margin: 0 0 24px; padding: 12px 16px; border-left: 3px solid var(--yb-site-primary, var(--color-border-2)); background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-text-2, var(--color-text-2)); font-size: 14px; line-height: 1.7; }
.wiki-doc__content { min-width: 0; }
.wiki-doc__section { margin-top: 48px; padding-top: 20px; border-top: 1px solid var(--yb-site-border, var(--color-border-2)); }
.wiki-doc__section-title { display: flex; align-items: center; gap: 6px; margin: 0 0 12px; color: var(--yb-site-heading, var(--color-text-1)); font-size: 16px; font-weight: 700; }
.wiki-doc__section-title :deep(svg) { font-size: 18px; }
.wiki-doc__sources, .wiki-doc__related { display: flex; flex-wrap: wrap; gap: 8px; margin: 0; padding: 0; list-style: none; }
.wiki-doc__source, .wiki-doc__related-link { display: inline-flex; min-height: 32px; align-items: center; gap: 6px; padding: 6px 14px; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 999px; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 13px; text-decoration: none; }
.wiki-doc__source:hover, .wiki-doc__related-link:hover { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-doc__source--plain { color: var(--yb-site-muted, var(--color-text-3)); }
.wiki-doc__pager { display: grid; gap: 12px; grid-template-columns: repeat(2, minmax(0, 1fr)); margin-top: 48px; }
.wiki-doc__pager-link { display: grid; gap: 6px; min-width: 0; padding: 14px 18px; border: 1px solid var(--yb-site-border, var(--color-border-2)); color: inherit; text-decoration: none; }
.wiki-doc__pager-link:hover { background: var(--yb-site-hover, var(--color-fill-1)); }
.wiki-doc__pager-link small { display: inline-flex; align-items: center; gap: 4px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; }
.wiki-doc__pager-link strong { color: var(--yb-site-heading, var(--color-text-1)); font-size: 15px; overflow-wrap: anywhere; }
.wiki-doc__pager-link--next { text-align: right; }
.wiki-doc__pager-link--next small { justify-content: flex-end; }
.wiki-doc__home-stats { display: flex; flex-wrap: wrap; gap: 18px; margin-bottom: 8px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; }
.wiki-doc__home-stats strong { color: var(--yb-site-heading, var(--color-text-1)); font-size: 20px; }
.wiki-doc__entry { display: flex; min-width: 0; flex: 1; align-items: center; gap: 8px; padding: 4px 0; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 14px; text-decoration: none; }
.wiki-doc__entry:hover { color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-doc__entry :deep(svg) { flex: 0 0 auto; color: var(--yb-site-muted, var(--color-text-3)); }
.wiki-doc__entry--directory { color: var(--yb-site-heading, var(--color-text-1)); font-weight: 600; }
.wiki-doc__entry.is-depth-1 { padding-left: 18px; }
.wiki-doc__entry.is-depth-2 { padding-left: 36px; }
.wiki-doc__entry.is-depth-3 { padding-left: 54px; }
.wiki-doc__state { min-height: 360px; }
.wiki-doc__notice { margin-bottom: 16px; }
/* 选区引用浮出入口：teleport 到 body 的固定定位浮层，水平以选区中心对齐 */
:global(.wiki-doc__quote-entry) {
  position: fixed;
  z-index: 2100;
  display: flex;
  gap: 4px;
  align-items: center;
  padding: 2px;
  transform: translateX(-50%);
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 6px;
  background: var(--yb-site-surface, var(--color-bg-2));
  box-shadow: 0 6px 20px rgb(0 0 0 / 12%);
}
/* 评论引用高亮：阅读层装饰（span），不改持久化 Markdown；定位滚动复用 --wiki-sticky-top 契约 */
.wiki-doc__markdown :deep(.wiki-quote-hit) {
  padding: 0 1px;
  margin: 0 -1px;
  background: color-mix(in srgb, var(--yb-site-primary, var(--color-fill-1)) 22%, transparent);
  border-bottom: 2px solid var(--yb-site-primary, var(--color-border-2));
  cursor: pointer;
  scroll-margin-top: calc(var(--wiki-sticky-top) + 24px);
}
.wiki-doc__markdown :deep(.wiki-quote-hit.is-active) {
  background: color-mix(in srgb, var(--yb-site-primary, var(--color-fill-1)) 38%, transparent);
}
/* 只读编辑器默认是「带边框的输入卡片」，公开阅读页压平成纯正文 */
.wiki-doc__markdown {
  color: var(--yb-site-text, var(--color-text-1));
  --md-bk-color: transparent;
  --md-color: var(--yb-site-text, var(--color-text-1));
  --md-border-color: var(--yb-site-border, var(--color-border-2));
}
.wiki-doc__markdown :deep(.yd-markdown-editor) { overflow: visible; border: 0; border-radius: 0; background: transparent; }
.wiki-doc__markdown :deep(.yd-markdown-editor .md-editor) { border: 0; background: transparent; }
.wiki-doc__markdown :deep(.md-editor-preview-wrapper) { overflow: visible; background: transparent; }
.wiki-doc__markdown :deep(.md-editor-preview), .wiki-doc__markdown :deep(.md-editor-previewOnly) { padding: 0; font-size: 16px; line-height: 1.8; background: transparent; color: inherit; }
.wiki-doc__markdown :deep(:is(h1, h2, h3, h4, h5, h6)) { color: var(--yb-site-heading, var(--color-text-1)); scroll-margin-top: calc(var(--wiki-sticky-top) + 24px); }
.wiki-doc__markdown :deep(a:not([href^='wiki://'])) { color: var(--yb-site-primary, var(--color-text-1)); }
.wiki-doc__markdown :deep(a[href^='wiki://']) { color: var(--yb-site-primary, var(--color-text-1)); border-bottom: 1px dashed var(--yb-site-border, var(--color-border-2)); cursor: pointer; text-decoration: none; }
.wiki-doc__markdown :deep(blockquote) { border-left: 3px solid var(--yb-site-primary, var(--color-border-2)); background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-text-2, var(--color-text-2)); }
.wiki-doc__markdown :deep(:is(th, td)) { border-color: var(--yb-site-border, var(--color-border-2)); background: transparent; color: inherit; }
.wiki-doc__markdown :deep(th) { background: var(--yb-site-hover, var(--color-fill-1)); }
/* md-editor 默认主题的表格斑马纹是固定白底，深色下必须换成主题色条纹 */
.wiki-doc__markdown :deep(table tr) { background: transparent; }
.wiki-doc__markdown :deep(table tr:nth-child(2n)) { background: color-mix(in srgb, var(--yb-site-hover, var(--color-fill-1)) 55%, transparent); }
.wiki-doc__markdown :deep(:not(pre) > code) { padding: 2px 6px; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 0; background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-primary, var(--color-text-1)); }
.wiki-doc__markdown :deep(.md-editor-code) { overflow: hidden; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 0; background: var(--yb-site-hover, var(--color-fill-1)); }
.wiki-doc__markdown :deep(.md-editor-code-head) { display: flex; min-height: 32px; align-items: center; padding: 0 8px; border-bottom: 1px solid var(--yb-site-border, var(--color-border-2)); background: color-mix(in srgb, var(--yb-site-hover, var(--color-fill-2)) 80%, transparent); }
.wiki-doc__markdown :deep(.md-editor-code-flag) { display: none; }
.wiki-doc__markdown :deep(.md-editor-code-action) { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }
.wiki-doc__markdown :deep(.md-editor-code-lang), .wiki-doc__markdown :deep(.md-editor-copy-button) { color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; }
.wiki-doc__markdown :deep(.md-editor-copy-button) { padding: 2px 5px; border-radius: 0; cursor: pointer; }
.wiki-doc__markdown :deep(.md-editor-copy-button:hover) { background: var(--yb-site-hover, var(--color-fill-3)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-doc__markdown :deep(.md-editor-code pre) { margin: 0; border-radius: 0; background: transparent; }
/* 抽屉面板本身也在 body 下，用 :global 命中；--yb-site-* 由 syncDrawerTokens 复制到面板上，
   YD 令牌是 oklch 三元组，面板自身样式只消费站点原始色值 */
:global(.wiki-doc__drawer) { border-right: 1px solid var(--yb-site-border, var(--color-border-2)); background: var(--yb-site-surface, var(--color-bg-2)); color: var(--yb-site-text, var(--color-text-1)); }
@media (max-width: 1179px) {
  .wiki-doc__shell--with-toc { grid-template-columns: 248px minmax(0, 1fr); }
  .wiki-doc__toc { display: none; }
}
@media (min-width: 1180px) {
  .wiki-doc__toolbar { display: none; }
  /* 评论线程占用右侧栏时目录列让位，但工具条保留「本页目录」入口，TOC 仍可达 */
  .wiki-doc__toolbar--pinned { display: flex; }
}
@media (max-width: 767px) {
  .wiki-doc__shell { width: min(100% - 28px, 1320px); grid-template-columns: minmax(0, 1fr); }
  .wiki-doc__rail { display: none; }
  .wiki-doc__main { padding: 20px 0 56px; }
  .wiki-doc__toolbar-nav { display: inline-flex; }
  .wiki-doc__title { font-size: 28px; }
  .wiki-doc__pager { grid-template-columns: minmax(0, 1fr); }
  .wiki-doc__pager-link--next { text-align: left; }
  .wiki-doc__pager-link--next small { justify-content: flex-start; }
}
</style>
