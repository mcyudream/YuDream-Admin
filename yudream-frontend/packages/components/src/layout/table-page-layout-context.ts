import type { InjectionKey } from 'vue'

/**
 * 由 YdTablePageLayout 提供：内嵌搜索表单提交/重置后，把搜索区收起的回调。
 * YdDynamicSearchForm 注入后在 search/reset 事件后调用；独立使用（未包裹布局）时为空。
 */
export const tablePageSearchFoldKey = Symbol('yd-table-page-search-fold') as InjectionKey<(fold: boolean) => void>
