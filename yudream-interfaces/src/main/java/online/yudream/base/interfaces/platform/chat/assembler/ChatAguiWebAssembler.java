package online.yudream.base.interfaces.platform.chat.assembler;

import online.yudream.base.domain.platform.ai.valobj.AiAgentToolResult;
import online.yudream.base.domain.platform.chat.valobj.ChatActivity;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ChatAguiWebAssembler {

    private static final String THREAD_ID = "yudream-chat";

    private ChatAguiWebAssembler() {
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
        return thinking("THINKING_TEXT_MESSAGE_START", traceId, null);
    }

    public static AguiStreamEventRes thinkingContent(String traceId, String content) {
        return thinking("THINKING_TEXT_MESSAGE_CONTENT", traceId, content);
    }

    public static AguiStreamEventRes thinkingEnd(String traceId) {
        return thinking("THINKING_TEXT_MESSAGE_END", traceId, null);
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

    public static AguiStreamEventRes activitySnapshot(String traceId, ChatActivity activity) {
        return agui("ACTIVITY_SNAPSHOT", traceId)
                .messageId("activity-" + traceId + "-" + activity.phase())
                .activityType(activity.activityType())
                .content(activityPayload(activity))
                .build();
    }

    public static AguiStreamEventRes runFinished(String traceId, Map<String, Object> result) {
        return agui("RUN_FINISHED", traceId).threadId(THREAD_ID).runId(traceId).result(result).build();
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

    private static Map<String, Object> activityPayload(ChatActivity activity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("phase", activity.phase() == null ? "progress" : activity.phase());
        payload.put("status", activity.status() == null ? "running" : activity.status());
        payload.put("title", activity.title() == null ? "" : activity.title());
        payload.put("content", activity.content() == null ? "" : activity.content());
        if (activity.query() != null) {
            payload.put("query", activity.query());
        }
        if (activity.hits() != null) {
            payload.put("hits", activity.hits());
        }
        if (activity.graph() != null) {
            payload.put("graph", activity.graph());
        }
        return payload;
    }
}
