<script setup lang="ts">
import type { CmsPage, CmsTemplateContext, HomePageLayout, HomeSection } from '@/api/modules/platform-cms'
import apiCms from '@/api/modules/platform-cms'
import { hasPublicWikiSpaces } from '@/api/modules/platform-wiki'
import { chromeRuntimeCss, readChromeCss } from '@/utils/cms-chrome'
import { renderCmsMarkdown, renderCmsVariables, resolveCmsTemplateRows, sanitizeCmsHtml } from '@/utils/cms-template-render'

const route = useRoute()
const appAccountStore = useAppAccountStore()
const appSettingsStore = useAppSettingsStore()

const loading = ref(false)
const home = ref<HomePageLayout | null>(null)
const page = ref<CmsPage | null>(null)
const publishedPages = ref<CmsPage[]>([])
const templateContext = ref<CmsTemplateContext>(emptyTemplateContext())
const errorMessage = ref('')
const wikiEnabled = ref(false)
interface SiteNavigationItem {
  id?: string
  label: string
  url: string
  parentId?: string
  visible?: boolean
  sort?: number
  children?: SiteNavigationItem[]
}
type SiteLayoutMode = 'HEADER_FOOTER' | 'HEADER_COPYRIGHT' | 'ADMIN'

const slug = computed(() => {
  const value = route.params.slug
  if (Array.isArray(value)) {
    return value.join('/')
  }
  return value ? String(value) : ''
})
const homeHtml = computed(() => home.value?.settings?.homeHtml || '')
const homeCss = computed(() => [
  home.value?.settings?.homeCss,
  readChromeCss(home.value?.settings, 'header'),
  readChromeCss(home.value?.settings, 'footer'),
].filter(Boolean).join('\n'))
const homeJs = computed(() => home.value?.settings?.homeJs || '')
const activeCmsJs = computed(() => page.value ? page.value.jsContent || '' : homeJs.value)
const navigationItems = computed(() => {
  const items = parseNavigationItems(home.value?.settings?.navigationJson).filter(item => !isAuthNavigationUrl(item.url))
  return wikiEnabled.value && !items.some(item => item.url === '/wiki')
    ? [...items, { id: 'capability-wiki', label: '知识库', url: '/wiki', visible: true, sort: Number.MAX_SAFE_INTEGER }]
    : items
})
const navigationTree = computed(() => buildNavigationTree(navigationItems.value))
const footerNavigationItems = computed(() => flattenNavigation(navigationTree.value))
const footerTitle = computed(() => home.value?.settings?.footerTitle || renderContext.value.site.name)
const footerDescription = computed(() => home.value?.settings?.footerDescription || renderContext.value.site.description || '由 YuDream CMS 驱动的内容站点')
const footerCopyright = computed(() => home.value?.settings?.footerCopyright || `© ${new Date().getFullYear()} ${renderContext.value.site.name}. All rights reserved.`)
const siteLayout = computed<SiteLayoutMode>(() => (home.value?.settings?.siteLayout as SiteLayoutMode) || 'HEADER_FOOTER')
const showFooter = computed(() => siteLayout.value === 'HEADER_FOOTER')
const showCopyright = computed(() => siteLayout.value === 'HEADER_COPYRIGHT' || siteLayout.value === 'ADMIN')
const siteRuntimeCss = computed(() => chromeRuntimeCss(siteLayout.value, homeCss.value))
const archiveFilter = computed(() => ({
  category: queryValue(route.query.category),
  tag: queryValue(route.query.tag),
  keyword: queryValue(route.query.keyword),
}))
const archiveTitle = computed(() => {
  if (archiveFilter.value.category) {
    return `分类：${archiveFilter.value.category}`
  }
  if (archiveFilter.value.tag) {
    return `标签：${archiveFilter.value.tag}`
  }
  if (archiveFilter.value.keyword) {
    return `搜索：${archiveFilter.value.keyword}`
  }
  return '全部内容'
})
const renderContext = computed(() => {
  const loggedIn = appAccountStore.isLogin
  const username = appAccountStore.account || ''
  const nickname = username || '访客'
  const avatar = appAccountStore.avatar || ''
  const navUsers = loggedIn
    ? [{ name: nickname, avatar, url: '/' }]
    : [{ name: '登录', avatar: appSettingsStore.logo, url: '/login' }]
  const pageItems = publishedPages.value.map(toPageItem)
  const siteInfo = {
    name: appSettingsStore.siteName || 'YuDream',
    description: appSettingsStore.siteDescription || home.value?.subtitle || page.value?.summary || '',
    logo: appSettingsStore.logo || '',
    favicon: appSettingsStore.favicon || '',
    loginBanner: appSettingsStore.loginBanner || '',
    currentYear: String(new Date().getFullYear()),
  }
  const copyright = appSettingsStore.settings.app.copyright
  return {
    site: siteInfo,
    system: {
      ...siteInfo,
      copyright: {
        company: copyright.company || '',
        website: copyright.website || '',
        dates: copyright.dates || siteInfo.currentYear,
      },
    },
    page: {
      title: page.value?.title || home.value?.title || '',
      slug: page.value?.slug || '',
      summary: page.value?.summary || '',
      excerpt: page.value?.excerpt || '',
      categories: (page.value?.categories || []).join(', '),
      tags: (page.value?.tags || []).join(', '),
    },
    auth: {
      isLoggedIn: loggedIn ? 'true' : 'false',
      welcome: loggedIn ? `欢迎回来，${nickname}` : '欢迎访问',
    },
    user: {
      username: username || 'guest',
      nickname,
      avatar,
    },
    route: {
      path: route.path,
      slug: slug.value,
    },
    navigation: navigationItems.value,
    navUsers,
    pages: pageItems,
    cms: templateContext.value.cms,
    knowledge: templateContext.value.knowledge,
    categories: collectTermItems(publishedPages.value, 'categories'),
    tags: collectTermItems(publishedPages.value, 'tags'),
    archive: {
      title: archiveTitle.value,
      category: archiveFilter.value.category,
      tag: archiveFilter.value.tag,
      keyword: archiveFilter.value.keyword,
      isFiltered: archiveFilter.value.category || archiveFilter.value.tag || archiveFilter.value.keyword ? 'true' : 'false',
    },
  }
})

watch(() => route.fullPath, load, { immediate: true })
watch([
  activeCmsJs,
  homeHtml,
  templateContext,
  () => page.value?.htmlContent,
  () => appAccountStore.isLogin,
], runCmsScript, { flush: 'post' })

let activeCmsScript: HTMLScriptElement | null = null

onBeforeUnmount(cleanupCmsScript)

async function load() {
  loading.value = true
  errorMessage.value = ''
  home.value = null
  page.value = null
  templateContext.value = emptyTemplateContext()
  void loadWikiNavigation()
  try {
    if (slug.value) {
      const res = await apiCms.publicPage(slug.value)
      page.value = res.data
      try {
        const homeRes = await apiCms.publicHome()
        home.value = homeRes.data
      }
      catch {
        home.value = null
      }
      document.title = `${res.data.seoTitle || res.data.title} - ${appSettingsStore.siteName}`
    }
    else {
      const res = await apiCms.publicHome()
      home.value = res.data
      document.title = `${res.data.title || '站点首页'} - ${appSettingsStore.siteName}`
    }
    await Promise.all([loadPublicPages(), loadTemplateContext()])
  }
  catch (error: any) {
    errorMessage.value = error?.response?.data?.message || '页面暂不可访问'
  }
  finally {
    loading.value = false
  }
}

async function loadTemplateContext() {
  try {
    const res = await apiCms.publicTemplateContext()
    templateContext.value = res.data
  }
  catch {
    templateContext.value = emptyTemplateContext()
  }
}

function emptyTemplateContext(): CmsTemplateContext {
  return {
    cms: { pages: { latest: [] } },
    knowledge: { spaces: [], pages: [], latest: [] },
  }
}

async function loadWikiNavigation() {
  wikiEnabled.value = await hasPublicWikiSpaces()
}

async function loadPublicPages() {
  try {
    const res = await apiCms.publicPages({
      page: 1,
      size: 12,
      keyword: archiveFilter.value.keyword || undefined,
      category: archiveFilter.value.category || undefined,
      tag: archiveFilter.value.tag || undefined,
    })
    publishedPages.value = res.data.records
  }
  catch {
    publishedPages.value = []
  }
  await runCmsScript()
}

function queryValue(value: unknown) {
  if (Array.isArray(value)) {
    return value[0] ? String(value[0]) : ''
  }
  return value ? String(value) : ''
}

function toPageItem(item: CmsPage) {
  return {
    id: item.id,
    title: item.title,
    slug: item.slug,
    url: `/site/${item.slug}`,
    summary: item.summary || '',
    excerpt: item.excerpt || item.summary || '',
    coverImageUrl: item.coverImageUrl || '',
    category: item.categories?.[0] || '',
    categories: (item.categories || []).join(', '),
    tags: (item.tags || []).join(', '),
    publishedAt: dateText(item.publishedAt || item.updateTime || item.createTime),
  }
}

function collectTermItems(items: CmsPage[], key: 'categories' | 'tags') {
  const counts = new Map<string, number>()
  items.forEach((item) => {
    ;(item[key] || []).forEach((term) => {
      counts.set(term, (counts.get(term) || 0) + 1)
    })
  })
  return Array.from(counts.entries()).map(([name, count]) => ({
    name,
    label: name,
    count,
    url: `/site?${key === 'categories' ? 'category' : 'tag'}=${encodeURIComponent(name)}`,
  }))
}

function sectionStyle(section: HomeSection) {
  return section.mediaUrl
    ? { backgroundImage: `linear-gradient(90deg, rgba(15, 23, 42, 0.74), rgba(15, 23, 42, 0.18)), url(${section.mediaUrl})` }
    : undefined
}

function parseNavigationItems(value?: string): SiteNavigationItem[] {
  if (!value) {
    return []
  }
  try {
    const parsed = JSON.parse(value) as SiteNavigationItem[]
    return Array.isArray(parsed)
      ? parsed.filter(item => item.visible !== false).sort((a, b) => (a.sort || 0) - (b.sort || 0))
      : []
  }
  catch {
    return []
  }
}

function buildNavigationTree(items: SiteNavigationItem[]) {
  const itemMap = new Map<string, SiteNavigationItem>()
  const roots: SiteNavigationItem[] = []
  items.forEach((item) => {
    const cloned = { ...item, children: [] }
    if (cloned.id) {
      itemMap.set(cloned.id, cloned)
    }
  })
  items.forEach((item) => {
    const current = item.id ? itemMap.get(item.id) : { ...item, children: [] }
    if (!current) {
      return
    }
    const parent = item.parentId ? itemMap.get(item.parentId) : undefined
    if (parent) {
      parent.children = [...(parent.children || []), current]
    }
    else {
      roots.push(current)
    }
  })
  const sortItems = (list: SiteNavigationItem[]) => {
    list.sort((a, b) => (a.sort || 0) - (b.sort || 0))
    list.forEach(item => item.children?.sort((a, b) => (a.sort || 0) - (b.sort || 0)))
    return list
  }
  return sortItems(roots)
}

function flattenNavigation(items: SiteNavigationItem[]) {
  return items.flatMap(item => [item, ...(item.children || [])])
}

function isAuthNavigationUrl(url?: string) {
  const normalized = (url || '').trim().toLowerCase()
  return normalized === '/login' || normalized === '/register' || normalized === '/signup'
}

function renderDynamicHtml(value?: string) {
  if (!value) {
    return ''
  }
  const sanitized = sanitizeCmsHtml(value)
  const doc = new DOMParser().parseFromString(`<div>${sanitized}</div>`, 'text/html')
  doc.querySelectorAll('[data-yb-system-nav]').forEach(el => el.remove())
  doc.querySelectorAll('main, section').forEach((el) => {
    const style = el.getAttribute('style') || ''
    const normalizedStyle = style
      .replace(/(?:^|;)\s*min-height\s*:\s*100vh\s*;?/gi, ';')
      .replace(/^;\s*/, '')
      .replace(/;\s*;/g, ';')
      .trim()
    if (normalizedStyle) {
      el.setAttribute('style', normalizedStyle)
    }
    else {
      el.removeAttribute('style')
    }
  })
  doc.querySelectorAll('[data-visible-when]').forEach((el) => {
    const rule = el.getAttribute('data-visible-when')
    const loggedIn = appAccountStore.isLogin
    if ((rule === 'logged-in' && !loggedIn) || (rule === 'guest' && loggedIn)) {
      el.remove()
    }
  })
  doc.querySelectorAll('[data-yb-repeat]').forEach((el) => {
    const key = el.getAttribute('data-yb-repeat')
    const rows = resolveCmsTemplateRows(key || '', renderContext.value)
    const template = el.innerHTML
    el.innerHTML = rows.map((item, index) => renderCmsVariables(template, { item, index: String(index + 1) }, renderContext.value)).join('')
  })
  doc.querySelectorAll('[data-yb-html]').forEach((el) => {
    el.innerHTML = sanitizeCmsHtml(el.getAttribute('data-yb-html') || '')
    el.removeAttribute('data-yb-html')
  })
  doc.querySelectorAll('[data-yb-markdown]').forEach((el) => {
    el.innerHTML = renderCmsMarkdown(el.getAttribute('data-yb-markdown') || '')
    el.removeAttribute('data-yb-markdown')
  })
  return renderCmsVariables(doc.body.firstElementChild?.innerHTML || sanitized, {}, renderContext.value)
}

async function runCmsScript() {
  await nextTick()
  cleanupCmsScript()
  const code = stripScriptTags(activeCmsJs.value).trim()
  if (!code) {
    return
  }
  ;(window as any).__YU_CMS_CONTEXT__ = renderContext.value
  ;(window as any).__YU_CMS_DISPOSERS__ = []
  ;(window as any).__YU_CMS_REGISTER_CLEANUP__ = (dispose: unknown) => {
    if (typeof dispose === 'function') {
      ;(window as any).__YU_CMS_DISPOSERS__.push(dispose)
    }
  }
  ;(window as any).__YU_CMS_READY__ = (callback: unknown) => {
    if (typeof callback !== 'function') {
      return
    }
    const readyCallback = callback as () => void
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', readyCallback, { once: true })
      return
    }
    queueMicrotask(readyCallback)
  }
  activeCmsScript = document.createElement('script')
  activeCmsScript.dataset.yuCmsPageScript = 'true'
  activeCmsScript.textContent = `
    ;(() => {
      ${code}
    })();
  `
  document.body.appendChild(activeCmsScript)
}

function cleanupCmsScript() {
  const disposers = ((window as any).__YU_CMS_DISPOSERS__ || []) as unknown[]
  disposers.forEach((dispose) => {
    try {
      if (typeof dispose === 'function') {
        dispose()
      }
    }
    catch (error) {
      console.warn('[YuDream CMS] cleanup failed', error)
    }
  })
  ;(window as any).__YU_CMS_DISPOSERS__ = []
  activeCmsScript?.remove()
  activeCmsScript = null
  document.querySelectorAll('script[data-yu-cms-page-script]').forEach(item => item.remove())
}

function stripScriptTags(value: string) {
  return String(value || '')
    .replace(/<script[^>]*>/gi, '')
    .replace(/<\/script>/gi, '')
    .trim()
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 10) : ''
}

</script>

<template>
  <main class="site-page" :class="`layout-${siteLayout.toLowerCase().replace('_', '-')}`">
    <div v-if="loading" class="site-state">
      加载中...
    </div>
    <div v-else-if="errorMessage" class="site-state">
      {{ errorMessage }}
    </div>

    <template v-else>
      <component :is="'style'">
        {{ siteRuntimeCss }}
      </component>
      <header data-yb-chrome="header" class="site-layout-header">
        <div class="site-layout-header__bar">
          <a data-yb-chrome-slot="logo" class="site-layout-header__brand" href="/site">
            <img v-if="renderContext.site.logo" :src="renderContext.site.logo" :alt="renderContext.site.name">
            <span>{{ renderContext.site.name }}</span>
          </a>
          <nav data-yb-chrome-slot="navigation" class="site-layout-header__nav">
            <div v-for="item in navigationTree" :key="item.id || item.url" class="site-nav-item" :class="{ 'has-children': item.children?.length }">
              <a :href="item.url">
                {{ item.label }}
                <span v-if="item.children?.length">⌄</span>
              </a>
              <div v-if="item.children?.length" class="site-nav-dropdown">
                <a v-for="child in item.children" :key="child.id || child.url" :href="child.url">{{ child.label }}</a>
              </div>
            </div>
          </nav>
          <div data-yb-chrome-slot="auth" class="site-layout-header__auth">
            <div v-if="!appAccountStore.isLogin" data-visible-when="guest">
              <a href="/login" class="ghost">登录</a>
              <a href="/register" class="primary">注册</a>
            </div>
            <details v-else data-visible-when="logged-in" class="site-layout-header__account">
              <summary class="ghost site-layout-header__action">
                <img v-if="appAccountStore.avatar" :src="appAccountStore.avatar" :alt="appAccountStore.account">
                <span>{{ appAccountStore.account }}</span>
                <i>⌄</i>
              </summary>
              <div>
                <a href="/">控制台</a>
                <a href="/profile">个人资料</a>
                <a href="/logout" class="danger">退出登录</a>
              </div>
            </details>
          </div>
        </div>
      </header>

      <div class="site-layout-frame">
        <aside v-if="siteLayout === 'ADMIN'" class="site-admin-sidebar">
          <strong>{{ renderContext.site.name }}</strong>
          <a href="/site">首页</a>
          <template v-for="item in navigationTree" :key="`side-${item.id || item.url}`">
            <a :href="item.url">{{ item.label }}</a>
            <a v-for="child in item.children" :key="`side-child-${child.id || child.url}`" class="child" :href="child.url">{{ child.label }}</a>
          </template>
        </aside>

        <div class="site-layout-content">
          <template v-if="!page && home">
            <div v-if="homeHtml" class="site-builder-home" v-html="renderDynamicHtml(homeHtml)" />
            <section v-if="!homeHtml" class="site-hero" :style="home.heroImageUrl ? { backgroundImage: `linear-gradient(90deg, rgba(15, 23, 42, 0.76), rgba(15, 23, 42, 0.2)), url(${home.heroImageUrl})` } : undefined">
              <div class="site-shell">
                <h1>{{ home.title }}</h1>
                <p>{{ home.subtitle }}</p>
              </div>
            </section>
            <section v-if="!homeHtml" class="site-shell site-sections">
              <article v-for="section in home.sections.filter(item => item.visible !== false)" :key="section.id || section.title" class="site-section" :class="`type-${section.type.toLowerCase()}`" :style="sectionStyle(section)">
                <div>
                  <span>{{ section.type }}</span>
                  <h2>{{ section.title }}</h2>
                  <p>{{ section.subtitle }}</p>
                  <a v-if="section.actionUrl" :href="section.actionUrl">{{ section.actionText || '了解更多' }}</a>
                </div>
              </article>
            </section>
          </template>

          <article v-if="page" class="site-article" :class="`template-${(page.template || 'DEFAULT').toLowerCase()}`">
            <component :is="'style'" v-if="page.cssContent">
              {{ page.cssContent }}
            </component>
            <header class="site-article__hero" :style="page.coverImageUrl ? { backgroundImage: `linear-gradient(90deg, rgba(15, 23, 42, 0.78), rgba(15, 23, 42, 0.16)), url(${page.coverImageUrl})` } : undefined">
              <div class="site-shell">
                <span>{{ page.slug }}</span>
                <h1>{{ page.title }}</h1>
                <p>{{ page.excerpt || page.summary }}</p>
                <div v-if="page.categories?.length || page.tags?.length" class="site-article__terms">
                  <span v-for="item in page.categories" :key="`cat-${item}`">{{ item }}</span>
                  <span v-for="item in page.tags" :key="`tag-${item}`">#{{ item }}</span>
                </div>
              </div>
            </header>
            <div class="site-shell site-article__body" v-html="page.htmlContent ? renderDynamicHtml(page.htmlContent) : renderCmsMarkdown(page.markdownContent)" />
          </article>
        </div>
      </div>

      <footer v-if="showFooter" data-yb-chrome="footer" class="site-layout-footer">
        <div class="site-shell">
          <div data-yb-chrome-slot="footer-brand">
            <strong>{{ footerTitle }}</strong>
            <p>{{ footerDescription }}</p>
            <small>{{ footerCopyright }}</small>
          </div>
          <nav data-yb-chrome-slot="footer-navigation">
            <a v-for="item in footerNavigationItems" :key="`foot-${item.id || item.url}`" :href="item.url">{{ item.label }}</a>
          </nav>
        </div>
      </footer>
      <footer v-else-if="showCopyright" data-yb-chrome="footer" class="site-layout-copyright">
        {{ footerCopyright }}
      </footer>
    </template>
  </main>
</template>

<style scoped>
@layer yudream-site-base {
.site-page {
  --yb-site-bg: #f8fafc;
  --yb-site-text: #111827;
  --yb-site-heading: #0f172a;
  --yb-site-muted: #64748b;
  --yb-site-caption: #94a3b8;
  --yb-site-nav-text: #475569;
  --yb-site-text-2: #334155;
  --yb-site-border: #e5e7eb;
  --yb-site-border-2: #e2e8f0;
  --yb-site-header-bg: rgba(255, 255, 255, 0.94);
  --yb-site-surface: #ffffff;
  --yb-site-hover: #f1f5f9;
  --yb-site-primary: #0f766e;
  --yb-site-primary-text: #ffffff;
  --yb-site-primary-btn-bg: #111827;
  --yb-site-primary-btn-text: #ffffff;
  --yb-site-hero-bg: linear-gradient(135deg, #0f766e, #1f2937);
  --yb-site-hero-text: #ffffff;
  --yb-site-danger: #b91c1c;

  min-height: 100vh;
  background: var(--yb-site-bg);
  color: var(--yb-site-text);
}

.site-page.dark,
.dark .site-page {
  --yb-site-bg: #0f172a;
  --yb-site-text: #e2e8f0;
  --yb-site-heading: #f8fafc;
  --yb-site-muted: #94a3b8;
  --yb-site-caption: #64748b;
  --yb-site-nav-text: #cbd5e1;
  --yb-site-text-2: #e2e8f0;
  --yb-site-border: #1e293b;
  --yb-site-border-2: #334155;
  --yb-site-header-bg: rgba(15, 23, 42, 0.86);
  --yb-site-surface: #1e293b;
  --yb-site-hover: #334155;
  --yb-site-primary: #2dd4bf;
  --yb-site-primary-text: #0f172a;
  --yb-site-primary-btn-bg: #2dd4bf;
  --yb-site-primary-btn-text: #0f172a;
  --yb-site-hero-bg: linear-gradient(135deg, #115e59, #111827);
  --yb-site-hero-text: #f8fafc;
}

.site-shell {
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
}

.site-state {
  display: grid;
  min-height: 100vh;
  place-items: center;
  color: var(--yb-site-muted);
}

.site-layout-header {
  position: sticky;
  top: 0;
  z-index: 1000;
  border-bottom: 1px solid var(--yb-site-border);
  background: var(--yb-site-header-bg);
  backdrop-filter: blur(12px);
  isolation: isolate;
}

.site-layout-header__bar {
  display: flex;
  width: min(1240px, calc(100% - 40px));
  min-height: 62px;
  margin: 0 auto;
  gap: 22px;
  align-items: center;
}

.site-layout-header__brand,
.site-layout-header__nav,
.site-layout-header__auth,
.site-layout-header__account summary {
  display: flex;
  align-items: center;
}

.site-layout-header__brand {
  min-width: 0;
  gap: 10px;
  color: var(--yb-site-heading);
  font-size: 18px;
  font-weight: 900;
  text-decoration: none;
}

.site-layout-header__brand img {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  object-fit: cover;
}

.site-layout-header__nav {
  flex: 1 1 auto;
  justify-content: flex-start;
  gap: 4px;
  min-width: 0;
}

.site-nav-item {
  position: relative;
}

.site-layout-header__nav a,
.site-nav-item > a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 9px;
  border-radius: 7px;
  color: var(--yb-site-nav-text);
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.site-layout-header__nav a:hover,
.site-nav-item:hover > a {
  background: var(--yb-site-hover);
  color: var(--yb-site-heading);
}

.site-nav-dropdown {
  position: absolute;
  top: 100%;
  left: -8px;
  z-index: 20;
  display: none;
  min-width: 168px;
  padding: 14px 8px 8px;
  border-radius: 10px;
  isolation: isolate;
}

.site-nav-dropdown::before {
  position: absolute;
  inset: 8px 0 0;
  z-index: -1;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
  content: "";
}

.site-nav-item:hover .site-nav-dropdown,
.site-nav-item:focus-within .site-nav-dropdown {
  display: grid;
  gap: 2px;
}

.site-nav-dropdown a {
  position: relative;
  display: flex;
  white-space: nowrap;
}

.site-layout-header__auth {
  gap: 8px;
}

.site-layout-header__auth > div[data-visible-when="guest"] {
  display: flex;
  gap: 8px;
}

.site-layout-header__auth a,
.site-layout-header__account summary {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 14px;
  font-weight: 750;
  line-height: 1;
  text-decoration: none;
}

.site-layout-header__auth a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
}

.site-layout-header__auth .ghost,
.site-layout-header__account summary {
  background: var(--yb-site-surface);
  color: var(--yb-site-text-2);
}

.site-layout-header__auth .ghost {
  border: 1px solid var(--yb-site-border-2);
}

.site-layout-header__auth .primary {
  background: var(--yb-site-primary-btn-bg);
  color: var(--yb-site-primary-btn-text);
}

.site-layout-header__account {
  position: relative;
}

.site-layout-header__account summary {
  gap: 7px;
  border: 0;
  list-style: none;
  cursor: pointer;
  outline: none;
  transition: background-color 0.18s ease, box-shadow 0.18s ease;
}

.site-layout-header__account summary::-webkit-details-marker {
  display: none;
}

.site-layout-header__account summary:hover,
.site-layout-header__account[open] summary {
  background: var(--yb-site-bg);
}

.site-layout-header__account summary:focus-visible {
  box-shadow: 0 0 0 3px rgba(148, 163, 184, 0.18);
}

.site-layout-header__account img {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
}

.site-layout-header__account i {
  color: var(--yb-site-caption);
  font-size: 12px;
  font-style: normal;
}

.site-layout-header__account > div {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  display: grid;
  min-width: 142px;
  padding: 7px;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
}

.site-layout-header__account a {
  padding: 9px 10px;
  border-radius: 8px;
  color: var(--yb-site-text-2);
  text-decoration: none;
}

.site-layout-header__account a:hover {
  background: var(--yb-site-hover);
}

.site-layout-header__account a.danger {
  color: var(--yb-site-danger);
}

.site-layout-frame {
  position: relative;
  z-index: 1;
  display: flex;
  min-height: calc(100vh - 63px);
}

.site-layout-content {
  flex: 1 1 auto;
  min-width: 0;
}

.site-admin-sidebar {
  position: sticky;
  top: 63px;
  display: grid;
  align-content: start;
  width: 240px;
  height: calc(100vh - 63px);
  padding: 18px 14px;
  border-right: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
  gap: 6px;
}

.site-admin-sidebar strong {
  padding: 10px 12px 14px;
  color: var(--yb-site-heading);
}

.site-admin-sidebar a {
  padding: 10px 12px;
  border-radius: 8px;
  color: var(--yb-site-nav-text);
  text-decoration: none;
}

.site-admin-sidebar a:hover {
  background: var(--yb-site-hover);
  color: var(--yb-site-heading);
}

.site-admin-sidebar a.child {
  margin-left: 12px;
  padding-left: 18px;
  color: var(--yb-site-muted);
  font-size: 13px;
}

.site-layout-footer {
  position: relative;
  z-index: 1;
  padding: 36px 0;
  border-top: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
  color: var(--yb-site-heading);
}

.site-layout-footer .site-shell {
  display: flex;
  gap: 18px;
  align-items: flex-start;
  justify-content: space-between;
}

.site-layout-footer strong {
  font-size: 20px;
}

.site-layout-footer p {
  margin: 8px 0 0;
  color: var(--yb-site-muted);
}

.site-layout-footer small {
  display: block;
  margin-top: 12px;
  color: var(--yb-site-caption);
}

.site-layout-footer nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.site-layout-footer a {
  color: var(--yb-site-nav-text);
  text-decoration: none;
}

.site-layout-footer a:hover {
  color: var(--yb-site-primary);
}

.site-layout-copyright {
  padding: 18px;
  border-top: 1px solid var(--yb-site-border);
  background: var(--yb-site-surface);
  color: var(--yb-site-muted);
  text-align: center;
}

.site-builder-home :deep(main) {
  min-height: initial;
}

.site-builder-home :deep(section:not([class])),
.site-builder-home :deep(article:not([class])) {
  margin-block: 0 1.25rem;
  padding-block: 0.75rem;
  padding-inline: 0;}

.site-builder-home :deep(:where(h1, h2, h3, h4, h5, h6)) {
  margin-block: 0 0.6em;
  line-height: 1.2;
}

.site-builder-home :deep(:where(p, ul, ol, blockquote)) {
  margin-block: 0 0.9em;
  line-height: 1.7;}

.site-builder-home :deep(:where(ul, ol)) {
  padding-inline-start: 1.4em;
}

.site-builder-home :deep(img) {
  max-width: 100%;
  height: auto;
}

.site-builder-home :deep(pre) {
  overflow: auto;
  max-width: 100%;
}

.site-builder-home :deep(iframe),
.site-builder-home :deep(video),
.site-builder-home :deep(embed) {
  max-width: 100%;
  height: auto;
}

.site-hero,
.site-article__hero {
  display: grid;
  min-height: 420px;
  align-items: end;
  padding: 72px 0;
  background: var(--yb-site-hero-bg);
  background-position: center;
  background-size: cover;
  color: var(--yb-site-hero-text);
}

.site-hero h1,
.site-article__hero h1 {
  max-width: 820px;
  margin: 0;
  font-size: clamp(42px, 8vw, 84px);
  line-height: 0.98;
  font-weight: 900;
}

.site-hero p,
.site-article__hero p {
  max-width: 680px;
  margin: 18px 0 0;
  color: rgba(255, 255, 255, 0.84);
  font-size: 18px;
}

.site-article__hero span {
  display: block;
  margin-bottom: 16px;
  color: rgba(255, 255, 255, 0.72);
}

.site-article__terms {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 18px;
}

.site-article__terms span {
  display: inline-flex;
  margin: 0;
  padding: 6px 10px;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.88);
  font-size: 13px;
}

.site-sections {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  padding: 28px 0 56px;
}

.site-section {
  display: grid;
  min-height: 240px;
  align-items: end;
  padding: 28px;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
  background-position: center;
  background-size: cover;
}

.site-section span {
  color: var(--yb-site-primary);
  font-weight: 700;
}

.site-section h2 {
  margin: 10px 0 8px;
  font-size: 28px;
}

.site-section p {
  margin: 0;
  color: var(--yb-site-muted);
}

.site-section a {
  display: inline-flex;
  margin-top: 16px;
  color: var(--yb-site-primary);
  font-weight: 700;
}

.site-article__body {
  max-width: 860px;
  padding: 48px 0 72px;
  color: var(--yb-site-text);
  font-size: 17px;
  line-height: 1.8;
}

.site-article__body :deep(main) {
  display: grid;
  gap: 20px;
}

.site-article__body :deep(:where(h1, h2, h3, h4, h5, h6)) {
  margin-block: 0 0.55em;
  line-height: 1.25;
}

.site-article__body :deep(:where(p, ul, ol, blockquote)) {
  margin-block: 0 0.85em;
  line-height: 1.8;
}

.site-article__body :deep(:where(ul, ol)) {
  padding-inline-start: 1.5em;
}

.site-article__body :deep(img),
.site-article__body :deep(iframe),
.site-article__body :deep(video),
.site-article__body :deep(embed) {
  max-width: 100%;
  height: auto;
}

.site-article__body :deep(pre) {
  overflow: auto;
  max-width: 100%;
}
.site-article__body :deep(.yb-cta) {
  padding: 34px;
  border-radius: 8px;
  background: var(--yb-site-primary);
  background-position: center;
  background-size: cover;
  color: var(--yb-site-hero-text);
}

.site-article__body :deep(.yb-hero h1) {
  max-width: 720px;
  margin: 0 0 12px;
  font-size: clamp(36px, 6vw, 68px);
  line-height: 1;
}

.site-article__body :deep(.yb-hero p),
.site-article__body :deep(.yb-cta p) {
  max-width: 640px;
  margin: 0;
  color: rgba(255, 255, 255, 0.84);
}

.site-article__body :deep(.yb-hero a),
.site-article__body :deep(.yb-cta a) {
  display: inline-flex;
  margin-top: 22px;
  padding: 10px 14px;
  border-radius: 6px;
  background: var(--yb-site-surface);
  color: var(--yb-site-primary);
  font-weight: 800;
  text-decoration: none;
}

.site-article__body :deep(.yb-text),
.site-article__body :deep(.yb-image) {
  padding: 22px;
  border: 1px solid var(--yb-site-border);
  border-radius: 8px;
  background: var(--yb-site-surface);
}

.site-article__body :deep(.yb-image figcaption) {
  display: grid;
  gap: 4px;
  margin-top: 10px;
}

.site-article__body :deep(.yb-image figcaption span) {
  color: var(--yb-site-muted);
}

.site-article__body :deep(h1),
.site-article__body :deep(h2),
.site-article__body :deep(h3) {
  line-height: 1.2;
}

.site-article__body :deep(img) {
  max-width: 100%;
  border-radius: 8px;
}

@media (max-width: 760px) {
  .site-layout-header {
    position: sticky;
    top: 0;
    z-index: 1000;
  }

  .site-layout-header__bar {
    align-items: stretch;
    flex-direction: column;
    width: calc(100% - 28px);
    min-height: 0;
    padding: 12px 0;
    gap: 10px;
  }

  .site-layout-header__nav {
    justify-content: flex-start;
    flex-wrap: wrap;
  }

  .site-layout-frame {
    display: block;
  }

  .site-admin-sidebar {
    position: static;
    width: auto;
    height: auto;
    border-right: 0;
    border-bottom: 1px solid var(--yb-site-border);
  }

  .site-layout-footer .site-shell {
    flex-direction: column;
  }

  .site-layout-footer nav {
    justify-content: flex-start;
  }

  .site-hero,
  .site-article__hero {
    min-height: 340px;
    padding: 48px 0;
  }

  .site-sections {
    grid-template-columns: 1fr;
  }

  .site-shell {
    width: calc(100% - 24px);
  }

  .site-builder-home :deep(section:not([class])),
  .site-builder-home :deep(article:not([class])) {
    padding-inline: 0.75rem;
  }

  .site-builder-home :deep(iframe),
  .site-builder-home :deep(video),
  .site-builder-home :deep(embed),
  .site-builder-home :deep(img),
  .site-builder-home :deep(table) {
    max-width: 100%;
    height: auto;
  }

  .site-builder-home :deep(pre) {
    white-space: pre-wrap;
    word-break: break-word;
  }

  .site-article__body :deep(iframe),
  .site-article__body :deep(video),
  .site-article__body :deep(embed),
  .site-article__body :deep(img),
  .site-article__body :deep(table) {
    max-width: 100%;
    height: auto;
  }

  .site-article__body :deep(pre) {
    white-space: pre-wrap;
    word-break: break-word;
  }
}
}
</style>
