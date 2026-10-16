import type { CSSProperties } from 'vue'
import { toBackendAssetUrl } from '@/utils/backend-url'

/**
 * 空间封面的展示层解析（纯函数，入口页卡片与治理区封面面板共用）。
 *
 * 契约（与后端 WikiCover 一致）：
 * - NONE：无封面。入口页保留按名称哈希的稳定图案占位，明确标注「未设置封面」，不冒充真实封面。
 * - SEEDED：服务端只存稳定 seed，前端必须按 seed 确定性呈现（同一 seed 每次渲染结果一致，不得随机跳变）。
 * - UPLOAD：服务端只存文件对象 ID；展示走显式公开端点 `/api/files/public/{id}/content`。
 *
 * 入参按 unknown 读取：共享 `WikiSpace` 类型暂未声明封面/ACL 字段（该类型由公开端接入方维护），
 * 这里做防御性解析——字段缺失（旧数据/旧响应）一律回落 NONE，不猜测、不抛错。
 */

export type WikiCoverType = 'NONE' | 'SEEDED' | 'UPLOAD'

export interface WikiCoverView {
  type: WikiCoverType
  seed: string
  fileObjectId: string
  alt: string
  /** UPLOAD 封面的公开读取地址；SEEDED/NONE 没有图片地址（由 seed/名称确定性呈现）。 */
  url: string
  /** UPLOAD 文件对象 ID 后端校验通过后的字符串形式，避免 Long 精度丢失。 */
  hasImage: boolean
}

const COVER_TYPES: WikiCoverType[] = ['NONE', 'SEEDED', 'UPLOAD']

function asRecord(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' ? value as Record<string, unknown> : {}
}

function asText(value: unknown): string {
  if (typeof value === 'string') {
    return value
  }
  return value === undefined || value === null ? '' : String(value)
}

function normalizeCoverType(value?: string): WikiCoverType {
  const type = (value || '').trim().toUpperCase()
  return (COVER_TYPES as string[]).includes(type) ? type as WikiCoverType : 'NONE'
}

/** 公开文件内容端点：匿名可读，登录态同样可用。 */
export function wikiPublicFileUrl(fileObjectId?: string): string {
  return fileObjectId ? toBackendAssetUrl(`/api/files/public/${fileObjectId}/content`) : ''
}

/** 解析空间的封面事实；缺字段（旧数据/旧响应）一律按 NONE 处理。 */
export function wikiCoverOf(space?: unknown): WikiCoverView {
  const record = asRecord(space)
  const type = normalizeCoverType(typeof record.coverType === 'string' ? record.coverType : undefined)
  const seed = asText(record.coverSeed)
  const fileObjectId = asText(record.coverFileObjectId)
  const alt = asText(record.coverAlt)
  const url = type === 'UPLOAD' ? wikiPublicFileUrl(fileObjectId) : ''
  return { type, seed, fileObjectId, alt, url, hasImage: Boolean(url) }
}

// 随机封面的确定性调色板：seed 哈希决定底色，同一 seed 永远同一结果。
// 色板取低饱和的中明度色（饱和度 20%–28%）：封面是背景板而不是视觉主体，
// 文字与操作条透过深色遮罩仍可读，也不会压过卡片里的名称为主视觉。
const SEEDED_COVER_GRADIENTS = [
  'linear-gradient(135deg, hsl(215deg 26% 50%), hsl(252deg 22% 44%))',
  'linear-gradient(135deg, hsl(174deg 22% 44%), hsl(202deg 24% 48%))',
  'linear-gradient(135deg, hsl(26deg 28% 50%), hsl(350deg 24% 52%))',
  'linear-gradient(135deg, hsl(258deg 20% 48%), hsl(318deg 20% 50%))',
  'linear-gradient(135deg, hsl(96deg 20% 42%), hsl(168deg 20% 42%))',
  'linear-gradient(135deg, hsl(198deg 24% 48%), hsl(232deg 24% 50%))',
  'linear-gradient(135deg, hsl(340deg 22% 52%), hsl(18deg 26% 50%))',
  'linear-gradient(135deg, hsl(46deg 26% 48%), hsl(96deg 22% 42%))',
]

/** 稳定字符串哈希（djb2 变体）：只用于确定性取色，不作为安全用途。 */
export function stableCoverHash(text: string): number {
  let hash = 5381
  for (let index = 0; index < text.length; index++) {
    hash = (((hash << 5) + hash) ^ text.charCodeAt(index)) >>> 0
  }
  return hash >>> 0
}

/** SEEDED 封面的确定性背景：同一 seed 恒等，刷新不会跳变。 */
export function seededCoverBackground(seed: string): string {
  return SEEDED_COVER_GRADIENTS[stableCoverHash(seed || '') % SEEDED_COVER_GRADIENTS.length]
}

/**
 * 卡片/预览的封面背景样式。
 * - UPLOAD 返回空样式（由 <img> 呈现真实封面）；
 * - SEEDED 用 seed 确定性渐变（低饱和色板，同一 seed 恒等）；
 * - NONE 用名称哈希兜底渐变，供「封面」设置预览呈现「未设置」的占位；
 *   入口页卡片不用这个渐变，空封面在卡片里呈现中性的点阵图案（见 WikiEntryView）。
 */
export function wikiCoverStyle(space?: unknown): CSSProperties {
  const cover = wikiCoverOf(space)
  if (cover.type === 'UPLOAD') {
    return {}
  }
  if (cover.type === 'SEEDED') {
    return { backgroundImage: seededCoverBackground(cover.seed) }
  }
  const record = asRecord(space)
  const fallback = asText(record.slug) || asText(record.name)
  return { backgroundImage: seededCoverBackground(fallback) }
}
