<script setup lang="ts">
import type { TableColumn } from '@yudream/components'
import type { SatoriChatMember, SatoriChatMessage, SatoriConnection, SatoriConnectionPayload, SatoriConversation, SatoriOperationLog } from '@/api/modules/platform-satori'
import apiSatori from '@/api/modules/platform-satori'

type MessageSegment = { type: 'text' | 'image', value: string }
type ChatAttachment = { file: File, previewUrl?: string, name: string, contentType?: string, image: boolean }

const toast = useFaToast()
const modal = useFaModal()
const loading = ref(false)
const actionKey = ref('')
const rows = ref<SatoriConnection[]>([])
const pagination = reactive({ page: 1, size: 10, total: 0 })
const keyword = ref('')
const composerMode = ref<'TEXT' | 'MARKDOWN' | 'HTML'>('MARKDOWN')
const previewUrl = ref('')
const formVisible = ref(false)
const editing = ref<SatoriConnection | null>(null)
const logVisible = ref(false)
const logLoading = ref(false)
const logConnection = ref<SatoriConnection | null>(null)
const logs = ref<SatoriOperationLog[]>([])
const pendingLogs = ref<SatoriOperationLog[]>([])
const logLevel = ref<'ALL' | SatoriOperationLog['level']>('ALL')
const logSearch = ref('')
const logPaused = ref(false)
const liveStatus = ref<'CONNECTING' | 'LIVE' | 'OFFLINE'>('OFFLINE')
const conversationVisible = ref(false)
const conversationLoading = ref(false)
const messageLoading = ref(false)
const messageSending = ref(false)
const chatConnection = ref<SatoriConnection | null>(null)
const conversations = ref<SatoriConversation[]>([])
const selectedConversation = ref<SatoriConversation | null>(null)
const chatMessages = ref<SatoriChatMessage[]>([])
const chatMembers = ref<SatoriChatMember[]>([])
const messageListRef = ref<HTMLElement | null>(null)
const quotedMessage = ref<SatoriChatMessage | null>(null)
const attachmentInput = ref<HTMLInputElement | null>(null)
const attachmentUploading = ref(false)
const pendingAttachments = ref<ChatAttachment[]>([])
const mentionVisible = ref(false)
const mentionSearch = ref('')
const chatText = ref('')
let logAbortController: AbortController | null = null
let chatAbortController: AbortController | null = null
const form = reactive<SatoriConnectionPayload>({ name: '', baseUrl: 'http://localhost:5500', platform: '', userId: '', token: '' })
const composer = reactive({ content: '# 消息预览\n\n支持 **Markdown** 与 HTML 渲染。', width: 720, transparent: false })

const columns: TableColumn<SatoriConnection>[] = [
  { accessorKey: 'name', header: '连接名称', width: 190 },
  { accessorKey: 'baseUrl', header: 'Satori 地址', width: 300 },
  { accessorKey: 'platform', header: '平台', width: 120 },
  { accessorKey: 'userId', header: '机器人 ID', width: 160 },
  { id: 'credential', header: '凭证', width: 100 },
  { id: 'status', header: '状态', width: 100 },
  { id: 'updated', header: '更新时间', width: 180 },
  { id: 'actions', header: '操作', width: 330, fixed: 'right' as const },
]

onMounted(load)
onBeforeUnmount(() => {
  closeLogStream()
  closeChatStream()
})

watch(conversationVisible, (visible) => {
  if (!visible) closeChatStream()
})

const filteredLogs = computed(() => logs.value.filter((log) => {
  if (logLevel.value !== 'ALL' && log.level !== logLevel.value) return false
  const needle = logSearch.value.trim().toLowerCase()
  return !needle || `${log.category} ${log.action} ${log.detail || ''}`.toLowerCase().includes(needle)
}))

const filteredMembers = computed(() => {
  const needle = mentionSearch.value.trim().toLowerCase()
  return !needle ? chatMembers.value : chatMembers.value.filter(member => `${member.name || ''} ${member.userId}`.toLowerCase().includes(needle))
})

const groupConversations = computed(() => conversations.value.filter(conversation => conversation.type !== 'FRIEND'))
const friendConversations = computed(() => conversations.value.filter(conversation => conversation.type === 'FRIEND'))

async function load() {
  loading.value = true
  try {
    const res = await apiSatori.pageConnections({ page: pagination.page, size: pagination.size, keyword: keyword.value || undefined })
    rows.value = res.data.records
    pagination.total = res.data.total
  }
  finally { loading.value = false }
}

function openCreate() {
  editing.value = null
  Object.assign(form, { name: '', baseUrl: 'http://localhost:5500', platform: '', userId: '', token: '' })
  formVisible.value = true
}

function openEdit(row: SatoriConnection) {
  editing.value = row
  Object.assign(form, { name: row.name, baseUrl: row.baseUrl, platform: row.platform, userId: row.userId, token: '' })
  formVisible.value = true
}

async function save() {
  if (editing.value)
    await apiSatori.updateConnection(editing.value.id, form)
  else
    await apiSatori.createConnection(form)
  formVisible.value = false
  toast.success('连接已保存')
  await load()
}

async function toggle(row: SatoriConnection) {
  actionKey.value = `${row.id}:toggle`
  try {
    if (row.enabled) await apiSatori.disableConnection(row.id)
    else await apiSatori.enableConnection(row.id)
    await load()
  }
  finally { actionKey.value = '' }
}

async function test(row: SatoriConnection) {
  actionKey.value = `${row.id}:test`
  try {
    const result = await apiSatori.testConnection(row.id)
    toast.success('连接可用', { description: `${result.data.platform}:${result.data.userId} / ${result.data.status || result.data.adapter || 'ready'}` })
  }
  finally { actionKey.value = '' }
}

async function openConversation(row: SatoriConnection) {
  closeChatStream()
  chatConnection.value = row
  conversationVisible.value = true
  selectedConversation.value = null
  conversations.value = []
  chatMessages.value = []
  chatMembers.value = []
  chatText.value = ''
  clearPendingAttachments()
  pendingAttachments.value = []
  quotedMessage.value = null
  connectChatStream(row.id)
  await loadConversations()
  if (conversations.value.length) await selectConversation(conversations.value[0])
}

async function loadConversations() {
  if (!chatConnection.value) return
  conversationLoading.value = true
  try {
    const result = await apiSatori.conversations(chatConnection.value.id)
    conversations.value = result.data.records
  }
  finally { conversationLoading.value = false }
}

async function selectConversation(conversation: SatoriConversation) {
  if (!chatConnection.value) return
  if (!conversation.channelId && conversation.targetUserId) {
    const opened = await apiSatori.openDirectConversation(chatConnection.value.id, conversation.targetUserId)
    Object.assign(conversation, opened.data, { name: conversation.name, avatar: conversation.avatar })
  }
  if (!conversation.channelId) return
  selectedConversation.value = conversation
  mentionVisible.value = false
  messageLoading.value = true
  try {
    const [messages, members] = await Promise.all([
      apiSatori.conversationMessages(chatConnection.value.id, conversation.channelId, { limit: 50 }),
      apiSatori.conversationMembers(chatConnection.value.id, { guildId: conversation.guildId }),
    ])
    chatMessages.value = messages.data.records
    chatMembers.value = members.data
    await nextTick()
    scrollMessageBottom()
  }
  finally { messageLoading.value = false }
}

function insertMention(member: SatoriChatMember) {
  chatText.value += `@${member.name || member.userId} `
  mentionVisible.value = false
  mentionSearch.value = ''
}

async function sendChatMessage() {
  if (messageSending.value || !chatConnection.value || !selectedConversation.value || (!chatText.value.trim() && !pendingAttachments.value.length)) return
  messageSending.value = true
  try {
    const quoted = quotedMessage.value
    if (pendingAttachments.value.length) {
      const data = new FormData()
      data.append('platform', chatConnection.value.platform)
      data.append('userId', chatConnection.value.userId)
      data.append('channelId', selectedConversation.value.channelId || '')
      data.append('content', chatText.value.trim())
      pendingAttachments.value.forEach(attachment => data.append('files', attachment.file))
      await apiSatori.sendMedia(chatConnection.value.id, data)
    }
    else {
      await apiSatori.sendMessage(chatConnection.value.id, {
        platform: chatConnection.value.platform,
        userId: chatConnection.value.userId,
        channelId: selectedConversation.value.channelId || '',
        type: quoted ? 'SATORI' : 'TEXT',
        content: quoted ? `<quote id="${escapeSatoriAttribute(quoted.id)}"/>${escapeSatoriText(chatText.value.trim())}` : chatText.value.trim(),
      })
    }
    chatText.value = ''
    clearPendingAttachments()
    pendingAttachments.value = []
    quotedMessage.value = null
    await selectConversation(selectedConversation.value)
  }
  finally { messageSending.value = false }
}

function handleComposerKeydown(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
    event.preventDefault()
    event.stopPropagation()
    void sendChatMessage()
  }
}

async function sendMentionAll() {
  if (!chatConnection.value || !selectedConversation.value || selectedConversation.value.type === 'FRIEND' || !chatText.value.trim()) return
  messageSending.value = true
  try {
    await apiSatori.sendMentionAll(chatConnection.value.id, {
      platform: chatConnection.value.platform,
      userId: chatConnection.value.userId,
      channelId: selectedConversation.value.channelId || '',
      content: chatText.value.trim(),
    })
    chatText.value = ''
    quotedMessage.value = null
    await selectConversation(selectedConversation.value)
  }
  finally { messageSending.value = false }
}

function quoteMessage(message: SatoriChatMessage) {
  quotedMessage.value = message
}

function pickAttachment() {
  attachmentInput.value?.click()
}

async function uploadAttachments(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!files.length) return
  attachmentUploading.value = true
  try {
    for (const file of files) {
      pendingAttachments.value.push({
        file,
        previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
        name: file.name,
        contentType: file.type || undefined,
        image: file.type.startsWith('image/'),
      })
    }
  }
  catch (error) {
    toast.error('附件上传失败', { description: error instanceof Error ? error.message : '网络异常' })
  }
  finally { attachmentUploading.value = false }
}

function removeAttachment(index: number) {
  const [removed] = pendingAttachments.value.splice(index, 1)
  if (removed?.previewUrl) URL.revokeObjectURL(removed.previewUrl)
}

function clearPendingAttachments() {
  pendingAttachments.value.forEach(attachment => {
    if (attachment.previewUrl) URL.revokeObjectURL(attachment.previewUrl)
  })
}

function connectChatStream(id: string) {
  closeChatStream()
  const controller = new AbortController()
  chatAbortController = controller
  void consumeChatStream(id, controller)
}

function closeChatStream() {
  chatAbortController?.abort()
  chatAbortController = null
}

async function consumeChatStream(id: string, controller: AbortController) {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(httpEndpoint(apiSatori.streamConversationMessages(id)), {
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: token } : {}),
      },
      signal: controller.signal,
    })
    if (!response.ok || !response.body) throw new Error(`实时消息连接失败（HTTP ${response.status}）`)
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (!controller.signal.aborted) {
      const chunk = await reader.read()
      if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      frames.forEach(handleChatFrame)
    }
  }
  catch (error) {
    if (!controller.signal.aborted) console.warn('Satori 实时消息连接已关闭', error)
  }
  finally {
    if (chatAbortController === controller) chatAbortController = null
  }
}

function handleChatFrame(frame: string) {
  const event = frame.match(/^event:\s*(.+)$/m)?.[1]?.trim()
  const data = frame.match(/^data:\s*(.+)$/m)?.[1]
  if (event === 'message' && data) appendLiveMessage(JSON.parse(data) as SatoriChatMessage)
}

function appendLiveMessage(message: SatoriChatMessage) {
  if (!selectedConversation.value?.channelId || message.channelId !== selectedConversation.value.channelId) return
  if (message.id && chatMessages.value.some(item => item.id === message.id)) return
  chatMessages.value.push(message)
  void nextTick(scrollMessageBottom)
}

async function openLogs(row: SatoriConnection) {
  closeLogStream()
  logConnection.value = row
  logVisible.value = true
  logPaused.value = false
  pendingLogs.value = []
  logLevel.value = 'ALL'
  logSearch.value = ''
  logLoading.value = true
  try {
    const result = await apiSatori.pageConnectionLogs(row.id, { page: 1, size: 100 })
    logs.value = [...result.data.records].reverse()
    connectLogStream(row.id)
  }
  finally { logLoading.value = false }
}

function connectLogStream(id: string) {
  closeLogStream()
  liveStatus.value = 'CONNECTING'
  const controller = new AbortController()
  logAbortController = controller
  void consumeLogStream(id, controller)
}

function closeLogStream() {
  logAbortController?.abort()
  logAbortController = null
  liveStatus.value = 'OFFLINE'
}

async function consumeLogStream(id: string, controller: AbortController) {
  try {
    const token = localStorage.getItem('token')
    const response = await fetch(httpEndpoint(apiSatori.streamConnectionLogs(id)), {
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: token } : {}),
      },
      signal: controller.signal,
    })
    if (!response.ok || !response.body) throw new Error(`实时日志连接失败（HTTP ${response.status}）`)
    liveStatus.value = 'LIVE'
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (!controller.signal.aborted) {
      const chunk = await reader.read()
      if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      frames.forEach(handleLogFrame)
    }
  }
  catch (error) {
    if (!controller.signal.aborted) {
      liveStatus.value = 'OFFLINE'
      toast.error('实时日志连接失败', { description: error instanceof Error ? error.message : '网络异常' })
    }
  }
  finally {
    if (logAbortController === controller) logAbortController = null
  }
}

function handleLogFrame(frame: string) {
  const event = frame.match(/^event:\s*(.+)$/m)?.[1]?.trim()
  const data = frame.match(/^data:\s*(.+)$/m)?.[1]
  if (event === 'log' && data) appendLiveLog(JSON.parse(data) as SatoriOperationLog)
}

function appendLiveLog(log: SatoriOperationLog) {
  if (logPaused.value) {
    pendingLogs.value.push(log)
    return
  }
  logs.value.push(log)
  if (logs.value.length > 500) logs.value.splice(0, logs.value.length - 500)
}

function toggleLogPause() {
  logPaused.value = !logPaused.value
  if (!logPaused.value && pendingLogs.value.length) {
    logs.value.push(...pendingLogs.value)
    pendingLogs.value = []
  }
}

function clearLogView() {
  logs.value = []
  pendingLogs.value = []
}

function exportLogs() {
  const text = filteredLogs.value.map(log => `${dateText(log.occurredAt)} [${log.level}] ${log.category}/${log.action} ${log.detail || ''}`).join('\n')
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }))
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `satori-${logConnection.value?.id || 'logs'}-${Date.now()}.log`
  anchor.click()
  URL.revokeObjectURL(url)
}

async function renderPreview() {
  if (previewUrl.value) URL.revokeObjectURL(previewUrl.value)
  const response = await apiSatori.render({
    sourceType: composerMode.value === 'TEXT' ? 'MARKDOWN' : composerMode.value,
    content: composerMode.value === 'TEXT' ? composer.content : composer.content,
    width: composer.width,
    transparent: composer.transparent,
  })
  previewUrl.value = URL.createObjectURL(response.data)
}

function confirmToggle(row: SatoriConnection) {
  modal.confirm({ title: row.enabled ? '停用连接' : '启用连接', content: `确认${row.enabled ? '停用' : '启用'} ${row.name}？`, onConfirm: () => toggle(row) })
}

function dateText(value?: string) { return value ? value.replace('T', ' ').slice(0, 19) : '-' }

function messageTime(value?: number | string) {
  if (value === undefined || value === null || value === '') return ''
  const numeric = Number(value)
  const date = Number.isFinite(numeric)
    ? new Date(numeric < 100000000000 ? numeric * 1000 : numeric)
    : new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-')
}

function avatarUrl(avatar: string | undefined, id: string | undefined, type: 'GROUP' | 'FRIEND' = 'FRIEND') {
  if (avatar && /^https?:\/\//i.test(avatar)) return avatar
  if (!id || !/^\d+$/.test(id)) return ''
  return type === 'GROUP'
    ? `https://p.qlogo.cn/gh/${id}/${id}/100/`
    : `https://q1.qlogo.cn/g?b=qq&nk=${id}&s=100`
}

function messageSegments(content?: string): MessageSegment[] {
  const source = content || ''
  const segments: MessageSegment[] = []
  const image = /<img\b[^>]*\bsrc=(["'])(.*?)\1[^>]*\/?>(?:<\/img>)?/gi
  let cursor = 0
  for (const match of source.matchAll(image)) {
    const index = match.index || 0
    appendTextSegment(segments, source.slice(cursor, index))
    const url = decodeHtml(match[2])
    if (/^https?:\/\//i.test(url)) segments.push({ type: 'image', value: url })
    cursor = index + match[0].length
  }
  appendTextSegment(segments, source.slice(cursor))
  return segments.length ? segments : [{ type: 'text', value: '' }]
}

function appendTextSegment(segments: MessageSegment[], value: string) {
  const text = decodeHtml(value.replace(/<at\b[^>]*name=(["'])(.*?)\1[^>]*\/?>(?:<\/at>)?/gi, '@$2').replace(/<\/?[^>]+>/g, ''))
  if (text) segments.push({ type: 'text', value: text })
}

function decodeHtml(value: string) {
  const textarea = document.createElement('textarea')
  textarea.innerHTML = value
  return textarea.value
}

function escapeSatoriText(value: string) {
  return value.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

function escapeSatoriAttribute(value: string) {
  return escapeSatoriText(value).replace(/"/g, '&quot;').replace(/'/g, '&apos;')
}

function scrollMessageBottom() {
  const element = messageListRef.value
  if (element) element.scrollTop = element.scrollHeight
}

function httpEndpoint(path: string) {
  if (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) return `/proxy/${path.replace(/^\//, '')}`
  const base = import.meta.env.VITE_APP_API_BASEURL || window.location.origin
  return `${base.replace(/\/$/, '')}/${path.replace(/^\//, '')}`
}

</script>

<template>
  <div>
    <FaPageHeader title="Satori 消息平台">
      <FaButton v-auth="'platform:satori:config'" @click="openCreate"><FaIcon name="i-ri:add-line" />新增连接</FaButton>
    </FaPageHeader>
    <FaPageMain>
      <div class="console-grid">
        <section class="workspace">
          <FaTable v-loading="loading" :columns="columns" :data="rows" row-key="id" border stripe table-class="min-w-[1080px]">
            <template #toolbar>
              <FaSearchBar class="w-full"><div class="flex gap-2"><FaInput v-model="keyword" clearable placeholder="名称或地址" @keydown.enter="load" /><FaButton @click="load"><FaIcon name="i-ri:search-line" />筛选</FaButton></div></FaSearchBar>
            </template>
            <template #cell-credential="{ row }"><FaTag :variant="row.original.credentialConfigured ? 'default' : 'secondary'">{{ row.original.credentialConfigured ? '已配置' : '未配置' }}</FaTag></template>
            <template #cell-status="{ row }"><FaTag :variant="row.original.enabled ? 'default' : 'secondary'">{{ row.original.enabled ? '已启用' : '已停用' }}</FaTag></template>
            <template #cell-updated="{ row }">{{ dateText(row.original.updateTime) }}</template>
            <template #cell-actions="{ row }"><div class="actions"><FaButton size="sm" variant="ghost" title="消息会话" :disabled="!row.original.enabled" @click="openConversation(row.original)"><FaIcon name="i-ri:chat-3-line" /></FaButton><FaButton size="sm" variant="ghost" title="实时日志" @click="openLogs(row.original)"><FaIcon name="i-ri:file-list-3-line" /></FaButton><FaButton size="sm" variant="outline" :loading="actionKey === `${row.original.id}:test`" @click="test(row.original)"><FaIcon name="i-ri:plug-line" /></FaButton><FaButton size="sm" variant="ghost" title="编辑连接" @click="openEdit(row.original)"><FaIcon name="i-ri:edit-line" /></FaButton><FaButton size="sm" :loading="actionKey === `${row.original.id}:toggle`" :variant="row.original.enabled ? 'ghost' : 'outline'" :title="row.original.enabled ? '停用连接' : '启用连接'" @click="confirmToggle(row.original)"><FaIcon :name="row.original.enabled ? 'i-ri:pause-line' : 'i-ri:play-line'" /></FaButton></div></template>
          </FaTable>
          <FaPagination v-model:page="pagination.page" v-model:size="pagination.size" :total="pagination.total" class="mt-3" @page-change="load" @size-change="load" />
        </section>
        <section class="composer">
          <div class="composer-head"><h2>消息渲染</h2><div class="segmented"><button v-for="mode in ['TEXT', 'MARKDOWN', 'HTML']" :key="mode" :class="{ active: composerMode === mode }" @click="composerMode = mode as typeof composerMode">{{ mode }}</button></div></div>
          <FaTextarea v-model="composer.content" :rows="12" input-class="font-mono" />
          <div class="render-controls"><a-form-item label="宽度"><a-input-number v-model="composer.width" :min="320" :max="1600" /></a-form-item><a-checkbox v-model="composer.transparent">透明背景</a-checkbox><FaButton v-auth="'platform:render:use'" @click="renderPreview"><FaIcon name="i-ri:image-line" />渲染</FaButton></div>
          <div class="preview"><img v-if="previewUrl" :src="previewUrl" alt="消息渲染预览"><span v-else>暂无预览</span></div>
        </section>
      </div>
    </FaPageMain>
    <FaModal v-model="formVisible" :title="editing ? '编辑 Satori 连接' : '新增 Satori 连接'" show-cancel-button @confirm="save"><a-form :model="form" layout="vertical"><a-form-item label="名称" required><FaInput v-model="form.name" /></a-form-item><a-form-item label="Satori 地址" required><FaInput v-model="form.baseUrl" placeholder="https://satori.example.com" /></a-form-item><a-form-item label="Satori Platform" required><FaInput v-model="form.platform" placeholder="例如 discord、qq" /></a-form-item><a-form-item label="Satori User ID" required><FaInput v-model="form.userId" placeholder="机器人自身账号 ID" /></a-form-item><a-form-item label="令牌" :required="!editing"><FaInput v-model="form.token" type="password" :placeholder="editing ? '留空保持原令牌' : 'Bearer Token'" /></a-form-item></a-form></FaModal>
    <FaModal v-model="conversationVisible" :title="`${chatConnection?.name || 'Satori'} 消息会话`" :show-cancel-button="false" class="sm:max-w-6xl"><div class="chat-workspace"><aside v-loading="conversationLoading" class="chat-sidebar"><div class="chat-sidebar__head"><strong>会话</strong><FaButton size="sm" variant="ghost" title="刷新会话" @click="loadConversations"><FaIcon name="i-ri:refresh-line" /></FaButton></div><div class="chat-section"><span><FaIcon name="i-ri:group-line" />群聊</span><button v-for="conversation in groupConversations" :key="conversation.channelId" class="chat-conversation" :class="{ active: selectedConversation?.channelId === conversation.channelId }" @click="selectConversation(conversation)"><span class="chat-conversation__avatar"><img v-if="avatarUrl(conversation.avatar, conversation.guildId, 'GROUP')" :src="avatarUrl(conversation.avatar, conversation.guildId, 'GROUP')" referrerpolicy="no-referrer"><FaIcon v-else name="i-ri:group-line" /></span><span><strong>{{ conversation.name || conversation.channelId }}</strong><small>{{ conversation.guildId || '群聊频道' }}</small></span></button><div v-if="!conversationLoading && !groupConversations.length" class="chat-section__empty">暂无群聊</div></div><div class="chat-section"><span><FaIcon name="i-ri:contacts-line" />好友</span><button v-for="conversation in friendConversations" :key="conversation.targetUserId" class="chat-conversation" :class="{ active: selectedConversation?.targetUserId === conversation.targetUserId }" @click="selectConversation(conversation)"><span class="chat-conversation__avatar"><img v-if="avatarUrl(conversation.avatar, conversation.targetUserId)" :src="avatarUrl(conversation.avatar, conversation.targetUserId)" referrerpolicy="no-referrer"><FaIcon v-else name="i-ri:user-3-line" /></span><span><strong>{{ conversation.name || conversation.targetUserId }}</strong><small>{{ conversation.targetUserId }}</small></span></button><div v-if="!conversationLoading && !friendConversations.length" class="chat-section__empty">暂无好友</div></div></aside><section v-loading="messageLoading" class="chat-main"><header class="chat-main__head"><div><strong>{{ selectedConversation?.name || '选择会话' }}</strong><small v-if="selectedConversation">{{ selectedConversation.type === 'FRIEND' ? `好友：${selectedConversation.targetUserId || ''}` : `频道 ID：${selectedConversation.channelId || ''}` }}</small></div><FaButton v-if="selectedConversation" size="sm" variant="ghost" title="刷新消息" @click="selectConversation(selectedConversation)"><FaIcon name="i-ri:refresh-line" /></FaButton></header><div ref="messageListRef" class="chat-message-list"><article v-for="message in chatMessages" :key="message.id" class="chat-message" :class="{ 'chat-message--self': message.userId === chatConnection?.userId }" @contextmenu.prevent="quoteMessage(message)"><span class="chat-avatar"><img v-if="avatarUrl(message.userAvatar, message.userId)" :src="avatarUrl(message.userAvatar, message.userId)" referrerpolicy="no-referrer"><template v-else>{{ (message.userName || message.userId || '?').slice(0, 1) }}</template></span><div><div class="chat-message__meta"><strong>{{ message.userName || message.userId || '未知用户' }}</strong><time>{{ messageTime(message.createdAt) }}</time></div><p class="chat-message__content"><template v-for="(segment, index) in messageSegments(message.content)" :key="`${message.id}-${index}`"><img v-if="segment.type === 'image'" :src="segment.value" alt="消息图片" referrerpolicy="no-referrer"><span v-else>{{ segment.value }}</span></template></p></div></article><div v-if="selectedConversation && !messageLoading && !chatMessages.length" class="chat-empty">暂无历史消息</div><div v-if="!selectedConversation" class="chat-empty">从左侧选择一个会话</div></div><footer class="chat-composer"><div v-if="quotedMessage" class="chat-quote"><span><FaIcon name="i-ri:reply-line" />引用 {{ quotedMessage.userName || quotedMessage.userId }}：{{ messageSegments(quotedMessage.content).filter(item => item.type === 'text').map(item => item.value).join('') || '图片消息' }}</span><FaButton size="sm" variant="ghost" title="取消引用" @click="quotedMessage = null"><FaIcon name="i-ri:close-line" /></FaButton></div><div v-if="pendingAttachments.length" class="attachment-list"><article v-for="(attachment, index) in pendingAttachments" :key="`${attachment.name}-${index}`" class="attachment-item"><img v-if="attachment.image && attachment.previewUrl" :src="attachment.previewUrl" :alt="attachment.name"><FaIcon v-else name="i-ri:attachment-2" /><span>{{ attachment.name }}</span><FaButton size="sm" variant="ghost" title="移除附件" @click="removeAttachment(index)"><FaIcon name="i-ri:close-line" /></FaButton></article></div><div class="chat-composer__toolbar"><FaButton size="sm" variant="ghost" :disabled="!selectedConversation" title="提及成员" @click="mentionVisible = !mentionVisible"><FaIcon name="i-ri:at-line" /></FaButton><FaButton size="sm" variant="ghost" :disabled="!selectedConversation" :loading="attachmentUploading" title="上传图片或文件" @click="pickAttachment"><FaIcon name="i-ri:attachment-2" /></FaButton><input ref="attachmentInput" type="file" multiple hidden @change="uploadAttachments"><FaButton v-if="selectedConversation?.type !== 'FRIEND'" size="sm" variant="ghost" :disabled="!selectedConversation || !chatText.trim()" title="@全体成员（需要机器人在群内为管理员）" @click="sendMentionAll"><FaIcon name="i-ri:group-line" /></FaButton><div v-if="mentionVisible" class="mention-picker"><FaInput v-model="mentionSearch" size="small" placeholder="搜索成员" /><button v-for="member in filteredMembers" :key="member.userId" @click="insertMention(member)"><span><img v-if="avatarUrl(member.avatar, member.userId)" :src="avatarUrl(member.avatar, member.userId)" referrerpolicy="no-referrer"><template v-else>{{ (member.name || member.userId).slice(0, 1) }}</template></span>{{ member.name || member.userId }}</button><div v-if="!filteredMembers.length" class="mention-picker__empty">暂无成员</div></div></div><FaTextarea v-model="chatText" :disabled="!selectedConversation" :rows="3" placeholder="输入消息" @keydown.capture="handleComposerKeydown" /><div class="chat-composer__actions"><span>Ctrl + Enter 发送</span><FaButton :disabled="!selectedConversation || (!chatText.trim() && !pendingAttachments.length)" :loading="messageSending" @click="sendChatMessage"><FaIcon name="i-ri:send-plane-2-line" />发送</FaButton></div></footer></section></div></FaModal>
    <FaModal v-model="logVisible" :title="`${logConnection?.name || 'Satori'} 实时日志`" :show-cancel-button="false" class="sm:max-w-6xl" @confirm="closeLogStream(); logVisible = false"><div v-loading="logLoading" class="live-console"><header class="live-console__head"><div class="live-console__identity"><span class="live-console__icon"><FaIcon name="i-ri:terminal-box-line" /></span><div><strong>实时日志</strong><span><i :class="['live-dot', { 'live-dot--ok': liveStatus === 'LIVE' }]" />{{ liveStatus === 'LIVE' ? '已连接' : liveStatus === 'CONNECTING' ? '连接中' : '已断开' }} {{ logs.length }} 条日志</span></div></div><div class="live-console__actions"><div class="level-tabs"><button :class="{ active: logLevel === 'ALL' }" @click="logLevel = 'ALL'">全部</button><button :class="{ active: logLevel === 'INFO' }" @click="logLevel = 'INFO'">Info</button><button :class="{ active: logLevel === 'WARN' }" @click="logLevel = 'WARN'">Warn</button><button :class="{ active: logLevel === 'ERROR' }" @click="logLevel = 'ERROR'">Error</button></div><FaInput v-model="logSearch" placeholder="搜索日志..." class="live-console__search"></FaInput><FaButton size="sm" variant="outline" title="下载当前日志" @click="exportLogs"><FaIcon name="i-ri:download-2-line" /></FaButton><FaButton size="sm" :variant="logPaused ? 'outline' : 'secondary'" :title="logPaused ? '继续日志' : '暂停日志'" @click="toggleLogPause"><FaIcon :name="logPaused ? 'i-ri:play-line' : 'i-ri:pause-line'" />{{ pendingLogs.length ? pendingLogs.length : '' }}</FaButton><FaButton size="sm" variant="ghost" title="清空当前视图" @click="clearLogView"><FaIcon name="i-ri:delete-bin-line" /></FaButton></div></header><div class="live-console__body"><article v-for="log in filteredLogs" :key="log.id" class="live-log"><div class="live-log__meta"><time>{{ dateText(log.occurredAt) }}</time><span :class="`live-level live-level--${log.level.toLowerCase()}`">{{ log.level }}</span></div><div class="live-log__content"><strong>[{{ log.category }}] {{ log.action }}</strong><p>{{ log.detail || '-' }}</p></div></article><div v-if="!logLoading && !filteredLogs.length" class="live-console__empty">暂无匹配日志</div></div></div></FaModal>
  </div>
</template>

<style scoped>
.console-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(330px, 410px); gap: 16px; align-items: start; }
.workspace, .composer { min-width: 0; }
.composer { display: grid; gap: 12px; border: 1px solid var(--color-border-2); padding: 14px; border-radius: 6px; background: var(--color-bg-2); }
.composer-head, .render-controls, .actions { display: flex; gap: 8px; align-items: center; }
.live-console { display: grid; gap: 14px; min-height: 560px; }.live-console__head { display: flex; gap: 16px; align-items: center; justify-content: space-between; padding: 12px 14px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-2); }.live-console__identity, .live-console__identity div, .live-console__actions { display: flex; gap: 10px; align-items: center; }.live-console__identity div { display: grid; gap: 3px; }.live-console__identity span { color: var(--color-text-3); font-size: 12px; }.live-console__icon { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 6px; color: #fff; background: rgb(var(--success-6)); font-size: 20px; }.live-dot { display: inline-block; width: 7px; height: 7px; margin-right: 4px; border-radius: 50%; background: rgb(var(--color-text-4)); }.live-dot--ok { background: rgb(var(--success-6)); box-shadow: 0 0 0 3px rgba(var(--success-6), .15); }.level-tabs { display: inline-flex; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 6px; }.level-tabs button { height: 30px; padding: 0 10px; border: 0; background: var(--color-bg-2); color: var(--color-text-2); font-size: 12px; }.level-tabs button.active { color: #fff; background: rgb(var(--primary-6)); }.live-console__search { width: 150px; }.live-console__body { display: grid; align-content: start; gap: 8px; max-height: 580px; overflow: auto; padding: 10px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-fill-1); }.live-log { display: grid; gap: 5px; padding: 10px 12px; border: 1px solid var(--color-border-2); border-radius: 5px; background: var(--color-bg-2); font-family: "Cascadia Mono", Consolas, monospace; font-size: 12px; }.live-log__meta { display: flex; gap: 10px; align-items: center; color: var(--color-text-3); }.live-level { font-weight: 700; }.live-level--info { color: rgb(var(--primary-6)); }.live-level--warn { color: rgb(var(--warning-6)); }.live-level--error { color: rgb(var(--danger-6)); }.live-log__content { display: grid; gap: 4px; color: var(--color-text-1); }.live-log__content p { margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }.live-console__empty { padding: 32px; color: var(--color-text-3); text-align: center; }
.composer-head { justify-content: space-between; }.composer h2 { margin: 0; font-size: 16px; }.segmented { display: inline-flex; border: 1px solid var(--color-border-2); border-radius: 6px; overflow: hidden; }.segmented button { height: 28px; padding: 0 8px; border: 0; background: transparent; font-size: 11px; }.segmented button.active { background: rgb(var(--primary-6)); color: white; }.render-controls { flex-wrap: wrap; justify-content: space-between; }.render-controls :deep(.arco-form-item) { margin: 0; }.preview { display: grid; place-items: center; min-height: 180px; overflow: auto; border: 1px dashed var(--color-border-2); background: var(--color-fill-1); color: var(--color-text-3); }.preview img { display: block; max-width: 100%; height: auto; }.actions { justify-content: center; } @media (max-width: 1100px) { .console-grid { grid-template-columns: 1fr; } }
.chat-workspace { display: grid; grid-template-columns: 248px minmax(0, 1fr); min-height: 620px; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-2); }.chat-sidebar { overflow: auto; border-right: 1px solid var(--color-border-2); background: var(--color-fill-1); }.chat-sidebar__head, .chat-main__head, .chat-composer__toolbar, .chat-composer__actions { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.chat-sidebar__head { height: 52px; padding: 0 12px; border-bottom: 1px solid var(--color-border-2); }.chat-conversation { display: flex; width: 100%; gap: 10px; align-items: center; padding: 11px 12px; border: 0; border-bottom: 1px solid var(--color-border-2); background: transparent; color: var(--color-text-1); text-align: left; }.chat-conversation:hover, .chat-conversation.active { background: var(--color-bg-2); }.chat-conversation.active { box-shadow: inset 3px 0 0 rgb(var(--primary-6)); }.chat-conversation__icon, .chat-avatar { display: grid; flex: 0 0 auto; place-items: center; width: 32px; height: 32px; border-radius: 6px; background: rgb(var(--primary-6)); color: #fff; }.chat-conversation > span:last-child { display: grid; min-width: 0; gap: 2px; }.chat-conversation strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }.chat-conversation small, .chat-main__head small { color: var(--color-text-3); font-size: 11px; }.chat-main { display: grid; min-width: 0; grid-template-rows: 58px minmax(0, 1fr) auto; }.chat-main__head { padding: 0 16px; border-bottom: 1px solid var(--color-border-2); }.chat-main__head > div { display: grid; gap: 3px; }.chat-message-list { display: grid; align-content: start; gap: 16px; overflow: auto; padding: 16px; background: var(--color-bg-1); }.chat-message { display: flex; gap: 10px; min-width: 0; }.chat-avatar { width: 30px; height: 30px; background: rgb(var(--arcoblue-6, var(--primary-6))); font-size: 12px; }.chat-message > div { min-width: 0; }.chat-message__meta { display: flex; gap: 10px; align-items: baseline; }.chat-message__meta strong { font-size: 13px; }.chat-message__meta time { color: var(--color-text-4); font-size: 11px; }.chat-message p { margin: 4px 0 0; color: var(--color-text-2); white-space: pre-wrap; overflow-wrap: anywhere; }.chat-composer { position: relative; display: grid; gap: 8px; padding: 10px 12px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-2); }.chat-composer__toolbar { justify-content: flex-start; }.chat-composer__actions { color: var(--color-text-3); font-size: 12px; }.mention-picker { position: absolute; z-index: 2; bottom: 100%; left: 12px; display: grid; width: 220px; max-height: 230px; gap: 4px; overflow: auto; padding: 8px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-2); box-shadow: 0 6px 18px rgba(0, 0, 0, .12); }.mention-picker button { display: flex; gap: 8px; align-items: center; width: 100%; padding: 6px; border: 0; border-radius: 4px; background: transparent; color: var(--color-text-1); text-align: left; }.mention-picker button:hover { background: var(--color-fill-2); }.mention-picker button span { display: grid; place-items: center; width: 22px; height: 22px; border-radius: 4px; background: var(--color-fill-3); color: var(--color-text-2); }.mention-picker__empty, .chat-empty { padding: 24px 16px; color: var(--color-text-3); text-align: center; } @media (max-width: 760px) { .chat-workspace { grid-template-columns: 1fr; min-height: 680px; }.chat-sidebar { max-height: 190px; border-right: 0; border-bottom: 1px solid var(--color-border-2); }.chat-main { min-height: 490px; } }
.chat-workspace { height: min(72vh, 720px); min-height: 560px; }.chat-section { display: grid; }.chat-section > span { display: flex; align-items: center; gap: 6px; height: 34px; padding: 0 12px; color: var(--color-text-3); font-size: 12px; }.chat-section__empty { padding: 8px 12px 12px; color: var(--color-text-4); font-size: 12px; }.chat-conversation__avatar { display: grid; flex: 0 0 auto; place-items: center; width: 34px; height: 34px; overflow: hidden; border-radius: 6px; background: var(--color-fill-3); color: var(--color-text-2); }.chat-conversation__avatar img, .chat-avatar img { display: block; width: 100%; height: 100%; object-fit: cover; }.chat-avatar { overflow: hidden; }.chat-message__content { display: flex; flex-wrap: wrap; align-items: flex-start; gap: 4px; }.chat-message__content > span { white-space: pre-wrap; }.chat-message__content img { display: block; max-width: min(360px, 100%); max-height: 300px; border: 1px solid var(--color-border-2); border-radius: 4px; object-fit: contain; }.chat-main { min-height: 0; }.chat-message-list { min-height: 0; } @media (max-width: 760px) { .chat-workspace { height: 72vh; min-height: 600px; }.chat-main { min-height: 0; } }
.chat-message--self { flex-direction: row-reverse; justify-content: flex-start; text-align: right; }.chat-message--self .chat-message__meta { justify-content: flex-end; }.chat-message--self .chat-message__content { justify-content: flex-end; }.chat-quote { display: flex; justify-content: space-between; align-items: center; gap: 8px; padding: 6px 8px; border-left: 3px solid rgb(var(--primary-6)); background: var(--color-fill-1); color: var(--color-text-2); font-size: 12px; }.chat-quote span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.mention-picker button span { overflow: hidden; }.mention-picker button img { display: block; width: 100%; height: 100%; object-fit: cover; }
.attachment-list { display: flex; flex-wrap: wrap; gap: 8px; }.attachment-item { display: flex; align-items: center; gap: 6px; max-width: 190px; padding: 4px 6px; border: 1px solid var(--color-border-2); border-radius: 4px; background: var(--color-fill-1); color: var(--color-text-2); font-size: 12px; }.attachment-item > img { width: 40px; height: 40px; border-radius: 3px; object-fit: cover; }.attachment-item > span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.attachment-item :deep(button) { margin-left: auto; }
</style>
