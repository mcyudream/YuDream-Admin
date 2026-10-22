<script setup lang="ts">
import type { YdWorkspaceRailItem, YdWorkspaceStatusItem } from '@yudream/components'
import type { StudioCommand } from '@/types/site-studio'
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import apiTheme from '@/api/modules/platform-theme'
import { useSiteStudioStore } from '@/store/modules/site-studio'
import { openStudioPreviewChannel, studioPagePathToSlug } from '@/utils/site-studio-preview'
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
const features = useAppFeatureStore()
// 能力未启用的功能不展示入口：AI 能力关闭时不出现“AI 设计助手”
const aiEnabled = computed(() => features.capabilityEnabled('ai'))
const inspectorOptions = computed(() => [
  { label: '元素属性', value: 'properties' },
  ...(aiEnabled.value ? [{ label: 'AI 设计助手', value: 'assistant' }] : []),
])
const toast = useYdToast()
const { auth } = useAppAuth()
const modal = useYdModal()

const view = ref<'overview' | 'design' | 'theme' | 'assets' | 'releases' | 'legacy'>('overview')
const sidebarOpen = ref(true)
const asideOpen = ref(true)
const pane = ref<'sidebar' | 'main' | 'aside'>('main')
const inspectorView = ref('properties')
watch(aiEnabled, (enabled) => {
  if (!enabled && inspectorView.value === 'assistant') {
    inspectorView.value = 'properties'
  }
})
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

// 设计视图使用侧栏与属性栏，其余视图收起以让内容占满主区。
// 活动栏在这里切换的是主区视图：窄屏布局默认会切到侧栏面板，统一回到主区。
watch(view, (value) => {
  pane.value = 'main'
  if (value === 'design') {
    sidebarOpen.value = true
    asideOpen.value = true
  }
  else {
    sidebarOpen.value = false
    asideOpen.value = false
  }
}, { immediate: true })

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
      const name = code === 'default' ? label : `${label}（${code}）`
      return { label: code === activeThemeCode.value ? `${name} · 公开站使用中` : name, value: code }
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

/** 当前编辑的主题是否就是公开站正在使用的主题 */
const editingActiveTheme = computed(() => store.themeCode === activeThemeCode.value)
const activating = ref(false)

/**
 * 设为公开站主题：插件主题即启用该主题插件（同作用域主题互斥顶替），内置默认主题即停用当前主题插件。
 * 尚未发布过的主题先提示，否则激活后公开站为空。
 */
function activateTheme() {
  const code = store.themeCode
  const unpublished = !store.state?.publishedReleaseId
  modal.confirm({
    title: '设为公开站主题',
    content: unpublished
      ? `「${code}」还没有发布过站点文档，激活后公开站 /site 会是空的。建议先「发布」再激活。仍要激活吗？`
      : `确认把公开站 /site 切换为「${code}」的已发布站点吗？当前主题「${activeThemeCode.value}」的内容会保留，随时可以切换回来。`,
    confirmButtonText: '激活',
    onConfirm: async () => {
      activating.value = true
      try {
        if (code === 'default') {
          await apiTheme.deactivate()
        }
        else {
          await apiTheme.activate(code)
        }
        await loadThemes()
        toast.success(`公开站已切换为「${code}」`)
      }
      catch {
        // 请求错误已由全局提示展示
      }
      finally {
        activating.value = false
      }
    },
  })
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
        if (store.themeCode === activeTheme) {
          toast.success('已发布到公开站 /site')
        }
        else if (auth('platform:theme-center:use')) {
          // 发布了未激活的主题：顺势询问是否切换公开站，避免「发布了却看不到」
          activateTheme()
        }
        else {
          toast.success('已发布（主题未激活，/site 暂不展示；需有「切换整站主题」权限的管理员激活）')
        }
      }
      catch {
        toast.error(store.saveError || '发布失败')
      }
    },
  })
}

// ===== 草稿预览：新标签页以公开站同一渲染查看当前页面；本页实时广播内存文档（含未保存更改） =====
const router = useRouter()
let previewChannel: BroadcastChannel | null = null
let previewTimer: ReturnType<typeof setTimeout> | undefined
function publishPreview() {
  if (!previewChannel || !store.doc) {
    return
  }
  previewChannel.postMessage({ type: 'document', themeCode: store.themeCode, documentJson: JSON.stringify(store.doc), dirty: store.dirty, revision: store.state?.revision })
}
watch(() => store.themeCode, (code) => {
  previewChannel?.close()
  previewChannel = openStudioPreviewChannel(code)
  if (previewChannel) {
    previewChannel.onmessage = (event: MessageEvent<{ type?: string, themeCode?: string }>) => {
      if (event.data?.type === 'request' && event.data.themeCode === store.themeCode) {
        publishPreview()
      }
    }
  }
}, { immediate: true })
watch(() => [store.doc, store.dirty] as const, () => {
  clearTimeout(previewTimer)
  previewTimer = setTimeout(publishPreview, 250)
})
onBeforeUnmount(() => {
  clearTimeout(previewTimer)
  previewChannel?.close()
})
function openPreview() {
  const slug = studioPagePathToSlug(store.activePage?.path ?? '/')
  window.open(router.resolve({ name: 'siteStudioPreview', params: { themeCode: store.themeCode, slug } }).href, '_blank')
}

function retry() {
  void store.load()
}

/** AI 面板与人工编辑共用同一命令管道；错误原样抛给调用方，不吞错 */
function runCommand(command: StudioCommand | StudioCommand[]) {
  return store.execute(command)
}

/** 首次打开默认编辑公开站正在使用的主题（否则改了默认主题却在 /site 看不到）；本次会话已选过主题则保持 */
async function initialize() {
  const firstOpen = !store.doc
  await loadThemes()
  void store.load(firstOpen ? activeThemeCode.value : store.themeCode)
}
void initialize()
</script>

<template>
  <!-- data-yd-fullheight：宿主布局据此把容器固定为视口高，工作台各栏自行内滚 -->
  <!-- data-yd-own-ai：工作台自带 AI 面板，全局 AI 悬浮球让位，避免压住属性栏与输入框 -->
  <div class="site-studio h-full min-h-0" data-yd-fullheight data-yd-own-ai>
    <!-- 单行 grid（minmax(0,1fr)）：内容包装层固定为整高、不被内容撑高，各栏自行内滚；遮罩为绝对定位，不占轨道 -->
    <YdLoadingOverlay :loading="store.loading" block-interaction class="grid grid-rows-[minmax(0,1fr)] h-full">
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
        v-model:pane="pane"
        :rail-items="railItems"
        storage-key="site-studio"
        :aside="{ defaultSize: 26, minSize: 20, maxSize: 45 }"
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
              <template #description>
                <span class="min-w-0 break-all">{{ store.saveError }}</span>
              </template>
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
            <YdTag v-if="editingActiveTheme" variant="secondary" class="whitespace-nowrap">
              公开站使用中
            </YdTag>
            <YdButton
              v-else
              v-auth="'platform:theme-center:use'"
              variant="outline"
              size="sm"
              :loading="activating"
              :disabled="store.busy || themeLoading"
              @click="activateTheme"
            >
              <YdIcon name="morph:power" /> 设为公开站主题
            </YdButton>
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
            <YdTooltip content="在新标签页按公开站效果预览当前页面（含未保存更改）">
              <YdButton variant="ghost" :disabled="!store.doc" aria-label="预览站点" @click="openPreview">
                <YdIcon name="morph:external-link" /> 预览站点
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
              <YdSegmented v-if="inspectorOptions.length > 1" v-model="inspectorView" :options="inspectorOptions" aria-label="检查器视图" />
              <strong v-else class="text-sm">元素属性</strong>
            </div>
            <div v-show="inspectorView === 'properties'" class="flex-1 min-h-0 overflow-auto">
              <StudioInspector />
            </div>
            <CmsStudioAgentPanel
              v-if="store.doc && aiEnabled"
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

<style scoped>
/* 网站工作台使用直角编辑器外观：只在工作台作用域内把圆角变量归零，不影响后台其他页面 */
.site-studio {
  --yd-surface-radius: 0;
  --yd-control-radius: 0;
  --radius: 0px;
}
</style>
