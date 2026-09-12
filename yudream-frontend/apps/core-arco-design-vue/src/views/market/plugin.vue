<script setup lang="ts">
import type { PluginMarketDetail } from '@/api/modules/plugin-market-public'
import apiMarketPublic, { pluginMarketDownloadUrl } from '@/api/modules/plugin-market-public'
import { toBackendAssetUrl } from '@/utils/backend-url'
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
const latest = computed(() => detail.value?.versions?.length
  ? [...detail.value.versions].at(-1)
  : undefined)

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

function isImageIcon(icon?: string) {
  return !!icon && (/^https?:\/\//i.test(icon) || icon.startsWith('/') || icon.startsWith('data:'))
}

function iconUrl(icon?: string) {
  return toBackendAssetUrl(icon)
}

function download(version: string) {
  window.open(pluginMarketDownloadUrl(code.value, version), '_blank')
}

function browseAuthor() {
  if (!detail.value?.authorId) {
    return
  }
  void router.push({
    name: 'publicMarketHome',
    query: { authorId: detail.value.authorId, author: detail.value.authorName },
  })
}
</script>

<template>
  <MarketChrome>
    <main class="plugin">
      <div class="plugin__inner">
        <button type="button" class="plugin-back" @click="router.push({ name: 'publicMarketHome' })">
          <FaIcon name="i-ri:arrow-left-line" />
          返回市场
        </button>
        <div v-if="loading" class="plugin-empty">加载中…</div>
        <div v-else-if="missing || !detail" class="plugin-empty">未找到该插件。</div>
        <template v-else>
          <header class="plugin-hero">
            <div class="plugin-hero__icon">
              <img v-if="isImageIcon(detail.icon)" :src="iconUrl(detail.icon)" :alt="detail.displayName || detail.code">
              <FaIcon v-else :name="detail.icon || 'i-ri:puzzle-2-line'" />
            </div>
            <div class="plugin-hero__copy">
              <p class="plugin-code">{{ detail.code }}</p>
              <h1>{{ detail.displayName || detail.code }}</h1>
              <p>{{ detail.description || '暂无简介' }}</p>
              <div class="plugin-meta">
                <span v-if="detail.category">{{ detail.category }}</span>
                <span v-for="tag in (detail.tags || [])" :key="tag">{{ tag }}</span>
              </div>
            </div>
            <div class="plugin-cta">
              <FaButton v-if="latest" @click="download(latest.version)">
                下载 {{ latest.version }}
              </FaButton>
              <p>{{ detail.downloads }} 次下载 · {{ formatTime(detail.updatedAt || detail.publishedAt) }} 更新</p>
            </div>
          </header>

          <section class="plugin-facts">
            <div>
              <dt>{{ detail.latestVersion }}</dt>
              <dd>最新版本</dd>
            </div>
            <div>
              <dt>{{ detail.downloads }}</dt>
              <dd>下载</dd>
            </div>
            <button type="button" @click="browseAuthor">
              <dt>{{ detail.authorName || '未知' }}</dt>
              <dd>作者</dd>
            </button>
            <div>
              <dt>{{ detail.license || '—' }}</dt>
              <dd>许可证</dd>
            </div>
          </section>

          <section>
            <h2>版本</h2>
            <div class="plugin-versions">
              <article v-for="version in [...detail.versions].reverse()" :key="version.version" class="plugin-version">
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
.plugin {
  min-height: 100%;
  flex: 1 1 auto;
  padding-top: var(--neco-page-top, 0px);
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}
.plugin__inner {
  width: min(960px, calc(100% - 40px));
  margin: 0 auto;
  padding: 32px 0 80px;
}
.plugin-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 24px;
  border: 0;
  background: transparent;
  color: var(--yb-site-muted, var(--color-text-3));
  cursor: pointer;
}
.plugin-hero {
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr) auto;
  gap: 20px;
  align-items: start;
  margin-bottom: 28px;
}
.plugin-hero__icon {
  display: flex;
  width: 88px;
  height: 88px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 20px;
  background: var(--yb-site-surface, var(--color-bg-2));
  font-size: 40px;
}
.plugin-hero__icon img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.plugin-code {
  margin: 0 0 6px;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
  letter-spacing: 0.04em;
}
.plugin-hero h1 {
  margin: 0 0 8px;
  font-size: 36px;
  line-height: 1.15;
}
.plugin-hero p {
  margin: 0 0 12px;
  color: var(--yb-site-muted, var(--color-text-3));
  line-height: 1.7;
}
.plugin-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.plugin-meta span {
  padding: 2px 8px;
  border-radius: 999px;
  background: var(--yb-site-surface, var(--color-bg-2));
  font-size: 12px;
}
.plugin-cta {
  display: grid;
  gap: 8px;
  justify-items: end;
}
.plugin-cta p {
  margin: 0;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
}
.plugin-facts {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 36px;
}
.plugin-facts div,
.plugin-facts button {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 12px;
  background: var(--yb-site-surface, var(--color-bg-2));
  color: inherit;
  text-align: left;
}
.plugin-facts button {
  cursor: pointer;
}
.plugin-facts dt {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 18px;
  font-weight: 700;
}
.plugin-facts dd {
  margin: 4px 0 0;
  color: var(--yb-site-muted, var(--color-text-3));
  font-size: 12px;
}
.plugin-versions {
  display: grid;
  gap: 10px;
}
.plugin-version {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 18px;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 14px;
  background: var(--yb-site-surface, var(--color-bg-2));
}
.plugin-version p,
.plugin-version span,
.plugin-empty {
  color: var(--yb-site-muted, var(--color-text-3));
}
.plugin-version p {
  margin: 6px 0;
}
.plugin-empty {
  padding: 64px 0;
  text-align: center;
}
@media (max-width: 820px) {
  .plugin-hero,
  .plugin-facts,
  .plugin-version {
    display: grid;
    grid-template-columns: 1fr;
  }
  .plugin-cta,
  .plugin-cta p {
    justify-items: start;
    text-align: left;
  }
}
</style>
