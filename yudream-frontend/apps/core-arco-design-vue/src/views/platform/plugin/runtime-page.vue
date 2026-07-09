<script setup lang="ts">
import type { Component } from 'vue'
import type { YuDreamPluginFrontendModule } from '@yudream/plugin-sdk'
import { createPluginSdk } from '@/plugins/sdk'
import { toBackendAssetUrl } from '@/utils/backend-url'

interface PluginRouteMeta {
  pluginCode: string
  component?: string
  entry?: string
  moduleName?: string
  sdkVersion?: string
}

const route = useRoute()
const remoteComponent = shallowRef<Component | null>(null)
const remoteError = ref('')
const remoteLoading = ref(false)

const plugin = computed(() => (route.meta.plugin || {}) as PluginRouteMeta)
const sdk = computed(() => createPluginSdk(plugin.value.pluginCode || ''))

watch(plugin, loadRemoteComponent, { immediate: true })

async function loadRemoteComponent() {
  remoteComponent.value = null
  remoteError.value = ''

  if (!plugin.value.pluginCode) {
    remoteError.value = '插件编码缺失'
    return
  }

  remoteLoading.value = true
  try {
    const entry = plugin.value.entry || `/api/platform/plugins/${plugin.value.pluginCode}/assets/remoteEntry.js`
    const module = await import(/* @vite-ignore */ toBackendAssetUrl(entry))
    await mountPluginModule(module)
  }
  catch (error: any) {
    remoteError.value = resolveLoadError(error)
  }
  finally {
    remoteLoading.value = false
  }
}

async function mountPluginModule(module: YuDreamPluginFrontendModule & Record<string, any>) {
  await installPluginModule(module)
  remoteComponent.value = resolveRemoteComponent(module)
  if (!remoteComponent.value) {
    remoteError.value = `远程入口未导出组件：${plugin.value.component || '-'}`
  }
}

async function installPluginModule(module: YuDreamPluginFrontendModule & Record<string, any>) {
  if (typeof module.install === 'function') {
    await module.install()
  }
  if (module.default && typeof module.default === 'object' && 'install' in module.default && typeof module.default.install === 'function') {
    await module.default.install()
  }
}

function resolveRemoteComponent(module: YuDreamPluginFrontendModule & Record<string, any>): Component | null {
  const component = plugin.value.component || ''
  const routeComponent = component.includes('/') ? component.split('/').pop() || component : component
  if (component && module.routes?.[component]) {
    return module.routes[component]
  }
  if (routeComponent && module.routes?.[routeComponent]) {
    return module.routes[routeComponent]
  }
  if (component && module[component]) {
    return module[component]
  }
  if (routeComponent && module[routeComponent]) {
    return module[routeComponent]
  }
  if (module.default && typeof module.default === 'object' && 'routes' in module.default) {
    return resolveRemoteComponent(module.default as YuDreamPluginFrontendModule & Record<string, any>)
  }
  return (module.default as Component) || null
}

function resolveLoadError(error: any) {
  const detail = error?.message ? `：${error.message}` : ''
  if (import.meta.env.DEV) {
    return `插件前端加载失败，请确认插件已在独立插件仓完成构建，并将插件 JAR 放入后端 plugins 目录${detail}`
  }
  return `远程插件加载失败${detail}`
}
</script>

<template>
  <div>
    <FaPageHeader :title="String(route.meta.title || '插件页面')" class="mb-0" />

    <FaPageMain>
      <component
        :is="remoteComponent"
        v-if="remoteComponent"
        :sdk="sdk"
        :route="route"
      />
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
