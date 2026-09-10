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
    <!-- 远程模块加载中只显示 chrome 内轻量骨架，不清空页头页脚，二次切换走模块缓存零等待 -->
    <div v-else-if="remoteLoading" class="plugin-site-skeleton" aria-hidden="true">
      <div class="plugin-site-skeleton__hero" />
      <div class="plugin-site-skeleton__row" />
      <div class="plugin-site-skeleton__row plugin-site-skeleton__row--short" />
    </div>
    <div v-else class="plugin-site-state">
      <h2>插件前端不可用</h2>
      <p>{{ remoteError }}</p>
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

.plugin-site-skeleton {
  display: flex;
  flex: 1 1 auto;
  flex-direction: column;
  gap: 16px;
  width: min(1240px, calc(100% - 40px));
  margin: 0 auto;
  padding: 40px 0 64px;
}

.plugin-site-skeleton__hero,
.plugin-site-skeleton__row {
  border-radius: 12px;
  background: var(--yb-site-surface, rgb(148 163 184 / 12%));
  animation: plugin-site-skeleton-pulse 1.4s ease-in-out infinite;
}

.plugin-site-skeleton__hero {
  height: 220px;
}

.plugin-site-skeleton__row {
  height: 18px;
}

.plugin-site-skeleton__row--short {
  width: 55%;
}

@keyframes plugin-site-skeleton-pulse {
  0%,
  100% {
    opacity: 1;
  }

  50% {
    opacity: 0.45;
  }
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
