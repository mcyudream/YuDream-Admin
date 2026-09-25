package online.yudream.base.domain.system.backup.valobj;

/** 任务执行统计。 */
public record BackupJobStats(
        long collectionCount,
        long documentCount,
        long objectCount,
        long pluginFileCount,
        long insertedCount,
        long conflictCount,
        long skippedCount
) {

    public static BackupJobStats empty() {
        return new BackupJobStats(0, 0, 0, 0, 0, 0, 0);
    }
}
