<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { fetchPublicWikiDocument, fetchPublicWikiDocuments, fetchPublicWikiTree, type WikiNode, type WikiPublicDocument, type WikiPublicDocumentDetail } from '@/api/modules/platform-wiki'
import { pageTypeLabel, resolveWikiLink, wikilinksToMarkdown } from '../platform/wiki/wiki-utils'
import { rewriteApiFileUrls } from '@/utils/api-file-url'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'

const route = useRoute()
const router = useRouter()
const appSettingsStore = useAppSettingsStore()
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
  <div class="wiki-public">
    <header class="wiki-public-header">
      <div class="wiki-public-shell wiki-public-header__inner">
        <a class="wiki-public-brand" href="/wiki"><span class="wiki-public-brand__mark"><FaIcon name="i-ri:book-2-line" /></span><span>知识库</span></a>
        <span class="wiki-public-header__divider" />
        <a class="wiki-public-space" href="/wiki"><FaIcon name="i-ri:arrow-left-line" /> 全部知识库</a>
        <nav class="wiki-public-header__nav" aria-label="知识库导航"><a :href="`/wiki/${encodeURIComponent(spaceSlug)}`">{{ spaceSlug }}</a><a href="/login">管理后台</a></nav>
      </div>
    </header>

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
            <div class="wiki-public-article"><MdPreview :model-value="documentMarkdown" language="zh-CN" preview-theme="github" code-theme="github" class="wiki-public-markdown" /></div>
          </template>
        </template>
        <template v-else-if="active">
          <div class="wiki-public-crumb"><a href="/wiki">知识库</a><FaIcon name="i-ri:arrow-right-s-line" /><a :href="`/wiki/${encodeURIComponent(spaceSlug)}`">{{ spaceSlug }}</a><FaIcon name="i-ri:arrow-right-s-line" /><span>{{ active.title }}</span></div>
          <h1>{{ active.title }}</h1>
          <div v-if="active.pageType || active.tags?.length" class="wiki-public-meta"><span v-if="active.pageType" class="wiki-public-meta__type">{{ pageTypeLabel(active.pageType) }}</span><span v-for="tag in active.tags || []" :key="tag" class="wiki-public-meta__tag"># {{ tag }}</span></div>
          <p v-if="active.summary" class="wiki-public-summary">{{ active.summary }}</p>
          <div class="wiki-public-article" @click="onContentClick"><MdPreview :model-value="activeMarkdown" language="zh-CN" preview-theme="github" code-theme="github" class="wiki-public-markdown" /></div>
          <section v-if="relatedPages.length" class="wiki-public-related"><div class="wiki-public-related__title"><FaIcon name="i-ri:links-line" /> 相关页面</div><FaButton v-for="node in relatedPages" :key="node.id" type="button" @click="open(node)"><FaIcon name="i-ri:file-text-line" /> {{ node.title }}</FaButton></section>
        </template>
        <section v-else class="wiki-public-empty"><FaIcon name="i-ri:book-open-line" /><strong>暂无已发布页面</strong><a href="/wiki">返回知识库列表</a></section>
      </main>
    </div>

    <footer class="wiki-public-footer"><div class="wiki-public-shell wiki-public-footer__inner"><span>{{ appSettingsStore.siteName ? `${appSettingsStore.siteName} Wiki` : '公开知识库' }}</span><a href="/wiki">知识库</a></div></footer>
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
</template>

<style scoped>
.wiki-public { display: flex; min-height: 100vh; flex-direction: column; background: var(--color-bg-1); color: var(--color-text-1); }
.wiki-public-shell { width: min(1240px, calc(100% - 40px)); margin: 0 auto; }
.wiki-public-header { position: sticky; top: 0; z-index: 10; border-bottom: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.wiki-public-header__inner { display: flex; min-height: 62px; align-items: center; gap: 12px; }
.wiki-public-brand, .wiki-public-space, .wiki-public-header__nav a { color: var(--color-text-1); text-decoration: none; }
.wiki-public-brand { display: inline-flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 700; }
.wiki-public-brand__mark { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 6px; background: var(--color-fill-1); color: var(--color-text-1); }
.wiki-public-header__divider { width: 1px; height: 20px; background: var(--color-border-2); }
.wiki-public-space { display: inline-flex; align-items: center; gap: 5px; color: var(--color-text-2); font-size: 14px; }
.wiki-public-space:hover, .wiki-public-header__nav a:hover { color: var(--color-text-1); }
.wiki-public-header__nav { display: flex; gap: 4px; margin-left: auto; }
.wiki-public-header__nav a { padding: 7px 10px; border-radius: 5px; color: var(--color-text-2); font-size: 14px; }
.wiki-public-header__nav a:hover { background: var(--color-fill-1); }
.wiki-public-layout { display: grid; flex: 1; grid-template-columns: 252px minmax(0, 1fr); }
.wiki-public-sidebar { position: sticky; top: 62px; align-self: start; max-height: calc(100vh - 62px); overflow-y: auto; padding: 28px 12px; border-right: 1px solid var(--color-border-2); }
.wiki-public-sidebar__title { display: flex; align-items: center; gap: 8px; padding: 0 10px 12px; color: var(--color-text-3); font-size: 13px; font-weight: 700; }
.wiki-public-sidebar__title :deep(svg) { color: var(--color-text-1); font-size: 16px; }
.wiki-public-nav { display: grid; gap: 3px; }
.wiki-public-nav button { display: flex; width: 100%; min-width: 0; overflow: hidden; align-items: flex-start; padding: 8px 10px; border: 0; border-radius: 5px; background: transparent; color: var(--color-text-2); cursor: pointer; font: inherit; font-size: 14px; line-height: 1.45; text-align: left; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.wiki-public-nav :deep([data-slot="button"]) { height: auto !important; min-height: 36px; flex-shrink: 1; align-items: flex-start; justify-content: flex-start; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.wiki-public-nav button:hover { background: var(--color-fill-1); color: var(--color-text-1); }
.wiki-public-nav button.active { background: var(--color-fill-1); color: var(--color-text-1); font-weight: 700; }
.wiki-public-sidebar__title--documents { margin-top: 20px; padding-top: 16px; border-top: 1px solid var(--color-border-2); }
.wiki-public-nav__folder { padding: 6px 10px 2px; color: var(--color-text-3); font-size: 12px; }
.wiki-public-main { min-width: 0; padding: 48px clamp(28px, 6vw, 88px) 72px; }
.wiki-public-crumb { display: flex; align-items: center; gap: 4px; margin-bottom: 16px; color: var(--color-text-3); font-size: 13px; }
.wiki-public-crumb a { color: var(--color-text-2); text-decoration: none; }
.wiki-public-crumb a:hover { color: var(--color-text-1); }
.wiki-public-main > h1 { margin: 0 0 14px; color: var(--color-text-1); font-size: 34px; line-height: 1.25; }
.wiki-public-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 14px; }
.wiki-public-meta__type, .wiki-public-meta__tag { padding: 2px 10px; border-radius: 999px; background: var(--color-fill-1); color: var(--color-text-2); font-size: 12px; }
.wiki-public-meta__type { border: 1px solid var(--color-border-2); color: var(--color-text-1); }
.wiki-public-summary { margin: 0 0 20px; padding: 12px 16px; border-left: 3px solid var(--color-border-2); border-radius: 0 8px 8px 0; background: var(--color-fill-1); color: var(--color-text-2); font-size: 14px; line-height: 1.7; }
.wiki-public-related { margin-top: 40px; padding: 18px 20px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-fill-1); }
.wiki-public-related__title { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; color: var(--color-text-1); font-size: 14px; font-weight: 700; }
.wiki-public-related__title :deep(svg), .wiki-public-related button { color: var(--color-text-1); }
.wiki-public-related button { display: inline-flex; align-items: center; gap: 5px; margin: 0 8px 8px 0; padding: 6px 12px; border: 1px solid var(--color-border-2); border-radius: 999px; background: var(--color-bg-2); cursor: pointer; font: inherit; font-size: 13px; }
.wiki-public-related button:hover { border-color: var(--color-border-2); background: var(--color-fill-1); }
.wiki-public-markdown :deep(a[href^='wiki://']) { color: var(--color-text-1); border-bottom: 1px dashed var(--color-border-2); cursor: pointer; text-decoration: none; }
.wiki-public-markdown { color: var(--color-text-1); }
.wiki-public-markdown :deep(.md-editor-preview-wrapper) { overflow: visible; }
.wiki-public-markdown :deep(.md-editor-preview) { padding: 0; font-size: 16px; line-height: 1.8; }
.wiki-public-markdown :deep(.md-editor-code) { overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 5px; background: var(--color-fill-1); }
.wiki-public-markdown :deep(.md-editor-code-head) { display: flex; min-height: 32px; align-items: center; padding: 0 8px; border-bottom: 1px solid var(--color-border-2); background: var(--color-fill-2); }
.wiki-public-markdown :deep(.md-editor-code-flag) { display: none; }
.wiki-public-markdown :deep(.md-editor-code-action) { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }
.wiki-public-markdown :deep(.md-editor-code-lang), .wiki-public-markdown :deep(.md-editor-copy-button) { color: var(--color-text-3); font-size: 12px; }
.wiki-public-markdown :deep(.md-editor-copy-button) { padding: 2px 5px; border-radius: 3px; cursor: pointer; }
.wiki-public-markdown :deep(.md-editor-copy-button:hover) { background: var(--color-fill-3); color: var(--color-text-1); }
.wiki-public-markdown :deep(.md-editor-code pre) { margin: 0; border-radius: 0; background: transparent; }
.wiki-public-error { color: rgb(var(--danger-6)); }
.wiki-public-empty { display: grid; min-height: 360px; place-content: center; justify-items: center; gap: 10px; color: var(--color-text-3); }
.wiki-public-empty :deep(svg) { color: var(--color-text-1); font-size: 32px; }
.wiki-public-empty a { color: var(--color-text-1); text-decoration: none; }
.wiki-public-footer { padding: 28px 0; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.wiki-public-footer__inner { display: flex; justify-content: space-between; color: var(--color-text-3); font-size: 13px; }
.wiki-public-footer a { color: var(--color-text-1); text-decoration: none; }
.wiki-public-overlay, .wiki-public-fab { display: none; }
@media (max-width: 760px) {
  .wiki-public-shell { width: min(100% - 28px, 1240px); }
  .wiki-public-header__inner { min-height: 56px; }
  .wiki-public-header__nav a:last-child { display: none; }
  .wiki-public-layout { display: block; }
  .wiki-public-sidebar {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    z-index: 41;
    width: min(288px, 82vw);
    max-height: none;
    align-self: auto;
    padding: 24px 12px 88px;
    overflow-y: auto;
    overscroll-behavior: contain;
    border-right: 1px solid var(--color-border-2);
    background: var(--color-bg-1);
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
    z-index: 40;
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
    z-index: 42;
    width: 48px;
    height: 48px;
    padding: 0;
    border: 1px solid var(--color-border-2);
    border-radius: 50%;
    background: var(--color-bg-2);
    color: var(--color-text-1);
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
  .wiki-public-main { padding: 30px 0 72px; }
  .wiki-public-main > h1 { font-size: 28px; }
}
</style>
