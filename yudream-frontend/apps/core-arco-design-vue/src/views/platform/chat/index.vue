<script setup lang="ts">
import type { YdAgentSession, YdAgentSendRequest, YdAgentWorkspace, YdChatAttachment, YdChatCitation, YdChatMessage, YdChatRetrievalHit } from '@yudream/components'
import { YdAgentWorkbench } from '@yudream/components'
import type { AgentApplication, AgentCatalog } from '@/api/modules/platform-agent'
import type { ChatScopeType } from '@/api/modules/platform-chat'
import apiAgent from '@/api/modules/platform-agent'
import { uploadChatAttachment } from '@/api/modules/platform-chat'
import AiToolPicker from '@/components/AiToolPicker/index.vue'
import WorkspaceExplorer from '@/components/WorkspaceExplorer/index.vue'
import { useWorkspaceReferences } from '@/composables/use-workspace-references'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'

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
const suggestions = ['帮我写一份项目周报', '梳理一下知识库内容', '分析当前数据并给出建议', '帮我审查这段代码']

async function perform(action: () => Promise<unknown>) {
  try { await action() }
  catch (error) { assistant.setError(error) }
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
    spaceSlug: wikiSlug.value || undefined,
    providerCode: model?.providerCode,
    modelCode: model?.modelCode,
  }
}

async function send(request: YdAgentSendRequest) {
  if (uploading.value || !auth('platform:chat:use')) return
  const text = await workspaceRefs.compose(request.text.trim() || request.text)
  await assistant.send({ ...request, text }, undefined, {
    ...sessionData(),
    contextRefs: wikiSlug.value ? [{ type: 'wiki', target: wikiSlug.value === '__all__' ? 'all' : wikiSlug.value }] : [],
  })
  if (!assistant.error) attachments.value = []
}

function retry(message: YdChatMessage) {
  const text = assistant.retryText(message)
  if (text) {
    void perform(() => send({ text, attachments: [], mode: mode.value, skillCodes: [], contextPaths: [] }))
  }
}

async function attach(files: File[]) {
  uploading.value = true
  try {
    const session = assistant.sessionId
    for (const file of files) {
      const attachment = await uploadChatAttachment(file)
      if (session !== assistant.sessionId) return
      attachments.value.push(attachment)
    }
  }
  catch (error) { assistant.setError(error) }
  finally { uploading.value = false }
}

function rename(session: YdAgentSession) {
  targetSession.value = session
  title.value = session.title
  renameOpen.value = true
}
function remove(session: YdAgentSession) {
  targetSession.value = session
  removeOpen.value = true
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
  if (!account.isLogin || !account.userId) return
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
      <YdButton v-if="assistant.activeRun" variant="outline" size="sm" @click="perform(assistant.reconnect)">恢复后端进度</YdButton>
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
      :disabled="assistant.loading || uploading || !auth('platform:chat:use')"
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
        <YdSelect v-model="scope" :disabled="assistant.busy" aria-label="助手范围" :options="scopeOptions" />
        <AiToolPicker v-if="scope === 'GENERAL'" :disabled="assistant.busy" />
        <YdSelect v-if="scope === 'AGENT'" v-model="selectedAgent" :disabled="assistant.busy" aria-label="Agent 应用" :options="agents.map(item => ({ label: item.name, value: item.code }))" />
        <YdSelect v-if="auth('platform:wiki:view')" v-model="wikiSlug" :disabled="assistant.busy" aria-label="知识库上下文" :options="[{ label: '不引用知识库', value: '' }, { label: '全部知识库', value: '__all__' }, ...(catalog?.knowledgeSpaces || []).map(item => ({ label: item.name, value: item.slug }))]" />
      </template>
      <template #header-actions>
        <YdButton v-if="canViewWorkspace" variant="outline" size="sm" @click="explorerOpen = true">
          <YdIcon name="morph:folder-open" /> 我的工作区
        </YdButton>
        <span v-if="assistant.quota" class="chat-page__quota">{{ assistant.quota.unlimited ? `今日已用 ${assistant.quota.usedTokens.toLocaleString()} tokens · 不限额` : `今日可用 ${assistant.quota.remainingTokens.toLocaleString()} tokens` }}</span>
      </template>
    </YdAgentWorkbench>
    <WorkspaceExplorer v-if="canViewWorkspace" v-model="explorerOpen" :can-edit="canEditWorkspace" :attachments="conversationAttachments" @changed="workspaceRefs.load" />
    <YdModal v-model="renameOpen" title="重命名会话" show-cancel-button :confirm-button-disabled="!title.trim()" @confirm="targetSession && perform(() => assistant.updateSession(targetSession!.id, { title: title.trim() }))">
      <YdInput v-model="title" aria-label="会话标题" :maxlength="100" />
    </YdModal>
    <YdModal v-model="removeOpen" title="删除会话" show-cancel-button @confirm="targetSession && perform(() => assistant.removeSession(targetSession!.id))">
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
@media (width <= 768px) { .chat-page { width: 100%; height: 100%; } }
</style>
