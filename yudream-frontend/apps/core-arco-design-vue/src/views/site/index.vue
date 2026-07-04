<script setup lang="ts">
import type { CmsPage, HomePageLayout, HomeSection } from '@/api/modules/platform-cms'
import apiCms from '@/api/modules/platform-cms'

const route = useRoute()
const appAccountStore = useAppAccountStore()
const appSettingsStore = useAppSettingsStore()

const loading = ref(false)
const home = ref<HomePageLayout | null>(null)
const page = ref<CmsPage | null>(null)
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
const navigationItems = computed(() => parseNavigationItems(home.value?.settings?.navigationJson))
const renderContext = computed(() => {
  const loggedIn = appAccountStore.isLogin
  const username = appAccountStore.account || ''
  const nickname = username || '访客'
  const avatar = appAccountStore.avatar || ''
  const navUsers = loggedIn
    ? [{ name: nickname, avatar, url: '/' }]
    : [{ name: '登录', avatar: appSettingsStore.logo, url: '/login' }]
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
  }
  catch (error: any) {
    errorMessage.value = error?.response?.data?.message || '页面暂不可访问'
  }
  finally {
    loading.value = false
  }
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

function renderDynamicHtml(value?: string) {
  if (!value) {
    return ''
  }
  const sanitized = sanitizeHtml(value)
  const doc = new DOMParser().parseFromString(`<div>${sanitized}</div>`, 'text/html')
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

    <header v-if="!loading && !errorMessage && navigationItems.length" class="site-nav">
      <div class="site-shell">
        <a class="site-nav__brand" href="/site">{{ renderContext.site.name }}</a>
        <nav>
          <a v-for="item in navigationItems" :key="item.id || item.url" :href="item.url">{{ item.label }}</a>
        </nav>
        <img v-if="appAccountStore.avatar" :src="appAccountStore.avatar" :alt="appAccountStore.account" class="site-nav__avatar">
      </div>
    </header>

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

.site-nav {
  position: sticky;
  top: 0;
  z-index: 20;
  border-bottom: 1px solid #e5e7eb;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(14px);
}

.site-nav .site-shell {
  display: flex;
  min-height: 64px;
  gap: 18px;
  align-items: center;
}

.site-nav__brand {
  color: #0f172a;
  font-weight: 900;
  text-decoration: none;
}

.site-nav nav {
  display: flex;
  gap: 8px;
  align-items: center;
  flex: 1 1 auto;
  flex-wrap: wrap;
}

.site-nav nav a {
  padding: 8px 10px;
  border-radius: 999px;
  color: #475569;
  text-decoration: none;
}

.site-nav nav a:hover {
  background: #f1f5f9;
  color: #0f172a;
}

.site-nav__avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  object-fit: cover;
}

.site-builder-home :deep(#pagebuilder),
.site-builder-home :deep(.pagebuilder) {
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

.site-article__body :deep(#pagebuilder),
.site-article__body :deep(.pagebuilder) {
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
