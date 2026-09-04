// 登录跳转目标工具：未登录访问受保护路由时记录目标，登录（含第三方登录）完成后原路返回

// 第三方登录会整页跳转到外部授权页，query 无法随行，先暂存 sessionStorage，回调时取出
const EXTERNAL_LOGIN_REDIRECT_KEY = 'externalLoginRedirect'

// 仅允许站内路径，拒绝协议相对地址（//evil.com）与登录回环
export function sanitizeRedirect(target: unknown, fallback = '/'): string {
  if (typeof target !== 'string' || !target.startsWith('/') || target.startsWith('//')) {
    return fallback
  }
  const path = target.split(/[?#]/, 1)[0]
  if (path === '/login' || path === '/register' || path === '/external-login/callback') {
    return fallback
  }
  return target
}

export function stashExternalLoginRedirect(redirect: string) {
  sessionStorage.setItem(EXTERNAL_LOGIN_REDIRECT_KEY, sanitizeRedirect(redirect))
}

export function consumeExternalLoginRedirect(): string | null {
  const value = sessionStorage.getItem(EXTERNAL_LOGIN_REDIRECT_KEY)
  sessionStorage.removeItem(EXTERNAL_LOGIN_REDIRECT_KEY)
  return value ? sanitizeRedirect(value, '/') : null
}
