# 用途化资源图投影 GraphSpi

> SPI v1（2.13.0）· 包 `online.yudream.base.plugin.spi.system.graph`

`PluginContext.graph()` 是插件访问宿主管理资源图的唯一入口。它支持绑定状态检查、版本化投影的**原子替换**和**结构化分页读取**。插件可使用显式图表编码的兼容接口，也可使用不含 `tableCode` 的自动绑定接口；后者由宿主按运行时可信 `pluginCode` 解析唯一 ACTIVE 且已授权的逻辑图表。没有或不唯一时返回结构化失败，绝不选择第一项。所有接口均不接受插件编码或 Cypher，宿主负责能力闸门、图表状态与授权校验，以及单一 Driver 生命周期和隔离。

## 端口与模型

```java
// 只读、无副作用：检查调用插件的唯一自动绑定，不访问投影。
PluginGraphBindingStatus bindingStatus();

// 兼容：调用方显式指定已授权的逻辑图表。
PluginGraphProjectionResult replaceProjection(PluginGraphProjectionRequest request);
PluginGraphProjectionView readProjection(PluginGraphProjectionViewRequest request);

// 推荐：宿主仅解析调用插件唯一 ACTIVE 且已授权的逻辑图表。
PluginGraphProjectionResult replaceProjection(PluginGraphProjection projection);
PluginGraphProjectionView readProjection(PluginGraphProjectionReadRequest request);

// 完整快照：无 tableCode、无 Cypher，适用于需要提交全部闭合图的插件。
PluginGraphProjectionResult replaceCompleteProjection(PluginGraphCompleteProjection projection);
```

- `PluginGraphService.tables()`：返回当前插件已授权且 ACTIVE 的 `PluginGraphTable(code, name, description)`，不包含物理库或凭据。
- `PluginGraphService.bindingStatus()`：返回 `PluginGraphBindingStatus(available, table, error)`。唯一绑定时 `available=true` 且仅携带安全的 `PluginGraphTable` 元数据；否则 `table` 为 `null` 并携带稳定的 `PluginGraphError`。该检查只查询逻辑图表绑定，**不会调用 `GraphProjectionGateway`，不会读取或写入投影**。
- 能力不可用时，`bindingStatus()` 返回 `CAPABILITY_UNAVAILABLE`；无匹配返回 `TABLE_BINDING_NOT_FOUND`；多匹配返回 `TABLE_BINDING_AMBIGUOUS`。这些失败均不选择任意图表，也不泄漏物理库、连接或凭据。
- `PluginGraphProjectionRequest(tableCode, namespace, versionId, nodes, relationships)` 与 `PluginGraphProjectionViewRequest(...)`：显式图表的兼容输入；`tableCode` 必须是当前插件可见图表的编码。
- `PluginGraphProjection(namespace, versionId, nodes, relationships)` 与 `PluginGraphProjectionReadRequest(namespace, versionId, nodePage, nodeSize, relationshipPage, relationshipSize)`：推荐的自动绑定输入；插件不配置或传递 `tableCode`。普通替换仍限制节点和关系各 1,000 条。
- `PluginGraphCompleteProjection(namespace, versionId, nodes, relationships)`：完整快照输入，不含 `tableCode` 或 Cypher；同样只使用唯一 ACTIVE 已授权绑定。最多 20,000 个节点、50,000 条关系和 8 MiB UTF-8 JSON；超限返回 `PROJECTION_LIMIT_EXCEEDED`，且不会触发图数据库 gateway。
- 自动绑定只接受唯一 ACTIVE 且授权给当前 `pluginCode` 的逻辑图表。无匹配返回 `TABLE_BINDING_NOT_FOUND`，多匹配返回 `TABLE_BINDING_AMBIGUOUS`，均不选择第一项。
- `PluginGraphProjectionView(success, namespace, versionId, nodes, relationships, error)`：结构化只读结果；`nodes` 和 `relationships` 均为 `PluginGraphProjectionPage(records, total, page, size)`。节点和边沿用 `PluginGraphProjectionNode`、`PluginGraphProjectionRelationship` 的安全 DTO。
- `PluginGraphProjectionResult(success, namespace, nodeCount, relationshipCount, error)`：替换结果；失败包含稳定的 `PluginGraphError(code, message)`，不泄漏 Neo4j Driver、凭据或连接配置。

宿主以固定 Neo4j label `PluginGraphProjection`、固定关系类型 `PluginGraphProjectionRelation` 和参数绑定读写，范围固定为可信的 `tableCode + pluginCode + namespace + versionId`。插件提供的 `type` 只是属性，不能改变 Cypher 结构。替换时，旧 scope 的清理和新节点/关系写入在单个 write transaction 中完成，任一步失败都会回滚。读取按稳定 ID 排序，并分别以固定查询计算节点/关系总数和提取当前页。

## 使用边界

```java
var binding = context.graph().bindingStatus();
if (!binding.available()) {
    // 按 binding.error().code() 降级；不尝试猜测或选择图表。
    return;
}

var write = context.graph().replaceProjection(new PluginGraphProjection(
        "resources",
        "v2026-08-23",
        List.of(new PluginGraphProjectionNode("task-1", "TASK", Map.of("title", "构建"))),
        List.of()
));
```

- 图数据库属于可选 platform 能力。能力或所选逻辑图表不可用时，插件必须处理结构化失败并降级相关功能。
- 调用方只能操作或读取自己的 `tableCode + pluginCode + namespace + versionId`；不能读取、删除或替换其他插件、图表或版本的数据。
- 宿主仅使用请求指定且通过 ACTIVE、授权校验的逻辑图表执行读写；不会从可用图表中任意选择。
- 同一 `namespace + versionId` 每次替换都是完整替换；需要保留的节点和关系必须在本次请求中完整提交。
- 插件不得持有跨 disable/unload 生命周期的 session 或 Driver。

## 禁止事项

以下做法违反插件契约：

- 使用通用 Cypher、`nodeQuery`、`relationshipQuery`、查询文本或管理命令；SPI 不提供这些接口。
- 传入或伪造 `pluginCode`、连接 URI、用户名、密码、数据库、Neo4j label 或关系类型。
- 使用未列入 `tables()` 返回列表的图表编码，或绕过宿主对所选图表的 ACTIVE、授权和能力双闸门校验。
- 读取 `NEO4J_URI`、`NEO4J_USERNAME`、`NEO4J_PASSWORD` 等环境变量或私有配置创建连接。
- 直接依赖 `org.neo4j.driver`，自行创建、缓存或关闭宿主 Neo4j Driver。
- 引用宿主 `domain`、`application`、`infrastructure`、Spring Bean、Mapper 或 Repository，或通过私有 HTTP/反射绕过 `PluginContext.graph()`。

需要新增用途化投影能力或 DTO 时，必须先扩展并发布稳定 SPI，再实现宿主适配；不得以任意 Cypher 作为临时通道。

## 发布与验证

`PluginContext.graph()` 属于插件编译期契约。发布它时，宿主必须同步提交 SPI 源码、`PluginContext` 文档、本文档、运行时 scope 适配和版本变更说明，并先完成 SPI Maven package。未发布、未验证的 SPI 版本不得被独立插件仓消费。
