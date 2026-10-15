# YdEmpty 空态

数据区的空状态容器：图标或插画、标题、描述与操作。用于初始为空与搜索无结果，不用它掩盖请求失败——失败与无权限属于 `YdResult`。

## Props

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `illustration` | `'empty' \| 'no-result' \| 'error' \| 'forbidden' \| 'success' \| 'warning' \| false` | `false` | 命名状态插画；`false` 时渲染默认图标盒 |
| `illustrationSize` | `'sm' \| 'md' \| 'lg'` | `'md'` | 插画宽度 80 / 120 / 168 像素 |
| `icon` | `string` | `'morph:inbox'` | 图标盒图标，仅在不使用插画时生效 |
| `title` | `string` | `'暂无内容'` | 主文案 |
| `description` | `string` | — | 补充说明，建议给下一步动作的线索 |

插槽：`illustration` 覆盖插画（优先于 `illustration` 属性），`icon` 覆盖图标，`title`、`description`、`action` 覆盖文案与操作，默认插槽等价于 `action`。

## 边界

- 默认外观是 56px 虚线图标盒，属于既有契约：不传 `illustration` 的页面保持原样。
- 插画只表达状态类别，配色与动效由 `YdStateIllustration` 统一管理，不接受业务颜色。
- 标题与描述始终可读；插画不能成为唯一的状态信息。
- 区分初始为空（`empty`）与筛选无结果（`no-result`）：前者引导创建第一条数据，后者提示调整条件或清空筛选。
- 需要整页结果、失败或无权限时用 `YdResult`，不要在页面里拼装多个 `YdEmpty`。
- 业务自有画面走 `#illustration` 插槽，自绘 SVG 必须使用中性语义变量并自行适配暗色与三套外观。
