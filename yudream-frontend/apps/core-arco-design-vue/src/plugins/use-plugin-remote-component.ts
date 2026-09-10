import type { YuDreamPluginFrontendModule } from '@yudream/plugin-sdk'
import type { MaybeRefOrGetter } from 'vue'
import type { Component } from 'vue'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { acquirePluginRemoteModule, type PluginRemoteModuleLease } from '@/plugins/remote-loader'
import { createPluginSdk } from '@/plugins/sdk'
import eventBus from '@/utils/eventBus'

export interface PluginRouteMeta {
  pluginCode: string
  component?: string
  entry?: string
  moduleName?: string
  sdkVersion?: string
  assetRevision?: string
  styles?: string[]
  scripts?: string[]
}

type RemoteModule = YuDreamPluginFrontendModule & Record<string, any>

/**
 * 按路由 meta.plugin 加载插件远程模块并解析页面组件。
 * runtime-page（控制台内嵌）与 runtime-site-page（公开站点 chrome）共用。
 */
export function usePluginRemoteComponent(route: RouteLocationNormalizedLoaded) {
  return usePluginRemoteComponentByMeta(computed(() => (route.meta.plugin || {}) as PluginRouteMeta))
}

/**
 * 按显式 meta 加载远程组件：主题首页/chrome（@PluginTheme homeComponent / chromeComponent）
 * 这类不走路由 meta.plugin 的挂载场景由调用方用 /themes/active 载荷构造 meta。
 */
export function usePluginRemoteComponentByMeta(meta: MaybeRefOrGetter<PluginRouteMeta | undefined>) {
  const remoteComponent = shallowRef<Component | null>(null)
  const remoteError = ref('')
  const remoteLoading = ref(false)
  const remoteLease = shallowRef<PluginRemoteModuleLease | null>(null)
  let loadSequence = 0

  const plugin = computed(() => toValue(meta) || { pluginCode: '' })
  const sdk = computed(() => createPluginSdk(plugin.value.pluginCode || ''))

  watch(plugin, () => void loadRemoteComponent(), { immediate: true })

  onMounted(() => {
    eventBus.on('plugin-devtools:remote-reload', handleRemoteReload)
  })

  onBeforeUnmount(() => {
    loadSequence += 1
    eventBus.off('plugin-devtools:remote-reload', handleRemoteReload)
    void releaseRemoteModule()
  })

  async function handleRemoteReload(code: string) {
    if (code && code === plugin.value.pluginCode) {
      await loadRemoteComponent()
    }
  }

  async function loadRemoteComponent() {
    const sequence = ++loadSequence
    remoteComponent.value = null
    remoteError.value = ''

    if (!plugin.value.pluginCode) {
      remoteError.value = '插件编码缺失'
      return
    }

    remoteLoading.value = true
    await releaseRemoteModule()
    try {
      const lease = await acquirePluginRemoteModule(plugin.value)
      if (sequence !== loadSequence) {
        await lease.release()
        return
      }
      remoteLease.value = lease
      remoteComponent.value = resolveRemoteComponent(lease.module)
      if (!remoteComponent.value) {
        remoteError.value = `远程入口未导出组件：${plugin.value.component || '-'}`
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
      return resolveRemoteComponent(module.default as RemoteModule)
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

  return {
    plugin,
    sdk,
    remoteComponent,
    remoteError,
    remoteLoading,
  }
}
