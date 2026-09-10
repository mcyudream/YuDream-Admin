# SPI 版本说明与升级指引

`yudream-plugin-spi` 是插件唯一编译期契约模块，坐标 `online.yudream.base:yudream-plugin-spi`。**当前源码版本：2.26.0**（以 `yudream-plugins/yudream-plugin-spi/pom.xml` 与根 `pom.xml` 的 `yudream.plugin.spi.version` 为准）。注意：2.25.0（插件主题能力）与 2.26.0（主题配置 schema + 主题块提供者）目前**仅本地 install、未发布 Nexus**，下游插件仓暂不可依赖。

源码升版不等于已发布到 Nexus。下游插件仓只应依赖已验证发布的版本；未跑通发布/验签流水线的版本不得用于生产插件。

配套前端契约是 `@yudream/plugin-sdk`，当前 npm 包版本 **1.5.0**（`yudream-frontend/packages/plugin-sdk/package.json`）。后端 `@PluginFrontend.sdkVersion` 填写宿主实际注入的 SDK 行为版本，目前为 `1.5.0`（运行时 `sdk.messaging` / `sdk.users` / `sdk.ai` / `sdk.files.thumbUrl`）。1.4.0 新增构建期导出 `./uno-config`，1.5.0 新增列表缩略图出口。

## 版本化文档结构

SPI 接口文档按版本号组织，每个版本一个完整教程目录：

```text
/plugin/spi/
  index.md                 ← 本页（版本清单 + 升级指引 + Agent 增量生成规范）
  v1/
    core                   生命周期与 PluginContext
    annotations            注解声明（含 GlobalWidget、siteNav）
    http                   HTTP 端点
    framework-services     框架能力端口
    file-preview           文件预览
    extension              扩展点 / 登录注册拦截
    graph                  平台 Neo4j 图数据库投影端口
```

每个页面按统一结构编写：**作用说明 → 方法签名表（名称/签名/参数/返回/说明）→ 使用示例 → 注意事项**。这保证任意版本教程可独立阅读，也便于机器比对。

## 版本清单

| 版本 | 状态 | 说明 |
|---|---|---|
| [v1 (2.26.0)](/plugin/spi/v1/core) | 当前源码（2.25.0/2.26.0 本地未发布） | 全量 API 教程。相对早期文档已补齐消息 `protocol`、AI 流式、宿主目录 SDK、官方 QQ、文件预览、入站邮箱、扩展点（登录/注册拦截与身份核验）、菜单显隐与公开站 `siteNav`、插件主题（含 homePreset 整套模板）、主题配置 schema 与主题块提供者 |

## 2.26.0 相对 2.24.0 的增量

v1 教程目录继续沿用，不另开 `v2/`。下列能力已在当前源码中，文档已同步：

| 范围 | 变更 |
|---|---|
| 插件主题（2.25.0，本地未发布） | `@PluginTheme` + `PluginContext.registerTheme(...)` 注册 SITE/ADMIN 整套主题：互斥顶替与激活位持久化、`homePreset` 首页方案 + `pages[]` 整套页面按 `themeCode` 隔离导入；主题样式经 `/api/platform/plugins/{code}/assets/**` 下发 |
| 主题配置 schema（2.26.0，本地未发布） | `@PluginTheme(configSchema="...")` 声明 JAR 内 theme-config.json，宿主渲染独立配置页 `/platform/theme-center/config/{theme}`，值按主题持久化，模板以 `theme.config.*` 消费 |
| 主题块提供者（2.26.0，本地未发布） | `PluginThemeBlockProvider`（`code/name/supportedThemes/data(ctx)`）：插件经 `registerExtension` 向公开站模板贡献 `blocks.{code}` 数据，只暴露公开安全字段 |
| 消息连接 | `PluginMessagingConnection` 增加 `protocol`（`milky` / `official`）。四参数构造保持源码兼容，缺省 `protocol=null`。`platform` 仍为 `qq`，不要用 `platform` 区分官 Q / Milky |
| 消息目录 | 宿主提供登录即可读的目录 HTTP：`GET /api/platform/plugins/messaging/connections`、`/groups?connectionId=`。插件前端用 `sdk.messaging`，不要再包一层插件 HTTP，也不要打 `/api/platform/milky/**` |
| 用户 / 部门 / 角色目录 | `GET /api/platform/plugins/users`、`/resolve`、`/departments`、`/roles`。插件前端用 `sdk.users`。`roles()` 是全站角色选项；按用户查角色仍走后端 `PluginUserService.listRoles(userId)` |
| AI 目录 | `GET /api/platform/plugins/ai/agents`、`/providers`。插件前端用 `sdk.ai`。真正对话 / 跑 Agent 仍走后端 `framework.ai()` |
| AI 流式 | `PluginAiService.chatStream(request, onDelta)` 与 `chatStream(request, onDelta, onTool)`；默认实现退化为一次性 `chat` 后回放，宿主可覆盖为真流式 |
| QQ 协议 | 能力码仍是 `milky`（展示名「QQ 消息平台」）。一条连接可选 Milky 或腾讯官方 OpenAPI v2，出站经 `RoutingMilkyApiGateway` 分流。官方身份是 openid；群列表来自进程内事件缓存，无历史拉取 |
| 消息身份绑定 | `PluginUserService` 增加默认方法 `findByMessagingIdentity` / `bindMessagingIdentityOnce` / `listMessagingIdentities`，并新增 `PluginMessagingIdentity`。旧插件继续 `bindQqOnce(userId, event.userId())` 即可：宿主按当前事件协议写入身份表。Milky 数字 QQ 仍镜像到 `User.qq`；官方 openid 不再写入该字段。历史 `User.qq` 启动时迁到 `sysMessagingIdentity` |
| 文件预览 | `FrameworkServices.filePreview()` / `PluginContext.filePreview()`，能力码 `file-preview`。插件拿签名地址与 KKFILE/DIRECT/NONE 决策，不要自行对接 kkFileView |
| 入站邮箱 | `FrameworkServices.inboundMail()`，能力码 `inbound-mail`。IMAPS 只读核验，插件拿不到凭据或正文 |
| 扩展点 | `registerExtension` / `extensions`；内置登录/注册拦截器与 `IdentityVerificationProvider`。disable/unload 自动回收 |
| 菜单显隐 | `PluginContext.setMenuVisible(routePath, visible)`，按路由路径匹配侧边栏，匹配不到时静默忽略 |
| 公开站导航 | `@PluginRoute.siteNav` 仅在 `publicAccess=true` 时生效，把页面注入 CMS 公开站头导航并使用站点 chrome |

## 升级指引

- 插件侧升级 SPI 只需修改依赖版本并按上方变更记录适配；宿主与 SPI 版本兼容矩阵见插件商店索引。
- SPI 遵循语义化原则：新增接口/方法为 minor；删除或改变签名为 major，会提前在变更页给出迁移代码对照。`PluginMessagingConnection` 的五参数 record 通过四参数兼容构造保持旧插件可编译。
- 未验证发布的版本不得用于下游（发布流水线 verify 通过后才可用）。新增 `PluginContext.graph()` 等框架能力端口时，必须先在 SPI 源码和宿主适配中实现，再补齐精确签名文档并发布；插件不得读取 Neo4j 环境变量或自行创建 Driver 暂时代替逻辑图表契约。
- 宿主目录（连接/群、用户/部门/角色、Agent/供应商）属于前端选择器能力，走 SDK 而不是插件自己的 HTTP。插件业务 CRUD、当前用户部门（如 `/me/departments`）、表单/题库等插件自有资源仍走 `/api/plugins/{pluginCode}/**`。

## 面向 Coding Agent 的增量更新规范

本目录结构专门支持"编码代理自动复制增量更新教程"。当 SPI 发布新版本时，代理应按以下流程为新版本生成 `vx.y/` 目录：

1. **Diff 源码**：对比 `yudream-plugins/yudream-plugin-spi/src/main/java` 在新旧版本间的变化：
   - 新增类 → 在对应分类页新增章节；
   - 删除类 → 在迁移章节记录替代方案；
   - 签名变化的方法 → 更新签名表并在"注意事项"标注行为差异。
2. **复制上一版目录**作为起点（如 `cp -r docs/plugin/spi/v1 docs/plugin/spi/v2`），只修改有变化的章节。
3. **保持章节骨架不变**：每页的 H2 标题、表格列（成员/类型/说明）、示例格式必须稳定，便于 diff 与检索。
4. **更新本页版本清单**：新增一行，标注状态（当前/已废弃）与破坏性变更摘要。版本号永远从 `yudream-plugin-spi/pom.xml` 读取，不凭记忆。
5. **同步 config.ts 侧边栏**（`.vitepress/config.ts` 中 `pluginSidebar`）加入新版本条目。
6. **校验**：运行 `pnpm --dir yd-docs build` 确认所有链接有效。

每篇教程中示例代码必须可直接编译（import 路径、泛型、record 构造器与源码一致），参数说明覆盖到每个字段。
