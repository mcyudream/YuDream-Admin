package online.yudream.base.interfaces.platform.wiki.assembler;

import online.yudream.base.application.platform.wiki.dto.WikiChatActivityDTO;
import online.yudream.base.application.platform.wiki.dto.WikiChatResultDTO;
import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiki 智能问答的 AG-UI 事件装配。事件结构与 CMS AG-UI 通道一致，仅活动类型与结果载荷按 Wiki 语义命名。
 */
public final class WikiAguiWebAssembler {

    private static final String THREAD_ID = "wiki-chat";

    private WikiAguiWebAssembler() {
    }

    public static AguiStreamEventRes runStarted(String traceId) {
        return agui("RUN_STARTED", traceId).threadId(THREAD_ID).runId(traceId).build();
    }

    public static AguiStreamEventRes textChunk(String traceId, String content) {
        return agui("TEXT_MESSAGE_CHUNK", traceId)
                .messageId("assistant-" + traceId)
                .role("assistant")
                .delta(content == null ? "" : content)
                .build();
    }

    public static AguiStreamEventRes thinkingStart(String traceId) {
        return thinking("THINKING_START", traceId, null);
    }

    public static AguiStreamEventRes thinkingContent(String traceId, String delta) {
        return thinking("THINKING_CONTENT", traceId, delta);
    }

    public static AguiStreamEventRes thinkingEnd(String traceId) {
        return thinking("THINKING_END", traceId, null);
    }

    public static AguiStreamEventRes toolStart(String traceId, String toolCallId, AiAgentToolResult tool) {
        return agui("TOOL_CALL_START", traceId)
                .toolCallId(toolCallId)
                .toolCallName(tool.toolName())
                .parentMessageId("assistant-" + traceId)
                .build();
    }

    public static AguiStreamEventRes toolResult(String traceId, String toolCallId, AiAgentToolResult tool) {
        return agui("TOOL_CALL_RESULT", traceId)
                .messageId("assistant-" + traceId)
                .role("tool")
                .toolCallId(toolCallId)
                .toolCallName(tool.toolName())
                .message(tool.message())
                .content(toolPayload(tool))
                .build();
    }

    /**
     * 兼容旧字符串进度活动：activityType 固定为 wiki-progress。
     */
    public static AguiStreamEventRes activitySnapshot(String traceId, String action, String content) {
        return agui("ACTIVITY_SNAPSHOT", traceId)
                .messageId("activity-" + traceId)
                .activityType("wiki-progress")
                .content(activityContent(action, content))
                .build();
    }

    public static AguiStreamEventRes activityDelta(String traceId, String action, String content) {
        return agui("ACTIVITY_DELTA", traceId)
                .messageId("activity-" + traceId)
                .activityType("wiki-progress")
                .patch(List.of(
                        Map.of("op", "replace", "path", "/phase", "value", phase(action)),
                        Map.of("op", "replace", "path", "/title", "value", title(action)),
                        Map.of("op", "replace", "path", "/content", "value", content == null ? "" : content)
                ))
                .build();
    }

    /**
     * 将应用层过程事件转为 ACTIVITY_SNAPSHOT，activityType 映射 wiki-progress/wiki-retrieval/wiki-graph，
     * messageId 对同一阶段稳定。
     */
    public static AguiStreamEventRes activitySnapshot(String traceId, WikiChatActivityDTO activity) {
        String activityType = activity == null || activity.activityType() == null ? "wiki-progress" : activity.activityType();
        String phase = activity == null || activity.phase() == null ? "progress" : activity.phase();
        return agui("ACTIVITY_SNAPSHOT", traceId)
                .messageId("activity-" + traceId + "-" + phase)
                .activityType(activityType)
                .content(activityContent(activity))
                .build();
    }

    public static AguiStreamEventRes runFinished(String traceId, WikiChatResultDTO result) {
        return agui("RUN_FINISHED", traceId).threadId(THREAD_ID).runId(traceId).result(resultPayload(result)).build();
    }

    public static AguiStreamEventRes runError(String traceId, String message) {
        return agui("RUN_ERROR", traceId).message(message == null ? "" : message).build();
    }

    private static AguiStreamEventRes thinking(String type, String traceId, String delta) {
        return agui(type, traceId)
                .messageId("thinking-" + traceId)
                .role("assistant")
                .delta(delta)
                .build();
    }

    private static AguiStreamEventRes.AguiStreamEventResBuilder agui(String type, String traceId) {
        return AguiStreamEventRes.builder()
                .type(type)
                .timestamp(Instant.now().toEpochMilli())
                .threadId(THREAD_ID)
                .runId(traceId);
    }

    private static Map<String, Object> toolPayload(AiAgentToolResult tool) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", tool.toolName());
        payload.put("action", tool.action());
        payload.put("message", tool.message());
        payload.put("payload", tool.payload());
        return payload;
    }

    private static Map<String, Object> activityContent(WikiChatActivityDTO activity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", activity == null ? "progress" : nullToEmpty(activity.phase()));
        result.put("status", activity == null ? "running" : nullToEmpty(activity.status()));
        result.put("title", activity == null ? "Wiki 问答" : nullToEmpty(activity.title()));
        result.put("content", activity == null ? "" : nullToEmpty(activity.content()));
        if (activity != null && activity.query() != null) {
            result.put("query", activity.query());
        }
        if (activity != null && activity.hits() != null) {
            result.put("hits", activity.hits().stream().map(WikiAguiWebAssembler::hitPayload).toList());
        }
        if (activity != null && activity.graph() != null) {
            result.put("graph", graphPayload(activity.graph()));
        }
        return result;
    }

    private static Map<String, Object> activityContent(String action, String content) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("phase", phase(action));
        result.put("title", title(action));
        result.put("content", content == null ? "" : content);
        return result;
    }

    private static Map<String, Object> hitPayload(WikiChatActivityDTO.Hit hit) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("score", hit.score());
        item.put("kind", nullToEmpty(hit.kind()));
        item.put("nodeId", nullToEmpty(hit.nodeId()));
        item.put("title", nullToEmpty(hit.title()));
        item.put("path", nullToEmpty(hit.path()));
        item.put("excerpt", nullToEmpty(hit.excerpt()));
        return item;
    }

    private static Map<String, Object> graphPayload(WikiChatActivityDTO.Graph graph) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", nullToEmpty(graph.query()));
        payload.put("nodes", graph.nodes() == null
                ? List.of()
                : graph.nodes().stream().map(WikiAguiWebAssembler::nodePayload).toList());
        payload.put("edges", graph.edges() == null
                ? List.of()
                : graph.edges().stream().map(WikiAguiWebAssembler::edgePayload).toList());
        return payload;
    }

    private static Map<String, Object> nodePayload(WikiChatActivityDTO.Node node) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", nullToEmpty(node.id()));
        item.put("title", nullToEmpty(node.title()));
        item.put("type", nullToEmpty(node.type()));
        item.put("role", nullToEmpty(node.role()));
        item.put("score", node.score());
        item.put("path", nullToEmpty(node.path()));
        return item;
    }

    private static Map<String, Object> edgePayload(WikiChatActivityDTO.Edge edge) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("source", nullToEmpty(edge.source()));
        item.put("target", nullToEmpty(edge.target()));
        item.put("weight", edge.weight());
        item.put("signal", nullToEmpty(edge.signal()));
        return item;
    }

    private static Map<String, Object> resultPayload(WikiChatResultDTO result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        String answer = result == null || result.answer() == null ? "" : result.answer();
        payload.put("content", answer);
        payload.put("reasoning", result == null ? "" : nullToEmpty(result.reasoning()));
        payload.put("citations", result == null || result.citations() == null
                ? List.of()
                : result.citations().stream().map(citation -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("title", nullToEmpty(citation.title()));
                    item.put("path", nullToEmpty(citation.path()));
                    item.put("nodeId", nullToEmpty(citation.nodeId()));
                    item.put("excerpt", nullToEmpty(citation.excerpt()));
                    item.put("images", citation.images() == null ? List.of() : citation.images().stream()
                            .map(image -> Map.of("url", nullToEmpty(image.url()), "caption", nullToEmpty(image.caption())))
                            .toList());
                    return item;
                }).toList());
        return payload;
    }

    private static String phase(String action) {
        return action == null || action.isBlank() ? "progress" : action;
    }

    private static String title(String action) {
        return switch (action == null ? "" : action) {
            case "retrieve" -> "检索知识库";
            case "tool-start" -> "调用检索工具";
            case "tool-complete" -> "检索完成";
            default -> "Wiki 问答";
        };
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
