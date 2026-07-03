package online.yudream.base.domain.platform.capability.valobj;

import java.time.LocalDateTime;

public record CapabilityTestResult(
        boolean success,
        String message,
        LocalDateTime testedAt
) {

    public static CapabilityTestResult success(String message) {
        return new CapabilityTestResult(true, message, LocalDateTime.now());
    }

    public static CapabilityTestResult failure(String message) {
        return new CapabilityTestResult(false, message, LocalDateTime.now());
    }
}
