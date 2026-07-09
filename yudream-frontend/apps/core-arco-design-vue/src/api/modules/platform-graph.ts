import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type GraphConnectionStatus = 'ACTIVE' | 'DISABLED'
export type GraphQueryStatus = 'SUCCESS' | 'FAILED'

export interface GraphPageParams {
  page: number
  size: number
  keyword?: string
}

export interface GraphConnection {
  id: string
  name: string
  code: string
  uri: string
  username: string
  database: string
  status: GraphConnectionStatus
  createTime?: string
  updateTime?: string
}

export interface GraphConnectionPayload {
  name: string
  code: string
  uri: string
  username: string
  password?: string
  database?: string
  status: GraphConnectionStatus
}

export interface GraphQueryPayload {
  cypher: string
  params: Record<string, any>
}

export interface GraphQueryLog {
  id: string
  connectionId: string
  connectionCode: string
  cypher: string
  params: Record<string, any>
  rows: Record<string, any>[]
  summary?: string
  durationMillis: number
  status: GraphQueryStatus
  errorMessage?: string
  executedAt?: string
}

export default {
  pageConnections: (params: GraphPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<GraphConnection>>>('api/platform/graph/connections', { params })
  },
  createConnection: (data: GraphConnectionPayload) => {
    return systemClient.post<unknown, ApiResponse<GraphConnection>>('api/platform/graph/connections', data)
  },
  updateConnection: (id: string, data: GraphConnectionPayload) => {
    return systemClient.put<unknown, ApiResponse<GraphConnection>>(`api/platform/graph/connections/${id}`, data)
  },
  disableConnection: (id: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/graph/connections/${id}`)
  },
  testConnection: (id: string) => {
    return systemClient.post<unknown, ApiResponse<GraphQueryLog>>(`api/platform/graph/connections/${id}/test`)
  },
  query: (id: string, data: GraphQueryPayload) => {
    return systemClient.post<unknown, ApiResponse<GraphQueryLog>>(`api/platform/graph/connections/${id}/query`, data)
  },
  pageLogs: (params: GraphPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<GraphQueryLog>>>('api/platform/graph/query-logs', { params })
  },
}
