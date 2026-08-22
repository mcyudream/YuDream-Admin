<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingBasicLabel from '../.vitepress/theme/components/demos/InteractiveRemainingBasicLabel.vue'

const srcBasic = `<script setup>
import { FaInput, FaLabel } from '@yudream/components'
import { reactive } from 'vue'

const form = reactive({
  name: '',
  email: '',
})
<\/script>

<template>
  <FaLabel label="用户名">
    <FaInput v-model="form.name" placeholder="请输入用户名" class="w-72" />
  </FaLabel>
  <FaLabel label="邮箱">
    <FaInput v-model="form.email" placeholder="请输入邮箱" class="w-72" />
  </FaLabel>
</template>`

const srcWidth = `<script setup>
import { FaButton, FaInput, FaLabel, FaSelect, FaTextarea } from '@yudream/components'
import { reactive } from 'vue'

const form = reactive({ title: '', type: 'notice', description: '' })
const typeOptions = [
  { label: '公告', value: 'notice' },
  { label: '消息', value: 'message' },
]
<\/script>

<template>
  <!-- 字符串：直接作为 CSS width -->
  <FaLabel label="标题" label-width="5rem">
    <FaInput v-model="form.title" class="w-full" />
  </FaLabel>
  <!-- 数字：自动补 px -->
  <FaLabel label="角色" :label-width="88">
    <FaSelect v-model="form.type" :options="typeOptions" class="w-full" />
  </FaLabel>
  <!-- 多行控件时用 items-start 顶部对齐 -->
  <FaLabel label="描述" label-width="5rem" class="items-start">
    <FaTextarea v-model="form.description" class="w-full" />
  </FaLabel>
  <!-- 不传 label，仅作布局容器 -->
  <FaLabel label-width="5rem">
    <div class="flex gap-2">
      <FaButton>保存</FaButton>
      <FaButton variant="outline">重置</FaButton>
    </div>
  </FaLabel>
</template>`

const srcSlot = `<script setup>
import { FaCheckbox, FaIcon, FaInput, FaLabel } from '@yudream/components'
import { shallowRef } from 'vue'

const keyword = shallowRef('')
const remember = shallowRef(false)
<\/script>

<template>
  <!-- 不传 label 时 default 插槽可自由组合任意内容 -->
  <FaLabel>
    <div class="text-muted-foreground flex shrink-0 gap-2 w-24 items-center">
      <FaIcon name="i-lucide:search" />
      <span>关键词</span>
    </div>
    <FaInput v-model="keyword" placeholder="请输入关键词" class="w-72" />
  </FaLabel>
  <FaLabel>
    <FaCheckbox v-model="remember" />
    <span>记住当前筛选条件</span>
  </FaLabel>
</template>`

const props = [
  ['<code>label</code>', '<code>string</code>', '—', '标签文本；不传时组件仅作为布局容器使用'],
  ['<code>labelWidth</code>', '<code>string | number</code>', '—', '标签固定宽度；数字自动补 <code>px</code> 单位，字符串直接使用（如 <code>&quot;5rem&quot;</code>）'],
  ['<code>class</code>', "<code>HTMLAttributes['class']</code>", '—', '透传到最外层 Label 元素；可用 <code>items-start</code> 等调整对齐'],
]

const slots = [
  ['<code>default</code>', '—', '表单项内容（输入框、选择器、按钮组等）；不传 <code>label</code> 时可自由组合标签与控件'],
]
</script>

# FaLabel 表单标签

表单标签组件，用于「标签 + 控件」的水平排布，支持固定标签宽度实现多行表单的标签对齐。基于内部 `Label`（reka-ui 体系）封装。框架会自动全局注册，页面中无需手动导入。

## 基础用法

<Demo title="基础用法" description="label 属性声明标签文本，default 插槽放表单控件" :source="srcBasic">
  <ClientOnly><InteractiveRemainingBasicLabel /></ClientOnly>
</Demo>

## 固定标签宽度

<Demo title="labelWidth 对齐" description="多行表单传相同 labelWidth 即可实现标签右对齐；数字自动补 px" :source="srcWidth">
  <ClientOnly><InteractiveRemainingBasicLabel /></ClientOnly>
</Demo>

## 自由组合内容

<Demo title="无 label 布局" description="不传 label 时仅作布局容器，default 插槽自由排版图标 + 文本 + 控件" :source="srcSlot">
  <ClientOnly><InteractiveRemainingBasicLabel /></ClientOnly>
</Demo>

## API

### Props

<ApiTable title="FaLabel Props" :data="props" />

### Slots

<ApiTable title="FaLabel Slots" :data="slots" :columns="['插槽', '作用域参数', '说明']" />

## 注意事项

- `labelWidth` 传**数字**时组件内部自动拼接 `px`；传字符串则原样作为 CSS 宽度，支持 `"120px"`、`"10em"`、`"5rem"` 等。
- 多个表单项使用相同的 `labelWidth` 可让标签列等宽对齐；标签文本过长时组件内已加 `text-nowrap`，注意预留足够宽度。
- 控件为 `FaTextarea` 等多行控件时，给组件追加 `class="items-start"` 让标签与控件顶部对齐。
- 不传 `label` 时组件退化为纯布局容器，`default` 插槽内容完全自定义（如复选框 + 文案、图标 + 文本）。
- 该组件只负责布局与展示，不涉及 ID 字段；若同一表单需要提交资源 ID，Java `Long`/Snowflake ID 在 JSON、TS 模型与 URL 参数中一律使用 `string`，禁止 `Number(id)`。

## 源码

- 组件：`yudream-frontend/packages/components/src/basic/label/index.vue`
- 内部 Label 元素：`yudream-frontend/packages/components/src/basic/label/label/`
- 示例：`yudream-frontend/packages/components/src/basic/label/_examples/`
