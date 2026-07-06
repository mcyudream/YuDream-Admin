package online.yudream.base.plugin.skin.domain.valobj;

public record MigrationLogEntry(
        long time,
        String level,
        String message
) {
}
