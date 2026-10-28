<script setup lang="ts">
import type { YdAgentSendRequest, YdAgentSession, YdAgentWorkspace, YdChatAttachment, YdChatCitation, YdChatMessage, YdChatRetrievalHit } from '@yudream/components'
import type { AxiosError } from 'axios'
import type { AgentApplication, AgentCatalog } from '@/api/modules/platform-agent'
import type { ChatScopeType } from '@/api/modules/platform-chat'
import { YdAgentWorkbench, YdAlert, YdButton, YdIcon } from '@yudream/components'
import apiAgent from '@/api/modules/platform-agent'
import { uploadChatAttachment } from '@/api/modules/platform-chat'
import AiToolPicker from '@/components/AiToolPicker/index.vue'
import WorkspaceExplorer from '@/components/WorkspaceExplorer/index.vue'
import { useWorkspaceReferences } from '@/composables/use-workspace-references'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { matchesAiSessionScope } from '@/utils/ai-session-scope'
import { hintForMessage, resolveApiErrorMessage } from '@/utils/api-error'

const assistant = useAiAssistantStore()
const account = useAppAccountStore()
const { auth } = useAppAuth()
const route = useRoute()
const router = useRouter()
const canEdit = computed(() => auth('platform:chat:session:edit'))
const scope = ref<ChatScopeType>('GENERAL')
const selectedAgent = ref('')
const wikiSlug = ref('')
const agents = ref<AgentApplication[]>([])
const catalog = ref<AgentCatalog>()
const attachments = ref<YdChatAttachment[]>([])
const uploading = ref(false)
const uploadProgress = ref('')
const sessionSaving = ref(false)
const sessionError = ref('')
const renameOpen = ref(false)
const removeOpen = ref(false)
const targetSession = ref<YdAgentSession>()
const title = ref('')
/** 工作区模式：问答始终可用；规划需能查看工作区；编码需查看+编辑（后端缺查看权限时不装配任何工作区工具） */
const canViewWorkspace = computed(() => auth('platform:chat:workspace:view'))
const canEditWorkspace = computed(() => auth('platform:chat:workspace:edit'))
/** 恢复上次使用的工作区模式；权限变化时降级到可用模式 */
function restoreMode(): 'ask' | 'plan' | 'code' {
  const saved = assistant.preferences.workspaceMode
  if (saved === 'plan') {
    return canViewWorkspace.value ? 'plan' : 'ask'
  }
  if (saved === 'code') {
    return canViewWorkspace.value && canEditWorkspace.value ? 'code' : 'ask'
  }
  return 'ask'
}
const mode = ref<'ask' | 'plan' | 'code'>(restoreMode())
watch(mode, value => assistant.setPreferences({ workspaceMode: value }))
const workspaceModes = computed(() => [
  'ask' as const,
  ...(canViewWorkspace.value ? ['plan' as const] : []),
  ...(canViewWorkspace.value && canEditWorkspace.value ? ['code' as const] : []),
])
/** 工作区文件：可 @ 引用进消息；文件管理器里可编辑、整理，并把附件另存到工作区 */
const workspaceRefs = useWorkspaceReferences(() => canViewWorkspace.value)
const explorerOpen = ref(false)
const conversationAttachments = computed<YdChatAttachment[]>(() => [...attachments.value, ...assistant.conversationMessages.flatMap(message => message.attachments ?? [])])
const workspaces = computed<YdAgentWorkspace[]>(() => canViewWorkspace.value
  ? [{ id: 'default', name: '我的工作区', kind: 'remote', access: canEditWorkspace.value ? 'write' : 'read', description: '每用户默认工作区：规划与编码模式在这里读写文件' }]
  : [])
const scopeOptions = computed(() => [
  { label: '通用助手', value: 'GENERAL' },
  ...(agents.value.length ? [{ label: 'Agent 应用', value: 'AGENT' }] : []),
  ...(auth('platform:wiki:view') ? [{ label: '知识库问答', value: 'WIKI' }] : []),
])
const suggestions = computed(() => {
  if (scope.value === 'WIKI') {
    return ['概括所选知识库的主要内容', '查找与我的问题相关的文档并标注来源', '对比相关文档中的不同方案', '根据知识库整理一份入门指南']
  }
  if (scope.value === 'AGENT') {
    return ['介绍你能处理的任务', '开始前列出需要我提供的信息', '为我的目标制定执行步骤', '检查执行结果并总结下一步']
  }
  if (mode.value === 'plan') {
    return ['梳理工作区的目录结构', '分析现有文件并制定修改计划', '比较实现方案与各自的影响', '把我的需求拆成可执行的步骤']
  }
  if (mode.value === 'code') {
    return ['审查工作区代码并给出改进建议', '根据我的需求修改工作区文件', '为现有代码补充必要的测试', '整理本次修改并说明验证方法']
  }
  return ['帮我整理一份项目周报', '把这段内容总结成要点', '审查我提供的代码', '帮我比较方案并给出建议']
})
const scopeIssue = computed(() => {
  if (scope.value === 'AGENT' && !selectedAgent.value) {
    return '请选择要使用的 Agent 应用。'
  }
  if (scope.value === 'WIKI' && !wikiSlug.value) {
    return '请选择一个知识库，或选择「全部知识库」。'
  }
  return ''
})
// Agent 应用使用工作流自身的模型；普通问答与知识库问答需要聊天模型。
const modelUnavailable = computed(() => assistant.initialized && !assistant.loading && !assistant.models.length && scope.value !== 'AGENT')
const needsModel = computed(() => modelUnavailable.value && !assistant.error)
const inputDisabled = computed(() => assistant.loading || uploading.value || !auth('platform:chat:use') || modelUnavailable.value || Boolean(scopeIssue.value))
const startsNewSession = computed(() => assistant.conversationSessionId && !matchesAiSessionScope(assistant.activeSession, sessionData()))
const contextSummary = computed(() => {
  if (scope.value === 'AGENT') {
    return agents.value.find(agent => agent.code === selectedAgent.value)?.description || '由所选应用处理任务，执行进度会保留在会话中。'
  }
  if (scope.value === 'WIKI') {
    return '基于所选知识库回答，并在回答中提供来源。'
  }
  if (mode.value === 'plan') {
    return '规划模式：读取工作区并制定方案。'
  }
  if (mode.value === 'code') {
    return '编码模式：可修改工作区文件，写入操作会请求确认。'
  }
  return '描述问题，或添加附件；需要访问外部信息时，可在「工具」中启用对应能力。'
})
const senderPlaceholder = computed(() => scope.value === 'WIKI' ? '输入要从知识库中查找的问题…' : scope.value === 'AGENT' ? '描述希望应用完成的任务…' : mode.value === 'plan' ? '描述目标，我会先分析并制定计划…' : mode.value === 'code' ? '描述需要完成的修改与预期结果…' : '输入问题，或添加附件一起讨论…')

async function perform(action: () => Promise<unknown>) {
  try {
    await action()
  }
  catch (error) {
    assistant.setError(error)
  }
}

watch(() => assistant.activeSession, (session) => {
  scope.value = session?.scopeType === 'CONSOLE' ? 'GENERAL' : session?.scopeType || 'GENERAL'
  selectedAgent.value = session?.scopeType === 'AGENT' ? session.agentCode || '' : ''
  wikiSlug.value = session?.spaceSlug || ''
  attachments.value = []
}, { immediate: true })

function sessionData() {
  const model = assistant.models.find(item => item.id === assistant.modelId)
  return {
    scopeType: scope.value,
    agentCode: scope.value === 'AGENT' ? selectedAgent.value : undefined,
    spaceSlug: scope.value !== 'AGENT' ? wikiSlug.value || undefined : undefined,
    providerCode: model?.providerCode,
    modelCode: model?.modelCode,
  }
}

async function send(request: YdAgentSendRequest) {
  if (inputDisabled.value || assistant.busy) {
    return
  }
  const text = await workspaceRefs.compose(request.text.trim() || request.text)
  await assistant.send({ ...request, text }, undefined, {
    ...sessionData(),
    contextRefs: scope.value !== 'AGENT' && wikiSlug.value ? [{ type: 'wiki', target: wikiSlug.value === '__all__' ? 'all' : wikiSlug.value }] : [],
  })
  if (!assistant.error) {
    attachments.value = []
  }
}

function retry(message: YdChatMessage) {
  const text = assistant.retryText(message)
  if (text) {
    void perform(() => send({ text, attachments: [], mode: mode.value, skillCodes: [], contextPaths: [] }))
  }
}

async function attach(files: File[]) {
  if (uploading.value || assistant.busy) {
    return
  }
  uploading.value = true
  try {
    const session = assistant.sessionId
    for (const [index, file] of files.entries()) {
      uploadProgress.value = `正在上传 ${index + 1}/${files.length}：${file.name}`
      const attachment = await uploadChatAttachment(file)
      if (session !== assistant.sessionId) {
        return
      }
      attachments.value.push(attachment)
    }
  }
  catch (error) {
    assistant.setError(error)
  }
  finally {
    uploading.value = false
    uploadProgress.value = ''
  }
}

function rename(session: YdAgentSession) {
  targetSession.value = session
  title.value = session.title
  sessionError.value = ''
  renameOpen.value = true
}
function remove(session: YdAgentSession) {
  targetSession.value = session
  sessionError.value = ''
  removeOpen.value = true
}

async function confirmSessionAction(kind: 'rename' | 'remove', action: 'confirm' | 'cancel' | 'close', done: () => void) {
  if (sessionSaving.value) {
    return
  }
  if (action !== 'confirm') {
    return done()
  }
  const session = targetSession.value
  if (!session || (kind === 'rename' && !title.value.trim())) {
    return
  }
  sessionSaving.value = true
  sessionError.value = ''
  try {
    if (kind === 'rename') {
      await assistant.updateSession(session.id, { title: title.value.trim() })
    }
    else { await assistant.removeSession(session.id) }
    done()
  }
  catch (error) {
    sessionError.value = error instanceof Error ? hintForMessage(await resolveApiErrorMessage(error as AxiosError)) : '会话操作失败，请稍后重试'
  }
  finally {
    sessionSaving.value = false
  }
}

function reloadAssistant() {
  if (account.userId) {
    void perform(() => assistant.initialize(String(account.userId), true))
  }
}

function openSource(source: YdChatCitation | YdChatRetrievalHit) {
  if (source.sourceUrl?.startsWith('/') && !source.sourceUrl.startsWith('//')) {
    void router.push(source.sourceUrl)
  }
  else if (source.sourceUrl && /^https?:\/\//i.test(source.sourceUrl)) {
    window.open(source.sourceUrl, '_blank', 'noopener,noreferrer')
  }
  else if (source.spaceSlug && source.path) {
    void router.push(`/wiki/${encodeURIComponent(source.spaceSlug)}/${encodeURI(source.path)}`)
  }
}

onMounted(() => perform(async () => {
  if (!account.isLogin || !account.userId) {
    return
  }
  void workspaceRefs.load()
  await assistant.initialize(String(account.userId))
  // Agent 应用与知识库目录是可选增强：能力未启用或无权限时静默降级，不影响通用问答
  const [catalogResult, agentResult] = await Promise.allSettled([apiAgent.catalog({ skipGlobalToast: true }), apiAgent.available({ skipGlobalToast: true })])
  catalog.value = catalogResult.status === 'fulfilled' ? catalogResult.value.data : undefined
  agents.value = agentResult.status === 'fulfilled' ? agentResult.value.data : []
  if ((route.query.scopeType === 'AGENT' || route.query.scopeType === 'WIKI') && canEdit.value) {
    await assistant.createSession({
      scopeType: route.query.scopeType,
      agentCode: typeof route.query.agentCode === 'string' ? route.query.agentCode : undefined,
      spaceSlug: typeof route.query.spaceSlug === 'string' ? route.query.spaceSlug : undefined,
    })
  }
}))
</script>

<template>
  <main class="chat-page">
    <YdAlert v-if="assistant.error" variant="destructive" :title="assistant.error">
      <YdButton v-if="assistant.activeRun && assistant.busy" variant="outline" size="sm" @click="perform(assistant.reconnect)">
        恢复后端进度
      </YdButton>
      <YdButton v-else variant="outline" size="sm" :loading="assistant.loading" @click="reloadAssistant">
        重新加载
      </YdButton>
    </YdAlert>
    <YdAgentWorkbench
      v-model:mode="mode"
      :sessions="assistant.conversationItems"
      :session-id="assistant.conversationSessionId"
      :models="assistant.models"
      :model-id="assistant.modelId"
      :available-modes="workspaceModes"
      :workspaces="workspaces"
      workspace-id="default"
      :approvals="assistant.conversationApprovals"
      :files="canViewWorkspace ? workspaceRefs.files.value : []"
      :skills="assistant.skills"
      :messages="assistant.conversationMessages"
      :harness="assistant.conversationHarness"
      :activities="assistant.conversationActivities"
      :attachments="attachments"
      :attachments-enabled="true"
      :disabled="inputDisabled"
      :placeholder="senderPlaceholder"
      :suggestions="suggestions"
      class="chat-page__workbench"
      @send="request => perform(() => send(request))"
      @stop="perform(assistant.stop)"
      @retry="message => retry(message)"
      @approval-decision="(id, approved) => assistant.activeRun && perform(() => assistant.respondInteraction(assistant.activeRun!.id, id, approved))"
      @attach="attach"
      @file-toggle="(path, included) => workspaceRefs.toggle(path, included)"
      @skill-toggle="(code, enabled) => assistant.setSkillEnabled(code, enabled)"
      @file-select="explorerOpen = true"
      @remove-attachment="attachment => attachments = attachments.filter(item => item !== attachment)"
      @update:session-id="id => perform(() => assistant.selectSession(id))"
      @update:model-id="assistant.modelId = $event"
      @session-create="canEdit && perform(() => assistant.createSession(sessionData()))"
      @session-rename="session => canEdit && rename(session)"
      @session-pin="session => canEdit && perform(() => assistant.updateSession(session.id, { pinned: !session.pinned }))"
      @session-remove="session => canEdit && remove(session)"
      @citation-select="openSource"
      @retrieval-select="openSource"
    >
      <template #sender-actions>
        <YdSelect v-model="scope" :disabled="assistant.loading || assistant.busy || uploading" aria-label="助手范围" :options="scopeOptions" />
        <AiToolPicker v-if="scope === 'GENERAL'" :disabled="assistant.loading || assistant.busy || uploading" />
        <YdSelect v-if="scope === 'AGENT'" v-model="selectedAgent" :disabled="assistant.loading || assistant.busy || uploading" aria-label="Agent 应用" placeholder="选择 Agent 应用" :options="agents.map(item => ({ label: item.name, value: item.code }))" />
        <YdSelect v-if="scope !== 'AGENT' && auth('platform:wiki:view')" v-model="wikiSlug" :disabled="assistant.loading || assistant.busy || uploading" aria-label="知识库上下文" :options="[{ label: scope === 'WIKI' ? '请选择知识库' : '不引用知识库', value: '' }, { label: '全部知识库', value: '__all__' }, ...(catalog?.knowledgeSpaces || []).map(item => ({ label: item.name, value: item.slug }))]" />
      </template>
      <template #composer-before>
        <div class="chat-page__composer-state" aria-live="polite">
          <YdAlert v-if="!auth('platform:chat:use')" title="当前账号没有使用 AI 助手的权限" />
          <YdAlert v-else-if="needsModel" title="还没有可用的聊天模型">
            <template #description>
              <p>请先在「平台能力」的 AI 配置中添加聊天端点与模型，配置完成后重新加载。</p>
              <div class="chat-page__state-actions">
                <YdButton v-if="auth('platform:capability:view')" variant="outline" size="sm" @click="router.push('/platform/capability')">
                  前往配置
                </YdButton>
                <YdButton variant="outline" size="sm" @click="reloadAssistant">
                  重新加载
                </YdButton>
              </div>
            </template>
          </YdAlert>
          <YdAlert v-else-if="scopeIssue" :title="scopeIssue" />
          <p v-else class="chat-page__context-summary">
            <YdIcon :name="uploading ? 'morph:upload' : 'morph:info'" />
            {{ uploading ? uploadProgress : assistant.loading ? '正在加载会话与可用模型…' : contextSummary }}
          </p>
          <p v-if="startsNewSession && !scopeIssue && !assistant.loading" class="chat-page__context-summary">
            下次发送将在所选范围开启新会话，原对话保留。
          </p>
        </div>
      </template>
      <template #header-actions>
        <YdButton v-if="canViewWorkspace" variant="outline" size="sm" @click="explorerOpen = true">
          <YdIcon name="morph:folder-open" /> 我的工作区
        </YdButton>
        <span v-if="assistant.quota" class="chat-page__quota">{{ assistant.quota.unlimited ? `今日已用 ${assistant.quota.usedTokens.toLocaleString()} tokens · 不限额` : `今日可用 ${assistant.quota.remainingTokens.toLocaleString()} tokens` }}</span>
      </template>
    </YdAgentWorkbench>
    <WorkspaceExplorer v-if="canViewWorkspace" v-model="explorerOpen" :can-edit="canEditWorkspace" :attachments="conversationAttachments" @changed="workspaceRefs.load" />
    <YdModal v-model="renameOpen" title="重命名会话" show-cancel-button :confirm-loading="sessionSaving" :confirm-button-disabled="!title.trim() || sessionSaving" :before-close="(action, done) => confirmSessionAction('rename', action, done)">
      <YdAlert v-if="sessionError" class="mb-3" variant="destructive" title="未能保存会话" :description="sessionError" />
      <YdInput v-model="title" aria-label="会话标题" :maxlength="100" />
    </YdModal>
    <YdModal v-model="removeOpen" title="删除会话" show-cancel-button :confirm-loading="sessionSaving" :before-close="(action, done) => confirmSessionAction('remove', action, done)">
      <YdAlert v-if="sessionError" class="mb-3" variant="destructive" title="未能删除会话" :description="sessionError" />
      <p>确定删除“{{ targetSession?.title }}”及其消息记录？</p>
    </YdModal>
  </main>
</template>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100dvh - var(--g-slots-layout-top-height) - var(--g-header-actual-height) - var(--g-topbar-actual-height) - var(--g-main-container-padding-top, 0px) - var(--g-slots-layout-bottom-height) - var(--g-main-container-padding-bottom, 0px));
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  color: var(--color-text-1);
  background: var(--color-bg-1);
}
.chat-page__workbench { min-height: 0; flex: 1; }
.chat-page__quota { color: var(--color-text-3); font-size: 12px; }
.chat-page__composer-state { min-width: 0; padding: 8px 16px 0; }
.chat-page__context-summary { display: flex; align-items: start; gap: 6px; margin: 0; color: var(--color-text-3); font-size: 12px; line-height: 1.7; overflow-wrap: anywhere; }
.chat-page__context-summary > :first-child { flex: none; margin-top: 3px; }
.chat-page__state-actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; }
@media (width <= 768px) { .chat-page { width: 100%; height: 100%; } }
</style>
