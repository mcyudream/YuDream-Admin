import type { MaybeRefOrGetter } from 'vue'
import type { SiteNavigationItem, WikiSpaceEntry } from './site-navigation-merge'
import { computed, ref, toValue, watch } from 'vue'
import { hasPublicWikiSpaces } from '@/api/modules/platform-wiki'
import { hasPublicPluginMarket } from '@/api/modules/plugin-market-public'
import { buildNavigationTree, flattenNavigation, manualNavigationItems, mergeSiteNavigation, parseNavigationItems } from './site-navigation-merge'

export type { SiteNavigationItem, WikiSpaceEntry } from './site-navigation-merge'
export { buildNavigationTree, flattenNavigation, isAuthNavigationUrl, isHttpNavigationUrl, isWikiNavigationUrl, manualNavigationItems, mergeSiteNavigation, navigationHref, parseNavigationItems } from './site-navigation-merge'

// 插件声明的站点导航项（@PluginRoute siteNav）：经公开 frontend-manifest 下发，
// 模块级 memo 让站点页与站点 chrome 共享一次请求；devtools 热重载时复位。
let wikiEnabledPromise: Promise<boolean> | null = null
let marketEnabledPromise: Promise<boolean> | null = null
let pluginSiteNavPromise: Promise<SiteNavigationItem[]> | null = null
let enabledPluginsPromise: Promise<string[]> | null = null
let wikiSpacesPromise: Promise<WikiSpaceEntry[]> | null = null

export function resetSiteNavigationCache() {
  enabledPluginsPromise = null
  wikiEnabledPromise = null
  marketEnabledPromise = null
  pluginSiteNavPromise = null
  wikiSpacesPromise = null
}

function loadWikiEnabled(): Promise<boolean> {
  wikiEnabledPromise ??= hasPublicWikiSpaces().catch(() => false)
  return wikiEnabledPromise
}

function loadMarketEnabled(): Promise<boolean> {
  marketEnabledPromise ??= hasPublicPluginMarket().catch(() => false)
  return marketEnabledPromise
}

/** 公开知识空间：知识库条目「自动同步子菜单」的数据来源，失败时按无空间处理 */
function loadWikiSpaceEntries(): Promise<WikiSpaceEntry[]> {
  wikiSpacesPromise ??= import('@/api/modules/platform-wiki')
    .then(({ fetchPublicWikiSpaces }) => fetchPublicWikiSpaces())
    .then((res) => {
      return (res.data || [])
        .map(space => ({ name: space.name, slug: String(space.slug || '') }))
        .filter(space => space.slug)
    })
    .catch(() => [])
  return wikiSpacesPromise
}

function loadPluginSiteNavItems(): Promise<SiteNavigationItem[]> {
  pluginSiteNavPromise ??= import('@/api/modules/platform-plugin')
    .then(({ default: apiPlugin }) => apiPlugin.frontendManifest())
    .then((res) => {
      const items: SiteNavigationItem[] = []
      for (const module of res.data.modules || []) {
        for (const route of module.routes || []) {
          // 带参数的动态路由（如 /activities/:id）套站点外壳但不能作为导航条目
          if (!route.siteNav || !route.publicAccess || !route.path || !route.component || route.path.includes(':')) {
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

/** 手动导航里是否声明了「自动同步知识空间」子菜单：据此决定是否拉取公开空间列表 */
function needsWikiSpaces(navigationJson: string | undefined, extra: SiteNavigationItem[]) {
  const parsed = parseNavigationItems(navigationJson, { keepHidden: true })
  return [...parsed, ...extra].some(item => item.autoChildren)
}

/**
 * 公开站可见的已启用能力：已启用插件（前端清单中的插件编码）与知识库、插件市场公开能力。
 * 站点文档的页面/导航依赖（requires）据此判定是否开放。
 */
export async function loadSiteCapabilities() {
  enabledPluginsPromise ??= import('@/api/modules/platform-plugin')
    .then(({ default: apiPlugin }) => apiPlugin.frontendManifest())
    .then(res => (res.data.modules || []).map(module => module.pluginCode))
    .catch(() => [])
  const [plugins, wiki, market] = await Promise.all([enabledPluginsPromise, loadWikiEnabled(), loadMarketEnabled()])
  return new Set([...plugins.map(code => `plugin:${code}`), ...(wiki ? ['capability:wiki'] : []), ...(market ? ['capability:plugin-market'] : [])])
}

/** 依赖是否全部满足 */
export function studioRequirementsMet(requires: string[] | undefined, available: Set<string>) {
  return (requires ?? []).every(item => available.has(item))
}

/**
 * 自动追加来源的地址集合（首页、插件 siteNav 页面、插件市场与知识库入口）：
 * 导航编辑器据此判断「删除后会自动重新出现」的条目，引导改为显式隐藏。
 */
export async function loadAutoNavigationUrls(): Promise<Set<string>> {
  const [plugins, market, wiki] = await Promise.all([loadPluginSiteNavItems(), loadMarketEnabled(), loadWikiEnabled()])
  const urls = new Set<string>(['/', '/site'])
  plugins.forEach((item) => {
    const url = (item.url || '').trim().replace(/\/+$/, '')
    if (url) {
      urls.add(url)
    }
  })
  if (market) {
    urls.add('/market')
  }
  if (wiki) {
    urls.add('/wiki')
  }
  return urls
}

/**
 * 非组件场景（站点文档运行时）读取合并后的公开导航：与 useSiteNavigation 同一合并规则。
 * appendAuto 为 true 时按 URL 去重追加额外条目、插件 siteNav 页面与市场/知识库入口；
 * 关闭时仅手动清单（隐藏条目不渲染），仍展开知识空间自动子菜单。
 */
export async function loadSiteNavigation(navigationJson?: string, extra: SiteNavigationItem[] = [], options: { appendAuto?: boolean } = {}) {
  const appendAuto = options.appendAuto !== false
  const needsSpaces = needsWikiSpaces(navigationJson, extra)
  const [plugins, market, wiki, spaces] = await Promise.all([
    appendAuto ? loadPluginSiteNavItems() : Promise.resolve([] as SiteNavigationItem[]),
    appendAuto ? loadMarketEnabled() : Promise.resolve(false),
    appendAuto ? loadWikiEnabled() : Promise.resolve(false),
    needsSpaces ? loadWikiSpaceEntries() : Promise.resolve([] as WikiSpaceEntry[]),
  ])
  const base = appendAuto
    ? mergeSiteNavigation(navigationJson, [...extra, ...plugins], market, wiki, spaces)
    : manualNavigationItems(navigationJson, spaces)
  return buildNavigationTree(base)
}

/**
 * 站点公开导航：首页 + CMS navigationJson + 插件 siteNav 路由 + 插件市场 + 知识库入口的合并结果。
 * 供 /site 页面与公开插件页面的站点 chrome 共用。
 */
export function useSiteNavigation(navigationJson: MaybeRefOrGetter<string | undefined>) {
  const wikiEnabled = ref(false)
  const marketEnabled = ref(false)
  const pluginNavItems = ref<SiteNavigationItem[]>([])
  const wikiSpaces = ref<WikiSpaceEntry[]>([])

  loadWikiEnabled().then(enabled => wikiEnabled.value = enabled)
  loadMarketEnabled().then(enabled => marketEnabled.value = enabled)
  loadPluginSiteNavItems().then(items => pluginNavItems.value = items)
  // 仅当导航声明了自动子菜单且知识库开放时才拉取空间列表；草稿异步加载完成后会重新求值
  watch([() => toValue(navigationJson), wikiEnabled], ([value, enabled]) => {
    if (enabled && needsWikiSpaces(value, [])) {
      void loadWikiSpaceEntries().then((spaces) => {
        wikiSpaces.value = spaces
      })
    }
    else {
      wikiSpaces.value = []
    }
  }, { immediate: true })

  const navigationItems = computed(() => mergeSiteNavigation(toValue(navigationJson), pluginNavItems.value, marketEnabled.value, wikiEnabled.value, wikiSpaces.value))
  const navigationTree = computed(() => buildNavigationTree(navigationItems.value))
  const footerNavigationItems = computed(() => flattenNavigation(navigationTree.value))

  return {
    navigationItems,
    navigationTree,
    footerNavigationItems,
  }
}
