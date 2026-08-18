package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.cmd.WikiNodeSaveCmd;
import online.yudream.base.application.platform.wiki.cmd.WikiSpaceSaveCmd;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.domain.common.exception.BizException;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.wiki.enumerate.WikiPageType;
import online.yudream.base.interfaces.platform.wiki.request.WikiNodeSaveRequest;
import online.yudream.base.interfaces.platform.wiki.request.WikiSpaceSaveRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class WikiWebAssembler {
    private WikiWebAssembler() {
    }

    public static WikiSpaceSaveCmd space(Long id, WikiSpaceSaveRequest r) {
        WikiSpaceSaveCmd c = new WikiSpaceSaveCmd();
        c.setId(id);
        c.setName(r.getName());
        c.setSlug(r.getSlug());
        c.setDescription(r.getDescription());
        c.setPublicReadEnabled(r.isPublicReadEnabled());
        c.setExternalSearchEnabled(r.isExternalSearchEnabled());
        c.setEmbeddingProviderCode(r.getEmbeddingProviderCode());
        c.setEmbeddingModelCode(r.getEmbeddingModelCode());
        c.setGraphEnabled(r.isGraphEnabled());
        c.setGraphProviderCode(r.getGraphProviderCode());
        c.setGraphModelCode(r.getGraphModelCode());
        c.setNeo4jConnectionCode(r.getNeo4jConnectionCode());
        c.setChunkSize(r.getChunkSize());
        c.setChunkOverlap(r.getChunkOverlap());
        c.setTopK(r.getTopK());
        c.setQueryExpansionEnabled(r.isQueryExpansionEnabled());
        c.setRerankEnabled(r.isRerankEnabled());
        c.setHitImageLimit(r.getHitImageLimit());
        c.setPurpose(r.getPurpose());
        c.setSchemaContent(r.getSchemaContent());
        c.setLanguage(r.getLanguage());
        c.setChatProviderCode(r.getChatProviderCode());
        c.setChatModelCode(r.getChatModelCode());
        c.setIngestProviderCode(r.getIngestProviderCode());
        c.setIngestModelCode(r.getIngestModelCode());
        c.setVisionProviderCode(r.getVisionProviderCode());
        c.setVisionModelCode(r.getVisionModelCode());
        c.setWebSearchProviderCode(r.getWebSearchProviderCode());
        c.setWebSearchApiKey(r.getWebSearchApiKey());
        c.setWebSearchInstanceUrl(r.getWebSearchInstanceUrl());
        c.setWebSearchEngine(r.getWebSearchEngine());
        c.setContextWindowTokens(r.getContextWindowTokens());
        c.setSourceGroundedDefault(r.isSourceGroundedDefault());
        c.setWatchEnabled(r.isWatchEnabled());
        c.setWatchFolderPath(r.getWatchFolderPath());
        return c;
    }

    public static WikiNodeSaveCmd node(Long id, Long spaceId, WikiNodeSaveRequest r) {
        WikiNodeSaveCmd c = new WikiNodeSaveCmd();
        c.setId(id);
        c.setSpaceId(spaceId);
        c.setParentId(r.getParentId() == null || r.getParentId().isBlank() ? null : Long.valueOf(r.getParentId()));
        c.setTitle(r.getTitle());
        c.setSlug(r.getSlug());
        c.setNodeType(r.getNodeType());
        c.setSort(r.getSort());
        c.setMarkdown(r.getMarkdown());
        c.setBody(r.getBody());
        c.setPageType(parsePageType(r.getPageType()));
        c.setSources(r.getSources());
        c.setRelated(r.getRelated());
        c.setTags(r.getTags());
        c.setSummary(r.getSummary());
        return c;
    }

    public static java.util.List<online.yudream.base.domain.platform.ai.valobj.AiChatMessage> chatHistory(
            online.yudream.base.interfaces.platform.wiki.request.WikiChatRequest r) {
        if (r == null || r.getHistory() == null) {
            return java.util.List.of();
        }
        return r.getHistory().stream()
                .map(turn -> new online.yudream.base.domain.platform.ai.valobj.AiChatMessage(
                        turn.getRole() == null ? "user" : turn.getRole(),
                        turn.getContent() == null ? "" : turn.getContent()))
                .toList();
    }

    public static Map<String, Object> toolEvent(AiAgentToolResult tool) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "tool");
        event.put("toolName", nullToEmpty(tool.toolName()));
        event.put("message", nullToEmpty(tool.message()));
        return event;
    }

    public static List<Map<String, Object>> citationEvents(WikiChatResultDTO result) {
        if (result == null || result.citations() == null) {
            return List.of();
        }
        return result.citations().stream().map(citation -> {
            Map<String, Object> event = new HashMap<>();
            event.put("title", nullToEmpty(citation.title()));
            event.put("path", nullToEmpty(citation.path()));
            event.put("nodeId", nullToEmpty(citation.nodeId()));
            event.put("images", citation.images() == null ? List.of() : citation.images().stream()
                    .map(image -> Map.of("url", nullToEmpty(image.url()), "caption", nullToEmpty(image.caption())))
                    .toList());
            return event;
        }).toList();
    }

    /** 页面类型按大小写不敏感解析，非法值直接拒绝。 */
    private static WikiPageType parsePageType(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return WikiPageType.valueOf(value.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            throw new BizException("页面类型不合法：" + value);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
