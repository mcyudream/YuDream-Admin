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
 * 原生 Agent 工具：wiki.search —— 混合检索（关键词 + 向量），支持“只读原文”模式。
 */
@Component
@RequiredArgsConstructor
public class WikiSearchAiTool implements AiAgentTool {

    public static final String TOOL_NAME = "wiki.search";
    public static final String PERMISSION_CODE = "platform:wiki:tool:search";

    private final WikiSearchAppService search;

    @Override
    public AiAgentToolDescriptor descriptor() {
        return new AiAgentToolDescriptor(
                TOOL_NAME,
                "Wiki 检索",
                "在指定 Wiki 知识库中执行混合检索（关键词 + 可选向量/图谱），返回带来源的页面片段；"
                        + "仅当用户明确要求只依据原始资料时才把 sourceGrounded 设为 true（否则保持 false）。",
                PERMISSION_CODE,
                "AI 检索 Wiki",
                "平台能力",
                "允许 AI Agent 检索 Wiki 知识库页面与原始资料。",
                Map.of(
                        "spaceSlug", Map.of("type", "string", "description", "知识库 slug"),
                        "query", Map.of("type", "string", "description", "检索关键词（可含多个词，系统会自动分词）"),
                        "topK", Map.of("type", "integer", "description", "返回条数，默认 8"),
                        "pathPrefix", Map.of("type", "string", "description", "路径前缀过滤（可选）"),
                        "graphExpansion", Map.of("type", "boolean", "description", "是否启用图谱扩展，默认 false"),
                        "sourceGrounded", Map.of("type", "boolean", "description", "是否只读原文，默认 false；除非用户明确要求只看原始资料，否则必须传 false")
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
        boolean sourceGrounded = bool(args.get("sourceGrounded"));
        List<WikiSearchHitDTO> hits = search.searchForAdmin(slug, query, topK, pathPrefix, graph, sourceGrounded);
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
        value.put("content", hit.getContent());
        value.put("sourceUrl", hit.getSourceUrl());
        value.put("images", hit.getImages() == null ? List.of() : hit.getImages().stream()
                .map(image -> Map.of("url", image.getUrl() == null ? "" : image.getUrl(),
                        "caption", image.getCaption() == null ? "" : image.getCaption()))
                .toList());
        return value;
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
