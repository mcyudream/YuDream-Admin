package online.yudream.base.domain.system.backup.enumerate;

/**
 * 合并导入冲突策略：两端同标识的数据视为冲突，按策略裁决；
 * 任一端独有的数据始终保留（无损并集，永不删除任何一侧数据）。
 */
public enum BackupConflictStrategy {
    /** 以本地现网数据为准：只插入本地缺失的数据，不覆盖任何现有数据。 */
    LOCAL_WINS,
    /** 以备份数据为准：备份中存在的同标识数据覆盖本地，本地独有数据保留。 */
    ARCHIVE_WINS
}
