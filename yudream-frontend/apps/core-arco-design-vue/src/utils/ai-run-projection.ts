import type { YdChatActivity, YdChatToolEvent } from '@yudream/components'

function record(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

/** Completed tool results have no status in the existing backend DTO; preserve their actual terminal meaning. */
export function projectAiRunTool(value: unknown, eventAction?: string): YdChatToolEvent | undefined {
  const tool = record(value)
  if (typeof tool.toolName !== 'string' || !tool.toolName) {
    return undefined
  }
  const status: YdChatToolEvent['status'] = tool.status === 'complete' || tool.status === 'error' || tool.status === 'cancelled' || tool.status === 'executing'
    ? tool.status
    : eventAction === 'start' || eventAction === 'running' ? 'executing' : 'complete'
  return {
    toolCallId: typeof tool.toolCallId === 'string' ? tool.toolCallId : undefined,
    toolName: tool.toolName,
    action: typeof tool.action === 'string' ? tool.action : undefined,
    message: typeof tool.message === 'string' ? tool.message : undefined,
    status,
    payload: record(tool.payload),
  }
}

export interface AiRunActivityProjection extends YdChatActivity {
  nodeId?: string
  nodeTitle?: string
  nodeKind?: string
  nodeStatus?: string
}

/** Replay updates the same phase instead of rendering stale running and completed rows together. */
export function projectAiRunActivities(values: unknown[] = [], runId: string): AiRunActivityProjection[] {
  const phases = new Map<string, AiRunActivityProjection>()
  for (const value of values) {
    const activity = projectAiRunActivity(value, runId)
    if (activity) {
      phases.set(JSON.stringify([activity.activityType, activity.phase]), activity)
    }
  }
  return [...phases.values()]
}

export function projectAiRunActivity(value: unknown, runId: string): AiRunActivityProjection | undefined {
  const activity = record(value)
  if (!Object.keys(activity).length) {
    return undefined
  }
  const graph = record(activity.graph)
  const nodeId = typeof activity.nodeId === 'string' ? activity.nodeId : undefined
  const nodeStatus = typeof activity.nodeStatus === 'string' ? activity.nodeStatus : nodeId && typeof activity.status === 'string' ? activity.status : undefined
  const status = typeof activity.status === 'string' ? activity.status.toLowerCase() : 'running'
  const nodes = Array.isArray(graph.nodes)
    ? graph.nodes.flatMap((value) => {
        const node = record(value)
        return typeof node.id === 'string' && typeof node.title === 'string' ? [{ id: node.id, title: node.title, path: typeof node.path === 'string' ? node.path : undefined, type: typeof node.type === 'string' ? node.type : undefined }] : []
      })
    : []
  const edges = Array.isArray(graph.edges)
    ? graph.edges.flatMap((value) => {
        const edge = record(value)
        return typeof edge.source === 'string' && typeof edge.target === 'string' ? [{ source: edge.source, target: edge.target, weight: typeof edge.weight === 'number' ? edge.weight : undefined }] : []
      })
    : []
  return {
    messageId: runId,
    activityType: typeof activity.activityType === 'string' ? activity.activityType : nodeId ? 'agent-node' : 'ai-progress',
    phase: typeof activity.phase === 'string' ? activity.phase : nodeId || (typeof activity.title === 'string' ? activity.title : 'progress'),
    title: typeof activity.title === 'string' ? activity.title : typeof activity.nodeTitle === 'string' ? activity.nodeTitle : undefined,
    content: typeof activity.content === 'string' ? activity.content : typeof activity.message === 'string' ? activity.message : undefined,
    status: status === 'completed' || status === 'skipped' ? 'complete' : status === 'failed' ? 'error' : status,
    nodeId,
    nodeTitle: typeof activity.nodeTitle === 'string' ? activity.nodeTitle : undefined,
    nodeKind: typeof activity.nodeKind === 'string' ? activity.nodeKind : undefined,
    nodeStatus,
    query: typeof activity.query === 'string' ? activity.query : undefined,
    graph: nodes.length ? { nodes, edges, query: typeof graph.query === 'string' ? graph.query : undefined } : undefined,
    hits: Array.isArray(activity.hits)
      ? activity.hits.flatMap((raw) => {
          const hit = record(raw)
          return typeof hit.title === 'string'
            ? [{
                title: hit.title,
                excerpt: typeof hit.excerpt === 'string' ? hit.excerpt : undefined,
                path: typeof hit.path === 'string' ? hit.path : undefined,
                nodeId: typeof hit.nodeId === 'string' ? hit.nodeId : undefined,
                spaceSlug: typeof hit.spaceSlug === 'string' ? hit.spaceSlug : undefined,
                sourceUrl: typeof hit.sourceUrl === 'string' ? hit.sourceUrl : undefined,
                score: typeof hit.score === 'number' ? hit.score : undefined,
              }]
            : []
        })
      : undefined,
  }
}

/** Harness/工作流进度阶段的中文标签（与组件包 YdChatProcess 内部映射保持一致）。 */
const PHASE_LABELS: Record<string, string> = {
  subscribed: '任务已接收',
  accepted: '任务已接收',
  analysis: '生成回答',
  reasoning: '深度思考',
  waiting: '等待输入',
  'awaiting-approval': '等待工具确认',
  'tool-start': '执行工具',
  'tool-complete': '工具完成',
  start: '开始处理',
  complete: '处理完成',
  failed: '处理失败',
  cancelled: '已取消',
}

export function aiRunPhaseLabel(phase?: string): string | undefined {
  if (!phase) return undefined
  return PHASE_LABELS[phase] ?? undefined
}
