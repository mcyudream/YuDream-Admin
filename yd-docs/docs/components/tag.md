<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingMisc from '../.vitepress/theme/components/demos/InteractiveRemainingMisc.vue'

const srcBasic = `<template>
  <div class="flex flex-wrap gap-4">
    <FaTag>默认标签</FaTag>
    <FaTag variant="destructive">危险标签</FaTag>
    <FaTag variant="outline">边框标签</FaTag>
    <FaTag variant="secondary">次要标签</FaTag>
  </div>
</template>`

const srcIcon = `<template>
  <div class="flex flex-wrap gap-4">
    <FaTag icon="i-lucide:check">成功</FaTag>
    <FaTag icon="i-lucide:alert-triangle" variant="destructive">警告</FaTag>
    <FaTag icon="i-lucide:info" variant="outline">信息</FaTag>
    <FaTag icon="i-lucide:star" variant="secondary">收藏</FaTag>
  </div>
</template>`

const srcClosable = `<script setup>
import { ref } from 'vue'

const tags = ref([
  { id: 1, label: '标签一', variant: 'default' as const },
  { id: 2, label: '标签二', variant: 'destructive' as const },
  { id: 3, label: '标签三', variant: 'outline' as const },
  { id: 4, label: '标签四', variant: 'secondary' as const },
])

function handleClose(id: number) {
  tags.value = tags.value.filter(tag => tag.id !== id)
}
<\/script>

<template>
  <div class="flex flex-wrap gap-4">
    <FaTag
      v-for="tag in tags"
      :key="tag.id"
      :variant="tag.variant"
      closable
      @close="handleClose(tag.id)"
    >
      {{ tag.label }}
    </FaTag>
  </div>
</template>`

const props = [
  ['<code>variant</code>', "<code>'default' | 'destructive' | 'outline' | 'secondary'</code>", "<code>'default'</code>", '视觉变体'],
  ['<code>icon</code>', '<code>string</code>', '—', '左侧图标名称（Iconify 类名），渲染为 size-3 图标'],
  ['<code>closable</code>', '<code>boolean</code>', '<code>false</code>', '显示右侧关闭按钮，点击触发 <code>close</code> 事件（组件不负责移除自身）'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]

const emits = [
  ['<code>close</code>', '<code>(event: MouseEvent)</code>', '点击关闭按钮时触发；需自行从数据源中移除对应项'],
]
</script>

# FaTag 标签

用于标记、分类或小体量的状态说明。基于 reka-ui `Primitive` 封装，默认渲染为 `<span>`。

> 源码位置：`yudream-frontend/packages/components/src/basic/tag/index.vue`（内部 `Tag` 原子组件在同目录 `tag/` 下）

## 基础用法

通过 `variant` 切换四种视觉变体。

<Demo title="标签变体" description="default / destructive / outline / secondary" :source="srcBasic">
  <ClientOnly><InteractiveRemainingMisc type="tag" /></ClientOnly>
</Demo>

## 带图标

设置 `icon` 属性在文字前展示 Iconify 图标。

<Demo title="带图标标签" description="icon 接收 Iconify 类名" :source="srcIcon">
  <div class="demo-row">
    <span style="background: var(--vp-c-brand-1); color: #fff; font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">✓ 成功</span>
    <span style="background: var(--vp-c-danger-1); color: #fff; font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">⚠ 警告</span>
    <span style="border: 1px solid var(--vp-c-divider); color: var(--vp-c-text-1); font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">ℹ 信息</span>
    <span style="background: var(--vp-c-bg-soft); color: var(--vp-c-text-1); font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">★ 收藏</span>
  </div>
</Demo>

## 可关闭标签

设置 `closable` 显示关闭按钮，在 `close` 事件中自行维护列表数据。

<Demo title="可关闭标签" description="点击 × 触发 close 事件" :source="srcClosable">
  <div class="demo-row">
    <span style="background: var(--vp-c-brand-1); color: #fff; font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">标签一 ✕</span>
    <span style="background: var(--vp-c-danger-1); color: #fff; font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">标签二 ✕</span>
    <span style="border: 1px solid var(--vp-c-divider); color: var(--vp-c-text-1); font-size: 12px; font-weight: 600; padding: 2px 10px; border-radius: 6px;">标签三 ✕</span>
  </div>
</Demo>

## 示例讲解

以可关闭标签为例：

```vue
<FaTag
  v-for="tag in tags"
  :key="tag.id"
  :variant="tag.variant"
  closable
  @close="handleClose(tag.id)"
>
  {{ tag.label }}
</FaTag>
```

- 组件是受控的：点击关闭按钮只派发 `close` 事件并携带原始 `MouseEvent`，不会自动隐藏标签。
- 关闭按钮内置 `focus:ring-2` 焦点环样式，键盘可达。
- 根元素可通过 `as` 渲染为其他标签（如 `a`）；默认 `as="span"`。

## API

### Props

<ApiTable title="FaTag Props" :data="props" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | 标签文字内容 |

### Emits

<ApiTable title="FaTag Emits" :data="emits" :columns="['事件', '回调参数', '说明']" />
