package online.yudream.base.domain.platform.integration.aggregate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import online.yudream.base.domain.common.base.BaseDomain;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeExecutionLog extends BaseDomain {

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
