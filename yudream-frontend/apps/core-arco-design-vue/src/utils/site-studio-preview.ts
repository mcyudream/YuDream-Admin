/**
 * 网站工作台 ↔ 草稿预览页的同源实时通道：工作台广播当前内存文档（含未保存更改），
 * 预览页打开时请求一次最新文档；通道按主题隔离，只在同一浏览器的同源标签页之间传递。
 */
export interface StudioPreviewMessage {
  type: 'document' | 'request'
  themeCode: string
  documentJson?: string
  dirty?: boolean
  revision?: string
}

export function studioPreviewChannelName(themeCode: string) {
  return `yd-site-studio-preview:${themeCode}`
}

export function openStudioPreviewChannel(themeCode: string): BroadcastChannel | null {
  return typeof BroadcastChannel === 'undefined' ? null : new BroadcastChannel(studioPreviewChannelName(themeCode))
}

/** 页面路径与预览地址的 slug 互转：'/' ↔ []，'/about/team' ↔ ['about', 'team'] */
export function studioPagePathToSlug(path: string): string[] {
  return path.split('/').filter(Boolean)
}

export function normalizeStudioPreviewPath(slug: string | string[] | undefined): string {
  const parts = Array.isArray(slug) ? slug : slug ? [slug] : []
  const joined = parts.filter(Boolean).join('/')
  return joined ? `/${joined}`.toLowerCase() : '/'
}
