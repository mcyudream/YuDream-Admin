package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiKnowledgeAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiIngestTaskDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiIngestTask;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiExtractionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiIngestCancellationRegistry;
import online.yudream.base.domain.platform.wiki.service.WikiIngestTaskRunner;
import online.yudream.base.domain.platform.wiki.service.WikiWebSearchGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import online.yudream.base.domain.platform.wiki.valobj.WikiSlug;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchConfig;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchResult;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 摄入编排服务：实现持久化摄入队列的 runner，负责两步思维链摄入、级联删除、深度研究、重建索引。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiIngestAppService implements WikiIngestTaskRunner {

    private static final int SOURCE_CONTEXT_LIMIT = 60_000;
    private static final int FALLBACK_BODY_LIMIT = 8_000;
    private static final Set<WikiPageType> SPECIAL_TYPES = Set.of(WikiPageType.INDEX, WikiPageType.LOG, WikiPageType.OVERVIEW);

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final AiGenerationGateway aiGeneration;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiSourceRepo sourceRepo;
    private final WikiIngestTaskRepo taskRepo;
    private final WikiReviewItemRepo reviewItemRepo;
    private final WikiSourceExtractionService extractionService;
    private final WikiPublicationAppService publicationAppService;
    private final WikiWebSearchGateway webSearchGateway;
    private final WikiIngestCancellationRegistry cancellationRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 队列操作 ----------

    public void enqueueIngest(Long spaceId, Long sourceId) {
        enabled();
        spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        taskRepo.save(WikiIngestTask.create(spaceId, sourceId, WikiIngestTaskType.INGEST, null, System.currentTimeMillis()));
    }

    public void enqueueCleanup(Long spaceId, Long sourceId, String title, String path) {
        enabled();
        try {
            String payload = objectMapper.writeValueAsString(Map.of("title", title == null ? "" : title, "path", path == null ? "" : path));
            taskRepo.save(WikiIngestTask.create(spaceId, sourceId, WikiIngestTaskType.DELETE_CLEANUP, payload, System.currentTimeMillis()));
        }
        catch (Exception exception) {
            throw new BizException("创建清理任务失败");
        }
    }

    public void enqueueResearch(Long spaceId, String topic, List<String> queries) {
        enabled();
        try {
            String payload = objectMapper.writeValueAsString(Map.of("topic", topic == null ? "" : topic,
                    "queries", queries == null ? List.of() : queries));
            taskRepo.save(WikiIngestTask.create(spaceId, null, WikiIngestTaskType.DEEP_RESEARCH, payload, System.currentTimeMillis()));
        }
        catch (Exception exception) {
            throw new BizException("创建研究任务失败");
        }
    }

    public void enqueueReindex(Long spaceId) {
        enabled();
        taskRepo.save(WikiIngestTask.create(spaceId, null, WikiIngestTaskType.REINDEX, null, System.currentTimeMillis()));
    }

    public List<WikiIngestTaskDTO> tasks(Long spaceId) {
        enabled();
        return taskRepo.findBySpaceId(spaceId).stream().map(WikiKnowledgeAssembler::ingestTask).toList();
    }

    public void cancelTask(Long taskId) {
        taskRepo.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == WikiIngestTaskStatus.QUEUED) {
                task.cancel();
                taskRepo.save(task);
            }
            else if (task.getStatus() == WikiIngestTaskStatus.RUNNING) {
                cancellationRegistry.markCancelled(taskId);
            }
        });
    }

    public void retryTask(Long taskId) {
        taskRepo.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == WikiIngestTaskStatus.FAILED || task.getStatus() == WikiIngestTaskStatus.CANCELLED) {
                task.resetForRetry();
                taskRepo.save(task);
            }
        });
    }

    /** 删除摄入任务；运行中的任务先标记取消，待其结束后可再次删除。 */
    public void deleteTask(Long taskId) {
        enabled();
        taskRepo.findById(taskId).ifPresent(task -> {
            if (task.getStatus() == WikiIngestTaskStatus.RUNNING) {
                cancellationRegistry.markCancelled(taskId);
                return;
            }
            taskRepo.deleteById(taskId);
        });
    }

    /** 清空知识库摄入队列：删除全部非运行中任务，运行中任务标记取消。返回删除数量。 */
    public int clearTasks(Long spaceId) {
        enabled();
        int removed = 0;
        for (WikiIngestTask task : taskRepo.findBySpaceId(spaceId)) {
            if (task.getStatus() == WikiIngestTaskStatus.RUNNING) {
                cancellationRegistry.markCancelled(task.getId());
                continue;
            }
            taskRepo.deleteById(task.getId());
            removed++;
        }
        return removed;
    }

    // ---------- 队列 runner ----------

    @Override
    public void run(WikiIngestTask task, Consumer<WikiIngestProgress> progress, BooleanSupplier isCancelled) {
        this.currentProgress = progress;
        switch (task.getTaskType()) {
            case INGEST -> doIngest(task, progress, isCancelled);
            case DELETE_CLEANUP -> doCleanup(task, progress);
            case DEEP_RESEARCH -> doResearch(task, progress, isCancelled);
            case REINDEX -> doReindex(task, progress);
            default -> throw new BizException("未知的摄入任务类型：" + task.getTaskType());
        }
    }

    private void doIngest(WikiIngestTask task, Consumer<WikiIngestProgress> progress, BooleanSupplier isCancelled) {
        log.info("doIngest start task={} space={} source={}", task.getId(), task.getSpaceId(), task.getSourceId());
        WikiSpace space = spaceRepo.findById(task.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        WikiSource source = sourceRepo.findById(task.getSourceId()).orElseThrow(() -> new BizException("资料不存在"));
        progress(task, source, "extract", "抽取原始资料文本与图片", 5);
        throwIfCancelled(isCancelled);

        if (source.getExtractionStatus() != WikiExtractionStatus.EXTRACTED) {
            extractionService.extract(source, space, aiConfig());
            sourceRepo.save(source);
        }
        if (source.getExtractionStatus() == WikiExtractionStatus.FAILED) {
            throw new BizException(source.getExtractionError());
        }
        if (source.isUnchangedSinceIngest(source.getContentHash())) {
            progress(task, source, "skip", "资料未变更，跳过摄入", 100);
            return;
        }

        progress(task, source, "analyze", "第一步：LLM 分析资料", 20);
        throwIfCancelled(isCancelled);
        JsonNode analysis = analyze(space, source);

        progress(task, source, "generate", "第二步：LLM 生成 Wiki 页面", 50);
        throwIfCancelled(isCancelled);
        JsonNode generated = generate(space, source, analysis);

        progress(task, source, "apply", "写入 Wiki 页面并维护 index/log/overview", 70);
        throwIfCancelled(isCancelled);
        List<Long> touched = applyPages(space, source, generated);
        updateIndex(space);
        updateOverview(space);
        appendLog(space, source);
        saveReviewItems(space, source, analysis, generated);

        source.markIngested(source.getContentHash());
        sourceRepo.save(source);

        progress(task, source, "publish", "发布页面并构建检索索引", 85);
        publishPages(touched);
        progress(task, source, "done", "摄入完成", 100);
    }

    private void doCleanup(WikiIngestTask task, Consumer<WikiIngestProgress> progress) {
        WikiSpace space = spaceRepo.findById(task.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        String path = "";
        String title = "";
        try {
            JsonNode payload = task.getPayloadJson() == null ? null : objectMapper.readTree(task.getPayloadJson());
            if (payload != null) {
                path = payload.path("path").asText("");
                title = payload.path("title").asText("");
            }
        }
        catch (Exception ignored) {
            // 忽略 payload 解析错误
        }
        progress(task, null, "cleanup", "清理被删除资料关联的 Wiki 页面", 20);
        List<WikiNode> pages = nodeRepo.findBySpaceId(space.getId());
        for (WikiNode node : pages) {
            if (node.getNodeType() != WikiNodeType.PAGE) {
                continue;
            }
            if (task.getSourceId() != null && ("source-" + task.getSourceId()).equals(node.getSlug())) {
                nodeRepo.deleteById(node.getId());
            }
            else if (node.getPageType() == WikiPageType.SOURCE_SUMMARY
                    && (title.isBlank() || node.getTitle().equals(title))) {
                nodeRepo.deleteById(node.getId());
            }
            else if (node.getSources() != null && node.getSources().contains(path)) {
                List<String> sources = new ArrayList<>(node.getSources());
                sources.remove(path);
                node.setSources(sources);
                String fullMarkdown = WikiFrontmatter.of(node.getTitle(), node.getPageType(), sources, node.getRelated(),
                        node.getTags(), node.getSummary(), node.bodyMarkdown()).fullMarkdown();
                node.applyGeneratedMarkdown(fullMarkdown);
                nodeRepo.save(node);
            }
        }
        updateIndex(space);
        updateOverview(space);
        progress(task, null, "done", "清理完成", 100);
    }

    private void doResearch(WikiIngestTask task, Consumer<WikiIngestProgress> progress, BooleanSupplier isCancelled) {
        WikiSpace space = spaceRepo.findById(task.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        String parsedTopic = "";
        List<String> queries = List.of();
        try {
            JsonNode payload = objectMapper.readTree(task.getPayloadJson());
            parsedTopic = payload.path("topic").asText("");
            queries = stringList(payload, "queries");
        }
        catch (Exception ignored) {
            // 忽略 payload 解析错误
        }
        final String topic = parsedTopic;
        progress(task, null, "search", "网络搜索资料", 20);
        throwIfCancelled(isCancelled);
        WikiWebSearchConfig webConfig = new WikiWebSearchConfig(space.getWebSearchProviderCode(), space.getWebSearchApiKey(),
                space.getWebSearchInstanceUrl(), space.getWebSearchEngine(), "general");
        List<WikiWebSearchResult> results = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String query : queries) {
            throwIfCancelled(isCancelled);
            for (WikiWebSearchResult result : webSearchGateway.search(webConfig, query, 4)) {
                if (seen.add(result.url())) {
                    results.add(result);
                }
            }
        }
        progress(task, null, "synthesize", "LLM 综合研究结果", 60);
        String body = synthesizeResearch(space, topic, results);
        String slug = WikiSlug.derive(topic);
        WikiNode node = nodeRepo.findBySlug(space.getId(), slug)
                .orElseGet(() -> WikiNode.page(space.getId(), null, topic, slug, 0));
        node.applyGeneratedMarkdown(WikiFrontmatter.of(topic, WikiPageType.RESEARCH,
                results.stream().map(WikiWebSearchResult::url).toList(), List.of(), List.of("research"),
                "关于「" + topic + "」的深度研究", body).fullMarkdown());
        Long nodeId = nodeRepo.save(node).getId();
        updateIndex(space);
        updateOverview(space);
        progress(task, null, "publish", "发布研究页面", 85);
        publishPages(List.of(nodeId));
        progress(task, null, "done", "深度研究完成", 100);
    }

    private void doReindex(WikiIngestTask task, Consumer<WikiIngestProgress> progress) {
        WikiSpace space = spaceRepo.findById(task.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        progress(task, null, "rebuild", "重建 index/overview", 20);
        updateIndex(space);
        updateOverview(space);
        progress(task, null, "reindex", "重新构建向量索引", 50);
        List<Long> nodeIds = nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE)
                .map(WikiNode::getId).toList();
        publishPages(nodeIds);
        progress(task, null, "done", "重建完成", 100);
    }

    // ---------- 两步思维链 ----------

    private JsonNode analyze(WikiSpace space, WikiSource source) {
        String system = "你是知识库维护引擎的分析器。你只输出 JSON，不输出任何解释或 Markdown。";
        String user = "知识库目的(purpose)：\n" + blankToEmpty(space.getPurpose()) + "\n\n"
                + "结构规则(schema)：\n" + blankToEmpty(space.getSchemaContent()) + "\n\n"
                + "现有内容目录(index)：\n" + existingIndex(space) + "\n\n"
                + "原始资料标题：" + source.getTitle() + "\n"
                + "原始资料路径：" + source.displayPath() + "\n\n"
                + "原始资料内容(节选)：\n" + truncate(source.getExtractedText(), SOURCE_CONTEXT_LIMIT) + "\n\n"
                + "请分析该资料并输出如下 JSON：\n"
                + "{\"entities\":[\"关键实体\"],\"concepts\":[\"关键概念\"],\"contradictions\":[\"与现有知识的矛盾或张力\"],"
                + "\"structureSuggestions\":[\"Wiki 结构建议\"],"
                + "\"reviewItems\":[{\"type\":\"CREATE_PAGE|DEEP_RESEARCH|SKIP|FLAG\",\"title\":\"\",\"description\":\"\",\"suggestedAction\":\"\",\"searchQueries\":[\"\"]}],"
                + "\"researchQueries\":[\"值得深入研究的搜索查询\"]}";
        return generateJson(system, user, space.getIngestProviderCode(), space.getIngestModelCode());
    }

    private JsonNode generate(WikiSpace space, WikiSource source, JsonNode analysis) {
        String system = "你是知识库维护引擎的生成器。你只输出 JSON，不输出任何解释或 Markdown。";
        String user = "知识库目的：\n" + blankToEmpty(space.getPurpose()) + "\n\n"
                + "结构规则：\n" + blankToEmpty(space.getSchemaContent()) + "\n\n"
                + "原始资料标题：" + source.getTitle() + "\n"
                + "原始资料路径：" + source.displayPath() + "\n\n"
                + "第一步分析结果：\n" + analysis + "\n\n"
                + "现有页面列表：\n" + existingPagesSummary(space) + "\n\n"
                + "请基于分析结果生成 Wiki 页面，输出如下 JSON：\n"
                + "{\"pages\":[{\"title\":\"页面标题\",\"type\":\"source_summary|entity|concept|synthesis|comparison\","
                + "\"summary\":\"一句话摘要\",\"sources\":[\"" + source.displayPath() + "\"],"
                + "\"related\":[\"其他页面标题\"],\"tags\":[\"标签\"],\"body\":\"Markdown 正文，使用 [[页面标题]] 交叉引用\"}],"
                + "\"reviewItems\":[{\"type\":\"CREATE_PAGE|DEEP_RESEARCH|SKIP|FLAG\",\"title\":\"\",\"description\":\"\",\"suggestedAction\":\"\",\"searchQueries\":[\"\"]}]}\n\n"
                + "必须包含至少一个 type 为 source_summary 的资料摘要页面。";
        return generateJson(system, user, space.getIngestProviderCode(), space.getIngestModelCode());
    }

    private JsonNode generateJson(String system, String user, String provider, String model) {
        AiGenerationRequest request = new AiGenerationRequest(system, user, null, provider, model, aiConfig())
                .withStructuredOutput(AiStructuredOutput.jsonObject());
        AiGenerationResult result = aiGeneration.generate(request);
        String text = result == null ? null : result.summary();
        if (text == null || text.isBlank()) {
            throw new BizException("LLM 未返回结构化结果");
        }
        try {
            return objectMapper.readTree(stripFence(text));
        }
        catch (Exception exception) {
            throw new BizException("LLM 结构化输出解析失败");
        }
    }

    // ---------- 页面应用 ----------

    private List<Long> applyPages(WikiSpace space, WikiSource source, JsonNode generated) {
        List<Long> touched = new ArrayList<>();
        boolean hasSourceSummary = false;
        JsonNode pages = generated.path("pages");
        if (pages.isArray()) {
            for (JsonNode page : pages) {
                String title = text(page, "title");
                if (title.isBlank()) {
                    continue;
                }
                WikiPageType type = parsePageType(text(page, "type"));
                if (type == WikiPageType.SOURCE_SUMMARY) {
                    hasSourceSummary = true;
                }
                touched.add(saveGeneratedPage(space, title, type, stringList(page, "sources"),
                        stringList(page, "related"), stringList(page, "tags"), text(page, "summary"), text(page, "body")));
            }
        }
        if (!hasSourceSummary) {
            touched.add(ensureSourceSummary(space, source));
        }
        // 原文档页面：完整保留原始资料正文（含图片），排序置顶，作为知识库的入口了解页
        touched.add(ensureSourceDocument(space, source));
        return touched;
    }

    /** 每个资料源对应一个稳定的原文档页面（slug 按资料 ID），重复摄入时原地更新。 */
    private Long ensureSourceDocument(WikiSpace space, WikiSource source) {
        String slug = "source-" + source.getId();
        WikiNode node = nodeRepo.findBySlug(space.getId(), slug)
                .orElseGet(() -> WikiNode.page(space.getId(), null, source.getTitle(), slug, -100));
        node.applyGeneratedMarkdown(WikiFrontmatter.of(source.getTitle(), WikiPageType.SOURCE_DOCUMENT,
                List.of(source.displayPath()), List.of(), List.of("原文档"),
                "原始资料原文：" + source.getTitle(), source.getExtractedText()).fullMarkdown());
        return nodeRepo.save(node).getId();
    }

    private Long saveGeneratedPage(WikiSpace space, String title, WikiPageType type, List<String> sources,
                                   List<String> related, List<String> tags, String summary, String body) {
        String slug = WikiSlug.derive(title);
        WikiNode node = nodeRepo.findBySlug(space.getId(), slug)
                .orElseGet(() -> WikiNode.page(space.getId(), null, title, slug, 0));
        node.applyGeneratedMarkdown(WikiFrontmatter.of(title, type, sources, related, tags, summary, body).fullMarkdown());
        return nodeRepo.save(node).getId();
    }

    private Long ensureSourceSummary(WikiSpace space, WikiSource source) {
        String body = truncate(source.getExtractedText(), FALLBACK_BODY_LIMIT);
        String summary = truncate(source.getExtractedText(), 200);
        return saveGeneratedPage(space, source.getTitle(), WikiPageType.SOURCE_SUMMARY, List.of(source.displayPath()),
                List.of(), List.of("source"), summary, body);
    }

    private void updateIndex(WikiSpace space) {
        Map<WikiPageType, List<WikiNode>> grouped = nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE && node.getPageType() != null
                        && !SPECIAL_TYPES.contains(node.getPageType()))
                .collect(Collectors.groupingBy(WikiNode::getPageType));
        StringBuilder index = new StringBuilder("# 内容目录\n\n");
        for (WikiPageType type : WikiPageType.values()) {
            List<WikiNode> nodes = grouped.getOrDefault(type, List.of());
            if (nodes.isEmpty()) {
                continue;
            }
            index.append("## ").append(type.label()).append("\n");
            for (WikiNode node : nodes) {
                index.append("- [[").append(node.getTitle()).append("]] — ").append(blankToEmpty(node.getSummary())).append('\n');
            }
            index.append('\n');
        }
        saveSpecialPage(space, "index", "内容目录", WikiPageType.INDEX, index.toString());
    }

    private void updateOverview(WikiSpace space) {
        Map<WikiPageType, List<WikiNode>> grouped = nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE && node.getPageType() != null
                        && !SPECIAL_TYPES.contains(node.getPageType()))
                .collect(Collectors.groupingBy(WikiNode::getPageType));
        StringBuilder overview = new StringBuilder("# 全局概要\n\n");
        for (WikiPageType type : WikiPageType.values()) {
            List<WikiNode> nodes = grouped.getOrDefault(type, List.of());
            if (nodes.isEmpty()) {
                continue;
            }
            overview.append("## ").append(type.label()).append("\n");
            for (WikiNode node : nodes) {
                if (node.getSummary() != null && !node.getSummary().isBlank()) {
                    overview.append("- **").append(node.getTitle()).append("**：").append(node.getSummary()).append('\n');
                }
            }
            overview.append('\n');
        }
        saveSpecialPage(space, "overview", "全局概要", WikiPageType.OVERVIEW, overview.toString());
    }

    private void appendLog(WikiSpace space, WikiSource source) {
        String slug = "log";
        WikiNode node = nodeRepo.findBySlug(space.getId(), slug)
                .orElseGet(() -> WikiNode.page(space.getId(), null, "操作日志", slug, 0));
        String existing = node.bodyMarkdown();
        String entry = "## [" + LocalDate.now() + "] ingest | " + source.getTitle() + "\n";
        String body = (existing == null || existing.isBlank() ? "" : existing.trim() + "\n\n") + entry;
        node.savePage("操作日志", WikiPageType.LOG, List.of(), List.of(), List.of(), "", body);
        nodeRepo.save(node);
    }

    private void saveSpecialPage(WikiSpace space, String slug, String title, WikiPageType type, String body) {
        WikiNode node = nodeRepo.findBySlug(space.getId(), slug)
                .orElseGet(() -> WikiNode.page(space.getId(), null, title, slug, 0));
        node.savePage(title, type, List.of(), List.of(), List.of(), "", body);
        nodeRepo.save(node);
    }

    private void saveReviewItems(WikiSpace space, WikiSource source, JsonNode analysis, JsonNode generated) {
        saveReviewItems(space, source, analysis);
        saveReviewItems(space, source, generated);
    }

    private void saveReviewItems(WikiSpace space, WikiSource source, JsonNode node) {
        if (node == null || !node.path("reviewItems").isArray()) {
            return;
        }
        for (JsonNode item : node.path("reviewItems")) {
            String title = text(item, "title");
            if (title.isBlank()) {
                continue;
            }
            reviewItemRepo.save(WikiReviewItem.create(space.getId(), source.getId(),
                    parseReviewType(text(item, "type")), title, text(item, "description"),
                    text(item, "suggestedAction"), stringList(item, "searchQueries"), List.of()));
        }
    }

    private void publishPages(List<Long> nodeIds) {
        for (Long nodeId : nodeIds) {
            try {
                publicationAppService.publish(nodeId);
            }
            catch (Exception exception) {
                log.warn("发布页面 {} 失败：{}", nodeId, exception.getMessage());
            }
        }
    }

    // ---------- 深度研究合成 ----------

    private String synthesizeResearch(WikiSpace space, String topic, List<WikiWebSearchResult> results) {
        String system = "你是深度研究助手，用中文撰写结构化的 Markdown 研究页面。";
        StringBuilder evidence = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            WikiWebSearchResult result = results.get(i);
            evidence.append("[").append(i + 1).append("] ").append(result.title()).append("\n")
                    .append(result.url()).append("\n")
                    .append(truncate(result.content(), 6_000)).append("\n\n");
        }
        String user = "研究主题：" + topic + "\n\n"
                + "知识库目的：\n" + blankToEmpty(space.getPurpose()) + "\n\n"
                + "搜索结果：\n" + evidence + "\n\n"
                + "请综合以上资料，产出一篇有交叉引用、带小标题的中文研究页面，并在关键结论处标注来源编号 [n]。";
        AiGenerationRequest request = new AiGenerationRequest(system, user, null,
                space.getIngestProviderCode(), space.getIngestModelCode(), aiConfig());
        AiGenerationResult result = aiGeneration.generate(request);
        return result == null || result.summary() == null ? "研究主题：" + topic : result.summary();
    }

    // ---------- 工具方法 ----------

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(CapabilityModule::getConfig)
                .orElseThrow(() -> new BizException("AI 能力未启用"));
    }

    private String existingIndex(WikiSpace space) {
        return nodeRepo.findBySlug(space.getId(), "index").map(WikiNode::bodyMarkdown).orElse("");
    }

    private String existingPagesSummary(WikiSpace space) {
        List<WikiNode> pages = nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE && node.getPageType() != null
                        && !SPECIAL_TYPES.contains(node.getPageType()))
                .limit(100).toList();
        if (pages.isEmpty()) {
            return "（暂无现有页面）";
        }
        return pages.stream().map(node -> "- " + node.getTitle() + "（" + node.getPageType().name() + "）")
                .collect(Collectors.joining("\n"));
    }

    private Consumer<WikiIngestProgress> currentProgress;

    private void progress(WikiIngestTask task, WikiSource source, String phase, String message, int percent) {
        if (currentProgress != null) {
            currentProgress.accept(new WikiIngestProgress(task.getId(), task.getSpaceId(),
                    source == null ? null : source.getId(), phase, message, percent, false));
        }
    }

    private void throwIfCancelled(BooleanSupplier isCancelled) {
        if (isCancelled.getAsBoolean()) {
            throw new BizException("任务已取消");
        }
    }

    private String stripFence(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            String withoutOpen = firstNewline >= 0 ? trimmed.substring(firstNewline + 1) : "";
            int close = withoutOpen.lastIndexOf("```");
            if (close >= 0) {
                withoutOpen = withoutOpen.substring(0, close);
            }
            return withoutOpen.trim();
        }
        return trimmed;
    }

    private WikiPageType parsePageType(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return WikiPageType.valueOf(normalized);
        }
        catch (IllegalArgumentException ignored) {
            return WikiPageType.CONCEPT;
        }
    }

    private WikiReviewItemType parseReviewType(String value) {
        String normalized = value == null ? "" : value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        try {
            return WikiReviewItemType.valueOf(normalized);
        }
        catch (IllegalArgumentException ignored) {
            return WikiReviewItemType.FLAG;
        }
    }

    private List<String> stringList(JsonNode node, String field) {
        JsonNode array = node.path(field);
        List<String> list = new ArrayList<>();
        if (array.isArray()) {
            array.forEach(item -> {
                String value = item.asText("").trim();
                if (!value.isBlank()) {
                    list.add(value);
                }
            });
        }
        return list;
    }

    private String text(JsonNode node, String field) {
        return node.path(field).asText("").trim();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }
}
