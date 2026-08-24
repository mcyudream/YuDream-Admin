import type { PluginFrontendModule } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import { toBackendAssetUrl } from '@/utils/backend-url'

const ASSET_ATTRIBUTE = 'data-yudream-plugin-asset'
const PLUGIN_CODE_ATTRIBUTE = 'data-yudream-plugin-code'
const ASSET_PATH_ATTRIBUTE = 'data-yudream-plugin-asset-path'
const ASSET_REVISION_ATTRIBUTE = 'data-yudream-plugin-asset-revision'

type PluginFrontendAssets = Pick<PluginFrontendModule, 'pluginCode' | 'styles' | 'scripts'> & {
  assetRevision?: string
}

type AssetKind = 'style' | 'script'

interface AssetRecord {
  element: HTMLLinkElement | HTMLScriptElement
  kind: AssetKind
  references: number
  promise: Promise<void>
}

export interface PluginFrontendAssetLease {
  release: () => void
}

const assetRegistry = new Map<string, AssetRecord>()
const retainedLeases = new Map<string, PluginFrontendAssetLease>()
const latestModules = new Map<string, PluginFrontendAssets>()

export function pluginFrontendAssetUrl(pluginCode: string, path: string, assetRevision?: string) {
  const code = normalizeSegment(pluginCode, '插件编码')
  const assetPath = normalizeAssetPath(path)
  const url = toBackendAssetUrl(`/api/platform/plugins/${code}/assets/${assetPath}`)
  const revision = normalizeRevision(assetRevision)
  return revision ? appendQuery(url, 'v', revision) : url
}

/** Acquire plugin-declared assets and release them when their consumer unmounts. */
export async function acquirePluginFrontendAssets(module: PluginFrontendAssets): Promise<PluginFrontendAssetLease> {
  const pluginCode = normalizeSegment(module.pluginCode, '插件编码')
  const revision = normalizeRevision(module.assetRevision)
  const acquired: string[] = []

  try {
    for (const path of module.styles || []) {
      acquired.push(await acquireAsset(pluginCode, path, revision, 'style'))
    }
    for (const path of module.scripts || []) {
      acquired.push(await acquireAsset(pluginCode, path, revision, 'script'))
    }
  }
  catch (error) {
    releaseAssets(acquired)
    throw error
  }

  let released = false
  return {
    release() {
      if (released) {
        return
      }
      released = true
      releaseAssets(acquired)
    },
  }
}

/** Compatibility entry point for callers that do not yet own a release lifecycle. */
export async function loadPluginFrontendAssets(module: PluginFrontendAssets) {
  const pluginCode = normalizeSegment(module.pluginCode, '插件编码')
  const lease = await acquirePluginFrontendAssets(module)
  const previous = retainedLeases.get(pluginCode)
  retainedLeases.set(pluginCode, lease)
  latestModules.set(pluginCode, { ...module, pluginCode })
  previous?.release()
}

export function loadPluginFrontendAssetsByCode(pluginCode: string) {
  return apiPlugin.frontendManifest()
    .then(res => (res.data.modules || []).find(item => item.pluginCode === pluginCode))
    .then(item => item ? loadPluginFrontendAssets(item) : undefined)
}

/**
 * Compatibility reload for the developer tools event. New styles load before
 * the previous retained lease is released, so a failed refresh keeps old CSS.
 */
export async function reloadPluginFrontendAssets(pluginCode: string, version: string) {
  const code = normalizeSegment(pluginCode, '插件编码')
  const module = latestModules.get(code)
  if (!module) {
    return
  }
  await loadPluginFrontendAssets({ ...module, assetRevision: version })
}

async function acquireAsset(pluginCode: string, path: string, revision: string, kind: AssetKind) {
  const assetPath = normalizeAssetPath(path)
  const key = `${pluginCode}\u0000${revision}\u0000${kind}\u0000${assetPath}`
  let record = assetRegistry.get(key)
  if (!record) {
    record = createAssetRecord(pluginCode, assetPath, revision, kind)
    assetRegistry.set(key, record)
  }

  try {
    await record.promise
    record.references += 1
    return key
  }
  catch (error) {
    if (assetRegistry.get(key) === record) {
      assetRegistry.delete(key)
      record.element.remove()
    }
    throw error
  }
}

function createAssetRecord(pluginCode: string, path: string, revision: string, kind: AssetKind): AssetRecord {
  const url = pluginFrontendAssetUrl(pluginCode, path, revision)
  const element = kind === 'style'
    ? document.createElement('link')
    : document.createElement('script')

  if (element instanceof HTMLLinkElement) {
    element.rel = 'stylesheet'
    element.href = url
  }
  else {
    element.type = 'module'
    element.src = url
  }
  element.setAttribute(ASSET_ATTRIBUTE, url)
  element.setAttribute(PLUGIN_CODE_ATTRIBUTE, pluginCode)
  element.setAttribute(ASSET_PATH_ATTRIBUTE, path)
  element.setAttribute(ASSET_REVISION_ATTRIBUTE, revision)
  document.head.appendChild(element)

  return {
    element,
    kind,
    references: 0,
    promise: awaitAsset(element),
  }
}

function releaseAssets(keys: string[]) {
  for (const key of keys) {
    const record = assetRegistry.get(key)
    if (!record) {
      continue
    }
    record.references -= 1
    if (record.references <= 0) {
      assetRegistry.delete(key)
      record.element.remove()
    }
  }
}

function awaitAsset(element: HTMLElement) {
  if (element.dataset.yudreamPluginAssetLoaded === 'true') {
    return Promise.resolve()
  }

  return new Promise<void>((resolve, reject) => {
    element.addEventListener('load', () => {
      element.dataset.yudreamPluginAssetLoaded = 'true'
      resolve()
    }, { once: true })
    element.addEventListener('error', () => {
      reject(new Error(`插件资源加载失败：${element.getAttribute('src') || element.getAttribute('href')}`))
    }, { once: true })
  })
}

function normalizeSegment(value: string, label: string) {
  const segment = value.trim()
  if (!segment || segment.includes('/') || segment.includes('\\') || segment.includes('..')) {
    throw new Error(`${label}非法`)
  }
  return segment
}

function normalizeAssetPath(value: string) {
  const path = value.trim()
  if (!path || path.startsWith('/') || path.includes('\\') || path.includes('..')) {
    throw new Error('插件资源路径非法')
  }
  return path
}

function normalizeRevision(value?: string) {
  return value?.trim() || ''
}

function appendQuery(url: string, name: string, value: string) {
  const separator = url.includes('?') ? '&' : '?'
  return `${url}${separator}${encodeURIComponent(name)}=${encodeURIComponent(value)}`
}
