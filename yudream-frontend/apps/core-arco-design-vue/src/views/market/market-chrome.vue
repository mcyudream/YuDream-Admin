<script setup lang="ts">
import type { HomePageLayout } from '@/api/modules/platform-cms'
import { fetchPublicCmsChrome } from '@/api/modules/platform-cms'
import SiteChrome from '@/views/site/site-chrome.vue'

const chromeHome = ref<HomePageLayout | null>(null)
onMounted(async () => {
  chromeHome.value = await fetchPublicCmsChrome()
})
</script>

<template>
  <SiteChrome :settings="chromeHome?.settings">
    <slot />
  </SiteChrome>
</template>
