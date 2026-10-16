import type { SearchConfig } from './search-config-types'

/**
 * API响应包装类型
 */
interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

/**
 * 获取搜索配置
 * @param queryClass 查询类的全限定名
 */
export async function getSearchConfig(queryClass: string): Promise<SearchConfig> {
  // 这里需要根据实际项目的API调用方式进行调整
  // 示例使用fetch,实际项目可能使用axios或其他HTTP客户端
  const response = await fetch(`/api/search-config?queryClass=${encodeURIComponent(queryClass)}`)

  if (!response.ok) {
    throw new Error(`获取搜索配置失败: ${response.statusText}`)
  }

  const result: ApiResponse<SearchConfig> = await response.json()

  if (result.code !== 200) {
    throw new Error(result.message || '获取搜索配置失败')
  }

  return result.data
}

/**
 * 搜索配置缓存
 */
const configCache = new Map<string, SearchConfig>()

/**
 * 获取搜索配置(带缓存)
 * @param queryClass 查询类的全限定名
 * @param useCache 是否使用缓存,默认true
 */
export async function getCachedSearchConfig(
  queryClass: string,
  useCache = true,
): Promise<SearchConfig> {
  if (useCache && configCache.has(queryClass)) {
    return configCache.get(queryClass)!
  }

  const config = await getSearchConfig(queryClass)
  configCache.set(queryClass, config)

  return config
}

/**
 * 清除搜索配置缓存
 * @param queryClass 查询类的全限定名,不传则清除所有
 */
export function clearSearchConfigCache(queryClass?: string): void {
  if (queryClass) {
    configCache.delete(queryClass)
  }
  else {
    configCache.clear()
  }
}
