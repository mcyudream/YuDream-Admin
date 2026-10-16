import type { ApiResponse, PageResult } from '@/api/modules/system-client'
import systemClient from '@/api/modules/system-client'

/**
 * 治理端局部接口适配层：只覆盖后端已实现的「空间封面 / 空间成员 ACL / 评论审核」端点。
 *
 * 为什么单独放这里而不是 `@/api/modules/platform-wiki.ts`：该共享模块同时被公开端接入方改动，
 * 为避免并行改动冲突，本文件只声明治理面板真正用到的类型与调用，路径与后端契约一一对应：
 *
 * - `PUT    /api/platform/wiki/spaces/{spaceId}/cover`（coverType=NONE|SEEDED|UPLOAD）
 * - `POST   /api/platform/wiki/spaces/{spaceId}/cover/random?alt=`
 * - `DELETE /api/platform/wiki/spaces/{spaceId}/cover`
 * - `GET    /api/platform/wiki/spaces/{spaceId}/members?page=&size=`（PageResult）
 * - `POST   /api/platform/wiki/spaces/{spaceId}/members`
 * - `PUT    /api/platform/wiki/spaces/{spaceId}/members/{memberId}`（actions + expectedVersion CAS）
 * - `DELETE /api/platform/wiki/spaces/{spaceId}/members/{memberId}`
 * - `POST   /api/platform/wiki/spaces/{spaceId}/acl/restricted`（显式启用受限 ACL，幂等）
 * - `GET    /api/platform/wiki/spaces/{spaceId}/comments?page=&size=&status=`（PageResult）
 * - `POST   /api/platform/wiki/comments/{commentId}/{publish|hide|restore|delete|pin|unpin}`
 *
 * 所有 ID 一律保持字符串（雪花 ID 禁止 Number()），分页参数由后端归一（page>=1、size<=100）。
 */

export type WikiSpaceCoverType = 'NONE' | 'SEEDED' | 'UPLOAD'
export type WikiSpaceMemberSubjectType = 'USER' | 'ROLE'
export type WikiSpaceAclAction = 'READ' | 'COMMENT' | 'EDIT' | 'MANAGE'
export type WikiSpaceAclMode = 'LEGACY' | 'RESTRICTED'
export type WikiCommentStatus = 'PENDING' | 'PUBLISHED' | 'HIDDEN' | 'DELETED'
export type WikiCommentModerationAction = 'publish' | 'hide' | 'restore' | 'delete' | 'pin' | 'unpin'

/** 空间上由后端返回、但共享 WikiSpace 类型暂未声明的封面/ACL 字段（运行时由服务端 DTO 提供）。 */
export interface WikiSpaceCoverFields {
  coverType?: string
  coverSeed?: string
  coverFileObjectId?: string
  coverAlt?: string
  aclMode?: string
  commentEnabled?: boolean
}

/** 封面更新请求体（request 字段名与服务端 WikiSpaceCoverRequest 一致）。 */
export interface WikiSpaceCoverPayload {
  coverType: WikiSpaceCoverType
  /** UPLOAD 必填：本人上传且公开可读的图片文件对象 ID（字符串）。 */
  fileObjectId?: string
  /** SEEDED 必填：稳定 seed（小写字母与数字）。 */
  seed?: string
  alt?: string
}

/** 成员授权视图（WikiSpaceMemberRes）。 */
export interface WikiSpaceMember {
  id: string
  spaceId: string
  subjectType: string
  subjectId: string
  subjectName?: string
  actions: string[]
  createdByUserId?: string
  /** CAS 令牌：调整授权必须原样回传，否则后端拒绝。 */
  version: number
}

export interface WikiSpaceMemberCreatePayload {
  subjectType: WikiSpaceMemberSubjectType
  subjectId: string
  actions: WikiSpaceAclAction[]
}

export interface WikiSpaceMemberUpdatePayload {
  actions: WikiSpaceAclAction[]
  expectedVersion: number
}

/** 评论视图（WikiCommentRes）。治理端列表不返回回复线程（replies 恒为空数组）。 */
export interface WikiComment {
  id: string
  spaceId: string
  nodeId?: string
  parentId?: string
  authorType?: string
  authorUserId?: string
  authorName?: string
  /** 纯文本正文，展示端不得解析 HTML。 */
  content: string
  status: string
  pinned: boolean
  reviewedByUserId?: string
  reviewedAt?: string
  createTime?: string
  updateTime?: string
  replies?: WikiComment[]
}

export interface WikiCommentPageParams {
  page: number
  size: number
  status?: WikiCommentStatus
}

// —— 空间封面 ——

export function updateWikiSpaceCover(spaceId: string, data: WikiSpaceCoverPayload) {
  return systemClient.put<unknown, ApiResponse<WikiSpaceCoverFields>>(`api/platform/wiki/spaces/${spaceId}/cover`, data)
}

export function randomWikiSpaceCover(spaceId: string, alt?: string) {
  const query = alt ? `?alt=${encodeURIComponent(alt)}` : ''
  return systemClient.post<unknown, ApiResponse<WikiSpaceCoverFields>>(`api/platform/wiki/spaces/${spaceId}/cover/random${query}`)
}

export function clearWikiSpaceCover(spaceId: string) {
  return systemClient.delete<unknown, ApiResponse<WikiSpaceCoverFields>>(`api/platform/wiki/spaces/${spaceId}/cover`)
}

// —— 空间成员 ACL ——

export function fetchWikiSpaceMembers(spaceId: string, params: { page: number, size: number }) {
  return systemClient.get<unknown, ApiResponse<PageResult<WikiSpaceMember>>>(`api/platform/wiki/spaces/${spaceId}/members`, { params })
}

export function createWikiSpaceMember(spaceId: string, data: WikiSpaceMemberCreatePayload) {
  return systemClient.post<unknown, ApiResponse<WikiSpaceMember>>(`api/platform/wiki/spaces/${spaceId}/members`, data)
}

export function updateWikiSpaceMember(spaceId: string, memberId: string, data: WikiSpaceMemberUpdatePayload) {
  return systemClient.put<unknown, ApiResponse<WikiSpaceMember>>(`api/platform/wiki/spaces/${spaceId}/members/${memberId}`, data)
}

export function deleteWikiSpaceMember(spaceId: string, memberId: string) {
  return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/wiki/spaces/${spaceId}/members/${memberId}`)
}

/** 启用受限 ACL（幂等）：启用前若无可管理成员，服务端会授予当前操作者 MANAGE。 */
export function enableWikiSpaceRestrictedAcl(spaceId: string) {
  return systemClient.post<unknown, ApiResponse<WikiSpaceCoverFields>>(`api/platform/wiki/spaces/${spaceId}/acl/restricted`)
}

// —— 评论审核 ——

export function fetchWikiComments(spaceId: string, params: WikiCommentPageParams) {
  return systemClient.get<unknown, ApiResponse<PageResult<WikiComment>>>(`api/platform/wiki/spaces/${spaceId}/comments`, { params })
}

export function moderateWikiComment(commentId: string, action: WikiCommentModerationAction) {
  return systemClient.post<unknown, ApiResponse<WikiComment>>(`api/platform/wiki/comments/${commentId}/${action}`)
}
