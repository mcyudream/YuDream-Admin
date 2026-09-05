# YdDatePicker 日期选择器

日期选择器组件，基于 Arco DatePicker 封装并适配 Fa 主题风格，覆盖日期、周、月、季度、年全部模式，支持日期时间联动选择。

## 使用场景

- 表单日期 / 日期时间录入
- 按周、月、季度、年维度的数据筛选
- 带快捷选项的日期选择

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `string \| number \| undefined` | - | 选中值，类型由 `valueFormat` 决定 |
| `mode` | `'date' \| 'week' \| 'month' \| 'quarter' \| 'year'` | `'date'` | 选择器模式 |
| `showTime` | `boolean` | `false` | 是否同时选择时间 |
| `valueFormat` | `string` | `'YYYY-MM-DD'`（`showTime` 时为 `'YYYY-MM-DD HH:mm:ss'`） | 绑定值格式，可传 `'timestamp'` / `'Date'` |
| `format` | `string` | - | 输入框展示格式，默认同 `valueFormat` |
| `placeholder` | `string` | `'请选择日期'` | 占位提示文本 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `readonly` | `boolean` | `false` | 是否只读 |
| `error` | `boolean` | `false` | 是否错误状态 |
| `allowClear` | `boolean` | `true` | 是否允许清除 |
| `dayStartOfWeek` | `0 \| 1` | - | 一周开始于周日还是周一 |
| `disabledDate` | `(current?: Date) => boolean` | - | 不可选择的日期 |
| `disabledTime` | `(current?: Date) => DisabledTimeProps` | - | 不可选择的时间 |
| `shortcuts` | `ShortcutType[]` | - | 快捷选项 |
| `shortcutsPosition` | `'left' \| 'bottom' \| 'right' \| 'top'` | - | 快捷选项位置 |
| `popupContainer` | `string \| HTMLElement` | - | 弹层挂载容器 |
| `class` | `HTMLAttributes['class']` | - | 自定义 CSS 类 |

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| `change` | 值变化时触发 | `value: string \| number \| undefined` |
| `clear` | 点击清除时触发 | - |
| `ok` | 点击确定按钮时触发（`showTime` 需确认） | `value: string \| number \| undefined` |
| `popup-visible-change` | 弹层显隐变化时触发 | `visible: boolean` |

## 注意事项

1. **v-model 绑定**：使用 `v-model` 实现双向数据绑定，绑定值默认是格式化后的字符串
2. **z-index**：弹层 z-index 已内置修复为 3000，可直接在 FaModal / FaDrawer（z-index 2000）中使用
3. **week 模式**：周选择建议显式传入 `valueFormat`（如 `'gggg-第wo周'`）以获得可读的绑定值
4. **禁用日期/时间**：`disabledDate` 与 `disabledTime` 与 Arco DatePicker 行为一致
