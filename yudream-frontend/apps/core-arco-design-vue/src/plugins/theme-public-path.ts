/**
 * 路由尚未就绪时，按 URL 判断是否公开站。必须覆盖启动页：vite-plugin-app-loading
 * 在 Vue/router 之前注入宿主 loading.html，SITE 主题 CSS 只有 media 打开才会
 * 覆盖启动页；不能等 afterEach，否则公开站会先闪宿主动画。
 * 未出现在此清单的插件公开路由会先走宿主启动页，路由就绪后再切 SITE 主题。
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
  '/forms',
  '/wiki',
  '/servers',
  '/activities',
  '/timeline',
]

/** 供测试与启动页闸门复用：hash/html5 两种 history 都认公开前缀。 */
export function isLikelyPublicPath(loc: Pick<Location, 'pathname' | 'hash'> = currentLocation()) {
  return isPublicPathname(loc.pathname) || isPublicPathname(hashPathname(loc.hash))
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
  const path = (raw.split('?')[0] || '/').replace(/\/+$/, '') || '/'
  return PUBLIC_PATH_PREFIXES.some(prefix => path === prefix || path.startsWith(`${prefix}/`))
}
