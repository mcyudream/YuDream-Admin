<script setup lang="ts">
import type { Component } from 'vue'
import type { YuDreamPluginFrontendModule } from '@yudream/plugin-sdk'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import { createPluginSdk } from '@/plugins/sdk'
import { toBackendAssetUrl } from '@/utils/backend-url'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

const props = defineProps<Props>()

const remoteComponent = shallowRef<Component | null>(null)
const remoteError = ref('')
const remoteLoading = ref(false)
const sdk = computed(() => createPluginSdk(props.card.pluginCode || ''))

watch(() => [props.card.pluginCode, props.card.component], loadRemoteComponent, { immediate: true })

async function loadRemoteComponent() {
  remoteComponent.value = null
  remoteError.value = ''

  if (!props.card.pluginCode) {
    remoteError.value = '插件编码缺失'
    return
  }

  remoteLoading.value = true
  try {
    const entry = `/api/platform/plugins/${props.card.pluginCode}/assets/remoteEntry.js`
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
    remoteError.value = `插件未导出首页卡片：${props.card.component || '-'}`
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
  const component = props.card.component || ''
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
  return null
}

function resolveLoadError(error: any) {
  const detail = error?.message ? `：${error.message}` : ''
  if (import.meta.env.DEV) {
    return `插件卡片加载失败，请确认插件已在独立插件仓完成构建，并将插件 JAR 放入后端 plugins 目录${detail}`
  }
  return `插件卡片加载失败${detail}`
}
</script>

<template>
  <component
    :is="remoteComponent"
    v-if="remoteComponent"
    :sdk="sdk"
    :card="card"
    :on-open="onOpen"
  />
  <div v-else class="dashboard-card__content dashboard-remote-state">
    <FaIcon :name="remoteLoading ? 'i-ri:loader-4-line' : 'i-ri:puzzle-2-line'" :class="{ 'animate-spin': remoteLoading }" />
    <div>
      <strong>{{ remoteLoading ? '正在加载插件卡片' : '插件卡片不可用' }}</strong>
      <span>{{ remoteLoading ? '正在读取插件前端组件。' : remoteError }}</span>
    </div>
    <FaButton v-if="!remoteLoading" variant="outline" size="sm" @click="loadRemoteComponent">
      重试
    </FaButton>
  </div>
</template>

<style scoped>
.dashboard-remote-state {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 96px;
  color: var(--color-text-3);
}

.dashboard-remote-state > :deep(.fa-icon) {
  font-size: 22px;
  color: var(--color-text-3);
}

.dashboard-remote-state div {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.dashboard-remote-state strong {
  color: var(--color-text-1);
  font-size: 13px;
}

.dashboard-remote-state span {
  overflow: hidden;
  color: var(--color-text-3);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@container (max-width: 320px) {
  .dashboard-remote-state {
    grid-template-columns: 1fr;
  }
}
</style>
