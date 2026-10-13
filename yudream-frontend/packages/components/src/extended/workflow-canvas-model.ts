export interface YdWorkflowPoint {
  x: number
  y: number
}

export interface YdWorkflowNode<TData = Record<string, unknown>> {
  id: string
  type: string
  position: YdWorkflowPoint
  data: TData
}

export interface YdWorkflowEdge<TData = Record<string, unknown>> {
  id: string
  source: string
  target: string
  sourceHandle?: string | null
  targetHandle?: string | null
  type?: string
  label?: string
  markerEnd?: string
  class?: string
  data?: TData
}

export interface YdWorkflowConnection {
  source: string | null
  target: string | null
  sourceHandle?: string | null
  targetHandle?: string | null
}

export function canEditYdWorkflowCanvas(state: { disabled?: boolean, readonly?: boolean }): boolean {
  return !state.disabled && !state.readonly
}

export function resolveYdWorkflowDeleteKeyCode(state: { disabled?: boolean, readonly?: boolean }, keyCode?: string | null): string | null {
  return canEditYdWorkflowCanvas(state) ? keyCode ?? null : null
}

export function getYdWorkflowNodeSlotNames(names: string[]): Array<`node-${string}`> {
  return names.filter((name): name is `node-${string}` => name.startsWith('node-'))
}

export function normalizeYdWorkflowConnection(connection: YdWorkflowConnection): YdWorkflowConnection {
  return {
    source: connection.source ?? null,
    target: connection.target ?? null,
    sourceHandle: connection.sourceHandle,
    targetHandle: connection.targetHandle,
  }
}

export interface YdWorkflowClickNode {
  id: string
}

export interface YdWorkflowClickEdge {
  id: string
}

export type YdWorkflowHandleType = 'source' | 'target'
export type YdWorkflowPosition = 'left' | 'top' | 'right' | 'bottom'
export type YdWorkflowHandleConnectable<TData = Record<string, unknown>>
  = boolean | number | 'single' | ((node: YdWorkflowNode<TData>, edges: YdWorkflowEdge[]) => boolean)

export interface YdWorkflowCanvasNodeSlot<TData = Record<string, unknown>> {
  id: string
  data: TData
  selected?: boolean
  connectable?: YdWorkflowHandleConnectable<TData>
}

export interface YdWorkflowViewportOptions {
  duration?: number
  padding?: number
}

export interface YdWorkflowCenterOptions {
  duration?: number
  zoom?: number
}

export interface YdWorkflowCanvasInstance<TNodeData = Record<string, unknown>, TEdgeData = Record<string, unknown>> {
  addNodes: (value: YdWorkflowNode<TNodeData> | YdWorkflowNode<TNodeData>[]) => void
  addEdges: (value: YdWorkflowEdge<TEdgeData> | YdWorkflowEdge<TEdgeData>[]) => void
  updateNodeData: (id: string, patch: Partial<TNodeData>) => void
  screenToCanvasCoordinate: (point: YdWorkflowPoint) => YdWorkflowPoint
  fitView: (options?: YdWorkflowViewportOptions) => Promise<boolean>
  setCenter: (x: number, y: number, options?: YdWorkflowCenterOptions) => Promise<boolean>
}

export interface YdWorkflowBackgroundOptions {
  patternColor?: string
  gap?: number
  size?: number
}

export type YdWorkflowControlPosition = 'top-left' | 'top-center' | 'top-right' | 'bottom-left' | 'bottom-center' | 'bottom-right'

export interface YdWorkflowControlsOptions {
  position?: YdWorkflowControlPosition
}

export interface YdWorkflowMiniMapOptions {
  position?: YdWorkflowControlPosition
  pannable?: boolean
  zoomable?: boolean
  width?: number
  height?: number
}
