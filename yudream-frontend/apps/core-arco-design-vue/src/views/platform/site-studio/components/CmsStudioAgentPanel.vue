<script setup lang="ts">
import type { YdAgentApproval, YdAgentHarnessState, YdAgentSendRequest } from '@yudream/components'
import type { AiFrontendToolRequest } from '@/api/modules/platform-ai-runs'
import type { SiteDocument, StudioCommand } from '@/types/site-studio'
import { YdAgentWorkbench, YdAlert, YdButton } from '@yudream/components'
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { executeStudioAgentTool } from '@/composables/use-site-studio-agent'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'

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
const harness = computed<YdAgentHarnessState>(() => ({
  name: 'AgentScope Harness',
  runId: run.value?.id,
  status: assistant.sending && assistant.sessionId === currentSession.value?.id ? 'running' : run.value?.status === 'RUNNING' ? 'running' : run.value?.status === 'WAITING' ? 'waiting' : run.value?.status === 'FAILED' ? 'error' : run.value?.status === 'CANCELLED' ? 'cancelled' : run.value?.status === 'COMPLETED' ? 'completed' : 'idle',
}))
const approvals = computed<YdAgentApproval[]>(() => (run.value?.pendingInteractions || []).filter(item => item.kind === 'confirmation').map(item => ({ id: item.requestId, title: `批准草稿工具：${item.toolName}`, description: JSON.stringify(item.arguments, null, 2), scope: '仅当前文档本地草稿；不保存、不发布' })))
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
    <YdAlert v-if="status" :title="status" />
    <YdAlert v-if="localError || assistant.error || run?.error" variant="destructive" :title="localError || run?.error || assistant.error">
      <YdButton v-if="run" size="sm" variant="outline" @click="perform(() => assistant.reconnect(run?.id))">
        重新读取运行状态
      </YdButton>
    </YdAlert>
    <YdAgentWorkbench
      :sessions="sessionItems" :session-id="currentSession?.id || ''" :models="assistant.models" :model-id="assistant.modelId"
      :messages="messages" :harness="harness" :approvals="approvals" :available-modes="['ask']"
      :disabled="!allowed || assistant.loading || !assistant.models.length || !props.contextKey || props.contextKey.length > 240 || (!currentSession && !canCreate)"
      :suggestions="['分析当前页面并说明改进范围', '优化当前选中节点的排版', '检查站点文档结构与安全性']"
      placeholder="描述设计目标；仅修改草稿，不保存、不发布…" class="studio-agent-panel__workbench"
      @send="request => perform(() => send(request))" @stop="perform(() => assistant.stop(run?.id))"
      @update:model-id="assistant.modelId = $event" @update:session-id="id => perform(() => selectSession(id))"
      @session-create="perform(createSession)" @session-pin="session => canCreate && perform(() => assistant.updateSession(session.id, { pinned: !session.pinned }))"
      @approval-decision="(id, approved) => run && perform(() => assistant.respondInteraction(run!.id, id, approved))"
    >
      <template #sender-actions>
        <span>范围：当前站点 AST 草稿 · 原子批处理 · 一次撤销 · 不保存、不发布</span>
      </template>
    </YdAgentWorkbench>
  </section>
</template>

<style scoped>
.studio-agent-panel { display: flex; height: 100%; min-height: 0; min-width: 0; flex-direction: column; overflow: hidden; background: var(--color-bg-1); }
.studio-agent-panel__workbench { flex: 1; min-height: 0; }
</style>
