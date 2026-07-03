export function toBackendAssetUrl(url?: string) {
  if (!url) {
    return ''
  }
  if (/^https?:\/\//i.test(url) || url.startsWith('data:') || url.startsWith('blob:')) {
    return url
  }
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY && url.startsWith('/api/')) {
    return `/proxy${url}`
  }
  return url
}
