/**
 * 路由尚未就绪时，按 URL 判断是否公开站。必须覆盖启动页：vite-plugin-app-loading
 * 在 Vue/router 之前注入宿主 loading.html，SITE 主题 CSS 只有 media 打开才会
 * 覆盖启动页；不能等 afterEach，否则公开站会先闪宿主动画。
 * 未出现在此清单的插件公开路由会先走宿主启动页，路由就绪后再切 SITE 主题。
 *
 * 根路径 `/` 对未登录访客会重定向到公开站首页，启动页发生在这次跳转之前，
 * 必须按「无 token 的根路径」打开 SITE 主题，否则全局换页动画永远是宿主成分。
 * 已登录访问 `/` 仍是后台工作台，保持宿主默认动画，避免侵入管理端。
 */
const PUBLIC_PATH_PREFIXES = [
  '/login',
  '/register',
  '/setup',
  '/verify-email',
  '/reset-password',
  '/external-login',
  '/pay/result',
  '/site',
  '/embed',
  '/forms',
  '/wiki',
  '/servers',
  '/activities',
  '/timeline',
]

export interface PublicPathSession {
  hasToken?: boolean
}

/** 供测试与启动页闸门复用：hash/html5 两种 history 都认公开前缀。 */
export function isLikelyPublicPath(
  loc: Pick<Location, 'pathname' | 'hash'> = currentLocation(),
  session: PublicPathSession = {},
) {
  if (isPublicPathname(loc.pathname) || isPublicPathname(hashPathname(loc.hash))) {
    return true
  }
  return isGuestRootPath(loc) && !hasSessionToken(session)
}

/**
 * 启动页 / 路由尚未带 meta.public 时，仍按 URL 判断是否公开站。
 * 访客根路径 `/` 会重定向到 /site，若只看 meta.public，SITE CSS 会在淡出前被关掉，结尾闪宿主彩虹方块。
 */
export function isSiteThemeVisible(
  routePublic?: boolean,
  loc: Pick<Location, 'pathname' | 'hash'> = currentLocation(),
  session: PublicPathSession = {},
) {
  return routePublic === true || isLikelyPublicPath(loc, session)
}

function currentLocation(): Pick<Location, 'pathname' | 'hash'> {
  if (typeof window === 'undefined') {
    return { pathname: '/', hash: '' }
  }
  return window.location
}

function hashPathname(hash: string) {
  if (!hash.startsWith('#')) {
    return ''
  }
  return hash.slice(1).split('?')[0] || ''
}

function isPublicPathname(raw: string) {
  const path = normalizePathname(raw)
  return PUBLIC_PATH_PREFIXES.some(prefix => path === prefix || path.startsWith(`${prefix}/`))
}

function isGuestRootPath(loc: Pick<Location, 'pathname' | 'hash'>) {
  const path = normalizePathname(loc.pathname)
  const hashPath = normalizePathname(hashPathname(loc.hash))
  if (path !== '/') {
    return false
  }
  return !hashPath || hashPath === '/'
}

function hasSessionToken(session: PublicPathSession) {
  if (typeof session.hasToken === 'boolean') {
    return session.hasToken
  }
  if (typeof localStorage === 'undefined') {
    return false
  }
  return Boolean(localStorage.getItem('token'))
}

function normalizePathname(raw: string) {
  return (raw.split('?')[0] || '/').replace(/\/+$/, '') || '/'
}
