<script setup lang="ts">
import type { CmsPage, HomePageLayout, HomeSection } from '@/api/modules/platform-cms'
import apiCms from '@/api/modules/platform-cms'

const route = useRoute()
const appAccountStore = useAppAccountStore()
const appSettingsStore = useAppSettingsStore()

const loading = ref(false)
const home = ref<HomePageLayout | null>(null)
const page = ref<CmsPage | null>(null)
const publishedPages = ref<CmsPage[]>([])
const errorMessage = ref('')
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
const homeCss = computed(() => home.value?.settings?.homeCss || '')
const navigationItems = computed(() => parseNavigationItems(home.value?.settings?.navigationJson).filter(item => !isAuthNavigationUrl(item.url)))
const navigationTree = computed(() => buildNavigationTree(navigationItems.value))
const footerNavigationItems = computed(() => flattenNavigation(navigationTree.value))
const footerTitle = computed(() => home.value?.settings?.footerTitle || renderContext.value.site.name)
const footerDescription = computed(() => home.value?.settings?.footerDescription || renderContext.value.site.description || '由 YuDream CMS 驱动的内容站点')
const footerCopyright = computed(() => home.value?.settings?.footerCopyright || `© ${new Date().getFullYear()} ${renderContext.value.site.name}. All rights reserved.`)
const siteLayout = computed<SiteLayoutMode>(() => (home.value?.settings?.siteLayout as SiteLayoutMode) || 'HEADER_FOOTER')
const showFooter = computed(() => siteLayout.value === 'HEADER_FOOTER')
const showCopyright = computed(() => siteLayout.value === 'HEADER_COPYRIGHT' || siteLayout.value === 'ADMIN')
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
  return {
    site: {
      name: appSettingsStore.siteName || 'YuDream',
      description: home.value?.subtitle || page.value?.summary || '',
      logo: appSettingsStore.logo || '',
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

async function load() {
  loading.value = true
  errorMessage.value = ''
  home.value = null
  page.value = null
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
      document.title = `${res.data.seoTitle || res.data.title} - YuDream`
    }
    else {
      const res = await apiCms.publicHome()
      home.value = res.data
      document.title = `${res.data.title || '站点首页'} - YuDream`
    }
    await loadPublicPages()
  }
  catch (error: any) {
    errorMessage.value = error?.response?.data?.message || '页面暂不可访问'
  }
  finally {
    loading.value = false
  }
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
  const sanitized = sanitizeHtml(value)
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
    const rows = key === 'navUsers'
      ? renderContext.value.navUsers
      : key === 'navigation'
        ? renderContext.value.navigation
        : key === 'pages'
          ? renderContext.value.pages
          : key === 'categories'
            ? renderContext.value.categories
            : key === 'tags'
              ? renderContext.value.tags
              : []
    const template = el.innerHTML
    el.innerHTML = rows.map((item, index) => renderVariables(template, { item, index: String(index + 1) })).join('')
  })
  return renderVariables(doc.body.firstElementChild?.innerHTML || sanitized)
}

function sanitizeHtml(value?: string) {
  return (value || '')
    .replace(/<script[\s\S]*?>[\s\S]*?<\/script>/gi, '')
    .replace(/\son\w+="[^"]*"/gi, '')
    .replace(/\son\w+='[^']*'/gi, '')
    .replace(/javascript:/gi, '')
}

function renderVariables(value: string, localContext: Record<string, any> = {}) {
  return value.replace(/\{\{\s*([\w.]+)\s*}}/g, (_, path: string) => escapeHtml(String(resolvePath(path, localContext) ?? '')))
}

function resolvePath(path: string, localContext: Record<string, any>) {
  const root = { ...renderContext.value, ...localContext }
  return path.split('.').reduce<any>((target, key) => {
    if (Array.isArray(target) && key === 'count') {
      return target.length
    }
    return target?.[key]
  }, root)
}

function markdownPreview(markdown?: string) {
  const lines = escapeHtml(markdown || '').split(/\r?\n/)
  const html: string[] = []
  let inList = false
  for (const line of lines) {
    const listMatch = line.match(/^\s*[-*]\s+(.+)$/)
    if (listMatch) {
      if (!inList) {
        html.push('<ul>')
        inList = true
      }
      html.push(`<li>${inlineMarkdown(listMatch[1])}</li>`)
      continue
    }
    if (inList) {
      html.push('</ul>')
      inList = false
    }
    if (line.startsWith('### ')) {
      html.push(`<h3>${inlineMarkdown(line.slice(4))}</h3>`)
    }
    else if (line.startsWith('## ')) {
      html.push(`<h2>${inlineMarkdown(line.slice(3))}</h2>`)
    }
    else if (line.startsWith('# ')) {
      html.push(`<h1>${inlineMarkdown(line.slice(2))}</h1>`)
    }
    else if (line.trim()) {
      html.push(`<p>${inlineMarkdown(line)}</p>`)
    }
  }
  if (inList) {
    html.push('</ul>')
  }
  return html.join('')
}

function inlineMarkdown(value: string) {
  return value
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/\[([^\]]+)]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
}

function dateText(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 10) : ''
}

function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
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
      <header class="site-layout-header">
        <div class="site-layout-header__bar">
          <a class="site-layout-header__brand" href="/site">
            <img v-if="renderContext.site.logo" :src="renderContext.site.logo" :alt="renderContext.site.name">
            <span>{{ renderContext.site.name }}</span>
          </a>
          <nav class="site-layout-header__nav">
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
          <div v-if="!appAccountStore.isLogin" class="site-layout-header__auth">
            <a href="/login" class="ghost">登录</a>
            <a href="/register" class="primary">注册</a>
          </div>
          <details v-else class="site-layout-header__account">
            <summary>
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
            <component :is="'style'" v-if="homeCss">
              {{ homeCss }}
            </component>
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
            <div class="site-shell site-article__body" v-html="page.htmlContent ? renderDynamicHtml(page.htmlContent) : markdownPreview(page.markdownContent)" />
          </article>
        </div>
      </div>

      <footer v-if="showFooter" class="site-layout-footer">
        <div class="site-shell">
          <div>
            <strong>{{ footerTitle }}</strong>
            <p>{{ footerDescription }}</p>
            <small>{{ footerCopyright }}</small>
          </div>
          <nav>
            <a v-for="item in footerNavigationItems" :key="`foot-${item.id || item.url}`" :href="item.url">{{ item.label }}</a>
          </nav>
        </div>
      </footer>
      <footer v-else-if="showCopyright" class="site-layout-copyright">
        {{ footerCopyright }}
      </footer>
    </template>
  </main>
</template>

<style scoped>
.site-page {
  min-height: 100vh;
  background: #f8fafc;
  color: #111827;
}

.site-shell {
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
}

.site-state {
  display: grid;
  min-height: 100vh;
  place-items: center;
  color: #64748b;
}

.site-layout-header {
  position: sticky;
  top: 0;
  z-index: 30;
  border-bottom: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(12px);
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
  color: #0f172a;
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
  color: #475569;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.site-layout-header__nav a:hover,
.site-nav-item:hover > a {
  background: #f1f5f9;
  color: #0f172a;
}

.site-nav-dropdown {
  position: absolute;
  top: calc(100% + 8px);
  left: 0;
  z-index: 20;
  display: none;
  min-width: 168px;
  padding: 6px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.12);
}

.site-nav-item:hover .site-nav-dropdown,
.site-nav-item:focus-within .site-nav-dropdown {
  display: grid;
  gap: 2px;
}

.site-nav-dropdown a {
  display: flex;
  white-space: nowrap;
}

.site-layout-header__auth {
  gap: 8px;
}

.site-layout-header__auth a,
.site-layout-header__account summary {
  min-height: 34px;
  padding: 0 12px;
  border-radius: 7px;
  font-size: 14px;
  font-weight: 750;
  text-decoration: none;
}

.site-layout-header__auth .ghost,
.site-layout-header__account summary {
  background: #fff;
  color: #334155;
}

.site-layout-header__auth .ghost {
  border: 1px solid #e2e8f0;
}

.site-layout-header__auth .primary {
  background: #111827;
  color: #fff;
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
  background: #f8fafc;
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
  color: #94a3b8;
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
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 16px 36px rgba(15, 23, 42, 0.14);
}

.site-layout-header__account a {
  padding: 9px 10px;
  border-radius: 8px;
  color: #334155;
  text-decoration: none;
}

.site-layout-header__account a:hover {
  background: #f1f5f9;
}

.site-layout-header__account a.danger {
  color: #b91c1c;
}

.site-layout-frame {
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
  border-right: 1px solid #e5e7eb;
  background: #fff;
  gap: 6px;
}

.site-admin-sidebar strong {
  padding: 10px 12px 14px;
  color: #0f172a;
}

.site-admin-sidebar a {
  padding: 10px 12px;
  border-radius: 8px;
  color: #475569;
  text-decoration: none;
}

.site-admin-sidebar a:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.site-admin-sidebar a.child {
  margin-left: 12px;
  padding-left: 18px;
  color: #64748b;
  font-size: 13px;
}

.site-layout-footer {
  padding: 36px 0;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  color: #0f172a;
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
  color: #64748b;
}

.site-layout-footer small {
  display: block;
  margin-top: 12px;
  color: #94a3b8;
}

.site-layout-footer nav {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.site-layout-footer a {
  color: #475569;
  text-decoration: none;
}

.site-layout-footer a:hover {
  color: #0f766e;
}

.site-layout-copyright {
  padding: 18px;
  border-top: 1px solid #e5e7eb;
  background: #fff;
  color: #64748b;
  text-align: center;
}

.site-builder-home :deep(main) {
  min-height: initial;
}

.site-builder-home :deep(section) {
  min-height: initial;
}

.site-builder-home :deep(img) {
  max-width: 100%;
}

.site-hero,
.site-article__hero {
  display: grid;
  min-height: 420px;
  align-items: end;
  padding: 72px 0;
  background: linear-gradient(135deg, #0f766e, #1f2937);
  background-position: center;
  background-size: cover;
  color: #fff;
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
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  background-position: center;
  background-size: cover;
}

.site-section span {
  color: #0f766e;
  font-weight: 700;
}

.site-section h2 {
  margin: 10px 0 8px;
  font-size: 28px;
}

.site-section p {
  margin: 0;
  color: #64748b;
}

.site-section a {
  display: inline-flex;
  margin-top: 16px;
  color: #0f766e;
  font-weight: 700;
}

.site-article__body {
  max-width: 860px;
  padding: 48px 0 72px;
  color: #1f2937;
  font-size: 17px;
  line-height: 1.8;
}

.site-article__body :deep(main) {
  display: grid;
  gap: 20px;
}

.site-article__body :deep(.yb-hero),
.site-article__body :deep(.yb-cta) {
  padding: 34px;
  border-radius: 8px;
  background: #0f766e;
  background-position: center;
  background-size: cover;
  color: #fff;
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
  background: #fff;
  color: #0f766e;
  font-weight: 800;
  text-decoration: none;
}

.site-article__body :deep(.yb-text),
.site-article__body :deep(.yb-image) {
  padding: 22px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
}

.site-article__body :deep(.yb-image figcaption) {
  display: grid;
  gap: 4px;
  margin-top: 10px;
}

.site-article__body :deep(.yb-image figcaption span) {
  color: #64748b;
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
    position: static;
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
    border-bottom: 1px solid #e5e7eb;
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
}
</style>
