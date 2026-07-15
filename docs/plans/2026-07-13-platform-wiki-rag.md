# 平台 Markdown Wiki 与 RAG Implementation Plan

> **For implementer:** Use TDD throughout. Write failing test first. Watch it fail. Then implement.

**Goal:** 提供可多库挂载、树形 Markdown 页面、版本化向量检索及外部 API/Agent 工具的 Wiki 平台能力。

**Architecture:** Wiki 以独立 DDD 上下文保存知识库、节点和发布版本；Neo4j 仅作为可重建的 Chunk/向量索引。应用服务在发布时按版本编排切片、Embedding、staging 写入与原子激活，并通过 API Key scope 对外暴露检索。

**Tech Stack:** Java 21、Spring Boot 3.5、MongoDB、Neo4j 5、Spring AI OpenAI、JUnit 5、Vue 3、TypeScript、Arco Design Vue、Vitest。

---

### Task 1: 注册 Wiki 平台能力与菜单权限

**Files:**
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/wiki/service/WikiCapabilityProvider.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/menu/enumerate/PlatformMenuModule.java`
- Modify: `yudream-bootstrap/src/main/resources/application.yml`
- Test: `yudream-infrastructure/src/test/java/online/yudream/base/infra/platform/wiki/service/WikiCapabilityProviderTest.java`

1. 先测试 descriptor 的 code 为 `wiki`、依赖为 `ai`/`neo4j`，且默认不连接外部服务。
2. 运行 `mvn -pl yudream-infrastructure -am -Dtest=WikiCapabilityProviderTest test`，确认失败。
3. 实现 `@ConditionalOnProperty` Provider、能力菜单与 `view/edit/publish/delete/manage` 权限种子。
4. 重跑命令，确认通过。

### Task 2: 建立 Wiki 聚合和树不变量

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/aggregate/WikiSpace.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/aggregate/WikiNode.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/aggregate/WikiPageVersion.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/enumerate/{WikiNodeType,WikiIndexStatus}.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/valobj/{WikiSlug,WikiEmbeddingProfile}.java`
- Test: `yudream-domain/src/test/java/online/yudream/base/domain/platform/wiki/aggregate/WikiNodeTest.java`

1. 写失败测试：slug 规范化、页面不可拥有子节点、根节点不可移动、目标父节点是自身/后代时抛出中文业务异常。
2. 运行 `mvn -pl yudream-domain -Dtest=WikiNodeTest test`。
3. 实现聚合状态转换和 value object 校验，保持 Long ID 只在领域内部使用。
4. 重跑并通过。

### Task 3: 定义持久化、检索和向量端口

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/repo/{WikiSpaceRepo,WikiNodeRepo,WikiPageVersionRepo}.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/service/{EmbeddingGateway,WikiVectorStore,WikiMarkdownChunker}.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/valobj/{WikiChunk,WikiSearchHit,WikiSearchRequest}.java`
- Test: `yudream-domain/src/test/java/online/yudream/base/domain/platform/wiki/service/WikiMarkdownChunkerTest.java`

1. 写失败测试，断言分块优先保留 Markdown 标题、chunk 长度受限、重叠文本可预测。
2. 运行 `mvn -pl yudream-domain -Dtest=WikiMarkdownChunkerTest test`。
3. 实现纯 Java chunker 及所有端口；向量 store 必须提供 `stage`、`activateVersion`、`removeVersion` 与 `searchActive`。
4. 重跑并通过。

### Task 4: 实现 Mongo 仓储与映射

**Files:**
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/wiki/{dataobj,mapper,impl}/...`
- Test: `yudream-infrastructure/src/test/java/online/yudream/base/infra/platform/wiki/impl/WikiMongoRepoTest.java`

1. 写失败集成测试：space slug 唯一、按 parent/sort 读树、草稿与版本可独立读取。
2. 运行 `mvn -pl yudream-infrastructure -am -Dtest=WikiMongoRepoTest test`。
3. 增加 `platformWikiSpace`、`platformWikiNode`、`platformWikiPageVersion` DO，索引只保存状态而非 embedding；实现 infra mapper 和 repo。
4. 重跑并通过。

### Task 5: 实现 AI Embedding 与 Neo4j 向量适配器

**Files:**
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/wiki/service/{AiEmbeddingGateway,Neo4jWikiVectorStore}.java`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/ai/service/...`（只增加 Embedding 端口实现所需能力）
- Test: `yudream-infrastructure/src/test/java/online/yudream/base/infra/platform/wiki/service/Neo4jWikiVectorStoreTest.java`

1. 写失败测试，验证 `stage` 使用 `(spaceId,nodeId,version,chunkNo)` MERGE，`activateVersion` 仅切换同页面版本，search 只返回 active Chunk。
2. 使用 Neo4j Testcontainers 运行 `mvn -pl yudream-infrastructure -am -Dtest=Neo4jWikiVectorStoreTest test`。
3. 使用已有 provider-first AI 配置调用 Embedding；Neo4j 创建向量索引、批量写入、余弦相似度查询和幂等删除。连接只在应用门控后实际索引或检索时建立。
4. 重跑并通过。

### Task 6: 实现可选知识图谱抽取与融合查询

**Files:**
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/service/WikiGraphExtractionGateway.java`
- Create: `yudream-domain/src/main/java/online/yudream/base/domain/platform/wiki/valobj/{WikiEntity,WikiRelation,WikiGraphExtraction}.java`
- Create: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/wiki/service/{AiWikiGraphExtractionGateway,Neo4jWikiKnowledgeGraphStore}.java`
- Test: `yudream-infrastructure/src/test/java/online/yudream/base/infra/platform/wiki/service/Neo4jWikiKnowledgeGraphStoreTest.java`

1. 写失败测试：抽取结果必须符合受限 Schema；关系必须引用本版本有效 Chunk；同 Space 同名同类型实体合并；graph expansion 不返回 inactive、跨 Space 或未发布 Chunk。
2. 使用 Neo4j Testcontainers 运行 `mvn -pl yudream-infrastructure -am -Dtest=Neo4jWikiKnowledgeGraphStoreTest test`。
3. 通过平台 AI 生成模型调用结构化输出抽取实体/关系；持久化 staging graph，并实现由向量命中实体发起的一跳、带衰减重排的融合查询。用户未启用 `graphEnabled` 时不得调用该 Gateway。
4. 重跑并通过。

### Task 7: 实现知识库与树管理用例

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/wiki/{cmd,query,dto,assembler,service}/...`
- Test: `yudream-application/src/test/java/online/yudream/base/application/platform/wiki/service/WikiAppServiceTest.java`

1. 写失败测试：创建 Space 时 slug 冲突拒绝；移动节点不得循环；只有 page 可保存 Markdown 草稿。
2. 运行 `mvn -pl yudream-application -am -Dtest=WikiAppServiceTest test`。
3. 实现 Space CRUD、目录/页面 CRUD、树查询、排序移动；每个用例先调用 `CapabilityAppService.ensureEnabled`。
4. 重跑并通过。

### Task 8: 实现版本化发布、索引和重试

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/wiki/service/WikiPublicationAppService.java`
- Test: `yudream-application/src/test/java/online/yudream/base/application/platform/wiki/service/WikiPublicationAppServiceTest.java`

1. 写失败测试：新发布调用 chunk/embed/stage/activate；启用图谱时仅在新版本图谱成功后激活；新版本失败时旧版本仍 active；重试不重复激活；撤销先关闭 alias。
2. 运行 `mvn -pl yudream-application -am -Dtest=WikiPublicationAppServiceTest test`。
3. 实现版本哈希、状态机、失败记录、重试/重建、删除清理。将远程调用放在事务边界外，最终状态持久化使用明确的补偿流程。
4. 重跑并通过。

### Task 9: 实现公开与 API Key 检索用例

**Files:**
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/wiki/service/WikiSearchAppService.java`
- Test: `yudream-application/src/test/java/online/yudream/base/application/platform/wiki/service/WikiSearchAppServiceTest.java`

1. 写失败测试：未发布/未启用 externalSearch 的内容不传给 vector store；scope 不匹配拒绝；`graphExpansion` 未启用时保持纯向量检索；结果带稳定公开 URL。
2. 运行 `mvn -pl yudream-application -am -Dtest=WikiSearchAppServiceTest test`。
3. 实现后台、匿名公开和外部搜索的不同访问策略；支持 `topK` 与 `pathPrefix` 上限。
4. 重跑并通过。

### Task 10: 暴露 DDD 合规 HTTP 接口与 Agent 工具

**Files:**
- Create: `yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/wiki/{controller,assembler,request,res}/...`
- Create: `yudream-application/src/main/java/online/yudream/base/application/platform/wiki/service/WikiSearchAiTool.java`
- Modify: API Key 认证/授权边界所对应的现有接口配置文件
- Test: `yudream-interfaces/src/test/java/online/yudream/base/interfaces/platform/wiki/controller/WikiControllerTest.java`

1. 写失败 MockMvc 测试：权限、API Key scope、Space 隔离、公开开关和 `/api/open/wiki/{slug}/search` 响应。
2. 运行 `mvn -pl yudream-interfaces -am -Dtest=WikiControllerTest test`。
3. 增加管理/公开/开放控制器和 assembler；控制器不得 `new Cmd/Res`。注册 `wiki.search` 原生 Agent Tool，返回结构化 hit。
4. 重跑并通过；执行 `rg -n "private .*to[A-Z]|new .*Cmd|new .*Res|\\.builder\\(\\)" yudream-interfaces/src/main/java/online/yudream/base/interfaces/platform/wiki -g "*Controller.java"`，无结果。

### Task 11: 实现管理端知识库工作台

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/api/modules/platform-wiki.ts`
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/wiki/{index.vue,components/WikiSpacePanel.vue,components/WikiTree.vue,components/WikiEditor.vue,components/WikiIndexStatus.vue}`
- Modify: `yudream-infrastructure/src/main/java/online/yudream/base/infra/platform/menu/enumerate/PlatformMenuModule.java`
- Test: `yudream-frontend/apps/core-arco-design-vue/src/views/platform/wiki/components/WikiTree.test.ts`

1. 写失败 Vitest 测试：树禁止把节点拖到自己的后代，索引失败显示重试动作。
2. 运行 `pnpm --filter @fantastic-admin/core-arco-design-vue exec vitest run src/views/platform/wiki/components/WikiTree.test.ts`。
3. 实现 API 类型和三栏工作台；Space 配置支持图谱增强开关、生成模型选择和限制配置；用熟悉图标按钮表示新增、移动、删除、发布，使用 `v-auth` 保护动作；编辑区支持 Markdown 预览及发布状态。
4. 重跑测试并执行 `pnpm --config.engine-strict=false --filter @fantastic-admin/core-arco-design-vue exec vue-tsc --noEmit --pretty false --skipLibCheck --ignoreDeprecations 6.0`。

### Task 12: 实现公开 Wiki 页面

**Files:**
- Create: `yudream-frontend/apps/core-arco-design-vue/src/views/wiki/index.vue`
- Modify: `yudream-frontend/apps/core-arco-design-vue/src/router/routes.ts`
- Test: `yudream-frontend/apps/core-arco-design-vue/src/views/wiki/index.test.ts`

1. 写失败测试：请求指定 Space tree，未发布页面不渲染，页面链接保留 `spaceSlug/nodePath`。
2. 运行 `pnpm --filter @fantastic-admin/core-arco-design-vue exec vitest run src/views/wiki/index.test.ts`。
3. 新增公开路由和目录/文章布局，复用已验证的安全 Markdown 渲染逻辑；不得输出未清洗 HTML。
4. 重跑测试和 `vue-tsc`。

### Task 13: 端到端验证与文档

**Files:**
- Create: `docs/platform/wiki-rag.md`
- Test: `yudream-bootstrap/src/test/java/online/yudream/base/WikiRagIntegrationTest.java`

1. 写失败集成测试：创建两库、发布两版、检索仅命中 active 版本、图谱增强仅在 Space 开启时执行且只扩展同 Space active Chunk、API Key scope 跨库拒绝、撤销后无命中。
2. 运行 `mvn -pl yudream-bootstrap -am -Dtest=WikiRagIntegrationTest test`。
3. 记录启动依赖、Neo4j 向量索引、Provider/模型配置、外部 API 和重建操作；完成集成测试。
4. 执行 `mvn -pl yudream-bootstrap -am -DskipTests compile` 与前端 `vue-tsc`，确认通过。

**提交说明：** 用户明确要求本设计与计划不提交 Git。实施阶段也只在用户另行授权时提交。
