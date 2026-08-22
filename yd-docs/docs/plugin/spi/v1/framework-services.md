# 框架能力端口 FrameworkServices

> SPI v1（2.7.0）· 包 `online.yudream.base.plugin.spi.system`

`context.framework()` 返回 `FrameworkServices`，是插件访问宿主稳定能力的唯一入口。**需要新能力时先扩展 SPI 端口/DTO 再由宿主实现适配，禁止直接引用宿主 Spring Bean 或仓储实现。**

## 端口总览

| 方法 | 返回 | 说明 |
|---|---|---|
| `users()` | `PluginUserService` | 用户档案/角色/部门/QQ 绑定 |
| `qqBindings()` | `PluginQqBindingService` | QQ 绑定码签发/核销 |
| `commands()` | `PluginCommandService` | 查询当前用户可用命令 |
| `ai()` | `PluginAiService` | AI 对话 / Agent / 工具 |
| `security()` | `PluginSecurityService` | 权限校验 |
| `mail()` | `PluginMailService` | 邮件发送 |
| `wordTemplates()` | `PluginWordTemplateService` | Word 模板渲染 |
| `documents(pluginCode)` | `PluginDocumentStore` | 插件作用域文档存储 |
| `files(pluginCode)` | `PluginFileStore` | 插件作用域二进制文件存储 |
| `secrets(pluginCode)` | `default PluginSecretStore` | 密钥存取；宿主未开启时抛 `UnsupportedOperationException` |
| `messaging()` | `PluginMessagingService` | 高层消息发送（平台无关渲染） |
| `messagingRaw()` | `PluginMessagingRawService` | 连接所选协议的原生调用 |
| `render()` | `PluginRenderService` | HTML/Markdown/URL 渲染为图片 |
| `platformFile(fileId)` | `Optional<PluginStoredFile>` | 只读访问平台通用上传（`/api/files`）中的文件 |
| `setting(key)` | `Optional<String>` | 读平台配置 |

## security

```java
boolean hasPermission(PluginPrincipal principal, String permission);
void requirePermission(PluginPrincipal principal, String permission); // 无权时抛异常
```

`PluginPrincipal(Long userId, List<String> permissions)`，`hasPermission(p)` 对 `"*"` 放行。

## user

| 方法 | 签名 | 说明 |
|---|---|---|
| `authenticate` | `Optional<PluginUserProfile> authenticate(String usernameOrEmail, String password)` | 用户名或邮箱 + 密码认证 |
| `create` | `PluginUserProfile create(PluginUserCreate create)` | 创建用户 |
| `findById` | `Optional<PluginUserProfile> findById(Long userId)` | 按 ID 查询 |
| `findByUsername` / `findByEmail` / `findByQq` | `Optional<PluginUserProfile> findByXxx(String)` | 唯一索引查询 |
| `bindQqOnce` | `void bindQqOnce(Long userId, String qq)` | 首次绑定 QQ（已绑则拒绝） |
| `searchUsers` | `List<PluginUserOption> searchUsers(String keyword, Long deptId, int page, int size)` | 关键字 + 部门分页搜索 |
| `listDepartments(keyword)` | `List<PluginDeptOption>` | 部门树（选项形态） |
| `listRoles(userId)` | `List<PluginUserRole>` | 用户角色列表 |
| `listDepartments(userId)` | `List<PluginUserDept>` | 用户所属部门 |
| `updateProfile` | `void updateProfile(Long userId, PluginUserProfileUpdate update)` | 更新资料 |

DTO 字段：

| DTO | 字段 |
|---|---|
| `PluginUserProfile` | `id, username, nickname, email, phone, qq, avatar, status` |
| `PluginUserCreate` | `username, nickname, email, phone, qq, password, encodedPassword(可选), emailVerified` |
| `PluginUserProfileUpdate` | `nickname, email, phone, qq, avatar` |
| `PluginUserRole` | `id, code, name` |
| `PluginUserDept` | `id, name, defaultDept(Boolean)` |
| `PluginUserOption` | `id(string), username, nickname, email, avatar, status, deptIds, deptNames` |
| `PluginDeptOption` | `id(string), name, parentId, status, children(List<PluginDeptOption>)` |

QQ 绑定：`issue(userId)` → `PluginQqBindingCode(String code, Instant expiresAt)`；`consume(code)` 核销并返回 `Long userId`。

## storage（文档 / 文件 / 密钥）

**`PluginDocumentStore`** —— 插件作用域文档库（按插件 code 集合隔离）：

| 方法 | 签名 | 说明 |
|---|---|---|
| `save` | `Map<String,Object> save(String collection, String id, Map doc)` | 写入/覆盖文档 |
| `findById` | `Optional<Map<String,Object>> findById(collection, id)` | 按 ID |
| `findAll` | `List<Map<String,Object>> findAll(collection, page, size)` | 全量分页 |
| `findByField` | `List<Map<String,Object>> findByField(collection, field, value, page, size)` | 按字段等值查询 |
| `count` | `long count(collection)` | 文档总数 |
| `updateIfFieldAtMost` | `default boolean updateIfFieldAtMost(collection, id, field, long maximum, Map doc)` | 乐观限流更新：仅当数值字段 ≤ maximum 时写入并返回 true（配额扣减场景）；不支持时默认 false |
| `delete` | `void delete(collection, id)` | 删除 |

**`PluginFileStore`**：

```java
String put(String objectKey, InputStream in, long contentLength, String contentType);
PluginStoredFile get(String objectKey);
void delete(String objectKey);
```

`PluginStoredFile(objectKey, contentType, Long contentLength, InputStream inputStream)`。

**`PluginSecretStore`**：`put(String key, byte[] secret)`、`Optional<byte[]> get(String key)`、`boolean delete(String key)` —— API key 等私有密钥的不透明存储。

## document（Word 模板）

| 方法 | 签名 | 说明 |
|---|---|---|
| `enabled()` | `default boolean enabled()` | 能力是否可用 |
| `templates(keyword, page, size)` | `default List<PluginWordTemplateSummary>` | 分页搜索模板 |
| `template(id)` / `templateByCode(code)` | `default Optional<PluginWordTemplateSummary>` | 按 ID/code 取摘要 |
| `render(templateContent, data)` | `PluginRenderedDocument render(byte[], Map<String,Object>)` | 用模板字节流渲染，占位符来自 data |

`PluginRenderedDocument(byte[] content, String contentType)`；`PluginWordTemplateSummary(id, code, name, originalFilename, updatedAt)`。按模板 ID 渲染是否支持取决于宿主实现，默认抛 `IllegalArgumentException`。

## mail

```java
PluginMailMessage.text(List.of("a@b.c"), "标题", "正文");
PluginMailMessage.html(List.of("a@b.c"), "标题", "<b>HTML</b>");
context.framework().mail().send(message);   // from 为 null 时用宿主默认发件人
```

`PluginMailMessage(from, to/cc/bcc(List<String>), subject, text, html)`。逐方法与宿主行为详见 [邮件 MailSpi](/plugin/spi/v1/mail)。

## render（图片渲染）

```java
// HTML / Markdown / URL → 图片（异步 CompletionStage）
CompletionStage<PluginRenderedImage> html(String html);
CompletionStage<PluginRenderedImage> html(String html, String selector); // 截取选择器区域
CompletionStage<PluginRenderedImage> markdown(String markdown);
CompletionStage<PluginRenderedImage> url(String url);
```

`PluginRenderedImage(contentType, byte[] content(getter 返回 clone), width, height)`。

**插件作用域 Thymeleaf 模板渲染**（模板放插件 JAR `src/main/resources/templates/`，逻辑名不带 `.html`）：

```java
CompletionStage<PluginRenderedImage> image = context.templateRenderer().render(
        "player-card",
        Map.of("playerName", "Steve", "online", true),
        "#player-card"          // selector 可空；提供时使用原生元素截图
);
```

运行时只使用当前插件 ClassLoader 读取模板，拒绝 `..` 与绝对路径跨插件读取。

## messaging（消息发送与监听）

**`PluginMessagingService`**：

| 方法 | 说明 |
|---|---|
| `connections()` | `List<PluginMessagingConnection(id, name, platform, userId)>` 可用连接列表 |
| `groups(connectionId)` | 该连接可见群组 `List<PluginMessagingGroup(id, name)>` |
| `send(request)` | 平台无关内容发送，宿主按目标平台渲染，不支持时降级 |
| `sendDirectToBoundUser(userId, content)` | 打开系统用户绑定 QQ 的私聊并发送（自动选择启用的 Milky 连接，配置歧义时拒绝） |
| `sendToChannel(connectionId, channelId, content)` | 发送到指定频道/群 |

- `PluginMessageRequest(connectionId, platform, userId, channelId, PluginMessageContent content)`
- `PluginMessageResult(List<String> messageIds, boolean rendered, boolean degraded)`——`degraded=true` 表示发生降级渲染
- `PluginMessageContent(type(TEXT/MARKDOWN/HTML/IMAGE/AUDIO/VIDEO/FILE/COMPOSITE), content, attachments(url/title/contentType), referrer(Map))`

**`PluginMessagingRawService`**：`invoke(connectionId, method, payload)` → 直连协议原生方法调用（如 Milky API），返回 `CompletionStage<Map<String,Object>>`。

**`PluginMessageInteractionRegistry`**（全部返回 `AutoCloseable` 句柄，disable 时自动回收）：

| 方法 | 说明 |
|---|---|
| `onMessage(filter, handler)` | 监听标准化消息事件 |
| `onNative(filter, handler)` | 监听平台原生事件 |
| `onCommand(command, handler)` | 监听命令触发 |
| `onButton(buttonId, handler)` | 监听按钮点击 |
| `beforeSend(handler)` / `afterSend(handler)` | 发送前/后拦截钩子 |

`PluginInteractionFilter(eventTypes(Set), platform, channelId, command)`——null/空视为不限。`PluginEvent` 含 `sequence, type, platform, userId, channelId, content, buttonId, command, referrer, nativeType, nativeData, connectionId, selfId, messageId`。

## command（消息命令）

- 编程式注册：`context.commands().register(definition, handler)` → `AutoCloseable`。
- `PluginCommandDefinition(code, command, name, permission, description, allowAnonymous)`。
- `framework().commands().listAccessible(userId)` → 该用户可见的全部插件命令 `List<PluginCommandInfo>`。
- 处理器入参 `PluginCommandContext(event, command, arguments(参数切分), userId)`。

## ai

| 方法 | 说明 |
|---|---|
| `chat(PluginAiChatRequest)` | 单轮对话（可带工具调用）→ `CompletionStage<PluginAiChatResponse>` |
| `tools()` | 平台已注册工具清单 |
| `providers()` | AI provider 及模型清单（provider-first 结构） |
| `agents()` | 可用 Agent 清单 |
| `runAgent(agentCode, request)` | 运行指定 Agent |
| `runAgentStream(agentCode, request, onDelta)` | 流式运行 Agent（default 为完成后整体回调一次；宿主可覆写为真流式） |

关键 DTO：

- `PluginAiChatRequest(systemPrompt, userPrompt, providerCode, modelCode, history(List<PluginAiChatMessage(role,content)>), executionContext, toolCallingEnabled)`
- `PluginAiChatResponse(content, List<PluginAiToolResult> toolResults)`
- `PluginAiExecutionContext(userId, platformUserId, connectionId, channelId, messageId, trigger, traceId, permissions, allowedToolNames(null→["*"]))`；方法 `hasPermission(p)`（空白或 `*` 放行）、`allowsTool(name)`

注册 Agent 工具：

```java
context.registerAiTool(new PluginAiTool() {
    @Override public PluginAiToolDescriptor descriptor() {
        return new PluginAiToolDescriptor(
                "query_activity", "查活动", "按关键字查询活动",
                "plugin:demo:view", PluginAiToolRisk.READ,
                false, Set.of("MENTION"),
                Map.of("type", "object",
                       "properties", Map.of("keyword", Map.of("type", "string"))));
    }
    @Override public PluginAiToolResult execute(PluginAiExecutionContext ctx, PluginAiToolCall call) {
        return new PluginAiToolResult("OK", "找到 N 个活动", Map.of());
    }
});
```

- `PluginAiToolDescriptor(name, title, description, permissionCode, risk(READ/WRITE/DESTRUCTIVE), requiresConfirmation, allowedTriggers(null→{"MENTION"}), inputSchema(JSON Schema Map))`
- `PluginAiToolCall(toolName, arguments)`；`PluginAiToolResult(action, message, payload)`

## memory（语义记忆）

```java
PluginSemanticMemoryStatus status();                                       // available/message/models
CompletionStage<Void> index(record);                                       // 异步入向量索引
CompletionStage<List<PluginSemanticMemoryHit>> search(query);
CompletionStage<Void> delete(namespace, id);
```

- `PluginSemanticMemoryRecord(namespace, id, content, providerCode, modelCode, metadata)`
- `PluginSemanticMemoryQuery(namespace, text, providerCode, modelCode, limit)`——limit 被钳制到 [1,30]
- `PluginSemanticMemoryHit(id, content, score(double), metadata)`
