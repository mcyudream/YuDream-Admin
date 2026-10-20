<script setup lang="ts">
import type { YdWorkspaceRailItem, YdWorkspaceStatusItem } from '@yudream/components'
import type { StudioCommand } from '@/types/site-studio'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import apiTheme from '@/api/modules/platform-theme'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import CmsStudioAgentPanel from './components/CmsStudioAgentPanel.vue'
import StudioAssetsView from './components/StudioAssetsView.vue'
import StudioDesignView from './components/StudioDesignView.vue'
import StudioInspector from './components/StudioInspector.vue'
import StudioLegacyView from './components/StudioLegacyView.vue'
import StudioOverviewView from './components/StudioOverviewView.vue'
import StudioReleasesView from './components/StudioReleasesView.vue'
import StudioStructurePanel from './components/StudioStructurePanel.vue'
import StudioThemeView from './components/StudioThemeView.vue'

defineOptions({ name: 'SiteStudio' })

const store = useSiteStudioStore()
const toast = useYdToast()
const modal = useYdModal()

const view = ref<'overview' | 'design' | 'theme' | 'assets' | 'releases' | 'legacy'>('overview')
const sidebarOpen = ref(true)
const asideOpen = ref(true)
const inspectorView = ref('properties')
const themeOptions = ref<{ label: string, value: string }[]>([])
const themeLoading = ref(false)
const activeThemeCode = ref('default')

const railItems: YdWorkspaceRailItem[] = [
  { value: 'overview', label: '概览', icon: 'morph:layout-dashboard' },
  { value: 'design', label: '设计', icon: 'morph:brush' },
  { value: 'theme', label: '主题', icon: 'morph:palette' },
  { value: 'assets', label: '资源', icon: 'morph:image' },
  { value: 'releases', label: '发布版本', icon: 'morph:history' },
  { value: 'legacy', label: '旧版与迁移', icon: 'morph:archive' },
]

const statusItems = ref<YdWorkspaceStatusItem[]>([])
watch(() => [store.themeCode, store.state?.revision, store.dirty, store.saving, store.publishing] as const, () => {
  statusItems.value = [
    { text: `主题：${store.themeCode}` },
    { text: `版本号：${store.state?.revision || '—'}` },
    ...(store.publishing ? [{ text: '发布中…' }] : []),
    ...(store.saving ? [{ text: '保存中…' }] : []),
    ...(store.dirty
      ? [{ text: '未保存更改', align: 'end' as const, tone: 'warning' as const, icon: 'morph:circle-dot' }]
      : store.state?.publishedReleaseId
        ? [{ text: '草稿已保存 · 已有线上版本', align: 'end' as const, tone: 'muted' as const, icon: 'morph:circle-check' }]
        : [{ text: store.doc ? '草稿已保存' : '尚未创建站点', align: 'end' as const, tone: 'muted' as const }]),
  ]
}, { immediate: true })

// 设计视图使用侧栏与属性栏，其余视图收起以让内容占满主区
watch(view, (value) => {
  if (value === 'design') {
    sidebarOpen.value = true
    asideOpen.value = true
  }
  else {
    sidebarOpen.value = false
    asideOpen.value = false
  }
}, { immediate: true })

watch(() => store.dirty, (dirty) => {
  if (dirty) {
    window.addEventListener('beforeunload', preventUnload)
  }
  else {
    window.removeEventListener('beforeunload', preventUnload)
  }
})
onMounted(() => window.addEventListener('beforeunload', preventUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', preventUnload))
function preventUnload(event: BeforeUnloadEvent) {
  if (store.dirty) {
    event.preventDefault()
  }
}

onBeforeRouteLeave(() => {
  if (!store.dirty) {
    return true
  }
  return new Promise<boolean>((resolve) => {
    modal.confirm({
      title: '离开网站工作台？',
      content: '当前站点有未保存的更改，离开后这些更改会丢失。可先保存草稿再离开。',
      confirmButtonText: '不保存并离开',
      cancelButtonText: '留在工作台',
      onConfirm: () => {
        resolve(true)
      },
      onCancel: () => resolve(false),
    })
  })
})

watch(themeOptions, (options) => {
  if (options.length && !options.some(option => option.value === store.themeCode)) {
    switchTheme(options[0]!.value)
  }
})

async function loadThemes() {
  themeLoading.value = true
  try {
    const res = await apiTheme.overview()
    const themes = res.data.themes || []
    const editable = res.data.editableThemes?.length ? res.data.editableThemes : ['default']
    activeThemeCode.value = themes.find(theme => theme.active)?.pluginCode || 'default'
    themeOptions.value = editable.map((code) => {
      const card = themes.find(theme => (theme.pluginCode || 'default') === code)
      const label = card?.name || (code === 'default' ? '默认主题' : code)
      return { label: code === 'default' ? label : `${label}（${code}）`, value: code }
    })
  }
  catch {
    themeOptions.value = [{ label: '默认主题', value: 'default' }]
  }
  finally {
    themeLoading.value = false
  }
}

function switchTheme(code: string) {
  if (store.busy || !themeOptions.value.some(option => option.value === code) || code === store.themeCode) {
    return
  }
  if (store.dirty) {
    modal.confirm({
      title: '切换编辑主题',
      content: `当前「${store.themeCode}」有未保存更改，切换编辑目标后更改会丢失（不会影响已保存内容）。`,
      confirmButtonText: '丢弃更改并切换',
      onConfirm: () => {
        void store.load(code)
      },
    })
    return
  }
  void store.load(code)
}

async function saveDraft() {
  try {
    await store.saveDraft()
    toast.success('草稿已保存')
  }
  catch {
    toast.error(store.saveError || '保存草稿失败')
  }
}

function publish() {
  const activeTheme = activeThemeCode.value
  modal.confirm({
    title: '发布站点文档',
    content: store.themeCode === activeTheme
      ? `确认把「${store.themeCode}」当前草稿发布到公开站 /site 吗？公开站将优先使用本次发布文档；发布后可在发布版本页恢复。`
      : `确认发布「${store.themeCode}」的站点文档吗？该主题当前不是激活的公开站主题，发布内容暂不出现在 /site；激活该主题后生效。发布后可在发布版本页恢复。`,
    confirmButtonText: '发布',
    onConfirm: async () => {
      try {
        await store.publish()
        toast.success(store.themeCode === activeTheme ? '已发布到公开站 /site' : '已发布（主题未激活，/site 暂不展示）')
      }
      catch {
        toast.error(store.saveError || '发布失败')
      }
    },
  })
}

function retry() {
  void store.load()
}

/** AI 面板与人工编辑共用同一命令管道；错误原样抛给调用方，不吞错 */
function runCommand(command: StudioCommand | StudioCommand[]) {
  return store.execute(command)
}

loadThemes()
void store.load()
</script>

<template>
  <div class="site-studio h-full min-h-0">
    <YdLoadingOverlay :loading="store.loading" block-interaction>
      <YdResult
        v-if="store.loadError"
        status="error"
        title="网站工作台加载失败"
        :description="store.loadError"
      >
        <template #actions>
          <YdButton variant="outline" @click="retry">
            重试
          </YdButton>
        </template>
      </YdResult>

      <YdWorkspaceLayout
        v-else
        v-model:rail="view"
        v-model:sidebar-open="sidebarOpen"
        v-model:aside-open="asideOpen"
        :rail-items="railItems"
        storage-key="site-studio"
        sidebar-label="结构与页面"
        main-label="站点内容"
        aside-label="属性"
        :status-items="statusItems"
        aria-label="网站工作台"
        class="h-full"
      >
        <template #header>
          <div class="flex flex-wrap gap-2 min-w-0 items-center">
            <YdIcon name="morph:paintbrush" class="text-base" />
            <strong class="whitespace-nowrap">网站工作台</strong>
            <YdAlert
              v-if="store.saveError"
              variant="destructive"
              class="py-1 flex-1 basis-56 min-w-0"
            >
              <span class="min-w-0 break-all">{{ store.saveError }}</span>
            </YdAlert>
            <span v-else-if="store.dirty" class="text-warning-6 text-xs whitespace-nowrap">
              <YdIcon name="morph:circle-dot" class="mr-1 align-[-1px] size-3 inline" />未保存更改
            </span>
          </div>
          <div class="flex flex-wrap gap-2 items-center">
            <YdSelect
              :model-value="store.themeCode"
              :options="themeOptions"
              :disabled="themeLoading || store.busy"
              aria-label="选择编辑主题"
              class="w-52"
              @update:model-value="switchTheme(String($event))"
            />
            <YdTooltip content="撤销">
              <YdButton
                v-auth="'platform:cms:edit'"
                variant="ghost"
                size="sm"
                aria-label="撤销"
                :disabled="!store.canUndo"
                @click="store.undo()"
              >
                <YdIcon name="morph:undo-2" />
              </YdButton>
            </YdTooltip>
            <YdTooltip content="重做">
              <YdButton
                v-auth="'platform:cms:edit'"
                variant="ghost"
                size="sm"
                aria-label="重做"
                :disabled="!store.canRedo"
                @click="store.redo()"
              >
                <YdIcon name="morph:redo-2" />
              </YdButton>
            </YdTooltip>
            <YdButton
              v-auth="'platform:cms:edit'"
              variant="outline"
              :disabled="!store.dirty || store.busy"
              :loading="store.saving"
              @click="saveDraft"
            >
              保存草稿
            </YdButton>
            <YdButton
              v-auth="'platform:cms:publish'"
              :loading="store.publishing"
              :disabled="!store.doc || store.busy"
              @click="publish"
            >
              发布
            </YdButton>
          </div>
        </template>

        <template #sidebar>
          <StudioStructurePanel />
        </template>

        <template #main>
          <StudioOverviewView v-if="view === 'overview'" @start-design="view = 'design'" />
          <StudioDesignView v-else-if="view === 'design'" />
          <StudioThemeView v-else-if="view === 'theme'" @edit-region="(view = 'design')" />
          <StudioAssetsView v-else-if="view === 'assets'" @open-design="view = 'design'" />
          <StudioReleasesView v-else-if="view === 'releases'" />
          <StudioLegacyView v-else />
        </template>

        <template #aside>
          <div class="flex flex-col h-full min-h-0">
            <div class="p-2 border-b shrink-0">
              <YdSegmented v-model="inspectorView" :options="[{ label: '元素属性', value: 'properties' }, { label: 'AI 设计助手', value: 'assistant' }]" aria-label="检查器视图" />
            </div>
            <div v-show="inspectorView === 'properties'" class="flex-1 min-h-0 overflow-auto">
              <StudioInspector />
            </div>
            <CmsStudioAgentPanel
              v-if="store.doc"
              v-show="inspectorView === 'assistant'"
              :document="store.doc"
              :selected-node-id="store.selectedNodeId"
              :revision="store.editRevision"
              :context-key="store.contextKey"
              :page-id="store.activePageId"
              :execute-command="runCommand"
              class="flex-1 min-h-0"
            />
          </div>
        </template>
      </YdWorkspaceLayout>
    </YdLoadingOverlay>
  </div>
</template>
