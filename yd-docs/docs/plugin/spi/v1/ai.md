# AI 端口：对话、Agent 与自定义工具

`FrameworkServices.ai()` 返回 `PluginAiService`，插件可以用它直接调用平台配置好的大模型与 Agent；同时插件可以通过 `context.registerAiTool(...)` 把自己的业务能力注册为 **AI 工具**，供平台的 AI 对话 / Agent 工作流调用。

> 源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/ai/`

---

## PluginAiService —— AI 服务入口

| 方法 | 签名 | 说明 |
|---|---|---|
| chat | `CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request)` | 单轮模型调用（可带历史与工具开关） |
| tools | `List<PluginAiToolDescriptor> tools()` | 当前已注册的全部 AI 工具描述 |
| providers | `List<PluginAiProviderOption> providers()` | 平台配置的模型 provider 清单（code/name/models） |
| agents | `List<PluginAiAgentOption> agents()` | 可调用的 Agent 清单 |
| runAgent | `CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request)` | 以指定 Agent 执行一轮任务 |
| runAgentStream | `default CompletionStage<PluginAiChatResponse> runAgentStream(String agentCode, PluginAiChatRequest request, Consumer<String> onDelta)` | 流式执行 Agent；**接口默认实现并非真流式**——它在完成后一次性回调全部 delta，宿主覆盖实现后才逐段回调 |

接口源码（`PluginAiService.java`）：

```java
public interface PluginAiService {
    CompletionStage<PluginAiChatResponse> chat(PluginAiChatRequest request);
    List<PluginAiToolDescriptor> tools();
    List<PluginAiProviderOption> providers();
    List<PluginAiAgentOption> agents();
    CompletionStage<PluginAiChatResponse> runAgent(String agentCode, PluginAiChatRequest request);

    default CompletionStage<PluginAiChatResponse> runAgentStream(
            String agentCode,
            PluginAiChatRequest request,
            Consumer<String> onDelta
    ) {
        return runAgent(agentCode, request).thenApply(response -> {
            if (onDelta != null && response.content() != null && !response.content().isEmpty()) {
                onDelta.accept(response.content());
            }
            return response;
        });
    }
}
```

获取方式：`context.framework().ai()`（`FrameworkServices.ai()`）。除 `tools()` / `providers()` / `agents()` 三个同步清单方法外，其余方法返回 `CompletionStage`，异步执行，不要在调用线程阻塞等待。

```java
PluginAiService ai = context.framework().ai();

// 前置检查
boolean available = !ai.providers().isEmpty();

PluginAiChatRequest req = new PluginAiChatRequest(
        "你是客服助手",                       // systemPrompt
        "帮我总结这条反馈：" + feedback,      // userPrompt
        null, null,                           // providerCode / modelCode，null 用平台默认
        List.of(),                            // history
        new PluginAiExecutionContext(userId, null, null, null, null,
                "PLUGIN", traceId, List.of(), List.of()),
        false);                               // toolCallingEnabled

ai.chat(req).thenAccept(resp -> {
    // resp.content() 为模型输出
});
```

---

## PluginAiChatRequest / 相关 DTO

### PluginAiChatRequest

源码（`PluginAiChatRequest.java`）：

```java
public record PluginAiChatRequest(
        String systemPrompt,
        String userPrompt,
        String providerCode,
        String modelCode,
        List<PluginAiChatMessage> history,
        PluginAiExecutionContext executionContext,
        boolean toolCallingEnabled
) {
    public PluginAiChatRequest {
        history = history == null ? List.of() : List.copyOf(history);
    }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `systemPrompt` | `String` | 系统提示词 |
| `userPrompt` | `String` | 用户输入 |
| `providerCode` | `String` | 指定 provider，`null` 用平台默认 |
| `modelCode` | `String` | 指定模型，`null` 用 provider 默认模型 |
| `history` | `List<PluginAiChatMessage>` | 多轮历史；紧凑构造器把 `null` 规整为空 List，并复制为不可变 List |
| `executionContext` | `PluginAiExecutionContext` | 执行上下文（用户、链路、权限、工具白名单） |
| `toolCallingEnabled` | `boolean` | 是否允许本轮触发工具调用 |

### PluginAiChatMessage

源码（`PluginAiChatMessage.java`）：`public record PluginAiChatMessage(String role, String content)`。

| 成员 | 类型 | 说明 |
|---|---|---|
| `role` | `String` | 角色（`user` / `assistant` 等） |
| `content` | `String` | 内容 |
| `assistant()` | 实例方法，返回 `boolean` | 判断本条消息是否为 assistant 角色（对 `role` 忽略大小写比较），便于插件遍历历史时分拣消息 |

构造历史消息直接用 record 构造器：

```java
List<PluginAiChatMessage> history = List.of(
        new PluginAiChatMessage("user", "昨天你帮我查过订单 1001"),
        new PluginAiChatMessage("assistant", "是的，该订单已发货。"));
```

### PluginAiChatResponse

源码（`PluginAiChatResponse.java`）：`public record PluginAiChatResponse(String content, List<PluginAiToolResult> toolResults)`。

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | `String` | 最终文本输出 |
| `toolResults` | `List<PluginAiToolResult>` | 本轮触发的工具执行结果；`null` 入参被紧凑构造器规整为空 List（不可变副本） |

### Provider / Model / Agent 清单 DTO

| 类型 | 字段 | 说明 |
|---|---|---|
| `PluginAiProviderOption(String code, String name, List<PluginAiModelOption> models)` | `code` / `name` / `models` | 平台配置的 AI provider；`models` 为 `null` 时规整为空 List。可先用它判断能力是否可用、让用户选模型 |
| `PluginAiModelOption(String code, String name)` | `code` / `name` | provider 下的单个模型 |
| `PluginAiAgentOption(String code, String name, String description)` | `code` / `name` / `description` | 平台已配置的 Agent，`code` 用于 `runAgent(...)` |

---

## PluginAiExecutionContext —— 执行上下文

源码（`PluginAiExecutionContext.java`）：

```java
public record PluginAiExecutionContext(
        Long userId,
        String platformUserId,
        String connectionId,
        String channelId,
        String messageId,
        String trigger,
        String traceId,
        List<String> permissions,
        List<String> allowedToolNames
) {
    public PluginAiExecutionContext {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        allowedToolNames = allowedToolNames == null ? List.of("*") : List.copyOf(allowedToolNames);
    }

    // 便捷构造器：不传 allowedToolNames 时默认 ["*"]（允许全部工具）
    public PluginAiExecutionContext(Long userId, String platformUserId, String connectionId, String channelId,
                                    String messageId, String trigger, String traceId, List<String> permissions) {
        this(userId, platformUserId, connectionId, channelId, messageId, trigger, traceId, permissions, List.of("*"));
    }
}
```

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `userId` | `Long` | — | 触发用户 ID（宿主内部雪花 ID）。**进入 JSON / URL / 表单时一律序列化为 string，禁止 `Number(id)`** |
| `platformUserId` | `String` | — | 机器人平台侧用户 ID（消息场景） |
| `connectionId` | `String` | — | 消息连接 ID |
| `channelId` | `String` | — | 频道/会话 ID |
| `messageId` | `String` | — | 触发消息 ID |
| `trigger` | `String` | — | 触发来源（如 MENTION / COMMAND / PLUGIN） |
| `traceId` | `String` | — | 全链路追踪 ID |
| `permissions` | `List<String>` | 空 List | 允许的权限码 |
| `allowedToolNames` | `List<String>` | `["*"]` | 允许调用的工具名白名单；`null` 规整为通配 |

辅助方法：

- `hasPermission(String permission)`：权限为空白视为放行；列表含 `"*"` 或包含该权限码时返回 true。
- `allowsTool(String name)`：白名单含 `"*"` 或包含该工具名时返回 true。

宿主在调度工具前会用它做**双重过滤**——不在白名单内的工具不会被调用。

---

## 自定义 AI 工具开发

### PluginAiTool 接口

```java
public interface PluginAiTool {
    PluginAiToolDescriptor descriptor();
    PluginAiToolResult execute(PluginAiExecutionContext context, PluginAiToolCall call);
}
```

注册方式：

```java
@Override
public void onEnable(PluginContext context) {
    context.registerAiTool(new OrderQueryTool());
}
```

### PluginAiToolDescriptor 字段表

源码（`PluginAiToolDescriptor.java`）：

```java
public record PluginAiToolDescriptor(
        String name,
        String title,
        String description,
        String permissionCode,
        PluginAiToolRisk risk,
        boolean requiresConfirmation,
        Set<String> allowedTriggers,
        Map<String, Object> inputSchema
) {
    public PluginAiToolDescriptor {
        risk = risk == null ? PluginAiToolRisk.READ : risk;
        allowedTriggers = allowedTriggers == null ? Set.of("MENTION") : Set.copyOf(allowedTriggers);
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
```

| 字段 | 类型 | 默认 | 说明 |
|---|---|---|---|
| `name` | `String` | 必填 | 工具唯一名（模型据此选择） |
| `title` | `String` | — | 展示名 |
| `description` | `String` | 必填 | 给模型的用途说明，写得越具体模型选得越准 |
| `permissionCode` | `String` | — | 执行所需权限码 |
| `risk` | `PluginAiToolRisk` | `READ`（`null` 入参被紧凑构造器兜底） | 风险级别：`READ` 只读 / `WRITE` 写入 / `DESTRUCTIVE` 破坏性 |
| `requiresConfirmation` | `boolean` | `false` | 是否需要用户二次确认后执行 |
| `allowedTriggers` | `Set<String>` | `{MENTION}`（`null` 入参被紧凑构造器兜底） | 允许的触发来源 |
| `inputSchema` | `Map<String,Object>` | 空 Map（`null` 入参被紧凑构造器兜底） | 参数 JSON Schema |

### PluginAiToolCall / PluginAiToolResult

- `PluginAiToolCall(String toolName, Map<String,Object> arguments)` —— 模型产出的参数；`arguments` 为 `null` 时被紧凑构造器规整为空 Map（不可变副本），取值前注意判空。
- `PluginAiToolResult(String action, String message, Map<String,Object> payload)` —— 插件返回给模型的执行结果（action 表明成功/失败语义，payload 会进入模型上下文）；`payload` 同样被规整为不可变空 Map。

### 完整示例

```java
public class OrderQueryTool implements PluginAiTool {

    private final PluginContext context;

    @Override
    public PluginAiToolDescriptor descriptor() {
        return new PluginAiToolDescriptor(
                "order_query", "订单查询",
                "按订单号查询订单状态与金额。arguments: {\"orderNo\": \"string\"}",
                "plugin:wallet:view",
                PluginAiToolRisk.READ,
                false,
                Set.of("MENTION"),
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "orderNo", Map.of("type", "string", "description", "订单号")),
                        "required", List.of("orderNo")));
    }

    @Override
    public PluginAiToolResult execute(PluginAiExecutionContext ctx, PluginAiToolCall call) {
        String orderNo = (String) call.arguments().get("orderNo");
        Map<String, Object> order = loadOrder(orderNo);          // 走 documents()/业务服务
        if (order == null) {
            return new PluginAiToolResult("FAIL", "订单不存在: " + orderNo, Map.of());
        }
        return new PluginAiToolResult("OK", "查询成功",
                Map.of("status", order.get("status"), "amount", order.get("amount")));
    }
}
```

---

## 完整示例：Agent 调用与流式输出

```java
public class ReportPlugin extends YuDreamPlugin {

    private PluginAiService ai;

    @Override
    public void onEnable(PluginContext context) {
        ai = context.framework().ai();

        // 前置检查：平台未配置任何 provider 时不要发起调用
        if (ai.providers().isEmpty()) {
            return;
        }
        // 可选：列出可用 Agent，确认目标 agentCode 存在
        boolean hasReporter = ai.agents().stream()
                .anyMatch(a -> "daily-reporter".equals(a.code()));

        String userIdStr = "1945000000000000001";          // 前端/URL 传来的 ID 一律是 string
        PluginAiExecutionContext ctx = new PluginAiExecutionContext(
                Long.valueOf(userIdStr),                    // Java 侧再转 Long
                null, null, null, null,
                "PLUGIN", "trace-20260822-001",
                List.of(),                                  // permissions：空列表（无额外权限）
                List.of("order_query"));                    // 只允许调用 order_query 工具

        PluginAiChatRequest req = new PluginAiChatRequest(
                "你是项目日报助手，输出简洁的中文日报。",
                "汇总今天的构建与部署情况",
                null, null,                                 // providerCode / modelCode 用平台默认
                List.of(),                                  // history
                ctx,
                true);                                      // toolCallingEnabled：允许触发工具

        // 1) 以指定 Agent 执行
        ai.runAgent("daily-reporter", req)
          .thenAccept(resp -> {
              for (PluginAiToolResult tr : resp.toolResults()) {
                  // 工具执行结果可记录日志或回传消息
              }
              // resp.content() 即最终报告文本
          })
          .exceptionally(ex -> {
              // 模型超时 / provider 未配置等失败降级
              return null;
          });

        // 2) 流式执行：onDelta 逐段收到增量文本（默认实现为一次性回调，见注意事项）
        ai.runAgentStream("daily-reporter", req,
                delta -> { /* 推送给前端或消息通道 */ });
    }
}
```

---

## 注意事项

- `runAgentStream` 的默认实现是"完成后一次性回调"，若你的宿主版本未覆盖真流式，前端不应依赖逐字渲染。
- 工具的 `description` 与 `inputSchema` 直接决定模型调用质量，请把参数格式、单位、约束写在 description 里。
- `DESTRUCTIVE` 级别工具建议同时设置 `requiresConfirmation = true`。
- 工具随插件 disable/unload 自动注销，无需手动反注册。
- **Long ID 序列化**：`PluginAiExecutionContext.userId` 为雪花 `Long`。凡进入 JSON、URL 参数、表单的一律用 string 承载，插件内用 `Long.valueOf(str)` 解析，禁止 `Number(id)` 截断。
- **不可变 DTO**：所有 record 的集合字段经紧凑构造器做 `List.copyOf` / `Map.copyOf` / `Set.copyOf` 规整——传 `null` 得到空集合（`allowedToolNames` 例外，`null` 得到 `["*"]`），传入的集合会被复制，构造后再修改原集合不影响 DTO。
- **异步语义**：`chat` / `runAgent` / `runAgentStream` 返回 `CompletionStage`，失败体现为 failed stage，务必链式 `exceptionally(...)` 降级，不要在请求线程 `join()` 死等。
- **能力前置检查**：调用前用 `providers().isEmpty()` / `agents()` 判断平台 AI 是否可用，避免必然失败的调用。

---

> 源码引用：`.../plugin/spi/system/ai/` 下 `PluginAiService.java`、`PluginAiTool.java`、`PluginAiToolDescriptor.java`、`PluginAiExecutionContext.java`、`PluginAiChatRequest.java` 等；宿主适配见 `yudream-infrastructure/.../infra/platform/plugin/service/PluginAiFrameworkService.java`
