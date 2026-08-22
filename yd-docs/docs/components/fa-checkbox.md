<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveBasicCheckbox from '../.vitepress/theme/components/demos/InteractiveBasicCheckbox.vue'

const srcBasic = `<script setup>
import { FaCheckbox } from '@yudream/components'
import { ref } from 'vue'

// 取值类型：boolean | 'indeterminate' | undefined
const checked = ref(false)
<\/script>

<template>
  <FaCheckbox v-model="checked">
    同意协议
  </FaCheckbox>
</template>`

const srcIndeterminate = `<script setup>
import { FaCheckbox } from '@yudream/components'
import { ref } from 'vue'

const checked = ref('indeterminate')
<\/script>

<template>
  <FaCheckbox v-model="checked">
    部分选中
  </FaCheckbox>
</template>`

const srcDisabled = `<template>
  <FaCheckbox v-model="unchecked" disabled>
    禁用未选中
  </FaCheckbox>
  <FaCheckbox v-model="checked" disabled>
    禁用已选中
  </FaCheckbox>
</template>`

const srcCustomStyle = `<template>
  <FaCheckbox
    v-model="checked"
    class="px-3 py-2 border border-primary/20 rounded-lg bg-primary/5 gap-3"
    item-class="size-5 rounded-md border-primary"
    label-class="text-primary font-medium w-full"
  >
    开启精细化配置
  </FaCheckbox>
</template>`

const props = [
  ['<code>v-model</code>', "<code>boolean | 'indeterminate' | undefined</code>", '—', '选中状态；<code>indeterminate</code> 表示半选'],
  ['<code>id</code>', '<code>string</code>', '—', '复选框 id，默认用 <code>useId()</code> 自动生成，用于关联 label'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用；label 同步显示禁用态'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素（checkbox + label 容器）class'],
  ['<code>itemClass</code>', '<code>string</code>', '—', '透传复选框本体 class'],
  ['<code>labelClass</code>', '<code>string</code>', '—', '透传 label class'],
]

const emits = [
  ['<code>change</code>', "<code>(value: boolean | 'indeterminate' | undefined) => void</code>", '选中状态变化时触发，与 <code>v-model</code> 同步'],
]
</script>

# FaCheckbox 复选框

单个复选框 + 文字标签的组合。基于 reka-ui 的 `CheckboxRoot` 封装 + Tailwind 样式，点击 label 同样触发切换。

## 基础用法

<Demo title="基础复选框" description="v-model 绑定布尔值，默认插槽为 label 文本" :source="srcBasic">
  <ClientOnly><InteractiveBasicCheckbox /></ClientOnly>
</Demo>

## 半选状态

`v-model` 传入 `'indeterminate'` 显示半选，常用于「全选」场景下有部分子项被选中的情况。

<Demo title="半选" :source="srcIndeterminate">
  <div class="demo-row">
    <span style="display:inline-flex;align-items:center;gap:8px;">
      <span style="display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;border-radius:4px;font-size:12px;background:var(--vp-c-brand-1);color:#fff;">−</span>
      <span style="font-size:14px;">部分选中</span>
    </span>
  </div>
</Demo>

## 禁用

<Demo title="禁用" :source="srcDisabled">
  <div class="demo-row" style="opacity:.5;">
    <span style="display:inline-flex;align-items:center;gap:8px;">
      <span style="display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;border:1px solid var(--vp-c-divider);border-radius:4px;"></span>
      <span style="font-size:14px;">禁用未选中</span>
    </span>
    <span style="display:inline-flex;align-items:center;gap:8px;">
      <span style="display:inline-flex;align-items:center;justify-content:center;width:16px;height:16px;border-radius:4px;font-size:12px;background:var(--vp-c-brand-1);color:#fff;">✓</span>
      <span style="font-size:14px;">禁用已选中</span>
    </span>
  </div>
</Demo>

## 自定义样式

通过 `class` / `itemClass` / `labelClass` 分别覆盖容器、复选框本体、label 的样式。

<Demo title="自定义样式" :source="srcCustomStyle">
  <div class="demo-row">
    <span style="display:inline-flex;align-items:center;gap:12px;padding:8px 12px;border:1px solid var(--vp-c-brand-1);border-radius:8px;background:var(--vp-c-brand-soft);">
      <span style="display:inline-flex;align-items:center;justify-content:center;width:20px;height:20px;border-radius:6px;font-size:13px;background:var(--vp-c-brand-1);color:#fff;">✓</span>
      <span style="font-size:14px;color:var(--vp-c-brand-1);font-weight:500;">开启精细化配置</span>
    </span>
  </div>
</Demo>

## 状态机

```mermaid
stateDiagram-v2
    [*] --> unchecked : v-model = false / undefined
    unchecked --> checked : 点击
    checked --> unchecked : 点击
    indeterminate --> checked : 点击
    unchecked --> indeterminate : v-model = 'indeterminate'
    checked --> indeterminate : v-model = 'indeterminate'
```

半选状态只能由外部赋值进入，用户点击半选框会切换为选中。

## API

### Props

<ApiTable title="FaCheckbox Props" :data="props" />

### Emits

<ApiTable title="FaCheckbox Emits" :data="emits" :columns="['事件', '签名', '说明']" />

### Slots

| 插槽 | 说明 |
| --- | --- |
| `default` | label 内容；内容为空时 label 自动隐藏（`empty:hidden`） |

::: warning 注意事项
- `v-model` 类型是 `boolean | 'indeterminate' | undefined`，不是严格的布尔，业务侧如需存库请先归一化。
- 需要多选场景请使用 `FaCheckboxGroup`，它负责数组模型、min/max 限制与自定义选项渲染。
:::

::: info 源码位置
- 封装组件：`yudream-frontend/packages/components/src/basic/checkbox/index.vue`
- 底层复选框：`yudream-frontend/packages/components/src/basic/checkbox/checkbox/Checkbox.vue`
- 示例：`yudream-frontend/packages/components/src/basic/checkbox/_examples/`
:::
