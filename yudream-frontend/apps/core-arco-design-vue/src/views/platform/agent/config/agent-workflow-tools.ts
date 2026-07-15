import type { AgentNodeData } from '../components/types'
import { isAgentToolConfigModelNode } from './agent-node-data'

export interface AgentWorkflowToolNode {
  id: string
  data?: Partial<AgentNodeData> | null
  [key: string]: unknown
}

export interface AgentWorkflowToolEdge {
  source: string
  target: string
  sourceHandle?: null | string
  targetHandle?: null | string
  data?: Record<string, unknown>
  [key: string]: unknown
}

export interface AgentWorkflowToolGraph<TNode extends AgentWorkflowToolNode = AgentWorkflowToolNode, TEdge extends AgentWorkflowToolEdge = AgentWorkflowToolEdge> {
  nodes: readonly TNode[]
  edges: readonly TEdge[]
}

interface LegacyToolMigration<TEdge extends AgentWorkflowToolEdge> {
  toolNodeId: string
  modelNodeId: string
  toolCode: string
  incoming: TEdge
  outgoing: TEdge
}

export function deriveAgentApplicationToolCodes(nodes: readonly AgentWorkflowToolNode[]) {
  const codes: string[] = []
  for (const node of nodes) {
    const kind = node.data?.kind
    if (kind && isAgentToolConfigModelNode(kind) && node.data?.toolConfigDeclared) {
      codes.push(...strings(node.data?.toolCodes))
    }
    else if (kind === 'tool') {
      const toolCode = string(node.data?.toolCode)
      if (toolCode) {
        codes.push(toolCode)
      }
    }
  }
  return orderedUnique(codes)
}

export function migrateLegacyToolNodes<TNode extends AgentWorkflowToolNode, TEdge extends AgentWorkflowToolEdge>(
  graph: AgentWorkflowToolGraph<TNode, TEdge>,
): { nodes: TNode[], edges: TEdge[] } {
  const nodes = graph.nodes.map(cloneNode)
  const edges = graph.edges.map(cloneEdge)
  const nodesById = new Map(graph.nodes.map(node => [node.id, node]))
  const migrations = findMigrations(graph.nodes, graph.edges, nodesById)
  if (!migrations.length) {
    return { nodes, edges }
  }

  const toolCodesByModel = new Map<string, string[]>()
  migrations.forEach(({ modelNodeId, toolCode }) => {
    const codes = toolCodesByModel.get(modelNodeId) || []
    codes.push(toolCode)
    toolCodesByModel.set(modelNodeId, codes)
  })

  const migratedNodes = nodes
    .filter(node => !migrations.some(migration => migration.toolNodeId === node.id))
    .map((node) => {
      const addedToolCodes = toolCodesByModel.get(node.id)
      if (!addedToolCodes || !node.data) {
        return node
      }
      return {
        ...node,
        data: {
          ...node.data,
          toolCodes: orderedUnique([...strings(node.data.toolCodes), ...addedToolCodes]),
          toolMode: migratedToolMode(node.data.toolMode),
          toolConfigDeclared: true,
        },
      }
    }) as TNode[]

  const migrationByToolNodeId = new Map(migrations.map(migration => [migration.toolNodeId, migration]))
  const migratedEdges: TEdge[] = []
  for (const edge of edges) {
    const incomingMigration = migrationByToolNodeId.get(edge.target)
    if (incomingMigration) {
      continue
    }
    const outgoingMigration = migrationByToolNodeId.get(edge.source)
    if (!outgoingMigration) {
      migratedEdges.push(edge)
      continue
    }
    migratedEdges.push({
      ...edge,
      source: outgoingMigration.modelNodeId,
      target: outgoingMigration.outgoing.target,
      sourceHandle: outgoingMigration.incoming.sourceHandle,
      targetHandle: outgoingMigration.outgoing.targetHandle,
    } as TEdge)
  }
  return { nodes: migratedNodes, edges: migratedEdges }
}

function findMigrations<TNode extends AgentWorkflowToolNode, TEdge extends AgentWorkflowToolEdge>(
  nodes: readonly TNode[],
  edges: readonly TEdge[],
  nodesById: ReadonlyMap<string, TNode>,
) {
  const migrations: LegacyToolMigration<TEdge>[] = []
  for (const node of nodes) {
    if (node.data?.kind !== 'tool') {
      continue
    }
    const toolCode = string(node.data.toolCode)
    const incoming = edges.filter(edge => edge.target === node.id)
    const outgoing = edges.filter(edge => edge.source === node.id)
    if (!toolCode || incoming.length !== 1 || outgoing.length !== 1) {
      continue
    }
    const predecessor = nodesById.get(incoming[0].source)
    if (!predecessor?.data?.kind || !isAgentToolConfigModelNode(predecessor.data.kind)) {
      continue
    }
    const predecessorOutgoing = edges.filter(edge => edge.source === predecessor.id)
    if (predecessorOutgoing.length !== 1 || predecessorOutgoing[0] !== incoming[0]) {
      continue
    }
    if (outgoing[0].target === predecessor.id) {
      continue
    }
    migrations.push({
      toolNodeId: node.id,
      modelNodeId: predecessor.id,
      toolCode,
      incoming: incoming[0],
      outgoing: outgoing[0],
    })
  }
  return migrations
}

function cloneNode<TNode extends AgentWorkflowToolNode>(node: TNode) {
  return cloneRecord(node) as TNode
}

function cloneEdge<TEdge extends AgentWorkflowToolEdge>(edge: TEdge) {
  return cloneRecord(edge) as TEdge
}

function cloneRecord(value: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(value).map(([key, item]) => [key, cloneValue(item)]))
}

function cloneValue(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(cloneValue)
  }
  if (value && typeof value === 'object') {
    return cloneRecord(value as Record<string, unknown>)
  }
  return value
}

function orderedUnique(values: string[]) {
  return [...new Set(values)]
}

function migratedToolMode(value: unknown) {
  return value === 'AUTO' || value === 'REQUIRED' ? value : 'AUTO'
}

function strings(value: unknown) {
  return Array.isArray(value)
    ? value.map(string).filter(Boolean)
    : []
}

function string(value: unknown) {
  return typeof value === 'string' ? value.trim() : ''
}
