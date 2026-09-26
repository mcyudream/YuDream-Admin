package online.yudream.base.plugin.spi.system.backup;

/**
 * 插件发起的备份任务状态快照（仅本插件触发的任务可查）。
 * status 为宿主任务状态：QUEUED / RUNNING / SUCCEEDED / FAILED。
 *
 * @since 2.33.0
 */
public record PluginBackupJobStatus(
        String jobId,
        String status,
        int percent,
        String message,
        String archiveName
) {
}
