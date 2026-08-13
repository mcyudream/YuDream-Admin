import type { ApiResponse } from './system-client'
import type { PluginModule } from './platform-plugin'
import systemClient from './system-client'

export interface PluginStorePluginJar {
  mavenCoordinates: string
  url: string
  sha256: string
}

export interface PluginStorePluginCompatibility {
  host?: string
  spi?: string
  frontendSdk?: string
}

export interface PluginStorePluginDependency {
  code: string
  range?: string
  required?: boolean
}

export interface PluginStorePluginPublisher {
  id: string
  name: string
  url?: string
  verified?: boolean
}

export interface PluginStorePluginSource {
  repository: string
  commit?: string
}

export interface PluginStorePluginDescriptor {
  releaseVersion?: string
  code: string
  version: string
  main: string
  displayName?: string
  description?: string
  icon?: string
  screenshots?: string[]
  compatibility?: PluginStorePluginCompatibility
  dependencies?: PluginStorePluginDependency[]
  publisher?: PluginStorePluginPublisher
  source?: PluginStorePluginSource
  license?: string
  releaseNotes?: string
  /** Kept for compatibility with newer backend responses. */
  installable?: boolean
  /** Kept for compatibility with newer backend responses. */
  installDisabledReason?: string
  jar?: PluginStorePluginJar
}

export interface PluginStorePlugin {
  code: string
  descriptor: PluginStorePluginDescriptor
  /** Legacy stores may return descriptor fields directly. */
  releaseVersion?: string
  version?: string
  compatibility?: PluginStorePluginCompatibility
  dependencies?: PluginStorePluginDependency[]
}

export interface PluginStorePluginVersion {
  releaseVersion: string
  descriptor?: PluginStorePluginDescriptor
  /** Newer responses may expose installability beside descriptor. */
  installable?: boolean
  installDisabledReason?: string
  compatibility?: PluginStorePluginCompatibility
  dependencies?: PluginStorePluginDependency[]
  /** Legacy responses may return descriptor fields directly. */
  code?: string
  version?: string
  main?: string
  displayName?: string
  description?: string
  icon?: string
  screenshots?: string[]
  jar?: PluginStorePluginJar
}

export interface PluginMarketplaceInstallResponse extends Array<PluginModule> {}

export interface PluginMarketplaceUpdate {
  code: string
  currentVersion?: string
  latestVersion?: string
  latestReleaseVersion?: string
  latestDisplayName?: string
  updateAvailable: boolean
  compatible: boolean
  blockedReason?: string
}

export interface PluginMarketplaceUpdatePlanDependency {
  code: string
  range?: string
  required: boolean
  warning?: boolean
  warningReason?: string
}

export interface PluginMarketplaceUpdatePlan {
  code: string
  fromVersion: string
  toVersion: string
  changeType: string
  requiredDependencies: PluginMarketplaceUpdatePlanDependency[]
  optionalDependencies: PluginMarketplaceUpdatePlanDependency[]
  affectedEnabledPlugins: string[]
  requiresRestart: boolean
  blockedReason?: string
  warnings: string[]
}

export interface PluginStorePluginDetail {
  code: string
  versions: PluginStorePluginVersion[]
}

export interface PluginMarketplaceInstallRequest {
  releaseVersion: string
}

export interface PluginMarketplaceUpdateRequest {
  releaseVersion: string
}

export interface PluginMarketplaceUpdateResult {
  modules: PluginModule[]
  requiresRestart: boolean
}

export default {
  list: () => systemClient.get<unknown, ApiResponse<PluginStorePlugin[]>>('api/platform/plugin-marketplace'),
  detail: (code: string) => systemClient.get<unknown, ApiResponse<PluginStorePluginDetail>>(`api/platform/plugin-marketplace/${code}`),
  updates: () => systemClient.get<unknown, ApiResponse<PluginMarketplaceUpdate[]>>('api/platform/plugin-marketplace/updates'),
  updatePlans: () => systemClient.get<unknown, ApiResponse<PluginMarketplaceUpdatePlan[]>>('api/platform/plugin-marketplace/update-plan'),
  updatePlan: (code: string, targetVersion?: string) => systemClient.get<unknown, ApiResponse<PluginMarketplaceUpdatePlan>>(`api/platform/plugin-marketplace/${code}/update-plan`, { params: { targetVersion } }),
  update: (code: string, data: PluginMarketplaceUpdateRequest) => systemClient.post<unknown, ApiResponse<PluginMarketplaceUpdateResult>>(`api/platform/plugin-marketplace/${code}/update`, data),
  rollback: (code: string) => systemClient.post<unknown, ApiResponse<PluginMarketplaceUpdateResult>>(`api/platform/plugin-marketplace/${code}/rollback`),
  install: (code: string, data: PluginMarketplaceInstallRequest) => systemClient.post<unknown, ApiResponse<PluginMarketplaceInstallResponse>>(`api/platform/plugin-marketplace/${code}/install`, data),
}
