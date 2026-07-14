import type { ApiResponse } from './system-client'
import systemClient from './system-client'
import type { BackendChartDataset, ChartDataRequest } from './platform-dataviz-normalizer'
import { normalizeDataset } from './platform-dataviz-normalizer'

import type { ChartDataset } from '@yudream/dataviz'

export type { ChartType, ChartDataset, ChartSeries, ChartNode, ChartLink } from '@yudream/dataviz'

export type { ChartDataRequest } from './platform-dataviz-normalizer'
export { normalizeDataset } from './platform-dataviz-normalizer'

export default {
  enableDefinition: (id: string) => {
    return systemClient.post<unknown, ApiResponse<void>>(`api/platform/dataviz/definitions/${id}/enable`)
  },
  queryDataset: async (data: ChartDataRequest) => {
    const res = await systemClient.post<unknown, ApiResponse<BackendChartDataset>>('api/platform/dataviz/dataset', data)
    return {
      ...res,
      data: normalizeDataset(res.data, data),
    } as ApiResponse<ChartDataset>
  },
}
