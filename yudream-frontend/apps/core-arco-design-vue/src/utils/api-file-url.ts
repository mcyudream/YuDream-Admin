/**
 * 站内文件地址的显示层解析：开发与生产均同源部署（dev 由 vite `/api` 代理转发，
 * 生产由 nginx 反代），站内 `/api/...` 地址原样返回即可。
 * 仅用于 <img>/预览等直接浏览器请求场景；axios 请求已由 systemClient 的 baseURL 处理。
 */
export function resolveApiFileUrl(url?: string): string {
  return url || ''
}

/** 将 markdown 中的站内文件地址（图片/链接）改写为当前环境可显示的地址 */
export function rewriteApiFileUrls(markdown: string): string {
  return markdown
}
