package online.yudream.base.interfaces.system.backup.res;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 备份计划响应。 */
@Data
@Builder
public class BackupPlanRes {
    private String id;
    private String code;
    private String name;
    private String cron;
    private List<String> scopeTags;
    private String targetCode;
    private String targetName;
    private Integer retentionCount;
    private Boolean enabled;
    private LocalDateTime lastRunAt;
    private String lastStatus;
    private String lastJobId;
}
