# LLM Wiki 模式重构设计

## 背景

原 `platform/wiki` 是「手工 Markdown 知识库 + 向量检索」，缺少文档摄入、PDF 抽图、index/log 维护、Lint、审阅、深度研究等能力，且「抽图」能力此前并不存在（仅有 Tika 纯文本抽取）。本次按 [llm_wiki](https://github.com/nashsu/llm_wiki) / Karpathy llm-wiki 模式，将其重构为「原始文档 → LLM 两步摄入 → 自动生成并维护 Wiki」。

## 核心结构变化

- 三层架构：`WikiSource`（不可变原始资料）→ `WikiNode`（LLM 生成页面，YAML frontmatter + `[[wikilink]]`）→ `WikiSpace.purpose/schemaContent`（方向意图与结构规则）。
- 三操作：Ingest（两步思维链摄入）、Query（混合检索 + 只读原文）、Lint（健康检查）。

## 新增能力与映射

| llm_wiki 能力 | 本实现 |
| --- | --- |
| 两步思维链摄入 | `WikiIngestAppService.analyze/generate`（JSON 结构化输出） |
| 多模态抽图 | `PdfBoxWikiDocumentImageExtractor` + `AiWikiVisionCaptionGateway`（PDF 内嵌图 + 视觉 caption） |
| 多格式解析 | 复用 Tika 文本抽取；PDF 抽图；网页剪藏/URL 抓取 `WikiWebPageFetcher` |
| 模型配置 | `WikiSpace` Chat/Ingest/Vision 独立 provider/model + web search 配置 |
| 只读原文 | `WikiSearchAppService` source-grounded 模式（`WikiSource.searchByKeyword`） |
| 项目迁移 | `WikiMigrationAppService` 导出/导入归档、重建索引 |
| 四信号图谱 + Louvain | `WikiGraphAnalysisAppService`（direct_link/source_overlap/adamic_adar/type_affinity + Louvain 社区检测 + 洞察） |
| 向量语义搜索 | 复用 `Neo4jWikiIndexGateway` + 新增关键词检索混合 |
| 持久化摄入队列 | `WikiIngestTask` + `WikiIngestQueueExecutor`（串行/崩溃恢复/重试/取消/SSE 进度） |
| 文件夹导入/监听 | 目录保留 `folderPath`；监听/重扫预留（`WikiSpace.watchEnabled/watchFolderPath`） |
| 深度研究 | `WikiDeepResearchAppService` + `HttpWikiWebSearchGateway`（Tavily/SerpApi/SearXNG） |
| 审阅系统 | `WikiReviewItem` + `WikiReviewAppService`（完成/忽略/触发研究） |
| Agent 工具 | `WikiSearchAiTool`（原生 `wiki.search`，含 source-grounded） |
| index/log/overview | 摄入自动维护特殊页面（`WikiPageType.INDEX/LOG/OVERVIEW`） |

## 分层归属

- 领域：聚合 `WikiSource/WikiIngestTask/WikiReviewItem`、改造 `WikiSpace/WikiNode`、端口（抽图/caption/web search/网页抓取/摄入队列/进度/取消注册）、值对象（frontmatter、图谱等）。
- 应用：`WikiSourceAppService`、`WikiSourceExtractionService`、`WikiIngestAppService`（runner）、`WikiSearchAppService`、`WikiLintAppService`、`WikiReviewAppService`、`WikiDeepResearchAppService`、`WikiMigrationAppService`、`WikiGraphAnalysisAppService`、`WikiSearchAiTool`。
- 基础设施：DO/mapper/repo、`PdfBoxWikiDocumentImageExtractor`、`AiWikiVisionCaptionGateway`、`HttpWikiWebSearchGateway`、`HttpWikiWebPageFetcher`、`InMemoryWikiIngestProgressGateway`、`InMemoryWikiIngestCancellationRegistry`、`WikiIngestQueueExecutor`。
- 接口：`WikiSourceController`、`WikiIngestController`、`WikiIngestProgressController`、`WikiLintController`、`WikiReviewController`、`WikiDeepResearchController`、`WikiGraphController`、`WikiMigrationController`，并重做 `WikiSpaceSaveRequest/WikiNodeSaveRequest/WikiSearchRequest/WikiWebAssembler`。

## 验证

- 后端 `yudream-domain/application/infrastructure/interfaces/bootstrap` 编译通过（JDK 21）。
- 单测：`WikiNodeTest`、`WikiFrontmatterTest`、`WikiSlugTest`、`WikiKnowledgeAggregatesTest`、`PdfBoxWikiDocumentImageExtractorTest`（真实 PDF 抽图）均通过。
- 前端 `test:typecheck` 与应用级 `vue-tsc`（全量 .vue）通过。
- **真机端到端（Mongo/Redis/RustFS(S3)/Neo4j + 真实 AI）**：Markdown/PDF 上传 → 文本抽取 → PDF 抽图 → 视觉 caption → 两步摄入（analyze+generate）→ 页面/index/log/overview 生成 → 发布 + 向量索引 → 关键词+向量混合检索 → 四信号图谱 + Louvain 社区 + 洞察 → Lint → 摄入队列（串行/重试/取消）→ wiki.search 原生工具问答（带引用）。全部跑通。

## 真机发现的修复

- 向量检索失败导致搜索整体 500 → 改为降级回退关键词检索；rerank 失败回退原始排序。
- 摄入任务多次保存触发 `@Version` 乐观锁冲突（`Cannot save entity ... version N`）导致任务孤儿化为 RUNNING → 仓储 `save` 后回写新版本号（source/task/reviewItem）。
- 关键词检索对多词/中文查询精确子串匹配过弱 → 增加 `WikiSearchTokenizer` 分词后“任一命中”匹配。
- 管理端摄入 SSE 裸 fetch 缺少 `Authorization` 头。
- `WikiFrontmatter.parse` 对缩进列表项解析失败；`WikiSlug.derive` 对中文标题退化短串冲突。
- 问答引用解析兼容 Spring AI 清洗后的工具名 `wiki_search`。

## 前端重做

管理端重做为图标侧边栏工作台（目录/资料源/摄入队列/检索/图谱/Lint/审核/研究/问答/设置），全部使用 Fa 组件；图谱用 echarts force 布局（着色/缩放/邻居高亮/社区与洞察联动）；`[[wikilink]]` 可点击跳转；模型/供应商一律下拉选择（按 kind/vision 过滤）；公开端检索支持只读原文开关且修正了页面链接。

## 已知边界

- Mermaid 图表渲染依赖前端引入 mermaid 包，本次未安装（md-editor-v3 支持，需在 pnpm catalog 增加依赖后启用）。
- Chrome Web Clipper 扩展是独立浏览器产物，服务端以 URL 抓取/网页剪藏覆盖其功能本质。
- Source 文件夹自动监听已建模（`watchEnabled/watchFolderPath`），定时重扫调度未在本仓启用。
- 问答/摄入的最终答案文案质量取决于所选 LLM（当前 gpt-5.6-sol 偶发保守措辞），属提示词/模型调参范畴，机制（工具调用 + 引用 + 内容）已验证。
