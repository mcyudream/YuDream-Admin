# 存储端口：文档 / 文件 / 密钥

SPI 为插件提供了三类**按 `pluginCode` 作用域隔离**的存储端口，全部通过 `PluginContext` 的便捷方法获取：

```java
PluginDocumentStore documents = context.documents(); // 文档存储
PluginFileStore files = context.files();             // 二进制文件存储
PluginSecretStore secrets = context.secrets();       // 密钥存储
```

也可以通过 `FrameworkServices` 显式传入插件编码：`framework().documents(pluginCode)` 等——两者等价，便捷方法内部就是以自身 `pluginCode()` 调用。

> 源码：`yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/storage/`、`.../system/secret/`

---

## PluginDocumentStore —— 结构化文档存储

面向"插件自己的业务数据"的键值 + 集合式文档存储（宿主基于 MongoDB 实现）。插件无需建表，直接按集合（collection）读写 `Map<String,Object>` 形式的文档。

### 方法一览

| 方法 | 签名 | 返回 | 说明 |
|---|---|---|---|
| save | `Map<String,Object> save(String collection, String id, Map document)` | 保存后的文档 | 写入或整体覆盖一个文档；`id` 由插件自己生成与管理 |
| findById | `Optional<Map> findById(String collection, String id)` | 可空文档 | 按主键查询 |
| findAll | `List<Map> findAll(String collection, int page, int size)` | 分页列表 | 全集合分页遍历 |
| findByField | `List<Map> findByField(String collection, String field, Object value, int page, int size)` | 分页列表 | 按字段等值查询 |
| count | `long count(String collection)` | 数量 | 集合文档总数 |
| updateIfFieldAtMost | `boolean updateIfFieldAtMost(String collection, String id, String field, long maximum, Map document)`（default） | 是否更新成功 | **原子条件更新**：仅当文档中 `field` 的当前值 ≤ `maximum` 时才写入 `document` 并返回 `true`，否则返回 `false` 且不做任何修改。注意 `document` 中若带 `id` 键会被忽略，主键始终保持为目标 `id` |
| delete | `void delete(String collection, String id)` | — | 删除文档 |

### 宿主实现规则（MongoPluginDocumentStore）

参数与文档的合法性由宿主统一校验，违规抛 `BizException`（中文文案）：

- **集合名**：匹配 `[A-Za-z0-9][A-Za-z0-9_-]{0,63}`，实际落库集合名为 `plugin_{插件code}__{集合名}`（`-` 统一替换为 `_`），天然按插件隔离。
- **文档 ID**：不能为空白，否则抛 `"文档 ID 不能为空"`。
- **字段名**：顶层与嵌套字段均不得以 `$` 开头、不得含 `.`，且不得使用保留字段 `_id` / `pluginCode`（违规抛 `"插件文档字段不合法：..."` / `"插件文档字段为系统保留字段：..."`）。查询字段另须匹配 `[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}` 且不含 `..`。
- **save 的自动字段**：写入时自动维护 `id`（等于入参 `id`）与 `pluginCode`；读取时 `_id` 与 `pluginCode` 被剥离，返回的 Map 保证含 `id`。
- **分页**：`page` 从 **1** 开始（小于 1 钳制为 1），`size` 钳制到 `[1, 200]`；列表结果按 `_id` 升序稳定排序。

### updateIfFieldAtMost 的典型场景

这是一个**原子计数闸门**，适合做限额、库存、投票上限等并发敏感逻辑：

```java
// 每个用户每天最多领取 5 次：dayCount 为当前已领次数
Map<String, Object> patch = Map.of(
        "userId", userId,
        "day", today,
        "dayCount", current + 1);
boolean ok = context.documents().updateIfFieldAtMost(
        "claim_log", claimId, "dayCount", 4, patch);
if (!ok) {
    // 已达上限，另一个并发请求抢先写入了
}
```

由于判断与写入在服务端原子完成，多个插件实例/并发请求不会超发。

### 注意事项

- 集合名不需要前缀，宿主会自动按插件作用域隔离——不同插件的同名集合互不可见（见上文落库命名规则）。
- 文档是自由结构 `Map`，字段约定由插件自行维护；建议在文档中保留 `id` 字段与查询字段。
- 分页从 **1** 开始，单页上限 200。
- **QQ 沙箱**：宿主在文档存储外再包一层 `SandboxAwarePluginDocumentStore`——沙箱会话中的写入/删除落在内存 overlay（删除为墓碑标记），读取按「基库 + overlay 合并」视图返回，不会污染真实库；非沙箱链路直接透传 Mongo 实现。

---

## PluginFileStore —— 二进制文件存储

面向对象存储（S3 兼容）的文件读写端口，同样按插件作用域隔离 objectKey 命名空间。

### 方法一览

| 方法 | 签名 | 说明 |
|---|---|---|
| put | `String put(String objectKey, InputStream in, long contentLength, String contentType)` | 上传文件，返回规范化后的 objectKey |
| get | `PluginStoredFile get(String objectKey)` | 读取文件元数据与流 |
| delete | `void delete(String objectKey)` | 删除文件 |

### 宿主实现规则（ObjectStoragePluginFileStore）

- **作用域隔离**：实际存储键为 `plugins/{pluginCode}/{objectKey}`，插件只能看到自己前缀下的对象；`put` 返回的仍是传入的逻辑 objectKey（规范化后）。
- **路径规范化与校验**（违规抛 `BizException`，中文文案）：空白抛 `"插件文件路径不能为空"`；`\` 统一替换为 `/`，去除开头 `/`；以 `/` 结尾、含空段、`.` 或 `..` 段一律抛 `"插件文件路径不合法：..."`——即不允许目录型 key 与路径穿越。

### PluginStoredFile 字段

| 字段 | 类型 | 说明 |
|---|---|---|
| `objectKey` | `String` | 存储键 |
| `contentType` | `String` | MIME 类型 |
| `contentLength` | `Long` | 字节数 |
| `inputStream` | `InputStream` | 内容流（读取方负责关闭） |

### 使用示例

```java
// 上传
try (InputStream in = new ByteArrayInputStream(pngBytes)) {
    String key = context.files().put("certs/" + userId + ".png",
            in, pngBytes.length, "image/png");
}

// 读取并回写给 HTTP 响应
PluginStoredFile file = context.files().get(objectKey);
byte[] body = file.inputStream().readAllBytes();
return PluginHttpResponse.ok(body)
        .withHeader("Content-Type", file.contentType());
```

> 实际签名细节以 `PluginHttpResponse` 页为准；此处演示 files 端口用法。

---

## PluginSecretStore —— 插件密钥存储

不透明字节级密钥存储（宿主加密落库），用于保存第三方 API Key、Webhook Secret 等**不能明文进配置文件或文档库**的数据。

| 方法 | 签名 | 说明 |
|---|---|---|
| put | `void put(String key, byte[] value)` | 写入/覆盖密钥 |
| get | `Optional<byte[]> get(String key)` | 读取密钥 |
| delete | `boolean delete(String key)` | 删除密钥 |

```java
context.secrets().put("alipay.privateKey", privateKeyPem.getBytes(StandardCharsets.UTF_8));

byte[] saved = context.secrets().get("alipay.privateKey")
        .orElseThrow(() -> new IllegalStateException("请先在插件设置中配置支付宝私钥"));
```

- 密钥按 `pluginCode` 作用域隔离，其他插件（包括同 code 不同版本的旧数据策略）不可越权访问。
- 存入的是原始字节，序列化格式由插件自行决定（PEM、JSON、Properties 均可）。

---

## 三类存储怎么选

| 需求 | 用哪个 |
|---|---|
| 业务记录、可查询的状态数据 | `documents()` |
| 图片、证书、导出文件等二进制内容 | `files()` |
| 凭证、密钥等敏感字符串 | `secrets()`（切勿存进 documents） |

---

> 源码引用：
> - `yudream-plugins/yudream-plugin-spi/src/main/java/online/yudream/base/plugin/spi/system/storage/PluginDocumentStore.java`
> - `.../system/storage/PluginFileStore.java`、`.../system/storage/PluginStoredFile.java`
> - `.../system/secret/PluginSecretStore.java`
> - 宿主实现见 `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/plugin/service/` 下对应装饰器（含沙箱作用域隔离）
