import type { MessagingBindingCode, MessagingBindingTarget } from '@/api/modules/profile'
import { computed, onScopeDispose, ref } from 'vue'

interface MessagingBindingApi {
  listMessagingBindingTargets: () => Promise<{ data: MessagingBindingTarget[] }>
  issueMessagingBindingCode: (connectionId: string) => Promise<{ data: MessagingBindingCode }>
}

export function bindingTargetLabel(target: { botName?: string, name?: string, connectionName?: string }) {
  return target.botName?.trim() || target.connectionName?.trim() || target.name?.trim() || '未命名连接'
}

export function bindingProtocolLabel(protocol?: string) {
  return ({ official: '官方 QQ 机器人', milky: 'Milky' })[protocol || ''] || protocol || '消息协议'
}

export function bindingCodeExpired(code: MessagingBindingCode, now: number) {
  const expiresAt = Date.parse(code.expiresAt)
  return !Number.isFinite(expiresAt) || expiresAt <= now
}

export function useMessagingBinding(api: MessagingBindingApi) {
  const targets = ref<MessagingBindingTarget[]>([])
  const loading = ref(false)
  const loadError = ref('')
  const issuingConnectionId = ref('')
  const issueError = ref('')
  const bindingCode = ref<MessagingBindingCode | null>(null)
  const boundCount = computed(() => targets.value.filter(target => target.bound).length)
  let disposed = false

  onScopeDispose(() => {
    disposed = true
  })

  async function load() {
    if (disposed || loading.value || issuingConnectionId.value) {
      return
    }
    loading.value = true
    loadError.value = ''
    try {
      const { data } = await api.listMessagingBindingTargets()
      if (disposed) {
        return
      }
      targets.value = data || []
      if (bindingCode.value && !targets.value.some(target => target.connectionId === bindingCode.value?.connectionId)) {
        bindingCode.value = null
      }
    }
    catch (error) {
      if (!disposed) {
        loadError.value = error instanceof Error ? error.message : '连接列表加载失败，请重试'
      }
    }
    finally {
      if (!disposed) {
        loading.value = false
      }
    }
  }

  async function issue(target: MessagingBindingTarget) {
    if (disposed || loading.value || issuingConnectionId.value || !targets.value.some(item => item.connectionId === target.connectionId)) {
      return
    }
    issuingConnectionId.value = target.connectionId
    issueError.value = ''
    // 绑定码由账号签发；再次生成后不再展示旧码，避免把旧命令发给其他连接。
    bindingCode.value = null
    try {
      const { data } = await api.issueMessagingBindingCode(target.connectionId)
      if (disposed) {
        return
      }
      if (!data.code || (data.connectionId && data.connectionId !== target.connectionId)) {
        throw new Error('绑定码与所选连接不一致，请重新生成')
      }
      bindingCode.value = {
        ...data,
        connectionId: target.connectionId,
        connectionName: data.connectionName || target.name,
        botName: data.botName || target.botName,
        protocol: data.protocol || target.protocol,
      }
    }
    catch (error) {
      if (!disposed) {
        issueError.value = error instanceof Error ? error.message : '绑定码生成失败，请重试'
      }
    }
    finally {
      if (!disposed) {
        issuingConnectionId.value = ''
      }
    }
  }

  return { targets, loading, loadError, issuingConnectionId, issueError, bindingCode, boundCount, load, issue }
}
