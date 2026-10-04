import type { IconNode } from 'morphicons'
import * as lucideIcons from 'lucide'

/**
 * MorphIcons 第一优先级图标源。
 *
 * MorphIcons 本身是描边图标变形动画引擎（morphicons/vue 的 MorphIcon），
 * 图标数据来自 lucide 数据包的 IconNode。这里全量引入 lucide 图标集
 * （约 2100 个，与 @iconify/json 的 lucide 集合同源同命名）建立
 * kebab-case 名称索引，供 FaIcon 以 `morph:*` / `lucide:*` 名称离线渲染，
 * 名称未命中时由 FaIcon 回退到既有 Iconify 通道。
 */
export const MORPH_ICON_PREFIX = 'morph:'

/** 兼容 lucide 官方命名与 Iconify 的 `lucide:` 集合前缀，二者指向同一数据。 */
const MORPH_NAME_PATTERN = /^(?:i-)?(?:morph|lucide):([A-Za-z0-9-]+)$/

/** 解析 `morph:xxx` / `lucide:xxx` / `i-morph:xxx` / `i-lucide:xxx`，返回 kebab-case 图标名。 */
export function parseMorphIconName(name: string): string | undefined {
  return MORPH_NAME_PATTERN.exec(name)?.[1]?.toLowerCase()
}

function toKebabCase(exportName: string): string {
  return exportName
    .replace(/([A-Z])([A-Z][a-z])/g, '$1-$2')
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/([a-z])(\d)/gi, '$1-$2')
    .replace(/(\d)(?=\d)/g, '$1-')
    .toLowerCase()
}

const nodeIndex = new Map<string, IconNode>()
// 别名与正名共享同一 IconNode 引用（如 Home === House），按引用去重后供选择器展示，
// 保留字典序较大的名称（lucide 改名方向为更长的新名，如 Home -> House）。
const canonicalNames = new Map<IconNode, string>()

for (const [exportName, value] of Object.entries(lucideIcons)) {
  if (!Array.isArray(value)) {
    continue
  }
  const kebabName = toKebabCase(exportName)
  const node = value as unknown as IconNode
  nodeIndex.set(kebabName, node)
  const canonical = canonicalNames.get(node)
  if (!canonical || kebabName > canonical) {
    canonicalNames.set(node, kebabName)
  }
}

export interface MorphIconOption {
  name: string
  keywords: string
}

/** 全量名称索引：别名同样可解析，保证历史/手写名称不失效。 */
export function resolveMorphIconNode(name: string): IconNode | undefined {
  return nodeIndex.get(name)
}

/** 去重后的全量 MorphIcons 清单（供图标选择器枚举），按名称排序。 */
export const MORPH_ICON_CATALOG: MorphIconOption[] = [...canonicalNames.values()]
  .sort((a, b) => a.localeCompare(b))
  .map(name => ({ name: MORPH_ICON_PREFIX + name, keywords: name.replaceAll('-', ' ') }))

/** 选择器常用图标（lucide 命名）。 */
export const MORPH_ICON_FREQUENT: string[] = [
  'morph:house',
  'morph:layout-dashboard',
  'morph:menu',
  'morph:settings',
  'morph:user',
  'morph:users',
  'morph:shield-user',
  'morph:network',
  'morph:gauge',
  'morph:cpu',
  'morph:database',
  'morph:radar',
  'morph:file-text',
  'morph:file-search',
  'morph:log-in',
  'morph:git-branch',
  'morph:square-terminal',
  'morph:wrench',
  'morph:palette',
  'morph:key-round',
  'morph:puzzle',
  'morph:store',
  'morph:book-open',
  'morph:message-circle',
  'morph:bot',
  'morph:sparkles',
  'morph:folder',
  'morph:layout-grid',
  'morph:link',
  'morph:search',
  'morph:bell',
  'morph:circle-question-mark',
  'morph:circle-check',
  'morph:triangle-alert',
  'morph:sun',
  'morph:moon',
  'morph:eye',
  'morph:eye-off',
  'morph:chevron-down',
  'morph:chevrons-left-right',
  'morph:plus',
  'morph:pencil',
  'morph:trash-2',
  'morph:download',
  'morph:upload',
  'morph:refresh-cw',
  'morph:play',
  'morph:pause',
]
