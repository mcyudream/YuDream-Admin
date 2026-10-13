# 后台外观预设与动效

后台「系统 → 主题配置」提供三套内置外观：灵动（默认）、雅致、紧凑。预设调整卡片圆角、阴影、工作台留白和交互节奏；主色、明暗模式和导航模式仍由既有主题设置控制。点击预设即时预览，点击「保存设置」通过原有主题 API 保存到 `sys_setting` 的 `appSettings` JSON。

## 配置与运行时

宿主应用设置中的 `appearance` 形如：

```json
{ "preset": "vibrant", "motion": "system" }
```

`preset` 可选 `vibrant` / `calm` / `compact`；`motion` 可选 `system` / `reduced`。旧配置与未知值自动回落默认值。`motion=system` 尊重操作系统的 `prefers-reduced-motion`，不会强制播放动画。外观配置是宿主扩展，不变更 SPI、SDK 或共享组件契约。

应用 store 将配置投射到 `html[data-admin-style][data-admin-motion]`。样式默认值统一在 `src/assets/styles/admin-appearance.css` 的 `@layer yudream-base` 中声明；消费范围为后台布局、工作台、主题设置与登录页，不重写公开站、业务表格和插件页面的选择器。

## ADMIN 主题插件

现有 `@PluginTheme(scopes = ADMIN)` 与声明式 `style.css` 即可覆盖下列变量，不需要增加 SPI 字段。变量必须声明在 `:root` / `.dark`；插件样式所在的 `yudream-plugin` 层高于内置默认值。宿主不得把这些变量写为 inline style，也不得在布局子元素上重设默认值，否则会挡住插件的根变量继承。

| 变量 | 用途 | 灵动默认值 |
| --- | --- | --- |
| `--yd-surface-radius` | 工作台、主题设置的卡片圆角 | `18px` |
| `--yd-control-radius` | 导航项圆角 | `12px` |
| `--yd-surface-shadow` / `--yd-surface-shadow-hover` | 卡片静态与聚焦/悬停阴影 | 中性色阴影 |
| `--yd-workspace-gap` | 工作台外侧间距 | `24px` |
| `--yd-motion-duration-fast` / `normal` / `slow` | 按压、常规过渡、入场时长 | `140ms` / `260ms` / `440ms` |
| `--yd-motion-ease` / `--yd-motion-ease-spring` | 常规与轻弹缓动曲线 | 缓出 / 轻弹 |
| `--yd-motion-lift` | 可交互元素悬停位移（负数向上） | `-3px` |
| `--yd-motion-press` | 按压缩放 | `.975` |
| `--yd-motion-enter-y` | 入场纵向位移 | `14px` |
| `--yd-motion-stagger` | 工作台卡片错峰间隔 | `45ms` |

例如插件可以在自己的声明式样式内设置：

```css
:root {
  --yd-surface-radius: 14px;
  --yd-motion-lift: -2px;
  --yd-motion-duration-slow: 360ms;
}
```

ADMIN 插件仍通过插件管理启停，停用后恢复已选内置预设；SITE 插件仍通过内容站点主题中心管理，两者继续由现有主题运行时按路由切换样式 `media`。系统或用户选择减少动态效果时，位移、按压、时长和错峰变量优先被归零，插件不应绕过该偏好。

## 实现约束

- 复用 Fa/Yd 控件，新增视觉只使用主题和中性语义变量。
- 页面使用 Vue Transition；卡片使用 CSS 入场，最多八个错峰节奏步，不引入常驻装饰动画或额外动画库。
- GridStack 的拖拽定位由其自身管理，只对内部卡片做入场动画；编辑模式取消入场，动画终态不保留 transform。按钮手柄内的图标须 `pointer-events: none`，避免拖动图标时被 GridStack 的交互元素过滤忽略。
- 悬停反馈仅在支持精确指针与 hover 的设备启用；普通信息卡不抬升，以免误示可点击。
- 新页面可显式给合适的操作入口添加 `yd-interactive`，不全局扫描 DOM 或给所有按钮/表格强加动画。

交互节奏参考 [scaffold-master 的动效实现](https://github.com/wbr664393425-lgtm/scaffold-master/blob/577079e9a347555fadddd3865fc0bbcdb00d9fb4/scaffold-vue/src/composables/motion.ts)，使用现有 Vue/CSS 能力独立实现。
