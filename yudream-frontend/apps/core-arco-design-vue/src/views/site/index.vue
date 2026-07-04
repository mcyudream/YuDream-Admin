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
  visible?: boolean
  sort?: number
}

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
  normalizeSystemNavigation(doc)
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

function normalizeSystemNavigation(doc: Document) {
  doc.querySelectorAll('[data-yb-system-nav]').forEach((nav) => {
    nav.querySelectorAll('[data-visible-when="guest"], [data-visible-when="logged-in"]').forEach(el => el.remove())
    nav.insertAdjacentHTML('beforeend', `
      <div data-visible-when="guest" style="display:flex; align-items:center; gap:8px;">
        <a href="/login" style="padding:8px 14px; border-radius:8px; color:#475569; font-weight:700; text-decoration:none;">登录</a>
        <a href="/register" style="padding:8px 14px; border-radius:8px; background:#0f766e; color:#ffffff; font-weight:800; text-decoration:none;">注册</a>
      </div>
      <details data-visible-when="logged-in" style="position:relative;">
        <summary style="display:flex; align-items:center; gap:10px; list-style:none; cursor:pointer;">
          <img src="{{user.avatar}}" alt="{{user.nickname}}" style="width:34px; height:34px; border-radius:50%; object-fit:cover; background:#e2e8f0;">
          <span style="color:#0f172a; font-weight:800;">{{user.nickname}}</span>
          <span style="color:#94a3b8;">⌄</span>
        </summary>
        <div style="position:absolute; top:calc(100% + 8px); right:0; z-index:20; display:grid; min-width:132px; padding:6px; border:1px solid #e5e7eb; border-radius:10px; background:#ffffff; box-shadow:0 14px 32px rgba(15,23,42,.12);">
          <a href="/" style="padding:9px 10px; border-radius:8px; color:#334155; text-decoration:none;">控制台</a>
          <a href="/profile" style="padding:9px 10px; border-radius:8px; color:#334155; text-decoration:none;">个人资料</a>
          <a href="/logout" style="padding:9px 10px; border-radius:8px; color:#b91c1c; text-decoration:none;">退出登录</a>
        </div>
      </details>
    `)
  })
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
  <main class="site-page">
    <div v-if="loading" class="site-state">
      加载中...
    </div>
    <div v-else-if="errorMessage" class="site-state">
      {{ errorMessage }}
    </div>

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

.site-builder-home :deep(main),
.site-builder-home :deep(section) {
  min-height: 100vh;
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
