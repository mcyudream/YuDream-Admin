package online.yudream.base.interfaces.platform.integration.res;

import lombok.Builder;
import lombok.Data;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;

import java.time.LocalDateTime;

@Data
@Builder
public class RuntimeExecutionLogRes {
    private Long id;
    private Long scriptId;
    private String scriptCode;
    private RuntimeLanguage language;
    private String stdin;
    private String stdout;
    private String stderr;
    private int exitCode;
    private long durationMillis;
    private ExecutionStatus status;
    private String errorMessage;
    private LocalDateTime executedAt;
}
