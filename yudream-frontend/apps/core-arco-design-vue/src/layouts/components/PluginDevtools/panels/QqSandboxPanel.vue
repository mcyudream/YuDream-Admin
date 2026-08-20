<script setup lang="ts">
import type {
  QqSandboxConversationType,
  QqSandboxEvent,
  QqSandboxLaunchPayload,
  QqSandboxPreset,
  QqSandboxRandomMode,
} from '@/api/modules/platform-devtools-qq-sandbox'
import eventBus from '@/utils/eventBus'
import { useQqSandbox } from '../useQqSandbox'

const PREFERENCES_KEY = 'pluginDevtoolsQqSandboxPreferences'

interface SandboxPreferences {
  conversationType: QqSandboxConversationType
  presetCode: string
  userId: string
  groupId: string
  nickname: string
  botId: string
  policyConnectionId: string
  randomMode: QqSandboxRandomMode
  autoScroll: boolean
}

function hydratePreferences(): SandboxPreferences {
  const defaults: SandboxPreferences = {
    conversationType: 'GROUP',
    presetCode: '',
    userId: '10001',
    groupId: '20001',
    nickname: '沙盒用户',
    botId: '10000',
    policyConnectionId: '',
    randomMode: 'REAL',
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
const pluginCode = ref('')
const command = ref('')
const composer = ref('')
const senderId = ref('')
const messageNickname = ref('')
const mentionSelf = ref(true)
const mentionsInput = ref('')
const replyMessageId = ref('')
const clientMessageId = ref('')
const timelineRef = ref<HTMLElement | null>(null)

const presetOptions = computed(() => [
  { label: '不使用预设', value: '' },
  ...sandbox.presets.map(item => ({ label: item.name, value: item.code })),
])
const conversationOptions = [
  { label: '群聊', value: 'GROUP' },
  { label: '私聊', value: 'PRIVATE' },
]
const randomModeOptions = [
  { label: '真实随机', value: 'REAL' },
  { label: '强制命中', value: 'FORCE_HIT' },
  { label: '强制未命中', value: 'FORCE_MISS' },
]
const selectedPreset = computed(() => sandbox.presets.find(item => item.code === preferences.presetCode))
const sessionActive = computed(() => Boolean(sandbox.session))

watch(preferences, value => localStorage.setItem(PREFERENCES_KEY, JSON.stringify(value)), { deep: true })
watch(() => preferences.presetCode, (code) => {
  const preset = sandbox.presets.find(item => item.code === code)
  if (preset) {
    applyPreset(preset)
  }
})
watch(() => sandbox.events.length, async () => {
  if (!preferences.autoScroll) {
    return
  }
  await nextTick()
  timelineRef.value?.scrollTo({ top: timelineRef.value.scrollHeight, behavior: 'smooth' })
})

onMounted(() => {
  void sandbox.loadPresets()
  eventBus.on('plugin-devtools:qq-sandbox-launch', handleLaunch)
  const pending = store.consumeQqSandboxLaunch()
  if (pending) {
    handleLaunch(pending)
  }
})

onBeforeUnmount(() => {
  eventBus.off('plugin-devtools:qq-sandbox-launch', handleLaunch)
})

function applyPreset(preset: QqSandboxPreset) {
  preferences.conversationType = preset.conversationType || preferences.conversationType
  preferences.userId = preset.userId || preferences.userId
  preferences.groupId = preset.groupId || preferences.groupId
  preferences.nickname = preset.nickname || preferences.nickname
  preferences.botId = preset.botId || preferences.botId
  preferences.policyConnectionId = preset.policyConnectionId || preferences.policyConnectionId
  preferences.randomMode = preset.randomMode || preferences.randomMode
  pluginCode.value = preset.pluginCode || pluginCode.value
  command.value = preset.command || command.value
  composer.value = preset.content || composer.value
}

function handleLaunch(payload: QqSandboxLaunchPayload) {
  store.consumeQqSandboxLaunch()
  pluginCode.value = payload.pluginCode
  command.value = payload.command || ''
  composer.value = payload.content || (payload.command ? `/${payload.command} ` : '')
}

async function startSession() {
  const selectedPluginCode = pluginCode.value.trim()
  if (!selectedPluginCode) {
    toast.warning('请选择或填写目标插件')
    return
  }
  if (!preferences.policyConnectionId.trim()) {
    toast.warning('请填写真实连接 ID，以加载实际群策略')
    return
  }
  if (!preferences.userId.trim()) {
    toast.warning('请填写用户 ID')
    return
  }
  try {
    await sandbox.createSession({
      presetCode: preferences.presetCode || undefined,
      conversationType: preferences.conversationType,
      pluginCode: selectedPluginCode,
      policyConnectionId: preferences.policyConnectionId.trim(),
      botId: preferences.botId.trim() || undefined,
      userId: preferences.userId.trim(),
      groupId: preferences.conversationType === 'GROUP' ? preferences.groupId.trim() || undefined : undefined,
      nickname: preferences.nickname.trim() || undefined,
      randomMode: preferences.randomMode,
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
  if (!content || !sandbox.session) {
    return
  }
  try {
    await sandbox.sendMessage({
      content,
      senderId: senderId.value.trim() || undefined,
      nickname: messageNickname.value.trim() || undefined,
      mentionSelf: mentionSelf.value,
      mentions: mentionsInput.value.split(/[\s,，]+/).map(item => item.trim()).filter(Boolean),
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

function eventContent(event: QqSandboxEvent) {
  const payload = event.payload as Record<string, unknown> | undefined
  return typeof payload?.content === 'string' ? payload.content : JSON.stringify(event.payload)
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
      <FaSelect
        v-model="preferences.presetCode" :options="presetOptions" :loading="sandbox.presetsLoading"
        class="sandbox-toolbar__preset"
      />
      <FaSelect v-model="preferences.conversationType" :options="conversationOptions" class="sandbox-toolbar__type" />
      <FaSelect v-model="preferences.randomMode" :options="randomModeOptions" class="sandbox-toolbar__random" />
      <FaInput v-model="pluginCode" placeholder="目标插件（必填）" class="sandbox-toolbar__plugin" />
      <FaInput v-model="command" placeholder="指令（不带 /）" class="sandbox-toolbar__command" />
      <div class="flex-1" />
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

    <div class="sandbox-workspace">
      <aside class="sandbox-config">
        <div class="pane-title">
          身份与会话
        </div>
        <label class="sandbox-field">
          <span>真实连接 ID/策略连接</span>
          <FaInput v-model="preferences.policyConnectionId" :disabled="sessionActive" placeholder="加载实际群策略（必填）" />
        </label>
        <label class="sandbox-field">
          <span>机器人 ID（selfId）</span>
          <FaInput v-model="preferences.botId" :disabled="sessionActive" />
        </label>
        <label class="sandbox-field">
          <span>用户 ID（默认发送者）</span>
          <FaInput v-model="preferences.userId" :disabled="sessionActive" />
        </label>
        <label class="sandbox-field">
          <span>昵称</span>
          <FaInput v-model="preferences.nickname" :disabled="sessionActive" />
        </label>
        <label v-if="preferences.conversationType === 'GROUP'" class="sandbox-field">
          <span>群 ID</span>
          <FaInput v-model="preferences.groupId" :disabled="sessionActive" />
        </label>
        <div v-if="selectedPreset?.description" class="preset-note">
          {{ selectedPreset.description }}
        </div>
        <div class="session-meta">
          <div><span>状态</span><b>{{ sandbox.session?.status || '-' }}</b></div>
          <div><span>目标插件</span><b>{{ sandbox.session?.pluginCode || pluginCode || '-' }}</b></div>
          <div><span>策略连接</span><b>{{ sandbox.session?.policyConnectionId || preferences.policyConnectionId || '-' }}</b></div>
          <div><span>本地预填</span><b>{{ command ? `/${command}` : '-' }}</b></div>
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
              <FaTag variant="secondary" class="text-xs">{{ event.event }}</FaTag>
              <span>{{ eventTitle(event) }}</span>
              <time>{{ eventTime(event) }}</time>
            </span>
            <span class="timeline-event__content">{{ eventContent(event) }}</span>
          </button>
          <div v-if="!sandbox.events.length" class="sandbox-empty">
            {{ sessionActive ? '等待 QQ 消息与处理事件' : '启动会话后在此查看实时事件' }}
          </div>
        </div>
        <div class="composer-options">
          <FaInput v-model="senderId" placeholder="发送者 ID（默认会话用户）" />
          <FaInput v-model="messageNickname" placeholder="发送者昵称（默认会话昵称）" />
          <FaInput v-model="mentionsInput" placeholder="提及 QQ，逗号或空格分隔" />
          <FaInput v-model="replyMessageId" placeholder="回复消息 ID（可选）" />
          <FaInput v-model="clientMessageId" placeholder="客户端消息 ID（可选）" />
          <label class="mention-self">
            <span>@机器人</span>
            <FaSwitch v-model="mentionSelf" />
          </label>
        </div>
        <div class="sandbox-composer">
          <FaTextarea
            v-model="composer"
            :rows="3"
            :disabled="!sessionActive"
            placeholder="输入 QQ 消息"
            @keydown="handleComposerKeydown"
          />
          <FaTooltip text="发送消息" side="left">
            <FaButton
              size="icon" :loading="sandbox.sending" :disabled="!sessionActive || !composer.trim()"
              aria-label="发送消息" @click="send"
            >
              <FaIcon name="i-ri:send-plane-2-line" />
            </FaButton>
          </FaTooltip>
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
          <pre>{{ formatJson(sandbox.selectedEvent) }}</pre>
        </template>
        <div v-else class="sandbox-empty">
          选择时间线事件查看原始信封
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.qq-sandbox {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 520px;
  height: calc(100vh - 150px);
}

.sandbox-toolbar,
.sandbox-status,
.sandbox-composer {
  display: flex;
  gap: 6px;
  align-items: center;
}

.sandbox-toolbar__preset {
  width: 154px;
}

.sandbox-toolbar__type {
  width: 90px;
}

.sandbox-toolbar__random {
  width: 112px;
}

.sandbox-toolbar__plugin {
  width: 122px;
}

.sandbox-toolbar__command {
  width: 132px;
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
  grid-template-columns: minmax(150px, 0.7fr) minmax(280px, 1.7fr) minmax(190px, 1fr);
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
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

.preset-note {
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

.composer-options {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  padding: 8px 8px 0;
  border-top: 1px solid var(--color-border-2);
}

.mention-self {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  padding: 0 8px;
  color: var(--color-text-3);
  font-size: 11px;
  border: 1px solid var(--color-border-2);
  border-radius: 6px;
  background: var(--color-bg-2);
}

.sandbox-composer {
  padding: 8px;
  border-top: 0;
}

.sandbox-composer > :first-child {
  flex: 1;
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
  .sandbox-workspace {
    grid-template-columns: minmax(130px, 0.6fr) minmax(260px, 1.4fr);
  }

  .sandbox-inspector {
    display: none;
  }
}

@media (max-width: 680px) {
  .qq-sandbox {
    height: auto;
    min-height: 0;
  }

  .sandbox-toolbar {
    flex-wrap: wrap;
  }

  .sandbox-toolbar__preset,
  .sandbox-toolbar__random,
  .sandbox-toolbar__plugin,
  .sandbox-toolbar__command {
    flex: 1;
    width: auto;
    min-width: 120px;
  }

  .sandbox-workspace {
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
