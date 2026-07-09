import type {
  ProjectDeptOption,
  ProjectAcceptanceRecord,
  ProjectCheckIn,
  ProjectMinecraftServerOption,
  ProjectProgressEvent,
  ProjectProgressProject,
  ProjectProgressStatus,
  ProjectUserOption,
  ProjectWorkDetail,
} from '../types'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'

const PAGE_SIZE = 200

export function createProjectProgressApi(sdk: YuDreamPluginSdk) {
  function query(params: Record<string, string | number | boolean | undefined | null>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        search.set(key, String(value))
      }
    })
    const value = search.toString()
    return value ? `?${value}` : ''
  }

  async function getAllPages<T>(path: string, params: Record<string, string | number | boolean | undefined | null> = {}) {
    const records: T[] = []
    let page = 1
    while (true) {
      const batch = await sdk.http.get<T[]>(`${path}${query({ ...params, page, size: PAGE_SIZE })}`)
      records.push(...batch)
      if (batch.length < PAGE_SIZE) {
        return records
      }
      page += 1
    }
  }

  return {
    status: () => sdk.http.get<ProjectProgressStatus>('/status'),
    projects: () => getAllPages<ProjectProgressProject>('/projects'),
    users: (keyword?: string, deptId?: string) => getAllPages<ProjectUserOption>('/users', { keyword, deptId }),
    resolveUsers: (ids: string[]) => ids.length
      ? sdk.http.get<ProjectUserOption[]>(`/users/resolve${query({ ids: ids.join(',') })}`)
      : Promise.resolve([]),
    departments: (keyword?: string) => sdk.http.get<ProjectDeptOption[]>(`/departments${query({ keyword })}`),
    minecraftServers: (includeDisabled = false) => sdk.http.get<ProjectMinecraftServerOption[]>(`/minecraft/servers${query({ includeDisabled })}`),
    createProject: (data: Record<string, unknown>) => sdk.http.post<ProjectProgressProject>('/projects', data),
    updateProject: (id: string, data: Record<string, unknown>) => sdk.http.request<ProjectProgressProject>(`/projects/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    deleteProject: (id: string) => sdk.http.request(`/projects/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    details: (projectId: string) => getAllPages<ProjectWorkDetail>(`/projects/${encodeURIComponent(projectId)}/details`),
    createDetail: (projectId: string, data: Record<string, unknown>) => sdk.http.post<ProjectWorkDetail>(`/projects/${encodeURIComponent(projectId)}/details`, data),
    updateDetail: (id: string, data: Record<string, unknown>) => sdk.http.request<ProjectWorkDetail>(`/details/${encodeURIComponent(id)}`, { method: 'PUT', data }),
    deleteDetail: (id: string) => sdk.http.request(`/details/${encodeURIComponent(id)}`, { method: 'DELETE' }),
    publishDetail: (id: string) => sdk.http.post<ProjectWorkDetail>(`/details/${encodeURIComponent(id)}/publish`),
    randomAssign: (id: string) => sdk.http.post<ProjectWorkDetail>(`/details/${encodeURIComponent(id)}/random-assign`),
    claim: (id: string) => sdk.http.post<ProjectWorkDetail>(`/details/${encodeURIComponent(id)}/claim`),
    claimableTasks: () => getAllPages<ProjectWorkDetail>('/tasks/claimable'),
    myTasks: () => getAllPages<ProjectWorkDetail>('/my-tasks'),
    pendingAcceptance: () => getAllPages<ProjectWorkDetail>('/acceptance/pending'),
    submitAcceptance: (detailId: string) => sdk.http.post<ProjectWorkDetail>(`/details/${encodeURIComponent(detailId)}/submit-acceptance`),
    projectCheckIns: (projectId: string) => getAllPages<ProjectCheckIn>(`/projects/${encodeURIComponent(projectId)}/check-ins`),
    createProjectCheckIn: (projectId: string, data: Record<string, unknown>) => sdk.http.post<ProjectCheckIn>(`/projects/${encodeURIComponent(projectId)}/check-ins`, data),
    projectMinecraftCheckIn: (projectId: string) => sdk.http.post<ProjectCheckIn>(`/projects/${encodeURIComponent(projectId)}/check-ins/minecraft`),
    autoMinecraftCheckIns: (projectId: string) => sdk.http.post<ProjectCheckIn[]>(`/projects/${encodeURIComponent(projectId)}/minecraft/auto-check-ins`),
    accept: (detailId: string, data: Record<string, unknown>) => sdk.http.post<ProjectAcceptanceRecord>(`/details/${encodeURIComponent(detailId)}/accept`, data),
    reject: (detailId: string, data: Record<string, unknown>) => sdk.http.post<ProjectAcceptanceRecord>(`/details/${encodeURIComponent(detailId)}/reject`, data),
    acceptanceRecords: (detailId: string) => getAllPages<ProjectAcceptanceRecord>(`/details/${encodeURIComponent(detailId)}/acceptance-records`),
    events: (projectId: string, since?: number) => getAllPages<ProjectProgressEvent>(`/projects/${encodeURIComponent(projectId)}/events`, { since }),
  }
}
