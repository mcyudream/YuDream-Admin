<script setup lang="ts">
import type { Component } from 'vue'
import { useStorage } from '@vueuse/core'
import { YdAction } from '@yudream/components'
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import ThemePanel from '@/components/ThemePanel.vue'
import { useShowcaseAppearance } from '@/composables/useShowcaseAppearance'
import AiSection from '@/sections/AiSection.vue'
import BasicSection from '@/sections/BasicSection.vue'
import CalendarSection from '@/sections/CalendarSection.vue'
import ChatSection from '@/sections/ChatSection.vue'
import CollectionControlsSection from '@/sections/CollectionControlsSection.vue'
import ComboboxCreateSection from '@/sections/ComboboxCreateSection.vue'
import DaisyComponentsSection from '@/sections/DaisyComponentsSection.vue'
import DataDisplaySection from '@/sections/DataDisplaySection.vue'
import DataSection from '@/sections/DataSection.vue'
import DateTimeSection from '@/sections/DateTimeSection.vue'
import DomActionSection from '@/sections/DomActionSection.vue'
import EffectsSection from '@/sections/EffectsSection.vue'
import EnhancedTableSection from '@/sections/EnhancedTableSection.vue'
import ExtendedSection from '@/sections/ExtendedSection.vue'
import FeedbackSection from '@/sections/FeedbackSection.vue'
import FilesSection from '@/sections/FilesSection.vue'
import FormControlsSection from '@/sections/FormControlsSection.vue'
import FormSection from '@/sections/FormSection.vue'
import LayoutSection from '@/sections/LayoutSection.vue'
import ListToolbarSection from '@/sections/ListToolbarSection.vue'
import LoadingSelectionSection from '@/sections/LoadingSelectionSection.vue'
import MigrationSection from '@/sections/MigrationSection.vue'
import NewComponentsSection from '@/sections/NewComponentsSection.vue'
import OverlaysSection from '@/sections/OverlaysSection.vue'
import PageBuilderSection from '@/sections/PageBuilderSection.vue'
import SettingsLayoutSection from '@/sections/SettingsLayoutSection.vue'
import SortableTreeSection from '@/sections/SortableTreeSection.vue'
import TablePageLayoutSection from '@/sections/TablePageLayoutSection.vue'
import TechnicalEditorsSection from '@/sections/TechnicalEditorsSection.vue'
import ToolsSection from '@/sections/ToolsSection.vue'
import WorkflowCanvasSection from '@/sections/WorkflowCanvasSection.vue'
import WorkspaceSection from '@/sections/WorkspaceSection.vue'

interface Category {
  key: string
  label: string
  en: string
  icon: string
  description: string
  group: string
  section: Component
}

interface Group {
  key: string
  label: string
  icon: string
  count: number
}

const AffectChatAgentSection = defineAsyncComponent(() => import('@/sections/AffectChatAgentSection.vue'))
const AffectDataCardsSection = defineAsyncComponent(() => import('@/sections/AffectDataCardsSection.vue'))
const AffectControlsSection = defineAsyncComponent(() => import('@/sections/AffectControlsSection.vue'))
const SceneGallerySection = defineAsyncComponent(() => import('@/sections/SceneGallerySection.vue'))
const VisualGallerySection = defineAsyncComponent(() => import('@/sections/VisualGallerySection.vue'))

// 五类内容目录：Components 复用已有组件页，其余目录使用场景与视觉展示页。
const groups: Group[] = [
  { key: 'Components', label: '组件', icon: 'morph:layout-grid', count: 50 },
  { key: 'Screens', label: '整页场景', icon: 'morph:panels-top-left', count: 12 },
  { key: 'Blocks', label: '功能区块', icon: 'morph:layout-grid', count: 11 },
  { key: 'Illustrations', label: '插画', icon: 'morph:shapes', count: 23 },
  { key: 'Motion', label: '动效', icon: 'morph:wand-sparkles', count: 8 },
]

const categories: Category[] = [
  { key: 'affect-chat-agents', label: '对话与智能体', en: 'Chat & Agents · 17', icon: 'morph:bot', group: 'Components', description: '17 个对话与智能体参考功能：消息、引用、评论、任务、记忆、审批与差异审阅。', section: AffectChatAgentSection },
  { key: 'affect-data-cards', label: '卡片与数据', en: 'Cards & Data · 14', icon: 'morph:chart-area', group: 'Components', description: '14 个卡片、数据展示与动作条参考功能，覆盖编辑、对比与撤销。', section: AffectDataCardsSection },
  { key: 'affect-controls', label: '导航、输入与反馈', en: 'Controls · 18', icon: 'morph:sliders-horizontal', group: 'Components', description: '18 个导航、输入与反馈参考功能，覆盖筛选、校验、流程与通知。', section: AffectControlsSection },
  { key: 'scene-gallery', label: '产品场景', en: 'Screens', icon: 'morph:panels-top-left', group: 'Screens', description: '12 个整页场景：助手、文件、集成、CRM、分析、Agent 运行与设置流程。', section: SceneGallerySection },
  { key: 'blocks-gallery', label: '功能区块', en: 'Blocks', icon: 'morph:layout-grid', group: 'Blocks', description: '11 个可操作功能区块：任务看板、指标、计费、应用目录与 AI 工作区。', section: SceneGallerySection },
  { key: 'illustrations-gallery', label: '交互插画', en: 'Illustrations', icon: 'morph:shapes', group: 'Illustrations', description: '23 个本地原创视觉场景，保留 hover、点击、拖动与切换交互。', section: VisualGallerySection },
  { key: 'motion-gallery', label: '动效', en: 'Motion', icon: 'morph:wand-sparkles', group: 'Motion', description: '8 类文字、数字、背景与状态过渡动效，兼容减少动态效果偏好。', section: VisualGallerySection },
  { key: 'extended', label: 'YD 体验实验室', en: 'YD Studio', icon: 'morph:sparkles', group: 'Components', description: '三套外观，共享主题，体验 YD 通用交互组件。', section: ExtendedSection },
  { key: 'effects', label: '动效与装饰', en: 'Motion', icon: 'morph:wand-sparkles', group: 'Components', description: '流光、粒子、翻转和数字变化，让界面鲜活起来。', section: EffectsSection },
  { key: 'tools', label: '交互与工具', en: 'Interaction', icon: 'morph:mouse-pointer-2', group: 'Components', description: '统一 YD 输入、弹层与选中态，体验完整交互流程。', section: ToolsSection },
  { key: 'calendar', label: '日历与时间', en: 'Calendar', icon: 'morph:calendar-days', group: 'Components', description: '日期、月份和年份的单选、范围与弹出选择。', section: CalendarSection },
  { key: 'basic', label: '基础', en: 'Basic', icon: 'morph:box', group: 'Components', description: '按钮、标签、徽章、头像等最基础的视觉元件。', section: BasicSection },
  { key: 'layout', label: '页面布局', en: 'Layout', icon: 'morph:layout-panel-top', group: 'Components', description: '页面头部、分节容器、分割线、固定栏与折叠面板。', section: LayoutSection },
  { key: 'daisy-components', label: '列表、指标与外框', en: 'Lists & Mockups', icon: 'morph:layout-template', group: 'Components', description: '通用列表、统计指标、浏览器/窗口预览及步骤、时间线和终端代码展示。', section: DaisyComponentsSection },
  { key: 'settings-layout', label: '设置页骨架', en: 'Settings', icon: 'morph:settings', group: 'Components', description: '左侧吸顶导航 + 右侧分区面板的设置页版式，窄屏自动降级为横向标签。', section: SettingsLayoutSection },
  { key: 'enhanced-table', label: '增强表格', en: 'Enhanced Table', icon: 'morph:table', group: 'Components', description: '图文并茏的成员列表：头像组合列、状态徽章与行内操作。', section: EnhancedTableSection },
  { key: 'form', label: '表单', en: 'Form', icon: 'morph:square-pen', group: 'Components', description: '输入框、多行文本、选择器、开关等表单控件。', section: FormSection },
  { key: 'form-controls', label: '表单进阶', en: 'Form Controls', icon: 'morph:sliders-horizontal', group: 'Components', description: '密码强度、验证码、数字、滑块与单选/多选组。', section: FormControlsSection },
  { key: 'date-time', label: '日期时间选择', en: 'Date & Time', icon: 'morph:alarm-clock', group: 'Components', description: '日期、周月季年模式与时间范围的选择器。', section: DateTimeSection },
  { key: 'files', label: '文件与图片上传', en: 'Upload', icon: 'morph:cloud-upload', group: 'Components', description: '拖拽、粘贴与文件夹上传，进度与预览管理。', section: FilesSection },
  { key: 'migration', label: '表单与动作契约', en: 'Form Contract', icon: 'morph:list-checks', group: 'Components', description: '统一字段校验、禁用继承、标签输入、键盘评分与自定布局动作。', section: MigrationSection },
  { key: 'collection-controls', label: '集合与文件选择', en: 'Selection', icon: 'morph:list-filter', group: 'Components', description: '搜索多选、已选内容回显与本地文件选择，保持统一的键盘和表单行为。', section: CollectionControlsSection },
  { key: 'technical-editors', label: '内容与代码编辑', en: 'Editors', icon: 'morph:file-code', group: 'Components', description: '主题化 Markdown 与代码编辑，支持受控内容、只读和禁用状态。', section: TechnicalEditorsSection },
  { key: 'sortable-tree', label: '拖拽与层级重排', en: 'Sortable Tree', icon: 'morph:list-tree', group: 'Components', description: '受控拖拽、键盘重排与延迟加载，保留字符串标识和层级元数据。', section: SortableTreeSection },
  { key: 'workflow-canvas', label: '工作流画布', en: 'Workflow Canvas', icon: 'morph:workflow', group: 'Components', description: '可缩放流程画布、受控节点连线、自定义节点与连接点。', section: WorkflowCanvasSection },
  { key: 'workspace', label: '工作区与多标签', en: 'Workspace', icon: 'morph:panel-left-open', group: 'Components', description: 'VS Code 式工作区外壳：活动栏、可拖拽分栏、多文件标签与状态栏，窄屏退化为单面板。', section: WorkspaceSection },
  { key: 'creatable-selection', label: '可创建搜索选择', en: 'Creatable Selection', icon: 'morph:list-plus', group: 'Components', description: '搜索候选不存在时由调用方明确接受新编码。', section: ComboboxCreateSection },
  { key: 'page-builder', label: '页面可视化构建', en: 'Page Builder', icon: 'morph:panels-top-left', group: 'Components', description: '受控 HTML、CSS 和构建项目数据，支持主题、上传与实例生命周期。', section: PageBuilderSection },
  { key: 'list-toolbar', label: '列表工具栏', en: 'List Toolbar', icon: 'morph:list-filter', group: 'Components', description: '管理列表页统一筛选区：限宽搜索、计数即筛选的状态胶囊、宽度受控的下拉筛选。', section: ListToolbarSection },
  { key: 'table-page-layout', label: '表格页版式', en: 'Table Page Layout', icon: 'morph:layout-list', group: 'Components', description: '表格页标准版式：页头只放标题、搜索默认折叠展开、Tab 在左操作在右并自动折叠不常用项。', section: TablePageLayoutSection },
  { key: 'data', label: '数据展示', en: 'Data Display', icon: 'morph:chart-area', group: 'Components', description: '卡片、页签、分页、进度条、滚动区域等数据承载组件。', section: DataSection },
  { key: 'data-display', label: '详情与数据标识', en: 'Details', icon: 'morph:file-text', group: 'Components', description: '描述列表、趋势标识、桌面表格与筛选区容器。', section: DataDisplaySection },
  { key: 'loading-selection', label: '加载与列表选择', en: 'Loading & Selection', icon: 'morph:inbox', group: 'Components', description: '局部遮罩、函数式文件选择与跨断点保留的表格选择。', section: LoadingSelectionSection },
  { key: 'feedback', label: '反馈', en: 'Feedback', icon: 'morph:message-circle', group: 'Components', description: '气泡提示、弹窗、警告提示、图片预览与空态插画等交互反馈组件。', section: FeedbackSection },
  { key: 'overlays', label: '浮层与菜单', en: 'Overlays', icon: 'morph:layers', group: 'Components', description: '抽屉、弹出框、悬浮卡片、下拉与右键菜单。', section: OverlaysSection },
  { key: 'dom-action', label: '动态挂载动作', en: 'DOM Actions', icon: 'morph:mouse-pointer-click', group: 'Components', description: '向画布和主题容器挂载真正的 YD 动作，支持状态更新与完整销毁。', section: DomActionSection },
  { key: 'chat', label: '聊天元件', en: 'Chat Elements', icon: 'morph:messages-square', group: 'Components', description: '欢迎语、建议、发送器、引用、过程与回答操作条。', section: ChatSection },
  { key: 'ai', label: 'AI 助手', en: 'Agent Studio', icon: 'morph:bot', group: 'Components', description: '从对话到交付：Harness、Skills、MCP、工作区与可检查的代码变更。', section: AiSection },
  { key: 'new-components', label: '新组件', en: 'New Components', icon: 'morph:component', group: 'Components', description: '确认、占位、颜色、树选择、结果、倒计时与导航辅助组件。', section: NewComponentsSection },
]

const requestedCategory = new URLSearchParams(window.location.search).get('category')
const requestedGroup = new URLSearchParams(window.location.search).get('group')
const initialCategory = requestedCategory && categories.some(item => item.key === requestedCategory)
  ? requestedCategory
  : requestedGroup && groups.some(group => group.key === requestedGroup)
    ? categories.find(item => item.group === requestedGroup)?.key ?? 'affect-chat-agents'
    : 'affect-chat-agents'
// 专用预览入口使用标签页内的视图状态，避免其他预览切页时中断当前演示。
const active = requestedCategory || requestedGroup
  ? ref(initialCategory)
  : useStorage('component-showcase:category', initialCategory)

const activeCategory = computed(() => categories.find(item => item.key === active.value) ?? categories[0])
const activeGroup = computed(() => groups.find(group => group.key === activeCategory.value.group) ?? groups[0])

const keyword = ref('')
const railCollapsed = useStorage('component-showcase:rail-collapsed', false)
const mobileNavOpen = ref(false)
const subCollapsed = ref<Record<string, boolean>>({ Components: categories.find(item => item.key === initialCategory)?.group !== 'Components' })

/** 按关键字过滤组件；有关键字时子侧栏平铺展示全部命中项 */
const filteredCategories = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) {
    return null
  }
  return categories.filter(item =>
    item.label.toLowerCase().includes(kw)
    || item.en.toLowerCase().includes(kw)
    || item.description.toLowerCase().includes(kw)
    || item.group.toLowerCase().includes(kw),
  )
})

/** 子侧栏当前展示的分组（有关键字时为命中项所在分组的集合） */
const visibleGroups = computed(() => {
  const filtered = filteredCategories.value
  if (!filtered) {
    return groups.map(group => ({ group, items: categories.filter(item => item.group === group.key) }))
  }
  return groups
    .map(group => ({ group, items: filtered.filter(item => item.group === group.key) }))
    .filter(entry => entry.items.length > 0)
})

const content = ref<HTMLElement>()
watch(active, () => content.value?.scrollTo({ top: 0 }), { flush: 'post' })

function selectCategory(key: string) {
  active.value = key
  mobileNavOpen.value = false
}

function selectGroup(group: Group) {
  const first = categories.find(item => item.group === group.key)
  if (first) {
    active.value = first.key
    subCollapsed.value[group.key] = false
    if (group.key !== 'Components') {
      subCollapsed.value.Components = true
    }
  }
  mobileNavOpen.value = false
  keyword.value = ''
}

const { preset: appearance } = useShowcaseAppearance()
function setAppearance(value: string) {
  appearance.value = value
}
</script>

<template>
  <div class="layout">
    <!-- 图标栏（主导航） -->
    <nav class="rail" aria-label="组件分组">
      <div class="rail__logo" title="YuDreamAdmin 组件">
        <YdIcon name="morph:layers" />
      </div>
      <div class="rail__groups">
        <YdAction
          v-for="group in groups"
          :key="group.key"
          type="button"
          class="rail__item yd-focus"
          :title="`${group.label} (${group.count})`"
          :aria-label="`${group.label}，${group.count} 项`"
          :aria-current="activeGroup.key === group.key ? 'true' : undefined"
          :class="{ 'is-active': activeGroup.key === group.key }"
          @click="selectGroup(group)"
        >
          <YdIcon :name="group.icon" />
        </YdAction>
      </div>
      <div class="rail__footer">
        <YdAction
          type="button"
          class="rail__item yd-focus"
          :title="railCollapsed ? '展开子侧栏' : '收起子侧栏'"
          :aria-label="railCollapsed ? '展开子侧栏' : '收起子侧栏'"
          @click="railCollapsed = !railCollapsed"
        >
          <YdIcon :name="railCollapsed ? 'morph:panel-left-open' : 'morph:panel-left-close'" />
        </YdAction>
      </div>
    </nav>

    <!-- 子侧栏（组内组件） -->
    <aside v-if="!railCollapsed" class="sub-sidebar" :class="{ 'is-mobile-open': mobileNavOpen }">
      <div class="sub-sidebar__search">
        <YdInput v-model="keyword" clearable placeholder="搜索组件与场景">
          <template #start>
            <YdIcon name="morph:search" />
          </template>
        </YdInput>
      </div>

      <nav class="sub-sidebar__nav">
        <template v-if="filteredCategories">
          <div class="sub-sidebar__group-caption">
            匹配结果（{{ filteredCategories.length }}）
          </div>
          <YdAction
            v-for="item in filteredCategories"
            :key="item.key"
            type="button"
            class="sub-sidebar__item yd-focus"
            :aria-current="active === item.key ? 'page' : undefined"
            :class="{ 'is-active': active === item.key }"
            @click="selectCategory(item.key)"
          >
            <YdIcon :name="item.icon" class="sub-sidebar__item-icon" />
            <span class="sub-sidebar__item-text">
              <span class="sub-sidebar__item-label">{{ item.label }}</span>
              <span class="sub-sidebar__item-en">{{ item.en }}</span>
            </span>
            <span v-if="item.group !== 'Components'" class="sub-sidebar__item-group">{{ groups.find(group => group.key === item.group)?.count }}</span>
          </YdAction>
          <div v-if="!filteredCategories.length" class="sub-sidebar__empty">
            没有匹配的组件
          </div>
        </template>

        <template v-else>
          <template v-for="entry in visibleGroups" :key="entry.group.key">
            <YdAction
              type="button"
              class="sub-sidebar__head yd-focus"
              :aria-expanded="!subCollapsed[entry.group.key]"
              @click="subCollapsed[entry.group.key] = !subCollapsed[entry.group.key]"
            >
              <span class="sub-sidebar__head-label">{{ entry.group.label }}</span>
              <span class="sub-sidebar__head-count">{{ entry.items.length }}</span>
              <YdIcon
                name="morph:chevron-down"
                class="sub-sidebar__head-chevron"
                :class="{ 'is-collapsed': subCollapsed[entry.group.key] }"
              />
            </YdAction>
            <template v-if="!subCollapsed[entry.group.key]">
              <YdAction
                v-for="item in entry.items"
                :key="item.key"
                type="button"
                class="sub-sidebar__item yd-focus"
                :aria-current="active === item.key ? 'page' : undefined"
                :class="{ 'is-active': active === item.key }"
                @click="selectCategory(item.key)"
              >
                <YdIcon :name="item.icon" class="sub-sidebar__item-icon" />
                <span class="sub-sidebar__item-text">
                  <span class="sub-sidebar__item-label">{{ item.label }}</span>
                  <span class="sub-sidebar__item-en">{{ item.en }}</span>
                </span>
              </YdAction>
            </template>
          </template>
        </template>
      </nav>
    </aside>

    <!-- 内容区 -->
    <main ref="content" class="content">
      <header class="topbar">
        <YdButton class="mobile-menu" size="icon-sm" variant="ghost" :aria-label="mobileNavOpen ? '关闭目录' : '打开目录'" :aria-expanded="mobileNavOpen" @click="mobileNavOpen = !mobileNavOpen">
          <YdIcon :name="mobileNavOpen ? 'morph:x' : 'morph:menu'" />
        </YdButton>
        <div class="topbar__crumb">
          <YdIcon name="morph:house" />
          <span>组件</span>
          <YdIcon name="morph:chevron-right" class="topbar__crumb-sep" />
          <span>{{ activeCategory.group }}</span>
          <YdIcon name="morph:chevron-right" class="topbar__crumb-sep" />
          <span class="topbar__crumb-current">{{ activeCategory.label }}</span>
        </div>
        <div class="topbar__actions">
          <div class="topbar__appearances" role="group" aria-label="外观切换">
            <YdButton
              v-for="item in [{ value: 'vibrant', label: '灵动' }, { value: 'calm', label: '雅致' }, { value: 'compact', label: '紧凑' }]"
              :key="item.value"
              :variant="appearance === item.value ? 'default' : 'outline'"
              size="sm"
              @click="setAppearance(item.value)"
            >
              {{ item.label }}
            </YdButton>
          </div>
          <ThemePanel />
        </div>
      </header>

      <div class="content__header">
        <div class="content__heading">
          <YdIcon :name="activeCategory.icon" class="content__heading-icon" />
          <h1>{{ activeCategory.label }}</h1>
          <span class="content__heading-en">{{ activeCategory.en }}</span>
        </div>
        <p>{{ activeCategory.description }}</p>
      </div>

      <div class="content__body">
        <component :is="activeCategory.section" :showcase-group="activeCategory.group" />
      </div>
    </main>

    <YdToast />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

/* 图标栏 */
.rail {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 56px;
  background: var(--g-sub-sidebar-bg, oklch(var(--background)));
  border-right: 1px solid var(--color-border-2);
}

.rail__logo {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  margin: 12px auto 8px;
  font-size: 18px;
  color: oklch(var(--primary-foreground));
  background: oklch(var(--primary));
  border-radius: var(--yd-control-radius);
}

.rail__groups {
  display: grid;
  flex: 1;
  gap: 4px;
  align-content: start;
  padding: 4px 8px;
}

.rail__item {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  margin: 0 auto;
  font-size: 18px;
  color: var(--g-sub-sidebar-menu-color, var(--color-text-2));
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yd-control-radius);
  transition: background var(--yd-motion-duration-fast) var(--yd-motion-ease), color var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.rail__item:hover {
  color: var(--g-sub-sidebar-menu-hover-color, var(--color-text-1));
  background: var(--g-sub-sidebar-menu-hover-bg, var(--color-fill-2));
}

.rail__item.is-active {
  color: oklch(var(--primary-foreground));
  background: oklch(var(--primary));
}

.rail__footer {
  padding: 8px;
}

/* 子侧栏 */
.sub-sidebar {
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  width: 208px;
  background: var(--g-sub-sidebar-bg, oklch(var(--background)));
  border-right: 1px solid var(--color-border-2);
}

.sub-sidebar__search {
  padding: 10px 12px 6px;
}

.sub-sidebar__nav {
  display: block;
  flex: 1;
  padding: 4px 8px 16px;
  overflow-y: auto;
}

.sub-sidebar__group-caption {
  padding: 10px 12px 6px;
  font-size: 12px;
  color: var(--color-text-3);
}

.sub-sidebar__head {
  display: flex;
  gap: 6px;
  align-items: center;
  width: 100%;
  padding: 10px 12px 4px;
  font: inherit;
  cursor: pointer;
  background: transparent;
  border: 0;
}

.sub-sidebar__head-label {
  flex: 1;
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-3);
  text-align: left;
}

.sub-sidebar__head-count {
  font-size: 11px;
  color: var(--color-text-4);
}

.sub-sidebar__head-chevron {
  font-size: 12px;
  color: var(--color-text-4);
  transition: transform var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.sub-sidebar__head-chevron.is-collapsed {
  transform: rotate(-90deg);
}

.sub-sidebar__item {
  display: flex;
  gap: 10px;
  align-items: center;
  width: 100%;
  min-height: 36px;
  padding: 7px 12px;
  font: inherit;
  color: var(--g-sub-sidebar-menu-color, var(--color-text-2));
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: var(--yd-control-radius);
  transition: background var(--yd-motion-duration-fast) var(--yd-motion-ease), color var(--yd-motion-duration-fast) var(--yd-motion-ease);
}

.sub-sidebar__item:hover {
  color: var(--g-sub-sidebar-menu-hover-color, var(--color-text-1));
  background: var(--g-sub-sidebar-menu-hover-bg, var(--color-fill-2));
}

.sub-sidebar__item.is-active {
  color: var(--g-sub-sidebar-menu-active-color, oklch(var(--primary)));
  background: var(--g-sub-sidebar-menu-active-bg, oklch(var(--primary) / 0.1));
}

.sub-sidebar__item-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.sub-sidebar__item-text {
  display: grid;
  gap: 1px;
  min-width: 0;
  flex: 1;
}

.sub-sidebar__item-label {
  overflow: hidden;
  font-size: 13.5px;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-sidebar__item-en {
  overflow: hidden;
  font-size: 11px;
  color: var(--color-text-3);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sub-sidebar__item.is-active .sub-sidebar__item-en {
  color: inherit;
}

.sub-sidebar__item-group {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--color-text-3);
}

.sub-sidebar__empty {
  padding: 24px 8px;
  font-size: 13px;
  color: var(--color-text-3);
  text-align: center;
}

/* 内容区 */
.content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  gap: 16px;
  align-items: center;
  justify-content: space-between;
  height: 52px;
  padding: 0 24px;
  background: oklch(var(--background));
  border-bottom: 1px solid var(--color-border-2);
}

.topbar__crumb {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 13px;
  color: var(--color-text-3);
}

.topbar__crumb-sep {
  font-size: 12px;
  color: var(--color-text-4);
}

.topbar__crumb-current {
  font-weight: 500;
  color: var(--color-text-1);
}

.topbar__actions {
  display: flex;
  gap: 12px;
  align-items: center;
}

.topbar__appearances {
  display: flex;
  gap: 6px;
}

.content__header {
  padding: 20px 28px 0;
  max-width: 1148px;
}

.content__heading {
  display: flex;
  gap: 10px;
  align-items: baseline;
}

.content__heading-icon {
  align-self: center;
  font-size: 22px;
  color: oklch(var(--primary));
}

.content__heading h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.content__heading-en {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-3);
}

.content__header p {
  margin: 8px 0 0;
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-text-3);
}

.content__body {
  display: grid;
  gap: var(--yd-workspace-gap);
  max-width: 1120px;
  padding: var(--yd-workspace-gap) 28px 56px;
}

.mobile-menu {
  display: none;
}

@media (width <= 768px) {
  .sub-sidebar {
    position: absolute;
    top: 0;
    left: 56px;
    z-index: 20;
    display: none;
    height: 100%;
    box-shadow: 0 8px 24px rgb(0 0 0 / 12%);
  }

  .sub-sidebar.is-mobile-open {
    display: flex;
  }

  .mobile-menu {
    display: inline-flex;
    flex-shrink: 0;
  }

  .topbar {
    flex-wrap: wrap;
    height: auto;
    padding: 10px 16px;
  }

  .topbar__appearances {
    display: none;
  }

  .content__header {
    padding: 16px 16px 0;
  }

  .content__body {
    padding: 16px;
  }

  .content__heading-en {
    display: none;
  }
}
</style>
