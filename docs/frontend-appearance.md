# 后台外观预设与动效

后台「系统 → 主题配置」提供三套内置外观：灵动（默认）、雅致、紧凑。预设调整卡片圆角、阴影、工作台留白和交互节奏；主色、明暗模式和导航模式仍由既有主题设置控制。点击预设即时预览，点击「保存设置」通过原有主题 API 保存到 `sys_setting` 的 `appSettings` JSON。

## 配置与运行时

宿主应用设置中的 `appearance` 形如：

```json
{ "preset": "vibrant", "motion": "system" }
```

`preset` 可选 `vibrant` / `calm` / `compact`；`motion` 可选 `system` / `reduced`。旧配置与未知值自动回落默认值。三套外观都保留已启用的功能动画，包括计数、翻词、加载、流光、跑马灯和轮播。灵动保留完整动效；雅致与紧凑在 `motion=reduced` 或操作系统 `prefers-reduced-motion: reduce` 时柔化动作、降低装饰密度、放慢循环，不统一停用动画或将计数直接跳到终值。外观设置继续由宿主持久化，YD 组件读取根节点设置，不增加 SPI 主题字段。

应用 store 将配置投射到 `html[data-admin-style][data-admin-motion]`。样式默认值统一在共享 `@yudream/components/appearance.css` 的 `@layer yudream-base` 根节点中声明，宿主 `src/assets/styles/admin-appearance.css` 仅导入该文件。YD 控件、后台布局和实时预览均消费这些变量；业务页面仍只使用中性语义变量。宿主和预览共用 `@fantastic-admin/themes/runtime` 的主色转换，默认值从 FA settings 读取，为 `#18181b`。

## ADMIN 主题插件

现有 `@PluginTheme(scopes = ADMIN)` 与声明式 `style.css` 即可覆盖下列变量，不需要增加 SPI 字段。变量必须声明在 `:root` / `.dark`；插件样式所在的 `yudream-plugin` 层高于内置默认值。宿主不得把这些变量写为 inline style，也不得在布局子元素上重设默认值，否则会挡住插件的根变量继承。

| 变量 | 用途 | 灵动默认值 |
| --- | --- | --- |
| `--yd-surface-radius` | 工作台、主题设置的卡片圆角 | `18px` |
| `--yd-control-radius` | 导航项圆角 | `12px` |
| `--yd-surface-shadow` / `--yd-surface-shadow-hover` | 卡片静态与聚焦/悬停阴影 | 中性色阴影 |
| `--yd-workspace-gap` | 工作台外侧间距 | `24px` |
| `--yd-surface-padding` / `--yd-content-gap` | YD 卡片内边距与内容间距 | `24px` / `16px` |
| `--yd-control-height` / `--yd-control-font-size` | 默认控件高度与字号，显式 size 优先 | `40px` / `14px` |
| `--yd-decoration-duration` | 装饰流光和闪烁节奏 | `4s` |
| `--yd-motion-loop-scale` | 循环时长倍率；雅致/紧凑减少偏好默认 `1.6` | `1` |
| `--yd-emphasis` | 文字与装饰强调色，深色模式提升可读性 | 从 FA 主色与前景色派生 |
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

ADMIN 插件仍通过插件管理启停，停用后恢复已选内置预设；SITE 插件仍通过内容站点主题中心管理，两者继续由现有主题运行时按路由切换样式 `media`。雅致或紧凑下系统/用户选择减少动态效果时，采用非零的柔和过渡、较小位移与按压幅度，循环时长增加。默认令牌仍位于基础层，插件可以覆盖；插件应延续功能动画持续运行、减少偏好柔化动作的约定。

## 实现约束

登录页采用通栏品牌区与表单区，站点名称为主标题，描述和插图来自站点配置。移动端先展示精简品牌区，再进入表单；不使用固定底栏或装饰性循环动画。SITE 主题可在 `.login-page` 内桥接 `--color-*` 与组件语义变量。深色图片遮罩上的标题必须单独保证对比度，不能直接继承后台浅色模式文字。相对图片 URL 必须在插件自身的选择器声明中消费，避免跨样式表消费变量时按宿主路径解析。每次登录页修改均需验证无插件和启用 SITE 插件两种状态。

- 复用 Fa/Yd 控件，新增视觉只使用主题和中性语义变量。
- 页面使用 Vue Transition；工作台卡片使用 CSS 入场，最多八个错峰节奏步。流光、粒子、跑马灯等装饰动效由共享 YD 组件显式提供，消费主题变量与同一减少动效政策，不给所有业务页面自动添加循环动画或额外动画库。
- GridStack 的拖拽定位由其自身管理，只对内部卡片做入场动画；编辑模式取消入场，动画终态不保留 transform。按钮手柄内的图标须 `pointer-events: none`，避免拖动图标时被 GridStack 的交互元素过滤忽略。
- 悬停反馈仅在支持精确指针与 hover 的设备启用；普通信息卡不抬升，以免误示可点击。
- 新页面可显式给合适的操作入口添加 `yd-interactive`，不全局扫描 DOM 或给所有按钮/表格强加动画。

交互节奏参考 [scaffold-master 的动效实现](https://github.com/wbr664393425-lgtm/scaffold-master/blob/577079e9a347555fadddd3865fc0bbcdb00d9fb4/scaffold-vue/src/composables/motion.ts)，使用现有 Vue/CSS 能力独立实现。
