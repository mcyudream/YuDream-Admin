<script setup lang="ts">
import type { Component } from 'vue'
import type { YuDreamPluginFrontendModule } from '@yudream/plugin-sdk'
import apiPlugin from '@/api/modules/platform-plugin'
import type { PluginFrontendModule, PluginGlobalWidget } from '@/api/modules/platform-plugin'
import { acquirePluginRemoteModule, type PluginRemoteModuleLease } from '@/plugins/remote-loader'
import { createPluginSdk } from '@/plugins/sdk'
import eventBus from '@/utils/eventBus'

defineOptions({
  name: 'PluginGlobalWidgets',
})

type RemoteModule = YuDreamPluginFrontendModule & Record<string, any>

interface WidgetInstance {
  widget: PluginGlobalWidget
  component: Component
  sdk: ReturnType<typeof createPluginSdk>
  lease: PluginRemoteModuleLease
  reloadKey: number
}

const { auth } = useAppAuth()

const instances = shallowRef<WidgetInstance[]>([])
let loadSequence = 0
let disposed = false

onMounted(() => {
  void loadWidgets()
  eventBus.on('plugin-devtools:remote-reload', handleRemoteReload)
})

onBeforeUnmount(() => {
  disposed = true
  loadSequence += 1
  eventBus.off('plugin-devtools:remote-reload', handleRemoteReload)
  void releaseAll()
})

async function loadWidgets() {
  const sequence = ++loadSequence
  try {
    const manifest = await apiPlugin.frontendManifest()
    if (disposed || sequence !== loadSequence) {
      return
    }
    const widgets = (manifest.data.globalWidgets || [])
      .filter(widget => widget.pluginCode && widget.component)
      .filter(widget => auth(widget.permission || ''))
      .sort((left, right) => (left.sort ?? 500) - (right.sort ?? 500))
    const modules = manifest.data.modules || []
    const next: WidgetInstance[] = []
    for (const widget of widgets) {
      if (disposed || sequence !== loadSequence) {
        break
      }
      const instance = await acquireWidget(widget, modules)
      if (instance) {
        next.push(instance)
      }
    }
    if (disposed || sequence !== loadSequence) {
      await Promise.all(next.map(item => item.lease.release()))
      return
    }
    const previous = instances.value
    instances.value = next
    await Promise.all(previous.map(item => item.lease.release()))
  }
  catch (error) {
    console.warn('[PluginGlobalWidgets] 全局挂件清单加载失败', error)
  }
}

async function acquireWidget(widget: PluginGlobalWidget, modules: PluginFrontendModule[]): Promise<WidgetInstance | null> {
  const module = modules.find(item => item.pluginCode === widget.pluginCode)
  if (!module) {
    console.warn(`[PluginGlobalWidgets] 挂件 ${widget.pluginCode}/${widget.code} 未找到前端模块`)
    return null
  }
  try {
    const lease = await acquirePluginRemoteModule(module)
    const component = resolveWidgetComponent(lease.module, widget.component)
    if (!component) {
      console.warn(`[PluginGlobalWidgets] 插件 ${widget.pluginCode} 未导出挂件组件：${widget.component}`)
      await lease.release()
      return null
    }
    return {
      widget,
      component,
      sdk: createPluginSdk(widget.pluginCode),
      lease,
      reloadKey: 0,
    }
  }
  catch (error) {
    console.warn(`[PluginGlobalWidgets] 挂件 ${widget.pluginCode}/${widget.code} 加载失败`, error)
    return null
  }
}

function resolveWidgetComponent(module: RemoteModule, componentName: string): Component | null {
  const shortName = componentName.includes('/') ? componentName.split('/').pop() || componentName : componentName
  if (componentName && module.routes?.[componentName]) {
    return module.routes[componentName]
  }
  if (shortName && module.routes?.[shortName]) {
    return module.routes[shortName]
  }
  if (componentName && module[componentName]) {
    return module[componentName]
  }
  if (shortName && module[shortName]) {
    return module[shortName]
  }
  if (module.default && typeof module.default === 'object' && 'routes' in module.default) {
    return resolveWidgetComponent(module.default as RemoteModule, componentName)
  }
  return null
}

async function handleRemoteReload(code: string) {
  if (!code || disposed) {
    return
  }
  const targets = instances.value.filter(item => item.widget.pluginCode === code)
  if (targets.length === 0) {
    return
  }
  try {
    const manifest = await apiPlugin.frontendManifest()
    if (disposed) {
      return
    }
    const modules = manifest.data.modules || []
    const next = [...instances.value]
    for (const target of targets) {
      const instance = await acquireWidget(target.widget, modules)
      const index = next.findIndex(item => item === target)
      if (instance) {
        next[index] = instance
      }
      else {
        next.splice(index, 1)
      }
      await target.lease.release()
    }
    instances.value = next
  }
  catch (error) {
    console.warn('[PluginGlobalWidgets] 挂件热重载失败', error)
  }
}

async function releaseAll() {
  const current = instances.value
  instances.value = []
  await Promise.all(current.map(item => item.lease.release()))
}
</script>

<template>
  <div class="plugin-global-widgets" aria-hidden="false">
    <div
      v-for="item in instances"
      :key="`${item.widget.pluginCode}:${item.widget.code}:${item.reloadKey}`"
      class="plugin-global-widget"
      :data-yudream-plugin="item.widget.pluginCode"
      :data-yudream-widget="item.widget.code"
    >
      <component :is="item.component" :sdk="item.sdk" :widget="item.widget" />
    </div>
  </div>
</template>

<style scoped>
.plugin-global-widgets {
  position: fixed;
  inset: 0;
  z-index: 900;
  pointer-events: none;
}

.plugin-global-widget {
  pointer-events: none;
}

.plugin-global-widget > :deep(*) {
  pointer-events: auto;
}
</style>
