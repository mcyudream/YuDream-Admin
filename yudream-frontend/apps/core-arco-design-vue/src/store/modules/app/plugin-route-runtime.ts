import type { Router } from 'vue-router'
import type { PluginFrontendModule } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'

export interface PluginRuntimeRouteMeta {
  pluginCode: string
  component: string
  entry?: string
  moduleName: string
  sdkVersion?: string
}

export function indexPluginRuntimeModules(modules: PluginFrontendModule[]) {
  const index = new Map<string, PluginFrontendModule>()
  modules.forEach((module) => {
    if (module.pluginCode && module.moduleName) {
      index.set(runtimeModuleKey(module.pluginCode, module.moduleName), module)
    }
  })
  return index
}

export function resolvePluginRuntimeRoute(
  meta: Record<string, unknown> | undefined,
  component: unknown,
  modules: Map<string, PluginFrontendModule>,
  manifestSdkVersion?: string,
): PluginRuntimeRouteMeta | undefined {
  const pluginCode = textValue(meta?.pluginCode)
  const moduleName = textValue(meta?.pluginModuleName)
  const remoteComponent = textValue(component)
  if (!pluginCode || !moduleName || !remoteComponent || remoteComponent === 'Layout') {
    return undefined
  }
  const module = modules.get(runtimeModuleKey(pluginCode, moduleName))
  return {
    pluginCode,
    component: remoteComponent,
    entry: module?.entry,
    moduleName,
    sdkVersion: module?.sdkVersion || manifestSdkVersion,
  }
}

function runtimeModuleKey(pluginCode: string, moduleName: string) {
  return `${pluginCode}:${moduleName}`
}

function textValue(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}

// 匿名可访问的插件公开路由（publicAccess），未登录时注册为顶级路由
let publicPluginRoutesPromise: Promise<string[]> | null = null

export function ensurePublicPluginRoutes(router: Router): Promise<string[]> {
  publicPluginRoutesPromise ??= registerPublicPluginRoutes(router).catch(() => [])
  return publicPluginRoutesPromise
}

async function registerPublicPluginRoutes(router: Router): Promise<string[]> {
  const res = await apiPlugin.frontendManifest()
  const paths: string[] = []
  for (const module of res.data.modules || []) {
    if (!module.pluginCode || !module.moduleName) {
      continue
    }
    for (const route of module.routes || []) {
      if (!route.publicAccess || !route.path || !route.component) {
        continue
      }
      const name = `plugin-public-${module.pluginCode}-${route.name || route.path}`
      if (router.hasRoute(name)) {
        paths.push(route.path)
        continue
      }
      router.addRoute({
        path: route.path,
        name,
        component: () => import('@/views/platform/plugin/runtime-page.vue'),
        meta: {
          public: true,
          title: route.title,
          plugin: {
            pluginCode: module.pluginCode,
            component: route.component,
            entry: module.entry,
            moduleName: module.moduleName,
            sdkVersion: module.sdkVersion || res.data.sdkVersion,
          },
        },
      })
      paths.push(route.path)
    }
  }
  return paths
}
