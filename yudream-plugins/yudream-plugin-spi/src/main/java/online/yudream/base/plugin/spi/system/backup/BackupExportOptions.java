package online.yudream.base.plugin.spi.system.backup;

import java.util.Map;

/**
 * 导出选项：宿主在触发备份任务时携带、原样传给提供者的参数。
 * 典型用途：插件发起的单实例备份（如 {@code instanceId}）。宿主不解释内容。
 *
 * @since 2.33.0
 */
public record BackupExportOptions(Map<String, String> values) {

    public static BackupExportOptions empty() {
        return new BackupExportOptions(Map.of());
    }

    public String get(String key) {
        return values == null ? null : values.get(key);
    }
}
