import type { PluginFrontendModule } from '@/api/modules/platform-plugin'

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
