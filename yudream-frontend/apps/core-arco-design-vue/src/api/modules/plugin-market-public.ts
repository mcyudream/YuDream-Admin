import axios from 'axios'

const baseURL = (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY)
  ? '/proxy/'
  : import.meta.env.VITE_APP_API_BASEURL

/** v2 协议裸 JSON 客户端：不走 systemClient 的 Result 包裹与接口加密。 */
const client = axios.create({
  baseURL,
  timeout: 1000 * 30,
})

client.interceptors.request.use((request) => {
  if (request.headers) {
    request.headers.Accept = 'application/json'
    request.headers['Accept-Language'] = 'zh-CN'
    const token = localStorage.getItem('token')
    if (token) {
      request.headers.Authorization = token
    }
  }
  return request
})

export const PLUGIN_MARKET_CATEGORIES = [
  'AI 与对话',
  '支付与钱包',
  'Minecraft',
  '消息与社区',
  '数据与看板',
  '主题与皮肤',
  '效率工具',
  '其他',
] as const

export type PluginMarketSort = 'newest' | 'downloads' | 'updated' | 'name'

export interface PluginMarketManifest {
  protocol: string
  name: string
  pluginCount: number
}

export interface PluginMarketCategory {
  code: string
  name: string
  count: number
}

export interface PluginMarketTag {
  tag: string
  count: number
}

export interface PluginMarketSummary {
  code: string
  displayName?: string
  description?: string
  icon?: string
  category?: string
  tags: string[]
  authorId?: string
  authorName?: string
  latestVersion: string
  downloads: number
  publishedAt?: string
  updatedAt?: string
  license?: string
}

export interface PluginMarketVersion {
  version: string
  main?: string
  releaseNotes?: string
  license?: string
  sha256: string
  sizeBytes?: number
  downloads: number
  publishedAt?: string
  downloadPath: string
  dependencies?: Array<{ code: string, range?: string, required?: boolean }>
  compatibility?: Record<string, string>
}

export interface PluginMarketDetail extends PluginMarketSummary {
  versions: PluginMarketVersion[]
}

export interface PluginMarketPage {
  total: number
  page: number
  size: number
  items: PluginMarketSummary[]
}

export interface PluginMarketQuery {
  search?: string
  categories?: string
  tags?: string
  authorId?: string
  publishedAfter?: string
  publishedBefore?: string
  sort?: PluginMarketSort
  page?: number
  size?: number
}

function v2(path: string) {
  return `api/public/plugin-market/api/v2${path}`
}

export function pluginMarketDownloadUrl(code: string, version: string) {
  const root = (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY)
    ? '/proxy/'
    : (import.meta.env.VITE_APP_API_BASEURL || '/')
  const prefix = String(root).endsWith('/') ? root : `${root}/`
  return `${prefix}api/public/plugin-market/api/v2/plugins/${encodeURIComponent(code)}/versions/${encodeURIComponent(version)}/download`
}

export default {
  manifest: () => client.get<PluginMarketManifest>(v2('/manifest')).then(res => res.data),
  categories: () => client.get<PluginMarketCategory[]>(v2('/categories')).then(res => res.data),
  tags: (limit = 30) => client.get<PluginMarketTag[]>(v2('/tags'), { params: { limit } }).then(res => res.data),
  plugins: (query: PluginMarketQuery) => client.get<PluginMarketPage>(v2('/plugins'), { params: query }).then(res => res.data),
  plugin: (code: string) => client.get<PluginMarketDetail>(v2(`/plugins/${encodeURIComponent(code)}`)).then(res => res.data),
}
