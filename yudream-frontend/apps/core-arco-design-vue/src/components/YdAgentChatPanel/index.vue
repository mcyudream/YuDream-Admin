<script setup lang="ts">
import type {
  UseYdChatStreamOptions,
  YdAgentSendRequest,
  YdChatAttachment,
  YdChatHistoryTurn,
  YdChatMessage,
  YdChatProtocol,
  YdChatToolCallReply,
  YdChatToolCallRequest,
  YdChatToolEvent,
  YdChatTransport,
} from '@yudream/components'
import type { YdAgentChatSession, YdAgentChatSessionMeta, YdAgentChatSessionStore } from './types'
import {
  provideYdChatConfig,
  useYdPersistentChatStream,
  YdAgentWorkbench,
  YdChatSessionList,
  YdWelcome,
} from '@yudream/components'
import apiAi from '@/api/modules/platform-ai'
import { cancelAiRun, getAiRun, listAiRuns, replyAiFrontendTool } from '@/api/modules/platform-ai-runs'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { projectAiRunActivity, projectAiRunTool } from '@/utils/ai-run-projection'
import { resolveApiFileUrl } from '@/utils/api-file-url'

const props = withDefaults(defineProps<{
  /** 流式端点完整 URL（可为函数读取最新值） */
  endpoint: string | (() => string)
  /** 流协议，默认 agui */
  protocol?: YdChatProtocol
  /** 传输方式，默认 sse */
  transport?: YdChatTransport
  /** 携带历史的最大轮数 */
  historyLimit?: number
  /** 构造请求体，history 为不含本轮问题的历史；缺省为 { question, history, attachments } */
  buildBody?: (question: string, history: YdChatHistoryTurn[], attachments?: YdChatAttachment[]) => Record<string, unknown>
  /** 历史消息序列化；默认由共享流处理器附加工具上下文 */
  historyContent?: (message: YdChatMessage) => string
  /** 会话持久化适配器；不传则为单会话模式（不显示会话管理） */
  sessionStore?: YdAgentChatSessionStore
  /** 保存会话前对消息做裁剪（如剥离工具 payload 中的大字段） */
  sanitizeMessage?: (message: YdChatMessage) => YdChatMessage
  /** 保存会话时采集的业务上下文（随会话持久化，切换会话时经 session-change 带回） */
  sessionMeta?: () => Record<string, unknown>
  /** 空态问候语 */
  welcomeTitle?: string
  /** 空态描述 */
  welcomeDescription?: string
  /** 空态推荐提问 */
  suggestions?: string[]
  /** 输入框占位文案 */
  placeholder?: string
  /** 等待首字节提示 */
  thinkingText?: string
  /** 输入区免责声明 */
  disclaimer?: string
  /** 可选附件类型 */
  accept?: string
  /** v2 客户端工具请求处理器（仅 websocket 传输；如 CMS 画布工具在本端真实执行并回传结果） */
  onToolCallRequest?: (request: YdChatToolCallRequest) => Promise<YdChatToolCallReply>
  durableContextKey: string
  toolsReady?: boolean
}>(), {
  protocol: 'agui',
  transport: 'sse',
  historyLimit: 10,
  buildBody: undefined,
  sessionStore: undefined,
  sanitizeMessage: undefined,
  sessionMeta: undefined,
  welcomeTitle: '今天想让我帮你做什么？',
  welcomeDescription: '',
  suggestions: () => [],
  placeholder: '尽管问，或让我帮你做点什么…',
  thinkingText: '正在思考…',
  disclaimer: '内容由 AI 生成，请核对重要信息',
  accept: 'image/*',
  toolsReady: true,
})

const emits = defineEmits<{
  /** 工具调用事件（TOOL_CALL_START / TOOL_CALL_RESULT 归一化后） */
  tool: [tool: YdChatToolEvent]
  /** 一条回答完成 */
  done: [message: YdChatMessage]
  /** 回答失败 */
  error: [message: YdChatMessage, error: unknown]
  /** 当前会话切换（新会话为 null；切换时带回持久化的 meta） */
  sessionChange: [session: YdAgentChatSession | null]
}>()

provideYdChatConfig({
  placeholder: props.placeholder,
  thinkingText: props.thinkingText,
  disclaimer: props.disclaimer,
})

const sessions = ref<YdAgentChatSessionMeta[]>([])
const sessionsLoading = ref(false)
const sessionDrawerOpen = ref(false)
const activeSession = ref<YdAgentChatSession | null>(null)
const attachments = ref<YdChatAttachment[]>([])
const renameOpen = ref(false)
const renameTitle = ref('')
const renameTarget = ref<{ id: string, title: string }>()
const removeOpen = ref(false)
const removeTarget = ref<{ id: string }>()
const account = useAppAccountStore()
const aiRuntime = useAiAssistantStore()

const streamOptions: UseYdChatStreamOptions = {
  endpoint: () => typeof props.endpoint === 'function' ? props.endpoint() : props.endpoint,
  protocol: props.protocol,
  transport: props.transport,
  historyLimit: props.historyLimit,
  historyContent: props.historyContent,
  buildBody: (question, history, currentAttachments) => props.buildBody
    ? props.buildBody(question, history, currentAttachments)
    : { question, history, attachments: currentAttachments },
  onTool: tool => emits('tool', tool),
  onToolCallRequest: props.onToolCallRequest,
  getWebSocketToken: async () => {
    if (props.protocol !== 'agui') {
      return localStorage.getItem('token') || undefined
    }
    // AG-UI 端点改用一次性握手票据，避免长效会话令牌出现在 URL 中
    const res = await apiAi.issueAguiWsTicket()
    if (!res.data?.ticket) {
      throw new Error('AI 连接没有返回握手票据')
    }
    return res.data.ticket
  },
  onDone: message => emits('done', message),
  onError: (message, error) => emits('error', message, error),
}
function normalizeRun(run: Awaited<ReturnType<typeof getAiRun>>['data']) {
  return { ...run, tools: (run.tools || []).flatMap((tool) => {
    const projected = projectAiRunTool(tool)
    return projected ? [projected] : []
  }), activities: (run.activities || []).flatMap((activity) => {
    const projected = projectAiRunActivity(activity, run.id)
    return projected ? [projected] : []
  }) }
}
const chatStream = useYdPersistentChatStream({
  ...streamOptions,
  ownerKey: () => account.isLogin ? String(account.userId) : '',
  contextKey: () => props.durableContextKey,
  toolsReady: () => props.toolsReady,
  persistence: {
    latest: async (context) => {
      const runs = (await listAiRuns(`cms-canvas:${context}`)).data.sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))
      return runs[0] ? normalizeRun(runs[0]) : undefined
    },
    snapshot: async id => normalizeRun((await getAiRun(id)).data),
    cancel: async id => normalizeRun((await cancelAiRun(id)).data),
    toolResult: async (id, request, reply) => {
      await replyAiFrontendTool(id, request.toolCallId, reply)
    },
    track: (id) => {
      const epoch = aiRuntime.lifecycleVersion()
      void getAiRun(id).then(response => aiRuntime.attachRun(response.data, epoch)).catch((error) => {
        if (epoch === aiRuntime.lifecycleVersion()) {
          aiRuntime.setError(error)
        }
      })
    },
  },
})
const { messages, streaming, send, stop: stopStream } = chatStream
async function stop() {
  try {
    await stopStream()
  }
  catch (error) {
    useYdToast().warning(error instanceof Error ? error.message : '停止任务失败')
  }
}
const harness = computed(() => ({ name: 'AgentScope Harness', runId: chatStream.runId.value || undefined, status: streaming.value ? chatStream.runStatus.value === 'WAITING' ? 'waiting' as const : 'running' as const : chatStream.runStatus.value === 'FAILED' ? 'error' as const : chatStream.runStatus.value === 'CANCELLED' ? 'cancelled' as const : chatStream.runStatus.value === 'COMPLETED' ? 'completed' as const : 'idle' as const }))
const restoreRun = 'restore' in chatStream ? chatStream.restore : undefined

const activeTitle = computed(() => activeSession.value?.title || '新的对话')
const sessionItems = computed(() => sessions.value.map(item => ({
  id: item.id,
  title: item.title,
  pinned: item.pinned,
  messageCount: item.messageCount,
})))

onMounted(() => {
  if (props.sessionStore) {
    void loadSessions()
  }
})

async function loadSessions() {
  if (!props.sessionStore) {
    return
  }
  sessionsLoading.value = true
  try {
    sessions.value = sortSessions(await props.sessionStore.list())
  }
  finally {
    sessionsLoading.value = false
  }
}

function sortSessions(list: YdAgentChatSessionMeta[]) {
  return [...list].sort((a, b) => Number(b.pinned ?? false) - Number(a.pinned ?? false) || (b.updatedAt ?? 0) - (a.updatedAt ?? 0))
}

/** 新建会话：立即持久化一个空会话，保证列表与选中状态一致 */
async function createSession() {
  if (props.durableContextKey && streaming.value) {
    return
  }
  // 切换/新建前先保存当前会话，避免正在生成或刚完成的消息因清空引用而丢失。
  await persistActiveSession()
  stop()
  sessionDrawerOpen.value = false
  if (!props.sessionStore) {
    messages.value = []
    activeSession.value = null
    emits('sessionChange', null)
    return
  }
  const now = Date.now()
  const session: YdAgentChatSession = {
    id: `yd-agent-${now.toString(36)}-${Math.random().toString(36).slice(2, 8)}`,
    title: '新的对话',
    pinned: false,
    updatedAt: now,
    messages: [],
  }
  activeSession.value = session
  messages.value = session.messages
  sessions.value = sortSessions([toMeta(session), ...sessions.value])
  await props.sessionStore.save(session)
  emits('sessionChange', session)
}

async function selectSession(id: string) {
  if (!props.sessionStore || id === activeSession.value?.id) {
    sessionDrawerOpen.value = false
    return
  }
  await persistActiveSession()
  if (!props.durableContextKey) {
    stop()
  }
  sessionDrawerOpen.value = false
  try {
    const session = await props.sessionStore.load(id)
    if (!session) {
      await loadSessions()
      return
    }
    activeSession.value = session
    if (props.durableContextKey && streaming.value) {
      const live = messages.value
      const liveIds = new Set(live.map(message => message.id))
      messages.value = [...session.messages.filter(message => !message.pending && !liveIds.has(message.id)), ...live]
    }
    else {
      messages.value = session.messages
    }
    emits('sessionChange', session)
  }
  catch {
    // 载入失败时保持当前会话
  }
}

function renameSession(item: { id: string, title: string }) {
  renameTarget.value = item
  renameTitle.value = item.title
  renameOpen.value = true
}

async function saveRename() {
  const item = renameTarget.value
  if (!item) {
    return
  }
  if (!props.sessionStore) {
    return
  }
  const title = renameTitle.value.trim()
  if (!title) {
    return
  }
  const session = item.id === activeSession.value?.id
    ? activeSession.value
    : await props.sessionStore.load(item.id)
  if (!session) {
    return
  }
  session.title = title
  session.updatedAt = Date.now()
  await props.sessionStore.save(session)
  await loadSessions()
}

async function pinSession(item: { id: string, pinned?: boolean }) {
  if (!props.sessionStore) {
    return
  }
  const session = item.id === activeSession.value?.id
    ? activeSession.value
    : await props.sessionStore.load(item.id)
  if (!session) {
    return
  }
  session.pinned = !item.pinned
  await props.sessionStore.save(session)
  await loadSessions()
}

function removeSession(item: { id: string }) {
  if (props.durableContextKey && streaming.value && item.id === activeSession.value?.id) {
    useYdToast().warning('请先停止当前运行，再删除会话记录')
    return
  }
  removeTarget.value = item
  removeOpen.value = true
}

async function confirmRemove() {
  const item = removeTarget.value
  if (!props.sessionStore || !item) {
    return
  }
  await props.sessionStore.remove(item.id)
  if (activeSession.value?.id === item.id) {
    activeSession.value = null
    messages.value = []
    emits('sessionChange', null)
  }
  await loadSessions()
}

function toMeta(session: YdAgentChatSession): YdAgentChatSessionMeta {
  const { messages: sessionMessages, ...meta } = session
  return { ...meta, messageCount: sessionMessages.length }
}

function compactText(value: string, maxLength: number) {
  const text = value.replace(/\s+/g, ' ').trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}

/** 每轮问答结束后持久化当前会话（标题/摘要/上下文/消息） */
async function persistActiveSession() {
  if (!props.sessionStore || !activeSession.value) {
    return
  }
  const session = activeSession.value
  const firstUser = session.messages.find(item => item.role === 'user' && item.content.trim())
  const lastAssistant = [...session.messages].reverse().find(item => item.role === 'assistant' && !item.pending)
  if (firstUser && (session.title === '新的对话' || !session.title)) {
    session.title = compactText(firstUser.content, 24) || session.title
  }
  session.preview = compactText(lastAssistant?.content || firstUser?.content || '', 120)
  session.meta = props.sessionMeta?.() ?? session.meta
  session.updatedAt = Date.now()
  const persistMessages = props.sanitizeMessage
    ? session.messages.map(item => props.sanitizeMessage!(item))
    : session.messages
  await props.sessionStore.save({ ...session, messages: persistMessages })
  await loadSessions()
}

async function onSend(text: string, currentAttachments: YdChatAttachment[]) {
  if (props.sessionStore && !activeSession.value) {
    await createSession()
  }
  const owner = String(account.userId)
  const pending = send(text, currentAttachments)
  if (props.durableContextKey) {
    await persistActiveSession()
  }
  await pending
  if (!account.isLogin || owner !== String(account.userId)) {
    return
  }
  attachments.value = []
  await persistActiveSession()
}

/** 供外部快捷指令 / 追问按钮直接发起一轮对话 */
async function sendPrompt(text: string) {
  const content = String(text || '').trim()
  if (content) {
    await onSend(content, [])
  }
}

async function respondToPendingAsk(title: string) {
  if (!('runId' in chatStream) || !chatStream.runId.value) {
    return false
  }
  const snapshot = (await getAiRun(chatStream.runId.value)).data
  const request = snapshot.pendingFrontendTools?.find(tool => tool.name === 'cms.ask.user')
  if (!request) {
    return false
  }
  await replyAiFrontendTool(snapshot.id, request.toolCallId, { ok: true, result: { selectedOption: title, message: `用户选择了：${title}` } })
  if ('acknowledgeTool' in chatStream) {
    chatStream.acknowledgeTool(request.toolCallId, { ok: true, result: { selectedOption: title, message: `用户选择了：${title}` } })
  }
  return true
}

function onAttach(files: File[]) {
  emitsAttachFiles(files)
}

// 附件读取（默认转 dataUrl）由使用方通过 attach 事件自行处理时，可监听该事件；
// 面板提供缺省实现：图片转 dataUrl，便于直接嵌入使用。
async function emitsAttachFiles(files: File[]) {
  for (const file of files) {
    attachments.value.push({
      fileName: file.name,
      contentType: file.type,
      size: file.size,
      kind: file.type.startsWith('image/') ? 'IMAGE' : 'FILE',
      dataUrl: await readFileAsDataUrl(file),
    })
  }
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onerror = () => reject(reader.error)
    reader.onload = () => resolve(String(reader.result || ''))
    reader.readAsDataURL(file)
  })
}

function onRemoveAttachment(attachment: YdChatAttachment) {
  attachments.value = attachments.value.filter(item => item !== attachment)
}

function onAgentSend(request: YdAgentSendRequest) {
  void onSend(request.text, request.attachments)
}

defineExpose({ sendPrompt, stop, streaming, activeSession, messages, selectSession, reloadSessions: loadSessions, restoreRun, respondToPendingAsk })
</script>

<template>
  <div class="yd-agent-chat">
    <YdModal v-model="renameOpen" title="重命名会话" show-cancel-button :confirm-button-disabled="!renameTitle.trim()" @confirm="saveRename">
      <YdInput v-model="renameTitle" aria-label="会话标题" :maxlength="100" />
    </YdModal>
    <YdModal v-model="removeOpen" title="删除会话记录" show-cancel-button @confirm="confirmRemove">
      <p>确定删除这份会话记录？</p>
    </YdModal>
    <header v-if="sessionStore" class="yd-agent-chat__head">
      <YdTooltip text="会话记录">
        <YdButton type="button" variant="ghost" size="icon-sm" class="yd-agent-chat__head-btn" aria-label="会话记录" :aria-expanded="sessionDrawerOpen" @click="sessionDrawerOpen = !sessionDrawerOpen">
          <YdIcon name="morph:menu" />
        </YdButton>
      </YdTooltip>
      <span class="yd-agent-chat__title" :title="activeTitle">{{ activeTitle }}</span>
      <YdTooltip text="新建会话">
        <YdButton type="button" variant="ghost" size="icon-sm" class="yd-agent-chat__head-btn" aria-label="新建会话" @click="createSession">
          <YdIcon name="morph:plus" />
        </YdButton>
      </YdTooltip>
    </header>

    <div class="yd-agent-chat__body">
      <YdAgentWorkbench
        :messages="messages"
        :harness="harness"
        :available-modes="['ask']"
        :image-url-resolver="resolveApiFileUrl"
        :suggestions="suggestions"
        :attachments="attachments"
        :attachment-accept="accept"
        :placeholder="placeholder"
        attachments-enabled
        class="yd-agent-chat__workbench"
        @send="onAgentSend"
        @stop="stop"
        @attach="onAttach"
        @remove-attachment="onRemoveAttachment"
      >
        <template #empty>
          <YdWelcome :title="welcomeTitle" :description="welcomeDescription" :suggestions="[]" />
        </template>
        <template #message-extra="slotProps">
          <slot name="message-extra" v-bind="slotProps" />
        </template>
        <template #composer-before>
          <YdAlert v-if="chatStream.runStatus.value === 'WAITING'" title="Harness 正在等待本次工具交互，离开页面不会取消任务" />
          <YdButton v-if="chatStream.connectionState.value === 'disconnected' && streaming" variant="outline" size="sm" @click="restoreRun">
            恢复原任务进度
          </YdButton>
          <slot name="footer" />
        </template>
        <template #sender-actions>
          <slot name="actions" />
        </template>
      </YdAgentWorkbench>

      <transition name="yd-agent-chat-fade">
        <div v-if="sessionStore && sessionDrawerOpen" class="yd-agent-chat__drawer">
          <div class="yd-agent-chat__drawer-head">
            <strong>会话记录</strong>
            <YdButton type="button" variant="ghost" size="icon-sm" class="yd-agent-chat__head-btn" aria-label="关闭" @click="sessionDrawerOpen = false">
              <YdIcon name="morph:x" />
            </YdButton>
          </div>
          <YdChatSessionList
            :sessions="sessionItems"
            :active-id="activeSession?.id || ''"
            :loading="sessionsLoading"
            @select="selectSession"
            @create="createSession"
            @rename="renameSession"
            @pin="pinSession"
            @remove="removeSession"
          />
        </div>
      </transition>
    </div>
  </div>
</template>

<style scoped>
.yd-agent-chat__workbench {
  height: 100%;
}

.yd-agent-chat {
  position: relative;
  display: grid;
  grid-template-rows: auto minmax(0, 1fr) auto;
  grid-template-columns: minmax(0, 1fr);
  width: 100%;
  min-width: 0;
  max-width: 100%;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  color: var(--color-text-1);
  background: var(--color-bg-1);
}

.yd-agent-chat__head {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
  align-items: center;
  min-height: 40px;
  padding: 6px 10px;
  border-bottom: 1px solid var(--color-border-2);
}

.yd-agent-chat__head-btn {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 28px;
  height: 28px;
  padding: 0;
  font-size: 16px;
  color: var(--color-text-2);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 6px;
}

.yd-agent-chat__head-btn:hover {
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

.yd-agent-chat__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 13px;
  color: var(--color-text-2);
  white-space: nowrap;
}

.yd-agent-chat__body {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
}

.yd-agent-chat__body :deep(.yd-chat-list) {
  flex: 1;
  min-height: 0;
  padding: 12px 12px 16px;
}

.yd-agent-chat__welcome {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  overflow-y: auto;
}

.yd-agent-chat__welcome :deep(.yd-welcome) {
  width: 100%;
  padding: 24px 16px;
}

.yd-agent-chat__welcome :deep(.yd-welcome__grid) {
  grid-template-columns: 1fr;
  width: 100%;
}

.yd-agent-chat__welcome :deep(.yd-welcome__title) {
  font-size: 20px;
}

.yd-agent-chat__drawer {
  position: absolute;
  inset: 0;
  z-index: 20;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-1);
  box-shadow: 6px 0 24px rgb(0 0 0 / 8%);
}

.yd-agent-chat__drawer-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  padding: 6px 12px;
  font-size: 13px;
  color: var(--color-text-1);
  border-bottom: 1px solid var(--color-border-2);
}

.yd-agent-chat__drawer :deep(.yd-session-list) {
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  background: transparent;
}

.yd-agent-chat__footer {
  display: grid;
  flex-shrink: 0;
  gap: 8px;
  min-width: 0;
  padding: 0 10px 10px;
}

/* 上下文控件（Agent / 模型 / 开关等）独立一行，避免挤占输入框工具条 */
.yd-agent-chat__controls {
  display: flex;
  gap: 8px;
  align-items: center;
  min-width: 0;
}

/* 嵌入式面板：去掉 YdChatSender 页面级的 clamp 大内边距，允许工具条收缩换行 */
.yd-agent-chat__footer :deep(.yd-sender) {
  width: 100%;
  min-width: 0;
  padding: 0;
}

.yd-agent-chat__footer :deep(.yd-sender__composer) {
  min-width: 0;
  border-radius: 14px;
}

.yd-agent-chat__footer :deep(.yd-sender__box) {
  min-height: 64px;
  padding: 12px 14px 4px;
  font-size: 13px;
}

.yd-agent-chat__footer :deep(.yd-sender__bar) {
  flex-wrap: wrap;
  gap: 6px;
  min-width: 0;
  padding: 4px 10px 10px;
}

.yd-agent-chat__footer :deep(.yd-sender__hint) {
  display: none;
}

.yd-agent-chat-fade-enter-active,
.yd-agent-chat-fade-leave-active {
  transition: opacity 0.15s ease;
}

.yd-agent-chat-fade-enter-from,
.yd-agent-chat-fade-leave-to {
  opacity: 0;
}
</style>
