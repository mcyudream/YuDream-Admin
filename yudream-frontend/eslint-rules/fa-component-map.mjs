/** Strict YD-only conformance primitives shared by ESLint and audit:ui. */
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { scanUiSyntax } from './ui-source-scanner.mjs'

const FRONTEND_ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const RESOLVER_PATH = path.join(FRONTEND_ROOT, 'packages/components/resolver.ts')

export function getPublicYdNames() {
  const source = readFileSync(RESOLVER_PATH, 'utf8')
  return new Set([...source.matchAll(/'((?:Yd|useYd)[A-Za-z0-9]+)'/g)].map(match => match[1]))
}

export const YD_PUBLIC_NAMES = getPublicYdNames()
export const THIRD_PARTY_UI_SOURCES = [
  '@arco-design/',
  'reka-ui',
  'shadcn-vue',
  'radix-vue',
  'element-plus',
  'ant-design-vue',
  'naive-ui',
  'vuetify',
  'primevue',
  'bootstrap-vue',
  'tdesign-vue',
]
export const NATIVE_INTERACTIVE_TAGS = new Set(['button', 'input', 'select', 'textarea', 'option'])
export const CLICKABLE_STRUCTURE_TAGS = new Set(['div', 'span', 'li', 'p', 'section', 'article'])

const IMPLEMENTATION_BOUNDARIES = [
  /^packages\/components\/src\//,
  /^packages\/plugin-sdk\/src\/host-components\.ts$/,
  /^apps\/[^/]+\/src\/(?:main|bootstrap|ui\/provider)\.[jt]sx?$/,
  /^apps\/[^/]+\/src\/ui\/YdUiProvider\.vue$/,
  /^apps\/core-arco-design-vue\/src\/ui\/provider\/index\.(?:ts|vue)$/,
  /^apps\/core-arco-design-vue\/src\/ui\/shadcn\/ui\//,
  /^apps\/[^/]+\/src\/types\/(?:components|auto-imports)\.d\.ts$/,
  /^apps\/core-arco-design-vue\/src\/assets\/styles\/dashboard-appearance\.css$/,
]

export function isImplementationBoundary(file) {
  const normalized = file.replaceAll('\\', '/').replace(/^.*?yudream-frontend\//, '')
  return IMPLEMENTATION_BOUNDARIES.some(pattern => pattern.test(normalized))
}

export function scanUiSource(text, file = '') {
  if (isImplementationBoundary(file) || /\.(?:css|scss)$/.test(file)) {
    return []
  }
  return scanUiSyntax(text, file, { publicNames: YD_PUBLIC_NAMES, thirdPartySources: THIRD_PARTY_UI_SOURCES })
}
