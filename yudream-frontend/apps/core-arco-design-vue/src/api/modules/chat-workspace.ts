import type { ApiResponse } from './system-client'
import systemClient from './system-client'

/** 用户工作区文件条目（每用户一个默认工作区，随账号自动生成）。 */
export interface ChatWorkspaceEntry {
  path: string
  size: number
  updatedAt: string
}

export interface ChatWorkspaceFileContent {
  path: string
  encoding: 'text' | 'base64'
  content: string
}

export default {
  list: () => systemClient.get<unknown, ApiResponse<ChatWorkspaceEntry[]>>('api/platform/chat/workspace'),
  read: (path: string) =>
    systemClient.get<unknown, ApiResponse<ChatWorkspaceFileContent>>('api/platform/chat/workspace/file', { params: { path } }),
  upload: (path: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/chat/workspace/upload?path=${encodeURIComponent(path)}`, form)
  },
  remove: (path: string) =>
    systemClient.delete<unknown, ApiResponse<void>>('api/platform/chat/workspace/file', { params: { path } }),
}
