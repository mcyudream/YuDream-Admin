<script setup lang="ts">
import type {
  YdChatAttachment,
  YdChatHistoryTurn,
  YdChatMessage,
  YdChatProtocol,
  YdChatToolCallReply,
  YdChatToolCallRequest,
  YdChatToolEvent,
  YdChatTransport,
} from '@yudream/components'
import {
  provideYdChatConfig,
  useYdChatStream,
  YdChatMessageList,
  YdChatSender,
  YdChatSessionList,
  YdWelcome,
} from '@yudream/components'
import type { YdAgentChatSession, YdAgentChatSessionMeta, YdAgentChatSessionStore } from './types'
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

const { messages, streaming, send, stop } = useYdChatStream({
  endpoint: () => typeof props.endpoint === 'function' ? props.endpoint() : props.endpoint,
  protocol: props.protocol,
  transport: props.transport,
  historyLimit: props.historyLimit,
  buildBody: (question, history, currentAttachments) => props.buildBody
    ? props.buildBody(question, history, currentAttachments)
    : { question, history, attachments: currentAttachments },
  onTool: tool => emits('tool', tool),
  onToolCallRequest: props.onToolCallRequest,
  getWebSocketToken: () => localStorage.getItem('token') || undefined,
  onDone: message => emits('done', message),
  onError: (message, error) => emits('error', message, error),
})

const isEmpty = computed(() => messages.value.length === 0)
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
  stop()
  sessionDrawerOpen.value = false
  try {
    const session = await props.sessionStore.load(id)
    if (!session) {
      await loadSessions()
      return
    }
    activeSession.value = session
    messages.value = session.messages
    emits('sessionChange', session)
  }
  catch {
    // 载入失败时保持当前会话
  }
}

async function renameSession(item: { id: string, title: string }) {
  if (!props.sessionStore) {
    return
  }
  const title = window.prompt('请输入会话标题', item.title)?.trim()
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

async function removeSession(item: { id: string }) {
  if (!props.sessionStore || !window.confirm('确定删除该会话？')) {
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
  await send(text, currentAttachments)
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

function onCopy(message: YdChatMessage) {
  if (message.content) {
    navigator.clipboard?.writeText(message.content)
  }
}

function onRegenerate(message: YdChatMessage) {
  const user = messages.value[messages.value.indexOf(message) - 1]
  if (user?.role === 'user') {
    void onSend(user.content, user.attachments ?? [])
  }
}

defineExpose({ sendPrompt, stop, streaming, activeSession, messages, selectSession, reloadSessions: loadSessions })
</script>

<template>
  <div class="yd-agent-chat">
    <header v-if="sessionStore" class="yd-agent-chat__head">
      <FaTooltip text="会话记录">
        <button type="button" class="yd-agent-chat__head-btn" aria-label="会话记录" @click="sessionDrawerOpen = !sessionDrawerOpen">
          <FaIcon name="i-ri:menu-line" />
        </button>
      </FaTooltip>
      <span class="yd-agent-chat__title" :title="activeTitle">{{ activeTitle }}</span>
      <FaTooltip text="新建会话">
        <button type="button" class="yd-agent-chat__head-btn" aria-label="新建会话" @click="createSession">
          <FaIcon name="i-ri:add-line" />
        </button>
      </FaTooltip>
    </header>

    <div class="yd-agent-chat__body">
      <div v-if="isEmpty" class="yd-agent-chat__welcome">
        <YdWelcome :title="welcomeTitle" :description="welcomeDescription" :suggestions="suggestions" @select="sendPrompt" />
      </div>
      <YdChatMessageList
        v-else
        :messages="messages"
        :image-url-resolver="resolveApiFileUrl"
        compact
        @copy-message="onCopy"
        @regenerate-message="onRegenerate"
      />

      <transition name="yd-agent-chat-fade">
        <div v-if="sessionStore && sessionDrawerOpen" class="yd-agent-chat__drawer">
          <div class="yd-agent-chat__drawer-head">
            <strong>会话记录</strong>
            <button type="button" class="yd-agent-chat__head-btn" aria-label="关闭" @click="sessionDrawerOpen = false">
              <FaIcon name="i-ri:close-line" />
            </button>
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

    <div class="yd-agent-chat__footer">
      <slot name="footer" />
      <div v-if="$slots.actions" class="yd-agent-chat__controls">
        <slot name="actions" />
      </div>
      <YdChatSender
        :loading="streaming"
        :suggestions="[]"
        :attachments="attachments"
        :accept="accept"
        @send="onSend"
        @stop="stop"
        @attach="onAttach"
        @remove-attachment="onRemoveAttachment"
      />
    </div>
  </div>
</template>

<style scoped>
.yd-agent-chat {
  position: relative;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  grid-template-rows: auto minmax(0, 1fr) auto;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  background: var(--color-bg-1);
  color: var(--color-text-1);
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
  width: 28px;
  height: 28px;
  flex-shrink: 0;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  place-items: center;
  font-size: 16px;
}

.yd-agent-chat__head-btn:hover {
  background: var(--color-fill-1);
  color: var(--color-text-1);
}

.yd-agent-chat__title {
  overflow: hidden;
  min-width: 0;
  flex: 1;
  color: var(--color-text-2);
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-agent-chat__body {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
}

.yd-agent-chat__body :deep(.yd-chat-list) {
  min-height: 0;
  flex: 1;
  padding: 12px 12px 16px;
}

.yd-agent-chat__welcome {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex: 1;
  align-items: center;
  justify-content: center;
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
  z-index: 20;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-1);
  box-shadow: 6px 0 24px rgb(0 0 0 / 8%);
  inset: 0;
}

.yd-agent-chat__drawer-head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  min-height: 40px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--color-border-2);
  color: var(--color-text-1);
  font-size: 13px;
}

.yd-agent-chat__drawer :deep(.yd-session-list) {
  width: 100%;
  min-width: 0;
  min-height: 0;
  flex: 1;
  background: transparent;
}

.yd-agent-chat__footer {
  display: grid;
  min-width: 0;
  flex-shrink: 0;
  gap: 8px;
  padding: 0 10px 10px;
}

/* 上下文控件（Agent / 模型 / 开关等）独立一行，避免挤占输入框工具条 */
.yd-agent-chat__controls {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
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
  min-width: 0;
  gap: 6px;
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
