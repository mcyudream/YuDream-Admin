# SPI 2.33.0 — 插件流式 HTTP 端点

SPI 2.33.0 起插件可注册"真流式" HTTP 端点：请求体与响应不经过宿主整体缓冲，
适合大文件上传下载、NDJSON 流、请求转响应回显（代理/管道）等场景。缓冲式端点
继续使用 `@PluginHttpEndpoint` / `PluginContext.registerHttpHandler`，同一路径
两者并存时流式优先。

## 注册方式

- 注解式：`@PluginStreamingHttpEndpoint(method, path, permission, wrapResult)` 标注在
  返回 `PluginHttpResponse` 的方法上，参数只支持 `PluginStreamingHttpRequest`（可追加 `PluginContext`）。
- 编程式：`PluginContext.registerStreamingHttpHandler(method, path, handler)`（default 方法，
  旧宿主上调用抛 `UnsupportedOperationException`，保持既有第三方实现类二进制兼容）。

`permission` 不在处理器内校验：宿主在任何请求体消费之前执行鉴权，
语义与 `@PluginHttpEndpoint#permission` 一致。

## 分发生命周期

宿主按以下顺序处理一次流式分发（顺序不可调换）：

1. 认证与插件端点鉴权（先于任何请求体消费）；
2. Content-Length 前置限长（`yudream.platform.plugin.http-streaming-max-body-bytes`，
   默认 10GiB，0/负数关闭限制）：超限返回 413 raw JSON，此时未读取任何请求体字节；
3. 物化 query / body / parts：multipart 由容器解析并**磁盘 spool**（临时文件）承载——
   语义上是"应用层不整体缓冲（不进内存/不做整体 byte[]），并非 socket 级流式"；
   `spring.servlet.multipart.resolve-lazily=true` 保证容器解析推迟到本步才触发；
   物化后按 part 总量再次限长，超限返回 413；
4. 执行插件处理器：`body.stream()` 返回唯一实例（重复获取返回同一流，关闭后再读抛
   `IllegalStateException`），边读边计数限长（含 `skip`），超限抛
   `PluginHttpBodySizeExceededException`（宿主映射为 413 raw JSON）；
5. 资源清理：
   - 返回**缓冲式 / SSE 响应**：请求体流与 part 资源在处理器返回后立即释放；
   - 返回**流式响应体**（body 为 `PluginHttpResponseBody`）：宿主把请求侧资源清理
     绑定到响应体关闭动作上，在响应写出结束后（含客户端断连、写出失败）统一触发。
     因此处理器返回后、宿主写出响应期间请求 body/parts 仍可读取，
     支持请求转响应回显、part 回传等"边读请求边写响应"端点。

响应写出由宿主直写 Servlet 原生输出（不经整体缓冲、不做 JSON 包装与接口加密，
与 SSE 同语义）；`contentLength() >= 0` 时下发 `Content-Length`，否则按 chunked 输出。
写出失败时：响应未提交回写 500 JSON；已提交只记录日志，绝不二次写 JSON。

part 说明：`PluginHttpStreamingPart.stream()` 基于容器磁盘 spool 可多次调用，
调用方负责 close，宿主在分发结束后兜底关闭并 `Part.delete()` 清理临时文件。

## 与缓冲式端点的契约差异

- 缓冲回退路径（同路径未注册流式端点时）完全保留旧 `@RequestBody String` 语义：
  任何空体（无 Content-Length 的 GET、content-length=0、chunked 空）body 为 `null`；
  字符集为 Content-Type charset → JSON 系 UTF-8 → 其余 ISO-8859-1。
- 接口加密开启时：请求侧解密要求全量缓冲（流式请求体在加密站点上实际为已解密的全量字节）；
  流式响应按 raw 明文输出，客户端需知晓该端点不返回加密信封。
- multipart 上传与接口加密互斥（加密过滤器不处理非 JSON 内容类型）。

## 兼容性

- `PluginContext.registerStreamingHttpHandler` 为 default 方法，未实现该契约的宿主上
  抛 `UnsupportedOperationException`；既有第三方 `PluginContext` 实现类无需改动。
- 运行时关闭顺序：插件 disable/unload 时宿主主动取消该插件全部进行中的流式分发
  （关闭请求体流唤醒挂起读取、关闭在途流式响应体），随后才释放 ClassLoader；
  注册随插件 disable/unload 自动移除，无需手工注销。

## 验证

- 契约单元/端到端（真 JAR 插件）：
  `mvn -pl yudream-infrastructure -am -Dtest=PluginStreamingHttpDispatchTest test`
  — 21 个用例（鉴权先于物化、前置/边读/partTotal 413、0 关闭限制、请求转响应回显与
  part 回传延迟关闭、cancel-before-attach、disable 主动取消、buffered/streaming 跨模式
  重复注册拒绝等）全部通过。
- Web 层支撑（缓冲字符集/空 body null/缓存包装剥离/part dispose/写出失败语义）：
  `mvn -pl yudream-interfaces -am -Dtest=PluginStreamingHttpWebSupportTest test`
  — 13 个用例全部通过。
- 上述测试为宿主内单元/组件级验证（Mock servlet 资源 + 真 JAR 插件网关），非真实浏览器/插件客户端端到端。
