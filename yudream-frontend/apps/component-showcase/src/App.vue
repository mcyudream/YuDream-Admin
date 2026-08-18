<script setup lang="ts">
import type { Component } from 'vue'
import { computed, ref } from 'vue'
import ThemePanel from '@/components/ThemePanel.vue'
import AiSection from '@/sections/AiSection.vue'
import BasicSection from '@/sections/BasicSection.vue'
import DataSection from '@/sections/DataSection.vue'
import FeedbackSection from '@/sections/FeedbackSection.vue'
import FormSection from '@/sections/FormSection.vue'

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
  { key: 'basic', label: '基础', en: 'Basic', icon: 'i-ri:apps-2-line', group: '通用', description: '按钮、标签、徽章、头像等最基础的视觉元件。', section: BasicSection },
  { key: 'form', label: '表单', en: 'Form', icon: 'i-ri:edit-box-line', group: '数据录入', description: '输入框、多行文本、选择器、开关等表单控件。', section: FormSection },
  { key: 'data', label: '数据展示', en: 'Data Display', icon: 'i-ri:layout-grid-line', group: '数据展示', description: '卡片、页签、分页、进度条、滚动区域等数据承载组件。', section: DataSection },
  { key: 'feedback', label: '反馈', en: 'Feedback', icon: 'i-ri:notification-2-line', group: '反馈', description: '气泡提示、弹窗、图片预览等交互反馈组件。', section: FeedbackSection },
  { key: 'ai', label: 'AI 助手', en: 'AI Chat', icon: 'i-ri:sparkling-2-line', group: 'AI 助手', description: '会话、消息气泡、输入区、附件、思考链、动作与 AG-UI 流式元件。', section: AiSection },
]

const active = ref('basic')
const keyword = ref('')

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
    <aside class="sidebar">
      <div class="sidebar__brand">
        <div class="sidebar__logo">
          <FaIcon name="i-ri:stack-line" />
        </div>
          <div class="sidebar__brand-text">
          <div class="sidebar__title">
            余梦 YuDreamAdmin 组件
          </div>
          <div class="sidebar__subtitle">
            YuDreamAdmin Components
          </div>
        </div>
      </div>

      <div class="sidebar__search">
        <FaInput v-model="keyword" clearable placeholder="搜索组件" class="w-full">
          <template #start>
            <FaIcon name="i-ri:search-line" />
          </template>
        </FaInput>
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
            class="sidebar__item"
            :class="{ 'is-active': active === item.key }"
            @click="active = item.key"
          >
            <FaIcon :name="item.icon" class="sidebar__item-icon" />
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
        余梦组件示例 · 静态数据 · 不连后端
      </footer>
    </aside>

    <main class="content">
      <header class="content__header">
        <div class="content__header-main">
          <div class="content__crumb">
            <FaIcon name="i-ri:home-4-line" />
            <span>组件</span>
            <FaIcon name="i-ri:arrow-right-s-line" class="content__crumb-sep" />
            <span>{{ activeCategory.group }}</span>
            <FaIcon name="i-ri:arrow-right-s-line" class="content__crumb-sep" />
            <span class="content__crumb-current">{{ activeCategory.label }}</span>
          </div>
          <div class="content__heading">
            <FaIcon :name="activeCategory.icon" class="content__heading-icon" />
            <h1>{{ activeCategory.label }}</h1>
            <span class="content__heading-en">{{ activeCategory.en }}</span>
          </div>
          <p>{{ activeCategory.description }}</p>
        </div>
        <ThemePanel />
      </header>

      <div class="content__body">
        <component :is="activeCategory.section" />
      </div>
    </main>

    <FaToast />
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  background: var(--color-fill-1);
  color: var(--color-text-1);
}

/* 侧边栏 */
.sidebar {
  display: flex;
  width: 264px;
  flex-shrink: 0;
  flex-direction: column;
  border-right: 1px solid var(--color-border-2);
  background: var(--color-bg-2);
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 18px 16px;
}

.sidebar__logo {
  display: grid;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  place-items: center;
  border-radius: 10px;
  background: rgb(var(--primary-6));
  color: #fff;
  font-size: 18px;
  box-shadow: 0 4px 10px rgb(var(--primary-6) / 30%);
}

.sidebar__title {
  font-size: 15px;
  font-weight: 650;
  line-height: 1.25;
}

.sidebar__subtitle {
  color: var(--color-text-3);
  font-size: 11px;
}

.sidebar__search {
  padding: 4px 14px 12px;
}

.sidebar__nav {
  display: grid;
  flex: 1;
  align-content: start;
  gap: 2px;
  overflow-y: auto;
  padding: 4px 12px 16px;
}

.sidebar__group {
  padding: 16px 8px 6px;
  color: var(--color-text-3);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--color-text-2);
  cursor: pointer;
  font: inherit;
  text-align: left;
  transition: background 0.15s, color 0.15s;
}

.sidebar__item:hover {
  background: var(--color-fill-2);
  color: var(--color-text-1);
}

.sidebar__item.is-active {
  background: rgba(var(--primary-6), 0.1);
  color: rgb(var(--primary-6));
}

.sidebar__item-icon {
  flex-shrink: 0;
  font-size: 16px;
}

.sidebar__item-text {
  display: grid;
  min-width: 0;
  gap: 1px;
}

.sidebar__item-label {
  font-size: 13.5px;
  font-weight: 500;
}

.sidebar__item-en {
  color: var(--color-text-3);
  font-size: 11px;
}

.sidebar__item.is-active .sidebar__item-en {
  color: rgba(var(--primary-6), 0.65);
}

.sidebar__empty {
  padding: 24px 8px;
  color: var(--color-text-3);
  font-size: 13px;
  text-align: center;
}

.sidebar__footer {
  padding: 12px 18px;
  border-top: 1px solid var(--color-border-2);
  color: var(--color-text-3);
  font-size: 11px;
  line-height: 1.5;
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
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 36px 18px;
  border-bottom: 1px solid var(--color-border-2);
  background: var(--color-bg-1);
}

.content__header-main {
  min-width: 0;
}

.content__crumb {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--color-text-3);
  font-size: 12px;
}

.content__crumb-sep {
  color: var(--color-text-4);
}

.content__crumb-current {
  color: var(--color-text-2);
}

.content__heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 10px;
}

.content__heading-icon {
  align-self: center;
  color: rgb(var(--primary-6));
  font-size: 22px;
}

.content__heading h1 {
  margin: 0;
  font-size: 22px;
  font-weight: 650;
  letter-spacing: -0.01em;
}

.content__heading-en {
  color: var(--color-text-3);
  font-size: 13px;
  font-weight: 500;
}

.content__header p {
  margin: 8px 0 0;
  color: var(--color-text-3);
  font-size: 13px;
  line-height: 1.6;
}

.content__body {
  display: grid;
  max-width: 1120px;
  gap: 20px;
  padding: 28px 36px 56px;
}
</style>
