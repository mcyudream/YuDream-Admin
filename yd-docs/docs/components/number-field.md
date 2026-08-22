<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
import { FaNumberField } from '@yudream/components'
import { ref } from 'vue'

const port = ref(8080)
<\/script>

<template>
  <FaNumberField v-model="port" :min="1" :max="65535" :step="1" />
</template>`

const props = [
  ['<code>v-model</code>', '<code>number</code>', '必填', '数值（双向绑定，required）'],
  ['<code>min</code>', '<code>number</code>', '—', '最小值'],
  ['<code>max</code>', '<code>number</code>', '—', '最大值'],
  ['<code>step</code>', '<code>number</code>', '—', '每次增减步长'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用输入与增减按钮'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class（默认宽 <code>w-[200px]</code>）'],
]
</script>

# FaNumberField 数字输入框

带「− / 输入框 / +」布局的数字输入组件，基于 reka-ui `NumberField` 封装：左右为增减按钮，中间为可直接键入的输入框。

## 基础用法

<Demo title="数字输入" description="点击 +/− 或直接键入修改数值，min/max/step 控制边界与步长" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="number-field" /></ClientOnly>
</Demo>

## 使用讲解

```vue
<script setup>
import { FaNumberField } from '@yudream/components'
import { ref } from 'vue'

const concurrency = ref(4)
</script>

<template>
  <FaNumberField
    v-model="concurrency"
    :min="1"
    :max="32"
    :step="1"
  />
</template>
```

- 组件内部由 `NumberFieldDecrement` / `NumberFieldInput` / `NumberFieldIncrement` 三部分组成，已按「减 − 输入 − 加」顺序排布，无需手动组装子组件。
- 默认根元素宽度 `w-[200px]`，可通过 `class` 覆盖。
- 与 `FaInput type="number"` 的区别：本组件提供步进按钮与受控的 min/max/step 语义，适合端口数、并发数等有明确边界的整数配置项。

## API

### Props

<ApiTable title="FaNumberField Props" :data="props" />

### Emits

无自定义 emits，`v-model` 更新即数值变化。

::: warning 注意
组件模型为 `number` 类型且必填，初始值请给具体数值而非 `undefined`。若字段本身是后端 Long/Snowflake ID，不要使用本组件——ID 在 JSON / TS / URL 中一律是 `string`。
:::

::: info 源码位置
`yudream-frontend/packages/components/src/basic/number-field/index.vue`
:::
