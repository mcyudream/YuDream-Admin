<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
import { FaButton, FaPageHeader } from '@yudream/components'
<\/script>

<template>
  <FaPageHeader title="用户管理" description="管理系统账号、角色与权限">
    <FaButton size="sm">新增用户</FaButton>
  </FaPageHeader>
</template>`

const srcSlot = `<script setup>
import { FaPageHeader } from '@yudream/components'
<\/script>

<template>
  <FaPageHeader>
    <template #title>
      订单详情 <span class="text-sm text-muted-foreground">#20240601-001</span>
    </template>
    <template #description>
      下单时间：2024-06-01 12:00
    </template>
  </FaPageHeader>
</template>`
</script>

# FaPageHeader 页头

页面顶部标题区：左侧标题 + 描述，右侧操作按钮区，底部带分隔线。通常作为 `FaPageMain` 之上的第一个元素使用。

依据源码：`yudream-frontend/packages/components/src/basic/page-header/index.vue`

## 基础用法

<Demo title="基础页头" description="title / description 文本，默认插槽渲染右侧操作区" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="page-header" /></ClientOnly>
</Demo>

## 插槽定制

`title` 与 `description` 具名插槽可覆盖纯文本；右侧操作区仅在存在默认插槽内容时渲染（`v-if="!!slots.default"`），因此纯展示页头不会留下空的操作列。

<Demo title="插槽定制" description="title / description 插槽支持富文本" :source="srcSlot">
  <div style="padding: 12px 20px; border-bottom: 1px solid var(--vp-c-divider);">
    <div style="font-size: 20px;">订单详情 <span style="color: var(--vp-c-text-2); font-size: 13px; font-size: 14px;">#20240601-001</span></div>
    <div style="color: var(--vp-c-text-2); font-size: 13px; margin-top: 4px;">下单时间：2024-06-01 12:00</div>
  </div>
</Demo>

## 示例讲解

以基础用法为例：

```vue
<FaPageHeader title="用户管理" description="管理系统账号、角色与权限">
  <FaButton size="sm">新增用户</FaButton>
</FaPageHeader>
```

- 根元素是 `flex flex-wrap items-center justify-between gap-5 border-b px-5 py-4` 的容器，自带 `mb-4` 下边距与底部分隔线，窄屏时左右两块会自动换行。
- 左侧标题/描述容器默认 `flex-[1_1_70%]`（可用 `mainClass` 覆盖）；标题为 `text-2xl`，描述为 `text-sm text-secondary-foreground/50 mt-2 empty-hidden`——即 `title` 与 `description` 都不传时描述占位自动隐藏，不会留下空行。
- 右侧操作区仅当默认插槽有内容时渲染（`v-if="!!slots.default"`），并用 `ml-a min-w-0 flex-wrap gap-2` 排版多个按钮；可通过 `defaultClass` 调整。

## API

### Props

<ApiTable title="FaPageHeader Props" :data="[
  ['<code>title</code>', '<code>string</code>', '—', '标题文本，被 <code>#title</code> 插槽覆盖'],
  ['<code>description</code>', '<code>string</code>', '—', '描述文本；为空时占位元素隐藏（<code>empty-hidden</code>）'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
  ['<code>mainClass</code>', '<code>string</code>', '—', '左侧标题/描述容器 class'],
  ['<code>defaultClass</code>', '<code>string</code>', '—', '右侧操作区 class'],
]" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `title` | 覆盖标题 |
| `description` | 覆盖描述 |
| `default` | 右侧操作按钮区 |

### Emits

无自定义 emits。
