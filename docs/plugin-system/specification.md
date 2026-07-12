# YuDream 插件开发规范

本文是插件系统的工程约束。新增插件、改造内置插件、扩展插件 SPI 时必须遵守。

## 1. 依赖边界

插件只能依赖：

- JDK 标准库。
- `online.yudream.base:yudream-plugin-spi`。
- 插件自身显式声明且可随插件隔离加载的第三方库。

插件禁止依赖：

- `yudream-domain`
- `yudream-application`
- `yudream-infrastructure`
- `yudream-interfaces`
- `yudream-bootstrap`
- 主系统内部 Spring Bean、Mapper、Repository、DO、Controller、Request、Response。

需要调用主系统能力时，通过 `FrameworkServices` 或新增 SPI 端口完成。

### 官方业务插件仓位规则

- 官方业务插件默认放在独立仓 `yudream-admin-plugins`，而不是继续回填到 `core` 仓。
- `core` 仓默认保留宿主运行时、`yudream-plugin-spi`、共享前端包、样例插件与迁移期兼容层。
- 本文中出现的 `yudream-plugins/yudream-plugin-{code}`、`yudream-frontend/packages/plugin-{code}` 都表示“当前插件仓内部的相对路径”，不表示必须放回主体仓。
- 分仓默认规则见 [standalone-plugin-repo-default.md](./standalone-plugin-repo-default.md)。

## 2. 命名规范

- 插件编码使用小写短横线：`yudream-skin`、`yudream-wallet`。
- Maven artifact 建议使用 `yudream-plugin-{code}`。
- Java 包名建议使用 `online.yudream.base.plugin.{business}`。
- 权限编码使用 `plugin:{code}:{action}`，例如 `plugin:yudream-skin:manage`。
- HTTP 路径只写插件内相对路径，最终由运行时挂到 `/api/plugins/{pluginCode}`。
- 前端包名使用 `@yudream/plugin-{code}` 或 workspace 内 `packages/plugin-{code}`。

## 3. 插件入口规范

插件入口类职责：

- 在 JAR 根目录的 `plugin.yml` 中声明 `name`、`main`、`version` 与 `depend` / `softdepend`；可用 `displayName` 声明面向用户的显示名称。
- 声明权限、前端、首页卡片、能力等静态元信息。
- 在生命周期方法中组装插件自身服务。
- 通过 `PluginContext` 注册 Controller、插件自有 API 服务、资源清理回调。

插件入口类不应：

- 写业务流程。
- 写数据库迁移和批处理主体逻辑。
- 写 HTTP 请求解析和响应转换。
- 写大量 UI 路由构造逻辑。
- 直接访问主系统内部实现。

## 4. 推荐包结构

中大型插件必须按职责拆分：

```text
bootstrap/        插件入口、运行时组装、生命周期
domain/           聚合、值对象、枚举、领域仓储接口、领域服务
application/      cmd、query、dto、assembler、service
infrastructure/   dataobj、mapper、impl、service、外部 SDK 适配
interfaces/       controller、assembler、request、res、http facade
migration/        迁移任务、迁移 DTO、迁移状态
```

小型插件可以合并部分层，但不得把所有业务都写进插件入口。

## 5. DDD 分层规范

插件如果包含业务状态和业务规则，应遵循主系统 DDD 规则：

- `domain` 不依赖框架和 Web。
- `application` 编排用例，不接收 HTTP request，不返回 HTTP response。
- `infrastructure` 负责持久化和外部技术细节。
- `interfaces` 负责 HTTP 边界和 request/res 转换。

Controller 必须薄：

- 可以读取 `PluginHttpRequest`、调用应用服务、返回 `PluginHttpResponse`。
- 不写业务规则。
- 不做复杂字段映射。
- 不直接操作持久化对象。

## 6. SPI 使用规范

插件必须通过 `PluginContext` 注册运行时贡献：

- `registerPermission`
- `registerMenu`
- `registerCapability`
- `registerDashboardCard`
- `registerFrontend`
- `registerHttpHandler`
- `registerHttpController`
- `exposeService`
- `onDispose`

插件图片模板必须放在插件 JAR 自身的 `templates/` 目录，并通过 `PluginContext.templateRenderer()` 渲染。运行时为每个插件绑定独立 ClassLoader，不允许插件模板落入框架 `templates/` 目录，也不允许使用 `..` 或绝对路径跨插件读取资源。模板渲染支持 Thymeleaf 变量和可选 CSS selector；selector 存在时必须使用原生元素截图。

`plugin.yml` 的 `depend` 是硬依赖：提供方必须先启用；`softdepend` 是可选依赖：消费者必须在 `dependencyAvailable(code)` 为 false 时不注册关联菜单、路由、权限、端点和任务。软依赖功能必须使用条件注册，不能用无条件扫描的静态注解声明。

插件业务 API 不得进入 `yudream-plugin-spi`。提供方可将 `*.api` 与实现放在同一 JAR，并通过 `exposeService(Api.class, implementation)` 导出；消费者通过 Maven `provided` 依赖该 API、在 `plugin.yml` 声明依赖、调用 `service(providerCode, Api.class)`。消费者 JAR 不得重复打包 API 类。

优先使用注解声明静态能力：

- `@PluginSpec`
- `@PluginPermission` / `@PluginPermissions`
- `@PluginFrontend`
- `@PluginRoute`
- `@PluginHttpEndpoint`
- `@PluginDashboardCard`
- `@PluginCapability`

只有动态能力、条件注册或兼容逻辑才使用命令式注册。

## 7. HTTP 接口规范

插件接口统一挂载：

```text
/api/plugins/{pluginCode}/**
```

规则：

- 管理接口必须声明权限。
- 用户侧接口必须以系统用户为核心，使用 `request.principal()` 或 `FrameworkServices.users()`。
- 外部协议兼容接口可以设置 `wrapResult=false`，例如 Authlib、支付回调。
- 长任务日志使用 SSE 或可轮询状态接口，不把大日志塞进一次性响应。
- 文件上传、下载、预览接口要明确权限和内容类型。

路径建议：

```text
GET    /status
GET    /settings
PUT    /settings
GET    /admin/resources
POST   /admin/resources
GET    /me/resources
POST   /me/resources
GET    /migration/{source}/status
GET    /migration/{source}/events
```

## 8. 权限规范

权限要区分查看、使用、管理：

```text
plugin:{code}:view
plugin:{code}:use
plugin:{code}:manage
```

具体规则：

- 管理菜单和管理接口使用 `manage`。
- 普通用户页面和个人资源使用 `use`。
- 公开资源列表或状态页可使用 `view`，完全公开协议接口才允许无权限。
- 前端按钮使用与接口一致的权限，不制造前后端不一致。

## 9. 前端插件规范

插件前端不得侵入主前端业务目录。禁止把插件业务页面写到：

```text
yudream-frontend/apps/*/src/views
```

本地开发目录（相对当前插件仓根目录）：

```text
yudream-frontend/packages/plugin-{code}/
```

对于官方业务插件，这个目录默认位于独立插件仓 `yudream-admin-plugins`；`core` 仓只保留样例插件或迁移期兼容层。

推荐结构：

```text
src/index.ts
src/pages/
src/components/
src/composables/
src/api/
src/types.ts
```

页面规范：

- 每个插件路由对应一个真实页面组件。
- 多管理面应拆成多个路由，不用一个巨型 tab 页面承载全部功能。
- 使用宿主 SDK/client 发请求，不内置私有 axios。
- 使用宿主组件库和现有后台布局风格。
- 管理页优先用列表、筛选、分页、详情抽屉/弹窗，避免营销式页面。
- 生产插件必须导出 remote ESM，不依赖 workspace alias。

生产 JAR 资源路径：

```text
META-INF/yudream-plugin/frontend/{pluginCode}/remoteEntry.js
META-INF/yudream-plugin/frontend/{pluginCode}/assets/*
```

## 10. 菜单与路由规范

插件菜单由 `@PluginFrontend` 和 `@PluginRoute` 声明。

顶级菜单：

- `menuTitle` 必填，除非插件不需要菜单。
- `menuIcon` 使用 Iconify 名称。
- `menuSort` 控制顶级排序，越大越靠前。

路由：

- `path` 必须全局唯一。
- `name` 必须全局唯一。
- `component` 对应插件前端导出的页面。
- `permission` 与页面访问权限一致。
- `sort` 控制同级页面排序。
- 多页面插件应使用 `parentTitle`、`parentPath`、`parentSort` 形成菜单目录。

不需要展示在菜单中的页面，应由运行时或 manifest 显式标记为隐藏路由；隐藏路由仍必须有权限控制。

## 11. 文件与存储规范

- 插件私有文件使用 `context.files()`。
- 插件私有文档使用 `context.documents()`。
- 文件路径应以插件 code 隔离。
- 不直接写主系统上传目录。
- 文件下载接口必须校验权限和归属。

## 12. 生命周期和资源释放

插件启用时注册的资源，禁用/卸载时必须释放。

要求：

- 长连接、线程池、定时任务、外部 SDK client 注册到 `context.onDispose(...)`。
- 不在构造函数中建立外部连接。
- 不在插件扫描阶段执行迁移、网络请求或大 IO。
- `onEnable` 只做轻量装配，昂贵操作延迟到具体业务调用。

## 13. 数据迁移规范

迁移任务应满足：

- 启动接口立即返回任务状态。
- 迁移日志通过 SSE 或状态接口查看。
- 页面刷新后能恢复当前任务状态。
- 迁移过程记录 warning/error，但 UI 不展示长警告列表影响主界面。
- 外部数据与系统用户、部门、角色对齐时，应优先复用系统用户创建规则和默认部门/角色规则。

## 14. 兼容协议规范

支付回调、Authlib Injector、Yggdrasil 等外部协议接口可绕过统一响应包装：

```java
@PluginHttpEndpoint(method = "POST", path = "/notify", wrapResult = false)
```

但仍需：

- 明确认证或签名校验方式。
- 控制错误响应格式。
- 避免泄漏内部异常。
- 与系统用户身份模型对齐，不新增平行账号体系。

## 15. 验证清单

以下命令以“当前插件仓根目录”为工作目录；如果是官方业务插件，默认应在 `yudream-admin-plugins` 中执行。

后端：

```powershell
mvn -pl yudream-plugins/yudream-plugin-{code} -am -DskipTests package
mvn -pl yudream-bootstrap -am -DskipTests compile
```

前端：

```powershell
cd yudream-frontend
pnpm --filter @yudream/plugin-{code} build
pnpm --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0
```

运行时：

- 插件 JAR 可加载。
- 插件可启用、禁用、卸载。
- 权限能注册并分配。
- 菜单路由出现且排序正确。
- HTTP 接口权限正确。
- 前端 remote entry 可加载。
- 禁用后菜单、路由、接口不再可用。
