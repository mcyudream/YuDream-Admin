# 声明式注册条目：record 速查

`PluginContext.registerXxx(...)` 接收的条目全部是不可变 record。本页逐个列出字段与语义；注解方式（`@PluginMenu` 等）与这些 record 一一对应，字段名相同。

> 源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/` 下 `menu/`、`permission/`、`capability/`、`dashboard/`、`frontend/`、`widget/` 包

---

## PluginMenuItem —— 菜单

`context.registerMenu(item)`

| 字段 | 类型 | 说明 |
|---|---|---|
| `title` | `String` | 菜单标题 |
| `path` | `String` | 前端路由路径 |
| `icon` | `String` | 图标（iconify 名称） |
| `permission` | `String` | 可见所需权限码 |
| `parentPath` | `String` | 父菜单路径，顶级传 `null` |
| `sort` | `Integer` | 排序权重 |

## PluginPermissionItem —— 权限

`context.registerPermission(item)`

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | 权限码（`plugin:{code}:{action}`） |
| `name` | `String` | 展示名 |
| `module` | `String` | 所属模块（权限树分组） |
| `description` | `String` | 说明 |

## PluginCapabilityItem —— 平台能力条目

`context.registerCapability(item)`

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | 能力 code |
| `name` | `String` | 展示名 |
| `type` | `String` | 能力类型 |
| `description` | `String` | 说明 |
| `icon` | `String` | 图标 |
| `defaultConfig` | `Map<String,String>` | 默认配置键值 |
| `dependencies` | `List<String>` | 依赖的其他能力 code，不可用时拒绝启用 |

## PluginDashboardCard —— 仪表盘卡片

`context.registerDashboardCard(card)`

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `code` | `String` | 必填 | 卡片唯一标识 |
| `title` / `description` | `String` | — | 标题与说明 |
| `icon` | `String` | — | 图标 |
| `category` | `String` | `"插件"` | 分类 |
| `permission` | `String` | — | 可见权限 |
| `component` | `String` | `ACTION_CARD` | 前端渲染组件类型 |
| `actionPath` | `String` | — | 点击跳转路径 |
| `dragPayloadTemplate` | `String` | — | 拖拽负载模板 |
| `tone` | `String` | `blue` | 色调 |
| `defaultW` / `defaultH` | `int` | — | 默认网格尺寸 |
| `minW` / `minH` | `int` | — | 最小网格尺寸 |
| `sort` | `int` | `500` | 排序 |
| `defaultOnFirstVisit` | `boolean` | `false` | 用户首次访问首页时默认展示（引导卡） |

提供全参构造器（16 参）与省略 `defaultOnFirstVisit` 的 15 参便捷重载。

## PluginFrontendModule —— 前端模块

`context.registerFrontend(module)`

| 字段 | 类型 | 说明 |
|---|---|---|
| `entry` | `String` | remoteEntry 入口路径，约定 `META-INF/yudream-plugin/frontend/{code}/remoteEntry.js` |
| `moduleName` | `String` | ESM 模块名（容器名称） |
| `sdkVersion` | `String` | 依赖的 `@yudream/plugin-sdk` 行为版本；当前宿主运行时为 `1.5.0`（npm 包 1.5.0） |
| `integrity` | `String` | 资源完整性校验值（可空） |
| `menuTitle` / `menuIcon` | `String` | 模块菜单标题/图标 |
| `menuSort` | `Integer` | 菜单排序 |
| `parentCode` | `String` | 挂载到哪个父模块/菜单下 |
| `styles` / `scripts` | `List<String>` | 额外注入的样式与脚本 |
| `routes` | `List<PluginFrontendRoute>` | 路由声明 |

提供 3 个便捷重载（按必填程度递减）。

## PluginFrontendRoute —— 前端路由

| 字段 | 类型 | 说明 |
|---|---|---|
| `path` | `String` | 路由路径 |
| `name` | `String` | 路由名（唯一） |
| `title` / `icon` | `String` | 菜单展示 |
| `parentPath` / `parentTitle` / `parentIcon` / `parentSort` | — | 父级菜单信息（可自动建父菜单） |
| `component` | `String` | **remoteEntry 导出的组件名**——运行时页据此从 `module.routes` 映射取组件 |
| `permission` | `String` | 访问权限 |
| `sort` | `Integer` | 排序 |
| `hideInMenu` | `boolean` | 保留路由但不出现在导航（详情页等） |
| `publicAccess` | `boolean` | **免登录公开路由**；需对应后端端点也不要求权限，宿主会将其注册为未登录可访问的顶级路由 |
| `siteNav` | `boolean` | 注入 CMS 公开站头导航；仅 `publicAccess=true` 时生效，使用站点 chrome |

## PluginGlobalWidget —— 全局挂件

`context.registerGlobalWidget(widget)`

| 字段 | 类型 | 说明 |
|---|---|---|
| `code` | `String` | 挂件编码，插件内唯一 |
| `component` | `String` | 远程模块 routes 映射中的组件键 |
| `permission` | `String` | 可见所需权限；空表示登录即可见 |
| `sort` | `int` | 排序，默认 500 |

宿主登录后的控制台布局会主动加载对应前端模块并常驻渲染该组件。manifest 对匿名访客不下发挂件。

---

## 注解 ↔ record 对照

| 注解 | 对应 record | 注册入口 |
|---|---|---|
| `@PluginMenu` | `PluginMenuItem` | `registerMenu` |
| `@PluginPermission` | `PluginPermissionItem` | `registerPermission` |
| `@PluginCapability` | `PluginCapabilityItem` | `registerCapability` |
| `@PluginDashboardCard` | `PluginDashboardCard` | `registerDashboardCard` |
| `@PluginFrontend` + `@PluginRoute` | `PluginFrontendModule` + `PluginFrontendRoute` | `registerFrontend` |
| `@PluginGlobalWidget` | `PluginGlobalWidget` | `registerGlobalWidget` |

注解由宿主启动时扫描注册；编程式注册适合运行期动态构造（依赖配置、循环生成等）。

---

> 源码引用：`.../plugin/spi/{menu,permission,capability,dashboard,frontend,widget}/` 各 record；前端加载链路见 [前端生产形态](/plugin/frontend-remote)
