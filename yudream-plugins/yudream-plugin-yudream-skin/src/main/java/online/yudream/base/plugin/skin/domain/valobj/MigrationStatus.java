package online.yudream.base.plugin.skin.domain.valobj;

import java.util.List;

public record MigrationStatus(
        String state,
        boolean running,
        Long startedAt,
        Long finishedAt,
        MigrationReport report,
        List<MigrationLogEntry> logs
) {
    public MigrationStatus {
        logs = logs == null ? List.of() : List.copyOf(logs);
    }
}
