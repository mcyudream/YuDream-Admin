/**
 * 菜单外链防护：仅 http/https 地址可作为 href / window.open 目标，
 * 其余（javascript:/data: 等任意 scheme）一律返回 undefined，交由调用方降级处理。
 */
export function safeExternalLink(link?: string): string | undefined {
  if (!link) {
    return undefined
  }
  try {
    const url = new URL(link)
    if ((url.protocol === 'http:' || url.protocol === 'https:') && url.hostname) {
      return link
    }
  }
  catch {
    return undefined
  }
  return undefined
}
