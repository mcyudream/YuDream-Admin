# YdDatePicker 日期选择器

日期选择器组件，基于 YD 系统日历面板（Reka + 系统风格）实现，覆盖日期、周、月、季度、年全部模式，支持日期时间联动选择，弹层与输入框均与宿主主题一致。

## 使用场景

- 表单日期 / 日期时间录入
- 按周、月、季度、年维度的数据筛选
- 带快捷选项的日期选择

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `string \| number \| undefined` | - | 选中值，类型由 `valueFormat` 决定 |
| `mode` | `'date' \| 'week' \| 'month' \| 'quarter' \| 'year'` | `'date'` | 选择器模式 |
| `showTime` | `boolean` | `false` | 是否同时选择时间（仅 `date` / `week` 模式生效） |
| `valueFormat` | `string` | 按模式与 `showTime` 决定 | 绑定值格式，可传 `'timestamp'` / `'Date'` |
| `format` | `string` | 按模式与 `showTime` 决定 | 输入框展示格式，也决定时间列粒度 |
| `placeholder` | `string` | `'请选择日期'` | 占位提示文本 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `readonly` | `boolean` | `false` | 是否只读 |
| `error` | `boolean` | `false` | 是否错误状态 |
| `allowClear` | `boolean` | `true` | 是否允许清除 |
| `dayStartOfWeek` | `0 \| 1 \| 2 \| 3 \| 4 \| 5 \| 6` | `1` | 一周开始于周几（`0` 为周日） |
| `disabledDate` | `(current?: Date) => boolean` | - | 不可选择的日期 |
| `disabledTime` | `(current?: Date) => YdPickerDisabledTime` | - | 不可选择的时间（`showTime` 生效） |
| `shortcuts` | `YdPickerShortcut[]` | - | 快捷选项 `{ label, value, disabled?, format? }` |
| `shortcutsPosition` | `'left' \| 'bottom' \| 'right' \| 'top'` | `'top'` | 快捷选项位置 |
| `popupContainer` | `string \| HTMLElement` | - | 弹层挂载容器 |
| `class` | `HTMLAttributes['class']` | - | 自定义 CSS 类 |

默认格式：

| mode | `format`（展示） | `valueFormat`（绑定） |
|------|------------------|------------------------|
| `date` | `YYYY-MM-DD`（`showTime` 时 `YYYY-MM-DD HH:mm:ss`） | 同左 |
| `week` | `gggg-wo` | `YYYY-MM-DD` |
| `month` | `YYYY-MM` | `YYYY-MM` |
| `quarter` | `YYYY-[Q]Q` | `YYYY-MM` |
| `year` | `YYYY` | `YYYY` |

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| `change` | 值变化时触发 | `value: string \| number \| undefined` |
| `clear` | 点击清除时触发 | - |
| `ok` | 点击确定按钮时触发（`showTime` 需确认） | `value: string \| number \| undefined` |
| `popup-visible-change` | 弹层显隐变化时触发 | `visible: boolean` |

## 注意事项

1. **v-model 绑定**：绑定值默认是格式化后的字符串；`valueFormat` 传 `'timestamp'` 时绑定毫秒时间戳，传 `'Date'` 时绑定 `Date` 对象
2. **输入框可直接输入**：按展示格式解析，回车或失焦写入；解析失败自动回退为当前值
3. **showTime 确认流程**：面板选中日期后不立即写入绑定值，点击「确定」才写入并触发 `change` / `ok`；时间列默认取当前时间
4. **z-index**：弹层 z-index 内置为 3000，可直接在 FaModal / FaDrawer（z-index 2000）中使用
5. **week 模式**：取值是所选周的起始日（受 `dayStartOfWeek` 控制）；建议显式传 `valueFormat`（如 `'gggg-第wo'`）以获得可读的绑定值
6. **禁用规则**：`disabledDate` 在 `date` 模式逐日判断；`week` / `month` / `quarter` / `year` 模式仅当整周 / 整月 / 整季 / 整年全部日期都被禁用时该单元格才不可选
7. **quarter 模式**：取值是该季度首月 1 日（默认绑定格式 `YYYY-MM`，如 `2026-07`）
