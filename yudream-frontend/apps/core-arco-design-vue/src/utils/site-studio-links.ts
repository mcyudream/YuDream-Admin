import type { SiteDocument, StudioPage } from '../types/site-studio'

/**
 * 站点文档链接 → 宿主路由（公开站 iframe 与宿主公开页 Shadow DOM 外壳共用）。
 * 文档内根相对路径映射到站点前缀（/site 或草稿预览前缀）；/wiki、/market、/forms 与带 data-yd-host-link 的
 * 绑定链接（插件页、账号菜单等）指向宿主路由原样跳转；外链与锚点原样返回。
 */
export function resolveStudioHref(href: string, basePath = '/site', hostLink = false) {
  const value = (href || '').trim()
  if (!value) {
    return ''
  }
  if (/^(?:https?:|mailto:|tel:)/i.test(value) || value.startsWith('//') || value.startsWith('#')) {
    return value
  }
  if (hostLink && value.startsWith('/') && value !== '/site' && !value.startsWith('/site/')) {
    return value
  }
  if (value === basePath || value.startsWith(`${basePath}/`) || ['/wiki', '/market', '/forms'].some(prefix => value === prefix || value.startsWith(`${prefix}/`))) {
    return value
  }
  if (basePath !== '/site' && (value === '/site' || value.startsWith('/site/'))) {
    return `${basePath}${value.slice('/site'.length) || '/'}`
  }
  return `${basePath}${value.startsWith('/') ? value : `/${value}`}`
}

/**
 * 按站点路径（不含 /site 前缀）匹配文档页面：精确路径优先，其次动态页面（:参数 段），返回参数值。
 * 例：/activities/abc 匹配页面 /activities/:id，params = { id: 'abc' }。
 */
export function matchStudioPage(document: SiteDocument, sitePath: string): { page: StudioPage, params: Record<string, string> } | null {
  const segments = sitePath.replace(/^\/+|\/+$/g, '').split('/').filter(Boolean)
  const exact = document.pages.find(page => page.path.replace(/^\/+|\/+$/g, '') === segments.join('/'))
  if (exact) {
    return { page: exact, params: {} }
  }
  for (const page of document.pages) {
    const pattern = page.path.replace(/^\/+|\/+$/g, '').split('/').filter(Boolean)
    if (pattern.length !== segments.length || !pattern.some(part => part.startsWith(':'))) {
      continue
    }
    const params: Record<string, string> = {}
    const ok = pattern.every((part, index) => {
      if (part.startsWith(':')) {
        params[part.slice(1)] = decodeURIComponent(segments[index]!)
        return true
      }
      return part === segments[index]
    })
    if (ok) {
      return { page, params }
    }
  }
  return null
}
