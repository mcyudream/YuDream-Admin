<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import apiCms from '@/api/modules/platform-cms'
import { fetchPublicWikiSpaces, type WikiPublicSpace } from '@/api/modules/platform-wiki'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'

const router = useRouter()
const appSettingsStore = useAppSettingsStore()
const loading = ref(false)
const spaces = ref<WikiPublicSpace[]>([])
const recentPages = ref<Array<{ title: string, summary: string, url: string, spaceName: string }>>([])
const publicPageCount = ref(0)
const query = ref('')
const siteName = computed(() => appSettingsStore.siteName || '')

async function load() {
  loading.value = true
  try {
    spaces.value = (await fetchPublicWikiSpaces()).data
    try {
      const context = (await apiCms.publicTemplateContext({ knowledgeLatestLimit: 6 })).data
      publicPageCount.value = context.knowledge.pages.length
      recentPages.value = context.knowledge.latest.slice(0, 6).map(item => ({
        title: item.title,
        summary: item.summary || item.excerpt || '查看已发布文档',
        url: item.url,
        spaceName: spaces.value.find(space => space.slug === item.spaceSlug)?.name || item.spaceSlug || '知识库',
      }))
    }
    catch {
      publicPageCount.value = 0
      recentPages.value = []
    }
    applyPublicSeo({
      title: '公开知识库',
      description: '浏览已发布文档，或直接检索全部知识库内容。',
      canonicalPath: '/wiki',
      type: 'website',
      siteName: siteName.value ? `${siteName.value} Wiki` : '公开知识库',
    })
  }
  finally {
    loading.value = false
  }
}

function search() {
  const keyword = query.value.trim()
  if (!keyword) return
  void router.push({ name: 'publicWikiSearch', query: { q: keyword } })
}

onMounted(load)

onBeforeUnmount(clearPublicSeo)
</script>

<template>
  <main class="wiki-home">
    <header class="wiki-home__header">
      <a href="/" class="wiki-home__brand"><FaIcon name="i-ri:book-2-line" /> {{ siteName || '知识库' }}</a>
      <a href="/login">登录</a>
    </header>
    <section class="wiki-home__content">
      <div class="wiki-home__intro">
        <span>公开文档门户</span>
        <div class="wiki-home__intro-row">
          <div><h1>知识库</h1><p>浏览已发布文档，或直接检索全部知识库内容。</p></div>
          <dl><div><dt>{{ spaces.length }}</dt><dd>知识空间</dd></div><div><dt>{{ publicPageCount }}</dt><dd>公开页面</dd></div></dl>
        </div>
      </div>
      <form class="wiki-home__search" @submit.prevent="search"><FaInput v-model="query" clearable placeholder="输入问题或关键词，回车搜索"><template #start><FaIcon name="i-ri:search-line" /></template></FaInput><FaButton html-type="submit"><FaIcon name="i-ri:search-line" /> 搜索</FaButton></form>
      <div class="wiki-home__section-head"><div><h2>知识空间</h2><p>按主题浏览公开文档。</p></div></div>
      <section class="wiki-home__spaces">
        <FaCard v-for="space in spaces" :key="space.slug" class="wiki-home__space" content-class="wiki-home__space-content" @click="router.push(`/wiki/${encodeURIComponent(space.slug)}`)"><FaIcon name="i-ri:book-open-line" /><strong>{{ space.name }}</strong><p>{{ space.description || '查看已发布文档' }}</p><span>进入知识库 <FaIcon name="i-ri:arrow-right-line" /></span></FaCard>
        <a-empty v-if="!loading && !spaces.length" description="暂无公开知识库" />
      </section>
      <section v-if="recentPages.length" class="wiki-home__recent">
        <div class="wiki-home__section-head"><div><h2>最近内容</h2><p>从公开知识空间快速进入页面。</p></div><a href="/wiki/search">搜索全部</a></div>
        <div class="wiki-home__recent-list">
          <a v-for="page in recentPages" :key="page.url" :href="page.url"><span><small>{{ page.spaceName }}</small><strong>{{ page.title }}</strong><p>{{ page.summary }}</p></span><FaIcon name="i-ri:arrow-right-up-line" /></a>
        </div>
      </section>
    </section>
  </main>
</template>

<style scoped>
.wiki-home { min-height: 100vh; background: var(--color-bg-1); color: var(--color-text-1); }
.wiki-home__header { display: flex; min-height: 62px; padding: 0 max(24px, calc((100% - 1200px) / 2)); border-bottom: 1px solid var(--color-border-2); align-items: center; justify-content: space-between; background: var(--color-bg-1); }
.wiki-home__header a { color: var(--color-text-2); font-size: 14px; text-decoration: none; }
.wiki-home__brand { display: inline-flex; gap: 8px; align-items: center; color: var(--color-text-1) !important; font-weight: 700; }
.wiki-home__content { width: min(1080px, calc(100% - 40px)); margin: 0 auto; padding: 72px 0; }
.wiki-home__intro > span { color: var(--color-text-3); font-size: 13px; font-weight: 700; }
.wiki-home__intro-row { display: flex; gap: 28px; align-items: end; justify-content: space-between; margin-top: 10px; }
.wiki-home__intro h1 { margin: 0 0 8px; font-size: 34px; }
.wiki-home__intro p { margin: 0; color: var(--color-text-3); }
.wiki-home__intro dl { display: flex; gap: 10px; margin: 0; }
.wiki-home__intro dl div { min-width: 100px; padding: 10px 12px; border-left: 1px solid var(--color-border-2); }
.wiki-home__intro dt { color: var(--color-text-1); font-size: 22px; font-weight: 700; }
.wiki-home__intro dd { margin: 2px 0 0; color: var(--color-text-3); font-size: 12px; }
.wiki-home__search { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; width: min(100%, 720px); margin: 34px 0 42px; }
.wiki-home__spaces { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.wiki-home__space { min-height: 185px; cursor: pointer; transition: box-shadow .15s; }
.wiki-home__space:hover { box-shadow: 0 6px 18px rgb(0 0 0 / 7%); }
.wiki-home__space-content { display: flex; min-height: 185px; flex-direction: column; align-items: flex-start; gap: 10px; }
.wiki-home__space-content > :deep(svg) { font-size: 22px; }
.wiki-home__space-content p { margin: 0; color: var(--color-text-3); font-size: 14px; line-height: 1.6; }
.wiki-home__space-content span { display: inline-flex; align-items: center; gap: 4px; margin-top: auto; color: var(--color-text-2); font-size: 13px; font-weight: 600; }
.wiki-home__section-head { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin: 34px 0 14px; }
.wiki-home__section-head h2, .wiki-home__section-head p { margin: 0; }
.wiki-home__section-head h2 { font-size: 20px; }
.wiki-home__section-head p { margin-top: 4px; color: var(--color-text-3); font-size: 13px; }
.wiki-home__section-head > a { color: var(--color-text-2); font-size: 13px; text-decoration: none; }
.wiki-home__recent { margin-top: 44px; }
.wiki-home__recent-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); border-top: 1px solid var(--color-border-2); }
.wiki-home__recent-list > a { display: flex; min-width: 0; gap: 14px; align-items: center; justify-content: space-between; padding: 18px 10px 18px 0; border-bottom: 1px solid var(--color-border-2); color: var(--color-text-1); text-decoration: none; }
.wiki-home__recent-list > a:nth-child(odd) { margin-right: 18px; }
.wiki-home__recent-list > a:nth-child(even) { padding-left: 18px; border-left: 1px solid var(--color-border-2); }
.wiki-home__recent-list span { display: grid; min-width: 0; gap: 5px; }
.wiki-home__recent-list small { color: var(--color-text-3); }
.wiki-home__recent-list strong, .wiki-home__recent-list p { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wiki-home__recent-list p { margin: 0; color: var(--color-text-2); font-size: 13px; }
.wiki-home__recent-list > a > :deep(svg) { flex-shrink: 0; color: var(--color-text-3); }
@media (max-width: 860px) { .wiki-home__spaces, .wiki-home__recent-list { grid-template-columns: 1fr; }.wiki-home__recent-list > a:nth-child(odd) { margin-right: 0; }.wiki-home__recent-list > a:nth-child(even) { padding-left: 0; border-left: 0; } }
@media (max-width: 600px) { .wiki-home__header { padding: 0 14px; }.wiki-home__content { width: min(100% - 28px, 1080px); padding: 44px 0; }.wiki-home__intro-row { align-items: stretch; flex-direction: column; }.wiki-home__intro h1 { font-size: 28px; }.wiki-home__intro dl { width: 100%; }.wiki-home__intro dl div { min-width: 0; flex: 1; }.wiki-home__search { grid-template-columns: 1fr; }.wiki-home__search :deep(button) { width: 100%; }.wiki-home__recent-list strong, .wiki-home__recent-list p { white-space: normal; overflow-wrap: anywhere; } }
</style>
