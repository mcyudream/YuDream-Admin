package online.yudream.base.interfaces.platform.ai.res;

import online.yudream.base.domain.platform.ai.enumerate.AiRunStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiRunRes(String id, String sessionId, String module, AiRunStatus status, long cursor,
                       String content, String reasoning, Object result, String error, Instant createdAt,
                       Instant updatedAt, List<Map<String, Object>> pendingFrontendTools, List<Object> tools,
                       List<Object> activities, String structuredContent, boolean resumeSupported,
                       List<online.yudream.base.domain.platform.ai.valobj.HarnessPendingInteraction> pendingInteractions,
                       String inputText, List<Map<String, Object>> generations) { }
