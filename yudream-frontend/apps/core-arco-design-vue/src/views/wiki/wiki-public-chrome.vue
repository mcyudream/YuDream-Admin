<script setup lang="ts">
import type { HomePageLayout } from '@/api/modules/platform-cms'
import { fetchPublicCmsChrome } from '@/api/modules/platform-cms'
import SiteChrome from '@/views/site/site-chrome.vue'

// 公开知识库与 /site 共用页头/页脚：默认主题走宿主 SiteChrome，激活 SITE 主题时走 chromeComponent。
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
