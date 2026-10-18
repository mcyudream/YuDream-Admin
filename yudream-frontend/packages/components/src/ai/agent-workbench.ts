import type { YdChatAttachment } from './useYdChatStream'

/** UI projection only. Runtime, permissions and persistence belong to the host Harness. */
export type YdAgentMode = 'ask' | 'plan' | 'code'
export type YdAgentRunStatus = 'idle' | 'running' | 'waiting' | 'completed' | 'error' | 'cancelled'
export interface YdAgentSession {
  id: string
  title: string
  pinned?: boolean
  messageCount?: number
  updatedAt?: string
  workspaceId?: string
  status?: YdAgentRunStatus
  /** 网关会话范围（GENERAL/AGENT/WIKI/CONSOLE），用于业务面板筛选自己的会话 */
  scopeType?: string
  /** 网关会话绑定的 agent（如 cms-canvas / cms-builder） */
  agentCode?: string
}
export interface YdAgentWorkspace {
  id: string
  name: string
  path?: string
  branch?: string
  kind: 'local' | 'worktree' | 'remote'
  access: 'read' | 'write' | 'unavailable'
  description?: string
}
export interface YdAgentModel {
  id: string
  label: string
  providerCode: string
  modelCode: string
}
export interface YdAgentHarnessState {
  name: string
  runId?: string
  status: YdAgentRunStatus
  phase?: string
  elapsedMs?: number
  contextTokens?: number
  contextLimit?: number
  checkpoint?: string
  memory?: string
}
export interface YdAgentSkill {
  code: string
  name: string
  description: string
  source?: string
  enabled: boolean
}
export interface YdAgentMcpServer {
  code: string
  name: string
  status: 'connected' | 'disconnected' | 'error'
  description?: string
  transport?: string
  tools?: string[]
}
export interface YdAgentContextFile {
  path: string
  description?: string
  included?: boolean
}
export interface YdAgentChange {
  id: string
  path: string
  status: 'added' | 'modified' | 'removed'
  additions: number
  deletions: number
  /** Unified diff text, rendered as text; never executed. */
  diff?: string
}
export interface YdAgentActivity {
  id: string
  title: string
  kind: 'context' | 'skill' | 'mcp' | 'edit' | 'test' | 'checkpoint'
  status: 'pending' | 'running' | 'complete' | 'error' | 'waiting'
  detail?: string
  input?: string
  output?: string
  durationMs?: number
}
export interface YdAgentApproval {
  id: string
  title: string
  description: string
  scope?: string
  command?: string
}
export interface YdAgentSendRequest {
  text: string
  attachments: YdChatAttachment[]
  sessionId?: string
  workspaceId?: string
  model?: YdAgentModel
  mode: YdAgentMode
  skillCodes: string[]
  contextPaths: string[]
}

/** Harness/工作流进度阶段的统一中文标签；后端事件只带 phase 时不至于露出英文键。 */
export const YD_ACTIVITY_PHASE_LABELS: Record<string, string> = {
  subscribed: '任务已接收',
  accepted: '任务已接收',
  running: '执行中',
  analysis: '生成回答',
  reasoning: '深度思考',
  waiting: '等待输入',
  'awaiting-approval': '等待工具确认',
  'tool-start': '执行工具',
  'tool-complete': '工具完成',
  'waiting-approval': '等待批准',
  start: '开始处理',
  complete: '处理完成',
  failed: '处理失败',
  cancelled: '已取消',
}

export function ydActivityPhaseLabel(phase?: string): string | undefined {
  if (!phase) return undefined
  return YD_ACTIVITY_PHASE_LABELS[phase] ?? undefined
}
