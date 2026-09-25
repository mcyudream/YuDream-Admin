package online.yudream.base.plugin.spi.system.backup;

/**
 * 合并导入冲突策略：与宿主系统数据的合并语义保持一致。
 * 两端都存在的数据视为冲突，按策略裁决；任一端独有的数据始终保留（无损并集）。
 *
 * @since 2.33.0
 */
public enum PluginBackupConflictStrategy {

    /** 以本地现网数据为准：只插入备份中本地缺失的数据，不覆盖任何现有数据。 */
    LOCAL_WINS,

    /** 以备份数据为准：备份中存在的同标识数据覆盖本地，本地独有数据保留。 */
    ARCHIVE_WINS
}
