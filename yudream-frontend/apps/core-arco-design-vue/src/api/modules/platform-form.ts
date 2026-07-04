import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type DynamicFormStatus = 'DRAFT' | 'PUBLISHED' | 'DISABLED'

export interface DynamicForm {
  id: number
  name: string
  code: string
  description?: string
  schemaJson: string
  optionJson?: string
  allowAnonymous: boolean
  status: DynamicFormStatus
  publishedAt?: string
  createTime?: string
  updateTime?: string
}

export interface DynamicFormPayload {
  name: string
  code: string
  description?: string
  schemaJson: string
  optionJson?: string
  allowAnonymous: boolean
  status: DynamicFormStatus
}

export interface DynamicFormPageParams {
  page: number
  size: number
  keyword?: string
  status?: DynamicFormStatus | ''
}

export interface FormSubmission {
  id: number
  formId: number
  formCode: string
  data: Record<string, unknown>
  submitterId?: number
  submitterIp?: string
  submittedAt?: string
  createTime?: string
}

export interface FormSubmissionPageParams {
  page: number
  size: number
}

export interface FormValueCount {
  value: string
  count: number
}

export interface FormFieldStat {
  field: string
  filled: number
  empty: number
  topValues: FormValueCount[]
}

export interface FormStatistics {
  formId: number
  formCode: string
  total: number
  today: number
  last7Days: number
  fields: FormFieldStat[]
}

export default {
  page: (params: DynamicFormPageParams) => systemClient.get<unknown, ApiResponse<PageResult<DynamicForm>>>('api/platform/forms', { params }),
  detail: (id: number) => systemClient.get<unknown, ApiResponse<DynamicForm>>(`api/platform/forms/${id}`),
  create: (data: DynamicFormPayload) => systemClient.post<unknown, ApiResponse<DynamicForm>>('api/platform/forms', data),
  update: (id: number, data: DynamicFormPayload) => systemClient.put<unknown, ApiResponse<DynamicForm>>(`api/platform/forms/${id}`, data),
  publish: (id: number) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/forms/${id}/publish`),
  unpublish: (id: number) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/forms/${id}/unpublish`),
  delete: (id: number) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/forms/${id}`),
  submissions: (id: number, params: FormSubmissionPageParams) => systemClient.get<unknown, ApiResponse<PageResult<FormSubmission>>>(`api/platform/forms/${id}/submissions`, { params }),
  statistics: (id: number) => systemClient.get<unknown, ApiResponse<FormStatistics>>(`api/platform/forms/${id}/statistics`),
  publicForm: (code: string) => systemClient.get<unknown, ApiResponse<DynamicForm>>(`api/public/forms/${code}`, { skipTokenRefresh: true }),
  submitPublic: (code: string, data: Record<string, unknown>) => systemClient.post<unknown, ApiResponse<FormSubmission>>(`api/public/forms/${code}/submissions`, { data }, { skipTokenRefresh: true }),
}
