<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingFormTabs from '../.vitepress/theme/components/demos/InteractiveRemainingFormTabs.vue'

const srcBasic = `<script setup>
import { FaTabs } from '@yudream/components'
import { ref } from 'vue'

const activeTab = ref('profile')

const list = [
  { label: '资料', value: 'profile' },
  { label: '账号', value: 'account' },
  { label: '通知', value: 'notice' },
]
<\/script>

<template>
  <FaTabs v-model="activeTab" :list="list" class="w-96">
    <template #profile>
      <div class="text-sm p-4 rounded-md bg-muted/50">这里展示用户基础资料。</div>
    </template>
    <template #account>
      <div class="text-sm p-4 rounded-md bg-muted/50">这里展示账号安全设置。</div>
    </template>
    <template #notice>
      <div class="text-sm p-4 rounded-md bg-muted/50">这里展示消息通知偏好。</div>
    </template>
  </FaTabs>
</template>`

const srcIcon = `<script setup>
import { FaTabs } from '@yudream/components'
import { ref } from 'vue'

const activeTab = ref('user')

const list = [
  { icon: 'i-lucide:user', label: '用户', value: 'user' },
  { icon: 'i-lucide:settings', label: '设置', value: 'setting' },
  { icon: 'i-lucide:bell', label: '通知', value: 'notice' },
]
<\/script>

<template>
  <FaTabs v-model="activeTab" :list="list" class="w-96">
    <template #user>用户信息内容。</template>
    <template #setting>系统设置内容。</template>
    <template #notice>通知消息内容。</template>
  </FaTabs>
</template>`

const srcStyle = `<script setup>
import { FaTabs } from '@yudream/components'
import { ref } from 'vue'

const activeTab = ref('overview')

const list = [
  { label: '总览', value: 'overview', class: 'rounded-md data-[state=active]:bg-primary data-[state=active]:text-primary-foreground' },
  { label: '趋势', value: 'trend', class: 'rounded-md data-[state=active]:bg-primary data-[state=active]:text-primary-foreground' },
  { label: '明细', value: 'detail', class: 'rounded-md data-[state=active]:bg-primary data-[state=active]:text-primary-foreground' },
]
<\/script>

<template>
  <FaTabs
    v-model="activeTab"
    :list="list"
    class="p-3 border rounded-lg w-96"
    list-class="gap-2 rounded-md bg-transparent p-0"
    content-class="rounded-md bg-muted/50 p-4 text-sm"
  >
    <template #overview>当前数据总览。</template>
    <template #trend>趋势分析内容。</template>
    <template #detail>明细数据内容。</template>
  </FaTabs>
</template>`

const props = [
  ['<code>v-model</code>', '<code>string | number</code>', '—', '当前激活标签的 value'],
  ['<code>list</code>', '<code>TabItem[]</code>', '<strong>必填</strong>', '标签列表，见下方 TabItem 表'],
  ['<code>class</code>', "<code>HTMLAttributes['class']</code>", '—', '根容器 class'],
  ['<code>listClass</code>', "<code>HTMLAttributes['class']</code>", '—', '标签列表（TabsList）class'],
  ['<code>contentClass</code>', "<code>HTMLAttributes['class']</code>", '—', '每个内容面板（TabsContent）class'],
]

const tabItem = [
  ['<code>icon</code>', '<code>string</code>', '—', '标签图标，使用 FaIcon 图标名（如 <code>i-lucide:user</code>），可选'],
  ['<code>label</code>', '<code>string</code>', '<strong>必填</strong>', '标签文字'],
  ['<code>value</code>', '<code>string | number</code>', '<strong>必填</strong>', '标签值，同时作为内容插槽名，必须在 list 内唯一'],
  ['<code>class</code>', "<code>HTMLAttributes['class']</code>", '—', '该标签触发器（TabsTrigger）的 class'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(value: string | number) => void</code>', '切换标签时触发，配合 v-model 使用'],
]
</script>

# FaTabs 标签页

标签页切换组件。基于 reka-ui `Tabs` 封装，通过 `list` 声明标签，用**动态插槽**承载每个标签的内容。常用于设置页分类、详情分栏、列表/图表视图切换。

## 基础用法

<Demo title="基础标签页" description="list 声明标签，#value 插槽放置对应内容" :source="srcBasic">
  <ClientOnly><InteractiveRemainingFormTabs variant="basic" /></ClientOnly>
</Demo>

## 带图标

`list` 项的 `icon` 字段传 FaIcon 图标名，图标渲染在标签文字左侧。

<Demo title="图标标签" description="icon 为可选项，不传则不渲染图标" :source="srcIcon">
  <ClientOnly><InteractiveRemainingFormTabs variant="icon" /></ClientOnly>
</Demo>

## 自定义样式

通过 `class` / `listClass` / `contentClass` 以及每个标签项自己的 `class`，可以分别控制容器、标签条、内容区和单个标签的样式。

<Demo title="自定义样式" description="标签项 class 可配合 data-[state=active] 描述激活态" :source="srcStyle">
  <ClientOnly><InteractiveRemainingFormTabs variant="style" /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaTabs Props" :data="props" />

### TabItem（list 数组项）

<ApiTable title="TabItem" :data="tabItem" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `[value]` | 动态插槽，插槽名等于 `list` 项的 `value`，渲染为该标签的内容面板 |

### Emits

<ApiTable title="FaTabs Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

## 注意事项

- **插槽名与 value 一一对应**：内容为 `<TabsContent v-for="item in list">` + `<slot :name="item.value" />` 渲染，缺了对应插槽的标签会显示空白面板。
- **value 必须唯一**：它同时作为 `TabsTrigger` / `TabsContent` 的 key 与 value。
- 组件内部用 `useTextDirection` 自动适配 LTR/RTL 方向，无需手动传 `dir`。
- 组件在框架内已全局自动注册，业务代码中直接使用 `<FaTabs>` 即可，无需手动 import（示例中的 import 仅作演示）。

## 源码

- 组件：`yudream-frontend/packages/components/src/basic/tabs/index.vue`
- 示例：`yudream-frontend/packages/components/src/basic/tabs/_examples/`
