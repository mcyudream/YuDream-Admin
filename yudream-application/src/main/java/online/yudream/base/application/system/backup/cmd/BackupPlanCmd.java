package online.yudream.base.application.system.backup.cmd;

import java.util.List;

/** 创建/更新备份计划命令。 */
public record BackupPlanCmd(
        String code,
        String name,
        String cron,
        List<String> scopeTags,
        String targetCode,
        Integer retentionCount
) {
}
