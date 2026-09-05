<script setup>
import Demo from '../.vitepress/theme/components/Demo.vue'
import ApiTable from '../.vitepress/theme/components/ApiTable.vue'
import InteractiveFormDatePicker from '../.vitepress/theme/components/demos/InteractiveFormDatePicker.vue'

const srcBasic = `<script setup>
import { YdDatePicker } from '@yudream/components'
import { ref } from 'vue'

const date = ref('2026-09-05')
<\/script>

<template>
  <YdDatePicker v-model="date" />
</template>`

const srcShowTime = `<YdDatePicker v-model="dateTime" show-time placeholder="请选择日期时间" />
<!-- show-time 下 valueFormat 默认为 'YYYY-MM-DD HH:mm:ss' -->`

const srcMode = `<YdDatePicker v-model="month" mode="month" value-format="YYYY-MM" placeholder="请选择月份" />
<!-- mode 可选 date / week / month / quarter / year -->`

const props = [
  ['<code>v-model</code>', '<code>string | number | undefined</code>', '—', '绑定值，字符串格式由 <code>valueFormat</code> 决定'],
  ['<code>mode</code>', "<code>'date' | 'week' | 'month' | 'quarter' | 'year'</code>", "<code>'date'</code>", '选择器粒度'],
  ['<code>showTime</code>', '<code>boolean</code>', '<code>false</code>', '是否带时间选择；开启后 <code>valueFormat</code> 默认变为 <code>YYYY-MM-DD HH:mm:ss</code>'],
  ['<code>valueFormat</code>', '<code>string</code>', '<code>YYYY-MM-DD</code>', '绑定值格式（dayjs 格式串）；传时间戳可用 <code>x</code>（毫秒）'],
  ['<code>format</code>', '<code>string</code>', '—', '输入框展示格式，缺省与 <code>valueFormat</code> 一致'],
  ['<code>placeholder</code>', '<code>string</code>', "<code>'请选择日期'</code>", '占位文本'],
  ['<code>disabled / readonly / error</code>', '<code>boolean</code>', '<code>false</code>', '禁用、只读与错误态'],
  ['<code>allowClear</code>', '<code>boolean</code>', '<code>true</code>', '是否显示清空按钮'],
  ['<code>dayStartOfWeek</code>', '<code>0 | 1</code>', '—', '一周起始日：<code>0</code> 周日、<code>1</code> 周一'],
  ['<code>disabledDate</code>', '<code>(current?: Date) =&gt; boolean</code>', '—', '禁用指定日期'],
  ['<code>disabledTime</code>', '<code>(current?: Date) =&gt; DisabledTimeProps</code>', '—', '禁用指定时间（配合 <code>showTime</code>）'],
  ['<code>shortcuts</code>', '<code>ShortcutType[]</code>', '—', '快捷选项（如「今天」「昨天」）'],
  ['<code>shortcutsPosition</code>', "<code>'left' | 'bottom' | 'right' | 'top'</code>", '—', '快捷选项面板位置'],
  ['<code>popupContainer</code>', '<code>string | HTMLElement</code>', '—', '弹层挂载容器，用于局部滚动或弹窗内场景'],
]

const emits = [
  ['<code>update:modelValue</code>', '<code>(value: string | number | undefined) =&gt; void</code>', '选中或清空时更新绑定值'],
  ['<code>change</code>', '<code>(value: string | number | undefined) =&gt; void</code>', '值变化（含清空）时触发'],
  ['<code>ok</code>', '<code>(value: string | number | undefined) =&gt; void</code>', '<code>showTime</code> 模式点击「确定」时触发'],
  ['<code>clear</code>', '<code>() =&gt; void</code>', '点击清空按钮时触发'],
  ['<code>popupVisibleChange</code>', '<code>(visible: boolean) =&gt; void</code>', '弹层显隐变化'],
]
</script>

# YdDatePicker 日期选择器

Fa 风格的日期/日期时间选择输入框，基于 Arco DatePicker 封装并统一为组件库主题 token 外观。绑定值直接是**格式化字符串**（默认 `YYYY-MM-DD`），与后端 DTO 的日期字段直接对接，无需手动 `dayjs` 转换。

## 基础用法

<Demo title="日期 / 日期时间 / 月份" description="v-model 为格式化字符串；show-time 带时间列；mode 切换选择粒度" :source="srcBasic">
  <ClientOnly><InteractiveFormDatePicker /></ClientOnly>
</Demo>

## 日期时间

开启 `showTime` 后面板追加时间列，点击「确定」才写回绑定值（可通过 `ok` 事件感知），`valueFormat` 默认升级为 `YYYY-MM-DD HH:mm:ss`：

<Demo title="showTime" :source="srcShowTime">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「日期时间」行。</p>
</Demo>

## 选择粒度

`mode` 支持 `week` / `month` / `quarter` / `year`；切换粒度后记得同步调整 `valueFormat`（如月份用 `YYYY-MM`）：

<Demo title="mode" :source="srcMode">
  <p style="margin: 0; color: var(--color-text-3, var(--vp-c-text-2)); font-size: 13px;">示例见上方交互演示的「月份」行。</p>
</Demo>

## API

### Props

<ApiTable title="YdDatePicker Props" :data="props" />

### Emits

<ApiTable title="YdDatePicker Emits" :data="emits" :columns="['事件', '回调签名', '说明']" />

## 注意事项

- **绑定值即字符串**：组件通过 <code>valueFormat</code> 让 <code>v-model</code> 始终是字符串（或时间戳数字），提交表单时可直接放入请求体；禁止再包一层 <code>dayjs().format()</code>。
- **范围选择请用 YdRangePicker**（[文档](/components/yd-range-picker)），不要用两个 YdDatePicker 拼接起止校验。
- 外观样式（<code>yd-picker</code>）来自主题 token，业务侧不要自行覆盖主色或激活态样式。
- 完整可运行示例见主前端仓 <code>apps/component-showcase</code> 的「表单」区。
