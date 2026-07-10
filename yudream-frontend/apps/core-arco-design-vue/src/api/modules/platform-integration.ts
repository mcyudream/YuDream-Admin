import type { ApiResponse, PageResult } from './system-client'
import systemClient from './system-client'

export type HttpMethodType = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
export type ConnectorStatus = 'ACTIVE' | 'DISABLED'
export type RuntimeLanguage = 'PYTHON'
export type ExecutionStatus = 'SUCCESS' | 'FAILED' | 'TIMEOUT'

export interface IntegrationPageParams {
  page: number
  size: number
  keyword?: string
}

export interface HttpConnector {
  id: string
  name: string
  code: string
  url: string
  method: HttpMethodType
  headers: Record<string, string>
  queryParams: Record<string, string>
  bodyTemplate?: string
  timeoutMillis: number
  retryTimes: number
  status: ConnectorStatus
  createTime?: string
  updateTime?: string
}

export interface HttpConnectorPayload {
  name: string
  code: string
  url: string
  method: HttpMethodType
  headers: Record<string, string>
  queryParams: Record<string, string>
  bodyTemplate?: string
  timeoutMillis: number
  retryTimes: number
  status: ConnectorStatus
}

export interface HttpInvokePayload {
  headers: Record<string, string>
  queryParams: Record<string, string>
  body?: string
}

export interface HttpInvocationLog {
  id: string
  connectorId: string
  connectorCode: string
  url: string
  method: HttpMethodType
  requestHeaders: Record<string, string>
  requestBody?: string
  responseStatus: number
  responseBody?: string
  durationMillis: number
  status: ExecutionStatus
  errorMessage?: string
  invokedAt?: string
}

export interface RuntimeScript {
  id: string
  name: string
  code: string
  language: RuntimeLanguage
  scriptContent: string
  timeoutMillis: number
  env: Record<string, string>
  status: ConnectorStatus
  createTime?: string
  updateTime?: string
}

export interface RuntimeScriptPayload {
  name: string
  code: string
  language: RuntimeLanguage
  scriptContent: string
  timeoutMillis: number
  env: Record<string, string>
  status: ConnectorStatus
}

export interface RuntimeExecutionLog {
  id: string
  scriptId: string
  scriptCode: string
  language: RuntimeLanguage
  stdin?: string
  stdout?: string
  stderr?: string
  exitCode: number
  durationMillis: number
  status: ExecutionStatus
  errorMessage?: string
  executedAt?: string
}

export default {
  pageConnectors: (params: IntegrationPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<HttpConnector>>>('api/platform/integration/http-connectors', { params })
  },
  createConnector: (data: HttpConnectorPayload) => {
    return systemClient.post<unknown, ApiResponse<HttpConnector>>('api/platform/integration/http-connectors', data)
  },
  updateConnector: (id: string, data: HttpConnectorPayload) => {
    return systemClient.put<unknown, ApiResponse<HttpConnector>>(`api/platform/integration/http-connectors/${id}`, data)
  },
  disableConnector: (id: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/integration/http-connectors/${id}`)
  },
  enableConnector: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/integration/http-connectors/${id}/enable`)
  },
  invokeConnector: (id: string, data: HttpInvokePayload) => {
    return systemClient.post<unknown, ApiResponse<HttpInvocationLog>>(`api/platform/integration/http-connectors/${id}/invoke`, data)
  },
  pageHttpLogs: (params: IntegrationPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<HttpInvocationLog>>>('api/platform/integration/http-logs', { params })
  },
  pageScripts: (params: IntegrationPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<RuntimeScript>>>('api/platform/integration/runtime-scripts', { params })
  },
  createScript: (data: RuntimeScriptPayload) => {
    return systemClient.post<unknown, ApiResponse<RuntimeScript>>('api/platform/integration/runtime-scripts', data)
  },
  updateScript: (id: string, data: RuntimeScriptPayload) => {
    return systemClient.put<unknown, ApiResponse<RuntimeScript>>(`api/platform/integration/runtime-scripts/${id}`, data)
  },
  disableScript: (id: string) => {
    return systemClient.delete<unknown, ApiResponse<void>>(`api/platform/integration/runtime-scripts/${id}`)
  },
  enableScript: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/integration/runtime-scripts/${id}/enable`)
  },
  executeScript: (id: string, stdin?: string) => {
    return systemClient.post<unknown, ApiResponse<RuntimeExecutionLog>>(`api/platform/integration/runtime-scripts/${id}/execute`, { stdin })
  },
  pageRuntimeLogs: (params: IntegrationPageParams) => {
    return systemClient.get<unknown, ApiResponse<PageResult<RuntimeExecutionLog>>>('api/platform/integration/runtime-logs', { params })
  },
}
