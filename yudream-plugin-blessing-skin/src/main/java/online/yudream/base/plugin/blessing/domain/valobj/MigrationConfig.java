package online.yudream.base.plugin.blessing.domain.valobj;

public record MigrationConfig(
        String driverClass,
        String jdbcUrl,
        String username,
        String password,
        String textureBaseDir
) {
}
