package online.yudream.base.interfaces.platform.ai.assembler;

import online.yudream.base.application.platform.ai.dto.AiRunDTO;
import online.yudream.base.domain.platform.ai.valobj.AiRunEvent;
import online.yudream.base.interfaces.platform.ai.res.AguiStreamEventRes;
import online.yudream.base.interfaces.platform.ai.res.AiRunEventRes;
import online.yudream.base.interfaces.platform.ai.res.AiRunRes;
import java.util.List;
import java.util.Map;

public final class AiRunWebAssembler {
    private AiRunWebAssembler() { }
    public static AiRunRes run(AiRunDTO dto) {
        return new AiRunRes(dto.id(), dto.sessionId(), dto.module(), dto.status(), dto.cursor(), dto.content(),
                dto.reasoning(), dto.result(), dto.error(), dto.createdAt(), dto.updatedAt(), dto.pendingFrontendTools(), dto.tools(), dto.activities(), dto.structuredContent(), dto.resumeSupported(), dto.pendingInteractions(), dto.inputText(), dto.generations());
    }
    public static List<AiRunRes> runs(List<AiRunDTO> dtos) { return dtos.stream().map(AiRunWebAssembler::run).toList(); }
    public static AiRunEventRes event(AiRunEvent event) {
        return new AiRunEventRes(event.runId(), event.cursor(), event.event(), event.action(), event.module(),
                event.traceId(), event.timestamp(), event.payload());
    }
    /** 历史 AG-UI 入口消费同一份持久日志，避免建立第二条运行链路。 */
    public static AguiStreamEventRes agui(AiRunEvent event) {
        var builder = AguiStreamEventRes.builder().timestamp(event.timestamp().toEpochMilli())
                .threadId("yudream-chat").runId(event.runId()).action(event.action());
        if (event.payload().get("callId") != null || "ai.generation".equals(event.event()) || "ai.input".equals(event.event())) {
            Map<String, Object> content = new java.util.LinkedHashMap<>(event.payload());
            content.put("action", event.action());
            return builder.type("ACTIVITY_SNAPSHOT").activityType(event.event()).content(content).build();
        }
        return switch (event.event()) {
            case "ai.message" -> builder.type("reasoning".equals(event.action())
                            ? "THINKING_TEXT_MESSAGE_CONTENT" : "TEXT_MESSAGE_CHUNK")
                    .messageId("assistant-" + event.runId()).role("assistant")
                    .delta(String.valueOf(event.payload().getOrDefault("content", ""))).build();
            case "ai.tool" -> builder.type("TOOL_CALL_RESULT").role("tool")
                    .toolCallId(event.runId() + "-" + event.cursor()).content(event.payload().get("tool")).build();
            case "ai.result" -> builder.type("RUN_FINISHED").result(event.payload().get("result")).build();
            case "ai.error" -> builder.type("RUN_ERROR").code("cancelled".equals(event.action()) ? "CANCELLED" : "FAILED")
                    .message(String.valueOf(event.payload().get("message"))).build();
            case "ai.frontend" -> "request".equals(event.action())
                    ? builder.type("TOOL_CALL_REQUEST").toolCallId(String.valueOf(event.payload().get("toolCallId")))
                    .toolCallName(String.valueOf(event.payload().get("name"))).content(event.payload().get("arguments")).build()
                    : builder.type("ACTIVITY_SNAPSHOT").activityType("ai.frontend").content(event.payload()).build();
            default -> builder.type("accepted".equals(event.action()) ? "RUN_STARTED" : "ACTIVITY_SNAPSHOT")
                    .activityType("ai.progress").content(event.payload().getOrDefault("activity", Map.of())).build();
        };
    }
}
