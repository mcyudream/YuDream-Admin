import type { AiRunSnapshot } from './platform-ai-runs'
import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface WorkspaceEntry {
  path: string
  size: number
  updatedAt: string
}

export interface WorkspaceFileContent {
  path: string
  encoding: 'text' | 'base64'
  content: string
}

const base = (import.meta.env.VITE_APP_API_BASEURL || window.location.origin).replace(/\/$/, '')

export function cmsWorkspaceChatEndpoint(targetKey: string) {
  return `${base}/api/platform/ai/cms/workspace/${encodeURIComponent(targetKey)}/chat/stream`
}

export function cmsWorkspaceRawUrl(path: string) {
  return `${base}/api/platform/ai/cms/workspace/raw/${path}`
}

export default {
  startChat: (targetKey: string, question: string) => systemClient.post<unknown, ApiResponse<AiRunSnapshot>>(`api/platform/ai/cms/workspace/${encodeURIComponent(targetKey)}/chat/runs`, { question }),
  list: () => systemClient.get<unknown, ApiResponse<WorkspaceEntry[]>>('api/platform/ai/cms/workspace'),
  read: (path: string) =>
    systemClient.get<unknown, ApiResponse<WorkspaceFileContent>>('api/platform/ai/cms/workspace/file', { params: { path } }),
  write: (path: string, content: string) =>
    systemClient.post<unknown, ApiResponse<void>>('api/platform/ai/cms/workspace/file', { path, encoding: 'text', content }),
  upload: (path: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/ai/cms/workspace/upload?path=${encodeURIComponent(path)}`, form)
  },
  delete: (path: string) =>
    systemClient.delete<unknown, ApiResponse<void>>('api/platform/ai/cms/workspace/file', { params: { path } }),
  publish: (themeCode = 'default', published = true) =>
    systemClient.post<unknown, ApiResponse<{ files: number, pages: number }>>('api/platform/ai/cms/workspace/publish', { themeCode, published }),
}
