<script setup lang="ts">
import type { Component } from 'vue'
import { useStorage } from '@vueuse/core'
import { computed, ref, watch } from 'vue'
import ThemePanel from '@/components/ThemePanel.vue'
import { useShowcaseAppearance } from '@/composables/useShowcaseAppearance'
import AiSection from '@/sections/AiSection.vue'
import BasicSection from '@/sections/BasicSection.vue'
import CalendarSection from '@/sections/CalendarSection.vue'
import DataSection from '@/sections/DataSection.vue'
import EffectsSection from '@/sections/EffectsSection.vue'
import ExtendedSection from '@/sections/ExtendedSection.vue'
import FeedbackSection from '@/sections/FeedbackSection.vue'
import FormSection from '@/sections/FormSection.vue'
import ToolsSection from '@/sections/ToolsSection.vue'

interface Category {
  key: string
  label: string
  en: string
  icon: string
  description: string
  group: string
  section: Component
}

const categories: Category[] = [
  { key: 'extended', label: 'YD 体验实验室', en: 'YD Studio', icon: 'morph:sparkles', group: '全新体验', description: '三套外观，共享主题，体验 YD 通用交互组件。', section: ExtendedSection },
  { key: 'effects', label: '动效与装饰', en: 'Motion', icon: 'morph:wand-sparkles', group: '全新体验', description: '流光、粒子、翻转和数字变化，让界面鲜活起来。', section: EffectsSection },
  { key: 'tools', label: '交互与工具', en: 'Interaction', icon: 'morph:mouse-pointer-2', group: '全新体验', description: '统一 YD 输入、弹层与选中态，体验完整交互流程。', section: ToolsSection },
  { key: 'calendar', label: '日历与时间', en: 'Calendar', icon: 'morph:calendar-days', group: '全新体验', description: '日期、月份和年份的单选、范围与弹出选择。', section: CalendarSection },
  { key: 'basic', label: '基础', en: 'Basic', icon: 'morph:layout-grid', group: '通用', description: '按钮、标签、徽章、头像等最基础的视觉元件。', section: BasicSection },
  { key: 'form', label: '表单', en: 'Form', icon: 'morph:square-pen', group: '数据录入', description: '输入框、多行文本、选择器、开关等表单控件。', section: FormSection },
  { key: 'data', label: '数据展示', en: 'Data Display', icon: 'morph:layout-grid', group: '数据展示', description: '卡片、页签、分页、进度条、滚动区域等数据承载组件。', section: DataSection },
  { key: 'feedback', label: '反馈', en: 'Feedback', icon: 'morph:bell', group: '反馈', description: '气泡提示、弹窗、图片预览等交互反馈组件。', section: FeedbackSection },
  { key: 'ai', label: 'AI 助手', en: 'Agent Studio', icon: 'morph:bot', group: 'AI 助手', description: '从对话到交付：Harness、Skills、MCP、工作区与可检查的代码变更。', section: AiSection },
]

const requestedCategory = new URLSearchParams(window.location.search).get('category')
// 专用预览入口使用标签页内的视图状态，避免其他预览切页时中断当前演示。
const active = requestedCategory && categories.some(item => item.key === requestedCategory)
  ? ref(requestedCategory)
  : useStorage('component-showcase:category', 'extended')
const content = ref<HTMLElement>()
watch(active, () => content.value?.scrollTo({ top: 0 }), { flush: 'post' })
const { preset: appearance } = useShowcaseAppearance()
function setAppearance(value: string) {
  appearance.value = value
}
const keyword = ref('')
const collapsed = ref(false)

const groups = computed(() => {
  const result: { name: string, items: Category[] }[] = []
  const kw = keyword.value.trim().toLowerCase()
  for (const category of categories) {
    const matched = !kw
      || category.label.toLowerCase().includes(kw)
      || category.en.toLowerCase().includes(kw)
      || category.description.toLowerCase().includes(kw)
    if (!matched) {
      continue
    }
    const group = result.find(item => item.name === category.group)
    if (group) {
      group.items.push(category)
    }
    else {
      result.push({ name: category.group, items: [category] })
    }
  }
  return result
})

const activeCategory = computed(() => categories.find(item => item.key === active.value) ?? categories[0])
</script>

<template>
  <div class="layout">
    <aside class="sidebar" :class="{ 'is-collapsed': collapsed }">
      <div class="sidebar__brand">
        <div class="sidebar__logo">
          <YdIcon name="morph:layers" />
        </div>
        <div class="sidebar__brand-text">
          <div class="sidebar__title">
            YuDream Admin
          </div>
          <div class="sidebar__subtitle">
            YuDreamAdmin Components
          </div>
        </div>
      </div>

      <div v-if="!collapsed" class="sidebar__search">
        <YdInput v-model="keyword" clearable placeholder="搜索组件" class="w-full">
          <template #start>
            <YdIcon name="morph:search" />
          </template>
        </YdInput>
      </div>

      <nav class="sidebar__nav">
        <template v-for="group in groups" :key="group.name">
          <div class="sidebar__group">
            {{ group.name }}
          </div>
          <button
            v-for="item in group.items"
            :key="item.key"
            type="button"
            :title="collapsed ? item.label : undefined"
            class="sidebar__item yd-focus"
            :aria-label="item.label"
            :aria-current="active === item.key ? 'page' : undefined"
            :class="{ 'is-active': active === item.key }"
            @click="active = item.key"
          >
            <YdIcon :name="item.icon" class="sidebar__item-icon" />
            <span class="sidebar__item-text">
              <span class="sidebar__item-label">{{ item.label }}</span>
              <span class="sidebar__item-en">{{ item.en }}</span>
            </span>
          </button>
        </template>

        <div v-if="!groups.length" class="sidebar__empty">
          没有匹配的组件
        </div>
      </nav>

      <footer class="sidebar__footer">
        <YdButton variant="ghost" size="icon-sm" :aria-label="collapsed ? '展开侧栏' : '折叠侧栏'" @click="collapsed = !collapsed">
          <YdIcon :name="collapsed ? 'morph:panel-left-open' : 'morph:panel-left-close'" />
        </YdButton>
        <span v-if="!collapsed">组件预览</span>
      </footer>
    </aside>

    <main ref="content" class="content">
      <header class="content__header">
        <div class="content__header-main">
          <div class="content__crumb">
            <YdIcon name="morph:house" />
            <span>组件</span>
            <YdIcon name="morph:chevron-right" class="content__crumb-sep" />
            <span>{{ activeCategory.group }}</span>
            <YdIcon name="morph:chevron-right" class="content__crumb-sep" />
            <span class="content__crumb-current">{{ activeCategory.label }}</span>
          </div>
          <div class="content__heading">
            <YdIcon :name="activeCategory.icon" class="content__heading-icon" />
            <h1>{{ activeCategory.label }}</h1>
            <span class="content__heading-en">{{ activeCategory.en }}</span>
          </div>
          <p>{{ activeCategory.description }}</p>
        </div>
        <div class="theme-controls">
          <div class="demo-row">
            <YdButton v-for="item in [{ value: 'vibrant', label: '灵动' }, { value: 'calm', label: '雅致' }, { value: 'compact', label: '紧凑' }]" :key="item.value" :variant="appearance === item.value ? 'default' : 'outline'" size="sm" @click="setAppearance(item.value)">
              {{ item.label }}
            </YdButton>
          </div>
          <ThemePanel />
        </div>
      </header>

      <div class="content__body">
        <component :is="activeCategory.section" />
      </div>
    </main>

    <YdToast />
  </div>
</template>

<style scoped>
.theme-controls {
  display: grid;
  gap: 10px;
  justify-items: end;
}

.layout {
  display: flex;
  height: 100vh;
  color: var(--color-text-1);
  background: var(--color-fill-1);
}

/* 侧边栏 */
.sidebar {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  width: var(--g-sub-sidebar-width, 220px);
  background: var(--g-sub-sidebar-bg);
  border-right: 1px solid var(--color-border-2);
}

.sidebar__brand {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 20px 18px 16px;
}

.sidebar__logo {
  display: grid;
  flex-shrink: 0;
  place-items: center;
  width: 36px;
  height: 36px;
  font-size: 18px;
  color: oklch(var(--primary-foreground));
  background: oklch(var(--primary));
  border-radius: 10px;
  box-shadow: none;
}

.sidebar__title {
  font-size: 15px;
  font-weight: 650;
  line-height: 1.25;
}

.sidebar__subtitle {
  font-size: 11px;
  color: var(--color-text-3);
}

.sidebar__search {
  padding: 4px 14px 12px;
}

.sidebar__nav {
  display: grid;
  flex: 1;
  gap: 2px;
  align-content: start;
  padding: 4px 12px 16px;
  overflow-y: auto;
}

.sidebar__group {
  padding: 16px 8px 6px;
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-3);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.sidebar__item {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  font: inherit;
  color: var(--color-text-2);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-radius: 8px;
  transition: background 0.15s, color 0.15s;
}

.sidebar__item:hover {
  color: var(--color-text-1);
  background: var(--color-fill-2);
}

.sidebar__item.is-active {
  color: var(--g-sub-sidebar-menu-active-color);
  background: var(--g-sub-sidebar-menu-active-bg);
}

.sidebar__item-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.sidebar__item-text {
  display: grid;
  gap: 1px;
  min-width: 0;
}

.sidebar__item-label {
  font-size: 13.5px;
  font-weight: 500;
}

.sidebar__item-en {
  font-size: 11px;
  color: var(--color-text-3);
}

.sidebar__item.is-active .sidebar__item-en {
  color: inherit;
}

.sidebar__empty {
  padding: 24px 8px;
  font-size: 13px;
  color: var(--color-text-3);
  text-align: center;
}

.sidebar__footer {
  padding: 12px 18px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--color-text-3);
  border-top: 1px solid var(--color-border-2);
}

/* 内容区 */
.content {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
}

.content__header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  gap: 16px;
  align-items: flex-start;
  justify-content: space-between;
  padding: 20px 36px 18px;
  background: var(--color-bg-1);
  border-bottom: 1px solid var(--color-border-2);
}

.content__header-main {
  min-width: 0;
}

.content__crumb {
  display: flex;
  gap: 4px;
  align-items: center;
  font-size: 12px;
  color: var(--color-text-3);
}

.content__crumb-sep {
  color: var(--color-text-4);
}

.content__crumb-current {
  color: var(--color-text-2);
}

.content__heading {
  display: flex;
  gap: 10px;
  align-items: baseline;
  margin-top: 10px;
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
  padding: var(--yd-workspace-gap) 36px 56px;
}
</style>

<style scoped>
/* 与宿主 Menu/item、SubSidebar 使用相同语义变量和尺寸。 */
.sidebar {
  width: var(--g-sub-sidebar-width, 220px);
  background: var(--g-sub-sidebar-bg, oklch(var(--background)));
  transition: width var(--yd-motion-duration-normal) var(--yd-motion-ease);
}

.sidebar__brand {
  height: 64px;
  padding: 12px 16px;
}

.sidebar__logo {
  width: 32px;
  height: 32px;
  color: oklch(var(--primary-foreground));
  background: oklch(var(--primary));
  border-radius: var(--yd-control-radius);
  box-shadow: none;
}

.sidebar__title {
  font-size: 16px;
  white-space: nowrap;
}

.sidebar__subtitle,
.sidebar__item-en {
  display: none;
}

.sidebar__nav {
  gap: 4px;
  padding-inline: 8px;
}

.sidebar__group {
  padding: 14px 12px 6px;
  font-size: 12px;
}

.sidebar__item {
  gap: 12px;
  min-height: 44px;
  padding: 10px 12px;
  color: var(--g-sub-sidebar-menu-color);
  border-radius: var(--yd-control-radius);
}

.sidebar__item:hover {
  color: var(--g-sub-sidebar-menu-hover-color);
  background: var(--g-sub-sidebar-menu-hover-bg);
}

.sidebar__item.is-active {
  color: var(--g-sub-sidebar-menu-active-color);
  background: var(--g-sub-sidebar-menu-active-bg);
}

.sidebar__item-icon {
  font-size: 18px;
}

.sidebar__item-label {
  font-size: 14px;
}

.sidebar__footer {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 8px;
}

.sidebar.is-collapsed {
  width: var(--g-sub-sidebar-collapse-width, 64px);
}

.is-collapsed .sidebar__brand-text,
.is-collapsed .sidebar__item-text,
.is-collapsed .sidebar__group {
  display: none;
}

.is-collapsed .sidebar__brand {
  justify-content: center;
  padding: 8px;
}

.is-collapsed .sidebar__item {
  justify-content: center;
}

.content__heading-icon {
  color: oklch(var(--primary));
}

@media (width <= 768px) {
  .sidebar {
    width: 56px;
  }

  .sidebar__brand-text,
  .sidebar__search,
  .sidebar__item-text,
  .sidebar__group,
  .sidebar__footer {
    display: none;
  }

  .sidebar__brand {
    padding: 8px;
  }

  .sidebar__item {
    justify-content: center;
    padding: 10px;
  }

  .sidebar__nav {
    padding: 4px;
  }

  .content__header {
    flex-wrap: wrap;
    padding: 16px;
  }

  .content__body {
    padding: 16px;
  }

  .content__heading-en {
    display: none;
  }
}
</style>
