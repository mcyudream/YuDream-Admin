/** 翻页 + 关键字查询参数，由 fetcher 消费去后端取数 */
export interface YdTablePickerQuery {
  page: number
  size: number
  keyword: string
}

export interface YdTablePickerResult<T = Record<string, unknown>> {
  list: T[]
  total: number
}
