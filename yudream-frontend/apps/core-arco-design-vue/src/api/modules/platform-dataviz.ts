import type { ApiResponse } from './system-client'
import systemClient from './system-client'

import type { ChartDataset, ChartLink, ChartNode, ChartType } from '@yudream/dataviz'

export type { ChartType, ChartDataset, ChartSeries, ChartNode, ChartLink } from '@yudream/dataviz'

export interface ChartDataRequest {
  chartType?: ChartType
  datasetQuery?: Record<string, unknown>
}

interface BackendChartSeries {
  name?: string
  categories?: string[]
  values?: number[]
  nodes?: ChartNode[]
  links?: ChartLink[]
}

interface BackendChartDataset {
  chartType?: ChartType
  title?: string
  subTitle?: string
  series?: BackendChartSeries[]
}

function normalizeDataset(raw: BackendChartDataset, request?: ChartDataRequest): ChartDataset {
  const firstSeries = raw.series?.[0]
  const metric = typeof request?.datasetQuery?.metric === 'string' ? request.datasetQuery.metric : ''
  if (firstSeries?.nodes && firstSeries.links) {
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
      [firstSeries?.name || 'value']: firstSeries?.values?.[index] ?? 0,
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

export default {
  queryDataset: async (data: ChartDataRequest) => {
    const res = await systemClient.post<unknown, ApiResponse<BackendChartDataset>>('api/platform/dataviz/dataset', data)
    return {
      ...res,
      data: normalizeDataset(res.data, data),
    } as ApiResponse<ChartDataset>
  },
}
