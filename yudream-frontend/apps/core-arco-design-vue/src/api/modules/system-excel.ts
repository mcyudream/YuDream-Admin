import type { ApiResponse } from './system-client'
import type { ExcelBlobResponse, ExcelImportResult } from '@/utils/excel'
import systemClient from './system-client'

export type ExcelModule = 'users' | 'roles' | 'depts' | 'menus'

export default {
  exportUsers: (params?: Record<string, unknown>) => exportExcel('users', params),
  userTemplate: () => templateExcel('users'),
  importUsers: (data: FormData) => importExcel('users', data),

  exportRoles: (params?: Record<string, unknown>) => exportExcel('roles', params),
  roleTemplate: () => templateExcel('roles'),
  importRoles: (data: FormData) => importExcel('roles', data),

  exportDepts: (params?: Record<string, unknown>) => exportExcel('depts', params),
  deptTemplate: () => templateExcel('depts'),
  importDepts: (data: FormData) => importExcel('depts', data),

  exportMenus: (params?: Record<string, unknown>) => exportExcel('menus', params),
  menuTemplate: () => templateExcel('menus'),
  importMenus: (data: FormData) => importExcel('menus', data),

  exportApiLogs: (params?: Record<string, unknown>) => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/api-logs/export', { params, responseType: 'blob' }),
  exportLoginLogs: (params?: Record<string, unknown>) => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/login-logs/export', { params, responseType: 'blob' }),
  exportOnlineUsers: (params?: Record<string, unknown>) => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/online-users/export', { params, responseType: 'blob' }),
}

function exportExcel(module: ExcelModule, params?: Record<string, unknown>) {
  return systemClient.get<unknown, ExcelBlobResponse>(`api/system/excel/${module}/export`, { params, responseType: 'blob' })
}

function templateExcel(module: ExcelModule) {
  return systemClient.get<unknown, ExcelBlobResponse>(`api/system/excel/${module}/template`, { responseType: 'blob' })
}

function importExcel(module: ExcelModule, data: FormData) {
  return systemClient.post<unknown, ApiResponse<ExcelImportResult>>(`api/system/excel/${module}/import`, data)
}
