import type { SiteDocument, StudioPackage } from '../types/site-studio'
import { cloneStudioJson, isStudioEmbeddedAsset, STUDIO_LIMITS, studioAssert, studioKeys, studioRecord, validateStudioDocument } from './site-studio-validation'
/** References are explicit. This function never fetches an external asset or credentials. */
export function createStudioPackage(document: SiteDocument, kind: StudioPackage['kind'] = 'site'): StudioPackage {
  validateStudioDocument(document)
  studioAssert(kind === 'theme' || kind === 'site', '不支持的包种类')
  const exported = cloneStudioJson(document)
  if (kind === 'theme') {
    const referenced = new Set<string>()
    const collect = (nodes: SiteDocument['theme']['header']) => {
      for (const node of nodes) {
        Object.values(node.attributes).forEach(value => referenced.add(value))
        const styleMaps = [node.styles, node.responsive?.tablet || {}, node.responsive?.mobile || {}]
        for (const styles of styleMaps) {
          for (const value of Object.values(styles)) {
            for (const match of value.matchAll(/url\(\s*(?:"([^"]*)"|'([^']*)'|([^\s"'()]+))\s*\)/gi)) {
              referenced.add(match[1] ?? match[2] ?? match[3] ?? '')
            }
          }
        }
        collect(node.children)
      }
    }
    collect(exported.theme.header)
    collect(exported.theme.footer)
    // 样式类/关键帧里的 url()、字体与站点功能引用的资产随主题包导出
    const styleValues = [
      ...(exported.theme.classes ?? []).flatMap(item => [item.styles, item.responsive?.tablet ?? {}, item.responsive?.mobile ?? {}, ...Object.values(item.states ?? {})]),
      ...(exported.theme.keyframes ?? []).flatMap(item => Object.values(item.frames)),
    ].flatMap(styles => Object.values(styles ?? {}))
    const audio = exported.features?.audio
    const assetIds = new Set([
      ...(exported.theme.fonts ?? []).map(font => font.assetId),
      ...(audio?.tracks ?? []).map(track => track.assetId),
      audio?.clickSoundAssetId,
      audio?.buttonSoundAssetId,
      audio?.toastSoundAssetId,
    ].filter(Boolean))
    for (const asset of exported.assets) {
      if (assetIds.has(asset.id) || styleValues.some(value => value.includes(asset.url))) {
        referenced.add(asset.url)
      }
    }
    for (const value of Object.values(exported.theme.tokens)) {
      for (const asset of exported.assets) {
        if (value.includes(asset.url)) {
          referenced.add(asset.url)
        }
      }
    }
    exported.pages = [{ id: crypto.randomUUID(), name: '主题预览', path: '/', root: [] }]
    exported.assets = exported.assets.filter(asset => referenced.has(asset.url))
  }
  const result: StudioPackage = {
    type: 'yudream-site-package',
    version: 1,
    kind,
    document: exported,
    assets: exported.assets.map(asset => ({ id: asset.id, mode: isStudioEmbeddedAsset(asset) ? 'embedded' : 'reference', url: asset.url, mimeType: asset.mimeType })),
  }
  studioAssert(new TextEncoder().encode(JSON.stringify(result)).length <= STUDIO_LIMITS.bytes, '导出包超过 2 MiB 限制')
  return result
}
export function importStudioPackage(input: unknown): StudioPackage {
  if (typeof input === 'string') {
    studioAssert(new TextEncoder().encode(input).length <= STUDIO_LIMITS.bytes, '导入包超过 2 MiB 限制')
    try {
      input = JSON.parse(input)
    }
    catch {
      throw new Error('导入包不是合法 JSON')
    }
  }
  studioRecord(input)
  studioKeys(input, ['type', 'version', 'kind', 'document', 'assets'])
  studioAssert(input.type === 'yudream-site-package' && input.version === 1 && (input.kind === 'theme' || input.kind === 'site'), '不支持的站点便携包类型或版本')
  validateStudioDocument(input.document)
  const document = cloneStudioJson(input.document)
  const expected: StudioPackage = {
    type: 'yudream-site-package',
    version: 1,
    kind: input.kind,
    document,
    assets: document.assets.map(asset => ({ id: asset.id, mode: isStudioEmbeddedAsset(asset) ? 'embedded' : 'reference', url: asset.url, mimeType: asset.mimeType })),
  }
  studioAssert(Array.isArray(input.assets) && input.assets.length === expected.assets.length, '资产清单缺失或数量不符')
  const seen = new Set<string>()
  for (const asset of input.assets) {
    studioRecord(asset)
    studioKeys(asset, ['id', 'mode', 'url', 'mimeType'])
    const match = expected.assets.find(item => item.id === asset.id)
    studioAssert(match && !seen.has(match.id) && match.mode === asset.mode && match.url === asset.url && match.mimeType === asset.mimeType, '资产清单与文档不一致')
    seen.add(match.id)
  }
  studioAssert(new TextEncoder().encode(JSON.stringify(input)).length <= STUDIO_LIMITS.bytes, '导入包超过 2 MiB 限制')
  return expected
}
export function serializeStudioPackage(document: SiteDocument, kind: StudioPackage['kind'] = 'site') {
  return JSON.stringify(createStudioPackage(document, kind))
}
