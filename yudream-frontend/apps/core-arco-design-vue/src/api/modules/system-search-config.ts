import type { SearchConfig } from '@yudream/components'
import type { ApiResponse } from './system-client'
import systemClient from './system-client'

/**
 * 搜索配置：按查询类全限定名获取 @Searchable 注解产物（后端 /api/search-config）。
 */
export function fetchSearchConfig(queryClass: string) {
  return systemClient.get<unknown, ApiResponse<SearchConfig>>('api/search-config', {
    params: { queryClass },
  })
}
