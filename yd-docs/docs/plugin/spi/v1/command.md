# 命令端口：PluginCommandRegistry

插件可以向机器人/系统注册**命令**（如 `/签到`、`/wallet 查询`），由宿主统一解析、鉴权与分发。

> 源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/command/`

---

## 注册：PluginCommandRegistry

通过 `context.commands()` 获取：

```java
public interface PluginCommandRegistry {
    AutoCloseable register(PluginCommandDefinition definition, PluginCommandHandler handler);
}
```

### register 参数表

| 参数 | 类型 | 说明 |
|---|---|---|
| definition | `PluginCommandDefinition` | 命令元数据 |
| handler | `PluginCommandHandler` | 处理函数 |

返回的 `AutoCloseable` 在插件 disable/unload 时由宿主统一回收；也可用 `context.onDispose(...)` 自行管理生命周期。

## PluginCommandHandler

```java
@FunctionalInterface
public interface PluginCommandHandler {
    void handle(PluginCommandContext context) throws Exception;
}
```

handler 抛出的异常由宿主记录日志，不影响其他命令。

---

## DTO 字段表

### PluginCommandDefinition

```java
public record PluginCommandDefinition(String code, String command, String name, String permission,
                                      String description, boolean allowAnonymous)
```

| 字段 | 类型 | 说明 |
|---|---|---|
| code | `String` | 插件内唯一标识 |
| command | `String` | 命令字面量（触发词） |
| name | `String` | 展示名 |
| permission | `String` | 执行所需权限码（遵循 `plugin:{code}:{action}`） |
| description | `String` | 用途说明 |
| allowAnonymous | `boolean` | 是否允许匿名（未登录/未绑定）用户触发 |

### PluginCommandContext

```java
public record PluginCommandContext(PluginEvent event, String command, List<String> arguments, Long userId)
```

| 字段 | 类型 | 说明 |
|---|---|---|
| event | `PluginEvent` | 触发的原始消息事件（见 [消息端口](/plugin/spi/v1/messaging)） |
| command | `String` | 命令名 |
| arguments | `List<String>` | 命令后的参数列表（按空白切分，null 规范化为空列表） |
| userId | `Long` | 系统侧用户 ID（可空，匿名时为 null） |

### PluginCommandInfo

面向管理端展示的信息结构：

```java
public record PluginCommandInfo(String pluginCode, String code, String command, String name,
                                String permission, String description, boolean allowAnonymous) {}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| pluginCode | `String` | 注册该命令的插件 code |
| code | `String` | 插件内唯一标识 |
| command | `String` | 命令字面量 |
| name | `String` | 展示名 |
| permission | `String` | 所需权限码 |
| description | `String` | 用途说明 |
| allowAnonymous | `boolean` | 是否允许匿名触发 |

---

## 查询：PluginCommandService

通过 `context.framework().commands()` 获取：

```java
public interface PluginCommandService {
    List<PluginCommandInfo> listAccessible(Long userId);
}
```

| 参数 | 类型 | 说明 |
|---|---|---|
| userId | `Long` | 系统侧用户 ID |

返回该用户当前可用的全部命令（含其他插件注册的），遵循权限模型，前端命令面板据此渲染。

---

## 完整示例

```java
@Override
public void onEnable(PluginContext context) {
    context.commands().register(
            new PluginCommandDefinition(
                    "sign", "/签到", "每日签到",
                    "plugin:sample-plugin:use",
                    "签到领取积分", false),
            ctx -> {
                Long userId = ctx.userId();          // 匿名时为 null，需判空
                List<String> args = ctx.arguments(); // /签到 双倍 -> ["双倍"]
                doSign(userId, args);
            });
}
```

---

## 与 @PluginCommand 注解的关系

方法上标注 `@PluginCommand`（`@Repeatable`，即一个方法可响应多个命令）与编程式 `register` **等价**——宿主启动时由注解扫描器把标注的方法自动注册进同一注册表。注解适合"一个方法一条命令"的简单场景；需要动态构造定义（如按配置批量注册）时用编程式。

注解属性与 `PluginCommandDefinition` 字段一一对应，其中 `permission`/`description` 默认空串、`allowAnonymous` 默认 false：

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(PluginCommands.class)
public @interface PluginCommand {
    String code();
    String command();
    String name();
    String permission() default "";
    String description() default "";
    boolean allowAnonymous() default false;
}
```

使用示例：

```java
@PluginCommand(code = "sign", command = "/签到", name = "每日签到",
        permission = "plugin:sample-plugin:use", description = "签到领取积分")
public void onSign(PluginCommandContext ctx) {
    doSign(ctx.userId(), ctx.arguments());
}
```

---

## 解析与鉴权流程

```mermaid
sequenceDiagram
    participant U as 用户
    participant H as 宿主事件总线
    participant R as 命令注册表
    participant P as 插件 Handler

    U->>H: 发送 /签到 参数
    H->>R: 匹配 command
    alt 未匹配
        H-->>U: 忽略或未知命令提示
    end
    R->>R: allowAnonymous? 权限校验 permission
    alt 校验失败
        H-->>U: 无权限提示
    else 通过
        H->>P: handle(PluginCommandContext)
        P-->>U: 业务响应（经 messaging 发送）
    end
```

## 注意事项

- 命令字面量全局唯一性由宿主管理，重复注册会被拒绝或覆盖，建议带上插件特色前缀。
- `allowAnonymous = true` 的命令拿到的 `ctx.userId()` 可能为 `null`，handler 内必须判空。
- 命令回复经 [消息端口](/plugin/spi/v1/messaging) 发送；需要协议级细节参见 [Milky 协议详解](/protocol/milky)。
- `listAccessible` 遵循权限模型，可用于在插件管理端展示"当前用户可用命令"。

---

> 源码引用：`.../plugin/spi/system/command/` 全部文件；注解定义见 `.../plugin/spi/annotation/PluginCommand.java`
