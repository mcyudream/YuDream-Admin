<script setup lang="ts">
import type { NotificationItem } from '@/api/modules/notifications'
import apiNotifications from '@/api/modules/notifications'
import { toBackendAssetUrl } from '@/utils/backend-url'

defineOptions({
  name: 'NotificationBell',
})

const router = useRouter()

const unread = ref(0)
const list = ref<NotificationItem[]>([])
const loading = ref(false)
let streamController: AbortController | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const hasToken = computed(() => !!localStorage.getItem('token'))

function typeIcon(item: NotificationItem) {
  if (!item.read) {
    // 未读消息红点
    return 'i-ri:circle-fill text-red-500 text-[10px]'
  }
  switch (item.type) {
    case 'reply':
      return 'i-ri:chat-3-line text-secondary-foreground/60'
    case 'like':
      return 'i-ri:thumb-up-line text-secondary-foreground/60'
    default:
      return 'i-ri:notification-3-line text-secondary-foreground/60'
  }
}

function dropdownItems() {
  if (!list.value.length) {
    return [[{ label: '暂无消息', icon: 'i-ri:inbox-line', handle: () => {} }]]
  }
  return [
    list.value.map(item => ({
      label: item.title,
      icon: typeIcon(item),
      disabled: false,
      handle: () => {
        if (!item.read) {
          item.read = true
          unread.value = Math.max(0, unread.value - 1)
          apiNotifications.markRead(item.id)
        }
        if (item.link) {
          router.push(item.link)
        }
      },
    })),
  ]
}

async function refresh() {
  try {
    const { data } = await apiNotifications.unreadCount()
    unread.value = data?.unread ?? 0
  }
  catch {
    unread.value = 0
  }
  try {
    const { data } = await apiNotifications.page({ page: 1, size: 10 })
    list.value = data?.records ?? []
  }
  catch {
    list.value = []
  }
}

async function markAll() {
  loading.value = true
  try {
    await apiNotifications.markAllRead()
    unread.value = 0
    list.value = list.value.map(item => ({ ...item, read: true }))
  }
  finally {
    loading.value = false
  }
}

/** fetch 流式消费通知 SSE（EventSource 无法携带 Authorization 头），断线自动重连。 */
async function connectStream() {
  const token = localStorage.getItem('token')
  if (!token) {
    return
  }
  const controller = new AbortController()
  streamController = controller
  let aborted = false
  try {
    const response = await fetch(toBackendAssetUrl('/api/notifications/stream'), {
      headers: { Authorization: token },
      signal: controller.signal,
    })
    if (!response.ok || !response.body) {
      return
    }
    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        break
      }
      buffer += decoder.decode(value, { stream: true })
      let index
      while ((index = buffer.indexOf('\n\n')) >= 0) {
        const chunk = buffer.slice(0, index)
        buffer = buffer.slice(index + 2)
        if (chunk.includes('event: notification')) {
          await refresh()
        }
      }
    }
  }
  catch {}
  finally {
    aborted = controller.signal.aborted
    if (streamController === controller) {
      streamController = null
    }
  }
  if (!aborted) {
    reconnectTimer = setTimeout(() => void connectStream(), 5000)
  }
}

onMounted(async () => {
  if (!hasToken.value) {
    return
  }
  await refresh()
  void connectStream()
})

onBeforeUnmount(() => {
  streamController?.abort()
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
  }
})
</script>

<template>
  <FaDropdown :align="'end'" :side="'bottom'" :items="dropdownItems()" class="flex-center" @open-change="(open: boolean) => open && refresh()">
    <template #header>
      <div class="flex-center-between gap-4 px-1">
        <span class="text-sm font-700">通知中心</span>
        <button
          v-if="unread > 0" class="text-xs cursor-pointer border-0 bg-transparent text-primary font-500"
          :disabled="loading" @click.stop="markAll"
        >
          全部已读
        </button>
      </div>
    </template>
    <button class="relative flex-center size-10 cursor-pointer border-0 rounded-lg bg-transparent" title="通知中心">
      <FaIcon name="i-ri:notification-3-line" class="text-lg" />
      <span
        v-if="unread > 0"
        class="absolute top-1 right-0.5 flex-center min-w-4 h-4 px-1 rounded-full bg-red-500 text-white text-[10px] font-700 leading-none"
      >
        {{ unread > 99 ? '99+' : unread }}
      </span>
    </button>
  </FaDropdown>
</template>
