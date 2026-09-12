<script setup lang="ts">
import type { PluginMarketCategory, PluginMarketQuery, PluginMarketSort, PluginMarketSummary, PluginMarketTag } from '@/api/modules/plugin-market-public'
import apiMarketPublic, { PLUGIN_MARKET_CATEGORIES } from '@/api/modules/plugin-market-public'
import { useAppFeatureStore } from '@/store/modules/app/features'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import MarketChrome from './market-chrome.vue'

const router = useRouter()
const route = useRoute()
const toast = useFaToast()
const auth = useAppAuth()
const accountStore = useAppAccountStore()
const featureStore = useAppFeatureStore()
const appSettingsStore = useAppSettingsStore()

const loading = ref(false)
const items = ref<PluginMarketSummary[]>([])
const total = ref(0)
const pluginCount = ref(0)
const categories = ref<PluginMarketCategory[]>([])
const tags = ref<PluginMarketTag[]>([])
const search = ref(typeof route.query.q === 'string' ? route.query.q : '')
const selectedCategories = ref<string[]>(typeof route.query.categories === 'string' && route.query.categories
  ? route.query.categories.split(',').filter(Boolean)
  : [])
const selectedTags = ref<string[]>(typeof route.query.tags === 'string' && route.query.tags
  ? route.query.tags.split(',').filter(Boolean)
  : [])
const authorId = ref(typeof route.query.authorId === 'string' ? route.query.authorId : '')
const authorName = ref(typeof route.query.author === 'string' ? route.query.author : '')
const publishedAfter = ref(typeof route.query.after === 'string' ? route.query.after.slice(0, 10) : '')
const publishedBefore = ref(typeof route.query.before === 'string' ? route.query.before.slice(0, 10) : '')
const sort = ref<PluginMarketSort>((route.query.sort as PluginMarketSort) || 'newest')
const page = ref(Number(route.query.page) > 0 ? Number(route.query.page) : 1)
const size = ref(Number(route.query.size) > 0 ? Number(route.query.size) : 12)

const canManageOwnPlugins = computed(() => accountStore.isLogin && auth.auth('platform:plugin-market-source:upload'))
const siteName = computed(() => appSettingsStore.siteName || '插件市场')
const sortOptions: { label: string, value: PluginMarketSort }[] = [
  { label: '最新发布', value: 'newest' },
  { label: '下载量', value: 'downloads' },
  { label: '最近更新', value: 'updated' },
  { label: '名称', value: 'name' },
]
const publicV2Url = `${window.location.origin}/api/public/plugin-market`
const hasFilters = computed(() => Boolean(
  selectedCategories.value.length
  || selectedTags.value.length
  || authorId.value
  || publishedAfter.value
  || publishedBefore.value,
))

watch([search, selectedCategories, selectedTags, authorId, authorName, publishedAfter, publishedBefore, sort, page, size], syncQuery, { deep: true })

onMounted(async () => {
  await featureStore.load()
  applyPublicSeo({
    title: '插件市场',
    description: '浏览、搜索并下载当前站点发布的插件。',
    canonicalPath: '/market',
    type: 'website',
    siteName: siteName.value,
  })
  await Promise.all([loadFacets(), loadPlugins()])
})

onBeforeUnmount(clearPublicSeo)

function syncQuery() {
  void router.replace({
    query: {
      q: search.value.trim() || undefined,
      categories: selectedCategories.value.length ? selectedCategories.value.join(',') : undefined,
      tags: selectedTags.value.length ? selectedTags.value.join(',') : undefined,
      authorId: authorId.value.trim() || undefined,
      author: authorName.value.trim() || undefined,
      after: publishedAfter.value || undefined,
      before: publishedBefore.value || undefined,
      sort: sort.value === 'newest' ? undefined : sort.value,
      page: page.value > 1 ? String(page.value) : undefined,
      size: size.value === 12 ? undefined : String(size.value),
    },
  })
}

async function loadFacets() {
  try {
    const [categoryRes, tagRes, manifest] = await Promise.all([
      apiMarketPublic.categories(),
      apiMarketPublic.tags(30),
      apiMarketPublic.manifest(),
    ])
    categories.value = categoryRes
    tags.value = tagRes
    pluginCount.value = manifest.pluginCount
  }
  catch {
    categories.value = PLUGIN_MARKET_CATEGORIES.map(name => ({ code: name, name, count: 0 }))
    tags.value = []
  }
}

async function loadPlugins() {
  loading.value = true
  try {
    const query: PluginMarketQuery = {
      search: search.value.trim() || undefined,
      categories: selectedCategories.value.join(',') || undefined,
      tags: selectedTags.value.join(',') || undefined,
      authorId: authorId.value.trim() || undefined,
      publishedAfter: publishedAfter.value || undefined,
      publishedBefore: publishedBefore.value || undefined,
      sort: sort.value,
      page: page.value,
      size: size.value,
    }
    const res = await apiMarketPublic.plugins(query)
    items.value = res.items
    total.value = res.total
  }
  catch {
    items.value = []
    total.value = 0
  }
  finally {
    loading.value = false
  }
}

function toggleCategory(name: string) {
  selectedCategories.value = selectedCategories.value.includes(name)
    ? selectedCategories.value.filter(item => item !== name)
    : [...selectedCategories.value, name]
  page.value = 1
  void loadPlugins()
}

function toggleTag(tag: string) {
  selectedTags.value = selectedTags.value.includes(tag)
    ? selectedTags.value.filter(item => item !== tag)
    : [...selectedTags.value, tag]
  page.value = 1
  void loadPlugins()
}

function applyAuthor(id?: string, name?: string) {
  authorId.value = id || ''
  authorName.value = name || ''
  page.value = 1
  void loadPlugins()
}

function onTimeFilterChange() {
  page.value = 1
  void loadPlugins()
}

function clearFilters() {
  selectedCategories.value = []
  selectedTags.value = []
  authorId.value = ''
  authorName.value = ''
  publishedAfter.value = ''
  publishedBefore.value = ''
  page.value = 1
  void loadPlugins()
}

function onSearch() {
  page.value = 1
  void loadPlugins()
}

function onSortChange() {
  page.value = 1
  void loadPlugins()
}

function onPageChange(value: number) {
  page.value = value
  void loadPlugins()
}

function onSizeChange(value: number) {
  size.value = value
  page.value = 1
  void loadPlugins()
}

function goAuthorWorkspace() {
  if (!accountStore.isLogin) {
    void router.push({ name: 'login', query: { redirect: '/platform/plugin-publish' } })
    return
  }
  void router.push('/platform/plugin-publish')
}

function pluginLocation(code: string) {
  return { name: 'publicMarketPlugin', params: { code } }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function formatDownloads(value?: number) {
  if (!value) {
    return '0'
  }
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value)
}

function isImageIcon(icon?: string) {
  return !!icon && (/^https?:\/\//i.test(icon) || icon.startsWith('/') || icon.startsWith('data:'))
}

function iconUrl(icon?: string) {
  return toBackendAssetUrl(icon)
}

async function copyPublicUrl() {
  await navigator.clipboard.writeText(publicV2Url)
  toast.success('已复制市场源地址')
}
</script>

<template>
  <MarketChrome>
    <main class="discover">
      <div class="discover__inner">
        <header class="discover-hero">
          <div class="discover-hero__copy">
            <p class="discover-kicker">公开插件社区</p>
            <h1>发现插件</h1>
            <p>浏览当前站点发布的插件。按分类、标签、作者与时间筛选，匿名即可下载。</p>
          </div>
          <dl class="discover-stats">
            <div>
              <dt>{{ pluginCount || total }}</dt>
              <dd>已发布插件</dd>
            </div>
            <div>
              <dt>{{ categories.filter(item => item.count).length }}</dt>
              <dd>分类</dd>
            </div>
          </dl>
        </header>

        <form class="discover-search" @submit.prevent="onSearch">
          <label class="discover-search__field discover-control">
            <FaIcon name="i-ri:search-line" />
            <input v-model="search" type="search" placeholder="搜索名称、编码或描述">
          </label>
          <label class="discover-sort discover-control">
            <select v-model="sort" @change="onSortChange">
              <option v-for="option in sortOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
            </select>
          </label>
          <button type="submit" class="discover-action">搜索</button>
          <button v-if="canManageOwnPlugins" type="button" class="discover-action discover-action--ghost" @click="goAuthorWorkspace">
            管理我的插件
          </button>
        </form>

        <div class="discover-layout">
          <aside class="discover-filters">
            <section>
              <h2>分类</h2>
              <button
                v-for="item in categories"
                :key="item.code"
                type="button"
                class="discover-filter"
                :class="{ 'is-active': selectedCategories.includes(item.name) }"
                @click="toggleCategory(item.name)"
              >
                <span>{{ item.name }}</span>
                <em>{{ item.count }}</em>
              </button>
            </section>
            <section v-if="tags.length">
              <h2>标签</h2>
              <div class="discover-tags">
                <button
                  v-for="item in tags"
                  :key="item.tag"
                  type="button"
                  class="discover-tag"
                  :class="{ 'is-active': selectedTags.includes(item.tag) }"
                  @click="toggleTag(item.tag)"
                >
                  {{ item.tag }}
                </button>
              </div>
            </section>
            <section>
              <h2>发布时间</h2>
              <div class="discover-range">
                <input v-model="publishedAfter" type="date" @change="onTimeFilterChange">
                <input v-model="publishedBefore" type="date" @change="onTimeFilterChange">
              </div>
            </section>
            <section v-if="authorId">
              <h2>作者</h2>
              <div class="discover-author-chip">
                <span>{{ authorName || authorId }}</span>
                <button type="button" @click="applyAuthor('', '')">清除</button>
              </div>
            </section>
            <button v-if="hasFilters" type="button" class="discover-clear" @click="clearFilters">
              清除筛选
            </button>
          </aside>

          <section class="discover-results">
            <div class="discover-results__head">
              <strong>{{ total }}</strong>
              <span>个插件</span>
            </div>
            <div v-if="loading" class="discover-empty">加载中…</div>
            <div v-else-if="!items.length" class="discover-empty">暂无符合条件的插件。</div>
            <div v-else class="discover-grid">
              <RouterLink
                v-for="item in items"
                :key="item.code"
                :to="pluginLocation(item.code)"
                class="discover-card"
              >
                <div class="discover-card__icon">
                  <img v-if="isImageIcon(item.icon)" :src="iconUrl(item.icon)" :alt="item.displayName || item.code">
                  <FaIcon v-else :name="item.icon || 'i-ri:puzzle-2-line'" />
                </div>
                <div class="discover-card__body">
                  <div class="discover-card__title">
                    <h3>{{ item.displayName || item.code }}</h3>
                    <span>{{ item.latestVersion }}</span>
                  </div>
                  <p>{{ item.description || '暂无简介' }}</p>
                  <div class="discover-card__meta">
                    <span v-if="item.category">{{ item.category }}</span>
                    <span v-for="tag in (item.tags || []).slice(0, 3)" :key="tag">{{ tag }}</span>
                  </div>
                  <div class="discover-card__foot">
                    <span
                      class="discover-card__author"
                      role="button"
                      tabindex="0"
                      @click.prevent.stop="applyAuthor(item.authorId, item.authorName)"
                      @keydown.enter.prevent.stop="applyAuthor(item.authorId, item.authorName)"
                    >
                      {{ item.authorName || '未知作者' }}
                    </span>
                    <span>{{ formatDownloads(item.downloads) }} 下载</span>
                    <span>{{ formatTime(item.updatedAt || item.publishedAt) }}</span>
                  </div>
                </div>
              </RouterLink>
            </div>
            <div class="discover-pagination">
              <span class="discover-pagination__total">共 {{ total }} 条</span>
              <label class="discover-pagination__sizes">
                <select :value="size" @change="onSizeChange(Number(($event.target as HTMLSelectElement).value))">
                  <option :value="12">12 条/页</option>
                  <option :value="24">24 条/页</option>
                  <option :value="48">48 条/页</option>
                </select>
              </label>
              <div class="discover-pagination__pager">
                <button type="button" :disabled="page <= 1" @click="onPageChange(page - 1)">‹</button>
                <strong>{{ page }}</strong>
                <button type="button" :disabled="page >= Math.max(1, Math.ceil(total / size))" @click="onPageChange(page + 1)">›</button>
              </div>
            </div>
          </section>
        </div>

        <footer class="discover-source">
          <div>
            <strong>订阅此市场</strong>
            <p>其他 YuDream 实例可将此地址添加为「v2 协议源」。</p>
          </div>
          <div class="discover-source__url">
            <code>{{ publicV2Url }}</code>
            <button type="button" class="discover-action discover-action--ghost" @click="copyPublicUrl">复制</button>
          </div>
        </footer>
      </div>
    </main>
  </MarketChrome>
</template>

<style scoped>
.discover {
  min-height: 100%;
  flex: 1 1 auto;
  padding-top: var(--neco-page-top, 0px);
  --background: var(--yb-site-bg, var(--color-bg-1));
  --foreground: var(--yb-site-text, var(--color-text-1));
  --card: var(--yb-site-surface, var(--color-bg-2));
  --card-foreground: var(--yb-site-text, var(--color-text-1));
  --popover: var(--yb-site-surface, var(--color-bg-2));
  --popover-foreground: var(--yb-site-text, var(--color-text-1));
  --muted: var(--yb-site-hover, var(--color-fill-1));
  --muted-foreground: var(--yb-site-muted, var(--color-text-3));
  --border: var(--yb-site-border, var(--color-border-2));
  --input: var(--yb-site-border, var(--color-border-2));
  --ring: var(--yb-site-primary, var(--color-primary-6, #3b82f6));
  --primary: var(--yb-site-primary, var(--color-primary-6, #3b82f6));
  --primary-foreground: var(--yb-site-primary-text, #fff);
  --accent: var(--yb-site-hover, var(--color-fill-1));
  --accent-foreground: var(--yb-site-text, var(--color-text-1));
  background:
    radial-gradient(1200px 420px at 12% -10%, color-mix(in srgb, var(--yb-site-primary, var(--color-primary-6, #3b82f6)) 14%, transparent), transparent 70%),
    var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.discover__inner {
  width: min(1180px, calc(100% - 40px));
  margin: 0 auto;
  padding: 36px 0 80px;
}
.discover-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 24px;
}
.discover-kicker {
  margin: 0;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}
.discover-hero h1 {
  margin: 8px 0 10px;
  font-size: 42px;
  line-height: 1.1;
}
.discover-hero p {
  margin: 0;
  max-width: 46rem;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 16px;
  line-height: 1.7;
}
.discover-stats {
  display: flex;
  gap: 10px;
  margin: 0;
}
.discover-stats div {
  min-width: 108px;
  padding: 12px 14px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 12px;
  background: color-mix(in srgb, var(--yb-site-surface, var(--color-bg-2)) 88%, transparent);
}
.discover-stats dt {
  font-size: 24px;
  font-weight: 800;
}
.discover-stats dd {
  margin: 2px 0 0;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
}
.discover-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px auto auto;
  gap: 10px;
  margin: 28px 0 32px;
}
.discover-control {
  min-width: 0;
}
.discover-search__field {
  display: flex;
  min-height: 36px;
  align-items: center;
  gap: 8px;
  padding: 0 12px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 8px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: var(--yb-site-text, var(--color-text-1));
}
.discover-search__field input {
  flex: 1;
  min-width: 0;
  border: 0;
  background: transparent;
  color: inherit;
  font: inherit;
  outline: none;
}
.discover-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 36px;
  padding: 0 16px;
  border: 1px solid var(--yb-site-primary-btn-bg, var(--yb-site-primary, var(--color-primary-6, #3b82f6)));
  border-radius: 8px;
  background: var(--yb-site-primary-btn-bg, var(--yb-site-primary, var(--color-primary-6, #3b82f6)));
  color: var(--yb-site-primary-btn-text, var(--yb-site-primary-text, #fff));
  font: inherit;
  cursor: pointer;
}
.discover-action--ghost {
  border-color: var(--yb-site-border, var(--color-border-2));
  background: var(--yb-site-surface, var(--color-bg-2));
  color: var(--yb-site-text, var(--color-text-1));
}
.discover-sort select {
  width: 100%;
  min-height: 36px;
  padding: 0 12px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 8px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: var(--yb-site-text, var(--color-text-1));
  font: inherit;
}
.discover-card {
  text-decoration: none;
  color: inherit;
}
.discover-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 28px;
  align-items: start;
}
.discover-filters {
  position: sticky;
  top: calc(var(--neco-page-top, 0px) + 16px);
  padding: 16px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 16px;
  background: color-mix(in srgb, var(--yb-site-surface, var(--color-bg-2)) 92%, transparent);
}
.discover-filters h2 {
  margin: 0 0 10px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--yb-site-muted, var(--color-text-3));
}
.discover-filters section + section {
  margin-top: 22px;
}
.discover-filter,
.discover-tag,
.discover-card,
.discover-card__author {
  color: inherit;
  cursor: pointer;
}
.discover-filter {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
}
.discover-filter.is-active,
.discover-tag.is-active {
  background: var(--yb-site-bg, var(--color-bg-1));
}
.discover-filter em {
  font-style: normal;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
}
.discover-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.discover-tag {
  padding: 4px 10px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 999px;
  background: transparent;
  font-size: 12px;
}
.discover-range {
  display: grid;
  gap: 8px;
}
.discover-range input {
  width: 100%;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 8px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: var(--yb-site-text, var(--color-text-1));
  font: inherit;
}
.discover-author-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 8px;
  background: var(--yb-site-bg, var(--color-bg-1));
  font-size: 13px;
}
.discover-author-chip button {
  border: 0;
  background: transparent;
  color: var(--yb-site-muted, var(--color-text-3));
  cursor: pointer;
}
.discover-clear {
  margin-top: 8px;
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--yb-site-muted, var(--color-text-3));
  font: inherit;
  cursor: pointer;
}
.discover-results__head {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin-bottom: 16px;
  color: var(--yb-site-muted, var(--color-text-3));
}
.discover-results__head strong {
  color: var(--yb-site-heading, var(--color-text-1));
  font-size: 20px;
}
.discover-empty {
  padding: 64px 0;
  color: var(--yb-site-muted, var(--color-text-3));
  text-align: center;
}
.discover-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.discover-card {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  gap: 16px;
  min-height: 168px;
  padding: 18px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 16px;
  background: var(--yb-site-surface, var(--color-bg-2));
  box-shadow: 0 1px 2px rgb(0 0 0 / 4%);
  text-align: left;
  transition: transform .15s ease, box-shadow .15s ease;
}
.discover-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgb(0 0 0 / 10%);
}
.discover-card__icon {
  display: flex;
  width: 72px;
  height: 72px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 16px;
  background: var(--yb-site-bg, var(--color-bg-1));
  font-size: 32px;
}
.discover-card__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.discover-card__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.discover-card h3 {
  margin: 0;
  font-size: 18px;
}
.discover-card__title span,
.discover-card p,
.discover-card__foot {
  color: var(--yb-site-muted, var(--color-text-3));
}
.discover-card p {
  display: -webkit-box;
  margin: 8px 0 12px;
  overflow: hidden;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  line-height: 1.55;
}
.discover-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.discover-card__meta span {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--yb-site-bg, var(--color-bg-1));
  font-size: 12px;
}
.discover-card__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 12px;
  font-size: 12px;
}
.discover-card__author {
  padding: 0;
  text-decoration: underline;
  text-underline-offset: 2px;
}
.discover-pagination {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 22px;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 13px;
}
.discover-pagination__total {
  margin-right: auto;
}
.discover-pagination__sizes select,
.discover-pagination__pager input,
.discover-pagination__pager button,
.discover-pagination__pager strong {
  min-width: 36px;
  min-height: 32px;
  padding: 0 10px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 8px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: var(--yb-site-text, var(--color-text-1));
  font: inherit;
}
.discover-pagination__pager {
  display: flex;
  align-items: center;
  gap: 8px;
}
.discover-pagination__pager button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.discover-source {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 48px;
  padding: 18px 20px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 16px;
  background: color-mix(in srgb, var(--yb-site-surface, var(--color-bg-2)) 92%, transparent);
}
.discover-source p {
  margin: 4px 0 0;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 13px;
}
.discover-source__url {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}
.discover-source code {
  max-width: min(100%, 36rem);
  overflow-wrap: anywhere;
  font-size: 12px;
}
@media (max-width: 980px) {
  .discover-grid,
  .discover-layout,
  .discover-search,
  .discover-hero {
    display: grid;
    grid-template-columns: 1fr;
  }
  .discover-filters {
    position: static;
  }
  .discover-hero h1 {
    font-size: 32px;
  }
  .discover__inner {
    width: min(100% - 28px, 1180px);
  }
}
</style>
