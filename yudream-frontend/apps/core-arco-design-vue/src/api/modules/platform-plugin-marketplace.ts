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
  /** 社区元数据：LOCAL/V2 源携带；静态索引源可能为空。 */
  category?: string
  tags?: string[]
  /** plugin.yml 可选 git 声明的源码仓库地址。 */
  gitUrl?: string
  /** 发布时间（ISO 文本）；静态索引源可能为空。 */
  publishedAt?: string
  /** 最近发布/更新时间（ISO 文本）。 */
  updatedAt?: string
  /** Kept for compatibility with newer backend responses. */
  installable?: boolean
  /** Kept for compatibility with newer backend responses. */
  installDisabledReason?: string
  jar?: PluginStorePluginJar
}

export interface PluginStorePlugin {
  code: string
  descriptor: PluginStorePluginDescriptor
  /** 来源市场源；能力未启用或内置直连时为空。 */
  sourceCode?: string
  sourceName?: string
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
  /** 该版本所在市场源；能力未启用或内置直连时为空。 */
  sourceCode?: string
  sourceName?: string
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
  sourceCode?: string
}

export interface PluginMarketplaceInstallPlanEntry {
  code: string
  displayName?: string
  /** true=硬依赖（必须满足才能安装目标），false=软依赖（可选安装）。 */
  required: boolean
  range?: string
  installed: boolean
  installedVersion?: string
  versionSatisfied: boolean
  storeAvailable: boolean
  storeVersion?: string
  storeSourceCode?: string
  storeSourceName?: string
  /** 候选市场版本自身是否可安装（兼容性与其必需依赖）。 */
  installable: boolean
  installDisabledReason?: string
}

export interface PluginMarketplaceInstallPlan {
  code: string
  releaseVersion: string
  installable: boolean
  installDisabledReason?: string
  /** 依赖条目按「被依赖者在前」排序，批量安装按此顺序执行。 */
  entries: PluginMarketplaceInstallPlanEntry[]
}

export interface PluginMarketplaceBatchInstallItem {
  code: string
  releaseVersion: string
  sourceCode?: string
}

export interface PluginMarketplaceUpdateRequest {
  releaseVersion: string
  sourceCode?: string
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
  rollback: (code: string, cascade = false) => systemClient.post<unknown, ApiResponse<PluginMarketplaceUpdateResult>>(`api/platform/plugin-marketplace/${code}/rollback`, undefined, { params: { cascade } }),
  install: (code: string, data: PluginMarketplaceInstallRequest) => systemClient.post<unknown, ApiResponse<PluginMarketplaceInstallResponse>>(`api/platform/plugin-marketplace/${code}/install`, data),
  installPlan: (code: string, releaseVersion: string, sourceCode?: string) => systemClient.get<unknown, ApiResponse<PluginMarketplaceInstallPlan>>(`api/platform/plugin-marketplace/${code}/install-plan`, { params: { releaseVersion, sourceCode } }),
  installBatch: (items: PluginMarketplaceBatchInstallItem[]) => systemClient.post<unknown, ApiResponse<PluginMarketplaceInstallResponse>>('api/platform/plugin-marketplace/install-batch', { items }),
}
