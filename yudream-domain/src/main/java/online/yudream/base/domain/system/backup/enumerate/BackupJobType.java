package online.yudream.base.domain.system.backup.enumerate;

/** 备份任务类型。 */
public enum BackupJobType {
    /** 全量导出到本机归档。 */
    EXPORT,
    /** 按备份计划推送到异地（FTP/WebDAV）。 */
    REMOTE_BACKUP,
    /** 从归档合并导入。 */
    IMPORT,
    /** 从异地归档下载后合并导入。 */
    REMOTE_RESTORE
}
