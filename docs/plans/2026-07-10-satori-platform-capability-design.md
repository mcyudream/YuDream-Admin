# Satori 平台能力设计

## 1. 背景与目标

YuDream Admin 需要将 Satori 作为核心 `platform` 可选能力接入，提供跨平台聊天协议的完整客户端封装，并在标准协议之上提供消息渲染、平台降级和插件复用能力。

本设计的目标包括：

- 封装 Satori v1 标准 HTTP API、WebSocket/WebHook 事件协议、资源模型、消息元素、元信息、资源代理和内部接口。
- 支持多个 Satori Server 和多个平台登录账号。
- 根据 `Login.features` 和 `Login.adapter` 识别平台能力并执行发送降级。
- 支持文字、Satori 元素、Markdown、HTML、图片、音频、视频和文件消息。
- 提供 HTML/Markdown 转图片的无界面渲染服务，同时支持 Docker 与普通服务器部署。
- 通过稳定 SPI 向插件提供消息发送和渲染能力，避免插件依赖核心实现。
- 提供连接管理、能力矩阵、消息调试、事件监视和渲染预览的管理端界面。

非目标：

- 不直接实现 Discord、飞书、QQ 等平台的原生 SDK。
- 不复制 Satori SDK 的适配器实现；平台差异由协议特性与原生逃生口承接。
- 不允许任意未授权的内部 API 代理、网页访问或脚本执行。

## 2. 总体架构

能力拆分为两个独立模块：

1. `satori`：管理连接、登录账号、标准 API、内部 API、资源上传、事件接收和会话恢复。
2. `message-render`：提供 HTML/Markdown 转图片、网页截图和后续 PDF/图表渲染扩展。

`satori` 不强依赖 `message-render`。渲染能力关闭或不可用时，文字、标准 Satori 元素和媒体消息仍可正常发送；只有显式要求图片渲染的用例才检查 `message-render` 能力。

渲染内核部署为独立的 `yudream-render-server`：

- Node.js + Playwright + Chromium Headless。
- Docker 场景由 Compose 启动独立 Sidecar。
- Linux/Windows Server 可直接运行同一 Node 服务或使用容器。
- 不依赖桌面环境、X11、显示器或浏览器窗口。
- Java 后端仅依赖稳定的内部 HTTP 协议，不管理 Chromium 进程。

## 3. DDD 分层

### 3.1 Domain

新增 `platform/satori` 与 `platform/render` 限界上下文。

核心模型：

- `SatoriConnection`：连接名称、Base URL、事件模式、启用状态、重连策略和加密凭证引用。
- `SatoriLogin`：连接下的平台登录账号，以 `connectionId + platform + userId` 唯一标识。
- `SatoriEventCursor`：连接最后确认的事件序列号。
- `SatoriEventRecord`：标准事件、原生事件数据、来源上下文和处理状态。
- `SatoriMessageContent`：统一消息内容与发送策略。
- `PlatformCapabilityProfile`：标准特性、适配器补充特性和降级规则。
- `MessageRenderRequest` / `MessageRenderResult`：渲染输入、约束与输出。

领域端口：

- `SatoriApiGateway`
- `SatoriInternalGateway`
- `SatoriEventGateway`
- `MessageRenderGateway`

Domain 不依赖 HTTP、WebSocket、Spring、Playwright 或 MongoDB 类型。

### 3.2 Application

应用服务负责用例编排：

- `SatoriConnectionAppService`：连接 CRUD、测试、启停。
- `SatoriApiAppService`：标准协议 API 用例。
- `SatoriEventAppService`：事件标准化、持久化、分发和游标推进。
- `MessageDeliveryAppService`：内容转换、能力判断、上传、拆分、降级与发送。
- `MessageRenderAppService`：渲染能力检查、请求校验和结果返回。

所有 Satori 与渲染用例在执行前通过 `CapabilityAppService.ensureEnabled(...)` 检查应用门控。

### 3.3 Infrastructure

基础设施实现包括：

- Satori HTTP RPC 客户端。
- Satori multipart 文件上传客户端。
- Satori WebSocket 生命周期与会话恢复客户端。
- MongoDB 仓储、DO 与 Mapper。
- 加密凭证存储适配器。
- Render Server HTTP 客户端。
- `satori` 和 `message-render` 能力 Provider。

Provider 的构造和 `enable(config)` 不建立外部连接。连接只在测试、显式连接、健康检查或业务调用时延迟创建；禁用时关闭 WebSocket、调度器和浏览器渲染客户端资源。

### 3.4 Interfaces

HTTP 边界分为：

- `/api/platform/satori/connections/**`
- `/api/platform/satori/logins/**`
- `/api/platform/satori/messages/**`
- `/api/platform/satori/events/**`
- `/api/platform/satori/api/**`
- `/api/platform/satori/internal/**`
- `/api/platform/satori/webhooks/{connectionId}`
- `/api/platform/message-render/**`

Controller 只负责路由、权限、校验、应用服务调用和响应包装。`request -> cmd`、`DTO -> res`、上传参数和事件边界转换全部放在 interface assembler。

## 4. 协议覆盖

### 4.1 标准 API

以强类型方法覆盖：

- Channel：`channel.get/list/create/update/delete/mute`、`user.channel.create`。
- Message：`message.create/get/delete/update/list`。
- User：`user.get`。
- Guild：`guild.get/list/approve`。
- Member：`guild.member.get/list/kick/mute/approve`。
- Role：`guild.member.role.set/unset`、`guild.role.list/create/update/delete`。
- Friend：`friend.list/delete/approve`。
- Reaction：`reaction.create/delete/clear/list`。
- Login：`login.get`。
- Resource：`upload.create`、代理 URL 构造与下载。
- Meta：`meta`、`meta/webhook.create/delete`。

分页统一建模为 `SatoriList<T>` 和 `SatoriBidiList<T>`，保留 `prev/next` 字符串令牌，不假设页码语义。

### 4.2 事件协议

支持 Opcode：

- `EVENT = 0`
- `PING = 1`
- `PONG = 2`
- `IDENTIFY = 3`
- `READY = 4`
- `META = 5`

连接建立后 10 秒内发送 IDENTIFY；收到 READY 后开始事件处理；每 10 秒发送 PING。断线后使用最后成功处理的 `sn` 恢复会话，并采用带抖动的指数退避重连。

WebHook 与 WebSocket 共用事件标准化管道。WebHook 校验 Bearer Token，成功处理返回 2xx，鉴权失败返回 4xx，处理失败返回 5xx。

### 4.3 消息元素

提供结构化 builder 和安全编码器，覆盖：

- 基础元素：`at`、`sharp`、`emoji`、`a`。
- 资源元素：`img`、`audio`、`video`、`file`。
- 修饰元素：`b/strong`、`i/em`、`u/ins`、`s/del`、`spl`、`code`、`sup`、`sub`。
- 排版元素：`br`、`p`、`message`。
- 元信息元素：`quote`、`author`。
- 交互元素：`button`。
- 平台扩展元素与命名空间属性。

编码器必须转义文本和属性，保留未知但合法的命名空间扩展，不允许调用方通过字符串拼接绕过安全处理。

### 4.4 内部接口

`/{version}/internal/{method}` 通过受控 passthrough 暴露：

- 明确的连接和登录账号上下文。
- HTTP 方法、相对路径、请求头、查询参数和请求体。
- 独立高权限。
- 方法、路径、请求体和响应体大小限制。
- 禁止覆盖 Satori 鉴权与登录标识请求头。

`internal` 事件和标准事件中的 `_type/_data` 原样保存。平台原生消息元素使用适配器命名空间。

## 5. 消息发送与平台适配

统一内容类型：

- `TEXT`
- `SATORI`
- `MARKDOWN`
- `HTML`
- `IMAGE`
- `AUDIO`
- `VIDEO`
- `FILE`
- `COMPOSITE`

发送流程：

1. 加载连接、登录账号及 `Login.features`。
2. 构建 `PlatformCapabilityProfile`。
3. TEXT/SATORI 直接编码；Markdown 可转标准元素或图片；HTML 交给 Render Server。
4. 二进制资源调用 `upload.create`，使用返回 URL 构造资源元素。
5. 根据平台能力决定单条发送、图文拆分、纯文本降级或拒绝。
6. 调用 `message.create` 并返回全部生成的 Message。

平台适配优先级：

1. `Login.features`：协议运行时事实，优先级最高。
2. `Login.adapter`：补充已知适配器的消息格式、限制与降级策略。
3. 默认标准配置：未知平台只使用明确可用的 Satori 标准能力。

`referrer` 必须能从事件完整传入发送 API，以支持飞书等平台的被动回复窗口、线程与回调令牌。

已知适配器配置覆盖钉钉、Discord、KOOK、飞书、LINE、Mail、Matrix、QQ、Satori、Slack、Telegram、微信公众号、企业微信、WhatsApp 和 Zulip。配置只表达协议差异和降级策略，不复制平台 SDK。

## 6. Render Server

首期端点：

- `POST /v1/render/html`
- `POST /v1/render/markdown`
- `POST /v1/render/url`
- `GET /health`

请求支持：

- HTML/Markdown、附加 CSS、主题。
- 视口宽度、最大高度、device scale factor。
- PNG/JPEG/WebP、质量、背景透明度。
- 超时、资源白名单和输出尺寸上限。

运行约束：

- Chromium Headless 进程池。
- 固定并发和队列长度。
- 单任务超时与浏览器崩溃拉起。
- 默认禁止 JavaScript、`file:`、环回地址、链路本地地址和私网 SSRF。
- HTML 白名单清洗和 CSS 限制。
- 内置中文、英文、等宽字体。
- 健康检查包含浏览器可用性但不在 Provider 启用时预热外部资源。

Markdown 使用成熟解析器生成 HTML，再进入同一截图管道。代码块、表格、引用、任务列表和长文本必须有稳定样式。

## 7. 事件与持久化

事件处理顺序：

1. 校验信令和连接上下文。
2. 解析并标准化资源提升后的事件。
3. 以 `connectionId + sn` 幂等写入事件记录。
4. 发布内部领域事件。
5. 标记处理结果并推进游标。

只有成功完成持久化和内部发布后才推进游标。重复事件返回已有结果，不重复触发副作用。

事件记录按配置保留天数并建立 TTL 索引；列表接口必须分页。大体积 `_data` 设置大小上限，超过限制时记录摘要和截断标记。

## 8. 安全与错误处理

- Token 加密存储，管理端只返回脱敏值。
- Satori `400/401/403/404/405/501/5xx` 映射为明确异常并保留 traceId。
- 仅连接失败、超时和允许的 5xx 可重试。
- 消息发送默认不自动重试，避免重复发送；调用方可携带业务幂等键。
- Render Server 失败根据请求策略选择纯文本降级或报错。
- `/internal/**`、连接凭证和 WebHook 管理使用独立权限。
- Java Long、Snowflake ID、平台 ID 和前端可见的 `sn` 均以字符串跨 JSON 边界。
- 资源代理只允许 `proxy_urls`、合法 `internal:` URL 和显式白名单。

## 9. 插件 SPI

新增稳定端口：

- `PluginMessagingService`：查询可用账号、发送统一消息、回复事件、调用受限标准 API。
- `PluginRenderService`：HTML/Markdown 转图片。

SPI DTO 使用简单 record/值对象，所有 ID 均为字符串。插件不得访问 Satori 聚合、仓储、应用服务、Mongo DO 或 Spring Bean。

## 10. 管理端

页面与职责：

- 连接管理：连接 CRUD、测试、启停和脱敏凭证。
- 登录账号：状态、平台、适配器和 features 能力矩阵。
- 消息调试台：内容模式、账号与频道、渲染预览、发送结果。
- 事件监视器：分页、事件类型、账号、处理状态和原生数据查看。
- 渲染预览：HTML/Markdown、主题、尺寸和输出格式。

管理端不是 Render Server 的运行依赖。无管理前端时，Java 与渲染服务仍可通过 API 和配置独立运行。

## 11. 配置与部署

项目门控默认关闭：

```yaml
yudream:
  platform:
    capabilities:
      satori:
        enabled: false
      message-render:
        enabled: false
```

Compose 增加可选 Render Server 服务、健康检查、网络隔离、资源限制和字体层。普通服务器提供 Node 启动脚本、环境变量模板和 systemd 示例。

连接配置与应用启停状态持久化在业务存储中；项目门控关闭时不注册接口、Provider、WebSocket 任务或管理端菜单。

## 12. 测试策略

- 单元测试：协议模型、消息编码、Markdown 转换、平台 profile 和降级规则。
- HTTP 契约测试：使用 MockWebServer 覆盖全部标准 API、分页、上传、状态码和 internal passthrough。
- WebSocket 测试：IDENTIFY、READY、PING/PONG、META、事件顺序、幂等、游标恢复和重连。
- WebHook 测试：反向鉴权、Opcode、状态码和重复事件。
- Render Server 测试：中文字体、代码块、表格、长文本、超时、SSRF 和像素非空检查。
- 应用集成测试：能力门控、连接生命周期、消息上传与发送降级。
- Docker E2E：Java、Satori Mock 和 Render Server 完整链路。
- 前端测试：连接管理、消息调试、事件分页、渲染预览和响应式布局。

## 13. 实施顺序

1. 固化 Satori 文档接口清单和契约测试夹具。
2. 建立领域模型、协议 DTO 与消息元素编码器。
3. 实现完整 HTTP API 与上传/代理支持。
4. 实现 WebSocket/WebHook 事件管道和游标恢复。
5. 实现 Render Server 与 Java 渲染网关。
6. 实现消息投递编排、Markdown/HTML 与平台降级。
7. 实现管理端与权限菜单。
8. 扩展插件 SPI 并实现宿主适配器。
9. 完成 Docker/Server 部署、端到端验证和文档。
