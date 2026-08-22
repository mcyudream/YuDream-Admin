# 语义记忆端口：向量检索

`context.semanticMemory()` 提供**语义记忆（向量检索）**能力：插件可以把文本切片写入向量索引，再按语义相似度检索。底层复用平台的 AI provider 嵌入模型。

> 源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/memory/`

获取方式：`PluginContext.semanticMemory()`——这是插件作用域端口，直接挂在插件上下文上（区别于经 `context.framework().xxx()` 获取的框架级服务）。

---

## PluginSemanticMemoryService

接口源码（`PluginSemanticMemoryService.java`）：

```java
public interface PluginSemanticMemoryService {

    PluginSemanticMemoryStatus status();

    CompletionStage<Void> index(PluginSemanticMemoryRecord record);

    CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query);

    CompletionStage<Void> delete(String namespace, String id);
}
```

| 方法 | 索引 | 说明 |
|---|---|---|
| status | `PluginSemanticMemoryStatus status()` | 同步查询能力可用性与可选嵌入模型清单 |
| index | `CompletionStage<Void> index(PluginSemanticMemoryRecord record)` | 写入/更新一条记忆（按 namespace+id 幂等） |
| search | `CompletionStage<List<PluginSemanticMemoryHit>> search(PluginSemanticMemoryQuery query)` | 语义检索 |
| delete | `CompletionStage<Void> delete(String namespace, String id)` | 删除一条记忆 |

除 `status()` 外全部异步方法返回 `CompletionStage`，失败体现为 failed stage，务必链式 `exceptionally(...)` 处理。

## 使用前置：status 检查

平台 AI 能力未配置嵌入模型时记忆服务不可用，**写入前应先检查**：

```java
PluginSemanticMemoryStatus st = context.semanticMemory().status();
if (!st.available()) {
    // st.message() 说明原因（如"未配置嵌入模型"）
    return;
}
// st.models() 为 PluginSemanticMemoryModelOption 列表，可让用户选择嵌入模型
```

## DTO 源码与字段表

### PluginSemanticMemoryRecord

源码（`PluginSemanticMemoryRecord.java`）：

```java
public record PluginSemanticMemoryRecord(String namespace, String id, String content,
                                         String providerCode, String modelCode,
                                         Map<String, Object> metadata) {
    public PluginSemanticMemoryRecord {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `namespace` | `String` | 命名空间，建议用 `pluginCode:业务域`（如 `wallet:faq`），实现逻辑隔离 |
| `id` | `String` | 记录唯一 ID（插件自行生成）。**注意是 `String` 而非 `Long`**；若业务主键是雪花 ID，请以字符串形式传入 |
| `content` | `String` | 被索引的文本 |
| `providerCode` | `String` | 嵌入模型 provider |
| `modelCode` | `String` | 嵌入模型 |
| `metadata` | `Map<String,Object>` | 任意元数据，检索时随命中返回；`null` 入参被紧凑构造器规整为空 Map（不可变副本） |

### PluginSemanticMemoryQuery

源码（`PluginSemanticMemoryQuery.java`）：

```java
public record PluginSemanticMemoryQuery(String namespace, String text, String providerCode,
                                        String modelCode, int limit) {
    public PluginSemanticMemoryQuery {
        limit = Math.clamp(limit, 1, 30);
    }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `namespace` | `String` | 检索的命名空间 |
| `text` | `String` | 查询文本 |
| `providerCode` / `modelCode` | `String` | 必须与写入时一致（同一向量空间） |
| `limit` | `int` | 返回条数，**紧凑构造器钳制在 1–30**（`Math.clamp(limit, 1, 30)`）：超过 30 取 30，≤0 取 1 |

### PluginSemanticMemoryHit / Status / ModelOption

源码（`PluginSemanticMemoryHit.java` / `PluginSemanticMemoryStatus.java` / `PluginSemanticMemoryModelOption.java`）：

| 类型 | 字段 |
|---|---|
| `PluginSemanticMemoryHit` | `id, content, score(double 相似度), metadata`；`metadata` 为 `null` 时规整为不可变空 Map |
| `PluginSemanticMemoryStatus` | `available(boolean), message, models(List<PluginSemanticMemoryModelOption>)`；`message` 为 `null` 规整为空串、`models` 为 `null` 规整为空 List；静态工厂 `unavailable(String)` 快速构造不可用状态 |
| `PluginSemanticMemoryModelOption` | `providerCode, providerName, modelCode, modelName` |

---

## 完整示例：FAQ 语义检索

下面是一个把"写入 + 检索 + 删除"串起来的完整插件片段，可直接对照实现：

```java
public class FaqMemoryService {

    private static final String NAMESPACE   = "myplugin:faq";
    private static final String PROVIDER    = "openai";
    private static final String MODEL       = "text-embedding-3-small";

    private final PluginContext context;

    public FaqMemoryService(PluginContext context) {
        this.context = context;
    }

    /** 启动时批量写入 FAQ（相同 namespace+id 为更新语义，可重复执行做幂等刷新）。 */
    public void seed() {
        PluginSemanticMemoryService memory = context.semanticMemory();

        // 前置检查：未配置嵌入模型时直接放弃，不要发起必然失败的调用
        PluginSemanticMemoryStatus st = memory.status();
        if (!st.available()) {
            return;
        }
        // 可选：确认期望的嵌入模型在可用清单里
        boolean modelReady = st.models().stream()
                .anyMatch(m -> PROVIDER.equals(m.providerCode()) && MODEL.equals(m.modelCode()));
        if (!modelReady) {
            return;
        }

        memory.index(new PluginSemanticMemoryRecord(
                NAMESPACE, "faq-001",
                "如何绑定 QQ？在个人设置页点击绑定按钮获取一次性绑定码。",
                PROVIDER, MODEL,
                Map.of("category", "account")))
            .join();   // 初始化场景可同步等待；请求线程内不要这样做

        memory.index(new PluginSemanticMemoryRecord(
                NAMESPACE, "faq-002",
                "忘记密码怎么办？在登录页点击找回密码，通过绑定邮箱重置。",
                PROVIDER, MODEL,
                Map.of("category", "account")))
            .join();
    }

    /** 命令/AI 工具中按自然语言检索 FAQ，返回拼接后的答案。 */
    public CompletionStage<String> answer(String question) {
        return context.semanticMemory()
            .search(new PluginSemanticMemoryQuery(NAMESPACE, question, PROVIDER, MODEL, 5))
            .thenApply(hits -> {
                if (hits.isEmpty()) {
                    return "暂无相关帮助内容。";
                }
                StringBuilder sb = new StringBuilder();
                for (PluginSemanticMemoryHit hit : hits) {
                    sb.append("- ").append(hit.content()).append('\n');
                    // hit.score() 为相似度，可按阈值过滤；hit.metadata() 取回写入时的元数据
                }
                return sb.toString();
            })
            .exceptionally(ex -> "记忆检索失败，请稍后再试。");
    }

    /** FAQ 下线时删除对应记忆，避免残留脏数据。 */
    public void removeFaq(String faqId) {
        context.semanticMemory().delete(NAMESPACE, faqId);
    }
}
```

最小用法（等价于上面的检索路径）：

```java
// 写入
context.semanticMemory().index(new PluginSemanticMemoryRecord(
        "wallet:faq", "faq-001",
        "如何绑定 QQ？在个人设置页点击绑定按钮获取一次性绑定码。",
        "openai", "text-embedding-3-small",
        Map.of("category", "account")))
    .join();

// 检索
List<PluginSemanticMemoryHit> hits = context.semanticMemory().search(
        new PluginSemanticMemoryQuery("wallet:faq", "怎么绑定qq",
                "openai", "text-embedding-3-small", 5))
    .join();
```

---

## 注意事项

- **provider/model 必须与写入时相同**，否则不在同一向量空间，检索结果无意义。
- `namespace` 是隔离边界：不同插件、不同业务域不要混用同一 namespace。
- 相同 `namespace+id` 重复 index 为更新语义；删除数据时记得调用 `delete`，避免残留脏记忆。
- `limit` 传入超过 30 的值会被钳制为 30，传入 ≤0 的值会被钳制为 1。
- **记录 ID 是 `String`**：`index` / `search` / `delete` 全程使用字符串 ID，没有 `Long` 重载。雪花 ID 场景请以字符串形式持有（与前端/JSON 侧的 string 约定天然一致）。
- **异步语义**：`index` / `search` / `delete` 返回 `CompletionStage`；初始化批量灌数据可以在启动路径 `join()`，但 HTTP 请求线程内请走回调或设超时，避免占满工作线程。

---

> 源码引用：`.../plugin/spi/system/memory/` 下 `PluginSemanticMemoryService.java`、`PluginSemanticMemoryRecord.java`、`PluginSemanticMemoryQuery.java`、`PluginSemanticMemoryHit.java`、`PluginSemanticMemoryStatus.java`、`PluginSemanticMemoryModelOption.java`
