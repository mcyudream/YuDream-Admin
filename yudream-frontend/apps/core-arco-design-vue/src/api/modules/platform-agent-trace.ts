import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type AgentTraceSource = 'CHAT' | 'WIKI' | 'CMS' | 'DEBUG' | 'PLUGIN' | 'SYSTEM'
export type AgentTraceStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED'

export interface AiUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface AgentTraceSummary {
  traceId: string
  source: AgentTraceSource
  ownerPluginCode?: string
  agentId?: string
  agentCode?: string
  agentName?: string
  status: AgentTraceStatus
  input?: string
  error?: string
  stepCount: number
  durationMs?: number
  startTime?: string
}

export interface AgentTraceStep {
  seq: number
  nodeId?: string
  nodeKind?: string
  nodeTitle?: string
  status: 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SKIPPED' | string
  inputSummary?: string
  outputSummary?: string
  reasoning?: string
  toolName?: string
  toolDetail?: string
  message?: string
  startTime?: string
  endTime?: string
  durationMs?: number
}

export interface AgentTraceDetail extends AgentTraceSummary {
  finalOutput?: string
  reasoning?: string
  usage?: AiUsage
  steps: AgentTraceStep[]
  endTime?: string
}

export interface AgentTracePage {
  total: number
  page: number
  size: number
  list: AgentTraceSummary[]
}

export interface AgentTraceBucket {
  key: string
  label: string
  count: number
}

export interface AgentTraceStats {
  total: number
  succeeded: number
  failed: number
  running: number
  avgDurationMs: number
  maxDurationMs: number
  promptTokens: number
  completionTokens: number
  totalTokens: number
  sources: AgentTraceBucket[]
  agents: AgentTraceBucket[]
}

export interface AgentTraceQueryParams {
  source?: string
  pluginCode?: string
  status?: string
  keyword?: string
  agentCode?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}

export default {
  page: (params?: AgentTraceQueryParams) => {
    return systemClient.get<unknown, ApiResponse<AgentTracePage>>('api/platform/agent-traces', { params })
  },
  stats: (params?: AgentTraceQueryParams) => {
    return systemClient.get<unknown, ApiResponse<AgentTraceStats>>('api/platform/agent-traces/stats', { params })
  },
  detail: (traceId: string) => {
    return systemClient.get<unknown, ApiResponse<AgentTraceDetail>>(`api/platform/agent-traces/${encodeURIComponent(traceId)}`)
  },
  delete: (traceId: string) => {
    return systemClient.delete<unknown, ApiResponse<number>>(`api/platform/agent-traces/${encodeURIComponent(traceId)}`)
  },
  clear: (params?: AgentTraceQueryParams) => {
    return systemClient.delete<unknown, ApiResponse<number>>('api/platform/agent-traces', { params })
  },
}
