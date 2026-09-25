package online.yudream.base.domain.system.backup.valobj;

import java.util.List;

/** 任务执行结果：导出/远程备份携带归档信息，导入/恢复仅携带统计与告警。 */
public record BackupJobResult(String archiveName, String archivePath, Long archiveSize, BackupJobStats stats,
                              List<String> warnings) {

    public static BackupJobResult archive(String archiveName, String archivePath, Long archiveSize,
                                          BackupJobStats stats) {
        return new BackupJobResult(archiveName, archivePath, archiveSize, stats, List.of());
    }

    public String warningMessage() {
        return warnings == null || warnings.isEmpty() ? null : String.join("；", warnings);
    }
}
