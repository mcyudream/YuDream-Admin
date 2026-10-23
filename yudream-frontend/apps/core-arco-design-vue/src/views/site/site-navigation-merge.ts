/**
 * 站点导航合并的纯函数部分：无 Vue 与接口依赖，供站点 chrome、站点文档运行时与单元测试共用。
 * 合并规则：首页 + 手动导航（含显式隐藏条目）+ 知识空间自动子菜单 + 插件 siteNav 页面 + 插件市场/知识库入口。
 */
export interface SiteNavigationItem {
  id?: string
  label: string
  url: string
  parentId?: string
  visible?: boolean
  sort?: number
  /** 外链在公开站内容区 iframe 嵌入，而不是新窗口打开 */
  embed?: boolean
  /** 图标地址（站点文档导航带入；CMS 旧导航无此字段） */
  icon?: string
  /** 子菜单自动同步知识库公开空间（仅知识库入口有意义）；手动子菜单仍优先 */
  autoChildren?: boolean
  children?: SiteNavigationItem[]
}

/** 公开知识空间（自动子菜单的数据来源） */
export interface WikiSpaceEntry {
  name: string
  slug: string
}

export function parseNavigationItems(value?: string, options: { keepHidden?: boolean } = {}): SiteNavigationItem[] {
  if (!value) {
    return []
  }
  try {
    const parsed = JSON.parse(value) as SiteNavigationItem[]
    return Array.isArray(parsed)
      ? parsed
          .filter(item => options.keepHidden || item.visible !== false)
          .sort((a, b) => (a.sort || 0) - (b.sort || 0))
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
    if (item.parentId && !parent) {
      // 父级被隐藏/删除（如登录等认证项被公开导航过滤）时，二级菜单整体隐藏，禁止平铺成一级
      return
    }
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

export function isHttpNavigationUrl(url?: string) {
  return /^https?:\/\//i.test((url || '').trim())
}

/** 勾选了站内嵌入的 http(s) 外链改写为 /embed，chrome 按站内路由无感跳转。 */
export function navigationHref(item: Pick<SiteNavigationItem, 'url' | 'label' | 'embed'>) {
  const url = (item.url || '').trim()
  if (!item.embed || !isHttpNavigationUrl(url)) {
    return url
  }
  const params = new URLSearchParams({ url })
  if (item.label) {
    params.set('title', item.label)
  }
  return `/embed?${params.toString()}`
}

function normalizedUrl(url?: string) {
  return (url || '').trim().replace(/\/+$/, '') || '/'
}

/** 是否知识库入口：自动子菜单只挂在这一类条目下 */
export function isWikiNavigationUrl(url?: string) {
  return normalizedUrl(url) === '/wiki'
}

/**
 * 展开「自动同步知识空间」的子菜单：挂在开启 autoChildren 的知识库条目下，
 * 手动子菜单优先、按地址去重，空间条目排在其后；父级无 ID 或已隐藏时不展开。
 */
function expandWikiSpaceChildren(entries: SiteNavigationItem[], wikiSpaces: WikiSpaceEntry[], knownUrls: Set<string>) {
  const children: SiteNavigationItem[] = []
  for (const entry of entries) {
    if (!entry.autoChildren || entry.visible === false || entry.parentId || !entry.id || !isWikiNavigationUrl(entry.url)) {
      continue
    }
    wikiSpaces.forEach((space, index) => {
      const url = `/wiki/${encodeURIComponent(space.slug)}`
      if (!space.slug || knownUrls.has(url) || isAuthNavigationUrl(url)) {
        return
      }
      knownUrls.add(url)
      children.push({
        id: `${entry.id}-space-${space.slug.replace(/\W/g, '') || index}`,
        parentId: entry.id,
        label: space.name,
        url,
        visible: true,
        // 手动子菜单按 0 起排，空间条目固定排在其后
        sort: 1000 + index,
      })
    })
  }
  return children
}

function isHomeNavigationUrl(url?: string) {
  const normalized = (url || '').trim()
  return normalized === '/site' || normalized === '/'
}

/** 公开站导航必须含首页：CMS/插件清单经常只有内页，品牌链接不能替代导航项。 */
function withHomeNavigationItem(items: SiteNavigationItem[]) {
  if (items.some(item => isHomeNavigationUrl(item.url))) {
    return items
  }
  return [
    { id: 'site-home', label: '首页', url: '/site', visible: true, sort: Number.MIN_SAFE_INTEGER },
    ...items,
  ]
}

/** 嵌套 children 展平为 parentId 关联的扁平清单（已扁平的输入原样保留） */
function flattenEntries(items: SiteNavigationItem[], parentId?: string): SiteNavigationItem[] {
  return items.flatMap((item) => {
    const { children, ...rest } = item
    const entry: SiteNavigationItem = parentId ? { ...rest, parentId } : rest
    return [entry, ...flattenEntries(children ?? [], rest.id)]
  })
}

/**
 * 解析手动导航并展开「自动同步知识空间」子菜单：
 * 返回应渲染的条目（显式隐藏条目已剔除）、自动子菜单与全量地址集合（含隐藏条目，用于去重）。
 */
function collectEntries(navigationJson: string | undefined, wikiSpaces: WikiSpaceEntry[]) {
  const entries = flattenEntries(parseNavigationItems(navigationJson, { keepHidden: true }))
    .filter(item => !isAuthNavigationUrl(item.url))
    .map(item => ({ ...item, url: navigationHref(item) }))
  const knownUrls = new Set(entries.map(item => normalizedUrl(item.url)))
  const autoChildren = expandWikiSpaceChildren(entries, wikiSpaces, knownUrls)
  const display = entries.filter(item => item.visible !== false)
  return { display, autoChildren, knownUrls }
}

/** 手动导航的展示清单（appendAuto 关闭时）：隐藏条目不渲染，仍展开知识空间自动子菜单，不追加插件/能力入口 */
export function manualNavigationItems(navigationJson: string | undefined, wikiSpaces: WikiSpaceEntry[] = []) {
  const { display, autoChildren } = collectEntries(navigationJson, wikiSpaces)
  return [...display, ...autoChildren]
}

/**
 * 公开站导航合并：手动导航（含显式隐藏条目）+ 知识空间自动子菜单 + 插件 siteNav + 插件市场/知识库入口。
 * 显式隐藏（visible=false）的手动条目不渲染，但其地址仍参与去重——用于压制同地址的自动追加条目；
 * 手动配置优先于自动追加（按 URL 去重）。
 */
export function mergeSiteNavigation(
  navigationJson: string | undefined,
  pluginNavItems: SiteNavigationItem[],
  marketEnabled: boolean,
  wikiEnabled: boolean,
  wikiSpaces: WikiSpaceEntry[] = [],
) {
  const { display, autoChildren, knownUrls } = collectEntries(navigationJson, wikiSpaces)
  const isKnown = (url: string) => knownUrls.has(normalizedUrl(url))
  const merged = [...display, ...autoChildren, ...pluginNavItems.filter(item => !isKnown(item.url) && !isAuthNavigationUrl(item.url))]
  // 能力入口与首页的追加也看隐藏条目：显式隐藏（visible=false）同样起到压制作用
  const withMarket = marketEnabled && !isKnown('/market')
    ? [...merged, { id: 'capability-market', label: '插件市场', url: '/market', visible: true, sort: Number.MAX_SAFE_INTEGER - 1 }]
    : merged
  const withWiki = wikiEnabled && !isKnown('/wiki')
    ? [...withMarket, { id: 'capability-wiki', label: '知识库', url: '/wiki', visible: true, sort: Number.MAX_SAFE_INTEGER }]
    : withMarket
  return isKnown('/') || isKnown('/site') ? withWiki : withHomeNavigationItem(withWiki)
}
