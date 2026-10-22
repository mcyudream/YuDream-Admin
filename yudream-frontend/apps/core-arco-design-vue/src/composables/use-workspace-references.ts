import type { ChatWorkspaceEntry } from '@/api/modules/chat-workspace'
import { computed, ref } from 'vue'
import apiChatWorkspace from '@/api/modules/chat-workspace'

/** 小文本整段内联进提示的上限；更大的文件交给助手的工作区读取工具 */
const INLINE_CHARS = 32000

/**
 * 把工作区文件引用进下一条消息（AI 助手抽屉与对话页共用）：
 * 列出工作区文件、切换引用、发送时把小文本内联、其余提示助手用工作区工具读取。
 */
export function useWorkspaceReferences(canLoad: () => boolean = () => true) {
  const entries = ref<ChatWorkspaceEntry[]>([])
  const loading = ref(false)
  const error = ref('')
  const referenced = ref<string[]>([])
  const fileEntries = computed(() => entries.value.filter(entry => entry.kind === 'FILE'))
  const files = computed(() => fileEntries.value.map(entry => ({ path: entry.path, included: referenced.value.includes(entry.path) })))

  async function load() {
    if (!canLoad()) {
      return
    }
    loading.value = true
    error.value = ''
    try {
      entries.value = (await apiChatWorkspace.overview()).entries
      referenced.value = referenced.value.filter(path => entries.value.some(entry => entry.path === path))
    }
    catch (cause) {
      error.value = cause instanceof Error ? cause.message : '读取工作区失败'
    }
    finally {
      loading.value = false
    }
  }

  function toggle(path: string, included: boolean) {
    referenced.value = included ? [...new Set([...referenced.value, path])] : referenced.value.filter(item => item !== path)
  }

  async function compose(text: string) {
    const paths = [...referenced.value]
    referenced.value = []
    if (!paths.length) {
      return text
    }
    const blocks: string[] = []
    for (const path of paths) {
      try {
        const file = await apiChatWorkspace.read(path)
        blocks.push(file.encoding === 'text' && file.content !== null && !file.truncated && file.content.length <= INLINE_CHARS
          ? `【工作区文件 ${path}】\n${file.content}`
          : `【工作区文件 ${path}】文件较大或不是文本，请用工作区读取工具自行查看`)
      }
      catch {
        blocks.push(`【工作区文件 ${path}】读取失败，如需要请在对话中说明`)
      }
    }
    return `${blocks.join('\n\n')}\n\n${text}`
  }

  return { entries, loading, error, referenced, fileEntries, files, load, toggle, compose }
}
