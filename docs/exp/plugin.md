# 插件经验：生命周期、菜单、主题与调试

> **适用范围**：主仓插件运行时与官方插件开发。
> **目标**：把依赖加载、热重载、主题、打包和消息渠道的已知故障变成可复用诊断。

## 1. 生命周期与依赖

### 热重载时重复注册权限/菜单，失败方清掉成功方贡献

- **现象**：启动恢复、管理端启停与 watcher 同时触发插件 load/enable，出现重复贡献错误，随后权限或菜单消失。
- **根因**：插件生命周期多线程并发；`containsKey/isEnabled` 检查与注解注册之间竞态，catch 清理又误清另一线程已注册成功的贡献。
- **规避**：JarPluginRuntimeGateway 的 load/enable/disable/unload 串行化；检查、注册、清理在同一插件生命周期锁内完成。
- **来源**：`plugin-runtime-lifecycle-lock`；`docs/dev/plugin-system.md`。

### 适配器热重载 StackOverflowError

- **现象**：重载依赖插件时递归停机溢出，或 reload 500。
- **根因**：`depend/softdepend` 图有环；`reverseDependencyDepth` 的 visiting+memo 仅是安全兜底，不会让环语义正确。
- **规避**：适配器不反向 softdepend 业务插件（`launcher-adapter` 不声明 `minecraft-server`）；能力由业务插件经扩展点贡献；重新 load consumer 以刷新其依赖 ClassLoader 快照。
- **来源**：`plugin-softdepend-acyclic`；`docs/dev/plugin-system.md`。

### 插件的 Service 类型在提供方热重载后变成 ClassNotFound

- **现象**：消费插件仍持有旧 ClassLoader 中的 provider API，出现 NCDFE。
- **根因**：consumer load 时快照了依赖 ClassLoader，provider 后启用/替换不会自动更新快照。
- **规避**：按依赖顺序先 unload consumer，再 load/enable provider，最后恢复 consumer；插件 API 由 provider JAR 唯一拥有。
- **来源**：`plugin-softdepend ClassLoader snapshot`。

### 启用/禁用软停用后没有恢复入口

- **现象**：数据保留但状态 DISABLED，管理员无法重新启用。
- **根因**：只实现了停用，没有幂等恢复用例、受保护 API 与 UI。
- **规避**：所有可逆软停用成对实现 enable/disable，恢复只改生命周期状态，不覆盖业务配置。
- **来源**：`reversible-disable`；`docs/dev/backend-architecture.md`。

## 2. 打包与开发模式

### 源码模式编译成功，运行插件报 NoClassDefFoundError

- **现象**：开发模式 classpath 缺第三方 SDK，但 Maven compile 通过。
- **根因**：开发 ClassLoader 只挂 `target/classes` 与 `target/plugin-dev/lib/*.jar`，普通 compile 没把依赖导出。
- **规避**：默认 `mvn -q compile -DskipTests -P dev-export`；依赖变化后 `mvn -pl <模块> -am -P dev-export package -DskipTests`。生产 shaded JAR 必须自带运行时类。
- **来源**：`plugin-devmode-compile-export`；`dev-export phase pitfall`。

### Java 热重载成功但前端仍显示旧代码

- **现象**：前端 dist 已更新，host 仍载入旧 ESM 模块；只发 lifecycle SSE 没变化。
- **根因**：浏览器按 URL 缓存 ESM；assetRevision 冻结或只计算 manifest，forceReload 未刷新 entry URL。
- **规避**：提供独立 frontend-reload endpoint；revision 实时由 dist 计算；重新获取 manifest 并给 entry 加时间戳 query，成功后本地立即 emit 远程重载事件，不依赖 SSE 在线。
- **来源**：`plugin-frontend-reload-esm-cache`、`DevTools frontend reload`。

### 插件前端本地热编译后依赖 JAR 缺失

- **现象**：`mvn compile` 显示编译成功，但插件启动缺依赖类。
- **根因**：`dev-export` 绑定 `process-classes`，普通 `compile` 不会触发导出；提示 Nothing to compile 也不代表导出已更新。
- **规避**：显式运行 `process-classes` 或 `package`；对 Java 改动必要时 touch 目标源确保 Maven 检查新产物。
- **来源**：`dev-export phase pitfall`、`Runtime plugin version check`。

### 受保护的插件文件下载在浏览器里变成未授权

- **现象**：页面裸 `window.open(downloadUrl)` 返回 401/403，但同页 API 请求正常。
- **根因**：Sa-Token 只从 Authorization 请求头取 token，浏览器直接打开 URL 不会携带宿主 SDK 注入的请求头。
- **规避**：用宿主 SDK 发 blob 请求，经 blob 下载工具保存，并从 Content-Disposition 解析文件名。
- **来源**：`plugin-authenticated-blob-download`。

### 同版本 JAR 替换后仍是旧实现

- **现象**：上传同版本 JAR 或 enable 后页面行为未变。
- **根因**：版本字符串相同不代表 JVM 中已加载字节变化；POST enable 不会编译或刷新 ClassLoader。
- **规避**：核对 loadedAt/class 字节或重新 unload/load；JAR 构建用 `mvn -pl <模块> -am`，必要时从已打包 JAR 验证。
- **来源**：`Runtime plugin version check`。

## 3. 菜单与 manifest

### 插件已启用但菜单/路由仍是旧结构

- **现象**：plugin.yml 已更新，菜单仍保持旧 parent/sort/可见性，或移除声明后残留重复入口。
- **根因**：frontend-manifest 只该承担 remote 模块元数据，菜单真相是持久化路由；路由动态注册登录后只跑一次，热重载没触发重建。
- **规避**：菜单/权限生命周期遵循 exact pluginCode，不覆盖已持久化管理员配置；移除的声明标 runtimeAvailable=false；RELOAD/FRONTEND_RELOAD 成功后防抖刷新动态路由与菜单，重置 publicAccess memo。
- **来源**：`plugin-frontend-default-parent`、`plugin-menu-projection-reconcile`、`plugin-devmode-reload`。

### 顶栏高亮跳到错误的业务分类

- **现象**：两个插件路由 namespace 前缀重叠时，顶栏选中先注册的错误菜单组。
- **根因**：使用第一个 prefix match，而非最长边界匹配。
- **规避**：精确路由优先，其次最长 segment-boundary prefix。
- **来源**：`plugin-menu-top-nav-match`。

### 结构父菜单权限导致整组子菜单消失

- **现象**：CATEGORY/LAYOUT 自身无权限时，已授权的页面子路由整组不见。
- **根因**：把结构父节点当受保护业务页，递归前先检查父权限。
- **规避**：结构祖先只承载已授权后代，不以自身 permission 二次挡叶子；显式父权限仍需生效；BUTTON 权限上溯所属页面，但不可用插件 BUTTON 不授权页面。
- **来源**：`menu-structural-auth`。

## 4. 主题

### 默认主题页面混入插件主题首页/导航

- **现象**：切换主题后首页卡片、CMS 页面、导航或 preset 混在一起；切回后旧布局被覆盖。
- **根因**：历史数据缺 themeCode，或方案把各主题内容合并到默认主题。
- **规避**：`HomePageLayout/HomePagePreset/CmsPage` 全按 themeCode 隔离；激活只拨 Setting 指针；启动 `CmsThemeBindingInitializer` 回填、按主题去重、迁移历史 plugin-backup 方案；公开查询仅返回激活主题内容。
- **来源**：`plugin-theme-home-preset`、`plugin-theme-page-set`、`theme per-theme template isolation`。

### 主题变量被站点头部忽略

- **现象**：主题 CSS 设置 header 背景但宿主导航仍无样式。
- **根因**：实际消费变量是 `--yb-site-header-background`；宿主内部 fallback 的 `--yb-site-header-bg` 不是主题覆盖入口。
- **规避**：使用主题变量契约正确变量，或用 `chromeComponent` 接管头尾；禁止继续以 RAW homeCss/`:has()` hack 叠加。
- **来源**：`site-chrome-var-contract`。

### 公开 Wiki 与主题导航不一致或深色文字不可读

- **现象**：Wiki 单独显示旧顶栏，或 dark theme 下 md-editor 出现白底浅字。
- **根因**：`/wiki` 未套 SiteChrome；MdPreview 沿用 md-editor 浅色默认背景/文字；移动浮层 z-index 低于主题导航。
- **规避**：Wiki/search/article/embed 全走 SiteChrome（默认主题也一致）；MdPreview `preview-theme=default` 并把 `--md-bk-color/--md-color` 桥接主题变量；抽屉/FAB z-index 1100/1110/1120 高于页头 1000/主题导航 1024。像素纹理只留主题插件资产。
- **来源**：`site-wiki-chrome-embed`。

### 插件公开页在宿主浅色主题下被写死深色

- **现象**：公开页默认主题文字/卡片对比异常，门户里其他插件也被 neco 样式污染。
- **根因**：插件写死 MC 深色调，或浮层 portal 到 body 后拿不到主题祖先变量。
- **规避**：插件消费 `--yb-site-*` 并回落浅色值；专属变量只由所属主题定义，主题私有变量不得跨插件引用；portal 深色样式用 `html:has(.neco-chrome)` 门控而非全局 data-theme 覆盖。
- **来源**：`plugin-public-page-theme-bridge`。

### 主题配置 secret 回显或图片清空不生效

- **现象**：secret 再次以明文返回，或清图后默认图复现。
- **根因**：schema secret 未加密/脱敏，空图片值被当成未设置并回填 default。
- **规避**：secret 经主密钥加密、管理端仅返回配置标志，留空保存保留旧值；image 清空存空串且读取不回落 schema default。
- **来源**：`plugin-theme-config-schema`、`plugin-theme-splash`。

## 5. QQ 与认证身份

### 官方 QQ 绑定写入了无法发送私聊的 ID

- **现象**：绑定显示成功但私聊找不到用户，或 User.qq 混入非数字值。
- **根因**：官方 OpenAPI 提供 openid 而非真实 QQ；把不同 scope 的 openid 都写进 User.qq。
- **规避**：统一写 sysMessagingIdentity：私聊 `user_openid`，群/频道 `member_openid+groupOpenid`；Milky 数字 QQ 才镜像 `User.qq`；官方直发用 `user_openid`；启动迁移只按数字 QQ 正则识别历史值。
- **来源**：`official-qq-messaging-identity`、`messaging-identity-not-qq-number`。

### 官方指令菜单触发频控或覆盖非托管菜单

- **现象**：启动时大量 API 请求被限频，或远端非本插件菜单被误修改。
- **根因**：每次插件启停都全量读取/写入，且远端无菜单删除接口。
- **规避**：启动读取快照、等全部插件加载后按语义 diff 创建/更新；运行时 30s 合并、无变化跳过；只管理有本地对应的菜单；写接口用 `set_official_menu`。
- **来源**：`official-qq-command-menu-snapshot`。

### 官方机器人交互按钮没有回调或 markdown 不兼容

- **现象**：按钮未呈现或富回复发送失败。
- **根因**：混淆 Milky/官方能力；官方被动回复依赖 referrer，QQ markdown 不支持表格；图片和按钮走专属协议。
- **规避**：官方无媒体使用 markdown `msg_type=2`，按钮映射 keyboard，传 `msg_id/event_id` 被动回复凭证；Milky 带按钮标 degraded；菜单 markdown 禁表格。
- **来源**：`official-qq-interactive-reply`。

## 6. DevTools 实测边界

### 面板显示命令失败但时间线没有异常堆栈

- **现象**：插件已 catch 并吞掉异常，宿主沙盒日志里没有失败记录。
- **根因**：宿主只能捕获逃逸异常与插件日志，无法感知插件自己 catch 后不记日志的异常。
- **规避**：插件端记录错误；面板分别显示 action、errorType、stackTrace 与 checker 堆栈，不声称可见不可观测的异常。
- **来源**：`qq-sandbox-error-diagnostics`。

### 插件沙盒操作误发真实群消息

- **现象**：调试消息影响真实 connection，或真实群策略被模拟身份污染。
- **根因**：沙盒使用了真实 connectionId 或把策略读取连接与输出连接混用。
- **规避**：模拟事件必须使用 `devtools-sandbox:{sessionId}` synthetic connection 截获 messaging/raw；真实 policyConnectionId 只读策略与历史；异步恢复按 synthetic connection 重新挂上下文。
- **来源**：`qq-sandbox-synthetic-connection`。

### 插件用户 ID 模拟绕过安全检查

- **现象**：测试改变真实账号绑定或写入资料。
- **根因**：模拟身份服务没有限制在沙盒执行作用域。
- **规避**：仅 `QqSandboxExecutionScope` 中装饰身份查询；`bindQqOnce/create/updateProfile` 拒绝；保留角色模拟三态与时间线覆盖记录。
- **来源**：`qq-sandbox-identity-simulation`。
