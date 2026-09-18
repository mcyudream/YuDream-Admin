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

  const baseUrl = import.meta.env.VITE_APP_API_BASEURL?.trim().replace(/\/+$/, '')
  return baseUrl ? `${baseUrl}${url}` : url
}

/**
 * 改写 CMS/主题注入内容（homeHtml、homeCss、页面 cssContent 等）里的后端相对资产地址：
 * CSS `url('/api/...')` 与 HTML `src/href/poster="/api/..."`。开发与生产均同源部署
 * （dev 由 vite `/api` 代理转发，生产由 nginx 反代），原样返回即可。
 * 外链与 data/blob 地址不受影响。
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
