<script setup lang="ts">
/**
 * YdWorkspaceLayout：VS Code 式工作区外壳（活动栏 / 侧栏 / 主区 / 属性栏 / 状态栏）。
 *
 * 与「页面卡片」的分工：本组件给需要长驻编辑的职业工作面用——外壳不滚、面板内滚，
 * 侧栏与属性栏之间是可拖拽分栏（内部用 YdLayoutContainer，尺寸按 storageKey 记忆）。
 *
 * 行为契约：
 * 1. 分栏：桌面端侧栏与属性栏可拖拽、可键盘调整；面板增删通过 `v-model:sidebarOpen` /
 *    `v-model:asideOpen` 控制，关闭即退出分栏，不留下 0 宽占位；
 * 2. 尺寸记忆：持久化键包含当前面板组成签名，收起再展开能恢复该组成下最后一次的宽度，
 *    不会把侧栏宽度错套到主区；
 * 3. 窄屏：< 768px 退化为单面板，用下拉选择器在 侧栏 / 主区 / 属性栏 之间切换，
 *    活动栏变成顶部横向图标行，主操作仍留在 header 插槽里；
 * 4. 外壳自带背景与高度：容器给了确定高度就铺满（`h-full`），页面直接放进路由内容区即可，
 *    不依赖百分比高度链；容器高度为 auto 时退回按内容撑开。
 */
import type { YdWorkspacePanelSize, YdWorkspaceRailItem, YdWorkspaceStatusItem } from './workspace-model'
import { computed, ref, useSlots, watch } from 'vue'
import { cn } from '#utils'
import { YdSelect } from '../adapters/index'
import Icon from '../basic/icon/index.vue'
import { useIsMobile } from '../basic/responsive-table'
import YdLayoutContainer from '../extended/YdLayoutContainer.vue'

export type { YdWorkspaceRailItem, YdWorkspaceStatusItem }

defineOptions({ name: 'YdWorkspaceLayout' })

const props = withDefaults(defineProps<{
  /** 活动栏条目：只做侧栏视图切换，不承载业务动作 */
  railItems?: YdWorkspaceRailItem[]
  railLabel?: string
  sidebarLabel?: string
  mainLabel?: string
  asideLabel?: string
  /** 侧栏尺寸（拖拽初始值与上下限） */
  sidebar?: YdWorkspacePanelSize
  /** 属性栏尺寸（拖拽初始值与上下限） */
  aside?: YdWorkspacePanelSize
  /** 分栏宽度记忆键；不同页面传不同值，避免互相串用 */
  storageKey?: string
  /** 状态栏条目；也可用 #status 插槽完全自定义 */
  statusItems?: YdWorkspaceStatusItem[]
  ariaLabel?: string
  class?: string
}>(), {
  railLabel: '视图切换',
  sidebarLabel: '侧栏',
  mainLabel: '主区',
  asideLabel: '属性栏',
  ariaLabel: '工作区',
})

type WorkspacePane = 'sidebar' | 'main' | 'aside'

const rail = defineModel<string>('rail', { default: '' })
const sidebarOpen = defineModel<boolean>('sidebarOpen', { default: true })
const asideOpen = defineModel<boolean>('asideOpen', { default: true })
const pane = defineModel<WorkspacePane>('pane', { default: 'main' })

const slots = useSlots()
const compact = useIsMobile()
const hasHeader = computed(() => Boolean(slots.header))
const hasRail = computed(() => Boolean(slots.rail) || Boolean(props.railItems?.length))

const showSidebar = computed(() => sidebarOpen.value && Boolean(slots.sidebar))
const showAside = computed(() => asideOpen.value && Boolean(slots.aside))

/** 分栏面板组成签名：尺寸记忆按组成隔离，收起/展开不会把宽度套错面板 */
const paneSignature = computed(() => [showSidebar.value ? 's' : '', 'm', showAside.value ? 'a' : ''].filter(Boolean).join(''))

const panels = computed(() => {
  const list: ({ key: WorkspacePane } & YdWorkspacePanelSize)[] = []
  if (showSidebar.value) {
    list.push({ key: 'sidebar', defaultSize: 20, minSize: 12, maxSize: 40, sizeUnit: '%', ...props.sidebar })
  }
  list.push({ key: 'main' })
  if (showAside.value) {
    list.push({ key: 'aside', defaultSize: 22, minSize: 14, maxSize: 42, sizeUnit: '%', ...props.aside })
  }
  return list
})

const compactPaneOptions = computed(() => [
  ...(showSidebar.value ? [{ label: props.sidebarLabel, value: 'sidebar' }] : []),
  { label: props.mainLabel, value: 'main' },
  ...(showAside.value ? [{ label: props.asideLabel, value: 'aside' }] : []),
])

/** 面板组成变化后当前激活面板可能已不可用：回落主区，避免空白 */
watch([compact, showSidebar, showAside], () => {
  if (pane.value === 'sidebar' && !showSidebar.value) {
    pane.value = 'main'
    return
  }
  if (pane.value === 'aside' && !showAside.value) {
    pane.value = 'main'
  }
}, { immediate: true })

/** 窄屏选择器回传字符串，这里收敛回受控面板并忽略未知值 */
const compactPane = computed({
  get: () => pane.value,
  set: (value: string | number | undefined) => {
    if (value === 'sidebar' || value === 'main' || value === 'aside') {
      pane.value = value
    }
  },
})

function selectRail(value: string) {
  rail.value = value
  if (!slots.sidebar) {
    return
  }
  // 活动栏就是「换侧栏视图」：收起状态下点选同时展开，窄屏直接切到侧栏面板
  sidebarOpen.value = true
  if (compact.value) {
    pane.value = 'sidebar'
  }
}

const startItems = computed(() => (props.statusItems ?? []).filter(item => item.align !== 'end'))
const endItems = computed(() => (props.statusItems ?? []).filter(item => item.align === 'end'))

function toneClass(tone: YdWorkspaceStatusItem['tone']): string {
  if (tone === 'success') {
    return 'yd-status-success'
  }
  if (tone === 'warning') {
    return 'yd-status-warning'
  }
  if (tone === 'danger') {
    return 'yd-status-danger'
  }
  return tone === 'muted' ? 'text-muted-foreground' : ''
}

// 分栏拖拽过程中禁用过渡，避免拖动时标签/内容跟随抖动
const dragging = ref(false)
</script>

<template>
  <div
    :class="cn('yd-workspace flex h-full min-h-0 w-full flex-col overflow-hidden bg-card', props.class)"
    :aria-label="ariaLabel"
  >
    <div
      v-if="hasHeader"
      class="yd-workspace__header px-3 py-2 border-b bg-card flex flex-wrap gap-2 items-center"
      role="toolbar"
    >
      <slot name="header" />
    </div>

    <!-- 窄屏活动栏是横向图标行，body 必须同步改成纵列，否则它会被当成左栏拉满高度 -->
    <div class="yd-workspace__body flex flex-1 min-h-0" :class="compact ? 'flex-col' : ''">
      <div
        v-if="hasRail"
        class="yd-workspace__rail border-border bg-muted/40 flex flex-shrink-0 gap-1 items-center"
        :class="compact ? 'yd-workspace__rail--compact border-b px-2 py-1' : 'flex-col border-r py-2'"
        role="toolbar"
        :aria-label="railLabel"
      >
        <slot name="rail">
          <button
            v-for="item in railItems"
            :key="item.value"
            type="button"
            class="yd-workspace__rail-item"
            :class="rail === item.value ? 'yd-workspace__rail-item--active' : ''"
            :aria-pressed="rail === item.value"
            :title="item.label"
            :aria-label="item.label"
            @click="selectRail(item.value)"
          >
            <Icon :name="item.icon" />
          </button>
        </slot>
      </div>

      <!-- 窄屏：单面板 + 面板切换器 -->
      <div v-if="compact" class="flex flex-1 flex-col min-h-0 min-w-0">
        <div v-if="compactPaneOptions.length > 1" class="px-2 py-2 border-b bg-card flex-shrink-0">
          <YdSelect v-model="compactPane" :options="compactPaneOptions" class="w-full" aria-label="切换工作区面板" />
        </div>
        <div class="yd-workspace__pane flex-1 min-h-0 min-w-0 overflow-auto">
          <slot :name="pane" />
        </div>
      </div>

      <!-- 宽屏：可拖拽分栏。
           面板插槽显式写全（不用 v-for + 动态插槽名：该组合会让 SFC 模板编译器崩溃），
           未启用的面板不会出现在 panels 里，多余的插槽不会渲染。 -->
      <YdLayoutContainer
        v-if="!compact"
        class="flex-1 min-h-0 min-w-0"
        :class="dragging ? 'yd-workspace--dragging' : ''"
        :panels="panels"
        :storage-key="storageKey ? `${storageKey}:${paneSignature}` : undefined"
        @dragging="dragging = $event"
      >
        <template #sidebar>
          <div class="yd-workspace__pane bg-muted/20 h-full min-h-0" :aria-label="sidebarLabel">
            <slot name="sidebar" />
          </div>
        </template>
        <template #main>
          <div class="yd-workspace__pane bg-card h-full min-h-0" :aria-label="mainLabel">
            <slot name="main" />
          </div>
        </template>
        <template #aside>
          <div class="yd-workspace__pane bg-muted/20 h-full min-h-0" :aria-label="asideLabel">
            <slot name="aside" />
          </div>
        </template>
      </YdLayoutContainer>
    </div>

    <div
      v-if="statusItems?.length || slots.status"
      class="yd-workspace__status text-xs text-muted-foreground px-3 py-1 border-t bg-muted/40 flex flex-shrink-0 flex-wrap gap-x-4 gap-y-1 items-center"
    >
      <slot name="status">
        <span
          v-for="item in startItems"
          :key="item.text"
          class="yd-workspace__status-item inline-flex gap-1 items-center"
          :class="toneClass(item.tone)"
          :title="item.title || item.text"
        >
          <Icon v-if="item.icon" :name="item.icon" class="size-3.5" />
          {{ item.text }}
        </span>
        <span v-if="endItems.length" class="flex gap-4 items-center sm:ml-auto">
          <span
            v-for="item in endItems"
            :key="item.text"
            class="yd-workspace__status-item inline-flex gap-1 items-center"
            :class="toneClass(item.tone)"
            :title="item.title || item.text"
          >
            <Icon v-if="item.icon" :name="item.icon" class="size-3.5" />
            {{ item.text }}
          </span>
        </span>
      </slot>
    </div>
  </div>
</template>
