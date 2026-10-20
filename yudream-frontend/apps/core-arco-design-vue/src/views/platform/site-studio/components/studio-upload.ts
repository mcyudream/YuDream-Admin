import type { StudioAsset } from '@/types/site-studio'
import { useYdFilePicker } from '@yudream/components'
import apiFiles from '@/api/modules/files'
import { toBackendAssetUrl } from '@/utils/backend-url'

/** 上传到既有 fileApi（module=cms、publicAccess）并登记进站点文档资产 */
export function studioKindOf(mimeType: string, fileName = ''): StudioAsset['kind'] {
  const value = `${mimeType} ${fileName}`.toLowerCase()
  if (value.startsWith('image/') || /\.(?:png|jpe?g|gif|webp|avif)$/.test(value)) {
    return 'image'
  }
  if (value.startsWith('video/') || /\.(?:mp4|webm|mov)$/.test(value)) {
    return 'video'
  }
  if (value.startsWith('audio/') || /\.(?:mp3|wav|ogg|m4a)$/.test(value)) {
    return 'audio'
  }
  if (value.includes('font') || /\.(?:woff2?|ttf|otf)$/.test(value)) {
    return 'font'
  }
  return 'file'
}

export async function uploadStudioAsset(accept: string): Promise<StudioAsset | null> {
  const picker = useYdFilePicker()
  const files = await picker.open({ accept, multiple: false })
  if (!files.length) {
    return null
  }
  const file = files[0]
  if (!file.type || /svg|html|xml|javascript/i.test(file.type)) {
    throw new Error('仅支持可识别的安全媒体资源，不支持 SVG/HTML 或未知类型')
  }
  const data = new FormData()
  data.append('file', file)
  data.append('module', 'cms')
  data.append('publicAccess', 'true')
  let response: Awaited<ReturnType<typeof apiFiles.upload>>
  try {
    response = await apiFiles.upload(data)
  }
  catch {
    // 失败原因已由全局请求提示说明，这里只给出可读的文件级结论
    throw new Error(`上传「${file.name}」失败`)
  }
  const uploaded = response.data
  const url = uploaded.url || ''
  if (!url) {
    throw new Error(`上传文件「${file.name}」后接口未返回可用的媒体地址`)
  }
  return {
    id: uploaded.id,
    name: uploaded.originalName || file.name,
    url: toBackendAssetUrl(url),
    mimeType: uploaded.contentType || file.type || 'application/octet-stream',
    kind: studioKindOf(uploaded.contentType || file.type || '', file.name),
  }
}
