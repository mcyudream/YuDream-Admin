import type { SearchConfig, SearchOptionsSource } from '../search/search-config-types'
import { onMounted, reactive, ref } from 'vue'
import { getCachedSearchConfig } from '../search/search-config-api'

/**
 * 分页结果
 */
export interface PageResult<T> {
  records: T[]
  total: number
  page: number
  size: number
}

/**
 * 分页查询参数
 */
export interface PageQuery {
  page: number
  size: number
  [key: string]: any
}

/**
 * useTablePage 配置选项
 */
export interface UseTablePageOptions<T, Q extends PageQuery> {
  /** 查询类的全限定名(用于获取搜索配置) */
  queryClass: string
  /** 加载数据的API函数 */
  loadApi: (query: Q) => Promise<PageResult<T>>
  /** 初始查询条件 */
  initialQuery?: Partial<Q>
  /** 初始页码 */
  initialPage?: number
  /** 初始每页数量 */
  initialSize?: number
  /** 选项数据源 */
  optionsSources?: SearchOptionsSource[]
  /** 是否自动加载 */
  autoLoad?: boolean
}

/**
 * 表格页面统一逻辑封装
 *
 * @example
 * ```ts
 * const {
 *   loading,
 *   rows,
 *   pagination,
 *   searchQuery,
 *   searchConfig,
 *   load,
 *   resetSearch,
 * } = useTablePage({
 *   queryClass: 'online.yudream.base.application.system.user.query.UserPageQuery',
 *   loadApi: apiUser.page,
 *   initialQuery: { status: 'ACTIVE' },
 * })
 * ```
 */
export function useTablePage<T = any, Q extends PageQuery = PageQuery>(
  options: UseTablePageOptions<T, Q>,
) {
  const {
    queryClass,
    loadApi,
    initialQuery = {},
    initialPage = 1,
    initialSize = 20,
    optionsSources = [],
    autoLoad = true,
  } = options

  // 加载状态
  const loading = ref(false)

  // 表格数据
  const rows = ref<T[]>([])

  // 分页信息
  const pagination = reactive({
    page: initialPage,
    size: initialSize,
    total: 0,
  })

  // 搜索条件
  const searchQuery = reactive<Record<string, any>>({
    ...initialQuery,
  })

  // 搜索配置
  const searchConfig = ref<SearchConfig>()

  // 加载搜索配置
  async function loadSearchConfig() {
    try {
      searchConfig.value = await getCachedSearchConfig(queryClass)
    }
    catch (error) {
      console.error('加载搜索配置失败:', error)
    }
  }

  // 加载数据
  async function load() {
    loading.value = true
    try {
      const query = {
        ...searchQuery,
        page: pagination.page,
        size: pagination.size,
      } as Q

      const result = await loadApi(query)

      rows.value = result.records
      pagination.total = result.total
      pagination.page = result.page
      pagination.size = result.size
    }
    catch (error) {
      console.error('加载数据失败:', error)
      rows.value = []
      pagination.total = 0
    }
    finally {
      loading.value = false
    }
  }

  // 搜索
  function search() {
    // 搜索时回到第一页
    pagination.page = 1
    load()
  }

  // 重置搜索
  function resetSearch() {
    // 清空搜索条件
    Object.keys(searchQuery).forEach((key) => {
      delete searchQuery[key]
    })

    // 恢复初始查询条件
    Object.assign(searchQuery, initialQuery)

    // 回到第一页
    pagination.page = 1

    // 重新加载
    load()
  }

  // 页码变化
  function onPageChange(page: number) {
    pagination.page = page
    load()
  }

  // 页大小变化
  function onSizeChange(size: number) {
    pagination.size = size
    pagination.page = 1
    load()
  }

  // 刷新(保持当前查询条件和页码)
  function refresh() {
    load()
  }

  // 初始化
  onMounted(async () => {
    await loadSearchConfig()
    if (autoLoad) {
      await load()
    }
  })

  return {
    /** 加载状态 */
    loading,
    /** 表格数据 */
    rows,
    /** 分页信息 */
    pagination,
    /** 搜索条件 */
    searchQuery,
    /** 搜索配置 */
    searchConfig,
    /** 选项数据源 */
    optionsSources,
    /** 加载数据 */
    load,
    /** 搜索 */
    search,
    /** 重置搜索 */
    resetSearch,
    /** 页码变化 */
    onPageChange,
    /** 页大小变化 */
    onSizeChange,
    /** 刷新 */
    refresh,
  }
}
