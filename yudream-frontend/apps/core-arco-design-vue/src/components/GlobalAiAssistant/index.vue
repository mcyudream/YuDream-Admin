<script setup lang="ts">
import type { MenuRecordRaw } from '@fantastic-admin/types'
import type { YdAgentSendRequest, YdAgentSession, YdAgentWorkspace, YdFilePickerInstance } from '@yudream/components'
import type { ChatWorkspaceEntry } from '@/api/modules/chat-workspace'
import type { AiFrontendRoute, AiFrontendToolRequest } from '@/api/modules/platform-ai-runs'
import { clearYdPersistentChatOwner, YdAgentWorkbench, YdAlert, YdButton, YdChatWindow, YdDrawer, YdFilePicker, YdFloatBall, YdForm, YdFormItem, YdIcon, YdInput, YdModal, YdNumberField, YdSelect, YdSwitch, YdTooltip } from '@yudream/components'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import apiChatWorkspace from '@/api/modules/chat-workspace'
import { fetchChatQuotaConfig, updateChatQuotaConfig } from '@/api/modules/platform-chat'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { useAiAssistantActions } from '@/composables/ai-assistant-actions'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { useAiSystemRunsStore } from '@/store/modules/ai-system-runs'
import { useTourStore } from '@/store/modules/tour'
import { resolveRoutePath } from '@/utils'
import { isSafeAiRoute, matchesAiAssistantShortcut } from '@/utils/ai-assistant-context'
import QuotaReconciliation from './QuotaReconciliation.vue'

const account = useAppAccountStore()
const menu = useAppMenuStore()
const features = useAppFeatureStore()
const route = useRoute()
const router = useRouter()
const { auth } = useAppAuth()
const assistant = useAiAssistantStore()
const systemRuns = useAiSystemRunsStore()
const tours = useTourStore()
const actions = useAiAssistantActions()
const settingsOpen = ref(false)
const tasksOpen = ref(false)
const taskRunId = ref('')
const renameOpen = ref(false)
const removeOpen = ref(false)
const targetSession = ref<YdAgentSession>()
const renameTitle = ref('')
const quotaConfig = ref<{ dailyTokenLimit: number, systemDailyTokenLimit: number }>({ dailyTokenLimit: 0, systemDailyTokenLimit: 0 })
const quotaConfigLoaded = ref(false)
const quotaConfigSaving = ref(false)
const quotaConfigError = ref('')
const canConfigureQuota = computed(() => auth('platform:chat:quota:config'))
/** 平台能力闸门：AI 能力未启用时入口与面板都不出现，也不发任何 AI 请求。 */
const capabilityEnabled = computed(() => features.capabilityEnabled('ai'))
const allowed = computed(() => account.isLogin && !!account.userId && capabilityEnabled.value && auth('platform:chat:use') && auth('platform:chat:session:view') && route.path !== '/setup')
const canEdit = computed(() => auth('platform:chat:session:edit'))
/** 工作区权限：查看 = 列出/读取（含助手只读工具），编辑 = 上传/删除。 */
const canViewWorkspace = computed(() => auth('platform:chat:workspace:view'))
const canEditWorkspace = computed(() => auth('platform:chat:workspace:edit'))
const shortcutLabel = computed(() => assistant.preferences.shortcut === 'alt-shift-a' ? 'Alt + Shift + A' : assistant.preferences.shortcut === 'disabled' ? '快捷键已关闭' : 'Ctrl / ⌘ + Shift + Y')
const pendingCount = computed(() => Object.keys(assistant.frontendRequests).length)
const nativeApprovals = computed(() => (assistant.activeRun?.pendingInteractions || []).filter(interaction => interaction.kind === 'confirmation').map(interaction => ({
  id: interaction.requestId,
  title: `Harness 请求批准工具：${interaction.toolName}`,
  description: JSON.stringify(interaction.arguments, null, 2),
  scope: '仅批准本次工具操作',
})))
/** 页面感知：面板打开期间跟随路由变化，始终指向用户当前所在页面。 */
const currentPageTitle = computed(() => {
  const registered = [...route.matched].reverse().find(item => typeof item.meta.title === 'string')?.meta.title
  if (typeof registered === 'string' && registered) {
    return registered
  }
  return typeof route.name === 'string' ? route.name : '未知页面'
})
const ballBadge = computed(() => (pendingCount.value ? pendingCount.value : assistant.activeRunCount || ''))
const ballLabel = computed(() => `AI 助手 · ${shortcutLabel.value}`)
/** 助手当前操作的唯一工作区：每用户默认工作区，会话与文件都在其中。 */
const workspaceDescriptor = computed<YdAgentWorkspace[]>(() => [{
  id: 'default',
  name: '我的工作区',
  kind: 'remote',
  access: 'write',
  description: '每用户默认工作区：对话与文件都在这里',
}])
watch([() => String(account.userId), () => account.isLogin], ([owner, loggedIn], previous) => {
  if (previous?.[0] && (!loggedIn || previous[0] !== owner)) {
    clearYdPersistentChatOwner(previous[0])
  }
})
watch([() => String(account.userId), () => account.isLogin, () => auth('platform:ai:system:view')], ([owner, loggedIn, permitted]) => {
  systemRuns.syncOwner(loggedIn && permitted ? owner : '')
}, { immediate: true })
const runOptions = computed(() => Object.values(assistant.runs).sort((a, b) => String(b.createdAt || '').localeCompare(String(a.createdAt || ''))).map(run => ({
  value: run.id,
  label: `${assistant.sessions.find(session => session.id === run.sessionId)?.title || (run.sessionId.startsWith('wiki-research:') ? 'Wiki 研究计划' : run.sessionId.startsWith('wiki-lint:') ? 'Wiki Lint' : run.module)} · ${run.status}`,
})))
const show = computed({ get: () => allowed.value && assistant.open, set: (value) => {
  assistant.open = value
} })
const visibleRoutes = computed<AiFrontendRoute[]>(() => {
  const output: AiFrontendRoute[] = []
  const visit = (items: MenuRecordRaw[], base = '') => {
    for (const item of items) {
      if (item.meta?.menu === false || item.meta?.link) {
        continue
      }
      const path = resolveRoutePath(base, item.path)
      if (item.children?.some(child => child.meta?.menu !== false)) {
        visit(item.children, path)
      }
      else {
        const matched = router.resolve(path)
        if (!matched.matched.length || !matched.matched.every(record => auth(record.meta.auth ?? ''))) {
          continue
        }
        const name = typeof matched.name === 'string' ? matched.name : ''
        const title = typeof item.meta?.title === 'string' ? item.meta.title : ''
        const candidate = { name, title, path }
        if (isSafeAiRoute(candidate) && !output.some(record => record.name === name)) {
          output.push(candidate)
        }
      }
    }
  }
  for (const group of menu.allMenus) {
    visit(group.children)
  }
  return output
})

watch([allowed, () => String(account.userId), () => account.isLogin], ([available, userId, loggedIn]) => {
  if (!loggedIn || !userId || !capabilityEnabled.value) {
    assistant.reset()
    return
  }
  if (!available) {
    void assistant.initializeObserver(userId)
    return
  }
  void assistant.initialize(userId)
}, { immediate: true })

watch([settingsOpen, canConfigureQuota], async ([visible, permitted]) => {
  if (!visible || !permitted) {
    return
  }
  const owner = String(account.userId)
  quotaConfigLoaded.value = false
  quotaConfigError.value = ''
  try {
    const response = await fetchChatQuotaConfig()
    if (owner === String(account.userId)) {
      quotaConfig.value = response.data
      quotaConfigLoaded.value = true
    }
  }
  catch (cause) {
    quotaConfigError.value = cause instanceof Error ? cause.message : '读取 AI 额度配置失败'
  }
})

async function saveQuotaConfig() {
  if (!canConfigureQuota.value || !quotaConfigLoaded.value) {
    return
  }
  quotaConfigSaving.value = true
  quotaConfigError.value = ''
  try {
    if (quotaConfig.value.dailyTokenLimit === undefined || quotaConfig.value.systemDailyTokenLimit === undefined) {
      throw new Error('请填写有效的每日 token 预算')
    }
    quotaConfig.value = (await updateChatQuotaConfig(quotaConfig.value.dailyTokenLimit, quotaConfig.value.systemDailyTokenLimit)).data
    await assistant.refreshQuota()
    useYdToast().success('AI 每日预算已保存')
  }
  catch (cause) {
    quotaConfigError.value = cause instanceof Error ? cause.message : '保存 AI 额度配置失败'
  }
  finally {
    quotaConfigSaving.value = false
  }
}

/** 工作区：每用户一个默认工作区，助手会话都在其中读写。 */
const workspaceOpen = ref(false)
const workspaceLoading = ref(false)
const workspaceUploading = ref(false)
const workspaceError = ref('')
const workspaceEntries = ref<ChatWorkspaceEntry[]>([])
const referencedPaths = ref<string[]>([])
const uploadInput = ref<YdFilePickerInstance | null>(null)
const workspaceFiles = computed(() => workspaceEntries.value.map(entry => ({ path: entry.path, included: referencedPaths.value.includes(entry.path) })))

async function loadWorkspace() {
  if (!allowed.value) {
    return
  }
  workspaceLoading.value = true
  workspaceError.value = ''
  try {
    workspaceEntries.value = (await apiChatWorkspace.list()).data
  }
  catch (cause) {
    workspaceError.value = cause instanceof Error ? cause.message : '读取工作区失败'
  }
  finally {
    workspaceLoading.value = false
  }
}

watch(workspaceOpen, (visible) => {
  if (visible) {
    void loadWorkspace()
  }
  else {
    referencedPaths.value = []
  }
})

function toggleReference(path: string, included: boolean) {
  referencedPaths.value = included ? [...new Set([...referencedPaths.value, path])] : referencedPaths.value.filter(item => item !== path)
}

function pickUpload() {
  uploadInput.value?.open()
}

async function onUpload(files: File[]) {
  const file = files[0]
  uploadInput.value?.reset()
  if (!file) {
    return
  }
  workspaceUploading.value = true
  workspaceError.value = ''
  try {
    await apiChatWorkspace.upload(file.name, file)
    useYdToast().success(`已上传到工作区：${file.name}`)
    await loadWorkspace()
  }
  catch (cause) {
    workspaceError.value = cause instanceof Error ? cause.message : '上传工作区文件失败'
  }
  finally {
    workspaceUploading.value = false
  }
}

async function removeWorkspaceFile(entry: ChatWorkspaceEntry) {
  workspaceError.value = ''
  try {
    await apiChatWorkspace.remove(entry.path)
    useYdToast().success(`已删除：${entry.path}`)
    referencedPaths.value = referencedPaths.value.filter(item => item !== entry.path)
    await loadWorkspace()
  }
  catch (cause) {
    workspaceError.value = cause instanceof Error ? cause.message : '删除工作区文件失败'
  }
}

/** 引用文件：小文本内联进本轮提示，其余交给助手的只读工作区工具自行读取。 */
async function composeWithReferences(text: string) {
  const paths = [...referencedPaths.value]
  referencedPaths.value = []
  if (!paths.length) {
    return text
  }
  const blocks: string[] = []
  for (const path of paths) {
    try {
      const response = await apiChatWorkspace.read(path)
      if (response.data.encoding === 'text' && response.data.content.length <= 32000) {
        blocks.push(`【工作区文件 ${path}】\n${response.data.content}`)
      }
      else {
        blocks.push(`【工作区文件 ${path}】文件较大，请用工作区读取工具自行查看`)
      }
    }
    catch {
      blocks.push(`【工作区文件 ${path}】读取失败，如需要请在对话中说明`)
    }
  }
  return `${blocks.join('\n\n')}\n\n${text}`
}

function formatSize(size: number) {
  const value = Number(size || 0)
  return value < 1024 ? `${value} B` : value < 1024 * 1024 ? `${(value / 1024).toFixed(1)} KB` : `${(value / 1024 / 1024).toFixed(1)} MB`
}

// Enabling the scoped READ tools authorizes navigation/highlight. Each real result returns to Harness.
// 业务页面（CMS 画布等）在挂载期注册自己的前端工具执行器；没有执行器的请求显式拒绝，避免模型空等。
const UI_TOOL_NAMES = new Set(['ui.navigate', 'ui.highlight', 'ui.tour'])
watch(() => Object.keys(assistant.frontendRequests), (ids) => {
  for (const id of ids) {
    const request = assistant.frontendRequests[id]?.request
    if (!request) {
      continue
    }
    const handler = assistant.frontendToolHandler(request.name)
    if (handler) {
      void perform(() => assistant.decideFrontendTool(id, true, handler))
      continue
    }
    if (UI_TOOL_NAMES.has(request.name)) {
      void perform(() => assistant.decideFrontendTool(id, true, executeFrontendTool))
      continue
    }
    void perform(() => assistant.decideFrontendTool(id, false, executeFrontendTool, `当前页面没有可执行 ${request.name} 的前端组件，请提示用户先打开对应页面再重试`))
  }
}, { immediate: true })

async function perform(action: () => Promise<unknown>) {
  try {
    await action()
  }
  catch (error) {
    assistant.setError(error)
  }
}

function toggle() {
  if (allowed.value) {
    assistant.open = !assistant.open
  }
}

function showTasks() {
  if (!assistant.runs[taskRunId.value]) {
    taskRunId.value = Object.values(assistant.runs).find(run => run.status === 'RUNNING')?.id || runOptions.value[0]?.value || ''
  }
  tasksOpen.value = true
}

function shortcut(event: KeyboardEvent) {
  if (!allowed.value || event.defaultPrevented || !matchesAiAssistantShortcut(event, assistant.preferences.shortcut)) {
    return
  }
  const target = event.target
  if (target instanceof HTMLElement && (target.isContentEditable || ['INPUT', 'TEXTAREA', 'SELECT'].includes(target.tagName))) {
    return
  }
  // A different dialog retains its keyboard/focus context.
  if (!assistant.open && document.querySelector('[role="dialog"][data-state="open"]')) {
    return
  }
  event.preventDefault()
  toggle()
}

onMounted(() => {
  void features.load()
  window.addEventListener('keydown', shortcut)
})
onBeforeUnmount(() => {
  window.removeEventListener('keydown', shortcut)
  assistant.reset()
})

async function send(request: YdAgentSendRequest) {
  if (!allowed.value || (!canEdit.value && !assistant.sessionId)) {
    return
  }
  const text = await composeWithReferences(request.text.trim() || request.text)
  if (!allowed.value) {
    return
  }
  const registeredTitle = [...route.matched].reverse().find(item => typeof item.meta.title === 'string')?.meta.title
  const name = typeof route.name === 'string' ? route.name : ''
  const current = name ? { name, title: typeof registeredTitle === 'string' ? registeredTitle : name } : undefined
  void perform(() => assistant.send({ ...request, text }, { route: current, routes: visibleRoutes.value, actions: actions.catalog.value, tours: tours.availableTours(route.path).map(item => item.code) }))
}

function rename(session: YdAgentSession) {
  if (!canEdit.value) {
    return
  }
  targetSession.value = session
  renameTitle.value = session.title
  renameOpen.value = true
}

function remove(session: YdAgentSession) {
  if (!canEdit.value) {
    return
  }
  targetSession.value = session
  removeOpen.value = true
}

async function executeFrontendTool(request: AiFrontendToolRequest): Promise<Record<string, unknown>> {
  if (!allowed.value) {
    throw new Error('登录状态、AI 能力或助手权限已失效')
  }
  if (request.name === 'ui.navigate') {
    if (!assistant.preferences.navigationTools) {
      throw new Error('页面导航工具已关闭')
    }
    const target = visibleRoutes.value.find(item => item.name === request.arguments.routeName)
    if (!target || !router.resolve({ name: target.name }).matched.every(item => auth(item.meta.auth ?? ''))) {
      throw new Error('目标页面不可见或没有权限')
    }
    assistant.open = false
    const failure = await router.push({ name: target.name })
    if (failure && route.name !== target.name) {
      throw new Error('页面导航被路由守卫拒绝')
    }
    await nextTick()
    const result: Record<string, unknown> = { routeName: target.name, title: target.title }
    if (assistant.preferences.pageAwareness || assistant.preferences.highlightTools) {
      result.actions = actions.catalog.value.slice(0, 200)
    }
    return result
  }
  if (request.name === 'ui.tour') {
    return playTour(request.arguments.tourCode)
  }
  if (!assistant.preferences.highlightTools) {
    throw new Error('页面操作定位已关闭')
  }
  const action = actions.catalog.value.find(item => item.code === request.arguments.actionCode)
  if (!action || action.routeName !== route.name || (request.arguments.routeName && request.arguments.routeName !== action.routeName)) {
    throw new Error('当前页面没有注册此操作')
  }
  await nextTick()
  return { ...actions.highlight(action.code) }
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
async function playTour(tourCode?: string) {
  if (!assistant.preferences.tourTools) {
    throw new Error('引导播放工具已关闭')
  }
  if (!tourCode || !tours.availableTours(route.path).some(item => item.code === tourCode)) {
    throw new Error('当前页面没有此引导')
  }
  assistant.open = false
  await nextTick()
  if (!tours.start(tourCode)) {
    throw new Error('引导未能启动')
  }
  return { tourCode, played: true }
}
</script>

<template>
  <template v-if="allowed">
    <YdTooltip :text="ballLabel">
      <YdFloatBall
        :label="ballLabel"
        :badge="ballBadge"
        :busy="assistant.activeRunCount > 0"
        :expanded="show"
        :storage-key="`yd.aiAssistant.ball.${account.userId}`"
        @click="toggle"
      />
    </YdTooltip>
    <YdChatWindow
      v-if="show"
      :title="'AI 助手'"
      :storage-key="`yd.aiAssistant.window.${account.userId}`"
      :width="520"
      :height="680"
      :min-width="360"
      :min-height="380"
      initial-side="right"
      @close="assistant.open = false"
    >
      <template #head-actions>
        <YdTooltip v-if="canViewWorkspace" text="我的工作区">
          <YdButton variant="ghost" size="icon-sm" :aria-label="`我的工作区（${workspaceEntries.length} 个文件）`" @click="workspaceOpen = true">
            <YdIcon name="morph:folder-open" />
          </YdButton>
        </YdTooltip>
        <YdTooltip text="后台任务">
          <YdButton variant="ghost" size="icon-sm" :aria-label="`后台任务 ${assistant.activeRunCount}`" @click="showTasks">
            <YdIcon name="morph:loader-circle" />
          </YdButton>
        </YdTooltip>
        <YdTooltip text="助手设置">
          <YdButton variant="ghost" size="icon-sm" aria-label="助手偏好与感知设置" @click="settingsOpen = true">
            <YdIcon name="morph:settings" />
          </YdButton>
        </YdTooltip>
      </template>
      <div class="yd-global-ai-body">
        <YdAlert v-if="assistant.error" variant="destructive" :title="assistant.error">
          <YdButton v-if="assistant.activeRun" variant="outline" size="sm" @click="perform(assistant.reconnect)">
            恢复后端进度
          </YdButton>
        </YdAlert>
        <YdAgentWorkbench
          :sessions="assistant.sessionItems"
          :session-id="assistant.sessionId"
          :models="assistant.models"
          :model-id="assistant.modelId"
          :messages="assistant.messages"
          :harness="assistant.harness"
          :activities="assistant.activities"
          :approvals="nativeApprovals"
          :available-modes="['ask']"
          :files="canViewWorkspace ? workspaceFiles : []"
          :workspaces="workspaceDescriptor"
          workspace-id="default"
          :disabled="assistant.loading || !canEdit && !assistant.sessionId"
          :suggestions="['帮我找到用户管理页面', '解释当前页面有哪些操作', '帮我规划下一步工作']"
          class="yd-global-ai-workbench"
          @send="send"
          @stop="perform(assistant.stop)"
          @file-toggle="(path, included) => toggleReference(path, included)"
          @file-select="() => { workspaceOpen = true }"
          @update:session-id="id => perform(() => assistant.selectSession(id))"
          @update:model-id="assistant.modelId = $event"
          @session-create="canEdit && perform(() => assistant.createSession())"
          @session-rename="rename"
          @session-pin="session => canEdit && perform(() => assistant.updateSession(session.id, { pinned: !session.pinned }))"
          @session-remove="remove"
          @approval-decision="(id, approved) => assistant.activeRun && perform(() => assistant.respondInteraction(assistant.activeRun!.id, id, approved))"
        >
          <template #header-actions>
            <YdTooltip :text="`当前页面：${currentPageTitle}（助手按需感知页面名称与已注册操作）`">
              <span class="yd-global-ai-page">
                <YdIcon name="morph:app-window" />
                {{ currentPageTitle }}
              </span>
            </YdTooltip>
          </template>
          <template #empty>
            <p>对话保存在你的工作区里，可随时在左下角入口上传文件；路由与页面感知默认关闭，可在设置中启用。</p>
          </template>
        </YdAgentWorkbench>
        <div class="yd-global-ai-status" role="status">
          <span v-if="assistant.quota">今日额度：已用 {{ assistant.quota.usedTokens.toLocaleString() }} / {{ assistant.quota.limitTokens.toLocaleString() }} tokens</span>
          <span v-if="assistant.activeRun && assistant.connections[assistant.activeRun.id] === 'reconnecting'">正在重连后端进度…</span>
          <span v-else>会话与运行进度保存在后端</span>
        </div>
      </div>
    </YdChatWindow>
    <YdDrawer v-model="workspaceOpen" title="我的工作区" :footer="false" :z-index="2200" content-class="w-[560px] max-w-full">
      <div class="yd-global-ai-workspace">
        <p class="yd-global-ai-setting-note">
          每个账号默认拥有一个工作区：所有对话都在其中，上传的文件也能被助手按你的授权读取。文件仅你自己可见。
        </p>
        <div class="yd-global-ai-workspace__bar">
          <YdButton size="sm" variant="outline" :loading="workspaceLoading" @click="loadWorkspace">
            <YdIcon name="morph:refresh-cw" /> 刷新
          </YdButton>
          <YdButton v-if="canEditWorkspace" size="sm" :loading="workspaceUploading" @click="pickUpload">
            <YdIcon name="morph:upload" /> 上传文件
          </YdButton>
        </div>
        <YdAlert v-if="workspaceError" variant="destructive" :title="workspaceError" />
        <p v-if="referencedPaths.length" class="yd-global-ai-setting-note">
          已引用 {{ referencedPaths.length }} 个文件，将在下一条消息中一并发送。
        </p>
        <ul v-if="workspaceEntries.length" class="yd-global-ai-files">
          <li v-for="entry in workspaceEntries" :key="entry.path" class="yd-global-ai-files__item">
            <span class="yd-global-ai-files__meta">
              <span class="yd-global-ai-files__name">{{ entry.path }}</span>
              <span class="yd-global-ai-files__size">{{ formatSize(entry.size) }}</span>
            </span>
            <span class="yd-global-ai-files__actions">
              <YdButton size="sm" :variant="referencedPaths.includes(entry.path) ? 'default' : 'outline'" @click="toggleReference(entry.path, !referencedPaths.includes(entry.path))">
                {{ referencedPaths.includes(entry.path) ? '取消引用' : '引用' }}
              </YdButton>
              <YdButton v-if="canEditWorkspace" size="sm" variant="destructive" @click="removeWorkspaceFile(entry)">
                删除
              </YdButton>
            </span>
          </li>
        </ul>
        <p v-else-if="!workspaceLoading" class="yd-global-ai-setting-note">
          工作区还是空的，先上传一个文件吧。
        </p>
      </div>
      <YdFilePicker ref="uploadInput" label="选择工作区文件" class="hidden" @select="onUpload"><template #default /></YdFilePicker>
    </YdDrawer>
    <YdDrawer v-model="tasksOpen" title="统一 AI 运行任务" :footer="false" :z-index="2200" content-class="w-[760px] max-w-full">
      <YdSelect v-model="taskRunId" :options="runOptions" allow-search aria-label="选择 AI 运行任务" class="mb-4 w-full" />
      <AiRunProgress v-if="taskRunId" :run-id="taskRunId" />
      <p v-else class="yd-global-ai-setting-note">
        当前没有 AI 运行记录。
      </p>
    </YdDrawer>
    <YdDrawer v-model="settingsOpen" title="AI 助手偏好" :footer="false" :z-index="2200">
      <YdForm :model="assistant.preferences" layout="vertical">
        <YdFormItem label="路由感知" description="只提供当前页面的名称与固定路径，不包含查询参数或业务 ID。">
          <YdSwitch :model-value="assistant.preferences.routeAwareness" @update:model-value="value => assistant.setPreferences({ routeAwareness: value })" />
        </YdFormItem>
        <YdFormItem label="页面感知" description="只提供页面显式注册的操作名称；不读取表单、列表、账号数据或页面正文。">
          <YdSwitch :model-value="assistant.preferences.pageAwareness" @update:model-value="value => assistant.setPreferences({ pageAwareness: value })" />
        </YdFormItem>
        <YdFormItem label="帮助打开页面" description="启用后允许助手打开当前有权限的菜单页面。">
          <YdSwitch :model-value="assistant.preferences.navigationTools" @update:model-value="value => assistant.setPreferences({ navigationTools: value })" />
        </YdFormItem>
        <YdFormItem label="定位并高亮操作" description="启用后允许助手聚焦和高亮已注册的操作，操作仍需你手动点击。">
          <YdSwitch :model-value="assistant.preferences.highlightTools" @update:model-value="value => assistant.setPreferences({ highlightTools: value })" />
        </YdFormItem>
        <YdFormItem label="播放新手引导" description="启用后允许助手在当前页面播放分步引导，只做展示不会代你操作。">
          <YdSwitch :model-value="assistant.preferences.tourTools" @update:model-value="value => assistant.setPreferences({ tourTools: value })" />
        </YdFormItem>
        <YdFormItem label="唤起快捷键">
          <YdSelect :model-value="assistant.preferences.shortcut" :options="[{ label: 'Ctrl / ⌘ + Shift + Y', value: 'mod-shift-y' }, { label: 'Alt + Shift + A', value: 'alt-shift-a' }, { label: '关闭快捷键', value: 'disabled' }]" @update:model-value="value => assistant.setPreferences({ shortcut: value as typeof assistant.preferences.shortcut })" />
        </YdFormItem>
      </YdForm>
      <section v-if="canConfigureQuota" aria-label="AI 每日预算管理" class="yd-global-ai-settings-section">
        <h3>每日预算管理</h3>
        <YdAlert v-if="quotaConfigError" variant="destructive" :title="quotaConfigError" />
        <YdForm v-if="quotaConfigLoaded" :model="quotaConfig" layout="vertical">
          <YdFormItem label="用户每日 token 预算" description="预算必须大于 0；已完成用量、运行预留和待核对用量共同占用可用预算。">
            <YdNumberField :model-value="quotaConfig.dailyTokenLimit" :min="1" :step="1000" :disabled="quotaConfigSaving" @update:model-value="value => { if (value !== undefined) quotaConfig.dailyTokenLimit = value }" />
          </YdFormItem>
          <YdFormItem label="系统任务每日 token 预算" description="控制无人值守的系统 AI 任务，不改变用户已登录会话的预算。">
            <YdNumberField :model-value="quotaConfig.systemDailyTokenLimit" :min="1" :step="1000" :disabled="quotaConfigSaving" @update:model-value="value => { if (value !== undefined) quotaConfig.systemDailyTokenLimit = value }" />
          </YdFormItem>
          <YdButton :loading="quotaConfigSaving" @click="saveQuotaConfig">
            保存每日预算
          </YdButton>
        </YdForm>
      </section>
      <QuotaReconciliation v-if="settingsOpen" />
      <p class="yd-global-ai-setting-note">
        偏好按当前账号保存在此浏览器。普通助手没有宿主文件系统、Shell 或编码权限。
      </p>
    </YdDrawer>
    <YdModal v-model="renameOpen" title="重命名会话" show-cancel-button :z-index="2200" :confirm-button-disabled="!renameTitle.trim()" @confirm="targetSession && perform(() => assistant.updateSession(targetSession!.id, { title: renameTitle.trim() }))">
      <YdInput v-model="renameTitle" aria-label="会话标题" :maxlength="100" />
    </YdModal>
    <YdModal v-model="removeOpen" title="删除会话" show-cancel-button :z-index="2200" @confirm="targetSession && perform(() => assistant.removeSession(targetSession!.id))">
      <p>确定删除“{{ targetSession?.title }}”及其消息记录？</p>
    </YdModal>
  </template>
</template>

<style scoped>
.yd-global-ai-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  flex: 1;
}

.yd-global-ai-workbench {
  height: 100%;
  min-height: 0;
}

.yd-global-ai-page {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 220px;
  overflow: hidden;
  padding: 4px 10px;
  color: var(--color-text-3);
  background: var(--color-fill-1);
  border-radius: 999px;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yd-global-ai-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
  min-height: 32px;
  padding: 7px 14px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-2);
  background: var(--color-bg-1);
  border-top: 1px solid var(--color-border-2);
}

.yd-global-ai-status span {
  overflow-wrap: anywhere;
}

.yd-global-ai-workspace {
  display: grid;
  gap: 12px;
}

.yd-global-ai-workspace__bar {
  display: flex;
  gap: 8px;
}

.yd-global-ai-files {
  display: grid;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.yd-global-ai-files__item {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--color-border-2);
  border-radius: 10px;
}

.yd-global-ai-files__meta {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.yd-global-ai-files__name {
  overflow-wrap: anywhere;
  font-size: 13px;
  color: var(--color-text-1);
}

.yd-global-ai-files__size {
  font-size: 11px;
  color: var(--color-text-3);
}

.yd-global-ai-files__actions {
  display: inline-flex;
  flex-shrink: 0;
  gap: 8px;
}

.yd-global-ai-settings-section {
  padding-top: 20px;
  margin-top: 20px;
  border-top: 1px solid var(--color-border-2);
}

.yd-global-ai-settings-section h3 {
  margin: 0 0 12px;
  color: var(--color-text-1);
  font-size: 14px;
  font-weight: 600;
}

.yd-global-ai-setting-note {
  font-size: 12px;
  line-height: 1.8;
  color: var(--color-text-3);
}

@media (width <= 768px) {
  .yd-global-ai-page {
    max-width: 140px;
  }
}
</style>

<style>
.yd-ai-assistant-highlight {
  outline: 3px solid var(--color-text-2);
  outline-offset: 5px;
}

/* 全屏接管页（放映大屏等）让位 */
html[data-fullscreen-stage="true"] .yd-float-ball {
  display: none;
}
</style>
