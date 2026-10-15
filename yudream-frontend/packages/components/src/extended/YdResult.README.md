# YdResult 结果与状态页

整页或整块的结果状态：成功、处理完成、需要注意、失败、无权限、页面不存在、服务异常。空白数据区用 `YdEmpty`；本组件用于一次任务或一次访问的结论与下一步操作。

## Props

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `status` | `'success' \| 'info' \| 'warning' \| 'error' \| '403' \| '404' \| '500'` | `'info'` | 决定默认标题、图标与插画场景 |
| `illustration` | `'auto' \| 'empty' \| 'no-result' \| 'error' \| 'forbidden' \| 'success' \| 'warning' \| false` | `'auto'` | `auto` 按 `status` 映射；`false` 回落图标盒 |
| `illustrationSize` | `'sm' \| 'md' \| 'lg'` | `'lg'` | 插画宽度 80 / 120 / 168 像素 |
| `title` | `string` | 按状态给出中文标题 | 主文案 |
| `description` | `string` | — | 失败原因与恢复提示 |

插槽：`illustration` 覆盖插画，`icon` 覆盖图标（仅在无插画时可见），`action`、默认插槽承载下一步操作。

`status` 到插画的默认映射：`success`→`success`、`warning`→`warning`、`error`→`error`、`500`→`error`、`403`→`forbidden`、`404`→`no-result`、`info`→图标盒（没有对应的命名场景）。

## 边界

- 失败必须给出原因与可执行的恢复方式；按钮文案说明动作结果，异步操作期间展示 loading，失败保留现场。
- `info` 不强行套插画：它的语义是"处理完成"，与空态、错误、无权限都不同。
- 颜色由状态令牌与插画的强调色统一决定，图标盒底色使用 `color-mix` 从 `--yd-status-*` 派生，不写死颜色。
- 不要在弹窗或行内轻提示里使用结果页版式：短反馈用 `YdToast`，需要持续可见的说明用 `YdAlert`。
