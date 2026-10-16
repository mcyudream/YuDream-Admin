package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.ai.service.AiRunAppService;
import online.yudream.base.domain.platform.ai.service.AiUsageExecutionScope;
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
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiReviewItem;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSource;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiExtractionStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIndexStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskStatus;
import online.yudream.base.domain.platform.wiki.enumerate.WikiIngestTaskType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewItemType;
import online.yudream.base.domain.platform.wiki.enumerate.WikiReviewStatus;
import online.yudream.base.domain.platform.wiki.repo.WikiIngestTaskRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiReviewItemRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSourceRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.service.WikiGeneratedPagePolicy;
import online.yudream.base.domain.platform.wiki.service.WikiIngestCancellationRegistry;
import online.yudream.base.domain.platform.wiki.service.WikiIngestTaskRunner;
import online.yudream.base.domain.platform.wiki.service.WikiWebSearchGateway;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import online.yudream.base.domain.platform.wiki.valobj.WikiIngestProgress;
import online.yudream.base.domain.platform.wiki.valobj.WikiSlug;
import online.yudream.base.domain.platform.wiki.valobj.WikiSourceImage;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchConfig;
import online.yudream.base.domain.platform.wiki.valobj.WikiWebSearchResult;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

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
    /** 进入生成上下文与原文档页的图片证据条数上限。 */
    private static final int MAX_EVIDENCE_IMAGES = 40;
    /** 生成内容的保留一级目录：LLM 生成的知识页只写入该目录，不再与根层级同名节点互相覆盖。 */
    private static final String GENERATED_DIRECTORY_SLUG = "llm-gen";
    private static final String GENERATED_DIRECTORY_TITLE = "LLM 生成";
    private static final Set<WikiPageType> SPECIAL_TYPES = Set.of(WikiPageType.INDEX, WikiPageType.LOG, WikiPageType.OVERVIEW);
    /** 内容型生成页进入 llm-gen；结构页、原文档与运行日志仍由各自系统入口维护。 */
    private static final Set<WikiPageType> GENERATED_PAGE_TYPES = Set.of(WikiPageType.SOURCE_SUMMARY,
            WikiPageType.ENTITY, WikiPageType.CONCEPT, WikiPageType.SYNTHESIS, WikiPageType.COMPARISON,
            WikiPageType.QUERY, WikiPageType.RESEARCH);
    private static final Set<String> SYSTEM_ROOT_SLUGS = Set.of("index", "overview", "log");

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<AiGenerationGateway> aiGeneration;
    private final ObjectProvider<AiRunAppService> runs;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiPageVersionRepo versionRepo;
    private final WikiSourceRepo sourceRepo;
    private final WikiIngestTaskRepo taskRepo;
    private final WikiReviewItemRepo reviewItemRepo;
    private final WikiSourceExtractionService extractionService;
    private final WikiImageCaptionService imageCaptions;
    private final WikiPublicationAppService publicationAppService;
    private final WikiWebSearchGateway webSearchGateway;
    private final WikiIngestCancellationRegistry cancellationRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---------- 队列操作 ----------

    public void enqueueIngest(Long spaceId, Long sourceId) {
        enabled();
        spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        WikiSource source = sourceRepo.findById(sourceId).orElseThrow(() -> new BizException("资料不存在"));
        if (!spaceId.equals(source.getSpaceId())) {
            throw new BizException("资料不属于指定知识库");
        }
        enqueue(WikiIngestTask.create(spaceId, sourceId, WikiIngestTaskType.INGEST, null, System.currentTimeMillis()));
    }

    public void enqueueCleanup(Long spaceId, Long sourceId, String title, String path) {
        enabled();
        try {
            String payload = objectMapper.writeValueAsString(Map.of("title", title == null ? "" : title, "path", path == null ? "" : path));
            enqueue(WikiIngestTask.create(spaceId, sourceId, WikiIngestTaskType.DELETE_CLEANUP, payload, System.currentTimeMillis()));
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
            enqueue(WikiIngestTask.create(spaceId, null, WikiIngestTaskType.DEEP_RESEARCH, payload, System.currentTimeMillis()));
        }
        catch (Exception exception) {
            throw new BizException("创建研究任务失败");
        }
    }

    public void enqueueReindex(Long spaceId) {
        enabled();
        enqueue(WikiIngestTask.create(spaceId, null, WikiIngestTaskType.REINDEX, null, System.currentTimeMillis()));
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
                if (task.getAiRunId() != null) {
                    var runService = runs.getIfAvailable();
                    if (runService != null) runService.cancel(task.getRequestedByUserId() == null ? 0L : task.getRequestedByUserId(), task.getAiRunId());
                }
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
        Long owner = task.getRequestedByUserId() == null ? 0L : task.getRequestedByUserId();
        runs.getObject().execute(owner, "wiki-ingest", "wiki-ingest:" + task.getId(), emitter -> {
            task.setAiRunId(emitter.id());
            taskRepo.save(task);
            currentRun.set(emitter);
            currentProgress.set(progress);
            try (AiUsageExecutionScope ignored = AiUsageExecutionScope.open(owner)) {
                switch (task.getTaskType()) {
                    case INGEST -> doIngest(task, progress, isCancelled);
                    case DELETE_CLEANUP -> doCleanup(task, progress);
                    case DEEP_RESEARCH -> doResearch(task, progress, isCancelled);
                    case REINDEX -> doReindex(task, progress);
                    default -> throw new BizException("未知的摄入任务类型：" + task.getTaskType());
                }
                return Map.of("taskId", String.valueOf(task.getId()), "spaceId", String.valueOf(task.getSpaceId()));
            } finally {
                currentRun.remove();
                currentProgress.remove();
            }
        }, () -> cancellationRegistry.markCancelled(task.getId()));
    }

    private void enqueue(WikiIngestTask task) {
        task.setRequestedByUserId(AiUsageExecutionScope.currentUserId());
        taskRepo.save(task);
    }

    private void doIngest(WikiIngestTask task, Consumer<WikiIngestProgress> progress, BooleanSupplier isCancelled) {
        log.info("doIngest start task={} space={} source={}", task.getId(), task.getSpaceId(), task.getSourceId());
        WikiSpace space = spaceRepo.findById(task.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        WikiSource source = sourceRepo.findById(task.getSourceId()).orElseThrow(() -> new BizException("资料不存在"));
        progress(task, source, "extract", "抽取原始资料文本与图片", 5);
        throwIfCancelled(isCancelled);

        if (source.getExtractionStatus() != WikiExtractionStatus.EXTRACTED) {
            extractionService.extract(source, space, task.getRequestedByUserId(), aiConfig());
            sourceRepo.save(source);
        }
        if (source.getExtractionStatus() == WikiExtractionStatus.FAILED) {
            throw new BizException(source.getExtractionError());
        }
        progress(task, source, "caption", "生成资料图片描述", 12);
        imageCaptions.caption(source, space, task.getRequestedByUserId(), aiConfig());
        sourceRepo.save(source);
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

        progress(task, source, "apply", applyMessage(space), 70);
        throwIfCancelled(isCancelled);
        List<Long> touched = applyPages(space, source, generated);
        Long indexId = updateIndex(space);
        Long overviewId = updateOverview(space);
        Long logId = appendLog(space, source);
        saveReviewItems(space, source, analysis, generated);

        source.markIngested(source.getContentHash());
        sourceRepo.save(source);

        progress(task, source, "publish", "发布页面并构建检索索引", 85);
        // 特殊页（内容目录/全局概要/操作日志）与内容页一起自动发布，避免只生成草稿
        List<Long> toPublish = new ArrayList<>(touched);
        if (indexId != null) {
            toPublish.add(indexId);
        }
        if (overviewId != null) {
            toPublish.add(overviewId);
        }
        if (logId != null) {
            toPublish.add(logId);
        }
        publishPages(toPublish);
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
                // 只移除已删除资料的来源引用，保留原 frontmatter 的其他内容与生成来源标记
                String fullMarkdown = WikiFrontmatter.of(node.getTitle(), node.getPageType(), sources, node.getRelated(),
                        node.getTags(), node.getSummary(), node.bodyMarkdown())
                        .withOrigin(node.frontmatter().origin()).fullMarkdown();
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
        String markdown = WikiFrontmatter.of(topic, WikiPageType.RESEARCH,
                results.stream().map(WikiWebSearchResult::url).toList(), List.of(), List.of("research"),
                "关于「" + topic + "」的深度研究", body).withOrigin(WikiFrontmatter.ORIGIN_LLM).fullMarkdown();
        WikiNode directory = generatedDirectory(space);
        WikiNode existing = generatedChild(space.getId(), directory.getId(), slug);
        Long nodeId;
        if (existing == null) {
            WikiNode node = WikiNode.page(space.getId(), directory.getId(), topic, slug, 0);
            node.setAncestorPath("/" + directory.getId() + "/");
            node.applyGeneratedMarkdown(markdown);
            nodeId = nodeRepo.save(node).getId();
        }
        else if (existing.getNodeType() == WikiNodeType.PAGE
                && WikiGeneratedPagePolicy.mayOverwriteDraft(existing.getMarkdownDraft(), publishedMarkdown(existing))) {
            existing.applyGeneratedMarkdown(markdown);
            nodeId = nodeRepo.save(existing).getId();
        }
        else {
            // 人工编辑过的研究页或同名非页面节点不覆盖，转为审核提案
            proposeGeneratedUpdate(space, null, existing, topic, "关于「" + topic + "」的深度研究",
                    existing.getNodeType() == WikiNodeType.PAGE ? "页面已被人工编辑" : "llm-gen 目录内已存在同名非页面节点");
            nodeId = null;
        }
        Long indexId = updateIndex(space);
        Long overviewId = updateOverview(space);
        progress(task, null, "publish", "发布研究页面", 85);
        List<Long> toPublish = new ArrayList<>();
        if (nodeId != null) {
            toPublish.add(nodeId);
        }
        if (indexId != null) {
            toPublish.add(indexId);
        }
        if (overviewId != null) {
            toPublish.add(overviewId);
        }
        publishPages(toPublish);
        progress(task, null, "done", "深度研究完成", 100);
    }

    private void doReindex(WikiIngestTask task, Consumer<WikiIngestProgress> progress) {
        WikiSpace space = spaceRepo.findById(task.getSpaceId()).orElseThrow(() -> new BizException("知识库不存在"));
        if (space.getEmbeddingProviderCode() == null || space.getEmbeddingProviderCode().isBlank()
                || space.getEmbeddingModelCode() == null || space.getEmbeddingModelCode().isBlank()) {
            throw new BizException("知识库未配置 Embedding Provider 和模型，无法构建向量索引；请先在知识库设置中配置后再重建索引");
        }
        progress(task, null, "rebuild", "重建 index/overview", 20);
        updateIndex(space);
        updateOverview(space);
        progress(task, null, "reindex", "重新构建向量索引", 50);
        List<Long> nodeIds = nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE)
                .map(WikiNode::getId).toList();
        publishPages(nodeIds);
        List<String> failures = indexFailures(nodeIds);
        if (!failures.isEmpty()) {
            throw new BizException("索引重建结束，但 " + failures.size() + "/" + nodeIds.size()
                    + " 个页面索引失败：" + failures.getFirst());
        }
        progress(task, null, "done", "重建完成", 100);
    }

    /** 汇总本次发布后仍处于索引失败的页面及原因 */
    private List<String> indexFailures(List<Long> nodeIds) {
        List<String> failures = new ArrayList<>();
        for (Long nodeId : nodeIds) {
            WikiNode node = nodeRepo.findById(nodeId).orElse(null);
            if (node == null || node.getPublishedVersionId() == null) {
                continue;
            }
            WikiPageVersion version = versionRepo.findById(node.getPublishedVersionId()).orElse(null);
            if (version != null && version.getIndexStatus() == WikiIndexStatus.FAILED) {
                failures.add("《" + node.getTitle() + "》" + (version.getIndexError() == null ? "" : "：" + version.getIndexError()));
            }
        }
        return failures;
    }

    // ---------- 两步思维链 ----------

    private JsonNode analyze(WikiSpace space, WikiSource source) {
        String system = "你是知识库维护引擎的分析器。你只输出 JSON，不输出任何解释或 Markdown。";
        String user = "知识库目的(purpose)：\n" + blankToEmpty(space.getPurpose()) + "\n\n"
                + "结构规则(schema)：\n" + blankToEmpty(space.getSchemaContent()) + "\n\n"
                + "现有内容目录(index)：\n" + existingIndex(space) + "\n\n"
                + "原始资料标题：" + source.getTitle() + "\n"
                + "原始资料路径：" + source.displayPath() + "\n\n"
                + "原始资料图片证据（页码/图序 caption，可作为事实依据）：\n" + imageEvidence(source) + "\n\n"
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
                + "原始资料图片证据（页码/图序 caption，可作为事实依据）：\n" + imageEvidence(source) + "\n\n"
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

    /** 图片证据提示文本：页码/图序 + caption；无 caption 时明确说明，避免模型凭空编造图片内容。 */
    private String imageEvidence(WikiSource source) {
        String lines = imageEvidenceLines(source);
        return lines.isBlank() ? "（本资料没有可用的图片描述）" : lines;
    }

    /** 图片证据行（不含小标题）；caption 不进入生成与页面时返回空串。 */
    private String imageEvidenceLines(WikiSource source) {
        List<WikiSourceImage> images = source.getImages() == null ? List.of() : source.getImages();
        StringBuilder evidence = new StringBuilder();
        int included = 0;
        for (WikiSourceImage image : images) {
            String caption = image == null || image.caption() == null ? "" : image.caption().trim();
            if (caption.isBlank()) {
                continue;
            }
            evidence.append("- ").append(imageLocation(image)).append("：").append(truncate(caption, 400)).append('\n');
            if (++included >= MAX_EVIDENCE_IMAGES) {
                break;
            }
        }
        return evidence.toString();
    }

    /** 定位只有页码与图序（抽取只提供页内资源序号），不承诺版面坐标。 */
    private String imageLocation(WikiSourceImage image) {
        return image.pageNumber() > 0
                ? "第 " + image.pageNumber() + " 页 图 " + (image.sequence() + 1)
                : "图 " + (image.sequence() + 1);
    }

    private JsonNode generateJson(String system, String user, String provider, String model) {
        AiGenerationRequest request = new AiGenerationRequest(system, user, null, provider, model, aiConfig())
                .withStructuredOutput(AiStructuredOutput.jsonObject());
        AiRunAppService.RunEmitter emitter = currentRun.get();
        AiGenerationResult result = aiGeneration.getObject().generateStream(request,
                delta -> { if (emitter != null) emitter.event("ai.structured", "delta", Map.of("content", delta)); },
                delta -> { if (emitter != null) emitter.reasoning(delta); },
                tool -> { if (emitter != null) emitter.tool(tool); },
                activity -> { if (emitter != null) emitter.progress(activity.action(), activity); });
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
                if (!GENERATED_PAGE_TYPES.contains(type)) {
                    proposeInvalidGeneratedType(space, source, title, text(page, "summary"), type);
                    continue;
                }
                if (type == WikiPageType.SOURCE_SUMMARY) {
                    hasSourceSummary = true;
                }
                Long nodeId = saveGeneratedPage(space, source, title, type, stringList(page, "sources"),
                        stringList(page, "related"), stringList(page, "tags"), text(page, "summary"), text(page, "body"));
                if (nodeId != null) {
                    touched.add(nodeId);
                }
            }
        }
        if (!hasSourceSummary) {
            Long summaryId = ensureSourceSummary(space, source);
            if (summaryId != null) {
                touched.add(summaryId);
            }
        }
        // 原文档页面：完整保留原始资料正文（含图片与图片证据），排序置顶，作为知识库的入口了解页
        Long sourceDocumentId = ensureSourceDocument(space, source);
        if (sourceDocumentId != null) {
            touched.add(sourceDocumentId);
        }
        return touched;
    }

    /** 每个资料源对应一个稳定的原文档页面（slug 按资料 ID），固定根层级，不进入 llm-gen。 */
    private Long ensureSourceDocument(WikiSpace space, WikiSource source) {
        String slug = "source-" + source.getId();
        WikiNode node = rootNode(space.getId(), slug);
        boolean existing = node != null;
        if (!existing) {
            node = WikiNode.page(space.getId(), null, source.getTitle(), slug, 0);
        }
        if (node.getNodeType() != WikiNodeType.PAGE) {
            throw new BizException("原文档保留路径 " + slug + " 已被非页面节点占用，资料页面未写入");
        }
        String markdown = WikiFrontmatter.of(source.getTitle(), WikiPageType.SOURCE_DOCUMENT,
                List.of(source.displayPath()), List.of(), List.of("原文档"),
                "原始资料原文：" + source.getTitle(), sourceDocumentBody(source))
                .withOrigin(WikiFrontmatter.ORIGIN_LLM).fullMarkdown();
        if (existing && node.getMarkdownDraft() != null
                && !WikiGeneratedPagePolicy.mayOverwriteDraft(node.getMarkdownDraft(), publishedMarkdown(node))) {
            proposeGeneratedUpdate(space, source, node, source.getTitle(), source.getTitle(),
                    "原文档页已被人工编辑");
            return null;
        }
        // 置顶：WikiNode 工厂对 sort 做了 >=0 钳制，这里显式赋负值让原文档排在最前作为入口了解页
        node.setSort(-100);
        node.applyGeneratedMarkdown(markdown);
        return nodeRepo.save(node).getId();
    }

    /** 原文档页正文：原文 + 图片证据（页码/图序/caption），保证 caption 出现在原始资料页。 */
    private String sourceDocumentBody(WikiSource source) {
        String text = source.getExtractedText() == null ? "" : source.getExtractedText();
        String evidence = imageEvidenceLines(source);
        if (evidence.isBlank()) {
            return text;
        }
        return (text.isBlank() ? "" : text.stripTrailing() + "\n\n") + "## 图片证据\n\n" + evidence;
    }

    /**
     * 写入 LLM 生成页面；返回 null 表示目标页面不可覆盖（人工编辑/同名冲突），生成候选未写入并转为审核提案。
     * 生成页面只写入保留目录 llm-gen，不触碰根层级或其他目录的同名节点。
     */
    private Long saveGeneratedPage(WikiSpace space, WikiSource source, String title, WikiPageType type,
                                   List<String> sources, List<String> related, List<String> tags,
                                   String summary, String body) {
        String slug = WikiSlug.derive(title);
        String markdown = WikiFrontmatter.of(title, type, sources, related, tags, summary, body)
                .withOrigin(WikiFrontmatter.ORIGIN_LLM).fullMarkdown();
        WikiNode directory = generatedDirectory(space);
        WikiNode node = generatedChild(space.getId(), directory.getId(), slug);
        if (node == null) {
            WikiNode created = WikiNode.page(space.getId(), directory.getId(), title, slug, 0);
            created.setAncestorPath("/" + directory.getId() + "/");
            created.applyGeneratedMarkdown(markdown);
            return nodeRepo.save(created).getId();
        }
        if (node.getNodeType() != WikiNodeType.PAGE) {
            proposeGeneratedUpdate(space, source, node, title, summary, "llm-gen 目录内已存在同名非页面节点");
            return null;
        }
        if (WikiGeneratedPagePolicy.mayOverwriteDraft(node.getMarkdownDraft(), publishedMarkdown(node))) {
            node.applyGeneratedMarkdown(markdown);
            return nodeRepo.save(node).getId();
        }
        proposeGeneratedUpdate(space, source, node, title, summary, "页面已被人工编辑");
        return null;
    }

    /**
     * 生成内容保留目录：按保留 slug 幂等查找/创建。
     * slug 被非根目录节点占用（页面或非根目录）时 fail-closed，拒绝任何生成写入。
     */
    private WikiNode generatedDirectory(WikiSpace space) {
        WikiNode existing = rootNode(space.getId(), GENERATED_DIRECTORY_SLUG);
        if (existing != null) {
            if (existing.getNodeType() != WikiNodeType.DIRECTORY || existing.getParentId() != null) {
                throw new BizException("保留目录 " + GENERATED_DIRECTORY_SLUG + " 已被非根目录节点占用，生成内容未写入；请先调整该节点");
            }
            return existing;
        }
        WikiNode directory = WikiNode.directory(space.getId(), null, GENERATED_DIRECTORY_TITLE, GENERATED_DIRECTORY_SLUG, 1);
        // 排在原文档（-100）之后、普通内容之前
        directory.setSort(-50);
        return nodeRepo.save(directory);
    }

    /** 保留目录内按 slug 查找子节点；路径解析只在这一层进行，不跨目录命中。 */
    private WikiNode generatedChild(Long spaceId, Long directoryId, String slug) {
        return nodeRepo.findBySpaceId(spaceId).stream()
                .filter(node -> directoryId.equals(node.getParentId()) && slug.equals(node.getSlug()))
                .findFirst().orElse(null);
    }

    /** 根层级系统/资料页查找：避免把生成写入误命中其他目录的同名节点。 */
    private WikiNode rootNode(Long spaceId, String slug) {
        return nodeRepo.findBySpaceId(spaceId).stream()
                .filter(node -> node.getParentId() == null && slug.equals(node.getSlug()))
                .findFirst().orElse(null);
    }

    /**
     * 旧版把生成页面直接放在根层级；来源仅靠 title/type/来源路径做保守检测，不执行静默迁移。
     * 有无法确认来源的根层级业务页也提示人工整理，避免首次升级悄悄复制出一套重复页面。
     */
    private int legacyGeneratedCount(WikiSpace space) {
        return (int) nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE && node.getParentId() == null)
                .filter(node -> !SYSTEM_ROOT_SLUGS.contains(node.getSlug()))
                .filter(node -> node.getPageType() == null || GENERATED_PAGE_TYPES.contains(node.getPageType()))
                .count();

    }

    private String applyMessage(WikiSpace space) {
        int legacy = legacyGeneratedCount(space);
        if (legacy <= 0) {
            return "写入生成页面到 " + GENERATED_DIRECTORY_SLUG + " 并维护 index/log/overview";
        }
        log.warn("知识库 {} 存在 {} 个根层级旧生成页面；本次不做静默迁移，保持原路径与 URL 不变", space.getId(), legacy);
        return "写入生成页面到 " + GENERATED_DIRECTORY_SLUG + "；检测到 " + legacy + " 个根层级旧生成页面，保持原路径未迁移";
    }

    /** 已发布版本全文；页面未发布返回 null，发布版本缺失返回空串（不可确认，按保护处理）。 */
    private String publishedMarkdown(WikiNode node) {
        if (node.getPublishedVersionId() == null) {
            return null;
        }
        return versionRepo.findById(node.getPublishedVersionId()).map(WikiPageVersion::getMarkdown).orElse("");
    }

    /** 不可覆盖：生成候选不落盘，登记待人工确认的审核提案（同知识库同标题去重）。 */
    private void proposeGeneratedUpdate(WikiSpace space, WikiSource source, WikiNode node, String title, String summary,
                                        String reason) {
        String effectiveTitle = title == null || title.isBlank() ? node.getTitle() : title;
        boolean exists = reviewItemRepo.findBySpaceIdAndStatus(space.getId(), WikiReviewStatus.PENDING).stream()
                .anyMatch(item -> item.getItemType() == WikiReviewItemType.FLAG && effectiveTitle.equals(item.getTitle()));
        if (exists) {
            return;
        }
        reviewItemRepo.save(WikiReviewItem.create(space.getId(), source == null ? null : source.getId(),
                WikiReviewItemType.FLAG, effectiveTitle,
                reason + "，本次 LLM 生成结果未写入。候选摘要：" + blankToEmpty(summary),
                "请人工确认：合并生成内容或保留当前版本；生成链路不会自动覆盖无法确认归属的页面。",
                List.of(), List.of(effectiveTitle)));
    }

    private void proposeInvalidGeneratedType(WikiSpace space, WikiSource source, String title, String summary,
                                             WikiPageType type) {
        boolean exists = reviewItemRepo.findBySpaceIdAndStatus(space.getId(), WikiReviewStatus.PENDING).stream()
                .anyMatch(item -> item.getItemType() == WikiReviewItemType.FLAG && title.equals(item.getTitle()));
        if (exists) {
            return;
        }
        reviewItemRepo.save(WikiReviewItem.create(space.getId(), source.getId(), WikiReviewItemType.FLAG, title,
                "LLM 返回了不允许由生成流程创建的页面类型 " + type + "，候选内容未写入。候选摘要：" + blankToEmpty(summary),
                "请人工检查候选内容并修正页面类型后重新摄入。", List.of(), List.of(title)));
    }
    private Long ensureSourceSummary(WikiSpace space, WikiSource source) {
        String body = truncate(source.getExtractedText(), FALLBACK_BODY_LIMIT);
        String summary = truncate(source.getExtractedText(), 200);
        return saveGeneratedPage(space, source, source.getTitle(), WikiPageType.SOURCE_SUMMARY,
                List.of(source.displayPath()), List.of(), List.of("source"), summary, body);
    }


    private Long updateIndex(WikiSpace space) {
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
        return saveSpecialPage(space, "index", "内容目录", WikiPageType.INDEX, index.toString());
    }

    private Long updateOverview(WikiSpace space) {
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
        return saveSpecialPage(space, "overview", "全局概要", WikiPageType.OVERVIEW, overview.toString());
    }

    private Long appendLog(WikiSpace space, WikiSource source) {
        String slug = "log";
        WikiNode node = rootNode(space.getId(), slug);
        if (node == null) {
            node = WikiNode.page(space.getId(), null, "操作日志", slug, 0);
        }
        String existing = node.bodyMarkdown();
        String entry = "## [" + LocalDate.now() + "] ingest | " + source.getTitle() + "\n";
        String body = (existing == null || existing.isBlank() ? "" : existing.trim() + "\n\n") + entry;
        node.applyGeneratedMarkdown(WikiFrontmatter.of("操作日志", WikiPageType.LOG, List.of(), List.of(),
                List.of(), "", body).withOrigin(WikiFrontmatter.ORIGIN_LLM).fullMarkdown());
        return nodeRepo.save(node).getId();
    }

    private Long saveSpecialPage(WikiSpace space, String slug, String title, WikiPageType type, String body) {
        WikiNode node = rootNode(space.getId(), slug);
        if (node == null) {
            node = WikiNode.page(space.getId(), null, title, slug, 0);
        }
        node.applyGeneratedMarkdown(WikiFrontmatter.of(title, type, List.of(), List.of(), List.of(), "", body)
                .withOrigin(WikiFrontmatter.ORIGIN_LLM).fullMarkdown());
        return nodeRepo.save(node).getId();
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
        AiRunAppService.RunEmitter emitter = currentRun.get();
        AiGenerationResult result = aiGeneration.getObject().generateStream(request,
                delta -> { if (emitter != null) emitter.content(delta); },
                delta -> { if (emitter != null) emitter.reasoning(delta); },
                tool -> { if (emitter != null) emitter.tool(tool); },
                activity -> { if (emitter != null) emitter.progress(activity.action(), activity); });
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

    private final ThreadLocal<Consumer<WikiIngestProgress>> currentProgress = new ThreadLocal<>();
    private final ThreadLocal<AiRunAppService.RunEmitter> currentRun = new ThreadLocal<>();


    private void progress(WikiIngestTask task, WikiSource source, String phase, String message, int percent) {
        if (currentRun.get() != null) currentRun.get().progress(phase,
                Map.of("title", message, "content", message, "percent", percent, "status", percent == 100 ? "complete" : "running"));
        if (currentProgress.get() != null) {
            currentProgress.get().accept(new WikiIngestProgress(task.getId(), task.getSpaceId(),
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
