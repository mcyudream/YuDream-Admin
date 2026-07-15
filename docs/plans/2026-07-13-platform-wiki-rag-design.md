# 平台 Markdown Wiki 与 RAG 设计

## 目标

在平台能力中提供类似 Wiki.js 的 Markdown 知识库。管理员可创建多个独立知识库，每个知识库使用唯一 `slug` 挂载公开路由，维护父子目录与页面树；发布后的页面以版本化切片进入向量索引，供公开阅读、API Key 客户端和 AI Agent 工具检索。

## 方案

采用完整 RAG，而非仅关键词检索。首个向量存储适配器使用已有的 Neo4j 5 能力和 Docker 配置；Embedding 复用 AI Provider 的 `providerCode + modelCode` 配置。二者均通过领域端口隔离，使将来可以增加 Qdrant、Milvus 或其他适配器而不改变 Wiki 用例。

Neo4j 同时可作为可选知识图谱存储。知识库管理员可显式启用“图谱增强检索”，并从平台已配置的生成模型中选择 `graphProviderCode + graphModelCode`。默认关闭，关闭时完全不调用生成模型，也不创建实体/关系；Embedding 向量检索仍照常工作。

Wiki 是独立的 `platform/wiki` 有界上下文，受两个运行时能力依赖：`ai` 和 `neo4j`。项目门控为 `yudream.platform.capabilities.wiki.enabled=true`；应用门控在每个 Wiki 用例执行前调用 `CapabilityAppService.ensureEnabled("wiki", "Wiki 知识库")`。未启用时不注册 Wiki Provider 和接口，不连接 Neo4j。

## 领域模型

- `WikiSpace`：知识库根，拥有名称、唯一 `slug`、描述、公开阅读/外部检索开关、可用 API Key scope、Embedding Provider/Model、分块和 TopK 参数，以及可选 `graphEnabled`、图谱抽取 Provider/Model 和图谱扩展上限。
- `WikiNode`：目录或页面节点。节点保留 `parentId`、`path`、`sort` 和不可变 `nodeType`；页面包含草稿 Markdown、发布版本、发布时间和索引状态。
- `WikiPageVersion`：发布快照，包含版本号、内容哈希、Markdown、标题、索引状态、失败原因与索引时间。草稿绝不进入该模型或检索。
- `WikiChunk`：派生索引记录，含 space/node/version、序号、标题、树路径、文本、token 近似长度、embedding 和公开可见性。Mongo 不保存大向量，只保存索引任务/版本状态；Chunk 和向量写入 Neo4j。
- `WikiKnowledgeGraph`：派生实体和关系。实体按 `spaceId + normalizedName + type` 去重；关系保存 source/target/type、出处 Chunk、置信度和版本。关系与 Chunk 一样不可直接编辑，可通过重建从页面版本完全恢复。

## 发布与更新索引

1. 编辑仅保存草稿。
2. 发布创建不可变 `WikiPageVersion`，按标题边界优先、固定长度和重叠回退的策略切分 Markdown。
3. 应用服务调用 `EmbeddingGateway` 批量获取向量，通过 `WikiVectorStore.stage` 写入 `spaceId:nodeId:version` 命名空间。
4. 当图谱增强启用时，对每个 Chunk 调用 `WikiGraphExtractionGateway`，要求模型返回受 JSON Schema 约束的实体和关系；先校验长度、类型、置信度和 Chunk 引用，再写入同一版本的 staging 图谱。
5. 所有 Chunk 和可选图谱写入成功后 `activateVersion` 原子更新 Neo4j 中该页面的 active 版本；随后删除旧版本 Chunk/关系。
6. 持久化页面当前发布版本与 `READY` 状态。任何一步失败均持久化 `FAILED` 和错误信息，保留此前 active 版本继续服务。
7. 重试与重建使用同一个版本和幂等键 `nodeId:version`，先清理该 staging version 再重建，不生成重复 Chunk。
8. 取消发布或删除时先撤销 active 版本、再删除向量和关系；节点移动时更新已发布版本的路径元数据并重建其索引。

## 检索和路由

后台管理接口位于 `/api/platform/wiki/**`，使用 `platform:wiki:view|edit|publish|delete|manage`。公开读取位于 `/api/public/wiki/{spaceSlug}/tree` 与 `/api/public/wiki/{spaceSlug}/pages/{nodePath}`，仅在 Space 开启 `publicReadEnabled` 且节点版本已发布时返回。

外部检索为 `POST /api/open/wiki/{spaceSlug}/search`。它要求现有 API Key 的 `wiki:search:{spaceSlug}` 或 `wiki:search:*` scope，且 Space 开启 `externalSearchEnabled`。请求支持 `query`、`topK`、`pathPrefix` 和可选 `graphExpansion`；响应仅含 active 公开 Chunk 的 score、片段、标题、路径和稳定 source URL。路由按 Space slug 严格隔离。

检索始终先做向量召回。若调用方请求 `graphExpansion` 且知识库启用了图谱增强，则从前 N 个向量命中提取实体，沿有效、同 Space 的关系至多扩展一跳，补充相邻页面 Chunk，并以向量分数、关系置信度与跳数衰减融合重排。图谱是增强信号，不能绕过 Space、发布版本和公开策略，也不能单独返回未被向量/权限过滤的内容。

向 AI 暴露 `wiki.search` 工具，声明 JSON Schema、描述和 `wiki:search` 权限元数据。Agent 使用原生 Tool Calling，由应用服务执行，不要求模型伪造工具调用 JSON。

## 管理与公开前端

管理入口是 `platform/wiki/index.vue` 三栏工作台：知识库选择/配置、目录树、Markdown 编辑器与发布索引状态。知识库配置提供图谱增强开关、模型选择和抽取/查询上限，显示预估调用量而非自动启用。树可新建目录或页面、移动和排序；服务端阻止移入后代节点。页面提供草稿保存、预览、发布、取消发布、重试和整库重建。

公开站点增加 `/wiki/:spaceSlug/:nodePath*`。显示知识库目录、已发布 Markdown 和来源 URL；渲染必须沿用受控 Markdown/HTML 清洗策略，拒绝脚本、事件属性和危险 URL。

## 失败和可观测性

Embedding/Neo4j 故障不会丢失内容。页面记录 `FAILED` 和可读错误，前端可重试；检索只查询已激活版本。索引操作记录 traceId、space/node/version、chunk 数和耗时。删除、撤销和重试均应幂等。

## 验收

- 可创建两个不同 `slug` 的知识库，并在独立公开路由下阅读已发布页面。
- 可创建、排序、移动多层目录；不能形成循环。
- 页面更新发布后检索只返回新版本的片段；索引失败时仍返回上一成功版本。
- 无对应 API Key scope、未开启外部检索、跨 Space 查询和未发布页面一律不可检索。
- AI Agent 可通过 `wiki.search` 获得带来源的结构化结果。
- 开启图谱增强的知识库在新版本发布后生成实体关系；启用 `graphExpansion` 的检索可召回一跳关联内容，且不泄露未发布或跨库内容。
