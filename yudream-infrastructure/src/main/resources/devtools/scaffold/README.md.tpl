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
