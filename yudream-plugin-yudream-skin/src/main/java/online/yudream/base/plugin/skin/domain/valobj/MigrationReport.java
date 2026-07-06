package online.yudream.base.plugin.skin.domain.valobj;

import java.util.List;

public record MigrationReport(
        int users,
        int players,
        int textures,
        int closetItems,
        int options,
        List<String> warnings
) {
    public MigrationReport {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
