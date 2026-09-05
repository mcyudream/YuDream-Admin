<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveFormTimePicker from '../.vitepress/theme/components/demos/InteractiveFormTimePicker.vue'

const srcBasic = `<script setup>
import { YdTimePicker } from '@yudream/components'
import { ref } from 'vue'

const time = ref('09:30:00')
<\/script>

<template>
  <YdTimePicker v-model="time" />
</template>`

const srcRange = `<YdTimePicker v-model="timeRange" range />
<!-- 绑定值为 [开始时间, 结束时间] 字符串数组 -->`

const srcStep = `<YdTimePicker v-model="time" format="HH:mm" :step="{ minute: 15 }" />
<!-- format 同时决定展示与绑定值格式；step 控制列滚动步长 -->`

const props = [
  ['<code>v-model</code>', '<code>string | string[] | undefined</code>', '—', '绑定值；单选为字符串，<code>range</code> 时为 <code>[开始, 结束]</code> 数组'],
  ['<code>range</code>', '<code>boolean</code>', '<code>false</code>', '是否时间段选择'],
  ['<code>format</code>', '<code>string</code>', "<code>'HH:mm:ss'</code>", '展示与绑定值格式（dayjs 格式串）'],
  ['<code>placeholder</code>', '<code>string | string[]</code>', "<code>'请选择时间'</code>", '占位文本；<code>range</code> 时默认 <code>[\'开始时间\', \'结束时间\']</code>'],
  ['<code>disabled / readonly / error</code>', '<code>boolean</code>', '<code>false</code>', '禁用、只读与错误态'],
  ['<code>allowClear</code>', '<code>boolean</code>', '<code>true</code>', '是否显示清空按钮'],
  ['<code>use12Hours</code>', '<code>boolean</code>', '<code>false</code>', '12 小时制（搭配 <code>format</code> 中的 <code>a</code>/<code>A</code> 显示上下午）'],
  ['<code>step</code>', '<code>{ hour?: number, minute?: number, second?: number }</code>', '—', '时/分/秒列的滚动步长'],
  ['<code>disabledHours</code>', '<code>() =&gt; number[]</code>', '—', '禁用的小时列表'],
  ['<code>disabledMinutes</code>', '<code>(selectedHour?: number) =&gt; number[]</code>', '—', '按已选小时禁用的分钟列表'],
  ['<code>disabledSeconds</code>', '<code>(selectedHour?: number, selectedMinute?: number) =&gt; number[]</code>', '—', '按已选时分禁用的秒列表'],
  ['<code>hideDisabledOptions</code>', '<code>boolean</code>', '<code>false</code>', '隐藏禁用项而不是置灰'],
  ['<code>popupContainer</code>', '<code>string | HTMLElement</code>', '—', '弹层挂载容器'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(value: string | string[] | undefined) =&gt; void</code>', '选中或清空时更新绑定值'],
  ['<code>change</code>', '<code>(value: string | string[] | undefined) =&gt; void</code>', '值变化（含清空）时触发'],
  ['<code>clear</code>', '<code>() =&gt; void</code>', '点击清空按钮时触发'],
  ['<code>popupVisibleChange</code>', '<code>(visible: boolean) =&gt; void</code>', '弹层显隐变化'],
]
</script>

# YdTimePicker 时间选择器

Fa 风格的时间（时间段）选择输入框，基于 Arco TimePicker 封装并统一为组件库主题 token 外观。绑定值直接是 `HH:mm:ss` 格式的**字符串**，适用于营业时间、定时任务时刻等纯时间字段。

## 基础用法

<Demo title="单时间 / 时间段 / 步长" description="range 切换时间段；format + step 控制精度与滚动粒度" :source="srcBasic">
  <ClientOnly><InteractiveFormTimePicker /></ClientOnly>
</Demo>

## 时间段

`range` 开启后绑定值变为 `[开始时间, 结束时间]` 字符串数组：

<Demo title="range" :source="srcRange">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「时间段」行。</p>
</Demo>

## 精度与步长

只需要到分钟时用 `format="HH:mm"` 收窄绑定值格式，并用 `step` 限制可选粒度：

<Demo title="format 与 step" :source="srcStep">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「步长」行（分钟列按 15 递增）。</p>
</Demo>

## API

### Props

<ApiTable title="YdTimePicker Props" :data="props" />

### Emits

<ApiTable title="YdTimePicker Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

## 注意事项

- **format 即契约**：<code>format</code> 同时决定面板展示与 <code>v-model</code> 字符串格式；后端字段为 <code>HH:mm</code> 时直接传 <code>format="HH:mm"</code>，不要在提交时再截取。
- 需要「日期 + 时间」时改用 [YdDatePicker](/components/yd-date-picker) 的 <code>showTime</code> 或 [YdRangePicker](/components/yd-range-picker)。
- 完整可运行示例见主前端仓 <code>apps/component-showcase</code> 的「表单」区。
