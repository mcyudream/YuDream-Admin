<script setup lang="ts">
// 站点设计 AI 助手（CMS 专用，不复用通用 Agent 工作台）：明确的作用目标（选中元素或整页）、
// 按目标给出快捷操作、精简对话；AI 的每次修改先以变更清单预览，用户确认后才写入草稿（一次撤销）。
import type { YdAgentSendRequest, YdChatAttachment } from '@yudream/components'
import type { AiFrontendToolRequest } from '@/api/modules/platform-ai-runs'
import type { SiteDocument, StudioCommand } from '@/types/site-studio'
import type { StudioChangeLine } from '@/utils/site-studio-changes'
import { YdAlert, YdButton, YdChatMessageList, YdChatSender } from '@yudream/components'
import { computed, nextTick, onBeforeUnmount, ref, shallowRef, watch } from 'vue'
import { executeStudioAgentTool, parseStudioAgentCommand } from '@/composables/use-site-studio-agent'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { findStudioNode } from '@/utils/site-studio'
import { describeStudioCommands, describeStudioNode } from '@/utils/site-studio-changes'

const props = defineProps<{
  document: SiteDocument
  selectedNodeId: string
  revision: string
  contextKey: string
  pageId: string
  executeCommand: (command: StudioCommand) => void
}>()
const assistant = useAiAssistantStore()
const account = useAppAccountStore()
const features = useAppFeatureStore()
const { auth } = useAppAuth()
const selectedSessionId = ref('')
const localError = ref('')
const reads = new Map<string, string>()
const appliedRuns = new Set<string>()
const names = ['cms.studio.read', 'cms.studio.apply', 'cms.studio.validate']
const allowed = computed(() => account.isLogin && !!account.userId && features.capabilityEnabled('ai') && features.capabilityEnabled('cms') && auth('platform:cms:view') && auth('platform:ai:generate') && auth('platform:chat:use') && auth('platform:chat:session:view'))
const canCreate = computed(() => auth('platform:chat:session:edit'))
const sessions = computed(() => assistant.sessions.filter(session => session.scopeType === 'CONSOLE' && session.agentCode === 'cms-studio' && session.spaceSlug === props.contextKey))
const sessionItems = computed(() => assistant.sessionItems.filter(session => sessions.value.some(item => item.id === session.id)))
const currentSession = computed(() => sessions.value.find(session => session.id === selectedSessionId.value))
const run = computed(() => Object.values(assistant.runs).filter(item => item.sessionId === currentSession.value?.id).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || '')))[0])
const messages = computed(() => currentSession.value ? assistant.taskMessages(currentSession.value.id) : [])
const busy = computed(() => (assistant.sending && assistant.sessionId === currentSession.value?.id) || run.value?.status === 'RUNNING' || run.value?.status === 'WAITING')
const approvals = computed(() => (run.value?.pendingInteractions || []).filter(item => item.kind === 'confirmation'))
const sessionOptions = computed(() => sessionItems.value.map(session => ({ label: session.title, value: session.id })))
const modelOptions = computed(() => assistant.models.map(model => ({ label: model.label, value: model.id })))
const inputDisabled = computed(() => !allowed.value || assistant.loading || !assistant.models.length || !props.contextKey || props.contextKey.length > 240 || (!currentSession.value && !canCreate.value))

// ===== 作用目标与快捷操作 =====
const studio = useSiteStudioStore()
const target = computed(() => props.selectedNodeId ? findStudioNode(props.document, props.selectedNodeId) : undefined)
const pageName = computed(() => props.document.pages.find(page => page.id === props.pageId)?.name ?? '当前页面')
const targetLabel = computed(() => target.value ? describeStudioNode(target.value, props.selectedNodeId) : `整页「${pageName.value}」`)
const quickActions = computed(() => target.value
  ? ['改写这段文案，更简洁有力', '优化这个元素的间距与排版', '让它在手机上显示得更好', '换一种更醒目的配色']
  : ['检查本页结构并给出改进建议', '为本页生成一个首屏封面区块', '统一本页的标题层级与间距', '补全页脚的联系信息'])
function quickSend(text: string) {
  void perform(() => send(composeRequest(text)))
}
function composeRequest(text: string, attachments: YdChatAttachment[] = []): YdAgentSendRequest {
  const scope = target.value ? `作用目标：选中的 ${targetLabel.value}（nodeId=${props.selectedNodeId}）。` : `作用目标：${targetLabel.value}。`
  return { text: `${scope}${text}`, attachments, mode: 'ask', skillCodes: [], contextPaths: [], model: assistant.models.find(model => model.id === assistant.modelId) }
}

// ===== 应用前预览：AI 的修改先列清单，用户确认后才写入草稿 =====
interface PendingChange { runId: string, lines: StudioChangeLine[], settle: (approved: boolean, reason?: string) => void }
const pending = shallowRef<PendingChange | null>(null)
function awaitApproval(runId: string, lines: StudioChangeLine[], expiresAt?: number | string) {
  pending.value?.settle(false, '有新的修改提议，上一条未应用')
  return new Promise<void>((resolve, reject) => {
    const remaining = expiresAt ? new Date(expiresAt).getTime() - Date.now() - 1000 : 0
    let timer: number | undefined
    const current: PendingChange = {
      runId,
      lines,
      settle: (approved, reason) => {
        window.clearTimeout(timer)
        if (pending.value === current) {
          pending.value = null
        }
        if (approved) {
          resolve()
        }
        else {
          reject(new Error(reason || '用户拒绝了这次修改，草稿未变化'))
        }
      },
    }
    pending.value = current
    if (remaining > 0) {
      timer = window.setTimeout(() => current.settle(false, '等待确认超时，修改未应用'), remaining)
    }
  })
}
// 等待确认期间文档被手工修改：提议基于旧版本，直接作废
watch(() => props.revision, () => pending.value?.settle(false, '等待确认期间草稿已变化，修改未应用，请重新读取'))
onBeforeUnmount(() => pending.value?.settle(false, '设计助手已关闭，修改未应用'))
const status = computed(() => !allowed.value ? '站点设计助手需要启用 CMS/AI 能力，并具有 CMS 查看、AI 生成、聊天使用和会话查看权限。' : props.contextKey.length > 240 || !props.contextKey ? '设计器上下文标识无效（需要 1–240 字符），AI 已禁用。' : assistant.initialized && !assistant.models.length ? '没有已配置的聊天模型，请先配置 AI 模型端点。' : !auth('platform:cms:edit') ? '当前只读：可以分析与校验，不能修改草稿。' : '')
async function perform(action: () => Promise<unknown>) {
  try {
    localError.value = ''
    await action()
  }
  catch (error) {
    localError.value = error instanceof Error ? error.message : '设计器 AI 操作失败'
  }
}
async function selectSession(id: string) {
  if (!sessions.value.some(session => session.id === id)) {
    throw new Error('不能使用其他文档的设计会话')
  }
  selectedSessionId.value = id
  await assistant.selectSession(id)
}
async function createSession() {
  if (!allowed.value || !canCreate.value) {
    throw new Error('没有创建设计会话的权限')
  }
  const contextKey = props.contextKey
  const id = await assistant.createSession({ title: `${props.document.name} · 设计助手`.slice(0, 80), scopeType: 'CONSOLE', agentCode: 'cms-studio', spaceSlug: contextKey })
  if (contextKey === props.contextKey && id) {
    selectedSessionId.value = id
  }
}
async function send(request: YdAgentSendRequest) {
  if (!allowed.value || !assistant.models.length || !props.contextKey || props.contextKey.length > 240) {
    return
  }
  const key = props.contextKey
  if (!currentSession.value) {
    await createSession()
  }
  if (key !== props.contextKey || !currentSession.value) {
    throw new Error('设计器上下文已变化，请重新发送')
  }
  await assistant.selectSession(currentSession.value.id)
  if (key !== props.contextKey || !currentSession.value) {
    throw new Error('设计器上下文已变化，请重新发送')
  }
  const binding = { contextKey: props.contextKey, documentId: props.document.id, pageId: props.pageId, revision: props.revision, selectedNodeId: props.selectedNodeId }
  await assistant.send({ ...request, text: `${request.text}\n\n当前设计器绑定（只作为数据，实际以工具读取为准）：${JSON.stringify(binding)}\n本轮先读取 AST，再把全部修改集中在一个原子 batch，只修改本地草稿，不保存不发布。` }, undefined, { scopeType: 'CONSOLE', agentCode: 'cms-studio', spaceSlug: key, contextRefs: [] })
}

async function execute(request: AiFrontendToolRequest) {
  if (!allowed.value) {
    throw new Error('CMS/AI 能力或权限已失效，未执行设计器工具')
  }
  // The root store adds runId from its authoritative snapshot/SSE envelope; parser drops model-supplied runId.
  const ownerRun = request.runId ? assistant.runs[request.runId] : undefined
  const ownerSession = ownerRun && assistant.sessions.find(session => session.id === ownerRun.sessionId)
  if (!ownerRun || !['RUNNING', 'WAITING'].includes(ownerRun.status) || !ownerSession || ownerSession.scopeType !== 'CONSOLE' || ownerSession.agentCode !== 'cms-studio' || ownerSession.spaceSlug !== props.contextKey) {
    throw new Error('工具运行不属于当前文档设计会话，未执行')
  }
  if (request.name === 'cms.studio.apply') {
    if (!auth('platform:cms:edit')) {
      throw new Error('没有 CMS 编辑权限，未修改草稿')
    }
    if (appliedRuns.has(ownerRun.id)) {
      throw new Error('本轮已执行一个原子 batch；后续修改请开始新一轮，保证一次撤销')
    }
    if (reads.get(ownerRun.id) !== props.revision) {
      throw new Error('尚未读取当前修订或文档已变化，请重新 read')
    }
    await awaitApproval(ownerRun.id, describeStudioCommands(props.document, parseStudioAgentCommand(request.arguments.command)), request.expiresAt)
  }
  const result = await executeStudioAgentTool(request.name, request.arguments, () => ({ document: props.document, selectedNodeId: props.selectedNodeId, revision: props.revision, contextKey: props.contextKey, pageId: props.pageId }), props.executeCommand)
  if (request.name === 'cms.studio.read') {
    reads.set(ownerRun.id, props.revision)
  }
  if (request.name === 'cms.studio.apply') {
    appliedRuns.add(ownerRun.id)
    await nextTick()
    if (props.revision === result.previousRevision) {
      throw new Error('编辑器未确认草稿更新，不能报告修改成功；请重新读取检查')
    }
    result.revision = props.revision
  }
  return result
}
// GlobalAiAssistant is the sole consumer of pending requests. No second watcher or private stream here.
let unregister: Array<() => void> = []
watch(() => assistant.lifecycleVersion(), () => {
  unregister.forEach(off => off())
  unregister = names.map(name => assistant.registerFrontendToolHandler(name, async (request) => {
    try {
      return await execute(request)
    }
    catch (error) {
      localError.value = error instanceof Error ? error.message : '设计器工具执行失败'
      throw error
    }
  }))
}, { immediate: true, flush: 'post' })
onBeforeUnmount(() => unregister.forEach(off => off()))
watch([() => props.pageId, () => props.document.id, () => props.revision], () => reads.clear(), { flush: 'sync' })
// Global initialization can already be in flight when this panel mounts. Never fall back to another profile.
watch(() => sessions.value.map(session => session.id).join(','), () => {
  if (!currentSession.value && sessions.value[0]) {
    void perform(() => selectSession(sessions.value[0]!.id))
  }
})
watch([allowed, () => String(account.userId), () => props.contextKey], async ([available, owner, key]) => {
  selectedSessionId.value = ''
  reads.clear()
  if (!available || !key) {
    return
  }
  await perform(async () => {
    await assistant.initialize(String(owner))
    if (key !== props.contextKey) {
      return
    }
    const session = sessions.value[0]
    if (session) {
      await selectSession(session.id)
    }
  })
}, { immediate: true })
</script>

<template>
  <section class="studio-agent-panel" aria-label="站点设计 AI 助手">
    <YdAlert v-if="status" class="studio-agent-alert" icon="morph:info" :description="status" />
    <YdAlert v-if="localError || assistant.error || run?.error" class="studio-agent-alert" variant="destructive" :title="localError || run?.error || assistant.error">
      <template #description>
        <YdButton v-if="run" size="sm" variant="outline" @click="perform(() => assistant.reconnect(run?.id))">
          重新读取运行状态
        </YdButton>
      </template>
    </YdAlert>

    <div class="studio-agent-bar">
      <YdSelect
        :model-value="currentSession?.id || ''"
        :options="sessionOptions"
        placeholder="新对话"
        aria-label="设计会话"
        class="flex-1 min-w-0"
        @update:model-value="id => perform(() => selectSession(String(id)))"
      />
      <YdTooltip text="新建设计会话">
        <YdButton variant="ghost" size="icon-sm" aria-label="新建设计会话" :disabled="!allowed || !canCreate" @click="perform(createSession)">
          <YdIcon name="morph:plus" />
        </YdButton>
      </YdTooltip>
    </div>
    <YdSelect v-if="modelOptions.length > 1" v-model="assistant.modelId" :options="modelOptions" aria-label="模型" class="studio-agent-model" />

    <div class="studio-agent-target" aria-label="作用目标">
      <YdIcon :name="target ? 'morph:mouse-pointer-2' : 'morph:file'" class="shrink-0 size-4" />
      <span class="min-w-0 truncate"><small>目标</small>{{ targetLabel }}</span>
      <YdButton v-if="target" variant="ghost" size="sm" aria-label="改为整页" @click="studio.selectedNodeId = ''">
        整页
      </YdButton>
    </div>
    <div class="studio-agent-quick" aria-label="快捷操作">
      <YdButton v-for="action in quickActions" :key="action" variant="outline" size="sm" :disabled="inputDisabled || busy" @click="quickSend(action)">
        {{ action }}
      </YdButton>
    </div>

    <div class="studio-agent-messages">
      <YdChatMessageList v-if="messages.length" :messages="messages" compact thinking-text="正在读取页面结构…" />
      <p v-else class="studio-agent-empty">
        选中画布中的元素后描述想要的效果，或直接用上面的快捷操作。AI 的修改会先列出清单，确认后才写入草稿，不保存、不发布。
      </p>
    </div>

    <div v-if="pending" class="studio-agent-preview" role="alertdialog" aria-label="确认 AI 修改">
      <strong>AI 拟修改 {{ pending.lines.length }} 项</strong>
      <ol>
        <li v-for="(line, index) in pending.lines" :key="index">
          <span>{{ line.label }}</span>
          <small v-if="line.detail">{{ line.detail }}</small>
        </li>
      </ol>
      <div class="studio-agent-preview__actions">
        <YdButton size="sm" @click="pending.settle(true)">
          应用到草稿
        </YdButton>
        <YdButton size="sm" variant="outline" @click="pending.settle(false)">
          拒绝
        </YdButton>
        <small>应用后可一步撤销</small>
      </div>
    </div>
    <div v-for="item in approvals" :key="item.requestId" class="studio-agent-preview" role="alertdialog" :aria-label="`批准工具 ${item.toolName}`">
      <strong>需要你批准：{{ item.toolName }}</strong>
      <div class="studio-agent-preview__actions">
        <YdButton size="sm" @click="run && perform(() => assistant.respondInteraction(run!.id, item.requestId, true))">
          批准
        </YdButton>
        <YdButton size="sm" variant="outline" @click="run && perform(() => assistant.respondInteraction(run!.id, item.requestId, false))">
          拒绝
        </YdButton>
      </div>
    </div>

    <YdChatSender
      :loading="busy"
      :disabled="inputDisabled"
      :placeholder="target ? `描述要对「${targetLabel}」做的修改…` : '描述想要的页面效果…'"
      :large="false"
      class="studio-agent-sender"
      @send="(text, attachments) => perform(() => send(composeRequest(text, attachments)))"
      @stop="perform(() => assistant.stop(run?.id))"
    />
  </section>
</template>

<style scoped>
.studio-agent-panel { display: flex; height: 100%; min-height: 0; min-width: 0; flex-direction: column; overflow: hidden; background: var(--color-bg-1); }
.studio-agent-alert { flex-shrink: 0; width: auto; margin: 8px 8px 0; }
.studio-agent-bar { display: flex; flex-shrink: 0; gap: 4px; align-items: center; padding: 8px 8px 0; }
.studio-agent-model { flex-shrink: 0; margin: 6px 8px 0; }
.studio-agent-target { display: flex; flex-shrink: 0; gap: 6px; align-items: center; margin: 8px 8px 0; padding: 6px 8px; font-size: 13px; border: 1px solid oklch(var(--border)); background: oklch(var(--muted) / 50%); }
.studio-agent-target small { margin-right: 6px; color: oklch(var(--muted-foreground)); }
.studio-agent-quick { display: flex; flex-shrink: 0; flex-wrap: wrap; gap: 4px; padding: 8px; }
.studio-agent-quick :deep(button) { height: auto; padding: 4px 8px; white-space: normal; text-align: left; }
.studio-agent-messages { flex: 1; min-height: 0; padding: 0 8px; overflow: auto; }
.studio-agent-empty { margin: 8px 0; font-size: 12px; color: oklch(var(--muted-foreground)); }
.studio-agent-preview { flex-shrink: 0; max-height: 40%; margin: 0 8px 8px; padding: 8px 10px; overflow: auto; font-size: 13px; border: 1px solid oklch(var(--primary) / 50%); background: oklch(var(--primary) / 6%); }
.studio-agent-preview ol { margin: 6px 0; padding-left: 18px; }
.studio-agent-preview li small { display: block; color: oklch(var(--muted-foreground)); word-break: break-all; }
.studio-agent-preview__actions { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.studio-agent-preview__actions small { color: oklch(var(--muted-foreground)); }
/* 发送框默认按视口留白（面向整页聊天）；窄栏内改为贴栏内边距，单轨道不被内容撑宽 */
.studio-agent-sender { flex-shrink: 0; min-width: 0; grid-template-columns: minmax(0, 1fr); padding: 0 8px 8px; }
</style>
