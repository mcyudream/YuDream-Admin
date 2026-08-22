# SPI v1 总览

`yudream-plugin-spi` v1 是插件与宿主之间的**唯一编译期契约**。本目录按端口拆分文档，每个端口一篇，含逐方法签名、字段表与示例。

- Maven 坐标：`online.yudream.base:yudream-plugin-spi`
- 版本：见 [SPI 版本说明](/plugin/spi/)（版本清单以源码 `pom.xml` 为准）
- 包根：`online.yudream.base.plugin.spi`

## 包结构

```text
online.yudream.base.plugin.spi
├── core                  插件生命周期与上下文（YuDreamPlugin / PluginContext / PluginDescriptor）
├── system
│   ├── FrameworkServices 宿主能力门面（users/ai/messaging/render/...）
│   ├── user              用户、部门、角色、QQ 绑定
│   ├── security          权限校验（PluginSecurityService / PluginPrincipal）
│   ├── storage           文档存储 / 文件存储
│   ├── secret            密钥存储
│   ├── mail              邮件发送
│   ├── document          Word 文档模板渲染
│   ├── render            HTML/Markdown 渲染成图 + Thymeleaf 模板截图
│   ├── ai                AI 对话 / Agent / 自定义工具
│   ├── messaging         消息收发与交互注册（Milky/QQ）
│   ├── command           命令注册
│   └── memory            语义记忆（向量检索）
├── http                  插件 HTTP 端点（Request/Response/SSE）
├── annotation            10 个声明式注解
└── menu / permission / capability / dashboard / frontend   声明式注册 record
```

## 端口索引

| 文档 | 内容 | 获取方式 |
|---|---|---|
| [core](/plugin/spi/v1/core) | YuDreamPlugin 生命周期、PluginContext 全量方法 | 入口类参数注入 |
| [framework-services](/plugin/spi/v1/framework-services) | FrameworkServices 门面 15 个入口 | `context.framework()` |
| [user](/plugin/spi/v1/user) | 用户查询/创建、部门角色、QQ 绑定 | `framework().users()` 等 |
| [security](/plugin/spi/v1/security) | 权限校验、Principal、权限码约定 | `framework().security()` |
| [storage](/plugin/spi/v1/storage) | 文档/文件/密钥三类作用域存储 | `context.documents()/files()/secrets()` |
| [mail](/plugin/spi/v1/mail) | 邮件发送（文本/HTML/抄送密送） | `framework().mail()` |
| [document-render](/plugin/spi/v1/document-render) | Word 模板、渲染成图、Thymeleaf | `framework().wordTemplates()` 等 |
| [ai](/plugin/spi/v1/ai) | AI 对话、Agent 调用、自定义 AI 工具 | `framework().ai()`、`registerAiTool` |
| [messaging](/plugin/spi/v1/messaging) | 消息发送、事件订阅、按钮/命令回调 | `framework().messaging()`、`context.interactions()` |
| [command](/plugin/spi/v1/command) | 命令注册与分发 | `context.commands()` |
| [memory](/plugin/spi/v1/memory) | 语义记忆向量检索 | `context.semanticMemory()` |
| [http](/plugin/spi/v1/http) | HTTP 端点挂载、请求响应模型、SSE | `registerHttpHandler/registerHttpController` |
| [annotations](/plugin/spi/v1/annotations) | 10 个声明式注解详解 | 标注在插件类/方法上 |
| [registry-items](/plugin/spi/v1/registry-items) | 注册条目 record 字段速查 | 作为 registerXxx 参数 |

## 统一约定

1. **长 ID 一律 string**：所有 DTO 中暴露给 JSON 的 `Long` ID 在传输层序列化为字符串，插件前端禁止 `Number(id)`。
2. **注册即回收**：所有 `registerXxx` 与事件订阅返回的 `AutoCloseable` 在插件 disable/unload 时由宿主统一回收。
3. **能力不可用即抛错**：`FrameworkServices` 上未启用的能力入口调用时抛"不可用"异常——依赖平台能力的插件应在 `plugin.yml` 声明软依赖并在代码中降级。
4. **异步返回 CompletionStage**：AI、消息、渲染等 IO 型端口均为异步接口。

## 下一步

- 第一次写插件 → [从零创建插件](/plugin/getting-started)
- 了解宿主如何适配这些端口 → [系统架构](/guide/architecture)
