<script setup lang="ts">
import {
  useYdToast,
  YdAction,
  YdAlert,
  YdAvatar,
  YdButton,
  YdCheckbox,
  YdCollapsible,
  YdCopyButton,
  YdEmpty,
  YdIcon,
  YdInput,
  YdKbd,
  YdKbdGroup,
  YdNumberField,
  YdPopconfirm,
  YdProgress,
  YdSegmented,
  YdSelect,
  YdSlider,
  YdSwitch,
  YdTag,
  YdTextarea,
  YdTooltip,
  YdTrend,
} from '@yudream/components'
import { computed, ref, watch } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

/**
 * AffectUI 风格「卡片 / 数据展示 / 按钮」区块的本地交互演示。
 * 全部数据为组件内本地状态，不发起网络请求；交互控件一律来自 @yudream/components 公开 Yd* 入口。
 * 轻提示依赖展示壳根部的全局 YdToast 组件（component-showcase 的 App.vue 已挂载）。
 * 颜色仅消费中性语义变量与共享 YD 外观令牌（--yd-emphasis / --yd-status-*）。
 */

const toast = useYdToast()

// ============================================================
// 演示目录与分组筛选
// ============================================================

type DemoGroup = 'cards' | 'data' | 'buttons'

interface DemoMeta {
  key: string
  title: string
  description: string
  group: DemoGroup
}

const demoCatalog: DemoMeta[] = [
  { key: 'cover-flow', title: '封面流 · Cover Flow', description: '拖动滑动切换封面卡片，支持按钮与键盘导航。', group: 'cards' },
  { key: 'project-card', title: '项目卡片 · Project Card', description: '子任务勾选驱动完成环形图，状态可推进。', group: 'cards' },
  { key: 'tips-panel', title: '提示面板 · Tips Panel', description: '提示逐条完成、忽略与恢复，全部本地状态。', group: 'cards' },
  { key: 'recommendation', title: '智能推荐 · Smart Recommendation', description: '候选推荐切换、采用与不感兴趣，操作可撤销。', group: 'cards' },
  { key: 'email-draft', title: '邮件草稿 · Email Draft', description: '收件人校验、语气切换重写正文，发送与丢弃可撤销。', group: 'cards' },
  { key: 'usage-meter', title: '用量计量 · Usage Meter', description: '套餐配额与 70% / 90% 阈值联动，状态随之变化。', group: 'data' },
  { key: 'compact-breakdown', title: '紧凑分解 · Compact Breakdown', description: '分类工时占比与环比，切换统计周期。', group: 'data' },
  { key: 'experiment-card', title: '实验卡片 · Experiment Card', description: 'A/B 样本累计、转化率对比与获胜判定。', group: 'data' },
  { key: 'kpi-chart', title: 'KPI 柱状图 · KPI Chart', description: '悬停或按钮聚焦单期数据，可切换指标。', group: 'data' },
  { key: 'breakdown-chart', title: '构成分解图 · Breakdown Chart', description: '堆叠条与图例联动，悬停或固定查看分段。', group: 'data' },
  { key: 'data-table', title: '可编辑数据表 · Data Table', description: '选择、排序、行内编辑、复制粘贴与撤销。', group: 'data' },
  { key: 'change-summary', title: '变更摘要 · Change Summary', description: '逐条保留或放弃变更并汇总增删行数。', group: 'data' },
  { key: 'button-playground', title: '按钮 · Button', description: '变体、尺寸与状态实时配置的按钮试验台。', group: 'buttons' },
  { key: 'reaction-bar', title: '回应条 · Reaction Bar', description: '可切换的表情回应与人数汇总，再次点击即取消。', group: 'buttons' },
]

const filterOptions: { label: string, value: 'all' | DemoGroup }[] = [
  { label: '全部', value: 'all' },
  { label: '卡片', value: 'cards' },
  { label: '数据展示', value: 'data' },
  { label: '按钮', value: 'buttons' },
]
const activeFilter = ref<'all' | DemoGroup>('all')
const visibleDemos = computed(() =>
  demoCatalog.filter(demo => activeFilter.value === 'all' || demo.group === activeFilter.value),
)

function demoKind(group: DemoGroup) {
  if (group === 'cards') {
    return '卡片'
  }
  return group === 'data' ? '数据展示' : '按钮'
}

function formatDelta(value: number) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(1)}%`
}

function percentDelta(current: number, previous: number) {
  if (!previous) {
    return 0
  }
  return (current - previous) / previous * 100
}

// ============================================================
// 卡片：封面流（Cover Flow）——拖动 / 滑动 / 键盘切换
// ============================================================

interface CoverItem {
  id: string
  title: string
  subtitle: string
  icon: string
  meta: string
}

const covers: CoverItem[] = [
  { id: 'cf-01', title: '品牌视觉手册', subtitle: '2026 春季版', icon: 'morph:palette', meta: '12 页 · 上周更新' },
  { id: 'cf-02', title: '产品发布会', subtitle: '主视觉提案', icon: 'morph:rocket', meta: '8 页 · 评审中' },
  { id: 'cf-03', title: '增长复盘', subtitle: 'Q3 数据速览', icon: 'morph:chart-line', meta: '16 页 · 已归档' },
  { id: 'cf-04', title: '设计系统', subtitle: '组件与令牌总览', icon: 'morph:component', meta: '24 页 · 持续维护' },
  { id: 'cf-05', title: '用研报告', subtitle: '用户访谈摘要', icon: 'morph:microscope', meta: '10 页 · 内部' },
  { id: 'cf-06', title: '季度路演', subtitle: '董事会材料', icon: 'morph:presentation', meta: '14 页 · 机密' },
]

const COVER_STEP = 180 // 单卡 168px + 间距 12px
const activeCover = ref(2)
const coverDrag = ref(0)
const coverDragging = ref(false)
let coverStartX = 0
let coverPointerMoved = false

function clampCoverIndex(index: number) {
  return Math.min(covers.length - 1, Math.max(0, index))
}

const coverStripStyle = computed(() => {
  const offset = COVER_STEP * activeCover.value - coverDrag.value
  return {
    transform: `translateX(calc(50% - 84px - ${offset}px))`,
    transition: coverDragging.value ? 'none' : 'transform var(--yd-motion-duration-normal) var(--yd-motion-ease)',
  }
})

function coverDistance(index: number) {
  return Math.abs(index - activeCover.value)
}

function moveCover(delta: number) {
  activeCover.value = clampCoverIndex(activeCover.value + delta)
}

function onCoverPointerDown(event: PointerEvent) {
  coverDragging.value = true
  coverPointerMoved = false
  coverStartX = event.clientX
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onCoverPointerMove(event: PointerEvent) {
  if (!coverDragging.value) {
    return
  }
  const delta = event.clientX - coverStartX
  if (Math.abs(delta) > 6) {
    coverPointerMoved = true
  }
  coverDrag.value = delta
}

function settleCoverDrag() {
  if (!coverDragging.value) {
    return
  }
  const moved = -Math.round(coverDrag.value / COVER_STEP)
  if (moved !== 0) {
    activeCover.value = clampCoverIndex(activeCover.value + moved)
  }
  coverDragging.value = false
  coverDrag.value = 0
}

function selectCover(index: number, event?: MouseEvent) {
  // 键盘触发的 click（detail === 0）始终生效；拖动结束后的残留 click 忽略。
  if (event && event.detail > 0 && coverPointerMoved) {
    return
  }
  activeCover.value = index
}

// ============================================================
// 卡片：项目卡片（Project Card）——子任务环形进度与状态
// ============================================================

interface SubtaskItem {
  id: string
  title: string
  done: boolean
}

const projectSubtasks = ref<SubtaskItem[]>([
  { id: 'pt-01', title: '确认视觉方向', done: true },
  { id: 'pt-02', title: '完成首页高保真', done: true },
  { id: 'pt-03', title: '组件走查与标注', done: false },
  { id: 'pt-04', title: '交付开发联调', done: false },
  { id: 'pt-05', title: '验收与归档', done: false },
])

const projectStatuses = ['进行中', '评审中', '已发布'] as const
type ProjectStatus = typeof projectStatuses[number]
const projectStatus = ref<ProjectStatus>('进行中')

const projectDone = computed(() => projectSubtasks.value.filter(item => item.done).length)
const projectPercent = computed(() => Math.round(projectDone.value / projectSubtasks.value.length * 100))
const ringCircumference = 2 * Math.PI * 34
const ringDashOffset = computed(() => ringCircumference * (1 - projectPercent.value / 100))
const ringColor = computed(() => projectPercent.value >= 100 ? 'var(--yd-status-success)' : 'var(--yd-emphasis)')

const projectStatusVariant = computed(() => {
  if (projectStatus.value === '进行中') {
    return 'default' as const
  }
  return projectStatus.value === '评审中' ? 'outline' as const : 'secondary' as const
})

function advanceProjectStatus() {
  const next = (projectStatuses.indexOf(projectStatus.value) + 1) % projectStatuses.length
  projectStatus.value = projectStatuses[next]
  toast(`状态已推进为「${projectStatus.value}」（本地演示）`)
}

// ============================================================
// 卡片：提示面板（Tips Panel）——完成、忽略与恢复
// ============================================================

interface TipItem {
  id: string
  title: string
  detail: string
  done: boolean
}

const tipSeed: TipItem[] = [
  { id: 'tip-01', title: '绑定工作通知', detail: '在偏好设置中开启提醒，任务变化第一时间知道。', done: false },
  { id: 'tip-02', title: '邀请第一位协作者', detail: '项目需要至少两名成员才能开启评审流。', done: true },
  { id: 'tip-03', title: '建立常用视图', detail: '把高频筛选保存为视图，下次一键直达。', done: false },
  { id: 'tip-04', title: '配置快捷回复', detail: '为常见反馈准备两到三套模板。', done: false },
]
const tips = ref(tipSeed.map(tip => ({ ...tip })))
const dismissedTips = ref<TipItem[]>([])
const tipsDone = computed(() => tips.value.filter(tip => tip.done).length)
const tipsPercent = computed(() => tips.value.length ? Math.round(tipsDone.value / tips.value.length * 100) : 0)

function dismissTip(id: string) {
  const index = tips.value.findIndex(tip => tip.id === id)
  if (index < 0) {
    return
  }
  dismissedTips.value.push(tips.value[index])
  tips.value.splice(index, 1)
}

function undoDismissTip() {
  const tip = dismissedTips.value.pop()
  if (!tip) {
    toast('没有可恢复的提示')
    return
  }
  tips.value.push(tip)
}

function completeAllTips() {
  tips.value.forEach((tip) => {
    tip.done = true
  })
}

function resetTips() {
  tips.value = tipSeed.map(tip => ({ ...tip }))
  dismissedTips.value = []
}

// ============================================================
// 卡片：智能推荐（Smart Recommendation）——候选、采用与撤销
// ============================================================

interface RecommendationItem {
  id: string
  title: string
  reason: string
  score: number
  tag: string
}

const recommendations: RecommendationItem[] = [
  { id: 'rc-01', title: '北极星指标看板模板', reason: '与团队近两周关注的留存主题高度相关。', score: 96, tag: '模板' },
  { id: 'rc-02', title: '灰度发布检查清单', reason: 'v2.4 发布在即，上个季度复用率最高。', score: 91, tag: '清单' },
  { id: 'rc-03', title: '评审会议记录规范', reason: '本月评审缺席记录较多，可统一模板改善。', score: 84, tag: '规范' },
  { id: 'rc-04', title: '竞品定价快照', reason: '季度经营会临近，补一份定价对照。', score: 78, tag: '资料' },
]
const recommendationIndex = ref(0)
const recommendationHistory = ref<number[]>([])
const adoptedRecommendationId = ref<string | null>(null)
const currentRecommendation = computed(() => recommendations[recommendationIndex.value])

function switchRecommendation(next: number) {
  if (next === recommendationIndex.value) {
    return
  }
  recommendationHistory.value.push(recommendationIndex.value)
  recommendationIndex.value = next
  adoptedRecommendationId.value = null
}

function nextRecommendation() {
  switchRecommendation((recommendationIndex.value + 1) % recommendations.length)
}

function rejectRecommendation() {
  toast('已减少此类推荐（本地演示）')
  nextRecommendation()
}

function adoptRecommendation() {
  adoptedRecommendationId.value = currentRecommendation.value.id
  toast.success('已采用该推荐（本地演示）')
}

function undoRecommendation() {
  const previous = recommendationHistory.value.pop()
  if (previous === undefined) {
    toast('没有可撤销的推荐操作')
    return
  }
  recommendationIndex.value = previous
  adoptedRecommendationId.value = null
}

// ============================================================
// 卡片：邮件草稿（Email Draft）——语气重写、发送与丢弃可撤销
// ============================================================

type EmailTone = 'formal' | 'neutral' | 'friendly'

const toneOptions: { label: string, value: EmailTone }[] = [
  { label: '正式', value: 'formal' },
  { label: '中性', value: 'neutral' },
  { label: '亲切', value: 'friendly' },
]

interface EmailSnapshot {
  recipient: string
  subject: string
  body: string
}

const emailTone = ref<EmailTone>('neutral')
const emailRecipient = ref('linzhou@example.com')
const emailSubject = ref('确认 v2.4 发布安排')
const emailBody = ref(buildEmailBody('neutral', 'linzhou@example.com'))
const emailPhase = ref<'editing' | 'sent' | 'discarded'>('editing')
const emailSnapshot = ref<EmailSnapshot | null>(null)

function buildEmailBody(tone: EmailTone, recipient: string) {
  const name = recipient.split('@')[0] || '同学'
  if (tone === 'formal') {
    return `${name}，您好：\n\n现就 v2.4 版本发布计划与您确认：定于本周四 14:00 进行发布评审，评审通过后当晚执行灰度发布。\n\n请于本周三前反馈您对发布范围的意见，谢谢。\n\n此致\n敬礼`
  }
  if (tone === 'friendly') {
    return `Hi ${name}：\n\nv2.4 定在这周四 14:00 评审，通过后当晚开始灰度。\n\n发布范围没问题的话，周三前跟我说一声就行～`
  }
  return `${name}，你好：\n\n和你确认一下 v2.4 的发布安排：本周四 14:00 发布评审，通过后当晚开始灰度发布。\n\n如对范围有补充，请在周三前回复。`
}

watch(emailTone, (tone) => {
  // 演示的关键交互：切换语气会按模板重写正文，覆盖手动编辑内容。
  emailBody.value = buildEmailBody(tone, emailRecipient.value)
})

const emailRecipientValid = computed(() => /^[^\s@]+@[^\s@][^\s.@]*\.[^\s@]+$/.test(emailRecipient.value.trim()))
const emailBodyCount = computed(() => emailBody.value.length)

function captureEmail(): EmailSnapshot {
  return { recipient: emailRecipient.value, subject: emailSubject.value, body: emailBody.value }
}

function sendEmail() {
  if (!emailRecipientValid.value) {
    return
  }
  emailSnapshot.value = captureEmail()
  emailPhase.value = 'sent'
}

function discardEmail() {
  emailSnapshot.value = captureEmail()
  emailRecipient.value = ''
  emailSubject.value = ''
  emailBody.value = ''
  emailPhase.value = 'discarded'
}

function undoEmail() {
  if (!emailSnapshot.value) {
    toast('没有可恢复的草稿')
    return
  }
  const snapshot = emailSnapshot.value
  emailRecipient.value = snapshot.recipient
  emailSubject.value = snapshot.subject
  emailBody.value = snapshot.body
  emailPhase.value = 'editing'
}

// ============================================================
// 数据展示：用量计量（Usage Meter）——配额与阈值
// ============================================================

interface UsagePlanOption {
  label: string
  value: string
  quota: number
}

const usagePlans: UsagePlanOption[] = [
  { label: '基础版 · 100 GB', value: 'basic', quota: 100 },
  { label: '专业版 · 1024 GB', value: 'pro', quota: 1024 },
  { label: '旗舰版 · 5120 GB', value: 'flagship', quota: 5120 },
]
const usagePlanValue = ref('pro')
const usageQuota = computed(() => usagePlans.find(plan => plan.value === usagePlanValue.value)?.quota ?? 100)
const usedGb = ref(760)

watch(usageQuota, (quota) => {
  usedGb.value = Math.min(usedGb.value, quota)
})

const usageRange = computed<number[]>({
  get: () => [usedGb.value],
  set: (value) => {
    const next = value[0]
    if (typeof next === 'number') {
      usedGb.value = Math.round(next)
    }
  },
})
const usageStep = computed(() => Math.max(1, Math.round(usageQuota.value / 400)))
const usagePercent = computed(() => Math.min(100, usedGb.value / usageQuota.value * 100))

type MeterLevel = 'normal' | 'warning' | 'danger'
const usageLevel = computed<MeterLevel>(() => {
  if (usagePercent.value >= 90) {
    return 'danger'
  }
  return usagePercent.value >= 70 ? 'warning' : 'normal'
})
const usageLevelText: Record<MeterLevel, string> = {
  normal: '充足',
  warning: '接近上限',
  danger: '已超预警',
}
const usageTagVariant = computed(() => {
  if (usageLevel.value === 'danger') {
    return 'destructive' as const
  }
  return usageLevel.value === 'warning' ? 'outline' as const : 'secondary' as const
})
const usageAdvice = computed(() => {
  if (usageLevel.value === 'danger') {
    return '已越过 90% 危险线，新文件可能无法同步，建议升级套餐或清理旧版本。'
  }
  if (usageLevel.value === 'warning') {
    return '已越过 70% 预警线，可以开始归档不常用的大文件。'
  }
  return '用量健康，无需处理。'
})

// ============================================================
// 数据展示：紧凑分解（Compact Breakdown）——周期对比
// ============================================================

type BreakdownPeriod = 'week' | 'month'

interface BreakdownEntry {
  id: string
  name: string
  icon: string
  hours: Record<BreakdownPeriod, { current: number, previous: number }>
}

const breakdownPeriods: { label: string, value: BreakdownPeriod }[] = [
  { label: '本周', value: 'week' },
  { label: '本月', value: 'month' },
]
const breakdownPeriod = ref<BreakdownPeriod>('week')
const breakdownEntries: BreakdownEntry[] = [
  {
    id: 'bd-01',
    name: '协作设计',
    icon: 'morph:palette',
    hours: { week: { current: 18.5, previous: 16 }, month: { current: 74, previous: 68 } },
  },
  {
    id: 'bd-02',
    name: '研发联调',
    icon: 'morph:terminal',
    hours: { week: { current: 22, previous: 25.5 }, month: { current: 91, previous: 97 } },
  },
  {
    id: 'bd-03',
    name: '客户会议',
    icon: 'morph:users',
    hours: { week: { current: 9.5, previous: 7 }, month: { current: 38.5, previous: 30 } },
  },
  {
    id: 'bd-04',
    name: '内部评审',
    icon: 'morph:clipboard-check',
    hours: { week: { current: 6, previous: 8 }, month: { current: 25, previous: 33 } },
  },
]

const breakdownRows = computed(() => breakdownEntries.map(entry => ({
  ...entry,
  current: entry.hours[breakdownPeriod.value].current,
  previous: entry.hours[breakdownPeriod.value].previous,
})))
const breakdownMax = computed(() => Math.max(...breakdownRows.value.map(row => row.current), 1))
const breakdownTotal = computed(() => breakdownRows.value.reduce((sum, row) => sum + row.current, 0))
const breakdownPreviousTotal = computed(() => breakdownRows.value.reduce((sum, row) => sum + row.previous, 0))
const breakdownDelta = computed(() => percentDelta(breakdownTotal.value, breakdownPreviousTotal.value))

// ============================================================
// 数据展示：实验卡片（Experiment Card）——样本与获胜判定
// ============================================================

interface ExperimentVariant {
  key: 'A' | 'B'
  name: string
  visitors: number
  conversions: number
}

const experimentSeed: ExperimentVariant[] = [
  { key: 'A', name: '原版注册页', visitors: 2480, conversions: 212 },
  { key: 'B', name: '单栏简洁版', visitors: 2465, conversions: 251 },
]
const experimentVariants = ref(experimentSeed.map(item => ({ ...item })))
const experimentDecided = ref(false)
const experimentWinner = ref<'A' | 'B' | 'tie' | null>(null)

function variantRate(variant: ExperimentVariant) {
  return variant.visitors ? variant.conversions / variant.visitors * 100 : 0
}

function addExperimentSamples() {
  // 固定增量模拟「再跑一天」，保持演示数据可预期。
  const a = experimentVariants.value.find(item => item.key === 'A')
  const b = experimentVariants.value.find(item => item.key === 'B')
  if (!a || !b) {
    return
  }
  a.visitors += 320
  a.conversions += 26
  b.visitors += 315
  b.conversions += 34
}

function decideExperiment() {
  const a = experimentVariants.value.find(item => item.key === 'A')
  const b = experimentVariants.value.find(item => item.key === 'B')
  if (!a || !b) {
    return
  }
  const rateA = variantRate(a)
  const rateB = variantRate(b)
  experimentWinner.value = Math.abs(rateA - rateB) < 0.05 ? 'tie' : rateA > rateB ? 'A' : 'B'
  experimentDecided.value = true
}

function resetExperiment() {
  experimentVariants.value = experimentSeed.map(item => ({ ...item }))
  experimentDecided.value = false
  experimentWinner.value = null
}

const experimentVerdict = computed(() => {
  if (!experimentDecided.value || !experimentWinner.value) {
    return null
  }
  if (experimentWinner.value === 'tie') {
    return '当前样本下两组转化率打平，建议继续积累样本后再判定。'
  }
  const winner = experimentVariants.value.find(item => item.key === experimentWinner.value)
  const loser = experimentVariants.value.find(item => item.key !== experimentWinner.value)
  if (!winner || !loser) {
    return null
  }
  const lift = (variantRate(winner) - variantRate(loser)) / variantRate(loser) * 100
  return `${winner.name} 获胜：转化率 ${variantRate(winner).toFixed(2)}%，较${loser.name}提升 ${lift.toFixed(1)}%（本地样本，仅演示）。`
})

// ============================================================
// 数据展示：KPI 柱状图（KPI Chart）——悬停 / 聚焦读取
// ============================================================

type KpiMetricKey = 'revenue' | 'users' | 'conversion'

const kpiMetricOptions: { label: string, value: KpiMetricKey }[] = [
  { label: '营收', value: 'revenue' },
  { label: '新增用户', value: 'users' },
  { label: '转化率', value: 'conversion' },
]
const kpiSeries: Record<KpiMetricKey, { label: string, unit: string, decimals: number, values: number[] }> = {
  revenue: { label: '营收', unit: '万元', decimals: 0, values: [128, 142, 136, 158, 171, 165, 189, 204] },
  users: { label: '新增用户', unit: '人', decimals: 0, values: [820, 940, 905, 1080, 1210, 1180, 1360, 1490] },
  conversion: { label: '转化率', unit: '%', decimals: 1, values: [2.8, 3.1, 2.9, 3.4, 3.8, 3.6, 4.1, 4.4] },
}
const kpiMonths = ['3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月']
const kpiMetric = ref<KpiMetricKey>('revenue')
const kpiFocus = ref<number | null>(null)

const KPI_W = 520
const KPI_H = 188
const KPI_PAD_X = 10
const KPI_PAD_TOP = 14
const KPI_PAD_BOTTOM = 8

const kpiValues = computed(() => kpiSeries[kpiMetric.value].values)
const kpiMax = computed(() => Math.max(...kpiValues.value) * 1.15)
const kpiBars = computed(() => {
  const values = kpiValues.value
  const slot = (KPI_W - KPI_PAD_X * 2) / values.length
  const barWidth = slot * 0.56
  return values.map((value, index) => {
    const height = Math.max(2, value / kpiMax.value * (KPI_H - KPI_PAD_TOP - KPI_PAD_BOTTOM))
    return {
      index,
      value,
      x: KPI_PAD_X + index * slot + (slot - barWidth) / 2,
      y: KPI_H - KPI_PAD_BOTTOM - height,
      width: barWidth,
      height,
    }
  })
})
const kpiAggregateLabel = computed(() => kpiMetric.value === 'conversion' ? '8 期平均' : '8 期累计')
const kpiAggregate = computed(() => {
  const values = kpiValues.value
  const sum = values.reduce((total, value) => total + value, 0)
  return kpiMetric.value === 'conversion' ? sum / values.length : sum
})
const kpiPeakIndex = computed(() => kpiValues.value.indexOf(Math.max(...kpiValues.value)))

function formatKpi(value: number) {
  const series = kpiSeries[kpiMetric.value]
  const text = series.decimals > 0 ? value.toFixed(series.decimals) : value.toLocaleString('zh-Hans-CN')
  return `${text} ${series.unit}`
}

const kpiFocusReadout = computed(() => {
  if (kpiFocus.value === null) {
    return null
  }
  const index = kpiFocus.value
  const value = kpiValues.value[index]
  const previous = index > 0 ? kpiValues.value[index - 1] : null
  return {
    label: kpiMonths[index],
    value: formatKpi(value),
    delta: previous === null ? null : formatDelta((value - previous) / previous * 100),
  }
})

function stepKpiFocus(delta: number) {
  const count = kpiValues.value.length
  if (kpiFocus.value === null) {
    kpiFocus.value = delta > 0 ? 0 : count - 1
    return
  }
  kpiFocus.value = Math.min(count - 1, Math.max(0, kpiFocus.value + delta))
}

// ============================================================
// 数据展示：构成分解图（Breakdown Chart）——堆叠条与图例联动
// ============================================================

type SourcePeriod = 'current' | 'previous'

interface SourceSegment {
  key: string
  label: string
  icon: string
  current: number
  previous: number
}

const sourcePeriodOptions: { label: string, value: SourcePeriod }[] = [
  { label: '本期', value: 'current' },
  { label: '上期', value: 'previous' },
]
const sourceSegments: SourceSegment[] = [
  { key: 'direct', label: '直接访问', icon: 'morph:panel-top', current: 38, previous: 41 },
  { key: 'search', label: '搜索引入', icon: 'morph:search', current: 31, previous: 27 },
  { key: 'referral', label: '外部推荐', icon: 'morph:link', current: 18, previous: 20 },
  { key: 'social', label: '社交媒体', icon: 'morph:share-2', current: 13, previous: 12 },
]
const sourceTotals: Record<SourcePeriod, number> = { current: 48200, previous: 43100 }
const sourcePeriod = ref<SourcePeriod>('current')
const hoveredSegmentKey = ref<string | null>(null)
const pinnedSegmentKey = ref<string | null>(null)
const activeSegmentKey = computed(() => pinnedSegmentKey.value ?? hoveredSegmentKey.value)
const sourcePeriodLabel = computed(() => sourcePeriod.value === 'current' ? '本期' : '上期')

function segmentShare(segment: SourceSegment) {
  return sourcePeriod.value === 'current' ? segment.current : segment.previous
}

function segmentVisits(segment: SourceSegment) {
  return Math.round(sourceTotals[sourcePeriod.value] * segmentShare(segment) / 100)
}

const activeSegment = computed(() =>
  sourceSegments.find(segment => segment.key === activeSegmentKey.value) ?? null,
)

const sourceStackLabel = computed(() =>
  `${sourcePeriodLabel.value}访问来源构成：${sourceSegments.map(segment => `${segment.label} ${segmentShare(segment)}%`).join('，')}`,
)

function formatPoints(value: number) {
  const sign = value > 0 ? '+' : ''
  return `${sign}${value.toFixed(0)} 个百分点`
}

function togglePinnedSegment(key: string) {
  pinnedSegmentKey.value = pinnedSegmentKey.value === key ? null : key
}

// ============================================================
// 数据展示：可编辑数据表（Data Table）——选择 / 排序 / 编辑 / 粘贴 / 撤销
// ============================================================

interface BudgetRow {
  id: string
  name: string
  owner: string
  budget: number
  note: string
}

const budgetSeed: BudgetRow[] = [
  { id: 'b-01', name: '视觉走查外包', owner: '陈念', budget: 12000, note: '含两轮修改' },
  { id: 'b-02', name: '字体授权', owner: '林舟', budget: 4800, note: '年度' },
  { id: 'b-03', name: '灰度环境资源', owner: '周衡', budget: 9600, note: '按量预估' },
  { id: 'b-04', name: '用户访谈激励', owner: '苏芮', budget: 3000, note: '8 场' },
]
const budgetRows = ref(budgetSeed.map(row => ({ ...row })))
const selectedIds = ref<string[]>([])
const sortKey = ref<'name' | 'budget'>('name')
const sortOrder = ref<'asc' | 'desc'>('asc')
const editingId = ref<string | null>(null)
const editDraft = ref({ name: '', budget: 0 })
const budgetHistory = ref<BudgetRow[][]>([])
let pasteSeq = 0

const allRowIds = computed(() => budgetRows.value.map(row => row.id))
const headerCheck = computed<boolean | 'indeterminate'>({
  get: () => {
    if (!selectedIds.value.length) {
      return false
    }
    return selectedIds.value.length === allRowIds.value.length ? true : 'indeterminate'
  },
  set: (value) => {
    if (value === 'indeterminate') {
      return
    }
    selectedIds.value = value ? [...allRowIds.value] : []
  },
})

const sortedRows = computed(() => {
  const rows = [...budgetRows.value]
  rows.sort((a, b) => {
    const result = sortKey.value === 'name'
      ? a.name.localeCompare(b.name, 'zh-Hans-CN')
      : a.budget - b.budget
    return sortOrder.value === 'asc' ? result : -result
  })
  return rows
})

const budgetTotal = computed(() => budgetRows.value.reduce((sum, row) => sum + row.budget, 0))

const selectedTsv = computed(() => {
  const rows = budgetRows.value.filter(row => selectedIds.value.includes(row.id))
  return ['名称\t负责人\t预算（元）', ...rows.map(row => `${row.name}\t${row.owner}\t${row.budget}`)].join('\n')
})

function toggleSort(key: 'name' | 'budget') {
  if (sortKey.value === key) {
    sortOrder.value = sortOrder.value === 'asc' ? 'desc' : 'asc'
  }
  else {
    sortKey.value = key
    sortOrder.value = 'asc'
  }
}

function sortIcon(key: 'name' | 'budget') {
  if (sortKey.value !== key) {
    return 'morph:arrow-up-down'
  }
  return sortOrder.value === 'asc' ? 'morph:chevron-up' : 'morph:chevron-down'
}

function toggleRowSelected(id: string, checked: boolean | 'indeterminate' | null | undefined) {
  if (checked !== true) {
    return
  }
  selectedIds.value = [...selectedIds.value, id]
}

function pushBudgetHistory() {
  budgetHistory.value.push(budgetRows.value.map(row => ({ ...row })))
  if (budgetHistory.value.length > 20) {
    budgetHistory.value.shift()
  }
}

function startEdit(row: BudgetRow) {
  editingId.value = row.id
  editDraft.value = { name: row.name, budget: row.budget }
}

function saveEdit() {
  const row = budgetRows.value.find(item => item.id === editingId.value)
  if (!row) {
    return
  }
  pushBudgetHistory()
  row.name = editDraft.value.name.trim() || row.name
  row.budget = Math.max(0, Math.round(editDraft.value.budget))
  editingId.value = null
  toast.success('已保存行修改')
}

function parsePastedRows(text: string): BudgetRow[] {
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
    .map((line) => {
      const [name, owner, budgetText] = line.split('\t')
      pasteSeq += 1
      return {
        id: `b-paste-${pasteSeq}`,
        name: (name ?? '').trim() || '未命名条目',
        owner: (owner ?? '').trim() || '待分配',
        budget: Number.parseFloat((budgetText ?? '').replace(/[^0-9.-]/g, '')) || 0,
        note: '来自粘贴',
      }
    })
}

async function pasteRows() {
  try {
    const text = await navigator.clipboard.readText()
    const parsed = parsePastedRows(text)
    if (!parsed.length) {
      toast.error('剪贴板内容不是可识别的表格行')
      return
    }
    pushBudgetHistory()
    budgetRows.value.push(...parsed)
    toast.success(`已粘贴 ${parsed.length} 行`)
  }
  catch {
    toast.error('读取剪贴板被拒绝，请允许浏览器授权后重试')
  }
}

function deleteSelected() {
  if (!selectedIds.value.length) {
    return
  }
  const count = selectedIds.value.length
  pushBudgetHistory()
  budgetRows.value = budgetRows.value.filter(row => !selectedIds.value.includes(row.id))
  selectedIds.value = []
  toast.success(`已删除 ${count} 行，可撤销`)
}

function undoBudget() {
  const snapshot = budgetHistory.value.pop()
  if (!snapshot) {
    toast('没有可撤销的修改')
    return
  }
  budgetRows.value = snapshot
  editingId.value = null
  selectedIds.value = selectedIds.value.filter(id => snapshot.some(row => row.id === id))
  toast.success('已撤销上一次修改')
}

// ============================================================
// 数据展示：变更摘要（Change Summary）——保留 / 放弃 / 撤销
// ============================================================

interface ChangeEntry {
  id: string
  title: string
  kind: '新增' | '删除' | '修改'
  added: number
  removed: number
  detail: string
  keep: boolean
}

const changeSeed: ChangeEntry[] = [
  { id: 'ch-01', title: '新增「发布检查清单」章节', kind: '新增', added: 24, removed: 0, detail: '包含灰度步骤、回滚口径与责任人矩阵。', keep: true },
  { id: 'ch-02', title: '重写评审流程说明', kind: '修改', added: 15, removed: 22, detail: '合并口头约定，明确 24 小时内反馈。', keep: true },
  { id: 'ch-03', title: '移除过期截图', kind: '删除', added: 0, removed: 9, detail: '旧版首页截图已失效，由设计稿链接替代。', keep: true },
  { id: 'ch-04', title: '修正口径：活跃用户定义', kind: '修改', added: 6, removed: 4, detail: '与数据团队对齐为「7 日内登录且产生关键行为」。', keep: false },
]
const changeEntries = ref(changeSeed.map(entry => ({ ...entry })))
const changeHistory = ref<ChangeEntry[][]>([])
const expandedChangeIds = ref<string[]>([])

const keptChanges = computed(() => changeEntries.value.filter(entry => entry.keep))
const keptAdded = computed(() => keptChanges.value.reduce((sum, entry) => sum + entry.added, 0))
const keptRemoved = computed(() => keptChanges.value.reduce((sum, entry) => sum + entry.removed, 0))

function pushChangeHistory() {
  changeHistory.value.push(changeEntries.value.map(entry => ({ ...entry })))
}

function setChangeKeep(id: string, keep: boolean) {
  const entry = changeEntries.value.find(item => item.id === id)
  if (!entry || entry.keep === keep) {
    return
  }
  pushChangeHistory()
  entry.keep = keep
}

function keepAllChanges() {
  pushChangeHistory()
  changeEntries.value.forEach((entry) => {
    entry.keep = true
  })
}

function dropAllChanges() {
  pushChangeHistory()
  changeEntries.value.forEach((entry) => {
    entry.keep = false
  })
}

function undoChanges() {
  const snapshot = changeHistory.value.pop()
  if (!snapshot) {
    toast('没有可撤销的变更操作')
    return
  }
  changeEntries.value = snapshot
}

function toggleChangeDetail(id: string) {
  expandedChangeIds.value = expandedChangeIds.value.includes(id)
    ? expandedChangeIds.value.filter(item => item !== id)
    : [...expandedChangeIds.value, id]
}

// ============================================================
// 按钮：按钮试验台（Button）
// ============================================================

type ButtonVariantKey = 'default' | 'secondary' | 'outline' | 'ghost' | 'destructive' | 'link'
type ButtonSizeKey = 'sm' | 'default' | 'lg'

const buttonVariantOptions: { label: string, value: ButtonVariantKey }[] = [
  { label: '默认', value: 'default' },
  { label: '次要', value: 'secondary' },
  { label: '描边', value: 'outline' },
  { label: '幽灵', value: 'ghost' },
  { label: '危险', value: 'destructive' },
  { label: '链接', value: 'link' },
]
const buttonSizeOptions: { label: string, value: ButtonSizeKey }[] = [
  { label: 'sm', value: 'sm' },
  { label: 'default', value: 'default' },
  { label: 'lg', value: 'lg' },
]
const buttonVariant = ref<ButtonVariantKey>('default')
const buttonSize = ref<ButtonSizeKey>('default')
const buttonLoading = ref(false)
const buttonDisabled = ref(false)
const buttonClicks = ref(0)
const lastClickAt = ref('')

function runDemoButton() {
  buttonClicks.value += 1
  lastClickAt.value = new Date().toLocaleTimeString('zh-Hans-CN', { hour12: false })
}

// ============================================================
// 按钮：回应条（Reaction Bar）——切换回应与人数汇总
// ============================================================

interface ReactionItem {
  key: string
  icon: string
  label: string
  count: number
  mine: boolean
  people: string[]
}

const reactions = ref<ReactionItem[]>([
  { key: 'like', icon: 'morph:thumbs-up', label: '赞', count: 12, mine: false, people: ['林舟', '陈念', '周衡'] },
  { key: 'celebrate', icon: 'morph:party-popper', label: '庆祝', count: 5, mine: true, people: ['苏芮', '何朗'] },
  { key: 'love', icon: 'morph:heart', label: '喜欢', count: 3, mine: false, people: ['叶蓁'] },
  { key: 'watch', icon: 'morph:eye', label: '看过', count: 8, mine: false, people: ['秦岭', '白露', '顾城'] },
])
const reactionTotal = computed(() => reactions.value.reduce((sum, item) => sum + item.count, 0))
const mineLabels = computed(() => reactions.value.filter(item => item.mine).map(item => item.label))

function toggleReaction(item: ReactionItem) {
  item.mine = !item.mine
  item.count += item.mine ? 1 : -1
}

function reactionSummary(item: ReactionItem) {
  const people = item.people.join('、')
  return `${item.label} · ${item.people.length > 2 ? `${people} 等` : people}`
}
</script>

<template>
  <div class="adc">
    <section class="adc-head">
      <div class="adc-head__text">
        <h2>AffectUI 卡片与数据</h2>
        <p>卡片、数据展示与按钮类区块的本地交互演示：全部为组件内状态，操作即改数据，可撤销的都提供撤销。</p>
      </div>
      <YdSegmented v-model="activeFilter" :options="filterOptions" aria-label="按分组筛选演示" />
    </section>
    <p class="adc-count" aria-live="polite">
      共 {{ visibleDemos.length }} 个演示
    </p>

    <template v-for="demo in visibleDemos" :key="demo.key">
      <!-- ================= 封面流 ================= -->
      <DemoCard v-if="demo.key === 'cover-flow'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div
          class="adc-cf"
          role="group"
          aria-label="封面流：拖动、左右方向键或按钮切换"
          @keydown.left.prevent="moveCover(-1)"
          @keydown.right.prevent="moveCover(1)"
        >
          <div
            class="adc-cf__strip"
            :style="coverStripStyle"
            @pointerdown="onCoverPointerDown"
            @pointermove="onCoverPointerMove"
            @pointerup="settleCoverDrag"
            @pointercancel="settleCoverDrag"
          >
            <YdAction
              v-for="(cover, index) in covers"
              :key="cover.id"
              class="adc-cf__card"
              :class="{ 'is-active': index === activeCover, 'is-near': coverDistance(index) === 1, 'is-far': coverDistance(index) > 1 }"
              :aria-current="index === activeCover ? 'true' : undefined"
              @click="selectCover(index, $event)"
            >
              <span class="adc-cf__art"><YdIcon :name="cover.icon" /></span>
              <span class="adc-cf__title">{{ cover.title }}</span>
              <span class="adc-cf__subtitle">{{ cover.subtitle }}</span>
              <span class="adc-cf__meta">{{ cover.meta }}</span>
            </YdAction>
          </div>
        </div>
        <div class="adc-cf__toolbar">
          <YdButton size="icon-sm" variant="outline" :disabled="activeCover === 0" aria-label="上一个封面" @click="moveCover(-1)">
            <YdIcon name="morph:chevron-left" />
          </YdButton>
          <span class="adc-cf__position">{{ activeCover + 1 }} / {{ covers.length }} · {{ covers[activeCover].title }}</span>
          <YdButton size="icon-sm" variant="outline" :disabled="activeCover === covers.length - 1" aria-label="下一个封面" @click="moveCover(1)">
            <YdIcon name="morph:chevron-right" />
          </YdButton>
          <YdKbdGroup class="adc-cf__kbd">
            <YdKbd size="sm">
              ←
            </YdKbd>
            <YdKbd size="sm">
              →
            </YdKbd>
          </YdKbdGroup>
        </div>
      </DemoCard>

      <!-- ================= 项目卡片 ================= -->
      <DemoCard v-else-if="demo.key === 'project-card'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-project">
          <div class="adc-project__main">
            <div class="adc-project__top">
              <div>
                <p class="adc-project__name">
                  官网改版 2.0
                </p>
                <p class="adc-project__meta">
                  PRJ-9042 · 负责人 陈念 · 10 月 24 日到期
                </p>
              </div>
              <YdTag :variant="projectStatusVariant">
                {{ projectStatus }}
              </YdTag>
            </div>
            <div class="adc-project__members">
              <YdAvatar src="" fallback="陈" />
              <YdAvatar src="" fallback="林" />
              <YdAvatar src="" fallback="周" />
              <span class="adc-project__meta">3 人参与</span>
            </div>
            <div class="adc-project__subtasks">
              <YdCheckbox
                v-for="subtask in projectSubtasks"
                :key="subtask.id"
                v-model="subtask.done"
                :aria-label="`子任务：${subtask.title}`"
              >
                <span class="adc-tips__text" :class="{ 'adc-done-text': subtask.done }">{{ subtask.title }}</span>
              </YdCheckbox>
            </div>
            <YdButton size="sm" variant="outline" :disabled="projectStatus === '已发布'" @click="advanceProjectStatus">
              推进状态
            </YdButton>
          </div>
          <div class="adc-project__ring" role="img" :aria-label="`子任务完成 ${projectDone} / ${projectSubtasks.length}`">
            <svg width="96" height="96" viewBox="0 0 96 96">
              <circle class="adc-project__ring-track" cx="48" cy="48" r="34" fill="none" stroke-width="9" />
              <circle
                class="adc-project__ring-value"
                cx="48"
                cy="48"
                r="34"
                fill="none"
                stroke-width="9"
                stroke-linecap="round"
                :stroke="ringColor"
                :stroke-dasharray="ringCircumference"
                :stroke-dashoffset="ringDashOffset"
                transform="rotate(-90 48 48)"
              />
            </svg>
            <div class="adc-project__ring-text">
              <strong>{{ projectPercent }}%</strong>
              <span>{{ projectDone }}/{{ projectSubtasks.length }} 项</span>
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 提示面板 ================= -->
      <DemoCard v-else-if="demo.key === 'tips-panel'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-toolbar">
          <div class="adc-tips-progress">
            <YdProgress :model-value="tipsPercent" />
            <span class="adc-hint">已完成 {{ tipsDone }}/{{ tips.length }}</span>
          </div>
          <div class="adc-toolbar__actions">
            <YdButton size="sm" variant="outline" :disabled="!tips.length" @click="completeAllTips">
              全部完成
            </YdButton>
            <YdButton size="sm" variant="ghost" :disabled="!dismissedTips.length" @click="undoDismissTip">
              <YdIcon name="morph:undo-2" />
              恢复提示
            </YdButton>
            <YdButton size="sm" variant="ghost" @click="resetTips">
              <YdIcon name="morph:rotate-ccw" />
              重置
            </YdButton>
          </div>
        </div>
        <YdAlert
          v-if="tips.length && tipsDone === tips.length"
          icon="morph:circle-check"
          title="引导完成"
          description="全部提示已处理，新功能说明会出现在帮助中心。"
        />
        <ul v-if="tips.length" class="adc-tips">
          <li v-for="tip in tips" :key="tip.id" class="adc-tips__row">
            <YdCheckbox v-model="tip.done" :aria-label="`提示：${tip.title}`">
              <span class="adc-tips__text">
                <span :class="{ 'adc-done-text': tip.done }">{{ tip.title }}</span>
                <span class="adc-tips__detail">{{ tip.detail }}</span>
              </span>
            </YdCheckbox>
            <YdButton size="icon-sm" variant="ghost" :aria-label="`忽略提示：${tip.title}`" title="忽略" @click="dismissTip(tip.id)">
              <YdIcon name="morph:x" />
            </YdButton>
          </li>
        </ul>
        <YdEmpty
          v-else
          icon="morph:sparkles"
          title="提示都处理完了"
          description="被忽略的提示可以点上方「恢复提示」找回。"
        />
      </DemoCard>

      <!-- ================= 智能推荐 ================= -->
      <DemoCard v-else-if="demo.key === 'recommendation'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-rec">
          <div class="adc-rec__card">
            <div class="adc-rec__head">
              <YdTag variant="secondary">
                {{ currentRecommendation.tag }}
              </YdTag>
              <span class="adc-rec__score">匹配度 {{ currentRecommendation.score }}</span>
            </div>
            <p class="adc-rec__title">
              {{ currentRecommendation.title }}
            </p>
            <p class="adc-rec__reason">
              {{ currentRecommendation.reason }}
            </p>
            <div class="adc-rec__actions">
              <YdButton size="sm" :disabled="adoptedRecommendationId === currentRecommendation.id" @click="adoptRecommendation">
                {{ adoptedRecommendationId === currentRecommendation.id ? '已采用' : '采用' }}
              </YdButton>
              <YdButton size="sm" variant="outline" @click="nextRecommendation">
                <YdIcon name="morph:shuffle" />
                换一个
              </YdButton>
              <YdButton size="sm" variant="ghost" @click="rejectRecommendation">
                不感兴趣
              </YdButton>
              <YdButton size="sm" variant="ghost" :disabled="!recommendationHistory.length" @click="undoRecommendation">
                <YdIcon name="morph:undo-2" />
                撤销
              </YdButton>
            </div>
          </div>
          <div class="adc-rec__list" role="list" aria-label="推荐候选">
            <YdAction
              v-for="(item, index) in recommendations"
              :key="item.id"
              class="adc-rec__row"
              :class="{ 'is-active': index === recommendationIndex }"
              :aria-current="index === recommendationIndex ? 'true' : undefined"
              @click="switchRecommendation(index)"
            >
              <span class="adc-rec__row-label">{{ item.title }}</span>
              <span class="adc-rec__row-score">{{ item.score }}</span>
            </YdAction>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 邮件草稿 ================= -->
      <DemoCard v-else-if="demo.key === 'email-draft'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-mail">
          <template v-if="emailPhase === 'editing'">
            <div class="adc-mail__grid">
              <div class="adc-field">
                <span class="adc-field__label">收件人</span>
                <YdInput v-model="emailRecipient" type="email" placeholder="name@example.com" aria-label="收件人邮箱" />
              </div>
              <div class="adc-field">
                <span class="adc-field__label">语气（切换即重写正文）</span>
                <YdSegmented v-model="emailTone" :options="toneOptions" aria-label="邮件语气" />
              </div>
            </div>
            <div class="adc-field">
              <span class="adc-field__label">主题</span>
              <YdInput v-model="emailSubject" placeholder="邮件主题" aria-label="邮件主题" />
            </div>
            <div class="adc-field">
              <span class="adc-field__label">正文</span>
              <YdTextarea v-model="emailBody" :rows="7" aria-label="邮件正文" />
              <span class="adc-hint">正文可继续手动编辑，当前 {{ emailBodyCount }} 字。</span>
            </div>
            <p v-if="!emailRecipientValid" class="adc-mail__warn" role="alert">
              收件人需要是合法邮箱，填写后才能发送。
            </p>
            <div class="adc-mail__actions">
              <YdButton size="sm" :disabled="!emailRecipientValid" @click="sendEmail">
                <YdIcon name="morph:send" />
                发送
              </YdButton>
              <YdPopconfirm
                title="丢弃这份草稿？"
                description="丢弃后内容保留在本地快照，可立即恢复。"
                confirm-text="丢弃"
                cancel-text="继续编辑"
                @confirm="discardEmail"
              >
                <YdButton size="sm" variant="outline">
                  丢弃草稿
                </YdButton>
              </YdPopconfirm>
            </div>
          </template>
          <template v-else>
            <YdAlert
              icon="morph:send"
              title="邮件已在本地标记为发送"
              description="演示不会发出真实邮件，可立即撤销。"
            />
            <YdAlert
              v-if="emailPhase === 'discarded'"
              icon="morph:trash-2"
              title="草稿已丢弃"
              description="内容仍保留在本地快照中，可一键恢复。"
            />
            <div>
              <YdButton size="sm" variant="outline" @click="undoEmail">
                <YdIcon name="morph:undo-2" />
                {{ emailPhase === 'sent' ? '撤销发送' : '恢复草稿' }}
              </YdButton>
            </div>
          </template>
        </div>
      </DemoCard>

      <!-- ================= 用量计量 ================= -->
      <DemoCard v-else-if="demo.key === 'usage-meter'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-toolbar">
          <div class="adc-field adc-field--plan">
            <span class="adc-field__label">套餐</span>
            <YdSelect v-model="usagePlanValue" :options="usagePlans" aria-label="选择套餐" />
          </div>
          <span class="adc-hint">
            已用 {{ usedGb.toLocaleString('zh-Hans-CN') }} GB / {{ usageQuota.toLocaleString('zh-Hans-CN') }} GB（{{ usagePercent.toFixed(1) }}%）
          </span>
        </div>
        <div
          class="adc-meter"
          role="meter"
          aria-label="存储用量"
          :aria-valuemin="0"
          :aria-valuemax="usageQuota"
          :aria-valuenow="usedGb"
          :aria-valuetext="`已用 ${usedGb} GB，共 ${usageQuota} GB`"
        >
          <div class="adc-meter__track">
            <div class="adc-meter__fill" :class="`adc-meter__fill--${usageLevel}`" :style="{ width: `${usagePercent}%` }" />
            <i class="adc-meter__mark adc-meter__mark--warning" style="left: 70%;" aria-hidden="true" />
            <i class="adc-meter__mark adc-meter__mark--danger" style="left: 90%;" aria-hidden="true" />
          </div>
          <div class="adc-meter__scale" aria-hidden="true">
            <span>0</span>
            <span class="adc-meter__scale-mark adc-meter__scale-mark--warning" style="left: 70%;">预警 70%</span>
            <span class="adc-meter__scale-mark adc-meter__scale-mark--danger" style="left: 90%;">危险 90%</span>
            <span>100%</span>
          </div>
        </div>
        <div class="adc-toolbar">
          <YdTag :variant="usageTagVariant">
            {{ usageLevelText[usageLevel] }}
          </YdTag>
          <span class="adc-hint">{{ usageAdvice }}</span>
        </div>
        <div class="adc-field">
          <span class="adc-field__label">调整已用容量（模拟）</span>
          <YdSlider v-model="usageRange" :min="0" :max="usageQuota" :step="usageStep" aria-label="已用容量" />
        </div>
      </DemoCard>

      <!-- ================= 紧凑分解 ================= -->
      <DemoCard v-else-if="demo.key === 'compact-breakdown'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-toolbar">
          <YdSegmented v-model="breakdownPeriod" :options="breakdownPeriods" aria-label="统计周期" />
          <span class="adc-hint adc-bd__total">
            合计 {{ breakdownTotal.toFixed(1) }} 小时
            <YdTrend
              :value="formatDelta(breakdownDelta)"
              :type="breakdownDelta >= 0 ? 'up' : 'down'"
              size="small"
              variant="soft"
            />
          </span>
        </div>
        <ul class="adc-bd">
          <li v-for="row in breakdownRows" :key="row.id" class="adc-bd__row">
            <YdIcon :name="row.icon" class="adc-bd__icon" />
            <span class="adc-bd__name">{{ row.name }}</span>
            <span class="adc-bd__bar" aria-hidden="true">
              <i :style="{ width: `${row.current / breakdownMax * 100}%` }" />
            </span>
            <span class="adc-bd__value">{{ row.current.toFixed(1) }}h</span>
            <YdTrend
              :value="formatDelta(percentDelta(row.current, row.previous))"
              :type="row.current >= row.previous ? 'up' : 'down'"
              size="small"
              variant="soft"
            />
          </li>
        </ul>
        <p class="adc-hint">
          环比表示投入时长的变化方向，此处不代表好坏语义。
        </p>
      </DemoCard>

      <!-- ================= 实验卡片 ================= -->
      <DemoCard v-else-if="demo.key === 'experiment-card'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-exp">
          <div
            v-for="variant in experimentVariants"
            :key="variant.key"
            class="adc-exp__variant"
            :class="{ 'is-winner': experimentWinner === variant.key }"
          >
            <div class="adc-exp__head">
              <strong>{{ variant.key }} · {{ variant.name }}</strong>
              <YdTag v-if="experimentWinner === variant.key">
                获胜
              </YdTag>
              <YdTag v-else-if="experimentWinner === 'tie'" variant="outline">
                平局
              </YdTag>
            </div>
            <p class="adc-hint">
              样本 {{ variant.visitors.toLocaleString('zh-Hans-CN') }} · 转化 {{ variant.conversions.toLocaleString('zh-Hans-CN') }}
            </p>
            <p class="adc-exp__rate">
              {{ variantRate(variant).toFixed(2) }}%
            </p>
          </div>
        </div>
        <YdAlert
          v-if="experimentVerdict"
          icon="morph:flask-conical"
          title="判定结果"
          :description="experimentVerdict"
        />
        <div class="adc-toolbar">
          <YdButton size="sm" :disabled="experimentDecided" @click="addExperimentSamples">
            再积累一天样本
          </YdButton>
          <YdButton size="sm" variant="outline" :disabled="experimentDecided" @click="decideExperiment">
            判定获胜版本
          </YdButton>
          <YdButton size="sm" variant="ghost" @click="resetExperiment">
            <YdIcon name="morph:rotate-ccw" />
            重开实验
          </YdButton>
        </div>
      </DemoCard>

      <!-- ================= KPI 柱状图 ================= -->
      <DemoCard v-else-if="demo.key === 'kpi-chart'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-toolbar">
          <YdSegmented v-model="kpiMetric" :options="kpiMetricOptions" aria-label="选择指标" @change="kpiFocus = null" />
          <div class="adc-toolbar__actions">
            <YdButton size="icon-sm" variant="outline" aria-label="上一期" @click="stepKpiFocus(-1)">
              <YdIcon name="morph:chevron-left" />
            </YdButton>
            <YdButton size="icon-sm" variant="outline" aria-label="下一期" @click="stepKpiFocus(1)">
              <YdIcon name="morph:chevron-right" />
            </YdButton>
          </div>
        </div>
        <div class="adc-kpi__stage">
          <div class="adc-kpi__canvas">
            <svg
              :width="KPI_W"
              :height="KPI_H"
              :viewBox="`0 0 ${KPI_W} ${KPI_H}`"
              role="img"
              :aria-label="`近 8 期${kpiSeries[kpiMetric].label}柱状图，峰值 ${kpiMonths[kpiPeakIndex]} ${formatKpi(kpiSeries[kpiMetric].values[kpiPeakIndex])}`"
              @pointerleave="kpiFocus = null"
            >
              <rect
                v-for="bar in kpiBars"
                :key="bar.index"
                class="adc-kpi__bar"
                :class="{ 'is-focus': kpiFocus === bar.index }"
                :x="bar.x"
                :y="bar.y"
                :width="bar.width"
                :height="bar.height"
                rx="5"
                @pointerenter="kpiFocus = bar.index"
              />
            </svg>
            <div class="adc-kpi__axis" aria-hidden="true">
              <span v-for="month in kpiMonths" :key="month">{{ month }}</span>
            </div>
          </div>
        </div>
        <div class="adc-kpi__readout" aria-live="polite">
          <template v-if="kpiFocusReadout">
            <strong>{{ kpiFocusReadout.label }}</strong>
            <span>{{ kpiFocusReadout.value }}</span>
            <span v-if="kpiFocusReadout.delta" class="adc-hint">环比 {{ kpiFocusReadout.delta }}</span>
          </template>
          <template v-else>
            <span class="adc-hint">
              {{ kpiAggregateLabel }} {{ formatKpi(kpiAggregate) }} · 峰值 {{ kpiMonths[kpiPeakIndex] }} {{ formatKpi(kpiSeries[kpiMetric].values[kpiPeakIndex]) }}。悬停柱形，或用箭头按钮逐期查看。
            </span>
          </template>
        </div>
      </DemoCard>

      <!-- ================= 构成分解图 ================= -->
      <DemoCard v-else-if="demo.key === 'breakdown-chart'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-toolbar">
          <YdSegmented v-model="sourcePeriod" :options="sourcePeriodOptions" aria-label="对比期间" />
          <span class="adc-hint">{{ sourcePeriodLabel }}访问 {{ sourceTotals[sourcePeriod].toLocaleString('zh-Hans-CN') }} 次</span>
        </div>
        <div class="adc-stack" role="img" :aria-label="sourceStackLabel">
          <div
            v-for="(segment, index) in sourceSegments"
            :key="segment.key"
            class="adc-stack__segment"
            :class="[`adc-stack__segment--${index}`, { 'is-dim': activeSegmentKey !== null && activeSegmentKey !== segment.key }]"
            :style="{ width: `${segmentShare(segment)}%` }"
            @pointerenter="hoveredSegmentKey = segment.key"
            @pointerleave="hoveredSegmentKey = null"
          />
        </div>
        <div class="adc-src-legend">
          <YdAction
            v-for="(segment, index) in sourceSegments"
            :key="segment.key"
            class="adc-src-legend__item"
            :class="{ 'is-active': activeSegmentKey === segment.key }"
            :aria-pressed="pinnedSegmentKey === segment.key"
            @click="togglePinnedSegment(segment.key)"
            @pointerenter="hoveredSegmentKey = segment.key"
            @pointerleave="hoveredSegmentKey = null"
            @focus="hoveredSegmentKey = segment.key"
            @blur="hoveredSegmentKey = null"
          >
            <i class="adc-src-legend__swatch" :class="`adc-src-legend__swatch--${index}`" aria-hidden="true" />
            <span>{{ segment.label }}</span>
            <strong>{{ segmentShare(segment) }}%</strong>
          </YdAction>
        </div>
        <div class="adc-kpi__readout" aria-live="polite">
          <template v-if="activeSegment">
            <strong>{{ activeSegment.label }}</strong>
            <span>{{ segmentVisits(activeSegment).toLocaleString('zh-Hans-CN') }} 次（占 {{ segmentShare(activeSegment) }}%）</span>
            <span class="adc-hint">较上期 {{ formatPoints(activeSegment.current - activeSegment.previous) }}</span>
          </template>
          <template v-else>
            <span class="adc-hint">悬停分段或点击图例固定一个来源，查看占比与访问次数。</span>
          </template>
        </div>
      </DemoCard>

      <!-- ================= 可编辑数据表 ================= -->
      <DemoCard v-else-if="demo.key === 'data-table'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-toolbar">
          <YdCopyButton
            :text="selectedTsv"
            label="复制所选"
            :disabled="!selectedIds.length"
            @copied="toast.success(`已复制 ${selectedIds.length} 行`)"
          />
          <YdButton size="sm" variant="outline" @click="pasteRows">
            <YdIcon name="morph:clipboard-paste" />
            粘贴行
          </YdButton>
          <YdPopconfirm
            title="删除所选行？"
            description="删除后可通过「撤销」恢复。"
            confirm-text="删除"
            cancel-text="先不删"
            @confirm="deleteSelected"
          >
            <YdButton size="sm" variant="destructive" :disabled="!selectedIds.length">
              <YdIcon name="morph:trash-2" />
              删除所选
            </YdButton>
          </YdPopconfirm>
          <YdButton size="sm" variant="ghost" :disabled="!budgetHistory.length" @click="undoBudget">
            <YdIcon name="morph:undo-2" />
            撤销
          </YdButton>
        </div>
        <div class="adc-table-wrap">
          <table class="adc-table">
            <thead>
              <tr>
                <th class="adc-table__check">
                  <YdCheckbox v-model="headerCheck" :aria-label="`全选数据行，当前已选 ${selectedIds.length} 行`" />
                </th>
                <th :aria-sort="sortKey === 'name' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : undefined">
                  <YdAction type="button" class="adc-table__sort" @click="toggleSort('name')">
                    名称
                    <YdIcon :name="sortIcon('name')" />
                  </YdAction>
                </th>
                <th>负责人</th>
                <th :aria-sort="sortKey === 'budget' ? (sortOrder === 'asc' ? 'ascending' : 'descending') : undefined">
                  <YdAction type="button" class="adc-table__sort" @click="toggleSort('budget')">
                    预算（元）
                    <YdIcon :name="sortIcon('budget')" />
                  </YdAction>
                </th>
                <th>备注</th>
                <th class="adc-table__ops">
                  <span class="adc-visually-hidden">操作</span>
                </th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in sortedRows" :key="row.id" :class="{ 'is-editing': editingId === row.id }">
                <td class="adc-table__check">
                  <YdCheckbox
                    :model-value="selectedIds.includes(row.id)"
                    :aria-label="`选择 ${row.name}`"
                    @update:model-value="toggleRowSelected(row.id, $event)"
                  />
                </td>
                <template v-if="editingId === row.id">
                  <td>
                    <YdInput v-model="editDraft.name" aria-label="编辑名称" />
                  </td>
                  <td>{{ row.owner }}</td>
                  <td>
                    <YdNumberField v-model="editDraft.budget" :min="0" :step="100" class="adc-table__number" aria-label="编辑预算" />
                  </td>
                  <td>{{ row.note }}</td>
                  <td class="adc-table__ops">
                    <YdButton size="sm" @click="saveEdit">
                      保存
                    </YdButton>
                    <YdButton size="sm" variant="ghost" @click="editingId = null">
                      取消
                    </YdButton>
                  </td>
                </template>
                <template v-else>
                  <td>{{ row.name }}</td>
                  <td>{{ row.owner }}</td>
                  <td class="adc-table__budget">
                    {{ row.budget.toLocaleString('zh-Hans-CN') }}
                  </td>
                  <td>{{ row.note }}</td>
                  <td class="adc-table__ops">
                    <YdButton size="icon-sm" variant="ghost" :aria-label="`编辑 ${row.name}`" @click="startEdit(row)">
                      <YdIcon name="morph:pencil" />
                    </YdButton>
                  </td>
                </template>
              </tr>
            </tbody>
          </table>
        </div>
        <p class="adc-hint" aria-live="polite">
          已选 {{ selectedIds.length }} 行 · 预算合计 {{ budgetTotal.toLocaleString('zh-Hans-CN') }} 元。粘贴支持「名称、负责人、预算」的 TSV 文本（可直接从表格软件复制）；最多保留 20 步撤销。
        </p>
      </DemoCard>

      <!-- ================= 变更摘要 ================= -->
      <DemoCard v-else-if="demo.key === 'change-summary'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-summary">
          <div class="adc-summary__totals" aria-live="polite">
            <span>保留 <strong>{{ keptChanges.length }}</strong> / {{ changeEntries.length }} 项变更</span>
            <em class="adc-summary__added">+{{ keptAdded }} 行</em>
            <em class="adc-summary__removed">−{{ keptRemoved }} 行</em>
          </div>
          <div class="adc-toolbar">
            <YdButton size="sm" variant="outline" @click="keepAllChanges">
              全部保留
            </YdButton>
            <YdButton size="sm" variant="outline" @click="dropAllChanges">
              全部放弃
            </YdButton>
            <YdButton size="sm" variant="ghost" :disabled="!changeHistory.length" @click="undoChanges">
              <YdIcon name="morph:undo-2" />
              撤销
            </YdButton>
          </div>
          <ul class="adc-changes">
            <li v-for="entry in changeEntries" :key="entry.id" class="adc-changes__row" :class="{ 'is-dropped': !entry.keep }">
              <div class="adc-changes__main">
                <YdTag :variant="entry.kind === '删除' ? 'destructive' : entry.kind === '新增' ? 'default' : 'secondary'">
                  {{ entry.kind }}
                </YdTag>
                <span class="adc-changes__title">{{ entry.title }}</span>
                <span class="adc-changes__count">
                  <em v-if="entry.added" class="adc-summary__added">+{{ entry.added }}</em>
                  <em v-if="entry.removed" class="adc-summary__removed">−{{ entry.removed }}</em>
                </span>
                <YdCollapsible
                  :model-value="expandedChangeIds.includes(entry.id)"
                  @update:model-value="toggleChangeDetail(entry.id)"
                >
                  <template #trigger="{ open }">
                    <YdButton size="sm" variant="ghost" :aria-expanded="open" :aria-label="`${open ? '收起' : '展开'}详情：${entry.title}`">
                      详情
                      <YdIcon :name="open ? 'morph:chevron-up' : 'morph:chevron-down'" />
                    </YdButton>
                  </template>
                  <p class="adc-changes__detail">
                    {{ entry.detail }}
                  </p>
                </YdCollapsible>
              </div>
              <div class="adc-changes__ops">
                <YdButton size="sm" :variant="entry.keep ? 'secondary' : 'outline'" :disabled="entry.keep" @click="setChangeKeep(entry.id, true)">
                  保留
                </YdButton>
                <YdButton size="sm" :variant="entry.keep ? 'outline' : 'secondary'" :disabled="!entry.keep" @click="setChangeKeep(entry.id, false)">
                  放弃
                </YdButton>
              </div>
            </li>
          </ul>
          <p class="adc-hint">
            放弃的变更保留在列表中便于反悔；「撤销」按步恢复保留状态。
          </p>
        </div>
      </DemoCard>

      <!-- ================= 按钮试验台 ================= -->
      <DemoCard v-else-if="demo.key === 'button-playground'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <div class="adc-bpg">
          <div class="adc-bpg__stage">
            <YdButton
              :variant="buttonVariant"
              :size="buttonSize"
              :loading="buttonLoading"
              :disabled="buttonDisabled"
              @click="runDemoButton"
            >
              {{ buttonLoading ? '加载中…' : '执行操作' }}
            </YdButton>
            <div class="adc-bpg__result" aria-live="polite">
              <p>已点击 {{ buttonClicks }} 次</p>
              <p v-if="lastClickAt" class="adc-hint">
                最近一次：{{ lastClickAt }}
              </p>
              <p v-if="buttonDisabled" class="adc-hint">
                禁用原因：演示配置开启了 disabled。
              </p>
            </div>
          </div>
          <div class="adc-bpg__controls">
            <div class="adc-field">
              <span class="adc-field__label">variant</span>
              <YdSegmented v-model="buttonVariant" :options="buttonVariantOptions" size="sm" aria-label="按钮变体" />
            </div>
            <div class="adc-field">
              <span class="adc-field__label">size</span>
              <YdSegmented v-model="buttonSize" :options="buttonSizeOptions" size="sm" aria-label="按钮尺寸" />
            </div>
            <div class="adc-bpg__switches">
              <div class="adc-switch-field">
                <span>loading</span>
                <YdSwitch v-model="buttonLoading" aria-label="加载状态" />
              </div>
              <div class="adc-switch-field">
                <span>disabled</span>
                <YdSwitch v-model="buttonDisabled" aria-label="禁用状态" />
              </div>
            </div>
          </div>
        </div>
      </DemoCard>

      <!-- ================= 回应条 ================= -->
      <DemoCard v-else-if="demo.key === 'reaction-bar'" :title="demo.title" :description="demo.description" :kind="demoKind(demo.group)">
        <article class="adc-post">
          <header class="adc-post__head">
            <YdAvatar src="" fallback="苏" />
            <div>
              <p class="adc-post__author">
                苏芮 · 发布于 10 分钟前
              </p>
              <p class="adc-post__meta">
                设计周报 #42
              </p>
            </div>
          </header>
          <p class="adc-post__body">
            本周完成了组件走查与暗色主题联调，发现 3 个对比度问题并已修复。下周一开放评审，欢迎大家提前试玩灰度环境。
          </p>
          <footer class="adc-post__foot">
            <div class="adc-reactions" role="group" aria-label="表情回应">
              <YdTooltip v-for="item in reactions" :key="item.key" :text="reactionSummary(item)" side="top">
                <YdButton
                  size="sm"
                  :variant="item.mine ? 'secondary' : 'outline'"
                  :aria-pressed="item.mine"
                  :aria-label="`${item.label}，当前 ${item.count} 人，点击${item.mine ? '取消' : '添加'}我的回应`"
                  @click="toggleReaction(item)"
                >
                  <YdIcon :name="item.icon" />
                  {{ item.count }}
                </YdButton>
              </YdTooltip>
            </div>
            <span class="adc-hint" aria-live="polite">
              {{ mineLabels.length ? `我的回应：${mineLabels.join('、')}` : '还没有我的回应' }} · 共 {{ reactionTotal }} 次
            </span>
          </footer>
        </article>
      </DemoCard>
    </template>
  </div>
</template>

<style scoped>
.adc {
  display: grid;
  gap: var(--yd-content-gap, 16px);
}

/* 头部与筛选 */
.adc-head {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: flex-end;
  justify-content: space-between;
}

.adc-head__text h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: var(--color-text-1);
}

.adc-head__text p {
  margin: 6px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.adc-count {
  margin: -6px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 通用工具 */
.adc-hint {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.adc-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.adc-toolbar__actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.adc-field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.adc-field--plan {
  width: 200px;
}

.adc-field__label {
  font-size: 12.5px;
  font-weight: 500;
  color: var(--color-text-2);
}

.adc-done-text {
  color: var(--color-text-3);
  text-decoration: line-through;
}

.adc-visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  white-space: nowrap;
  clip-path: inset(50%);
}

/* 封面流 */
.adc-cf {
  padding: 8px 0;
  overflow: hidden;
}

.adc-cf__strip {
  display: flex;
  gap: 12px;
  touch-action: pan-y;
  will-change: transform;
}

.adc-cf__card {
  display: grid;
  flex: 0 0 auto;
  gap: 5px;
  width: 168px;
  padding: 10px;
  text-align: left;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius, 10px);
  opacity: 0.55;
  transform: scale(0.9);
  transition: opacity var(--yd-motion-duration-normal) var(--yd-motion-ease), transform var(--yd-motion-duration-normal) var(--yd-motion-ease), border-color var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-cf__card.is-near {
  opacity: 0.8;
  transform: scale(0.95);
}

.adc-cf__card.is-active {
  z-index: 2;
  border-color: var(--yd-emphasis);
  opacity: 1;
  transform: scale(1);
}

.adc-cf__art {
  display: grid;
  place-items: center;
  height: 92px;
  margin-bottom: 2px;
  font-size: 30px;
  color: var(--yd-emphasis);
  background: linear-gradient(160deg, color-mix(in srgb, var(--yd-emphasis) 22%, var(--color-bg-3)), var(--color-fill-2));
  border-radius: 8px;
}

.adc-cf__title {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13.5px;
  font-weight: 600;
  color: var(--color-text-1);
  white-space: nowrap;
}

.adc-cf__subtitle {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.adc-cf__meta {
  font-size: 11px;
  color: var(--color-text-3);
}

.adc-cf__toolbar {
  display: flex;
  gap: 10px;
  align-items: center;
}

.adc-cf__position {
  min-width: 150px;
  font-size: 12.5px;
  color: var(--color-text-2);
}

.adc-cf__kbd {
  margin-left: auto;
}

/* 项目卡片 */
.adc-project {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 16px;
  align-items: center;
}

.adc-project__main {
  display: grid;
  gap: 12px;
  justify-items: start;
}

.adc-project__top {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.adc-project__name {
  margin: 0;
  font-size: 15.5px;
  font-weight: 600;
  color: var(--color-text-1);
}

.adc-project__meta {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.adc-project__members {
  display: flex;
  gap: 6px;
  align-items: center;
}

.adc-project__subtasks {
  display: grid;
  gap: 2px;
  width: 100%;
}

.adc-project__ring {
  position: relative;
  width: 96px;
  height: 96px;
}

.adc-project__ring-track {
  stroke: var(--color-fill-3);
}

.adc-project__ring-value {
  transition: stroke-dashoffset var(--yd-motion-duration-slow) var(--yd-motion-ease);
}

.adc-project__ring-text {
  position: absolute;
  inset: 0;
  display: grid;
  gap: 1px;
  place-content: center;
  text-align: center;
}

.adc-project__ring-text strong {
  font-size: 18px;
  font-weight: 650;
  color: var(--color-text-1);
}

.adc-project__ring-text span {
  font-size: 11px;
  color: var(--color-text-3);
}

/* 提示面板 */
.adc-tips-progress {
  display: grid;
  flex: 1;
  gap: 4px;
  min-width: 200px;
}

.adc-tips {
  display: grid;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.adc-tips__row {
  display: flex;
  gap: 6px;
  align-items: flex-start;
  padding: 4px 8px 4px 4px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.adc-tips__row > :first-child {
  flex: 1;
  min-width: 0;
}

.adc-tips__text {
  display: grid;
  gap: 2px;
  font-size: 13.5px;
  color: var(--color-text-1);
}

.adc-tips__detail {
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-3);
}

/* 智能推荐 */
.adc-rec {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 16px;
  align-items: start;
}

.adc-rec__card {
  display: grid;
  gap: 8px;
  padding: 16px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.adc-rec__head {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.adc-rec__score {
  font-size: 12px;
  color: var(--color-text-3);
}

.adc-rec__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-1);
}

.adc-rec__reason {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-2);
}

.adc-rec__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
}

.adc-rec__list {
  display: grid;
  gap: 6px;
}

.adc-rec__row {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: transparent;
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
  transition: background var(--yd-motion-duration-fast) var(--yd-motion-ease), border-color var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-rec__row:hover {
  background: var(--color-fill-1);
}

.adc-rec__row.is-active {
  background: color-mix(in srgb, var(--yd-emphasis) 8%, var(--color-bg-1));
  border-color: var(--yd-emphasis);
}

.adc-rec__row-label {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--color-text-1);
  white-space: nowrap;
}

.adc-rec__row-score {
  flex-shrink: 0;
  font-size: 12px;
  color: var(--color-text-3);
}

/* 邮件草稿 */
.adc-mail {
  display: grid;
  gap: 12px;
}

.adc-mail__grid {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 12px;
}

.adc-mail__warn {
  margin: 0;
  font-size: 12.5px;
  color: var(--yd-status-danger);
}

.adc-mail__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 用量计量 */
.adc-meter {
  display: grid;
  gap: 6px;
}

.adc-meter__track {
  position: relative;
  height: 12px;
  background: var(--color-fill-2);
  border-radius: 999px;
}

.adc-meter__fill {
  width: 0;
  height: 100%;
  border-radius: inherit;
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease), background var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-meter__fill--normal {
  background: var(--yd-emphasis);
}

.adc-meter__fill--warning {
  background: var(--yd-status-warning);
}

.adc-meter__fill--danger {
  background: var(--yd-status-danger);
}

.adc-meter__mark {
  position: absolute;
  top: -3px;
  bottom: -3px;
  width: 2px;
  transform: translateX(-50%);
}

.adc-meter__mark--warning {
  background: var(--yd-status-warning);
}

.adc-meter__mark--danger {
  background: var(--yd-status-danger);
}

.adc-meter__scale {
  position: relative;
  height: 16px;
  font-size: 11px;
  color: var(--color-text-4, var(--color-text-3));
}

.adc-meter__scale > :first-child {
  position: absolute;
  left: 0;
}

.adc-meter__scale > :last-child {
  position: absolute;
  right: 0;
}

.adc-meter__scale-mark {
  position: absolute;
  white-space: nowrap;
  transform: translateX(-50%);
}

.adc-meter__scale-mark--warning {
  color: var(--yd-status-warning);
}

.adc-meter__scale-mark--danger {
  color: var(--yd-status-danger);
}

/* 紧凑分解 */
.adc-bd {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.adc-bd__total {
  display: inline-flex;
  gap: 6px;
  align-items: center;
}

.adc-bd__row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.adc-bd__icon {
  flex-shrink: 0;
  font-size: 15px;
  color: var(--color-text-3);
}

.adc-bd__name {
  flex-shrink: 0;
  min-width: 72px;
  font-size: 13px;
  color: var(--color-text-1);
}

.adc-bd__bar {
  display: block;
  flex: 1;
  min-width: 96px;
  height: 6px;
  overflow: hidden;
  background: var(--color-fill-2);
  border-radius: 999px;
}

.adc-bd__bar i {
  display: block;
  height: 100%;
  background: var(--yd-emphasis);
  border-radius: inherit;
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.adc-bd__value {
  flex-shrink: 0;
  min-width: 56px;
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-2);
  text-align: right;
}

/* 实验卡片 */
.adc-exp {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.adc-exp__variant {
  display: grid;
  gap: 4px;
  padding: 12px 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
  transition: border-color var(--yd-motion-duration-fast) var(--yd-motion-ease), background var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-exp__variant.is-winner {
  background: color-mix(in srgb, var(--yd-emphasis) 6%, var(--color-bg-2));
  border-color: var(--yd-emphasis);
}

.adc-exp__head {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  justify-content: space-between;
}

.adc-exp__head strong {
  font-size: 13.5px;
  color: var(--color-text-1);
}

.adc-exp__rate {
  margin: 0;
  font-size: 21px;
  font-weight: 650;
  font-variant-numeric: tabular-nums;
  color: var(--color-text-1);
}

/* KPI 柱状图与读出面板 */
.adc-kpi__stage {
  overflow-x: auto;
}

.adc-kpi__canvas {
  min-width: 520px;
}

.adc-kpi__canvas svg {
  display: block;
}

.adc-kpi__bar {
  fill: color-mix(in srgb, var(--yd-emphasis) 30%, var(--color-fill-3));
  transition: fill var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-kpi__bar.is-focus {
  fill: var(--yd-emphasis);
}

.adc-kpi__axis {
  display: flex;
  margin-top: 4px;
}

.adc-kpi__axis span {
  flex: 1;
  font-size: 11px;
  color: var(--color-text-4, var(--color-text-3));
  text-align: center;
}

.adc-kpi__readout {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-height: 40px;
  padding: 10px 12px;
  font-size: 13px;
  color: var(--color-text-2);
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.adc-kpi__readout strong {
  color: var(--color-text-1);
}

/* 构成分解图 */
.adc-stack {
  display: flex;
  height: 40px;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.adc-stack__segment {
  height: 100%;
  transition: opacity var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-stack__segment--0 {
  background: var(--yd-emphasis);
}

.adc-stack__segment--1 {
  background: color-mix(in srgb, var(--yd-emphasis) 72%, var(--color-fill-3));
}

.adc-stack__segment--2 {
  background: color-mix(in srgb, var(--yd-emphasis) 45%, var(--color-fill-3));
}

.adc-stack__segment--3 {
  background: color-mix(in srgb, var(--yd-emphasis) 24%, var(--color-fill-3));
}

.adc-stack__segment.is-dim {
  opacity: 0.4;
}

.adc-src-legend {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.adc-src-legend__item {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  padding: 5px 10px;
  font-size: 12.5px;
  color: var(--color-text-2);
  border: 1px solid var(--color-border-2);
  border-radius: 999px;
  transition: background var(--yd-motion-duration-fast) var(--yd-motion-ease), border-color var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.adc-src-legend__item:hover {
  background: var(--color-fill-1);
}

.adc-src-legend__item.is-active {
  color: var(--color-text-1);
  border-color: var(--yd-emphasis);
}

.adc-src-legend__item strong {
  font-variant-numeric: tabular-nums;
}

.adc-src-legend__swatch {
  width: 10px;
  height: 10px;
  border-radius: 3px;
}

.adc-src-legend__swatch--0 {
  background: var(--yd-emphasis);
}

.adc-src-legend__swatch--1 {
  background: color-mix(in srgb, var(--yd-emphasis) 72%, var(--color-fill-3));
}

.adc-src-legend__swatch--2 {
  background: color-mix(in srgb, var(--yd-emphasis) 45%, var(--color-fill-3));
}

.adc-src-legend__swatch--3 {
  background: color-mix(in srgb, var(--yd-emphasis) 24%, var(--color-fill-3));
}

/* 可编辑数据表 */
.adc-table-wrap {
  overflow-x: auto;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.adc-table {
  width: 100%;
  min-width: 680px;
  font-size: 13px;
  border-collapse: collapse;
}

.adc-table th {
  padding: 8px 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
  text-align: left;
  background: var(--color-bg-2);
  border-bottom: 1px solid var(--color-border-2);
}

.adc-table td {
  padding: 6px 10px;
  border-bottom: 1px solid var(--color-border-3, var(--color-border-2));
}

.adc-table tbody tr:last-child td {
  border-bottom: 0;
}

.adc-table tr.is-editing td {
  background: color-mix(in srgb, var(--yd-emphasis) 5%, var(--color-bg-1));
}

.adc-table__check {
  width: 48px;
}

.adc-table__budget {
  font-variant-numeric: tabular-nums;
}

.adc-table__ops {
  width: 120px;
  text-align: right;
  white-space: nowrap;
}

.adc-table__sort {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  color: inherit;
}

.adc-table__number {
  width: 160px;
}

/* 变更摘要 */
.adc-summary {
  display: grid;
  gap: 12px;
}

.adc-summary__totals {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  font-size: 13px;
  color: var(--color-text-2);
}

.adc-summary__added {
  font-style: normal;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--yd-status-success);
}

.adc-summary__removed {
  font-style: normal;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--yd-status-danger);
}

.adc-changes {
  display: grid;
  gap: 6px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.adc-changes__row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 8px;
}

.adc-changes__row.is-dropped {
  border-style: dashed;
  opacity: 0.65;
}

.adc-changes__main {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

.adc-changes__title {
  font-size: 13px;
  color: var(--color-text-1);
}

.adc-changes__count {
  display: inline-flex;
  gap: 6px;
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.adc-changes__detail {
  flex-basis: 100%;
  padding-left: 2px;
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.adc-changes__ops {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

/* 按钮试验台 */
.adc-bpg {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.adc-bpg__stage {
  display: grid;
  gap: 12px;
  place-content: start;
  justify-items: start;
  padding: 18px;
  background: var(--color-bg-2);
  border: 1px dashed var(--color-border-2);
  border-radius: 10px;
}

.adc-bpg__result p {
  margin: 0;
  font-size: 13px;
  color: var(--color-text-2);
}

.adc-bpg__controls {
  display: grid;
  gap: 12px;
  align-content: start;
}

.adc-bpg__switches {
  display: flex;
  gap: 20px;
}

.adc-switch-field {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--color-text-2);
}

/* 回应条 */
.adc-post {
  display: grid;
  gap: 10px;
  padding: 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.adc-post__head {
  display: flex;
  gap: 10px;
  align-items: center;
}

.adc-post__author {
  margin: 0;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-1);
}

.adc-post__meta {
  margin: 1px 0 0;
  font-size: 12px;
  color: var(--color-text-3);
}

.adc-post__body {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.7;
  color: var(--color-text-2);
}

.adc-post__foot {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.adc-reactions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

/* 窄屏适配（统一 768px 断点） */
@media (width <= 768px) {
  .adc-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .adc-cf__kbd {
    display: none;
  }

  .adc-project,
  .adc-rec,
  .adc-mail__grid,
  .adc-exp,
  .adc-bpg {
    grid-template-columns: 1fr;
  }

  .adc-project__ring {
    justify-self: center;
  }

  .adc-bd__bar {
    flex-basis: 100%;
    order: 5;
  }

  .adc-changes__ops {
    flex-basis: 100%;
    justify-content: flex-end;
  }
}
</style>
