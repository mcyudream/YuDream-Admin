import type { ApiResponse } from './system-client'
import type { ExcelBlobResponse, ExcelImportResult } from '@/utils/excel'
import systemClient from './system-client'

export type ExcelModule = 'users' | 'roles' | 'depts' | 'menus'

/** 系统数据模板包导入结果：在通用导入结果上补充新增/更新/跳过计数与导入提示（均可选，视后端返回）。 */
export interface ExcelTemplatePackageImportResult extends ExcelImportResult {
  created?: number
  updated?: number
  skipped?: number
  warnings?: string[]
}

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
  exportAgentTraces: (params?: Record<string, unknown>) => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/agent-traces/export', { params, responseType: 'blob' }),
  exportOnlineUsers: (params?: Record<string, unknown>) => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/online-users/export', { params, responseType: 'blob' }),

  /** 系统数据模板包：下载仅含受控字段表头的空白模板（.xlsx，需 system:template:export） */
  templatePackageBlank: () => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/template-package/blank', { responseType: 'blob' }),
  /** 系统数据模板包：导出当前部门/角色/用户/菜单数据为模板包（.xlsx，需 system:template:export） */
  templatePackageExport: () => systemClient.get<unknown, ExcelBlobResponse>('api/system/excel/template-package/export', { responseType: 'blob' }),
  /** 系统数据模板包导入：multipart {file}，只写入受控字段、不删除模板外记录（需 system:template:import） */
  importTemplatePackage: (data: FormData) => systemClient.post<unknown, ApiResponse<ExcelTemplatePackageImportResult>>('api/system/excel/template-package/import', data),
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
