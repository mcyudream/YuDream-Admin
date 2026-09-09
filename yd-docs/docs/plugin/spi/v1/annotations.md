# 注解声明

> SPI v1 · 当前源码 2.24.0 · 包 `online.yudream.base.plugin.spi.annotation`

优先使用注解声明静态能力（宿主扫描注册）；只有动态能力、条件注册或兼容逻辑才使用命令式 `registerXxx`。

## @PluginSpec

插件身份声明，写在入口类上，也是 `descriptor()` 的默认数据源。TYPE、RUNTIME、@Inherited。

| 属性 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `code` | String | ✅ | 插件唯一 code，须与 plugin.yml `name` 一致 |
| `name` | String | ✅ | 显示名 |
| `version` | String | ✅ | 版本号 |
| `description` | String | ❌ 默认 `""` | 描述 |
| `dependencies` | String[] | ❌ 默认 `{}` | 硬依赖插件 code 列表 |

```java
@PluginSpec(code = "sample-plugin", name = "样例插件", version = "1.0.0",
        description = "YuDream 插件系统样例")
```

## @PluginPermission / @PluginPermissions

TYPE 上可重复（容器注解 `@PluginPermissions`），声明权限点。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `code` | String | 必填 | 权限码，约定 `plugin:{code}:{action}` |
| `name` | String | 必填 | 显示名 |
| `module` | String | 必填 | 所属模块（权限管理树节点） |
| `description` | String | `""` | 描述 |

```java
@PluginPermission(code = "plugin:demo:view", name = "查看演示插件", module = "平台插件")
@PluginPermission(code = "plugin:demo:manage", name = "管理演示插件", module = "平台插件")
```

## @PluginFrontend

TYPE。声明前端 remote 模块及路由。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `entry` | String | `""` | ESM `remoteEntry.js` 相对路径；留空由宿主推导为 `/api/platform/plugins/{pluginCode}/assets/remoteEntry.js`（标准 JAR 布局） |
| `moduleName` | String | 必填 | Federation 模块名 |
| `sdkVersion` | String | `""` | 宿主注入 SDK **行为**版本。当前宿主运行时为 `1.5.0`；npm 包 `@yudream/plugin-sdk` 为 1.5.0（含 `thumbUrl` 与构建期 `./uno-config`） |
| `integrity` | String | `""` | 资源 SRI 校验值 |
| `menuTitle` | String | `""` | 插件顶级菜单名 |
| `menuIcon` | String | `""` | 顶级菜单图标（Iconify 名称） |
| `menuSort` | int | 0 | 顶级菜单排序，越大越靠前 |
| `parentCode` | String | `""` | 父模块 code |
| `styles` | `String[]` | `{}` | 附加样式资源相对路径列表，随 remote 模块一同由宿主加载 |
| `scripts` | `String[]` | `{}` | 附加脚本资源相对路径列表，随 remote 模块一同由宿主加载 |
| `routes` | `PluginRoute[]` | `{}` | 路由列表 |

## @PluginRoute

嵌套注解（不可独立标注），用于 `@PluginFrontend.routes` 与命令式注册。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `path` | String | 必填 | 路由路径，全局唯一 |
| `name` | String | 必填 | 路由名，全局唯一 |
| `title` | String | 必填 | 页面标题 |
| `component` | String | 必填 | 组件标识，对应插件前端导出的页面组件（如 `Home`） |
| `icon` | String | `""` | 图标 |
| `parentPath` / `parentTitle` / `parentIcon` / `parentSort` | — | `""`×3 / 0 | 将多个页面归到插件目录下形成菜单分组 |
| `permission` | String | `""` | 访问所需权限码 |
| `sort` | int | 0 | 同级排序，越大越靠前 |
| `hideInMenu` | boolean | false | 保留路由但从导航隐藏（详情页等上下文页） |
| `publicAccess` | boolean | false | 免登录公开路由（对应 HTTP 端点也必须免权限） |
| `siteNav` | boolean | false | 注入 CMS 公开站头导航；仅与 `publicAccess` 同时生效，页面使用站点 chrome 而非后台布局 |

```java
routes = {
    @PluginRoute(path = "/platform/plugins/demo/list", name = "platform-plugin-demo-list",
            title = "资源列表", component = "ResourceList",
            parentTitle = "演示插件", parentPath = "/platform/plugins/demo",
            permission = "plugin:demo:view", sort = 10),
    @PluginRoute(path = "/platform/plugins/demo/detail", name = "platform-plugin-demo-detail",
            title = "详情", component = "ResourceDetail",
            hideInMenu = true, permission = "plugin:demo:view")
}
```

## @PluginMenu / @PluginMenus

TYPE 可重复。纯后端声明的菜单项（无前端路由时使用）。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `title` / `path` | String | 必填 | 标题 / 路径 |
| `icon` | String | `""` | 图标 |
| `permission` | String | `""` | 所需权限 |
| `parentPath` | String | `""` | 父菜单路径 |
| `sort` | int | 0 | 排序 |

## @PluginHttpEndpoint

METHOD 级端点声明，配合 `context.registerHttpController(controller)` 使用。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `method` | String | 必填 | HTTP 方法（GET/POST/PUT/DELETE…） |
| `path` | String | 必填 | 插件内相对路径（以 `/` 开头），实际挂载 `/api/plugins/{pluginCode}{path}` |
| `permission` | String | `""` | 所需权限，空则不校验 |
| `wrapResult` | boolean | true | true 时返回值被宿主包成统一 `Result` 信封；外部协议兼容场景设 false 原样输出 |

处理方法签名约定：`PluginHttpResponse xxx(PluginHttpRequest request, PluginContext context)`，两个参数均可省略。

```java
public class DemoController {
    @PluginHttpEndpoint(method = "GET", path = "/hello", permission = "plugin:demo:view")
    public PluginHttpResponse hello(PluginHttpRequest request) { ... }

    @PluginHttpEndpoint(method = "POST", path = "/notify", wrapResult = false)
    public PluginHttpResponse notify(PluginHttpRequest request) { ... } // 支付回调等
}
```

## @PluginDashboardCard / @PluginDashboardCards

TYPE 可重复。首页仪表盘卡片。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `code` / `title` | String | 必填 | 卡片 code / 标题 |
| `description` | String | `""` | 描述 |
| `icon` | String | `""` | 图标 |
| `category` | String | `"插件"` | 分类 |
| `permission` | String | `""` | 可见所需权限 |
| `component` | String | `"ACTION_CARD"` | 前端卡片组件类型 |
| `actionPath` | String | `""` | 点击跳转路由 |
| `dragPayloadTemplate` | String | `""` | 拖拽负载模板 |
| `tone` | String | `"blue"` | 色调 |
| `defaultW` / `defaultH` | int | 4 / 3 | 默认宽高（栅格单位） |
| `minW` / `minH` | int | 3 / 2 | 最小宽高 |
| `sort` | int | 500 | 排序 |
| `defaultOnFirstVisit` | boolean | false | 首次访问首页的默认指引；用户保存个人布局后不再自动展示 |

## @PluginCapability / @PluginCapabilities

TYPE 可重复。声明插件提供的平台能力（受双闸门管控）。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `code` / `name` / `type` | String | 必填 | 能力 code / 显示名 / 类型 |
| `description` | String | `""` | 描述 |
| `icon` | String | `""` | 图标 |
| `defaultConfig` | `PluginConfigEntry[]` | `{}` | 默认配置键值对 |
| `dependencies` | String[] | `{}` | 运行时依赖的能力 code：依赖不可用拒绝启用；禁用依赖级联禁用依赖方 |

```java
@PluginCapability(code = "mc-server-ping", name = "MC 服务器探活", type = "monitor",
        dependencies = {"sse"})
```

## @PluginConfigEntry

无 Target（作嵌套值使用）。`key()` 与 `value()` 均必填——能力的默认配置键值对。

## @PluginCommand / @PluginCommands

METHOD 可重复。把方法声明为消息命令处理器（聊天平台命令）。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `code` | String | 必填 | 命令业务 code |
| `command` | String | 必填 | 触发词（如 `/weather`） |
| `name` | String | 必填 | 显示名 |
| `permission` | String | `""` | 所需权限 |
| `description` | String | `""` | 描述 |
| `allowAnonymous` | boolean | false | 是否允许匿名触发 |

```java
@PluginCommand(code = "weather", command = "/weather", name = "天气查询",
        description = "查询指定城市天气")
	public void onWeather(PluginCommandContext ctx) { ... }
```

## @PluginGlobalWidget / @PluginGlobalWidgets

TYPE 可重复。声明登录后控制台布局常驻的远程挂件（网页宠物、全局助手等）。manifest 对匿名访客不下发。

| 属性 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `code` | String | 必填 | 挂件编码，插件内唯一 |
| `component` | String | 必填 | 远程模块 routes 映射中的组件键 |
| `permission` | String | `""` | 可见所需权限；空表示登录即可见 |
| `sort` | int | 500 | 排序 |

挂件组件通过 props 接收 `sdk`。编程式注册走 `context.registerGlobalWidget(...)`。

## 注意事项

- 软依赖相关功能**不能**用无条件扫描的静态注解声明——必须改用 `onEnable` 内 `dependencyAvailable(...)` 判断后的命令式注册。
- 权限编码遵循 `view`/`use`/`manage` 三级划分，前端按钮权限与接口保持一致。
