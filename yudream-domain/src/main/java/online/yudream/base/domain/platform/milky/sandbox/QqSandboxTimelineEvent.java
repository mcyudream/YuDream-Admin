package online.yudream.base.domain.platform.milky.sandbox;

import java.time.Instant;
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
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
