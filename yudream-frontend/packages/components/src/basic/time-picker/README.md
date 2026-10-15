# YdTimePicker 时间选择器

时间选择器组件，基于 YD 系统时间列（系统风格）实现，支持单时间选择与时间范围选择，列粒度与 12 小时制跟随 `format` 与 `use12Hours`。

## 使用场景

- 表单时间录入
- 起止时间段选择
- 自定义步长 / 禁用时间的时间选择

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `string \| (string \| undefined)[] \| undefined` | - | 选中值；`range` 时为 `[开始, 结束]` 数组 |
| `range` | `boolean` | `false` | 是否为时间范围选择 |
| `format` | `string` | `'HH:mm:ss'` | 时间格式，同时决定绑定值格式与时间列粒度 |
| `placeholder` | `string \| string[]` | `'请选择时间'`（`range` 时为 `['开始时间', '结束时间']`） | 占位提示文本 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `readonly` | `boolean` | `false` | 是否只读 |
| `error` | `boolean` | `false` | 是否错误状态 |
| `allowClear` | `boolean` | `true` | 是否允许清除 |
| `use12Hours` | `boolean` | `false` | 是否 12 小时制（显示 上午/下午 列） |
| `step` | `{ hour?: number, minute?: number, second?: number }` | - | 时分秒选项步长 |
| `disabledHours` | `() => number[]` | - | 禁用的小时（0–23） |
| `disabledMinutes` | `(selectedHour?: number) => number[]` | - | 禁用的分钟 |
| `disabledSeconds` | `(selectedHour?: number, selectedMinute?: number) => number[]` | - | 禁用的秒 |
| `hideDisabledOptions` | `boolean` | `false` | 是否隐藏禁用的选项 |
| `popupContainer` | `string \| HTMLElement` | - | 弹层挂载容器 |
| `class` | `HTMLAttributes['class']` | - | 自定义 CSS 类 |

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| `change` | 值变化（确认）时触发 | `value: string \| string[] \| undefined` |
| `clear` | 点击清除时触发 | - |
| `popup-visible-change` | 弹层显隐变化时触发 | `visible: boolean` |

## 注意事项

1. **v-model 绑定**：绑定值为按 `format` 格式化的字符串（范围为数组）；输入框可直接输入，回车或失焦按 `format` 解析写入
2. **z-index**：弹层 z-index 内置为 3000，可直接在 FaModal / FaDrawer（z-index 2000）中使用
3. **确认交互**：时间面板选择后需点击「确定」才会写入绑定值；`range` 时面板同时给出开始/结束两组时间列
4. **列粒度**：时间列由 `format` 决定（含 `HH`/`hh` 出小时列、`mm` 出分钟列、`ss` 出秒列、`A`/`a` 出上下午列）；`use12Hours` 强制显示上午/下午列，写入仍按 `format` 输出
5. **步长与禁用**：禁用项仍显示但不可点（`hideDisabledOptions` 可隐藏）；切换小时导致当前分钟/秒被禁用时，会自动收敛到最近的可用值
