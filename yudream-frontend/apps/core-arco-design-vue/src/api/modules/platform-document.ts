import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type TemplateStatus = 'ACTIVE' | 'DISABLED'
export type GenerationStatus = 'SUCCESS' | 'FAILED'

export interface DocumentPageParams {
  page: number
  size: number
  keyword?: string
}

export interface WordTemplate {
  id: number
  name: string
  code: string
  templateFileId: number
  templateFileUrl?: string
  originalFilename?: string
  placeholders: Record<string, string>
  description?: string
  status: TemplateStatus
  createTime?: string
  updateTime?: string
}

export interface WordTemplatePayload {
  name: string
  code: string
  placeholders: Record<string, string>
  description?: string
  status: TemplateStatus
}

export interface WordGenerationRecord {
  id: number
  templateId: number
  templateCode: string
  outputFileId?: number
  outputFileUrl?: string
  outputFilename?: string
  data: Record<string, string>
  status: GenerationStatus
  errorMessage?: string
  operatorId?: number
  generatedAt?: string
}

export default {
  pageTemplates: (params: DocumentPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<WordTemplate>>>('api/platform/documents/word-templates', { params })
  },
  uploadTemplate: (file: File, meta: WordTemplatePayload) => {
    return systemClient.post<unknown, ApiResponse<WordTemplate>>('api/platform/documents/word-templates', templateForm(file, meta))
  },
  updateTemplate: (id: number, data: WordTemplatePayload) => {
    return systemClient.put<unknown, ApiResponse<WordTemplate>>(`api/platform/documents/word-templates/${id}`, data)
  },
  replaceTemplateFile: (id: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return systemClient.put<unknown, ApiResponse<WordTemplate>>(`api/platform/documents/word-templates/${id}/file`, form)
  },
  disableTemplate: (id: number) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/documents/word-templates/${id}`)
  },
  generate: (id: number, data: Record<string, string>) => {
    return systemClient.post<unknown, ApiResponse<WordGenerationRecord>>(`api/platform/documents/word-templates/${id}/generate`, { data })
  },
  pageRecords: (params: DocumentPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<WordGenerationRecord>>>('api/platform/documents/word-records', { params })
  },
}

function templateForm(file: File, meta: WordTemplatePayload) {
  const form = new FormData()
  form.append('file', file)
  form.append('meta', new Blob([JSON.stringify(meta)], { type: 'application/json' }))
  return form
}
