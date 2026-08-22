<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveUtilityDemo from '../.vitepress/theme/components/demos/InteractiveUtilityDemo.vue'

const srcBasic = `<script setup>
import { FaSlider } from '@yudream/components'
import { ref } from 'vue'

const value = ref([30])
<\/script>

<template>
  <FaSlider v-model="value" />
</template>`

const srcRange = `<script setup>
import { FaSlider } from '@yudream/components'
import { ref } from 'vue'

// 数组长度为 2 即为区间选择
const range = ref([20, 80])
<\/script>

<template>
  <FaSlider v-model="range" :step="10" />
</template>`
</script>

# FaSlider 滑块

基于 [reka-ui](https://reka-ui.com) 的 `SliderRoot` 封装 + Tailwind 样式，模型值为 `number[]`：单值滑块传单元素数组，区间滑块传双元素数组。内置数值 tooltip（`tooltip`，默认开启）。

依据源码：`yudream-frontend/packages/components/src/basic/slider/index.vue`、`yudream-frontend/packages/components/src/basic/slider/slider/`

## 基础用法

<Demo title="单值滑块" description="v-model 为 number[]，默认范围 0-100，步长 1" :source="srcBasic">
  <ClientOnly><InteractiveUtilityDemo type="slider" /></ClientOnly>
</Demo>

## 区间与步长

<Demo title="区间选择 + 步长" description="双 thumb 区间选择，step=10" :source="srcRange">
  <div style="display: flex; align-items: center; gap: 12px;">
    <div style="flex: 1; position: relative; height: 4px; border-radius: 2px; background: var(--vp-c-divider);"><div style="position: absolute; left: 20%; top: 0; height: 4px; width: 60%; background: var(--vp-c-brand-1); border-radius: 2px;"></div><div style="position: absolute; top: 50%; transform: translate(-50%, -50%); width: 16px; height: 16px; border-radius: 50%; background: #fff; border: 2px solid var(--vp-c-brand-1); left: 20%;"></div><div style="position: absolute; top: 50%; transform: translate(-50%, -50%); width: 16px; height: 16px; border-radius: 50%; background: #fff; border: 2px solid var(--vp-c-brand-1); left: 80%;"></div></div>
    <span style="color: var(--vp-c-text-2); font-size: 13px;">20 - 80</span>
  </div>
</Demo>

## 垂直与反转

```vue
<script setup>
import { FaSlider } from '@yudream/components'
import { ref } from 'vue'

const v1 = ref([30])
const v2 = ref([70])
</script>

<template>
  <!-- 垂直方向：需自行给根元素一个高度，内置最小高度 min-h-44 -->
  <FaSlider v-model="v1" orientation="vertical" class="h-40" />

  <!-- 反转轨道：从 max 到 min 方向填充 -->
  <FaSlider v-model="v2" inverted />
</template>
```

- `orientation="vertical"` 时组件切换为纵向布局（`flex-col`），根元素带 `data-[orientation=vertical]:min-h-44` 最小高度约束，建议用 `class="h-40"` 这类方式显式给定高度。
- `inverted` 只改变填充方向，取值范围仍由 `min` / `max` 决定。
- `thumbAlignment` 控制 thumb 在轨道端点的对齐：`contain`（默认，thumb 不超出轨道两端）或 `overflow`（thumb 允许溢出端点半个身位）。

## 拖动提示气泡

每个 thumb 外层都包了一个 `FaTooltip`（`:text="当前值"` 且 `disable-closing-trigger`）；`tooltip` prop 为 `false` 时只是禁用了 Tooltip 的显示，thumb 本身的样式与交互不受影响。气泡文字为数值的字符串形式，深浅模式自动跟随主题。

<Demo title="拖动显示数值" description="tooltip 默认开启，拖动 thumb 时显示当前值" :source="srcBasic">
  <div style="display: flex; align-items: center; gap: 12px;">
    <div style="flex: 1; position: relative; height: 4px; border-radius: 2px; background: var(--vp-c-divider);"><div style="position: absolute; left: 0; top: 0; height: 4px; width: 45%; background: var(--vp-c-brand-1); border-radius: 2px;"></div><span style="position: absolute; top: -30px; transform: translateX(-50%); background: var(--vp-c-text-1); color: var(--vp-c-bg); font-size: 12px; padding: 2px 8px; border-radius: 4px; left: 45%;">45</span><div style="position: absolute; top: 50%; transform: translate(-50%, -50%); width: 16px; height: 16px; border-radius: 50%; background: #fff; border: 2px solid var(--vp-c-brand-1); left: 45%;"></div></div>
    <span style="color: var(--vp-c-text-2); font-size: 13px;">45</span>
  </div>
</Demo>

## API

### Props

除下列 props 外，其余 reka-ui `SliderRootProps` 属性经 `v-bind="props"` 透传给底层 Slider。

<ApiTable title="FaSlider Props" :data="[
  ['<code>v-model</code>', '<code>number[]</code>', '—', '选中值数组；单元素为单值，双元素为区间'],
  ['<code>defaultValue</code>', '<code>number[]</code>', '<code>[0]</code>', '非受控初始值'],
  ['<code>min</code> / <code>max</code>', '<code>number</code>', '<code>0</code> / <code>100</code>', '取值范围'],
  ['<code>step</code>', '<code>number</code>', '<code>1</code>', '步长'],
  ['<code>disabled</code>', '<code>boolean</code>', '<code>false</code>', '禁用'],
  ['<code>inverted</code>', '<code>boolean</code>', '<code>false</code>', '反转轨道方向（从 max 到 min 填充）'],
  ['<code>orientation</code>', `<code>'horizontal' | 'vertical'</code>`, `<code>'horizontal'</code>`, '方向'],
  ['<code>thumbAlignment</code>', `<code>'contain' | 'overflow'</code>`, `<code>'contain'</code>`, 'thumb 在轨道端点的对齐方式'],
  ['<code>tooltip</code>', '<code>boolean</code>', '<code>true</code>', '拖动时显示数值气泡'],
  ['<code>class</code>', '<code>string</code>', '—', '透传根元素 class'],
]" />

### Slots / Emits

无自定义插槽；原生事件透传。`update:modelValue` 由 `defineModel<number[]>()` 提供，同时透传 reka-ui `SliderRootEmits` 的其余事件（如 `valueCommit`，在拖动结束松开时触发，适合做「拖完再提交」的场景）。
