# FaRangePicker 日期范围选择器

日期范围选择器组件，基于 Arco RangePicker 封装并适配 Fa 主题风格，覆盖日期、周、月、季度、年全部模式，支持日期时间联动选择。

## 使用场景

- 时间范围筛选
- 起止日期录入
- 带快捷选项的范围选择

## Props

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `v-model` | `(string \| number)[] \| undefined` | - | 选中范围 `[开始, 结束]`，元素类型由 `valueFormat` 决定 |
| `mode` | `'date' \| 'week' \| 'month' \| 'quarter' \| 'year'` | `'date'` | 选择器模式 |
| `showTime` | `boolean` | `false` | 是否同时选择时间 |
| `valueFormat` | `string` | `'YYYY-MM-DD'`（`showTime` 时为 `'YYYY-MM-DD HH:mm:ss'`） | 绑定值格式，可传 `'timestamp'` / `'Date'` |
| `format` | `string` | - | 输入框展示格式，默认同 `valueFormat` |
| `placeholder` | `string[]` | `['开始日期', '结束日期']` | 起止占位提示文本 |
| `disabled` | `boolean` | `false` | 是否禁用 |
| `readonly` | `boolean` | `false` | 是否只读 |
| `error` | `boolean` | `false` | 是否错误状态 |
| `allowClear` | `boolean` | `true` | 是否允许清除 |
| `dayStartOfWeek` | `0 \| 1` | - | 一周开始于周日还是周一 |
| `disabledDate` | `(current?: Date) => boolean` | - | 不可选择的日期 |
| `disabledTime` | `(current: Date, type: 'start' \| 'end') => DisabledTimeProps` | - | 不可选择的时间 |
| `shortcuts` | `ShortcutType[]` | - | 快捷选项 |
| `shortcutsPosition` | `'left' \| 'bottom' \| 'right'` | - | 快捷选项位置（Arco 范围选择不支持 `top`） |
| `popupContainer` | `string \| HTMLElement` | - | 弹层挂载容器 |
| `class` | `HTMLAttributes['class']` | - | 自定义 CSS 类 |

## Events

| 事件名 | 说明 | 回调参数 |
|--------|------|----------|
| `change` | 值变化时触发 | `value: (string \| number)[] \| undefined` |
| `clear` | 点击清除时触发 | - |
| `ok` | 点击确定按钮时触发（`showTime` 需确认） | `value: (string \| number)[] \| undefined` |
| `popup-visible-change` | 弹层显隐变化时触发 | `visible: boolean` |

## 注意事项

1. **v-model 绑定**：绑定值为二元数组 `[开始, 结束]`，清除后为 `undefined`
2. **z-index**：弹层 z-index 已内置修复为 3000，可直接在 FaModal / FaDrawer（z-index 2000）中使用
3. **范围禁用时间**：`disabledTime` 通过第二个参数 `type` 区分开始/结束面板
