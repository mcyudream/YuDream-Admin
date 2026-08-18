package online.yudream.base.application.platform.wiki.service;

import lombok.RequiredArgsConstructor;
import online.yudream.base.application.platform.wiki.dto.WikiSearchHitDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.service.AiAgentTool;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolCall;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolDescriptor;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公开站点的原生 Agent 工具：wiki.search。
 * <p>
 * 与 {@link WikiSearchAiTool} 共享同一工具名与 schema，但只检索已发布页面：execute 强制走
 * searchForPublicSite，并把 sourceGrounded 强制为 false，避免公开问答经工具调用读取原始资料全文。
 */
@Component
@RequiredArgsConstructor
public class WikiPublicSearchAiTool implements AiAgentTool {

    public static final String TOOL_NAME = WikiSearchAiTool.TOOL_NAME;
    /**
     * 公开检索只返回已发布页面，与匿名公开 REST（/api/public/wiki/{slug}/search）暴露面同级，不绑定平台权限码。
     * 留空后显式权限上下文（QQ 机器人等插件发起的 Agent 运行）与游客会话均可调用；
     * 管理侧完整检索（含原始资料）仍由 {@link WikiSearchAiTool} 的 platform:wiki:tool:search 保护。
     */
    public static final String PERMISSION_CODE = "";

    private static final int PAGE_EXCERPT_LIMIT = 800;
    private static final int SOURCE_EXCERPT_LIMIT = 320;

    private final WikiSearchAppService search;

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "Wiki 公开检索",
                "在公开 Wiki 知识库中检索已发布页面（关键词 + 可选向量/图谱），返回带来源的页面片段。"
                        + "公开检索不读取原始资料，sourceGrounded 参数会被忽略并强制为 false。",
                PERMISSION_CODE,
                "AI 检索公开 Wiki",
                "平台能力",
                "允许 AI Agent 检索公开 Wiki 知识库的已发布页面。",
                Map.of(
                        "spaceSlug", Map.of("type", "string", "description", "知识库 slug"),
                        "query", Map.of("type", "string", "description", "检索关键词（可含多个词，系统会自动分词）"),
                        "topK", Map.of("type", "integer", "description", "返回条数，默认 8"),
                        "pathPrefix", Map.of("type", "string", "description", "路径前缀过滤（可选）"),
                        "graphExpansion", Map.of("type", "boolean", "description", "是否启用图谱扩展，默认 false"),
                        "sourceGrounded", Map.of("type", "boolean", "description", "公开场景忽略，始终为 false")
                )
        );
    }

    @Override
    public AiAgentToolResult execute(AiAgentToolCall call) {
        Map<String, Object> args = call.arguments() == null ? Map.of() : call.arguments();
        String slug = text(args.get("spaceSlug"));
        String query = text(args.get("query"));
        if (!StringUtils.hasText(slug)) {
            throw new BizException("wiki.search 缺少 spaceSlug 参数");
        }
        if (!StringUtils.hasText(query)) {
            throw new BizException("wiki.search 缺少 query 参数");
        }
        int topK = intValue(args.get("topK"), 8);
        String pathPrefix = text(args.get("pathPrefix"));
        boolean graph = bool(args.get("graphExpansion"));
        // 公开场景强制 sourceGrounded=false，避免读取原始资料全文。
        List<WikiSearchHitDTO> hits = search.searchForPublicSite(slug, query, topK, pathPrefix, graph, false);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("spaceSlug", slug);
        payload.put("query", query);
        payload.put("hits", hits.stream().map(this::hit).toList());
        return new AiAgentToolResult(TOOL_NAME, "search", PERMISSION_CODE, "检索到 " + hits.size() + " 条结果。", payload);
    }

    private Map<String, Object> hit(WikiSearchHitDTO hit) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("score", hit.getScore());
        value.put("kind", hit.getKind());
        value.put("nodeId", hit.getNodeId());
        value.put("sourceId", hit.getSourceId());
        value.put("title", hit.getTitle());
        value.put("path", hit.getPath());
        value.put("content", truncate(hit.getContent(), excerptLimit(hit.getKind())));
        value.put("sourceUrl", hit.getSourceUrl());
        value.put("images", hit.getImages() == null ? List.of() : hit.getImages().stream()
                .map(image -> Map.of("url", image.getUrl() == null ? "" : image.getUrl(),
                        "caption", image.getCaption() == null ? "" : image.getCaption()))
                .toList());
        return value;
    }

    private int excerptLimit(String kind) {
        return "SOURCE".equals(kind) ? SOURCE_EXCERPT_LIMIT : PAGE_EXCERPT_LIMIT;
    }

    private String truncate(String value, int limit) {
        if (value == null) {
            return "";
        }
        return value.length() > limit ? value.substring(0, limit) : value;
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(value.toString().trim());
        }
        catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private boolean bool(Object value) {
        return value != null && (Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(value.toString().trim()));
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
