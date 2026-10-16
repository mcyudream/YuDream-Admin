import type { ApiResponse } from './system-client'
import systemClient from './system-client'

/**
 * 飞书集成接口（宿主后台），字段与端点按后端 `FeishuCloudDocController` / `FeishuQuotaController` 契约。
 *
 * - `GET    api/platform/feishu/drive/files?folderToken&pageToken`（分页；folderToken 为空列出应用根目录）
 * - `GET    api/platform/feishu/wiki/spaces?pageToken`
 * - `GET    api/platform/feishu/documents/{documentId}`
 * - `GET    api/platform/feishu/documents/{documentId}/content?lang`（lang 为飞书整数枚举）
 * - `GET    api/platform/feishu/documents/{documentId}/blocks?pageToken`
 * - `POST   api/platform/feishu/documents/{documentId}/blocks/{parentBlockId}/children`（children 为块 JSON 原文）
 * - `DELETE api/platform/feishu/documents/{documentId}/blocks/{parentBlockId}/children`（[startIndex, endIndex)）
 * - `PATCH  api/platform/feishu/documents/{documentId}/blocks/{blockId}/elements`（elements 为文本元素 JSON 原文）
 * - `GET    api/platform/feishu/quota/me`
 * - `GET    api/platform/feishu/quota/config`
 * - `PUT    api/platform/feishu/quota/config`
 *
 * 后端未提供整篇正文覆盖写接口，因此这里没有对应的写方法；分页不传 pageSize，由后端默认（20 条/页）决定。
 * 这些调用全部 `skipGlobalToast`：失败由页面渲染明确状态，不弹通用 toast 掩盖原因。
 */

/* ------------------------------ 响应契约 ------------------------------ */

/** 云空间文件条目（后端 FeishuCloudDocRes）。 */
export interface FeishuCloudDocRes {
  token: string
  name?: string
  type?: string
  parentToken?: string
  url?: string
  shortcutTargetType?: string
  shortcutTargetToken?: string
  createdTime?: string
  modifiedTime?: string
  ownerId?: string
}

/** 云空间文件分页（后端 FeishuCloudDocPageRes）。 */
export interface FeishuCloudDocPageRes {
  items?: FeishuCloudDocRes[]
  nextPageToken?: string
  hasMore?: boolean
}

/** 知识库空间条目（后端 FeishuWikiSpaceRes）。 */
export interface FeishuWikiSpaceRes {
  spaceId: string
  name?: string
  description?: string
  spaceType?: string
  visibility?: string
  openSharing?: string
}

/** 知识库空间分页（后端 FeishuWikiSpacePageRes）。 */
export interface FeishuWikiSpacePageRes {
  items?: FeishuWikiSpaceRes[]
  nextPageToken?: string
  hasMore?: boolean
}

/** Knowledge-base node metadata returned by Feishu Wiki v2. */
export interface FeishuWikiNodeRes {
  nodeToken: string
  objToken?: string
  objType?: string
  title?: string
  hasChild?: boolean
  parentNodeToken?: string
  nodeType?: string
  originNodeToken?: string
  originSpaceId?: string
  spaceId?: string
}

export interface FeishuWikiNodePageRes {
  spaceId: string
  items?: FeishuWikiNodeRes[]
  nextPageToken?: string
  hasMore?: boolean
}

/** 文档元信息（后端 FeishuDocumentMetaRes）。 */
export interface FeishuDocumentMetaRes {
  documentId: string
  revisionId?: string
  title?: string
}

/** 文档纯文本内容，content 原样来自飞书 raw_content（后端 FeishuDocumentContentRes）。 */
export interface FeishuDocumentContentRes {
  documentId: string
  content?: string
}

/** 文档块，raw 为飞书原始块 JSON 文本（后端 FeishuDocumentBlockRes）。 */
export interface FeishuDocumentBlockRes {
  blockId: string
  parentId?: string
  blockType?: number
  children?: string[]
  raw?: string
}

/** 文档块分页（后端 FeishuDocumentBlockPageRes）。 */
export interface FeishuDocumentBlockPageRes {
  documentId: string
  items?: FeishuDocumentBlockRes[]
  nextPageToken?: string
  hasMore?: boolean
}

/** 写操作结果：新版本号与受影响块原文（后端 FeishuBlockWriteResultRes）。 */
export interface FeishuBlockWriteResultRes {
  documentId: string
  revisionId?: string
  blockIds?: string[]
  blocksRaw?: string[]
}

/** 当前用户额度（后端 FeishuQuotaRes）。 */
export interface FeishuQuotaRes {
  userId: string
  minuteKey?: string
  minuteUsed?: number
  minuteLimit?: number
  dailyKey?: string
  dailyUsed?: number
  dailyLimit?: number
}

/** 全局默认额度（后端 FeishuQuotaConfigRes）。 */
export interface FeishuQuotaConfigRes {
  defaultPerMinuteLimit?: number
  defaultDailyLimit?: number
}

/* ------------------------------ 请求契约 ------------------------------ */

/** 追加子块：children 为飞书块 JSON 原文，可把块读取接口返回的 raw 直接回填。 */
export interface FeishuBlockAppendBody {
  children: string[]
  index?: number
}

/** 删除子块：删除父块下 [startIndex, endIndex) 区间的子块。 */
export interface FeishuBlockDeleteBody {
  startIndex: number
  endIndex: number
}

/** 更新文本块：elements 为飞书文本元素 JSON 原文；documentRevisionId 为空按最新版本处理。 */
export interface FeishuElementUpdateBody {
  elements: string[]
  documentRevisionId?: number
}

/** 额度上限：留空保持当前值。 */
export interface FeishuQuotaConfigBody {
  defaultPerMinuteLimit?: number
  defaultDailyLimit?: number
}

const REQUEST_OPTIONS = { skipGlobalToast: true } as const

function path(segment: string) {
  return encodeURIComponent(segment)
}

function optionalParams(params: Record<string, string | number | undefined>) {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined && value !== '')
  return entries.length ? Object.fromEntries(entries) : undefined
}

export default {
  listDriveFiles: (params: { folderToken?: string, pageToken?: string } = {}) => {
    return systemClient.get<unknown, ApiResponse<FeishuCloudDocPageRes>>('api/platform/feishu/drive/files', {
      ...REQUEST_OPTIONS,
      params: optionalParams(params),
    })
  },
  listWikiSpaces: (params: { pageToken?: string } = {}) => {
    return systemClient.get<unknown, ApiResponse<FeishuWikiSpacePageRes>>('api/platform/feishu/wiki/spaces', {
      ...REQUEST_OPTIONS,
      params: optionalParams(params),
    })
  },
  listWikiNodes: (spaceId: string, params: { parentNodeToken?: string, pageToken?: string } = {}) => {
    return systemClient.get<unknown, ApiResponse<FeishuWikiNodePageRes>>(
      `api/platform/feishu/wiki/spaces/${path(spaceId)}/nodes`,
      { ...REQUEST_OPTIONS, params: optionalParams(params) },
    )
  },
  getDocument: (documentId: string) => {
    return systemClient.get<unknown, ApiResponse<FeishuDocumentMetaRes>>(`api/platform/feishu/documents/${path(documentId)}`, REQUEST_OPTIONS)
  },
  getDocumentContent: (documentId: string, lang?: number) => {
    return systemClient.get<unknown, ApiResponse<FeishuDocumentContentRes>>(`api/platform/feishu/documents/${path(documentId)}/content`, {
      ...REQUEST_OPTIONS,
      params: optionalParams({ lang }),
    })
  },
  getDocumentBlocks: (documentId: string, params: { pageToken?: string } = {}) => {
    return systemClient.get<unknown, ApiResponse<FeishuDocumentBlockPageRes>>(`api/platform/feishu/documents/${path(documentId)}/blocks`, {
      ...REQUEST_OPTIONS,
      params: optionalParams(params),
    })
  },
  appendBlockChildren: (documentId: string, parentBlockId: string, body: FeishuBlockAppendBody) => {
    return systemClient.post<unknown, ApiResponse<FeishuBlockWriteResultRes>>(
      `api/platform/feishu/documents/${path(documentId)}/blocks/${path(parentBlockId)}/children`,
      body,
      REQUEST_OPTIONS,
    )
  },
  deleteBlockChildren: (documentId: string, parentBlockId: string, body: FeishuBlockDeleteBody) => {
    return systemClient.delete<unknown, ApiResponse<void>>(
      `api/platform/feishu/documents/${path(documentId)}/blocks/${path(parentBlockId)}/children`,
      { ...REQUEST_OPTIONS, data: body },
    )
  },
  updateBlockElements: (documentId: string, blockId: string, body: FeishuElementUpdateBody) => {
    return systemClient.patch<unknown, ApiResponse<FeishuBlockWriteResultRes>>(
      `api/platform/feishu/documents/${path(documentId)}/blocks/${path(blockId)}/elements`,
      body,
      REQUEST_OPTIONS,
    )
  },
  getQuotaMe: () => {
    return systemClient.get<unknown, ApiResponse<FeishuQuotaRes>>('api/platform/feishu/quota/me', REQUEST_OPTIONS)
  },
  getQuotaConfig: () => {
    return systemClient.get<unknown, ApiResponse<FeishuQuotaConfigRes>>('api/platform/feishu/quota/config', REQUEST_OPTIONS)
  },
  updateQuotaConfig: (body: FeishuQuotaConfigBody) => {
    return systemClient.put<unknown, ApiResponse<FeishuQuotaConfigRes>>('api/platform/feishu/quota/config', body, REQUEST_OPTIONS)
  },
}
