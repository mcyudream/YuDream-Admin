<script setup lang="ts">
import type { WikiPublicSpace } from '@/api/modules/platform-wiki'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import apiCms from '@/api/modules/platform-cms'
import { fetchPublicWikiSpaces } from '@/api/modules/platform-wiki'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import WikiPublicChrome from './wiki-public-chrome.vue'

interface WikiHomePage { title: string, summary: string, url: string, spaceName: string }

const router = useRouter()
const appSettingsStore = useAppSettingsStore()
const loading = ref(true)
const error = ref('')
const spaces = ref<WikiPublicSpace[]>([])
const recentPages = ref<WikiHomePage[]>([])
// null 表示统计不可用：与「确实为 0」区分，请求失败时不能假报空
const publicPageCount = ref<number | null>(null)
const query = ref('')
const siteName = computed(() => appSettingsStore.siteName || '')
const spaceCountText = computed(() => (loading.value || error.value) ? '—' : String(spaces.value.length))
const pageCountText = computed(() => publicPageCount.value === null ? '—' : String(publicPageCount.value))
// 请求代际：重载时丢弃在途的旧响应
let loadRequestId = 0

function spaceHref(space: WikiPublicSpace) {
  return `/wiki/${encodeURIComponent(space.slug)}`
}

/** 最近内容摘要可能带 frontmatter 与 Markdown 语法：先归一成可读纯文本再展示 */
function excerptText(raw: string, limit = 140) {
  const text = (raw || '')
    .replace(/^\s*---[\s\S]*?---\s*/, '')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, '')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s*/gm, '')
    .replace(/[*_`>|]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > limit ? `${text.slice(0, limit)}…` : text
}

/** 站内路径走 router 无感跳转；外链、新窗口手势与修饰键保留原生 <a> 行为 */
function navigate(event: MouseEvent, url: string) {
  if (!url || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
    return
  }
  if (/^(?:https?:|mailto:|tel:|#)/i.test(url)) {
    return
  }
  event.preventDefault()
  void router.push(url)
}

async function load() {
  const requestId = ++loadRequestId
  loading.value = true
  error.value = ''
  let nextSpaces: WikiPublicSpace[]
  try {
    nextSpaces = (await fetchPublicWikiSpaces()).data || []
  }
  catch (exception: any) {
    if (requestId !== loadRequestId) {
      return
    }
    spaces.value = []
    recentPages.value = []
    publicPageCount.value = null
    loading.value = false
    error.value = exception?.message || '公开知识库暂时无法访问，请稍后重试'
    return
  }
  if (requestId !== loadRequestId) {
    return
  }
  spaces.value = nextSpaces
  try {
    const context = (await apiCms.publicTemplateContext({ knowledgeLatestLimit: 6 })).data
    if (requestId !== loadRequestId) {
      return
    }
    publicPageCount.value = context.knowledge.pages.length
    recentPages.value = context.knowledge.latest.slice(0, 6).map(item => ({
      title: item.title,
      summary: excerptText(item.summary || item.excerpt || '') || '查看已发布文档',
      url: item.url,
      spaceName: nextSpaces.find(space => space.slug === item.spaceSlug)?.name || item.spaceSlug || '知识库',
    }))
  }
  catch {
    if (requestId !== loadRequestId) {
      return
    }
    // 统计与最近内容属于增强信息：失败时显示「—」并隐藏列表，不谎报 0
    publicPageCount.value = null
    recentPages.value = []
  }
  if (requestId !== loadRequestId) {
    return
  }
  loading.value = false
}

function search() {
  const keyword = query.value.trim()
  if (!keyword) {
    return
  }
  void router.push({ name: 'publicWikiSearch', query: { q: keyword } })
}

onMounted(() => {
  applyPublicSeo({
    title: '公开知识库',
    description: '浏览已发布文档，或直接检索全部知识库内容。',
    canonicalPath: '/wiki',
    type: 'website',
    siteName: siteName.value ? `${siteName.value} Wiki` : '公开知识库',
  })
  void load()
})

onBeforeUnmount(() => {
  loadRequestId += 1
  clearPublicSeo()
})
</script>

<template>
  <WikiPublicChrome>
    <main data-wiki-page-root="portal" class="wiki-portal">
      <div class="wiki-portal__shell">
        <header class="wiki-portal__head">
          <p class="wiki-portal__kicker">公开文档门户</p>
          <h1>知识库</h1>
          <p class="wiki-portal__lead">浏览已发布文档，或直接检索全部知识库内容。</p>
          <form class="wiki-portal__search" @submit.prevent="search">
            <YdInput v-model="query" clearable placeholder="输入问题或关键词，回车搜索">
              <template #start><YdIcon name="morph:search" /></template>
            </YdInput>
            <YdButton type="submit" :loading="loading && !spaces.length"><YdIcon name="morph:search" /> 搜索</YdButton>
          </form>
          <p class="wiki-portal__stats">
            <span><strong>{{ spaceCountText }}</strong> 个知识空间</span>
            <span><strong>{{ pageCountText }}</strong> 篇公开页面</span>
            <RouterLink to="/wiki/search">浏览全部搜索</RouterLink>
          </p>
        </header>

        <section class="wiki-portal__section" aria-labelledby="wiki-portal-spaces">
          <div class="wiki-portal__section-head">
            <h2 id="wiki-portal-spaces">知识空间</h2>
            <p>按主题浏览公开文档。</p>
          </div>
          <div v-if="loading" class="wiki-portal__skeleton" aria-hidden="true">
            <YdSkeleton v-for="index in 3" :key="index" variant="rect" height="168px" />
          </div>
          <YdResult
            v-else-if="error"
            status="error"
            title="公开知识库暂时无法访问"
            :description="error"
          >
            <template #action>
              <YdButton type="button" variant="outline" @click="load"><YdIcon name="morph:refresh-cw" /> 重新加载</YdButton>
            </template>
          </YdResult>
          <YdEmpty
            v-else-if="!spaces.length"
            icon="morph:book-open"
            title="暂无公开知识库"
            description="还没有开放公开阅读的知识空间。"
          />
          <div v-else class="wiki-portal__spaces">
            <RouterLink v-for="space in spaces" :key="space.slug" class="wiki-portal__space-link" :to="spaceHref(space)">
              <YdCard class="wiki-portal__space" content-class="wiki-portal__space-content">
                <div class="wiki-portal__space-head">
                  <span class="wiki-portal__space-icon" aria-hidden="true"><YdIcon name="morph:book-open" /></span>
                  <strong>{{ space.name }}</strong>
                </div>
                <p>{{ space.description || '查看已发布文档' }}</p>
                <span class="wiki-portal__space-more">进入知识库 <YdIcon name="morph:arrow-right" /></span>
              </YdCard>
            </RouterLink>
          </div>
        </section>

        <section v-if="recentPages.length" class="wiki-portal__section" aria-labelledby="wiki-portal-recent">
          <div class="wiki-portal__section-head">
            <h2 id="wiki-portal-recent">最近内容</h2>
            <p>从公开知识空间快速进入页面。</p>
          </div>
          <YdList divided class="wiki-portal__recent">
            <YdListRow v-for="page in recentPages" :key="page.url">
              <a class="wiki-portal__recent-link" :href="page.url" @click="navigate($event, page.url)">
                <span class="wiki-portal__recent-main">
                  <small>{{ page.spaceName }}</small>
                  <strong>{{ page.title }}</strong>
                  <p>{{ page.summary }}</p>
                </span>
                <YdIcon name="morph:arrow-up-right" />
              </a>
            </YdListRow>
          </YdList>
        </section>
      </div>
    </main>
  </WikiPublicChrome>
</template>

<style scoped>
.wiki-portal {
  display: flex;
  min-height: 100%;
  flex: 1 1 auto;
  flex-direction: column;
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.wiki-portal__shell { width: min(1080px, calc(100% - 40px)); margin: 0 auto; padding: 44px 0 72px; }
.wiki-portal__kicker { margin: 0 0 8px; color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; font-weight: 700; letter-spacing: 0.08em; }
.wiki-portal__head h1 { margin: 0 0 10px; color: var(--yb-site-heading, var(--color-text-1)); font-size: 34px; line-height: 1.2; }
.wiki-portal__lead { max-width: 46rem; margin: 0; color: var(--yb-site-muted, var(--color-text-3)); font-size: 15px; line-height: 1.7; }
.wiki-portal__search { display: grid; width: min(100%, 720px); grid-template-columns: minmax(0, 1fr) auto; gap: 10px; margin: 26px 0 14px; }
.wiki-portal__stats { display: flex; flex-wrap: wrap; gap: 18px; align-items: center; margin: 0; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; }
.wiki-portal__stats strong { color: var(--yb-site-heading, var(--color-text-1)); font-size: 18px; }
.wiki-portal__stats a { margin-left: auto; color: var(--yb-site-text-2, var(--color-text-2)); text-decoration: none; }
.wiki-portal__stats a:hover { color: var(--yb-site-heading, var(--color-text-1)); }
.wiki-portal__section { margin-top: 44px; }
.wiki-portal__section-head { display: flex; align-items: baseline; gap: 14px; margin-bottom: 14px; }
.wiki-portal__section-head h2 { margin: 0; color: var(--yb-site-heading, var(--color-text-1)); font-size: 20px; }
.wiki-portal__section-head p { margin: 0; color: var(--yb-site-muted, var(--color-text-3)); font-size: 13px; }
.wiki-portal__skeleton { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.wiki-portal__spaces { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; }
.wiki-portal__space-link { display: block; border-radius: 10px; color: inherit; text-decoration: none; }
.wiki-portal__space-link:focus-visible { outline: 2px solid var(--yb-site-text-2, var(--color-text-2)); outline-offset: 2px; }
.wiki-portal__space { min-height: 185px; cursor: pointer; border: 1px solid var(--yb-site-border, var(--color-border-2)); border-radius: 10px; background: var(--yb-site-surface, var(--color-bg-2)); box-shadow: 0 1px 3px rgb(0 0 0 / 6%); transition: box-shadow 0.15s; }
.wiki-portal__space-link:hover .wiki-portal__space { box-shadow: 0 6px 18px rgb(0 0 0 / 10%); }
.wiki-portal__space-content { display: flex; min-height: 185px; flex-direction: column; align-items: stretch; gap: 12px; }
/* 图标与标题同行：图标置于固定尺寸的方块内，与标题首行居中对齐；YdIcon 外层为 i.yd-icon，按其定尺寸 */
.wiki-portal__space-head { display: flex; gap: 12px; align-items: center; min-width: 0; }
.wiki-portal__space-icon { display: inline-flex; flex: none; align-items: center; justify-content: center; width: 40px; height: 40px; border-radius: 10px; color: var(--yb-site-heading, var(--color-text-1)); background: var(--yb-site-surface-2, var(--color-fill-2)); font-size: 20px; }
.wiki-portal__space-head strong { min-width: 0; color: var(--yb-site-heading, var(--color-text-1)); font-size: 16px; line-height: 1.4; overflow-wrap: anywhere; }
.wiki-portal__space-content p { margin: 0; color: var(--yb-site-muted, var(--color-text-3)); font-size: 14px; line-height: 1.6; }
.wiki-portal__space-more { display: inline-flex; align-self: flex-start; align-items: center; gap: 4px; margin-top: auto; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 13px; font-weight: 600; }
.wiki-portal__recent-link { display: flex; min-width: 0; flex: 1; align-items: center; gap: 14px; justify-content: space-between; color: inherit; text-decoration: none; }
.wiki-portal__recent-link:hover strong { color: var(--yb-site-primary, var(--color-text-1)); }
.wiki-portal__recent-main { display: grid; min-width: 0; gap: 4px; }
.wiki-portal__recent-main small { color: var(--yb-site-muted, var(--color-text-3)); font-size: 12px; }
.wiki-portal__recent-main strong { color: var(--yb-site-heading, var(--color-text-1)); font-size: 15px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wiki-portal__recent-main p { margin: 0; color: var(--yb-site-text-2, var(--color-text-2)); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.wiki-portal__recent-link > :deep(svg) { flex-shrink: 0; color: var(--yb-site-muted, var(--color-text-3)); }
@media (max-width: 900px) {
  .wiki-portal__spaces, .wiki-portal__skeleton { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 768px) {
  .wiki-portal__shell { width: min(100% - 28px, 1080px); padding: 32px 0 56px; }
  .wiki-portal__head h1 { font-size: 28px; }
  .wiki-portal__search { grid-template-columns: minmax(0, 1fr); }
  .wiki-portal__search :deep([data-slot="button"]) { width: 100%; }
  .wiki-portal__stats a { margin-left: 0; }
  .wiki-portal__spaces, .wiki-portal__skeleton { grid-template-columns: minmax(0, 1fr); }
  .wiki-portal__section-head { flex-direction: column; gap: 4px; }
  .wiki-portal__recent-main strong, .wiki-portal__recent-main p { white-space: normal; overflow-wrap: anywhere; }
}
</style>
