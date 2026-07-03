package online.yudream.base.domain.platform.integration.valobj;

import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;

public record HttpInvocationResult(
        int statusCode,
        String body,
        long durationMillis,
        ExecutionStatus status,
        String errorMessage
) {
}
