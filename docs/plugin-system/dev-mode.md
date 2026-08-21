# 插件开发模式与开发者工具

本文说明 YuDream Admin 宿主内置的插件开发者工具套件：**插件开发模式（源码热重载）**、**开发者调试浮窗**、**Agent 执行链路追踪** 与 **前端审查工具**。全部能力由宿主侧实现，插件无需任何适配。

> 插件仓侧的目录约定、`dev-export` profile 与首次准备步骤见插件仓 `docs/plugin-dev-mode.md`，本文聚焦宿主机制与面板使用。

## 1. 能力总览

| 能力 | 入口 | 配置门 |
| --- | --- | --- |
| 开发模式（目录加载 + 监听热重载） | 宿主配置或面板登记 | `yudream.platform.plugin.dev-mode.enabled`（默认不配置→自动检测：源码运行开、JAR 运行关） |
| 开发者工具 REST/SSE API | `/api/platform/plugin-devtools/**` | 权限码 `platform:plugin-devtools:view` / `manage` |
| Agent 执行链路追踪 | 调试浮窗「追踪」页 | `yudream.platform.agent.trace.enabled`（默认 `true`） |
| 前端悬浮调试浮窗（非模态） | 管理后台常驻悬浮按钮（可拖拽、贴边收起） | 后端 status 可用 + 权限，或前端 DEV 模式 |
| 前端审查（Fa 组件优先/品牌色令牌） | `pnpm audit:ui` + eslint | 无（warn 级，不阻断构建） |

## 2. 插件开发模式

开发模式下宿主不打包 JAR，直接从插件模块的 `target/classes` 加载插件，并监听源码、编译产物与前端 dist 的变化自动热重载。**仅限本地开发，生产环境禁止开启**；开启时启动日志会输出显著警告。

### 开启方式：自动检测优先，配置兜底

`enabled` 为三态：不配置（缺省）时按宿主运行方式**自动检测**——`DevModeEnvironment` 读取网关类的代码源位置，类来自目录（IDE / `spring-boot:run`，即源码运行）则开启，来自 JAR 则关闭；显式配置 `true/false` 时以配置为准。状态端点返回 `hostRunMode`（SOURCE/JAR）与 `devModeAuto`（是否自动检测生效），浮窗「概览」页会展示「已启用（自动检测）」或「已启用（配置开启）」。

监听器（`PluginDevModeWatcher`）因此**不能用 `@ConditionalOnProperty` 硬门控**（它感知不到自动判定值），改为启动时按生效值决定是否起轮询线程，告警日志注明闸门来源。

### 开发项目来源：配置文件 + 面板登记

开发项目有两个来源，合并后统一参与目录加载与热重载：

- **CONFIG**：yml `dev-mode.projects` 列表，面板只读；
- **FILE**：调试浮窗「设置」页登记的目录，持久化在本地清单文件（默认 `plugins/dev-projects.json`，相对 `user.dir`，与插件 JAR 目录同约定，已被 `.gitignore` 的 `/plugins/` 覆盖；可用 `dev-mode.store-file` 覆盖路径）。此文件是有意选择的**非数据库存储**——coding agent 与用户都能直接读取它来定位插件源码目录。

合并规则：同 code 时 CONFIG 优先并输出告警；面板只能增删 FILE 源，对 CONFIG 源项目的删除会被拒绝并提示去 yml 移除。清单文件带 mtime 缓存自动重载（watcher 每秒轮询天然驱动），面板登记后若插件已启用会立即触发一次热切重载。登记时可在宿主机目录选择弹窗中从文件系统根目录逐层浏览；目录条目会标记 Maven 模块与插件模块，选中后自动回填绝对路径，并在 `code` 尚未填写时回填从 `plugin.yml` 推断出的编码。宿主依次读 `<path>/target/classes/plugin.yml`、`<path>/src/main/resources/plugin.yml` 自动推断；都读不到会报错提示先执行一次 `mvn compile`。

### 配置

```yaml
yudream:
  platform:
    plugin:
      dev-mode:
        # enabled: true        # 可选；不配置时按源码/JAR 运行自动检测
        # store-file: ...      # 可选，面板登记清单路径，默认 plugins/dev-projects.json
        poll-interval-ms: 1000   # 文件轮询间隔
        debounce-ms: 800         # 变化防抖窗口
        projects:                # CONFIG 源，面板只读
          - code: demo            # 必须与 plugin.yml 的 name 一致
            path: /path/to/yudream-admin-plugins/yudream-plugins/yudream-plugin-demo
            auto-compile: true    # 监听到 .java 变化自动执行 compile-command
            compile-command: mvn -q compile -DskipTests -P dev-export
            # frontend-dist: ...  # 可选，默认推导 {path}/../../yudream-frontend/packages/plugin-{code}/dist
```

### 工作原理

- **目录加载**：`plugin.yml` 读 `target/classes/plugin.yml`；ClassLoader 由 `target/classes/` 与 `target/plugin-dev/lib/*.jar`（runtime 依赖，`dev-export` profile 导出）组成。同 code 的开发模式项目优先于 `plugins/` 目录中的 JAR，插件列表/详情带 `devMode` 标记。
- **前端资源**：开发模式插件的前端资产直接从 `frontend-dist` 目录取文件并做内容协商，不再走 JAR 内 classpath。
- **监听管线**（`PluginDevModeWatcher`，启动时按生效开关决定是否起线程）：
  1. `src/main/java` 变化且 `auto-compile` → 防抖后在模块目录执行 `compile-command`；编译失败作为事件推送，**不会**用陈旧产物重载，也不会影响宿主进程；
  2. `target/classes` 变化 → 防抖 → 走 禁用 → 卸载 → 目录加载 → 恢复启用 管线；
  3. 前端 `dist` 变化 → 发布前端重载事件，经 SSE 桥到调试浮窗，触发当前插件运行时页面重挂载远程模块（重挂载会重置页面状态，不是状态保持的 HMR）。
- **路由/菜单自动重建**：浮窗 SSE 收到 RELOAD 或 FRONTEND_RELOAD 成功事件后，除重挂载当前插件页面外，还会防抖调用 `refreshDynamicRoutes` 重新拉取后端菜单与前端 manifest 重建动态路由，插件新增/变更的菜单项无需手动刷新页面即可出现；同时清空公开路由（publicAccess）memo，下次未登录导航按新 manifest 注册。

### 限制

- 热重载只重建本插件 ClassLoader；硬/软依赖提供者必须已启用，依赖方遇到 ABI 变化需手动重载。
- 开发模式插件不要走市场安装/更新/回滚流程；删除插件记录不会删除源码目录。
- Windows 下 `compile-command` 需要 `mvn`（或 `mvn.cmd`）在 PATH，否则填绝对路径。

## 3. 开发者调试浮窗

悬浮按钮**常驻管理后台布局层**（与路由无关，公开页无布局不显示），带未读事件计数徽标：可拖拽换位，松手吸附最近屏幕边缘；松手点距边缘 24px 以内会收成**半隐边缘条**（悬停提透明度并加宽，点击边缘条一步展开浮窗）。位置以 `{side, topRatio, docked}` 比例形式持久化在 localStorage（`pluginDevtoolsFab`），窗口缩放自动适配；「设置」页可一键重置位置。

面板本体是 **Teleport 到 `body` 的非模态置顶浮窗**（对齐 Vue DevTools 的独立窗口心智），不是抽屉：无遮罩、不锁页面滚动，浮窗打开时系统照常可用；`z-index` 2100，高于侧栏（1010）、顶栏（1020）与宿主模态（2000），浮窗内登记项目模态为 2200，宿主目录选择模态为 2300，任何页面、任何情况下浮窗及其嵌套交互都在最上层。拖标题栏移动、拖右下角手柄缩放，几何 `{left, top, width, height}` 持久化在 localStorage（`pluginDevtoolsPanel`），视口缩放自动 clamp 回可见区；「设置」页可重置位置与尺寸。全局快捷键 `Ctrl/Cmd+Shift+D` 开关浮窗（`Esc` 关闭，输入框与已开模态内不劫持）。

浮窗信息架构对齐 Vue DevTools：左侧图标导航栏，按开发动线分七页，激活页持久化（`pluginDevtoolsPage`）：

- **概览**：状态卡（开发模式含自动检测标记与宿主运行方式、Agent 追踪、插件计数、开发项目清单文件路径）+ 最近动态 feed（插件生命周期 LOAD/ENABLE/DISABLE/UNLOAD/RELOAD/COMPILE/FRONTEND_RELOAD 事件流，取最新 20 条，可清空）。
- **插件**：主从结构——先插件清单（名称、状态、开发模式徽标与来源），点入某插件后分组展示其运行时贡献，按开发关注度排序：HTTP 端点、QQ 指令、前端模块与路由 → 权限、菜单 → AI 工具、声明式 Agent → 平台能力、消息交互、首页卡片、服务导出。端点测试器与指令模拟器在插件详情内，开发模式插件可一键「重载」。未启用（LOADED/ERROR）的插件详情提供「启用」按钮，且资产区展示未启用横幅——重载不会自动启用从未启用过的插件，贡献全 0 属预期。清单工具栏可切换「依赖图」视图：按插件展示依赖（depend）/可选依赖（softdepend）/被依赖/被可选依赖四向关系，指向未安装插件的依赖标红；每张卡片提供「禁用预览」，弹窗列出禁用该插件的级联影响——启用中的传递硬依赖方（按建议禁用顺序排列，运行时拒绝禁用存在启用中硬依赖方的插件）、启用中的直接软依赖方（禁用后其可选集成降级）、已加载的直接依赖方（存在时卸载/重载将被拒绝）。
- **QQ 沙盒**：构造真实 Milky `message_receive` 事件，按生产顺序执行消息交互、`/`/`!` 指令解析、QQ 绑定与角色权限，并支持 @机器人、额外提及、回复消息和随机触发三态（真实概率/强制命中/强制未命中）。支持身份模拟（模拟未绑定 QQ、模拟角色）；插件处理器逃逸异常与插件 WARN/ERROR 日志以结构化负载进时间线。真实策略连接只用于读取群策略与历史种子；所有回复写入 synthetic connection 时间线，不发送到 QQ。
- **追踪**：实时执行区（SSE 增量累积，运行中的 trace 只能在这里看步骤）+ 历史记录（分页、按来源/状态过滤）。详情页逐步展示输入摘要、思考过程、工具调用入出参、输出与耗时，失败步骤红标，可导出 JSON 用于缺陷上报。
- **日志**：按插件过滤的运行日志流——REST 拉取最近清单（默认 100、上限 500 条，级别/关键字过滤）+ SSE 实时追加，按 sequence 去重；可暂停（暂停期日志缓存于缓冲区）、清空与展开异常堆栈。过滤依据插件包名前缀（`PluginLoggerPrefix`：从 mainClass 截取 `online.yudream.base.plugin.` 根包后的第一段，第三方未遵循包约定的插件兜底用 根包+编码），数据源为宿主 SystemLogBuffer 环形缓冲，与沙盒日志桥同一包约定。
- **审查**：读取 vite dev 中间件 `/__yudream-devtools/audit.json` 展示的审查报告（见第 7 节）。
- **设置**：开发项目管理（登记/移除/立即重载，含来源标记与路径/编译/描述符状态位）+ 面板偏好（悬浮按钮位置、浮窗位置与尺寸一键重置）。

浮窗头部只保留标题与双 SSE（生命周期流/追踪流）连接状态点，状态明细移入「概览」页。

可见性：拥有 `platform:plugin-devtools:view` 权限且后端 status 端点可用；纯前端 DEV 模式（`import.meta.env.DEV`）下按钮始终可见，后端不可用时浮窗内降级提示。

## 4. 开发者工具 API

统一挂载 `/api/platform/plugin-devtools/**`，仅管理员：

| 方法与路径 | 权限码 | 说明 |
| --- | --- | --- |
| `GET /status` | view | 开发模式与追踪开关状态（含 `hostRunMode`、`devModeAuto`、`devProjectStoreFile`） |
| `GET /plugins` | view | 插件清单（含 devMode 标记与 depend/softdepend 依赖列表，前端据此构依赖图） |
| `GET /plugins/{code}/assets` | view | 单插件运行时资产快照 |
| `GET /plugins/{code}/disable-preview` | view | 禁用级联预览：启用中的传递硬依赖方（按建议禁用顺序）、启用中的直接软依赖方、已加载的直接依赖方（卸载阻塞） |
| `POST /plugins/{code}/reload` | manage | 手动重载（开发模式插件） |
| `POST /plugins/{code}/command-test` | manage | QQ 指令模拟触发（见 5.1） |
| `GET /dev-projects` | view | 开发项目合并清单（CONFIG+FILE，含来源与路径/编译/描述符状态位，不受开关过滤） |
| `GET /dev-projects/browse?path=...` | manage | 逐层浏览宿主机目录；path 为空返回文件系统根，返回 Maven/插件模块标记与可推断编码 |
| `POST /dev-projects` | manage | 面板登记开发目录（code 可留空自动推断；已启用插件立即热切） |
| `DELETE /dev-projects/{code}` | manage | 移除 FILE 源项目（CONFIG 源需在 yml 中移除） |
| `GET /agent-traces` | view | 追踪分页查询（source/plugin/状态过滤） |
| `GET /agent-traces/{traceId}` | view | 单条追踪全步骤 |
| `GET /qq-sandbox/presets` | view | QQ 沙盒消息形态预设（发送人、群、角色选项） |
| `POST /qq-sandbox/sessions` | manage | 创建会话；指定已启用插件、真实策略连接、群/用户/机器人 ID、随机三态与身份模拟（forceUnbound/simulateRoles） |
| `POST /qq-sandbox/sessions/{sessionId}/messages` | manage | 注入合成 Milky 事件（文本、@、reply、发送者均使用 string ID）；`type` 支持 `message`（默认）/ `group_request`（入群请求，content 为验证留言可空）/ `button`（按钮回调，必填 `buttonId`） |
| `GET /qq-sandbox/sessions/{sessionId}/events/stream` | view | SSE：标准化、触发/阻断、Agent、工具和捕获回复时间线 |
| `DELETE /qq-sandbox/sessions/{sessionId}` | manage | 结束会话并清理内存覆盖层与异步执行 |
| `GET /qq-sandbox/cases` | view | 列出已保存的 QQ 沙盒用例（按更新时间倒序） |
| `POST /qq-sandbox/cases` | manage | 保存或覆盖用例（body 携带 id 则覆盖同名项；setup 为会话初始参数，steps 为有序合成消息） |
| `DELETE /qq-sandbox/cases/{caseId}` | manage | 删除用例 |
| `POST /qq-sandbox/cases/{caseId}/replay` | manage | 按用例初始参数新建会话并逐条同步回放消息，返回新会话 |
| `GET /events/stream` | view | SSE：生命周期/编译/前端重载事件；RELOAD 成功时 payload 携带 `assetsDiff`（重载前后运行时资产差异，按类别列出 added/removed 标识） |
| `GET /agent-traces/stream` | view | SSE：步骤增量 + trace 完成事件 |
| `GET /plugins/{code}/logs` | view | 插件运行日志查询（level/keyword/limit 过滤，上限 500） |
| `GET /plugins/{code}/logs/stream` | view | SSE：按插件 logger 前缀过滤的实时日志 |

> 宿主 sa-token 只从请求头读 token，SSE 不能用 `EventSource`（无法携带 `Authorization`），浮窗通过 fetch + ReadableStream 手工解析事件流，新页面复用 `plugin-devtools` store 即可。

### 5.1 指令模拟器

`POST /plugins/{code}/command-test` 接收 `{ command, arguments, content }`，构造指令上下文直接走运行时指令发布管线，返回匹配到的指令、handler 输出/异常与耗时——无需真实 QQ 环境即可调试插件注册的指令。「插件」页详情内指令行的「模拟触发」按钮即调此接口。

### 5.2 端点测试器

「插件」页详情内 HTTP 端点行的「试用」按钮在面板内发起真实请求：自动提取路径参数、支持查询字符串与请求体，展示真实 HTTP 状态码、耗时与响应原文。该请求走原生 fetch 而非 axios 封装，因此非 2xx 不会被拦截器吞掉；若目标端点启用了接口加密，响应体可能是密文，面板按原文展示。

### 5.3 QQ 群聊沙盒

沙盒使用 `devtools-sandbox:{sessionId}` 作为 synthetic connection。生产 Milky 入站和沙盒共用标准化、interaction filter、指令 grammar、QQ 绑定与权限分发代码；所选真实 `policyConnectionId` 必须存在且启用，只用于 ai-chatbot 读取对应群策略、短期历史种子和语义记忆 namespace，不能作为发送连接。

安全仿真边界：

- `messaging()` / `messagingRaw()` 输出只写会话时间线，synthetic connection 永不查询 `MilkyConnectionRepo` 或调用 `MilkyApiGateway`；插件异步回调也可按 connection 重新附着会话。
- 插件文档写删落会话内存覆盖层；生产文档只读。语义记忆允许真实检索，index/delete 只记录不落库。
- Agent 所有工具统一经过风险闸门；未声明风险默认 `WRITE`，沙盒只允许显式 `READ` 且仍满足原权限/触发/allowedToolNames 的工具。Python 与未知工具默认拒绝。
- ai-chatbot 沙盒历史、活动、画像、限流计数与语义索引不写生产数据；`agent_pending` 到 terminal 诊断使 HTTP 等待回复捕获完成。120 秒超时会取消已跟踪模型/工具 future 并拒绝迟到写入。
- 会话进程内保存，空闲 30 分钟过期、最多 200 个；删除、超时、过期或 LRU 淘汰都会清理 listener、覆盖层与 pending future。

身份模拟（`SandboxAwarePluginUserService`，`@Primary` 装饰 SPI 实现，仅沙盒作用域激活时改写）：

- 会话创建可携带 `forceUnbound`（插件侧 `findByQq` 判定为未绑定）与 `simulateRoles` 三态：`null` 走发送人真实角色（默认）、空列表为无角色、非空为角色 code 列表（未知 code 记入 `unknownRoles`）；角色选项经 `GET /qq-sandbox/presets` 下发。面板默认发送人取首个已绑定系统用户，避免默认匿名导致插件全被「未绑定」阻断。
- 沙盒会话内 `bindQqOnce`/`create`/`updateProfile` 抛 `BizException` 禁止写系统用户数据；每次身份改写追加 `sandbox/identity.override` 时间线事件。

错误诊断可观测性：

- 指令处理器（`command.error`）、消息交互处理器（`handler.error`）、分发链路（`dispatch.error`）逃逸出的异常都会以 `{errorType, message, stackTrace}` 结构化负载追加到会话时间线（`QqSandboxDiagnostics`），检查器单独渲染堆栈。
- `QqSandboxLogAppender`（logback appender）在沙盒作用域激活时把插件包 `online.yudream.base.plugin.*` 的 WARN/ERROR 日志（含堆栈）桥接为 `log/log.warn|log.error` 时间线事件，并从 logger 名推导插件编码归属；插件 catch 后只记日志的失败也能定位。生产链路作用域为空，直接透传。

沙盒隔离宿主官方 SPI/Agent/消息/文档/语义记忆边界；第三方插件自行创建线程且不调用 synthetic messaging/diagnostic，或自行创建网络/文件客户端的行为无法在不修改 SPI/JVM 沙箱的前提下透明拦截，调试时仍应只加载可信插件。

事件类型补全（message 之外的合成事件）：

- 注入消息的 `type` 字段决定合成的事件形态：`message`（默认，走 `message_receive` 全链路）、`group_request`（入群请求，复用生产 `dispatchGroupRequest` 分支，data 携带 `group_id/user_id/request_id/comment`，`clientMessageId` 可作 `request_id`）、`button`（按钮回调，合成 `button_click` 事件，`buttonId` 精确路由到插件 `onButton` 交互；`button_click` 按 native 事件对待，与 `internal`/`group_request` 同列）。
- 入站时间线 action 按类型区分：`message.synthetic` / `group_request.synthetic` / `button.synthetic`；必填校验随类型变化（消息要内容、按钮要 `buttonId`、入群请求留言可空）。
- 面板状态栏「导出时间线」把当前会话快照与全部内存事件下载为 JSON（纯前端导出，不占端点）。

用例保存与回放：

- 面板工具栏「存为用例」把当前时间线中的 `message.synthetic` / `group_request.synthetic` / `button.synthetic` 合成事件按序收割为步骤（含类型与按钮 ID），连同当前会话初始参数（插件范围、策略连接、群/用户/机器人、随机三态、身份模拟）保存为用例；「用例」列表支持回放与删除。
- 用例持久化在本地 JSON 文件 `plugins/qq-sandbox-cases.json`（与开发模式项目清单同目录），跨宿主重启保留；旧版没有 `type` 字段的用例按 `message` 兼容回放。
- 回放（`POST /qq-sandbox/cases/{caseId}/replay`）按保存的初始参数新建会话，追加 `session/case.replay` 时间线事件（caseId/caseName/steps），再逐条同步发送保存的消息；新会话接管事件流，语义与手工逐条发送完全一致。

## 6. Agent 执行链路追踪

`AgentWorkflowRuntimeService.execute(...)` 是全入口唯一汇聚点，追踪器在应用层装饰其回调，chat/wiki/cms/debug/plugin 全部入口自动生效，上层零改动。

- **来源标记**：`AgentTraceSource` = `CHAT` / `WIKI` / `CMS` / `DEBUG` / `PLUGIN` / `SYSTEM`；插件 Agent（负数 ID）自动反查 ownerPluginCode 标为 `PLUGIN`。
- **记录内容**：trace 级（输入、最终输出、错误、token 用量、起止耗时）+ step 级（节点、状态、输入/输出摘要、思考过程、工具名与入出参、耗时）。
- **持久化**：Mongo 存储，TTL 索引默认 7 天，并按来源限量清理。
- **实时推送**：每步经应用事件发布到 SSE 桥，浮窗实时渲染。

配置（`yudream.platform.agent.trace`）：

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 是否记录追踪 |
| `max-text-length` | `4000` | 单段输入/输出/工具详情截断长度 |
| `max-reasoning-length` | `8000` | 单步思考过程截断长度 |
| `retention-days` | `7` | Mongo TTL 保留天数 |
| `max-per-source` | `500` | 每个来源最多保留条数 |

## 7. 前端审查工具

宿主前端内置两条本地 eslint 规则（`yudream-frontend/eslint-rules/`，根 `eslint.config.js` 以 `yudream/*` 命名空间注册，`apps/**` 启用 **warn** 级，不阻断构建）：

- `yudream/prefer-fa-component`：模板 `<a-*>` 标签与 `import { X } from '@arco-design/web-vue'` 双检测，命中 47 项 Arco→Fa 映射（`eslint-rules/fa-component-map.mjs`）时告警并给出替代组件；AI 对话场景文件追加 Yd* 元件提示。Form/Grid/Tree 等无 Fa 等价物的不告警。确需 Arco 时 `eslint-disable` 本行并注明原因。
- `yudream/no-brand-color-token`：业务样式出现 Arco 品牌色阶梯令牌 `--primary-N`（如 `rgb(var(--primary-6))`）时告警，引导改用 `--color-bg-*` / `--color-text-*` 等中性语义变量。组件主题系统自身的 `--primary` / `--primary-foreground`（oklch 形式）不受影响。

配套命令（`yudream-frontend` 根目录）：

```bash
pnpm audit:ui           # 全仓扫描 apps/*/src，生成 audit-report.json（含 Arco 使用分布）
pnpm test:eslint-rules  # 规则用例测试（node:test + RuleTester）
```

vite dev 中间件把 `audit-report.json` 暴露在 `/__yudream-devtools/audit.json`（每次请求实时读文件，重跑 `pnpm audit:ui` 后面板刷新即最新），调试浮窗「前端审查」页直接展示；报告不存在时中间件返回 404 与引导文案。报告文件已加入 `.gitignore`，不入库。

## 8. 故障排查

- **悬浮按钮不出现**：确认当前账号有 `platform:plugin-devtools:view` 权限；后端不可用时仅前端 DEV 模式可见降级面板。按钮可能被拖到了屏幕边缘收成半隐边缘条——沿左右边缘找一下（悬停会提亮加宽），或按 `Ctrl/Cmd+Shift+D` 直接开关浮窗，或在 localStorage 删除 `pluginDevtoolsFab` 重置位置。
- **开发模式未按预期开启/关闭**：看「概览」页状态卡的「自动检测/配置开启」标记——未显式配置 `enabled` 时按源码/JAR 运行自动判定，显式配置优先于自动检测。
- **面板登记的目录不生效**：看「设置」页项目行的三个状态点（源码目录存在/类产物已编译/plugin.yml 可读）；登记清单在 `devProjectStoreFile` 指向的 JSON 文件，可直接检查其内容。
- **改代码不重载**：看「概览」页最近动态的 COMPILE 事件——编译失败会推送错误且不重载；确认 `compile-command` 在宿主进程环境可执行（Windows 注意 PATH）。
- **前端改动不生效**：确认插件前端在 `vite build --watch`，且最近动态出现 FRONTEND_RELOAD；重挂载会重置页面状态属预期行为。
- **追踪页查不到运行中的执行**：完成才落库，运行中的 trace 在「实时执行」区（SSE 累积）查看。
- **审查面板 404**：先在 `yudream-frontend` 根目录执行 `pnpm audit:ui` 生成报告；该中间件仅在 vite dev 模式存在。
