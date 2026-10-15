<script setup lang="ts">
import type { TableColumn, YdChatMessage, YdPromptItem, YdThoughtChainItem } from '@yudream/components'
import {
  useYdToast,
  YdAction,
  YdAlert,
  YdAvatar,
  YdBadge,
  YdBubble,
  YdButton,
  YdChatMessageList,
  YdChatSender,
  YdCheckbox,
  YdCopyButton,
  YdCountTo,
  YdDescriptions,
  YdDigitalCard,
  YdDropdown,
  YdEmpty,
  YdIcon,
  YdInput,
  YdLoading,
  YdMarquee,
  YdPopconfirm,
  YdProgress,
  YdPrompts,
  YdQrcode,
  YdRate,
  YdSegmented,
  YdSelect,
  YdSparkline,
  YdStat,
  YdStats,
  YdSteps,
  YdSwitch,
  YdTable,
  YdTabs,
  YdTag,
  YdThoughtChain,
  YdTimeline,
  YdTooltip,
  YdTrend,
  YdWelcome,
} from '@yudream/components'
import { computed, ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

const props = defineProps<{ showcaseGroup?: string }>()

/**
 * 场景画廊：AffectUI 风格「应用界面 + 内容区块」的本地交互演示。
 * 全部数据为组件内本地状态，不发起网络请求；交互控件一律来自 @yudream/components 公开 Yd* 入口。
 * 轻提示依赖展示壳根部的全局 YdToast 组件（component-showcase 的 App.vue 已挂载）。
 */

const toast = useYdToast()

// ============================================================
// 画廊级：搜索 + 分组筛选
// ============================================================

interface SceneMeta {
  key: string
  title: string
  en: string
  description: string
  group: 'screens' | 'blocks'
  keywords: string[]
}

const scenes: SceneMeta[] = [
  // —— 应用界面（12）——
  { key: 'workspace', title: '应用工作台', en: 'App Workspace', description: '侧栏导航、全局搜索与工作区概览的组合演示。', group: 'screens', keywords: ['壳', '工作区', '导航', 'workspace', 'shell'] },
  { key: 'assistant', title: '助手首页与对话', en: 'Assistant Chat', description: '欢迎语、推荐提问、会话列表与消息流的本地模拟。', group: 'screens', keywords: ['助手', '聊天', '对话', 'ai', 'chat', '会话'] },
  { key: 'files', title: '文件空间', en: 'Files', description: '关键字与类型筛选、列表/网格双视图、收藏与删除。', group: 'screens', keywords: ['文件', '上传', '网盘', 'files'] },
  { key: 'integrations', title: '集成中心', en: 'Integrations', description: '外部资源连接开关、状态筛选与集成卡片。', group: 'screens', keywords: ['集成', '连接', '开关', 'integration'] },
  { key: 'analytics', title: '数据分析看板', en: 'Analytics', description: '时间范围切换、指标趋势、目标进度与热门页面。', group: 'screens', keywords: ['分析', '指标', '图表', 'analytics', 'dashboard'] },
  { key: 'crm', title: 'CRM 客户表格', en: 'CRM Table', description: '搜索、阶段筛选、排序与行级操作菜单。', group: 'screens', keywords: ['客户', '表格', 'crm', '销售'] },
  { key: 'agent-run', title: 'Agent 运行与审批', en: 'Agent Runs', description: '工具执行六态与人工审批流转的本地演示。', group: 'screens', keywords: ['agent', '审批', '工具', '运行'] },
  { key: 'settings-billing', title: '设置与账单', en: 'Settings & Billing', description: '通知开关、方案切换、席位用量与账单记录。', group: 'screens', keywords: ['设置', '账单', '计费', 'billing', 'settings', '订阅'] },
  { key: 'onboarding', title: '新手引导与评估', en: 'Onboarding & Evals', description: '引导步骤、完成清单与模型评估任务。', group: 'screens', keywords: ['引导', '评估', 'onboarding', 'evals', '清单'] },
  { key: 'folder', title: '文件夹详情', en: 'Folder', description: '面包屑导航、文件与文件夹条目、关键字搜索、列表/网格视图与空态。', group: 'screens', keywords: ['文件夹', '目录', '面包屑', '文件', 'folder', 'breadcrumb'] },
  { key: 'agent-chat', title: 'Agent 对话台', en: 'Agent Chat', description: '执行模式、步骤链推进与工具事件的本地模拟。', group: 'screens', keywords: ['agent', '对话', '聊天', '步骤', '工具', 'chat'] },
  { key: 'evals', title: '模型评估台', en: 'Evals', description: '数据集与模型选择、用例执行进度与历史结果。', group: 'screens', keywords: ['评估', '评测', '数据集', '用例', 'evals'] },
  // —— 内容区块（11）——
  { key: 'agent-gallery', title: 'Agent 画廊', en: 'Agent Gallery', description: 'Agent 卡片目录：分类筛选、关键字搜索、启用开关与运行反馈。', group: 'blocks', keywords: ['agent', '画廊', '智能体', 'gallery'] },
  { key: 'pricing', title: '定价方案', en: 'Pricing Plans', description: '月付/年付切换与三档方案对比。', group: 'blocks', keywords: ['定价', '价格', 'pricing', '方案'] },
  { key: 'invoice-credit-note', title: '发票与贷项通知单', en: 'Invoice & Credit Note', description: '视图切换的发票明细与贷项冲销：单号复制、行项目合计与冲抵确认。', group: 'blocks', keywords: ['发票', '贷项', '冲销', '账单', 'invoice', 'credit', 'note'] },
  { key: 'metrics', title: '指标仪表盘', en: 'Metrics Dashboard', description: '可刷新的 KPI 网格与迷你趋势。', group: 'blocks', keywords: ['指标', 'kpi', '仪表盘', 'metrics'] },
  { key: 'kpi-board', title: '团队 KPI 看板', en: 'KPI Board', description: '周期切换的核心指标卡与小组目标完成进度。', group: 'blocks', keywords: ['kpi', '团队', '目标', 'board', '指标'] },
  { key: 'app-directory', title: '应用目录', en: 'App Directory', description: '搜索、分类筛选与安装状态管理。', group: 'blocks', keywords: ['目录', '应用', 'directory', '安装'] },
  { key: 'client-context', title: '客户上下文', en: 'Client Context', description: '客户摘要、标签、动态时间线与快捷动作。', group: 'blocks', keywords: ['客户', '上下文', 'context', '跟进'] },
  { key: 'task-board', title: '任务看板', en: 'Task Board', description: '三列任务状态流转、优先级筛选与负责人。', group: 'blocks', keywords: ['看板', '任务', 'board', 'kanban'] },
  { key: 'ai-workspace', title: 'AI 工作台', en: 'AI Workspace', description: '项目侧栏、按项目隔离的会话与清空操作的复合演示。', group: 'blocks', keywords: ['ai', '工作台', '工作区', '项目', 'workspace'] },
  { key: 'logo-marquee', title: '客户 Logo 墙', en: 'Logo Marquee', description: '双向滚动 Logo 走马灯，可调速、反向与悬停暂停。', group: 'blocks', keywords: ['logo', '滚动', '走马灯', 'marquee', '客户'] },
  { key: 'testimonial-wall', title: '用户评价墙', en: 'Testimonial Wall', description: '按评分筛选的用户引言卡片与换一批。', group: 'blocks', keywords: ['评价', '口碑', '引言', 'testimonial', '评分'] },
]

const galleryKeyword = ref('')
const galleryGroup = ref<string>(props.showcaseGroup === 'Blocks' ? 'blocks' : 'screens')
const galleryTabs = [
  { label: '全部', value: 'all' },
  { label: '应用界面', value: 'screens' },
  { label: '内容区块', value: 'blocks' },
]

const visibleScenes = computed(() => {
  const kw = galleryKeyword.value.trim().toLowerCase()
  return scenes.filter((scene) => {
    if (galleryGroup.value !== 'all' && scene.group !== galleryGroup.value) {
      return false
    }
    if (!kw) {
      return true
    }
    return [scene.title, scene.en, scene.description, ...scene.keywords]
      .some(text => text.toLowerCase().includes(kw))
  })
})

function resetGalleryFilters() {
  galleryKeyword.value = ''
  galleryGroup.value = 'all'
}

// ============================================================
// 场景：应用工作台
// ============================================================

interface WorkspaceNav {
  key: string
  label: string
  icon: string
  badge?: number
}

const workspaceNavs: WorkspaceNav[] = [
  { key: 'overview', label: '概览', icon: 'morph:house' },
  { key: 'projects', label: '项目', icon: 'morph:folder' },
  { key: 'members', label: '成员', icon: 'morph:users' },
  { key: 'messages', label: '消息', icon: 'morph:bell', badge: 3 },
  { key: 'settings', label: '设置', icon: 'morph:settings' },
]
const activeWorkspaceNav = ref('overview')

const workspaceSearch = ref('')

interface WorkspaceProject {
  id: string
  name: string
  status: '进行中' | '已发布' | '已暂停'
  updatedAt: string
}

const workspaceProjects: WorkspaceProject[] = [
  { id: 'p-9001', name: '官网改版', status: '进行中', updatedAt: '今天 10:24' },
  { id: 'p-9002', name: '移动端 App 2.0', status: '已发布', updatedAt: '昨天 18:02' },
  { id: 'p-9003', name: '数据迁移工具', status: '已暂停', updatedAt: '10-09 14:40' },
]
const filteredProjects = computed(() => {
  const kw = workspaceSearch.value.trim().toLowerCase()
  if (!kw) {
    return workspaceProjects
  }
  return workspaceProjects.filter(project => project.name.toLowerCase().includes(kw))
})

function projectStatusVariant(status: WorkspaceProject['status']) {
  if (status === '进行中') {
    return 'outline' as const
  }
  if (status === '已发布') {
    return 'secondary' as const
  }
  return 'destructive' as const
}

const workspaceMembers = [
  { id: 'u-9007199254740993', name: '林舟', role: '管理员', fallback: '林' },
  { id: 'u-9007199254740994', name: '陈念', role: '编辑', fallback: '陈' },
  { id: 'u-9007199254740995', name: '周衡', role: '访客', fallback: '周' },
]

const workspaceNotices = [
  { id: 'n-1', title: '陈念 评论了「官网改版」', time: '10 分钟前' },
  { id: 'n-2', title: '「移动端 App 2.0」已通过验收', time: '2 小时前' },
  { id: 'n-3', title: '本周存储用量达到 68%', time: '昨天' },
]

const workspacePrefs = ref({ weeklyDigest: true, opReminder: false })

// ============================================================
// 场景：助手首页与对话（本地模拟，不连接模型服务）
// ============================================================

interface DemoSession {
  id: string
  title: string
  count: number
}

const assistantSessions: DemoSession[] = [
  { id: 's-1', title: '权限模型答疑', count: 12 },
  { id: 's-2', title: '插件开发入门', count: 6 },
  { id: 's-3', title: '本周平台动态', count: 3 },
]
const activeAssistantSession = ref('s-1')

const assistantMessages = ref<YdChatMessage[]>([])
const assistantSending = ref(false)

const assistantPrompts: YdPromptItem[] = [
  { key: 'p-1', label: '总结本周变更', description: '按模块整理提交与 issue', icon: 'morph:list-checks', value: '帮我总结本周的平台变更。' },
  { key: 'p-2', label: '解释双闸门', description: '平台能力为何要两级校验', icon: 'morph:shield-question', value: '解释一下平台能力的双闸门机制。' },
  { key: 'p-3', label: '起草发布说明', description: '面向插件开发者的更新公告', icon: 'morph:pen', value: '帮我起草一份插件开发者更新公告。' },
]

function sendAssistant(text: string) {
  const content = text.trim()
  if (!content || assistantSending.value) {
    return
  }
  assistantSending.value = true
  assistantMessages.value.push({ id: `m-u-${Date.now()}`, role: 'user', content })
  const replyId = `m-a-${Date.now()}`
  assistantMessages.value.push({ id: replyId, role: 'assistant', content: '', pending: true })
  window.setTimeout(() => {
    const reply = assistantMessages.value.find(item => item.id === replyId)
    if (reply) {
      reply.content = `这是本地演示回复：已收到「${content}」。当前页面只做组件组合展示，未连接真实模型服务。`
      reply.pending = false
    }
    assistantSending.value = false
  }, 900)
}

// ============================================================
// 场景：文件空间
// ============================================================

type FileKind = '文档' | '图片' | '表格' | '压缩包'

interface DemoFile {
  id: string
  name: string
  kind: FileKind
  size: string
  updatedAt: string
  starred: boolean
}

const files = ref<DemoFile[]>([
  { id: 'f-1', name: '架构评审纪要.docx', kind: '文档', size: '2.1 MB', updatedAt: '今天 09:12', starred: true },
  { id: 'f-2', name: '首页视觉稿.png', kind: '图片', size: '4.8 MB', updatedAt: '今天 08:40', starred: false },
  { id: 'f-3', name: '三季度经营数据.xlsx', kind: '表格', size: '864 KB', updatedAt: '昨天 16:30', starred: false },
  { id: 'f-4', name: '品牌素材包.zip', kind: '压缩包', size: '128 MB', updatedAt: '10-12 11:05', starred: false },
  { id: 'f-5', name: '插件接入指南.pdf', kind: '文档', size: '1.3 MB', updatedAt: '10-11 15:47', starred: true },
  { id: 'f-6', name: '用户访谈录音整理.docx', kind: '文档', size: '96 KB', updatedAt: '10-10 10:02', starred: false },
])

const fileKeyword = ref('')
const fileKind = ref('')
// 展示组件的模型为宽泛 string，视图状态保持 string 以便直接双向绑定
const fileView = ref('list')
const fileViewTabs = [
  { label: '列表', value: 'list', icon: 'morph:list' },
  { label: '网格', value: 'grid', icon: 'morph:layout-grid' },
]
const fileKindOptions = [
  { label: '全部类型', value: '' },
  { label: '文档', value: '文档' },
  { label: '图片', value: '图片' },
  { label: '表格', value: '表格' },
  { label: '压缩包', value: '压缩包' },
]

const filteredFiles = computed(() => {
  const kw = fileKeyword.value.trim().toLowerCase()
  return files.value.filter((file) => {
    if (fileKind.value && file.kind !== fileKind.value) {
      return false
    }
    return !kw || file.name.toLowerCase().includes(kw)
  })
})

function fileKindIcon(kind: FileKind) {
  const map: Record<FileKind, string> = {
    文档: 'morph:file-text',
    图片: 'morph:image',
    表格: 'morph:table',
    压缩包: 'morph:archive',
  }
  return map[kind]
}

function toggleFileStar(file: DemoFile) {
  file.starred = !file.starred
  toast(file.starred ? `已收藏「${file.name}」` : `已取消收藏「${file.name}」`)
}

function removeFile(file: DemoFile) {
  files.value = files.value.filter(item => item.id !== file.id)
  toast.success(`已删除「${file.name}」（本地演示）`)
}

function resetFileFilters() {
  fileKeyword.value = ''
  fileKind.value = ''
}

const fileColumns: TableColumn<DemoFile>[] = [
  { accessorKey: 'name', header: '名称' },
  { accessorKey: 'kind', header: '类型' },
  { accessorKey: 'size', header: '大小' },
  { accessorKey: 'updatedAt', header: '更新时间' },
  { accessorKey: 'actions', header: '操作', align: 'right', enableSorting: false },
]

// ============================================================
// 场景：集成中心
// ============================================================

interface Integration {
  id: string
  name: string
  description: string
  icon: string
  connected: boolean
}

const integrations = ref<Integration[]>([
  { id: 'i-mongo', name: 'MongoDB', description: '业务主数据库连接', icon: 'morph:database', connected: true },
  { id: 'i-redis', name: 'Redis', description: '缓存与会话存储', icon: 'morph:zap', connected: true },
  { id: 'i-s3', name: '对象存储 S3', description: '附件与静态资源', icon: 'morph:cloud', connected: false },
  { id: 'i-smtp', name: '邮件 SMTP', description: '通知与验证码邮件', icon: 'morph:mail', connected: false },
  { id: 'i-git', name: '代码仓库', description: '拉取构建与变更记录', icon: 'morph:git-branch', connected: false },
  { id: 'i-im', name: '即时消息', description: '审批与告警群通知', icon: 'morph:message-circle', connected: false },
])

const integrationFilter = ref('all')
const integrationTabs = [
  { label: '全部', value: 'all' },
  { label: '已连接', value: 'connected' },
  { label: '可接入', value: 'available' },
]

const filteredIntegrations = computed(() => {
  if (integrationFilter.value === 'connected') {
    return integrations.value.filter(item => item.connected)
  }
  if (integrationFilter.value === 'available') {
    return integrations.value.filter(item => !item.connected)
  }
  return integrations.value
})

function toggleIntegration(item: Integration) {
  item.connected = !item.connected
  toast.success(item.connected ? `已连接 ${item.name}（本地演示）` : `已断开 ${item.name}（本地演示）`)
}

// ============================================================
// 场景：数据分析看板
// ============================================================

type AnalyticsRange = '7d' | '30d' | '90d'

interface AnalyticsDataset {
  visits: number
  conversions: number
  revenue: string
  visitsTrend: string
  conversionsTrend: string
  spark: number[]
  goal: number
  pages: { id: string, page: string, views: string }[]
}

const analyticsRange = ref('7d')
const analyticsTabs: { label: string, value: AnalyticsRange }[] = [
  { label: '近 7 天', value: '7d' },
  { label: '近 30 天', value: '30d' },
  { label: '近 90 天', value: '90d' },
]

const analyticsData: Record<AnalyticsRange, AnalyticsDataset> = {
  '7d': {
    visits: 12_846,
    conversions: 962,
    revenue: '¥86,420',
    visitsTrend: '8.4%',
    conversionsTrend: '3.1%',
    spark: [12, 18, 14, 24, 20, 31, 35],
    goal: 72,
    pages: [
      { id: 'pg-1', page: '/home', views: '4,208' },
      { id: 'pg-2', page: '/pricing', views: '2,951' },
      { id: 'pg-3', page: '/docs/quick-start', views: '1,877' },
    ],
  },
  '30d': {
    visits: 51_337,
    conversions: 4_106,
    revenue: '¥352,900',
    visitsTrend: '12.5%',
    conversionsTrend: '6.8%',
    spark: [22, 26, 19, 31, 28, 36, 40, 38, 44, 47],
    goal: 84,
    pages: [
      { id: 'pg-1', page: '/home', views: '16,402' },
      { id: 'pg-2', page: '/pricing', views: '11,236' },
      { id: 'pg-3', page: '/blog/launch', views: '7,981' },
    ],
  },
  '90d': {
    visits: 148_902,
    conversions: 12_384,
    revenue: '¥1,028,560',
    visitsTrend: '21.2%',
    conversionsTrend: '9.4%',
    spark: [18, 24, 30, 27, 35, 41, 38, 47, 52, 58, 61, 66],
    goal: 93,
    pages: [
      { id: 'pg-1', page: '/home', views: '47,113' },
      { id: 'pg-2', page: '/pricing', views: '33,860' },
      { id: 'pg-3', page: '/docs/plugin', views: '22,407' },
    ],
  },
}

const currentAnalytics = computed(() => analyticsData[analyticsRange.value as AnalyticsRange])

const analyticsPageColumns: TableColumn<AnalyticsDataset['pages'][number]>[] = [
  { accessorKey: 'page', header: '页面' },
  { accessorKey: 'views', header: '浏览量', align: 'right' },
]

// ============================================================
// 场景：CRM 客户表格
// ============================================================

type CrmStage = '线索' | '商机' | '成交' | '流失'

interface CrmCustomer {
  id: string
  name: string
  stage: CrmStage
  amount: string
  owner: string
  ownerFallback: string
  lastContact: string
}

const customers = ref<CrmCustomer[]>([
  { id: 'c-1', name: '云帆传媒', stage: '成交', amount: '¥128,000', owner: '陈念', ownerFallback: '陈', lastContact: '今天 11:20' },
  { id: 'c-2', name: '启程旅行', stage: '商机', amount: '¥86,500', owner: '林舟', ownerFallback: '林', lastContact: '昨天 17:45' },
  { id: 'c-3', name: '白鹭书店', stage: '线索', amount: '¥12,000', owner: '陈念', ownerFallback: '陈', lastContact: '10-13 09:30' },
  { id: 'c-4', name: '北望科技', stage: '流失', amount: '¥0', owner: '周衡', ownerFallback: '周', lastContact: '09-28 15:10' },
  { id: 'c-5', name: '南屿咖啡', stage: '商机', amount: '¥43,200', owner: '林舟', ownerFallback: '林', lastContact: '10-14 10:05' },
])

const crmKeyword = ref('')
const crmStage = ref('')
const crmStageOptions = [
  { label: '全部阶段', value: '' },
  { label: '线索', value: '线索' },
  { label: '商机', value: '商机' },
  { label: '成交', value: '成交' },
  { label: '流失', value: '流失' },
]

const filteredCustomers = computed(() => {
  const kw = crmKeyword.value.trim().toLowerCase()
  return customers.value.filter((customer) => {
    if (crmStage.value && customer.stage !== crmStage.value) {
      return false
    }
    return !kw || customer.name.toLowerCase().includes(kw)
  })
})

function crmStageVariant(stage: CrmStage) {
  if (stage === '成交') {
    return 'default' as const
  }
  if (stage === '商机') {
    return 'secondary' as const
  }
  if (stage === '线索') {
    return 'outline' as const
  }
  return 'destructive' as const
}

function crmRowActions(customer: CrmCustomer) {
  return [
    [
      { label: '查看详情', icon: 'morph:eye', handle: () => toast(`查看「${customer.name}」详情（本地演示）`) },
      { label: '标记为成交', icon: 'morph:circle-check', handle: () => {
        customer.stage = '成交'
        toast.success(`「${customer.name}」已标记为成交`)
      } },
    ],
    [
      { label: '删除客户', icon: 'morph:trash', variant: 'destructive' as const, handle: () => {
        customers.value = customers.value.filter(item => item.id !== customer.id)
        toast.error(`已删除「${customer.name}」（本地演示）`)
      } },
    ],
  ]
}

const crmColumns: TableColumn<CrmCustomer>[] = [
  { accessorKey: 'name', header: '客户' },
  { accessorKey: 'stage', header: '阶段' },
  { accessorKey: 'amount', header: '额度', align: 'right' },
  { accessorKey: 'owner', header: '负责人' },
  { accessorKey: 'lastContact', header: '最近联系' },
  { accessorKey: 'actions', header: '操作', align: 'right', enableSorting: false },
]

// ============================================================
// 场景：Agent 运行与审批（本地演示，不触发真实执行）
// ============================================================

type ToolStatus = 'queued' | 'running' | 'awaiting-approval' | 'success' | 'error' | 'cancelled'

interface ToolCall {
  id: string
  name: string
  detail: string
  status: ToolStatus
}

function toolStatusMeta(status: ToolStatus) {
  const map: Record<ToolStatus, { label: string, variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
    'queued': { label: '待排队', variant: 'outline' },
    'running': { label: '执行中', variant: 'default' },
    'awaiting-approval': { label: '待审批', variant: 'default' },
    'success': { label: '已成功', variant: 'secondary' },
    'error': { label: '失败', variant: 'destructive' },
    'cancelled': { label: '已取消', variant: 'outline' },
  }
  return map[status]
}

const toolCalls = ref<ToolCall[]>([
  { id: 't-1', name: '读取工作区文件', detail: 'docs/release-notes.md', status: 'success' },
  { id: 't-2', name: '生成变更摘要', detail: '12 个提交，3 个模块', status: 'success' },
  { id: 't-3', name: '写入发布说明', detail: '需要写入 docs/release-notes.md', status: 'awaiting-approval' },
  { id: 't-4', name: '同步到公告页', detail: '等待上一步完成后执行', status: 'queued' },
])

const runEvents = ref([
  { id: 'e-1', datetime: '2026-10-15 10:02', title: '运行启动', description: '触发指令：整理本周变更并更新发布说明。' },
  { id: 'e-2', datetime: '2026-10-15 10:02', title: '读取与摘要完成', description: '两个工具按顺序执行成功。' },
  { id: 'e-3', datetime: '2026-10-15 10:03', title: '等待人工审批', description: '写入文件属于敏感操作，需要确认。' },
])

function approveTool(tool: ToolCall) {
  tool.status = 'running'
  window.setTimeout(() => {
    tool.status = 'success'
    runEvents.value.push({ id: `e-${Date.now()}`, datetime: '2026-10-15 10:04', title: '审批通过', description: `「${tool.name}」执行完成。` })
    toast.success(`已批准并执行「${tool.name}」（本地演示）`)
  }, 900)
}

function rejectTool(tool: ToolCall) {
  tool.status = 'cancelled'
  runEvents.value.push({ id: `e-${Date.now()}`, datetime: '2026-10-15 10:04', title: '审批拒绝', description: `「${tool.name}」已取消，后续步骤不再执行。` })
  toast(`已拒绝「${tool.name}」（本地演示）`)
}

// ============================================================
// 场景：设置与账单
// ============================================================

const billingSettings = ref({
  mailNotice: true,
  twoFactor: false,
  autoRenew: true,
  weeklyDigest: false,
})

type PlanKey = 'free' | 'pro' | 'enterprise'

const currentPlan = ref('pro')
const planOptions: { label: string, value: PlanKey }[] = [
  { label: '免费版', value: 'free' },
  { label: '专业版', value: 'pro' },
  { label: '企业版', value: 'enterprise' },
]

const planMeta: Record<PlanKey, { name: string, price: string, seats: number, seatTotal: number, renewDate: string }> = {
  free: { name: '免费版', price: '¥0 / 月', seats: 3, seatTotal: 5, renewDate: '长期有效' },
  pro: { name: '专业版', price: '¥199 / 月', seats: 8, seatTotal: 10, renewDate: '2026-11-01 自动续费' },
  enterprise: { name: '企业版', price: '¥1,999 / 月', seats: 42, seatTotal: 60, renewDate: '2026-12-01 自动续费' },
}

const currentPlanMeta = computed(() => planMeta[currentPlan.value as PlanKey])
const seatPercent = computed(() => Math.round(currentPlanMeta.value.seats / currentPlanMeta.value.seatTotal * 100))

const billingInvoices = [
  { id: 'inv-1', no: 'INV-20260930-001', date: '2026-09-30', amount: '¥199.00', paid: true },
  { id: 'inv-2', no: 'INV-20260831-004', date: '2026-08-31', amount: '¥199.00', paid: true },
  { id: 'inv-3', no: 'INV-20261015-002', date: '2026-10-15', amount: '¥199.00', paid: false },
]

function switchPlan() {
  toast.success(`已切换为${planMeta[currentPlan.value as PlanKey].name}（本地演示）`)
}

// ============================================================
// 场景：新手引导与评估
// ============================================================

const onboardingSteps = [
  { id: 'create', title: '创建工作区', description: '初始化团队空间' },
  { id: 'invite', title: '邀请成员', description: '至少邀请 1 位协作者' },
  { id: 'connect', title: '接入数据', description: '连接数据库或上传文件' },
  { id: 'eval', title: '完成评估', description: '运行一次模型评估' },
]
const onboardingStep = ref(1)

const onboardingChecks = ref([
  { id: 'oc-1', label: '完善团队名称与头像', done: true },
  { id: 'oc-2', label: '邀请 3 位成员加入', done: true },
  { id: 'oc-3', label: '接入第一个数据源', done: false },
  { id: 'oc-4', label: '运行一次示例评估', done: false },
])
const onboardingPercent = computed(() =>
  Math.round(onboardingChecks.value.filter(item => item.done).length / onboardingChecks.value.length * 100))
const onboardingDone = computed(() => onboardingPercent.value === 100)

interface EvalTask {
  id: string
  name: string
  model: string
  passRate: number
  status: 'idle' | 'running' | 'done'
}

const evalTasks = ref<EvalTask[]>([
  { id: 'ev-1', name: '权限问答回归', model: 'GLM-4.6', passRate: 92, status: 'done' },
  { id: 'ev-2', name: '插件接入引导', model: 'GLM-4.6', passRate: 78, status: 'done' },
  { id: 'ev-3', name: '长上下文摘要', model: 'GLM-4.5-Air', passRate: 0, status: 'idle' },
])

function runEval(task: EvalTask) {
  if (task.status === 'running') {
    return
  }
  task.status = 'running'
  window.setTimeout(() => {
    task.passRate = 80 + Math.floor(Math.random() * 19)
    task.status = 'done'
    toast.success(`「${task.name}」评估完成：通过率 ${task.passRate}%（本地演示）`)
  }, 1200)
}

function evalStatusMeta(status: EvalTask['status']) {
  if (status === 'running') {
    return { label: '运行中', variant: 'default' as const }
  }
  if (status === 'done') {
    return { label: '已完成', variant: 'secondary' as const }
  }
  return { label: '待运行', variant: 'outline' as const }
}

// ============================================================
// 场景：文件夹详情（本地静态目录，不发起网络请求）
// ============================================================

interface FolderEntry {
  id: string
  name: string
  type: 'folder' | FileKind
  size: string
  updatedAt: string
  starred: boolean
  children?: FolderEntry[]
}

const folderRoot = ref<FolderEntry>({
  id: 'fd-root',
  name: '项目资料',
  type: 'folder',
  size: '',
  updatedAt: '10-15',
  starred: false,
  children: [
    {
      id: 'fd-design',
      name: '设计稿',
      type: 'folder',
      size: '',
      updatedAt: '10-14',
      starred: true,
      children: [
        { id: 'fd-design-home', name: '首页视觉稿.png', type: '图片', size: '4.8 MB', updatedAt: '10-14', starred: true },
        { id: 'fd-design-icon', name: '图标规范.docx', type: '文档', size: '1.2 MB', updatedAt: '10-12', starred: false },
      ],
    },
    {
      id: 'fd-archive',
      name: '归档',
      type: 'folder',
      size: '',
      updatedAt: '09-28',
      starred: false,
      children: [],
    },
    { id: 'fd-review', name: '架构评审纪要.docx', type: '文档', size: '2.1 MB', updatedAt: '10-13', starred: false },
    { id: 'fd-metrics', name: '三季度经营数据.xlsx', type: '表格', size: '864 KB', updatedAt: '10-11', starred: false },
    { id: 'fd-assets', name: '品牌素材包.zip', type: '压缩包', size: '128 MB', updatedAt: '10-09', starred: true },
  ],
})

const folderTrail = ref<FolderEntry[]>([folderRoot.value])
const currentFolder = computed(() => folderTrail.value[folderTrail.value.length - 1])

const folderKeyword = ref('')
const folderView = ref('list')
const folderViewTabs = [
  { label: '列表', value: 'list', icon: 'morph:list' },
  { label: '网格', value: 'grid', icon: 'morph:layout-grid' },
]

const folderEntries = computed(() => {
  const children = currentFolder.value.children ?? []
  const kw = folderKeyword.value.trim().toLowerCase()
  if (!kw) {
    return children
  }
  return children.filter(entry => entry.name.toLowerCase().includes(kw))
})

function openFolderEntry(entry: FolderEntry) {
  if (entry.type !== 'folder') {
    toast(`打开「${entry.name}」（本地演示）`)
    return
  }
  folderTrail.value = [...folderTrail.value, entry]
  folderKeyword.value = ''
}

function jumpFolderTrail(index: number) {
  folderTrail.value = folderTrail.value.slice(0, index + 1)
  folderKeyword.value = ''
}

function toggleFolderStar(entry: FolderEntry) {
  entry.starred = !entry.starred
  toast(entry.starred ? `已收藏「${entry.name}」` : `已取消收藏「${entry.name}」`)
}

function folderTypeIcon(entry: FolderEntry) {
  return entry.type === 'folder' ? 'morph:folder' : fileKindIcon(entry.type)
}

function folderEntrySize(entry: FolderEntry) {
  return entry.type === 'folder' ? `${(entry.children ?? []).length} 项` : entry.size
}

// ============================================================
// 场景：Agent 对话台（本地模拟，不连接模型服务）
// ============================================================

type AgentModeKey = 'quick' | 'standard' | 'research'

interface AgentModePlan {
  key: AgentModeKey
  label: string
  steps: { title: string, description: string }[]
}

const agentModes: AgentModePlan[] = [
  {
    key: 'quick',
    label: '快速回答',
    steps: [
      { title: '理解任务', description: '解析输入并确定回答范围' },
      { title: '生成回答', description: '直接组织结论与要点' },
    ],
  },
  {
    key: 'standard',
    label: '标准执行',
    steps: [
      { title: '理解任务', description: '解析输入并拆解子任务' },
      { title: '调用工具', description: '读取工作区上下文与数据' },
      { title: '汇总输出', description: '整理结果并生成回答' },
    ],
  },
  {
    key: 'research',
    label: '深度调研',
    steps: [
      { title: '理解任务', description: '解析输入并形成调研提纲' },
      { title: '检索资料', description: '检索知识库与历史会话' },
      { title: '交叉验证', description: '核对来源一致性与时效' },
      { title: '汇总输出', description: '给出结论与引用清单' },
    ],
  },
]

const agentMode = ref<AgentModeKey>('standard')
const agentModeOptions = agentModes.map(mode => ({ label: mode.label, value: mode.key }))
const activeAgentMode = computed(() => agentModes.find(mode => mode.key === agentMode.value) ?? agentModes[1])

const agentMessages = ref<YdChatMessage[]>([])
const agentBusy = ref(false)
const agentStepStatus = ref<Array<'pending' | 'running' | 'success'>>([])

const agentStepItems = computed<YdThoughtChainItem[]>(() => activeAgentMode.value.steps.map((step, index) => ({
  key: `agent-step-${index}`,
  title: step.title,
  description: step.description,
  status: agentStepStatus.value[index] ?? 'pending',
})))

function sendAgentTask(text: string) {
  const content = text.trim()
  if (!content || agentBusy.value) {
    return
  }
  agentBusy.value = true
  agentMessages.value.push({ id: `ag-u-${Date.now()}`, role: 'user', content })
  const steps = activeAgentMode.value.steps
  agentStepStatus.value = steps.map((_, index) => (index === 0 ? 'running' as const : 'pending' as const))
  steps.forEach((_, index) => {
    window.setTimeout(() => {
      agentStepStatus.value[index] = 'success'
      if (index + 1 < steps.length) {
        agentStepStatus.value[index + 1] = 'running'
      }
    }, 700 * (index + 1))
  })
  window.setTimeout(() => {
    agentMessages.value.push({
      id: `ag-a-${Date.now()}`,
      role: 'assistant',
      content: `已按「${activeAgentMode.value.label}」处理「${content}」。本次执行共 ${steps.length} 个步骤并全部完成；回复由页面内模拟生成，未连接真实模型服务。`,
      reasoning: agentMode.value === 'research' ? '调研模式会先列提纲，再逐项验证来源时效，最后汇总结论与引用。' : undefined,
      tools: agentMode.value === 'quick'
        ? undefined
        : [{ toolCallId: `ag-tool-${Date.now()}`, toolName: 'workspace.search', status: 'complete', message: `为「${content}」检索到 3 条相关上下文` }],
    })
    agentBusy.value = false
  }, 700 * steps.length + 400)
}

// ============================================================
// 场景：模型评估台（本地模拟，不连接模型服务）
// ============================================================

interface EvalDatasetMeta {
  id: string
  name: string
  caseTotal: number
  description: string
}

const evalDatasets: EvalDatasetMeta[] = [
  { id: 'ds-1', name: '权限问答集', caseTotal: 12, description: '权限码解释与越权拦截问答' },
  { id: 'ds-2', name: '插件接入集', caseTotal: 10, description: 'SPI 契约与生命周期问答' },
  { id: 'ds-3', name: '长文本摘要集', caseTotal: 8, description: '会议纪要与发布说明摘要' },
]

const evalDatasetId = ref('ds-1')
const evalDatasetOptions = evalDatasets.map(dataset => ({ label: `${dataset.name}（${dataset.caseTotal} 例）`, value: dataset.id }))
const evalActiveDataset = computed(() => evalDatasets.find(dataset => dataset.id === evalDatasetId.value) ?? evalDatasets[0])

const evalModel = ref('GLM-4.6')
const evalModelOptions = [
  { label: 'GLM-4.6', value: 'GLM-4.6' },
  { label: 'GLM-4.5-Air', value: 'GLM-4.5-Air' },
  { label: 'GLM-4.5', value: 'GLM-4.5' },
]

interface EvalCaseResult {
  id: string
  name: string
  status: 'pending' | 'pass' | 'fail'
}

function buildEvalCases(dataset: EvalDatasetMeta): EvalCaseResult[] {
  return Array.from({ length: dataset.caseTotal }, (_, index) => ({
    id: `${dataset.id}-${String(index + 1).padStart(2, '0')}`,
    name: `${dataset.name} · 用例 ${String(index + 1).padStart(2, '0')}`,
    status: 'pending' as const,
  }))
}

const evalCases = ref<EvalCaseResult[]>(buildEvalCases(evalDatasets[0]))

watch(evalDatasetId, () => {
  evalCases.value = buildEvalCases(evalActiveDataset.value)
})

interface EvalRunRecord {
  id: string
  dataset: string
  model: string
  passed: number
  caseTotal: number
  passRate: number
  duration: string
}

const evalRuns = ref<EvalRunRecord[]>([
  { id: 'run-1', dataset: '权限问答集', model: 'GLM-4.6', passed: 11, caseTotal: 12, passRate: 92, duration: '41s' },
  { id: 'run-2', dataset: '插件接入集', model: 'GLM-4.5-Air', passed: 8, caseTotal: 10, passRate: 80, duration: '28s' },
])

const evalRunColumns: TableColumn<EvalRunRecord>[] = [
  { accessorKey: 'dataset', header: '数据集' },
  { accessorKey: 'model', header: '模型' },
  { accessorKey: 'passRate', header: '通过率' },
  { accessorKey: 'duration', header: '耗时', align: 'right' },
]

const evalRunning = ref(false)
const evalProgress = ref(0)

function startEvalRun() {
  if (evalRunning.value) {
    return
  }
  evalRunning.value = true
  evalProgress.value = 0
  evalCases.value = buildEvalCases(evalActiveDataset.value)
  const total = evalCases.value.length
  let elapsed = 0
  const timer = window.setInterval(() => {
    evalProgress.value = Math.min(100, evalProgress.value + Math.round(8 + Math.random() * 12))
    elapsed += 280
    const settledCount = Math.round(evalProgress.value / 100 * total)
    evalCases.value.forEach((item, index) => {
      if (item.status === 'pending' && index < settledCount) {
        item.status = Math.random() < 0.88 ? 'pass' : 'fail'
      }
    })
    if (evalProgress.value >= 100) {
      window.clearInterval(timer)
      const passed = evalCases.value.filter(item => item.status === 'pass').length
      evalRuns.value.unshift({
        id: `run-${Date.now()}`,
        dataset: evalActiveDataset.value.name,
        model: evalModel.value,
        passed,
        caseTotal: total,
        passRate: Math.round(passed / total * 100),
        duration: `${Math.max(1, Math.round(elapsed / 1000))}s`,
      })
      evalRunning.value = false
      toast.success(`「${evalActiveDataset.value.name}」评估完成：通过率 ${Math.round(passed / total * 100)}%（本地演示）`)
    }
  }, 280)
}

// ============================================================
// 区块：任务看板
// ============================================================

type TaskState = 'todo' | 'doing' | 'done'
type TaskPriority = '高' | '中' | '低'

interface BoardTask {
  id: string
  title: string
  priority: TaskPriority
  assignee: string
  state: TaskState
}

const boardTasks = ref<BoardTask[]>([
  { id: 'tk-1', title: '整理权限码词表', priority: '高', assignee: '林', state: 'todo' },
  { id: 'tk-2', title: '补齐发票预览空态', priority: '中', assignee: '陈', state: 'todo' },
  { id: 'tk-3', title: '重构设置页开关组', priority: '低', assignee: '周', state: 'todo' },
  { id: 'tk-4', title: '看板拖拽状态同步', priority: '高', assignee: '陈', state: 'doing' },
  { id: 'tk-5', title: '集成中心连接开关', priority: '中', assignee: '林', state: 'doing' },
  { id: 'tk-6', title: '发布说明初稿', priority: '中', assignee: '周', state: 'done' },
  { id: 'tk-7', title: '评估任务随机化', priority: '低', assignee: '林', state: 'done' },
])

const boardColumns: { key: TaskState, label: string }[] = [
  { key: 'todo', label: '待办' },
  { key: 'doing', label: '进行中' },
  { key: 'done', label: '已完成' },
]
const taskPriority = ref('')
const taskPriorityOptions = [
  { label: '全部优先级', value: '' },
  { label: '高', value: '高' },
  { label: '中', value: '中' },
  { label: '低', value: '低' },
]

function tasksOf(state: TaskState) {
  return boardTasks.value.filter(task => task.state === state && (!taskPriority.value || task.priority === taskPriority.value))
}

function taskCount(state: TaskState) {
  return boardTasks.value.filter(task => task.state === state).length
}

function moveTask(task: BoardTask, direction: 1 | -1) {
  const index = boardColumns.findIndex(column => column.key === task.state)
  const next = boardColumns[index + direction]
  if (!next) {
    return
  }
  task.state = next.key
  toast(`「${task.title}」已移动到「${next.label}」`)
}

function priorityVariant(priority: TaskPriority) {
  if (priority === '高') {
    return 'destructive' as const
  }
  if (priority === '中') {
    return 'default' as const
  }
  return 'secondary' as const
}

// ============================================================
// 区块：定价切换
// ============================================================

const billingCycle = ref('monthly')
const billingCycleTabs = [
  { label: '月付', value: 'monthly' },
  { label: '年付 · 省 20%', value: 'yearly' },
]

interface PricingPlan {
  id: string
  name: string
  monthly: number
  yearly: number
  features: string[]
  highlight?: boolean
}

const pricingPlans: PricingPlan[] = [
  {
    id: 'plan-free',
    name: '免费版',
    monthly: 0,
    yearly: 0,
    features: ['5 名成员', '1 个工作区', '基础报表', '社区支持'],
  },
  {
    id: 'plan-pro',
    name: '专业版',
    monthly: 99,
    yearly: 950,
    highlight: true,
    features: ['不限成员', '10 个工作区', '高级分析与导出', 'Agent 运行额度', '工单支持'],
  },
  {
    id: 'plan-enterprise',
    name: '企业版',
    monthly: 299,
    yearly: 2870,
    features: ['专属实例部署', 'SSO 与审计日志', '私有化模型接入', '专属客户成功经理'],
  },
]

function planPriceLabel(plan: PricingPlan) {
  if (plan.monthly === 0) {
    return '免费'
  }
  return billingCycle.value === 'monthly' ? `¥${plan.monthly} / 月` : `¥${plan.yearly} / 年`
}

function planPriceNote(plan: PricingPlan) {
  if (plan.monthly === 0) {
    return '永久免费，无需绑卡'
  }
  return billingCycle.value === 'monthly' ? '按月支付，随时取消' : `折合 ¥${(plan.yearly / 12).toFixed(1)} / 月`
}

function choosePlan(plan: PricingPlan) {
  toast.success(`已选择「${plan.name}」（${billingCycle.value === 'monthly' ? '月付' : '年付'}，本地演示）`)
}

// ============================================================
// 区块：应用目录
// ============================================================

interface DirectoryApp {
  id: string
  name: string
  description: string
  category: string
  installed: boolean
}

const directoryApps = ref<DirectoryApp[]>([
  { id: 'app-1', name: '报表工坊', description: '拖拽式报表与订阅推送', category: '报表', installed: true },
  { id: 'app-2', name: '审批中心', description: '多级审批流与代批规则', category: '协作', installed: true },
  { id: 'app-3', name: '代码扫描', description: '提交质量与漏洞门禁', category: '开发', installed: false },
  { id: 'app-4', name: '知识问答', description: '基于知识库的 AI 问答', category: 'AI 工具', installed: false },
  { id: 'app-5', name: '会议纪要', description: '语音转写与待办提取', category: 'AI 工具', installed: false },
  { id: 'app-6', name: '客户地图', description: '区域客户分布与跟进', category: '报表', installed: false },
])

const directoryKeyword = ref('')
const directoryCategory = ref('')
const directoryCategoryOptions = [
  { label: '全部分类', value: '' },
  { label: '报表', value: '报表' },
  { label: '协作', value: '协作' },
  { label: '开发', value: '开发' },
  { label: 'AI 工具', value: 'AI 工具' },
]

const filteredApps = computed(() => {
  const kw = directoryKeyword.value.trim().toLowerCase()
  return directoryApps.value.filter((app) => {
    if (directoryCategory.value && app.category !== directoryCategory.value) {
      return false
    }
    return !kw || app.name.toLowerCase().includes(kw) || app.description.toLowerCase().includes(kw)
  })
})

function toggleApp(app: DirectoryApp) {
  app.installed = !app.installed
  toast.success(app.installed ? `已安装「${app.name}」（本地演示）` : `已卸载「${app.name}」（本地演示）`)
}

function resetDirectoryFilters() {
  directoryKeyword.value = ''
  directoryCategory.value = ''
}

// ============================================================
// 区块：指标仪表盘
// ============================================================

interface KpiMetric {
  id: string
  label: string
  value: number
  unit: string
  delta: number
  spark: number[]
}

const metricPeriod = ref('week')
const metricPeriodOptions = [
  { label: '今日', value: 'day' },
  { label: '本周', value: 'week' },
  { label: '本月', value: 'month' },
]

const kpiMetrics = ref<KpiMetric[]>([
  { id: 'kpi-1', label: '销售额', value: 128_430, unit: '元', delta: 12.4, spark: [30, 34, 28, 40, 44, 41, 52] },
  { id: 'kpi-2', label: '订单数', value: 1_284, unit: '单', delta: 5.8, spark: [18, 22, 26, 24, 30, 28, 34] },
  { id: 'kpi-3', label: '转化率', value: 3.6, unit: '%', delta: -1.2, spark: [42, 38, 40, 36, 34, 37, 33] },
  { id: 'kpi-4', label: '客单价', value: 186, unit: '元', delta: 2.1, spark: [20, 24, 22, 28, 26, 30, 29] },
])

function refreshMetrics() {
  kpiMetrics.value = kpiMetrics.value.map((metric) => {
    const delta = Math.round((Math.random() * 30 - 10) * 10) / 10
    return {
      ...metric,
      delta,
      spark: metric.spark.map(() => Math.round(20 + Math.random() * 40)),
    }
  })
  toast('指标已刷新（本地随机数据）')
}

function metricTrendType(delta: number) {
  return delta >= 0 ? 'up' as const : 'down' as const
}

// ============================================================
// 区块：客户上下文
// ============================================================

const clientContext = {
  name: '林舟',
  company: '云帆传媒',
  fallback: '林',
  vip: true,
  descriptions: [
    { label: '邮箱', value: 'linzhou@yunfan.example' },
    { label: '电话', value: '138****6688' },
    { label: '来源', value: '官网表单' },
    { label: '负责人', value: '陈念' },
  ],
  tags: ['续约中', '高意向', '华东区域'],
  timeline: [
    { id: 'ctx-1', datetime: '2026-10-15', title: '通话回访', description: '确认续约意向，约定下周发送合同。' },
    { id: 'ctx-2', datetime: '2026-10-09', title: '提交扩容需求', description: '席位从 10 扩到 20，关注报价。' },
    { id: 'ctx-3', datetime: '2026-09-26', title: '完成验收培训', description: '核心成员完成平台操作培训。' },
  ],
}

// ============================================================
// 区块：发票与贷项通知单（单卡双视图，两个功能演示各自独立）
// ============================================================

const billingDocView = ref<'invoice' | 'credit'>('invoice')
const billingDocTabs: { label: string, value: 'invoice' | 'credit' }[] = [
  { label: '发票', value: 'invoice' },
  { label: '贷项通知单', value: 'credit' },
]

interface InvoiceItem {
  name: string
  qty: number
  price: number
}

interface Invoice {
  id: string
  issued: string
  due: string
  paid: boolean
  items: InvoiceItem[]
}

const invoices: Invoice[] = [
  {
    id: 'INV-2026-0930',
    issued: '2026-09-30',
    due: '2026-10-15',
    paid: true,
    items: [
      { name: '专业版月度订阅', qty: 1, price: 199 },
      { name: 'Agent 运行额度包', qty: 2, price: 50 },
    ],
  },
  {
    id: 'INV-2026-1015',
    issued: '2026-10-15',
    due: '2026-10-30',
    paid: false,
    items: [
      { name: '专业版月度订阅', qty: 1, price: 199 },
      { name: '额外席位（3 个）', qty: 3, price: 30 },
    ],
  },
]

const selectedInvoiceId = ref(invoices[0].id)
const invoiceOptions = invoices.map(invoice => ({
  label: `${invoice.id}（${invoice.paid ? '已支付' : '待支付'}）`,
  value: invoice.id,
}))
const currentInvoice = computed(() => invoices.find(invoice => invoice.id === selectedInvoiceId.value) ?? invoices[0])

const invoiceSubtotal = computed(() => currentInvoice.value.items.reduce((sum, item) => sum + item.qty * item.price, 0))
const invoiceTax = computed(() => Math.round(invoiceSubtotal.value * 0.06 * 100) / 100)
const invoiceTotal = computed(() => Math.round((invoiceSubtotal.value + invoiceTax.value) * 100) / 100)

function money(value: number) {
  return `¥${value.toFixed(2)}`
}

// ============================================================
// 区块：发票与贷项通知单 —— 贷项通知单视图数据
// ============================================================

interface CreditNoteItem {
  name: string
  qty: number
  price: number
}

interface CreditNoteDoc {
  id: string
  originalInvoice: string
  reason: '部分退货' | '服务未生效' | '重复扣费'
  method: '原路退回' | '余额冲抵'
  issued: string
  settled: boolean
  items: CreditNoteItem[]
}

const creditNotes = ref<CreditNoteDoc[]>([
  {
    id: 'CN-2026-1012',
    originalInvoice: 'INV-2026-0930',
    reason: '服务未生效',
    method: '原路退回',
    issued: '2026-10-12',
    settled: false,
    items: [
      { name: 'Agent 运行额度包', qty: 1, price: 50 },
      { name: '额外席位（2 个）', qty: 2, price: 30 },
    ],
  },
  {
    id: 'CN-2026-1005',
    originalInvoice: 'INV-2026-0915',
    reason: '部分退货',
    method: '余额冲抵',
    issued: '2026-10-05',
    settled: true,
    items: [
      { name: '专业版月度订阅', qty: 1, price: 199 },
    ],
  },
])

const selectedCreditNoteId = ref(creditNotes.value[0].id)
const creditNoteOptions = computed(() => creditNotes.value.map(note => ({
  label: `${note.id}（${note.settled ? '已冲抵' : '待确认'}）`,
  value: note.id,
})))
const currentCreditNote = computed(() => creditNotes.value.find(note => note.id === selectedCreditNoteId.value) ?? creditNotes.value[0])
const creditNoteTotal = computed(() => currentCreditNote.value.items.reduce((sum, item) => sum + item.qty * item.price, 0))

function settleCreditNote() {
  if (currentCreditNote.value.settled) {
    return
  }
  currentCreditNote.value.settled = true
  toast.success(`贷项通知单 ${currentCreditNote.value.id} 已冲抵（本地演示）`)
}

// ============================================================
// 区块：客户 Logo 墙
// ============================================================

interface BrandLogo {
  id: string
  name: string
  icon: string
}

const logoBrands: BrandLogo[] = [
  { id: 'logo-1', name: '云帆传媒', icon: 'morph:sailboat' },
  { id: 'logo-2', name: '北望科技', icon: 'morph:compass' },
  { id: 'logo-3', name: '启程旅行', icon: 'morph:plane' },
  { id: 'logo-4', name: '白鹭书店', icon: 'morph:book-open' },
  { id: 'logo-5', name: '南屿咖啡', icon: 'morph:coffee' },
  { id: 'logo-6', name: '星衡律所', icon: 'morph:scale' },
  { id: 'logo-7', name: '深蓝体育', icon: 'morph:trophy' },
  { id: 'logo-8', name: '禾光农业', icon: 'morph:leaf' },
]

const logoMarqueePace = ref('standard')
const logoMarqueePaceOptions = [
  { label: '舒缓', value: 'slow' },
  { label: '标准', value: 'standard' },
  { label: '轻快', value: 'fast' },
]
const logoMarqueeDuration = computed(() => {
  if (logoMarqueePace.value === 'slow') {
    return 44
  }
  if (logoMarqueePace.value === 'fast') {
    return 16
  }
  return 28
})
const logoMarqueeReverse = ref(false)
const logoMarqueePauseOnHover = ref(true)

// ============================================================
// 区块：用户评价墙
// ============================================================

interface Testimonial {
  id: string
  quote: string
  name: string
  company: string
  fallback: string
  rating: number
  source: string
}

const testimonials = ref<Testimonial[]>([
  { id: 'ts-1', quote: '权限码词表和双闸门机制让插件接入第一次变得可预期，评审时间缩短了一半。', name: '沈其远', company: '北望科技 · 平台架构师', fallback: '沈', rating: 5, source: '深度访谈' },
  { id: 'ts-2', quote: '从安装插件到看到第一张报表只用了十分钟，引导步骤没有一处需要猜。', name: '阮清', company: '白鹭书店 · 运营负责人', fallback: '阮', rating: 5, source: '上线回访' },
  { id: 'ts-3', quote: 'Agent 运行的人工审批做得很克制，敏感操作必须确认，审计记录也跟得上。', name: '翟一鸣', company: '星衡律所 · 信息安全', fallback: '翟', rating: 4, source: '问卷回执' },
  { id: 'ts-4', quote: '发票与账单页直接替财务省掉了一个导出脚本，明细、税额、状态都在一处。', name: '明蕙', company: '云帆传媒 · 财务共享', fallback: '明', rating: 4, source: '问卷回执' },
  { id: 'ts-5', quote: '三套外观在深色模式下的表现超出预期，我们没有为夜班值守做任何额外适配。', name: '薄野', company: '深蓝体育 · 值班系统', fallback: '薄', rating: 5, source: '社区评价' },
  { id: 'ts-6', quote: '评估任务把模型回归变成了日常动作，通过率曲线直接进了周报。', name: '楼雨桐', company: '启程旅行 · 数据分析', fallback: '楼', rating: 3, source: '上线回访' },
])

const testimonialTier = ref('all')
const testimonialTierTabs = [
  { label: '全部', value: 'all' },
  { label: '五星精选', value: 'top' },
  { label: '四星及以上', value: 'good' },
]

const filteredTestimonials = computed(() => {
  if (testimonialTier.value === 'top') {
    return testimonials.value.filter(item => item.rating === 5)
  }
  if (testimonialTier.value === 'good') {
    return testimonials.value.filter(item => item.rating >= 4)
  }
  return testimonials.value
})

function shuffleTestimonials() {
  testimonials.value = [...testimonials.value.slice(1), testimonials.value[0]]
  toast('已换一批评价（本地演示）')
}

// ============================================================
// 区块：团队 KPI 看板
// ============================================================

type KpiBoardPeriod = 'month' | 'quarter' | 'year'

interface KpiBoardTeam {
  id: string
  name: string
  owner: string
  ownerFallback: string
  target: number
  done: number
}

interface KpiBoardSnapshot {
  revenue: number
  newCustomers: number
  retention: number
  avgResponse: string
  teams: KpiBoardTeam[]
}

const kpiBoardPeriod = ref<KpiBoardPeriod>('month')
const kpiBoardPeriodTabs: { label: string, value: KpiBoardPeriod }[] = [
  { label: '本月', value: 'month' },
  { label: '本季度', value: 'quarter' },
  { label: '本年度', value: 'year' },
]

const kpiBoardSnapshots: Record<KpiBoardPeriod, KpiBoardSnapshot> = {
  month: {
    revenue: 328_600,
    newCustomers: 46,
    retention: 96.4,
    avgResponse: '2.4h',
    teams: [
      { id: 'team-1', name: '华东销售组', owner: '陈念', ownerFallback: '陈', target: 120_000, done: 108_400 },
      { id: 'team-2', name: '华南销售组', owner: '林舟', ownerFallback: '林', target: 100_000, done: 96_200 },
      { id: 'team-3', name: '客户成功组', owner: '周衡', ownerFallback: '周', target: 80_000, done: 61_500 },
    ],
  },
  quarter: {
    revenue: 986_400,
    newCustomers: 137,
    retention: 95.8,
    avgResponse: '2.9h',
    teams: [
      { id: 'team-1', name: '华东销售组', owner: '陈念', ownerFallback: '陈', target: 360_000, done: 341_200 },
      { id: 'team-2', name: '华南销售组', owner: '林舟', ownerFallback: '林', target: 300_000, done: 288_600 },
      { id: 'team-3', name: '客户成功组', owner: '周衡', ownerFallback: '周', target: 240_000, done: 205_300 },
    ],
  },
  year: {
    revenue: 3_842_000,
    newCustomers: 512,
    retention: 94.9,
    avgResponse: '3.6h',
    teams: [
      { id: 'team-1', name: '华东销售组', owner: '陈念', ownerFallback: '陈', target: 1_440_000, done: 1_402_500 },
      { id: 'team-2', name: '华南销售组', owner: '林舟', ownerFallback: '林', target: 1_200_000, done: 1_187_400 },
      { id: 'team-3', name: '客户成功组', owner: '周衡', ownerFallback: '周', target: 960_000, done: 902_800 },
    ],
  },
}

const currentKpiBoard = computed(() => kpiBoardSnapshots[kpiBoardPeriod.value])

function kpiTeamPercent(team: KpiBoardTeam) {
  return Math.min(100, Math.round(team.done / team.target * 100))
}

function kpiTeamMet(team: KpiBoardTeam) {
  return team.done >= team.target
}

// ============================================================
// 区块：Agent 画廊
// ============================================================

type GalleryAgentCategory = '写作' | '研发' | '数据' | '客服'

interface GalleryAgent {
  id: string
  name: string
  description: string
  category: GalleryAgentCategory
  icon: string
  enabled: boolean
  runs: number
  running: boolean
}

const galleryAgents = ref<GalleryAgent[]>([
  { id: 'ga-release', name: '发布说明助手', description: '汇总提交与 issue，起草发布公告', category: '写作', icon: 'morph:pen', enabled: true, runs: 128, running: false },
  { id: 'ga-changelog', name: '变更摘要官', description: '面向管理层的周度变更速览', category: '写作', icon: 'morph:list-checks', enabled: false, runs: 19, running: false },
  { id: 'ga-review', name: '代码评审员', description: '按分层与权限规范扫描变更缺口', category: '研发', icon: 'morph:scan-search', enabled: true, runs: 76, running: false },
  { id: 'ga-perf', name: '性能巡检员', description: '定期巡检接口耗时并给出建议', category: '研发', icon: 'morph:gauge', enabled: true, runs: 58, running: false },
  { id: 'ga-sql', name: '数据取数助手', description: '自然语言生成查询并导出报表', category: '数据', icon: 'morph:database', enabled: false, runs: 41, running: false },
  { id: 'ga-ticket', name: '客服工单分类', description: '自动归类工单并推荐回复口径', category: '客服', icon: 'morph:headset', enabled: true, runs: 233, running: false },
])

const agentGalleryCategory = ref('all')
const agentGalleryTabs: { label: string, value: 'all' | GalleryAgentCategory }[] = [
  { label: '全部', value: 'all' },
  { label: '写作', value: '写作' },
  { label: '研发', value: '研发' },
  { label: '数据', value: '数据' },
  { label: '客服', value: '客服' },
]
const agentGalleryKeyword = ref('')

const filteredGalleryAgents = computed(() => {
  const kw = agentGalleryKeyword.value.trim().toLowerCase()
  return galleryAgents.value.filter((agent) => {
    if (agentGalleryCategory.value !== 'all' && agent.category !== agentGalleryCategory.value) {
      return false
    }
    return !kw || agent.name.toLowerCase().includes(kw) || agent.description.toLowerCase().includes(kw)
  })
})

function toggleGalleryAgent(agent: GalleryAgent) {
  agent.enabled = !agent.enabled
  toast(agent.enabled ? `已启用「${agent.name}」（本地演示）` : `已停用「${agent.name}」（本地演示）`)
}

function runGalleryAgent(agent: GalleryAgent) {
  if (!agent.enabled || agent.running) {
    return
  }
  agent.running = true
  window.setTimeout(() => {
    agent.running = false
    agent.runs += 1
    toast.success(`「${agent.name}」运行完成（本地演示）`)
  }, 900)
}

// ============================================================
// 区块：AI 工作台（本地模拟，不连接模型服务）
// ============================================================

interface AiwsProject {
  id: string
  name: string
  description: string
  owner: string
  ownerFallback: string
  docs: number
  status: '进行中' | '已交付' | '已暂停'
}

const aiwsProjects: AiwsProject[] = [
  { id: 'aiws-1', name: '官网改版', description: '新官网视觉与内容迁移', owner: '陈念', ownerFallback: '陈', docs: 12, status: '进行中' },
  { id: 'aiws-2', name: '插件市场筹备', description: '上架流程与审核规范', owner: '林舟', ownerFallback: '林', docs: 8, status: '进行中' },
  { id: 'aiws-3', name: '数据迁移工具', description: '历史库分批迁移方案', owner: '周衡', ownerFallback: '周', docs: 5, status: '已暂停' },
]

const activeAiwsProjectId = ref('aiws-1')
const activeAiwsProject = computed(() => aiwsProjects.find(project => project.id === activeAiwsProjectId.value) ?? aiwsProjects[0])

const aiwsThreads = ref<Record<string, YdChatMessage[]>>({
  'aiws-1': [
    { id: 'aiws-m-1', role: 'assistant', content: '「官网改版」空间已就绪：可以让我整理页面清单、起草文案或核对验收标准。' },
  ],
  'aiws-2': [],
  'aiws-3': [],
})
const activeAiwsThread = computed(() => aiwsThreads.value[activeAiwsProjectId.value] ?? [])
const aiwsSending = ref(false)

function sendAiws(text: string) {
  const content = text.trim()
  if (!content || aiwsSending.value) {
    return
  }
  const projectId = activeAiwsProjectId.value
  if (!aiwsThreads.value[projectId]) {
    aiwsThreads.value[projectId] = []
  }
  aiwsSending.value = true
  aiwsThreads.value[projectId].push({ id: `aiws-u-${Date.now()}`, role: 'user', content })
  const replyId = `aiws-a-${Date.now()}`
  aiwsThreads.value[projectId].push({ id: replyId, role: 'assistant', content: '', pending: true })
  window.setTimeout(() => {
    const reply = aiwsThreads.value[projectId]?.find(item => item.id === replyId)
    if (reply) {
      reply.content = `「${activeAiwsProject.value.name}」本地演示回复：已记录「${content}」。工作台为页面内模拟，未连接真实模型服务。`
      reply.pending = false
    }
    aiwsSending.value = false
  }, 900)
}

function clearAiwsThread() {
  if (!activeAiwsThread.value.length) {
    return
  }
  aiwsThreads.value[activeAiwsProjectId.value] = []
  toast(`已清空「${activeAiwsProject.value.name}」会话（本地演示）`)
}

function aiwsStatusVariant(status: AiwsProject['status']) {
  if (status === '进行中') {
    return 'default' as const
  }
  if (status === '已交付') {
    return 'secondary' as const
  }
  return 'outline' as const
}
</script>

<template>
  <div class="sg">
    <!-- 画廊级搜索与分组筛选 -->
    <section class="sg-head">
      <div class="sg-head__text">
        <h2>场景画廊</h2>
        <p>以真实任务组合 YD 组件的界面与区块演示，全部为本地数据，可搜索、切换分组、直接操作。</p>
      </div>
      <div class="sg-head__tools">
        <YdInput v-model="galleryKeyword" clearable placeholder="搜索场景，如：审批、发票、看板" aria-label="搜索场景">
          <template #start>
            <YdIcon name="morph:search" />
          </template>
        </YdInput>
        <YdTabs v-model="galleryGroup" :list="galleryTabs" />
      </div>
    </section>
    <p class="sg-count" aria-live="polite">
      共 {{ visibleScenes.length }} 个场景
    </p>

    <YdEmpty
      v-if="!visibleScenes.length"
      title="没有匹配的场景"
      description="换个关键字，或清除当前筛选条件。"
    >
      <YdButton size="sm" variant="outline" @click="resetGalleryFilters">
        清除筛选
      </YdButton>
    </YdEmpty>

    <template v-for="scene in visibleScenes" :key="scene.key">
      <!-- ================= 应用工作台 ================= -->
      <DemoCard v-if="scene.key === 'workspace'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-shell">
          <nav class="sg-shell__nav" aria-label="工作区导航">
            <YdAction
              v-for="nav in workspaceNavs"
              :key="nav.key"
              type="button"
              class="sg-shell__item"
              :class="{ 'is-active': activeWorkspaceNav === nav.key }"
              :aria-current="activeWorkspaceNav === nav.key ? 'page' : undefined"
              @click="activeWorkspaceNav = nav.key"
            >
              <YdBadge :value="nav.badge ?? false">
                <YdIcon :name="nav.icon" />
              </YdBadge>
              <span>{{ nav.label }}</span>
            </YdAction>
          </nav>

          <div class="sg-shell__main">
            <div class="sg-shell__topbar">
              <YdInput v-model="workspaceSearch" clearable placeholder="搜索项目" aria-label="搜索项目">
                <template #start>
                  <YdIcon name="morph:search" />
                </template>
              </YdInput>
              <YdBadge :value="3">
                <YdButton size="icon-sm" variant="ghost" aria-label="通知">
                  <YdIcon name="morph:bell" />
                </YdButton>
              </YdBadge>
              <YdAvatar src="" fallback="雨" />
            </div>

            <div v-if="activeWorkspaceNav === 'overview'" class="sg-stack">
              <div class="sg-kpi">
                <YdDigitalCard title="今日活跃" :digital="1284" icon="morph:users" trend="up" description="较昨日 +8.4%" />
                <YdDigitalCard title="进行中任务" :digital="23" icon="morph:list-checks" trend="stable" description="本周到期 6 项" />
                <YdDigitalCard title="存储用量" digital="68%" icon="morph:database" trend="up" description="接近套餐上限，建议扩容" />
              </div>
              <div class="sg-list">
                <div v-for="project in filteredProjects" :key="project.id" class="sg-list__row">
                  <span class="sg-list__title">{{ project.name }}</span>
                  <YdTag :variant="projectStatusVariant(project.status)">
                    {{ project.status }}
                  </YdTag>
                  <span class="sg-list__meta">{{ project.updatedAt }}</span>
                </div>
                <p v-if="!filteredProjects.length" class="sg-hint">
                  没有匹配的项目
                </p>
              </div>
            </div>

            <div v-else-if="activeWorkspaceNav === 'projects'" class="sg-list">
              <div v-for="project in filteredProjects" :key="project.id" class="sg-list__row">
                <span class="sg-list__title">{{ project.name }}</span>
                <YdTag :variant="projectStatusVariant(project.status)">
                  {{ project.status }}
                </YdTag>
                <span class="sg-list__meta">{{ project.updatedAt }}</span>
                <YdButton size="sm" variant="ghost" @click="toast(`打开「${project.name}」（本地演示）`)">
                  打开
                </YdButton>
              </div>
            </div>

            <div v-else-if="activeWorkspaceNav === 'members'" class="sg-list">
              <div v-for="member in workspaceMembers" :key="member.id" class="sg-list__row">
                <YdAvatar src="" :fallback="member.fallback" />
                <span class="sg-list__title">{{ member.name }}</span>
                <span class="sg-list__id">{{ member.id }}</span>
                <YdTag :variant="member.role === '管理员' ? 'default' : 'secondary'">
                  {{ member.role }}
                </YdTag>
              </div>
            </div>

            <div v-else-if="activeWorkspaceNav === 'messages'" class="sg-stack">
              <YdAlert icon="morph:bell" title="3 条未读消息" description="点击下方条目标记为已读（本地演示，不会真正消除顶部徽标）。" />
              <div class="sg-list">
                <div v-for="notice in workspaceNotices" :key="notice.id" class="sg-list__row">
                  <span class="sg-list__title">{{ notice.title }}</span>
                  <span class="sg-list__meta">{{ notice.time }}</span>
                  <YdButton size="sm" variant="ghost" @click="toast(`已读：${notice.title}`)">
                    标记已读
                  </YdButton>
                </div>
              </div>
            </div>

            <div v-else class="sg-stack">
              <div class="sg-setting">
                <div>
                  <p class="sg-setting__title">
                    每周摘要
                  </p>
                  <p class="sg-setting__desc">
                    每周一发送上周工作区动态
                  </p>
                </div>
                <YdSwitch v-model="workspacePrefs.weeklyDigest" aria-label="每周摘要" />
              </div>
              <div class="sg-setting">
                <div>
                  <p class="sg-setting__title">
                    操作提醒
                  </p>
                  <p class="sg-setting__desc">
                    任务状态变化时提醒我
                  </p>
                </div>
                <YdSwitch v-model="workspacePrefs.opReminder" aria-label="操作提醒" />
              </div>
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 助手首页与对话 ================= -->
      <DemoCard v-else-if="scene.key === 'assistant'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdAlert icon="morph:circle-info" title="本地演示" description="回复为组件内模拟生成，不连接模型服务；生产环境由宿主 Harness 管理真实运行。" />
        <div class="sg-chat">
          <div class="sg-chat__sessions">
            <p class="sg-caption">
              会话
            </p>
            <YdAction
              v-for="session in assistantSessions"
              :key="session.id"
              type="button"
              class="sg-chat__session"
              :class="{ 'is-active': activeAssistantSession === session.id }"
              :aria-current="activeAssistantSession === session.id ? 'true' : undefined"
              @click="activeAssistantSession = session.id"
            >
              <span>{{ session.title }}</span>
              <span class="sg-chat__count">{{ session.count }}</span>
            </YdAction>
          </div>

          <div class="sg-chat__main">
            <template v-if="!assistantMessages.length">
              <YdWelcome
                title="下午好，需要做点什么？"
                description="可以让我整理变更、解释平台机制或起草公告。"
                :suggestions="['帮我总结本周的平台变更。', '解释一下平台能力的双闸门机制。']"
                @select="sendAssistant"
              />
              <YdPrompts title="试试这些" :items="assistantPrompts" @select="item => sendAssistant(item.value ?? item.label)" />
            </template>
            <template v-else>
              <div class="sg-chat__messages">
                <YdChatMessageList :messages="assistantMessages" />
              </div>
              <YdChatSender :loading="assistantSending" constrained @send="sendAssistant" @stop="assistantSending = false" />
            </template>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 文件空间 ================= -->
      <DemoCard v-else-if="scene.key === 'files'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar">
          <YdInput v-model="fileKeyword" clearable placeholder="搜索文件名" aria-label="搜索文件名">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdSelect v-model="fileKind" :options="fileKindOptions" placeholder="筛选类型" aria-label="按类型筛选" class="sg-toolbar__field" />
          <YdTabs v-model="fileView" :list="fileViewTabs" />
          <YdButton size="sm" variant="outline" @click="toast('本地演示：已模拟上传 1 个文件')">
            <YdIcon name="morph:cloud-upload" />
            上传
          </YdButton>
        </div>

        <template v-if="filteredFiles.length">
          <YdTable
            v-if="fileView === 'list'"
            :columns="fileColumns"
            :data="filteredFiles"
            row-key="id"
            stripe
            sortable
            empty-text="暂无文件"
          >
            <template #cell-name="{ row }">
              <span class="sg-file">
                <YdIcon :name="fileKindIcon(row.original.kind)" />
                <span>{{ row.original.name }}</span>
                <YdIcon v-if="row.original.starred" name="morph:star" class="sg-file__star" />
              </span>
            </template>
            <template #cell-kind="{ row }">
              <YdTag variant="secondary">
                {{ row.original.kind }}
              </YdTag>
            </template>
            <template #cell-actions="{ row }">
              <span class="sg-row-actions">
                <YdTooltip :text="row.original.starred ? '取消收藏' : '收藏'">
                  <YdButton size="icon-sm" :variant="row.original.starred ? 'default' : 'ghost'" :aria-label="row.original.starred ? '取消收藏' : '收藏'" @click="toggleFileStar(row.original)">
                    <YdIcon name="morph:star" />
                  </YdButton>
                </YdTooltip>
                <YdTooltip text="下载">
                  <YdButton size="icon-sm" variant="ghost" aria-label="下载文件" @click="toast(`开始下载「${row.original.name}」（本地演示）`)">
                    <YdIcon name="morph:download" />
                  </YdButton>
                </YdTooltip>
                <YdTooltip text="删除">
                  <YdButton size="icon-sm" variant="ghost" aria-label="删除文件" @click="removeFile(row.original)">
                    <YdIcon name="morph:trash" />
                  </YdButton>
                </YdTooltip>
              </span>
            </template>
          </YdTable>

          <div v-else class="sg-grid">
            <div v-for="file in filteredFiles" :key="file.id" class="sg-filecard">
              <YdIcon :name="fileKindIcon(file.kind)" class="sg-filecard__icon" />
              <p class="sg-filecard__name">
                {{ file.name }}
              </p>
              <p class="sg-filecard__meta">
                {{ file.kind }} · {{ file.size }} · {{ file.updatedAt }}
              </p>
              <div class="sg-row-actions">
                <YdButton size="sm" :variant="file.starred ? 'default' : 'outline'" @click="toggleFileStar(file)">
                  {{ file.starred ? '已收藏' : '收藏' }}
                </YdButton>
                <YdButton size="sm" variant="ghost" @click="removeFile(file)">
                  删除
                </YdButton>
              </div>
            </div>
          </div>
        </template>
        <YdEmpty
          v-else
          title="没有匹配的文件"
          description="当前关键字或类型没有命中任何文件。"
        >
          <YdButton size="sm" variant="outline" @click="resetFileFilters">
            重置筛选
          </YdButton>
        </YdEmpty>
      </DemoCard>

      <!-- ================= 集成中心 ================= -->
      <DemoCard v-else-if="scene.key === 'integrations'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdTabs v-model="integrationFilter" :list="integrationTabs" class="sg-tabs-line" />
        <div class="sg-grid">
          <div v-for="item in filteredIntegrations" :key="item.id" class="sg-integration">
            <div class="sg-integration__head">
              <YdIcon :name="item.icon" class="sg-integration__icon" />
              <div class="sg-integration__text">
                <p class="sg-integration__name">
                  {{ item.name }}
                  <YdTag :variant="item.connected ? 'secondary' : 'outline'">
                    {{ item.connected ? '已连接' : '未连接' }}
                  </YdTag>
                </p>
                <p class="sg-integration__desc">
                  {{ item.description }}
                </p>
              </div>
            </div>
            <div class="sg-row-actions">
              <YdButton size="sm" variant="outline" @click="toast(`打开「${item.name}」配置（本地演示）`)">
                配置
              </YdButton>
              <YdSwitch :model-value="item.connected" :aria-label="`连接 ${item.name}`" @update:model-value="toggleIntegration(item)" />
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 数据分析看板 ================= -->
      <DemoCard v-else-if="scene.key === 'analytics'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdTabs v-model="analyticsRange" :list="analyticsTabs" class="sg-tabs-line" />
        <div class="sg-kpi">
          <div class="sg-kpi__tile">
            <p class="sg-kpi__label">
              访问量
            </p>
            <p class="sg-kpi__value">
              {{ currentAnalytics.visits.toLocaleString() }}
            </p>
            <div class="sg-kpi__foot">
              <YdTrend :value="currentAnalytics.visitsTrend" type="up" />
              <YdSparkline :data="currentAnalytics.spark" label="访问量趋势" />
            </div>
          </div>
          <div class="sg-kpi__tile">
            <p class="sg-kpi__label">
              转化数
            </p>
            <p class="sg-kpi__value">
              {{ currentAnalytics.conversions.toLocaleString() }}
            </p>
            <div class="sg-kpi__foot">
              <YdTrend :value="currentAnalytics.conversionsTrend" type="up" variant="soft" />
              <YdSparkline :data="[...currentAnalytics.spark].reverse()" label="转化趋势" />
            </div>
          </div>
          <div class="sg-kpi__tile">
            <p class="sg-kpi__label">
              营收
            </p>
            <p class="sg-kpi__value">
              {{ currentAnalytics.revenue }}
            </p>
            <p class="sg-kpi__note">
              含订阅与额度包收入
            </p>
          </div>
          <div class="sg-kpi__tile">
            <p class="sg-kpi__label">
              季度目标完成
            </p>
            <p class="sg-kpi__value">
              {{ currentAnalytics.goal }}%
            </p>
            <YdProgress :model-value="currentAnalytics.goal" />
          </div>
        </div>
        <div class="sg-subcard">
          <p class="sg-caption">
            热门页面
          </p>
          <YdTable :columns="analyticsPageColumns" :data="currentAnalytics.pages" row-key="id" stripe />
        </div>
      </DemoCard>

      <!-- ================= CRM 客户表格 ================= -->
      <DemoCard v-else-if="scene.key === 'crm'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar">
          <YdInput v-model="crmKeyword" clearable placeholder="搜索客户名称" aria-label="搜索客户名称">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdSelect v-model="crmStage" :options="crmStageOptions" placeholder="筛选阶段" aria-label="按阶段筛选" class="sg-toolbar__field" />
          <YdButton size="sm" @click="toast('新建客户（本地演示）')">
            <YdIcon name="morph:plus" />
            新建客户
          </YdButton>
        </div>
        <YdTable
          :columns="crmColumns"
          :data="filteredCustomers"
          row-key="id"
          stripe
          sortable
          empty-text="没有匹配的客户"
        >
          <template #cell-name="{ row }">
            <span class="sg-strong">{{ row.original.name }}</span>
          </template>
          <template #cell-stage="{ row }">
            <YdTag :variant="crmStageVariant(row.original.stage)">
              {{ row.original.stage }}
            </YdTag>
          </template>
          <template #cell-owner="{ row }">
            <span class="sg-owner">
              <YdAvatar src="" :fallback="row.original.ownerFallback" />
              {{ row.original.owner }}
            </span>
          </template>
          <template #cell-actions="{ row }">
            <YdDropdown :items="crmRowActions(row.original)" align="end">
              <YdButton size="icon-sm" variant="ghost" aria-label="更多客户操作">
                <YdIcon name="morph:ellipsis" />
              </YdButton>
            </YdDropdown>
          </template>
        </YdTable>
      </DemoCard>

      <!-- ================= Agent 运行与审批 ================= -->
      <DemoCard v-else-if="scene.key === 'agent-run'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdAlert icon="morph:circle-info" title="本地演示" description="工具状态与审批在本页内流转，不触发任何真实执行；生产环境由宿主 Harness 权威管理。" />
        <div class="sg-agent">
          <div class="sg-stack">
            <p class="sg-caption">
              工具调用
            </p>
            <div v-for="tool in toolCalls" :key="tool.id" class="sg-tool">
              <div class="sg-tool__head">
                <span class="sg-strong">{{ tool.name }}</span>
                <YdTag :variant="toolStatusMeta(tool.status).variant">
                  {{ toolStatusMeta(tool.status).label }}
                </YdTag>
              </div>
              <p class="sg-tool__detail">
                {{ tool.detail }}
              </p>
              <div v-if="tool.status === 'running'" class="sg-row-actions">
                <YdLoading :size="16" text="执行中" />
              </div>
              <div v-else-if="tool.status === 'awaiting-approval'" class="sg-row-actions">
                <YdButton size="sm" @click="approveTool(tool)">
                  <YdIcon name="morph:check" />
                  批准
                </YdButton>
                <YdButton size="sm" variant="outline" @click="rejectTool(tool)">
                  <YdIcon name="morph:x" />
                  拒绝
                </YdButton>
              </div>
            </div>
          </div>
          <div class="sg-stack">
            <p class="sg-caption">
              运行事件
            </p>
            <YdTimeline :data="runEvents" />
          </div>
        </div>
      </DemoCard>

      <!-- ================= 设置与账单 ================= -->
      <DemoCard v-else-if="scene.key === 'settings-billing'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-two-col">
          <div class="sg-stack">
            <p class="sg-caption">
              安全与通知
            </p>
            <div class="sg-setting">
              <div>
                <p class="sg-setting__title">
                  邮件通知
                </p>
                <p class="sg-setting__desc">
                  账单与安全事件邮件提醒
                </p>
              </div>
              <YdSwitch v-model="billingSettings.mailNotice" aria-label="邮件通知" />
            </div>
            <div class="sg-setting">
              <div>
                <p class="sg-setting__title">
                  双因素认证
                </p>
                <p class="sg-setting__desc">
                  登录时要求输入动态验证码
                </p>
              </div>
              <YdSwitch v-model="billingSettings.twoFactor" aria-label="双因素认证" />
            </div>
            <div class="sg-setting">
              <div>
                <p class="sg-setting__title">
                  自动续费
                </p>
                <p class="sg-setting__desc">
                  到期当天自动扣款续订
                </p>
              </div>
              <YdSwitch v-model="billingSettings.autoRenew" aria-label="自动续费" />
            </div>
            <div class="sg-setting">
              <div>
                <p class="sg-setting__title">
                  每周摘要
                </p>
                <p class="sg-setting__desc">
                  每周一发送用量与账单摘要
                </p>
              </div>
              <YdSwitch v-model="billingSettings.weeklyDigest" aria-label="每周摘要" />
            </div>
          </div>

          <div class="sg-stack">
            <p class="sg-caption">
              当前方案
            </p>
            <YdSelect v-model="currentPlan" :options="planOptions" aria-label="切换方案" @update:model-value="switchPlan" />
            <div class="sg-subcard">
              <div class="sg-plan">
                <div>
                  <p class="sg-plan__name">
                    {{ currentPlanMeta.name }}
                  </p>
                  <p class="sg-plan__price">
                    {{ currentPlanMeta.price }}
                  </p>
                </div>
                <YdButton size="sm" variant="outline" @click="toast('管理席位（本地演示）')">
                  管理席位
                </YdButton>
              </div>
              <p class="sg-setting__desc">
                席位 {{ currentPlanMeta.seats }} / {{ currentPlanMeta.seatTotal }}
              </p>
              <YdProgress :model-value="seatPercent" />
              <p class="sg-setting__desc">
                {{ currentPlanMeta.renewDate }}
              </p>
            </div>
            <div class="sg-list">
              <div v-for="invoiceRow in billingInvoices" :key="invoiceRow.id" class="sg-list__row">
                <span class="sg-list__title">{{ invoiceRow.no }}</span>
                <span class="sg-list__meta">{{ invoiceRow.date }} · {{ invoiceRow.amount }}</span>
                <YdTag :variant="invoiceRow.paid ? 'secondary' : 'default'">
                  {{ invoiceRow.paid ? '已支付' : '待支付' }}
                </YdTag>
                <YdButton size="sm" variant="ghost" @click="toast(`下载 ${invoiceRow.no}（本地演示）`)">
                  下载
                </YdButton>
              </div>
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 新手引导与评估 ================= -->
      <DemoCard v-else-if="scene.key === 'onboarding'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-two-col">
          <div class="sg-stack">
            <p class="sg-caption">
              上手引导
            </p>
            <YdSteps v-model="onboardingStep" :steps="onboardingSteps" clickable vertical />
            <div class="sg-stack">
              <YdCheckbox
                v-for="check in onboardingChecks"
                :key="check.id"
                v-model="check.done"
              >
                {{ check.label }}
              </YdCheckbox>
            </div>
            <YdProgress :model-value="onboardingPercent" />
            <YdAlert
              v-if="onboardingDone"
              icon="morph:circle-check"
              title="引导完成"
              description="全部清单项已完成，可以开始正式使用。"
            />
          </div>
          <div class="sg-stack">
            <p class="sg-caption">
              模型评估
            </p>
            <div v-for="task in evalTasks" :key="task.id" class="sg-eval">
              <div class="sg-tool__head">
                <span class="sg-strong">{{ task.name }}</span>
                <YdTag variant="outline">
                  {{ task.model }}
                </YdTag>
                <YdTag :variant="evalStatusMeta(task.status).variant">
                  {{ evalStatusMeta(task.status).label }}
                </YdTag>
              </div>
              <div class="sg-eval__meter">
                <YdProgress :model-value="task.passRate" />
                <span class="sg-eval__rate">通过率 {{ task.passRate }}%</span>
              </div>
              <YdButton size="sm" variant="outline" :disabled="task.status === 'running'" @click="runEval(task)">
                {{ task.status === 'running' ? '评估中…' : '运行评估' }}
              </YdButton>
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 文件夹详情 ================= -->
      <DemoCard v-else-if="scene.key === 'folder'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <nav class="sg-fd__crumb" aria-label="文件夹路径">
          <template v-for="(crumb, index) in folderTrail" :key="crumb.id">
            <YdIcon v-if="index > 0" name="morph:chevron-right" class="sg-fd__crumb-sep" />
            <YdAction
              type="button"
              class="sg-fd__crumb-item"
              :class="{ 'is-current': index === folderTrail.length - 1 }"
              :aria-current="index === folderTrail.length - 1 ? 'page' : undefined"
              @click="jumpFolderTrail(index)"
            >
              <YdIcon name="morph:folder" />
              {{ crumb.name }}
            </YdAction>
          </template>
        </nav>
        <div class="sg-toolbar">
          <YdInput v-model="folderKeyword" clearable placeholder="搜索当前文件夹" aria-label="搜索当前文件夹">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdTabs v-model="folderView" :list="folderViewTabs" />
          <YdButton size="sm" variant="outline" @click="toast('本地演示：已在当前文件夹新建 1 个文件夹')">
            <YdIcon name="morph:folder-plus" />
            新建文件夹
          </YdButton>
        </div>

        <template v-if="folderEntries.length">
          <div v-if="folderView === 'list'" class="sg-fd__list">
            <div v-for="entry in folderEntries" :key="entry.id" class="sg-fd__row">
              <YdIcon :name="folderTypeIcon(entry)" class="sg-fd__row-icon" />
              <YdAction
                v-if="entry.type === 'folder'"
                type="button"
                class="sg-fd__row-name"
                :aria-label="`进入文件夹 ${entry.name}`"
                @click="openFolderEntry(entry)"
              >
                {{ entry.name }}
              </YdAction>
              <span v-else class="sg-fd__row-name">{{ entry.name }}</span>
              <span class="sg-fd__row-meta">{{ folderEntrySize(entry) }}</span>
              <span class="sg-fd__row-meta">{{ entry.updatedAt }}</span>
              <YdTooltip :text="entry.starred ? '取消收藏' : '收藏'">
                <YdButton size="icon-sm" :variant="entry.starred ? 'default' : 'ghost'" :aria-label="entry.starred ? '取消收藏' : '收藏'" @click="toggleFolderStar(entry)">
                  <YdIcon name="morph:star" />
                </YdButton>
              </YdTooltip>
            </div>
          </div>

          <div v-else class="sg-grid">
            <div v-for="entry in folderEntries" :key="entry.id" class="sg-filecard">
              <YdIcon :name="folderTypeIcon(entry)" class="sg-filecard__icon" />
              <p class="sg-filecard__name">
                {{ entry.name }}
              </p>
              <p class="sg-filecard__meta">
                {{ folderEntrySize(entry) }} · {{ entry.updatedAt }}
              </p>
              <div class="sg-row-actions">
                <YdButton v-if="entry.type === 'folder'" size="sm" variant="outline" @click="openFolderEntry(entry)">
                  打开
                </YdButton>
                <YdButton size="sm" :variant="entry.starred ? 'default' : 'outline'" @click="toggleFolderStar(entry)">
                  {{ entry.starred ? '已收藏' : '收藏' }}
                </YdButton>
              </div>
            </div>
          </div>
        </template>
        <YdEmpty
          v-else
          :title="currentFolder.children?.length ? '没有匹配的条目' : '文件夹为空'"
          :description="currentFolder.children?.length ? '当前关键字没有命中任何文件或文件夹。' : '这个文件夹还没有内容，可以新建文件夹或回到上级。'"
        >
          <YdButton v-if="currentFolder.children?.length" size="sm" variant="outline" @click="folderKeyword = ''">
            清除搜索
          </YdButton>
          <YdButton v-else size="sm" variant="outline" @click="toast('本地演示：已在当前文件夹新建 1 个文件夹')">
            <YdIcon name="morph:folder-plus" />
            新建文件夹
          </YdButton>
        </YdEmpty>
        <p class="sg-hint">
          共 {{ (currentFolder.children ?? []).length }} 个条目 · 文件夹可直接进入，面包屑可回跳上级目录。
        </p>
      </DemoCard>

      <!-- ================= Agent 对话台 ================= -->
      <DemoCard v-else-if="scene.key === 'agent-chat'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdAlert icon="morph:circle-info" title="本地演示" description="执行步骤与工具事件在页面内模拟流转，不连接模型服务；生产环境由宿主 Harness 管理真实运行。" />
        <div class="sg-agent-chat">
          <div class="sg-stack">
            <p class="sg-caption">
              执行模式
            </p>
            <YdSelect v-model="agentMode" :options="agentModeOptions" aria-label="执行模式" />
            <p class="sg-caption">
              执行步骤
            </p>
            <YdThoughtChain :items="agentStepItems" title="本次执行" :collapsible="false" />
          </div>
          <div class="sg-agent-chat__main">
            <div v-if="agentMessages.length" class="sg-agent-chat__messages">
              <YdBubble v-for="message in agentMessages" :key="message.id" :message="message" />
            </div>
            <YdEmpty
              v-else
              title="还没有任务"
              description="选择执行模式并下发一个任务，观察左侧步骤链推进。"
            />
            <YdChatSender :loading="agentBusy" constrained @send="sendAgentTask" @stop="agentBusy = false" />
          </div>
        </div>
      </DemoCard>

      <!-- ================= 模型评估台 ================= -->
      <DemoCard v-else-if="scene.key === 'evals'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdAlert icon="morph:circle-info" title="本地演示" description="评估在页面内用随机结果模拟，不连接模型服务或真实数据集。" />
        <div class="sg-two-col">
          <div class="sg-stack">
            <p class="sg-caption">
              运行配置
            </p>
            <YdSelect v-model="evalDatasetId" :options="evalDatasetOptions" aria-label="选择数据集" :disabled="evalRunning" />
            <YdSelect v-model="evalModel" :options="evalModelOptions" aria-label="选择模型" :disabled="evalRunning" />
            <p class="sg-hint">
              {{ evalActiveDataset.description }} · 共 {{ evalActiveDataset.caseTotal }} 个用例
            </p>
            <YdButton :disabled="evalRunning" @click="startEvalRun">
              <YdIcon name="morph:play" />
              {{ evalRunning ? '评估运行中…' : '运行评估' }}
            </YdButton>
            <YdProgress :model-value="evalProgress" />
          </div>
          <div class="sg-stack">
            <p class="sg-caption">
              用例结果
            </p>
            <div class="sg-eval-cases">
              <YdTag
                v-for="evalCase in evalCases"
                :key="evalCase.id"
                :variant="evalCase.status === 'pass' ? 'secondary' : evalCase.status === 'fail' ? 'destructive' : 'outline'"
                class="sg-eval-case"
              >
                <YdIcon :name="evalCase.status === 'pass' ? 'morph:check' : evalCase.status === 'fail' ? 'morph:x' : 'morph:circle-dot'" />
                {{ evalCase.name }}
              </YdTag>
            </div>
            <p class="sg-caption">
              运行历史
            </p>
            <YdTable :columns="evalRunColumns" :data="evalRuns" row-key="id" stripe>
              <template #cell-passRate="{ row }">
                {{ row.original.passed }}/{{ row.original.caseTotal }} · {{ row.original.passRate }}%
              </template>
            </YdTable>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 任务看板 ================= -->
      <DemoCard v-else-if="scene.key === 'task-board'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar">
          <YdSelect v-model="taskPriority" :options="taskPriorityOptions" placeholder="筛选优先级" aria-label="按优先级筛选" class="sg-toolbar__field" />
          <span class="sg-hint">共 {{ boardTasks.length }} 项任务</span>
        </div>
        <div class="sg-columns">
          <div v-for="column in boardColumns" :key="column.key" class="sg-col">
            <p class="sg-col__title">
              {{ column.label }}
              <span class="sg-col__count">{{ taskCount(column.key) }}</span>
            </p>
            <div v-for="task in tasksOf(column.key)" :key="task.id" class="sg-task">
              <div class="sg-task__head">
                <span class="sg-task__title">{{ task.title }}</span>
                <YdTag :variant="priorityVariant(task.priority)">
                  {{ task.priority }}
                </YdTag>
              </div>
              <div class="sg-task__foot">
                <YdAvatar src="" :fallback="task.assignee" />
                <div class="sg-row-actions">
                  <YdTooltip text="上一列">
                    <YdButton size="icon-sm" variant="ghost" :aria-label="`将「${task.title}」移动到上一列`" :disabled="column.key === 'todo'" @click="moveTask(task, -1)">
                      <YdIcon name="morph:chevron-left" />
                    </YdButton>
                  </YdTooltip>
                  <YdTooltip text="下一列">
                    <YdButton size="icon-sm" variant="ghost" :aria-label="`将「${task.title}」移动到下一列`" :disabled="column.key === 'done'" @click="moveTask(task, 1)">
                      <YdIcon name="morph:chevron-right" />
                    </YdButton>
                  </YdTooltip>
                </div>
              </div>
            </div>
            <p v-if="!tasksOf(column.key).length" class="sg-hint">
              暂无任务
            </p>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 定价切换 ================= -->
      <DemoCard v-else-if="scene.key === 'pricing'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-pricing-head">
          <YdTabs v-model="billingCycle" :list="billingCycleTabs" />
        </div>
        <div class="sg-pricing">
          <div
            v-for="plan in pricingPlans"
            :key="plan.id"
            class="sg-pricecard"
            :class="{ 'is-highlight': plan.highlight }"
          >
            <p class="sg-pricecard__name">
              {{ plan.name }}
              <YdTag v-if="plan.highlight" variant="default">
                推荐
              </YdTag>
            </p>
            <p class="sg-pricecard__price">
              {{ planPriceLabel(plan) }}
            </p>
            <p class="sg-pricecard__note">
              {{ planPriceNote(plan) }}
            </p>
            <ul class="sg-pricecard__features">
              <li v-for="feature in plan.features" :key="feature">
                <YdIcon name="morph:check" />
                {{ feature }}
              </li>
            </ul>
            <YdButton :variant="plan.highlight ? 'default' : 'outline'" class="sg-pricecard__cta" @click="choosePlan(plan)">
              选择方案
            </YdButton>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 应用目录 ================= -->
      <DemoCard v-else-if="scene.key === 'app-directory'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar">
          <YdInput v-model="directoryKeyword" clearable placeholder="搜索应用" aria-label="搜索应用">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdSelect v-model="directoryCategory" :options="directoryCategoryOptions" placeholder="筛选分类" aria-label="按分类筛选" class="sg-toolbar__field" />
          <span class="sg-hint">{{ filteredApps.length }} 个应用</span>
        </div>
        <div v-if="filteredApps.length" class="sg-grid">
          <div v-for="app in filteredApps" :key="app.id" class="sg-app">
            <div class="sg-app__head">
              <YdIcon name="morph:layout-grid" class="sg-app__icon" />
              <div>
                <p class="sg-app__name">
                  {{ app.name }}
                </p>
                <YdTag variant="secondary">
                  {{ app.category }}
                </YdTag>
              </div>
            </div>
            <p class="sg-app__desc">
              {{ app.description }}
            </p>
            <YdButton size="sm" :variant="app.installed ? 'outline' : 'default'" @click="toggleApp(app)">
              {{ app.installed ? '已安装 · 点击卸载' : '安装' }}
            </YdButton>
          </div>
        </div>
        <YdEmpty
          v-else
          title="没有匹配的应用"
          description="换个关键字或分类试试。"
        >
          <YdButton size="sm" variant="outline" @click="resetDirectoryFilters">
            清除筛选
          </YdButton>
        </YdEmpty>
      </DemoCard>

      <!-- ================= Agent 画廊 ================= -->
      <DemoCard v-else-if="scene.key === 'agent-gallery'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar">
          <YdInput v-model="agentGalleryKeyword" clearable placeholder="搜索 Agent" aria-label="搜索 Agent">
            <template #start>
              <YdIcon name="morph:search" />
            </template>
          </YdInput>
          <YdTabs v-model="agentGalleryCategory" :list="agentGalleryTabs" class="sg-tabs-line" />
        </div>
        <div v-if="filteredGalleryAgents.length" class="sg-grid">
          <div v-for="agent in filteredGalleryAgents" :key="agent.id" class="sg-agentcard">
            <div class="sg-agentcard__head">
              <YdIcon :name="agent.icon" class="sg-agentcard__icon" />
              <div class="sg-agentcard__text">
                <p class="sg-agentcard__name">
                  {{ agent.name }}
                </p>
                <YdTag variant="secondary">
                  {{ agent.category }}
                </YdTag>
              </div>
              <YdSwitch :model-value="agent.enabled" :aria-label="`启用 ${agent.name}`" @update:model-value="toggleGalleryAgent(agent)" />
            </div>
            <p class="sg-agentcard__desc">
              {{ agent.description }}
            </p>
            <div class="sg-agentcard__foot">
              <span class="sg-hint">累计运行 {{ agent.runs }} 次</span>
              <YdButton size="sm" :disabled="!agent.enabled || agent.running" @click="runGalleryAgent(agent)">
                <YdIcon :name="agent.running ? 'morph:loader' : 'morph:play'" />
                {{ agent.running ? '运行中…' : '运行' }}
              </YdButton>
            </div>
          </div>
        </div>
        <YdEmpty
          v-else
          title="没有匹配的 Agent"
          description="换个关键字或分类试试。"
        />
      </DemoCard>

      <!-- ================= AI 工作台 ================= -->
      <DemoCard v-else-if="scene.key === 'ai-workspace'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <YdAlert icon="morph:circle-info" title="本地演示" description="项目侧栏与会话为页面内模拟，不连接模型服务；会话按项目隔离。" />
        <div class="sg-aiws">
          <aside class="sg-aiws__side">
            <p class="sg-caption">
              项目
            </p>
            <YdAction
              v-for="project in aiwsProjects"
              :key="project.id"
              type="button"
              class="sg-aiws__project"
              :class="{ 'is-active': activeAiwsProjectId === project.id }"
              :aria-current="activeAiwsProjectId === project.id ? 'true' : undefined"
              @click="activeAiwsProjectId = project.id"
            >
              <span class="sg-aiws__project-main">
                <span class="sg-aiws__project-name">{{ project.name }}</span>
                <span class="sg-aiws__project-desc">{{ project.description }}</span>
              </span>
              <YdTag :variant="aiwsStatusVariant(project.status)">
                {{ project.status }}
              </YdTag>
            </YdAction>
            <YdButton size="sm" variant="outline" @click="toast('新建项目（本地演示）')">
              <YdIcon name="morph:plus" />
              新建项目
            </YdButton>
          </aside>
          <div class="sg-aiws__main">
            <div class="sg-aiws__head">
              <YdAvatar src="" :fallback="activeAiwsProject.ownerFallback" />
              <div class="sg-aiws__title">
                <p class="sg-strong">
                  {{ activeAiwsProject.name }}
                </p>
                <p class="sg-hint">
                  {{ activeAiwsProject.owner }} · {{ activeAiwsProject.docs }} 份资料
                </p>
              </div>
              <YdButton size="sm" variant="ghost" :disabled="!activeAiwsThread.length" @click="clearAiwsThread">
                清空会话
              </YdButton>
            </div>
            <div v-if="activeAiwsThread.length" class="sg-aiws__messages">
              <YdChatMessageList :messages="activeAiwsThread" />
            </div>
            <YdEmpty
              v-else
              class="sg-aiws__empty"
              title="开始本项目对话"
              description="会话按项目隔离，切换项目后互不影响。"
            />
            <YdChatSender :loading="aiwsSending" constrained @send="sendAiws" @stop="aiwsSending = false" />
          </div>
        </div>
      </DemoCard>

      <!-- ================= 指标仪表盘 ================= -->
      <DemoCard v-else-if="scene.key === 'metrics'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar">
          <YdSelect v-model="metricPeriod" :options="metricPeriodOptions" aria-label="统计周期" class="sg-toolbar__field" />
          <YdButton size="sm" variant="outline" @click="refreshMetrics">
            <YdIcon name="morph:refresh-cw" />
            刷新
          </YdButton>
        </div>
        <div class="sg-kpi">
          <div v-for="metric in kpiMetrics" :key="metric.id" class="sg-kpi__tile">
            <p class="sg-kpi__label">
              {{ metric.label }}
            </p>
            <p class="sg-kpi__value">
              {{ metric.value.toLocaleString() }}<span class="sg-kpi__unit">{{ metric.unit }}</span>
            </p>
            <div class="sg-kpi__foot">
              <YdTrend :value="`${Math.abs(metric.delta)}%`" :type="metricTrendType(metric.delta)" />
              <YdSparkline :data="metric.spark" :label="`${metric.label}趋势`" />
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 客户上下文 ================= -->
      <DemoCard v-else-if="scene.key === 'client-context'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-two-col">
          <div class="sg-stack">
            <div class="sg-client">
              <YdAvatar src="" :fallback="clientContext.fallback" class="sg-client__avatar" />
              <div>
                <p class="sg-client__name">
                  {{ clientContext.name }}
                  <YdTag v-if="clientContext.vip" variant="default">
                    VIP
                  </YdTag>
                </p>
                <p class="sg-client__company">
                  {{ clientContext.company }}
                </p>
              </div>
            </div>
            <YdDescriptions :items="clientContext.descriptions" :column="2" border size="sm" />
            <div class="sg-row-actions">
              <YdTag v-for="tag in clientContext.tags" :key="tag" variant="secondary">
                {{ tag }}
              </YdTag>
            </div>
            <div class="sg-row-actions">
              <YdButton size="sm" @click="toast('写邮件（本地演示）')">
                <YdIcon name="morph:mail" />
                写邮件
              </YdButton>
              <YdButton size="sm" variant="outline" @click="toast('记录跟进（本地演示）')">
                记录跟进
              </YdButton>
            </div>
          </div>
          <div class="sg-stack">
            <p class="sg-caption">
              最近动态
            </p>
            <YdTimeline :data="clientContext.timeline" />
          </div>
        </div>
      </DemoCard>

      <!-- ================= 发票与贷项通知单 ================= -->
      <DemoCard v-else-if="scene.key === 'invoice-credit-note'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar sg-toolbar--tight">
          <YdTabs v-model="billingDocView" :list="billingDocTabs" />
        </div>

        <!-- 发票视图 -->
        <template v-if="billingDocView === 'invoice'">
          <div class="sg-toolbar">
            <YdSelect v-model="selectedInvoiceId" :options="invoiceOptions" aria-label="选择发票" class="sg-toolbar__field" />
            <YdCopyButton :text="currentInvoice.id" label="复制发票号" />
            <YdButton size="sm" variant="outline" @click="toast('本地演示：未生成真实文件')">
              <YdIcon name="morph:download" />
              下载 PDF
            </YdButton>
          </div>
          <div class="sg-invoice">
            <div class="sg-invoice__head">
              <div>
                <p class="sg-invoice__issuer">
                  <YdIcon name="morph:layers" />
                  雨梦科技
                </p>
                <p class="sg-invoice__meta">
                  电子发票（普通发票）
                </p>
              </div>
              <div class="sg-invoice__no">
                <span class="sg-strong">{{ currentInvoice.id }}</span>
                <YdTag :variant="currentInvoice.paid ? 'secondary' : 'default'">
                  {{ currentInvoice.paid ? '已支付' : '待支付' }}
                </YdTag>
              </div>
            </div>
            <div class="sg-invoice__meta-row">
              <span>开票日期：{{ currentInvoice.issued }}</span>
              <span>付款期限：{{ currentInvoice.due }}</span>
            </div>
            <table class="sg-invoice__table">
              <thead>
                <tr>
                  <th>项目</th>
                  <th class="is-num">
                    数量
                  </th>
                  <th class="is-num">
                    单价
                  </th>
                  <th class="is-num">
                    小计
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in currentInvoice.items" :key="item.name">
                  <td>{{ item.name }}</td>
                  <td class="is-num">
                    {{ item.qty }}
                  </td>
                  <td class="is-num">
                    {{ money(item.price) }}
                  </td>
                  <td class="is-num">
                    {{ money(item.qty * item.price) }}
                  </td>
                </tr>
              </tbody>
            </table>
            <div class="sg-invoice__totals">
              <p>
                <span>小计</span>
                <span>{{ money(invoiceSubtotal) }}</span>
              </p>
              <p>
                <span>税（6%）</span>
                <span>{{ money(invoiceTax) }}</span>
              </p>
              <p class="sg-invoice__grand">
                <span>合计</span>
                <span>{{ money(invoiceTotal) }}</span>
              </p>
            </div>
          </div>
        </template>

        <!-- 贷项通知单视图 -->
        <template v-else>
          <div class="sg-toolbar">
            <YdSelect v-model="selectedCreditNoteId" :options="creditNoteOptions" aria-label="选择贷项通知单" class="sg-toolbar__field" />
            <YdCopyButton :text="currentCreditNote.originalInvoice" :label="`复制原发票号 ${currentCreditNote.originalInvoice}`" />
          </div>
          <div class="sg-credit">
            <div class="sg-credit__body">
              <div class="sg-invoice__head">
                <div>
                  <p class="sg-invoice__issuer">
                    <YdIcon name="morph:rotate-ccw" />
                    贷项通知单
                  </p>
                  <p class="sg-invoice__meta">
                    对应原发票 {{ currentCreditNote.originalInvoice }}
                  </p>
                </div>
                <div class="sg-invoice__no">
                  <span class="sg-strong">{{ currentCreditNote.id }}</span>
                  <YdTag :variant="currentCreditNote.settled ? 'secondary' : 'default'">
                    {{ currentCreditNote.settled ? '已冲抵' : '待确认' }}
                  </YdTag>
                </div>
              </div>
              <div class="sg-invoice__meta-row">
                <span>开单日期：{{ currentCreditNote.issued }}</span>
                <span>冲销原因：{{ currentCreditNote.reason }}</span>
                <span>退款方式：{{ currentCreditNote.method }}</span>
              </div>
              <table class="sg-invoice__table">
                <thead>
                  <tr>
                    <th>冲抵项目</th>
                    <th class="is-num">
                      数量
                    </th>
                    <th class="is-num">
                      单价
                    </th>
                    <th class="is-num">
                      小计
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="item in currentCreditNote.items" :key="item.name">
                    <td>{{ item.name }}</td>
                    <td class="is-num">
                      {{ item.qty }}
                    </td>
                    <td class="is-num">
                      {{ money(item.price) }}
                    </td>
                    <td class="is-num">
                      {{ money(item.qty * item.price) }}
                    </td>
                  </tr>
                </tbody>
              </table>
              <div class="sg-invoice__totals">
                <p class="sg-invoice__grand">
                  <span>应退金额</span>
                  <span>{{ money(creditNoteTotal) }}</span>
                </p>
              </div>
              <div class="sg-row-actions">
                <YdPopconfirm
                  title="确认冲抵该通知单？"
                  :description="`将按${currentCreditNote.method}退款 ${money(creditNoteTotal)}。`"
                  confirm-text="确认冲抵"
                  cancel-text="再想想"
                  @confirm="settleCreditNote"
                >
                  <YdButton size="sm" :disabled="currentCreditNote.settled">
                    同意冲抵
                  </YdButton>
                </YdPopconfirm>
                <YdButton size="sm" variant="outline" @click="toast('本地演示：未生成真实文件')">
                  <YdIcon name="morph:download" />
                  下载 PDF
                </YdButton>
              </div>
            </div>
            <div class="sg-credit__aside">
              <YdQrcode :value="`yd-credit:${currentCreditNote.id}:${creditNoteTotal}`" :size="112" label="核销二维码" />
              <p class="sg-hint">
                扫码核验通知单（本地演示）
              </p>
            </div>
          </div>
        </template>
      </DemoCard>

      <!-- ================= 客户 Logo 墙 ================= -->
      <DemoCard v-else-if="scene.key === 'logo-marquee'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar sg-toolbar--tight">
          <YdSegmented v-model="logoMarqueePace" :options="logoMarqueePaceOptions" aria-label="滚动速度" />
          <div class="sg-toggle">
            <p class="sg-setting__title">
              反向滚动
            </p>
            <YdSwitch v-model="logoMarqueeReverse" aria-label="反向滚动" />
          </div>
          <div class="sg-toggle">
            <p class="sg-setting__title">
              悬停暂停
            </p>
            <YdSwitch v-model="logoMarqueePauseOnHover" aria-label="悬停暂停" />
          </div>
        </div>
        <div class="sg-logo-marquee">
          <YdMarquee :duration="logoMarqueeDuration" :reverse="logoMarqueeReverse" :pause-on-hover="logoMarqueePauseOnHover" gap="12px">
            <span v-for="brand in logoBrands" :key="brand.id" class="sg-logo">
              <YdIcon :name="brand.icon" />
              {{ brand.name }}
            </span>
          </YdMarquee>
          <YdMarquee :duration="logoMarqueeDuration" :reverse="!logoMarqueeReverse" :pause-on-hover="logoMarqueePauseOnHover" gap="12px">
            <span v-for="brand in [...logoBrands].reverse()" :key="brand.id" class="sg-logo sg-logo--muted">
              <YdIcon :name="brand.icon" />
              {{ brand.name }}
            </span>
          </YdMarquee>
        </div>
        <p class="sg-hint">
          客户名称与图标均为本地演示数据；悬停可暂停，切换反向后两行保持相对滚动。
        </p>
      </DemoCard>

      <!-- ================= 用户评价墙 ================= -->
      <DemoCard v-else-if="scene.key === 'testimonial-wall'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar sg-toolbar--tight">
          <YdTabs v-model="testimonialTier" :list="testimonialTierTabs" />
          <YdButton size="sm" variant="outline" @click="shuffleTestimonials">
            <YdIcon name="morph:refresh-cw" />
            换一批
          </YdButton>
          <span class="sg-hint">{{ filteredTestimonials.length }} 条评价</span>
        </div>
        <div class="sg-grid">
          <div v-for="testimonial in filteredTestimonials" :key="testimonial.id" class="sg-quote">
            <YdIcon name="morph:quote" class="sg-quote__mark" />
            <p class="sg-quote__text">
              {{ testimonial.quote }}
            </p>
            <YdRate :model-value="testimonial.rating" readonly :aria-label="`${testimonial.name}的评分：${testimonial.rating} 星`" />
            <div class="sg-quote__foot">
              <YdAvatar src="" :fallback="testimonial.fallback" />
              <div class="sg-quote__who">
                <p class="sg-quote__name">
                  {{ testimonial.name }}
                </p>
                <p class="sg-quote__company">
                  {{ testimonial.company }}
                </p>
              </div>
              <YdTag variant="outline">
                {{ testimonial.source }}
              </YdTag>
            </div>
          </div>
        </div>
        <p v-if="!filteredTestimonials.length" class="sg-hint">
          该评分档暂无评价
        </p>
      </DemoCard>

      <!-- ================= 团队 KPI 看板 ================= -->
      <DemoCard v-else-if="scene.key === 'kpi-board'" :title="scene.title" :description="scene.description" :kind="scene.group === 'screens' ? '整页' : '区块'">
        <div class="sg-toolbar sg-toolbar--tight">
          <YdSegmented v-model="kpiBoardPeriod" :options="kpiBoardPeriodTabs" aria-label="统计周期" />
          <span class="sg-hint">口径：签约回款</span>
        </div>
        <YdStats>
          <YdStat title="签约回款">
            <template #figure>
              <YdIcon name="morph:circle-check" />
            </template>
            <template #value>
              <YdCountTo :key="`revenue-${kpiBoardPeriod}`" :end-val="currentKpiBoard.revenue" prefix="¥" separator="," />
            </template>
            <template #description>
              较上期 +12.6%
            </template>
          </YdStat>
          <YdStat title="新增客户">
            <template #figure>
              <YdIcon name="morph:user-plus" />
            </template>
            <template #value>
              <YdCountTo :key="`customers-${kpiBoardPeriod}`" :end-val="currentKpiBoard.newCustomers" />
            </template>
            <template #description>
              含试用转正
            </template>
          </YdStat>
          <YdStat title="客户留存率">
            <template #figure>
              <YdIcon name="morph:refresh-cw" />
            </template>
            <template #value>
              <YdCountTo :key="`retention-${kpiBoardPeriod}`" :end-val="currentKpiBoard.retention" :decimals="1" suffix="%" />
            </template>
            <template #description>
              按月滚动口径
            </template>
          </YdStat>
          <YdStat title="平均响应" :value="currentKpiBoard.avgResponse">
            <template #figure>
              <YdIcon name="morph:bell" />
            </template>
            <template #description>
              工单首个回复耗时
            </template>
          </YdStat>
        </YdStats>
        <div class="sg-stack">
          <p class="sg-caption">
            小组目标完成
          </p>
          <div v-for="team in currentKpiBoard.teams" :key="team.id" class="sg-kpi-team">
            <YdAvatar src="" :fallback="team.ownerFallback" />
            <div class="sg-kpi-team__meta">
              <p class="sg-kpi-team__name">
                {{ team.name }} · {{ team.owner }}
              </p>
              <YdProgress :model-value="kpiTeamPercent(team)" />
            </div>
            <span class="sg-kpi-team__figure">¥{{ team.done.toLocaleString() }} / ¥{{ team.target.toLocaleString() }}</span>
            <YdTag :variant="kpiTeamMet(team) ? 'secondary' : 'default'">
              {{ kpiTeamMet(team) ? '已达标' : '推进中' }}
            </YdTag>
          </div>
        </div>
      </DemoCard>
    </template>
  </div>
</template>

<style scoped>
.sg {
  display: grid;
  gap: var(--yd-content-gap, 16px);
}

/* 画廊头部 */
.sg-head {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
}

.sg-head__text h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: var(--color-text-1);
}

.sg-head__text p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.sg-head__tools {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.sg-head__tools > :first-child {
  width: 260px;
}

.sg-count {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 通用布局 */
.sg-stack {
  display: grid;
  gap: 12px;
}

.sg-caption {
  margin: 0;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
}

.sg-hint {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

.sg-strong {
  font-weight: 600;
  color: var(--color-text-1);
}

.sg-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.sg-toolbar > :first-child {
  flex: 1;
  min-width: 180px;
  max-width: 320px;
}

.sg-toolbar__field {
  width: 148px;
}

.sg-row-actions {
  display: inline-flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
}

.sg-tabs-line {
  max-width: 480px;
}

/* 通用列表行 */
.sg-list {
  display: grid;
  gap: 4px;
}

.sg-list__row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  min-height: 40px;
  padding: 6px 10px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.sg-list__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sg-list__meta {
  font-size: 12px;
  color: var(--color-text-3);
}

.sg-list__id {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 11px;
  color: var(--color-text-4, var(--color-text-3));
}

/* 场景：应用工作台 */
.sg-shell {
  display: grid;
  grid-template-columns: 168px 1fr;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-shell__nav {
  display: grid;
  gap: 2px;
  align-content: start;
  padding: 8px;
  background: var(--color-bg-2);
  border-right: 1px solid var(--color-border-2);
}

.sg-shell__item {
  display: flex;
  gap: 8px;
  align-items: center;
  width: 100%;
  min-height: 34px;
  padding: 6px 10px;
  font: inherit;
  font-size: 13.5px;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yd-control-radius, 8px);
  transition: background 0.15s ease, color 0.15s ease;
}

.sg-shell__item:hover {
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

.sg-shell__item.is-active {
  font-weight: 600;
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.sg-shell__main {
  display: grid;
  gap: 14px;
  align-content: start;
  min-width: 0;
  padding: 14px;
}

.sg-shell__topbar {
  display: flex;
  gap: 10px;
  align-items: center;
}

.sg-shell__topbar > :first-child {
  flex: 1;
  max-width: 320px;
}

/* KPI 与指标 */
.sg-kpi {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.sg-kpi__tile {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-kpi__label {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.sg-kpi__value {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  letter-spacing: -0.01em;
  color: var(--color-text-1);
}

.sg-kpi__unit {
  margin-left: 3px;
  font-size: 12px;
  font-weight: 400;
  color: var(--color-text-3);
}

.sg-kpi__note {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.sg-kpi__foot {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.sg-kpi__foot > * {
  min-width: 0;
}

.sg-subcard {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

/* 场景：助手对话 */
.sg-chat {
  display: grid;
  grid-template-columns: 208px 1fr;
  height: 460px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-chat__sessions {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 10px;
  background: var(--color-bg-2);
  border-right: 1px solid var(--color-border-2);
  overflow-y: auto;
}

.sg-chat__session {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-height: 34px;
  padding: 6px 10px;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yd-control-radius, 8px);
}

.sg-chat__session:hover {
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

.sg-chat__session.is-active {
  font-weight: 600;
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.sg-chat__count {
  flex-shrink: 0;
  min-width: 20px;
  padding: 0 6px;
  font-size: 11px;
  line-height: 18px;
  color: var(--color-text-2);
  text-align: center;
  background: var(--color-fill-2);
  border-radius: 999px;
}

.sg-chat__main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
  padding: 12px;
  overflow-y: auto;
}

.sg-chat__messages {
  flex: 1;
  min-height: 0;
}

/* 场景：文件 */
.sg-file {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

.sg-file__star {
  color: var(--color-text-2);
}

.sg-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.sg-filecard {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-filecard__icon {
  font-size: 22px;
  color: var(--color-text-2);
}

.sg-filecard__name {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
  word-break: break-all;
}

.sg-filecard__meta {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 场景：集成 */
.sg-integration {
  display: grid;
  gap: 10px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-integration__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.sg-integration__icon {
  flex-shrink: 0;
  font-size: 20px;
  color: var(--color-text-2);
}

.sg-integration__text {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.sg-integration__name {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
}

.sg-integration__desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

/* 场景：CRM */
.sg-owner {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}

/* 场景：Agent 运行 */
.sg-agent {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
}

.sg-tool {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-tool__head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.sg-tool__detail {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

/* 场景：设置与账单 */
.sg-two-col {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.sg-setting {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--color-border-2);
}

.sg-setting__title {
  margin: 0;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text-1);
}

.sg-setting__desc {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.sg-plan {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.sg-plan__name {
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--color-text-1);
}

.sg-plan__price {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

/* 场景：新手引导与评估 */
.sg-eval {
  display: grid;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-eval__meter {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
}

.sg-eval__rate {
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

/* 区块：任务看板 */
.sg-columns {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.sg-col {
  display: grid;
  gap: 8px;
  align-content: start;
  min-height: 120px;
  padding: 10px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-col__title {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0 0 2px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-2);
}

.sg-col__count {
  min-width: 20px;
  padding: 0 6px;
  font-size: 11px;
  font-weight: 500;
  line-height: 18px;
  color: var(--color-text-2);
  text-align: center;
  background: var(--color-fill-2);
  border-radius: 999px;
}

.sg-task {
  display: grid;
  gap: 10px;
  padding: 10px 12px;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.sg-task__head {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.sg-task__title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.sg-task__foot {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

/* 区块：定价 */
.sg-pricing-head {
  display: flex;
  justify-content: center;
}

.sg-pricing {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  align-items: stretch;
}

.sg-pricecard {
  display: grid;
  gap: 8px;
  align-content: start;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-pricecard.is-highlight {
  border-color: var(--color-text-3);
  box-shadow: 0 2px 10px rgb(0 0 0 / 6%);
}

.sg-pricecard__name {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 14px;
  font-weight: 650;
  color: var(--color-text-1);
}

.sg-pricecard__price {
  margin: 0;
  font-size: 24px;
  font-weight: 650;
  color: var(--color-text-1);
}

.sg-pricecard__note {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.sg-pricecard__features {
  display: grid;
  gap: 6px;
  padding: 0;
  margin: 4px 0 0;
  list-style: none;
}

.sg-pricecard__features li {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--color-text-2);
}

.sg-pricecard__cta {
  margin-top: 6px;
}

/* 区块：应用目录 */
.sg-app {
  display: grid;
  gap: 10px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-app__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.sg-app__icon {
  flex-shrink: 0;
  font-size: 20px;
  color: var(--color-text-2);
}

.sg-app__name {
  margin: 0 0 4px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
}

.sg-app__desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

/* 区块：客户上下文 */
.sg-client {
  display: flex;
  gap: 12px;
  align-items: center;
}

.sg-client__avatar {
  width: 44px;
  height: 44px;
  font-size: 16px;
}

.sg-client__name {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--color-text-1);
}

.sg-client__company {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

/* 区块：发票预览 */
.sg-invoice {
  display: grid;
  gap: 12px;
  max-width: 560px;
  padding: 18px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-invoice__head {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  justify-content: space-between;
}

.sg-invoice__issuer {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 0;
  font-size: 15px;
  font-weight: 650;
  color: var(--color-text-1);
}

.sg-invoice__meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.sg-invoice__no {
  display: flex;
  gap: 8px;
  align-items: center;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 13px;
}

.sg-invoice__meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 18px;
  font-size: 12.5px;
  color: var(--color-text-3);
}

.sg-invoice__table {
  width: 100%;
  font-size: 13px;
  border-collapse: collapse;
}

.sg-invoice__table th {
  padding: 6px 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
  text-align: left;
  border-bottom: 1px solid var(--color-border-2);
}

.sg-invoice__table td {
  padding: 8px 4px;
  color: var(--color-text-1);
  border-bottom: 1px solid var(--color-border-2);
}

.sg-invoice__table .is-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.sg-invoice__totals {
  display: grid;
  gap: 4px;
  margin-left: auto;
  max-width: 240px;
  width: 100%;
}

.sg-invoice__totals p {
  display: flex;
  justify-content: space-between;
  margin: 0;
  font-size: 13px;
  color: var(--color-text-2);
}

.sg-invoice__totals p.sg-invoice__grand {
  padding-top: 6px;
  font-size: 15px;
  font-weight: 650;
  color: var(--color-text-1);
  border-top: 1px solid var(--color-border-2);
}

/* 场景：文件夹详情 */
.sg-fd__crumb {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  align-items: center;
}

.sg-fd__crumb-sep {
  flex-shrink: 0;
  font-size: 14px;
  color: var(--color-text-4, var(--color-text-3));
}

.sg-fd__crumb-item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-height: 28px;
  padding: 4px 8px;
  font: inherit;
  font-size: 13px;
  color: var(--color-text-2);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yd-control-radius, 8px);
}

.sg-fd__crumb-item:hover {
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

.sg-fd__crumb-item.is-current {
  font-weight: 600;
  color: var(--color-text-1);
}

.sg-fd__list {
  display: grid;
  gap: 4px;
}

.sg-fd__row {
  display: flex;
  gap: 10px;
  align-items: center;
  min-height: 40px;
  padding: 6px 10px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.sg-fd__row-icon {
  flex-shrink: 0;
  font-size: 18px;
  color: var(--color-text-2);
}

.sg-fd__row-name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  color: var(--color-text-1);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

button.sg-fd__row-name {
  font: inherit;
  font-size: 13.5px;
  font-weight: 500;
  cursor: pointer;
  background: transparent;
  border: 0;
}

button.sg-fd__row-name:hover {
  text-decoration: underline;
}

.sg-fd__row-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 区块：Agent 画廊 */
.sg-agentcard {
  display: grid;
  gap: 8px;
  align-content: start;
  padding: 14px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-agentcard__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.sg-agentcard__icon {
  flex-shrink: 0;
  font-size: 20px;
  color: var(--color-text-2);
}

.sg-agentcard__text {
  display: grid;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.sg-agentcard__name {
  margin: 0;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
}

.sg-agentcard__desc {
  margin: 0;
  font-size: 12.5px;
  color: var(--color-text-3);
}

.sg-agentcard__foot {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  margin-top: 2px;
}

/* 区块：AI 工作台 */
.sg-aiws {
  display: grid;
  grid-template-columns: 232px 1fr;
  height: 420px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-aiws__side {
  display: grid;
  gap: 6px;
  align-content: start;
  padding: 10px;
  background: var(--color-bg-2);
  border-right: 1px solid var(--color-border-2);
  overflow-y: auto;
}

.sg-aiws__project {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 8px 10px;
  font: inherit;
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yd-control-radius, 8px);
  transition: background 0.15s ease;
}

.sg-aiws__project:hover {
  background: var(--color-fill-1);
}

.sg-aiws__project.is-active {
  background: var(--color-fill-2);
}

.sg-aiws__project-main {
  display: grid;
  gap: 2px;
  flex: 1;
  min-width: 0;
}

.sg-aiws__project-name {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sg-aiws__project-desc {
  overflow: hidden;
  font-size: 11.5px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sg-aiws__main {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  padding: 12px;
}

.sg-aiws__head {
  display: flex;
  flex-shrink: 0;
  gap: 10px;
  align-items: center;
}

.sg-aiws__title {
  flex: 1;
  min-width: 0;
}

.sg-aiws__messages {
  display: grid;
  flex: 1;
  gap: 10px;
  align-content: start;
  min-height: 0;
  padding: 10px;
  overflow-y: auto;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-aiws__empty {
  flex: 1;
  align-content: center;
}

/* 场景：Agent 对话台 */
.sg-agent-chat {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 16px;
}

.sg-agent-chat__main {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}

.sg-agent-chat__messages {
  display: grid;
  gap: 10px;
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow-y: auto;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

/* 场景：模型评估台 */
.sg-eval-cases {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.sg-eval-case {
  max-width: 100%;
}

/* 区块：贷项通知单 */
.sg-credit {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: start;
  max-width: 640px;
  padding: 18px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-credit__body {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.sg-credit__aside {
  display: grid;
  gap: 6px;
  justify-items: center;
  max-width: 140px;
  text-align: center;
}

/* 区块：客户 Logo 墙 */
.sg-toolbar--tight > :first-child {
  flex: 0 0 auto;
  min-width: 0;
  max-width: none;
}

.sg-toggle {
  display: flex;
  gap: 10px;
  align-items: center;
}

.sg-logo-marquee {
  display: grid;
  gap: 10px;
  padding: 14px 0;
  overflow: hidden;
  border-block: 1px solid var(--color-border-2);
}

.sg-logo {
  display: inline-flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  padding: 8px 16px;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
}

.sg-logo--muted {
  font-weight: 500;
  color: var(--color-text-2);
}

/* 区块：用户评价墙 */
.sg-quote {
  display: grid;
  gap: 8px;
  align-content: start;
  padding: 16px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-quote__mark {
  font-size: 20px;
  color: var(--color-text-4, var(--color-text-3));
}

.sg-quote__text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--color-text-1);
}

.sg-quote__foot {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-top: 4px;
}

.sg-quote__who {
  flex: 1;
  min-width: 0;
}

.sg-quote__name {
  margin: 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-1);
}

.sg-quote__company {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 区块：团队 KPI 看板 */
.sg-kpi-team {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.sg-kpi-team__meta {
  display: grid;
  gap: 6px;
  flex: 1;
  min-width: 0;
}

.sg-kpi-team__name {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.sg-kpi-team__figure {
  flex-shrink: 0;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
}

/* 响应式 */
@media (width <= 768px) {
  .sg-head {
    flex-direction: column;
    align-items: stretch;
  }

  .sg-head__tools > :first-child {
    width: 100%;
  }

  .sg-shell,
  .sg-chat,
  .sg-agent,
  .sg-two-col,
  .sg-agent-chat,
  .sg-aiws {
    grid-template-columns: 1fr;
  }

  .sg-credit {
    grid-template-columns: 1fr;
  }

  .sg-credit__aside {
    justify-items: start;
    max-width: none;
    text-align: left;
  }

  .sg-shell__nav {
    grid-auto-flow: column;
    justify-content: start;
    overflow-x: auto;
    border-right: 0;
    border-bottom: 1px solid var(--color-border-2);
  }

  .sg-shell__item {
    width: auto;
    white-space: nowrap;
  }

  .sg-chat {
    height: auto;
  }

  .sg-aiws {
    height: auto;
  }

  .sg-aiws__side {
    border-right: 0;
    border-bottom: 1px solid var(--color-border-2);
  }

  .sg-aiws__messages {
    max-height: 320px;
  }

  .sg-chat__main {
    overflow-y: visible;
  }

  .sg-chat__messages {
    max-height: 320px;
    overflow-y: auto;
  }

  .sg-kpi {
    grid-template-columns: repeat(2, 1fr);
  }

  .sg-columns,
  .sg-pricing {
    grid-template-columns: 1fr;
  }

  .sg-invoice {
    max-width: none;
  }
}

@media (width <= 480px) {
  .sg-kpi {
    grid-template-columns: 1fr;
  }
}
</style>
