<script setup lang="ts">
import apiFiles from '@/api/modules/files'
import systemClient from '@/api/modules/system-client'
import { milkyApiCatalog } from '@/api/modules/milky-api-catalog'

type View = 'recent' | 'friends' | 'groups' | 'notifications'
type Scene = 'friend' | 'group'

interface Segment { type: string; data?: Record<string, any> }
interface Peer { id: string; scene: Scene; name: string; avatar?: string; remark?: string; memberCount?: number; preview?: string; time?: string; unread?: number }
interface ChatMessage { message_seq: string; sender_id?: string; sender_nickname?: string; sender_avatar?: string; time?: number; segments?: Segment[] }
interface GroupMember { user_id: string; nickname?: string; card?: string; avatar?: string; role?: string }

const props = defineProps<{ connectionId: string }>()
const toast = useFaToast()
const activeView = ref<View>('recent')
const search = ref('')
const loading = ref(false)
const sending = ref(false)
const friends = ref<Peer[]>([])
const groups = ref<Peer[]>([])
const recent = ref<Peer[]>([])
const selected = ref<Peer | null>(null)
const messages = ref<ChatMessage[]>([])
const members = ref<GroupMember[]>([])
const groupInfo = ref<Record<string, any> | null>(null)
const showMembers = ref(false)
const showApi = ref(false)
const apiName = ref('get_login_info')
const apiPayload = ref('{}')
const apiResult = ref('')
const content = ref('')
const quoted = ref<ChatMessage | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const emojiOpen = ref(false)
const mentionOpen = ref(false)
const mentionSearch = ref('')
const notificationTab = ref<'friend' | 'group'>('friend')
const notifications = ref<any[]>([])
const composer = ref<HTMLElement | null>(null)
const messageScroll = ref<HTMLElement | null>(null)
let eventAbort: AbortController | null = null

const emojis = ['😀', '😁', '😂', '🥰', '😍', '😎', '😭', '😤', '😡', '🤔', '👍', '👋', '🌹', '🎂']
const faces = [{ id: '1', label: '微笑' }, { id: '2', label: '撇嘴' }, { id: '3', label: '色' }, { id: '4', label: '发呆' }, { id: '5', label: '得意' }, { id: '14', label: '流泪' }]
const faceGlyphs: Record<string, string> = { '1': '🙂', '2': '🙁', '3': '😍', '4': '😳', '5': '😎', '14': '😭' }

const visiblePeers = computed(() => {
  const source = activeView.value === 'friends' ? friends.value : activeView.value === 'groups' ? groups.value : recent.value
  const keyword = search.value.trim().toLowerCase()
  return keyword ? source.filter(item => `${item.name}${item.id}`.toLowerCase().includes(keyword)) : source
})
const visibleMentionMembers = computed(() => {
  const keyword = mentionSearch.value.trim().toLowerCase()
  const source = members.value.slice(0, 100)
  return keyword ? source.filter(member => `${member.card || ''} ${member.nickname || ''} ${member.user_id}`.toLowerCase().includes(keyword)) : source
})

function list(value: unknown): any[] { return Array.isArray(value) ? value : [] }
void faces
function object(value: unknown): Record<string, any> { return value && typeof value === 'object' ? value as Record<string, any> : {} }
function displayName(value: any) { return value.card || value.remark || value.nickname || value.group_name || value.user_id || value.group_id || '未知会话' }
function avatarUrl(value: any): string | undefined {
  if (typeof value === 'string' && value) return value
  const source = value?.avatar ?? value?.avatar_url ?? value?.face ?? value?.avatarUrl ?? value?.image_url ?? value?.imageUrl
  if (typeof source === 'string' && source) return source
  if (source && typeof source === 'object') return avatarUrl(source)
  return undefined
}
function avatar(value: any, scene?: Scene) {
  const direct = avatarUrl(value)
  if (direct) return direct
  const qq = value.user_id || value.sender_id || value.group_id || value.id
  if (!qq) return undefined
  const isGroup = scene === 'group' && !value.user_id && !value.sender_id
  return isGroup ? `https://p.qlogo.cn/gh/${qq}/${qq}/100/` : `https://q1.qlogo.cn/g?b=qq&nk=${qq}&s=100`
}
function formatTime(value?: number) { return value ? new Date(value > 10_000_000_000 ? value : value * 1000).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '' }

function segmentPreview(segments?: Segment[]) {
  return (segments || []).map((segment) => {
    if (segment.type === 'text') return String(segment.data?.text || '')
    if (segment.type === 'image') return '[图片]'
    if (segment.type === 'file') return `[文件] ${String(segment.data?.name || '')}`
    if (segment.type === 'face') return '[表情]'
    return `[${segment.type}]`
  }).join('')
}

function segmentText(message: ChatMessage) { return segmentPreview(message.segments) || '[消息]' }
function mediaUrl(value: unknown) {
  if (typeof value !== 'string' || !value) return undefined
  if (/^(https?:|data:|blob:)/i.test(value)) return value
  return value.startsWith('/') ? `${window.location.origin}${value}` : value
}

async function invoke(api: string, payload: Record<string, any> = {}) {
  const response: any = await systemClient.post(`api/platform/milky/connections/${props.connectionId}/chat/api/${api}`, payload)
  return response.data
}

async function loadPeers() {
  loading.value = true
  try {
    const response: any = await systemClient.get(`api/platform/milky/connections/${props.connectionId}/chat/conversations`)
    const groupRows = list(response.data?.groups?.groups ?? response.data?.groups)
    const friendRows = list(response.data?.friends?.friends ?? response.data?.friends)
    groups.value = groupRows.map(group => ({ id: String(group.group_id), scene: 'group' as const, name: String(displayName(group)), avatar: avatar(group, 'group'), memberCount: Number.isFinite(Number(group.member_count)) ? Number(group.member_count) : (Number.isFinite(Number(group.member_count_max)) ? Number(group.member_count_max) : undefined) }))
    friends.value = friendRows.map(friend => ({ id: String(friend.user_id), scene: 'friend' as const, name: String(displayName(friend)), avatar: avatar(friend, 'friend'), remark: friend.remark }))
    recent.value = [...groups.value, ...friends.value]
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '加载会话失败')
  }
  finally { loading.value = false }
}

async function openPeer(peer: Peer) {
  selected.value = peer
  showMembers.value = false
  groupInfo.value = null
  if (peer.scene === 'group') await loadMembers(peer.id)
  await loadHistory(peer)
}

async function loadMembers(groupId: string) {
  try {
    const result = await invoke('get_group_member_list', { group_id: groupId })
    members.value = list(object(result).members ?? object(result).data?.members ?? result).map(item => ({ ...object(item), avatar: avatar(object(item), 'friend') })) as GroupMember[]
  }
  catch { members.value = [] }
}

async function resolveSegment(segment: Segment): Promise<Segment> {
  const data = { ...(segment.data || {}) }
  if (!data.url && typeof data.temp_url === 'string') data.url = data.temp_url
  if (segment.type === 'image' || segment.type === 'file' || segment.type === 'record' || segment.type === 'video') {
    const resourceId = data.file_id || data.resource_id || data.file
    if (resourceId) {
      try {
        const result = await invoke('get_resource_temp_url', { resource_id: resourceId })
        const url = object(result).url || object(result).temp_url || object(result).data?.url || result
        if (typeof url === 'string') data.url = url
      }
      catch { /* keep the original id visible when the temporary URL is unavailable */ }
    }
  }
  return { ...segment, data }
}

async function loadHistory(peer = selected.value) {
  if (!peer) return
  const response: any = await systemClient.get(`api/platform/milky/connections/${props.connectionId}/chat/history`, { params: { scene: peer.scene, peerId: peer.id, limit: 50 } })
  const rows = list(response.data?.messages ?? response.data)
  messages.value = await Promise.all(rows.map(async item => ({ ...item, segments: await Promise.all(list(item.segments).map(segment => resolveSegment(object(segment) as Segment))), sender_avatar: avatarUrl(item.sender_avatar) || avatarUrl(item.sender) || avatar(item, 'friend'), sender_nickname: item.sender_nickname || item.sender?.nickname || item.sender?.card || item.sender?.display_name })))
  if (peer.scene === 'group' && members.value.length) {
    const memberMap = new Map(members.value.map(member => [String(member.user_id), member]))
    messages.value.forEach(message => {
      const member = memberMap.get(String(message.sender_id || ''))
      if (member) {
        message.sender_nickname ||= member.card || member.nickname
        message.sender_avatar ||= member.avatar
      }
    })
  }
  const bySeq = new Map(messages.value.map(message => [String(message.message_seq), message]))
  messages.value.forEach(message => message.segments?.forEach(segment => {
    if (segment.type !== 'reply') return
    const target = bySeq.get(String(segment.data?.message_seq || segment.data?.message_id || ''))
    if (target) segment.data = { ...segment.data, preview: segmentText(target), sender_nickname: target.sender_nickname }
  }))
  const preview = messages.value.at(-1)
  const row = recent.value.find(item => item.id === peer.id && item.scene === peer.scene)
  if (row && preview) { row.preview = segmentPreview(preview.segments); row.time = formatTime(preview.time) }
  await nextTick()
  const scrollToBottom = () => { if (messageScroll.value) messageScroll.value.scrollTop = messageScroll.value.scrollHeight }
  scrollToBottom()
  requestAnimationFrame(() => { scrollToBottom(); requestAnimationFrame(scrollToBottom) })
  window.setTimeout(scrollToBottom, 350)
}

async function appendIncoming(data: Record<string, any>) {
  if (!selected.value) return
  const eventMessage = object(data.message)
  const source = { ...eventMessage, ...data }
  const seq = String(source.message_seq || source.message_id || '')
  if (!seq || messages.value.some(message => String(message.message_seq) === seq)) return
  const segments = await Promise.all(list(source.segments || data.message).map(segment => resolveSegment(object(segment) as Segment)))
  const sender = object(source.sender || source.user || source.member || source.group_member)
  const senderId = String(source.sender_id || source.user_id || sender.user_id || sender.id || '')
  let member = members.value.find(item => String(item.user_id) === senderId)
  if (selected.value.scene === 'group' && senderId && !member && (sender.user_id || sender.nickname || sender.card)) {
    member = { ...sender, user_id: senderId, avatar: avatar(sender, 'friend') } as GroupMember
    members.value.push(member)
  }
  if (selected.value.scene === 'group' && senderId && !member) {
    try {
      const result = await invoke('get_group_member_info', { group_id: selected.value.id, user_id: senderId })
      const remote = object(result).member || object(result).data?.member || object(result)
      if (remote.user_id || remote.userId || remote.nickname || remote.card || avatarUrl(remote)) {
        member = { ...remote, user_id: String(remote.user_id || remote.userId || senderId), avatar: avatar(remote, 'friend') } as GroupMember
        members.value.push(member)
      }
    }
    catch { /* best effort; keep rendering the message if lookup is unavailable */ }
  }
  messages.value.push({
    ...source,
    message_seq: seq,
    sender_id: senderId,
    sender_nickname: source.sender_nickname || source.nickname || sender.nickname || sender.card || sender.display_name || member?.card || member?.nickname,
    sender_avatar: avatarUrl(source.sender_avatar)
      || avatarUrl(source.avatar)
      || avatarUrl(sender)
      || (typeof member?.avatar === 'string' ? member.avatar : undefined)
      || avatar({ ...source, ...sender, sender_id: senderId, user_id: senderId }, 'friend'),
    segments,
  })
  await nextTick()
  if (messageScroll.value) messageScroll.value.scrollTop = messageScroll.value.scrollHeight
}

async function loadGroupDetails() {
  if (!selected.value || selected.value.scene !== 'group') return
  showMembers.value = true
  groupInfo.value = null
  members.value = []
  try {
    const [info, memberRows] = await Promise.all([
      invoke('get_group_info', { group_id: selected.value.id }),
      invoke('get_group_member_list', { group_id: selected.value.id }),
    ])
    groupInfo.value = object(info)
    members.value = list(object(memberRows).members ?? object(memberRows).data?.members ?? memberRows).map(item => ({ ...object(item), avatar: avatar(object(item), 'friend') })) as GroupMember[]
  }
  catch (error) {
    toast.error(error instanceof Error ? error.message : '加载群资料失败')
  }
}

async function loadNotifications() {
  const api = notificationTab.value === 'friend' ? 'get_friend_requests' : 'get_group_notifications'
  notifications.value = list(await invoke(api))
}

async function switchView(view: View) {
  activeView.value = view
  if (view === 'notifications') await loadNotifications()
}

async function send(segments?: Segment[]) {
  if (!selected.value) return
  const message = (segments || [{ type: 'text', data: { text: content.value.trim() } }]).map(segment => {
    if (!['image', 'file', 'record', 'video'].includes(segment.type)) return segment
    const data = { ...(segment.data || {}) }
    const uri = data.uri || data.url || data.file || data.temp_url
    if (segment.type === 'image') return { type: 'image', data: { uri } }
    if (segment.type === 'file') return { type: 'file', data: { uri, name: data.name } }
    return { type: segment.type, data: { uri } }
  })
  if (!segments && !content.value.trim()) return
  if (quoted.value) message.unshift({ type: 'reply', data: { message_seq: quoted.value.message_seq } })
  sending.value = true
  try {
    await systemClient.post(`api/platform/milky/connections/${props.connectionId}/chat/messages`, { scene: selected.value.scene, peerId: selected.value.id, message })
    content.value = ''
    quoted.value = null
    await loadHistory()
  }
  finally { sending.value = false }
}

function addEmoji(emoji: string) { content.value += emoji; emojiOpen.value = false; composer.value?.focus() }
function mention(member: GroupMember) { content.value += `@${member.card || member.nickname || member.user_id} `; mentionOpen.value = false; mentionSearch.value = '' }
function mentionAll() { mentionOpen.value = false; mentionSearch.value = ''; void send([{ type: 'mention_all', data: {} }]) }
function chooseFile() { fileInput.value?.click() }

async function uploadFile(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    if (file.type.startsWith('image/')) {
      const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader()
        reader.onload = () => resolve(String(reader.result || ''))
        reader.onerror = () => reject(new Error('图片读取失败'))
        reader.readAsDataURL(file)
      })
      const base64 = dataUrl.slice(dataUrl.indexOf(',') + 1)
      if (!base64) throw new Error('图片内容为空')
      await send([{ type: 'image', data: { uri: `base64://${base64}` } }])
      return
    }
    const data = new FormData(); data.append('file', file); data.append('publicAccess', 'true')
    const result = await apiFiles.upload(data)
    const rawUrl = result.data.url
    const publicPath = result.data.id ? `/api/files/public/${result.data.id}/content` : rawUrl
    const url = typeof publicPath === 'string' && publicPath.startsWith('/') ? `${window.location.origin}${publicPath}` : publicPath
    if (!url) throw new Error('上传结果没有文件地址')
    await send([{ type: file.type.startsWith('image/') ? 'image' : 'file', data: { uri: url, name: file.name } }])
  }
  catch (error) { toast.error(error instanceof Error ? error.message : '文件发送失败') }
  finally { (event.target as HTMLInputElement).value = '' }
}

async function recall(message: ChatMessage) {
  if (!selected.value) return
  await invoke(selected.value.scene === 'group' ? 'recall_group_message' : 'recall_private_message', { message_seq: message.message_seq, [selected.value.scene === 'group' ? 'group_id' : 'user_id']: selected.value.id })
  await loadHistory()
}

async function react(message: ChatMessage, code: string) {
  if (!selected.value || selected.value.scene !== 'group') return
  await invoke('send_group_message_reaction', { group_id: selected.value.id, message_seq: message.message_seq, reaction: code })
}

function mentionName(id: string) {
  const member = members.value.find(item => String(item.user_id) === String(id))
  return member?.card || member?.nickname || id
}

async function runApi() {
  try {
    apiResult.value = JSON.stringify(await invoke(apiName.value, JSON.parse(apiPayload.value)), null, 2)
  }
  catch (error) { apiResult.value = error instanceof Error ? error.message : '调用失败' }
}

async function actNotification(item: any, accept: boolean) {
  const group = notificationTab.value === 'group'
  await invoke(group ? (accept ? 'accept_group_request' : 'reject_group_request') : (accept ? 'accept_friend_request' : 'reject_friend_request'), object(item))
  await loadNotifications()
}

function connectEvents() {
  const token = localStorage.getItem('token')
  const base = (import.meta.env.DEV && import.meta.env.VITE_ENABLE_PROXY) ? '/proxy/' : import.meta.env.VITE_APP_API_BASEURL
  eventAbort = new AbortController()
  void fetch(`${base}api/platform/milky/connections/${props.connectionId}/chat/events`, { headers: token ? { Authorization: token } : {}, signal: eventAbort.signal }).then(async response => {
    if (!response.ok || !response.body) return
    const reader = response.body.getReader(); const decoder = new TextDecoder(); let buffer = ''
    while (true) {
      const chunk = await reader.read(); if (chunk.done) break
      buffer += decoder.decode(chunk.value, { stream: true })
      const blocks = buffer.split(/\n\n/); buffer = blocks.pop() || ''
      for (const block of blocks) {
        const dataLine = block.split(/\n/).find(line => line.startsWith('data:'))
        const eventLine = block.split(/\n/).find(line => line.startsWith('event:'))
        if (eventLine?.slice(6).trim() !== 'message_receive' || !dataLine) continue
        try { const payload = JSON.parse(dataLine.slice(5).trim()); const data = payload.data || {}; const peerId = String(data.peer_id || data.group_id || data.user_id || ''); if (selected.value && (!peerId || peerId === selected.value.id)) await appendIncoming(data); else await loadPeers() } catch { /* ignore malformed push events */ }
      }
    }
  }).catch(() => { /* reconnect on next workspace open */ })
}

onMounted(async () => { await loadPeers(); connectEvents() })
onBeforeUnmount(() => { eventAbort?.abort(); eventAbort = null })
</script>

<template>
  <section class="qq-workspace">
    <div v-if="loading" class="workspace-loading">加载中...</div>
    <aside class="conversation-rail">
      <nav class="rail-tabs" aria-label="会话分类">
        <button :class="{ active: activeView === 'recent' }" @click="switchView('recent')"><FaIcon name="i-ri:history-line" />最近</button>
        <button :class="{ active: activeView === 'friends' }" @click="switchView('friends')"><FaIcon name="i-ri:user-3-line" />好友</button>
        <button :class="{ active: activeView === 'groups' }" @click="switchView('groups')"><FaIcon name="i-ri:group-line" />群组</button>
        <button class="icon-tab" :class="{ active: activeView === 'notifications' }" title="通知" @click="switchView('notifications')"><FaIcon name="i-ri:notification-3-line" /></button>
      </nav>

      <template v-if="activeView !== 'notifications'">
        <div class="rail-search"><FaIcon name="i-ri:search-line" /><input v-model="search" :placeholder="activeView === 'groups' ? '搜索群组' : '搜索好友、群聊'" /></div>
        <div class="peer-scroll">
          <button v-for="peer in visiblePeers" :key="`${peer.scene}-${peer.id}`" class="peer-row" :class="{ selected: selected?.id === peer.id && selected?.scene === peer.scene }" @click="openPeer(peer)">
            <a-badge v-if="Number(peer.unread) > 0" :count="Number(peer.unread)" :max-count="99" :offset="[-2, 3]"><a-avatar :size="42" :image-url="peer.avatar">{{ peer.name.slice(0, 1) }}</a-avatar></a-badge><a-avatar v-else :size="42" :image-url="peer.avatar">{{ peer.name.slice(0, 1) }}</a-avatar>
            <span class="peer-copy"><b>{{ peer.name }}</b><small>{{ activeView === 'groups' ? `${peer.memberCount || 0} 人` : (peer.preview || (peer.scene === 'group' ? '群聊' : '好友')) }}</small></span>
            <time>{{ peer.time }}</time>
          </button>
          <a-empty v-if="!visiblePeers.length" description="暂无会话" />
        </div>
      </template>

      <template v-else>
        <div class="notification-tabs"><button :class="{ active: notificationTab === 'friend' }" @click="notificationTab = 'friend'; loadNotifications()">好友申请</button><button :class="{ active: notificationTab === 'group' }" @click="notificationTab = 'group'; loadNotifications()">群通知</button></div>
        <div class="peer-scroll notification-list"><article v-for="item in notifications" :key="item.request_id || item.notification_id" class="notification-row"><a-avatar :size="38" :image-url="item.avatar">{{ String(displayName(item)).slice(0, 1) }}</a-avatar><div><b>{{ displayName(item) }}</b><p>{{ item.comment || item.message || '新的申请' }}</p><span><FaButton size="sm" @click="actNotification(item, true)">同意</FaButton><FaButton size="sm" variant="ghost" @click="actNotification(item, false)">拒绝</FaButton></span></div></article><a-empty v-if="!notifications.length" description="暂无通知" /></div>
      </template>
    </aside>

    <main class="chat-stage">
      <header class="chat-header">
        <template v-if="selected"><a-avatar :size="38" :image-url="selected.avatar">{{ selected.name.slice(0, 1) }}</a-avatar><div><h2>{{ selected.name }}</h2><p>{{ selected.scene === 'group' ? `群聊 ${selected.id}${selected.memberCount ? ` · ${selected.memberCount} 人` : ''}` : `私聊 ${selected.id}` }}</p></div></template>
        <span v-else class="placeholder-title">选择一个会话</span>
        <span v-if="selected" class="message-count">{{ messages.length }} 条消息</span>
        <div class="header-actions"><FaButton variant="ghost" title="全部 Milky API" @click="showApi = true"><FaIcon name="i-ri:terminal-box-line" /></FaButton><FaButton v-if="selected?.scene === 'group'" variant="ghost" title="群资料与成员" @click="loadGroupDetails"><FaIcon name="i-ri:team-line" /></FaButton></div>
      </header>

      <div ref="messageScroll" class="message-scroll">
        <a-empty v-if="!selected" description="从左侧选择好友或群聊" />
        <a-empty v-else-if="!messages.length" description="暂无消息" />
        <article v-for="message in messages" :key="message.message_seq" class="message-row" :class="{ self: message.sender_id === selected?.id }">
          <a-avatar :size="36" :image-url="message.sender_avatar">{{ String(message.sender_nickname || message.sender_id || '?').slice(0, 1) }}</a-avatar>
          <div class="message-body"><div class="sender-line"><b>{{ message.sender_nickname || message.sender_id || '未知用户' }}</b><time>{{ formatTime(message.time) }}</time></div><div class="bubble"><template v-for="(segment, index) in message.segments || []" :key="index"><span v-if="segment.type === 'text'">{{ segment.data?.text }}</span><img v-else-if="segment.type === 'image' && (segment.data?.url || segment.data?.file)" class="message-image" :src="mediaUrl(segment.data?.url || segment.data?.file)" alt="图片" referrerpolicy="no-referrer" /><a v-else-if="segment.type === 'file'" :href="mediaUrl(segment.data?.url || segment.data?.file)" target="_blank">📎 {{ segment.data?.name || '文件' }}</a><span v-else-if="segment.type === 'face'" class="face-segment">{{ faceGlyphs[String(segment.data?.face_id)] || '🙂' }}</span><span v-else-if="segment.type === 'mention'" class="mention-segment">@{{ mentionName(String(segment.data?.user_id || '')) }}</span><span v-else-if="segment.type === 'mention_all'" class="mention-segment">@全体成员</span><blockquote v-else-if="segment.type === 'reply'">回复 #{{ segment.data?.message_seq || segment.data?.message_id || '' }}<small v-if="segment.data?.preview">{{ segment.data.preview }}</small></blockquote><span v-else-if="segment.type === 'image'">[图片资源加载失败]</span><span v-else>{{ `[${segment.type}]` }}</span></template></div><div class="message-tools"><button type="button" title="引用" @click="quoted = message">↩</button><button v-if="selected?.scene === 'group'" type="button" title="回应" @click="react(message, '1')">☺</button><button type="button" title="撤回" @click="recall(message)">⌫</button></div></div>
        </article>
      </div>

      <footer class="composer" :class="{ disabled: !selected }">
        <div v-if="quoted" class="quote-bar">正在回复：{{ segmentText(quoted) }}<button @click="quoted = null"><FaIcon name="i-ri:close-line" /></button></div>
        <div class="composer-tools"><div class="tool-popover"><button type="button" title="Emoji" @click="emojiOpen = !emojiOpen">🙂</button><div v-if="emojiOpen" class="inline-panel emoji-grid"><button v-for="emoji in emojis" :key="emoji" type="button" @click="addEmoji(emoji)">{{ emoji }}</button></div></div><div v-if="selected?.scene === 'group'" class="tool-popover"><button type="button" title="提及成员" @click="mentionOpen = !mentionOpen">@</button><div v-if="mentionOpen" class="inline-panel mention-panel"><input v-model="mentionSearch" class="mention-search" placeholder="搜索群成员" /><button type="button" class="mention-option" @click="mentionAll"><span class="mention-avatar">@</span><span>全体成员</span></button><button v-for="member in visibleMentionMembers" :key="member.user_id" type="button" class="mention-option" @click="mention(member)"><a-avatar :size="26" :image-url="member.avatar">{{ String(member.card || member.nickname || member.user_id).slice(0, 1) }}</a-avatar><span>{{ member.card || member.nickname || member.user_id }}</span></button><span v-if="!visibleMentionMembers.length" class="mention-empty">未找到成员</span></div></div><button type="button" title="图片或文件" @click="chooseFile">📎</button><input ref="fileInput" type="file" class="hidden" @change="uploadFile" /></div>
        <textarea ref="composer" v-model="content" :disabled="!selected || sending" placeholder="输入消息，Ctrl + Enter 发送" @keydown.ctrl.enter.prevent="send()" />
        <FaButton class="send-button" :disabled="!selected || !content.trim() || sending" @click="send()"><FaIcon name="i-ri:send-plane-2-line" /></FaButton>
      </footer>
    </main>

    <aside v-if="showMembers && selected?.scene === 'group'" class="detail-rail"><header><div><b>群成员</b><small>{{ groupInfo?.member_count || selected.memberCount || members.length }} 人</small></div><button @click="showMembers = false"><FaIcon name="i-ri:close-line" /></button></header><div v-if="groupInfo" class="group-summary"><a-avatar :size="54" :image-url="selected.avatar">{{ selected.name.slice(0, 1) }}</a-avatar><b>{{ groupInfo.group_name || selected.name }}</b><small>{{ selected.id }}</small></div><div class="member-scroll"><div v-for="member in members" :key="member.user_id" class="member-row"><a-avatar :size="34" :image-url="member.avatar">{{ String(member.card || member.nickname || member.user_id).slice(0, 1) }}</a-avatar><span><b>{{ member.card || member.nickname || member.user_id }}</b><small>{{ member.role || '成员' }}</small></span></div></div></aside>

    <a-drawer v-model:visible="showApi" title="Milky API 工作台" :width="560" :z-index="3000" popup-container="body"><div class="api-workbench"><a-select v-model="apiName" allow-search><a-option v-for="api in milkyApiCatalog" :key="api.name" :value="api.name">{{ api.category }} / {{ api.name }}</a-option></a-select><a-textarea v-model="apiPayload" :auto-size="{ minRows: 12, maxRows: 18 }" placeholder="请求 JSON" /><FaButton @click="runApi">调用 {{ apiName }}</FaButton><pre v-if="apiResult">{{ apiResult }}</pre></div></a-drawer>
  </section>
</template>

<style scoped>
.qq-workspace { position: relative; display: grid; grid-template-columns: 280px minmax(0, 1fr); height: min(760px, calc(100vh - 250px)); min-height: 420px; overflow: hidden; border: 1px solid var(--color-border-2); background: var(--color-bg-1); }
.workspace-loading { position: absolute; inset: 0; z-index: 5; display: grid; place-items: center; background: rgb(var(--gray-1) / 55%); color: var(--color-text-2); pointer-events: none; }
.conversation-rail, .detail-rail { display: flex; min-width: 0; flex-direction: column; background: var(--color-fill-1); }
.conversation-rail { border-right: 1px solid var(--color-border-2); }.detail-rail { position: absolute; top: 0; right: 0; bottom: 0; z-index: 4; width: 260px; border-left: 1px solid var(--color-border-2); box-shadow: -8px 0 18px rgb(0 0 0 / 8%); }
.rail-tabs { display: grid; grid-template-columns: repeat(3, 1fr) 38px; gap: 2px; padding: 8px; border-bottom: 1px solid var(--color-border-2); }.rail-tabs button, .composer-tools button, .message-tools button, .detail-rail header button { display: inline-flex; gap: 4px; align-items: center; justify-content: center; min-height: 32px; border: 0; border-radius: 4px; background: transparent; color: var(--color-text-2); font-size: 13px; }.rail-tabs button.active { background: var(--color-bg-1); color: rgb(var(--primary-6)); font-weight: 600; }.rail-tabs .icon-tab { padding: 0; }
.rail-search { display: flex; gap: 8px; align-items: center; margin: 10px; padding: 0 10px; height: 34px; border: 1px solid var(--color-border-2); border-radius: 4px; background: var(--color-bg-1); color: var(--color-text-3); }.rail-search input { width: 100%; border: 0; outline: 0; background: transparent; color: inherit; }
.peer-scroll, .member-scroll { overflow: auto; padding: 2px 6px; }.peer-row { display: grid; grid-template-columns: 42px minmax(0, 1fr) auto; width: 100%; gap: 10px; align-items: center; min-height: 62px; padding: 8px; border: 0; border-radius: 4px; background: transparent; text-align: left; }.peer-row:hover { background: var(--color-fill-2); }.peer-row.selected { background: rgb(var(--primary-1)); }.peer-copy { display: grid; min-width: 0; gap: 4px; }.peer-copy b, .peer-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.peer-copy b { font-weight: 500; }.peer-copy small, time, small { color: var(--color-text-3); font-size: 12px; }.peer-row time { align-self: start; }
.notification-tabs { display: flex; gap: 12px; padding: 12px; border-bottom: 1px solid var(--color-border-2); }.notification-tabs button { border: 0; background: transparent; color: var(--color-text-2); }.notification-tabs button.active { color: rgb(var(--primary-6)); }.notification-row { display: flex; gap: 10px; padding: 12px 6px; border-bottom: 1px solid var(--color-border-2); }.notification-row div { display: grid; gap: 4px; min-width: 0; }.notification-row p { margin: 0; color: var(--color-text-3); font-size: 12px; }.notification-row span { display: flex; gap: 6px; }
.chat-stage { display: grid; grid-template-rows: 64px minmax(0, 1fr) auto; min-width: 0; min-height: 0; }.chat-header { display: flex; gap: 10px; align-items: center; min-width: 0; padding: 0 16px; border-bottom: 1px solid var(--color-border-2); background: var(--color-bg-1); }.chat-header h2 { margin: 0; font-size: 15px; font-weight: 600; }.chat-header p { margin: 3px 0 0; color: var(--color-text-3); font-size: 12px; }.placeholder-title { color: var(--color-text-3); }.message-count { margin-left: auto; color: var(--color-text-3); font-size: 12px; }.header-actions { display: flex; gap: 4px; margin-left: 4px; }
.message-scroll { min-height: 0; overflow: auto; padding: 20px max(24px, 5%); background: var(--color-bg-1); }.message-row { display: flex; gap: 10px; max-width: 760px; margin: 0 0 20px; }.message-body { min-width: 0; }.sender-line { display: flex; gap: 8px; align-items: baseline; margin-bottom: 4px; }.sender-line b { font-size: 12px; font-weight: 500; color: var(--color-text-2); }.bubble { display: flex; flex-wrap: wrap; gap: 2px; width: fit-content; max-width: min(520px, 100%); padding: 9px 12px; border: 1px solid var(--color-border-2); border-radius: 4px; background: var(--color-fill-1); line-height: 1.55; word-break: break-word; }.bubble :deep(.arco-image) { display: block; overflow: hidden; border-radius: 4px; }.bubble a { display: inline-flex; gap: 6px; align-items: center; color: rgb(var(--primary-6)); }.message-tools { visibility: hidden; display: flex; gap: 4px; }.message-row:hover .message-tools { visibility: visible; }.message-tools button { min-height: 24px; padding: 0 3px; }
.composer { position: relative; display: grid; grid-template-columns: auto minmax(0, 1fr) 40px; gap: 8px; align-items: end; min-height: 100px; padding: 10px 16px; border-top: 1px solid var(--color-border-2); background: var(--color-bg-1); }.composer-tools { display: flex; gap: 3px; align-items: center; }.composer-tools button { min-width: 30px; color: var(--color-text-2); }.composer textarea { width: 100%; min-height: 38px; max-height: 110px; resize: none; padding: 9px 10px; border: 1px solid var(--color-border-2); border-radius: 4px; outline: 0; background: var(--color-fill-1); }.composer textarea:focus { border-color: rgb(var(--primary-5)); }.send-button { width: 38px; height: 38px; padding: 0; }.quote-bar { position: absolute; top: -30px; left: 16px; right: 16px; display: flex; justify-content: space-between; padding: 6px 10px; border: 1px solid var(--color-border-2); border-bottom: 0; border-radius: 4px 4px 0 0; background: var(--color-fill-1); color: var(--color-text-2); font-size: 12px; }.quote-bar button { border: 0; background: transparent; }
.detail-rail header { display: flex; justify-content: space-between; align-items: center; padding: 14px; border-bottom: 1px solid var(--color-border-2); }.detail-rail header div { display: grid; gap: 2px; }.group-summary { display: grid; justify-items: center; gap: 6px; padding: 18px; border-bottom: 1px solid var(--color-border-2); }.member-row { display: flex; gap: 9px; align-items: center; padding: 8px 4px; }.member-row span { display: grid; gap: 2px; min-width: 0; }.member-row b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; font-weight: 500; }
.tool-popover { position: relative; }.inline-panel { position: absolute; bottom: 38px; left: 0; z-index: 20; padding: 6px; border: 1px solid var(--color-border-2); border-radius: 6px; background: var(--color-bg-1); box-shadow: 0 8px 24px rgb(0 0 0 / 14%); }.emoji-grid { display: grid; grid-template-columns: repeat(7, 34px); gap: 4px; }.emoji-grid button { height: 30px; border: 0; border-radius: 4px; background: transparent; font-size: 18px; }.emoji-grid button:hover, .face-grid button:hover, .mention-panel button:hover { background: var(--color-fill-2); }.face-grid { display: grid; grid-template-columns: repeat(3, 68px); gap: 4px; padding: 4px; }.face-grid button, .mention-panel button { padding: 7px; border: 0; border-radius: 4px; background: transparent; text-align: left; }.mention-panel { display: grid; max-height: 280px; overflow: auto; }.bubble blockquote { width: 100%; margin: 0 0 5px; padding-left: 8px; border-left: 2px solid rgb(var(--primary-5)); color: var(--color-text-3); font-size: 12px; }.mention-segment { color: rgb(var(--primary-6)); }
.api-workbench { display: grid; gap: 12px; }.api-workbench pre { overflow: auto; max-height: 300px; margin: 0; padding: 12px; border: 1px solid var(--color-border-2); background: var(--color-fill-1); font-size: 12px; }
@media (max-width: 900px) { .qq-workspace { grid-template-columns: 230px minmax(0, 1fr); height: calc(100vh - 160px); min-height: 520px; }.detail-rail { position: absolute; right: 0; top: 0; bottom: 0; z-index: 3; background: var(--color-bg-1); box-shadow: -8px 0 18px rgb(0 0 0 / 8%); }.message-scroll { padding: 16px; }.bubble { max-width: 320px; } }
@media (max-width: 640px) { .qq-workspace { grid-template-columns: 1fr; }.conversation-rail { display: none; }.chat-stage { min-width: 0; }.message-scroll { padding: 12px; }.composer { grid-template-columns: auto minmax(0, 1fr) 36px; padding: 8px; }.composer-tools button { min-width: 25px; }.chat-header { padding: 0 10px; } }
.bubble blockquote { flex: 0 0 100%; width: 100%; max-width: 100%; }.bubble blockquote small { display: block; max-width: 360px; margin-top: 3px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.message-image { display: block; width: auto; max-width: 220px; max-height: 260px; border-radius: 4px; object-fit: contain; }
:global(.arco-drawer-container), :global(.arco-drawer-mask), :global(.arco-drawer), :global(.arco-select-popup), :global(.arco-trigger-popup) { z-index: 3000 !important; }
.mention-panel { left: 0 !important; right: auto !important; width: 240px; padding: 6px; align-items: stretch; justify-items: stretch; text-align: left; transform: none !important; }.mention-search { width: 100%; height: 30px; margin-bottom: 5px; padding: 0 8px; border: 1px solid var(--color-border-2); border-radius: 4px; outline: 0; background: var(--color-fill-1); color: var(--color-text-1); text-align: left; }.mention-option { display: flex !important; width: 100%; gap: 8px; align-items: center; justify-content: flex-start !important; min-height: 36px; margin: 0; white-space: nowrap; text-align: left !important; }.mention-option span { overflow: hidden; text-overflow: ellipsis; text-align: left; }.mention-avatar { display: inline-grid; width: 26px; height: 26px; flex: 0 0 26px; place-items: center; border-radius: 50%; background: rgb(var(--primary-2)); color: rgb(var(--primary-6)); }.mention-empty { padding: 10px 6px; color: var(--color-text-3); font-size: 12px; text-align: left; }
</style>
