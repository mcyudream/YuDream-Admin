<script setup lang="ts">
import type { PluginMarketCategory, PluginMarketQuery, PluginMarketSort, PluginMarketSummary, PluginMarketTag } from '@/api/modules/plugin-market-public'
import { PLUGIN_MARKET_CATEGORIES } from '@/api/modules/plugin-market-public'
import apiMarketPublic from '@/api/modules/plugin-market-public'
import apiMarketSource from '@/api/modules/platform-plugin-market-source'
import { useAppFeatureStore } from '@/store/modules/app/features'
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
const publishedAfter = ref(typeof route.query.after === 'string' ? route.query.after : '')
const publishedBefore = ref(typeof route.query.before === 'string' ? route.query.before : '')
const sort = ref<PluginMarketSort>((route.query.sort as PluginMarketSort) || 'newest')
const page = ref(Number(route.query.page) > 0 ? Number(route.query.page) : 1)
const size = 20
const publishVisible = ref(false)
const publishing = ref(false)
const publishFile = ref<File | null>(null)
const publishNotes = ref('')
const publishCategory = ref('')
const publishTags = ref('')
const publishLicense = ref('')
const publishCompatibility = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const canPublish = computed(() => accountStore.isLogin && auth.auth('platform:plugin-market-source:upload'))
const siteName = computed(() => appSettingsStore.siteName || '插件市场')
const sortOptions: { label: string, value: PluginMarketSort }[] = [
  { label: '最新发布', value: 'newest' },
  { label: '下载量', value: 'downloads' },
  { label: '最近更新', value: 'updated' },
  { label: '名称', value: 'name' },
]
const categoryOptions = computed(() => PLUGIN_MARKET_CATEGORIES.map(name => ({ label: name, value: name })))

watch([search, selectedCategories, selectedTags, authorId, publishedAfter, publishedBefore, sort, page], syncQuery, { deep: true })

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
      after: publishedAfter.value || undefined,
      before: publishedBefore.value || undefined,
      sort: sort.value === 'newest' ? undefined : sort.value,
      page: page.value > 1 ? String(page.value) : undefined,
    },
  })
}

async function loadFacets() {
  try {
    const [categoryRes, tagRes] = await Promise.all([
      apiMarketPublic.categories(),
      apiMarketPublic.tags(30),
    ])
    categories.value = categoryRes
    tags.value = tagRes
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
      size,
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

function applyAuthor(id?: string) {
  if (!id) {
    return
  }
  authorId.value = id
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

function openPlugin(code: string) {
  void router.push({ name: 'publicMarketPlugin', params: { code } })
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

function onPublishFileChange(event: Event) {
  publishFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

function openPublish() {
  if (!accountStore.isLogin) {
    void router.push({ name: 'login', query: { redirect: '/market' } })
    return
  }
  publishFile.value = null
  publishNotes.value = ''
  publishCategory.value = ''
  publishTags.value = ''
  publishLicense.value = ''
  publishCompatibility.value = ''
  publishVisible.value = true
}

async function submitPublish() {
  if (!publishFile.value) {
    toast.error('请选择插件 JAR')
    return
  }
  publishing.value = true
  try {
    const data = new FormData()
    data.append('file', publishFile.value)
    if (publishNotes.value.trim()) {
      data.append('releaseNotes', publishNotes.value.trim())
    }
    if (publishCategory.value) {
      data.append('category', publishCategory.value)
    }
    if (publishTags.value.trim()) {
      data.append('tags', publishTags.value.trim())
    }
    const metadata: Record<string, unknown> = {}
    if (publishLicense.value.trim()) {
      metadata.license = publishLicense.value.trim()
    }
    if (publishCompatibility.value.trim()) {
      try {
        const parsed = JSON.parse(publishCompatibility.value.trim()) as unknown
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) {
          toast.error('兼容性必须是 JSON 对象，例如 {"host":">=2.16.0"}')
          return
        }
        metadata.compatibility = parsed
      }
      catch {
        toast.error('兼容性必须是合法 JSON')
        return
      }
    }
    if (Object.keys(metadata).length) {
      data.append('metadata', JSON.stringify(metadata))
    }
    await apiMarketSource.uploadPublication(data)
    publishVisible.value = false
    toast.success('发布成功，已进入本机市场源')
    await Promise.all([loadFacets(), loadPlugins()])
  }
  catch {
    // systemClient 已提示错误
  }
  finally {
    publishing.value = false
  }
}
</script>

<template>
  <MarketChrome>
    <main class="market-page">
      <div class="market-page__inner">
        <header class="market-hero">
          <div>
            <span>公开插件社区</span>
            <h1>发现插件</h1>
            <p>浏览当前站点发布的插件，按分类、标签与下载量筛选。</p>
          </div>
          <FaButton v-if="canPublish" @click="openPublish">
            <FaIcon name="i-ri:upload-2-line" />
            发布插件
          </FaButton>
          <FaButton v-else-if="!accountStore.isLogin" variant="outline" @click="openPublish">
            登录后发布
          </FaButton>
        </header>

        <form class="market-search" @submit.prevent="onSearch">
          <FaInput v-model="search" clearable placeholder="搜索名称、编码或描述">
            <template #start>
              <FaIcon name="i-ri:search-line" />
            </template>
          </FaInput>
          <FaSelect v-model="sort" :options="sortOptions" class="market-sort" @update:model-value="onSortChange" />
          <FaButton html-type="submit">搜索</FaButton>
        </form>

        <div class="market-layout">
          <aside class="market-filters">
            <section>
              <h2>分类</h2>
              <button
                v-for="item in categories"
                :key="item.code"
                type="button"
                class="market-filter"
                :class="{ 'is-active': selectedCategories.includes(item.name) }"
                @click="toggleCategory(item.name)"
              >
                <span>{{ item.name }}</span>
                <em>{{ item.count }}</em>
              </button>
            </section>
            <section v-if="tags.length">
              <h2>标签</h2>
              <div class="market-tags">
                <button
                  v-for="item in tags"
                  :key="item.tag"
                  type="button"
                  class="market-tag"
                  :class="{ 'is-active': selectedTags.includes(item.tag) }"
                  @click="toggleTag(item.tag)"
                >
                  {{ item.tag }}
                </button>
              </div>
            </section>
            <section>
              <h2>作者</h2>
              <FaInput v-model="authorId" clearable placeholder="作者用户 ID" @clear="onTimeFilterChange" />
              <FaButton variant="outline" size="sm" class="mt-2" @click="onTimeFilterChange">
                按作者筛选
              </FaButton>
              <div class="mt-2 text-xs" style="color: var(--yb-site-muted, var(--color-text-3));">
                也可点击卡片上的作者名筛选。
              </div>
            </section>
            <section>
              <h2>发布时间</h2>
              <label class="market-time">
                <span>起始</span>
                <input v-model="publishedAfter" type="date" @change="onTimeFilterChange">
              </label>
              <label class="market-time">
                <span>截止</span>
                <input v-model="publishedBefore" type="date" @change="onTimeFilterChange">
              </label>
            </section>
            <FaButton variant="link" class="market-clear" @click="clearFilters">
              清除筛选
            </FaButton>
          </aside>

          <section class="market-results">
            <div class="market-results__head">
              <span>共 {{ total }} 个插件</span>
            </div>
            <div v-if="loading" class="market-empty">加载中…</div>
            <div v-else-if="!items.length" class="market-empty">暂无符合条件的插件。</div>
            <div v-else class="market-grid">
              <button
                v-for="item in items"
                :key="item.code"
                type="button"
                class="market-card"
                @click="openPlugin(item.code)"
              >
                <div class="market-card__icon">
                  <FaIcon :name="item.icon || 'i-ri:puzzle-line'" />
                </div>
                <div class="market-card__body">
                  <div class="market-card__title">
                    <strong>{{ item.displayName || item.code }}</strong>
                    <span>{{ item.latestVersion }}</span>
                  </div>
                  <p>{{ item.description || '暂无简介' }}</p>
                  <div class="market-card__meta">
                    <FaTag v-if="item.category" variant="secondary">{{ item.category }}</FaTag>
                    <FaTag v-for="tag in (item.tags || []).slice(0, 3)" :key="tag" variant="secondary">{{ tag }}</FaTag>
                  </div>
                  <div class="market-card__foot">
                    <button
                      type="button"
                      class="market-author"
                      @click.stop="applyAuthor(item.authorId)"
                    >
                      {{ item.authorName || '未知作者' }}
                    </button>
                    <span>{{ formatDownloads(item.downloads) }} 下载</span>
                    <span>{{ formatTime(item.updatedAt || item.publishedAt) }}</span>
                  </div>
                </div>
              </button>
            </div>
            <FaPagination
              v-if="total > size"
              v-model:page="page"
              :size="size"
              :total="total"
              class="market-pagination"
              @page-change="onPageChange"
            />
          </section>
        </div>
      </div>
    </main>

    <FaModal v-model="publishVisible" title="发布插件" show-cancel-button class="sm:max-w-xl" :confirm-loading="publishing" @confirm="submitPublish">
      <a-form :model="{ notes: publishNotes, category: publishCategory, tags: publishTags, license: publishLicense }" layout="vertical">
        <a-form-item label="插件 JAR">
          <input ref="fileInput" type="file" accept=".jar" class="hidden" @change="onPublishFileChange">
          <div class="flex items-center gap-2">
            <FaButton variant="outline" @click="fileInput?.click()">选择文件</FaButton>
            <span class="min-w-0 break-all text-sm">{{ publishFile?.name || '未选择' }}</span>
          </div>
          <div class="mt-1 text-xs" style="color: var(--yb-site-muted, var(--color-text-3));">
            未填写的字段以 plugin.yml 为准。
          </div>
        </a-form-item>
        <a-form-item label="分类（可选）">
          <FaSelect v-model="publishCategory" allow-clear :options="categoryOptions" placeholder="选择分类" />
        </a-form-item>
        <a-form-item label="标签（可选，逗号分隔）">
          <FaInput v-model="publishTags" placeholder="如 chat, wiki" />
        </a-form-item>
        <a-form-item label="许可证（可选）">
          <FaInput v-model="publishLicense" placeholder="如 MIT、Apache-2.0" />
        </a-form-item>
        <a-form-item label="兼容性 JSON（可选）">
          <FaTextarea v-model="publishCompatibility" :rows="3" placeholder='{"host":">=2.16.0"}' />
        </a-form-item>
        <a-form-item label="发布说明（可选）">
          <FaTextarea v-model="publishNotes" :rows="4" placeholder="本版本更新内容" />
        </a-form-item>
      </a-form>
    </FaModal>
  </MarketChrome>
</template>

<style scoped>
.market-page {
  min-height: 100%;
  flex: 1 1 auto;
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.market-page__inner {
  width: min(1120px, calc(100% - 40px));
  margin: 0 auto;
  padding: 40px 0 72px;
}
.market-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
}
.market-hero span {
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 13px;
  font-weight: 700;
}
.market-hero h1 {
  margin: 8px 0 6px;
  font-size: 32px;
}
.market-hero p {
  margin: 0;
  color: var(--yb-site-muted, var(--color-text-3));
}
.market-search {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 160px auto;
  gap: 10px;
  margin: 28px 0 32px;
}
.market-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 28px;
}
.market-filters h2 {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--yb-site-muted, var(--color-text-3));
}
.market-filter {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.market-filter.is-active,
.market-tag.is-active {
  background: var(--yb-site-surface, var(--color-bg-2));
}
.market-filter em {
  font-style: normal;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
}
.market-filters section + section {
  margin-top: 22px;
}
.market-time {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr);
  gap: 8px;
  align-items: center;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}
.market-time input {
  width: 100%;
  min-height: 32px;
  padding: 0 8px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 8px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: inherit;
}
.market-clear {
  margin-top: 8px;
  padding-left: 0;
}
.market-author {
  padding: 0;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.market-tag {
  padding: 4px 8px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 999px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 12px;
}
.market-results__head {
  margin-bottom: 14px;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 13px;
}
.market-empty {
  padding: 48px 0;
  color: var(--yb-site-muted, var(--color-text-3));
  text-align: center;
}
.market-grid {
  display: grid;
  gap: 12px;
}
.market-card {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr);
  gap: 14px;
  width: 100%;
  padding: 16px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 12px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: inherit;
  text-align: left;
  cursor: pointer;
}
.market-card__icon {
  display: flex;
  width: 48px;
  height: 48px;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  background: var(--yb-site-bg, var(--color-bg-1));
  font-size: 22px;
}
.market-card__title {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}
.market-card__title span,
.market-card p,
.market-card__foot {
  color: var(--yb-site-muted, var(--color-text-3));
}
.market-card p {
  margin: 6px 0 10px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.market-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.market-card__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 10px;
  font-size: 12px;
}
.market-pagination {
  margin-top: 20px;
}
@media (max-width: 860px) {
  .market-layout,
  .market-search,
  .market-hero {
    display: grid;
    grid-template-columns: 1fr;
  }
  .market-page__inner {
    width: min(100% - 28px, 1120px);
  }
}
</style>
