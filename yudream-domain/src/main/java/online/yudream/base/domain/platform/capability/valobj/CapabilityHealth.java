package online.yudream.base.domain.platform.capability.valobj;

import online.yudream.base.domain.platform.capability.enumerate.CapabilityStatus;

import java.time.LocalDateTime;
import java.util.Map;

public record CapabilityHealth(
        CapabilityStatus status,
        String message,
        LocalDateTime checkedAt,
        Map<String, Object> metrics
) {

    public static CapabilityHealth disabled(String message) {
        return new CapabilityHealth(CapabilityStatus.DISABLED, message, LocalDateTime.now(), Map.of());
    }

    public static CapabilityHealth enabled(String message, Map<String, Object> metrics) {
        return new CapabilityHealth(CapabilityStatus.ENABLED, message, LocalDateTime.now(), metrics == null ? Map.of() : metrics);
    }

    public static CapabilityHealth error(String message) {
        return new CapabilityHealth(CapabilityStatus.ERROR, message, LocalDateTime.now(), Map.of());
    }
}
