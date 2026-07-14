import type { ChartDataset, ChartLink, ChartNode, ChartType } from '@yudream/dataviz'

export interface ChartDataRequest {
  chartType?: ChartType
  datasetQuery?: Record<string, unknown>
}

export interface BackendChartSeries {
  name?: string
  categories?: string[]
  values?: Array<number | string>
  nodes?: ChartNode[]
  links?: ChartLink[]
}

export interface BackendChartDataset {
  chartType?: ChartType
  title?: string
  subTitle?: string
  series?: BackendChartSeries[]
}

export function normalizeDataset(raw: BackendChartDataset, request?: ChartDataRequest): ChartDataset {
  const firstSeries = raw.series?.[0]
  const metric = typeof request?.datasetQuery?.metric === 'string' ? request.datasetQuery.metric : ''
  if (firstSeries && (raw.chartType === 'graph' || raw.chartType === 'sankey')) {
    return {
      id: firstSeries.name || 'graph',
      label: raw.title || '',
      chartType: raw.chartType,
      nodes: firstSeries.nodes,
      links: firstSeries.links,
    }
  }
  const dataset: ChartDataset = {
    id: firstSeries?.name || 'default',
    label: raw.title || '',
    chartType: raw.chartType,
    dimensions: ['name', firstSeries?.name || 'value'],
    source: (firstSeries?.categories || []).map((name, index) => ({
      name,
      [firstSeries?.name || 'value']: toFiniteNumber(firstSeries?.values?.[index]),
    })),
  }
  if (raw.chartType === 'line' && metric.includes('-')) {
    dataset.series = [{
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 5,
      showSymbol: true,
      areaStyle: { opacity: 0.62 },
      lineStyle: { width: 2 },
    }]
    dataset.dataZoom = [
      {
        type: 'slider',
        height: 22,
        bottom: 0,
        showDetail: false,
        brushSelect: false,
        start: 0,
        end: 100,
      },
      { type: 'inside' },
    ]
  }
  return dataset
}

function toFiniteNumber(value: number | string | undefined): number {
  const numberValue = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(numberValue) ? numberValue : 0
}
