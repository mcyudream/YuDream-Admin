<script setup lang="ts">
import type { YdChatAttachment, YdChatCitation, YdChatMessage, YdChatRetrievalHit, YdSuggestionItem } from '@yudream/components'
import { provideYdChatConfig, useYdChatStream, YdChatMessageList, YdChatSender, YdChatSessionList, YdWelcome } from '@yudream/components'
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { createChatSession, deleteChatSession, fetchMyChatQuota, listChatMessages, listChatSessions, updateChatSession, uploadChatAttachment, type ChatMessage, type ChatQuota, type ChatScopeType, type ChatSession, chatStreamEndpoint } from '@/api/modules/platform-chat'
import apiAgent, { type AgentApplication, type AgentCatalog } from '@/api/modules/platform-agent'
import { resolveApiFileUrl, rewriteApiFileUrls } from '@/utils/api-file-url'

provideYdChatConfig({ placeholder: '尽管问，或让我帮你做点什么…', thinkingText: '余梦正在思考…', reasoningTitle: '深度思考', disclaimer: '内容由余梦 AI 生成，请核对重要信息' })

const activeId = ref('')
const sessions = ref<ChatSession[]>([])
const sessionsLoading = ref(false)
const quota = ref<ChatQuota | null>(null)
const agentCode = ref('')
const baseScopeType = ref<Extract<ChatScopeType, 'GENERAL' | 'WIKI'>>('GENERAL')
const wikiContextSlug = ref<string | null>(null)
const providerCode = ref('')
const modelCode = ref('')
const attachments = ref<YdChatAttachment[]>([])
const agents = ref<AgentApplication[]>([])
const catalog = ref<AgentCatalog | null>(null)
const attachUploading = ref(false)
const wikiMenuOpen = ref(false)
const sessionDrawerVisible = ref(false)
const router = useRouter()
const route = useRoute()

const { messages, streaming, send, stop } = useYdChatStream({
  endpoint: () => chatStreamEndpoint(activeId.value),
  protocol: 'agui',
  transport: 'sse',
  historyLimit: 10,
  buildBody: (question, history, currentAttachments) => ({
    sessionId: activeId.value,
    scopeType: agentCode.value ? 'AGENT' : baseScopeType.value,
    agentCode: agentCode.value || undefined,
    spaceSlug: wikiContextSlug.value && wikiContextSlug.value !== '__all__' ? wikiContextSlug.value : undefined,
    contextRefs: wikiContextSlug.value ? [{ type: 'wiki', target: wikiContextSlug.value === '__all__' ? 'all' : wikiContextSlug.value, label: wikiContextLabel.value }] : [],
    providerCode: providerCode.value || undefined,
    modelCode: modelCode.value || undefined,
    question,
    history,
    attachments: currentAttachments,
  }),
})

const chatModels = computed(() => (catalog.value?.models ?? []).filter(item => item.configured && item.kind === 'chat'))
const wikiSpaces = computed(() => catalog.value?.knowledgeSpaces ?? [])
const activeAgent = computed(() => agents.value.find(item => item.code === agentCode.value))
const activeModel = computed(() => chatModels.value.find(item => item.providerCode === providerCode.value && item.modelCode === modelCode.value))
const modelOptions = computed(() => chatModels.value.map(item => ({
  fullLabel: `${item.providerName} · ${item.modelName}`,
  label: item.modelName,
  value: `${item.providerCode}::${item.modelCode}`,
})))
const wikiContextLabel = computed(() => wikiContextSlug.value === '__all__' ? '全部知识库' : wikiSpaces.value.find(item => item.slug === wikiContextSlug.value)?.name ?? '')
const welcomeTitle = computed(() => activeAgent.value?.name || '今天想让我帮你做什么？')
const isEmpty = computed(() => messages.value.length === 0)
const modelValue = computed({
  get: () => providerCode.value && modelCode.value ? `${providerCode.value}::${modelCode.value}` : '',
  set: (value: string) => {
    const [provider, model] = value.split('::')
    providerCode.value = provider ?? ''
    modelCode.value = model ?? ''
  },
})
const wikiMentionItems = computed<YdSuggestionItem[]>(() => [
  { key: '__all__', label: 'wiki 全部知识库', description: '检索所有可用知识库', icon: 'i-ri:global-line', value: 'wiki 全部知识库' },
  ...wikiSpaces.value.map(space => ({ key: space.slug, label: `wiki ${space.name}`, description: space.slug, icon: 'i-ri:book-shelf-line', value: `wiki ${space.name}` })),
])
const suggestions = ['帮我写一份项目周报', '梳理一下知识库内容', '分析当前数据并给出建议', '帮我审查这段代码']

function normalizeWikiContext(slug?: string | null): string | null {
  if (!slug) return null
  return slug === 'all' ? '__all__' : slug
}

function selectWikiContext(slug: string | null) {
  wikiContextSlug.value = normalizeWikiContext(slug)
  wikiMenuOpen.value = false
}

function onMentionSelect(item: YdSuggestionItem) {
  const key = String(item.key)
  selectWikiContext(key === '__all__' ? '__all__' : key)
}

async function loadSessions(selectFirst = true) {
  sessionsLoading.value = true
  try {
    sessions.value = (await listChatSessions()).data
    if (selectFirst && !activeId.value && sessions.value.length) await selectSession(sessions.value[0].id)
  } finally {
    sessionsLoading.value = false
  }
}

async function loadQuota() {
  try { quota.value = (await fetchMyChatQuota()).data } catch { quota.value = null }
}

async function loadCatalog() {
  try {
    const [catalogRes, agentsRes] = await Promise.all([apiAgent.catalog(), apiAgent.available()])
    catalog.value = catalogRes.data
    agents.value = agentsRes.data
    const fallback = chatModels.value.find(item => item.defaultModel) ?? chatModels.value[0]
    if (fallback && !providerCode.value) {
      providerCode.value = fallback.providerCode
      modelCode.value = fallback.modelCode
    }
  } catch {
    catalog.value = null
    agents.value = []
  }
}

async function selectSession(id: string) {
  if (id === activeId.value) return
  stop()
  sessionDrawerVisible.value = false
  activeId.value = id
  const session = sessions.value.find(item => item.id === id)
  if (session) {
    agentCode.value = session.agentCode ?? ''
    baseScopeType.value = session.scopeType === 'WIKI' ? 'WIKI' : 'GENERAL'
    wikiContextSlug.value = normalizeWikiContext(session.spaceSlug)
    providerCode.value = session.providerCode ?? providerCode.value
    modelCode.value = session.modelCode ?? modelCode.value
  }
  messages.value = (await listChatMessages(id)).data.map(toYdMessage)
}

function isActiveProcessStatus(status?: string): boolean {
  const normalized = status?.trim().toLowerCase().replaceAll('_', '-')
  return !normalized || ['running', 'pending', 'started', 'executing', 'processing', 'in-progress'].includes(normalized)
}

function restoredProcessStatus(status: ChatMessage['status'], processStatus?: string): 'cancelled' | 'complete' | 'error' | 'executing' | undefined {
  const normalized = processStatus?.trim().toLowerCase().replaceAll('_', '-')
  if (!isActiveProcessStatus(normalized)) {
    if (normalized === 'complete' || normalized === 'completed') return 'complete'
    if (normalized === 'error' || normalized === 'failed') return 'error'
    if (normalized === 'cancelled' || normalized === 'canceled' || normalized === 'skipped') return 'cancelled'
    return undefined
  }
  if (status === 'FAILED') return 'error'
  if (status === 'CANCELLED') return 'cancelled'
  return status === 'COMPLETED' ? 'complete' : 'executing'
}

function toYdMessage(message: ChatMessage): YdChatMessage {
  return {
    id: message.id,
    role: message.role === 'ASSISTANT' ? 'assistant' : 'user',
    content: message.content,
    reasoning: message.reasoning,
    citations: message.citations?.map(citation => ({ ...citation })) ?? [],
    tools: message.tools?.map(tool => ({ ...tool, status: restoredProcessStatus(message.status, tool.status) })) ?? [],
    activities: (message.activities ?? [])
      .filter(activity => activity.activityType)
      .map(activity => ({ ...activity, activityType: activity.activityType as string, status: restoredProcessStatus(message.status, activity.status) })),
    attachments: message.attachments ?? [],
    error: message.status === 'FAILED',
    pending: false,
  }
}

async function createSession() {
  const created = (await createChatSession({
    title: '新的对话',
    scopeType: agentCode.value ? 'AGENT' : baseScopeType.value,
    agentCode: agentCode.value || undefined,
    spaceSlug: wikiContextSlug.value || undefined,
    providerCode: providerCode.value || undefined,
    modelCode: modelCode.value || undefined,
  })).data
  sessions.value.unshift(created)
  await selectSession(created.id)
}

async function renameSession(session: { id: string, title: string }) {
  const title = window.prompt('请输入会话标题', session.title)
  if (!title) return
  const updated = (await updateChatSession(session.id, { title })).data
  const target = sessions.value.find(item => item.id === session.id)
  if (target) Object.assign(target, updated)
}

async function pinSession(session: { id: string, pinned?: boolean }) {
  const updated = (await updateChatSession(session.id, { pinned: !session.pinned })).data
  const target = sessions.value.find(item => item.id === session.id)
  if (target) Object.assign(target, updated)
}

async function removeSession(session: { id: string }) {
  if (!window.confirm('确定删除该会话？')) return
  await deleteChatSession(session.id)
  sessions.value = sessions.value.filter(item => item.id !== session.id)
  if (activeId.value === session.id) {
    activeId.value = ''
    messages.value = []
  }
}

async function onAttach(files: File[]) {
  attachUploading.value = true
  try {
    for (const file of files) attachments.value.push(await uploadChatAttachment(file))
  } finally {
    attachUploading.value = false
  }
}

function onRemoveAttachment(attachment: YdChatAttachment) { attachments.value = attachments.value.filter(item => item !== attachment) }

async function onSend(text: string, currentAttachments: YdChatAttachment[]) {
  await send(text, currentAttachments)
  attachments.value = []
  await Promise.all([loadQuota(), loadSessions()])
}

function onCopy(message: YdChatMessage) { if (message.content) navigator.clipboard?.writeText(message.content) }
function onRegenerate(message: YdChatMessage) {
  const user = messages.value[messages.value.indexOf(message) - 1]
  if (user?.role === 'user') void send(user.content, user.attachments)
}

function openSource(source: YdChatCitation | YdChatRetrievalHit) {
  if (source.sourceUrl) {
    if (source.sourceUrl.startsWith('/')) void router.push(source.sourceUrl)
    else window.open(source.sourceUrl, '_blank', 'noopener')
    return
  }
  if (source.spaceSlug && source.path) void router.push(`/wiki/${encodeURIComponent(source.spaceSlug)}/${encodeURI(source.path)}`)
}

onMounted(async () => {
  const queryScope = route.query.scopeType
  const hasRouteContext = queryScope === 'WIKI' || queryScope === 'AGENT'
  baseScopeType.value = queryScope === 'WIKI' ? 'WIKI' : 'GENERAL'
  if (typeof route.query.spaceSlug === 'string' && route.query.spaceSlug) wikiContextSlug.value = normalizeWikiContext(route.query.spaceSlug)
  if (typeof route.query.agentCode === 'string' && route.query.agentCode) agentCode.value = route.query.agentCode
  await Promise.all([loadSessions(!hasRouteContext), loadQuota(), loadCatalog()])
  if (hasRouteContext) await createSession()
})
</script>

<template>
  <main class="chat-page">
    <aside class="chat-page__sessions">
      <YdChatSessionList :sessions="sessions" :active-id="activeId" :loading="sessionsLoading" @select="selectSession" @create="createSession" @rename="renameSession" @pin="pinSession" @remove="removeSession" />
    </aside>
    <a-drawer v-model:visible="sessionDrawerVisible" title="会话记录" :width="320" popup-container="body" class="chat-page__session-drawer">
      <YdChatSessionList :sessions="sessions" :active-id="activeId" :loading="sessionsLoading" @select="selectSession" @create="createSession" @rename="renameSession" @pin="pinSession" @remove="removeSession" />
    </a-drawer>
    <section class="chat-page__main">
      <header class="chat-page__top">
        <FaTooltip text="会话记录">
          <button type="button" class="chat-page__history" aria-label="会话记录" @click="sessionDrawerVisible = true"><FaIcon name="i-ri:menu-line" /></button>
        </FaTooltip>
        <div v-if="quota" class="chat-page__quota" :title="`今日已用 ${quota.usedTokens.toLocaleString()} tokens`"><span class="chat-page__quota-ring" />剩余额度 {{ quota.remainingTokens.toLocaleString() }}</div>
      </header>
      <div v-if="isEmpty" class="chat-page__empty"><YdWelcome :title="welcomeTitle" :suggestions="suggestions" @select="send" /></div>
      <YdChatMessageList v-else :messages="messages" :transform-content="rewriteApiFileUrls" :image-url-resolver="resolveApiFileUrl" @copy-message="onCopy" @regenerate-message="onRegenerate" @citation-click="openSource" @retrieval-click="openSource" />
      <YdChatSender :constrained="true" :loading="streaming || attachUploading" :suggestions="[]" :attachments="attachments" :mention-items="wikiMentionItems" @send="onSend" @stop="stop" @attach="onAttach" @remove-attachment="onRemoveAttachment" @mention-select="onMentionSelect">
        <template #actions>
          <div class="chat-page__context-controls">
            <div class="chat-context">
              <FaTooltip text="引用知识库。也可输入 @wiki 选择并自动填充"><button type="button" class="chat-control chat-control--icon" :class="{ 'is-active': wikiContextSlug }" aria-label="知识库上下文" @click="wikiMenuOpen = !wikiMenuOpen"><FaIcon name="i-ri:book-shelf-line" /><span>{{ wikiContextLabel || '知识库' }}</span></button></FaTooltip>
              <div v-if="wikiMenuOpen" class="chat-context__menu">
                <button type="button" :class="{ active: wikiContextSlug === '__all__' }" @click="selectWikiContext('__all__')"><FaIcon name="i-ri:global-line" />检索全部知识库</button>
                <button v-for="space in wikiSpaces" :key="space.slug" type="button" :class="{ active: wikiContextSlug === space.slug }" @click="selectWikiContext(space.slug)"><FaIcon name="i-ri:book-2-line" />{{ space.name }}</button>
                <button v-if="wikiContextSlug" type="button" class="chat-context__clear" @click="selectWikiContext(null)"><FaIcon name="i-ri:close-line" />关闭知识库</button>
              </div>
            </div>
            <div class="chat-control chat-control--select">
              <FaIcon name="i-ri:robot-2-line" />
              <a-select v-model="agentCode" allow-clear size="small" :bordered="false" placeholder="Agent" :options="agents.map(item => ({ label: item.name, value: item.code }))" />
            </div>
            <div class="chat-control chat-control--select chat-control--model" :title="activeModel ? `${activeModel.providerName} · ${activeModel.modelName}` : '选择模型'">
              <FaIcon name="i-ri:cpu-line" />
              <a-select v-model="modelValue" size="small" :bordered="false" placeholder="选择模型" :options="modelOptions">
                <template #option="{ data }"><span :title="data.fullLabel">{{ data.fullLabel }}</span></template>
              </a-select>
            </div>
          </div>
        </template>
      </YdChatSender>
    </section>
  </main>
</template>

<style scoped>
.chat-page {
  display: flex;
  height: calc(100dvh - var(--g-slots-layout-top-height) - var(--g-header-actual-height) - var(--g-topbar-actual-height) - var(--g-main-container-padding-top, 0px) - var(--g-slots-layout-bottom-height) - var(--g-main-container-padding-bottom, 0px));
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
  color: var(--color-text-1);
}
.chat-page__sessions { display: flex; flex-shrink: 0; min-height: 0; border-right: 1px solid var(--color-border-2); }
.chat-page__main { display: grid; min-width: 0; min-height: 0; flex: 1; grid-template-rows: auto minmax(0, 1fr) auto; overflow: hidden; }
.chat-page__top { display: flex; align-items: center; justify-content: space-between; min-height: 46px; padding: 8px 20px; border-bottom: 1px solid var(--color-border-2); }
.chat-page__history { display: none; width: 30px; height: 30px; padding: 0; border: 0; border-radius: 6px; background: transparent; color: var(--color-text-2); cursor: pointer; place-items: center; font-size: 18px; }
.chat-page__history:hover { background: var(--color-fill-1); color: var(--color-text-1); }
.chat-page__quota { display: inline-flex; align-items: center; gap: 7px; padding: 5px 10px; border: 1px solid var(--color-border-2); border-radius: 999px; background: var(--color-bg-2); color: var(--color-text-3); font-size: 12px; }
.chat-page__quota-ring { width: 8px; height: 8px; border-radius: 50%; background: var(--color-text-2); }
.chat-page__empty { display: flex; min-height: 0; align-items: center; justify-content: center; overflow-y: auto; }
.chat-page__empty :deep(.yd-welcome) { width: min(100%, 720px); padding: clamp(24px, 5vh, 56px) 24px 24px; }
.chat-page__context-controls { display: inline-flex; min-width: 0; align-items: center; gap: 2px; padding: 2px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-fill-1); }
.chat-context { position: relative; flex-shrink: 0; }
.chat-control { display: inline-flex; min-width: 0; height: 28px; align-items: center; border-radius: 6px; color: var(--color-text-3); font-size: 12px; }
.chat-control--select { max-width: 156px; gap: 4px; padding-left: 7px; }
.chat-control--select :deep(.arco-select) { min-width: 0; flex: 1; }
.chat-control--select :deep(.arco-select-view-single) { height: 26px; min-width: 0; padding: 0 5px; border: 0; background: transparent; color: var(--color-text-2); font-size: 12px; }
.chat-control--select :deep(.arco-select-view-value) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-control--select:hover, .chat-control--select:focus-within { background: var(--color-bg-1); color: var(--color-text-1); }
.chat-control--model { width: 260px; max-width: 260px; }
.chat-control--icon { gap: 5px; max-width: 160px; padding: 0 8px; border: 0; background: transparent; cursor: pointer; font: inherit; text-overflow: ellipsis; white-space: nowrap; }
.chat-control--icon:hover, .chat-control--icon.is-active { background: var(--color-bg-1); color: var(--color-text-1); }
.chat-context__menu { position: absolute; z-index: 10; bottom: calc(100% + 8px); left: 0; display: grid; width: 250px; max-height: 260px; overflow-y: auto; padding: 5px; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); box-shadow: 0 10px 28px rgb(0 0 0 / 12%); }
.chat-context__menu button { display: flex; align-items: center; gap: 8px; min-width: 0; padding: 8px; border: 0; border-radius: 5px; background: transparent; color: var(--color-text-2); cursor: pointer; font: inherit; font-size: 13px; text-align: left; }
.chat-context__menu button:hover, .chat-context__menu button.active { background: var(--color-fill-1); color: var(--color-text-1); }
.chat-context__menu .chat-context__clear { margin-top: 4px; border-top: 1px solid var(--color-border-2); color: var(--color-text-3); }
@media (max-width: 760px) {
  /* 移动端：100dvh 链式计算会受地址栏/版权栏/固定区影响而超出可视区，改为由已 flex 撑满的父容器决定高度 */
  .chat-page { width: 100%; max-width: 100%; height: 100%; }
  .chat-page__sessions { display: none; }
  .chat-page__history { display: grid; }
  .chat-page__top { min-height: 42px; padding: 6px 12px; }
  .chat-page__quota { font-size: 11px; }
  .chat-page__empty :deep(.yd-welcome) { padding-inline: 12px; }
  .chat-page__session-drawer :deep(.arco-drawer-body) { padding: 0; }
  .chat-page__session-drawer :deep(.yd-session-list) { width: 100%; min-width: 0; min-height: 100%; }
}
@media (max-width: 560px) {
  .chat-page__context-controls { order: 2; width: 100%; justify-content: space-between; }
  .chat-control--select { max-width: calc(50% - 18px); }
  .chat-control--model { width: auto; max-width: calc(50% - 18px); }
  .chat-control--icon { max-width: 42px; justify-content: center; padding: 0; }
  .chat-control--icon span { display: none; }
  .chat-context__menu { bottom: calc(100% + 8px); width: min(280px, calc(100vw - 24px)); }
}
</style>
