# 消息端口：QQ 机器人收发与交互注册

`context.framework().messaging()` / `messagingRaw()` 提供消息发送能力，`context.interactions()` 提供**消息事件订阅**——插件借此实现机器人的消息监听、按钮、命令等交互。出站协议由连接的 `protocol` 决定（`milky` 或 `official`），插件代码保持平台无关。

> 源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/messaging/`

---

## PluginMessagingService —— 发送

通过 `context.framework().messaging()` 获取：

```java
public interface PluginMessagingService {
    List<PluginMessagingConnection> connections();
    List<PluginMessagingGroup> groups(String connectionId);
    CompletionStage<PluginMessageResult> send(PluginMessageRequest request);
    CompletionStage<PluginMessageResult> sendDirectToBoundUser(String userId, PluginMessageContent content);
    CompletionStage<PluginMessageResult> sendToChannel(String connectionId, String channelId, PluginMessageContent content);
}
```

### 方法参数表

| 方法 | 参数 | 类型 | 说明 |
|---|---|---|---|
| `connections()` | （无） | | 返回当前启用的消息连接清单 |
| `groups(connectionId)` | connectionId | `String` | 连接 ID |
| `send(request)` | request | `PluginMessageRequest` | 通用发送请求 |
| `sendDirectToBoundUser(userId, content)` | userId | `String` | 系统侧用户 ID（字符串） |
| | content | `PluginMessageContent` | 消息体 |
| `sendToChannel(connectionId, channelId, content)` | connectionId | `String` | 连接 ID |
| | channelId | `String` | 频道/会话 ID |
| | content | `PluginMessageContent` | 消息体 |

所有发送方法均返回 `CompletionStage<PluginMessageResult>`，异步完成；发送失败以异常形式体现在 future 上。

### 方法说明

- **connections()**：列出当前启用的消息连接。`platform` 当前为 `qq`，用 `protocol`（`milky` / `official`）区分出站协议，不要用 `platform` 判断是否官 Q。插件前端选择器应使用宿主 SDK `sdk.messaging.connections()`。
- **groups(connectionId)**：列出指定连接可见的群/频道。官方连接没有历史群拉取，只返回本进程事件缓存；重启后为空直到再次收到该群消息。
- **send(request)**：最通用的发送入口，由 `PluginMessageRequest` 完整描述目标。官方连接会读 `content.referrer.message_scene`：`group` → `send_group_message`，`friend`/`private` → `send_private_message`，`channel` → `send_channel_message`，`dm` → `send_guild_dm`。`referrer.msg_id` / `event_id` / `message_id` 会作为被动回复凭证带上。Milky 连接忽略官方场景名，群仍走 `send_group_message`。
- **sendDirectToBoundUser(userId, content)**：向**绑定了机器人账号的系统用户**发私聊。宿主自动选定已启用的消息连接；存在多个可用连接且无法消歧时拒绝发送。依赖系统的账号绑定数据（见 [用户端口](/plugin/spi/v1/user) 的 `PluginQqBindingService`）。官方身份是 openid，不能当成 QQ 号做账号绑定。
- **sendToChannel(connectionId, channelId, content)**：向指定连接下的群发消息（始终走 `send_group_message`）。官方文字子频道 / 频道私信不要用这个入口，应走 `send()` 并在 `content.referrer.message_scene` 标明 `channel` / `dm`，或用 `messagingRaw().invoke` 调用特异化方法。

### 使用示例

```java
FrameworkServices fw = context.framework();

// 1. 列出可用连接
List<PluginMessagingConnection> conns = fw.messaging().connections();
if (conns.isEmpty()) {
    return; // 无启用连接，跳过发送
}
PluginMessagingConnection conn = conns.get(0);

// 2. 向频道发文本消息
fw.messaging().send(new PluginMessageRequest(
        conn.id(), conn.platform(),
        "123456",                 // 平台侧用户 ID（可空）
        "987654",                 // 频道/群 ID
        new PluginMessageContent(
                PluginMessageContent.Type.TEXT,
                "hello from plugin",
                null, null)))
        .thenAccept(result -> log.info("sent: {}", result.messageIds()));

// 3. 向绑定了系统用户的私聊发消息
fw.messaging().sendDirectToBoundUser(
        "10001",                  // 系统侧用户 ID，字符串
        new PluginMessageContent(
                PluginMessageContent.Type.TEXT, "签到成功", null, null));
```

---

## PluginMessageContent —— 消息体

```java
public record PluginMessageContent(
        Type type,
        String content,
        List<Attachment> attachments,
        Map<String, Object> referrer,
        List<Button> buttons) {

    public enum Type { TEXT, MARKDOWN, HTML, IMAGE, AUDIO, VIDEO, FILE, COMPOSITE }

    public record Attachment(String url, String title, String contentType) {}

    public record Button(String id, String label, String data, boolean enter) {
        public static Button command(String id, String label, String cmd) { ... }
        public static Button callback(String id, String label, String data) { ... }
    }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| type | `Type` | 内容类型枚举 |
| content | `String` | 正文文本或资源地址（按 type 解释） |
| attachments | `List<Attachment>` | 附件列表，可为 null（规范化为空列表） |
| referrer | `Map<String,Object>` | 来源上下文（如回复引用），可为 null |
| buttons | `List<Button>` | 交互按钮列表，可为 null（规范化为空列表） |

`Attachment` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| url | `String` | 附件地址 |
| title | `String` | 附件标题 |
| contentType | `String` | MIME 类型 |

`Button` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| id | `String` | 按钮 ID（回调时回传） |
| label | `String` | 按钮文案 |
| data | `String` | 指令文本或回调数据 |
| enter | `boolean` | `true` = 点击直接发出指令（官方 keyboard `action.type=2`）；`false` = 回调事件（`action.type=1`） |

要点：

- `MARKDOWN` / `HTML` 内容在平台开启消息渲染能力时会先经 render-server 渲染成图片再发送，实现富文本降级；
- 构造函数会把 null 的 `attachments`/`referrer`/`buttons` 规范化为不可变空集合；
- 四参数旧构造器（无 `buttons`）仍然保留，旧插件无需改代码；
- `Button.command(id, label, cmd)` 等价于 `enter=true` 的指令按钮，`Button.callback(id, label, data)` 等价于 `enter=false` 的回调按钮；
- 按钮仅在官方 QQ 机器人连接上原生呈现为 keyboard（一行最多 4 个、最多 5 行）；Milky 等其他协议自动降级为纯文本，消息本身不会丢失；
- 官方 QQ 连接上，无媒体附件的 `TEXT`/`MARKDOWN` 消息一律以 `msg_type=2`（markdown）发出，`attachments` 中的 http(s) 图片会以 `[[title]]` 标记内嵌进 markdown 正文；`referrer` 中的 `msg_id`/`event_id` 作为被动回复凭证透传。

---

## PluginMessageRequest / PluginMessageResult

### PluginMessageRequest

| 字段 | 类型 | 说明 |
|---|---|---|
| connectionId | `String` | 目标连接 ID |
| platform | `String` | 平台标识 |
| userId | `String` | 平台侧目标用户 ID（私聊时使用，可空） |
| channelId | `String` | 频道/会话 ID |
| content | `PluginMessageContent` | 消息体 |

### PluginMessageResult

| 字段 | 类型 | 说明 |
|---|---|---|
| messageIds | `List<String>` | 发送成功后的平台侧消息 ID |
| rendered | `boolean` | 是否经渲染为图片后发送 |
| degraded | `boolean` | 是否发生了降级发送 |

---

## PluginMessagingConnection / PluginMessagingGroup

```java
public record PluginMessagingConnection(String id, String name, String platform, String userId, String protocol) {
    public PluginMessagingConnection(String id, String name, String platform, String userId) {
        this(id, name, platform, userId, null);
    }
}
public record PluginMessagingGroup(String id, String name) {}
```

| 类型 | 字段 | 说明 |
|---|---|---|
| Connection | id | 连接 ID |
| | name | 连接名 |
| | platform | 平台标识（当前为 `qq`） |
| | userId | 连接关联的机器人账号 ID |
| | protocol | 出站协议：`milky` 或 `official`；四参数构造缺省为 null |
| Group | id | 群/频道 ID |
| | name | 群/频道名 |

插件前端选择器应使用宿主 SDK `sdk.messaging.connections()/groups()`，不要再为连接/群目录单独写插件 HTTP。官方连接的群列表来自本进程事件缓存，重启后为空直到再次收到该群消息。打开选择器时，宿主会对还只有 openid 的群补一次 `GET /v2/groups/{openid}/info`，有权限时把群名写进缓存。

---

## PluginMessagingRawService —— 协议原生调用

通过 `context.framework().messagingRaw()` 获取：

```java
CompletionStage<Map<String, Object>> invoke(String connectionId, String method, Map<String, Object> payload);
```

| 参数 | 类型 | 说明 |
|---|---|---|
| connectionId | `String` | 目标连接 ID |
| method | `String` | 协议方法名 |
| payload | `Map<String,Object>` | 协议参数 |

按连接所选协议**原生方法透传**：`method` 为协议方法名，`payload` 为协议参数，返回协议原始响应。Milky 连接使用方法名（如 `get_friend_list`）；官方连接既可使用同一套共享方法名（适配器映射到 OpenAPI），也可传入特异化方法名（`send_channel_message`、`create_guild_dm`、`send_guild_dm`、`recall_channel_message`、`get_guild_list`、`set_guild_mute`、`set_guild_member_mute`、`set_guild_members_mute`、`get_group_bot_state`、`get_join_approval_strategy`）或路径（如 `GET /v2/groups/{openid}/info`、`POST /v2/groups/{openid}/files`）。官方没有 Milky 的 `get_resource_temp_url`，附件 URL 已在事件里时直接使用；群成员列表无接口权限时回落会话缓存，不再把 400/11253 打成 ERROR。管理端原生工作台列出官方 sitemap 全部 REST。注意原生调用绕过了宿主的渲染降级等增强逻辑，需自行处理协议约束。

```java
Map<String, Object> resp = context.framework().messagingRaw()
        .invoke(connId, "get_friend_list", Map.of())
        .toCompletableFuture().join();
```

---

## PluginMessageInteractionRegistry —— 事件订阅

通过 `context.interactions()` 获取。所有注册方法返回 `AutoCloseable`，插件 disable/unload 时宿主统一回收，也可用 `context.onDispose(...)` 自行管理。

| 方法 | 签名 | 说明 |
|---|---|---|
| onMessage | `AutoCloseable onMessage(PluginInteractionFilter filter, PluginMessageHandler handler)` | 订阅普通消息事件 |
| onNative | `AutoCloseable onNative(PluginInteractionFilter filter, PluginMessageHandler handler)` | 订阅协议原生事件 |
| onCommand | `AutoCloseable onCommand(String command, PluginMessageHandler handler)` | 订阅命令触发 |
| onButton | `AutoCloseable onButton(String buttonId, PluginMessageHandler handler)` | 订阅按钮回调 |
| beforeSend | `AutoCloseable beforeSend(PluginMessageHandler handler)` | 发送前置拦截（可做审计/改写） |
| afterSend | `AutoCloseable afterSend(PluginMessageHandler handler)` | 发送后置回调 |

各方法参数即表中签名所示：`filter` 见 [PluginInteractionFilter](#plugininteractionfilter-字段表)，`command`/`buttonId` 均为精确匹配的 `String`。

`PluginMessageHandler` 为函数式接口：

```java
@FunctionalInterface
public interface PluginMessageHandler {
    void handle(PluginEvent event) throws Exception;
}
```

handler 抛出的异常由宿主记录日志并继续分发后续监听者，不会中断整个事件总线。

---

## PluginEvent 字段表

```java
public record PluginEvent(String sequence, String type, String platform, String userId, String channelId,
                          String content, String buttonId, String command, Map<String, Object> referrer,
                          String nativeType, Object nativeData, String connectionId, String selfId, String messageId)
```

| 字段 | 类型 | 说明 |
|---|---|---|
| sequence | `String` | 事件序号（注意是字符串，非数值） |
| type | `String` | 事件类型 |
| platform | `String` | 平台标识 |
| userId | `String` | 触发用户（平台侧 ID） |
| channelId | `String` | 频道/会话 ID |
| content | `String` | 文本内容 |
| buttonId | `String` | 按钮回调 ID（按钮事件时） |
| command | `String` | 命令名（命令事件时） |
| referrer | `Map<String,Object>` | 引用/回复上下文（null 规范化为空 Map）。Milky 群聊用 `mentions`（真实 mention 段里的 user_id 列表）；官方 OpenAPI 群 AT / 私聊另有 `mentionSelf=true`，不要把官方 AT 伪装成 Milky mention 段。 |
| nativeType | `String` | 原生事件类型（onNative 场景） |
| nativeData | `Object` | 原始事件载荷 |
| connectionId | `String` | 来源连接 |
| selfId | `String` | 机器人自身账号 ID |
| messageId | `String` | 消息 ID |

## PluginInteractionFilter 字段表

```java
public record PluginInteractionFilter(Set<String> eventTypes, String platform, String channelId, String command)
```

| 字段 | 类型 | 说明 |
|---|---|---|
| eventTypes | `Set<String>` | 关注的事件类型集合（null 规范化为空集合，表示不限制） |
| platform | `String` | 平台过滤 |
| channelId | `String` | 频道过滤 |
| command | `String` | 命令过滤 |

除 `eventTypes` 外，其余字段为 null 时表示不限制。

---

## 完整示例：群消息问答 + 按钮 + 原生调用

```java
@Override
public void onEnable(PluginContext context) {
    // 监听含"天气"关键词的消息并回复
    context.interactions().onMessage(
            new PluginInteractionFilter(Set.of("MESSAGE"), null, null, null),
            event -> {
                if (event.content() != null && event.content().contains("天气")) {
                    String city = extractCity(event.content());
                    context.framework().messaging().send(new PluginMessageRequest(
                            event.connectionId(), event.platform(),
                            event.userId(), event.channelId(),
                            new PluginMessageContent(
                                    PluginMessageContent.Type.TEXT,
                                    city + " 今日晴",
                                    null, null)));
                }
            });

    // 注册按钮回调
    context.interactions().onButton("weather:refresh", event -> {
        // 刷新按钮被点击，event.buttonId() == "weather:refresh"
    });

    // 订阅协议原生事件
    context.interactions().onNative(
            new PluginInteractionFilter(null, null, null, null),
            event -> {
                // event.nativeType()/nativeData() 为协议原始信息
            });
}
```

---

## 注意事项

- `sendDirectToBoundUser` 依赖系统的账号绑定数据（见 [用户端口](/plugin/spi/v1/user)），目标用户未绑定机器人账号时失败。
- 所有 ID（connectionId/channelId/userId/messageId 等）在 SPI 中均为 `String`，不要转成数值。
- 高频场景请在 filter 中尽量收窄条件，避免每个事件都进入 handler。
- 需要协议细节（连接模型、事件流、共享方法名与官方特异化入口）参见 [QQ 机器人协议详解](/protocol/milky)。

---

> 源码引用：`.../plugin/spi/system/messaging/` 全部文件；宿主适配见 `yudream-infrastructure/.../infra/platform/plugin/service/` 与 milky 能力模块
