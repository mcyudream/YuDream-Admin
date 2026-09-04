<script setup lang="ts">
import { usePluginRemoteComponent } from '@/plugins/use-plugin-remote-component'

const route = useRoute()
const { plugin, sdk, remoteComponent, remoteError, remoteLoading } = usePluginRemoteComponent(route)
</script>

<template>
  <div>
    <FaPageHeader :title="String(route.meta.title || '插件页面')" class="mb-0" />

    <FaPageMain>
      <div v-if="remoteComponent" :data-yudream-plugin="plugin.pluginCode">
        <component
          :is="remoteComponent"
          :sdk="sdk"
          :route="route"
        />
      </div>
      <div v-else class="plugin-runtime-empty">
        <div class="runtime-icon">
          <FaIcon name="i-ri:puzzle-2-line" />
        </div>
        <div>
          <h2>{{ remoteLoading ? '正在加载插件前端' : '插件前端不可用' }}</h2>
          <p>{{ remoteLoading ? '正在获取远程入口并解析页面组件。' : remoteError }}</p>
        </div>
      </div>
    </FaPageMain>
  </div>
</template>

<style scoped>
.plugin-runtime-empty {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 14px;
  align-items: center;
  padding: 18px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.runtime-icon {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 6px;
  background: var(--color-fill-2);
  color: rgb(var(--primary-6));
  font-size: 22px;
}

.plugin-runtime-empty h2 {
  margin: 0 0 4px;
  color: var(--color-text-1);
  font-size: 18px;
  font-weight: 700;
}

.plugin-runtime-empty p {
  margin: 0;
  color: var(--color-text-3);
  font-size: 13px;
}

@media (max-width: 720px) {
  .plugin-runtime-empty {
    grid-template-columns: 1fr;
  }
}
</style>
