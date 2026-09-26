# SPI 2.33.0：插件 WebSocket 端点

此版本新增一个向后兼容的契约：插件可注册 WebSocket 端点，由宿主统一挂载、鉴权与生命周期回收。`PluginHttpPart`/`PluginHttpRequest.parts`（2.31.0）与插件流式 HTTP 契约同属插件网络面，本版本号由插件 WebSocket 契约占用。

```java
public interface PluginWsHandler {
    void onOpen(PluginWsSession session, PluginWsHandshake handshake);
    default void onText(PluginWsSession session, String payload) {}
    default void onBinary(PluginWsSession session, byte[] payload) {}
    default void onClose(PluginWsSession session, int statusCode, String reason) {}
    default void onError(PluginWsSession session, Throwable error) {}
}

default void PluginContext.registerWebSocketHandler(String path, String permission, PluginWsHandler handler)
default void PluginContext.registerWebSocketHandler(String path, PluginWsHandler handler) // 无权限门槛
```

## 兼容性

- `registerWebSocketHandler` 在 `PluginContext` 上以 `default` 方法声明，未实现该契约的宿主上调用抛出 `UnsupportedOperationException`（需要 SPI ≥ 2.33.0 的宿主）。既有第三方 `PluginContext` 实现类无需任何改动即可编译与运行。
- 既有插件不注册 WS 端点则行为完全不变；无任何既有签名被修改。

## 挂载与路径

- 端点最终挂载在宿主命名空间：`/api/platform/plugin-ws/{pluginCode}{path}`（`path` 为注册时相对路径，支持 `*`、`**`、`{var}` 模式）。
- **不挂载在** `/api/plugins/{code}/**`（其 MVC `RequestMapping` order=0 会压过任何标准 WS 映射），也不挂载在 `/api/platform/plugins/**`（与 `GET /{code}/assets/**` 等既有路由在任意插件 code 下形状重叠）。最终前缀 `/api/platform/plugin-ws/**` 与两者完全隔离：任意合法插件 code（含 `"ws"`、`"assets"`）的既有 MVC 路由全部保持可达。宿主实现为自建 `SimpleUrlHandlerMapping`（order = -100，仅覆盖本前缀），只放行带 `Upgrade: websocket` 头的请求；非升级请求命中本前缀一律 404。
- 插件 code 不做任何保留字限制（不保留 `"ws"`）。

## 鉴权模型

握手阶段（HTTP 升级请求）按以下顺序解析主体，`PluginWsHandshake.principal()` 永不为 null，匿名连接 `userId` 为 null：

1. **Authorization 通道（非浏览器/浏览器均可）**：握手请求仍经过宿主过滤器链，Sa-Token 会话、API Key、OAuth 上下文均可解析（等价插件 HTTP 派发的主体解析）。
2. **单次票据通道（浏览器）**：浏览器原生 WebSocket 无法携带 Authorization 头。已认证用户先调 `POST /api/platform/plugin-ws-ticket`（body: `{"pluginCode","endpointPath"}`，均必填；code 限 `[A-Za-z0-9][A-Za-z0-9._-]*`，路径 ≤2048 字符且不含 `%`/`?`/`#`/空白/控制字符，按原始未解码语义比对），换取 60 秒有效、**单次**、强绑定 `userId + permissions 快照 + pluginCode + 规范化 path` 的票据，再以 `?ws_ticket=` 发起握手。票据端点刻意位于 WS 前缀之外（`/api/platform/plugin-ws-ticket`），否则会被 order=-100 守卫 404。
3. **匿名**：由插件自行判断（注册时不声明 `permission` 即可放行匿名）。

### 权限与撤销语义

- 注册时声明 `permission`（如 `plugin:{code}:connect`）→ 宿主在握手阶段判定；匿名访问受权限端点返回 401，已认证无权限返回 403。
- 票据握手时**不使用发放时的权限快照判定**：按 `userId` 实时重查权限，并与快照取**交集**——受限 OAuth/API Key 主体不会被升级为该用户全量权限；撤销/禁用即时生效。实时查询返回 null（身份不可复核，如注销）直接拒绝握手。票据残余有效期最长 60 秒，单次消费，无法通过重放延长。
- 活跃票据总量（1024）与按用户发放速率（60 秒窗口 30 张）受限；token 不进入任何日志。

## Origin 策略

- 仅按**直连请求**的 scheme/host/port 做同源比对，不读取 `X-Forwarded-*` / `Forwarded`；部署在反向代理之后时，必须在 `yudream.plugin.ws.allowed-origins` 显式追加对外 Origin（精确匹配，如 `https://example.com`）。
- Origin 缺失仅允许 Authorization 通道（非浏览器客户端）；票据与匿名通道 Origin 缺失一律 403。

## 插件可见数据

- `PluginWsHandshake.headers` 已剔除 `Authorization`、`Proxy-Authorization`、`Cookie`、`Set-Cookie`、`X-API-Key`；`query` 已剔除 `ws_ticket` 与 sa-token 会话令牌参数名（token-name，默认 `Authorization`）。headers/query 均深拷贝，`PluginWsHandshake` 不可变；建立连接时宿主还会复核插件仍启用且解析到同一 handler 实例（握手与建连之间 disable/reload 的旧会话被拒绝），发送为真非阻塞入队（共享受控 writer 串行写出，写超 deadline 由 watchdog 以 1011 强制关闭）。

## 生命周期与资源边界

- 注册随插件 disable/unload 自动移除（`clearRuntimeContributions`）；宿主监听插件生命周期事件，DISABLE/UNLOAD/RELOAD 成功与 ENABLE/RELOAD 失败时强制以 1001 关闭该插件全部会话（dev-mode RELOAD 会更换类加载器，旧会话必须关闭），并回调 `onClose`。
- 发送经有界队列（`yudream.plugin.ws.send-buffer-limit-bytes`，默认 512KB；`send-time-limit-ms` 默认 5000）：超时或缓冲超限强制关闭会话，不会无界堆积。
- 单条消息上限 `max-message-buffer-size-bytes`（默认 1MB），超出由容器以 1009 关闭；二进制帧在回调插件前做防御性拷贝。
- 插件回调抛出的异常不会外泄：宿主记录日志并按 1011 关闭会话；`close(code, reason)` 对非法关闭码（保留码 1004/1005/1006/1015、越界）回落 1011，reason 按 RFC 6455 截断到 123 字节。

## 配置

`yudream.plugin.ws.*`：`enabled`（默认 true）、`allowed-origins`（额外精确 Origin 白名单）、`send-buffer-limit-bytes`（默认 512KB）、`send-time-limit-ms`（默认 5000，writer 写出超时由 watchdog 关闭会话）、`max-message-buffer-size-bytes`（默认 1MB）。不设置全局会话空闲超时（该配置会作用于整个 ServletContext 共享的 WebSocket 容器、侵入既有平台 WS），空闲行为跟随容器默认。

## 宿主实现清单

- SPI：`spi/ws/`（`PluginWsHandler`、`PluginWsSession`、`PluginWsHandshake`、`PluginWsCloseStatus`）+ `PluginContext.registerWebSocketHandler`。
- 宿主：`interfaces/platform/plugin/ws/`（桥接处理器、握手拦截器、会话适配器、Origin 策略、票据服务与控制器、映射装配）、`infrastructure/.../PluginContextImpl`（WS 注册表）、`JarPluginRuntimeGateway.resolveWsEndpoint`、`bootstrap/.../RuntimeGatewayWsEndpointResolver`（装配层桥接）。
