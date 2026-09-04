import type { MaybeRefOrGetter } from 'vue'
import { hasPublicWikiSpaces } from '@/api/modules/platform-wiki'

export interface SiteNavigationItem {
  id?: string
  label: string
  url: string
  parentId?: string
  visible?: boolean
  sort?: number
  children?: SiteNavigationItem[]
}

export function parseNavigationItems(value?: string): SiteNavigationItem[] {
  if (!value) {
    return []
  }
  try {
    const parsed = JSON.parse(value) as SiteNavigationItem[]
    return Array.isArray(parsed)
      ? parsed.filter(item => item.visible !== false).sort((a, b) => (a.sort || 0) - (b.sort || 0))
      : []
  }
  catch {
    return []
  }
}

export function buildNavigationTree(items: SiteNavigationItem[]) {
  const itemMap = new Map<string, SiteNavigationItem>()
  const roots: SiteNavigationItem[] = []
  items.forEach((item) => {
    const cloned = { ...item, children: [] }
    if (cloned.id) {
      itemMap.set(cloned.id, cloned)
    }
  })
  items.forEach((item) => {
    const current = item.id ? itemMap.get(item.id) : { ...item, children: [] }
    if (!current) {
      return
    }
    const parent = item.parentId ? itemMap.get(item.parentId) : undefined
    if (parent) {
      parent.children = [...(parent.children || []), current]
    }
    else {
      roots.push(current)
    }
  })
  const sortItems = (list: SiteNavigationItem[]) => {
    list.sort((a, b) => (a.sort || 0) - (b.sort || 0))
    list.forEach(item => item.children?.sort((a, b) => (a.sort || 0) - (b.sort || 0)))
    return list
  }
  return sortItems(roots)
}

export function flattenNavigation(items: SiteNavigationItem[]) {
  return items.flatMap(item => [item, ...(item.children || [])])
}

export function isAuthNavigationUrl(url?: string) {
  const normalized = (url || '').trim().toLowerCase()
  return normalized === '/login' || normalized === '/register' || normalized === '/signup'
}

// 插件声明的站点导航项（@PluginRoute siteNav）：经公开 frontend-manifest 下发，
// 模块级 memo 让站点页与站点 chrome 共享一次请求；devtools 热重载时复位。
let wikiEnabledPromise: Promise<boolean> | null = null
let pluginSiteNavPromise: Promise<SiteNavigationItem[]> | null = null

export function resetSiteNavigationCache() {
  wikiEnabledPromise = null
  pluginSiteNavPromise = null
}

function loadWikiEnabled(): Promise<boolean> {
  wikiEnabledPromise ??= hasPublicWikiSpaces().catch(() => false)
  return wikiEnabledPromise
}

function loadPluginSiteNavItems(): Promise<SiteNavigationItem[]> {
  pluginSiteNavPromise ??= import('@/api/modules/platform-plugin')
    .then(({ default: apiPlugin }) => apiPlugin.frontendManifest())
    .then((res) => {
      const items: SiteNavigationItem[] = []
      for (const module of res.data.modules || []) {
        for (const route of module.routes || []) {
          if (!route.siteNav || !route.publicAccess || !route.path || !route.component) {
            continue
          }
          items.push({
            id: `plugin-${module.pluginCode}-${route.name || route.path}`,
            label: route.title || module.menuTitle || module.pluginCode,
            url: route.path,
            visible: true,
            // 插件导航默认排在 CMS 配置的导航之后、知识库之前；
            // 需要精确排序时在 CMS 导航里手动配置同 URL 条目即可（按 URL 去重，手动配置优先）。
            sort: Number.MAX_SAFE_INTEGER - 1000 + items.length,
          })
        }
      }
      return items
    })
    .catch(() => [])
  return pluginSiteNavPromise
}

/**
 * 站点公开导航：CMS navigationJson + 插件 siteNav 路由 + 知识库入口的合并结果。
 * 供 /site 页面与公开插件页面的站点 chrome 共用。
 */
export function useSiteNavigation(navigationJson: MaybeRefOrGetter<string | undefined>) {
  const wikiEnabled = ref(false)
  const pluginNavItems = ref<SiteNavigationItem[]>([])

  loadWikiEnabled().then(enabled => wikiEnabled.value = enabled)
  loadPluginSiteNavItems().then(items => pluginNavItems.value = items)

  const navigationItems = computed(() => {
    const items = parseNavigationItems(toValue(navigationJson)).filter(item => !isAuthNavigationUrl(item.url))
    const knownUrls = new Set(items.map(item => item.url))
    const merged = [...items, ...pluginNavItems.value.filter(item => !knownUrls.has(item.url) && !isAuthNavigationUrl(item.url))]
    return wikiEnabled.value && !merged.some(item => item.url === '/wiki')
      ? [...merged, { id: 'capability-wiki', label: '知识库', url: '/wiki', visible: true, sort: Number.MAX_SAFE_INTEGER }]
      : merged
  })
  const navigationTree = computed(() => buildNavigationTree(navigationItems.value))
  const footerNavigationItems = computed(() => flattenNavigation(navigationTree.value))

  return {
    navigationItems,
    navigationTree,
    footerNavigationItems,
  }
}
