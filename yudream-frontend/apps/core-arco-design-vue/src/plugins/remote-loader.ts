import type { PluginFrontendAssetLease } from './frontend-assets'
import type { PluginFrontendModule } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import { toBackendAssetUrl } from '@/utils/backend-url'
import { acquirePluginFrontendAssets, pluginFrontendAssetUrl } from './frontend-assets'

type RemoteManifest = Pick<PluginFrontendModule, 'pluginCode' | 'entry' | 'styles' | 'scripts'> & {
  assetRevision?: string
}

type RemoteModule = Record<string, any>

interface RemoteRecord {
  module?: RemoteModule
  promise: Promise<RemoteModule>
  references: number
  assets?: PluginFrontendAssetLease
}

export interface PluginRemoteModuleLease {
  module: RemoteModule
  release: () => Promise<void>
}

const remoteRegistry = new Map<string, RemoteRecord>()
const installedModules = new WeakSet<object>()

/**
 * Loads a plugin remote with its declared assets. A cached remote is installed
 * once per live lease group; releasing the final lease invokes optional dispose.
 */
export async function acquirePluginRemoteModule(manifest: RemoteManifest, _options: { forceReload?: boolean } = {}): Promise<PluginRemoteModuleLease> {
  const pluginCode = normalizePluginCode(manifest.pluginCode)
  const revision = manifest.assetRevision?.trim() || ''
  const entryUrl = resolvePluginRemoteEntry(pluginCode, manifest.entry, revision)
  const key = `${pluginCode}\u0000${revision}\u0000${entryUrl}`
  let record = remoteRegistry.get(key)
  if (!record) {
    record = createRemoteRecord(manifest, entryUrl)
    remoteRegistry.set(key, record)
  }

  try {
    const module = await record.promise
    record.references += 1
    let released = false
    return {
      module,
      async release() {
        if (released) {
          return
        }
        released = true
        record!.references -= 1
        if (record!.references > 0) {
          return
        }
        remoteRegistry.delete(key)
        try {
          await disposePluginModule(module)
        }
        finally {
          record!.assets?.release()
        }
      },
    }
  }
  catch (error) {
    if (remoteRegistry.get(key) === record) {
      remoteRegistry.delete(key)
      record.assets?.release()
    }
    throw error
  }
}

export async function acquirePluginRemoteModuleByCode(pluginCode: string, options?: { forceReload?: boolean }) {
  const code = normalizePluginCode(pluginCode)
  const response = await apiPlugin.frontendManifest()
  const manifest = response.data.modules?.find(item => item.pluginCode === code)
  if (!manifest) {
    throw new Error(`未找到插件前端模块：${code}`)
  }
  return acquirePluginRemoteModule(manifest, options)
}

export function resolvePluginRemoteEntry(pluginCode: string, entry?: string, assetRevision?: string) {
  const code = normalizePluginCode(pluginCode)
  const candidate = entry?.trim()
  if (!candidate) {
    return pluginFrontendAssetUrl(code, 'remoteEntry.js', assetRevision)
  }
  if (candidate.startsWith(`/api/platform/plugins/${code}/assets/`)) {
    return appendRevision(toBackendAssetUrl(candidate), assetRevision)
  }
  if (candidate.startsWith('/') || /^[a-z][a-z\d+.-]*:/i.test(candidate)) {
    throw new Error('插件远程入口非法')
  }
  return pluginFrontendAssetUrl(code, candidate, assetRevision)
}

function createRemoteRecord(manifest: RemoteManifest, entryUrl: string): RemoteRecord {
  const record = {} as RemoteRecord
  record.promise = (async () => {
    const assets = await acquirePluginFrontendAssets(manifest)
    record.assets = assets
    try {
      const module = await import(/* @vite-ignore */ entryUrl) as RemoteModule
      await installPluginModule(module)
      record.module = module
      return module
    }
    catch (error) {
      assets.release()
      throw error
    }
  })()
  return record
}

async function installPluginModule(module: RemoteModule) {
  const installable = resolveInstallableModule(module)
  if (!installable || installedModules.has(installable)) {
    return
  }
  if (typeof installable.install === 'function') {
    await installable.install()
  }
  installedModules.add(installable)
}

async function disposePluginModule(module: RemoteModule) {
  const installable = resolveInstallableModule(module)
  if (!installable) {
    return
  }
  try {
    if (typeof installable.dispose === 'function') {
      await installable.dispose()
    }
  }
  finally {
    installedModules.delete(installable)
  }
}

function resolveInstallableModule(module: RemoteModule) {
  if (module && typeof module === 'object' && typeof module.install === 'function') {
    return module
  }
  if (module.default && typeof module.default === 'object') {
    return module.default as RemoteModule
  }
  return module && typeof module === 'object' ? module : undefined
}

function normalizePluginCode(value: string) {
  const code = value.trim()
  if (!code || code.includes('/') || code.includes('\\') || code.includes('..')) {
    throw new Error('插件编码非法')
  }
  return code
}

function appendRevision(url: string, revision?: string) {
  const normalized = revision?.trim()
  if (!normalized) {
    return url
  }
  return `${url}${url.includes('?') ? '&' : '?'}v=${encodeURIComponent(normalized)}`
}
