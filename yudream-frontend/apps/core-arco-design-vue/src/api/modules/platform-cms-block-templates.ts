import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export interface StudioBlockTemplate {
  id: string
  name: string
  category: string
  description: string
  keywords: string[]
  nodesJson: string
  revision: string
  createdBy: string | null
  createdAt?: string
  updatedAt?: string
}

export interface StudioBlockTemplateSave {
  name: string
  category: string
  description?: string
  keywords?: string[]
  /** 更新时省略表示只改元数据 */
  nodesJson?: string
  expectedRevision?: string
}

const base = 'api/platform/cms/block-templates'

export default {
  list: () => systemClient.get<unknown, ApiResponse<StudioBlockTemplate[]>>(base),
  create: (data: StudioBlockTemplateSave) => systemClient.post<unknown, ApiResponse<StudioBlockTemplate>>(base, data),
  update: (id: string, data: StudioBlockTemplateSave) => systemClient.put<unknown, ApiResponse<StudioBlockTemplate>>(`${base}/${encodeURIComponent(id)}`, data),
  remove: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`${base}/${encodeURIComponent(id)}`),
}
