import type { Ref } from 'vue'
import type { ChartDataset, ChartLink, ChartNode, ChartTheme } from '../types'

type MaybeRef<T> = Ref<T> | T
import * as d3 from 'd3'
import { onUnmounted, unref, watch } from 'vue'
import { useResizeObserver } from '@vueuse/core'
import { adaptThemeForD3 } from '../utils/theme-adapters'
import { forceSimulation } from '../utils/d3-layouts'

export interface D3GraphDataset {
  nodes: ChartNode[]
  links: ChartLink[]
}

function extractGraphData(dataset: ChartDataset): D3GraphDataset {
  if (dataset.nodes && dataset.links) {
    return { nodes: dataset.nodes, links: dataset.links }
  }

  const first = dataset.source?.[0]
  if (first && typeof first === 'object' && !Array.isArray(first)) {
    const raw = first as Record<string, unknown>
    if (Array.isArray(raw.nodes) && Array.isArray(raw.links)) {
      return {
        nodes: raw.nodes as ChartNode[],
        links: raw.links as ChartLink[],
      }
    }
  }

  return { nodes: [], links: [] }
}

function radius(node: ChartNode): number {
  const value = typeof node.value === 'number' ? node.value : 0
  return Math.sqrt(value) * 2 + 5
}

/**
 * D3 力导向图组合式函数
 * @param elRef - 挂载图形容器
 * @param dataset - 图表数据集
 * @param theme - 图表主题
 * @returns 渲染方法
 */
export function useD3Graph(
  elRef: Ref<HTMLElement | null | undefined>,
  dataset: MaybeRef<ChartDataset>,
  theme: MaybeRef<ChartTheme> = 'light',
) {
  let simulation: d3.Simulation<d3.SimulationNodeDatum, undefined> | null = null
  let svg: d3.Selection<SVGSVGElement, unknown, null, undefined> | null = null

  function render() {
    const el = elRef.value
    if (!el) {
      return
    }

    const { nodes, links } = extractGraphData(unref(dataset))
    const themeConfig = adaptThemeForD3(unref(theme))

    const width = el.clientWidth || 600
    const height = el.clientHeight || 400

    el.innerHTML = ''
    svg = d3.select(el)
      .append('svg')
      .attr('width', width)
      .attr('height', height)
      .attr('viewBox', [0, 0, width, height])

    const g = svg.append('g')

    const link = g.append('g')
      .attr('stroke', themeConfig.grid)
      .attr('stroke-opacity', 0.6)
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke-width', d => Math.sqrt((d.value as number) || 1))

    const node = g.append('g')
      .attr('stroke', themeConfig.background)
      .attr('stroke-width', 1.5)
      .selectAll('circle')
      .data(nodes)
      .join('circle')
      .attr('r', d => radius(d))
      .attr('fill', (d, i) => {
        const colors = themeConfig.colors
        return colors.length > 0 ? colors[i % colors.length] : '#3b82f6'
      })

    const label = g.append('g')
      .attr('fill', themeConfig.text)
      .selectAll('text')
      .data(nodes)
      .join('text')
      .text(d => d.name)
      .attr('font-size', 12)
      .attr('dx', 8)
      .attr('dy', 3)

    simulation = forceSimulation(nodes, links, width, height)

    simulation.on('tick', () => {
      link
        .attr('x1', d => ((d as unknown as { source: d3.SimulationNodeDatum }).source.x ?? 0))
        .attr('y1', d => ((d as unknown as { source: d3.SimulationNodeDatum }).source.y ?? 0))
        .attr('x2', d => ((d as unknown as { target: d3.SimulationNodeDatum }).target.x ?? 0))
        .attr('y2', d => ((d as unknown as { target: d3.SimulationNodeDatum }).target.y ?? 0))

      node
        .attr('cx', d => ((d as unknown as d3.SimulationNodeDatum).x ?? 0))
        .attr('cy', d => ((d as unknown as d3.SimulationNodeDatum).y ?? 0))

      label
        .attr('x', d => ((d as unknown as d3.SimulationNodeDatum).x ?? 0))
        .attr('y', d => ((d as unknown as d3.SimulationNodeDatum).y ?? 0))
    })

    const drag = d3.drag<SVGCircleElement, ChartNode>()
      .on('start', (event, d) => {
        if (!event.active && simulation) {
          simulation.alphaTarget(0.3).restart()
        }
        const datum = d as unknown as d3.SimulationNodeDatum & { fx?: number | null, fy?: number | null }
        datum.fx = datum.x
        datum.fy = datum.y
      })
      .on('drag', (event, d) => {
        const datum = d as unknown as d3.SimulationNodeDatum & { fx?: number | null, fy?: number | null }
        datum.fx = event.x
        datum.fy = event.y
      })
      .on('end', (event, d) => {
        if (!event.active && simulation) {
          simulation.alphaTarget(0)
        }
        const datum = d as unknown as d3.SimulationNodeDatum & { fx?: number | null, fy?: number | null }
        datum.fx = null
        datum.fy = null
      })

    node.call(drag as any)
  }

  function handleResize() {
    render()
  }

  useResizeObserver(elRef, handleResize)

  watch([elRef, () => unref(dataset), () => unref(theme)], () => {
    if (elRef.value) {
      render()
    }
  }, { deep: true })

  onUnmounted(() => {
    simulation?.stop()
    svg?.remove()
  })

  return {
    render,
  }
}
