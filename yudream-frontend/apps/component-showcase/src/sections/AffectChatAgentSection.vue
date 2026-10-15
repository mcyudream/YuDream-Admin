<script setup lang="ts">
import type { YdChatCitation, YdChatMessage } from '@yudream/components'
import { useYdToast, YdAlert, YdAvatar, YdBubble, YdButton, YdChatActions, YdChatLoading, YdChatReasoning, YdChatSender, YdCitationList, YdEmpty, YdIcon, YdInput, YdLabel, YdList, YdListRow, YdLoading, YdProgress, YdSegmented, YdSelect, YdSwitch, YdTag, YdTextarea } from '@yudream/components'
import { computed, onBeforeUnmount, ref } from 'vue'
import DemoCard from '@/components/DemoCard.vue'

// AffectUI 目录对照演示：Chat 10 项、Agents 7 项。
// 全部交互使用公开 Yd 组件与页面本地状态，不发起网络请求，也不连接真实 Agent 运行时。

const toast = useYdToast()

/** 所有演示延时统一登记，卸载时清理，避免离开页面后继续写入状态 */
const pendingTimers = new Set<ReturnType<typeof setTimeout>>()
function demoTimeout(run: () => void, delay: number) {
  const id = setTimeout(() => {
    pendingTimers.delete(id)
    run()
  }, delay)
  pendingTimers.add(id)
}
onBeforeUnmount(() => {
  for (const id of pendingTimers) {
    clearTimeout(id)
  }
  pendingTimers.clear()
})

function formatTokens(value: number): string {
  return value >= 1000 ? `${(value / 1000).toFixed(1)}k` : String(value)
}

/** 演示头像没有真实图片，统一传入空地址走 fallback 文案 */
const EMPTY_AVATAR_SRC = ''

// ========== Chat · AI Answer ==========
const answerQuestion: YdChatMessage = { role: 'user', content: '平台插件的权限码应该怎么设计？' }
const AI_ANSWER_FULL = '权限码建议按「资源 + 动作」两段设计：资源段对应插件内的业务对象，动作段必须使用平台既定词表（查看、操作、危险）。查看与操作权限按资源拆分，删除、发布等危险动作单独成码，便于在角色上做最小授权。平台配置闸门控制插件能力开关，用例应用闸门负责每次调用的校验，两层都通过才放行。'
const answerMessage = ref<YdChatMessage>({ id: 'affect-answer', role: 'assistant', content: AI_ANSWER_FULL })
const answerStreaming = computed(() => !!answerMessage.value.pending)

function replayAnswer() {
  if (answerStreaming.value) {
    return
  }
  let cursor = 0
  answerMessage.value = { id: 'affect-answer', role: 'assistant', content: '', pending: true }
  const step = () => {
    cursor = Math.min(cursor + 16, AI_ANSWER_FULL.length)
    answerMessage.value = {
      id: 'affect-answer',
      role: 'assistant',
      content: AI_ANSWER_FULL.slice(0, cursor),
      pending: cursor < AI_ANSWER_FULL.length,
    }
    if (cursor < AI_ANSWER_FULL.length) {
      demoTimeout(step, 90)
    }
  }
  demoTimeout(step, 240)
}

// ========== Chat · Chat Composer ==========
const composerText = ref('')
const composerLoading = ref(false)
const composerSuggestions = ['总结今天的平台动态', '帮我起草一条发布公告']
interface ComposerLogItem { id: string, text: string, at: string }
const composerLog = ref<ComposerLogItem[]>([])
let composerSeq = 0

function sendComposer(text: string) {
  composerLoading.value = true
  composerSeq += 1
  const entry: ComposerLogItem = { id: `composer-${composerSeq}`, text, at: '刚刚' }
  demoTimeout(() => {
    composerLoading.value = false
    composerLog.value = [entry, ...composerLog.value].slice(0, 4)
  }, 1400)
}

// ========== Chat · Thinking State ==========
const reasoningContent = '先确认权限码的资源段来自插件贡献的菜单与操作集合，再核对动作词表……查看与操作分开成码之后，角色授权的粒度才够细……最后补上危险动作需要二次确认的约定。'
const reasoningPending = ref(false)

function rerunReasoning() {
  if (reasoningPending.value) {
    return
  }
  reasoningPending.value = true
  demoTimeout(() => {
    reasoningPending.value = false
  }, 2200)
}

// ========== Chat · Writing Assistant ==========
const draftText = ref('这个功能挺好用的，大家觉得咋样就咋办吧，反正都行。')
interface WritingSuggestion { before: string, after: string, reason: string }
const writingSuggestion = ref<WritingSuggestion>()
const writingApplied = ref(false)
const writingBusy = ref(false)

function suggestWriting() {
  if (writingBusy.value) {
    return
  }
  writingBusy.value = true
  writingApplied.value = false
  demoTimeout(() => {
    writingSuggestion.value = {
      before: draftText.value,
      after: '该功能整体符合预期：交互路径清晰、默认值合理。建议保留当前方案，并在发布说明中补充使用示例。',
      reason: '口语化表述改为「结论 + 依据」',
    }
    writingBusy.value = false
  }, 900)
}

function applyWriting() {
  if (!writingSuggestion.value) {
    return
  }
  draftText.value = writingSuggestion.value.after
  writingApplied.value = true
  writingSuggestion.value = undefined
}

function dismissWriting() {
  writingSuggestion.value = undefined
}

// ========== Chat · Thinking Loader ==========
const THINKING_STAGES = ['检索平台知识库…', '比对权限规范条文…', '组织回答结构…'] as const
const thinkingStage = ref(-1)
const thinkingRunning = computed(() => thinkingStage.value >= 0 && thinkingStage.value < THINKING_STAGES.length)
const thinkingStageText = computed(() => {
  const index = thinkingStage.value
  return index >= 0 && index < THINKING_STAGES.length ? THINKING_STAGES[index] ?? '' : ''
})

function startThinking() {
  if (thinkingRunning.value) {
    return
  }
  thinkingStage.value = 0
  const advance = () => {
    if (thinkingStage.value < THINKING_STAGES.length - 1) {
      thinkingStage.value += 1
      demoTimeout(advance, 900)
    }
    else {
      demoTimeout(() => {
        thinkingStage.value = THINKING_STAGES.length
      }, 900)
    }
  }
  demoTimeout(advance, 900)
}

// ========== Chat · Cited Answer ==========
const citedSources: YdChatCitation[] = [
  {
    title: '平台能力双闸门机制',
    path: '架构设计 / 能力闸门',
    spaceName: '平台知识库',
    excerpt: '项目配置闸门决定能力是否注册端点、恢复数据或连接外部资源；用例应用闸门在每次调用时重新校验当前上下文是否仍然满足条件……',
  },
  {
    title: '权限码设计规范',
    path: '平台规范 / 权限码',
    spaceName: '平台知识库',
    excerpt: '权限码末段动作必须使用既定词表：查看、操作、危险……查看与操作权限按资源拆分，危险权限单独成码并要求确认。',
  },
]
const openedSource = ref<YdChatCitation>()

function toggleSource(citation: YdChatCitation) {
  openedSource.value = openedSource.value?.title === citation.title ? undefined : citation
}

// ========== Chat · Message Actions ==========
const actionsMode = ref<'text' | 'icon'>('text')
const actionsModeOptions: { label: string, value: 'text' | 'icon' }[] = [
  { label: '完整', value: 'text' },
  { label: '纯图标', value: 'icon' },
]
const messageExtraActions = [{ key: 'note', label: '加入笔记', icon: 'morph:notebook-pen' }]
const feedbackValue = ref<'like' | 'dislike' | null>(null)
const feedbackLabel = computed(() => feedbackValue.value === 'like' ? '已点赞' : feedbackValue.value === 'dislike' ? '已点踩' : '未反馈')

// ========== Chat · Model Picker ==========
const modelGroups = [
  {
    label: '通用对话',
    options: [
      { label: 'YD Chat Lite', value: 'yd-chat-lite' },
      { label: 'YD Chat Pro', value: 'yd-chat-pro' },
    ],
  },
  {
    label: '推理增强',
    options: [
      { label: 'YD Reasoner', value: 'yd-reasoner' },
    ],
  },
]
interface ModelInfo { label: string, context: string, capabilities: string[], note: string }
const modelCatalog: Record<string, ModelInfo> = {
  'yd-chat-lite': { label: 'YD Chat Lite', context: '32k', capabilities: ['流式输出', '工具调用'], note: '低延迟档，适合轻量问答与高频短交互。' },
  'yd-chat-pro': { label: 'YD Chat Pro', context: '128k', capabilities: ['流式输出', '工具调用', '视觉输入'], note: '均衡档，默认推荐；长会话与附件场景表现稳定。' },
  'yd-reasoner': { label: 'YD Reasoner', context: '128k', capabilities: ['深度推理', '工具调用'], note: '先推理后作答，适合规范比对、复杂分析类问题。' },
}
const selectedModel = ref('yd-chat-pro')
const selectedModelInfo = computed(() => modelCatalog[selectedModel.value])

// ========== Chat · Comments Panel ==========
interface AffectComment { id: string, author: string, role: string, time: string, content: string, resolved: boolean }
const comments = ref<AffectComment[]>([
  { id: 'cm-1', author: '竹隐', role: '评审', time: '10:02', content: '引用出处建议直接链接到权限模型章节，便于核对原文。', resolved: false },
  { id: 'cm-2', author: '陈叙', role: '维护者', time: '10:15', content: '第二段的闸门顺序写反了，需要调整。', resolved: false },
  { id: 'cm-3', author: '竹隐', role: '评审', time: '11:40', content: '示例代码风格符合平台约定。', resolved: true },
])
type CommentFilter = 'all' | 'open' | 'resolved'
const commentFilter = ref<CommentFilter>('open')
const commentFilterOptions: { label: string, value: CommentFilter }[] = [
  { label: '全部', value: 'all' },
  { label: '待处理', value: 'open' },
  { label: '已解决', value: 'resolved' },
]
const visibleComments = computed(() => comments.value.filter(comment =>
  commentFilter.value === 'all'
  || (commentFilter.value === 'open' ? !comment.resolved : comment.resolved),
))
const resolvedCount = computed(() => comments.value.filter(comment => comment.resolved).length)
const resolveRatio = computed(() => Math.round(resolvedCount.value / comments.value.length * 100))

function toggleComment(comment: AffectComment) {
  comment.resolved = !comment.resolved
}

// ========== Chat · Comment Thread ==========
interface ThreadReply { id: string, author: string, content: string, time: string }
const threadResolved = ref(false)
const threadReplies = ref<ThreadReply[]>([
  { id: 'rp-1', author: '陈叙', content: '同意拆分。已经同步了最新的词表版本，导出暂时挂在危险动作下。', time: '10:26' },
])
const replyDraft = ref('')
let replySeq = 1

function sendReply() {
  const content = replyDraft.value.trim()
  if (!content) {
    return
  }
  replySeq += 1
  threadReplies.value.push({ id: `rp-${replySeq}`, author: '我', content, time: '刚刚' })
  replyDraft.value = ''
}

function toggleThread() {
  threadResolved.value = !threadResolved.value
}

// ========== Agents · Agent Inbox ==========
type InboxStatus = 'awaiting' | 'running' | 'done'
interface InboxTask { id: string, agent: string, icon: string, title: string, status: InboxStatus, time: string }
const inboxTasks = ref<InboxTask[]>([
  { id: 'tk-1', agent: '发布助手', icon: 'morph:rocket', title: '整理本周更新日志草稿', status: 'awaiting', time: '09:41' },
  { id: 'tk-2', agent: '巡检助手', icon: 'morph:shield-check', title: '检查插件依赖版本漂移', status: 'running', time: '10:12' },
  { id: 'tk-3', agent: '文档助手', icon: 'morph:book-open', title: '补全引用来源与原文链接', status: 'done', time: '昨天' },
  { id: 'tk-4', agent: '发布助手', icon: 'morph:rocket', title: '草拟发布公告初稿', status: 'awaiting', time: '11:05' },
])
type InboxFilter = 'all' | InboxStatus
const inboxFilter = ref<InboxFilter>('all')
const inboxStatusMeta: Record<InboxStatus, { label: string, variant: 'default' | 'secondary' | 'outline' }> = {
  awaiting: { label: '待审批', variant: 'default' },
  running: { label: '运行中', variant: 'secondary' },
  done: { label: '已完成', variant: 'outline' },
}
function inboxCount(status: InboxStatus): number {
  return inboxTasks.value.filter(task => task.status === status).length
}
const inboxFilterOptions = computed<{ label: string, value: InboxFilter }[]>(() => [
  { label: `全部 ${inboxTasks.value.length}`, value: 'all' },
  { label: `待审批 ${inboxCount('awaiting')}`, value: 'awaiting' },
  { label: `运行中 ${inboxCount('running')}`, value: 'running' },
  { label: `已完成 ${inboxCount('done')}`, value: 'done' },
])
const visibleTasks = computed(() => inboxTasks.value.filter(task =>
  inboxFilter.value === 'all' || task.status === inboxFilter.value,
))

// ========== Agents · Context Window ==========
const CONTEXT_LIMIT = 128_000
const SYSTEM_PROMPT_TOKENS = 3_200
const WORKSPACE_FILE_TOKENS = 18_500
const contextHistory = ref(41_000)
const contextTools = ref(12_800)
const includeWorkspaceFiles = ref(true)
const contextCompressed = ref(false)

const contextUsed = computed(() =>
  SYSTEM_PROMPT_TOKENS + contextHistory.value + contextTools.value
  + (includeWorkspaceFiles.value ? WORKSPACE_FILE_TOKENS : 0))
const contextPercent = computed(() => Math.min(100, Math.round(contextUsed.value / CONTEXT_LIMIT * 100)))
const contextOverThreshold = computed(() => contextPercent.value >= 80)
const contextSegments = computed(() => [
  { label: '系统提示', value: SYSTEM_PROMPT_TOKENS },
  { label: '会话历史', value: contextHistory.value },
  { label: '工具结果', value: contextTools.value },
  { label: '工作区文件', value: includeWorkspaceFiles.value ? WORKSPACE_FILE_TOKENS : 0 },
].map(segment => ({ ...segment, percent: Math.round(segment.value / CONTEXT_LIMIT * 100) })))

function compressHistory() {
  if (contextCompressed.value || contextHistory.value === 0) {
    return
  }
  contextHistory.value = Math.round(contextHistory.value * 0.4)
  contextCompressed.value = true
  toast('演示：历史已压缩，释放上下文空间')
}

// ========== Agents · Memory Panel ==========
interface MemoryItem { id: string, kind: '偏好' | '事实', content: string, paused: boolean }
const memories = ref<MemoryItem[]>([
  { id: 'mem-1', kind: '偏好', content: '回复使用简体中文，代码注释保留中文', paused: false },
  { id: 'mem-2', kind: '事实', content: '主分支为 master，提交信息使用中文', paused: false },
  { id: 'mem-3', kind: '偏好', content: '示例数据优先使用平台权限域', paused: true },
])
const editingMemoryId = ref<string>()
const editingMemoryDraft = ref('')
const lastDeleted = ref<{ item: MemoryItem, index: number }>()

function startMemoryEdit(item: MemoryItem) {
  editingMemoryId.value = item.id
  editingMemoryDraft.value = item.content
}

function saveMemoryEdit() {
  const item = memories.value.find(memory => memory.id === editingMemoryId.value)
  const content = editingMemoryDraft.value.trim()
  if (item && content) {
    item.content = content
  }
  editingMemoryId.value = undefined
}

function cancelMemoryEdit() {
  editingMemoryId.value = undefined
}

function deleteMemory(item: MemoryItem) {
  const index = memories.value.indexOf(item)
  if (index < 0) {
    return
  }
  lastDeleted.value = { item, index }
  memories.value.splice(index, 1)
}

function undoDelete() {
  const snapshot = lastDeleted.value
  if (!snapshot) {
    return
  }
  memories.value.splice(Math.min(snapshot.index, memories.value.length), 0, snapshot.item)
  lastDeleted.value = undefined
}

// ========== Agents · Tool Call Card ==========
type ToolStatus = 'error' | 'running' | 'success'
const toolStatusMeta: Record<ToolStatus, { label: string, variant: 'destructive' | 'secondary' | 'default' }> = {
  error: { label: '执行失败', variant: 'destructive' },
  running: { label: '执行中', variant: 'secondary' },
  success: { label: '已完成', variant: 'default' },
}
const demoTool = ref({
  name: 'read_workspace_file',
  args: '{ "path": "docs/dev/permission-codes.md" }',
  status: 'error' as ToolStatus,
  output: '',
})

function retryTool() {
  if (demoTool.value.status === 'running') {
    return
  }
  demoTool.value.status = 'running'
  demoTimeout(() => {
    demoTool.value.status = 'success'
    demoTool.value.output = '## 6. 危险动作\n\n删除、发布、恢复默认等动作必须单独成码……（本地演示输出，非真实文件读取）'
    toast('演示：工具重试成功')
  }, 1600)
}

// ========== Agents · Approval Card ==========
type ApprovalDecision = 'pending' | 'approved' | 'denied'
const approvalState = ref<ApprovalDecision>('pending')
const approvalCommand = 'pnpm --filter @yudream/components run test:typecheck'

function decideApproval(decision: Exclude<ApprovalDecision, 'pending'>) {
  if (approvalState.value !== 'pending') {
    return
  }
  approvalState.value = decision
  toast(decision === 'approved' ? '演示：已批准，等待宿主执行' : '演示：已拒绝该次调用')
}

function resetApproval() {
  approvalState.value = 'pending'
}

// ========== Agents · Agent Plan ==========
type PlanStatus = 'pending' | 'running' | 'done' | 'error'
interface PlanStep { id: string, title: string, status: PlanStatus }
const planStatusMeta: Record<PlanStatus, { label: string, variant: 'default' | 'secondary' | 'outline' | 'destructive' }> = {
  pending: { label: '待执行', variant: 'secondary' },
  running: { label: '执行中', variant: 'default' },
  done: { label: '已完成', variant: 'outline' },
  error: { label: '执行失败', variant: 'destructive' },
}
const planSteps = ref<PlanStep[]>([
  { id: 'p1', title: '收集权限域文档与既定词表', status: 'done' },
  { id: 'p2', title: '起草回答目录结构', status: 'done' },
  { id: 'p3', title: '补全引用与示例片段', status: 'error' },
  { id: 'p4', title: '整体校对并输出', status: 'pending' },
])
const planRunning = computed(() => planSteps.value.some(step => step.status === 'running'))
const planDoneCount = computed(() => planSteps.value.filter(step => step.status === 'done').length)
const planPercent = computed(() => Math.round(planDoneCount.value / planSteps.value.length * 100))

function movePlanStep(index: number, offset: -1 | 1) {
  const target = index + offset
  if (planRunning.value || target < 0 || target >= planSteps.value.length) {
    return
  }
  const [step] = planSteps.value.splice(index, 1)
  if (!step) {
    return
  }
  planSteps.value.splice(target, 0, step)
}

function retryPlanStep(step: PlanStep) {
  if (planRunning.value || step.status === 'running') {
    return
  }
  step.status = 'running'
  demoTimeout(() => {
    step.status = 'done'
    toast('演示：步骤已完成')
  }, 1500)
}

// ========== Agents · Inline Diff ==========
type DiffDecision = 'pending' | 'accepted' | 'rejected'
interface DiffLine { type: 'add' | 'del' | 'ctx', text: string }
interface DiffHunk { id: string, file: string, decision: DiffDecision, lines: DiffLine[] }
const diffDecisionMeta: Record<DiffDecision, { label: string, variant: 'secondary' | 'default' | 'destructive' }> = {
  pending: { label: '待处理', variant: 'secondary' },
  accepted: { label: '已采纳', variant: 'default' },
  rejected: { label: '已拒绝', variant: 'destructive' },
}
const diffHunks = ref<DiffHunk[]>([
  {
    id: 'dh-1',
    file: 'sections/AffectChatAgentSection.vue',
    decision: 'pending',
    lines: [
      { type: 'ctx', text: 'const mode = ref<AskMode>("ask")' },
      { type: 'add', text: 'const mode = ref<DemoMode>("ask") // 本地演示模式' },
      { type: 'add', text: 'provideYdChatConfig({ reasoningTitle: "演示推理" })' },
    ],
  },
  {
    id: 'dh-2',
    file: 'composables/useDemoState.ts',
    decision: 'pending',
    lines: [
      { type: 'ctx', text: 'export function useDemoState() {' },
      { type: 'del', text: '  const count = ref(0)' },
      { type: 'add', text: '  const count = useStorage("demo-count", 0)' },
      { type: 'ctx', text: '  return { count }' },
    ],
  },
  {
    id: 'dh-3',
    file: 'README.md',
    decision: 'pending',
    lines: [
      { type: 'del', text: '## 演示说明' },
      { type: 'add', text: '## 演示说明（本地状态，不发起网络请求）' },
      { type: 'ctx', text: '所有交互均由公开 Yd 组件承载。' },
    ],
  },
])
const diffDecidedCount = computed(() => diffHunks.value.filter(hunk => hunk.decision !== 'pending').length)
const diffAcceptedAdd = computed(() => diffHunks.value
  .filter(hunk => hunk.decision === 'accepted')
  .reduce((sum, hunk) => sum + hunk.lines.filter(line => line.type === 'add').length, 0))
const diffAcceptedDel = computed(() => diffHunks.value
  .filter(hunk => hunk.decision === 'accepted')
  .reduce((sum, hunk) => sum + hunk.lines.filter(line => line.type === 'del').length, 0))

function decideHunk(hunk: DiffHunk, decision: Exclude<DiffDecision, 'pending'>) {
  hunk.decision = decision
}

function resetHunk(hunk: DiffHunk) {
  hunk.decision = 'pending'
}

function diffLineMark(type: DiffLine['type']): string {
  if (type === 'add') {
    return '+'
  }
  return type === 'del' ? '−' : ' '
}
</script>

<template>
  <div class="affect-section">
    <YdAlert
      class="affect-notice"
      icon="morph:flask"
      title="AffectUI 目录对照 · 本地演示"
      description="以下 17 个条目（Chat 10 项、Agents 7 项）使用公开 Yd 组件与页面本地状态实现，不发起网络请求，也不连接真实 Agent 运行时。"
    />

    <section class="affect-group" aria-labelledby="affect-chat-title">
      <h2 id="affect-chat-title" class="affect-group__title">
        Chat · 会话元件（10）
      </h2>
      <div class="affect-grid">
        <DemoCard class="affect-grid__item affect-grid__item--wide" title="AI Answer" kind="Chat" description="回答气泡 + 操作条（YdBubble）；「重新回答」触发本地分片回放，展示生成中光标与完成态">
          <div class="stack">
            <YdBubble :message="answerQuestion" />
            <YdBubble :message="answerMessage" show-actions feedback @regenerate="replayAnswer" />
            <div class="demo-row">
              <YdButton size="sm" variant="outline" :disabled="answerStreaming" @click="replayAnswer">
                <YdIcon name="morph:refresh-cw" />
                重新回答（本地回放）
              </YdButton>
              <YdTag variant="secondary">
                本地演示，不发起请求
              </YdTag>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item affect-grid__item--wide" title="Chat Composer" kind="Chat" description="YdChatSender 发送器：建议 chips、生成中停止；发送与生成等待均为本地模拟，记录保留在页面状态">
          <div class="stack">
            <YdChatSender
              v-model="composerText"
              :loading="composerLoading"
              :suggestions="composerSuggestions"
              @send="sendComposer"
              @stop="composerLoading = false"
            />
            <YdList v-if="composerLog.length" divided dense class="composer-log">
              <YdListRow v-for="item in composerLog" :key="item.id" grow>
                <span class="composer-log__text">{{ item.text }}</span>
                <YdTag variant="outline">
                  {{ item.at }}
                </YdTag>
              </YdListRow>
            </YdList>
            <YdEmpty v-else icon="morph:inbox" title="还没有发送记录" description="在上方输入并发送，记录会显示在这里。" />
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Thinking State" kind="Chat" description="YdChatReasoning 深度思考块：可展开收起，推理中状态在标题旁显示加载动画">
          <div class="stack">
            <YdChatReasoning :content="reasoningContent" :pending="reasoningPending" default-expanded />
            <div class="demo-row">
              <YdLabel label="推理中">
                <YdSwitch v-model="reasoningPending" />
              </YdLabel>
              <YdButton size="sm" variant="outline" :disabled="reasoningPending" @click="rerunReasoning">
                <YdIcon name="morph:brain" />
                重新推理
              </YdButton>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Writing Assistant" kind="Chat" description="草稿润色建议：生成、应用与放弃都作用于本地草稿，不调用外部服务">
          <div class="stack">
            <div>
              <p class="field-title">
                草稿文本
              </p>
              <YdTextarea v-model="draftText" :rows="3" placeholder="输入需要润色的内容" />
            </div>
            <div class="demo-row">
              <YdButton size="sm" :disabled="writingBusy" @click="suggestWriting">
                <YdIcon name="morph:wand-sparkles" />
                生成润色建议
              </YdButton>
              <YdChatLoading v-if="writingBusy" text="正在生成建议…" />
              <YdTag v-else-if="writingApplied" variant="secondary">
                已应用到草稿
              </YdTag>
            </div>
            <div v-if="writingSuggestion" class="soft-card">
              <div class="writing-suggestion">
                <p class="field-title">
                  原文
                </p>
                <p class="writing-suggestion__text">
                  {{ writingSuggestion.before }}
                </p>
                <p class="field-title">
                  建议
                </p>
                <p class="writing-suggestion__text writing-suggestion__text--after">
                  {{ writingSuggestion.after }}
                </p>
              </div>
              <div class="demo-row">
                <YdTag variant="secondary">
                  {{ writingSuggestion.reason }}
                </YdTag>
                <YdButton size="sm" @click="applyWriting">
                  应用建议
                </YdButton>
                <YdButton size="sm" variant="ghost" @click="dismissWriting">
                  放弃
                </YdButton>
              </div>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Thinking Loader" kind="Chat" description="YdChatLoading 三点加载指示：按阶段推进思考状态，完成后可重新开始">
          <div class="stack">
            <div class="thinking-loader">
              <YdChatLoading v-if="thinkingStage < THINKING_STAGES.length" :text="thinkingStageText || '等待开始…'" />
              <YdTag v-else variant="secondary">
                思考完成，回答已给出
              </YdTag>
              <span class="thinking-loader__step">阶段 {{ Math.min(thinkingStage + 1, THINKING_STAGES.length) }} / {{ THINKING_STAGES.length }}</span>
            </div>
            <YdButton size="sm" variant="outline" :disabled="thinkingRunning" @click="startThinking">
              <YdIcon name="morph:sparkles" />
              {{ thinkingStage < 0 ? '开始思考' : '再来一次' }}
            </YdButton>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item affect-grid__item--wide" title="Cited Answer" kind="Chat" description="带引用标记的回答（YdCitationList）；点选同一条目可展开或收起来源原文，正文标记与列表联动">
          <div class="stack">
            <p class="answer-text">
              平台的插件能力默认关闭，需要同时通过项目配置闸门与用例应用闸门<sup class="answer-text__ref">[1]</sup>；权限码的末段动作必须来自既定词表<sup class="answer-text__ref">[2]</sup>，查看、操作与危险权限按资源拆分。
            </p>
            <YdCitationList :citations="citedSources" label="引用来源（点选展开原文）" @select="toggleSource" />
            <div v-if="openedSource" class="source-panel">
              <div class="demo-row demo-row--tight">
                <strong class="source-panel__title">{{ openedSource.title }}</strong>
                <YdTag variant="secondary">
                  {{ openedSource.spaceName }}
                </YdTag>
                <span class="source-panel__path">{{ openedSource.path }}</span>
                <YdButton size="sm" variant="ghost" class="source-panel__close" @click="openedSource = undefined">
                  <YdIcon name="morph:x" />
                  收起原文
                </YdButton>
              </div>
              <blockquote class="source-panel__excerpt">
                {{ openedSource.excerpt }}
              </blockquote>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Message Actions" kind="Chat" description="YdChatActions 操作条：完整/纯图标切换、复制、重新生成、点赞点踩与自定义「加入笔记」动作">
          <div class="stack">
            <YdSegmented v-model="actionsMode" size="sm" block :options="actionsModeOptions" aria-label="操作条展示模式" />
            <div class="soft-card">
              <YdChatActions
                copy-text="这是可复制的回答正文（演示）。"
                regenerable
                feedback
                :icon-only="actionsMode === 'icon'"
                :items="messageExtraActions"
                @feedback="feedbackValue = $event"
                @regenerate="toast('演示：重新生成回答')"
                @action="toast('演示：已加入笔记')"
              />
              <p class="demo-hint">
                当前反馈状态：{{ feedbackLabel }}；复制与反馈状态保存在组件内部，自定义动作由页面承接。
              </p>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Model Picker" kind="Chat" description="YdSelect 分组模型选择：切换后展示上下文窗口与能力差异（演示目录，不发起请求）">
          <div class="stack">
            <div>
              <p class="field-title">
                对话模型
              </p>
              <YdSelect v-model="selectedModel" :options="modelGroups" />
            </div>
            <div v-if="selectedModelInfo" class="soft-card">
              <div class="demo-row demo-row--tight">
                <strong>{{ selectedModelInfo.label }}</strong>
                <YdTag variant="secondary">
                  上下文 {{ selectedModelInfo.context }}
                </YdTag>
                <YdTag v-for="capability in selectedModelInfo.capabilities" :key="capability" variant="outline">
                  {{ capability }}
                </YdTag>
              </div>
              <p class="demo-hint">
                {{ selectedModelInfo.note }}
              </p>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Comments Panel" kind="Chat" description="行内评审评论：按状态过滤、解决/重新打开，解决进度实时统计">
          <div class="stack">
            <div class="demo-row">
              <YdSegmented v-model="commentFilter" size="sm" :options="commentFilterOptions" aria-label="评论状态过滤" />
              <span class="demo-hint">已解决 {{ resolvedCount }} / {{ comments.length }}</span>
            </div>
            <YdProgress :model-value="resolveRatio" />
            <YdList divided dense>
              <YdListRow v-for="comment in visibleComments" :key="comment.id" grow :class="{ 'comment-row--resolved': comment.resolved }">
                <YdAvatar :src="EMPTY_AVATAR_SRC" :fallback="comment.author" />
                <div class="comment-row__body">
                  <div class="demo-row demo-row--tight">
                    <strong>{{ comment.author }}</strong>
                    <span class="comment-row__meta">{{ comment.role }} · {{ comment.time }}</span>
                    <YdTag v-if="comment.resolved" variant="outline">
                      已解决
                    </YdTag>
                  </div>
                  <p class="comment-row__content">
                    {{ comment.content }}
                  </p>
                </div>
                <YdButton size="sm" variant="ghost" @click="toggleComment(comment)">
                  {{ comment.resolved ? '重新打开' : '解决' }}
                </YdButton>
              </YdListRow>
            </YdList>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Comment Thread" kind="Chat" description="评论串：本地追加回复，整串解决后停止新回复，可重新打开继续讨论">
          <div class="soft-card">
            <div class="demo-row demo-row--tight">
              <strong>权限码词表是否需要拆分「导出」动作？</strong>
              <YdTag v-if="threadResolved" variant="outline">
                已解决
              </YdTag>
              <YdButton size="sm" variant="ghost" class="thread-resolve" @click="toggleThread">
                {{ threadResolved ? '重新打开' : '解决讨论' }}
              </YdButton>
            </div>
            <YdList divided dense class="thread-list">
              <YdListRow grow>
                <YdAvatar :src="EMPTY_AVATAR_SRC" fallback="竹隐" />
                <div class="comment-row__body">
                  <div class="demo-row demo-row--tight">
                    <strong>竹隐</strong>
                    <span class="comment-row__meta">发起人 · 10:02</span>
                  </div>
                  <p class="comment-row__content">
                    现在发布和下载混在一起了，导出要不要单独成码？
                  </p>
                </div>
              </YdListRow>
              <YdListRow v-for="reply in threadReplies" :key="reply.id" grow>
                <YdAvatar :src="EMPTY_AVATAR_SRC" :fallback="reply.author" />
                <div class="comment-row__body">
                  <div class="demo-row demo-row--tight">
                    <strong>{{ reply.author }}</strong>
                    <span class="comment-row__meta">{{ reply.time }}</span>
                  </div>
                  <p class="comment-row__content">
                    {{ reply.content }}
                  </p>
                </div>
              </YdListRow>
            </YdList>
            <div class="stack">
              <YdTextarea v-model="replyDraft" :rows="2" :disabled="threadResolved" placeholder="回复这条讨论…" aria-label="回复内容" />
              <div class="demo-row">
                <YdButton size="sm" :disabled="!replyDraft.trim() || threadResolved" @click="sendReply">
                  回复
                </YdButton>
                <span class="demo-hint">{{ threadResolved ? '讨论已解决，重新打开后可继续回复。' : '回复仅追加到本地演示线程。' }}</span>
              </div>
            </div>
          </div>
        </DemoCard>
      </div>
    </section>

    <section class="affect-group" aria-labelledby="affect-agents-title">
      <h2 id="affect-agents-title" class="affect-group__title">
        Agents · 智能体元件（7）
      </h2>
      <div class="affect-grid">
        <DemoCard class="affect-grid__item affect-grid__item--wide" title="Agent Inbox" kind="Agents" description="智能体任务收件箱：按状态过滤，计数显示在过滤项上，处理动作由本地状态承接">
          <div class="stack">
            <YdSegmented v-model="inboxFilter" size="sm" block :options="inboxFilterOptions" aria-label="任务状态过滤" />
            <YdList divided dense>
              <YdListRow v-for="task in visibleTasks" :key="task.id" grow>
                <span class="task-icon">
                  <YdIcon :name="task.icon" />
                </span>
                <div class="comment-row__body">
                  <strong>{{ task.title }}</strong>
                  <span class="comment-row__meta">{{ task.agent }} · {{ task.time }}</span>
                </div>
                <YdTag :variant="inboxStatusMeta[task.status].variant">
                  {{ inboxStatusMeta[task.status].label }}
                </YdTag>
                <YdButton v-if="task.status === 'awaiting'" size="sm" variant="outline" @click="toast(`演示：打开任务「${task.title}」`)">
                  去处理
                </YdButton>
              </YdListRow>
            </YdList>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Context Window" kind="Agents" description="上下文占用投影：分项进度、工作区文件开关与历史压缩，阈值告警按本地数值计算">
          <div class="stack">
            <div class="demo-row demo-row--tight">
              <strong>已用 {{ formatTokens(contextUsed) }} / {{ formatTokens(CONTEXT_LIMIT) }}</strong>
              <YdTag :variant="contextOverThreshold ? 'destructive' : 'secondary'">
                占用 {{ contextPercent }}%
              </YdTag>
              <YdTag v-if="contextCompressed" variant="outline">
                历史已压缩
              </YdTag>
            </div>
            <YdProgress :model-value="contextPercent" />
            <dl class="context-breakdown">
              <div v-for="segment in contextSegments" :key="segment.label" class="context-breakdown__row">
                <dt>{{ segment.label }}</dt>
                <dd class="context-breakdown__bar">
                  <YdProgress :model-value="segment.percent" />
                </dd>
                <dd class="context-breakdown__value">
                  {{ formatTokens(segment.value) }}
                </dd>
              </div>
            </dl>
            <div class="demo-row">
              <YdLabel label="包含工作区文件">
                <YdSwitch v-model="includeWorkspaceFiles" />
              </YdLabel>
              <YdButton size="sm" variant="outline" :disabled="contextCompressed || contextHistory === 0" @click="compressHistory">
                <YdIcon name="morph:eraser" />
                压缩历史
              </YdButton>
            </div>
            <YdAlert
              v-if="contextOverThreshold"
              icon="morph:triangle-alert"
              title="上下文即将用尽"
              description="建议压缩历史会话或暂时排除工作区文件，再继续本轮任务。"
            />
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item affect-grid__item--wide" title="Memory Panel" kind="Agents" description="智能体记忆：行内编辑、暂停生效与删除撤销，全部状态保存在当前页面">
          <div class="stack">
            <YdList divided dense>
              <YdListRow v-for="memory in memories" :key="memory.id" wrap grow>
                <YdTag variant="secondary">
                  {{ memory.kind }}
                </YdTag>
                <template v-if="editingMemoryId === memory.id">
                  <YdInput v-model="editingMemoryDraft" class="memory-edit" aria-label="编辑记忆内容" placeholder="记忆内容" />
                  <YdButton size="sm" :disabled="!editingMemoryDraft.trim()" @click="saveMemoryEdit">
                    保存
                  </YdButton>
                  <YdButton size="sm" variant="ghost" @click="cancelMemoryEdit">
                    取消
                  </YdButton>
                </template>
                <template v-else>
                  <span class="memory-row__content" :class="{ 'affect-dim': memory.paused }">{{ memory.content }}</span>
                  <div class="demo-row demo-row--tight memory-row__controls">
                    <YdLabel label="暂停">
                      <YdSwitch v-model="memory.paused" />
                    </YdLabel>
                    <YdButton size="icon-sm" variant="ghost" title="编辑记忆" aria-label="编辑记忆" @click="startMemoryEdit(memory)">
                      <YdIcon name="morph:pencil" />
                    </YdButton>
                    <YdButton size="icon-sm" variant="ghost" title="删除记忆" aria-label="删除记忆" @click="deleteMemory(memory)">
                      <YdIcon name="morph:trash-2" />
                    </YdButton>
                  </div>
                </template>
              </YdListRow>
            </YdList>
            <div v-if="lastDeleted" class="undo-bar">
              <span>已删除 1 条记忆，撤销可恢复到原位置。</span>
              <YdButton size="sm" variant="outline" @click="undoDelete">
                <YdIcon name="morph:undo-2" />
                撤销
              </YdButton>
            </div>
            <p class="demo-hint">
              暂停的记忆保留内容但不再参与回答；再次删除会覆盖上一条的撤销机会。
            </p>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Tool Call Card" kind="Agents" description="工具调用卡片：失败后重试驱动状态流转（本地模拟），输入参数与输出随状态展示">
          <div class="soft-card tool-card">
            <div class="demo-row demo-row--tight">
              <span class="task-icon">
                <YdIcon name="morph:file-search" />
              </span>
              <code class="tool-card__name">{{ demoTool.name }}</code>
              <YdTag :variant="toolStatusMeta[demoTool.status].variant">
                {{ toolStatusMeta[demoTool.status].label }}
              </YdTag>
            </div>
            <pre class="tool-card__args">{{ demoTool.args }}</pre>
            <div v-if="demoTool.status === 'running'" class="tool-card__running">
              <YdLoading :size="20" text="执行中" />
            </div>
            <pre v-else-if="demoTool.status === 'success'" class="tool-card__output">{{ demoTool.output }}</pre>
            <div v-else class="demo-row">
              <span class="tool-card__error">工具返回失败（本地演示的初始状态）。</span>
              <YdButton size="sm" variant="outline" @click="retryTool">
                <YdIcon name="morph:rotate-cw" />
                重试
              </YdButton>
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item" title="Approval Card" kind="Agents" description="敏感操作审批卡：批准/拒绝仅提交决策，宿主侧仍会重新校验；可重置后重新演示">
          <div class="soft-card">
            <div class="demo-row demo-row--tight">
              <span class="task-icon">
                <YdIcon name="morph:shield-check" />
              </span>
              <strong>运行测试命令</strong>
              <YdTag v-if="approvalState === 'approved'" variant="default">
                已批准
              </YdTag>
              <YdTag v-else-if="approvalState === 'denied'" variant="destructive">
                已拒绝
              </YdTag>
              <YdTag v-else variant="secondary">
                等待确认
              </YdTag>
            </div>
            <p class="demo-hint">
              工具希望在工作区执行以下命令：
            </p>
            <pre class="tool-card__args">{{ approvalCommand }}</pre>
            <template v-if="approvalState === 'pending'">
              <div class="demo-row">
                <YdButton size="sm" @click="decideApproval('approved')">
                  批准执行
                </YdButton>
                <YdButton size="sm" variant="destructive" @click="decideApproval('denied')">
                  拒绝
                </YdButton>
              </div>
              <p class="demo-hint">
                批准只提交决策，命令不会在演示页面执行。
              </p>
            </template>
            <template v-else>
              <p class="demo-hint">
                {{ approvalState === 'approved' ? '已批准：宿主将重新校验权限并恢复对应调用。' : '已拒绝：该次调用取消，不产生任何执行。' }}
              </p>
              <YdButton size="sm" variant="ghost" @click="resetApproval">
                <YdIcon name="morph:rotate-ccw" />
                重置演示
              </YdButton>
            </template>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item affect-grid__item--wide" title="Agent Plan" kind="Agents" description="执行计划：步骤上移/下移重排（执行中锁定），失败步骤可重试并本地完成">
          <div class="stack">
            <YdList divided dense>
              <YdListRow v-for="(step, index) in planSteps" :key="step.id" grow>
                <span class="plan-step__index">{{ index + 1 }}</span>
                <span class="plan-step__title" :class="{ 'affect-dim': step.status === 'pending' }">{{ step.title }}</span>
                <YdTag :variant="planStatusMeta[step.status].variant">
                  {{ planStatusMeta[step.status].label }}
                </YdTag>
                <div class="demo-row demo-row--tight plan-step__controls">
                  <YdButton size="icon-sm" variant="ghost" title="上移" :aria-label="`上移：${step.title}`" :disabled="planRunning || index === 0" @click="movePlanStep(index, -1)">
                    <YdIcon name="morph:chevron-up" />
                  </YdButton>
                  <YdButton size="icon-sm" variant="ghost" title="下移" :aria-label="`下移：${step.title}`" :disabled="planRunning || index === planSteps.length - 1" @click="movePlanStep(index, 1)">
                    <YdIcon name="morph:chevron-down" />
                  </YdButton>
                  <YdButton v-if="step.status === 'error'" size="sm" variant="outline" :disabled="planRunning" @click="retryPlanStep(step)">
                    <YdIcon name="morph:rotate-cw" />
                    重试
                  </YdButton>
                  <YdChatLoading v-if="step.status === 'running'" text="执行中…" />
                </div>
              </YdListRow>
            </YdList>
            <div class="demo-row">
              <span class="demo-hint">{{ planDoneCount }} / {{ planSteps.length }} 步完成</span>
              <YdProgress class="plan-progress" :model-value="planPercent" />
            </div>
          </div>
        </DemoCard>

        <DemoCard class="affect-grid__item affect-grid__item--wide" title="Inline Diff" kind="Agents" description="行内代码差异：逐块接受/拒绝与撤销决定，顶部实时统计采纳后的增删行数">
          <div class="stack">
            <div class="demo-row demo-row--tight">
              <strong>本轮共 {{ diffHunks.length }} 处修改</strong>
              <YdTag variant="secondary">
                已处理 {{ diffDecidedCount }} / {{ diffHunks.length }}
              </YdTag>
              <YdTag v-if="diffDecidedCount" variant="outline">
                采纳后 +{{ diffAcceptedAdd }} −{{ diffAcceptedDel }}
              </YdTag>
            </div>
            <div v-for="hunk in diffHunks" :key="hunk.id" class="soft-card diff-hunk">
              <div class="demo-row demo-row--tight">
                <code class="diff-hunk__file">{{ hunk.file }}</code>
                <YdTag :variant="diffDecisionMeta[hunk.decision].variant">
                  {{ diffDecisionMeta[hunk.decision].label }}
                </YdTag>
                <div class="demo-row demo-row--tight diff-hunk__actions">
                  <template v-if="hunk.decision === 'pending'">
                    <YdButton size="sm" @click="decideHunk(hunk, 'accepted')">
                      接受变更
                    </YdButton>
                    <YdButton size="sm" variant="outline" @click="decideHunk(hunk, 'rejected')">
                      拒绝变更
                    </YdButton>
                  </template>
                  <YdButton v-else size="sm" variant="ghost" @click="resetHunk(hunk)">
                    <YdIcon name="morph:undo-2" />
                    撤销决定
                  </YdButton>
                </div>
              </div>
              <div class="diff-hunk__lines">
                <div v-for="(line, lineIndex) in hunk.lines" :key="lineIndex" class="diff-line" :class="`diff-line--${line.type}`">
                  <span class="diff-line__mark" aria-hidden="true">{{ diffLineMark(line.type) }}</span>
                  <code class="diff-line__text">{{ line.text }}</code>
                </div>
              </div>
            </div>
          </div>
        </DemoCard>
      </div>
    </section>
  </div>
</template>

<style scoped>
.affect-section {
  display: grid;
  gap: 20px;
  min-width: 0;
}

.affect-group {
  display: grid;
  gap: 14px;
}

.affect-group__title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-2);
}

.affect-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 380px), 1fr));
  gap: 16px;
  align-items: start;
}

.affect-grid__item--wide {
  grid-column: 1 / -1;
}

.stack {
  display: grid;
  gap: 12px;
  min-width: 0;
}

.demo-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.demo-row--tight {
  gap: 8px;
}

.demo-hint {
  margin: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.field-title {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 500;
  color: var(--color-text-3);
}

.soft-card {
  display: grid;
  gap: 10px;
  padding: 12px 14px;
  background: var(--color-bg-2);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.affect-dim {
  color: var(--color-text-3);
}

.answer-text {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.8;
  color: var(--color-text-1);
}

.answer-text__ref {
  font-size: 11px;
  color: var(--color-text-3);
}

.source-panel {
  display: grid;
  gap: 8px;
  padding: 12px 14px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.source-panel__title {
  font-size: 13px;
  color: var(--color-text-1);
}

.source-panel__path {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 12px;
  color: var(--color-text-3);
  white-space: nowrap;
}

.source-panel__close {
  margin-left: auto;
}

.source-panel__excerpt {
  padding: 10px 14px;
  margin: 0;
  font-size: 12.5px;
  line-height: 1.8;
  color: var(--color-text-2);
  background: var(--color-bg-1);
  border-inline-start: 3px solid var(--color-border-3);
  border-radius: var(--yd-control-radius);
}

.composer-log__text {
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.writing-suggestion {
  display: grid;
  gap: 4px;
}

.writing-suggestion__text {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-text-3);
}

.writing-suggestion__text--after {
  color: var(--color-text-1);
}

.thinking-loader {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  background: var(--color-fill-1);
  border-radius: var(--yd-control-radius);
}

.thinking-loader__step {
  font-size: 12px;
  color: var(--color-text-3);
}

.comment-row__body {
  display: grid;
  gap: 2px;
  min-width: 0;
  font-size: 13px;
  color: var(--color-text-1);
}

.comment-row__meta {
  font-size: 12px;
  color: var(--color-text-3);
}

.comment-row__content {
  margin: 0;
  font-size: 12.5px;
  line-height: 1.6;
  color: var(--color-text-2);
}

.comment-row--resolved .comment-row__content {
  color: var(--color-text-3);
}

.thread-list {
  background: var(--color-bg-1);
  border-radius: var(--yd-control-radius);
}

.thread-resolve {
  margin-left: auto;
}

.task-icon {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: var(--yd-control-radius);
}

.context-breakdown {
  display: grid;
  gap: 8px;
  margin: 0;
}

.context-breakdown__row {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr) 56px;
  gap: 10px;
  align-items: center;
}

.context-breakdown__row dt {
  font-size: 12px;
  color: var(--color-text-3);
}

.context-breakdown__bar {
  margin: 0;
}

.context-breakdown__value {
  margin: 0;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--color-text-2);
  text-align: right;
}

.memory-edit {
  flex: 1;
  min-width: 180px;
}

.memory-row__content {
  min-width: 0;
  font-size: 13px;
  color: var(--color-text-1);
}

.memory-row__controls {
  margin-left: auto;
}

.undo-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  font-size: 12.5px;
  color: var(--color-text-2);
  background: var(--color-fill-1);
  border-radius: var(--yd-control-radius);
}

.tool-card__name {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12.5px;
  color: var(--color-text-1);
}

.tool-card__args {
  padding: 10px 12px;
  margin: 0;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-2);
  white-space: pre;
  background: var(--color-fill-1);
  border-radius: var(--yd-control-radius);
}

.tool-card__output {
  padding: 10px 12px;
  margin: 0;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  color: var(--color-text-1);
  white-space: pre-wrap;
  background: var(--color-fill-1);
  border-radius: var(--yd-control-radius);
}

.tool-card__running {
  padding: 4px 0;
}

.tool-card__error {
  font-size: 12.5px;
  color: var(--yd-status-danger);
}

.plan-step__index {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 22px;
  height: 22px;
  font-size: 12px;
  color: var(--color-text-2);
  background: var(--color-fill-2);
  border-radius: 50%;
}

.plan-step__title {
  min-width: 0;
  font-size: 13px;
  color: var(--color-text-1);
}

.plan-step__controls {
  margin-left: auto;
}

.plan-progress {
  flex: 1;
  min-width: 140px;
}

.diff-hunk__file {
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  color: var(--color-text-2);
  overflow-wrap: anywhere;
}

.diff-hunk__actions {
  margin-left: auto;
}

.diff-hunk__lines {
  display: grid;
  overflow-x: auto;
  font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
  font-size: 12px;
  line-height: 1.7;
  background: var(--color-bg-1);
  border: 1px solid var(--color-border-2);
  border-radius: var(--yd-control-radius);
}

.diff-line {
  display: flex;
  padding: 0 10px;
}

.diff-line--add {
  background: var(--color-fill-1);
  border-inline-start: 2px solid var(--yd-status-success);
}

.diff-line--del {
  background: var(--color-fill-1);
  border-inline-start: 2px solid var(--yd-status-danger);
}

.diff-line__mark {
  flex-shrink: 0;
  width: 16px;
  color: var(--color-text-3);
  white-space: pre;
}

.diff-line--add .diff-line__mark {
  color: var(--yd-status-success);
}

.diff-line--del .diff-line__mark {
  color: var(--yd-status-danger);
}

.diff-line__text {
  color: var(--color-text-1);
  white-space: pre;
}

@media (width <= 768px) {
  .affect-section {
    gap: 14px;
  }

  .affect-grid {
    grid-template-columns: 1fr;
  }

  .memory-row__controls {
    width: 100%;
    margin-left: 0;
  }

  .context-breakdown__row {
    grid-template-columns: 64px minmax(0, 1fr) 48px;
  }
}
</style>
