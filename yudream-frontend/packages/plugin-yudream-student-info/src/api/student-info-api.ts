import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import type { StudentInfoSummary, StudentProfile, StudentProfileFilters } from '../types'

export function createStudentInfoApi(sdk: YuDreamPluginSdk) {
  function query(params: Record<string, string | number | undefined>) {
    const search = new URLSearchParams()
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        search.set(key, String(value))
      }
    })
    const value = search.toString()
    return value ? `?${value}` : ''
  }

  return {
    status: () => sdk.http.get<StudentInfoSummary>('/status'),
    me: () => sdk.http.get<StudentProfile>('/me'),
    saveMe: (data: Record<string, unknown>) => sdk.http.request<StudentProfile>('/me', { method: 'PUT', data }),
    profiles: (filters: StudentProfileFilters, page = 1, size = 20) => sdk.http.get<StudentProfile[]>(`/admin/profiles${query({ page, size, ...filters })}`),
    profile: (userId: string) => sdk.http.get<StudentProfile>(`/admin/profiles/${encodeURIComponent(userId)}`),
    saveProfile: (data: Record<string, unknown>) => sdk.http.request<StudentProfile>('/admin/profiles', { method: 'PUT', data }),
    deleteProfile: (userId: string) => sdk.http.request(`/admin/profiles/${encodeURIComponent(userId)}`, { method: 'DELETE' }),
  }
}
