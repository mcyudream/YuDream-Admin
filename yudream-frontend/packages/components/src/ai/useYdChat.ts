import type { UseYdChatStreamOptions, YdChatAttachment, YdChatMessage } from './useYdChatStream'
import { computed, ref } from 'vue'
import { useYdChatStream } from './useYdChatStream'

export interface UseYdChatOptions extends UseYdChatStreamOptions {
  /** 初始消息（如后端历史回填） */
  initialMessages?: YdChatMessage[]
}

/**
 * 聊天数据管理 composable（对齐 antd-x useXChat / tdesign useChat）：
 * 在 useYdChatStream 之上维护输入框、附件与消息状态，提供 sendInput/regenerate 等高层动作。
 */
export function useYdChat(options: UseYdChatOptions) {
  const { messages, streaming, toolHint, send, stop, clear } = useYdChatStream(options)

  const input = ref('')
  const attachments = ref<YdChatAttachment[]>([])

  if (options.initialMessages?.length) {
    messages.value = [...options.initialMessages]
  }

  /** 发送输入框内容（携带当前附件），成功后清空输入与附件 */
  async function sendInput(text?: string) {
    const question = (text ?? input.value).trim()
    if (!question || streaming.value) {
      return
    }
    await send(question, attachments.value)
    input.value = ''
    attachments.value = []
  }

  /** 重新生成：找到该 assistant 消息前的最后一条 user 消息并重发 */
  async function regenerate(message: YdChatMessage) {
    const index = messages.value.indexOf(message)
    if (index <= 0) {
      return
    }
    for (let i = index - 1; i >= 0; i--) {
      const candidate = messages.value[i]
      if (candidate.role === 'user') {
        await send(candidate.content, candidate.attachments)
        return
      }
    }
  }

  function addAttachment(attachment: YdChatAttachment) {
    attachments.value.push(attachment)
  }

  function removeAttachment(attachment: YdChatAttachment) {
    attachments.value = attachments.value.filter(item => item !== attachment)
  }

  function setMessages(next: YdChatMessage[]) {
    messages.value = next
  }

  const empty = computed(() => messages.value.length === 0)

  return {
    messages,
    input,
    attachments,
    streaming,
    toolHint,
    empty,
    send,
    sendInput,
    regenerate,
    stop,
    clear,
    setMessages,
    addAttachment,
    removeAttachment,
  }
}
