<script setup lang="ts">
import type { HomePageLayout } from '@/api/modules/platform-cms'
import { fetchPublicCmsChrome } from '@/api/modules/platform-cms'
import { applyPublicSeo, clearPublicSeo } from '@/utils/public-seo'
import SiteChrome from './site-chrome.vue'

const route = useRoute()
const chromeHome = ref<HomePageLayout | null>(null)
const failed = ref(false)

const embedUrl = computed(() => {
  const raw = typeof route.query.url === 'string' ? route.query.url.trim() : ''
  try {
    const parsed = new URL(raw)
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return ''
    }
    return parsed.toString()
  }
  catch {
    return ''
  }
})

const embedTitle = computed(() => {
  const title = typeof route.query.title === 'string' ? route.query.title.trim() : ''
  return title || '嵌入页面'
})

onMounted(async () => {
  chromeHome.value = await fetchPublicCmsChrome()
  applyPublicSeo({
    title: embedTitle.value,
    description: embedUrl.value ? `在站点内浏览 ${embedTitle.value}` : '嵌入页面',
    canonicalPath: route.fullPath,
    type: 'website',
  })
})

onBeforeUnmount(clearPublicSeo)

watch(() => route.fullPath, () => {
  failed.value = false
  applyPublicSeo({
    title: embedTitle.value,
    description: embedUrl.value ? `在站点内浏览 ${embedTitle.value}` : '嵌入页面',
    canonicalPath: route.fullPath,
    type: 'website',
  })
})
</script>

<template>
  <SiteChrome :settings="chromeHome?.settings">
    <div class="site-embed">
      <div class="site-embed__frame">
        <header class="site-embed__bar">
          <strong>{{ embedTitle }}</strong>
          <a v-if="embedUrl" :href="embedUrl" target="_blank" rel="noopener noreferrer">新窗口打开</a>
        </header>
        <iframe
          v-if="embedUrl && !failed"
          class="site-embed__iframe"
          :src="embedUrl"
          :title="embedTitle"
          referrerpolicy="no-referrer"
          sandbox="allow-scripts allow-same-origin allow-forms allow-popups allow-popups-to-escape-sandbox"
          @error="failed = true"
        />
        <div v-else class="site-embed__empty">
          <strong>{{ embedUrl ? '该页面无法在站内嵌入' : '未提供有效的外链地址' }}</strong>
          <a v-if="embedUrl" :href="embedUrl" target="_blank" rel="noopener noreferrer">在新窗口打开</a>
        </div>
      </div>
    </div>
  </SiteChrome>
</template>

<style scoped>
.site-embed {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: 0;
  padding: var(--neco-page-top, 24px) clamp(16px, 4vw, 40px) 40px;
  background: var(--yb-site-bg, var(--color-bg-1));
  color: var(--yb-site-text, var(--color-text-1));
}

.site-embed__frame {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-height: min(72vh, 760px);
  overflow: hidden;
  border: 1px solid var(--yb-site-border, var(--color-border-2));
  border-radius: 10px;
  background: var(--yb-site-surface, var(--color-bg-2));
  box-shadow: 0 1px 3px rgb(0 0 0 / 6%);
}

.site-embed__bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--yb-site-border, var(--color-border-2));
  background: color-mix(in srgb, var(--yb-site-hover, var(--color-fill-1)) 70%, transparent);
}

.site-embed__bar strong {
  min-width: 0;
  overflow: hidden;
  color: var(--yb-site-heading, var(--color-text-1));
  text-overflow: ellipsis;
  white-space: nowrap;
}

.site-embed__bar a,
.site-embed__empty a {
  color: var(--yb-site-text-2, var(--color-text-2));
  font-size: 13px;
  text-decoration: none;
}

.site-embed__iframe {
  display: block;
  width: 100%;
  flex: 1 1 auto;
  min-height: 560px;
  border: 0;
  background: #fff;
}

.site-embed__empty {
  display: grid;
  flex: 1 1 auto;
  place-content: center;
  justify-items: center;
  gap: 10px;
  padding: 48px 20px;
  color: var(--yb-site-muted, var(--color-text-3));
  text-align: center;
}

.site-embed__empty strong {
  color: var(--yb-site-heading, var(--color-text-1));
}
</style>
