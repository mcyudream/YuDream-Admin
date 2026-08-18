package online.yudream.base.application.platform.wiki.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.capability.service.CapabilityAppService;
import online.yudream.base.application.platform.wiki.assembler.WikiKnowledgeAssembler;
import online.yudream.base.application.platform.wiki.dto.WikiLintReportDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiGenerationGateway;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationRequest;
import online.yudream.base.domain.platform.ai.valobj.AiGenerationResult;
import online.yudream.base.domain.platform.ai.valobj.AiStructuredOutput;
import online.yudream.base.domain.platform.capability.aggregate.CapabilityModule;
import online.yudream.base.domain.platform.capability.repo.CapabilityModuleRepo;
import online.yudream.base.domain.platform.wiki.aggregate.WikiNode;
import online.yudream.base.domain.platform.wiki.aggregate.WikiSpace;
import online.yudream.base.domain.platform.wiki.enumerate.WikiNodeType;
import online.yudream.base.domain.platform.wiki.repo.WikiNodeRepo;
import online.yudream.base.domain.platform.wiki.repo.WikiSpaceRepo;
import online.yudream.base.domain.platform.wiki.valobj.WikiLintIssue;
import online.yudream.base.domain.platform.wiki.valobj.WikiLintReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lint：Wiki 健康检查（孤立页、矛盾、过时声明、缺失交叉引用/页面、数据空白）。
 */
@Service
@RequiredArgsConstructor
public class WikiLintAppService {

    private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|[^\\]]*)?]]");
    private static final Set<String> SPECIAL_TITLES = Set.of("内容目录", "操作日志", "全局概要");

    private final CapabilityAppService capabilities;
    private final CapabilityModuleRepo capabilityModuleRepo;
    private final AiGenerationGateway aiGeneration;
    private final WikiSpaceRepo spaceRepo;
    private final WikiNodeRepo nodeRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public WikiLintReportDTO lint(Long spaceId) {
        enabled();
        WikiSpace space = spaceRepo.findById(spaceId).orElseThrow(() -> new BizException("知识库不存在"));
        List<WikiNode> pages = nodeRepo.findBySpaceId(spaceId).stream()
                .filter(node -> node.getNodeType() == WikiNodeType.PAGE).toList();
        List<WikiLintIssue> issues = new ArrayList<>(detectOrphans(pages));
        try {
            issues.addAll(llmLint(space, pages));
        }
        catch (Exception exception) {
            issues.add(new WikiLintIssue(WikiLintIssue.CATEGORY_DATA_GAP, "info", "LLM 深度检查未完成",
                    exception.getMessage(), List.of(), "检查 AI 配置后重试", List.of()));
        }
        String summary = "共发现 " + issues.size() + " 个问题";
        return WikiKnowledgeAssembler.lintReport(new WikiLintReport(LocalDateTime.now(), summary, issues));
    }

    private List<WikiLintIssue> detectOrphans(List<WikiNode> pages) {
        Map<String, WikiNode> byTitle = pages.stream()
                .collect(Collectors.toMap(WikiNode::getTitle, node -> node, (a, b) -> a));
        Set<String> referenced = pages.stream()
                .flatMap(node -> linkedTitles(node.bodyMarkdown()).stream())
                .collect(Collectors.toSet());
        pages.forEach(node -> referenced.addAll(node.getRelated()));
        List<WikiLintIssue> issues = new ArrayList<>();
        for (WikiNode page : pages) {
            if (SPECIAL_TITLES.contains(page.getTitle())) {
                continue;
            }
            if (!referenced.contains(page.getTitle())) {
                issues.add(new WikiLintIssue(WikiLintIssue.CATEGORY_ORPHAN, "warn", "孤立页面",
                        "页面「" + page.getTitle() + "」没有任何入链，缺少与知识库其余部分的连接",
                        List.of(page.getTitle()), "补充 [[wikilink]] 或 related 引用", List.of()));
            }
        }
        return issues;
    }

    private List<WikiLintIssue> llmLint(WikiSpace space, List<WikiNode> pages) {
        String pageList = pages.stream().limit(200)
                .map(node -> "- " + node.getTitle() + "（" + node.getPageType() + "）: " + blankToEmpty(node.getSummary()))
                .collect(Collectors.joining("\n"));
        String system = "你是知识库健康检查器。只输出 JSON，不输出解释。";
        String user = "知识库目的：\n" + blankToEmpty(space.getPurpose()) + "\n\n"
                + "页面列表：\n" + pageList + "\n\n"
                + "请检查并输出如下 JSON：\n"
                + "{\"issues\":[{\"category\":\"contradiction|stale|missing_cross_ref|missing_page|data_gap\","
                + "\"severity\":\"error|warn|info\",\"title\":\"\",\"description\":\"\",\"pageTitles\":[\"\"],"
                + "\"suggestedAction\":\"\",\"searchQueries\":[\"\"]}]}";
        AiGenerationRequest request = new AiGenerationRequest(system, user, null,
                space.getIngestProviderCode(), space.getIngestModelCode(), aiConfig())
                .withStructuredOutput(AiStructuredOutput.jsonObject());
        AiGenerationResult result = aiGeneration.generate(request);
        String text = result == null ? null : result.summary();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(stripFence(text));
        }
        catch (Exception exception) {
            return List.of();
        }
        List<WikiLintIssue> issues = new ArrayList<>();
        if (root.path("issues").isArray()) {
            for (JsonNode item : root.path("issues")) {
                String title = item.path("title").asText("").trim();
                if (title.isBlank()) {
                    continue;
                }
                issues.add(new WikiLintIssue(
                        item.path("category").asText("data_gap"),
                        item.path("severity").asText("info"),
                        title,
                        item.path("description").asText(""),
                        stringList(item, "pageTitles"),
                        item.path("suggestedAction").asText(""),
                        stringList(item, "searchQueries")));
            }
        }
        return issues;
    }

    private List<String> linkedTitles(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        List<String> titles = new ArrayList<>();
        Matcher matcher = WIKILINK.matcher(markdown);
        while (matcher.find()) {
            titles.add(matcher.group(1).trim());
        }
        return titles;
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

    private Map<String, String> aiConfig() {
        return capabilityModuleRepo.findByCode("ai")
                .filter(CapabilityModule::enabled)
                .map(CapabilityModule::getConfig)
                .orElseThrow(() -> new BizException("AI 能力未启用"));
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void enabled() {
        capabilities.ensureEnabled("wiki", "Wiki 知识库");
    }
}
