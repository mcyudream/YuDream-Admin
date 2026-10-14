package online.yudream.base.domain.platform.ai.valobj;

import java.time.Instant;
import java.util.Map;

/** 只有可公开的界面事件进入日志，不存模型请求、密钥或内部权限快照。 */
public record AiRunEvent(String runId, long cursor, String event, String action, String module,
                         String traceId, Instant timestamp, Map<String, Object> payload) { }
