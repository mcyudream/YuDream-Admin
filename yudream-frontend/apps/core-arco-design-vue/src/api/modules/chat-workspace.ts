import type { ApiResponse } from './system-client'
import systemClient from './system-client'

/**
 * 用户工作区（每用户一个）：元数据在数据库、内容在对象存储；目录是显式条目。
 * 后端 Long 序列化为字符串：id 保持字符串，字节数等计量值在这里统一转成 number。
 */
export interface ChatWorkspaceEntry {
  id: string
  path: string
  kind: 'FILE' | 'DIRECTORY'
  size: number
  contentType: string | null
  revision: number
  updatedBy: 'USER' | 'AI' | 'SYSTEM' | null
  updatedAt: string
}

export interface ChatWorkspaceOverview {
  workspaceId: string
  uri: string
  entries: ChatWorkspaceEntry[]
  usedBytes: number
  /** null 表示不限 */
  quotaBytes: number | null
  tmpRetentionDays: number
  trashRetentionDays: number
}

export interface ChatWorkspaceFileContent {
  path: string
  /** binary：二进制或超过预览上限，经下载获取 */
  encoding: 'text' | 'binary'
  content: string | null
  truncated: boolean
  size: number
  contentType: string | null
  revision: number
}

export interface ChatWorkspaceTrashItem {
  id: string
  path: string
  kind: 'FILE' | 'DIRECTORY'
  size: number
  childCount: number
  deletedAt: string
  expiresAt: string
}

export interface ChatWorkspaceVersion {
  id: string
  revision: number
  size: number
  actor: 'USER' | 'AI' | 'SYSTEM' | null
  createdAt: string
  current: boolean
}

export interface ChatWorkspaceConfig {
  quotaMb: number
  tmpRetentionDays: number
}

type Raw<T> = { [K in keyof T]: T[K] extends number ? number | string : T[K] extends number | null ? number | string | null : T[K] }

const count = (value: number | string | null | undefined) => value === null || value === undefined ? 0 : Number(value)

function entry(raw: Raw<ChatWorkspaceEntry>): ChatWorkspaceEntry {
  return { ...raw, size: count(raw.size), revision: count(raw.revision) }
}

const BASE = 'api/platform/chat/workspace'

export default {
  async overview() {
    const response = await systemClient.get<unknown, ApiResponse<Raw<ChatWorkspaceOverview> & { entries: Raw<ChatWorkspaceEntry>[] }>>(BASE)
    const data = response.data
    return { ...data, entries: data.entries.map(entry), usedBytes: count(data.usedBytes), quotaBytes: data.quotaBytes === null ? null : count(data.quotaBytes), tmpRetentionDays: count(data.tmpRetentionDays), trashRetentionDays: count(data.trashRetentionDays) } satisfies ChatWorkspaceOverview
  },
  async read(path: string) {
    const data = (await systemClient.get<unknown, ApiResponse<Raw<ChatWorkspaceFileContent>>>(`${BASE}/file`, { params: { path } })).data
    return { ...data, size: count(data.size), revision: count(data.revision) } satisfies ChatWorkspaceFileContent
  },
  async download(path: string) {
    const response = await systemClient.get<unknown, { data: Blob }>(`${BASE}/file/download`, { params: { path }, responseType: 'blob', timeout: 0 })
    return response.data
  },
  async save(path: string, content: string, expectedRevision?: number) {
    return entry((await systemClient.put<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/file`, { path, content, expectedRevision })).data)
  },
  async upload(directory: string, file: File) {
    const form = new FormData()
    form.append('file', file)
    return entry((await systemClient.post<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/upload`, form, { params: { directory }, timeout: 0 })).data)
  },
  async createDirectory(path: string) {
    return entry((await systemClient.post<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/directory`, { path })).data)
  },
  async move(from: string, to: string) {
    return entry((await systemClient.post<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/move`, { from, to })).data)
  },
  async remove(path: string) {
    return count((await systemClient.delete<unknown, ApiResponse<number>>(`${BASE}/file`, { params: { path } })).data)
  },
  async trash() {
    const data = (await systemClient.get<unknown, ApiResponse<Raw<ChatWorkspaceTrashItem>[]>>(`${BASE}/trash`)).data
    return data.map(item => ({ ...item, size: count(item.size), childCount: count(item.childCount) }) satisfies ChatWorkspaceTrashItem)
  },
  async restore(id: string) {
    return entry((await systemClient.post<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/trash/${id}/restore`)).data)
  },
  purge: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`${BASE}/trash/${id}`),
  emptyTrash: () => systemClient.delete<unknown, ApiResponse<number>>(`${BASE}/trash`),
  async versions(path: string) {
    const data = (await systemClient.get<unknown, ApiResponse<Raw<ChatWorkspaceVersion>[]>>(`${BASE}/versions`, { params: { path } })).data
    return data.map(item => ({ ...item, size: count(item.size), revision: count(item.revision) }) satisfies ChatWorkspaceVersion)
  },
  async restoreVersion(path: string, versionId: string) {
    return entry((await systemClient.post<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/versions/${versionId}/restore`, { path })).data)
  },
  async saveAttachment(fileId: string, directory: string) {
    return entry((await systemClient.post<unknown, ApiResponse<Raw<ChatWorkspaceEntry>>>(`${BASE}/attachments`, { fileId, directory })).data)
  },
  async config() {
    const data = (await systemClient.get<unknown, ApiResponse<Raw<ChatWorkspaceConfig>>>(`${BASE}/config`)).data
    return { quotaMb: count(data.quotaMb), tmpRetentionDays: count(data.tmpRetentionDays) } satisfies ChatWorkspaceConfig
  },
  async updateConfig(config: ChatWorkspaceConfig) {
    const data = (await systemClient.put<unknown, ApiResponse<Raw<ChatWorkspaceConfig>>>(`${BASE}/config`, config)).data
    return { quotaMb: count(data.quotaMb), tmpRetentionDays: count(data.tmpRetentionDays) } satisfies ChatWorkspaceConfig
  },
}
