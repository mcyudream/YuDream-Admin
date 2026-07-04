package online.yudream.base.plugin.blessing.interfaces.request;

public record MigrationRequest(
        String driverClass,
        String jdbcUrl,
        String username,
        String password,
        String textureBaseDir
) {
}
