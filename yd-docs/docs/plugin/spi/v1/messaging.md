# 消息端口：Milky 机器人收发与交互注册

`context.framework().messaging()` / `messagingRaw()` 提供消息发送能力，`context.interactions()` 提供**消息事件订阅**——插件借此实现机器人的消息监听、按钮、命令等交互。

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

- **connections()**：列出当前启用的消息连接。插件可据此让用户在配置中选择目标连接。
- **groups(connectionId)**：列出指定连接可见的群/频道，用于配置选择器或校验目标。
- **send(request)**：最通用的发送入口，由 `PluginMessageRequest` 完整描述目标。
- **sendDirectToBoundUser(userId, content)**：向**绑定了机器人账号的系统用户**发私聊。宿主自动选定已启用的 Milky 连接；存在多个可用连接且无法消歧时拒绝发送。依赖系统的账号绑定数据（见 [用户端口](/plugin/spi/v1/user) 的 `PluginQqBindingService`）。
- **sendToChannel(connectionId, channelId, content)**：向指定连接下的频道发消息。

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
        Map<String, Object> referrer) {

    public enum Type { TEXT, MARKDOWN, HTML, IMAGE, AUDIO, VIDEO, FILE, COMPOSITE }

    public record Attachment(String url, String title, String contentType) {}
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| type | `Type` | 内容类型枚举 |
| content | `String` | 正文文本或资源地址（按 type 解释） |
| attachments | `List<Attachment>` | 附件列表，可为 null（规范化为空列表） |
| referrer | `Map<String,Object>` | 来源上下文（如回复引用），可为 null |

`Attachment` 字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| url | `String` | 附件地址 |
| title | `String` | 附件标题 |
| contentType | `String` | MIME 类型 |

要点：

- `MARKDOWN` / `HTML` 内容在平台开启消息渲染能力时会先经 render-server 渲染成图片再发送，实现富文本降级；
- 构造函数会把 null 的 `attachments`/`referrer` 规范化为不可变空集合；
- 该类型没有静态工厂方法，直接用 record 构造器创建。

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
public record PluginMessagingConnection(String id, String name, String platform, String userId) {}
public record PluginMessagingGroup(String id, String name) {}
```

| 类型 | 字段 | 说明 |
|---|---|---|
| Connection | id | 连接 ID |
| | name | 连接名 |
| | platform | 平台标识（如 Milky） |
| | userId | 连接关联的机器人账号 ID |
| Group | id | 群/频道 ID |
| | name | 群/频道名 |

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

按连接所选协议**原生方法透传**：`method` 为协议方法名，`payload` 为协议参数，返回协议原始响应。Milky 连接使用方法名（如 `get_friend_list`）；官方连接既可使用同一套共享方法名（适配器映射到 OpenAPI），也可传入特异化路径（如 `GET /v2/groups/{openid}/info`、`POST /v2/groups/{openid}/files`）。管理端原生工作台列出官方 sitemap 全部 REST。注意原生调用绕过了宿主的渲染降级等增强逻辑，需自行处理协议约束。

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
| referrer | `Map<String,Object>` | 引用/回复上下文（null 规范化为空 Map） |
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
