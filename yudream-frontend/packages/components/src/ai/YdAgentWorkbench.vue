<script setup lang="ts">
import type { YdAgentReference, YdAgentRenderDocument, YdAgentToolCall } from './agent-render'
import type { YdAgentActivity, YdAgentApproval, YdAgentChange, YdAgentContextFile, YdAgentHarnessState, YdAgentMcpServer, YdAgentMode, YdAgentModel, YdAgentSendRequest, YdAgentSession, YdAgentSkill, YdAgentWorkspace } from './agent-workbench'
import type { YdChatAction, YdChatAttachment, YdChatCitation, YdChatGraphNode, YdChatMessage, YdChatRetrievalHit } from './useYdChatStream'
import { useElementSize } from '@vueuse/core'
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { YdButton, YdIcon, YdSelect } from '../adapters/index'
import YdCode from '../extended/YdCode.vue'
import YdCopyButton from '../extended/YdCopyButton.vue'
import YdAgentAnswer from './YdAgentAnswer.vue'
import YdAgentContextPanel from './YdAgentContextPanel.vue'
import YdBubble from './YdBubble.vue'
import YdChatProcess from './YdChatProcess.vue'
import YdChatSender from './YdChatSender.vue'
import YdChatSessionList from './YdChatSessionList.vue'
import YdThoughtChain from './YdThoughtChain.vue'
import '../extended/extended.css'

defineOptions({ name: 'YdAgentWorkbench' })
const props = withDefaults(defineProps<{
  sessions?: YdAgentSession[]
  sessionId?: string
  workspaces?: YdAgentWorkspace[]
  workspaceId?: string
  models?: YdAgentModel[]
  modelId?: string
  mode?: YdAgentMode
  availableModes?: YdAgentMode[]
  messages?: YdChatMessage[]
  harness: YdAgentHarnessState
  activities?: YdAgentActivity[]
  skills?: YdAgentSkill[]
  mcpServers?: YdAgentMcpServer[]
  files?: YdAgentContextFile[]
  changes?: YdAgentChange[]
  approvals?: YdAgentApproval[]
  renderDocuments?: Record<string, YdAgentRenderDocument>
  suggestions?: string[]
  disabled?: boolean
  attachmentsEnabled?: boolean
  attachments?: YdChatAttachment[]
  attachmentAccept?: string
  placeholder?: string
  imageUrlResolver?: (url: string) => string
  demo?: boolean
}>(), {
  sessions: () => [],
  workspaces: () => [],
  models: () => [],
  mode: 'ask',
  availableModes: () => ['ask', 'plan', 'code'],
  messages: () => [],
  activities: () => [],
  skills: () => [],
  mcpServers: () => [],
  files: () => [],
  changes: () => [],
  approvals: () => [],
  renderDocuments: () => ({}),
  suggestions: () => [],
  attachmentsEnabled: false,
  attachments: () => [],
  placeholder: '描述任务或输入问题…',
})
const emit = defineEmits<{
  'update:sessionId': [id: string]
  'update:workspaceId': [id: string]
  'update:modelId': [id: string]
  'update:mode': [mode: YdAgentMode]
  'send': [request: YdAgentSendRequest]
  'stop': []
  /** 失败回答的「重试」：父组件按该消息之前的用户输入重新发送 */
  'retry': [message: YdChatMessage]
  'attach': [files: File[]]
  'removeAttachment': [attachment: YdChatAttachment]
  'sessionCreate': []
  'sessionRename': [session: YdAgentSession]
  'sessionPin': [session: YdAgentSession]
  'sessionRemove': [session: YdAgentSession]
  'fileSelect': [file: YdAgentContextFile]
  'fileToggle': [path: string, included: boolean]
  'skillToggle': [code: string, enabled: boolean]
  'approvalDecision': [id: string, approved: boolean]
  'referenceSelect': [reference: YdAgentReference]
  'toolSelect': [tool: YdAgentToolCall]
  'toolRetry': [tool: YdAgentToolCall]
  'citationSelect': [citation: YdChatCitation]
  'retrievalSelect': [hit: YdChatRetrievalHit]
  'graphNodeSelect': [node: YdChatGraphNode]
  'actionSelect': [action: YdChatAction]
}>()
const root = ref<HTMLElement>()
const thread = ref<HTMLElement>()
const { width } = useElementSize(root)
const wide = computed(() => width.value >= 900)
const mobile = computed(() => width.value > 0 && width.value <= 768)
// 侧栏只在确有上下文（文件、技能、MCP，或规划/编码模式下的工作区）时默认展开；普通问答保持对话区宽敞
const hasContext = computed(() => props.files.length > 0 || props.skills.length > 0 || props.mcpServers.length > 0 || (props.workspaces.length > 0 && props.mode !== 'ask'))
const contextVisible = ref(false)
watch(hasContext, (value) => {
  contextVisible.value = value
}, { immediate: true })
const contextDrawer = ref(false)
const sessionDrawer = ref(false)
const changesDrawer = ref(false)
const activeChange = ref<string>()
const drafts = reactive<Record<string, string>>({})
const draft = computed({ get: () => drafts[props.sessionId || '__new__'] || '', set: value => (drafts[props.sessionId || '__new__'] = value) })
const workspace = computed(() => props.workspaces.find(item => item.id === props.workspaceId))
const session = computed(() => props.sessions.find(item => item.id === props.sessionId))
const model = computed(() => props.models.find(item => item.id === props.modelId))
const busy = computed(() => props.harness.status === 'running' || props.harness.status === 'waiting')
const inputDisabled = computed(() => props.disabled || workspace.value?.access === 'unavailable' || (props.mode === 'code' && workspace.value?.access !== 'write'))
const sessionItems = computed(() => props.sessions.map(item => ({ ...item, scopeType: 'AGENT' })))
const workspaceOptions = computed(() => props.workspaces.map(item => ({ label: item.name, value: item.id, disabled: item.access === 'unavailable' })))
const modelOptions = computed(() => props.models.map(item => ({ label: item.label, value: item.id })))
const modeOptions = computed(() => [
  { label: '问答', value: 'ask' },
  { label: '规划', value: 'plan' },
  { label: '编码', value: 'code', disabled: workspace.value?.access !== 'write' },
].filter(option => props.availableModes.includes(option.value as YdAgentMode)))
const runState = computed(() => ({
  idle: { label: '就绪', icon: 'morph:circle-dot' },
  running: { label: '执行中', icon: 'morph:loader-circle' },
  waiting: { label: '等待批准', icon: 'morph:shield-question' },
  completed: { label: '已完成', icon: 'morph:circle-check' },
  error: { label: '执行失败', icon: 'morph:circle-alert' },
  cancelled: { label: '已停止', icon: 'morph:circle-stop' },
}[props.harness.status]))
const elapsed = computed(() => props.harness.elapsedMs === undefined ? '' : `${Math.max(0, props.harness.elapsedMs / 1000).toFixed(1)} 秒`)
const traceTitle = computed(() => `执行轨迹${elapsed.value ? ` · ${elapsed.value}` : ''}`)
const currentChange = computed(() => props.changes.find(item => item.id === activeChange.value) || props.changes[0])
// 最后一条消息已在气泡内展示过程时，不再重复显示运行轨迹
const showTrace = computed(() => props.activities.length > 0 && !props.messages.at(-1)?.activities?.length)
const traceItems = computed(() => props.activities.map(item => ({
  key: item.id,
  title: item.title,
  description: item.detail && item.detail.replace(/[。.]+$/, '') !== item.title.replace(/[。.]+$/, '') ? item.detail : undefined,
  status: item.status === 'complete' ? 'success' as const : item.status === 'waiting' ? 'pending' as const : item.status,
  icon: item.status === 'waiting' ? 'morph:shield-question' : undefined,
})))
function selectSession(id: string) {
  emit('update:sessionId', id)
  sessionDrawer.value = false
}
function toggleContext() {
  if (wide.value) {
    contextVisible.value = !contextVisible.value
  }
  else {
    contextDrawer.value = true
  }
}
function send(text: string, attachments: YdChatAttachment[]) {
  if (busy.value || inputDisabled.value || !text.trim()) {
    return
  }
  emit('send', {
    text: text.trim(),
    attachments,
    sessionId: props.sessionId,
    workspaceId: props.workspaceId,
    model: model.value,
    mode: props.mode,
    skillCodes: props.skills.filter(item => item.enabled).map(item => item.code),
    contextPaths: props.files.filter(item => item.included).map(item => item.path),
  })
}
function displayMessage(message: YdChatMessage): YdChatMessage {
  return ((message.id && props.renderDocuments[message.id]) || message.tools?.length) && !message.content
    ? { ...message, content: ' ' }
    : message
}
function messageDocument(message: YdChatMessage): YdAgentRenderDocument {
  if (message.id && props.renderDocuments[message.id]) {
    return props.renderDocuments[message.id]!
  }
  return {
    version: 1,
    blocks: [
      ...((message.content || message.pending) ? [{ type: 'markdown' as const, text: message.content }] : []),
      ...(message.tools || []).map((tool, index) => ({ type: 'tool' as const, tool: {
        id: tool.toolCallId || `${message.id || 'message'}-tool-${index}`,
        name: tool.toolName || '工具调用',
        status: tool.status === 'executing' ? 'running' as const : tool.status === 'complete' ? 'success' as const : tool.status === 'error' ? 'error' as const : tool.status === 'cancelled' ? 'cancelled' as const : 'queued' as const,
        description: tool.message,
        output: tool.payload ? JSON.stringify(tool.payload, null, 2) : undefined,
      } })),
    ],
  }
}
function changeMode(value: unknown) {
  if (props.availableModes.includes(value as YdAgentMode) && (value === 'ask' || value === 'plan' || (value === 'code' && workspace.value?.access === 'write'))) {
    emit('update:mode', value)
  }
}
function changeWorkspace(value: unknown) {
  if (typeof value === 'string' && props.workspaces.some(item => item.id === value && item.access !== 'unavailable')) {
    emit('update:workspaceId', value)
  }
}
let nearBottom = true
function onScroll() {
  const el = thread.value
  nearBottom = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 100
}
function scrollToBottom(force = false) {
  if (!nearBottom && !force) {
    return
  }
  void nextTick(() => {
    if (thread.value) {
      thread.value.scrollTop = thread.value.scrollHeight
    }
    nearBottom = true
  })
}
watch(() => props.sessionId, () => scrollToBottom(true))
watch(() => props.messages.length, () => scrollToBottom(true))
watch(() => [props.messages.at(-1)?.content, props.messages.at(-1)?.pending, JSON.stringify(props.renderDocuments)], () => scrollToBottom())
watch(wide, (value) => {
  if (value) {
    contextDrawer.value = false
  }
})
defineExpose({ scrollToBottom })
</script>

<template>
  <section ref="root" class="yd-agent-workbench yd-surface" aria-label="Agent 工作台">
    <div v-if="demo" class="yd-agent-demo">
      <YdIcon name="morph:flask-conical" />本地交互演示 · 任务、工具与文件结果均为示例，不连接 AI 或修改真实工作区。
    </div>
    <div class="yd-agent-layout" :class="{ 'has-context': wide && contextVisible, 'is-mobile': mobile }">
      <aside v-if="!mobile" class="yd-agent-sessions" aria-label="工作会话">
        <div class="yd-agent-sidebar-title">
          <YdIcon name="morph:orbit" /><strong>工作会话</strong><span>{{ sessions.length }}</span>
        </div>
        <YdChatSessionList :sessions="sessionItems" :active-id="sessionId" @select="selectSession" @create="emit('sessionCreate')" @rename="emit('sessionRename', $event)" @pin="emit('sessionPin', $event)" @remove="emit('sessionRemove', $event)" />
      </aside>
      <main class="yd-agent-main">
        <header class="yd-agent-heading">
          <YdButton v-if="mobile" variant="ghost" size="icon-sm" aria-label="打开会话" @click="sessionDrawer = true">
            <YdIcon name="morph:panel-left" />
          </YdButton>
          <div class="yd-agent-title">
            <span v-if="wide">YU DREAM / AGENT</span><h2>{{ session?.title || '开始一个新任务' }}</h2>
          </div>
          <div class="yd-agent-heading-actions">
            <YdButton v-if="changes.length" variant="outline" size="sm" :aria-expanded="changesDrawer" @click="changesDrawer = true">
              <YdIcon name="morph:git-compare-arrows" />变更 <span>{{ changes.length }}</span>
            </YdButton>
            <YdButton variant="ghost" size="icon-sm" aria-label="工作区与上下文" :aria-expanded="wide ? contextVisible : contextDrawer" @click="toggleContext">
              <YdIcon name="morph:panel-right" />
            </YdButton>
            <slot name="header-actions" />
          </div>
        </header>
        <div class="yd-agent-toolbar">
          <YdSelect v-if="workspaces.length" :model-value="workspaceId" :options="workspaceOptions" :disabled="busy || disabled" aria-label="选择工作区" @update:model-value="changeWorkspace" />
          <span v-if="workspace?.branch" class="yd-agent-branch"><YdIcon name="morph:git-branch" />{{ workspace.branch }}</span>
          <span class="yd-agent-run-state" role="status" aria-live="polite"><YdIcon :name="runState.icon" :class="{ 'yd-agent-spin': harness.status === 'running' }" />{{ runState.label }}</span>
        </div>
        <div ref="thread" class="yd-agent-thread" role="log" aria-label="任务对话" @scroll.passive="onScroll">
          <div v-if="!messages.length" class="yd-agent-welcome">
            <div class="yd-agent-welcome-icon">
              <YdIcon name="morph:bot" />
            </div>
            <p class="yd-agent-eyebrow">
              从想法，到可验证的结果
            </p><h3>一起把下一步做好。</h3>
            <p>说明目标，选好上下文。Agent 会把计划、工具调用和工作成果带回这里。</p>
            <div class="yd-agent-starters">
              <YdButton v-for="item in suggestions" :key="item" variant="outline" :disabled="inputDisabled || busy" @click="draft = item">
                <YdIcon name="morph:arrow-up-right" />{{ item }}
              </YdButton>
            </div>
            <slot name="empty" />
          </div>
          <div v-for="(message, index) in messages" :key="message.id || index" class="yd-agent-message">
            <YdBubble :message="displayMessage(message)" :image-url-resolver="imageUrlResolver" thinking-text="正在准备下一步…" @citation-click="emit('citationSelect', $event)">
              <template #process>
                <YdChatProcess v-if="message.activities?.length" :activities="message.activities" @retrieval-select="emit('retrievalSelect', $event)" @graph-node-select="emit('graphNodeSelect', $event)" />
              </template>
              <template #content>
                <YdAgentAnswer :document="messageDocument(message)" :retry-disabled="busy || disabled" @reference-select="emit('referenceSelect', $event)" @tool-select="emit('toolSelect', $event)" @tool-retry="emit('toolRetry', $event)" />
              </template>
              <template #footer>
                <div v-if="message.role === 'assistant' && message.error && !message.pending" class="yd-agent-message-actions">
                  <YdButton variant="outline" size="sm" :disabled="busy || disabled" @click="emit('retry', message)">
                    <YdIcon name="morph:rotate-ccw" />重试
                  </YdButton>
                </div>
                <div v-else-if="message.role === 'assistant' && message.content.trim() && !message.pending" class="yd-agent-message-actions">
                  <YdCopyButton :text="message.content" label="复制回答" /><slot name="message-actions" :message="message" />
                  <YdButton v-for="action in message.actions" :key="`${action.action}:${action.value}`" variant="outline" size="sm" @click="emit('actionSelect', action)">
                    {{ action.label }}
                  </YdButton>
                </div>
              </template>
            </YdBubble>
            <slot name="message-extra" :message="message" :index="index" />
          </div>
        </div>
        <div v-if="showTrace" class="yd-agent-trace">
          <YdThoughtChain :items="traceItems" :title="traceTitle" :default-expanded="false" />
        </div>
        <div v-if="approvals.length" class="yd-agent-approvals" aria-label="待批准操作">
          <section v-for="approval in approvals" :key="approval.id" class="yd-agent-approval">
            <div><YdIcon name="morph:shield-question" /><strong>{{ approval.title }}</strong></div><p>{{ approval.description }}</p>
            <code v-if="approval.command">{{ approval.command }}</code><small v-if="approval.scope">范围：{{ approval.scope }}</small>
            <div class="yd-agent-approval-actions">
              <YdButton variant="outline" size="sm" :disabled="disabled" @click="emit('approvalDecision', approval.id, false)">
                拒绝
              </YdButton><YdButton size="sm" :disabled="disabled" @click="emit('approvalDecision', approval.id, true)">
                批准本次
              </YdButton>
            </div>
          </section>
        </div>
        <slot name="composer-before" />
        <YdChatSender v-model="draft" :loading="busy" :disabled="inputDisabled" :attachments-enabled="attachmentsEnabled" :attachments="attachments" :accept="attachmentAccept" :placeholder="placeholder" :mention-items="files.map(file => ({ key: file.path, label: file.path, value: file.path, icon: 'morph:file-code' }))" :large="false" :disclaimer="demo ? '本地演示 · Enter 发送，Shift + Enter 换行' : 'Enter 发送，Shift + Enter 换行'" @send="send" @stop="emit('stop')" @attach="emit('attach', $event)" @remove-attachment="emit('removeAttachment', $event)" @mention-select="emit('fileToggle', String($event.key), true)">
          <template #context>
            <div v-if="files.some(file => file.included)" class="yd-agent-context-chips">
              <YdButton v-for="file in files.filter(file => file.included)" :key="file.path" variant="ghost" size="sm" :disabled="busy || disabled" :title="file.path" @click="emit('fileToggle', file.path, false)">
                <YdIcon name="morph:file-code" /><span>{{ file.path.split('/').at(-1) }}</span><YdIcon name="morph:x" />
              </YdButton>
            </div>
          </template>
          <template #actions>
            <YdSelect v-if="modeOptions.length > 1" :model-value="mode" :options="modeOptions" :disabled="busy || disabled" aria-label="执行模式" @update:model-value="changeMode" /><YdSelect v-if="models.length" :model-value="modelId" :options="modelOptions" :disabled="busy || disabled" aria-label="选择模型" @update:model-value="typeof $event === 'string' && emit('update:modelId', $event)" />
            <slot name="sender-actions" />
          </template>
        </YdChatSender>
        <p v-if="inputDisabled && !disabled" class="yd-agent-input-hint">
          {{ workspace?.access === 'unavailable' ? '当前工作区不可用，请选择其他工作区。' : '编码需要可写工作区；当前可使用问答或规划。' }}
        </p>
      </main>
      <aside v-if="wide && contextVisible" class="yd-agent-inspector" aria-label="工作区与上下文">
        <YdAgentContextPanel :workspace="workspace" :files="files" :skills="skills" :mcp-servers="mcpServers" :harness="harness" :busy="busy || disabled" @file-select="emit('fileSelect', $event)" @file-toggle="(path, included) => emit('fileToggle', path, included)" @skill-toggle="(code, enabled) => emit('skillToggle', code, enabled)" />
      </aside>
    </div>
    <section v-if="contextDrawer" class="yd-agent-overlay" aria-label="工作区与上下文" @keydown.esc.stop="contextDrawer = false">
      <header class="yd-agent-overlay__head">
        <strong>工作区与上下文</strong>
        <YdButton variant="ghost" size="icon-sm" aria-label="关闭工作区与上下文" @click="contextDrawer = false">
          <YdIcon name="morph:x" />
        </YdButton>
      </header>
      <div class="yd-agent-overlay__body">
        <YdAgentContextPanel :workspace="workspace" :files="files" :skills="skills" :mcp-servers="mcpServers" :harness="harness" :busy="busy || disabled" @file-select="emit('fileSelect', $event)" @file-toggle="(path, included) => emit('fileToggle', path, included)" @skill-toggle="(code, enabled) => emit('skillToggle', code, enabled)" />
      </div>
    </section>
    <section v-if="sessionDrawer" class="yd-agent-overlay" aria-label="工作会话" @keydown.esc.stop="sessionDrawer = false">
      <header class="yd-agent-overlay__head">
        <strong>工作会话</strong>
        <YdButton variant="ghost" size="icon-sm" aria-label="关闭工作会话" @click="sessionDrawer = false">
          <YdIcon name="morph:x" />
        </YdButton>
      </header>
      <div class="yd-agent-overlay__body">
        <YdChatSessionList :sessions="sessionItems" :active-id="sessionId" @select="selectSession" @create="emit('sessionCreate'); sessionDrawer = false" @rename="emit('sessionRename', $event)" @pin="emit('sessionPin', $event)" @remove="emit('sessionRemove', $event)" />
      </div>
    </section>
    <section v-if="changesDrawer" class="yd-agent-overlay" aria-label="检查代码变更" @keydown.esc.stop="changesDrawer = false">
      <header class="yd-agent-overlay__head">
        <strong>检查代码变更</strong>
        <YdButton variant="ghost" size="icon-sm" aria-label="关闭检查代码变更" @click="changesDrawer = false">
          <YdIcon name="morph:x" />
        </YdButton>
      </header>
      <div class="yd-agent-overlay__body">
        <p class="yd-agent-overlay__desc">
          查看差异与验证结果，变更由宿主运行时提供。
        </p>
        <div class="yd-agent-change-list">
          <YdButton v-for="change in changes" :key="change.id" :variant="currentChange?.id === change.id ? 'secondary' : 'ghost'" @click="activeChange = change.id">
            <YdIcon name="morph:file-diff" /><span>{{ change.path }}</span><small>+{{ change.additions }} / −{{ change.deletions }}</small>
          </YdButton>
        </div>
        <YdCode v-if="currentChange" :code="currentChange.diff || '暂无差异内容'" language="diff" :title="currentChange.path" :line-numbers="false" />
        <p v-else class="yd-agent-empty-changes">
          当前任务没有代码变更。
        </p><slot name="changes-footer" :changes="changes" />
      </div>
    </section>
  </section>
</template>

<style scoped>
/* 窄宽度下的会话/上下文/变更：在工作台内部覆盖展示，不使用页面级抽屉（助手窗口内保持在窗口里） */
.yd-agent-overlay {
  position: absolute;
  z-index: 5;
  inset: 0;
  display: flex;
  flex-direction: column;
  background: oklch(var(--background));
}
.yd-agent-overlay__head {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid oklch(var(--border));
}
.yd-agent-overlay__body {
  flex: 1;
  min-height: 0;
  padding: 12px;
  overflow: auto;
}
.yd-agent-overlay__desc {
  margin: 0 0 10px;
  font-size: 12px;
  color: oklch(var(--muted-foreground));
}
.yd-agent-workbench {
  position: relative;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: min(880px, max(680px, calc(100dvh - 210px)));
  container-type: inline-size;
  overflow: hidden;
}

.yd-agent-demo {
  display: flex;
  flex: none;
  gap: 8px;
  align-items: center;
  padding: 9px 16px;
  font-size: 11px;
  color: var(--color-text-3);
  background: var(--color-fill-1);
  border-bottom: 1px solid var(--color-border-2);
}

.yd-agent-layout {
  display: grid;
  flex: 1;
  grid-template-columns: 180px minmax(0, 1fr);
  min-height: 0;
}

.yd-agent-layout.has-context {
  grid-template-columns: 180px minmax(0, 1fr) 270px;
}

.yd-agent-layout.is-mobile {
  grid-template-columns: minmax(0, 1fr);
}

.yd-agent-sessions {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg-2);
  border-right: 1px solid var(--color-border-2);
}

.yd-agent-sidebar-title {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 22px 14px 10px;
  font-size: 12px;
}

.yd-agent-sidebar-title span {
  margin-left: auto;
  color: var(--color-text-3);
}

.yd-agent-sessions :deep(.yd-session-list) {
  flex: 1;
  width: 100%;
  min-width: 0;
  min-height: 0;
  background: transparent;
  border: 0;
}

.yd-agent-sessions :deep(.yd-session-list__head) {
  padding: 12px;
}

.yd-agent-sessions :deep(.yd-session) {
  border-radius: var(--yd-control-radius);
}

.yd-agent-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  background: var(--color-bg-1);
}

.yd-agent-heading {
  display: flex;
  flex: none;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 20px 22px 12px;
}

.yd-agent-title {
  flex: 1;
  min-width: 0;
}

.yd-agent-title > span {
  font-size: 9px;
  color: var(--color-text-3);
  letter-spacing: 0.12em;
}

.yd-agent-title h2 {
  margin: 5px 0 0;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 17px;
  white-space: nowrap;
}

.yd-agent-heading-actions {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 5px;
  align-items: center;
  max-width: 100%;
}

.yd-agent-toolbar {
  display: flex;
  flex: none;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 0 22px 14px;
  font-size: 11px;
  border-bottom: 1px solid var(--color-border-2);
}

.yd-agent-toolbar :deep(.yd-select) {
  width: 170px;
  height: 30px;
  min-height: 30px;
  font-size: 11px;
}

.yd-agent-branch,
.yd-agent-run-state {
  display: inline-flex;
  gap: 5px;
  align-items: center;
  color: var(--color-text-3);
}

.yd-agent-run-state {
  margin-left: auto;
  color: var(--yd-emphasis);
}

.yd-agent-thread {
  flex: 1;
  min-height: 150px;
  padding: 24px;
  overflow: auto;
  overscroll-behavior: contain;
}

.yd-agent-message + .yd-agent-message {
  margin-top: 24px;
}

.yd-agent-message :deep(.yd-bubble-row__body) {
  min-width: 0;
}

.yd-agent-message :deep(.yd-bubble-row__avatar) {
  color: var(--yd-emphasis);
}

.yd-agent-message-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.yd-agent-message-actions :deep(.yd-button) {
  height: 26px;
  min-height: 26px;
  font-size: 11px;
}

.yd-agent-welcome {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 12px;
  align-content: center;
  justify-items: start;
  min-width: 0;
  max-width: 600px;
  min-height: 100%;
  margin-inline: auto;
}

.yd-agent-welcome-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  font-size: 26px;
  color: var(--yd-emphasis);
  background: var(--color-fill-2);
  border-radius: var(--yd-control-radius);
}

.yd-agent-welcome h3 {
  margin: 0;
  font-size: clamp(24px, 3vw, 34px);
  font-weight: 650;
  letter-spacing: -0.03em;
}

.yd-agent-welcome p {
  margin: 0;
  font-size: 13px;
  line-height: 1.8;
  color: var(--color-text-3);
}

.yd-agent-welcome .yd-agent-eyebrow {
  font-size: 10px;
  letter-spacing: 0.08em;
}

.yd-agent-starters {
  display: grid;
  gap: 8px;
  width: 100%;
  margin-top: 10px;
}

.yd-agent-starters :deep(.yd-button) {
  justify-content: flex-start;
  max-width: 100%;
  font-size: 12px;
  text-align: left;
  white-space: normal;
}

.yd-agent-inspector {
  min-width: 0;
  min-height: 0;
  overflow: auto;
  background: var(--color-bg-2);
  border-left: 1px solid var(--color-border-2);
}

.yd-agent-trace {
  flex: none;
  max-height: 185px;
  margin: 0 20px 10px;
  overflow: auto;
}

.yd-agent-trace :deep(.yd-thought-chain__head) {
  font-size: 11px;
}

.yd-agent-trace :deep(.yd-thought-chain__list) {
  padding-block: 10px;
}

.yd-agent-trace :deep(.yd-thought-chain__icon) {
  color: var(--yd-emphasis);
}

.yd-agent-approvals {
  flex: none;
  max-height: 200px;
  padding: 0 20px 10px;
  overflow: auto;
}

.yd-agent-approval {
  padding: 12px 14px;
  background: var(--color-fill-1);
  border: 1px solid var(--color-border-3);
  border-radius: var(--yd-control-radius);
}

.yd-agent-approval + .yd-agent-approval {
  margin-top: 8px;
}

.yd-agent-approval > div:first-child {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
}

.yd-agent-approval p {
  margin: 8px 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-text-2);
}

.yd-agent-approval code,
.yd-agent-approval small {
  display: block;
  font-size: 11px;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
}

.yd-agent-approval-actions {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
  margin-top: 10px;
}

.yd-agent-main :deep(.yd-sender) {
  padding: 8px 20px 16px;
}

.yd-agent-main :deep(.yd-sender__bar) {
  flex-wrap: wrap;
}

.yd-agent-main :deep(.yd-sender__bar .yd-select) {
  width: auto;
  min-width: 85px;
  height: 28px;
  min-height: 28px;
  font-size: 11px;
}

.yd-agent-main :deep(.yd-sender__hint) {
  order: 3;
  width: 100%;
  margin-left: 0;
  font-size: 10px;
}

.yd-agent-main :deep(.yd-sender__action) {
  margin-left: auto;
}

.yd-agent-context-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  padding: 6px 8px 0;
}

.yd-agent-context-chips :deep(.yd-button) {
  max-width: 100%;
  height: 24px;
  padding-inline: 6px;
  font-size: 10px;
}

.yd-agent-context-chips span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-agent-input-hint {
  margin: 0 20px 12px;
  font-size: 11px;
  color: var(--color-text-2);
}

.yd-agent-change-list {
  display: grid;
  gap: 4px;
  margin-bottom: 18px;
}

.yd-agent-change-list :deep(.yd-button) {
  justify-content: flex-start;
  height: auto;
  min-height: 36px;
}

.yd-agent-change-list span {
  overflow-wrap: anywhere;
}

.yd-agent-change-list small {
  flex: none;
  margin-left: auto;
}

.yd-agent-empty-changes {
  font-size: 13px;
  color: var(--color-text-3);
}

.yd-agent-spin {
  animation: yd-agent-spin calc(1.2s * var(--yd-motion-loop-scale)) linear infinite;
}

@keyframes yd-agent-spin {
  to {
    rotate: 360deg;
  }
}

@media (width <= 768px) {
  .yd-agent-heading {
    flex-wrap: wrap;
    gap: 6px;
    padding: 14px 12px 10px;
  }

  .yd-agent-heading-actions {
    flex-basis: 100%;
    justify-content: flex-end;
  }

  .yd-agent-title h2 {
    font-size: 15px;
  }

  .yd-agent-toolbar {
    padding-inline: 12px;
  }

  .yd-agent-branch {
    display: none;
  }

  .yd-agent-thread {
    padding: 16px 12px;
  }

  .yd-agent-main :deep(.yd-sender) {
    padding-inline: 10px;
  }

  .yd-agent-demo {
    font-size: 10px;
  }
}
</style>
