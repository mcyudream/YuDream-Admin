<script setup lang="ts">
import type { WorkspaceEntry } from '@/api/modules/platform-cms-workspace'
import { cmsWorkspaceChatEndpoint, cmsWorkspaceRawUrl } from '@/api/modules/platform-cms-workspace'
import apiWorkspace from '@/api/modules/platform-cms-workspace'
import { useYdChatStream, YdChatMessageList, YdChatSender } from '@yudream/components'
import { computed, ref, watch } from 'vue'

const toast = useFaToast()
const modal = useFaModal()
const loading = ref(false)
const entries = ref<WorkspaceEntry[]>([])
const selectedPath = ref('')
const editingContent = ref('')
const uploadInput = ref<HTMLInputElement>()
const previewKey = ref(0)
const targetKey = 'home'

const { messages, streaming, send, stop, clear } = useYdChatStream({
  endpoint: () => cmsWorkspaceChatEndpoint(targetKey),
  protocol: 'agui',
  transport: 'sse',
  historyLimit: 8,
})

const textEntries = computed(() =>
  entries.value
    .filter(entry => isTextPath(entry.path))
    .sort((a, b) => a.path.localeCompare(b.path)),
)
const assetEntries = computed(() =>
  entries.value.filter(entry => !isTextPath(entry.path)).sort((a, b) => a.path.localeCompare(b.path)),
)
const previewUrl = computed(() => `${cmsWorkspaceRawUrl(`preview/pages/`)}index.html?v=${previewKey.value}`)

watch(selectedPath, loadSelected)
onMounted(() => {
  void refresh()
  clear()
})

async function refresh() {
  loading.value = true
  try {
    entries.value = (await apiWorkspace.list()).data
    if (!entries.value.some(entry => entry.path === selectedPath.value)) {
      const first = textEntries.value.find(entry => entry.path.endsWith('.html')) ?? textEntries.value[0]
      selectedPath.value = first ? first.path : ''
      editingContent.value = ''
    }
    previewKey.value++
  }
  finally { loading.value = false }
}
async function loadSelected() {
  if (!selectedPath.value) {
    editingContent.value = ''
    return
  }
  const res = await apiWorkspace.read(selectedPath.value)
  editingContent.value = res.data.encoding === 'text' ? res.data.content : ''
}
async function saveSelected() {
  if (!selectedPath.value) {
    toast.warning('请先选择文件')
    return
  }
  await apiWorkspace.write(selectedPath.value, editingContent.value)
  toast.success('已保存')
  previewKey.value++
}
function createPage() {
  const path = window.prompt('页面路径（pages/ 下，如 about/index.html）')
  if (!path) return
  const normalized = path.trim().replace(/^\/+/, '')
  if (!normalized.startsWith('pages/') || !normalized.endsWith('.html')) {
    toast.error('路径必须位于 pages/ 下且以 .html 结尾')
    return
  }
  void apiWorkspace.write(normalized, '<!doctype html>\n<html lang="zh-CN">\n<head>\n  <meta charset="UTF-8">\n  <script src="/api/platform/ai/cms/workspace/lib.js"><\/script>\n</head>\n<body>\n  <main class="yb-ai-page"></main>\n</body>\n</html>\n').then(async () => {
    toast.success('页面已创建')
    await refresh()
    selectedPath.value = normalized
  })
}
function pickUpload() {
  uploadInput.value?.click()
}
async function onUploadChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  const path = `assets/${file.name}`
  await apiWorkspace.upload(path, file)
  toast.success(`资产已上传：${path}`)
  await refresh()
}
function confirmPublish() {
  modal.confirm({
    title: '发布站点',
    content: '将先运行完整性校验（HTML/CSS/JS 结构与类覆盖），通过后整树快照为发布版本并同步站点页面。确认发布？',
    onConfirm: async () => {
      const res = await apiWorkspace.publish()
      toast.success(`已发布：${res.data.files} 个文件、${res.data.pages} 个页面`)
      window.open(`${cmsWorkspaceRawUrl('').replace('/workspace/raw/', '/public/cms/site/')}pages/index.html`, '_blank')
    },
  })
}
function isTextPath(path: string) {
  return /\.(html?|css|js|json|md|svg|txt)$/i.test(path)
}
function fileName(path: string) {
  return path.split('/').pop() || path
}
</script>

<template>
  <div>
    <FaPageHeader title="CMS 站点工作区" class="mb-0">
      <template #right>
        <FaButton size="sm" variant="outline" @click="pickUpload">
          <FaIcon name="morph:upload" /> 上传资产
        </FaButton>
        <FaButton size="sm" variant="outline" @click="createPage">
          <FaIcon name="morph:file-plus" /> 新建页面
        </FaButton>
        <FaButton size="sm" variant="outline" @click="refresh">
          <FaIcon name="morph:refresh-cw" /> 刷新
        </FaButton>
        <FaButton v-auth="'platform:ai:generate'" size="sm" @click="confirmPublish">
          <FaIcon name="morph:rocket" /> 发布站点
        </FaButton>
      </template>
    </FaPageHeader>
    <FaPageMain class="!px-0">
      <div class="workbench">
        <aside class="files-pane">
          <div class="pane-head">页面与文件</div>
          <div class="file-group">
            <div class="group-title">页面</div>
            <button
              v-for="entry in textEntries"
              :key="entry.path"
              class="file-item"
              :class="{ active: entry.path === selectedPath }"
              @click="selectedPath = entry.path"
            >
              <FaIcon name="morph:file-code" /> {{ fileName(entry.path) }}
            </button>
            <div v-if="!textEntries.length" class="group-empty">暂无文件，先让 AI 生成或新建页面</div>
          </div>
          <div class="file-group">
            <div class="group-title">资产</div>
            <div v-for="entry in assetEntries" :key="entry.path" class="file-item static">
              <FaIcon name="morph:paperclip" /> {{ fileName(entry.path) }}
            </div>
            <div v-if="!assetEntries.length" class="group-empty">暂无资产</div>
          </div>
        </aside>
        <section class="editor-pane">
          <div class="pane-head">
            <span class="min-w-0 truncate">{{ selectedPath || '选择左侧文件' }}</span>
            <FaButton size="sm" variant="outline" :disabled="!selectedPath" @click="saveSelected">保存</FaButton>
          </div>
          <FaTextarea
            v-model="editingContent"
            class="editor h-full w-full"
            input-class="font-mono"
            :autosize="false"
            placeholder="选择文件后编辑"
          />
        </section>
        <section class="preview-pane">
          <div class="pane-head">
            预览
            <FaButton size="sm" variant="ghost" @click="previewKey++">刷新</FaButton>
          </div>
          <iframe :key="previewKey" :src="previewUrl" class="preview" title="站点预览" />
        </section>
        <section class="chat-pane">
          <div class="pane-head">AI 构建</div>
          <div class="chat-body">
            <YdChatMessageList :messages="messages">
              <template #empty>
                <div class="chat-empty">
                  <strong>描述你想要的页面</strong>
                  <p>例如：给首页加一个三列特性区块，样式用 yb-ai- 前缀</p>
                </div>
              </template>
            </YdChatMessageList>
          </div>
          <YdChatSender :loading="streaming" @send="send" @stop="stop" />
        </section>
      </div>
    </FaPageMain>
    <input ref="uploadInput" type="file" class="hidden" @change="onUploadChange">
  </div>
</template>

<style scoped>
.workbench { display: grid; grid-template-columns: 220px minmax(0, 1.2fr) minmax(0, 1fr) 320px; gap: 12px; min-height: calc(100vh - 220px); }
.files-pane, .editor-pane, .preview-pane, .chat-pane { display: flex; min-width: 0; flex-direction: column; overflow: hidden; border: 1px solid var(--color-border-2); border-radius: 8px; background: var(--color-bg-1); }
.pane-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 10px 12px; border-bottom: 1px solid var(--color-border-2); font-size: 12px; font-weight: 500; color: var(--color-text-2); }
.file-group { padding: 8px; }
.group-title { padding: 4px 8px; color: var(--color-text-3); font-size: 11px; }
.group-empty { padding: 8px; color: var(--color-text-3); font-size: 11px; }
.file-item { display: flex; width: 100%; align-items: center; gap: 6px; padding: 6px 8px; border: 0; border-radius: 6px; background: transparent; color: var(--color-text-2); font-size: 12px; text-align: left; cursor: pointer; }
.file-item:hover { background: var(--color-fill-2); }
.file-item.active { color: rgb(var(--primary-6)); background: var(--color-fill-2); font-weight: 500; }
.file-item.static { cursor: default; }
.file-item .del { margin-left: auto; border: 0; background: transparent; color: var(--color-text-3); cursor: pointer; }
.editor :deep(textarea) { min-height: 100%; resize: none; }
.editor { min-height: 0; }
.preview { width: 100%; min-height: 0; border: 0; background: #fff; flex: 1; }
.chat-body { min-height: 0; overflow: auto; padding: 0 12px; flex: 1; }
.chat-empty { display: grid; gap: 6px; padding: 24px 8px; color: var(--color-text-3); font-size: 12px; justify-items: center; text-align: center; }
.chat-empty strong { color: var(--color-text-1); }
@media (max-width: 1024px) {
  .workbench { grid-template-columns: 1fr; }
}
</style>
