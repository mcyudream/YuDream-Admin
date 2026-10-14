<script setup lang="ts">
import type { YdAgentHarnessState, YdAgentSendRequest, YdChatAttachment, YdChatMessage } from '@yudream/components'
import type { AgentDebugAttachment } from './types'
import { YdAgentWorkbench, YdAlert, YdButton } from '@yudream/components'
import AiRunProgress from '@/components/AiRunProgress/index.vue'

const props = defineProps<{
  messages: YdChatMessage[]
  running: boolean
  harness: YdAgentHarnessState
  runId?: string
  failure?: string
  allowImage: boolean
  allowFiles: boolean
  modelLabel: string
}>()
const emit = defineEmits<{
  send: [content: string, attachments: AgentDebugAttachment[]]
  stop: []
  clear: []
  close: []
}>()
const attachments = ref<AgentDebugAttachment[]>([])
const attachmentError = ref('')
const displayedAttachments = computed<YdChatAttachment[]>(() => attachments.value.map(item => ({ fileName: item.name, contentType: item.type, size: item.size, dataUrl: item.dataUrl, kind: item.type.startsWith('image/') ? 'IMAGE' : 'DOCUMENT' })))
const fileAccept = computed(() => [
  props.allowImage ? 'image/*' : '',
  props.allowFiles ? '.txt,.md,.markdown,.json,.csv,.xml,.html,.css,.js,.ts,.yaml,.yml,.pdf,.doc,.docx,.rtf,.odt' : '',
].filter(Boolean).join(','))
function send(request: YdAgentSendRequest) {
  if (props.running) {
    return
  }
  emit('send', request.text, [...attachments.value])
  attachments.value = []
}
function analyzeAttachments() {
  if (!attachments.value.length || props.running) {
    return
  }
  emit('send', '请分析这些附件的内容', [...attachments.value])
  attachments.value = []
}
function removeAttachment(attachment: YdChatAttachment) {
  const index = attachments.value.findIndex(item => item.name === attachment.fileName && item.dataUrl === attachment.dataUrl)
  if (index >= 0) {
    attachments.value.splice(index, 1)
  }
}
async function onFileChange(files: File[]) {
  attachmentError.value = ''
  try {
    for (const file of files) {
      const image = file.type.startsWith('image/')
      if (image && !props.allowImage) {
        throw new Error('当前模型不支持图片输入')
      }
      if (!image && !props.allowFiles) {
        throw new Error('当前模型节点未开启文本附件')
      }
      if (image && file.size > 5 * 1024 * 1024) {
        throw new Error('单张图片不能超过 5MB')
      }
      if (!image && file.size > 10 * 1024 * 1024) {
        throw new Error('单个文档附件不能超过 10MB')
      }
      if (image && attachments.value.some(item => item.type.startsWith('image/'))) {
        throw new Error('当前调试仅支持一张图片')
      }
      attachments.value.push({ id: crypto.randomUUID(), name: file.name, type: file.type || 'text/plain', size: file.size, dataUrl: await readDataUrl(file), text: !image && file.size <= 256 * 1024 && isTextFile(file) ? await file.text() : undefined })
    }
  }
  catch (error) {
    attachmentError.value = error instanceof Error ? error.message : '附件读取失败'
  }
}
function readDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(new Error('附件读取失败'))
    reader.readAsDataURL(file)
  })
}
function isTextFile(file: File) {
  return file.type.startsWith('text/') || /\.(?:txt|md|markdown|json|csv|xml|html|css|js|ts|yaml|yml)$/i.test(file.name)
}
</script>

<template>
  <aside class="debug-panel">
    <YdAgentWorkbench :messages="messages" :harness="harness" :available-modes="['ask']" :attachments="displayedAttachments" :attachment-accept="fileAccept" :attachments-enabled="allowImage || allowFiles" class="debug-workbench" placeholder="输入调试消息…" @send="send" @stop="emit('stop')" @attach="onFileChange" @remove-attachment="removeAttachment">
      <template #header-actions>
        <span class="debug-model-label">{{ modelLabel }}</span>
        <YdButton variant="ghost" size="icon-sm" aria-label="恢复后端调试进度" @click="emit('clear')">
          <YdIcon name="morph:refresh-cw" />
        </YdButton>
        <YdButton variant="ghost" size="icon-sm" aria-label="返回配置" @click="emit('close')">
          <YdIcon name="morph:x" />
        </YdButton>
      </template>
      <template #empty>
        <p>提交调试问题，节点、工具和模型内容会按实际运行实时显示。</p>
      </template>
      <template #composer-before>
        <YdAlert v-if="failure || attachmentError" variant="destructive" :title="failure || attachmentError" />
        <AiRunProgress v-if="runId && harness.status === 'waiting'" :run-id="runId" />
      </template>
      <template #sender-actions>
        <YdButton v-if="attachments.length" variant="outline" size="sm" :disabled="running" @click="analyzeAttachments">
          分析附件
        </YdButton>
      </template>
    </YdAgentWorkbench>
  </aside>
</template>

<style scoped>
.debug-panel {
  width: 100%;
  min-width: 0;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  background: var(--color-bg-1);
  border-left: 1px solid var(--color-border-2);
}

.debug-workbench {
  height: 100%;
}

.debug-model-label {
  font-size: 12px;
  color: var(--color-text-3);
}
</style>
