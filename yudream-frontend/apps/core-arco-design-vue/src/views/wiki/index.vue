<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { fetchPublicWikiDocument, fetchPublicWikiDocuments, fetchPublicWikiTree, type WikiNode, type WikiPublicDocument, type WikiPublicDocumentDetail } from '@/api/modules/platform-wiki'
import { pageTypeLabel, resolveWikiLink, wikilinksToMarkdown } from '../platform/wiki/wiki-utils'
import { rewriteApiFileUrls } from '@/utils/api-file-url'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import WikiPublicChrome from './wiki-public-chrome.vue'

const route = useRoute()
const router = useRouter()
const tree = ref<WikiNode[]>([])
const error = ref('')
const spaceSlug = computed(() => String(route.params.spaceSlug || ''))
const nodePath = computed(() => Array.isArray(route.params.nodePath) ? route.params.nodePath.join('/') : String(route.params.nodePath || ''))
const pages = computed(() => flatten(tree.value).filter(node => node.nodeType === 'PAGE'))
const active = computed(() => pages.value.find(node => node.path === nodePath.value || node.slug === nodePath.value) || pages.value[0])
const activeMarkdown = computed(() => rewriteApiFileUrls(wikilinksToMarkdown(active.value?.body ?? active.value?.markdown ?? '')))
const relatedPages = computed(() => (active.value?.related || [])
  .map(title => pages.value.find(node => node.title === title))
  .filter((node): node is WikiNode => Boolean(node)))

// 原文档目录：已摄入的原始资料，可查看原文
const documents = ref<WikiPublicDocument[]>([])
const documentDetail = ref<WikiPublicDocumentDetail | null>(null)
const documentLoading = ref(false)
const documentError = ref('')
const documentId = computed(() => String(route.query.doc || ''))
const mobileNavOpen = ref(false)
const isMobileNav = ref(false)
let mobileQuery: MediaQueryList | undefined
const documentGroups = computed(() => {
  const groups = new Map<string, WikiPublicDocument[]>()
  for (const doc of documents.value) {
    const folder = doc.folderPath || '/'
    if (!groups.has(folder)) groups.set(folder, [])
    groups.get(folder)!.push(doc)
  }
  return [...groups.entries()].map(([folder, items]) => ({ folder, items }))
})
const documentMarkdown = computed(() => rewriteApiFileUrls(documentDetail.value?.content ?? ''))

async function load() {
  try {
    error.value = ''
    tree.value = (await fetchPublicWikiTree(spaceSlug.value)).data
    applyPublicSeo({
      title: active.value?.title || spaceSlug.value || '知识库',
      description: active.value?.summary || `浏览 ${spaceSlug.value} 的公开文档。`,
      canonicalPath: route.path,
      type: active.value ? 'article' : 'website',
      siteName: `${spaceSlug.value} Wiki`,
      publishedAt: undefined,
      updatedAt: undefined,
      breadcrumbs: [{ name: '知识库', path: '/wiki' }, { name: spaceSlug.value, path: `/wiki/${encodeURIComponent(spaceSlug.value)}` }],
    })
  }
  catch (exception: any) {
    error.value = exception?.message || '知识库无法访问'
  }
  try {
    documents.value = (await fetchPublicWikiDocuments(spaceSlug.value)).data || []
  }
  catch {
    documents.value = []
  }
}

function closeMobileNav() {
  mobileNavOpen.value = false
}

function toggleMobileNav() {
  mobileNavOpen.value = !mobileNavOpen.value
}

function syncMobileNav() {
  isMobileNav.value = Boolean(mobileQuery?.matches)
  if (!isMobileNav.value) closeMobileNav()
}

function onEscape(event: KeyboardEvent) {
  if (event.key === 'Escape') closeMobileNav()
}

function lockBody(lock: boolean) {
  document.body.style.overflow = lock ? 'hidden' : ''
}

function open(node: WikiNode) {
  closeMobileNav()
  void router.push({ path: `/wiki/${encodeURIComponent(spaceSlug.value)}/${encodeURI(node.path || node.slug)}` })
}

function openDocument(doc: WikiPublicDocument) {
  closeMobileNav()
  void router.push({ path: route.path, query: { doc: doc.id } })
}

async function loadDocument(id: string) {
  if (!id) {
    documentDetail.value = null
    return
  }
  try {
    documentLoading.value = true
    documentError.value = ''
    documentDetail.value = (await fetchPublicWikiDocument(spaceSlug.value, id)).data
    applyPublicSeo({
      title: `${documentDetail.value?.title || '原文档'} - 原文档`,
      description: `查看 ${spaceSlug.value} 知识库的原始资料：${documentDetail.value?.title || ''}`,
      canonicalPath: route.path,
      type: 'article',
      siteName: `${spaceSlug.value} Wiki`,
      breadcrumbs: [
        { name: '知识库', path: '/wiki' },
        { name: spaceSlug.value, path: `/wiki/${encodeURIComponent(spaceSlug.value)}` },
        { name: documentDetail.value?.title || '原文档', path: route.fullPath },
      ],
    })
  }
  catch (exception: any) {
    documentDetail.value = null
    documentError.value = exception?.message || '原文档无法访问'
  }
  finally {
    documentLoading.value = false
  }
}

function onContentClick(event: MouseEvent) {
  const title = resolveWikiLink(event)
  if (!title) return
  const target = pages.value.find(node => node.title === title)
  if (target) open(target)
}

function flatten(nodes: WikiNode[]): WikiNode[] {
  return nodes.flatMap(node => [node, ...flatten(node.children || [])])
}

onMounted(() => {
  load()
  mobileQuery = window.matchMedia('(max-width: 760px)')
  syncMobileNav()
  mobileQuery.addEventListener('change', syncMobileNav)
  window.addEventListener('keydown', onEscape)
})
watch(mobileNavOpen, visible => lockBody(visible))
watch(spaceSlug, () => {
  documents.value = []
  documentDetail.value = null
  closeMobileNav()
  load()
})
watch(documentId, loadDocument, { immediate: true })
watch(active, (page) => {
  if (!page) return
  applyPublicSeo({
    title: page.title,
    description: page.summary || `浏览 ${spaceSlug.value} 的公开文档。`,
    canonicalPath: route.path,
    type: 'article',
    siteName: `${spaceSlug.value} Wiki`,
    breadcrumbs: [
      { name: '知识库', path: '/wiki' },
      { name: spaceSlug.value, path: `/wiki/${encodeURIComponent(spaceSlug.value)}` },
      { name: page.title, path: route.path },
    ],
  })
})
onBeforeUnmount(() => {
  lockBody(false)
  window.removeEventListener('keydown', onEscape)
  mobileQuery?.removeEventListener('change', syncMobileNav)
  clearPublicSeo()
})
</script>

<template>
  <WikiPublicChrome>
  <div class="wiki-public">
    <div class="wiki-public-layout wiki-public-shell">
      <aside
        id="wiki-public-sidebar"
        class="wiki-public-sidebar"
        :class="{ 'is-open': mobileNavOpen }"
        :aria-hidden="isMobileNav && !mobileNavOpen"
      >
        <div class="wiki-public-sidebar__title"><FaIcon name="i-ri:folder-3-line" /> 文档目录</div>
        <nav class="wiki-public-nav" aria-label="文档目录"><FaButton v-for="node in pages" :key="node.id" :class="{ active: !documentId && node.id === active?.id }" @click="open(node)">{{ node.title }}</FaButton></nav>
        <template v-if="documents.length">
          <div class="wiki-public-sidebar__title wiki-public-sidebar__title--documents"><FaIcon name="i-ri:file-list-3-line" /> 原文档</div>
          <nav class="wiki-public-nav" aria-label="原文档目录">
            <template v-for="group in documentGroups" :key="group.folder">
              <div v-if="group.folder !== '/'" class="wiki-public-nav__folder">{{ group.folder }}</div>
              <FaButton v-for="doc in group.items" :key="doc.id" :class="{ active: doc.id === documentId }" @click="openDocument(doc)">{{ doc.title }}</FaButton>
            </template>
          </nav>
        </template>
      </aside>

      <main class="wiki-public-main">
        <p v-if="error" class="wiki-public-error">{{ error }}</p>
        <template v-else-if="documentId">
          <p v-if="documentLoading" class="wiki-public-error">原文档加载中...</p>
          <p v-else-if="documentError" class="wiki-public-error">{{ documentError }}</p>
          <template v-else-if="documentDetail">
            <div class="wiki-public-crumb"><a href="/wiki">知识库</a><FaIcon name="i-ri:arrow-right-s-line" /><a :href="`/wiki/${encodeURIComponent(spaceSlug)}`">{{ spaceSlug }}</a><FaIcon name="i-ri:arrow-right-s-line" /><span>原文档</span><FaIcon name="i-ri:arrow-right-s-line" /><span>{{ documentDetail.title }}</span></div>
            <h1>{{ documentDetail.title }}</h1>
            <div class="wiki-public-meta"><span class="wiki-public-meta__type">原文档</span><span v-if="documentDetail.folderPath && documentDetail.folderPath !== '/'" class="wiki-public-meta__tag">{{ documentDetail.folderPath }}</span><span v-if="documentDetail.kind" class="wiki-public-meta__tag">{{ documentDetail.kind }}</span></div>
            <div class="wiki-public-article"><MdPreview :model-value="documentMarkdown" language="zh-CN" preview-theme="default" code-theme="github" class="wiki-public-markdown" /></div>
          </template>
        </template>
        <template v-else-if="active">
          <div class="wiki-public-crumb"><a href="/wiki">知识库</a><FaIcon name="i-ri:arrow-right-s-line" /><a :href="`/wiki/${encodeURIComponent(spaceSlug)}`">{{ spaceSlug }}</a><FaIcon name="i-ri:arrow-right-s-line" /><span>{{ active.title }}</span></div>
          <h1>{{ active.title }}</h1>
          <div v-if="active.pageType || active.tags?.length" class="wiki-public-meta"><span v-if="active.pageType" class="wiki-public-meta__type">{{ pageTypeLabel(active.pageType) }}</span><span v-for="tag in active.tags || []" :key="tag" class="wiki-public-meta__tag"># {{ tag }}</span></div>
          <p v-if="active.summary" class="wiki-public-summary">{{ active.summary }}</p>
          <div class="wiki-public-article" @click="onContentClick"><MdPreview :model-value="activeMarkdown" language="zh-CN" preview-theme="default" code-theme="github" class="wiki-public-markdown" /></div>
          <section v-if="relatedPages.length" class="wiki-public-related"><div class="wiki-public-related__title"><FaIcon name="i-ri:links-line" /> 相关页面</div><FaButton v-for="node in relatedPages" :key="node.id" type="button" @click="open(node)"><FaIcon name="i-ri:file-text-line" /> {{ node.title }}</FaButton></section>
        </template>
        <section v-else class="wiki-public-empty"><FaIcon name="i-ri:book-open-line" /><strong>暂无已发布页面</strong><a href="/wiki">返回知识库列表</a></section>
      </main>
    </div>

    <div class="wiki-public-overlay" :class="{ 'is-open': mobileNavOpen }" aria-hidden="true" @click="closeMobileNav" />
    <button
      type="button"
      class="wiki-public-fab"
      :class="{ 'is-open': mobileNavOpen }"
      :aria-expanded="mobileNavOpen"
      aria-controls="wiki-public-sidebar"
      :aria-label="mobileNavOpen ? '关闭文档目录' : '打开文档目录'"
      @click="toggleMobileNav"
    >
      <span class="wiki-public-fab__line" />
      <span class="wiki-public-fab__line" />
      <span class="wiki-public-fab__line" />
    </button>
  </div>
  </WikiPublicChrome>
</template>

<style scoped>
.wiki-public {
  display: flex;
  min-height: 100%;
  flex: 1 1 auto;
  flex-direction: column;
  /* 主题悬浮导航（neco）经 --neco-page-top 让位；默认主题无此变量则不偏移 */
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.wiki-public-shell { width: min(1240px, calc(100% - 40px)); margin: 0 auto; }
.wiki-public-layout { display: grid; flex: 1; grid-template-columns: 252px minmax(0, 1fr); }
.wiki-public-sidebar {
  position: sticky;
  top: 0;
  align-self: start;
  max-height: calc(100vh - 72px);
  overflow-y: auto;
  margin: 24px 0;
  padding: 20px 12px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 10px;
  background: var(--yb-site-surface, var(--color-bg-2));
  box-shadow: 0 1px 3px rgb(0 0 0 / 6%);
}
.wiki-public-sidebar__title { display: flex; align-items: center; gap: 8px; padding: 0 10px 12px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; font-weight: 700; }
.wiki-public-sidebar__title :deep(svg) { color: var(--yb-site-heading, var(--color-text-1)); font-size: 16px; }
.wiki-public-nav { display: grid; gap: 3px; }
.wiki-public-nav button { display: flex; width: 100%; min-width: 0; overflow: hidden; align-items: flex-start; padding: 8px 10px; border: 0; border-radius: 0; background: transparent; color: var(--yb-site-text-2, var(--color-text-2)); cursor: pointer; font: inherit; font-size: 14px; line-height: 1.45; text-align: left; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.wiki-public-nav :deep([data-slot="button"]) { height: auto !important; min-height: 36px; flex-shrink: 1; align-items: flex-start; justify-content: flex-start; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.wiki-public-nav button:hover { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-public-nav button.active { background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-heading, var(--color-text-1)); font-weight: 700; }
.wiki-public-sidebar__title--documents { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--yb-site-border, var(--color-border-2)); }
.wiki-public-nav__folder { padding: 6px 10px 2px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; }
.wiki-public-main { min-width: 0; margin: 24px 0 48px; padding: 32px clamp(22px, 4vw, 56px) 48px; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 10px; background: var(--yb-site-surface, var(--color-bg-2)); box-shadow: 0 1px 3px rgb(0 0 0 / 6%); }
.wiki-public-crumb { display: flex; align-items: center; gap: 4px; margin-bottom: 16px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; }
.wiki-public-crumb a { color: var(--yb-site-text-2, var(--color-text-2)); text-decoration: none; }
.wiki-public-crumb a:hover { color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-public-main > h1 { margin: 0 0 14px; color: var(--yb-site-heading, var(--color-text-1)); font-size: 34px; line-height: 1.25; }
.wiki-public-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.wiki-public-meta__type, .wiki-public-meta__tag { padding: 2px 10px; border-radius: 999px; background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-text-2, var(--color-text-2)); font-size: 12px; }
.wiki-public-meta__type { border: 1px solid var(--yb-site-border, var(--color-border-2)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-public-summary { margin: 0 0 20px; padding: 12px 16px; border-left: 3px solid var(--yb-site-primary, var(--color-border-2)); background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-text-2, var(--color-text-2)); font-size: 14px; line-height: 1.7; }
.wiki-public-article { background: transparent; }
.wiki-public-related { margin-top: 40px; padding: 18px 20px; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 10px; background: var(--yb-site-hover, var(--color-fill-1)); }
.wiki-public-related__title { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; color: var(--yb-site-heading, var(--color-text-1)); font-size: 14px; font-weight: 700; }
.wiki-public-related__title :deep(svg), .wiki-public-related button { color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-public-related button { display: inline-flex; align-items: center; gap: 5px; margin: 0 8px 8px 0; padding: 6px 12px; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 999px; background: var(--yb-site-surface, var(--color-bg-2)); cursor: pointer; font: inherit; font-size: 13px; }
.wiki-public-related button:hover { background: var(--yb-site-hover, var(--color-fill-1)); }
.wiki-public-markdown :deep(a[href^='wiki://']) { color: var(--yb-site-primary, var(--color-text-1)); border-bottom: 1px dashed var(--yb-site-border, var(--color-border-2)); cursor: pointer; text-decoration: none; }
.wiki-public-markdown {
  color: var(--yb-site-text, var(--color-text-1));
  /* md-editor-v3 自带白底与浅色字，全部换成站点主题变量，否则主题深色下不可读 */
  --md-bk-color: transparent;
  --md-color: var(--yb-site-text, var(--color-text-1));
  --md-border-color: var(--yb-site-border, var(--color-border-2));
}
.wiki-public-markdown :deep(.md-editor-preview-wrapper) { overflow: visible; background: transparent; }
.wiki-public-markdown :deep(.md-editor-preview), .wiki-public-markdown :deep(.md-editor-previewOnly) { padding: 0; font-size: 16px; line-height: 1.8; background: transparent; color: inherit; }
.wiki-public-markdown :deep(:is(h1, h2, h3, h4, h5, h6)) { color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-public-markdown :deep(a:not([href^='wiki://'])) { color: var(--yb-site-primary, var(--color-text-1)); }
.wiki-public-markdown :deep(blockquote) { border-left: 3px solid var(--yb-site-primary, var(--color-border-2)); background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-text-2, var(--color-text-2)); }
.wiki-public-markdown :deep(:is(th, td) ) { border-color: var(--yb-site-border, var(--color-border-2)); background: transparent; color: inherit; }
.wiki-public-markdown :deep(:is(th)) { background: var(--yb-site-hover, var(--color-fill-1)); }
/* md-editor 默认主题的表格斑马纹是固定白底，深色下必须换成主题色条纹 */
.wiki-public-markdown :deep(table tr) { background: transparent; }
.wiki-public-markdown :deep(table tr:nth-child(2n)) { background: color-mix(in srgb, var(--yb-site-hover, var(--color-fill-1)) 55%, transparent); }
.wiki-public-markdown :deep(:not(pre) > code) { padding: 2px 6px; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 0; background: var(--yb-site-hover, var(--color-fill-1)); color: var(--yb-site-primary, var(--color-text-1)); }
.wiki-public-markdown :deep(.md-editor-code) { overflow: hidden; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 0; background: var(--yb-site-hover, var(--color-fill-1)); }
.wiki-public-markdown :deep(.md-editor-code-head) { display: flex; min-height: 32px; align-items: center; padding: 0 8px; border-bottom: 1px solid var(--yb-site-border, var(--color-border-2)); background: color-mix(in srgb, var(--yb-site-hover, var(--color-fill-2)) 80%, transparent); }
.wiki-public-markdown :deep(.md-editor-code-flag) { display: none; }
.wiki-public-markdown :deep(.md-editor-code-action) { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }
.wiki-public-markdown :deep(.md-editor-code-lang), .wiki-public-markdown :deep(.md-editor-copy-button) { color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; }
.wiki-public-markdown :deep(.md-editor-copy-button) { padding: 2px 5px; border-radius: 0; cursor: pointer; }
.wiki-public-markdown :deep(.md-editor-copy-button:hover) { background: var(--yb-site-hover, var(--color-fill-3)); color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-public-markdown :deep(.md-editor-code pre) { margin: 0; border-radius: 0; background: transparent; }
.wiki-public-error { color: var(--yb-site-danger, rgb(var(--danger-6))); }
.wiki-public-empty { display: grid; min-height: 360px; place-content: center; justify-items: center; gap: 10px; color: var(--yb-site-muted, var(--color-text-3)); }
.wiki-public-empty :deep(svg) { color: var(--yb-site-heading, var(--color-text-1)); font-size: 32px; }
.wiki-public-empty a { color: var(--yb-site-heading, var(--color-text-1)); text-decoration: none; }
.wiki-public-overlay, .wiki-public-fab { display: none; }
@media (max-width: 760px) {
  .wiki-public-shell { width: min(100% - 28px, 1240px); }
  .wiki-public-layout { display: block; }
  .wiki-public-sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    /* 站点页头 z-index 1000、主题导航 1024：抽屉必须盖过它们，否则标题被裁切 */
    z-index: 1110;
    width: min(288px, 82vw);
    max-height: none;
    align-self: auto;
    padding: 24px 12px 88px;
    overflow-y: auto;
    overscroll-behavior: contain;
    border-right: 1px solid var(--yb-site-border, var(--color-border-2));
    background: var(--yb-site-surface, var(--color-bg-1));
    transform: translateX(-100%);
    transition: transform 0.28s ease, visibility 0s linear 0.28s;
    pointer-events: none;
    visibility: hidden;
  }
  .wiki-public-sidebar.is-open {
    transform: translateX(0);
    pointer-events: auto;
    visibility: visible;
    transition: transform 0.28s ease, visibility 0s linear 0s;
    box-shadow: 8px 0 32px rgb(0 0 0 / 16%);
  }
  .wiki-public-overlay {
    display: block;
    position: fixed;
    inset: 0;
    z-index: 1100;
    background: rgb(0 0 0 / 45%);
    opacity: 0;
    pointer-events: none;
    transition: opacity 0.22s ease;
  }
  .wiki-public-overlay.is-open {
    opacity: 1;
    pointer-events: auto;
  }
  .wiki-public-fab {
    display: flex;
    position: fixed;
    left: max(16px, env(safe-area-inset-left));
    bottom: max(16px, env(safe-area-inset-bottom));
    z-index: 1120;
    width: 48px;
    height: 48px;
    padding: 0;
    border: 1px solid var(--yb-site-border, var(--color-border-2));
    border-radius: 50%;
    background: var(--yb-site-surface, var(--color-bg-2));
    color: var(--yb-site-heading, var(--color-text-1));
    box-shadow: 0 8px 24px rgb(0 0 0 / 16%);
    cursor: pointer;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 5px;
    transition: background 0.2s ease, transform 0.15s ease;
  }
  .wiki-public-fab:active { transform: scale(0.94); }
  .wiki-public-fab__line {
    display: block;
    width: 16px;
    height: 2px;
    border-radius: 1px;
    background: currentcolor;
    transition: transform 0.25s ease, opacity 0.2s ease;
  }
  .wiki-public-fab.is-open .wiki-public-fab__line:nth-child(1) { transform: translateY(7px) rotate(45deg); }
  .wiki-public-fab.is-open .wiki-public-fab__line:nth-child(2) { opacity: 0; }
  .wiki-public-fab.is-open .wiki-public-fab__line:nth-child(3) { transform: translateY(-7px) rotate(-45deg); }
  .wiki-public-main { padding: 22px 16px 48px; margin: 12px 0 32px; }
  .wiki-public-main > h1 { font-size: 28px; }
}
</style>
