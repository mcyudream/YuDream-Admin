# YdRangePicker 日期范围选择器

日期范围选择器组件，基于 YD 系统日历面板（Reka + 系统风格）实现，覆盖日期、周、月、季度、年全部模式，支持日期时间联动选择与快捷选项。

## 使用场景

- 时间范围筛选
- 起止日期录入
- 带快捷选项的范围选择

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `(string \| number)[] \| undefined` | - | 选中范围 `[开始, 结束]`，元素类型由 `valueFormat` 决定 |
| `mode` | `'date' \| 'week' \| 'month' \| 'quarter' \| 'year'` | `'date'` | 选择器模式 |
| `showTime` | `boolean` | `false` | 是否同时选择时间（仅 `date` / `week` 模式生效） |
| `valueFormat` | `string` | 按模式与 `showTime` 决定 | 绑定值格式，可传 `'timestamp'` / `'Date'` |
| `format` | `string` | 按模式与 `showTime` 决定 | 输入框展示格式，也决定时间列粒度 |
| `placeholder` | `string[]` | `['开始日期', '结束日期']` | 起止占位提示文本 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `readonly` | `boolean` | `false` | 是否只读 |
| `error` | `boolean` | `false` | 是否错误状态 |
| `allowClear` | `boolean` | `true` | 是否允许清除 |
| `dayStartOfWeek` | `0 \| 1 \| 2 \| 3 \| 4 \| 5 \| 6` | `1` | 一周开始于周几（`0` 为周日） |
| `disabledDate` | `(current?: Date) => boolean` | - | 不可选择的日期 |
| `disabledTime` | `(current: Date, type: 'start' \| 'end') => YdPickerDisabledTime` | - | 不可选择的时间（`showTime` 生效） |
| `shortcuts` | `YdPickerShortcut[]` | - | 快捷选项 `{ label, value, disabled?, format? }` |
| `shortcutsPosition` | `'left' \| 'bottom' \| 'right'` | `'top'` | 快捷选项位置 |
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
| `change` | 值变化时触发（范围完整时触发；无 `showTime` 时即选完第二个端点） | `value: (string \| number)[] \| undefined` |
| `clear` | 点击清除时触发 | - |
| `ok` | 点击确定按钮时触发（`showTime` 需确认） | `value: (string \| number)[] \| undefined` |
| `popup-visible-change` | 弹层显隐变化时触发 | `visible: boolean` |

## 注意事项

1. **v-model 绑定**：绑定值为二元数组 `[开始, 结束]`，清除后为 `undefined`；只有两端都选完才写入绑定值，选中第一个端点时只在面板与输入框中预览
2. **z-index**：弹层 z-index 内置为 3000，可直接在 FaModal / FaDrawer（z-index 2000）中使用
3. **范围禁用时间**：`disabledTime` 通过第二个参数 `type` 区分开始/结束面板
4. **week 模式**：两端取值都是所选周的起始日；`quarter` 模式两端取值为季度首月 1 日
5. **禁用规则**：与 YdDatePicker 相同，按整周 / 整月 / 整季 / 整年全部禁用才不可选
6. **showTime 确认流程**：面板提供开始/结束两组时间列，点「确定」才写入绑定值并触发 `change` / `ok`
