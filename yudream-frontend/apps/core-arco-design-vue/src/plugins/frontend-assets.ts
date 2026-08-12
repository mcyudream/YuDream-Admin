import type { PluginFrontendModule } from '@/api/modules/platform-plugin'
import apiPlugin from '@/api/modules/platform-plugin'
import { toBackendAssetUrl } from '@/utils/backend-url'

const ASSET_ATTRIBUTE = 'data-yudream-plugin-asset'

export function pluginFrontendAssetUrl(pluginCode: string, path: string) {
  const code = normalizeSegment(pluginCode, '插件编码')
  const assetPath = normalizeAssetPath(path)
  return toBackendAssetUrl(`/api/platform/plugins/${code}/assets/${assetPath}`)
}

export async function loadPluginFrontendAssets(module: Pick<PluginFrontendModule, 'pluginCode' | 'styles' | 'scripts'>) {
  for (const path of module.styles || []) {
    await loadStyle(module.pluginCode, path)
  }
  for (const path of module.scripts || []) {
    await loadModuleScript(module.pluginCode, path)
  }
}

export function loadPluginFrontendAssetsByCode(pluginCode: string) {
  return apiPlugin.frontendManifest()
    .then(res => (res.data.modules || []).find(item => item.pluginCode === pluginCode))
    .then(item => item ? loadPluginFrontendAssets(item) : undefined)
}

function loadStyle(pluginCode: string, path: string) {
  const url = pluginFrontendAssetUrl(pluginCode, path)
  const selector = `link[${ASSET_ATTRIBUTE}="${cssEscape(url)}"]`
  const existing = document.head.querySelector<HTMLLinkElement>(selector)
  if (existing) {
    return awaitAsset(existing)
  }

  const element = document.createElement('link')
  element.rel = 'stylesheet'
  element.href = url
  element.setAttribute(ASSET_ATTRIBUTE, url)
  document.head.appendChild(element)
  return awaitAsset(element)
}

function loadModuleScript(pluginCode: string, path: string) {
  const url = pluginFrontendAssetUrl(pluginCode, path)
  const selector = `script[${ASSET_ATTRIBUTE}="${cssEscape(url)}"]`
  const existing = document.head.querySelector<HTMLScriptElement>(selector)
  if (existing) {
    return awaitAsset(existing)
  }

  const element = document.createElement('script')
  element.type = 'module'
  element.src = url
  element.setAttribute(ASSET_ATTRIBUTE, url)
  document.head.appendChild(element)
  return awaitAsset(element)
}

function awaitAsset(element: HTMLElement) {
  if (element.dataset.yudreamPluginAssetLoaded === 'true') {
    return Promise.resolve()
  }
  if (element.dataset.yudreamPluginAssetFailed === 'true') {
    return Promise.reject(new Error(`插件资源加载失败：${element.getAttribute('src') || element.getAttribute('href')}`))
  }

  return new Promise<void>((resolve, reject) => {
    element.addEventListener('load', () => {
      element.dataset.yudreamPluginAssetLoaded = 'true'
      resolve()
    }, { once: true })
    element.addEventListener('error', () => {
      element.dataset.yudreamPluginAssetFailed = 'true'
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

function cssEscape(value: string) {
  return value.replace(/(["\\])/g, '\\$1')
}
