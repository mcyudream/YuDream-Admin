/**
 * 公开阅读站的站点主题变量辅助。
 *
 * SiteChrome（含主题 chrome）在 `.site-chrome` 上声明 `--yb-site-*`；页面自身是它的后代，
 * 直接消费这些变量即可跟随站点主题。只有 teleport 到 body 的浮层（如 YdDrawer）脱离继承域，
 * 需要在打开时把当前值复制到浮层根元素上。
 *
 * 注意：不要把 `--yb-site-*` 写成 YD 令牌（`--background` 等）。YD 组件内部按
 * `oklch(var(--token))` 消费，宿主令牌是三通道值；写入 hex 会让整条声明失效并回退到 initial，
 * 比保留宿主令牌更糟。因此这里只做「站点原始值」的复制。
 */
export const WIKI_SITE_TOKENS = [
  '--yb-site-bg',
  '--yb-site-text',
  '--yb-site-heading',
  '--yb-site-muted',
  '--yb-site-caption',
  '--yb-site-nav-text',
  '--yb-site-text-2',
  '--yb-site-border',
  '--yb-site-border-2',
  '--yb-site-surface',
  '--yb-site-hover',
  '--yb-site-primary',
  '--yb-site-primary-text',
  '--yb-site-danger',
]

function readSiteToken(source: HTMLElement, token: string): string {
  return window.getComputedStyle(source).getPropertyValue(token).trim()
}

/**
 * SiteChrome 在主题就绪后会用主题 chrome 重建页面元素，组件绑定到的 ref 可能指向已卸载的旧元素，
 * 因此优先用元素自身的稳定标记找当前挂载中的页面根。
 */
export function resolveWikiPageRoot(bound: HTMLElement | null | undefined, rootSelector: string): HTMLElement | null {
  if (bound?.isConnected) {
    return bound
  }
  return document.querySelector<HTMLElement>(rootSelector)
}

/** 把站点主题变量的当前值复制到脱离继承域的浮层根元素 */
export function copyWikiSiteTokens(target: HTMLElement, source: HTMLElement) {
  for (const token of WIKI_SITE_TOKENS) {
    const value = readSiteToken(source, token)
    if (value) {
      target.style.setProperty(token, value)
    }
  }
}
