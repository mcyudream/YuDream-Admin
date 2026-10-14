# 插件系统：SPI 边界、生命周期、菜单、主题与 DevTools

> **适用范围**：宿主插件运行时、`yudream-plugin-spi` 契约、官方插件仓协作。
> **目标**：插件只经 SPI 与宿主交互，全部运行贡献可完整回收，菜单/主题/市场语义一致。

契约细则权威在 `docs/plugin-system/specification.md`（原位不动），本文件是工程规则的入口。

## 1. SPI 边界与工程结构

- `yudream-plugin-spi` 是插件**唯一**可依赖的宿主模块；插件代码**禁止**依赖 domain/application/infrastructure/interfaces/bootstrap。
- SPI 包按职责划分：`core`（描述符/生命周期/运行时上下文）、`http`（插件 HTTP 契约）、`frontend`（前端模块/远程入口/动态路由元数据）、`menu`、`permission`、`capability`、`system`（宿主框架能力稳定端口）。
- 插件调用宿主能力只能走 SPI 端口（`system.FrameworkServices`、`system.user.PluginUserService`、`system.security.PluginSecurityService` 等）；需要新能力时**先发布稳定 SPI 端口/DTO，再在宿主实现适配**。
- **禁止**向插件暴露领域聚合根、领域仓储、应用服务、infra 仓储、dataobj、Spring Bean；插件只拿 DTO 风格视图、调用用例导向端口。插件侧 DTO 用稳定、可序列化的 record/简单值对象，不复用接口 `request/res` 或 `dataobj`。
- 插件间业务 API 放 provider JAR 的稳定最小 `*.api` 包；consumer 以 `provided` 编译并通过 `context.service(providerCode, Api.class)` 进程内调用；**禁止**复制 provider API 进 consumer JAR。provider 有意拥有多实现扩展 API 时，各方 `exposeService(ProviderApi.class, impl)`，provider 经 `context.services(...)` 发现。
- 插件按职责分包（domain/application/infrastructure/interfaces/migration/frontend/bootstrap），与宿主同套 DDD 原则；入口类只做装配与生命周期，所有注册走 `PluginContext.registerXxx(...)`，保证 disable/unload 可完整回收。HTTP 端点方法放 `interfaces/controller`，在 `onEnable` 里 `registerHttpController(new XxxController(...))`。

## 2. plugin.yml 与依赖图

- JAR 根**必须**有权威 `plugin.yml`：`name`（唯一稳定 code）/`main`/`version` 必填；`displayName` 仅展示、回落 `name`，**永不**用于 depend/softdepend/路由/服务查找；可选 `git` 声明 http(s) 源码仓库地址（随市场目录下发展示链接）。
- `depend` 硬依赖、`softdepend` 软依赖。**禁止**打包 `META-INF/services/...YuDreamPlugin`，运行时直接实例化 `plugin.yml.main`；静态权限/菜单/路由/端点/能力/迁移优先注解注册，动态贡献用 `registerXxx`。
- 硬依赖必须先于 consumer 加载启用；启动恢复递归先恢复软依赖再建 consumer 类加载器，缺失/禁用/失败的软依赖不阻塞 consumer，consumer **必须**用 `context.dependencyAvailable(code)` 或可选服务结果条件降级，相关菜单/路由/端点/权限/任务**条件注册**而非无条件扫描注解。
- 通用扩展点走 `PluginContext.registerExtension(ExtensionPoint.class, impl, priority)`（数值小先执行，默认 0，同值按注册序），消费方经 `PluginContext.extensions(...)` 或宿主 `system.extension.PluginExtensionQuery` 查询；禁用/卸载自动回收。每个扩展点**必须**文档化失败策略：否决式（注册/登录拦截）fail-closed，通知式（`AuthEventListener`）fail-open。**禁止**反射宿主内部作逃生门。
- **依赖图必须无环**：平台适配器不得 softdepend 已 softdepend 自己的业务插件（`launcher-adapter` 不得声明 `minecraft-server`；服务器列表页与 YMCL scope 由 `minecraft-server` 以 `LauncherProvider` 贡献）。宿主 `reverseDependencyDepth` 的 visiting+memo 只是安全网，不是允许成环的理由——热重载按反向依赖深度停机，成环会 StackOverflowError。
- provider 存在已加载的硬/软依赖方时**禁止**直接卸载，走级联（`cascade=true`）：叶子依赖方先停，仅软依赖目标的插件自动降级恢复，沿硬依赖链依赖目标的插件保持禁用；级联语义同样适用于删除与回滚（回滚级联先停机再换 JAR 再恢复可恢复依赖方）。
- 运行时实现**必须**按插件 code 跟踪并在 disable/unload 释放：disposables、类加载器、已注册 handler、前端 manifest、菜单、权限、能力 provider。load/enable/disable/unload **必须** synchronized 串行化（watcher/HTTP/启动恢复并发触发同一插件生命周期，竞态会重复注册并清掉并发成功方的贡献）。

## 3. HTTP 端点、模板与前端产物

- 插件 HTTP 端点统一挂载 `/api/plugins/{pluginCode}/**`，经运行时分发器；动态 Spring MVC controller 注册在卸载语义/权限/诊断稳定前不用。
- 插件自有 Thymeleaf 模板放插件 JAR 的 `src/main/resources/templates`，经插件作用域 `PluginContext.templateRenderer()` 按逻辑名（不带 `.html`）渲染；运行时绑定该插件 ClassLoader，拒绝穿越/绝对路径，给选择器时用原生元素截图。
- 生产插件前端只通过 JAR 内 ESM `remoteEntry.js` + 宿主注入 SDK 加载；前端资源打进 `META-INF/yudream-plugin/frontend/{pluginCode}`，运行时把 `/api/platform/plugins/{pluginCode}/assets/{assetPath}` 映射到该 classpath 目录。标准 JAR 布局省略 `@PluginFrontend.entry`（默认 `.../assets/remoteEntry.js`）；仅非标准外部托管才显式设 `entry`。
- workspace 加载仅是开发便利；**禁止**生产 manifest 依赖 workspace 别名，宿主 app 不得依赖具体插件前端包。官方业务插件源码默认在 `yudream-admin-plugins` 仓；宿主前端 DEV 模式**不得** auto-glob `packages/plugin-*/src/index.ts`，跨仓本地调试桥必须显式。
- 插件前端包按职责拆分：`src/pages`（路由页）/`src/components`/`src/composables`/`src/api`（SDK 封装）/`src/types.ts`；顶层 shell 只选当前页与共享状态。一个插件路由映射一个真实页面组件，禁止把全部功能藏进一个巨型 Tab 组件。
- 插件前端样式统一走声明式 `style.css`：`vite.config.ts` 挂 `@yudream/plugin-sdk/uno-config` 的 `yuDreamPluginUnoCss()`（与宿主同预设 UnoCSS，关 reset，只引用宿主主题变量），入口 `import 'virtual:uno.css'`，lib `cssFileName: 'style'`，Java 侧 `@PluginFrontend(styles = {"style.css"})`；**禁止** `styles.css?inline` + `install()` 手写 `<style>` 注入。级联隔离：SDK ≥1.6.0 产物全部降入 `@layer yudream-plugin`，宿主层序固定 `yudream-base`（宿主 preflight）< `yudream-plugin` < 宿主未分层工具类——插件可覆写宿主 preflight 与原生元素样式，但永远不能覆盖宿主同名工具类/响应式变体；旧未分层产物仍会顶掉宿主工具类，**必须**随 SDK ≥1.6.0 重建。
- 宿主不扫插件包的 UnoCSS：插件产物工具类必须在构建期自带，不依赖宿主运行时补齐。
- 受保护的插件下载不能 `window.open` 裸 URL（Sa-Token 只读请求头），走宿主 SDK blob 请求并从 Content-Disposition 解析文件名。
- 插件 `PluginDocumentStore` 页大小上限 200：全量扫描/跨插件聚合必须按 ≤200 分页循环；插件前端 bundle 改后**必须**重建（dist 被 gitignore 但打进 JAR）。
- 人员/部门/服务器等选择器**禁止**让用户手输内部 ID：用后端 picker API 搜索解析，以姓名/用户名/邮箱/服务器名等业务标签展示；历史 ID 只允许作搜索兼容入口。

## 4. 菜单：业务大类分类制

- 新插件在 `plugin.yml` 声明 `menu.category`（`mc`/`todo`/`social`/`other` 或自定义 `{code,name,icon,sort}`），`menu.mode` 默认 `CATEGORY`：同类共用宿主拥有的 `plugin-category:{code}` 一级大图标分类（无路由/权限、不随单插件停用），插件模块和功能放分类下，**禁止**每注册一个功能新增一级入口。显式 `parentCode` 优先；`INDEPENDENT` 仅明确个例；缺 `menu` 块或 `LEGACY` 保留旧模式。
- 已持久化及管理员调整过的菜单不自动搬移或覆盖；菜单管理新建根节点默认分类，类型仍可显式选择。前端分类生成必须兼容有 path 的旧根 MENU/LINK，转换菜单/路由时不得丢根菜单与外链。
- 插件菜单声明只用于首次初始化与 `MISSING_ONLY` 补缺失：首初始化以插件记录持久化标记判断，**不能**用当前菜单是否存在猜测；已有菜单/权限零写入；create-missing 撞并发唯一键后按 code 重读收敛。
- `PluginFrontend.parentCode` 指向共享父菜单时不创建消费插件自己的模块目录，路由直接挂该父级。启用只恢复 exact pluginCode 记录，停用/卸载/删除只隐藏 exact pluginCode 记录，**禁止**递归影响子菜单；隐藏持久化失败必须向调用方报错。
- 系统菜单和插件菜单可互为父级，生命周期只看节点自身 pluginCode。无显式权限的 CATEGORY/LAYOUT 是结构节点，随有权限的子路由进路由树，但显式父权限仍生效。插件专用排序接口/UI **禁止**作为第二套菜单配置源。
- frontend-manifest 只提供远程模块加载元数据；菜单树/Layout/名称/图标/路由/权限/父级/显隐/状态统一以 `/api/menu/routes` 的持久化菜单为准；manifest 暂不可用时仍让 runtime-page 用插件默认 remoteEntry。
- 顶栏激活评分每个菜单组：精确路由匹配优先，否则最长段边界前缀胜；**禁止**取第一个前缀命中（插件路由命名空间会跨父级重叠）。
- 详情页/编辑器/操作历史/范围统计等需要上下文标识的插件路由声明 `@PluginRoute(hideInMenu = true)`：宿主保留路由与 Layout 包装，菜单投影 `visible=false`，只从上下文导航进入。
- 插件启用必须重投影前端菜单声明且不覆盖既有菜单字段；被移除声明遗留的 `module:/parent:/route:` 注册标记 `runtimeAvailable=false`，避免升级后上下文父布局残留成重复导航。
- 细则见 `docs/plugin-system/specification.md` 第 10 节。

## 5. 插件主题（SITE/ADMIN）

- 主题经 `@PluginTheme` 注册（一个插件一个主题，`scopes` 声明 SITE/ADMIN，`styles` 至少一个 JAR 前端目录相对路径资产）；同 scope 同时只激活一个，启用新主题插件自动顶替旧插件并持久化激活位（`sys_setting` category `plugin-theme`，key `pluginTheme.active.site|admin`），禁用/卸载/删除释放激活位回落内置主题，`restoreEnabledPlugins` 后 reconcile 校正。
- 前端 `main.ts` 挂载前拉 `/api/platform/plugins/themes/active` 注入常驻 `link[data-yudream-theme-scope]`，theme-runtime 按路由 `meta.public` 切换 site/admin link 启停。CSS 约定：**SITE 只写 `.site-page`/`.site-chrome` 与 `--yb-site-*` 变量，ADMIN 只写 `:root`/`.dark` 宿主 OKLCH 变量**，两 scope 不得互相污染。
- **主题即整套独立模板**：`HomePageLayout`/`HomePagePreset`/`CmsPage` 均以 `themeCode`（`"default"` 或插件 code）归属主题，各主题首页设计/页面集/方案/导航完全独立、绝不混杂；SITE 激活指针只是 Setting 键，切换/停用只拨指针，不做还原或下线——内容天然归属主题，指针离开即对外不可见，再启用原样恢复。
- `homePreset` 声明 JAR 内首页方案 JSON：激活链路 `PluginThemeAppService → CmsPresetAppService.importPluginPreset`：纯量 upsert 方案 `plugin:{code}` → 该主题无布局则克隆 default 当前布局作起点 → 布局未被管理员改动或尚未写入 homeHtml（仍走 FEATURE 卡片回退）则自动应用新方案 → 管理员已手写 homeHtml 则只更新方案不动布局。方案按主题隔离列表与快照计数（每主题上限 10、按单调 id 裁剪）；`apply` 写自身主题布局并保留 published。`SiteThemeQueryService` 直读 Setting 解析激活主题（空→default），公开查询只出激活主题内容。
- `homePreset` JSON 可再带 `pages[]` 整套页面（slug/title/summary/coverImageUrl/template/markdownContent/htmlContent/cssContent/jsContent/seo*）：导入该主题自己的页面集（标 `CmsPage.sourcePluginCode`）为已发布；slug 唯一性按 `(themeCode, slug)`，本主题内未占用→创建发布、同属该插件→覆盖更新、被管理员占用→跳过 warn 永不覆盖；声明清单之外的该插件旧页面转草稿。主题页面与 `settings.homeHtml` 一律用 `data-yb-*` 模板指令注入系统数据，**禁止**声明 `navigationJson`。
- 启动迁移 `CmsThemeBindingInitializer`（幂等 ApplicationRunner，raw Mongo）回填存量 themeCode、按主题去重布局、把历史 `plugin-backup:*` 预设转为主题布局后删除。
- **版式页推荐 Vue 原生承载**（SPI 2.26.0 起）：`@PluginTheme(homeComponent="theme/Home", chromeComponent="theme/Chrome")` 声明远程模块 routes 键（格式 `[a-z0-9][a-z0-9/-]*/[A-Za-z][A-Za-z0-9]*`，注册期校验）。`homeComponent` 接管 `/site`；`chromeComponent` 接管公开站页头/页脚（宿主注入导航数据且必须含首页 `/site`，主题自己画导航，切页不再换 chrome）。公开 `/wiki`（含搜索与文章页）与 `/embed` 走同一套 chrome（默认主题同样），**禁止**独立顶栏。其余版式页走 `@PluginRoute(publicAccess=true, siteNav=true)`；主题页面用 `sdk.site.context({blocks, limit, cmsLatest})` 取公开主题上下文（`GET /api/public/theme/context`，匿名）。主题对业务插件一律 `softdepend`（**禁止** `depend`），不 import 其他插件代码，块缺失即回落 theme-config 静态清单。CMS 回归内容系统：`/site/:slug` 详情页与默认主题首页仍走 CMS 模板引擎。
- 未声明 `chromeComponent` 时仍由宿主 SiteChrome 承载，可继续用 chrome 变量契约：宿主只消费带兜底的 `--yb-site-header-position/top/border/background/backdrop`、`--yb-site-header-bar-*`、`--yb-site-header-brand-display`、`--yb-site-header-nav-justify`，自身永不赋值（主题**必须**写 `--yb-site-header-background`，不是宿主兜底的 `--yb-site-header-bg`）。
- 启动页皮肤属主题：宿主 `loading.html` 只保留默认动画，**禁止**把主题 GIF/字体放进宿主 `public/`；SITE 主题用自己的 style.css 覆盖 `[data-app-loading]`；`index.html` 在公开路径（含无 token 的 `/`）立刻注入上次缓存的 SITE CSS 并 preload 主题 loading 图，theme-runtime 在 router 就绪前按 URL 公开前缀打开 SITE media，样式与 GIF 就绪后才 `loadingFadeOut`；已登录的 `/` 仍是后台。插件产物在 `@layer yudream-plugin`，覆盖宿主未分层 loading 样式**必须**带 `!important`。
- **主题配置走声明式 schema**：`@PluginTheme(configSchema="...")` 声明 JAR 内 `theme-config.json`（`{sections:[{code,title,description,fields:[...]}]}`，type 支持 text/textarea/number/switch/select/color/image/list，list 用 `itemFields` 成重复器；`default` 经 `@JsonAlias` 映射 `defaultValue`；image 走宿主 FaImageUpload，`module=theme-{pluginCode}`，值存站内 URL）。值按主题隔离持久化 Setting（key `pluginTheme.config.{themeCode}`、category `plugin-theme`、type JSON）；`secret` 字段经 `CapabilityCredentialCipher` 加密（AAD `capability:pluginTheme.config.{theme}.{field}`），管理端脱敏为空串+`secretConfigured`、留空保存不修改，`publicConfig` 剔除 secret 且永不阻断渲染。端点 `GET/PUT /api/platform/themes/{code}/config`（`platform:theme-center:config`）；配置页隐藏路由 `/platform/theme-center/config/:theme`；模板以 `theme.config.*` 消费即时生效；default 主题无配置页。image 字段清空**必须**存空串，读取不得回落 schema default。
- **插件数据进主题模板走块提供者**：SPI `PluginThemeBlockProvider`（`code()/name()/supportedThemes()` 默认空=全主题/`data(ctx)` 返回 JSON 可序列化对象），`onEnable` 中 `context.registerExtension(...)` 注册、禁用自动回收；application 经 `PluginExtensionQuery` 直取。模板写 `{{blocks.xxx}}`/`data-yb-for="x in blocks.xxx"`；前端 `templateContextQuery()` 扫描 `blocks.{code}` 随 `GET /api/public/cms/template-context?blocks=` 懒加载（不进首屏，单次上限 12 块，code 正则 `[a-z0-9][a-z0-9-]{0,39}`），按激活 SITE 主题过滤 `supportedThemes`、仅启用插件、逐块异常隔离。块数据匿名可见，提供者只暴露公开安全字段，宿主不做字段级过滤。
- 主题中心挂在平台「内容站点」下（`platform:theme-center`，`/platform/theme-center`，已取代原 `platform:cms` 种子菜单并吸收其页面/首页定制/导航/媒体库/首页方案 tab；权限 `platform:theme-center:view/:use`）；后端 `ThemeCenterAppService.overview/activate/deactivateSiteTheme`，cms 能力关闭时 overview 降级。工作台「编辑主题」选择器可离线预编辑任意主题（默认当前激活）；home/pages/navigation/presets 四个 tab 经 `CmsController ?theme=` 按所选主题读写。既有部署的种子菜单需手工删除旧 `platform:cms` 节点。
- 公开页换肤正途是插件适配主题：插件自带 `--xx-*` 桥接变量回退消费 `--yb-site-*`；主题按插件专属类名写样式仅作兼容层，须注释标注并随插件迁移逐步移除。主题私有变量（如 `--neco-*`）其他插件**禁止**引用。
- 细则见 `docs/plugin-system/specification.md` 9.1。

## 6. 外部登录 SPI

- **SPI 2.29.0 自动开户绑定**：用 `PluginExternalLoginIdentity.authenticatedUserId`（字符串 ID），只能声明本次新建或已验证本站凭据的用户；**禁止**按邮箱匹配或浏览器参数自动认领已有账号。宿主核销 state 后必须检查停用与绑定冲突，缺省字段保持 `BIND_REQUIRED`；开户前用 `PluginUserService.findByExternalIdentity` 排除既有绑定。详见 `docs/plugin-system/spi-2.29.0-external-login.md`。
- **SPI 2.32.0 登录入口呈现**：`PluginExternalLoginProvider.presentation()`（default `ICON`；`TAB` 登录方式 Tab；宿主对未实现/返回 null/抛异常一律回落 ICON）；`descriptor().sort()` 同时对 Tab 与图标排序，内置基线账号密码 100、Passkey 200，`sort < 100` 排在账号密码前。首选登录方式是站点级配置（Setting 键 `loginDefaultMethod`，取值 `password`/`passkey`/`external:{providerCode}:{type}`，非法值归一为空串），随 `/api/settings/public` 匿名下发；入口未启用/下线/非 Tab 时静默回落账号密码，绑定流程（`externalLoginBindingToken`）忽略该配置。宿主登录页账号输入框与「记住账号」行**必须**常驻（`v-show` 不用 `v-if`），否则浏览器自动填充与 vee-validate 注册失效。详见 `docs/plugin-system/spi-2.32.0-login-tab.md`。

## 7. DevTools 与 Agent 追踪

- 开发者工具只在宿主侧：`platform/devtools` 后端四层 + `plugin-devtools` 前端 store/浮窗；Agent 执行追踪在 `platform/agent`。插件零适配，不碰 `yudream-plugin-spi` 契约。见 `docs/plugin-system/dev-mode.md`。
- 开发模式开关 `yudream.platform.plugin.dev-mode.enabled` 三态：缺省由 `DevModeEnvironment` 自动判定（网关类 CodeSource 是目录=源码运行=开，JAR=关），显式 `true/false` 优先；`@ConditionalOnProperty` 看不到自动值，`PluginDevModeWatcher` 只能在 `@PostConstruct` 按 effectiveEnabled 决定是否起线程，并日志大声告警闸门来源。
- 开发项目双来源由 `PluginDevProjectCatalog` 合并：CONFIG（yml `dev-mode.projects`，面板只读）优先于 FILE（面板登记，持久化到 `plugins/dev-projects.json`，已 gitignore，tmp+原子 move 写入，mtime 缓存自动重载）；同 code 冲突 CONFIG 胜并告警；面板增删仅作用 FILE；登记已启用插件立即发 `PluginDevReloadRequested.ofRegister` 热切；code 留空从 `target/classes/plugin.yml` 再 `src/main/resources/plugin.yml` 推断，失败提示先编译。`POST /dev-projects/batch` 走 `Catalog.addFromDirectory`（深度 ≤3、跳 `node_modules/target/dist/src/.git`/隐藏目录、上限 100、同 code 保留排序首项、重复 skipped、只 persist 一次）。
- 默认 `compileCommand`（单个登记留空、批量登记、`DevProject` 字段默认）是 `mvn -q compile -DskipTests -P dev-export`：开发模式 ClassLoader 只挂 `target/classes + target/plugin-dev/lib/*.jar`，不查 Maven 仓库，缺 profile 时第三方 SDK 只在编译期可见（NoClassDefFoundError 先例 AlipayApiException）；自定义命令除非插件无第三方运行时依赖，否则**不得**丢该 profile。生产 shaded JAR 仍须自带这些类；依赖变化后跑 `mvn -pl <模块> -am -P dev-export package -DskipTests`。注意 `mvn compile` 到不了 `process-classes`，dev-export 必须显式 `process-classes`/`package`。
- watcher 三条管线：自动编译 / classes 热重载 / dist 前端重挂载；`reloadDevPlugin` 定向 `describeDevPlugin` 同步描述符，按 code 分段锁串行，级联停启硬/软依赖方（外层先停、依赖序恢复，依赖方失败不阻断目标），`wasEnabled` 含 `restoreIntentActive`；REGISTER 触发的源码切换约 30s 窗口内可抑制，WATCHER 不抑制；编译失败发事件，**绝不**重载陈旧产物或拖崩宿主。
- 前端热重载独立于 Java 重载：`POST /api/platform/plugin-devtools/plugins/{code}/frontend-reload` 只发 FRONTEND_RELOAD（须开发模式且已启用）；`assetRevision` 必须按 dist 实时计算（liveAssetRevision），浏览器 ESM 按 URL 缓存，`forceReload` 要重新拉 manifest 并给 entry 追加 `reload-${Date.now()}`；浮窗按钮成功后本地立即 emit `plugin-devtools:remote-reload`，不依赖 SSE 已连接。浮窗 SSE 收到 RELOAD/FRONTEND_RELOAD 成功后除重挂载当前插件页外，还防抖 `refreshDynamicRoutes` 重建动态路由与菜单（动态路由登录后只注册一次），并重置 publicAccess 公开路由 memo。
- SSE 纪律：sa-token 只读 Authorization 头，EventSource 无法携带，devtools/agent SSE 一律 fetch + ReadableStream 手工解析 `event:`/`data:` 分块；生产 JAR 默认关开发模式，后端 SSE 端点必须 `requireDevMode()`，前端 `connect()`/`onMounted` 必须看 `status.devModeEnabled === true`；重连对 400/401/403/404/405 停流，网络失败有限次重试。
- 浮窗（`src/layouts/components/PluginDevtools/`）是唯一面板面，无固定管理页/菜单种子：FAB 在根布局、可拖边缘吸附（24px 内收成边缘条），面板 Teleport 到 body 的非模态浮窗（不用抽屉），头拖+角缩放，几何持久化 `pluginDevtoolsPanel`，`Ctrl/Cmd+Shift+D`/`Esc` 切换；左侧图标导航五页（概览/插件/追踪/审计/设置）。z-index：侧栏 1010、顶栏 1020、宿主模态 2000，FAB/面板 2100，面板内模态 2200、再嵌套 2300。可见性需 `platform:plugin-devtools:view` + devModeEnabled。
- DevTools HTTP 挂 `/api/platform/plugin-devtools/**`（`platform:plugin-devtools:view`/`manage`）；SSE 桥广播 `plugin-lifecycle`/`agent-trace`。指令模拟器 `POST /{code}/command-test` 免 QQ 调试指令。
- 插件日志流：`SystemLogQuery` 增 `loggerPrefix`（startsWith），domain `PluginLoggerPrefix.of(mainClass, code)` 从 mainClass 截取 `online.yudream.base.plugin.` 根包后第一段；`recentPluginLogs/subscribePluginLogs` 经 `SystemLogRepo` 过滤（默认 100、上限 500）；接口层 `PluginLogStreamBridge` 每连接一条订阅（`GET .../plugins/{code}/logs[/stream]`）；前端 `usePluginLogs` 组合 REST 最近清单 + fetch SSE，按 sequence 去重、500 条封顶。
- 重载资产差异：domain `PluginRuntimeAssetsDiff.diff(before, after)` 按类别（menus/permissions/capabilities/dashboardCards/frontendModules/httpEndpoints/commands/messageInteractions/aiTools/agents/exposedServices）产 Entry(category, added, removed)；`PluginLifecycleEvent` 携带 assetsDiff（保留 7 参重载兼容）；reload 前后各取 `runtimeGateway.runtimeAssets(code)` 快照。
- 依赖图与禁用预览：`GET /plugins` 携带 depend/softdepend；`GET /plugins/{code}/disable-preview` 计算 blockers（启用中传递硬依赖方，最外层在前）/softDependents/unloadBlockers。注意 `PluginAppService.listInstalled()` 的 DTO loaded/enabled 恒 false，真实运行状态用 `PluginRuntimeGateway.loaded/enabled`。
- 脚手架：`POST /scaffold` 生成独立 Maven 插件模块（模板 `devtools/scaffold/*.tpl`，`{{TOKEN}}` 替换）；独立 pom 无 parent/repositories，SPI 经本机 `~/.m2` 解析（默认版本常量 `PluginScaffoldGenerator.DEFAULT_SPI_VERSION` 须与宿主根 pom 同步，可请求 `spiVersion` 覆盖）；生成 plugin.yml、含 ping 自检指令的入口类（包名 `online.yudream.base.plugin.{code 去连字符}`）与四个空分包。
- Agent 追踪：`yudream.platform.agent.trace.enabled`（默认 true）装饰 `AgentWorkflowRuntimeService.execute(...)`，每个入口（chat/wiki/cms/debug/plugin）都留痕；Mongo TTL（`retention-days` 默认 7）+ 按 source 上限；步进事件桥接 SSE；只在完成时落库，运行中执行只经 DevTools SSE 实况可见。历史追踪在日志菜单 `system/agent-trace`（`GET/DELETE /api/platform/agent-traces`、Excel 导出），不依赖开发模式。

## 8. QQ 沙盒

- 沙盒复用生产 Milky 标准化/指令/绑定/权限分发，但 `PluginEvent.connectionId` **必须**用 `devtools-sandbox:{sessionId}` 截获 messaging/raw；真实 policyConnectionId 只读加载群策略与历史。插件异步回调按 synthetic connection 从会话仓重新附着 context；会话 overlay 跨多轮保持，删除/超时/30 分钟 TTL/LRU 淘汰时取消 pending future 并完整清理。
- 身份模拟走 `SandboxAwarePluginUserService`（`@Primary` 装饰 `PluginUserFrameworkService`），仅 `QqSandboxExecutionScope` 激活时改写：`forceUnbound` 让 `findByQq` 返回 empty，`simulateRoles` 三态（null=真实/空列表=无角色/code 列表=指定角色，未知 code 入 unknownRoles）；沙盒内 `bindQqOnce/create/updateProfile` 抛 BizException。每次改写追加 `sandbox/identity.override` 时间线事件。
- 错误可观测性三层兜底：`QqSandboxDiagnostics.appendError` 把指令/交互处理器/分发逃逸异常以 `{errorType,message,stackTrace}` 入时间线；`QqSandboxLogAppender`（logback）在作用域激活时桥接插件包 WARN/ERROR 日志为时间线事件并从 logger 名推导插件码。**插件自己 catch 且不记日志的异常宿主不可见**。
- 用例保存/回放：domain `QqSandboxCase` + `QqSandboxCaseRepo` 端口；infra `JsonQqSandboxCaseRepo` 落 `plugins/qq-sandbox-cases.json`（tmp+ATOMIC_MOVE）。回放=create(setup)+逐条同步 send()；端点 `GET/POST/DELETE /qq-sandbox/cases`、`POST /cases/{id}/replay`；前端从 `message.synthetic` 事件 payload 收割步骤。
- 注入消息 type 支持 message/group_request/button：group_request 复用生产 `dispatchGroupRequest`（data 需 group_id/user_id/request_id/comment），button 合成 `button_click` 由 `MilkyPluginEventDispatcher` 新分支路由 `onButton`；入站 action 区分 `*.synthetic`；面板支持时间线导出 JSON。

## 9. 官方 QQ 机器人

- 指令菜单/面板写入受 30 QPM 查询、10 QPM 创建修改限制：启动先 GET 远端菜单与各 scope 面板到进程内快照，插件全部加载后按 remark+items 语义差异分类创建或更新；运行中插件启停/重载与本地快照对比，30s 合并写入，无变动跳过。官方无面板删除接口。菜单写入走 `set_official_menu`，不要用 `PUT /v2/menu` 字面量。
- 交互回复：`PluginMessageContent` 带 `List<Button>`（`Button.command/callback` 静态工厂）；官方连接无媒体一律 `msg_type=2` markdown 兜底（http(s) 图片附件以 `[[title]]` 内嵌为 `![title](url)`），buttons 映射 keyboard（一行 4 个、最多 5 行，enter=true→指令，false→回调，permission type=2 群成员可用），referrer 的 msg_id/event_id 透传为被动回复凭证；Milky 连接带按钮置 `degraded=true`。QQ markdown 无表格，指令菜单用安全模板（标题/分组/列表/引用/***分割线）。
- 断线优先 `OP_RESUME`（IDENTIFY 有配额）；传输错误与 `OP_RECONNECT/OP_INVALID_SESSION` 控制信号降 WARN。

## 10. 业务插件既有约定（消费方遵守）

- project-progress：打卡频次是自然时间桶非滚动间隔（1440 分钟=本地日历日至少一次）；自动 MC 打卡在当桶去重，手动可重复。管理员计入项目成员；指派/随机指派/发布指派/认领明细必须把被指派人加入成员集；删项目/明细级联打卡、验收与进度事件。验收证据存 `ProjectFileEvidence`，经认证 SDK blob 预览下载；统计含指派明细、完成、待验收、验收通过/驳回与项目级打卡。
- 富回复图片选择：答案模型用 `[[wiki-image:1]]` 索引占位选图，插件只解析被选占位为附件，消息适配器按附件标题交错文图段，禁止把检索到的图全部追加。
