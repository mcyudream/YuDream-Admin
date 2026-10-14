<script setup lang="ts">
import type { MenuRecordRaw } from '@fantastic-admin/types'
import type { YdAgentSendRequest, YdAgentSession } from '@yudream/components'
import type { AiFrontendRoute, AiFrontendToolRequest } from '@/api/modules/platform-ai-runs'
import { clearYdPersistentChatOwner, YdAgentWorkbench, YdAlert, YdButton, YdDrawer, YdForm, YdFormItem, YdIcon, YdInput, YdModal, YdNumberField, YdSelect, YdSwitch, YdTooltip } from '@yudream/components'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { fetchChatQuotaConfig, updateChatQuotaConfig } from '@/api/modules/platform-chat'
import AiRunProgress from '@/components/AiRunProgress/index.vue'
import { useAiAssistantActions } from '@/composables/ai-assistant-actions'
import { useAiAssistantStore } from '@/store/modules/ai-assistant'
import { useAiSystemRunsStore } from '@/store/modules/ai-system-runs'
import { resolveRoutePath } from '@/utils'
import { isSafeAiRoute, matchesAiAssistantShortcut } from '@/utils/ai-assistant-context'
import QuotaReconciliation from './QuotaReconciliation.vue'

const account = useAppAccountStore()
const menu = useAppMenuStore()
const route = useRoute()
const router = useRouter()
const { auth } = useAppAuth()
const assistant = useAiAssistantStore()
const systemRuns = useAiSystemRunsStore()
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
const allowed = computed(() => account.isLogin && !!account.userId && auth('platform:chat:use') && auth('platform:chat:session:view') && route.path !== '/setup')
const canEdit = computed(() => auth('platform:chat:session:edit'))
const shortcutLabel = computed(() => assistant.preferences.shortcut === 'alt-shift-a' ? 'Alt + Shift + A' : assistant.preferences.shortcut === 'disabled' ? '快捷键已关闭' : 'Ctrl / ⌘ + Shift + Y')
const pendingCount = computed(() => Object.keys(assistant.frontendRequests).length)
const nativeApprovals = computed(() => (assistant.activeRun?.pendingInteractions || []).filter(interaction => interaction.kind === 'confirmation').map(interaction => ({
  id: interaction.requestId,
  title: `Harness 请求批准工具：${interaction.toolName}`,
  description: JSON.stringify(interaction.arguments, null, 2),
  scope: '仅批准本次工具操作',
})))
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
  if (loggedIn && userId) {
    if (!available) {
      void assistant.initializeObserver(userId)
      return
    }
    void assistant.initialize(userId)
  }
  else {
    assistant.reset()
  }
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

// Enabling the scoped READ tools authorizes navigation/highlight. Each real result returns to Harness.
watch(() => Object.keys(assistant.frontendRequests), (ids) => {
  for (const id of ids) {
    void perform(() => assistant.decideFrontendTool(id, true, executeFrontendTool))
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

onMounted(() => window.addEventListener('keydown', shortcut))
onBeforeUnmount(() => {
  window.removeEventListener('keydown', shortcut)
  assistant.reset()
})

function send(request: YdAgentSendRequest) {
  if (!allowed.value || (!canEdit.value && !assistant.sessionId)) {
    return
  }
  const registeredTitle = [...route.matched].reverse().find(item => typeof item.meta.title === 'string')?.meta.title
  const name = typeof route.name === 'string' ? route.name : ''
  const current = name ? { name, title: typeof registeredTitle === 'string' ? registeredTitle : name } : undefined
  void perform(() => assistant.send(request, { route: current, routes: visibleRoutes.value, actions: actions.catalog.value }))
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
    throw new Error('登录状态或助手权限已失效')
  }
  if (request.name === 'ui.navigate') {
    if (!assistant.preferences.navigationTools) {
      throw new Error('页面导航工具已关闭')
    }
    const target = visibleRoutes.value.find(item => item.name === request.arguments.routeName)
    if (!target || !router.resolve({ name: target.name }).matched.every(item => auth(item.meta.auth ?? ''))) {
      throw new Error('目标页面不可见或没有权限')
    }
    const failure = await router.push({ name: target.name })
    if (failure && route.name !== target.name) {
      throw new Error('页面导航被路由守卫拒绝')
    }
    assistant.open = false
    await nextTick()
    const result: Record<string, unknown> = { routeName: target.name, title: target.title }
    if (assistant.preferences.pageAwareness || assistant.preferences.highlightTools) {
      result.actions = actions.catalog.value.slice(0, 200)
    }
    return result
  }
  if (!assistant.preferences.highlightTools) {
    throw new Error('页面操作定位已关闭')
  }
  const action = actions.catalog.value.find(item => item.code === request.arguments.actionCode)
  if (!action || action.routeName !== route.name || (request.arguments.routeName && request.arguments.routeName !== action.routeName)) {
    throw new Error('当前页面没有注册此操作')
  }
  assistant.open = false
  await nextTick()
  return { ...actions.highlight(action.code) }
}
</script>

<template>
  <template v-if="allowed">
    <YdTooltip :text="`AI 助手 · ${shortcutLabel}`">
      <YdButton class="yd-global-ai-trigger" :variant="pendingCount ? 'default' : 'outline'" :aria-expanded="show" aria-label="唤起全局 AI 助手" @click="toggle">
        <YdIcon :name="assistant.activeRunCount ? 'morph:loader-circle' : 'morph:bot'" :class="{ 'animate-spin': assistant.activeRunCount > 0 }" />
        <span>{{ pendingCount ? `页面工具执行中 ${pendingCount}` : assistant.activeRunCount ? `AI 执行中 ${assistant.activeRunCount}` : 'AI 助手' }}</span>
      </YdButton>
    </YdTooltip>
    <YdModal v-model="show" title="全局 AI 助手" :description="`${shortcutLabel} 唤起；关闭窗口后任务继续执行`" :footer="false" :destroy-on-close="false" :z-index="1900" class="yd-global-ai-modal" content-class="p-0 min-h-0 overflow-hidden" header-class="py-3">
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
          :disabled="assistant.loading || !canEdit && !assistant.sessionId"
          :suggestions="['帮我找到用户管理页面', '解释当前页面有哪些操作', '帮我规划下一步工作']"
          class="yd-global-ai-workbench"
          @send="send"
          @stop="perform(assistant.stop)"
          @update:session-id="id => perform(() => assistant.selectSession(id))"
          @update:model-id="assistant.modelId = $event"
          @session-create="canEdit && perform(() => assistant.createSession())"
          @session-rename="rename"
          @session-pin="session => canEdit && perform(() => assistant.updateSession(session.id, { pinned: !session.pinned }))"
          @session-remove="remove"
          @approval-decision="(id, approved) => assistant.activeRun && perform(() => assistant.respondInteraction(assistant.activeRun!.id, id, approved))"
        >
          <template #header-actions>
            <YdButton variant="outline" size="sm" @click="showTasks">
              后台任务 {{ assistant.activeRunCount }}
            </YdButton>
            <YdButton variant="ghost" size="icon-sm" aria-label="助手偏好与感知设置" @click="settingsOpen = true">
              <YdIcon name="morph:settings" />
            </YdButton>
          </template>
          <template #empty>
            <p>路由与页面感知默认关闭，可在设置中启用。页面工具只帮助导航和定位，操作仍由你执行。</p>
          </template>
        </YdAgentWorkbench>
        <div class="yd-global-ai-status" role="status">
          <span v-if="assistant.quota">今日额度：已用 {{ assistant.quota.usedTokens.toLocaleString() }} / {{ assistant.quota.limitTokens.toLocaleString() }} tokens</span>
          <span v-if="assistant.quota?.reservedTokens">运行预留 {{ assistant.quota.reservedTokens.toLocaleString() }} tokens</span>
          <span v-if="assistant.quota?.uncertainTokens">待核对用量 {{ assistant.quota.uncertainTokens.toLocaleString() }} tokens</span>
          <span v-if="assistant.activeRun && assistant.connections[assistant.activeRun.id] === 'reconnecting'">正在重连后端进度…</span>
          <span v-else>会话与运行进度保存在后端</span>
        </div>
      </div>
    </YdModal>
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
.yd-global-ai-trigger {
  position: fixed;
  right: 24px;
  bottom: calc(76px + env(safe-area-inset-bottom));
  z-index: 1500;
}

.yd-global-ai-modal {
  width: min(1240px, calc(100vw - 24px));
  max-height: calc(100dvh - 24px);
}

.yd-global-ai-body {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  max-height: calc(100dvh - 104px);
}

.yd-global-ai-workbench {
  height: min(740px, calc(100dvh - 160px));
}

.yd-global-ai-status {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: space-between;
  min-height: 34px;
  padding: 8px 14px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-2);
  background: var(--color-bg-1);
  border-top: 1px solid var(--color-border-2);
}

.yd-global-ai-status span {
  overflow-wrap: anywhere;
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
  .yd-global-ai-trigger {
    right: max(12px, env(safe-area-inset-right));
    bottom: calc(72px + env(safe-area-inset-bottom));
  }

  .yd-global-ai-modal {
    width: calc(100vw - 16px);
    max-height: calc(100dvh - 16px);
  }

  .yd-global-ai-body {
    max-height: calc(100dvh - 92px);
  }

  .yd-global-ai-workbench {
    height: calc(100dvh - 156px);
  }
}
</style>

<style>
.yd-ai-assistant-highlight {
  outline: 3px solid var(--color-text-2);
  outline-offset: 5px;
}
</style>
