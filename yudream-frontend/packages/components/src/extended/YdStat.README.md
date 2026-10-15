# YdStat / YdStats 统计指标

`YdStats` 将多个指标按横向或纵向排列；默认在窄屏允许换行。`YdStat` 提供标题、数值、说明，以及 `figure`、`actions` 等具名插槽。纯展示数字使用此组件，动画数值仍可组合 `YdCountTo` / `YdAnimatedCountTo`。

## YdStats Props

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `vertical` | `boolean` | `false` | 改为纵向排列 |
| `responsive` | `boolean` | `true` | 横向排列时允许窄屏换行 |

## YdStat Props

| 属性 | 类型 | 说明 |
| --- | --- | --- |
| `title` | `string` | 指标标题 |
| `value` | `string \| number` | 指标值；可用 `value` 插槽替换 |
| `description` | `string` | 补充说明 |

插槽：`figure`、`title`、`value`、`description`、`actions`。
