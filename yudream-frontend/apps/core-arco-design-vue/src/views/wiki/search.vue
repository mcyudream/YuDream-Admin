<script setup lang="ts">
import type { WikiPublicSpace, WikiSearchHit } from '@/api/modules/platform-wiki'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { fetchPublicWikiSpaces, searchAllPublicWiki } from '@/api/modules/platform-wiki'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import WikiPublicChrome from './wiki-public-chrome.vue'

const route = useRoute()
const router = useRouter()
const appSettingsStore = useAppSettingsStore()
const query = ref('')
const selectedSpace = ref('')
const spaces = ref<WikiPublicSpace[]>([])
const hits = ref<WikiSearchHit[]>([])
const loading = ref(false)
const searched = ref(false)
const error = ref('')
let searchRequestId = 0

// 当前生效的检索词以路由为准：输入框可以先行编辑，结果与标题不会提前跳变
const routeQuery = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const searchTitle = computed(() => routeQuery.value ? `“${routeQuery.value}”的搜索结果` : '搜索知识库')
const resultItems = computed(() => hits.value.map(hit => ({ hit, ...hitLink(hit) })))
// 服务端每次最多返回 12 条，命中上限时提示收窄条件而不是假装结果完整
const hitLimited = computed(() => hits.value.length >= 12)

function hitLink(hit: WikiSearchHit) {
  if (hit.sourceUrl && /^https?:/i.test(hit.sourceUrl)) {
    return { href: hit.sourceUrl, external: true }
  }
  if (hit.sourceUrl?.startsWith('/')) {
    return { href: hit.sourceUrl, external: false }
  }
  const slug = hit.spaceSlug || selectedSpace.value
  return {
    href: slug ? `/wiki/${encodeURIComponent(slug)}/${encodeURI(hit.path.replace(/^\/+/, ''))}` : '/wiki',
    external: false,
  }
}

function syncFromRoute() {
  query.value = typeof route.query.q === 'string' ? route.query.q : ''
  selectedSpace.value = typeof route.query.space === 'string' ? route.query.space : ''
}

async function loadSpaces() {
  try {
    spaces.value = (await fetchPublicWikiSpaces()).data || []
  }
  catch {
    // 空间筛选是附加能力：列表失败不阻塞搜索
    spaces.value = []
  }
}

async function search() {
  const requestId = ++searchRequestId
  const keyword = query.value.trim()
  if (!keyword) {
    // 清空关键词：作废在途请求并立刻退出 loading，回到初始态
    hits.value = []
    searched.value = false
    error.value = ''
    loading.value = false
    return
  }
  loading.value = true
  searched.value = true
  error.value = ''
  try {
    const nextHits = (await searchAllPublicWiki({ query: keyword, spaceSlug: selectedSpace.value || undefined })).data || []
    if (requestId === searchRequestId) {
      hits.value = nextHits.slice(0, 12)
    }
  }
  catch (exception: any) {
    if (requestId !== searchRequestId) {
      return
    }
    hits.value = []
    error.value = exception?.message || '搜索暂时不可用，请稍后重试'
  }
  finally {
    if (requestId === searchRequestId) {
      loading.value = false
    }
  }
}

function submitSearch() {
  const keyword = query.value.trim()
  if (!keyword) {
    return
  }
  const currentQuery = typeof route.query.q === 'string' ? route.query.q : ''
  const currentSpace = typeof route.query.space === 'string' ? route.query.space : ''
  if (currentQuery === keyword && currentSpace === selectedSpace.value) {
    // 路由没变化时 replace 不会触发 watch，这里直接重跑，避免点了搜索没反应
    void search()
    return
  }
  void router.replace({
    name: 'publicWikiSearch',
    query: { q: keyword, ...(selectedSpace.value ? { space: selectedSpace.value } : {}) },
  })
}

// 输入框清空时立刻回到初始态，不等下一次路由变化
watch(query, (value) => {
  if (value.trim()) {
    return
  }
  searchRequestId += 1
  hits.value = []
  searched.value = false
  error.value = ''
  loading.value = false
})

// 路由是检索的唯一触发源：初次进入与关键词变化各有且只有一次请求
watch(() => route.fullPath, () => {
  syncFromRoute()
  applySearchSeo()
  void search()
}, { immediate: true })

onMounted(() => {
  void loadSpaces()
})

onBeforeUnmount(() => {
  searchRequestId += 1
  clearPublicSeo()
})

function applySearchSeo() {
  const siteName = appSettingsStore.siteName
  applyPublicSeo({
    title: routeQuery.value ? `${routeQuery.value}的知识库搜索结果` : '搜索知识库',
    description: siteName ? `搜索 ${siteName} 公开知识库中的已发布页面。` : '搜索公开知识库中的已发布页面。',
    canonicalPath: route.fullPath,
    type: 'website',
    siteName: siteName ? `${siteName} Wiki` : '公开知识库',
  })
}
</script>

<template>
  <WikiPublicChrome>
    <main data-wiki-page-root="search" class="wiki-search">
      <div class="wiki-search__shell">
        <header class="wiki-search__head">
          <p class="wiki-search__kicker">公开文档</p>
          <h1>{{ searchTitle }}</h1>
          <form class="wiki-search__form" @submit.prevent="submitSearch">
            <YdInput v-model="query" clearable placeholder="输入问题或关键词">
              <template #start><YdIcon name="morph:search" /></template>
            </YdInput>
            <YdSelect v-model="selectedSpace" clearable placeholder="全部知识库" :options="spaces.map(space => ({ label: space.name, value: space.slug }))" />
            <YdButton type="submit" :loading="loading"><YdIcon name="morph:search" /> 搜索</YdButton>
          </form>
          <p v-if="searched && !loading && !error && hits.length" class="wiki-search__count">
            找到 {{ hits.length }} 条相关页面<span v-if="hitLimited">（服务端单次最多返回 12 条，可收窄关键词）</span>
          </p>
        </header>

        <div v-if="loading" class="wiki-search__state"><YdLoading :size="26" text="正在搜索…" /></div>
        <YdResult
          v-else-if="error"
          class="wiki-search__state"
          status="error"
          title="无法完成搜索"
          :description="error"
        >
          <template #action>
            <YdButton type="button" variant="outline" @click="search"><YdIcon name="morph:refresh-cw" /> 重试</YdButton>
          </template>
        </YdResult>
        <YdEmpty
          v-else-if="searched && !hits.length"
          class="wiki-search__state"
          illustration="no-result"
          title="没有找到相关页面"
          description="换一个关键词，或尝试在全部知识库中搜索。"
        />
        <YdList v-else-if="hits.length" divided class="wiki-search__results">
          <YdListRow v-for="item in resultItems" :key="`${item.hit.spaceSlug}-${item.hit.nodeId || item.hit.path}`">
            <a v-if="item.external" class="wiki-search__result" :href="item.href" rel="noopener">
              <span class="wiki-search__result-head"><strong>{{ item.hit.title }}</strong><YdIcon name="morph:arrow-up-right" /></span>
              <span class="wiki-search__result-path"><YdIcon name="morph:book-open" /> {{ item.hit.spaceName || '知识库' }}<i>/</i>{{ item.hit.path }}</span>
              <span class="wiki-search__result-excerpt">{{ item.hit.content }}</span>
            </a>
            <RouterLink v-else class="wiki-search__result" :to="item.href">
              <span class="wiki-search__result-head"><strong>{{ item.hit.title }}</strong><YdIcon name="morph:arrow-right" /></span>
              <span class="wiki-search__result-path"><YdIcon name="morph:book-open" /> {{ item.hit.spaceName || '知识库' }}<i>/</i>{{ item.hit.path }}</span>
              <span class="wiki-search__result-excerpt">{{ item.hit.content }}</span>
            </RouterLink>
          </YdListRow>
        </YdList>
        <YdEmpty
          v-else
          class="wiki-search__state"
          icon="morph:search"
          title="输入关键词开始搜索"
          description="支持按标题、正文与知识空间检索已发布页面。"
        />
      </div>
    </main>
  </WikiPublicChrome>
</template>

<style scoped>
.wiki-search {
  display: flex;
  min-height: 100%;
  flex: 1 1 auto;
  flex-direction: column;
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.wiki-search__shell { width: min(100% - 40px, 980px); margin: 0 auto; padding: 44px 0 80px; }
.wiki-search__kicker { margin: 0 0 8px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; font-weight: 700; letter-spacing: 0.08em; }
.wiki-search__head h1 { margin: 0; color: var(--yb-site-heading, var(--color-text-1)); font-size: 30px; line-height: 1.35; overflow-wrap: anywhere; }
.wiki-search__form { display: grid; grid-template-columns: minmax(0, 1fr) 190px auto; gap: 10px; margin-top: 24px; }
.wiki-search__count { margin: 22px 0 0; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; }
.wiki-search__count span { color: var(--yb-site-muted, var(--color-text-3)); }
.wiki-search__results { margin-top: 18px; }
.wiki-search__result { display: grid; min-width: 0; flex: 1; gap: 8px; padding: 6px 0; color: inherit; text-decoration: none; }
.wiki-search__result:hover strong { color: var(--yb-site-primary, var(--color-text-1)); }
.wiki-search__result-head { display: flex; min-width: 0; align-items: center; gap: 8px; }
.wiki-search__result-head strong { min-width: 0; color: var(--yb-site-heading, var(--color-text-1)); font-size: 16px; overflow-wrap: anywhere; }
.wiki-search__result-head :deep(svg) { margin-left: auto; flex-shrink: 0; color: var(--yb-site-muted, var(--color-text-3)); }
.wiki-search__result-path { display: flex; min-width: 0; align-items: center; gap: 6px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; overflow-wrap: anywhere; }
.wiki-search__result-path i { color: var(--yb-site-border, var(--color-border-2)); font-style: normal; }
.wiki-search__result-excerpt { display: -webkit-box; overflow: hidden; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 14px; line-height: 1.7; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.wiki-search__state { min-height: 260px; margin-top: 26px; }
@media (max-width: 768px) {
  .wiki-search__shell { width: min(100% - 28px, 980px); padding: 32px 0 64px; }
  .wiki-search__head h1 { font-size: 24px; }
  .wiki-search__form { grid-template-columns: minmax(0, 1fr); }
  .wiki-search__form :deep([data-slot="button"]) { width: 100%; }
  .wiki-search__result-excerpt { -webkit-line-clamp: 4; }
}
</style>
