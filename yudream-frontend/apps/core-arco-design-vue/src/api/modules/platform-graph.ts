import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type GraphTableStatus = 'ACTIVE' | 'DISABLED'
export type GraphQueryStatus = 'SUCCESS' | 'FAILED'
export interface GraphPageParams { page: number; size: number; keyword?: string }
export interface GraphTable { id: string; name: string; code: string; description?: string; status: GraphTableStatus; authorizedPluginCodes?: string[]; createTime?: string; updateTime?: string }
export interface GraphTablePayload { name: string; code: string; description?: string; status: GraphTableStatus; authorizedPluginCodes?: string[] }
export interface GraphQueryLog { id: string; tableId: string; tableCode: string; cypher: string; params: Record<string, unknown>; rows: Record<string, unknown>[]; summary?: string; durationMillis: number; status: GraphQueryStatus; errorMessage?: string; executedAt?: string }
export default {
  pageTables: (params: GraphPageParams) => systemClient.get<unknown, ApiResponse<PageResult<GraphTable>>>('api/platform/graph/tables', { params }),
  createTable: (data: GraphTablePayload) => systemClient.post<unknown, ApiResponse<GraphTable>>('api/platform/graph/tables', data),
  updateTable: (id: string, data: GraphTablePayload) => systemClient.put<unknown, ApiResponse<GraphTable>>(`api/platform/graph/tables/${id}`, data),
  disableTable: (id: string) => systemClient.delete<unknown, ApiResponse<void>>(`api/platform/graph/tables/${id}`),
  enableTable: (id: string) => systemClient.post<unknown, ApiResponse<void>>(`api/platform/graph/tables/${id}/enable`),
  testTable: (id: string) => systemClient.post<unknown, ApiResponse<GraphQueryLog>>(`api/platform/graph/tables/${id}/test`),
  pageLogs: (params: GraphPageParams) => systemClient.get<unknown, ApiResponse<PageResult<GraphQueryLog>>>('api/platform/graph/query-logs', { params }),
}
