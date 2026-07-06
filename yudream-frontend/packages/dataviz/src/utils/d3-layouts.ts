import type { ChartLink, ChartNode } from '../types'
import * as d3 from 'd3'

/**
 * 创建 D3 力导向仿真
 * @param nodes - 节点数组
 * @param links - 边数组
 * @param width - 画布宽度
 * @param height - 画布高度
 * @returns D3 仿真实例
 */
export function forceSimulation(
  nodes: ChartNode[],
  links: ChartLink[],
  width: number,
  height: number,
) {
  const simulation = d3.forceSimulation(nodes as unknown as d3.SimulationNodeDatum[])
    .force(
      'link',
      d3.forceLink(links as unknown as d3.SimulationLinkDatum<d3.SimulationNodeDatum>[])
        .id((d: unknown) => (d as ChartNode).id)
        .distance(100),
    )
    .force('charge', d3.forceManyBody().strength(-300))
    .force('center', d3.forceCenter(width / 2, height / 2))
    .force(
      'collide',
      d3.forceCollide()
        .radius((d: unknown) => {
          const node = d as ChartNode
          const value = typeof node.value === 'number' ? node.value : 0
          return Math.sqrt(value) * 2 + 8
        })
        .iterations(2),
    )

  return simulation
}
