# YdFab 浮动动作按钮

用于页面或局部容器角落的少量高频快捷操作。组件以 YD 图标渲染触发按钮与动作；动作由调用方执行，组件只发出 `select(action)`。

## Props 与模型

| 属性 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `actions` | `YdFabAction[]` | 必填 | `{ id, label, icon?, ariaLabel?, disabled? }` |
| `v-model:open` | `boolean` | `false` | 展开状态 |
| `triggerIcon` | `string` | `morph:plus` | 收起时的主按钮图标 |
| `triggerLabel` | `string` | `打开快捷操作` | 主按钮可访问名称 |
| `layout` | `'vertical' \| 'flower'` | `'vertical'` | 扇形最多四项；超过时退化为纵向 |
| `position` | `'fixed' \| 'absolute'` | `'fixed'` | 定位方式；absolute 需父容器建立定位上下文 |
| `closeOnSelect` | `boolean` | `true` | 选择动作后是否收起 |

事件：`select(action)`、`open`、`close`。插槽：`trigger` 接收 `{ open }`，`close` 自定义展开后的关闭图标，`action` 接收 `{ action, index }`，自定义内容仍置于带可访问名称的按钮内部；`main-action` 接收 `{ close }`，在主按钮旁渲染调用方提供的 YD 操作。关闭按钮始终保留，避免主动作占用收起入口。

主按钮原生支持 Enter/Space，Escape 关闭并回焦，点击外部或焦点离开关闭。每个动作必须有简短可访问名称；仅图标时提供 `label` 或 `ariaLabel`。浮动控件不适合替代表单主提交或批量危险操作。
