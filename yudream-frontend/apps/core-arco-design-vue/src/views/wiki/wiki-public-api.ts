import type { ApiResponse, PageResult } from '@/api/modules/system-client'
import systemClient from '@/api/modules/system-client'

/**
 * 公开 Wiki 端点的局部适配层（空间资料 + 评论 + 评论文本锚点）。
 *
 * 共享模块 `@/api/modules/platform-wiki` 由宿主维护，这里只补齐公开评论/资料契约，
 * 不在共享文件里叠加快照式改动；两端合流时把本文件并入共享模块即可。
 * 路径与服务端 WikiPublicCommentController / WikiPublicSpaceController 一一对应。
 */

/** 公开端空间资料：commentEnabled 是服务端下发的评论能力声明，前端不自行推导。 */
export interface WikiPublicSpaceProfile {
  name: string
  slug: string
  description?: string
  coverType?: string
  coverSeed?: string
  coverUrl?: string
  coverAlt?: string
  commentEnabled: boolean
}

/**
 * 锚点请求载荷（客户端草稿）：与后端 WikiCommentAnchorRequest 逐字段同构的**扁平**结构，
 * 只提交引用文本与定位提示。版本号/修订号/内容哈希一律由服务端按当前已发布版本盖章，
 * 请求侧**没有**这些字段。
 *
 * 结构约束（服务端校验，前端不提交半截提示）：blockOrdinal/blockType 成对出现；
 * tableRow/tableCell 只随 blockType === 'tableCell' 出现且必须成对；起止偏移可单独出现。
 */
export interface WikiCommentAnchorPayload {
  /** 引用原文；服务端按空白折叠归一化后要求 1..512 字符且能在已发布正文中唯一定位 */
  exact: string
  /** 引用前文（服务端上限 64 字符），多处命中时消歧 */
  prefix?: string
  /** 引用后文（服务端上限 64 字符） */
  suffix?: string
  /** 捕获时所在块序号（提示），从 0 开始 */
  blockOrdinal?: number
  /** 捕获时所在块类型（提示，lowerCamel 线协议名）：paragraph/heading/listItem/blockquote/codeBlock/tableCell */
  blockType?: string
  /** 表格行序（仅 blockType === 'tableCell'） */
  tableRow?: number
  /** 表格列序（仅 blockType === 'tableCell'） */
  tableCell?: number
  /** 捕获时起始偏移提示（归一化文本坐标） */
  startOffset?: number
  /** 捕获时结束偏移提示（归一化文本坐标，不含） */
  endOffset?: number
}

/** 响应里的 anchor 与请求同构（扁平字段原样带回）。 */
export type WikiCommentAnchor = WikiCommentAnchorPayload

/**
 * 公开端评论：GET 只返回已发布评论；写操作由服务端要求登录并校验作者本人。
 *
 * 锚点的版本身份与读取状态是 anchor 的**兄弟字段**（后端 WikiCommentRes：
 * versionId/revision/contentHash/anchorState），不在 anchor 对象内；无锚点评论（含历史评论）
 * 这些字段一律不下发，也不存在 PAGE 之类的锚点种类。
 */
export interface WikiPublicComment {
  id: string
  spaceId?: string
  nodeId?: string
  parentId?: string
  authorType?: string
  authorUserId?: string
  authorName?: string
  content: string
  status?: string
  pinned?: boolean
  /** 根评论的文本锚点；回复继承根评论锚点；无锚点时服务端不下发该字段 */
  anchor?: WikiCommentAnchor | null
  /** 锚点盖章的已发布版本 ID（长 ID 保持字符串） */
  versionId?: string
  /** 锚点盖章的发布修订号（服务端按字符串下发，长数值禁止 Number） */
  revision?: string
  /** 锚点盖章的发布内容哈希；与当前已发布版本不一致 ⇒ 服务端判 reanchorable */
  contentHash?: string
  /** 服务端只读投影（小写）：active / reanchorable / orphaned；治理视图不下发 */
  anchorState?: string
  createTime?: string
  updateTime?: string
  replies?: WikiPublicComment[]
}

function spaceBase(slug: string) {
  return `api/public/wiki/${encodeURIComponent(slug)}`
}

export function fetchPublicWikiProfile(slug: string) {
  return systemClient.get<unknown, ApiResponse<WikiPublicSpaceProfile>>(`${spaceBase(slug)}/profile`)
}

export function fetchPublicWikiComments(slug: string, nodeId: string, params?: { page?: number, size?: number }) {
  return systemClient.get<unknown, ApiResponse<PageResult<WikiPublicComment>>>(`${spaceBase(slug)}/nodes/${encodeURIComponent(nodeId)}/comments`, { params })
}

/**
 * 发表评论：服务端从认证上下文取作者，未登录一律拒绝（匿名评论未开放）；
 * 新评论落为待审核状态，不会立即出现在只读列表里。
 * 带 `anchor` 时服务端要求节点已发布，并按当前已发布版本重新校验引用能否唯一定位；
 * 回复不接受 anchor（锚点继承根评论），因此有 parentId 时忽略锚点。
 */
export function createPublicWikiComment(slug: string, nodeId: string, data: { content: string, parentId?: string, anchor?: WikiCommentAnchorPayload }) {
  const payload: { content: string, parentId?: string, anchor?: WikiCommentAnchorPayload } = { content: data.content }
  if (data.parentId) {
    payload.parentId = data.parentId
  }
  else if (data.anchor) {
    payload.anchor = data.anchor
  }
  return systemClient.post<unknown, ApiResponse<WikiPublicComment>>(`${spaceBase(slug)}/nodes/${encodeURIComponent(nodeId)}/comments`, payload)
}
