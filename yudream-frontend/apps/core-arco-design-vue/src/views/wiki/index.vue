<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import type { HomePageLayout } from '@/api/modules/platform-cms'
import { fetchPublicCmsChrome } from '@/api/modules/platform-cms'
import { fetchPublicWikiTree, type WikiNode } from '@/api/modules/platform-wiki'

const route = useRoute()
const router = useRouter()
const tree = ref<WikiNode[]>([])
const error = ref('')
const cmsHome = ref<HomePageLayout | null>(null)
const spaceSlug = computed(() => String(route.params.spaceSlug || ''))
const nodePath = computed(() => Array.isArray(route.params.nodePath) ? route.params.nodePath.join('/') : String(route.params.nodePath || ''))
const pages = computed(() => flatten(tree.value).filter(node => node.nodeType === 'PAGE'))
const active = computed(() => pages.value.find(node => node.path === nodePath.value || node.slug === nodePath.value) || pages.value[0])
const cmsNavigation = computed(() => parseNavigation(cmsHome.value?.settings?.navigationJson))
const footerTitle = computed(() => cmsHome.value?.settings?.footerTitle || spaceSlug.value)
const footerDescription = computed(() => cmsHome.value?.settings?.footerDescription || '基于 YuDream Wiki 构建')
const footerCopyright = computed(() => cmsHome.value?.settings?.footerCopyright || `© ${new Date().getFullYear()} YuDream. All rights reserved.`)
const footerNavigation = computed(() => cmsNavigation.value)

async function load() {
  try {
    error.value = ''
    cmsHome.value = null
    tree.value = (await fetchPublicWikiTree(spaceSlug.value)).data
    void loadCmsChrome()
  }
  catch (exception: any) {
    error.value = exception?.message || '知识库无法访问'
  }
}

async function loadCmsChrome() {
  cmsHome.value = await fetchPublicCmsChrome()
}

function open(node: WikiNode) {
  router.push(`/wiki/${encodeURIComponent(spaceSlug.value)}/${encodeURI(node.path || node.slug)}`)
}

function flatten(nodes: WikiNode[]): WikiNode[] {
  return nodes.flatMap(node => [node, ...flatten(node.children || [])])
}

interface CmsNavigationItem {
  id?: string
  label: string
  url: string
  parentId?: string
  visible?: boolean
  sort?: number
  children?: CmsNavigationItem[]
}

function parseNavigation(value?: string): CmsNavigationItem[] {
  if (!value) return []
  try {
    const items = JSON.parse(value) as CmsNavigationItem[]
    if (!Array.isArray(items)) return []
    return items
      .filter(item => item.visible !== false && item.label && item.url)
      .sort((left, right) => (left.sort || 0) - (right.sort || 0))
      .map(item => ({ ...item, children: [] }))
  }
  catch {
    return []
  }
}

onMounted(load)
watch(spaceSlug, load)
</script>

<template>
  <div class="wiki-public">
    <header class="wiki-public-header">
      <div class="wiki-public-shell wiki-public-header__inner">
        <a class="wiki-public-brand" href="/">
          <span class="wiki-public-brand__mark"><FaIcon name="i-ri:book-2-line" /></span>
          <span>YuDream</span>
        </a>
        <span class="wiki-public-header__divider" />
        <a class="wiki-public-space" :href="`/wiki/${encodeURIComponent(spaceSlug)}`">{{ spaceSlug }}</a>
        <nav class="wiki-public-header__nav" aria-label="知识库导航">
          <a v-for="item in cmsNavigation" :key="item.id || item.url" :href="item.url">{{ item.label }}</a>
          <a v-if="!cmsNavigation.length" :href="`/wiki/${encodeURIComponent(spaceSlug)}`">文档</a>
          <a href="/login">管理后台</a>
        </nav>
      </div>
    </header>

    <div class="wiki-public-layout wiki-public-shell">
      <aside class="wiki-public-sidebar">
        <div class="wiki-public-sidebar__title"><FaIcon name="i-ri:folder-3-line" /> 文档目录</div>
        <nav class="wiki-public-nav" aria-label="文档目录">
          <button v-for="node in pages" :key="node.id" :class="{ active: node.id === active?.id }" @click="open(node)">{{ node.title }}</button>
        </nav>
      </aside>

      <main class="wiki-public-main">
        <p v-if="error" class="wiki-public-error">{{ error }}</p>
        <template v-else-if="active">
          <div class="wiki-public-crumb"><a :href="`/wiki/${encodeURIComponent(spaceSlug)}`">{{ spaceSlug }}</a><FaIcon name="i-ri:arrow-right-s-line" /><span>{{ active.title }}</span></div>
          <h1>{{ active.title }}</h1>
          <MdPreview :model-value="active.markdown || ''" language="zh-CN" preview-theme="github" code-theme="github" class="wiki-public-markdown" />
        </template>
        <section v-else class="wiki-public-empty">
          <FaIcon name="i-ri:book-open-line" />
          <strong>暂无已发布页面</strong>
        </section>
      </main>
    </div>

    <footer class="wiki-public-footer">
      <div class="wiki-public-shell wiki-public-footer__inner">
        <div><strong>{{ footerTitle }}</strong><span>{{ footerDescription }}</span><small>{{ footerCopyright }}</small></div>
        <nav v-if="footerNavigation.length"><a v-for="item in footerNavigation" :key="`footer-${item.id || item.url}`" :href="item.url">{{ item.label }}</a></nav>
        <a v-else href="/">YuDream</a>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.wiki-public { display: flex; min-height: 100vh; flex-direction: column; background: #fff; color: #17202a; }
.wiki-public-shell { width: min(1240px, calc(100% - 40px)); margin: 0 auto; }
.wiki-public-header { position: sticky; top: 0; z-index: 10; border-bottom: 1px solid #e5e7eb; background: rgb(255 255 255 / 94%); backdrop-filter: blur(12px); }
.wiki-public-header__inner { display: flex; min-height: 62px; align-items: center; gap: 12px; }.wiki-public-brand, .wiki-public-space, .wiki-public-header__nav a { color: #0f172a; text-decoration: none; }.wiki-public-brand { display: inline-flex; align-items: center; gap: 8px; font-size: 17px; font-weight: 800; }.wiki-public-brand__mark { display: grid; width: 29px; height: 29px; place-items: center; border-radius: 6px; background: #e8f4ef; color: #087443; }.wiki-public-header__divider { width: 1px; height: 20px; background: #e5e7eb; }.wiki-public-space { font-size: 14px; font-weight: 650; }.wiki-public-header__nav { display: flex; gap: 4px; margin-left: auto; }.wiki-public-header__nav a { padding: 7px 10px; border-radius: 5px; color: #475569; font-size: 14px; }.wiki-public-header__nav a:hover { background: #f1f5f9; color: #0f172a; }
.wiki-public-layout { display: grid; flex: 1; grid-template-columns: 252px minmax(0, 1fr); }.wiki-public-sidebar { align-self: stretch; padding: 28px 12px; border-right: 1px solid #e5e7eb; }.wiki-public-sidebar__title { display: flex; align-items: center; gap: 8px; padding: 0 10px 12px; color: #64748b; font-size: 13px; font-weight: 700; }.wiki-public-sidebar__title :deep(svg) { color: #087443; font-size: 16px; }.wiki-public-nav { display: grid; gap: 3px; }.wiki-public-nav button { width: 100%; padding: 8px 10px; border: 0; border-radius: 5px; background: transparent; color: #475569; cursor: pointer; font: inherit; font-size: 14px; text-align: left; }.wiki-public-nav button:hover { background: #f1f5f9; color: #0f172a; }.wiki-public-nav button.active { background: #e8f4ef; color: #087443; font-weight: 700; }
.wiki-public-main { min-width: 0; padding: 48px clamp(28px, 6vw, 88px) 72px; }.wiki-public-crumb { display: flex; align-items: center; gap: 4px; margin-bottom: 16px; color: #94a3b8; font-size: 13px; }.wiki-public-crumb a { color: #64748b; text-decoration: none; }.wiki-public-crumb a:hover { color: #087443; }.wiki-public-main > h1 { margin: 0 0 28px; color: #0f172a; font-size: 34px; line-height: 1.25; }.wiki-public-markdown { color: #17202a; }.wiki-public-markdown :deep(.md-editor-preview-wrapper) { overflow: visible; }.wiki-public-markdown :deep(.md-editor-preview) { padding: 0; font-size: 16px; line-height: 1.8; }.wiki-public-markdown :deep(.md-editor-code) { overflow: hidden; border: 1px solid #e5e7eb; border-radius: 5px; background: #f8fafc; }.wiki-public-markdown :deep(.md-editor-code-head) { display: flex; min-height: 32px; align-items: center; padding: 0 8px; border-bottom: 1px solid #e5e7eb; background: #f1f5f9; }.wiki-public-markdown :deep(.md-editor-code-flag) { display: none; }.wiki-public-markdown :deep(.md-editor-code-action) { display: flex; width: 100%; align-items: center; justify-content: space-between; gap: 8px; }.wiki-public-markdown :deep(.md-editor-code-lang), .wiki-public-markdown :deep(.md-editor-copy-button) { color: #64748b; font-size: 12px; }.wiki-public-markdown :deep(.md-editor-copy-button) { padding: 2px 5px; border-radius: 3px; cursor: pointer; }.wiki-public-markdown :deep(.md-editor-copy-button:hover) { background: #e2e8f0; color: #087443; }.wiki-public-markdown :deep(.md-editor-code pre) { margin: 0; border-radius: 0; background: transparent; }.wiki-public-error { color: #b42318; }.wiki-public-empty { display: grid; min-height: 360px; place-content: center; justify-items: center; gap: 10px; color: #64748b; }.wiki-public-empty :deep(svg) { color: #087443; font-size: 32px; }
.wiki-public-footer { padding: 28px 0; border-top: 1px solid #e5e7eb; background: #fff; }.wiki-public-footer__inner { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; color: #64748b; font-size: 13px; }.wiki-public-footer__inner > div { display: grid; gap: 5px; }.wiki-public-footer strong { color: #0f172a; font-size: 15px; }.wiki-public-footer small { color: #94a3b8; }.wiki-public-footer nav { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }.wiki-public-footer a { color: #087443; text-decoration: none; }
@media (max-width: 760px) { .wiki-public-shell { width: min(100% - 28px, 1240px); }.wiki-public-header__inner { min-height: 56px; }.wiki-public-header__nav a:not(:last-child) { display: none; }.wiki-public-layout { grid-template-columns: 1fr; }.wiki-public-sidebar { padding: 14px 0; border-right: 0; border-bottom: 1px solid #e5e7eb; }.wiki-public-sidebar__title { display: none; }.wiki-public-nav { display: flex; overflow-x: auto; }.wiki-public-nav button { width: auto; flex: 0 0 auto; white-space: nowrap; }.wiki-public-main { padding: 30px 0 48px; }.wiki-public-main > h1 { font-size: 28px; }.wiki-public-footer { padding: 22px 0; }.wiki-public-footer__inner { align-items: flex-start; flex-direction: column; gap: 10px; }.wiki-public-footer nav { justify-content: flex-start; } }
</style>
