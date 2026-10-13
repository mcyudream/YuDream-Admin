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
