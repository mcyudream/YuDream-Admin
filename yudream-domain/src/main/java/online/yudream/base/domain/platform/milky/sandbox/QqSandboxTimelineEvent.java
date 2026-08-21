package online.yudream.base.domain.platform.milky.sandbox;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record QqSandboxTimelineEvent(
        long sequence,
        Instant timestamp,
        String phase,
        String action,
        String pluginCode,
        Map<String, Object> payload
) {
    public QqSandboxTimelineEvent {
        // Map.copyOf 遇 null 键值会裸 NPE；诊断负载常携带可空字段（如语义索引 content），入事件前统一剔除
        if (payload == null) {
            payload = Map.of();
        } else {
            Map<String, Object> cleaned = new LinkedHashMap<>();
            payload.forEach((key, value) -> {
                if (key != null && value != null) cleaned.put(key, value);
            });
            payload = Map.copyOf(cleaned);
        }
    }
}
