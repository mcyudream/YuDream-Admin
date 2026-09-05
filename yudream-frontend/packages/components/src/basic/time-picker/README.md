# YdTimePicker 时间选择器

时间选择器组件，基于 Arco TimePicker 封装并适配 Fa 主题风格，支持单时间选择与时间范围选择。

## 使用场景

- 表单时间录入
- 起止时间段选择
- 自定义步长 / 禁用时间的时间选择

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `string \| (string \| undefined)[] \| undefined` | - | 选中值；`range` 时为 `[开始, 结束]` 数组 |
| `range` | `boolean` | `false` | 是否为时间范围选择 |
| `format` | `string` | `'HH:mm:ss'` | 时间格式，同时决定绑定值格式 |
| `placeholder` | `string \| string[]` | `'请选择时间'`（`range` 时为 `['开始时间', '结束时间']`） | 占位提示文本 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `readonly` | `boolean` | `false` | 是否只读 |
| `error` | `boolean` | `false` | 是否错误状态 |
| `allowClear` | `boolean` | `true` | 是否允许清除 |
| `use12Hours` | `boolean` | `false` | 是否 12 小时制 |
| `step` | `{ hour?: number, minute?: number, second?: number }` | - | 时分秒选项步长 |
| `disabledHours` | `() => number[]` | - | 禁用的小时 |
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

1. **v-model 绑定**：使用 `v-model` 实现双向数据绑定，绑定值为按 `format` 格式化的字符串（范围为数组）
2. **z-index**：弹层 z-index 已内置修复为 3000，可直接在 FaModal / FaDrawer（z-index 2000）中使用
3. **确认交互**：时间面板选择后需点击「确定」才会写入绑定值，与 Arco TimePicker 行为一致
