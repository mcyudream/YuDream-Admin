<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveRemainingMisc from '../.vitepress/theme/components/demos/InteractiveRemainingMisc.vue'

const srcBasic = `<script setup>
import { FaRadioGroup } from '@yudream/components'
import { ref } from 'vue'

const pay = ref('monthly')

const options = [
  { label: '按月付费', value: 'monthly', description: '每月自动续费，随时取消' },
  { label: '按年付费', value: 'yearly', description: '一次支付 12 个月，享 8 折优惠' },
  { label: '企业定制', value: 'enterprise', disabled: true },
]
<\/script>

<template>
  <FaRadioGroup v-model="pay" :options="options" />
</template>`

const srcSlot = `<script setup>
import { FaRadioGroup } from '@yudream/components'
<\/script>

<template>
  <!-- 自定义每个选项的渲染 -->
  <FaRadioGroup v-model="plan" :options="options">
    <template #option="{ option, checked, disabled }">
      <div :class="['plan-card', { 'plan-card--active': checked, 'plan-card--disabled': disabled }]">
        <span>{{ option.label }}</span>
        <span class="price">{{ option.price }}</span>
      </div>
    </template>
  </FaRadioGroup>
</template>`

const props = [
  ['<code>v-model</code>', '<code>AcceptableValue | undefined</code>', '<code>undefined</code>', '当前选中值（双向绑定）'],
  ['<code>options</code>', '<code>RadioGroupOption[]</code>', '必填', '选项列表，见下方 Option 结构'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '整体禁用（单个选项可用 <code>option.disabled</code>）'],
  ['<code>dir</code>', "<code>'ltr' | 'rtl'</code>", '跟随文档方向', '排版方向'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
  ['<code>optionClass</code>', '<code>string</code>', '—', '每个选项行容器的 class'],
  ['<code>itemClass</code>', '<code>string</code>', '—', '每个圆形单选钮的 class'],
]

const optionFields = [
  ['<code>label</code>', '<code>string</code>', '必填', '选项标题文本'],
  ['<code>value</code>', '<code>AcceptableValue</code>', '必填', '选项值；注意业务 ID 一律用 string'],
  ['<code>description</code>', '<code>string</code>', '—', '选项下方的次要说明文字'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用该选项'],
  ['<code>id</code>', '<code>string</code>', '自动生成', '自定义 DOM id（label 关联）'],
]
</script>

# FaRadioGroup 单选组

基于 reka-ui `RadioGroup` 封装的单选按钮组，通过 `options` 数据驱动渲染，支持带描述文字的选项与完全自定义的选项插槽。

## 基础用法

<Demo title="数据驱动单选组" description="传入 options 数组即可渲染，支持描述文字与禁用" :source="srcBasic">
  <ClientOnly><InteractiveRemainingMisc type="radio-group" /></ClientOnly>
</Demo>

## 自定义选项插槽

使用 `#option` 插槽可完全接管每个选项的渲染，作用域提供 `{ option, checked, disabled, id }`。使用插槽时默认的圆形单选钮会隐藏（`v-show` 控制），适合卡片式选择场景。

<Demo title="插槽自定义" description="checked 状态可用于高亮卡片样式" :source="srcSlot">
  <div class="demo-row">
    <div style="border: 2px solid var(--vp-c-brand); border-radius: 8px; padding: 12px 20px; text-align: center;">基础版<br><small>已选中</small></div>
    <div style="border: 1px solid var(--vp-c-divider); border-radius: 8px; padding: 12px 20px; text-align: center;">专业版</div>
    <div style="border: 1px solid var(--vp-c-divider); border-radius: 8px; padding: 12px 20px; text-align: center;">旗舰版</div>
  </div>
</Demo>

## API

### Props

<ApiTable title="FaRadioGroup Props" :data="props" />

### RadioGroupOption 结构

<ApiTable title="Option 字段" :data="optionFields" />

### Slots

| 插槽 | 作用域 | 说明 |
| --- | --- | --- |
| `option` | `{ option, checked, disabled, id }` | 自定义选项内容；使用后隐藏默认单选圆钮 |

### Emits

| 事件 | 参数 | 说明 |
| --- | --- | --- |
| `change` | `(value: AcceptableValue \| undefined)` | 选中值变化时触发 |

::: warning ID 类型
若选项 `value` 来自后端主键（Java Long / Snowflake ID），在 JSON / TS / URL 中一律使用 `string`，禁止 `Number(id)`。
:::

::: info 源码位置
`yudream-frontend/packages/components/src/basic/radio-group/index.vue`
:::
