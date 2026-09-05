<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveFormRangePicker from '../.vitepress/theme/components/demos/InteractiveFormRangePicker.vue'

const srcBasic = `<script setup>
import { YdRangePicker } from '@yudream/components'
import { ref } from 'vue'

const range = ref([])
<\/script>

<template>
  <YdRangePicker v-model="range" />
</template>`

const srcShortcuts = `<script setup>
import { YdRangePicker } from '@yudream/components'
import { ref } from 'vue'

const range = ref([])

function dayOffset(offset) {
  const d = new Date()
  d.setDate(d.getDate() + offset)
  return d
}

const shortcuts = [
  { label: '近 7 天', value: () => [dayOffset(-6), new Date()] },
  { label: '近 30 天', value: () => [dayOffset(-29), new Date()] },
  { label: '本月', value: () => [new Date(new Date().getFullYear(), new Date().getMonth(), 1), new Date()] },
]
<\/script>

<template>
  <YdRangePicker v-model="range" :shortcuts="shortcuts" />
</template>`

const srcShowTime = `<YdRangePicker v-model="range" show-time />
<!-- show-time 下 valueFormat 默认为 'YYYY-MM-DD HH:mm:ss'，绑定值为两个元素的字符串数组 -->`

const props = [
  ['<code>v-model</code>', '<code>(string | number)[] | undefined</code>', '—', '绑定值 <code>[开始, 结束]</code>，字符串格式由 <code>valueFormat</code> 决定'],
  ['<code>mode</code>', "<code>'date' | 'week' | 'month' | 'quarter' | 'year'</code>", "<code>'date'</code>", '选择器粒度'],
  ['<code>showTime</code>', '<code>boolean</code>', '<code>false</code>', '是否带时间选择；开启后 <code>valueFormat</code> 默认变为 <code>YYYY-MM-DD HH:mm:ss</code>'],
  ['<code>valueFormat</code>', '<code>string</code>', '<code>YYYY-MM-DD</code>', '绑定值格式（dayjs 格式串）'],
  ['<code>format</code>', '<code>string</code>', '—', '输入框展示格式，缺省与 <code>valueFormat</code> 一致'],
  ['<code>placeholder</code>', '<code>string[]</code>', "<code>['开始日期', '结束日期']</code>", '起止两个输入框的占位文本'],
  ['<code>disabled / readonly / error</code>', '<code>boolean</code>', '<code>false</code>', '禁用、只读与错误态'],
  ['<code>allowClear</code>', '<code>boolean</code>', '<code>true</code>', '是否显示清空按钮'],
  ['<code>dayStartOfWeek</code>', '<code>0 | 1</code>', '—', '一周起始日：<code>0</code> 周日、<code>1</code> 周一'],
  ['<code>disabledDate</code>', '<code>(current?: Date) =&gt; boolean</code>', '—', '禁用指定日期'],
  ['<code>disabledTime</code>', "<code>(current: Date, type: 'start' | 'end') =&gt; DisabledTimeProps</code>", '—', '按起/止分别禁用时间（配合 <code>showTime</code>）'],
  ['<code>shortcuts</code>', '<code>ShortcutType[]</code>', '—', '快捷范围（如「近 7 天」「本月」）'],
  ['<code>shortcutsPosition</code>', "<code>'left' | 'bottom' | 'right'</code>", '—', '快捷选项面板位置'],
  ['<code>popupContainer</code>', '<code>string | HTMLElement</code>', '—', '弹层挂载容器'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(value: (string | number)[] | undefined) =&gt; void</code>', '选中或清空时更新绑定值'],
  ['<code>change</code>', '<code>(value: (string | number)[] | undefined) =&gt; void</code>', '值变化（含清空）时触发'],
  ['<code>ok</code>', '<code>(value: (string | number)[] | undefined) =&gt; void</code>', '<code>showTime</code> 模式点击「确定」时触发'],
  ['<code>clear</code>', '<code>() =&gt; void</code>', '点击清空按钮时触发'],
  ['<code>popupVisibleChange</code>', '<code>(visible: boolean) =&gt; void</code>', '弹层显隐变化'],
]
</script>

# YdRangePicker 日期范围选择器

Fa 风格的起止日期（时间）选择输入框，基于 Arco RangePicker 封装并统一为组件库主题 token 外观。绑定值为 `[开始, 结束]` 的**格式化字符串数组**，与搜索栏/查询 DTO 的起止字段直接对接。

## 基础用法

<Demo title="日期范围与快捷选项" description="shortcuts 提供「近 7 天 / 近 30 天 / 本月」等常用范围；show-time 追加时间列" :source="srcBasic">
  <ClientOnly><InteractiveFormRangePicker /></ClientOnly>
</Demo>

## 快捷范围

`shortcuts` 是搜索栏日期筛选的高频配置，`value` 支持返回 `[Date, Date]` 的函数：

<Demo title="shortcuts" :source="srcShortcuts">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「日期范围」行。</p>
</Demo>

## 含时间的范围

<Demo title="showTime" :source="srcShowTime">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「含时间」行。</p>
</Demo>

## API

### Props

<ApiTable title="YdRangePicker Props" :data="props" />

### Emits

<ApiTable title="YdRangePicker Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

## 注意事项

- **绑定值为数组**：<code>v-model</code> 始终是 <code>[开始, 结束]</code> 两个元素（清空后为 <code>undefined</code>/空），提交查询前注意判空。
- 搜索栏场景常与 <code>FaSearchBar</code> 搭配；重置搜索条件时把绑定值置回 <code>undefined</code> 即可清空。
- 单个日期选择请用 [YdDatePicker](/components/yd-date-picker)；纯时间（无日期）请用 [YdTimePicker](/components/yd-time-picker)。
- 完整可运行示例见主前端仓 <code>apps/component-showcase</code> 的「表单」区。
