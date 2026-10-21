/** 已知数据源，供工作台检查器提示可选值与字段 */
export const STUDIO_DATA_SOURCES = [
  { value: 'site:navigation', label: '站点导航（CMS 导航 + 站点页面 + 插件页面）', fields: ['label', 'url', 'icon', '$index', 'children'] },
  { value: 'block:server-list', label: '服务器列表（Minecraft 服务器插件）', fields: ['name', 'address', 'motd', 'statusText', 'playersText', 'latencyText', 'icon'] },
  { value: 'block:activity-square', label: '活动广场（活动证明插件）', fields: ['title', 'summary', 'statusText', 'meta', 'cover', 'url'] },
  { value: 'block:timeline', label: '大事记（大事记插件）', fields: ['title', 'time', 'summary', 'url'] },
  { value: 'cms:latest', label: '最新文章（CMS）', fields: ['title', 'summary', 'excerpt', 'publishedAt', 'url'] },
  { value: 'wiki:spaces', label: '知识库空间', fields: ['name', 'description', 'slug', 'url'] },
  { value: 'market:plugins', label: '插件市场', fields: ['name', 'description', 'category', 'latestVersion', 'url'] },
  { value: 'market:categories', label: '插件市场分类', fields: ['name', 'count', 'url'] },
] as const
