# Milky 机器人（QQ 接入）

Milky 能力（能力码 `milky`，类型 `MESSAGING`）通过 **Milky 协议**把 QQ 机器人接入平台：连接管理、沙盒聊天、消息事件分发。插件通过 [消息端口](/plugin/spi/v1/messaging) 消费该能力；协议层细节见 [Milky 协议详解](/protocol/milky)。

> 源码：`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/milky/`、`yudream-application/.../platform/milky/`、`yudream-interfaces/.../platform/milky/controller/`

## 能力描述符与闸门

```java
// MilkyCapabilityProvider
new CapabilityDescriptor("milky", "Milky", CapabilityType.MESSAGING,
    "Milky QQ 协议与 WebQQ 管理", "i-ri:chat-3-line", 75, Map.of(), List.of())
```

- **项目闸门**：`PLATFORM_MILKY_ENABLED`（默认 `true`），决定 provider 是否装配、端点是否注册；
- **应用闸门**：每次用例前 `ensureEnabled("milky", ...)` 校验持久化开关；
- 连接凭据（平台地址、token）经 `AesGcmMilkyCredentialCipher` AES-GCM 加密落库，统一使用 `YUDREAM_CREDENTIAL_KEY`（Base64 解码后恰为 32 字节）。旧 `YUDREAM_MILKY_CREDENTIAL_KEY` 仅用于读取历史密文；后续保存会使用统一主密钥重加密。

## 连接管理

`MilkyConnectionController`（`/api/platform/milky/connections`）：

| 端点 | 说明 |
|---|---|
| `GET /` | 分页查询（keyword 过滤），ID 一律 string |
| `POST /` | 创建连接 |
| `PUT /{id}` | 更新连接 |
| `POST /{id}/enable` | 启用：启动事件 WebSocket 与发送通道 |
| `POST /{id}/disable` | 停用：断开事件流并清理运行时资源 |
| `POST /{id}/test` | 连通性测试 |

- 每个连接对应一个 Milky 客户端实例（`MilkyConnectionRuntime` 管理生命周期，`MilkyRuntimeShutdownRequested` 触发停用清理）；
- 连接启用后才参与消息收发；存在多个启用连接时，插件发送必须显式指定 `connectionId`，否则因配置歧义被拒绝。

## 沙盒聊天

`MilkyChatController`（`/api/platform/milky/connections/{connectionId}/chat`，提供 `conversations` / `history` 等端点）+ `QqSandboxController` 提供**开发沙盒**：不接入真实 QQ，在管理界面里模拟 `message_receive` 等事件注入完整分发管线，用于调试插件的命令/按钮/监听逻辑，支持指令模拟器、端点测试器与用例保存/回放（见 [插件开发者工具](/plugin/dev-tools)）。

## 消息收发与富文本降级

```mermaid
flowchart LR
    subgraph Inbound["入站"]
        QQ["QQ (Milky)"] -->|"WS /event"| EG["ReactorMilkyEventGateway"] --> Bus["Spring 事件总线"] --> D["MilkyPluginEventDispatcher<br/>按钮/命令/监听器"] --> P["插件 interactions"]
        Bus --> W["WebQQ 管理界面 (SSE)"]
    end
    subgraph Outbound["出站"]
        P2["插件发送 PluginMessageContent"] --> R{"内容类型"}
        R -->|"MARKDOWN / HTML"| RS["render-server<br/>渲染成图"] --> Send["Milky API 发送"]
        R -->|"TEXT / IMAGE / FILE"| Send
    end
```

- 入站事件经宿主事件总线（`MilkyEventPublished`）同时驱动**插件分发**与**WebQQ 管理界面实时展示**；
- 出站 MARKDOWN/HTML 内容在消息渲染能力（`PLATFORM_MESSAGE_RENDER_ENABLED`）开启时先渲染为图片再发送，即**富文本降级**；`PluginMessageResult.rendered` / `degraded` 标记是否发生；
- 需要调用未封装的对端方法时走 `PluginMessagingRawService.invoke(connectionId, method, payload)`，调用同步记录到开发者工具沙盒时间线。

## 相关配置

| 配置 | 说明 |
|---|---|
| `PLATFORM_MILKY_ENABLED` | 项目闸门（默认 `true`） |
| `YUDREAM_CREDENTIAL_KEY` | 连接凭据加密主密钥（Base64 编码、解码后恰为 32 字节） |

::: warning ID 类型
`connectionId`、`channelId`、`userId`、`messageId` 等在 JSON / TS / URL 中一律是 `string`，禁止 `Number(id)`。
:::
