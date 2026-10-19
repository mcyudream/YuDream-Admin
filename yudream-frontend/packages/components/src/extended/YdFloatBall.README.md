# YdFloatBall 可拖拽圆球入口

悬浮在视口角落的圆形入口：一个图标 + 可选角标，可拖拽、松手吸附最近的左右边缘，并按 `storage-key` 记住位置。适合「打开某个面板/视图」的常驻入口（AI 助手、客服、快捷工具）；不承担表单提交或危险操作。

## Props 与事件

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `icon` | `string` | `morph:bot` | 圆球图标（YD 图标名） |
| `label` | `string` | `AI 助手` | 可访问名称，同时作为原生 `title` |
| `badge` | `string \| number` | — | 角标文案；空字符串、`undefined`、`null` 不显示 |
| `size` | `number` | `54` | 圆球直径（px） |
| `draggable` | `boolean` | `true` | 关闭后固定不动，仅响应点击 |
| `storageKey` | `string` | — | 传入后按账号/场景持久化 `{ side, y }`（如 `yd.aiAssistant.ball.123`） |
| `edgeOffset` | `number` | `20` | 距视口边缘的最小间距 |
| `bottom` | `number` | `96` | 首次出现时距底部的距离 |
| `busy` | `boolean` | `false` | 运行中：图标旋转并显示脉冲圈 |
| `disabled` | `boolean` | `false` | 禁用点击与拖拽 |
| `expanded` | `boolean` | `false` | 映射 `aria-expanded`，表示对应面板是否已打开 |

事件：`click(event)`。拖拽位移超过 4px 时不触发 `click`，避免误开面板。

## 交互约定

- 拖拽使用 Pointer Events，视口缩放（`resize`）后自动回到可视范围；圆球整体始终留在视口内。
- 松手时按圆球中心与视口中线的关系吸附到左/右边缘，位置随 `storageKey` 写入 `localStorage`；存储不可用时只保留本次会话位置。
- 键盘可达：原生 `button`，Enter/Space 触发点击，`focus-visible` 有描边。
- `busy` 与角标只表达状态，不代替错误提示；进入全屏接管页时由调用方隐藏（宿主用 `html[data-fullscreen-stage="true"]` 规则让位）。
- 尊重 `prefers-reduced-motion`：减少动效时不做旋转/脉冲。
