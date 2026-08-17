/**
 * 站内文件地址的显示层解析：开发环境经 /proxy 前缀访问后端文件内容，生产环境同源直接可用。
 * 仅用于 <img>/预览等直接浏览器请求场景；axios 请求已由 systemClient 的 baseURL 处理。
 */
const devProxyPrefix = import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY ? '/proxy' : ''

export function resolveApiFileUrl(url?: string): string {
  if (!url) {
    return ''
  }
  return devProxyPrefix && url.startsWith('/api/') ? `${devProxyPrefix}${url}` : url
}

/** 将 markdown 中的站内文件地址（图片/链接）改写为当前环境可显示的地址 */
export function rewriteApiFileUrls(markdown: string): string {
  if (!devProxyPrefix || !markdown) {
    return markdown
  }
  return markdown.replaceAll('](/api/', `](${devProxyPrefix}/api/`)
}
