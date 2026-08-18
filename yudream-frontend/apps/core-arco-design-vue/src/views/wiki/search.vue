<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchPublicWikiSpaces, searchAllPublicWiki, type WikiPublicSpace, type WikiSearchHit } from '@/api/modules/platform-wiki'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'

const route = useRoute()
const router = useRouter()
const query = ref('')
const selectedSpace = ref('')
const spaces = ref<WikiPublicSpace[]>([])
const hits = ref<WikiSearchHit[]>([])
const loading = ref(false)
const searched = ref(false)
const error = ref('')
let searchRequestId = 0

const searchTitle = computed(() => query.value.trim() ? `“${query.value.trim()}”的搜索结果` : '搜索知识库')

function syncFromRoute() {
  query.value = typeof route.query.q === 'string' ? route.query.q : ''
  selectedSpace.value = typeof route.query.space === 'string' ? route.query.space : ''
}

async function loadSpaces() {
  spaces.value = (await fetchPublicWikiSpaces()).data
}

async function search() {
  const requestId = ++searchRequestId
  const keyword = query.value.trim()
  const spaceSlug = selectedSpace.value || undefined
  if (!keyword) {
    hits.value = []
    searched.value = false
    return
  }
  loading.value = true
  searched.value = true
  error.value = ''
  try {
    const nextHits = (await searchAllPublicWiki({ query: keyword, spaceSlug })).data.slice(0, 12)
    if (requestId === searchRequestId) hits.value = nextHits
  }
  catch (exception: any) {
    if (requestId !== searchRequestId) return
    hits.value = []
    error.value = exception?.message || '搜索暂时不可用，请稍后重试'
  }
  finally {
    if (requestId === searchRequestId) loading.value = false
  }
}

function submitSearch() {
  const keyword = query.value.trim()
  if (!keyword) return
  void router.replace({
    name: 'publicWikiSearch',
    query: { q: keyword, ...(selectedSpace.value ? { space: selectedSpace.value } : {}) },
  })
}

function hitLink(hit: WikiSearchHit) {
  if (hit.sourceUrl?.startsWith('/')) return hit.sourceUrl
  const slug = hit.spaceSlug || selectedSpace.value
  return slug ? `/wiki/${encodeURIComponent(slug)}/${encodeURI(hit.path.replace(/^\/+/, ''))}` : '/wiki'
}

onMounted(async () => {
  syncFromRoute()
  await loadSpaces()
  await search()
  applySearchSeo()
})

onBeforeUnmount(clearPublicSeo)

watch(() => route.fullPath, () => {
  syncFromRoute()
  applySearchSeo()
  void search()
})

function applySearchSeo() {
  const siteName = useAppSettingsStore().siteName
  applyPublicSeo({
    title: query.value.trim() ? `${query.value.trim()}的知识库搜索结果` : '搜索知识库',
    description: siteName ? `搜索 ${siteName} 公开知识库中的已发布页面。` : '搜索公开知识库中的已发布页面。',
    canonicalPath: route.fullPath,
    type: 'website',
    siteName: siteName ? `${siteName} Wiki` : '公开知识库',
  })
}
</script>

<template>
  <main class="wiki-search-page">
    <header class="wiki-search-page__header">
      <div class="wiki-search-page__shell wiki-search-page__header-inner">
        <a class="wiki-search-page__brand" href="/wiki"><FaIcon name="i-ri:book-2-line" /> 知识库</a>
        <a class="wiki-search-page__back" href="/wiki"><FaIcon name="i-ri:arrow-left-line" /> 返回知识库</a>
      </div>
    </header>

    <section class="wiki-search-page__shell wiki-search-page__content">
      <div class="wiki-search-page__heading"><p>公开文档</p><h1>{{ searchTitle }}</h1></div>
      <form class="wiki-search-page__form" @submit.prevent="submitSearch">
        <FaInput v-model="query" clearable placeholder="输入问题或关键词"><template #start><FaIcon name="i-ri:search-line" /></template></FaInput>
        <FaSelect v-model="selectedSpace" clearable placeholder="全部知识库" :options="spaces.map(space => ({ label: space.name, value: space.slug }))" />
        <FaButton html-type="submit" :loading="loading"><FaIcon name="i-ri:search-line" /> 搜索</FaButton>
      </form>

      <p v-if="searched && !loading && !error" class="wiki-search-page__count">找到 {{ hits.length }} 条相关页面</p>
      <section v-if="loading" class="wiki-search-page__empty"><FaIcon name="i-ri:loader-4-line" class="wiki-search-page__loading-icon" /><strong>正在搜索</strong></section>
      <section v-else-if="error" class="wiki-search-page__empty"><FaIcon name="i-ri:error-warning-line" /><strong>无法完成搜索</strong><p>{{ error }}</p></section>
      <section v-else-if="searched && !hits.length" class="wiki-search-page__empty"><FaIcon name="i-ri:file-search-line" /><strong>没有找到相关页面</strong><p>换一个关键词，或尝试在全部知识库中搜索。</p></section>
      <section v-else class="wiki-search-page__results">
        <FaCard v-for="hit in hits" :key="`${hit.spaceSlug}-${hit.nodeId || hit.path}`" class="wiki-search-result" @click="router.push(hitLink(hit))">
          <div class="wiki-search-result__heading"><strong>{{ hit.title }}</strong><FaIcon name="i-ri:arrow-right-up-line" /></div>
          <div class="wiki-search-result__location"><FaIcon name="i-ri:book-open-line" /> {{ hit.spaceName || '知识库' }}<span>/</span>{{ hit.path }}</div>
          <p>{{ hit.content }}</p>
        </FaCard>
      </section>
    </section>
  </main>
</template>

<style scoped>
.wiki-search-page { min-height: 100vh; background: var(--color-bg-1); color: var(--color-text-1); }
.wiki-search-page__shell { width: min(100% - 40px, 980px); margin: 0 auto; }
.wiki-search-page__header { border-bottom: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.wiki-search-page__header-inner { display: flex; min-height: 62px; align-items: center; justify-content: space-between; gap: 16px; }
.wiki-search-page__brand, .wiki-search-page__back { display: inline-flex; align-items: center; gap: 8px; color: var(--color-text-2); font-size: 14px; text-decoration: none; }
.wiki-search-page__brand { color: var(--color-text-1); font-size: 16px; font-weight: 700; }
.wiki-search-page__back:hover { color: var(--color-text-1); }
.wiki-search-page__content { padding: 58px 0 80px; }
.wiki-search-page__heading p { margin: 0 0 8px; color: var(--color-text-3); font-size: 13px; font-weight: 600; }
.wiki-search-page__heading h1 { margin: 0; font-size: 30px; line-height: 1.35; }
.wiki-search-page__form { display: grid; grid-template-columns: minmax(0, 1fr) 190px auto; gap: 10px; margin-top: 28px; }
.wiki-search-page__count { margin: 26px 0 12px; color: var(--color-text-3); font-size: 13px; }
.wiki-search-page__results { display: grid; gap: 10px; }
.wiki-search-result { cursor: pointer; transition: box-shadow .15s; }
.wiki-search-result:hover { box-shadow: 0 5px 16px rgb(0 0 0 / 7%); }
.wiki-search-result__heading { display: flex; min-width: 0; align-items: center; gap: 8px; }
.wiki-search-result__heading strong { overflow: hidden; font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.wiki-search-result__heading :deep(svg) { margin-left: auto; color: var(--color-text-3); }
.wiki-search-result__location { display: flex; min-width: 0; align-items: center; gap: 6px; margin-top: 9px; color: var(--color-text-3); font-size: 12px; }
.wiki-search-result__location span { color: var(--color-border-2); }
.wiki-search-result p { display: -webkit-box; overflow: hidden; margin: 9px 0 0; color: var(--color-text-2); font-size: 14px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.wiki-search-page__empty { display: grid; justify-items: center; gap: 8px; margin-top: 48px; padding: 54px 20px; border: 1px dashed var(--color-border-2); border-radius: 8px; color: var(--color-text-3); text-align: center; }
.wiki-search-page__empty :deep(svg) { font-size: 34px; }
.wiki-search-page__loading-icon { animation: wiki-search-spin 1s linear infinite; }
.wiki-search-page__empty strong { color: var(--color-text-1); }
.wiki-search-page__empty p { margin: 0; font-size: 13px; }
@keyframes wiki-search-spin { to { transform: rotate(360deg); } }
@media (max-width: 680px) { .wiki-search-page__shell { width: min(100% - 28px, 980px); }.wiki-search-page__content { padding-top: 34px; }.wiki-search-page__heading h1 { font-size: 24px; }.wiki-search-page__form { grid-template-columns: 1fr; }.wiki-search-page__form :deep(button) { width: 100%; } }
</style>
