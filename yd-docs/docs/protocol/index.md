# 通信协议总览

平台的机器人接入与富文本消息渲染建立在两套协议之上：对外对接 QQ 机器人的 **Milky 协议**与**腾讯官方 QQ 机器人 OpenAPI v2**（共用能力码 `milky`、同一出站端口），对内服务于消息卡片与富文本降级的**渲染服务 HTTP 协议**。

> 历史说明：早期版本曾通过 Satori v1 协议对接机器人平台，现已弃用并从代码中移除。机器人接入统一走能力码 `milky`（展示名「QQ 消息平台」），一条连接可选 Milky 或官方 OpenAPI。

```mermaid
flowchart LR
    subgraph Host["宿主 (Spring Boot)"]
        SPI["插件 SPI 端口<br/>PluginMessagingService / RawService / interactions"]
        R["RoutingMilkyApiGateway"]
        M["ReactorMilkyApiGateway"]
        O["OfficialQqBotApiAdapter"]
    end
    QQ1["QQ 客户端<br/>(Milky)"] -- "HTTP /api/{api}" --> M
    M -- "WebSocket /event" --> QQ1
    QQ2["腾讯官方机器人<br/>(OpenAPI v2)"] -- "REST /v2/**" --> O
    O -- "Gateway / Webhook" --> QQ2
    SPI --> R
    R --> M
    R --> O
    RS["render-server<br/>(Headless Chromium)"]
    R -. "富文本降级：Markdown/HTML 转图片" .-> RS
```

## QQ 消息平台（Milky + 官方 OpenAPI）

宿主以「连接」为单位管理多个机器人实例，出站统一走 `RoutingMilkyApiGateway.invoke(context, api, body)`：

- **Milky**：HTTP API `POST {baseUrl}/api/{api}` + WebSocket `/event?access_token=`；身份是 QQ 号。
- **官方 OpenAPI v2**：REST `https://api.bot.qq.com`（沙箱 `https://sandbox.api.bot.qq.com`）+ Gateway 或 Webhook `/api/public/qqbot/{connectionId}/webhook`；身份是 **openid**，不能当成 QQ 号。
- **凭据安全**：连接 token / AppSecret 经 `AesGcmMilkyCredentialCipher` 加密落库；新写入使用 Base64 解码后恰为 32 字节的 `YUDREAM_CREDENTIAL_KEY` 及连接作用域 AAD，旧 `YUDREAM_MILKY_CREDENTIAL_KEY`（16/24/32 字节）仅用于解密历史密文。
- **插件入口**：插件不直接触碰协议，统一经 [MessagingSpi](/plugin/spi/v1/messaging) 的平台无关端口收发消息。`PluginMessagingConnection.protocol` 为 `milky` 或 `official`；需要原生方法时走 `invoke(connectionId, method, payload)`。
- **前端选择器**：插件管理页用 SDK `sdk.messaging.connections()/groups()`，对应宿主目录 `GET /api/platform/plugins/messaging/**`。不要再包一层插件 HTTP，也不要打管理端 `/api/platform/milky/**`。官方连接没有历史群拉取，`groups()` 只返回本进程事件缓存。

完整字段、端点、官方菜单/面板、被动回复与事件分发链路见 [QQ 协议详解](/protocol/milky)。

## 渲染服务协议（内部）

render-server 是独立的 Headless Chromium 服务，把 HTML、Markdown 或外部 URL 渲染为图片/PDF：

| 端点 | 说明 |
|---|---|
| `POST /v1/render/html` | 渲染 HTML 片段 |
| `POST /v1/render/markdown` | 渲染 Markdown |
| `POST /v1/render/url` | 截取外部 URL |
| `GET /health` | 健康检查 |

- 错误语义：`400` 参数非法 / `429` 并发或队列满 / `500` 渲染失败 / `504` 超时；
- 并发由 `BrowserPool` 控制，上限来自 `RENDER_MAX_CONCURRENT` / `RENDER_MAX_QUEUE`；
- 安全模型：可控的子资源拦截、SSRF 私网段拒绝、输入体积限额、内置 CSP。

使用方式见 [消息渲染能力](/features/render)；插件经 `framework().render()` 访问。

## 能力闸门

两个能力都遵守项目闸门 + 应用闸门双闸门约定：

| 能力码 | 项目闸门开关 | 说明 |
|---|---|---|
| `milky` | `PLATFORM_MILKY_ENABLED` | QQ 消息平台：Milky 与官方 OpenAPI（默认开启） |
| `message-render` | `PLATFORM_MESSAGE_RENDER_ENABLED` | 富文本渲染服务 |

::: info 源码位置
- Milky / 官方适配：`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/milky/`、`.../milky/official/`
- 插件桥接：`yudream-infrastructure/.../infra/platform/plugin/service/MilkyPluginMessagingService.java`
- 宿主目录：`yudream-interfaces/.../platform/plugin/controller/PluginMessagingCatalogController.java`
- render-server：`yudream-render-server/src/`
:::
