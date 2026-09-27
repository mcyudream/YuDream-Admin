/** 站点资产路径（/api/... 或 /upload/...）拼接到域 origin；完整 URL 原样返回。 */
export function resolveAssetUrl(serverUrl: string, path: string | null | undefined): string {
  if (!path) {
    return '';
  }
  if (/^https?:\/\//i.test(path)) {
    return path;
  }
  return `${serverUrl}${path.startsWith('/') ? '' : '/'}${path}`;
}
