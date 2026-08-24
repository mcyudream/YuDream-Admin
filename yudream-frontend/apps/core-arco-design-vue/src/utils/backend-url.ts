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
