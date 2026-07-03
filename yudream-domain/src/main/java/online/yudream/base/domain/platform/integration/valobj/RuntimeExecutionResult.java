package online.yudream.base.domain.platform.integration.valobj;

import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;

public record RuntimeExecutionResult(
        String stdout,
        String stderr,
        int exitCode,
        long durationMillis,
        ExecutionStatus status,
        String errorMessage
) {
}
