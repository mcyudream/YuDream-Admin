/**
 * 公开文章正文的图片地址改写（纯函数）。
 *
 * 背景：Wiki 内联图片一律按私有文件保存（工作台 wiki-images.ts 的策略），匿名读
 * `/api/files/{id}/content` 会 401。后端已提供发布态感知代理
 * `GET /api/public/wiki/{spaceSlug}/nodes/{nodeId}/images/{fileId}`：服务端校验
 * 空间已公开 + 节点已发布 + 文件被该发布版本正文引用 + 只输出 image/*。
 *
 * 因此只在“已发布节点”锚点存在时改写正文图片；没有锚点（原文档、未发布、草稿）
 * 一律不改写，服务端继续 fail closed。改写不会放大访问面：被改写出的地址仍由
 * 服务端按“该文件是否被发布正文引用”重新判定。
 */

/** 站内文件地址：兼容绝对 origin 与 dev /proxy 前缀；只认 /api/files/{id}/content，不含缩略图 */
const FILE_CONTENT_URL = /^(?:https?:\/\/[^/\s"'()]+)?(?:\/proxy)?\/api\/files\/(\d+)\/content(?:[?#]\S*)?$/

/** Markdown 图片语法：![alt](url "title") */
const MARKDOWN_IMAGE = /!\[([^\]]*)\]\(\s*([^)\s]+)((?:\s+["'][^"']*["'])?\s*)\)/g

/** HTML img 的 src：<img src="..." /> */
const HTML_IMAGE_SRC = /(<img\s[^>]*?src\s*=\s*)(["'])([^"']*)\2/gi

/** 发布态图片代理地址；与后端 WikiPublicImageAppService.publicImageUrl 同构。 */
export function publicWikiImageProxyPath(spaceSlug: string, nodeId: string, fileId: string): string {
  return `/api/public/wiki/${encodeURIComponent(spaceSlug)}/nodes/${encodeURIComponent(nodeId)}/images/${encodeURIComponent(fileId)}`
}

/** 从站内文件地址解析文件 ID；外链、缩略图与非 content 路径返回 null。 */
export function wikiFileObjectId(url: string | undefined): string | null {
  const matched = FILE_CONTENT_URL.exec((url || '').trim())
  return matched ? matched[1] : null
}

/** 已发布节点锚点下把站内私有图片地址换成公开代理地址；不满足条件时原样返回。 */
export function publishedWikiImageUrl(url: string, spaceSlug: string, nodeId: string): string {
  if (!spaceSlug || !nodeId) {
    return url
  }
  const fileId = wikiFileObjectId(url)
  return fileId ? publicWikiImageProxyPath(spaceSlug, nodeId, fileId) : url
}

/** 改写公开文章正文里图片（Markdown/HTML）的站内文件地址；链接与正文文字不动。 */
export function rewritePublishedWikiImages(markdown: string, spaceSlug: string, nodeId: string): string {
  if (!markdown || !spaceSlug || !nodeId) {
    return markdown
  }
  return markdown
    .replace(MARKDOWN_IMAGE, (match, alt: string, url: string, tail: string) => {
      const rewritten = publishedWikiImageUrl(url, spaceSlug, nodeId)
      return rewritten === url ? match : `![${alt}](${rewritten}${tail})`
    })
    .replace(HTML_IMAGE_SRC, (match, prefix: string, quote: string, url: string) => {
      const rewritten = publishedWikiImageUrl(url, spaceSlug, nodeId)
      return rewritten === url ? match : `${prefix}${quote}${rewritten}${quote}`
    })
}
