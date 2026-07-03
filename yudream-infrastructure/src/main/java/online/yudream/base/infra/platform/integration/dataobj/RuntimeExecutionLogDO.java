package online.yudream.base.infra.platform.integration.dataobj;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import online.yudream.base.domain.platform.integration.enumerate.ExecutionStatus;
import online.yudream.base.domain.platform.integration.enumerate.RuntimeLanguage;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "platformRuntimeExecutionLog")
public class RuntimeExecutionLogDO extends BaseDO {
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
