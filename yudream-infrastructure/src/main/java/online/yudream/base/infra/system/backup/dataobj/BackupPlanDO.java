package online.yudream.base.infra.system.backup.dataobj;

import lombok.Data;
import lombok.EqualsAndHashCode;
import online.yudream.base.domain.system.backup.enumerate.BackupJobStatus;
import online.yudream.base.infra.common.baseobj.BaseDO;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Document("sysBackupPlan")
public class BackupPlanDO extends BaseDO {
    @Indexed(unique = true)
    private String code;
    private String name;
    private String cron;
    private List<String> scopeTags;
    private String targetCode;
    private int retentionCount;
    private boolean enabled;
    private LocalDateTime lastRunAt;
    private BackupJobStatus lastStatus;
    private Long lastJobId;
}
