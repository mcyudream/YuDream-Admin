<script setup lang="ts">
import type { Component } from 'vue'
import type { YuDreamPluginFrontendModule } from '@yudream/plugin-sdk'
import apiPlugin from '@/api/modules/platform-plugin'
import type { DashboardCard } from '@/api/modules/system-dashboard'
import { acquirePluginRemoteModule, type PluginRemoteModuleLease } from '@/plugins/remote-loader'
import { createPluginSdk } from '@/plugins/sdk'

interface Props {
  card: DashboardCard
  onOpen?: (card?: DashboardCard) => void
}

type RemoteModule = YuDreamPluginFrontendModule & Record<string, any>

const props = defineProps<Props>()

const remoteComponent = shallowRef<Component | null>(null)
const remoteError = ref('')
const remoteLoading = ref(false)
const remoteLease = shallowRef<PluginRemoteModuleLease | null>(null)
let loadSequence = 0
const sdk = computed(() => createPluginSdk(props.card.pluginCode || ''))

watch(() => [props.card.pluginCode, props.card.component], () => void loadRemoteComponent(), { immediate: true })

onBeforeUnmount(() => {
  loadSequence += 1
  void releaseRemoteModule()
})

async function loadRemoteComponent() {
  const sequence = ++loadSequence
  remoteComponent.value = null
  remoteError.value = ''

  if (!props.card.pluginCode) {
    remoteError.value = '插件编码缺失'
    return
  }

  remoteLoading.value = true
  await releaseRemoteModule()
  try {
    const manifest = await apiPlugin.frontendManifest()
    const module = (manifest.data.modules || []).find(item => item.pluginCode === props.card.pluginCode)
    if (!module) {
      throw new Error('未找到插件前端模块')
    }
    const lease = await acquirePluginRemoteModule(module)
    if (sequence !== loadSequence) {
      await lease.release()
      return
    }
    remoteLease.value = lease
    remoteComponent.value = resolveRemoteComponent(lease.module)
    if (!remoteComponent.value) {
      remoteError.value = `插件未导出首页卡片：${props.card.component || '-'}`
      await releaseRemoteModule()
    }
  }
  catch (error: any) {
    if (sequence === loadSequence) {
      remoteError.value = resolveLoadError(error)
    }
  }
  finally {
    if (sequence === loadSequence) {
      remoteLoading.value = false
    }
  }
}

async function releaseRemoteModule() {
  const lease = remoteLease.value
  remoteLease.value = null
  await lease?.release()
}

function resolveRemoteComponent(module: RemoteModule): Component | null {
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
    return resolveRemoteComponent(module.default as RemoteModule)
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
  <div v-if="remoteComponent" :data-yudream-plugin="card.pluginCode">
    <component
      :is="remoteComponent"
      :sdk="sdk"
      :card="card"
      :on-open="onOpen"
    />
  </div>
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
