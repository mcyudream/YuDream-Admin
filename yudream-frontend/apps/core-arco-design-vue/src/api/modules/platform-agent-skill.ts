import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export interface AgentSkill {
  id: string
  name: string
  description: string
  content: string
  resources: Record<string, string>
  enabled: boolean
  builtin: boolean
  updateTime?: string
}

export interface AgentSkillPayload {
  id?: string
  name: string
  description: string
  content: string
  resources?: Record<string, string>
  enabled?: boolean
}

export default {
  page: (params: Record<string, unknown>) => systemClient.get<unknown, ApiResponse<PageResult<AgentSkill>>>('api/platform/agent-skills', { params }),
  enabledList: () => systemClient.get<unknown, ApiResponse<AgentSkill[]>>('api/platform/agent-skills/enabled'),
  detail: (id: string) => systemClient.get<unknown, ApiResponse<AgentSkill>>(`api/platform/agent-skills/${id}`),
  create: (data: AgentSkillPayload) => systemClient.post<unknown, ApiResponse<AgentSkill>>('api/platform/agent-skills', data),
  setEnabled: (id: string, enabled: boolean) => systemClient.put<unknown, ApiResponse<AgentSkill>>(`api/platform/agent-skills/${id}/enabled/${enabled}`),
  delete: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/agent-skills/${id}`),
  uploadZip: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return systemClient.post<unknown, ApiResponse<AgentSkill>>('api/platform/agent-skills/upload', form)
  },
}
