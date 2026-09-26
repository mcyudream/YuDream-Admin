package online.yudream.base.plugin.spi.system.backup;

import java.util.Map;

/**
 * 插件备份任务摘要（列表用，仅返回本插件触发任务）：
 * targetCode/targetName 为空表示本机导出（宿主备份中心可下载），
 * options 为触发时携带的导出选项（如 {@code instanceId} 单实例过滤）。
 *
 * @since 2.33.0
 */
public record PluginBackupJobSummary(
        String jobId,
        String status,
        int percent,
        String message,
        String archiveName,
        String targetCode,
        String targetName,
        long createdAt,
        Map<String, String> options
) {
}
