# YdStateIllustration 状态插画

空态、结果与失败页的命名状态插画。组件只表达状态类别，不承载文案与动作：可读信息由 `YdEmpty` / `YdResult` 的标题、描述与操作插槽提供，插画自身固定 `aria-hidden="true"` 且不可聚焦。

六个命名场景对应固定的语义，不允许把场景当成可换皮肤的插画库：

| `name` | 用途 | 强调色 |
| --- | --- | --- |
| `empty` | 初始为空：还没有数据，等待第一条内容 | 中性 |
| `no-result` | 搜索或筛选无结果 | `--yd-emphasis` |
| `error` | 执行失败、请求错误 | `--yd-status-danger` |
| `forbidden` | 无权限、被拒绝访问 | `--yd-status-danger` |
| `success` | 操作成功完成 | `--yd-status-success` |
| `warning` | 需要用户注意的中间态 | `--yd-status-warning` |

## Props

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `name` | `'empty' \| 'no-result' \| 'error' \| 'forbidden' \| 'success' \| 'warning'` | 必填 | 命名场景 |
| `size` | `'sm' \| 'md' \| 'lg'` | `'md'` | 80 / 120 / 168 像素宽，宽度受容器限制 |
| `animated` | `boolean` | `true` | 强调元素低频上下浮动（3px） |

无事件与插槽，内容全部由 `name` 决定。

## 视觉与动效边界

- 颜色只取中性语义变量（`--muted`、`--card`、`--muted-foreground`）与 `--yd-status-*` / `--yd-emphasis`，随暗色与三套外观自动变化；不得传入自定义颜色或品牌色。
- 只有强调元素做低频位移，节奏取 `--yd-decoration-duration` 与 `--yd-motion-loop-scale`：灵动 4s、雅致 7s、紧凑 9s，减少动效偏好下继续降低幅度。组件不用 RAF、计时器或观察器，卸载时没有需要释放的资源。
- 插画是辅助信息，不能替代状态标题、失败原因与恢复动作。

## 使用

`YdEmpty` / `YdResult` 已经内置插画位，业务页面优先用它们；单独使用本组件仅适用于 `YdEmpty` 无法覆盖的版式（例如页脚、卡片角落的状态提示）。

```vue
<script setup lang="ts">
import { YdStateIllustration } from '@yudream/components'
</script>

<template>
  <YdStateIllustration name="no-result" size="sm" />
</template>
```
