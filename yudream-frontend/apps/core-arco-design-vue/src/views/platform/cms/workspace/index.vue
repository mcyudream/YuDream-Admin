<script setup lang="ts">
import type { YdAgentSendRequest, YdAgentWorkspace, YdFilePickerInstance } from '@yudream/components'
import type { WorkspaceEntry } from '@/api/modules/platform-cms-workspace'
import { YdAgentWorkbench, YdAlert } from '@yudream/components'
import { computed, ref, watch } from 'vue'
import apiWorkspace from '@/api/modules/platform-cms-workspace'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { useAiRunTask } from '@/composables/use-ai-run-task'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import CmsGrapesEditor from '@/views/platform/theme-center/components/CmsGrapesEditor.vue'

const toast = useYdToast()
const modal = useYdModal()
const loading = ref(false)
const entries = ref<WorkspaceEntry[]>([])
const workspaceLoaded = ref(false)
const createVisible = ref(false)
const createPath = ref('')
const selectedPath = ref('')
const uploadInput = ref<YdFilePickerInstance | null>(null)
const previewKey = ref(0)
const targetKey = 'home'

const editVisible = ref(false)
const editPath = ref('')
const editContent = ref('')
const previewVisible = ref(false)
const previewDoc = ref('')
const canvasVisible = ref(false)
const canvasHtml = ref('')
const canvasCss = ref('')
const canvasJs = ref('')
const canvasProjectJson = ref('')
const canvasFilePath = computed(() => selectedPath.value || 'pages/index.html')
const chatOpen = ref(false)
const preparing = ref(false)

const runtime = useAiAssistantStore()
const task = useAiRunTask(`cms-${targetKey}`)
const workspaces = computed<YdAgentWorkspace[]>(() => workspaceLoaded.value ? [{ id: `cms-${targetKey}`, name: 'CMS 站点工作区', kind: 'remote', access: 'write', description: '由服务端保存的站点页面与资产文件' }] : [])
const messages = computed(() => runtime.taskMessages(`cms-${targetKey}`))
const harness = computed(() => ({ name: 'AgentScope Harness', runId: task.run.value?.id, status: task.running.value || preparing.value ? task.run.value?.status === 'WAITING' ? 'waiting' as const : 'running' as const : task.run.value?.status === 'FAILED' ? 'error' as const : task.run.value?.status === 'CANCELLED' ? 'cancelled' as const : task.run.value ? 'completed' as const : 'idle' as const }))
watch(() => task.run.value?.status, (status) => {
  if (status === 'COMPLETED') {
    void refresh()
  }
})

watch(selectedPath, loadSelected)
onMounted(refresh)

async function refresh() {
  loading.value = true
  try {
    entries.value = (await apiWorkspace.list()).data
    workspaceLoaded.value = true
    previewKey.value++
  }
  finally {
    loading.value = false
  }
}
async function loadSelected() {
  if (!selectedPath.value || !selectedPath.value.endsWith('.html')) {
    return
  }
  const res = await apiWorkspace.read(selectedPath.value)
  editPath.value = selectedPath.value
  editContent.value = res.data.encoding === 'text' ? res.data.content : ''
}
function openEdit(path: string) {
  selectedPath.value = path
  void loadSelected().then(() => {
    editPath.value = path
    editVisible.value = true
  })
}
async function saveEdit() {
  await apiWorkspace.write(editPath.value, editContent.value)
  toast.success('已保存')
  editVisible.value = false
  previewKey.value++
  await refresh()
  selectedPath.value = editPath.value
}
function createPage() {
  createPath.value = ''
  createVisible.value = true
}
async function submitNewPage() {
  const normalized = createPath.value.trim().replace(/^\/+/, '')
  if (!normalized.startsWith('pages/') || !normalized.endsWith('.html')) {
    toast.error('路径必须位于 pages/ 下且以 .html 结尾')
    return
  }
  const skeleton = '<!doctype html>\n<html lang="zh-CN">\n<head>\n  <meta charset="UTF-8">\n</head>\n<body>\n  <main class="yb-ai-page"></main>\n</body>\n</html>\n'
  await apiWorkspace.write(normalized, skeleton)
  createVisible.value = false
  toast.success('页面已创建')
  await refresh()
  openEdit(normalized)
}
function pickUpload() {
  uploadInput.value?.open()
}
async function onUploadChange(files: File[]) {
  const file = files[0]
  uploadInput.value?.reset()
  if (!file) {
    return
  }
  const path = `assets/${file.name}`
  await apiWorkspace.upload(path, file)
  toast.success(`资产已上传：${path}`)
  await refresh()
}
function deleteEntry(entry: WorkspaceEntry) {
  modal.confirm({
    title: '删除文件',
    content: `确定删除「${entry.path}」吗？`,
    onConfirm: async () => {
      await apiWorkspace.delete(entry.path)
      if (selectedPath.value === entry.path) {
        selectedPath.value = ''
      }
      toast.success('已删除')
      await refresh()
    },
  })
}
function confirmPublish() {
  modal.confirm({
    title: '发布站点',
    content: '先运行完整性校验，通过后整树快照为发布版本并同步站点页面。确认发布？',
    onConfirm: async () => {
      const res = await apiWorkspace.publish()
      toast.success(`已发布：${res.data.files} 个文件、${res.data.pages} 个页面`)
      previewKey.value++
    },
  })
}
async function openPreview(entry: WorkspaceEntry) {
  const cssRes = await apiWorkspace.read('assets/css/site.css').then(res => res.data).catch(() => null)
  const htmlRes = await apiWorkspace.read(entry.path).then(res => res.data).catch(() => null)
  if (!htmlRes) {
    toast.warning('页面不存在')
    return
  }
  let html = htmlRes.encoding === 'text' ? htmlRes.content : ''
  const css = cssRes && cssRes.encoding === 'text' ? cssRes.content : ''
  if (css) {
    html = html.replace(/<\/head>/i, `<style>${css.replace(/<\/style/gi, '')}</style></head>`)
  }
  // 预览内联二进制资产：引用转 data URL，绕过 iframe 认证问题
  const assetPattern = /(?:src|href)="(assets\/[^"]+)"/g
  const referenced = new Set<string>()
  let match = assetPattern.exec(html)
  while (match) {
    referenced.add(match[1])
    match = assetPattern.exec(html)
  }
  for (const ref of referenced) {
    try {
      const res = await apiWorkspace.read(ref)
      const data = res.data.encoding === 'base64' ? res.data.content : btoa(unescape(encodeURIComponent(res.data.content)))
      html = html.split(`"${ref}"`).join(`"data:${mimeOf(ref)};base64,${data}"`)
    }
    catch {
      /* 缺失资产保留原引用 */
    }
  }
  previewDoc.value = html
  previewVisible.value = true
}
function mimeOf(path: string) {
  const lower = path.toLowerCase()
  if (lower.endsWith('.png')) {
    return 'image/png'
  }
  if (lower.endsWith('.gif')) {
    return 'image/gif'
  }
  if (lower.endsWith('.webp')) {
    return 'image/webp'
  }
  if (lower.endsWith('.svg')) {
    return 'image/svg+xml'
  }
  if (lower.endsWith('.mp4')) {
    return 'video/mp4'
  }
  if (lower.endsWith('.webm')) {
    return 'video/webm'
  }
  return 'image/jpeg'
}
async function openCanvas(entry: WorkspaceEntry) {
  selectedPath.value = entry.path
  const [html, css, project] = await Promise.all([
    apiWorkspace.read(entry.path).then(res => (res.data.encoding === 'text' ? res.data.content : '')).catch(() => ''),
    apiWorkspace.read('assets/css/site.css').then(res => (res.data.encoding === 'text' ? res.data.content : '')).catch(() => ''),
    apiWorkspace.read(entry.path.replace(/\.html$/, '.grapes.json')).then(res => (res.data.encoding === 'text' ? res.data.content : '')).catch(() => ''),
  ])
  canvasHtml.value = html
  canvasCss.value = css
  canvasJs.value = ''
  canvasProjectJson.value = project || ''
  canvasVisible.value = true
}

/** @文件引用：抽屉内 @ 触发工作区文本文件联想，选中后内容注入本轮提示。 */
const pendingRefs = ref<string[]>([])
const workspaceFiles = computed(() =>
  entries.value
    .filter(entry => entry.path.endsWith('.html') || /\.(?:css|js|json|md|svg|txt)$/i.test(entry.path))
    .filter(entry => entry.path !== selectedPath.value)
    .map(entry => ({
      path: entry.path,
      included: pendingRefs.value.includes(entry.path),
    })),
)
function toggleReference(path: string, included: boolean) {
  pendingRefs.value = included ? [...new Set([...pendingRefs.value, path])] : pendingRefs.value.filter(item => item !== path)
}
async function ask(request: YdAgentSendRequest) {
  if (task.running.value || preparing.value) {
    return
  }
  preparing.value = true
  const started = runtime.lifecycleVersion()
  try {
    let full = request.text
    for (const refPath of pendingRefs.value) {
      try {
        const res = await apiWorkspace.read(refPath)
        if (res.data.encoding === 'text') {
          full = `【引用文件 ${refPath}】\n${res.data.content}\n\n${full}`
        }
      }
      catch {
        /* 引用读取失败忽略该文件 */
      }
    }
    pendingRefs.value = []
    if (started !== runtime.lifecycleVersion()) {
      return
    }
    runtime.appendTaskInput(`cms-${targetKey}`, full)
    await task.start(() => apiWorkspace.startChat(targetKey, full))
  }
  finally {
    preparing.value = false
  }
}
async function onCanvasSave(payload: { htmlContent: string, cssContent: string, jsContent: string, builderProjectJson: string }) {
  const htmlPath = canvasFilePath.value
  await apiWorkspace.write(htmlPath, payload.htmlContent)
  await apiWorkspace.write('assets/css/site.css', payload.cssContent)
  if (payload.jsContent) {
    await apiWorkspace.write('assets/js/site.js', payload.jsContent)
  }
  await apiWorkspace.write(htmlPath.replace(/\.html$/, '.grapes.json'), payload.builderProjectJson)
  toast.success('已保存到工作区')
  canvasVisible.value = false
  previewKey.value++
  await refresh()
}
function openChat() {
  chatOpen.value = true
}
function formatSize(size: number) {
  const value = Number(size || 0)
  return value < 1024 ? `${value} B` : `${(value / 1024).toFixed(1)} KB`
}
function isHtmlPath(path: string) {
  return path.endsWith('.html')
}
function fileName(path: string) {
  return path.split('/').pop() || path
}
</script>

<template>
  <div>
    <YdPageHeader title="CMS 站点工作区" class="mb-0">
      <template #right>
        <YdButton size="sm" variant="outline" @click="createPage">
          新建页面
        </YdButton>
        <YdButton size="sm" variant="outline" @click="pickUpload">
          上传资产
        </YdButton>
        <YdButton size="sm" variant="outline" @click="openChat">
          AI 构建
        </YdButton>
        <YdButton size="sm" @click="confirmPublish">
          发布站点
        </YdButton>
      </template>
    </YdPageHeader>
    <YdPageMain>
      <div class="ws-section">
        <div class="ws-section__head">
          <div>
            <h3>站点文件</h3>
            <p>pages/ 下每页一个 HTML；assets/ 存放样式、脚本与图片视频。页面通过 data-yb-* 与模板变量接入站点数据，发布前会自动做完整性校验。</p>
          </div>
          <YdButton size="sm" variant="ghost" @click="refresh">
            <YdIcon name="morph:refresh-cw" /> 刷新
          </YdButton>
        </div>
        <YdLoadingOverlay :loading="loading">
          <YdResponsiveTable

            row-key="path"
            :data="entries"
            :columns="[
              { accessorKey: 'path', header: '文件路径' },
              { accessorKey: 'size', header: '大小', width: 110 },
              { accessorKey: 'updatedAt', header: '更新时间', width: 180 },
              { id: 'operation', header: '操作', width: 300 },
            ]"
          >
            <template #cell-path="{ row }">
              <span class="min-w-0 break-all">{{ fileName(row.original.path) }}</span>
            </template>
            <template #cell-size="{ row }">
              {{ formatSize(Number(row.original.size || 0)) }}
            </template>
            <template #cell-operation="{ row }">
              <div class="flex gap-2 justify-center">
                <template v-if="isHtmlPath(row.original.path)">
                  <YdButton size="sm" variant="outline" @click="openEdit(row.original.path)">
                    编辑
                  </YdButton>
                  <YdButton size="sm" variant="outline" @click="openCanvas(row.original)">
                    画布
                  </YdButton>
                </template>
                <YdButton size="sm" variant="outline" @click="openPreview(row.original)">
                  预览
                </YdButton>
                <YdButton size="sm" variant="destructive" @click="deleteEntry(row.original)">
                  删除
                </YdButton>
              </div>
            </template>
            <template #card="{ row }">
              <YdCard class="w-full">
                <div class="flex flex-col gap-3">
                  <span class="text-sm font-medium min-w-0 break-all">{{ row.path }}</span>
                  <div class="pt-3 border-t flex flex-wrap gap-2">
                    <template v-if="isHtmlPath(row.path)">
                      <YdButton size="sm" variant="outline" @click="openEdit(row.path)">
                        编辑
                      </YdButton>
                      <YdButton size="sm" variant="outline" @click="openCanvas(row)">
                        画布
                      </YdButton>
                    </template>
                    <YdButton size="sm" variant="outline" @click="openPreview(row)">
                      预览
                    </YdButton>
                    <YdButton size="sm" variant="destructive" @click="deleteEntry(row)">
                      删除
                    </YdButton>
                  </div>
                </div>
              </YdCard>
            </template>
          </YdResponsiveTable>
        </YdLoadingOverlay>
      </div>
    </YdPageMain>

    <YdFilePicker ref="uploadInput" label="选择资产文件" @select="onUploadChange">
      <template #default />
    </YdFilePicker>

    <YdModal v-model="editVisible" :title="`编辑：${editPath}`" class="sm:max-w-5xl">
      <YdTextarea v-model="editContent" class="w-full" input-class="font-mono" :autosize="false" :rows="22" />
      <template #footer>
        <YdButton variant="outline" @click="editVisible = false">
          取消
        </YdButton>
        <YdButton @click="saveEdit">
          保存
        </YdButton>
      </template>
    </YdModal>

    <YdModal v-model="createVisible" title="新建页面">
      <YdInput v-model="createPath" placeholder="pages/about/index.html" aria-label="页面文件路径" @keydown.enter="submitNewPage" />
      <template #footer>
        <YdButton variant="outline" @click="createVisible = false">
          取消
        </YdButton>
        <YdButton @click="submitNewPage">
          创建
        </YdButton>
      </template>
    </YdModal>

    <YdModal v-model="previewVisible" title="页面预览" class="sm:max-w-6xl">
      <iframe :srcdoc="previewDoc" class="preview-frame" title="页面预览" />
    </YdModal>

    <div v-if="canvasVisible" class="canvas-overlay">
      <CmsGrapesEditor
        :title="`画布编辑：${canvasFilePath}`"
        :html-content="canvasHtml"
        :css-content="canvasCss"
        :js-content="canvasJs"
        :builder-project-json="canvasProjectJson"
        page-template="DEFAULT"
        history-target-type="page"
        :history-target-id="canvasFilePath"
        :history-target-label="canvasFilePath"
        @close="canvasVisible = false"
        @save="onCanvasSave"
      />
    </div>

    <YdDrawer v-model="chatOpen" title="AI 构建助手" :footer="false" content-class="w-[760px] max-w-full">
      <div class="chat-drawer__body">
        <YdAlert v-if="task.failure.value" variant="destructive" :title="task.failure.value" />
        <YdAgentWorkbench :messages="messages" :harness="harness" :available-modes="['ask']" :workspaces="workspaces" :workspace-id="`cms-${targetKey}`" :files="workspaceFiles" class="workspace-agent-workbench" @send="ask" @stop="task.stop" @file-toggle="toggleReference" @file-select="file => openEdit(file.path)">
          <template #empty>
            <div class="chat-empty">
              <strong>描述你想要的页面</strong>
              <p>例如：给首页加一个三列特性区块，样式用 yb-ai- 前缀</p>
            </div>
          </template>
          <template #composer-before>
            <AiRunProgress v-if="task.run.value?.status === 'WAITING'" :run-id="task.run.value.id" />
          </template>
        </YdAgentWorkbench>
      </div>
    </YdDrawer>
  </div>
</template>

<style scoped>
.workspace-agent-workbench {
  height: min(760px, 82vh);
}

.ws-section {
  display: grid;
  gap: 16px;
}

.ws-section__head {
  display: flex;
  gap: 16px;
  align-items: end;
  justify-content: space-between;
}

.ws-section__head h3,
.ws-section__head p {
  margin: 0;
}

.ws-section__head p {
  margin-top: 6px;
  font-size: 13px;
  color: var(--color-text-3);
}

.preview-frame {
  width: 100%;
  height: 68vh;
  background: #fff;
  border: 1px solid var(--color-border-2);
}

.canvas-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: var(--color-bg-1);
}

.chat-drawer__body {
  min-width: 0;
  min-height: 0;
}

.chat-empty {
  display: grid;
  gap: 6px;
  justify-items: center;
  max-width: 320px;
  padding: 32px 12px;
  font-size: 12px;
  color: var(--color-text-3);
  text-align: center;
}

.chat-empty strong {
  color: var(--color-text-1);
}
</style>
