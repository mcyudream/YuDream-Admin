import type { AxiosResponse } from 'axios'
import apiFiles from '@/api/modules/files'
import systemClient from '@/api/modules/system-client'
import { toBackendAssetUrl } from '@/utils/backend-url'

/**
 * Wiki 内联图片的文件对象约定。
 *
 * 安全约束（评审修正）：内联图片**始终按私有文件保存**（publicAccess=false）。
 * 不能按知识库公开状态推导可见性——公开知识库里的未发布草稿图片会因此变成匿名可读。
 * 私有文件仅属主或持 system:file:manage 的调用方可读（FileAppService.assertCanRead），
 * 匿名访问一律要求 publicAccess=true，所以私有是最安全的默认值。
 *
 * 已知未决项（需要后端契约，前端无法自行解决）：
 * 公开页展示已发布页面的内联图片需要一个「发布态感知」的读取地址，二者任一：
 *   1) 代理（推荐）：GET /api/public/wiki/{spaceSlug}/nodes/{nodeId}/images/{fileId}
 *      服务端校验 空间公开 + 节点存在已发布版本 + 该文件被该节点正文引用后流式返回；
 *   2) 提升：发布时把正文引用的图片提升为公开、取消发布时回收——需要文件可见性更新接口，
 *      并且必须按节点引用追踪，否则无法回收。
 * 契约落地前，Markdown 里保存的仍是 /api/files/{id}/content（私有、不可匿名读）：
 * 工作台用登录态 blob 预览，公开页的内联图片会显示浏览器默认失败态。
 *
 * 说明：theme-center 的 CmsMarkdownEditor 固定 module=cms + publicAccess=true，
 * 会把私有知识库里的图片变成匿名可读，因此 Wiki 侧不复用它。
 */
export const WIKI_IMAGE_MODULE = 'wiki-image'

/** 单张图片大小上限：超出直接拒绝，避免占用对象存储与上传带宽 */
export const WIKI_IMAGE_MAX_BYTES = 10 * 1024 * 1024

/** 可内联的图片类型；刻意排除 SVG（可携带脚本，后端也按附件下发） */
const WIKI_IMAGE_TYPES = [
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
  'image/bmp',
  'image/avif',
  'image/x-icon',
]

export interface WikiInlineImage {
  url: string
  alt: string
  title: string
}

export async function uploadWikiImage(file: File): Promise<WikiInlineImage> {
  const type = (file.type || '').toLowerCase()
  if (!WIKI_IMAGE_TYPES.includes(type)) {
    throw new Error('仅支持 PNG / JPEG / GIF / WEBP / BMP / AVIF / ICO 图片')
  }
  if (file.size > WIKI_IMAGE_MAX_BYTES) {
    throw new Error(`图片不能超过 ${Math.round(WIKI_IMAGE_MAX_BYTES / 1024 / 1024)}MB`)
  }
  const data = new FormData()
  data.append('file', file)
  data.append('module', WIKI_IMAGE_MODULE)
  // 固定私有：不按空间公开状态推导，避免未发布草稿里的图片被匿名读取
  data.append('publicAccess', 'false')
  const res = await apiFiles.upload(data)
  const url = toBackendAssetUrl(res.data.url)
  if (!url) {
    throw new Error('图片上传后未返回访问地址')
  }
  const name = res.data.originalName || file.name
  return { url, alt: name, title: name }
}

const blobUrls = new Map<string, Promise<string>>()

/**
 * 用登录态取回文件内容并换成本地 blob 地址。
 * 图片是私有文件，img 标签无法携带鉴权头，因此工作台预览必须走登录态读取。
 */
export function authenticatedWikiImageUrl(path: string): Promise<string> {
  const cached = blobUrls.get(path)
  if (cached) {
    return cached
  }
  const pending = systemClient
    .get<Blob, AxiosResponse<Blob>>(path, { responseType: 'blob', skipGlobalToast: true })
    .then(response => URL.createObjectURL(response.data))
    .catch((error) => {
      // 失败不缓存，允许权限变化或网络恢复后重试
      blobUrls.delete(path)
      throw error
    })
  blobUrls.set(path, pending)
  return pending
}

/** 释放已创建的 blob 地址（编辑器卸载时调用，避免内存泄漏） */
export function releaseWikiImageBlobs() {
  for (const pending of blobUrls.values()) {
    void pending
      .then(url => URL.revokeObjectURL(url))
      .catch(() => {
        // 未成功创建的地址无需释放
      })
  }
  blobUrls.clear()
}
