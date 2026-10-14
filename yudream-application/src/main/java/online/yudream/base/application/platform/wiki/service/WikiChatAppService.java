package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiChatActivityAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.service.AiAgentToolExecutionScope;
import online.yudream.base.domain.platform.ai.service.HarnessAgentGateway;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.ai.valobj.AiChatMessage;
import online.yudream.base.domain.platform.ai.valobj.AiUsage;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunRequest;
import online.yudream.base.domain.platform.ai.valobj.HarnessRunResult;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiPageVersion;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiPageVersionRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiFrontmatter;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Wiki 问答：先检索注入上下文（RAG），LLM 仍可通过 wiki.search 原生工具深挖；回答带页面引用。
 * 生成走 Harness 会话代理：带 (userId, sessionId) 的调用获得框架管理的跨轮次上下文；
 * 匿名/一次性调用无状态运行（history 由调用方显式提供）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WikiChatAppService {

    /** 管理端检索工具（wiki.search 有权限码实现）的模型侧安全名 */
    static final String ADMIN_TOOL_NAME = "wiki_search";
    /** 公开检索工具（wiki.search 无权限码实现）的模型侧派生别名 */
    static final String PUBLIC_TOOL_NAME = "wiki_search_public";

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final ObjectProvider<HarnessAgentGateway> harnessAgents;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final WikiPageVersionRepo versionRepo;
    private final WikiSearchAiTool wikiSearchTool;
    private final WikiPublicSearchAiTool publicSearchTool;
    private final WikiSearchAppService searchService;

    public WikiChatResultDTO chat(Long spaceId, String question, List<AiChatMessage> history) {
        Prepared prepared = prepare(spaceId, question, history, null);
        return generateAndFinish(prepared, wikiSearchTool, ADMIN_TOOL_NAME, "platform:wiki:tool:search",
                null, null, null, null, null, null);
    }

    /**
     * 流式问答：onDelta 推送回答增量，onTool 推送工具调用（如 wiki.search 检索），返回最终结果（含引用）。
     */
    public WikiChatResultDTO chatStream(Long spaceId, String question, List<AiChatMessage> history,
                                        Consumer<String> onDelta, Consumer<AiAgentToolResult> onTool) {
        return chatStream(spaceId, question, history, onDelta, null, onTool, null);
    }

    /**
     * 流式问答（含结构化过程事件）：onActivity 推送 retrieve/wiki-retrieval/wiki-graph/generate/complete 活动。
     */
    public WikiChatResultDTO chatStream(Long spaceId, String question, List<AiChatMessage> history,
                                        Consumer<String> onDelta, Consumer<AiAgentToolResult> onTool,
                                        Consumer<WikiChatActivityDTO> onActivity) {
        return chatStream(spaceId, question, history, onDelta, null, onTool, onActivity);
    }

    /**
     * 管理端流式问答，分别转发正文、真实 reasoning、工具和结构化过程事件。
     * 提供 userId+sessionId 时走有状态 Harness 会话（跨轮次上下文由框架管理），否则无状态。
     */
    public WikiChatResultDTO chatStream(Long spaceId, String question, List<AiChatMessage> history,
                                        Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                        Consumer<AiAgentToolResult> onTool,
                                        Consumer<WikiChatActivityDTO> onActivity) {
        return chatStream(spaceId, null, null, question, history, onDelta, onReasoningDelta, onTool, onActivity);
    }

    /**
     * 管理端流式问答（可带会话身份）。
     */
    public WikiChatResultDTO chatStream(Long spaceId, Long userId, String sessionId, String question,
                                        List<AiChatMessage> history,
                                        Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                        Consumer<AiAgentToolResult> onTool,
                                        Consumer<WikiChatActivityDTO> onActivity) {
        return chatStream(spaceId, userId, sessionId, question, history, List.of(), onDelta,
                onReasoningDelta, onTool, onActivity);
    }

    public WikiChatResultDTO chatStream(Long spaceId, Long userId, String sessionId, String question,
                                        List<AiChatMessage> history, List<String> permissionCodes,
                                        Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                        Consumer<AiAgentToolResult> onTool,
                                        Consumer<WikiChatActivityDTO> onActivity) {
        Prepared prepared = prepare(spaceId, question, history, onActivity);
        return generateAndFinish(prepared, wikiSearchTool, ADMIN_TOOL_NAME,
                "platform:wiki:tool:search", userId, sessionId, onDelta, onReasoningDelta, onTool, onActivity,
                permissionCodes);
    }

    /**
     * 公开站点流式问答：按 slug 解析公开知识库，仅检索已发布内容。
     */
    public WikiChatResultDTO chatStreamBySlug(String slug, String question, List<AiChatMessage> history,
                                              Consumer<String> onDelta, Consumer<AiAgentToolResult> onTool) {
        return chatStreamBySlug(slug, question, history, onDelta, onTool, null);
    }

    /**
     * 公开站点流式问答（含结构化过程事件）。
     */
    public WikiChatResultDTO chatStreamBySlug(String slug, String question, List<AiChatMessage> history,
                                              Consumer<String> onDelta, Consumer<AiAgentToolResult> onTool,
                                              Consumer<WikiChatActivityDTO> onActivity) {
        return chatStreamBySlug(slug, question, history, onDelta, null, onTool, onActivity);
    }

    /**
     * 公开站点流式问答，分别转发正文、reasoning、工具和结构化过程事件。
     * 提供 userId+sessionId 时走有状态 Harness 会话，否则无状态（匿名调用必须无状态）。
     */
    public WikiChatResultDTO chatStreamBySlug(String slug, String question, List<AiChatMessage> history,
                                              Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                              Consumer<AiAgentToolResult> onTool,
                                              Consumer<WikiChatActivityDTO> onActivity) {
        return chatStreamBySlug(slug, null, null, question, history, onDelta, onReasoningDelta, onTool, onActivity);
    }

    /**
     * 公开站点流式问答（可带会话身份）。
     */
    public WikiChatResultDTO chatStreamBySlug(String slug, Long userId, String sessionId, String question,
                                              List<AiChatMessage> history,
                                              Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                              Consumer<AiAgentToolResult> onTool,
                                              Consumer<WikiChatActivityDTO> onActivity) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaceRepo.findBySlug(slug).orElseThrow(() -> new BizException("知识库不存在"));
        if (!space.isPublicReadEnabled()) {
            throw new BizException("该知识库未开放公开阅读");
        }
        Prepared prepared = prepare(space, question, history, true, onActivity);
        return generateAndFinish(prepared, publicSearchTool, PUBLIC_TOOL_NAME, null, userId, sessionId,
                onDelta, onReasoningDelta, onTool, onActivity);
    }

    private WikiChatResultDTO generateAndFinish(Prepared prepared, AiAgentTool searchTool, String allowedToolName,
                                                String toolPermissionCode, Long userId, String sessionId,
                                                Consumer<String> onDelta,
                                                Consumer<AiAgentToolResult> onTool,
                                                Consumer<WikiChatActivityDTO> onActivity) {
        return generateAndFinish(prepared, searchTool, allowedToolName, toolPermissionCode, userId, sessionId,
                onDelta, null, onTool, onActivity);
    }

    private WikiChatResultDTO generateAndFinish(Prepared prepared, AiAgentTool searchTool, String allowedToolName,
                                                String toolPermissionCode, Long userId, String sessionId,
                                                Consumer<String> onDelta,
                                                Consumer<String> onReasoningDelta,
                                                Consumer<AiAgentToolResult> onTool,
                                                Consumer<WikiChatActivityDTO> onActivity) {
        return generateAndFinish(prepared, searchTool, allowedToolName, toolPermissionCode, userId, sessionId,
                onDelta, onReasoningDelta, onTool, onActivity, List.of());
    }

    private WikiChatResultDTO generateAndFinish(Prepared prepared, AiAgentTool searchTool, String allowedToolName,
                                                String toolPermissionCode, Long userId, String sessionId,
                                                Consumer<String> onDelta, Consumer<String> onReasoningDelta,
                                                Consumer<AiAgentToolResult> onTool,
                                                Consumer<WikiChatActivityDTO> onActivity,
                                                List<String> permissionCodes) {
        emitActivity(onActivity, progress("generate", "running", "生成回答", "正在生成回答…"));
        StringBuilder reasoning = new StringBuilder();
        Consumer<String> reasoningConsumer = delta -> {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            reasoning.append(delta);
            if (onReasoningDelta != null) {
                onReasoningDelta.accept(delta);
            }
        };
        boolean stateful = sessionId != null && !sessionId.isBlank();
        HarnessRunRequest request = new HarnessRunRequest(
                userId,
                stateful ? "wiki-" + prepared.spaceKey() + "-" + sessionId : null,
                "wiki-qa",
                prepared.systemPrompt(),
                prepared.question(),
                List.of(),
                prepared.providerCode(),
                prepared.modelCode(),
                prepared.config(),
                true,
                permissionCodes == null ? List.of() : permissionCodes,
                stateful ? List.of() : prepared.history(),
                List.of(allowedToolName));
        HarnessRunResult result;
        try (AiAgentToolExecutionScope ignored = AiAgentToolExecutionScope.open(List.of(searchTool))) {
            result = harnessAgents.getObject().run(request, onDelta, reasoningConsumer, onTool,
                    progress -> emitActivity(onActivity, progress(progress.action(), "running", "模型运行", progress.content())));
        }
        emitActivity(onActivity, progress("complete", "completed", "回答完成", "回答已生成。"));
        return toResult(result, reasoning.toString(), prepared.hits());
    }

    private record Prepared(String spaceKey, String systemPrompt, String question, String providerCode,
                            String modelCode, Map<String, String> config, List<AiChatMessage> history,
                            List<WikiSearchHitDTO> hits) {
    }

    private Prepared prepare(Long spaceId, String question, List<AiChatMessage> history,
                             Consumer<WikiChatActivityDTO> onActivity) {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
        WikiSpace space = spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        return prepare(space, question, history, false, onActivity);
    }

    private Prepared prepare(WikiSpace space, String question, List<AiChatMessage> history, boolean publicSite,
                             Consumer<WikiChatActivityDTO> onActivity) {
        if (question == null || question.isBlank()) {
            throw new BizException("问题不能为空");
        }
        if (blank(space.getChatProviderCode()) || blank(space.getChatModelCode())) {
            throw new BizException("该知识库未配置 Chat 模型");
        }

        emitActivity(onActivity, progress("retrieve", "running", "检索知识库", "正在检索知识库…"));
        List<WikiSearchHitDTO> rawHits;
        boolean retrievalFailed = false;
        String failureReason = null;
        try {
            rawHits = retrieveHits(space, question, publicSite);
        }
        catch (Exception exception) {
            log.warn("问答预检索失败：{}", exception.getMessage());
            rawHits = List.of();
            retrievalFailed = true;
            failureReason = exception.getMessage();
        }

        // 公开场景只保留已发布 PAGE 命中；非 PAGE/SOURCE 及未发布页均不外泄，也不进入 prompt 与引用。
        List<WikiNode> pages = (publicSite || onActivity != null) ? visiblePages(space, publicSite) : List.of();
        List<WikiSearchHitDTO> hits = filterVisibleHits(rawHits, pages, publicSite);

        if (onActivity != null) {
            emitActivity(onActivity, retrieval(question, hits, pages, retrievalFailed, failureReason, publicSite));
            emitActivity(onActivity, graph(question, hits, pages));
        }

        String system = "你是「" + space.getName() + "」知识库的问答助手。规则：\n"
                + "1. 下面的「知识库相关内容」就是答案来源，直接基于它们组织答案；如需更多细节可调用 wiki.search 工具（spaceSlug="
                + space.getSlug() + "）进一步检索。\n"
                + "2. 只有当「知识库相关内容」确实与问题无关时，才说明知识库没有相关内容；否则务必基于给出的内容作答。\n"
                + "3. 在答案中引用页面时用 [[页面标题]] 标注。\n"
                + "4. 用中文回答。\n"
                + "5. 正文使用标准 Markdown，段落之间保留空行，列表、标题、引用和代码块使用规范语法，不输出未闭合标记。\n"
                + "6. 若内容中给出了「相关图片」，在回答的合适位置用 Markdown 图片语法原样引用展示（保留给出的图片地址与说明）。\n\n"
                + "知识库目的：\n" + nullToEmpty(space.getPurpose()) + "\n\n"
                + "知识库相关内容：\n" + contextOf(hits);
        return new Prepared(space.getSlug(), system, question, space.getChatProviderCode(),
                space.getChatModelCode(), aiConfig(), history == null ? List.of() : history, hits);
    }

    private WikiChatResultDTO toResult(HarnessRunResult result, String reasoning, List<WikiSearchHitDTO> retrieved) {
        Map<String, WikiChatResultDTO.Citation> citations = new LinkedHashMap<>();
        for (WikiChatResultDTO.Citation citation : extractCitations(result == null ? null : result.toolResults())) {
            citations.putIfAbsent(citation.title() + "|" + citation.path(), citation);
        }
        for (WikiSearchHitDTO hit : retrieved) {
            if (hit == null || hit.getTitle() == null || hit.getTitle().isBlank()) {
                continue;
            }
            String key = hit.getTitle() + "|" + (hit.getPath() == null ? "" : hit.getPath());
            citations.putIfAbsent(key, WikiChatResultDTO.Citation.builder()
                    .title(hit.getTitle())
                    .path(hit.getPath())
                    .nodeId(hit.getNodeId())
                    .excerpt(truncate(hit.getContent(), 800))
                    .images(hitImages(hit))
                    .build());
        }
        String answer = result == null ? "" : result.content();
        return WikiChatResultDTO.builder()
                .answer(answer)
                .reasoning(reasoning)
                .citations(new ArrayList<>(citations.values()))
                .usage(result == null ? AiUsage.empty() : result.usage())
                .build();
    }

    private List<WikiSearchHitDTO> retrieveHits(WikiSpace space, String question, boolean publicSite) {
        List<WikiSearchHitDTO> hits = publicSite
                ? searchService.searchForPublicSite(space.getSlug(), question, 5, null, false)
                : searchService.searchForAdmin(space.getSlug(), question, 5, null, false);
        return hits == null ? List.of() : hits;
    }

    private List<WikiNode> visiblePages(WikiSpace space, boolean publicSite) {
        List<WikiNode> pages = nodeRepo.findBySpaceId(space.getId()).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE)
                .filter(node -> !publicSite || node.getPublishedVersionId() != null)
                .toList();
        if (!publicSite) {
            return pages;
        }
        // 公开场景一次批量读取发布版本，并校验版本与节点/空间归属，避免 N+1 和错配内容泄漏。
        Map<Long, WikiNode> ownerByVersionId = pages.stream().collect(Collectors.toMap(
                WikiNode::getPublishedVersionId,
                node -> node,
                (left, right) -> left,
                LinkedHashMap::new));
        return materializePublishedPages(pages, versionRepo.findByIds(ownerByVersionId.keySet()), space.getId());
    }

    static List<WikiNode> materializePublishedPages(List<WikiNode> pages, List<WikiPageVersion> versions, Long spaceId) {
        Map<Long, WikiNode> ownerByVersionId = pages.stream()
                .filter(node -> node != null && node.getPublishedVersionId() != null)
                .collect(Collectors.toMap(
                        WikiNode::getPublishedVersionId,
                        node -> node,
                        (left, right) -> left,
                        LinkedHashMap::new));
        return versions.stream()
                .filter(version -> version != null && version.getId() != null)
                .map(version -> {
                    WikiNode node = ownerByVersionId.get(version.getId());
                    if (node == null || !node.getId().equals(version.getNodeId()) || !spaceId.equals(version.getSpaceId())) {
                        return null;
                    }
                    return materializePublishedPage(node, version);
                })
                .filter(view -> view != null)
                .toList();
    }

    /**
     * 基于发布版本 Markdown 构造脱离仓储的页面视图，仅用于公开图谱展示，不修改仓储聚合。
     */
    static WikiNode materializePublishedPage(WikiNode node, WikiPageVersion version) {
        if (node == null || version == null || version.getMarkdown() == null) {
            return null;
        }
        WikiFrontmatter frontmatter = WikiFrontmatter.parse(version.getMarkdown());
        String title = frontmatter.title().isBlank() ? version.getTitle() : frontmatter.title();
        if (title == null || title.isBlank()) {
            return null;
        }
        WikiNode view = WikiNode.builder()
                .id(node.getId())
                .spaceId(node.getSpaceId())
                .parentId(node.getParentId())
                .ancestorPath(node.getAncestorPath())
                .title(title)
                .slug(node.getSlug())
                .nodeType(node.getNodeType())
                .sort(node.getSort())
                .markdownDraft(version.getMarkdown())
                .publishedVersionId(node.getPublishedVersionId())
                .pageType(frontmatter.pageType())
                .sources(new ArrayList<>(frontmatter.sources()))
                .related(new ArrayList<>(frontmatter.related()))
                .tags(new ArrayList<>(frontmatter.tags()))
                .summary(frontmatter.summary())
                .build();
        view.setSlug(node.getSlug());
        view.setAncestorPath(node.getAncestorPath());
        view.setParentId(node.getParentId());
        return view;
    }

    /**
     * 公开场景只保留已发布 PAGE 命中；管理端保持原样。
     */
    static List<WikiSearchHitDTO> filterVisibleHits(List<WikiSearchHitDTO> rawHits, List<WikiNode> pages, boolean publicSite) {
        if (!publicSite || rawHits == null || rawHits.isEmpty()) {
            return rawHits == null ? List.of() : rawHits;
        }
        Set<String> publishedPageIds = pages.stream()
                .filter(node -> node.getPublishedVersionId() != null && node.getId() != null)
                .map(node -> String.valueOf(node.getId()))
                .collect(Collectors.toSet());
        return rawHits.stream()
                .filter(hit -> hit != null && "PAGE".equals(hit.getKind()) && publishedPageIds.contains(hit.getNodeId()))
                .toList();
    }

    private WikiChatActivityDTO progress(String phase, String status, String title, String content) {
        return WikiChatActivityDTO.builder()
                .activityType("wiki-progress")
                .phase(phase)
                .status(status)
                .title(title)
                .content(content)
                .build();
    }

    private WikiChatActivityDTO retrieval(String question, List<WikiSearchHitDTO> hits, List<WikiNode> pages,
                                          boolean failed, String reason, boolean publicSite) {
        List<WikiChatActivityDTO.Hit> displayHits = WikiChatActivityAssembler.hits(hits, pages);
        String content;
        if (failed) {
            content = publicSite
                    ? "预检索暂时不可用，已继续生成回答。"
                    : "预检索失败，已降级继续生成。" + (reason == null || reason.isBlank() ? "" : "原因：" + reason);
        }
        else {
            content = "预检索完成，共 " + displayHits.size() + " 条相关内容。";
        }
        return WikiChatActivityDTO.builder()
                .activityType("wiki-retrieval")
                .phase("wiki-retrieval")
                .status(failed ? "failed" : "completed")
                .title("预检索")
                .content(content)
                .query(question)
                .hits(displayHits)
                .build();
    }

    private WikiChatActivityDTO graph(String question, List<WikiSearchHitDTO> hits, List<WikiNode> pages) {
        WikiChatActivityDTO.Graph localGraph = WikiChatActivityAssembler.graph(question, hits, pages);
        return WikiChatActivityDTO.builder()
                .activityType("wiki-graph")
                .phase("wiki-graph")
                .status("completed")
                .title("知识图谱")
                .content("本轮局部图谱，共 " + localGraph.nodes().size() + " 节点 / " + localGraph.edges().size() + " 边。")
                .query(question)
                .graph(localGraph)
                .build();
    }

    private void emitActivity(Consumer<WikiChatActivityDTO> onActivity, WikiChatActivityDTO activity) {
        if (onActivity != null && activity != null) {
            onActivity.accept(activity);
        }
    }

    private String contextOf(List<WikiSearchHitDTO> hits) {
        if (hits.isEmpty()) {
            return "（未检索到相关内容）";
        }
        StringBuilder context = new StringBuilder();
        for (WikiSearchHitDTO hit : hits) {
            context.append("### ").append(hit.getTitle()).append('\n')
                    .append(truncate(hit.getContent(), 1200)).append("\n");
            // 命中页的站内图片随上下文给出，模型回答时可按需用 Markdown 图片语法引用展示
            if (hit.getImages() != null && !hit.getImages().isEmpty()) {
                context.append("相关图片：\n");
                for (WikiSearchHitDTO.Image image : hit.getImages()) {
                    if (image.getUrl() != null && !image.getUrl().isBlank()) {
                        context.append("![").append(image.getCaption() == null ? "配图" : image.getCaption())
                                .append("](").append(image.getUrl()).append(")\n");
                    }
                }
            }
            context.append('\n');
        }
        return context.toString();
    }

    private List<WikiChatResultDTO.Citation> extractCitations(List<AiAgentToolResult> toolResults) {
        List<WikiChatResultDTO.Citation> citations = new ArrayList<>();
        if (toolResults == null) {
            return citations;
        }
        for (AiAgentToolResult tool : toolResults) {
            if (tool == null || tool.payload() == null
                    || !("wiki.search".equals(tool.toolName()) || "wiki_search".equals(tool.toolName())
                         || "wiki_search_public".equals(tool.toolName()))) {
                continue;
            }
            Object hits = tool.payload().get("hits");
            if (!(hits instanceof List<?> list)) {
                continue;
            }
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    continue;
                }
                String title = text(map.get("title"));
                String path = text(map.get("path"));
                if (!title.isBlank()) {
                    citations.add(WikiChatResultDTO.Citation.builder()
                            .title(title)
                            .path(path)
                            .nodeId(text(map.get("nodeId")))
                            .excerpt(truncate(text(map.get("content")), 800))
                            .images(payloadImages(map.get("images")))
                            .build());
                }
            }
        }
        return citations;
    }

    private List<WikiChatResultDTO.Citation.Image> hitImages(WikiSearchHitDTO hit) {
        if (hit.getImages() == null) {
            return List.of();
        }
        return hit.getImages().stream()
                .map(image -> new WikiChatResultDTO.Citation.Image(image.getUrl(), image.getCaption()))
                .toList();
    }

    private List<WikiChatResultDTO.Citation.Image> payloadImages(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<WikiChatResultDTO.Citation.Image> images = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                String url = text(map.get("url"));
                if (!url.isBlank()) {
                    images.add(new WikiChatResultDTO.Citation.Image(url, text(map.get("caption"))));
                }
            }
        }
        return images;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(CapabilityModule::getConfig)
                .orElseThrow(() -> new BizException("AI 能力未启用"));
    }
}
