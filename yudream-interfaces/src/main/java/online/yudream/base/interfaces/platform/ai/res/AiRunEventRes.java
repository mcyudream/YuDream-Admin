package online.yudream.base.interfaces.platform.ai.res;

import java.time.Instant;
import java.util.Map;

public record AiRunEventRes(String runId, long cursor, String event, String action, String module,
                            String traceId, Instant timestamp, Map<String, Object> payload) { }
