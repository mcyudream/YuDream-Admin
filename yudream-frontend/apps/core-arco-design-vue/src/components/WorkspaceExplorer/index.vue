<script setup lang="ts">
import type { YdChatAttachment, YdFilePickerInstance } from '@yudream/components'
import type { ChatWorkspaceEntry, ChatWorkspaceFileContent, ChatWorkspaceOverview, ChatWorkspaceTrashItem, ChatWorkspaceVersion } from '@/api/modules/chat-workspace'
import type { WorkspaceTreeNode } from '@/utils/workspace-tree'
import { useYdToast, YdAlert, YdButton, YdCodeEditor, YdEmpty, YdFilePicker, YdIcon, YdInput, YdModal, YdProgress, YdTabs, YdTag, YdTree } from '@yudream/components'
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import apiWorkspace from '@/api/modules/chat-workspace'
import { buildWorkspaceTree, formatWorkspaceBytes, workspaceEditorLanguage, workspaceIsImage, workspaceJoin, workspaceName, workspaceNameError, workspaceParent } from '@/utils/workspace-tree'

/**
 * 我的工作区文件管理器：目录树 + 预览/编辑 + 新建、上传、移动改名、删除（回收站）、历史版本，
 * 以及把对话附件保存到当前目录。元数据与内容都经工作区端点，AI 写入的文件会标出来源。
 */
const props = withDefaults(defineProps<{
  canEdit?: boolean
  /** 当前对话中的附件（有 fileId 的可保存到工作区） */
  attachments?: YdChatAttachment[]
  zIndex?: number
}>(), { canEdit: false, attachments: () => [], zIndex: 2100 })
const emit = defineEmits<{ changed: [] }>()
const open = defineModel<boolean>({ default: false })

type View = 'files' | 'trash' | 'attachments'
type PromptMode = 'file' | 'folder' | 'move'

const toast = useYdToast()
const overview = ref<ChatWorkspaceOverview | null>(null)
const loading = ref(false)
const busy = ref(false)
const view = ref<View>('files')
/** 当前打开的条目路径（树只做单向展示：YdTree 在发出选择事件前已改写自身选中值） */
const openPath = ref('')
const expandedKeys = ref<string[]>([])
const content = ref<ChatWorkspaceFileContent | null>(null)
const contentLoading = ref(false)
const draft = ref('')
const imageUrl = ref('')
const versionsOpen = ref(false)
const versions = ref<ChatWorkspaceVersion[]>([])
const trash = ref<ChatWorkspaceTrashItem[]>([])
const uploadInput = ref<YdFilePickerInstance | null>(null)
const prompt = ref<{ open: boolean, mode: PromptMode, value: string }>({ open: false, mode: 'file', value: '' })
/** 确认框放在管理器自己的层级之上（全局确认框会被管理器遮住） */
const confirmation = ref<{ open: boolean, title: string, content: string, confirmText: string, resolve?: (ok: boolean) => void }>({ open: false, title: '', content: '', confirmText: '' })

const entries = computed(() => overview.value?.entries ?? [])
const tree = computed(() => buildWorkspaceTree(entries.value))
const selected = computed<ChatWorkspaceEntry | null>(() => entries.value.find(entry => entry.path === openPath.value) ?? null)
const currentDirectory = computed(() => !selected.value ? '' : selected.value.kind === 'DIRECTORY' ? selected.value.path : workspaceParent(selected.value.path))
const editable = computed(() => props.canEdit && content.value?.encoding === 'text' && !content.value.truncated)
const dirty = computed(() => editable.value && draft.value !== (content.value?.content ?? ''))
const usage = computed(() => {
  const data = overview.value
  if (!data) {
    return null
  }
  const percent = data.quotaBytes ? Math.min(100, Math.round(data.usedBytes / data.quotaBytes * 100)) : 0
  return { percent, text: `已用 ${formatWorkspaceBytes(data.usedBytes)}${data.quotaBytes ? ` / ${formatWorkspaceBytes(data.quotaBytes)}` : '（不限）'}` }
})
const savableAttachments = computed(() => {
  const seen = new Set<string>()
  return props.attachments.filter(item => item.fileId && !seen.has(item.fileId) && seen.add(item.fileId))
})
const tabs = computed(() => [
  { label: '文件', value: 'files', icon: 'morph:folder' },
  ...(savableAttachments.value.length ? [{ label: `对话附件 ${savableAttachments.value.length}`, value: 'attachments', icon: 'morph:paperclip' }] : []),
  { label: '回收站', value: 'trash', icon: 'morph:trash-2' },
])
const promptTitle = computed(() => ({ file: '新建文件', folder: '新建文件夹', move: '移动或重命名' })[prompt.value.mode])
const promptError = computed(() => {
  const value = prompt.value.value.trim()
  if (prompt.value.mode === 'move') {
    return value ? value.split('/').map(workspaceNameError).find(Boolean) ?? '' : '路径不能为空'
  }
  return workspaceNameError(value) || (value.includes('/') ? '名称不能包含 /，请先选中目标文件夹' : '')
})

async function run<T>(action: () => Promise<T>) {
  busy.value = true
  try {
    return await action()
  }
  catch {
    // 请求错误已由全局提示展示
    return undefined
  }
  finally {
    busy.value = false
  }
}

async function load() {
  loading.value = true
  try {
    overview.value = await apiWorkspace.overview()
    if (openPath.value && !entries.value.some(entry => entry.path === openPath.value)) {
      clearSelection()
    }
  }
  catch {
    overview.value = null
  }
  finally {
    loading.value = false
  }
}

async function loadTrash() {
  trash.value = (await run(() => apiWorkspace.trash())) ?? []
}

function revokeImage() {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = ''
  }
}

function clearSelection() {
  openPath.value = ''
  content.value = null
  draft.value = ''
  versionsOpen.value = false
  revokeImage()
}

function ask(title: string, body: string, confirmText: string) {
  confirmation.value.resolve?.(false)
  return new Promise<boolean>((resolve) => {
    confirmation.value = { open: true, title, content: body, confirmText, resolve }
  })
}

function answer(ok: boolean) {
  const resolve = confirmation.value.resolve
  confirmation.value = { ...confirmation.value, open: false, resolve: undefined }
  resolve?.(ok)
}

/** 弹窗先回写关闭、再发出 confirm：关闭（取消、遮罩、×）延后一拍按「否」处理，确认已先应答时为空操作 */
function dismissConfirmation() {
  setTimeout(answer, 0, false)
}

function confirmDiscard() {
  return dirty.value ? ask('放弃未保存的修改？', `「${content.value?.path}」有未保存的修改。`, '放弃修改') : Promise.resolve(true)
}

async function openEntry(entry: ChatWorkspaceEntry) {
  openPath.value = entry.path
  content.value = null
  draft.value = ''
  versionsOpen.value = false
  revokeImage()
  if (entry.kind === 'DIRECTORY') {
    if (!expandedKeys.value.includes(entry.path)) {
      expandedKeys.value = [...expandedKeys.value, entry.path]
    }
    return
  }
  contentLoading.value = true
  try {
    const result = await apiWorkspace.read(entry.path)
    if (openPath.value !== entry.path) {
      return
    }
    content.value = result
    draft.value = result.content ?? ''
    if (result.encoding === 'binary' && workspaceIsImage(entry) && entry.size <= 20 * 1024 * 1024) {
      const blob = await apiWorkspace.download(entry.path)
      imageUrl.value = URL.createObjectURL(new Blob([blob], { type: entry.contentType || 'image/png' }))
    }
  }
  catch {
    content.value = null
  }
  finally {
    contentLoading.value = false
  }
}

async function onSelect(node: WorkspaceTreeNode | null) {
  // 再次点击已打开的节点（树会发出 null）保持打开；有未保存修改时先确认
  if (!node || node.entry.path === openPath.value || !(await confirmDiscard())) {
    return
  }
  await openEntry(node.entry)
}

async function save() {
  const file = content.value
  if (!file || !dirty.value) {
    return
  }
  const saved = await run(() => apiWorkspace.save(file.path, draft.value, file.revision))
  if (saved) {
    content.value = { ...file, content: draft.value, revision: saved.revision, size: saved.size }
    toast.success('已保存', { description: `${file.path} · 第 ${saved.revision} 版` })
    await load()
    emit('changed')
  }
}

async function download(entry: ChatWorkspaceEntry) {
  const blob = await run(() => apiWorkspace.download(entry.path))
  if (!blob) {
    return
  }
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = workspaceName(entry.path)
  link.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function startPrompt(mode: PromptMode) {
  prompt.value = { open: true, mode, value: mode === 'move' ? selected.value?.path ?? '' : '' }
}

async function submitPrompt() {
  if (promptError.value) {
    return
  }
  const { mode } = prompt.value
  const value = prompt.value.value.trim().replace(/^\/+|\/+$/g, '')
  prompt.value.open = false
  if (mode === 'move') {
    const source = selected.value
    if (!source || value === source.path || !(await confirmDiscard())) {
      return
    }
    const moved = await run(() => apiWorkspace.move(source.path, value))
    if (moved) {
      toast.success('已移动', { description: `${source.path} → ${moved.path}` })
      await load()
      await openEntry(moved)
      emit('changed')
    }
    return
  }
  const path = workspaceJoin(currentDirectory.value, value)
  const created = await run(() => mode === 'file' ? apiWorkspace.save(path, '', 0) : apiWorkspace.createDirectory(path))
  if (created) {
    await load()
    if (currentDirectory.value && !expandedKeys.value.includes(currentDirectory.value)) {
      expandedKeys.value = [...expandedKeys.value, currentDirectory.value]
    }
    await openEntry(created)
    emit('changed')
  }
}

async function onUpload(files: File[]) {
  uploadInput.value?.reset()
  if (!files.length) {
    return
  }
  const directory = currentDirectory.value
  let uploaded = 0
  await run(async () => {
    for (const file of files) {
      await apiWorkspace.upload(directory, file)
      uploaded++
    }
  })
  if (uploaded) {
    toast.success(`已上传 ${uploaded} 个文件`, { description: directory ? `到 ${directory}/` : '到工作区根目录' })
    await load()
    emit('changed')
  }
}

async function remove(entry: ChatWorkspaceEntry) {
  const folder = entry.kind === 'DIRECTORY'
  if (!(await ask(folder ? '删除文件夹？' : '删除文件？', `「${entry.path}」${folder ? '及其中的全部内容' : ''}将移入回收站，${overview.value?.trashRetentionDays ?? 30} 天内可以恢复。`, '移入回收站'))) {
    return
  }
  const count = await run(() => apiWorkspace.remove(entry.path))
  if (count !== undefined) {
    toast.success('已移入回收站', { description: count > 1 ? `共 ${count} 项` : entry.path })
    clearSelection()
    await load()
    emit('changed')
  }
}

async function toggleVersions() {
  versionsOpen.value = !versionsOpen.value
  if (versionsOpen.value && content.value) {
    const path = content.value.path
    versions.value = (await run(() => apiWorkspace.versions(path))) ?? []
  }
}

async function restoreVersion(version: ChatWorkspaceVersion) {
  const file = selected.value
  if (!file || !(await ask(`恢复到第 ${version.revision} 版？`, '当前内容会保留为一个历史版本，可以再切回来。', '恢复')) || !(await confirmDiscard())) {
    return
  }
  const restored = await run(() => apiWorkspace.restoreVersion(file.path, version.id))
  if (restored) {
    toast.success('已恢复', { description: `${file.path} 现为第 ${restored.revision} 版` })
    await load()
    await openEntry(restored)
    emit('changed')
  }
}

async function restoreTrash(item: ChatWorkspaceTrashItem) {
  const restored = await run(() => apiWorkspace.restore(item.id))
  if (restored) {
    toast.success('已恢复', { description: restored.path === item.path ? restored.path : `原位置已被占用，恢复为 ${restored.path}` })
    await Promise.all([load(), loadTrash()])
    emit('changed')
  }
}

async function purgeTrash(item?: ChatWorkspaceTrashItem) {
  const confirmed = await ask(item ? '彻底删除？' : '清空回收站？', item ? `「${item.path}」及其历史版本将被永久删除，无法恢复。` : '回收站中的全部内容及其历史版本将被永久删除，无法恢复。', '永久删除')
  if (!confirmed) {
    return
  }
  await run(async () => {
    if (item) {
      await apiWorkspace.purge(item.id)
    }
    else {
      await apiWorkspace.emptyTrash()
    }
  })
  await Promise.all([load(), loadTrash()])
}

async function saveAttachment(attachment: YdChatAttachment) {
  const directory = currentDirectory.value
  const saved = await run(() => apiWorkspace.saveAttachment(attachment.fileId!, directory))
  if (saved) {
    toast.success('已保存到工作区', { description: saved.path })
    await load()
    emit('changed')
  }
}

/** 打开期间 Ctrl/⌘ + S 保存当前文件 */
function onKeydown(event: KeyboardEvent) {
  if (dirty.value && (event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    void save()
  }
}

function formatTime(value: string) {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

async function close() {
  if (await confirmDiscard()) {
    open.value = false
  }
}

watch(open, (visible) => {
  if (visible) {
    view.value = 'files'
    window.addEventListener('keydown', onKeydown)
    void load()
  }
  else {
    window.removeEventListener('keydown', onKeydown)
    clearSelection()
  }
}, { immediate: true })
watch(view, (value) => {
  if (value === 'trash') {
    void loadTrash()
  }
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  revokeImage()
})
</script>

<template>
  <YdModal
    :model-value="open"
    title="我的工作区"
    :z-index="zIndex"
    :footer="false"
    maximizable
    class="workspace-explorer-modal sm:max-w-6xl"
    @update:model-value="value => { if (!value) void close() }"
  >
    <div class="workspace-explorer">
      <header class="workspace-explorer__bar">
        <YdTabs v-model="view" :list="tabs" class="workspace-explorer__tabs" />
        <div v-if="usage" class="workspace-explorer__usage" :title="overview?.uri">
          <span>{{ usage.text }}</span>
          <YdProgress v-if="overview?.quotaBytes" :model-value="usage.percent" class="workspace-explorer__meter" />
        </div>
      </header>

      <div v-if="view === 'files'" class="workspace-explorer__body">
        <aside class="workspace-explorer__tree" aria-label="工作区目录">
          <div class="workspace-explorer__actions">
            <YdButton size="sm" variant="ghost" :loading="loading" aria-label="刷新" @click="load">
              <YdIcon name="morph:refresh-cw" />
            </YdButton>
            <template v-if="canEdit">
              <YdButton size="sm" variant="ghost" :disabled="busy" @click="startPrompt('file')">
                <YdIcon name="morph:file-plus" /> 文件
              </YdButton>
              <YdButton size="sm" variant="ghost" :disabled="busy" @click="startPrompt('folder')">
                <YdIcon name="morph:folder-plus" /> 文件夹
              </YdButton>
              <YdButton size="sm" variant="ghost" :disabled="busy" @click="uploadInput?.open()">
                <YdIcon name="morph:upload" /> 上传
              </YdButton>
            </template>
          </div>
          <p class="workspace-explorer__hint">
            {{ canEdit ? `新建与上传到：${currentDirectory ? `${currentDirectory}/` : '根目录'}` : '只读：你没有编辑工作区的权限' }}
          </p>
          <div v-if="tree.length" class="workspace-explorer__tree-list">
            <YdTree
              v-model:expanded-keys="expandedKeys"
              :selected-keys="openPath ? [openPath] : []"
              :data="tree"
              filterable
              filter-placeholder="搜索文件"
              class="workspace-explorer__tree-view"
              @selection-change="node => onSelect(node as WorkspaceTreeNode | null)"
            >
              <template #default="{ node, expanded }">
                <span class="workspace-explorer__node">
                  <YdIcon :name="(node as WorkspaceTreeNode).entry.kind === 'DIRECTORY' ? (expanded ? 'morph:folder-open' : 'morph:folder') : 'morph:file-text'" />
                  <span class="workspace-explorer__node-name">{{ (node as WorkspaceTreeNode).label }}</span>
                  <YdTag v-if="(node as WorkspaceTreeNode).entry.updatedBy === 'AI'" variant="secondary" class="workspace-explorer__ai">
                    AI
                  </YdTag>
                </span>
              </template>
            </YdTree>
          </div>
          <YdEmpty v-else-if="!loading" icon="morph:folder-open" title="工作区还是空的" :description="canEdit ? '新建文件、上传，或让 AI 在编码模式中创建。' : '还没有文件。'" />
        </aside>

        <section class="workspace-explorer__pane" aria-live="polite">
          <YdEmpty v-if="!selected" icon="morph:mouse-pointer-click" title="选择一个文件" description="在左侧打开文件即可预览或编辑；AI 可在你授权的模式中读写这些文件。" />
          <template v-else>
            <div class="workspace-explorer__pane-head">
              <div class="workspace-explorer__title">
                <strong>{{ selected.path }}</strong>
                <span class="workspace-explorer__meta">
                  <template v-if="selected.kind === 'FILE'">{{ formatWorkspaceBytes(selected.size) }} · 第 {{ selected.revision }} 版 · </template>
                  {{ selected.updatedBy === 'AI' ? 'AI 修改于' : '更新于' }} {{ formatTime(selected.updatedAt) }}
                </span>
              </div>
              <div class="workspace-explorer__pane-actions">
                <YdButton v-if="editable" size="sm" :disabled="!dirty || busy" @click="save">
                  <YdIcon name="morph:save" /> 保存
                </YdButton>
                <YdButton v-if="selected.kind === 'FILE'" size="sm" variant="outline" @click="download(selected)">
                  <YdIcon name="morph:download" /> 下载
                </YdButton>
                <YdButton v-if="selected.kind === 'FILE'" size="sm" variant="outline" :aria-pressed="versionsOpen" @click="toggleVersions">
                  <YdIcon name="morph:history" /> 历史
                </YdButton>
                <template v-if="canEdit">
                  <YdButton size="sm" variant="outline" :disabled="busy" @click="startPrompt('move')">
                    <YdIcon name="morph:folder-input" /> 移动/改名
                  </YdButton>
                  <YdButton size="sm" variant="destructive" :disabled="busy" @click="remove(selected)">
                    <YdIcon name="morph:trash-2" /> 删除
                  </YdButton>
                </template>
              </div>
            </div>

            <div v-if="versionsOpen" class="workspace-explorer__versions">
              <div v-for="version in versions" :key="version.id" class="workspace-explorer__row">
                <span>第 {{ version.revision }} 版 · {{ formatWorkspaceBytes(version.size) }} · {{ version.actor === 'AI' ? 'AI' : '我' }} · {{ formatTime(version.createdAt) }}</span>
                <YdTag v-if="version.current" variant="secondary">
                  当前
                </YdTag>
                <YdButton v-else-if="canEdit" size="sm" variant="ghost" :disabled="busy" @click="restoreVersion(version)">
                  恢复此版本
                </YdButton>
              </div>
            </div>

            <div v-if="selected.kind === 'DIRECTORY'" class="workspace-explorer__folder">
              <p>文件夹内 {{ entries.filter(entry => entry.path.startsWith(`${selected!.path}/`)).length }} 项。新建或上传的文件会放在这里。</p>
            </div>
            <div v-else-if="contentLoading" class="workspace-explorer__folder">
              <p>正在读取…</p>
            </div>
            <template v-else-if="content">
              <YdAlert v-if="content.truncated" title="文件较大，只显示开头部分，不能在这里编辑；可下载后查看完整内容。" />
              <div v-if="content.encoding === 'text'" class="workspace-explorer__editor">
                <YdCodeEditor v-model="draft" :language="workspaceEditorLanguage(content.path)" :readonly="!editable" height="var(--workspace-editor-height)" :aria-label="`编辑 ${content.path}`" />
              </div>
              <div v-else-if="imageUrl" class="workspace-explorer__image">
                <img :src="imageUrl" :alt="selected.path">
              </div>
              <div v-else class="workspace-explorer__folder">
                <p>{{ content.contentType || '二进制文件' }}，{{ formatWorkspaceBytes(content.size) }}。这类文件不在这里预览，可以下载查看。</p>
              </div>
            </template>
          </template>
        </section>
      </div>

      <section v-else-if="view === 'attachments'" class="workspace-explorer__list" aria-label="对话附件">
        <p class="workspace-explorer__hint">
          保存到：{{ currentDirectory ? `${currentDirectory}/` : '工作区根目录' }}（在「文件」中选中文件夹可改变位置）
        </p>
        <div v-for="item in savableAttachments" :key="item.fileId" class="workspace-explorer__row">
          <span><YdIcon name="morph:paperclip" /> {{ item.fileName }}<template v-if="item.size"> · {{ formatWorkspaceBytes(Number(item.size)) }}</template></span>
          <YdButton size="sm" variant="outline" :disabled="!canEdit || busy" @click="saveAttachment(item)">
            保存到工作区
          </YdButton>
        </div>
      </section>

      <section v-else class="workspace-explorer__list" aria-label="回收站">
        <div class="workspace-explorer__row workspace-explorer__row--head">
          <span>删除的文件保留 {{ overview?.trashRetentionDays ?? 30 }} 天，tmp/ 中的文件 {{ overview?.tmpRetentionDays ?? 7 }} 天未修改会自动清理。</span>
          <YdButton v-if="canEdit && trash.length" size="sm" variant="destructive" :disabled="busy" @click="purgeTrash()">
            清空回收站
          </YdButton>
        </div>
        <YdEmpty v-if="!trash.length" icon="morph:trash-2" title="回收站是空的" />
        <div v-for="item in trash" :key="item.id" class="workspace-explorer__row">
          <span>
            <YdIcon :name="item.kind === 'DIRECTORY' ? 'morph:folder' : 'morph:file-text'" />
            {{ item.path }}<template v-if="item.childCount"> 等 {{ item.childCount + 1 }} 项</template> · {{ formatWorkspaceBytes(item.size) }} · {{ formatTime(item.expiresAt) }} 后清除
          </span>
          <span v-if="canEdit" class="workspace-explorer__pane-actions">
            <YdButton size="sm" variant="outline" :disabled="busy" @click="restoreTrash(item)">
              恢复
            </YdButton>
            <YdButton size="sm" variant="ghost" :disabled="busy" @click="purgeTrash(item)">
              彻底删除
            </YdButton>
          </span>
        </div>
      </section>
    </div>

    <YdFilePicker ref="uploadInput" label="选择上传到工作区的文件" multiple class="hidden" @select="onUpload">
      <template #default />
    </YdFilePicker>
    <YdModal :model-value="confirmation.open" :title="confirmation.title" show-cancel-button :confirm-button-text="confirmation.confirmText" :z-index="zIndex + 200" @update:model-value="value => { if (!value) dismissConfirmation() }" @confirm="answer(true)">
      <p>{{ confirmation.content }}</p>
    </YdModal>
    <YdModal v-model="prompt.open" :title="promptTitle" show-cancel-button :z-index="zIndex + 100" :confirm-button-disabled="!!promptError" @confirm="submitPrompt">
      <YdInput v-model="prompt.value" :aria-label="promptTitle" :maxlength="240" :placeholder="prompt.mode === 'move' ? '新的完整路径，如 archive/notes.md' : '名称'" @keydown.enter="submitPrompt" />
      <p class="workspace-explorer__hint">
        {{ promptError || (prompt.mode === 'move' ? '改名只改最后一段；改前面的目录即可移动。' : `位置：${currentDirectory ? `${currentDirectory}/` : '根目录'}`) }}
      </p>
    </YdModal>
  </YdModal>
</template>

<style scoped>
.workspace-explorer {
  --workspace-editor-height: calc(min(72vh, 760px) - 170px);

  display: flex;
  flex-direction: column;
  gap: 12px;
  height: min(72vh, 760px);
  min-height: 420px;
}

.workspace-explorer__bar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
  align-items: center;
  justify-content: space-between;
}

.workspace-explorer__usage {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.workspace-explorer__meter {
  width: 120px;
}

.workspace-explorer__body {
  display: grid;
  flex: 1;
  grid-template-columns: minmax(220px, 300px) minmax(0, 1fr);
  gap: 12px;
  min-height: 0;
}

.workspace-explorer__tree,
.workspace-explorer__pane {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
  padding: 10px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.workspace-explorer__tree-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.workspace-explorer__tree-view {
  align-content: start;
}

.workspace-explorer__actions,
.workspace-explorer__pane-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  align-items: center;
}

.workspace-explorer__hint {
  margin: 0;
  font-size: 12px;
  color: var(--color-text-3);
  overflow-wrap: anywhere;
}

.workspace-explorer__node {
  display: inline-flex;
  gap: 6px;
  align-items: center;
  min-width: 0;
}

.workspace-explorer__node-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-explorer__ai {
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
}

.workspace-explorer__pane-head {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: flex-start;
  justify-content: space-between;
}

.workspace-explorer__title {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.workspace-explorer__title strong {
  overflow-wrap: anywhere;
}

.workspace-explorer__meta {
  font-size: 12px;
  color: var(--color-text-3);
}

.workspace-explorer__editor {
  flex: 1;
  min-height: 240px;
}

.workspace-explorer__image {
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: auto;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.workspace-explorer__image img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.workspace-explorer__folder {
  padding: 16px;
  color: var(--color-text-3);
}

.workspace-explorer__versions,
.workspace-explorer__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow: auto;
}

.workspace-explorer__versions {
  max-height: 180px;
  padding: 6px;
  background: var(--color-fill-1);
  border-radius: 8px;
}

.workspace-explorer__list {
  flex: 1;
  min-height: 0;
}

.workspace-explorer__row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  font-size: 13px;
  border-bottom: 1px solid var(--color-border-2);
}

.workspace-explorer__row > span:first-child {
  min-width: 0;
  overflow-wrap: anywhere;
}

.workspace-explorer__row--head {
  color: var(--color-text-3);
  border-bottom: none;
}

@media (width <= 768px) {
  .workspace-explorer {
    height: auto;
    min-height: 0;
  }

  .workspace-explorer__body {
    grid-template-columns: 1fr;
  }

  .workspace-explorer__tree {
    max-height: 40vh;
  }

  .workspace-explorer__editor {
    --workspace-editor-height: 50vh;
  }
}
</style>
