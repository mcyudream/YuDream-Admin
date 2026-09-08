# 邮件与文档渲染（Mail / Word 模板 / 图片渲染）

> SPI v1 · 当前源码 2.24.0 · 包 `online.yudream.base.plugin.spi.system.mail` / `system.document` / `system.render`

本页覆盖宿主暴露给插件的四组"输出型"能力端口：

| 端口 | 获取方式 | 用途 |
|---|---|---|
| `PluginMailService` | `context.framework().mail()` | 发送纯文本 / HTML 邮件 |
| `PluginWordTemplateService` | `context.framework().wordTemplates()` | DOCX 模板填充渲染（按内容字节或按平台模板 ID） |
| `PluginRenderService` | `context.framework().render()` | HTML / Markdown / URL 渲染成图片（异步） |
| `PluginTemplateRenderService` | `context.templateRenderer()` | 插件 JAR 内 Thymeleaf 模板渲染成图片（插件作用域） |

整体调用链：

```mermaid
flowchart LR
    subgraph 插件 JAR
        P[插件代码]
        T[templates/*.html<br>插件模板资源]
    end
    subgraph 宿主 SPI 端口
        M[PluginMailService]
        W[PluginWordTemplateService]
        R[PluginRenderService]
        TR[PluginTemplateRenderService]
    end
    subgraph 宿主实现
        MS[MailSender 领域端口]
        DOC[DocxWordTemplateRenderer<br>Apache POI XWPF]
        GW[HttpMessageRenderGateway<br>POST /v1/render/{html\|markdown\|url}]
    end
    RS[(yudream-render-server<br>无头浏览器截图)]
    SMTP[(SMTP 服务)]

    P --> M --> MS --> SMTP
    P --> W --> DOC
    P --> R --> GW --> RS
    P --> TR -->|Thymeleaf 渲染为 HTML| R
    T --> TR
```

---

## 一、邮件发送 PluginMailService

源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail/PluginMailService.java`

接口只有一个方法：

```java
public interface PluginMailService {
    void send(PluginMailMessage message);
}
```

### PluginMailMessage 字段

源码：`.../spi/system/mail/PluginMailMessage.java`，是一个 record：

```java
public record PluginMailMessage(
        String from,
        List<String> to,
        List<String> cc,
        List<String> bcc,
        String subject,
        String text,
        String html
) { ... }
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `from` | `String` | 发件人地址；传 `null` 时使用宿主邮件配置的默认发件人 |
| `to` | `List<String>` | 收件人列表；`null` 会被压缩构造器规整为空 List（`List.copyOf` 不可变） |
| `cc` | `List<String>` | 抄送；同上，`null` → 空 List |
| `bcc` | `List<String>` | 密送；同上，`null` → 空 List |
| `subject` | `String` | 主题，**必填** |
| `text` | `String` | 纯文本正文；与 `html` 二选一 |
| `html` | `String` | HTML 正文；与 `text` 二选一 |

### 静态工厂方法

```java
// 纯文本邮件：from=null、cc/bcc=空、html=null
public static PluginMailMessage text(List<String> to, String subject, String text);

// HTML 邮件：from=null、cc/bcc=空、text=null
public static PluginMailMessage html(List<String> to, String subject, String html);
```

两个工厂都只覆盖最常见场景（无抄送、默认发件人）。需要 cc/bcc 或自定义发件人时直接用 record 构造器：

```java
PluginMailMessage message = new PluginMailMessage(
        "noreply@example.com",
        List.of("a@example.com", "b@example.com"),
        List.of("cc@example.com"),
        List.of(),
        "活动审核结果",
        null,
        "<h1>审核通过</h1><p>你的活动已通过审核。</p>"
);
context.framework().mail().send(message);
```

### 宿主行为（PluginMailFrameworkService）

实现：`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginMailFrameworkService.java`

`send(...)` 在投递前做两项校验，不满足直接抛 `IllegalArgumentException`：

- `message == null` 或 `message.to().isEmpty()` → `"邮件收件人不能为空"`
- `message.subject()` 无文本 → `"邮件主题不能为空"`

校验通过后透传到领域端口 `MailSender.send(MailMessage)`，字段一一映射（from/to/cc/bcc/subject/text/html），由宿主邮件基础设施实际投递。

---

## 二、Word 模板渲染 PluginWordTemplateService

源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/document/PluginWordTemplateService.java`

```java
public interface PluginWordTemplateService {

    default boolean enabled() { return true; }

    default List<PluginWordTemplateSummary> templates(String keyword, int page, int size) { return List.of(); }

    default Optional<PluginWordTemplateSummary> template(Long id) { return Optional.empty(); }

    default Optional<PluginWordTemplateSummary> templateByCode(String code) { return Optional.empty(); }

    PluginRenderedDocument render(byte[] templateContent, Map<String, Object> data);

    default PluginRenderedDocument render(Long templateId, Map<String, Object> data) {
        throw new IllegalArgumentException("Word 模板渲染能力不支持按模板 ID 渲染");
    }
}
```

### 方法一览

| 方法 | 签名 | 默认实现行为 | 宿主行为 |
|---|---|---|---|
| `enabled` | `default boolean enabled()` | 恒 `true` | 返回能力 `document-template` 的持久化启用状态 |
| `templates` | `default List<PluginWordTemplateSummary> templates(String keyword, int page, int size)` | 空 List | 分页搜索平台模板库中 **已启用（ACTIVE）** 的模板 |
| `template` | `default Optional<PluginWordTemplateSummary> template(Long id)` | `Optional.empty()` | 按 ID 查模板摘要（仅 ACTIVE；`id == null` 直接返回 empty） |
| `templateByCode` | `default Optional<PluginWordTemplateSummary> templateByCode(String code)` | `Optional.empty()` | 按 code 查模板摘要（空白 code 直接返回 empty） |
| `render`（按内容） | `PluginRenderedDocument render(byte[] templateContent, Map<String, Object> data)` | **抽象方法，必须实现** | 直接用传入的 DOCX 字节渲染 |
| `render`（按模板 ID） | `default PluginRenderedDocument render(Long templateId, Map<String, Object> data)` | 抛 `IllegalArgumentException("Word 模板渲染能力不支持按模板 ID 渲染")` | 从平台模板库取模板文件再渲染 |

### 两种 render 的实现差异

- **按内容渲染 `render(byte[], Map)`**：SPI 契约中唯一的抽象方法。宿主实现（`DefaultFrameworkServices.renderWordTemplate(byte[], ...)`）把字节数组包装为 `ByteArrayInputStream` 交给 `WordTemplateRenderer`，与平台模板库完全解耦——插件自己携带 `.docx` 模板文件时用这个方法。`templateContent` 为 `null` 或空数组时抛 `IllegalArgumentException("Word 模板内容不能为空")`。
- **按模板 ID 渲染 `render(Long, Map)`**：SPI 默认直接拒绝（抛 `IllegalArgumentException`），因为"平台模板库"不是 SPI 能假设的能力。宿主实现会依次校验：能力已启用 → 模板存在且 ACTIVE（否则 `"Word 模板不存在或已停用"`）→ 模板文件存在（否则 `"Word 模板文件不存在"`）→ 从对象存储读取模板字节流渲染；渲染过程异常包装为 `IllegalArgumentException("Word 模板渲染失败：...")`。

两种路径最终都走同一个 `WordTemplateRenderer` 领域端口。

### 能力闸门

宿主端所有方法（除 `enabled()` 本身外）都会先过能力闸门：

```java
// DefaultFrameworkServices.ensureWordTemplateEnabled()
if (!wordTemplateEnabled()) {
    throw new IllegalArgumentException("Word 模板能力未启用，请先在能力管理中启用 document-template");
}
```

能力 code 为 **`document-template`**（持久化在能力模块表中，未配置视为未启用）。**插件侧正确姿势是先判断再调用**：

```java
PluginWordTemplateService wordTemplates = context.framework().wordTemplates();
if (!wordTemplates.enabled()) {
    // 降级：不渲染，或提示管理员去能力管理启用 document-template
    return;
}
```

### 渲染引擎与模板语法

宿主渲染实现是 `DocxWordTemplateRenderer`（`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/document/service/DocxWordTemplateRenderer.java`），基于 Apache POI XWPF，输入必须是 `.docx`。模板占位符语法：

| 语法 | 说明 |
|---|---|
| <code v-pre>&#123;&#123;name&#125;&#125;</code> / `${name}` | 变量替换，支持点路径（如 <code v-pre>&#123;&#123;user.nickname&#125;&#125;</code>）；值来自 `Map` 键或 JavaBean 的 `key()` / `getKey()` 无参方法；解析不到时替换为空串 |
| <code v-pre>&#123;&#123;#items&#125;&#125;...&#123;&#123;/items&#125;&#125;</code> | 集合循环：行内循环展开文本；写在**表格行**里时按行复制模板行逐条渲染（循环标记独占一行的标记行会被删除） |
| 循环作用域 | 每次迭代向数据 Map 注入 `this`、`item`，若元素是 Map 则把其键直接并入作用域 |
| `participantTableAppend` | 特殊布尔键：为 true 且存在 `participants` 集合时，向"姓名/学号"表头的表格自动追加参与者行（活动证明类模板用） |

渲染范围覆盖正文、表格（含单元格内嵌套表格）、页眉、页脚。`data` 传 `null` 按空 Map 处理。产物 contentType 固定为 `application/vnd.openxmlformats-officedocument.wordprocessingml.document`。

### DTO 字段

`PluginWordTemplateSummary`（源码 `.../spi/system/document/PluginWordTemplateSummary.java`）：

```java
public record PluginWordTemplateSummary(
        Long id,
        String code,
        String name,
        String originalFilename,
        long updatedAt
) {
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `Long` | 模板 ID（雪花 ID）。**进入 JSON / URL / 表单时一律序列化为 string，禁止 `Number(id)`** |
| `code` | `String` | 模板编码，业务唯一 |
| `name` | `String` | 模板名称 |
| `originalFilename` | `String` | 原始上传文件名 |
| `updatedAt` | `long` | 更新时间，**epoch 毫秒**（无更新时间时取创建时间；都没有则为 0） |

`PluginRenderedDocument`（源码 `.../spi/system/document/PluginRenderedDocument.java`）：

```java
public record PluginRenderedDocument(
        byte[] content,
        String contentType
) {
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `content` | `byte[]` | 渲染产物字节 |
| `contentType` | `String` | MIME 类型，DOCX 场景固定为 `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |

### 完整示例

```java
// 1) 按平台模板 code 找到模板，再按 ID 渲染
PluginWordTemplateService wordTemplates = context.framework().wordTemplates();
Optional<PluginWordTemplateSummary> template = wordTemplates.templateByCode("activity-proof");
if (template.isEmpty()) {
    throw new IllegalStateException("平台未配置活动证明模板");
}
// 注意：templateId 是 Long；若从 URL/表单接收应为 string 再 Long.valueOf(...)
PluginRenderedDocument doc = wordTemplates.render(template.get().id(), Map.of(
        "activityName", "国庆庆典",
        "organizer", "校团委",
        "participants", List.of(
                Map.of("name", "张三", "className", "软件 2301", "studentNo", "20230001")
        )
));

// 2) 插件自带模板字节渲染（不依赖平台模板库）
byte[] templateBytes = readPluginResource("docs/notice-template.docx");
PluginRenderedDocument doc2 = wordTemplates.render(templateBytes, Map.of("title", "系统维护通知"));

// 产物可直接经插件 HTTP 端点下载：contentType 用 doc.contentType()，文件名自取
```

---

## 三、图片渲染 PluginRenderService

源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/render/PluginRenderService.java`

```java
public interface PluginRenderService {
    CompletionStage<PluginRenderedImage> html(String html);
    default CompletionStage<PluginRenderedImage> html(String html, String selector) {
        return html(html);
    }
    CompletionStage<PluginRenderedImage> markdown(String markdown);
    CompletionStage<PluginRenderedImage> url(String url);
}
```

| 方法 | 说明 |
|---|---|
| `html(String html)` | 渲染整段 HTML 为图片 |
| `html(String html, String selector)` | 渲染 HTML 后按 CSS 选择器**截取单个元素**；SPI 默认实现退化为整页渲染（忽略 selector），宿主实现已覆写为真正的元素截图 |
| `markdown(String markdown)` | Markdown 转 HTML 后渲染为图片 |
| `url(String url)` | 抓取指定 URL 页面渲染为图片 |

### 异步模型

四个方法全部返回 `CompletionStage<PluginRenderedImage>`，渲染在宿主专用虚拟线程池（核心 2 / 最大 4，队列 100，线程名前缀 `plugin-render-`）中执行，不阻塞插件调用线程。拒绝或执行失败时返回 failed stage（`CompletableFuture.failedFuture`），异常只记日志（不泄露内部错误详情）。插件侧典型用法：

```java
context.framework().render()
        .markdown("# 每日战报\n- 在线 42 人")
        .thenAccept(image -> {
            // image.content() 为图片字节，可经 messaging 发出或存 files()
        })
        .exceptionally(ex -> {
            // 渲染失败降级（如改发纯文本）
            return null;
        });
```

### 宿主实现要点

实现：`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/PluginRenderFrameworkService.java` → `HttpMessageRenderGateway.java`

- 底层是独立渲染服务 `yudream-render-server`（无头浏览器），HTTP 端点 `POST /v1/render/{html|markdown|url}`；`selector` 通过 options 传递，仅对 HTML 源生效。
- 整个调用受平台能力 **`message-render`** 闸门控制（应用层 `ensureEnabled("message-render", "消息渲染")`），能力未启用时渲染直接失败——插件需要把渲染结果作为可选增强而非硬依赖。
- 特殊行为：`html(html)`（无 selector 版本）在 HTML 内容包含 `command-menu-card` 时，宿主自动使用 `#command-menu-card` 作为选择器截取该元素。
- 返回的 `contentType` 取渲染服务响应头（通常为 `image/png`），`width/height` 由宿主解析图片实际像素得出。

### PluginRenderedImage 字段

源码：`.../spi/system/render/PluginRenderedImage.java`

```java
public record PluginRenderedImage(String contentType, byte[] content, int width, int height) {
    public PluginRenderedImage {
        content = content == null ? new byte[0] : content.clone();
    }
    @Override
    public byte[] content() { return content.clone(); }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `contentType` | `String` | 图片 MIME（通常 `image/png`） |
| `content` | `byte[]` | 图片字节；构造与 getter 均做防御性 clone，`null` 入参规整为空数组 |
| `width` | `int` | 图片宽度（像素） |
| `height` | `int` | 图片高度（像素） |

---

## 四、插件模板渲染 PluginTemplateRenderService

源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/render/PluginTemplateRenderService.java`

```java
public interface PluginTemplateRenderService {

    default CompletionStage<PluginRenderedImage> render(String templateName, Map<String, Object> variables) {
        return render(templateName, variables, null);
    }

    CompletionStage<PluginRenderedImage> render(String templateName, Map<String, Object> variables, String selector);
}
```

与 `PluginRenderService.html(...)` 的区别：**模板来自插件 JAR 自身**，经 Thymeleaf 渲染成 HTML 后再交给 `PluginRenderService` 出图。这是 `PluginContext` 上的插件作用域端口（`context.templateRenderer()`），宿主在创建插件上下文时按插件 ClassLoader 逐个实例化（`PluginContextImpl` 第 74 行）。

### 模板目录约定

- 模板必须放在插件 JAR 的 `src/main/resources/templates/` 目录下。
- `templateName` 是**逻辑名**：不带 `templates/` 前缀、不带 `.html` 后缀，支持 `/` 分层（如 `cards/player-card` 对应 `templates/cards/player-card.html`）。
- 名称合法性：只允许 `[A-Za-z0-9/_-]+`；空名、以 `/` 开头或结尾、含 `..`、含 `\` 一律抛 `IllegalArgumentException`（`"插件模板名称不能为空"` / `"插件模板名称无效: ..."`）。
- 运行时通过**插件自己的 ClassLoader** 以 `findResource` 查找（模板解析器被包装为只读插件资源，不会回落到宿主或其他插件）；资源不存在抛 `IllegalArgumentException("插件模板不存在: templates/<name>.html")`。
- `variables` 传 `null` 按空 Map 处理；模板引擎缓存开启（`cacheable=true`）。
- `selector` 为 `null` 或空白时整页截图，否则截取该元素（透传给 `PluginRenderService.html(html, selector)`）。

### 完整实例：sample-plugin 的 sample-card

样例插件自带模板 `yudream-plugins/yudream-sample-plugin/src/main/resources/templates/sample-card.html`：

```html
<!doctype html>
<html lang="zh-CN" xmlns:th="http://www.thymeleaf.org">
<head>
  <meta charset="utf-8">
  <style>
    * { box-sizing: border-box; }
    body { display: inline-block; margin: 0; background: #f3f4f6; font-family: Arial, "Microsoft YaHei", sans-serif; }
    #sample-card { width: 420px; padding: 24px; border: 16px solid #f3f4f6; background: #fff; color: #182230; }
    h1 { margin: 0 0 8px; color: #009688; font-size: 22px; }
    p { margin: 0; color: #667085; font-size: 14px; }
  </style>
</head>
<body>
  <main id="sample-card">
    <h1 th:text="${title}">插件模板</h1>
    <p th:text="${content}">模板内容来自插件 JAR。</p>
  </main>
</body>
</html>
```

插件代码中渲染（变量 `title` / `content` 对应模板中的 `th:text`）：

```java
CompletionStage<PluginRenderedImage> image = context.templateRenderer().render(
        "sample-card",                       // 逻辑名 → templates/sample-card.html
        Map.of(
                "title", "服务器状态",
                "content", "在线 42 人，TPS 20.0"
        ),
        "#sample-card"                       // 只截取卡片元素；传 null 则整页截图
);
```

注意模板里 `body { display: inline-block; }` 与卡片外层 `border: 16px solid #f3f4f6` 的写法：前者让页面宽度收缩到内容，后者给截图留边距——配合 `#sample-card` 元素截图可以得到边缘干净的卡片图。

```mermaid
sequenceDiagram
    participant P as 插件代码
    participant TR as PluginTemplateRenderService
    participant CL as 插件 ClassLoader
    participant TE as Thymeleaf Engine
    participant R as PluginRenderService
    participant RS as render-server

    P->>TR: render("sample-card", vars, "#sample-card")
    TR->>TR: normalizeTemplateName（校验合法性）
    TR->>CL: findResource("templates/sample-card.html")
    CL-->>TR: URL（不存在则抛 IllegalArgumentException）
    TR->>TE: process(name, Context(vars))
    TE-->>TR: HTML 字符串
    TR->>R: html(html, selector)
    R->>RS: POST /v1/render/html
    RS-->>R: 图片字节 + contentType
    R-->>P: CompletionStage&lt;PluginRenderedImage&gt;
```

---

## 五、注意事项

- **能力闸门**：Word 模板依赖能力 `document-template`，图片渲染依赖能力 `message-render`，两者都可能在管理后台被关闭。插件必须先 `enabled()` 检查（Word）或做好 `exceptionally` 降级（渲染），不要把产物当作必然可用。
- **SPI 默认实现 ≠ 宿主行为**：`PluginWordTemplateService` 除 `render(byte[], Map)` 外全是 default 方法（空 List / empty Optional / 抛异常），`PluginRenderService.html(html, selector)` 默认忽略 selector。这些默认值只保证契约可编译演进，**真实行为以宿主实现为准**——本文"宿主行为"列描述的就是当前宿主实现。
- **Long ID 序列化**：`PluginWordTemplateSummary.id`、`template(Long id)` 入参均为雪花 `Long`。凡进入 JSON、URL 参数、表单的一律用 string 承载，插件内用 `Long.valueOf(str)` 解析，禁止 `Number(id)` 截断。
- **异常语义**：参数与状态问题统一抛 `IllegalArgumentException`（含中文消息，可直接用于错误提示）；渲染执行异常走 failed `CompletionStage`。
- **线程模型**：图片渲染是异步虚拟线程执行，不要在插件 HTTP 请求线程里 `join()` 死等，避免占满 Tomcat 工作线程；需要同步返回的场景也应设置超时。
- **模板隔离**：`PluginTemplateRenderService` 只读当前插件 JAR 的 `templates/` 目录，不能跨插件引用模板；公共卡片样式请复制模板或通过 provider 插件的稳定 `*.api` 包共享渲染服务。

## 源码引用

- SPI 契约：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/mail/`（`PluginMailService.java`、`PluginMailMessage.java`）
- SPI 契约：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/document/`（`PluginWordTemplateService.java`、`PluginWordTemplateSummary.java`、`PluginRenderedDocument.java`）
- SPI 契约：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/render/`（`PluginRenderService.java`、`PluginTemplateRenderService.java`、`PluginRenderedImage.java`）
- 宿主实现：`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/`（`PluginMailFrameworkService.java`、`PluginRenderFrameworkService.java`、`PluginTemplateRenderFrameworkService.java`、`DefaultFrameworkServices.java`）
- 渲染链路：`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/document/service/DocxWordTemplateRenderer.java`、`yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/render/service/HttpMessageRenderGateway.java`、`yudream-application/src/main/java/online/yudream/base/application/platform/render/service/MessageRenderAppService.java`
- 示例模板：`yudream-plugins/yudream-sample-plugin/src/main/resources/templates/sample-card.html`
