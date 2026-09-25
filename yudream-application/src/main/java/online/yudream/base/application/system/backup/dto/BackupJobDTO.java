package online.yudream.base.application.system.backup.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 备份任务视图。 */
public record BackupJobDTO(
        String id,
        String type,
        String status,
        String trigger,
        List<String> scopeTags,
        String planCode,
        String targetCode,
        String targetName,
        String strategy,
        String archiveName,
        Long archiveSize,
        String phase,
        int percent,
        String message,
        long collectionCount,
        long documentCount,
        long objectCount,
        long pluginFileCount,
        long insertedCount,
        long conflictCount,
        long skippedCount,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        LocalDateTime createTime
) {
}
