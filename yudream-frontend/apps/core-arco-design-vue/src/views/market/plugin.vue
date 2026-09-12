<script setup lang="ts">
import type { PluginMarketDetail } from '@/api/modules/plugin-market-public'
import apiMarketPublic, { pluginMarketDownloadUrl } from '@/api/modules/plugin-market-public'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import MarketChrome from './market-chrome.vue'

const route = useRoute()
const router = useRouter()
const appSettingsStore = useAppSettingsStore()

const loading = ref(false)
const detail = ref<PluginMarketDetail | null>(null)
const missing = ref(false)
const code = computed(() => String(route.params.code || ''))
const siteName = computed(() => appSettingsStore.siteName || '插件市场')

onMounted(load)
watch(code, load)
onBeforeUnmount(clearPublicSeo)

async function load() {
  if (!code.value) {
    missing.value = true
    return
  }
  loading.value = true
  missing.value = false
  try {
    detail.value = await apiMarketPublic.plugin(code.value)
    applyPublicSeo({
      title: detail.value.displayName || detail.value.code,
      description: detail.value.description || '插件详情',
      canonicalPath: `/market/${encodeURIComponent(detail.value.code)}`,
      type: 'website',
      siteName: siteName.value,
    })
  }
  catch {
    detail.value = null
    missing.value = true
  }
  finally {
    loading.value = false
  }
}

function formatTime(value?: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

function formatSize(bytes?: number) {
  if (!bytes) {
    return '—'
  }
  return bytes < 1024 * 1024 ? `${(bytes / 1024).toFixed(1)} KB` : `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

function download(version: string) {
  window.open(pluginMarketDownloadUrl(code.value, version), '_blank')
}
</script>

<template>
  <MarketChrome>
    <main class="market-detail">
      <div class="market-detail__inner">
        <button type="button" class="market-back" @click="router.push({ name: 'publicMarketHome' })">
          <FaIcon name="i-ri:arrow-left-line" />
          返回市场
        </button>
        <div v-if="loading" class="market-empty">加载中…</div>
        <div v-else-if="missing || !detail" class="market-empty">未找到该插件。</div>
        <template v-else>
          <header class="market-detail__hero">
            <div>
              <h1>{{ detail.displayName || detail.code }}</h1>
              <p>{{ detail.description || '暂无简介' }}</p>
              <div class="market-card__meta">
                <FaTag v-if="detail.category" variant="secondary">{{ detail.category }}</FaTag>
                <FaTag v-for="tag in (detail.tags || [])" :key="tag" variant="secondary">{{ tag }}</FaTag>
              </div>
            </div>
            <div class="market-detail__stats">
              <div><dt>{{ detail.latestVersion }}</dt><dd>最新版本</dd></div>
              <div><dt>{{ detail.downloads }}</dt><dd>下载</dd></div>
              <div><dt>{{ detail.authorName || '未知' }}</dt><dd>作者</dd></div>
              <div><dt>{{ formatTime(detail.updatedAt || detail.publishedAt) }}</dt><dd>更新</dd></div>
            </div>
          </header>

          <section>
            <h2>版本</h2>
            <div class="market-versions">
              <article v-for="version in [...detail.versions].reverse()" :key="version.version" class="market-version">
                <div>
                  <strong>{{ version.version }}</strong>
                  <p>{{ version.releaseNotes || '无发布说明' }}</p>
                  <span>{{ formatTime(version.publishedAt) }} · {{ formatSize(version.sizeBytes) }} · {{ version.downloads }} 下载<template v-if="version.license"> · {{ version.license }}</template></span>
                </div>
                <FaButton variant="outline" @click="download(version.version)">下载</FaButton>
              </article>
            </div>
          </section>
        </template>
      </div>
    </main>
  </MarketChrome>
</template>

<style scoped>
.market-detail {
  min-height: 100%;
  flex: 1 1 auto;
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.market-detail__inner {
  width: min(920px, calc(100% - 40px));
  margin: 0 auto;
  padding: 32px 0 72px;
}
.market-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  border: 0;
  background: transparent;
  color: var(--yb-site-muted, var(--color-text-3));
  cursor: pointer;
}
.market-detail__hero {
  display: flex;
  justify-content: space-between;
  gap: 24px;
  margin-bottom: 32px;
}
.market-detail__hero h1 {
  margin: 0 0 8px;
  font-size: 32px;
}
.market-detail__hero p {
  margin: 0 0 12px;
  color: var(--yb-site-muted, var(--color-text-3));
}
.market-detail__stats {
  display: flex;
  gap: 10px;
}
.market-detail__stats div {
  min-width: 96px;
  padding: 10px 12px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 8px;
  background: var(--yb-site-surface, var(--color-bg-2));
}
.market-detail__stats dt {
  font-size: 18px;
  font-weight: 700;
}
.market-detail__stats dd {
  margin: 2px 0 0;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
}
.market-versions {
  display: grid;
  gap: 10px;
}
.market-version {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 12px;
  background: var(--yb-site-surface, var(--color-bg-2));
}
.market-version p,
.market-version span,
.market-empty {
  color: var(--yb-site-muted, var(--color-text-3));
}
.market-version p {
  margin: 6px 0;
}
.market-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.market-empty {
  padding: 48px 0;
  text-align: center;
}
@media (max-width: 720px) {
  .market-detail__hero,
  .market-version,
  .market-detail__stats {
    display: grid;
    grid-template-columns: 1fr;
  }
}
</style>
