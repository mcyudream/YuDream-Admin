import type { AgentNodeData, AgentNodeKind } from '../components/types'
import { agentModelKind, isAgentChatModelNode } from './agent-node-data'

interface WorkflowNodeLike {
  id: string
  data?: Partial<AgentNodeData> | null
}

interface WorkflowEdgeLike {
  source: string
  target: string
  sourceHandle?: null | string
}

interface ModelLike {
  providerCode: string
  modelCode: string
  kind: string
  configured: boolean
  vision?: boolean
}

export interface AgentWorkflowValidationCatalog {
  models: ModelLike[]
  knowledgeSpaceSlugs: Set<string>
  toolCodes: Set<string>
}

export interface AgentWorkflowValidationIssue {
  nodeId?: string
  message: string
}

export function validateAgentWorkflow(
  nodes: WorkflowNodeLike[],
  edges: WorkflowEdgeLike[],
  catalog: AgentWorkflowValidationCatalog,
) {
  const issues: AgentWorkflowValidationIssue[] = []
  const starts = nodes.filter(node => node.data?.kind === 'start')
  const ends = nodes.filter(node => node.data?.kind === 'end')
  if (starts.length !== 1) {
    issues.push({ message: '工作流必须且只能包含一个开始节点' })
  }
  if (ends.length !== 1) {
    issues.push({ message: '工作流必须且只能包含一个结束节点' })
  }
  if (starts.length === 1 && ends.length === 1 && !reachable(starts[0].id, ends[0].id, edges)) {
    issues.push({ nodeId: ends[0].id, message: '结束节点必须能够从开始节点到达' })
  }

  nodes.forEach((node) => {
    const data = node.data || {}
    const require = (value: unknown, label: string): value is string => {
      if (typeof value !== 'string' || !value.trim()) {
        issues.push({ nodeId: node.id, message: `节点“${data.title}”：${label}不能为空` })
        return false
      }
      return true
    }
    if (data.kind === 'condition') {
      require(data.condition, '条件表达式')
      const handles = new Set(edges.filter(edge => edge.source === node.id).map(edge => edge.sourceHandle))
      if (!handles.has('true') || !handles.has('false')) {
        issues.push({ nodeId: node.id, message: `节点“${data.title}”：条件节点必须同时连接 true 和 false 分支` })
      }
    }
    else if (data.kind === 'code') {
      require(data.code, 'Python 代码')
    }
    else if (data.kind === 'template') {
      require(data.template, '模板内容')
    }
    else if (data.kind === 'search' || data.kind === 'vector') {
      const knowledgeSpaceSlug = data.knowledgeSpaceSlug
      if (require(knowledgeSpaceSlug, '知识空间') && !catalog.knowledgeSpaceSlugs.has(knowledgeSpaceSlug)) {
        issues.push({ nodeId: node.id, message: `节点“${data.title}”：知识空间不可用` })
      }
    }
    else if (data.kind && agentModelKind(data.kind)) {
      const kind = agentModelKind(data.kind)
      let configured: ModelLike | undefined
      if (require(data.providerCode, '模型提供方') && require(data.modelCode, '模型')) {
        configured = catalog.models.find(model => model.configured
          && model.kind.toLowerCase() === kind
          && model.providerCode === data.providerCode
          && model.modelCode === data.modelCode)
        if (!configured) {
          issues.push({ nodeId: node.id, message: `节点“${data.title}”：模型未配置或类型不匹配` })
        }
      }
      if (configured && data.kind === 'vision' && !configured.vision) {
        issues.push({ nodeId: node.id, message: `节点“${data.title || node.id}”：所选模型不支持 Vision 图片输入` })
      }
      validateModelToolConfiguration(node.id, data, catalog, issues)
      if (data.kind === 'extract' && !jsonObject(data.outputSchema)) {
        issues.push({ nodeId: node.id, message: `节点“${data.title || node.id}”：输出 Schema 必须是 JSON 对象` })
      }
      if (data.kind === 'classify' && distinctLabels(data.classes).length < 2) {
        issues.push({ nodeId: node.id, message: `节点“${data.title || node.id}”：分类标签至少需要两个不重复的值` })
      }
    }
    else if (data.kind === 'document') {
      require(data.documentInput, '文档输入')
    }
    else if (data.kind === 'citation') {
      require(data.citationSource, '引用来源')
    }
    else if (data.kind === 'tool') {
      const toolCode = data.toolCode
      if (require(toolCode, '工具') && !catalog.toolCodes.has(toolCode)) {
        issues.push({ nodeId: node.id, message: `节点“${data.title}”：工具不可用` })
      }
    }
  })
  return { valid: issues.length === 0, issues }
}

function validateModelToolConfiguration(
  nodeId: string,
  data: Partial<AgentNodeData>,
  catalog: AgentWorkflowValidationCatalog,
  issues: AgentWorkflowValidationIssue[],
) {
  if (!data.kind || !isAgentChatModelNode(data.kind)) {
    return
  }
  const toolCodes = strings(data.toolCodes)
  const mode = data.toolMode || 'NONE'
  if (mode !== 'NONE' && mode !== 'AUTO' && mode !== 'REQUIRED') {
    issues.push({ nodeId, message: `节点“${data.title || nodeId}”：工具调用模式无效` })
    return
  }
  if (mode === 'NONE' && toolCodes.length) {
    issues.push({ nodeId, message: `节点“${data.title || nodeId}”：禁用工具时不能选择工具` })
  }
  if ((mode === 'AUTO' || mode === 'REQUIRED') && !toolCodes.length) {
    issues.push({ nodeId, message: `节点“${data.title || nodeId}”：自动或必须调用模式至少需要一个工具` })
  }
  toolCodes.forEach((toolCode) => {
    if (!catalog.toolCodes.has(toolCode)) {
      issues.push({ nodeId, message: `节点“${data.title || nodeId}”：工具“${toolCode}”不可用` })
    }
  })
}

function jsonObject(value: unknown) {
  if (typeof value !== 'string' || !value.trim()) {
    return false
  }
  try {
    const parsed = JSON.parse(value)
    return Boolean(parsed) && typeof parsed === 'object' && !Array.isArray(parsed)
  }
  catch {
    return false
  }
}

function distinctLabels(value: unknown) {
  return [...new Set(strings(value).map(item => item.toLocaleLowerCase()))]
}

function strings(value: unknown) {
  return Array.isArray(value)
    ? value.filter((item): item is string => typeof item === 'string').map(item => item.trim()).filter(Boolean)
    : []
}

export function migrateConditionSourceHandle(
  sourceKind: AgentNodeKind | undefined,
  sourceHandle: null | string | undefined,
  label: unknown,
  data: Record<string, unknown> | undefined,
) {
  if (sourceKind !== 'condition' || (sourceHandle && sourceHandle !== 'source')) {
    return sourceHandle || undefined
  }
  const legacy = typeof label === 'string' ? label : typeof data?.branch === 'string' ? data.branch : ''
  const normalized = legacy.trim().toLowerCase()
  return normalized === 'true' || normalized === 'false' ? normalized : sourceHandle || undefined
}

function reachable(startId: string, endId: string, edges: WorkflowEdgeLike[]) {
  const visited = new Set<string>()
  const queue = [startId]
  while (queue.length) {
    const current = queue.shift()!
    if (current === endId) {
      return true
    }
    if (visited.has(current)) {
      continue
    }
    visited.add(current)
    edges.filter(edge => edge.source === current).forEach(edge => queue.push(edge.target))
  }
  return false
}
