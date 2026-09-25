package online.yudream.base.application.system.backup.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 备份计划视图。 */
public record BackupPlanDTO(
        String id,
        String code,
        String name,
        String cron,
        List<String> scopeTags,
        String targetCode,
        String targetName,
        int retentionCount,
        boolean enabled,
        LocalDateTime lastRunAt,
        String lastStatus,
        String lastJobId
) {
}
