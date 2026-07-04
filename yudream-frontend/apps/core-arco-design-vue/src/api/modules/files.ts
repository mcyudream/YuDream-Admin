import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface FileObject {
  id: number
  originalName?: string
  contentType?: string
  size?: number
  module?: string
  url?: string
  createTime?: string
}

export interface FilePageParams {
  page: number
  size: number
  keyword?: string
  module?: string
  publicAccess?: boolean
}

export default {
  page: (params: FilePageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<FileObject>>>('api/files', { params })
  },
  upload: (data: FormData) => {
    return systemClient.post<unknown, ApiResponse<FileObject>>('api/files/upload', data)
  },
}
