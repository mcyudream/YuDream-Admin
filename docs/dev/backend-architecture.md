# 后端架构：DDD 分层、Controller 禁项、平台能力双闸门

> **适用范围**：`yudream-domain` / `yudream-application` / `yudream-infrastructure` / `yudream-interfaces` / `yudream-bootstrap` 五个后端模块。
> **目标**：分层职责唯一、边界转换集中在 assembler、平台能力可动态启停且不拖累未启用项目。

开始后端工作前**必须**完整阅读本文件。涉及具体限界上下文时，先在全部四个模块检视对应包；分层改造以 `system/user` 包为基线（聚合/值对象/仓储/应用/接口的参考布局）。

## 1. 领域层：只放业务含义与不变量

| 包 | 职责 |
|---|---|
| `aggregate` | 聚合根与实体（`User`、`Role`、`Dept`） |
| `valobj` | 身份/值对象（`RoleID`、`DeptID`、`UserDept`） |
| `enumerate` | 领域枚举（状态、类型、来源） |
| `repo` | 仓储接口，只声明契约 |
| `service` | 跨聚合规则领域服务 |

- **禁止**框架/Web 类进领域层。
- 不变量与状态流转属于聚合时**必须**放聚合上；类型化标识与复合值用值对象。
- 值对象**必须**实现 `equals/hashCode` 并按值比较（历史坑见 `docs/exp/backend.md`）。
- 领域/业务异常用正常中文文案。

## 2. 应用层：用例编排，不碰边界对象

| 包 | 职责 |
|---|---|
| `cmd` | 写用例输入 |
| `query` | 读用例查询 |
| `dto` | 应用输出 DTO |
| `assembler` | `domain -> DTO` 应用内转换 |
| `service` | 编排仓储、领域服务、事务、校验、授权敏感用例 |

- 应用 service 可构造聚合并调用聚合方法。
- 应用 service **不得**接收接口 `request`、**不得**返回接口 `res`；非琐碎的 `domain <-> DTO` 转换**必须**放应用 assembler，禁止在 service 里堆积 `toDTO/toDomain`。
- 持久化查询构造不进应用层。

## 3. 基础设施层：技术适配器

| 包 | 职责 |
|---|---|
| `dataobj` | 持久化对象（Mongo DO） |
| `mapper` | `domain <-> dataobj` 转换 |
| `impl` | 仓储实现（实现领域 repo 接口） |
| `service` | 外部技术服务与网关 |

- dataobj **不外泄**到应用/接口层；`domain <-> dataobj` 只在 infra mapper。
- S3、Redis、Sa-Token 网关细节、邮件、RabbitMQ、Neo4j 等外部系统都在这层、藏在领域/应用契约后面。
- 仓储 `save` 若允许调用方继续复用领域实例，**必须**把 MongoTemplate 递增的 `@Version` 回写领域对象（参照 `WikiSourceRepoImpl`），否则并发保存稳定触发 `OptimisticLockingFailureException`。
- 允许显式的适配目录（`bootstrap`、`context`、scanner/config 包）。

## 4. 接口层：只做 HTTP 边界

| 包 | 职责 |
|---|---|
| `controller` | 路由、鉴权注解、校验触发、调用应用 service、`Result` 包装 |
| `assembler` | `request -> cmd`、`DTO -> res`、Excel 行映射 |
| `request` / `res` | 出入参 |
| `row` | Excel 行模型 |
| `vo` | 仅限项目既有 UI 视图对象；HTTP 响应优先 `res` |

Controller 硬规则：
- **禁止** `new XxxCmd/Res/ExcelRow`、响应 `builder()`、私有 `toXxx/parseXxx/templateXxx/rowToXxx`、Excel 模板构造、业务不变量。
- **禁止**在 Controller 里做 request 行到 cmd 的 stream 映射——包进 assembler 方法。
- ✅ 允许：page/size 上限、鉴权/会话辅助调用、把应用 service 返回值交给 assembler、返回 `Result`。

Assembler 归属：接口 assembler 负责 `request -> cmd`、`DTO -> res`、`Excel row -> cmd`、`DTO -> Excel row` 与模板行；应用 assembler 负责领域到应用 DTO；infra mapper 负责领域到 dataobj。

## 5. 平台能力：双闸门 + 懒连接 + 依赖级联

### 5.1 system 与 platform 的归属

- 安全与身份等基线能力（接口加密、双 token、API Key、Passkey、OAuth）属于 `system`。
- MQ、Neo4j、Python Runtime、HTTP 集成、文档生成、CMS、AI/Agent 等可动态加载能力属于 `platform`，与 `system` 分离，**必须**支持动态启停，避免未启用项目被迫配置无用中间件。
- SSE、WebSocket、数据可视化**不属于**平台能力：它们是宿主内置的底层能力（见 5.8），与 MongoDB、Redis 同级，直接内置、无开关、不进能力目录。
- 接口加密**必须**在公钥端点之前暴露未认证未加密的状态端点（前端先判断是否加密），加密过滤器显式放行这两个引导端点；可解密 `text/event-stream` 端点的入站请求，但**不得**包装/加密 SSE 响应体。

### 5.2 双闸门

1. **项目闸门**：配置/`@ConditionalOnProperty` 决定是否允许加载 provider（如 `yudream.platform.capabilities.rabbitmq.enabled=true`）。
2. **应用闸门**：应用层每次用例前 `ensureEnabled(...)` 检查持久化状态。

- 项目闸门不允许的能力**不得**注册端点、**不得**启动恢复、不得以运行时可切换 provider 的形式出现。
- 应用闸门关闭的能力**必须**在应用层拒绝用例，**不得**创建外部连接、声明中间件资源或要求项目配置未用中间件。

### 5.3 provider 懒初始化

- Infra provider 只是工具包装：构造与 `enable(config)` 只能存本地配置/标记状态，**禁止**建立 MQ/Neo4j/Redis 连接、验证远端连通性、声明队列/topic、启动长驻资源。
- 连接只在两道闸门通过且真实业务/连接/健康检查/显式测试动作需要时创建；disable 时关闭并清理资源（关闭 WebSocket/SSE 会话、RabbitMQ 连接工厂、Neo4j Driver 缓存），不能只改 enabled 状态。

### 5.4 依赖声明与级联

- 能力间运行时依赖**必须**声明在 `CapabilityDescriptor.dependencies`；依赖不可用时拒绝启用，禁用依赖**必须**级联禁用依赖方，保证 UI 与运行时状态一致。
- 能力配置默认值：`defaultConfig` 只播种空配置行，新增配置键**必须**在读取处运行时回落默认值（常量放领域 valobj 共享）。

### 5.5 能力菜单过滤

- 除 `platform:capability` 能力管理入口外，平台功能菜单在对应能力关闭/未注册时从 `/api/menu/routes` 隐藏。能力归属解析顺序：①菜单注解 `@MenuNode(capability = "…")`（本节点或父节点声明，经 `MenuCapabilityRegistry` 运行期索引，已有库中的菜单无需重新同步）；②`MenuAppService.PLATFORM_MENU_CAPABILITIES` 历史显式映射；③按 `platform:*` code 第二段推断（能力文档缺失时放行）。
- **新增或调整平台能力入口时必须在 `@MenuNode` 上声明 `capability`**，不再扩充历史映射；菜单 code 与能力码不一致（如 `platform:theme-center → cms`、`platform:agent-skill → agent`）时尤其不能依赖推断。
- 能力未启用的功能**不展示入口**，而不是展示“需要启用”的占位：前端本地路由以 `meta.capability` 声明（守卫按不存在处理为 404），页面内入口（标签、按钮、面板、浮动入口）以 `useAppFeatureStore().capabilityEnabled(code)` 判定后不渲染。

### 5.6 插件市场源（plugin-market-source）

- 本能力**只提供本机源**：双闸门控制 LOCAL 播种、自托管发布/审核与公开 `/market`；关闭时不回落 Nexus/`store-root-url`。
- 远程 V2_API/STATIC_INDEX 订阅与安装**不依赖**本能力：市场源管理端点始终注册；「添加市场源」走系统路由 `/platform/plugin-marketplace/add-source`（不进菜单）；「市场源管理」菜单不映射能力；LOCAL 源在能力关闭时从列表与目录隐藏。
- 默认内置源 `default` 为 LOCAL（本机 PUBLISHED 进程内直读，`downloadUrl=local:{jarPath}`，`Files.copy`+SHA-256，路径限制在 `market-source.directory` 下）；发布物 JAR 存 `market-source.directory`，**必须**独立于插件扫描目录；Docker 挂载 `/app/market-source`；缺失或空 JAR 公开下载 404 且不计数。
- 市场读各启用源快照合并展示；安装/更新记录 `marketSourceCode` 并跨源取最高 SemVer。快照：STATIC 存 `latestDescriptorJson`，LOCAL/V2 填 `structuredVersions`。
- 自托管服务端正式协议为 v2 裸 JSON REST（`/api/public/plugin-market/api/v2`：manifest/categories/tags/plugins/detail/download），静态 `schemaVersion=1` index.json 仅 legacy。分类清单 `PluginMarketCategories`，标签 ≤10 个、≤24 字符小写；`authorId` 精确、`publishedAfter/Before` ISO-8601。
- `{code}@{pluginVersion}` 不可覆盖（DO 字段用 `pluginVersion`，避免与 BaseDO 乐观锁 `version` 撞名）。审核开关 `reviewRequired` 与公开社区开关 `publicEnabled` 均为能力配置键、读取处默认回落 true；关闭 `publicEnabled` 后 `/market` 与公开 v2/legacy 停止服务，后台发布/审核/订阅不受影响。
- 状态机 PENDING→PUBLISHED/REJECTED、PUBLISHED→REVOKED；持有 `platform:plugin-market-source:publish` 上传直接 PUBLISHED 跳过审核；作者只能改自己的发布物（无 accept 且无源管理 edit/delete 时）；编辑不重审，硬删除移除记录与 JAR。
- 发布走界面/API Key 双通道（`upload` 权限，API Key 绑定用户可审计），元数据从 JAR 内 plugin.yml 自动解析，分类/标签为社区元数据。后台「插件发布/发布审核」映射本能力（能力关则隐藏）。
- 细则见 `yd-docs/docs/plugin/market-source.md`。

### 5.7 数据备份（system/backup 基线能力，无双闸门）

- 全量导出/合并导入用 YDBA 归档（ZIP：`manifest.json` + `mongo/*.ndjson` EJSON EXTENDED + `objects/` 与索引 + `plugins/{code}/{scope}/{path}` 与索引）；合并导入是无损并集，同标识冲突开始前一次询问策略（`LOCAL_WINS` 只补缺 / `ARCHIVE_WINS` upsert 前 purge 业务键）。
- 本地归档目录默认 `config/backup`；集合排除两层：硬排除 `system.*`/视图/备份自身三表；日志遥测令牌类默认排除且 `yudream.system.backup.exclude-collections` 可整组覆盖（前缀 `*` 通配），生效清单入 `manifest.excludedCollections`，核心 `sys*` 业务表全进备份。
- 异地备份目标 FTP/FTPS（commons-net）/WebDAV（JDK HttpClient），密码主密钥加密（AAD `system-backup:target:{code}`）；任务队列单虚拟线程串行；计划 cron 为 Spring 6 位表达式，监听 `BackupPlansChangedEvent` 重排。
- 插件经 SPI `system.backup` 扩展点 `PluginBackupProvider` 贡献范围（`scopeCode` 归属插件码），恢复缺失提供方时跳过并告警；插件主动触发走 `FrameworkServices.backups(pluginCode).startScopeBackup`（只能触发本插件 scope），`listScopeJobs(scopeCode, limit)` 回查任务摘要；前端异地目标选择走宿主目录端点 `GET /api/platform/plugins/backup/targets`（SDK 1.8.0 起 `sdk.backup.targets()`）。
- 导入跨主机比对 `manifest.masterKeyFingerprint`。细则见 `docs/system/backup.md`。

### 5.8 内置基础设施（SSE / WebSocket / 数据可视化）

SSE 实时推送、WebSocket 双向通信、数据可视化属于**宿主内置的底层能力**，与 MongoDB、Redis 同级：直接内置在后端，不注册为平台能力、不进能力目录、不在能力管理页展示，也没有启停开关与配置项。

- 端点与组件常开：`/api/platform/sse/connect`、`/api/platform/ws`、`@yudream/dataviz` 始终可用；**不得**为它们加 `@ConditionalOnProperty` 项目闸门、`ensureEnabled` 应用闸门、`CapabilityDescriptor.dependencies` 依赖声明或能力管理页卡片（AI 不再声明 `sse` 依赖）。
- 代码就是普通后端组件：住在普通包（如 `interfaces/platform/realtime`），**不得**实现 `CapabilityProvider`，也不暴露 describe/health/enable/disable/test 这套能力契约。
- 不作能力即不在目录：`CapabilityAppService.list()` 按"是否存在 provider"过滤，删掉 Provider 后自动从目录与菜单过滤中消失；数据库里遗留的旧能力行是惰性的（列表不展示、`restoreEnabledProviders()` 跳过无 provider 的行），无需数据迁移。
- 基础设施自身的边界仍要守：连接必须登录（SSE 走带令牌的 fetch 流式读取、WS 握手校验令牌）、并发连接上限、关闭时清理会话。
- 判断标准：需要按项目配置、需要连接外部中间件、关掉能省资源的（MQ、Neo4j、AI 等）才做平台能力；进程内的通用基础设施（实时推送、图表渲染一类）一律直接内置。

## 6. 通用后端规则

- **软停用可逆**：凡保留持久化记录切到 `DISABLED/DEPRECATED/enabled=false` 的软停用，**必须**提供显式且幂等的启用领域动作、应用用例、受权限保护 API 与前端入口；启用只恢复生命周期状态，不覆盖业务配置。真实删除/吊销/过期/下线工作流不适用。
- **账号级联删除（purge）**：用户物理删除是不可恢复的独立工作流，权限码 `system:user:purge`（词表 `purge`＝永久删除，与历史 `:delete` 的停用口径区分），守卫为「不能删自己」且「操作者角色权重必须严格高于目标」；级联回收账号级数据——消息身份、安全凭据（通行密钥/刷新令牌/OAuth 令牌与授权码/外部账号/API Key）、移动设备、站内通知、聊天会话与消息及配额——删除后**必须**强制该账号全部会话下线。登录日志、接口日志、AI 运行、评论等审计与内容数据保留快照；插件侧自持数据不在回收范围，插件不得强依赖用户存在。
- **记录型列表分页**：操作记录、流水、日志、审计、导入历史等可无限增长的列表，API **必须**暴露 page/size 并前端分页，禁止固定拉 100/200 条。
- **归属从会话推导**：插件用户侧资源归属由后端从当前会话/PluginPrincipal/Sa 上下文推导，前端**不得**传 `uploaderId/ownerId` 等可伪造归属字段；管理员代操作设计独立用例与权限。
- **角色授权透传**：角色保存/分配权限不校验权限码是否存在于注册表——失效或未注册的码原样入库不阻断保存，仅展示层隐藏，插件恢复后授权自动生效；前端勾选保留树外不可见码，提交原样透传。
- **消息身份**：官方 QQ OpenAPI 不提供真实 QQ 号；账号绑定写入 `sysMessagingIdentity`（Milky 数字 QQ 镜像 `User.qq`；官方私聊绑 `user_openid`、群/频道绑 `member_openid+groupOpenid`），**禁止**把 openid 写入 `User.qq`；历史 `User.qq` 启动时按 `^\d{5,12}$` 分类迁移；`sendDirectToBoundUser` 在官方连接只用 `user_openid`；同一官方连接只保留一条 openid，后绑覆盖。
- **渲染消息模板**：Milky 指令菜单等渲染消息布局**必须**用 classpath Thymeleaf 模板；Java 只组装视图数据并调用 TemplateEngine，禁止在分发方法里拼 HTML/CSS。
- **选择器截图**：渲染请求带 CSS 选择器时用 Playwright `Locator.screenshot()`；禁止 boundingBox+page.screenshot clip 或改视口模拟（回流会裁掉 padding/border）。
- **Authlib Injector**：服务端点保持 `/api/plugins/authlib-injector`；`meta.links`、`skinDomains`、ALI 从当前公开请求/纹理 URL 推导，不拿皮肤/yggdrasil 路径替换 authlib 服务地址。
- **版本注入**：关于系统页的框架/SPI 版本经 Maven 资源过滤注入（bootstrap `about.properties`、SPI `spi-build.properties`），运行时走 domain 端口 + infra classpath 读取（不经 Environment，避免 IDE 未过滤时占位符穿透）；最新版本探测走 domain `LatestVersionGateway` 端口 + infra `NexusLatestVersionGateway`（maven-metadata.xml/npm dist-tags，TTL 缓存），出站基址由应用层过 `OutboundUrlGuard`。
- **Neo4j 插件访问**：插件访问图数据库必须经已发布的 `FrameworkServices.graph()` SPI 端口，宿主负责平台连接、能力闸门与 Driver 生命周期；插件**禁止**读 `NEO4J_*` 环境变量、直接依赖 `org.neo4j.driver` 或自建/缓存 Driver。

## 7. Review Checklist

完成后端工作前跑定向扫描：

```bash
rg -n "private .*to[A-Z]|new .*Cmd|new .*ExcelRow|\.builder\(\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces -g "*Controller.java"
rg -n "\\\\u[0-9a-fA-F]{4}|\?\)" yudream-domain/src/main/java yudream-application/src/main/java yudream-infrastructure/src/main/java yudream-interfaces/src/main/java yudream-bootstrap/src/main/java -g "*.java"
```

然后验证编译（Windows 先设 JDK 21）：

```bash
mvn -pl yudream-bootstrap -am -DskipTests compile
```
