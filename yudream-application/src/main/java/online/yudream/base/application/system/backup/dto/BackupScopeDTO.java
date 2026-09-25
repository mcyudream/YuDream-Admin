package online.yudream.base.application.system.backup.dto;

/** 备份范围视图（系统范围 + 插件范围）。 */
public record BackupScopeDTO(
        String tag,
        String type,
        String pluginCode,
        String scopeCode,
        String displayName,
        String description,
        String defaultSchedule
) {
}
