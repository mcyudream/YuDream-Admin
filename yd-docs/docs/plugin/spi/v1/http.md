# HTTP 端点

> SPI v1 · 当前源码 2.24.0 · 包 `online.yudream.base.plugin.spi.http`

插件 HTTP 端点由宿主运行时统一分发，挂载在 `/api/plugins/{pluginCode}/**` 下。权限校验、统一响应包装均由宿主完成。

## PluginHttpRequest

| 字段 | 类型 | 说明 |
|---|---|---|
| `method` | String | HTTP 方法 |
| `path` | String | 匹配后的相对路径（不含 `/api/plugins/{pluginCode}` 前缀） |
| `headers` | `Map<String, List<String>>` | 请求头（同名多值；null 归一为空 Map，构造时防御性拷贝） |
| `query` | `Map<String, List<String>>` | 查询参数，同名多值 |
| `body` | String | 请求体原文 |
| `principal` | [PluginPrincipal](#pluginprincipal) | 已认证用户，匿名时可能为 null |

## PluginHttpResponse

字段：`int status`、`Map<String,String> headers`、`String contentType`（为空自动回退 `application/json`）、`Object body`、`boolean wrapped`。

| 成员 | 签名 | 说明 |
|---|---|---|
| `ok(body)` | `static PluginHttpResponse ok(Object)` | 200 + JSON + 统一信封包装 |
| `json(status, body)` | `static` | 指定状态码 JSON，wrapped=true |
| `rawJson(status, body)` | `static` | wrapped=false：body 原样序列化，不加系统信封（外部协议兼容场景） |
| `noContent()` | `static` | 204 空响应 |
| `withWrapped(wrapped)` | 实例方法 | 返回切换 wrapped 标记的新实例 |

## PluginHttpHandler

函数式接口，配合 `context.registerHttpHandler(method, path, handler)` 编程式注册：

```java
public interface PluginHttpHandler {
    PluginHttpResponse handle(PluginHttpRequest request);
}
```

## PluginSseStream

SSE 流式响应句柄，用于长任务日志推送等场景：

```java
public interface PluginSseStream {
    void subscribe(Subscriber subscriber);
    void unsubscribe(Subscriber subscriber);

    interface Subscriber {
        void send(String event, Object data); // 发送一个命名事件
        void complete();                      // 正常结束
        void error(Throwable throwable);
    }
}
```

::: warning
发送结构化错误事件后应调用 `complete()` 正常结束，不要使用 `completeWithError`——否则 Spring MVC 可能在响应已是 `text/event-stream` 时把异常路由进全局 JSON 处理器。
:::

## PluginPrincipal

```java
public record PluginPrincipal(Long userId, List<String> permissions)
```

- `hasPermission(String p)`：权限集含 `"*"` 或包含 p 时返回 true。
- `userId()` 是 Java Long；跨到前端 JSON 时必须转为 string。

## 使用示例

带权限的管理接口：

```java
public class DemoController {

    @PluginHttpEndpoint(method = "GET", path = "/admin/resources",
            permission = "plugin:demo:manage")
    public PluginHttpResponse list(PluginHttpRequest request) {
        int page = Integer.parseInt(request.query().getOrDefault("page", List.of("1")).get(0));
        return PluginHttpResponse.ok(service.page(page));
    }

    // 用户侧：从 principal 取当前用户
    @PluginHttpEndpoint(method = "GET", path = "/me/resources")
    public PluginHttpResponse mine(PluginHttpRequest request) {
        long userId = request.principal().userId();
        return PluginHttpResponse.ok(service.findByUser(userId));
    }
}
```

注意事项：

- 路径只写相对部分且以 `/` 开头；不要在代码里拼 `/api/plugins/...` 前缀。
- 管理接口必须声明权限；用户侧以 `request.principal()` 为核心。
- 长任务日志用 SSE（`PluginSseStream`）或可轮询状态接口，不要把大日志塞进一次性响应。
