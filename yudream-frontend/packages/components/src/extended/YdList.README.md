# YdList 通用列表

用于展示非表格型的内容行，例如最近活动、收藏或通知。管理数据列表仍使用 `YdResponsiveTable` / `YdEnhancedTable`。

## Props

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `divided` | `boolean` | `false` | 在行之间显示分隔线 |
| `dense` | `boolean` | `false` | 缩小行间距 |

## 子组件

`YdListRow` 提供行布局，`grow` 令第二个直接子项占据剩余空间，`wrap` 允许子内容换行。
