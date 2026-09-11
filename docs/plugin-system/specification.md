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
- `registerExtension` / `extensions`
- `exposeService`
- `onDispose`

`registerExtension(ExtensionPoint.class, implementation, priority)` 是通用扩展点注册口：扩展点接口可以由宿主定义（如 `system/auth` 包下的认证契约），也可以由 provider 插件在自己的 `*.api` 包定义。priority 数值越小越先执行（默认 0，同值按注册先后）；注册句柄随插件 disable/unload 自动回收，消费方通过 `PluginContext.extensions(...)` 或宿主应用层注入的 `PluginExtensionQuery` 查询，永远看不到已禁用/已卸载插件的残留实现。

宿主内置认证扩展点（`online.yudream.base.plugin.spi.system.auth`）：

- `RegisterInterceptor` / `LoginInterceptor`：veto 型前置拦截器，同步执行、可否决，fail-closed（实现抛异常时本次注册/登录被拒绝）。
- `IdentityVerificationProvider`：声明一种身份核验方式（学信网、教育邮箱、CARSI、人工审核等）；站点设置 `system.auth.registration.required-verifications`（逗号分隔方式编码）声明必需方式，注册时逐一 `check(...)`，缺失或未通过即拒绝。核验交互界面由插件自身端点与前端承载，前端可通过匿名端点 `GET /api/user/register/verification-methods` 获取当前可用方式清单。注册成功后，插件可通过 `PluginUserService.replaceTags(userId, namespace, tags)` 把学校、审核状态等写入系统用户标签（按命名空间隔离，人员管理只读展示）。注册成功后，插件可通过 `PluginUserService.replaceTags(userId, namespace, tags)` 把学校、审核状态等写入系统用户标签（按命名空间隔离，人员管理只读展示）。
- `AuthEventListener`：注册成功 / 登录成功事件订阅，事务提交后派发，fail-open（监听器异常只记日志，不影响源用例）。

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

插件可同时采用以下资源方式：

- **声明式样式（推荐）**：插件在 `vite.config.ts` 中挂载 `@yudream/plugin-sdk/uno-config` 的 `yuDreamPluginUnoCss()`（与宿主一致的 UnoCSS 预设：presetWind4 关闭 reset、shadcn 主题映射、attributify/icons/typography），并在入口 `import 'virtual:uno.css'`。Vite lib 模式用 `cssFileName: 'style'` 固定产物名，Java 侧 `@PluginFrontend(styles = {"style.css"})` 声明后由宿主在导入 `remoteEntry.js` 前加载、随页面切出按引用计数回收。插件只引用宿主注入的主题变量，不得自行重新定义 `:root` 主题变量或引入全局 reset。
- **独立资源**：在 `PluginFrontendModule` 的 `styles` 与 `scripts` 中声明相对路径，例如 `List.of("assets/plugin.css")` 与 `List.of("assets/bootstrap.js")`。宿主在导入 `remoteEntry.js` 前按声明顺序加载 CSS 和 module script。
- **内联样式（遗留兼容）**：`import styles from './styles.css?inline'` 并在 `install()` 中手写 `<style>` 注入的方式已被声明式 `styles` 取代，新插件不得再使用；宿主不会自动加载未声明的独立 CSS。
- **其他静态资源**：随 JAR 放入同一前端目录；插件通过 `sdk.assets.url("assets/logo.svg")` 取得资源 URL。路径必须是相对路径，且不得包含 `..` 或反斜杠。

Vite 产物应保留相对引用和 hash 文件名，保证 CSS、JS chunk、图片、字体能从插件 `/assets/**` 地址加载；动态 import 的 JS chunk 无须额外写入 `scripts`。

### 9.1 主题插件（Plugin Theme）

插件可以通过 `@PluginTheme` 注册整套主题，接管公开站（`/site`）或管理后台的视觉：

```java
@PluginTheme(
    code = "pixel-dream",
    name = "像素梦境",
    description = "像素风公开站主题",
    scopes = {PluginThemeScope.SITE},
    styles = {"theme/site.css"},
    preview = "theme/preview.png",
    homeComponent = "theme/Home",            // 可选：Vue 原生首页组件（远程模块 routes 键，推荐）
    chromeComponent = "theme/Chrome",        // 可选：Vue 原生 chrome 组件（页头/页脚由主题自管）
    homePreset = "theme/home-preset.json",   // 可选：CMS 首页方案（data-yb 模板体系，与 homeComponent 二选一）
    configSchema = "theme/theme-config.json" // 可选：主题配置 schema（WordPress 自定义器形态）
)
public class PixelThemePlugin implements YuDreamPlugin { ... }
```

契约规则：

- 一个插件最多注册一个主题；`scopes` 至少声明一个范围（`SITE` 公开站 / `ADMIN` 管理后台），`styles` 至少声明一个 CSS 资产。
- `styles`/`preview`/`homePreset` 必须是 JAR 内 `META-INF/yudream-plugin/frontend/{pluginCode}/` 下的相对路径，不得包含 `..`、反斜杠或绝对路径；宿主经 `/api/platform/plugins/{code}/assets/**` 下发并携带 `assetRevision` 缓存指纹。
- `homeComponent`/`chromeComponent` 必须是远程模块 `routes` 导出表中的组件键，格式 `[a-z0-9][a-z0-9/-]*/[A-Za-z][A-Za-z0-9]*`（如 `theme/Home`、`theme/Chrome`），注册时校验；`homeComponent` 与 `homePreset` 互斥——前者由 Vue 页面接管公开站首页，后者走 CMS data-yb 模板体系，同时声明以 `homeComponent` 为准。`chromeComponent` 声明后公开站页头/页脚由该组件接管，导航数据由宿主注入（始终含首页 `/site`，CMS 已配置同 URL 则不重复）；未声明时仍由宿主 SiteChrome 承载。
- 同一 scope 同时只激活一个主题：启用声明了主题的新插件时，宿主自动禁用同 scope 冲突的旧主题插件并接管激活位；禁用/卸载/删除主题插件即释放激活位，该 scope 回落宿主内置主题。重启恢复后宿主按持久化激活位校正。
- 主题随路由切换作用域：公开路由（`meta.public`）启用 SITE 主题、禁用 ADMIN 主题，后台路由相反，两个 scope 的 CSS 变量不得互相污染。

Vue 原生主题页（homeComponent + chromeComponent，推荐路径）：

- SITE 主题的版式页就是该插件 frontend 包里的普通 Vue SFC：首页经 `homeComponent` 接管 `/site`；页头/页脚经 `chromeComponent` 接管（导航数据由宿主注入且首位必须是首页 `/site`，主题自己画导航，切页不再换 chrome）。其余主题页经 `@PluginRoute(publicAccess = true, siteNav = true)` 注册为公开站路由并进入站点导航（站内跳转走 router、无整页刷新）。
- 页面数据经 SDK site client 获取（SDK ≥1.7.0）：`sdk.site.context({ blocks, limit, cmsLatest })` 拉取 `GET /api/public/theme/context`（匿名），返回 `{ themeCode, themeConfig, blocks, cmsPagesLatest }`——主题配置（secret 已剔除）、所请主题块与 CMS 最新文章一次拿齐；`sdk.site.applySeo(...)` 设置页面 SEO；`sdk.site.assetUrl(path)` 处理资产路径。拉取失败必须回落静态兜底，页面不得报错。
- 与 CMS 的边界：默认主题与文章内容页（`/site/:slug` 新闻详情等）继续走 CMS 渲染；声明了 `homeComponent` 的主题其版式页不再是 CmsPage，主题中心首页 tab 显示「由插件页面承载」并隐藏 homeHtml 设计器。旧主题遗留的 CMS 首页布局数据保留无害（切回默认主题才用）。
- 主题插件对其他业务插件只能是 `softdepend`（文档与加载顺序意义），**禁止 `depend`**——主题必须可独立运行；块缺失/插件未装载时回落 `theme.config` 静态清单或空态。主题不 import 任何其他插件的代码/组件，数据契约走宿主中转，代码零耦合。

chrome 接管（chromeComponent，推荐）与变量契约（回落）：

- 声明 `chromeComponent` 后由主题远程 Vue 组件完全接管页头页脚（NMO 复刻必须走这条，避免宿主 SiteChrome 在首页/内页切样式）。
- 未声明时仍用 chrome 变量契约：宿主 SiteChrome 对以下变量**只消费不赋值**，主题在 style.css 里设变量即可改外观：
  - `--yb-site-header-position` / `--yb-site-header-top` / `--yb-site-header-border` / `--yb-site-header-background` / `--yb-site-header-backdrop`（整条头部）；
  - `--yb-site-header-bar-width` / `--yb-site-header-bar-min-height` / `--yb-site-header-bar-margin` / `--yb-site-header-bar-padding` / `--yb-site-header-bar-border` / `--yb-site-header-bar-radius` / `--yb-site-header-bar-bg` / `--yb-site-header-bar-shadow`（头部内栏，盒式导航条）；
  - `--yb-site-header-brand-display`（品牌区显隐）、`--yb-site-header-nav-justify`（导航对齐）。
- 注意宿主自己赋值的 `--yb-site-header-bg` 是兜底色、由宿主在未分层样式里引用——主题要改头部背景必须设契约变量 `--yb-site-header-background`，而不是 `--yb-site-header-bg`。
- 页面级限定用 `.site-chrome:has(.your-home-class)` 设变量（如首页覆盖导航：fixed + transparent），变量继承到头部后代生效；禁止再向 CMS homeCss 写 RAW overlay 选择器 hack（已退役）。

自带首页方案（homePreset）：

- SITE 主题可声明 `homePreset` 指向 JAR 内一份首页方案 JSON（hero 标题/副标题、首页区块 sections、settings 键）；SITE scope 激活时宿主自动读取导入为方案 `plugin:{pluginCode}`（归属该主题）并应用到该主题自己的首页布局，公开站首页整套切换。
- **主题即整套独立模板**：首页布局、首页方案、CMS 页面一律携带 `themeCode`（内置主题为 `default`，插件主题为插件 code）归属各自主题，内容、CSS、HTML、JS 彼此完全隔离、绝不混杂。切换/停用主题只是拨动公开站激活主题指针（Setting `pluginTheme.active.site`），双方数据互不触碰；停用无需备份还原，内容随主题对外不可见，再次启用即原样恢复。
- 导入语义：主题尚无首页布局时，宿主先克隆默认主题当前首页作为起点再应用方案；已有布局且内容与上一版方案一致（未被管理员改动），或当前布局尚未写入 `homeHtml`（仍走 FEATURE 卡片回退），则自动应用新版方案（升级无残留）；管理员已手写 `homeHtml` 则只更新方案不覆盖内容（方案列表一键可应用）。方案内容纯净应用，不与任何基准合并，不会残留上一个主题的 settings 键；应用保留 `published` 发布状态。
- 应用前宿主把该主题当前首页存为「切换前快照」方案（内容一致则去重，每主题快照上限 10 份）；后台「主题中心 → 首页方案」按主题查看方案并一键应用或删除。
- 内容定制能力（cms）关闭时不导入方案、不初始化主题布局，仅应用主题 CSS；方案资产缺失或非法时跳过导入，不影响主题激活。

主题页面集（homePreset.pages[]）：

- SITE 主题的 homePreset JSON 可声明 `pages[]` 整套页面：`{slug, title, summary, coverImageUrl, template(DEFAULT/DOC/LANDING/BLANK), markdownContent, htmlContent, cssContent, jsContent, seoTitle, seoDescription}`，除 slug/title 外均可空。页面随主题启用导入到**该主题自己的页面集**并发布，标记 `themeCode={pluginCode}` 与 `sourcePluginCode={pluginCode}`。
- slug 归属语义：同一主题内 slug 未被占用 → 创建发布；已被**同一插件**占用 → 覆盖更新并发布（主题升级同步内容）；被管理员占用 → 跳过并记 warn，**永不覆盖管理员内容**。不同主题的页面允许复用同一 slug（slug 唯一性按 `(themeCode, slug)` 维度校验）。本次声明清单之外的该插件旧页面 → 转草稿（声明即同步）。
- 可见性语义：公开站只渲染激活主题的首页与页面；主题停用后其页面天然不可见但保持已发布状态（不再下线转草稿），再次启用即原样恢复。
- 内容注入约定：主题页面与 `settings.homeHtml` 一律用 `data-yb-*` 模板指令（`data-yb-for/if/html/markdown/limit` 等）与 `{{路径}}` 变量注入系统数据（站点信息、登录态、最新页面/Wiki 等）；**禁止声明 `navigationJson`**，导航归属主题布局的 settings、始终由系统渲染。主题自带页面 CSS 只写布局，配色应消费主题自身变量。
- 主题中心：后台「平台 → 主题中心」聚合展示全部 SITE 主题卡（预览图、是否含首页方案/页面集、激活态）并支持一键启用/恢复默认；CMS 页面/首页外观/导航/媒体库/首页方案管理均并入主题中心，工作台顶部提供「编辑主题」选择器（默认当前激活主题，可离线预编辑任意主题的内容），页面列表标记归属主题与「插件托管」。注意：菜单为种子初始化，既有部署升级后需手工清理旧的「内容站点（platform:cms）」菜单节点。

主题配置（configSchema，WordPress 自定义器形态）：

- SITE 主题可声明 `configSchema` 指向 JAR 内一份 `theme-config.json`（路径校验规则与 `homePreset` 相同）；声明后主题卡出现「配置」入口，进入独立配置页 `/platform/theme-center/config/{theme}`（左侧分节导航 + 右侧分节表单，宿主按 schema 渲染，主题不提供前端页面）。
- schema 格式：`{ "sections": [{ "code", "title", "description", "fields": [...] }] }`；字段 `{ "key", "label", "description", "type", "placeholder", "default", "options", "secret", "itemFields" }`，`type` 支持 `text`/`textarea`/`number`/`switch`/`select`/`color`/`image`/`list`；`select` 用 `options: [{label, value}]`；`list` 用 `itemFields` 声明子字段形成重复器（值成为真数组，供 `data-yb-for` 遍历）；`image` 走宿主内置上传器（`FaImageUpload` → `POST /api/files/upload`，公开文件，module=`theme-{pluginCode}`），值存站内文件 URL，也可继续使用主题自带资产路径。
- 持久化与安全：配置值按主题隔离存入 Setting（key `pluginTheme.config.{themeCode}`、category `plugin-theme`、type JSON），读取时 schema 默认值与已存值合并；字段标记 `secret: true` 后经凭据加密存储，管理端读取脱敏为空串（另返回 `secretConfigured` 标识），留空保存表示不修改。
- 模板消费：公开站模板上下文根新增 `theme.config`（嵌套对象，**secret 字段已剔除**），可写 `{{theme.config.heroTitle}}`、`data-yb-if="theme.config.showServers"`、`data-yb-for="item in theme.config.introItems"`；保存后公开站即时生效。内置 default 主题无配置页（端点返回空 schema）。

主题块提供者（PluginThemeBlockProvider，插件联合扩展主题动态内容）：

- 系统能力（CMS 页面、Wiki、导航、站点设置）已在模板上下文（`cms.pages.latest`/`knowledge.*`/`navigation`/`site.*`），主题模板直接引用；**插件私有数据**通过 SPI 接口 `PluginThemeBlockProvider` 贡献：`code()`（块 code，插件内唯一）、`name()`、`supportedThemes()`（默认空=全部主题可用）、`data(PluginThemeBlockContext ctx)` 返回 JSON 可序列化对象，ctx 携带 `themeCode` 与 `limit`（模板 `data-yb-limit` 上限）。
- 注册复用既有扩展管道：`onEnable` 中 `context.registerExtension(PluginThemeBlockProvider.class, provider)`，禁用/卸载自动回收，无需新基建。
- 模板消费：`{{blocks.server-list}}`、`data-yb-for="server in blocks.server-list"`、`data-yb-if="blocks.activity-square"`；前端扫描模板中的 `blocks.{code}` 路径随 template-context 请求懒加载（不进入首屏载荷），单块上限 12 个；块不存在/插件未启用/单块异常时该块缺省（if 为假、for 为空），优雅降级不影响页面其余部分。
- Vue 原生主题页消费：同一份块数据也经 `GET /api/public/theme/context?blocks=...` 匿名下发，主题页面 `sdk.site.context({ blocks: ['server-list'] })` 自取——data-yb 模板与 Vue 页面共用同一解析端口，`supportedThemes()` 过滤与单块异常隔离语义一致。
- **公开数据安全约定**：块数据对匿名访问者可见，提供者实现必须只暴露公开安全字段（不暴露内部 ID、凭据、用户隐私）；宿主不做字段级过滤。

CSS 作用域约定：

- SITE 主题只写 `.site-page` / `.site-chrome` 容器与 `--yb-site-*` 变量（背景/文本/标题/主色/边框/导航等），禁止覆写 `:root` 宿主后台变量。
- ADMIN 主题写 `:root` / `.dark` 下的宿主主题变量（OKLCH 三通道，如 `--primary`、`--background`），禁止触碰 `.site-page` / `--yb-site-*`。
- 主题 CSS 不得引入全局 reset，不得改动布局结构类（flex/grid 排版由宿主与插件页面自己控制）。

公开页换肤契约（谁适配谁）：

- 正途是**插件适配主题**：插件公开页应当自带 `--xx-*` 桥接变量（如大事记页面的 `--tl-*`）并回退消费 `--yb-site-*`，主题插件只负责定义 `--yb-site-*` 变量与像素化等通用处理，页面自动跟随换肤。
- 对使用 Arco 组件的插件公开页，主题在插件容器作用域内覆写 `--color-*` / `--primary-6` 变量属合法的变量级适配。
- 主题按其他插件的专属类名（如 `.tl-card`、`.qb-*`）书写样式仅是面向存量插件的**兼容层**，须注释标注；插件迁移到桥接变量后应逐步移除，新增插件公开页不得再要求主题侧追加专属选择器。

宿主端点：

- `GET /api/platform/plugins/themes/active`（匿名）：返回各 scope 当前激活主题（含样式资产相对路径、`assetRevision`，SITE 主题附带 `homeComponent`/`moduleName` 供宿主挂载 Vue 首页），宿主启动时在 `app.mount` 前注入常驻 `<link data-yudream-theme-scope>` 避免主题闪烁。
- `GET /api/platform/plugins/themes`（`platform:plugin:view`）：全部已启用插件声明的主题与各 scope 激活者，供主题设置页展示。
- `GET /api/platform/themes/overview`（`platform:theme-center:view`）：主题中心聚合视图——SITE 主题卡（预览资产、含首页方案/页面集标记、激活态）+ 激活主题的首页方案列表（含 `active` 标志）+ 可编辑主题清单 `editableThemes`（默认主题 + 主题插件卡 + 拥有存量布局的主题）；cms 能力关闭时降级为主题卡 + 空方案列表。
- `POST /api/platform/themes/{code}/activate` / `POST /api/platform/themes/deactivate`（`platform:theme-center:use`）：启用指定 SITE 主题（互斥顶替）/ 停用当前 SITE 主题回落内置。
- `GET /api/platform/themes/{code}/config` / `PUT /api/platform/themes/{code}/config`（`platform:theme-center:config`）：主题配置 schema + 合并值读取（secret 脱敏）/ 按 schema 校验保存，公开站即时生效。
- `GET /api/public/cms/template-context`（匿名）：模板上下文查询参数新增 `blocks`（块 code 列表）与 `blockLimit`，按激活 SITE 主题过滤 `supportedThemes()` 后逐块调用提供者，响应根新增 `blocks` 映射；公开首页/页面载荷新增 `themeConfig`（剔除 secret 的主题配置对象）。
- `GET /api/public/theme/context`（匿名）：Vue 原生主题页的数据端点，入参 `blocks`（逗分隔块 code）、`limit`（块条数上限）、`cmsLatest`（CMS 最新文章条数），返回 `{ themeCode, themeConfig, blocks, cmsPagesLatest }`——与模板 blocks 同一解析端口与降级语义，全部匿名安全字段。

## 10. 菜单与路由规范

插件菜单由 `@PluginFrontend` 和 `@PluginRoute` 声明。

顶级菜单：

- `menuTitle` 必填，除非插件不需要菜单。
- `menuIcon` 使用 Iconify 名称。
- 插件根 `plugin.yml` 可声明可选 `icon`，作为插件管理、市场和未显式声明菜单/路由图标时的默认图标。
- `icon` 支持已注册的 Iconify/FaIcon 名称（如 `i-ri:puzzle-2-line`）或插件图片资源/安全 URL；图片资源应随插件发布并使用相对路径。
- 显式的 `menuIcon`、`icon`、`parentIcon` 优先于插件级默认 `icon`。
- `menuSort` 控制顶级排序，越大越靠前。

路由：

- `path` 必须全局唯一。
- `name` 必须全局唯一。
- `component` 对应插件前端导出的页面。
- `permission` 与页面访问权限一致。
- `sort` 控制同级页面排序。
- 多页面插件应使用 `parentTitle`、`parentPath`、`parentSort` 形成菜单目录。
- `publicAccess` 标记匿名可访问的公开路由，公开路由随未登录 manifest 下发并注册为顶级路由。
- `siteNav` 必须与 `publicAccess` 一起使用：页面注入 `/site` 公开站页头导航，并以站点页头/页脚布局渲染；导航项默认排在首页之后、CMS 导航之后、知识库入口之前，在 CMS 导航中配置同 URL 条目可覆盖其位置。公开站导航始终含首页 `/site`（品牌链接不能替代导航项）。

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
