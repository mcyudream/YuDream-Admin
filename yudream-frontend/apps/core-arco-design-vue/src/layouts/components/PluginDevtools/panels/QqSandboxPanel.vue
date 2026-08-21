<script setup lang="ts">
import type {
  QqSandboxConversationType,
  QqSandboxEvent,
  QqSandboxLaunchPayload,
  QqSandboxRandomMode,
} from '@/api/modules/platform-devtools-qq-sandbox'
import eventBus from '@/utils/eventBus'
import { useQqSandbox } from '../useQqSandbox'

const PREFERENCES_KEY = 'pluginDevtoolsQqSandboxPreferences'

interface SandboxPreferences {
  conversationType: QqSandboxConversationType
  userId: string
  groupId: string
  nickname: string
  botId: string
  policyConnectionId: string
  randomMode: QqSandboxRandomMode
  forceUnbound: boolean
  roleMode: 'REAL' | 'NONE' | 'CUSTOM'
  simulateRoles: string[]
  autoScroll: boolean
}

function hydratePreferences(): SandboxPreferences {
  const defaults: SandboxPreferences = {
    conversationType: 'GROUP',
    // 留空 = 启动时自动选首个已绑定系统用户，避免默认匿名导致插件被「未绑定」阻断
    userId: '',
    groupId: '20001',
    nickname: '沙盒用户',
    botId: '10000',
    policyConnectionId: '',
    randomMode: 'REAL',
    forceUnbound: false,
    roleMode: 'REAL',
    simulateRoles: [],
    autoScroll: true,
  }
  try {
    return { ...defaults, ...JSON.parse(localStorage.getItem(PREFERENCES_KEY) || '{}') }
  }
  catch {
    return defaults
  }
}

const toast = useFaToast()
const store = usePluginDevtoolsStore()
const sandbox = reactive(useQqSandbox())
const preferences = reactive(hydratePreferences())
// 插件范围默认空串 = 全部插件，与真实 QQ 群一致；仅「在沙盒中测试」入口会限定单个插件
const pluginCode = ref('')
const composer = ref('')
const senderId = ref('')
const messageNickname = ref('')
const mentionSelf = ref(true)
const mentionsInput = ref('')
const mentionSelected = ref<string[]>([])
const replyMessageId = ref('')
const clientMessageId = ref('')
const showConfig = ref(false)
const showMessageOptions = ref(false)
const timelineRef = ref<HTMLElement | null>(null)

const CUSTOM_VALUE = '__custom__'
const ANONYMOUS_VALUE = '__anonymous__'
const SESSION_DEFAULT_VALUE = '__session_default__'
const ANONYMOUS_QQ = '10001'
// 身份模拟的发送人选择：匿名未绑定 / 系统已绑定用户（走真实角色权限）/ 自定义 QQ
const defaultSenderChoice = ref(ANONYMOUS_VALUE)
const customSenderQq = ref('')
const groupChoice = ref('')
const customGroupId = ref('')
// 单条消息的发送人覆盖，默认跟随会话发送人
const senderOverrideChoice = ref(SESSION_DEFAULT_VALUE)

const conversationOptions = [
  { label: '群聊', value: 'GROUP' },
  { label: '私聊', value: 'PRIVATE' },
]
const randomModeOptions = [
  { label: '真实随机', value: 'REAL' },
  { label: '强制命中', value: 'FORCE_HIT' },
  { label: '强制未命中', value: 'FORCE_MISS' },
]
const roleModeOptions = [
  { label: '真实角色', value: 'REAL' },
  { label: '无角色', value: 'NONE' },
  { label: '指定角色', value: 'CUSTOM' },
]
const connectionOptions = computed(() =>
  sandbox.connections.map(item => ({ label: `${item.name}（${item.connectionId}）`, value: item.connectionId })),
)
const sessionActive = computed(() => Boolean(sandbox.session))
const pluginScopeLabel = computed(() => sandbox.session?.pluginCode || pluginCode.value || '全部插件')

function senderLabel(sender: { qq: string, nickname: string, roles: string[] }) {
  const roles = sender.roles.length ? ` · ${sender.roles.join('/')}` : ' · 无角色'
  return `${sender.nickname}（${sender.qq}）${roles}`
}

const senderSelectOptions = computed(() => [
  { label: `匿名未绑定用户（QQ ${ANONYMOUS_QQ}）`, value: ANONYMOUS_VALUE },
  ...sandbox.senders.map(sender => ({ label: senderLabel(sender), value: sender.qq })),
  { label: '自定义 QQ…', value: CUSTOM_VALUE },
])
const composerSenderOptions = computed(() => [
  { label: '会话默认发送人', value: SESSION_DEFAULT_VALUE },
  ...senderSelectOptions.value,
])
const mentionOptions = computed(() =>
  sandbox.senders.map(sender => ({ label: `${sender.nickname}（${sender.qq}）`, value: sender.qq })),
)
const groupSelectOptions = computed(() => [
  ...sandbox.groupOptions.map(group => ({ label: `${group.groupName}（${group.groupId}）`, value: group.groupId })),
  { label: '自定义群 ID…', value: CUSTOM_VALUE },
])
const roleOptions = computed(() =>
  sandbox.roles.map(role => ({ label: `${role.name}（${role.code}）`, value: role.code })),
)

watch(preferences, value => localStorage.setItem(PREFERENCES_KEY, JSON.stringify(value)), { deep: true })

function syncSenderChoice() {
  const current = preferences.userId.trim()
  if (current === ANONYMOUS_QQ) {
    // 只有显式选择匿名才回退到未绑定用户
    defaultSenderChoice.value = ANONYMOUS_VALUE
  }
  else if (current && sandbox.senders.some(sender => sender.qq === current)) {
    defaultSenderChoice.value = current
  }
  else if (current) {
    defaultSenderChoice.value = CUSTOM_VALUE
    customSenderQq.value = current
  }
  else if (sandbox.senders.length) {
    // 默认发送人取首个已绑定系统用户，让插件走真实的绑定与角色判定
    defaultSenderChoice.value = sandbox.senders[0].qq
  }
  else {
    defaultSenderChoice.value = ANONYMOUS_VALUE
  }
}

watch(defaultSenderChoice, (choice) => {
  if (choice === ANONYMOUS_VALUE) {
    preferences.userId = ANONYMOUS_QQ
  }
  else if (choice === CUSTOM_VALUE) {
    preferences.userId = customSenderQq.value.trim()
  }
  else {
    const sender = sandbox.senders.find(item => item.qq === choice)
    if (sender) {
      preferences.userId = sender.qq
      preferences.nickname = sender.nickname
    }
  }
})
watch(customSenderQq, (value) => {
  if (defaultSenderChoice.value === CUSTOM_VALUE) {
    preferences.userId = value.trim()
  }
})

function syncGroupChoice() {
  const current = preferences.groupId.trim()
  if (!current && sandbox.groupOptions.length) {
    groupChoice.value = sandbox.groupOptions[0].groupId
    return
  }
  if (current && sandbox.groupOptions.some(group => group.groupId === current)) {
    groupChoice.value = current
  }
  else {
    groupChoice.value = CUSTOM_VALUE
    customGroupId.value = current
  }
}

watch(groupChoice, (choice) => {
  if (choice === CUSTOM_VALUE) {
    preferences.groupId = customGroupId.value.trim()
  }
  else if (choice) {
    preferences.groupId = choice
  }
})
watch(customGroupId, (value) => {
  if (groupChoice.value === CUSTOM_VALUE) {
    preferences.groupId = value.trim()
  }
})

async function refreshGroupOptions() {
  const selfId = await sandbox.loadGroups(preferences.policyConnectionId.trim())
  // selfId 只是预填便利：仅在用户未显式设置机器人 ID 时带出
  if (selfId && (!preferences.botId.trim() || preferences.botId.trim() === '10000')) {
    preferences.botId = selfId
  }
  syncGroupChoice()
}

watch(() => preferences.policyConnectionId, () => {
  if (!sessionActive.value) {
    void refreshGroupOptions()
  }
})

watch(senderOverrideChoice, (choice) => {
  if (choice === SESSION_DEFAULT_VALUE) {
    senderId.value = ''
    messageNickname.value = ''
  }
  else if (choice === ANONYMOUS_VALUE) {
    senderId.value = ANONYMOUS_QQ
  }
  else if (choice !== CUSTOM_VALUE) {
    const sender = sandbox.senders.find(item => item.qq === choice)
    if (sender) {
      senderId.value = sender.qq
      messageNickname.value = sender.nickname
    }
  }
})
watch(() => sandbox.events.length, async () => {
  if (!preferences.autoScroll) {
    return
  }
  await nextTick()
  timelineRef.value?.scrollTo({ top: timelineRef.value.scrollHeight, behavior: 'smooth' })
})

onMounted(async () => {
  await sandbox.loadPresets()
  syncSenderChoice()
  // 策略连接必须指向真实已启用的 Milky 连接，默认带出第一个可用连接，免手输 ID
  if (!preferences.policyConnectionId && sandbox.connections.length) {
    preferences.policyConnectionId = sandbox.connections[0].connectionId
  }
  if (preferences.policyConnectionId) {
    void refreshGroupOptions()
  }
  eventBus.on('plugin-devtools:qq-sandbox-launch', handleLaunch)
  const pending = store.consumeQqSandboxLaunch()
  if (pending) {
    handleLaunch(pending)
  }
})

onBeforeUnmount(() => {
  eventBus.off('plugin-devtools:qq-sandbox-launch', handleLaunch)
})

function handleLaunch(payload: QqSandboxLaunchPayload) {
  store.consumeQqSandboxLaunch()
  pluginCode.value = payload.pluginCode
  composer.value = payload.content || (payload.command ? `/${payload.command} ` : '')
}

async function startSession() {
  if (!preferences.policyConnectionId.trim()) {
    toast.warning('暂无已启用的 Milky 连接，请先在 Milky 连接配置中启用一个连接')
    return
  }
  if (!preferences.userId.trim()) {
    toast.warning('请填写用户 ID')
    return
  }
  try {
    await sandbox.createSession({
      conversationType: preferences.conversationType,
      pluginCode: pluginCode.value.trim() || undefined,
      policyConnectionId: preferences.policyConnectionId.trim(),
      botId: preferences.botId.trim() || undefined,
      userId: preferences.userId.trim(),
      groupId: preferences.conversationType === 'GROUP' ? preferences.groupId.trim() || undefined : undefined,
      nickname: preferences.nickname.trim() || undefined,
      randomMode: preferences.randomMode,
      forceUnbound: preferences.forceUnbound,
      // 真实角色传 null，由后端按发送人真实角色判定；无角色传空数组
      simulateRoles: preferences.roleMode === 'REAL'
        ? null
        : preferences.roleMode === 'NONE' ? [] : [...preferences.simulateRoles],
    })
    toast.success('QQ 沙盒会话已创建')
  }
  catch {
    // 请求拦截器已提示
  }
}

async function stopSession() {
  try {
    await sandbox.closeSession()
  }
  catch {
    // 请求拦截器已提示
  }
}

async function send() {
  const content = composer.value.trim()
  // FaTextarea 会把 keydown 同时绑到根节点和内部 textarea（冒泡双触发），必须用 sending 闸门保证一次按键只发一次
  if (!content || !sandbox.session || sandbox.sending) {
    return
  }
  try {
    const mentions = [
      ...mentionSelected.value,
      ...mentionsInput.value.split(/[\s,，]+/).map(item => item.trim()).filter(Boolean),
    ]
    await sandbox.sendMessage({
      content,
      senderId: senderId.value.trim() || undefined,
      nickname: messageNickname.value.trim() || undefined,
      mentionSelf: mentionSelf.value,
      mentions: [...new Set(mentions)],
      replyMessageId: replyMessageId.value.trim() || undefined,
      clientMessageId: clientMessageId.value.trim() || undefined,
    })
    composer.value = ''
    replyMessageId.value = ''
    clientMessageId.value = ''
  }
  catch {
    // 请求拦截器已提示
  }
}

function eventTitle(event: QqSandboxEvent) {
  const payload = event.payload as Record<string, unknown> | undefined
  return String(payload?.senderName || payload?.direction || event.action || event.event)
}

// 沙盒诊断事件（handler.error/command.error/log.error 等）在时间线用 destructive 标红
function isErrorEvent(event: QqSandboxEvent) {
  const action = event.action || ''
  const payload = event.payload as Record<string, unknown> | undefined
  return action.includes('error')
    || action.includes('blocked')
    || action.includes('rejected')
    || typeof payload?.errorType === 'string'
    || typeof payload?.stackTrace === 'string'
}

const selectedStackTrace = computed(() => {
  const payload = sandbox.selectedEvent?.payload as Record<string, unknown> | undefined
  const stack = payload?.stackTrace
  return typeof stack === 'string' && stack ? stack : null
})

// 检查器 JSON 视图剔除 stackTrace 单独展示，避免一屏转义文本
const selectedEventJson = computed(() => {
  const event = sandbox.selectedEvent
  if (!event) {
    return ''
  }
  if (!selectedStackTrace.value) {
    return formatJson(event)
  }
  const { payload, ...rest } = event
  const { stackTrace, ...payloadRest } = (payload || {}) as Record<string, unknown>
  return formatJson({ ...rest, payload: payloadRest })
})

function eventContent(event: QqSandboxEvent) {
  const payload = event.payload as Record<string, unknown> | undefined
  return typeof payload?.content === 'string' ? payload.content : JSON.stringify(event.payload)
}

const BASE64_IMAGE_PREFIX = 'base64://'

// Milky 图片消息的内容是 base64://  URI，时间线应渲染成图片而不是一屏原始文本
function eventImageSrc(event: QqSandboxEvent): string | null {
  const payload = event.payload as Record<string, unknown> | undefined
  const content = payload?.content
  if (typeof content !== 'string' || !content.startsWith(BASE64_IMAGE_PREFIX)) {
    return null
  }
  const raw = content.slice(BASE64_IMAGE_PREFIX.length).trim()
  if (!raw) {
    return null
  }
  let mime = 'image/png'
  if (raw.startsWith('/9j/')) {
    mime = 'image/jpeg'
  }
  else if (raw.startsWith('R0lGOD')) {
    mime = 'image/gif'
  }
  else if (raw.startsWith('UklGR')) {
    mime = 'image/webp'
  }
  return `data:${mime};base64,${raw}`
}

const imagePreviewSrc = ref<string | null>(null)

function openImagePreview(src: string) {
  imagePreviewSrc.value = src
}

function eventTime(event: QqSandboxEvent) {
  const payload = event.payload as Record<string, unknown> | undefined
  const value = event.timestamp || (typeof payload?.occurredAt === 'string' ? payload.occurredAt : '')
  if (!value) {
    return ''
  }
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleTimeString()
}

function formatJson(value: unknown) {
  return JSON.stringify(value, null, 2)
}

function handleComposerKeydown(event: KeyboardEvent) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void send()
  }
}
</script>

<template>
  <div class="qq-sandbox">
    <div class="sandbox-toolbar">
      <FaSelect v-model="preferences.conversationType" :options="conversationOptions" class="sandbox-toolbar__type" />
      <FaSelect v-model="preferences.randomMode" :options="randomModeOptions" class="sandbox-toolbar__random" />
      <span v-if="pluginCode" class="plugin-scope">
        目标：{{ pluginCode }}
        <button type="button" class="plugin-scope__clear" aria-label="清除插件范围" @click="pluginCode = ''">
          <FaIcon name="i-ri:close-line" class="size-3" />
        </button>
      </span>
      <div class="flex-1" />
      <FaTooltip :text="showConfig ? '收起身份模拟设置' : '身份模拟设置（默认自动模拟）'" side="bottom">
        <FaButton
          :variant="showConfig ? 'secondary' : 'outline'" size="icon"
          aria-label="身份模拟设置" @click="showConfig = !showConfig"
        >
          <FaIcon name="i-ri:id-card-line" />
        </FaButton>
      </FaTooltip>
      <FaTooltip :text="preferences.autoScroll ? '关闭自动滚动' : '开启自动滚动'" side="bottom">
        <FaButton
          variant="outline" size="icon" :aria-label="preferences.autoScroll ? '关闭自动滚动' : '开启自动滚动'"
          @click="preferences.autoScroll = !preferences.autoScroll"
        >
          <FaIcon :name="preferences.autoScroll ? 'i-ri:arrow-down-double-line' : 'i-ri:pause-mini-line'" />
        </FaButton>
      </FaTooltip>
      <FaButton v-if="!sessionActive" size="sm" :loading="sandbox.creating" @click="startSession">
        <FaIcon name="i-ri:play-line" />
        启动
      </FaButton>
      <FaButton v-else variant="outline" size="sm" @click="stopSession">
        <FaIcon name="i-ri:stop-line" />
        结束
      </FaButton>
    </div>

    <div class="sandbox-status">
      <span class="sandbox-status__dot" :class="{ 'sandbox-status__dot--on': sandbox.connected }" />
      <span>{{ sandbox.connected ? '事件流已连接' : sessionActive ? '事件流连接中' : '未启动' }}</span>
      <span v-if="sandbox.session" class="font-mono">{{ sandbox.session.sessionId }}</span>
      <span v-if="sandbox.streamError" class="sandbox-status__error">{{ sandbox.streamError }}</span>
      <div class="flex-1" />
      <FaButton
        variant="ghost" size="icon-sm" title="清空内存时间线" :disabled="!sandbox.events.length"
        @click="sandbox.resetTimeline"
      >
        <FaIcon name="i-ri:delete-bin-line" />
      </FaButton>
    </div>

    <div class="sandbox-workspace" :class="{ 'sandbox-workspace--with-config': showConfig }">
      <aside v-if="showConfig" class="sandbox-config">
        <div class="pane-title">
          身份模拟（默认值已可用）
        </div>
        <label class="sandbox-field">
          <span>策略连接（加载真实群策略）</span>
          <FaSelect
            v-if="connectionOptions.length" v-model="preferences.policyConnectionId"
            :options="connectionOptions" :disabled="sessionActive" class="w-full"
          />
          <div v-else class="config-note">
            暂无已启用的 Milky 连接
          </div>
        </label>
        <label class="sandbox-field">
          <span>机器人 ID（selfId，选择策略连接后自动带出）</span>
          <FaInput v-model="preferences.botId" :disabled="sessionActive" />
        </label>
        <label class="sandbox-field">
          <span>发送人（绑定用户走真实绑定与角色权限）</span>
          <FaSelect
            v-model="defaultSenderChoice" :options="senderSelectOptions" :disabled="sessionActive"
            class="w-full"
          />
        </label>
        <label v-if="defaultSenderChoice === CUSTOM_VALUE" class="sandbox-field">
          <span>自定义发送人 QQ</span>
          <FaInput v-model="customSenderQq" :disabled="sessionActive" placeholder="输入未绑定的 QQ 号即模拟匿名用户" />
        </label>
        <label class="sandbox-field sandbox-field--switch">
          <span>模拟未绑定（插件侧判定为未绑定 QQ）</span>
          <FaSwitch v-model="preferences.forceUnbound" :disabled="sessionActive" />
        </label>
        <label class="sandbox-field">
          <span>角色模拟（默认走发送人真实角色）</span>
          <FaSelect
            v-model="preferences.roleMode" :options="roleModeOptions" :disabled="sessionActive"
            class="w-full"
          />
        </label>
        <label v-if="preferences.roleMode === 'CUSTOM'" class="sandbox-field">
          <span>指定角色（可多选）</span>
          <FaSelect
            v-model="preferences.simulateRoles" multiple :options="roleOptions"
            :disabled="sessionActive" placeholder="选择要模拟的角色" class="w-full"
          />
        </label>
        <label class="sandbox-field">
          <span>昵称</span>
          <FaInput v-model="preferences.nickname" :disabled="sessionActive" />
        </label>
        <label v-if="preferences.conversationType === 'GROUP'" class="sandbox-field">
          <span>群（来自策略连接的真实群列表）</span>
          <FaSelect
            v-model="groupChoice" :options="groupSelectOptions" :disabled="sessionActive"
            :placeholder="sandbox.groupsLoading ? '群列表加载中…' : '选择群或自定义'" class="w-full"
          />
        </label>
        <label v-if="preferences.conversationType === 'GROUP' && groupChoice === CUSTOM_VALUE" class="sandbox-field">
          <span>自定义群 ID</span>
          <FaInput v-model="customGroupId" :disabled="sessionActive" />
        </label>
        <div class="session-meta">
          <div><span>状态</span><b>{{ sandbox.session?.status || '-' }}</b></div>
          <div><span>插件范围</span><b>{{ pluginScopeLabel }}</b></div>
          <div><span>随机模式</span><b>{{ sandbox.session?.randomMode || preferences.randomMode }}</b></div>
        </div>
      </aside>

      <main class="sandbox-chat">
        <div ref="timelineRef" class="sandbox-timeline">
          <button
            v-for="(event, index) in sandbox.events"
            :key="`${event.timestamp || index}:${event.event}`"
            type="button"
            class="timeline-event"
            :class="{ 'timeline-event--selected': sandbox.selectedEvent === event }"
            @click="sandbox.selectedEvent = event"
          >
            <span class="timeline-event__meta">
              <FaTag :variant="isErrorEvent(event) ? 'destructive' : 'secondary'" class="text-xs">
                {{ event.action || event.event }}
              </FaTag>
              <span>{{ eventTitle(event) }}</span>
              <time>{{ eventTime(event) }}</time>
            </span>
            <img
              v-if="eventImageSrc(event)" class="timeline-event__image" :src="eventImageSrc(event)!"
              alt="捕获的图片消息" @click.stop="openImagePreview(eventImageSrc(event)!)"
            >
            <span v-else class="timeline-event__content">{{ eventContent(event) }}</span>
          </button>
          <div v-if="!sandbox.events.length" class="sandbox-empty">
            {{ sessionActive ? '等待 QQ 消息与处理事件' : '点击「启动」即可开聊，消息会广播给全部已启用插件' }}
          </div>
        </div>
        <div v-if="showMessageOptions" class="composer-options">
          <FaSelect
            v-model="senderOverrideChoice" :options="composerSenderOptions"
            placeholder="本条发送者（默认会话发送人）" class="w-full"
          />
          <template v-if="senderOverrideChoice === CUSTOM_VALUE">
            <FaInput v-model="senderId" placeholder="自定义发送者 QQ" />
            <FaInput v-model="messageNickname" placeholder="发送者昵称（默认会话昵称）" />
          </template>
          <FaSelect
            v-model="mentionSelected" multiple :options="mentionOptions"
            placeholder="提及人（可多选系统用户）" class="w-full"
          />
          <FaInput v-model="mentionsInput" placeholder="额外提及 QQ，逗号或空格分隔" />
          <FaInput v-model="replyMessageId" placeholder="回复消息 ID（可选）" />
          <FaInput v-model="clientMessageId" placeholder="客户端消息 ID（可选）" />
        </div>
        <div class="sandbox-composer">
          <FaTextarea
            v-model="composer"
            :rows="2"
            :disabled="!sessionActive"
            placeholder="像真实 QQ 群一样输入消息，/帮助 触发指令，Enter 发送"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-actions">
            <FaTooltip text="开启后本条消息模拟 @机器人（触发 Agent）" side="left">
              <label class="mention-self">
                <FaSwitch v-model="mentionSelf" />
              </label>
            </FaTooltip>
            <FaTooltip :text="showMessageOptions ? '收起消息选项' : '更多消息选项（提及/发送者/回复）'" side="left">
              <FaButton
                :variant="showMessageOptions ? 'secondary' : 'ghost'" size="icon-sm"
                aria-label="更多消息选项" @click="showMessageOptions = !showMessageOptions"
              >
                <FaIcon name="i-ri:equalizer-line" />
              </FaButton>
            </FaTooltip>
            <FaTooltip text="发送消息" side="left">
              <FaButton
                size="icon" :loading="sandbox.sending" :disabled="!sessionActive || !composer.trim()"
                aria-label="发送消息" @click="send"
              >
                <FaIcon name="i-ri:send-plane-2-line" />
              </FaButton>
            </FaTooltip>
          </div>
        </div>
      </main>

      <aside class="sandbox-inspector">
        <div class="pane-title">
          事件检查器
        </div>
        <template v-if="sandbox.selectedEvent">
          <div class="inspector-meta">
            <span>event</span><b>{{ sandbox.selectedEvent.event }}</b>
            <span>action</span><b>{{ sandbox.selectedEvent.action || '-' }}</b>
            <span>traceId</span><b>{{ sandbox.selectedEvent.traceId || '-' }}</b>
          </div>
          <pre v-if="selectedStackTrace" class="inspector-stack">{{ selectedStackTrace }}</pre>
          <pre>{{ selectedEventJson }}</pre>
        </template>
        <div v-else class="sandbox-empty">
          选择时间线事件查看原始信封
        </div>
      </aside>
    </div>

    <FaModal :model-value="!!imagePreviewSrc" title="图片预览" :footer="false" :z-index="2200" content-class="sm:max-w-3xl" @update:model-value="imagePreviewSrc = null">
      <div class="image-preview">
        <img v-if="imagePreviewSrc" :src="imagePreviewSrc" alt="图片预览">
      </div>
    </FaModal>
  </div>
</template>

<style scoped>
.qq-sandbox {
  display: flex;
  flex-direction: column;
  gap: 8px;
  height: 100%;
  min-height: 0;
}

.sandbox-toolbar,
.sandbox-status,
.sandbox-composer {
  display: flex;
  gap: 6px;
  align-items: center;
}

.sandbox-toolbar__type {
  width: 90px;
}

.sandbox-toolbar__random {
  width: 112px;
}

.plugin-scope {
  display: inline-flex;
  gap: 4px;
  align-items: center;
  padding: 3px 8px;
  color: var(--color-text-2);
  font-size: 11px;
  border: 1px solid var(--color-border-2);
  border-radius: 5px;
  background: var(--color-fill-1);
}

.plugin-scope__clear {
  display: inline-flex;
  align-items: center;
  color: var(--color-text-3);
  cursor: pointer;
}

.plugin-scope__clear:hover {
  color: var(--color-text-1);
}

.sandbox-status {
  min-height: 28px;
  padding: 4px 8px;
  color: var(--color-text-3);
  font-size: 11px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.sandbox-status__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--color-fill-4);
}

.sandbox-status__dot--on {
  background: var(--color-text-2);
}

.sandbox-status__error {
  color: var(--color-text-2);
}

.sandbox-workspace {
  display: grid;
  grid-template-columns: minmax(280px, 1.7fr) minmax(190px, 1fr);
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.sandbox-workspace--with-config {
  grid-template-columns: minmax(170px, 0.7fr) minmax(280px, 1.6fr) minmax(190px, 1fr);
}

.sandbox-config,
.sandbox-inspector,
.sandbox-chat {
  min-width: 0;
  min-height: 0;
}

.sandbox-config,
.sandbox-inspector {
  padding: 10px;
  overflow: auto;
}

.sandbox-config {
  border-right: 1px solid var(--color-border-2);
}

.sandbox-inspector {
  border-left: 1px solid var(--color-border-2);
}

.pane-title {
  margin-bottom: 10px;
  color: var(--color-text-2);
  font-size: 12px;
  font-weight: 600;
}

.sandbox-field {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
  color: var(--color-text-3);
  font-size: 11px;
}

.sandbox-field--switch {
  flex-direction: row;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.config-note {
  padding: 7px 8px;
  color: var(--color-text-3);
  font-size: 11px;
  border-radius: 5px;
  background: var(--color-fill-1);
}

.session-meta,
.inspector-meta {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 6px 8px;
  margin-top: 12px;
  color: var(--color-text-3);
  font-size: 11px;
}

.inspector-stack {
  max-height: 40vh;
  margin-top: 10px;
  padding: 8px 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-3);
  color: var(--color-danger-5, var(--color-danger, #f53f3f));
  font-size: 11px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  overflow-y: auto;
}

.session-meta b,
.inspector-meta b {
  overflow-wrap: anywhere;
  color: var(--color-text-2);
  font-weight: 500;
}

.sandbox-chat {
  display: flex;
  flex-direction: column;
}

.sandbox-timeline {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 5px;
  min-height: 0;
  padding: 8px;
  overflow-y: auto;
  background: var(--color-bg-1);
}

.timeline-event {
  width: 100%;
  padding: 7px 8px;
  color: var(--color-text-2);
  text-align: left;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.timeline-event:hover,
.timeline-event--selected {
  background: var(--color-fill-2);
}

.timeline-event__meta {
  display: flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
  color: var(--color-text-3);
  font-size: 11px;
}

.timeline-event__meta span {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.timeline-event__content {
  display: block;
  margin-top: 5px;
  font-size: 12px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.timeline-event__image {
  display: block;
  max-width: 100%;
  max-height: 220px;
  margin-top: 5px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  object-fit: contain;
  background: var(--color-fill-1);
  cursor: zoom-in;
}

.image-preview {
  display: flex;
  justify-content: center;
  max-height: 70vh;
  overflow: auto;
}

.image-preview img {
  max-width: 100%;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-fill-1);
}

.composer-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 8px 8px 0;
  border-top: 1px solid var(--color-border-2);
}

.sandbox-composer {
  padding: 8px;
  border-top: 1px solid var(--color-border-2);
}

.composer-options + .sandbox-composer {
  border-top: 0;
}

.sandbox-composer > :first-child {
  flex: 1;
}

.composer-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
  align-items: center;
  align-self: stretch;
  justify-content: center;
}

.mention-self {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2px;
  cursor: pointer;
}

.sandbox-inspector pre {
  margin: 10px 0 0;
  color: var(--color-text-2);
  font-size: 11px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.sandbox-empty {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 80px;
  color: var(--color-text-3);
  font-size: 12px;
  text-align: center;
}

@media (max-width: 900px) {
  .sandbox-workspace,
  .sandbox-workspace--with-config {
    grid-template-columns: minmax(260px, 1.4fr);
  }

  .sandbox-workspace--with-config {
    grid-template-columns: minmax(150px, 0.6fr) minmax(260px, 1.4fr);
  }

  .sandbox-inspector {
    display: none;
  }
}

@media (max-width: 680px) {
  .qq-sandbox {
    height: auto;
  }

  .sandbox-toolbar {
    flex-wrap: wrap;
  }

  .sandbox-toolbar__type,
  .sandbox-toolbar__random {
    flex: 1;
    width: auto;
    min-width: 110px;
  }

  .sandbox-workspace,
  .sandbox-workspace--with-config {
    display: flex;
    flex-direction: column;
    overflow: visible;
  }

  .sandbox-config {
    border-right: 0;
    border-bottom: 1px solid var(--color-border-2);
  }

  .sandbox-timeline {
    min-height: 300px;
  }

  .composer-options {
    grid-template-columns: 1fr;
  }
}
</style>
