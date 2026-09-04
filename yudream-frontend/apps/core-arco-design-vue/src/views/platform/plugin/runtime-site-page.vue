<script setup lang="ts">
import type { HomePageLayout } from '@/api/modules/platform-cms'
import { fetchPublicCmsChrome } from '@/api/modules/platform-cms'
import { usePluginRemoteComponent } from '@/plugins/use-plugin-remote-component'
import SiteChrome from '@/views/site/site-chrome.vue'

// siteNav 公开插件页的宿主：与 /site 共用页头/页脚 chrome，远程组件渲染在站点内容区。
const route = useRoute()
const { plugin, sdk, remoteComponent, remoteError, remoteLoading } = usePluginRemoteComponent(route)

const chromeHome = ref<HomePageLayout | null>(null)
onMounted(async () => {
  chromeHome.value = await fetchPublicCmsChrome()
})
</script>

<template>
  <SiteChrome :settings="chromeHome?.settings">
    <div v-if="remoteComponent" class="plugin-site-page" :data-yudream-plugin="plugin.pluginCode">
      <component
        :is="remoteComponent"
        :sdk="sdk"
        :route="route"
      />
    </div>
    <div v-else class="plugin-site-state">
      <h2>{{ remoteLoading ? '正在加载插件前端' : '插件前端不可用' }}</h2>
      <p>{{ remoteLoading ? '正在获取远程入口并解析页面组件。' : remoteError }}</p>
    </div>
  </SiteChrome>
</template>

<style scoped>
.plugin-site-page {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  min-width: 0;
}

.plugin-site-state {
  display: grid;
  flex: 1 1 auto;
  align-content: center;
  justify-items: center;
  padding: 64px 20px;
  color: var(--yb-site-muted, #64748b);
  text-align: center;
}

.plugin-site-state h2 {
  margin: 0 0 8px;
  color: var(--yb-site-heading, #0f172a);
  font-size: 20px;
}

.plugin-site-state p {
  margin: 0;
  font-size: 14px;
}
</style>
