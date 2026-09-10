export function toBackendAssetUrl(url?: string) {
  if (!url) {
    return ''
  }
  if (/^https?:\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url
  }
  if (!url.startsWith('/api/')) {
    return url
  }
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) {
    return `/proxy${url}`
  }

  const baseUrl = import.meta.env.VITE_APP_API_BASEURL?.trim().replace(/\/+$/, '')
  return baseUrl ? `${baseUrl}${url}` : url
}

/**
 * 改写 CMS/主题注入内容（homeHtml、homeCss、页面 cssContent 等）里的后端相对资产地址：
 * CSS `url('/api/...')` 与 HTML `src/href/poster="/api/..."`，dev 下补 `/proxy` 前缀，
 * 生产同源部署时原样返回。外链与 data/blob 地址不受影响。
 */
export function rewriteBackendAssetUrls(content?: string) {
  if (!content || !content.includes('/api/')) {
    return content || ''
  }
  return content.replace(
    /(url\(\s*['"]?|(?:src|href|poster)\s*=\s*["'])(\/api\/[^'")\s>]+)/g,
    (_match, prefix: string, path: string) => `${prefix}${toBackendAssetUrl(path)}`,
  )
}
