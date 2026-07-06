import type { ApiResponse } from './system-client'
import systemClient from './system-client'

export type { ChartType, ChartDataset, ChartSeries, ChartNode, ChartLink } from '@yudream/dataviz'

export interface ChartDataRequest {
  chartType?: ChartType
  datasetQuery?: Record<string, unknown>
}

export default {
  queryDataset: (data: ChartDataRequest) => {
    return systemClient.post<unknown, ApiResponse<ChartDataset>>('api/platform/dataviz/dataset', data)
  },
}
