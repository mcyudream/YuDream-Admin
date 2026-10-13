# {{DISPLAY_NAME}}（{{CODE}}）

{{DESCRIPTION}}

## 本地开发

1. `mvn compile` 编译出 `target/classes`（含 plugin.yml）。
2. 在宿主开发者工具「设置」页登记本目录（生成时勾选自动登记可跳过此步）。
3. 开发模式监听到类产物后自动加载；此后改动 `src/main/java` 会自动重编译并重载。

自检：在 QQ 沙盒或群聊中发送 `/{{CODE}}`，机器人应回复 `pong`。

## 目录约定

- `bootstrap`：入口类，只做装配与生命周期，所有注册走 `PluginContext.registerXxx(...)`
- `domain` / `application` / `infrastructure` / `interfaces`：按职责分包，禁止依赖宿主内部模块
- 宿主能力只经 `yudream-plugin-spi` 端口调用；HTTP 端点统一挂载 `/api/plugins/{{CODE}}/**`

## 菜单注册

新增管理页面通过 `@PluginFrontend` / `@PluginRoute` 声明，并在 `plugin.yml` 的 `menu.category` 选择最主要的业务大类：`mc`（MC）、`todo`（待办）、`social`（社交），暂未确定时使用 `other`（其他）。同类插件共用一个一级大图标入口，插件模块与具体功能列在分类下面，不为每个功能生成一级入口。

`menu` 块的默认模式为 `CATEGORY`。自定义分类可以声明 `category: {code: stable-code, name: 分类名称, icon: 'morph:folder', sort: 0}`。明确需要独立一级入口的个例使用 `menu.mode: INDEPENDENT`；旧插件没有 `menu` 块时保持旧结构，也可显式使用 `LEGACY`。既有菜单与管理员调整不会因升级被覆盖；`@PluginFrontend.parentCode` 的显式父菜单优先。
